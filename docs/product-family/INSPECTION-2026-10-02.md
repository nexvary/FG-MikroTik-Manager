# FG MTM inspected baseline — 2026-10-02

This is a source-backed baseline, not a declaration that the entire product-family specification is complete. Source state: 7030f3c6009eec43b642b841f455d317ac322c2a, Android 0.8.4/code 13. Stabilization changes follow on dev/product-family. Hardware claims are explicitly separated from unit/emulator evidence.

## Branches and history

| Remote branch | Head at inspection | Actual contents / disposition |
|---|---|---|
| main | 63e7026 | Initial documentation only; not the current application. |
| dev/phase-1-foundation | 7030f3c | Current native Android implementation, including API, diagnostics, automation, portals and vouchers. Preserve as the starting point. |
| dev/wcg-7.6-parity | bb8e187 | Older 0.1.0 voucher-focused implementation. Inspected mapper/exporter and diff against current application. Contains alternative Wcg models/exporters and lacks current API, advanced setup, portal and session fixes. Do not merge wholesale or introduce a second voucher engine. Reusable ideas must be assessed individually. |
| dev/product-family | initially 7030f3c | Dedicated branch for the coherent product-family implementation. |

A local dev/phone-ui-0.6.0 ref is not a current remote branch. Recent foundation commits fix API reconnect, setup guards, voucher readiness, portal binding, clock/customer-port preparation, install safe areas, command library and voucher sharing. No signing key is tracked. No AGENTS.md was found.

## Architecture/build

Single Android app module. Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Java 17, minSDK 26, target/compileSDK 35. Application/namespace: com.fgmachines.mikrotikmanager. Compose screens -> AndroidViewModel/StateFlow -> RouterRepository -> RouterOsTransport. API socket, REST and AUTO implementations exist. UI/admin forms are large composables; no Room/business SQL database, no shared core module, no desktop or server project exists.

Dependencies: Compose Material3/Foundation/UI, activity 1.10.0, lifecycle 2.8.7, coroutines 1.9.0, serialization 1.7.3, OkHttp 4.12.0, core-ktx 1.15.0, ZXing 3.5.3. Unit tests: JUnit4 and coroutines-test. Dependency upgrades require their own compatibility review, rather than upgrading indiscriminately.

Navigation is enum-based with MENU/ADVANCED/NETWORK/SYSTEM/VOUCHERS/ABOUT. One active repository. No role-aware navigation or persistent multi-router registry. Main old router-status card remains removed; health appears under advanced setup.

## Actual feature matrix

“IMPLEMENTED” below means the documented scope exists in source, not that every RouterOS version/printer was certified.

| Feature | State | Implementation evidence / gap |
|---|---|---|
| Direct local RouterOS control | IMPLEMENTED + REQUIRES REAL ROUTER TEST | API socket/REST; no FG server dependency. API reads reconnect after IO failure; mutations are not replayed by the socket transport. |
| AUTO negotiation | BROKEN at baseline; corrected in stabilization | First operation could be a mutation and fallback could resend it. Use serialized read-only negotiation, then execute once. |
| API-SSL | NOT IMPLEMENTED | Enum exists; repository explicitly rejects it. Plain Socket does not implement TLS. |
| REST HTTPS | IMPLEMENTED + REQUIRES REAL ROUTER TEST | OkHttp certificate validation; no trust-all path. HTTP option is exposed but manifest blocks cleartext: PARTIAL/unsupported deployment configuration. |
| Failed initial connection cleanup | BROKEN at baseline; corrected in stabilization | New repository was not assigned until dashboard success, leaving failed candidate unclosed. |
| Discovery | IMPLEMENTED + REQUIRES REAL ROUTER TEST | MNDP multicast with packet parser/unit tests. LAN/VPN broadcast behavior varies. |
| Health/readiness/diagnostics | PARTIAL + REQUIRES REAL ROUTER TEST | Actual table inspection, ping evidence, error categories, current portal directory checks. No complete AP/MAC/bypass reasoning engine or unified alert subsystem. |
| HotSpot guided setup | IMPLEMENTED + REQUIRES REAL ROUTER TEST | Inspect/signature/conflicts/backup/apply/verify/undo in AdvancedRouterManager; existing HotspotManager setup preserved. Distinct legacy/simple setup paths need consolidation, not blind deletion. |
| Automatic time/NTP | IMPLEMENTED + REQUIRES REAL ROUTER TEST | Phone date/time/zone, owner NTP servers preserved, readback check; NTP synchronized state distinguished. |
| Customer port preparation | IMPLEMENTED + REQUIRES REAL ROUTER TEST | WAN/existing bindings excluded, proposed migration, backup, verification/undo. No assumption that every AP LAN port is a client port. |
| Subscribers | PARTIAL | Router HotSpot users/active users with enable/disable/delete/add time/profile actions. No business subscriber identity, receivables, lifecycle or imports. |
| Active sessions | PARTIAL | HotSpot users/active in HotspotManager; generic PPP menu fallback is not a separate active-session tab. |
| Profiles/packages | PARTIAL | Router profiles loaded; generic network editing. No priced canonical business package model or full clone workflow. |
| Vouchers | IMPLEMENTED + REQUIRES REAL ROUTER TEST | Existing unique generator, modes, quota/time, provision result, encrypted history, QR/PDF/PNG. Archive lacks indexed paging and router/organization ownership. |
| Voucher Designer | PARTIAL | Presets/layout/branding/export; no full WYSIWYG drag/resize/undo/layers. |
| Voucher sharing | IMPLEMENTED + REQUIRES DEVICE APP TEST | Per-voucher text and PNG+QR, Android ACTION_SEND chooser/ClipData URI grant; PDF export shares via existing framework. No WhatsApp-only restriction or automatic message sending. |
| Portal Designer | PARTIAL + REQUIRES REAL ROUTER TEST | Branding/colors/terms/support/logo, previews, new-folder install, verified contents/binding, rollback pointer. No desktop component canvas. Preview overlay transparent root corrected during stabilization. |
| Printing | PARTIAL + REQUIRES PRINTER TEST | A4/thermal-size PDF and PNG, system sharing. No Bluetooth/TCP printer driver, saved printer or verified physical 58/80mm output. |
| Top-Up | PARTIAL | Adds HotSpot usage time without resetting usage/absolute expiry, changes profile. No financial recharge journal/data/speed/expiry combined workflow. |
| HotSpot | IMPLEMENTED + REQUIRES REAL ROUTER TEST | Setup/users/portal/provisioning. Current user's real successful Internet session is user evidence, not unattended test coverage. |
| PPPoE | PARTIAL + REQUIRES REAL ROUTER TEST | PPP secret provisioning/generic CRUD. No complete sessions/expiry/billing mapping/imports. |
| NAS | NOT IMPLEMENTED | No NAS domain/schema/registry. |
| RADIUS | NOT IMPLEMENTED / REQUIRES SERVER | No auth/accounting ingestion or central RADIUS service. API remains independent. |
| Accounting | NOT IMPLEMENTED / REQUIRES SERVER | No session ledger or packet deduplication. |
| Billing / receivables | NOT IMPLEMENTED | Voucher price is display metadata, not a financial ledger. |
| Payments / Expenses / Invoices / POS | NOT IMPLEMENTED | No database/repositories/screens recording these operations. |
| Sales / Financial Reports | NOT IMPLEMENTED | No genuine revenue KPI; do not show mock revenue. |
| Resellers / wallets | NOT IMPLEMENTED | No balances/commissions/ownership boundaries. |
| Employees / roles / permissions | NOT IMPLEMENTED | RouterOS user editor is not an FG employee/permission system. |
| Multi-Router | PARTIAL | Connect/reconnect to one endpoint; no persistent center/groups/router-specific archives. |
| Multi-Branch | NOT IMPLEMENTED | No organization/branch foreign keys or authorized scopes. |
| Alerts | NOT IMPLEMENTED | UI error messages are not alerts with dedup/cooldown/state. |
| Subscriber Import | NOT IMPLEMENTED | No preview/conflict report/import transaction. |
| Monitoring | PARTIAL | Dashboard/manual refresh and current counters; no lifecycle periodic scheduler, history, aggregation or scale-aware polling. |
| Topology | NOT IMPLEMENTED | No derived logical topology UI/model. |
| Backups | PARTIAL + REQUIRES REAL ROUTER TEST | Router encrypted binary backups/RSC, history/restore and encrypted local unlock secrets. No complete portable local business database backup, cloud backup or desktop backup. |
| Audit | PARTIAL | RouterChangeVault encrypted last 100 changes with timestamp/action/result/backup. No actors/branch/organization/device/old/new full audit, no append-only finance audit. |
| Firewall | PARTIAL + REQUIRES REAL ROUTER TEST | Filter generic CRUD; NAT/Mangle/Raw read commands. No complete tabs/reorder/snapshot for each critical edit. |
| Network Tools | PARTIAL | Command center/library and diagnostics ping; no complete dedicated traceroute/Torch/bandwidth/scan UI. |
| Common actions library | IMPLEMENTED + REQUIRES REAL ROUTER TEST | 137 bilingual entries in 11 categories, search/parameters/preview/risk; device/package-specific availability is explained. |
| Security/credentials | PARTIAL | Keystore AES-GCM voucher history/backup secrets, no plaintext saved router credentials. No biometric/PIN/timeout, API-SSL or OS desktop secure store. REST error response raw body removed to avoid reflecting sensitive fields. |
| Arabic/English | IMPLEMENTED + REQUIRES ACCESSIBILITY TEST | RTL/LTR layout and bilingual screens; not all font scaling/TalkBack/keyboard/resolution permutations verified. |
| Windows Desktop / installer | NOT IMPLEMENTED | No Windows project/toolchain/installer. |
| FG Server / sync | NOT IMPLEMENTED / REQUIRES SERVER | No service, API contracts, Postgres migrations, workers, auth or sync implementation. |

## DUPLICATED / unsafe assumptions to avoid

The parity branch contains alternate voucher domain/export implementations. Do not merge those as parallel production systems. Current generic RouterAdmin menus and specialized HotspotManager both expose network operations; develop the specialized business workflow using the same repository/transport and clarify advanced versus simple ownership. Encrypted preferences are currently distinct purpose-specific archives, not competing sales databases. No business database exists yet; one must be introduced with migrations and stable organization/branch/router/entity identifiers.

## Verification / release

Baseline 0.8.4 CI run 37005214070: 74 unit tests, zero failures/errors/skips; lint 0 errors, 9 warnings, 2 informational findings; portal Node checks pass; debug APK assembled. Emulator fixture screenshots cover 25 screens at 720x1600/1080x2400. These prove local layout/build behavior only. Actual preview toolbar overlap was found by image review despite green CI and must be verified after its fix. Printer/real-router/server/Windows/scale testing is not implied.

CI currently builds debug APKs with ephemeral runner debug signing. Verified 0.8.3 and 0.8.4 signing certificates differ. Upgrade compatibility is BLOCKED until the previously installed signing key is recovered and supplied through secure CI configuration. Do not uninstall the existing app: its Keystore-protected local data may become inaccessible. No stable release signing configuration, AAB release workflow or secure updater currently exists. Do not fabricate a compatible release or commit any private key.

## Enterprise readiness

| Target | Evidence/state | Bottleneck / action required |
|---|---|---|
| 80 routers/NAS | Not tested; not supported by current one-session UI | Indexed registry, per-router sessions, bounded scheduler/backoff; optional server workers. |
| 12,000 subscribers | Not tested | Router reads return full rows; no canonical indexed subscriber DB or list paging. |
| 80,000 vouchers | Not tested | Encrypted preference batches are all decrypted before take(limit). Requires indexed migration and bounded pages without losing existing secrets. |
| Accounting/financial history | Not implemented | Append-only business ledger, database constraints/transactions/indexes and server accounting ingestion. |

No capacity claim is made and no licensing caps are introduced. Current generation batch size 1..5000 is a per-operation validation, not a lifetime voucher capacity license. A memory/time review must precede changing it.

## Dependency order

1. Stabilization: safe transport negotiation, session cleanup, preview opacity, actual UI review, signing recovery.
2. One local business store: stable IDs, money in integer minor units with currency, immutable financial events/reversals, organization/branch ownership, migrations/indexes/transactions, paginated repositories. Migrate legacy voucher archives once with rollback/checks and preserve Keystore secrets.
3. Simple-mode workflows around existing RouterRepository: subscribers/packages/POS/sales/payments/expenses/invoices; no fake KPI or disconnected button.
4. Employee authorization enforced in services and navigation; wallets/branches/imports; unified audit.
5. Saved routers, monitored bounded sessions, alerts and logical topology; real network/printer testing.
6. Optional server contracts/auth/Postgres/workers/accounting/RADIUS/sync with append/reversal finance conflict semantics and measured scale.
7. Windows architecture decision after reusable domain contracts exist; implement real tables/printing/secure storage/installer and Windows 10/11 validation. Android remains a client.

Competitive feature names in the specification are requirements, not independently verified claims about other vendors. No proprietary code/assets/branding is copied.

## Subsequent archive stabilization

VoucherHistoryStore now selects a bounded page of encrypted batch IDs before decrypting. New IDs combine timestamp and UUID; existing timestamp IDs remain readable with the original Keystore alias/cipher/preferences unchanged. Writes are serialized across instances and committed durably; save failures are surfaced instead of silently discarded. VoucherHistoryIndex tests exercise 80,000 synthetic ID entries, not 80,000 stored/activated vouchers, database scale, or 80 routers. Sorting the preference index remains O(N log N); this is a mitigation, not the final indexed business database or an enterprise-readiness claim. The archive UI still shows recent batches; the cursor API prepares safe further paging without adding an unsupported button.

Explicit transport disposal now prevents stale requests reopening a session after disconnect. AUTO tracks candidates during negotiation so closing the session closes in-flight candidates; socket close happens before buffered stream close. Regression tests cover read reconnect, no mutation replay, concurrent negotiation, cancellation, close-before-reuse and close-during-negotiation. These remain transport-level tests; full ViewModel cancellation/navigation concurrency needs a separate lifecycle test pass.

Connect-screen stabilization adds explicit IME inset consumption and adjustResize, password keyboard/done action, and keyboard dismissal before connection. Emulator proof now additionally captures 360x640dp RTL/LTR, focused password with IME, and 1.3 font scale. Fixture screenshots do not certify physical devices, TalkBack or every font-scale/resolution combination.
