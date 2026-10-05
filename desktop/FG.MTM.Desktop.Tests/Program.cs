using System.Net;
using System.Text;
using FG.MTM.Desktop.Server;

int passed = 0;
async Task Test(string name, Func<Task> action) { await action(); Console.WriteLine($"PASS {name}"); passed++; }
void Check(bool ok) { if (!ok) throw new Exception("Assertion failed"); }
async Task Throws<T>(Func<Task> action) where T : Exception { try { await action(); } catch (T) { return; } throw new Exception($"Expected {typeof(T).Name}"); }
const string LoginJson = "{\"token\":\"abcdefghijklmnopqrstuvwxyz123456\",\"expires_in\":900}";
const string IdentityJson = "{\"tenant\":\"company\",\"branch\":\"main\",\"role\":\"owner\"}";
HttpResponseMessage Json(string body, HttpStatusCode code = HttpStatusCode.OK) => new(code) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
(FgServerClient Client, HttpClient Http) Make(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> send)
{
    var http = new HttpClient(new Handler(send)); var client = new FgServerClient(http); client.SetBaseAddress("https://fg.example"); return (client, http);
}
async Task Authenticate(FgServerClient c) => await c.LoginAsync("company", "main", "owner", "secret password");
foreach (var url in new[] { "http://fg.example", "https://u:p@fg.example", "https://fg.example/path", "https://fg.example/?token=x", "https://fg.example/#x", "relative", "ftp://fg.example", "" })
    await Test($"reject origin {url}", () => Throws<ArgumentException>(() => Task.FromResult(ServerConnection.ValidateOrigin(url))));
await Test("valid HTTPS origin", () => { Check(ServerConnection.ValidateOrigin("https://fg.example:8443").Port == 8443); return Task.CompletedTask; });
await Test("required account scope", () => Throws<ArgumentException>(() => Task.FromResult(new ServerConnection("https://fg.example", "", "main", "owner").Validate())));
await Test("real login request and identity contract", async () => {
    int calls = 0;
    var (c,h) = Make(async (r, ct) => {
        calls++;
        if (r.RequestUri!.AbsolutePath == "/v1/login") {
            Check(r.Method == HttpMethod.Post); using var body = System.Text.Json.JsonDocument.Parse(await r.Content!.ReadAsStringAsync(ct));
            Check(body.RootElement.EnumerateObject().Count() == 2); Check(body.RootElement.GetProperty("username").GetString() == "owner"); return Json(LoginJson);
        }
        Check(r.RequestUri.AbsolutePath == "/v1/identity" && r.Headers.Authorization?.Scheme == "Bearer"); return Json(IdentityJson);
    }); using(h) { await Authenticate(c); Check(c.IsAuthenticated && c.Identity!.Branch == "main" && calls == 2); c.Logout(); Check(!c.IsAuthenticated && c.Identity == null); }
});
await Test("tenant and branch mismatch never retains token", async () => {
    var (c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath=="/v1/login" ? LoginJson : IdentityJson.Replace("main","other"))));
    using(h) { await Throws<UnauthorizedAccessException>(()=>Authenticate(c)); Check(!c.IsAuthenticated); await Throws<UnauthorizedAccessException>(()=>c.GetRadiusUsersAsync()); }
});
await Test("snake case counters and boolean stopped", async () => {
    var (c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath switch {
        "/v1/login"=>LoginJson, "/v1/identity"=>IdentityJson,
        "/v1/radius/users"=>"{\"users\":[{\"username\":\"a\",\"enabled\":true,\"expires\":\"2000-01-01T00:00:00Z\",\"seconds\":60,\"input_octets\":4294967356,\"output_octets\":125,\"active_sessions\":1}]}",
        _=>"{\"sessions\":[{\"nas\":\"router\",\"session\":\"id\",\"username\":\"a\",\"seconds\":60,\"input_octets\":4294967356,\"output_octets\":125,\"stopped\":false},{\"nas\":\"router\",\"session\":\"old\",\"username\":\"a\",\"stopped\":true}]}" })));
    using(h) { await Authenticate(c); var u=(await c.GetRadiusUsersAsync()).Single(); Check(u.InputOctets==4294967356 && u.TotalUsage==4294967481 && u.ActiveSessions==1 && u.IsExpired); var s=await c.GetRadiusSessionsAsync(true); Check(s.Count==1 && s[0].Session=="id" && !s[0].Stopped); }
});
foreach(var code in new[]{HttpStatusCode.Unauthorized,HttpStatusCode.Forbidden})
await Test($"{code} clears session", async()=> {
    var (c,h)=Make((r,ct)=>Task.FromResult(r.RequestUri!.AbsolutePath switch {"/v1/login"=>Json(LoginJson),"/v1/identity"=>Json(IdentityJson),_=>Json("{}",code)}));
    using(h) { await Authenticate(c); await Throws<UnauthorizedAccessException>(()=>c.GetRadiusUsersAsync()); Check(!c.IsAuthenticated); }
});
await Test("active query transmitted", async()=> {
    var (c,h)=Make((r,ct)=> { if(r.RequestUri!.AbsolutePath=="/v1/radius/sessions") Check(r.RequestUri.Query=="?active=1"); return Task.FromResult(Json(r.RequestUri.AbsolutePath switch {"/v1/login"=>LoginJson,"/v1/identity"=>IdentityJson,_=>"{\"sessions\":[]}"})); });
    using(h) { await Authenticate(c); Check((await c.GetRadiusSessionsAsync(true)).Count==0); }
});
foreach(var body in new[]{"{}","{\"token\":\"short\",\"expires_in\":900}",LoginJson.Replace("900","0"),LoginJson.Replace("900","901"),"not-json"})
await Test("malformed login clears session: "+body, async()=> {
    var (c,h)=Make((r,ct)=>Task.FromResult(Json(body))); using(h) { await Throws<Exception>(()=>Authenticate(c)); Check(!c.IsAuthenticated); }
});
await Test("login POST never automatically retried", async()=> {
    int count=0; var(c,h)=Make((r,ct)=>{count++;throw new HttpRequestException("Network unavailable");});
    using(h) { await Throws<HttpRequestException>(()=>Authenticate(c)); Check(count==1 && !c.IsAuthenticated); }
});
await Test("cancellation passed to transport", async()=> {
    var(c,h)=Make(async(r,ct)=>{await Task.Delay(Timeout.Infinite,ct);return Json(LoginJson);});
    using(h) { using var cancel=new CancellationTokenSource(30); await Throws<OperationCanceledException>(()=>c.LoginAsync("company","main","owner","password",cancel.Token)); Check(!c.IsAuthenticated); }
});
await Test("server unavailable", async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json("{}",HttpStatusCode.ServiceUnavailable))); using(h) {await Throws<HttpRequestException>(()=>Authenticate(c));Check(!c.IsAuthenticated);}
});
await Test("redirect rejected", async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json("{}",HttpStatusCode.Redirect)));using(h) {await Throws<HttpRequestException>(()=>Authenticate(c));Check(!c.IsAuthenticated);}
});
await Test("changing server clears identity",async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath=="/v1/login"?LoginJson:IdentityJson)));using(h) {await Authenticate(c);c.SetBaseAddress("https://other.example");Check(!c.IsAuthenticated&&c.Identity==null);}
});
await Test("session expiration blocks authorized calls", async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath=="/v1/login"?LoginJson.Replace("900","1"):IdentityJson)));
    using(h) { await Authenticate(c); await Task.Delay(1200); await Throws<UnauthorizedAccessException>(()=>c.GetRadiusUsersAsync()); Check(c.Identity==null); }
});
await Test("RouterOS REST reads and redacts secrets",async()=> {
    using var h=new HttpClient(new Handler((r,ct)=> {
        Check(r.RequestUri!.Scheme=="https" && r.RequestUri.AbsolutePath.StartsWith("/rest/"));
        Check(r.Headers.Authorization?.Scheme=="Basic");
        return Task.FromResult(Json("[{\"name\":\"router\",\"password\":\"hidden\",\"private-key\":\"hidden\",\"cpu-load\":\"10\"}]"));
    }));
    using var c=new FG.MTM.Desktop.Router.RouterRestClient(h);
    await c.ConnectAsync("https://router.example","admin","secret");
    var row=(await c.ReadAsync("Health")).Single();Check(row.Count==2 && row["cpu-load"]=="10");
    await Throws<ArgumentException>(()=>c.ReadAsync("../v1/login"));c.Disconnect();
    await Throws<UnauthorizedAccessException>(()=>c.ReadAsync("Health"));
});
await Test("RouterOS object response and failed login",async()=> {
    bool denied=false;
    using var h=new HttpClient(new Handler((r,ct)=>Task.FromResult(denied?Json("{}",HttpStatusCode.Forbidden):Json("{\"uptime\":\"1h\"}"))));
    using var c=new FG.MTM.Desktop.Router.RouterRestClient(h);
    await c.ConnectAsync("https://router.example","admin","secret");Check((await c.ReadAsync("Health")).Single()["uptime"]=="1h");
    denied=true;await Throws<UnauthorizedAccessException>(()=>c.ReadAsync("Health"));Check(!c.IsConnected);
});
await Test("business snapshot preserves financial integers and table kinds",async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath switch {"/v1/login"=>LoginJson,"/v1/identity"=>IdentityJson,_=>"{\"revision\":4,\"records\":[{\"table\":\"ledger\",\"id\":\"l1\",\"body\":{\"amount_minor\":9007199254740993,\"note\":\"=HYPERLINK(x)\"}}]}"})));
    using(h) {await Authenticate(c);var snapshot=await c.GetBusinessAsync();Check(snapshot.Revision==4 && snapshot.Records!.Single().Body.GetProperty("amount_minor").GetInt64()==9007199254740993);var csv=BusinessCsv.Write(snapshot.Records);Check(csv.Contains("9007199254740993") && csv.Contains("'=HYPERLINK"));}
});
await Test("diagnostics is a scoped POST with an empty body",async()=> {
    var(c,h)=Make(async(r,ct)=> {
        if(r.RequestUri!.AbsolutePath=="/v1/diagnostics") {Check(r.Method==HttpMethod.Post && r.Headers.Authorization?.Scheme=="Bearer");Check(await r.Content!.ReadAsStringAsync(ct)=="{}");return Json("{\"source\":\"server\",\"checked_at\":\"2026-10-05T00:00:00Z\",\"cached\":true,\"checks\":[{\"key\":\"tls\",\"target\":\"fixed\",\"state\":\"failed\",\"elapsed_ms\":25}]}");}
        return Json(r.RequestUri.AbsolutePath=="/v1/login"?LoginJson:IdentityJson);
    });using(h) {await Authenticate(c);var report=await c.DiagnoseAsync();Check(report.Cached && report.Checks!.Single().ElapsedMs==25);}
});
await Test("logout revokes on server and clears memory even on failure",async()=> {
    bool revoked=false;var(c,h)=Make(async(r,ct)=> {
        if(r.RequestUri!.AbsolutePath=="/v1/logout") {revoked=true;Check(r.Method==HttpMethod.Post && await r.Content!.ReadAsStringAsync(ct)=="{}");return Json("{}",HttpStatusCode.ServiceUnavailable);}
        return Json(r.RequestUri.AbsolutePath=="/v1/login"?LoginJson:IdentityJson);
    });using(h) {await Authenticate(c);await Throws<HttpRequestException>(()=>c.LogoutAsync());Check(revoked && !c.IsAuthenticated && c.Identity is null);}
});
await Test("invalid business bodies rejected",async()=> {
    var(c,h)=Make((r,ct)=>Task.FromResult(Json(r.RequestUri!.AbsolutePath switch {"/v1/login"=>LoginJson,"/v1/identity"=>IdentityJson,_=>"{\"revision\":1,\"records\":[{\"table\":\"ledger\",\"id\":\"a\",\"body\":[]}]}"})));
    using(h){await Authenticate(c);await Throws<InvalidOperationException>(()=>c.GetBusinessAsync());}
});
Console.WriteLine($"{passed} client contract tests passed.");
sealed class Handler(Func<HttpRequestMessage,CancellationToken,Task<HttpResponseMessage>> send):HttpMessageHandler
{ protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken ct)=>send(request,ct); }
