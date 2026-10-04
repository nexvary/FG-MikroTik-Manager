using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace FG.MTM.Desktop.Server;

// Owns an in-memory session only. Callers supply a client with automatic redirects disabled.
public sealed class FgServerClient(HttpClient http)
{
    private string? token;
    private Uri? origin;
    private DateTimeOffset expires;
    private readonly SemaphoreSlim gate = new(1, 1);
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    public bool IsAuthenticated => token is not null && DateTimeOffset.UtcNow < expires;
    public ServerIdentity? Identity { get; private set; }

    public void SetBaseAddress(string value)
    {
        var uri = ServerConnection.ValidateOrigin(value);
        Logout();
        origin = uri;
    }
    public void Logout() { token = null; expires = default; Identity = null; }

    public async Task<ServerIdentity> LoginAsync(string tenant, string branch, string username, string password, CancellationToken ct = default)
    {
        await gate.WaitAsync(ct);
        try
        {
            Logout();
            new ServerConnection(origin?.ToString() ?? "", tenant, branch, username).Validate();
            using var request = Request(HttpMethod.Post, "v1/login");
            // The existing API accepts exactly these two fields.
            request.Content = JsonContent.Create(new { username, password }, options: Json);
            using var response = await SendAsync(request, ct);
            var result = await ReadAsync<LoginResult>(response, ct);
            if (string.IsNullOrWhiteSpace(result.Token) || result.Token.Length < 20 || result.ExpiresIn is <= 0 or > 900)
                throw new InvalidOperationException("Invalid login response.");
            token = result.Token;
            expires = DateTimeOffset.UtcNow.AddSeconds(result.ExpiresIn);
            using var identityRequest = Authorized("v1/identity");
            using var identityResponse = await SendAsync(identityRequest, ct);
            var identity = await ReadAsync<ServerIdentity>(identityResponse, ct);
            if (identity.Tenant != tenant || identity.Branch != branch || string.IsNullOrWhiteSpace(identity.Role))
                throw new UnauthorizedAccessException("Account scope does not match the selected tenant and branch.");
            Identity = identity;
            return identity;
        }
        catch { Logout(); throw; }
        finally { gate.Release(); }
    }

    public async Task<IReadOnlyList<RadiusUser>> GetRadiusUsersAsync(CancellationToken ct = default)
    {
        using var request = Authorized("v1/radius/users");
        using var response = await SendAsync(request, ct);
        var envelope = await ReadAsync<RadiusUsersEnvelope>(response, ct);
        return envelope.Users ?? throw new InvalidOperationException("Missing users response.");
    }
    public async Task<IReadOnlyList<RadiusSession>> GetRadiusSessionsAsync(bool activeOnly, CancellationToken ct = default)
    {
        using var request = Authorized(activeOnly ? "v1/radius/sessions?active=1" : "v1/radius/sessions");
        using var response = await SendAsync(request, ct);
        var envelope = await ReadAsync<RadiusSessionsEnvelope>(response, ct);
        var sessions = envelope.Sessions ?? throw new InvalidOperationException("Missing sessions response.");
        return activeOnly ? sessions.Where(s => !s.Stopped).ToList() : sessions;
    }
    private HttpRequestMessage Request(HttpMethod method, string path) =>
        new(method, new Uri(origin ?? throw new InvalidOperationException("Configure FG Server first."), path));
    private HttpRequestMessage Authorized(string path)
    {
        if (!IsAuthenticated) { Logout(); throw new UnauthorizedAccessException("Login is required or the session expired."); }
        var request = Request(HttpMethod.Get, path);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return request;
    }
    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(TimeSpan.FromSeconds(20));
        var response = await http.SendAsync(request, HttpCompletionOption.ResponseContentRead, timeout.Token);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
        { response.Dispose(); Logout(); throw new UnauthorizedAccessException("Session expired or access denied."); }
        if ((int)response.StatusCode is >= 300 and < 400)
        { response.Dispose(); throw new HttpRequestException("Server redirects are not permitted."); }
        try { response.EnsureSuccessStatusCode(); return response; }
        catch { response.Dispose(); throw; }
    }
    private static async Task<T> ReadAsync<T>(HttpResponseMessage response, CancellationToken ct) =>
        await response.Content.ReadFromJsonAsync<T>(Json, ct) ?? throw new InvalidOperationException("Empty server response.");
}
public sealed record LoginResult(string Token, [property: JsonPropertyName("expires_in")] int ExpiresIn);
public sealed record ServerIdentity(string Tenant, string Branch, string Role);
public sealed record RadiusUsersEnvelope(List<RadiusUser>? Users);
public sealed record RadiusSessionsEnvelope(List<RadiusSession>? Sessions);
public sealed record RadiusUser(string Username, long Seconds,
    [property: JsonPropertyName("input_octets")] long InputOctets,
    [property: JsonPropertyName("output_octets")] long OutputOctets,
    [property: JsonPropertyName("active_sessions")] int ActiveSessions, bool Enabled, string? Expires)
{
    public decimal TotalUsage => (decimal)InputOctets + OutputOctets;
    public bool IsExpired => DateTimeOffset.TryParse(Expires, out var date) && date <= DateTimeOffset.UtcNow;
}
public sealed record RadiusSession(string Nas, string Session, string Username, long Seconds,
    [property: JsonPropertyName("input_octets")] long InputOctets,
    [property: JsonPropertyName("output_octets")] long OutputOctets, bool Stopped)
{
    public decimal TotalUsage => (decimal)InputOctets + OutputOctets;
}
