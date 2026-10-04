# FG MTM Desktop 0.1.0 preview

.NET 8 / WPF. Existing foundation extended, not replaced. This is an early functional preview, not Android parity or production final.

Working: Arabic RTL / English, sidebar navigation and actual Back history, advanced visibility, HTTPS FG Server login, identity verification against the selected tenant/branch, in-memory token with 15-minute expiry, logout (local; server logout endpoint not present), RADIUS users and sessions, active/expired/disabled search filters, exact byte counters and safe network errors. The API supplies no session start timestamp. Automatic retries are deliberately disabled (including login POST); Refresh provides manual read retry. Redirects are disabled on the actual HttpClient.

RouterOS 7 HTTPS REST reads are available for health, interfaces, neighbors, HotSpot/PPP users and active sessions, and 23 advanced menus including NAT/Mangle/WireGuard. Secret fields are removed before display. Trusted router TLS certificates are required. There is no insecure TLS bypass. RouterOS 6 binary API, writes, discovery, multi-router and commerce sections remain pending. No mock results. RADIUS mutation and disconnect endpoints do not exist yet, so these actions are not offered. Changing branch requires another authorized account. The current API caps users at 2000 and sessions at 5000; pagination is still required for larger deployments.

Run client contract tests:

```sh
dotnet run --project desktop/FG.MTM.Desktop.Tests -c Release
```

Build/launch on Windows:

```sh
dotnet run --project desktop/FG.MTM.Desktop -c Release
```

CI runs contract tests, builds, publishes a self-contained portable preview, then launches the actual WPF executable and checks page/back navigation and both RTL/LTR layouts. It does not prove visual quality or real server/network commissioning. Installer is intentionally pending functional parity and acceptance; the portable is labelled preview.
