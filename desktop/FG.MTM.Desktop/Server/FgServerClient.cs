using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace FG.MTM.Desktop.Server;

public sealed class FgServerClient(HttpClient http)
{
    private string? token;
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public void SetBaseAddress(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
            throw new ArgumentException("FG Server requires an absolute HTTPS URL.", nameof(value));
        http.BaseAddress = uri;
    }

    public async Task<LoginResult> LoginAsync(string tenant, string branch, string username, string password, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync("v1/login", new { tenant, branch, username, password }, Json, ct);
        response.EnsureSuccessStatusCode();
        var result = await response.Content.ReadFromJsonAsync<LoginResult>(Json, ct)
            ?? throw new InvalidOperationException("Empty login response.");
        token = result.Token;
        return result;
    }

    public async Task<IReadOnlyList<RadiusUser>> GetRadiusUsersAsync(CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, "v1/radius/users");
        using var response = await http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        var envelope = await response.Content.ReadFromJsonAsync<RadiusUsersEnvelope>(Json, ct);
        return envelope?.Users ?? [];
    }

    public async Task<IReadOnlyList<RadiusSession>> GetRadiusSessionsAsync(bool activeOnly, CancellationToken ct = default)
    {
        using var request = Authorized(HttpMethod.Get, activeOnly ? "v1/radius/sessions?active=1" : "v1/radius/sessions");
        using var response = await http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        var envelope = await response.Content.ReadFromJsonAsync<RadiusSessionsEnvelope>(Json, ct);
        return envelope?.Sessions ?? [];
    }

    private HttpRequestMessage Authorized(HttpMethod method, string uri)
    {
        if (string.IsNullOrWhiteSpace(token)) throw new InvalidOperationException("Login is required.");
        var request = new HttpRequestMessage(method, uri);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return request;
    }
}

public sealed record LoginResult(string Token, string Role, string Tenant, string Branch);
public sealed record RadiusUsersEnvelope(List<RadiusUser> Users);
public sealed record RadiusSessionsEnvelope(List<RadiusSession> Sessions);
public sealed record RadiusUser(string Username, long Seconds, long InputOctets, long OutputOctets, int ActiveSessions, bool Enabled, string? Expires);
public sealed record RadiusSession(string Nas, string Session, string Username, long Seconds, long InputOctets, long OutputOctets, string? Started, string? Stopped);
