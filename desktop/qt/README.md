# FG MTM for Windows 0.17.2

Native C++20 and Qt 6.8.3 Quick/QuickControls2, Network, Widgets/PrintSupport and SQLite, built with CMake/MSVC 2022 and packaged with Inno Setup. This follows the applicable desktop stack in `nexvary/NEXVARY-Avionics-Lab`; the application runs locally without a browser engine.

Arabic RTL and English use the Android application's black, navy, silver, blue, mint and gold palette. The first six task cards and sidebar sections follow the connected Android menu order: Advanced Setup, Network & connectivity, System & security, Voucher Studio, Subscribers & accounts, About developer. Windows-specific workspaces follow these entries. The Windows workspace includes the FG Machines icon, task dashboard, resizable layouts, a floating terminal (F4), 137 searchable Android command templates, and a bilingual installation introduction.

## Workspaces

| Android functionality | Windows entry point |
| --- | --- |
| Router profiles, MNDP discovery, API/API_SSL, HTTPS REST, explicit HTTP REST and AUTO | Routers |
| Network/system administration, 33 menu paths, named field forms, previews and confirmations | Routers; floating terminal |
| Readiness, Internet packet loss/latency, interface and HotSpot checks | Network tools → Diagnostics |
| HotSpot preparation, DHCP/pool/NAT, client bridge planning, protected WAN and rollback | Network tools → HotSpot setup |
| Subscriber enable/disable, disconnection/deletion, profile and time top-up | Network tools → Subscribers |
| DNS family / ads-and-trackers filtering with selected client networks and restoration | Network tools → Protection |
| Portal branding, logo selection, saved design, login/status preview, verified installation and restoration | Network tools → Login portal |
| Encrypted router backups, backup password vault, restoration, clock/NTP, missing-DNS repair, export and reboot | Network tools → Backup & clock |
| HotSpot, PPPoE, User Manager and offline vouchers; bulk generation, expiry, QR, selected-card preview, PDF/PNG/HTML/CSV/RSC, A4/58/80 mm printing | Vouchers & archive |
| Encrypted archive, paging, portable password-protected export/import | Vouchers & archive |
| Subscribers, plans, renewal, charges/payments, sales, invoices/receipts, voids and expenses | Local business |
| Staff roles, local login, disabling staff, reseller commissions/wallets, branch selection and audit | Local business |
| CSV subscriber import, preview/revalidation, reports by currency and full-period export | Import & reports |
| Device/account binding, importing router accounts, immutable renewal jobs, expiry enforcement and suspension before invoice void | Bindings & renewal |
| HTTPS identity verification and two-way, revision-checked replication with conflict protection | Server synchronization |
| Server business browsing, RADIUS users/sessions and server diagnostics | Server business; RADIUS; Server diagnostics |
| Four monitored routers, two concurrent polls, health/outage/recovery alerts and optional background tray operation | Monitoring & alerts |
| Developer and FG Machines links | About |

Router writes require local ROUTER permission and are recorded as PENDING followed by ACKNOWLEDGED or REVIEW, without password or command-argument logging. Financial writes are transactional and use stable operation identifiers; records are reversed or voided rather than overwritten. Lost mutation replies never trigger an automatic replay. Conflicting replica records stop synchronization.

Connection passwords and server sessions stay in memory. Router backup passwords, voucher archives and recovery journals use Windows DPAPI. Portable backups use the Android-compatible PBKDF2/AES-GCM envelope. HTTPS certificate and host checks remain enabled. A local owner can be enrolled before using staff accounts; restores and joining an empty server branch precede owner enrollment.

## First use

1. Install `FG-MTM-Windows-0.17.2-Setup.exe` on Windows x64.
2. For an existing server branch, sign in under Server synchronization and join the empty local store before setting up the local owner. Otherwise, create the owner in Local business first.
3. Connect to a MikroTik router in Routers using its actual transport and enabled port. Save a named connection if monitoring is needed. Stored profiles exclude passwords.
4. Server synchronization is optional. The supplied server origin is `https://3.65.234.184`, tenant `d1d1af2d-10f7-41ab-ae96-bdc10333d781`, branch `8f2aeb05-aac9-4769-a2a4-920aa1e1859e`. Use an independently created Windows server account to keep single-session server authentication from revoking another device's token.

## Build and validation

Configure with Qt 6.8.3 for MSVC 2022 x64, then build Release and run CTest. `.github/workflows/windows-qt.yml` builds the native application, runs eight contract groups, independently decodes generated QR pixels, deploys the Qt dependencies, checks every Arabic/English workspace and operation dialogs, builds the installer, and launches the installed application. UI PNGs and JUnit test evidence are uploaded separately.

Tests exercise protocol framing, lost-read recovery and no mutation replay, exact money, roles and wallets, CSV atomicity, receipts and portable/legacy backup restore, preparation/rollback constraints, monitoring transitions, renewal/expiry sequencing with a mock RouterOS server, and HTTPS replication using a process-local public test CA. TLS fixtures are included only in the test executable. Test data and screenshots are explicit fixtures; they are not claimed to come from the user's router or server.

Physical router commissioning, printer-driver operation and the user's live FG Server credentials are not available in the build environment; automated and installed-app checks do not certify those external systems.

### Multiple MikroTik routers and access points

Save each MikroTik under a separate connection name and IP; select the target and authenticate before making changes. Up to four saved MikroTik routers can be monitored concurrently. `Network devices & access points` reads the connected router's Neighbor, DHCP lease and ARP tables, merges records by MAC, and keeps different MACs separate even when an IP conflicts. Results report their evidence source and are not a live availability test. Missing permissions produce partial results with an explanation. Selecting a discovered device fills its address; it does not automatically connect, change settings or grant RouterOS capability. Non-MikroTik AP configuration depends on the model and its supported management interface. Devices with static IPs that do not advertise discovery or appear in ARP may require manual address entry. MNDP discovery is limited to the local broadcast domain.

A second router can use the same upstream internet through the appropriate upstream LAN/switch connection. Independent HotSpot networks require separate downstream address ranges and DHCP servers; a shared HotSpot extension uses bridged APs on the existing HotSpot LAN. Do not join the WAN into the HotSpot bridge. Select the intended topology before configuring either router.

## 0.17.1 parity update

- Subscriber cards include active-session counts, usage state, remaining time/data, and combined saved/live counters. Unknown session reads are explicitly unknown; counters remain decimal strings to avoid JavaScript precision loss. Search and Online now are available, and router changes clear stale subscriber data.
- Voucher profiles and HotSpot servers can be loaded from the connected router for all three supported online services. Manual names remain available when needed.
- Shared voucher text is bilingual and includes the separate password, allowance, expiry and activation status; the login QR still omits a separate password. PDF/PNG/print includes allowance, data and absolute expiry.
- Portable archive export uses the Android DTO fields/defaults. Reimport compares this canonical payload and preserves local activation metadata. Newly imported online cards require activation reconciliation and are marked REVIEW.
- Navigation restores the previous network/system group and subscriber tab. Standalone portal installation now saves an encrypted router backup and verifies clock/NTP before uploading, matching Android's preparation.

These are software changes verified by contract and packaged-app checks. Real router/printer commissioning and two-device physical archive transfer remain separate acceptance checks.

## 0.17.2 desktop interface

Denser task cards, true router/server connection tiles, colored workspace headings, larger two-tone icon badges, and explicit row layouts for RTL navigation. Disconnected diagnostics and subscriber pages now explain the next action without invented metrics. Network tools include a direct route to the router connection screen. Android files remain unchanged.
