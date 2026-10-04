namespace FG.MTM.Desktop.Server;

public sealed record ServerConnection(string BaseUrl, string Tenant, string Branch, string Username)
{
    public Uri Validate()
    {
        if (!Uri.TryCreate(BaseUrl, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
            throw new ArgumentException("HTTPS server URL is required.");
        if (string.IsNullOrWhiteSpace(Tenant) || string.IsNullOrWhiteSpace(Branch) || string.IsNullOrWhiteSpace(Username))
            throw new ArgumentException("Tenant, branch and username are required.");
        return uri;
    }
}
