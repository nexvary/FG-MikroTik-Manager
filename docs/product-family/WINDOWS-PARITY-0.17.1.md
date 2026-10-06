# Windows / Android parity — 0.17.1

Inspected the live `dev/product-family` branch at `2722447d6bb128b848cd5942ba8e4d385d17d798` before changing it. Android is 0.17.1 / versionCode 26. Windows is the native C++20 / Qt application in `desktop/qt`; the older WPF application is retained as existing code.

## Capability comparison

| Android capability | Native Windows implementation |
| --- | --- |
| Router profiles, local discovery, REST HTTPS/explicit HTTP, API/API_SSL/AUTO | Bridge + RouterClient; connection profiles and discovery |
| Network and system management | Same 33 module descriptors and field forms; command preview/confirmation |
| Command library and terminal | Same 137 templates; floating F4 terminal; clipboard |
| Router/client readiness and network doctor | 21 client-scoped checks plus Internet and hostname probes; failed/denied reads remain UNKNOWN |
| HotSpot / bridge preparation and recovery | Preview/revalidate, encrypted backup, guarded apply/rollback |
| Adult / ads / tracker DNS filtering | Same resolver modes, selected private DHCP networks, restore journal |
| Subscriber/voucher operations | Enable/disable, disconnect/delete, profile change and time top-up; online-only view and search |
| Allowance and consumption | Saved/live counters, state, remaining time/data; unavailable/overflow values remain unknown |
| Four voucher services | HotSpot, User Manager, PPPoE, offline; loaded router profiles/server suggestions |
| Bulk generation and activation | Up to 5,000 unique cards, finite limits/expiry, per-card results, no automatic replay after an uncertain write |
| QR, sharing, export and printing | QR, bilingual credential text, selected-card PNG/PDF, batch PDF/HTML/CSV/RSC, A4/58/80 mm |
| Voucher archive | Encrypted local storage, paging, Android-compatible portable archive; local activation metadata survives canonical reimport |
| Portal branding and preview | Name, support, welcome, colour, terms, website, logo; login/status preview; verified install/restore |
| Backup, clock, DNS repair and quick tools | Encrypted backup/password vault, restore, clock/NTP, config export, flush DNS, reboot |
| Local business | Subscribers/plans, charges/payments, renewal, sales/POS items, invoices/receipts, expenses, reversal/void |
| Staff, branches, roles and audit | Local owner/account management, permission guards, branch scope, immutable financial/audit records |
| Resellers | Wallet entries, commission and role-scoped access |
| Imports and reports | CSV preview/revalidation/atomic import, complete-period export, separate currencies; router-account import under Bindings & renewal |
| Router-bound subscriptions | Device/account identity, immutable renewal job, expiry enforcement, suspension before invoice void |
| Server workflows | HTTPS scope verification, revision/conflict-protected two-way replication, business/RADIUS/session/diagnostic reading |
| Monitoring | Four opted-in routers, two concurrent polls, outages/recovery; optional Windows tray operation |
| Arabic / English, Back, About | RTL/LTR, same eleven main palette colours, restored navigation context, developer links |

The first six primary entries follow the actual Android menu order: Advanced Setup, Network & connectivity, System & security, Voucher Studio, Subscribers & accounts, About developer. Desktop workspaces and shortcuts follow these entries. Long Arabic sidebar labels wrap rather than being shortened.

## Changes from the inspected Windows baseline

Completed client-scoped diagnostics, subscriber consumption/online display, loaded voucher profiles, bilingual sharing text, printed data/expiry, canonical portable archive interoperability, standalone portal backup/clock preparation, navigation context restoration, and full Arabic sidebar labels.

Portable archives carry voucher data and never establish live activation. An existing local batch keeps its activation state after a matching portable round trip. A newly imported online batch is REVIEW until its router outcome is reconciled; it is not silently replayed.

## Validation gates

- Eight Windows contract suites: protocol, router transport, vouchers/archive, business, network tools, monitoring, billing, HTTPS/cloud.
- Independent QR pixel decoding.
- Packaged and installed application smoke tests; 32 Arabic/English workspace/dialog screenshots including subscriber/online pages.
- Android/Windows portal asset equality plus login/status tests for CHAP, independent passwords, units, unlimited/zero limits and safe prefill.
- Direct palette comparison: eleven matching colour values. Module/command resources: 33 modules, 137 command templates. Business schema: Android database version 7, 109 statements.

Automated test data is an explicit fixture. Physical MikroTik/AP commissioning, actual A4/58/80 mm printer drivers, two-device archive transfer and Windows 10/11 user-device acceptance remain unverified. Platform sharing/printing uses Windows clipboard/files/printer drivers and Android's share/print facilities respectively.

The installer is an unsigned development release candidate. Do not claim physical acceptance, exhaustive interaction equivalence or a signed production release solely from successful CI.
