namespace FG.MTM.Desktop.Server;
public sealed record ServerConnection(string BaseUrl, string Tenant, string Branch, string Username)
{
    public static Uri ValidateOrigin(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps ||
            string.IsNullOrEmpty(uri.Host) || uri.UserInfo.Length != 0 || uri.Query.Length != 0 ||
            uri.Fragment.Length != 0 || uri.AbsolutePath != "/")
            throw new ArgumentException("An HTTPS server origin without credentials, path, query or fragment is required.");
        return uri;
    }
    public Uri Validate()
    {
        var uri = ValidateOrigin(BaseUrl);
        if (new[] { Tenant, Branch, Username }.Any(v => string.IsNullOrWhiteSpace(v) || v.Length > 120))
            throw new ArgumentException("Tenant, branch and username are required (maximum 120 characters).");
        return uri;
    }
}
