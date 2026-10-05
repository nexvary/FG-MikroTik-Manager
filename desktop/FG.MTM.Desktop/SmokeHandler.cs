using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
namespace FG.MTM.Desktop;
// Only selected by explicit --smoke-test; normal launches use the real TLS transport.
internal sealed class SmokeHandler : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken ct)
    {
        object body = request.RequestUri!.AbsolutePath switch {
            "/v1/login" => new {token="abcdefghijklmnopqrstuvwxyz123456",expires_in=900},
            "/v1/identity" => new {tenant="fixture",branch="main",role="owner"},
            "/v1/radius/users" => new {users=Array.Empty<object>()},
            "/v1/radius/sessions" => new {sessions=Array.Empty<object>()},
            "/v1/business/sync" => new {revision=1,records=Enumerable.Range(1,51).Select(i=>new {table="subscribers",id="fixture-"+i,body=new {name="Customer "+i,account="fixture-"+i,service="HotSpot"}})},
            _ => new { }
        };
        return Task.FromResult(new HttpResponseMessage(request.RequestUri.AbsolutePath=="/v1/logout"?HttpStatusCode.NoContent:HttpStatusCode.OK){Content=new StringContent(JsonSerializer.Serialize(body),Encoding.UTF8,"application/json")});
    }
}
