using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using FG.MTM.Desktop.Server;
namespace FG.MTM.Desktop.Router;

// RouterOS 7 REST over trusted HTTPS. Never disables certificate validation.
public sealed class RouterRestClient(HttpClient http) : IDisposable
{
    private Uri? origin;
    private string? authorization;
    public bool IsConnected => authorization is not null;
    public static readonly IReadOnlyDictionary<string,string> Modules = new Dictionary<string,string> {
        ["Health"]="system/resource", ["Interfaces"]="interface", ["Bridge"]="interface/bridge", ["VLAN"]="interface/vlan",
        ["IP"]="ip/address", ["DHCP"]="ip/dhcp-server", ["DNS"]="ip/dns", ["Routes"]="ip/route",
        ["Firewall"]="ip/firewall/filter", ["NAT"]="ip/firewall/nat", ["Mangle"]="ip/firewall/mangle",
        ["Queues"]="queue/simple", ["Wi-Fi"]="interface/wifi", ["HotSpot users"]="ip/hotspot/user", ["HotSpot online"]="ip/hotspot/active",
        ["PPPoE users"]="ppp/secret", ["PPPoE online"]="ppp/active", ["WireGuard"]="interface/wireguard",
        ["Files"]="file", ["Logs"]="log", ["Scripts"]="system/script", ["Scheduler"]="system/scheduler", ["Neighbors"]="ip/neighbor"
    };
    public async Task ConnectAsync(string url,string username,string password,CancellationToken ct=default)
    {
        Disconnect(); origin=ServerConnection.ValidateOrigin(url);
        if(string.IsNullOrWhiteSpace(username)||username.Contains(':')||username.Length>120||password.Length>128)
            throw new ArgumentException("Invalid router credentials.");
        authorization=Convert.ToBase64String(Encoding.UTF8.GetBytes(username+":"+password));
        try { await ReadAsync("Health",ct); } catch { Disconnect();throw; }
    }
    public async Task<IReadOnlyList<Dictionary<string,string>>> ReadAsync(string module,CancellationToken ct=default)
    {
        if(origin is null||authorization is null)throw new UnauthorizedAccessException("Router login required.");
        if(!Modules.TryGetValue(module,out var menu))throw new ArgumentException("Unsupported router module.");
        using var request=new HttpRequestMessage(HttpMethod.Get,new Uri(origin,"rest/"+menu));
        request.Headers.Authorization=new AuthenticationHeaderValue("Basic",authorization);
        using var timeout=CancellationTokenSource.CreateLinkedTokenSource(ct);timeout.CancelAfter(TimeSpan.FromSeconds(20));
        using var response=await http.SendAsync(request,timeout.Token);
        if(response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden){Disconnect();throw new UnauthorizedAccessException("Router access denied.");}
        if((int)response.StatusCode is >=300 and <400)throw new HttpRequestException("Router redirects are not permitted.");
        response.EnsureSuccessStatusCode();
        using var json=JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
        var rows=json.RootElement.ValueKind switch {
            JsonValueKind.Array=>json.RootElement.EnumerateArray().ToArray(),
            JsonValueKind.Object=>new[]{json.RootElement},
            _=>throw new JsonException("Invalid RouterOS response.")
        };
        return rows.Select(row=>row.EnumerateObject().Where(p=>!Sensitive(p.Name)).ToDictionary(p=>p.Name,p=>p.Value.ValueKind==JsonValueKind.String?p.Value.GetString()??"":p.Value.ToString())).ToList();
    }
    private static bool Sensitive(string name)=>new[]{"password","secret","private-key","preshared-key","community"}.Any(s=>name.Contains(s,StringComparison.OrdinalIgnoreCase));
    public void Disconnect(){authorization=null;origin=null;}
    public void Dispose()=>Disconnect();
}
