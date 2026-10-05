# FG MTM Desktop 0.2.0

Windows 10/11 x64, .NET 8 WPF, self-contained installer and portable ZIP. Arabic RTL default / English, vector menu icons, crimson/royal gold/neon/silver styling, FG Machines executable icon, real Back navigation.

Works: HTTPS FG Server login and tenant/branch identity check, server-side logout, scoped RADIUS users/sessions and filters, all 17 synchronized business record types with search, 50-row pages and safe UTF-8 CSV export, bounded server diagnostics (database/DNS/TCP/verified TLS). Business data is read-only; financial integer minor units remain exact. Existing RouterOS 7 HTTPS REST reads for health/interfaces/neighbors/HotSpot/PPP and advanced menus remain available. Trusted TLS is required; no certificate bypass or stored passwords/tokens. Use a dedicated Windows owner account in the same company/branch, distinct from phone and web accounts: another login for the same username revokes its previous session.

Pending: Android parity, local commerce writes, voucher creation/printing, portal deployment, RouterOS 6 binary API, router writes/discovery, multi-router monitoring and field commissioning. This is an incremental release, not completion of Windows parity. No public server address or user identity is embedded. In Settings enter your HTTPS origin (IP certificates work), exact tenant/branch IDs, and the dedicated Windows account.

Build/tests:

```sh
dotnet run --project desktop/FG.MTM.Desktop.Tests -c Release
dotnet build desktop/FG.MTM.Desktop -c Release
dotnet publish desktop/FG.MTM.Desktop -c Release -r win-x64 --self-contained true -o desktop/publish
```

Windows CI builds and launches both portable and installed apps; tests navigation, RTL/LTR, business search/pagination and session cleanup. --smoke-test uses explicitly labeled deterministic UI fixtures, never production data. API contracts use transport fixtures; actual user-server credentials and physical RouterOS commissioning are separate acceptance steps. Screenshots are CI acceptance fixtures. Installer is per-user, requires no separately installed .NET, and is unsigned.
