# FG MTM acceptance inspection — 2026-10-04

Inspected branch `dev/product-family`, initially `703eb77f8aba45863287e3eee1d7def2b05fcc8e`. Android 0.14.0 / code 22 / com.fgmachines.mikrotikmanager. No signing key created or changed.

## Android evidence

Latest successful Android run: https://github.com/nexvary/FG-MikroTik-Manager/actions/runs/37209127170
Source: `4d88bf3adaea13a336bd0bfd90fc71dcf13d5d27`. The Android tree equals the initial branch HEAD, confirmed using git diff. Compared with the prompt's `5e098cb` baseline, only OperationsUiTest changed. Production Android sources remained unchanged.

Downloaded verification XML: 107 unit tests / zero failures and errors. Lint succeeded with 14 warnings and 3 informational issues, not a warning-free result. Instrumentation transcript: `OK (44 tests)`. Build and compact-phone UI proof passed. Reviewed the archive, read-only workspace and Arabic business screenshots independently; no overlap or cut-off action was apparent in those screenshots. These selected screenshots do not establish physical behavior or exhaustive manual acceptance of every screen.

The instrumentation suite covers real SQLite migration (including v1 and v2 histories), foreign-scope enforcement, immutable ledger/audit, concurrent payment/renewal, reversal, sales/payment channels, employee permissions, backup corruption and passwords, archive wrong password/replay/conflict/safe rollback, navigation history and modal Back. Test source and downloaded transcript were inspected. Fixtures on the emulator are not a real router, printer, or pair of phones.

Code inspection: database version 6, WAL and foreign keys enabled; immutable ledger and audit triggers retained. No Android production Log/println/printStackTrace calls found by the targeted search. This is a code/log review, not a complete independent penetration test.

Multi-router: at most four opted-in routers, two concurrent polls, isolated catches, 30-second interval, temporary credentials cleared on stop/background, UI explicitly separates router reachability from Internet/physical AP topology. Offline/recovery are in-app events; background OS notifications are not implemented. Full bidirectional server sync remains open (the current financial transfer is explicit, one-way).

APK: https://github.com/nexvary/FG-MikroTik-Manager/actions/runs/37209127170/artifacts/11306196195
UI proof: https://github.com/nexvary/FG-MikroTik-Manager/actions/runs/37209127170/artifacts/11306132424

**Status: tested Android release candidate, not Production Final.** Final exhaustive navigation/interaction acceptance and the gaps above must remain visible.

## Physical acceptance

**PHYSICAL ACCEPTANCE PENDING**

Real MikroTik, AP topology/Internet checks, actual printers (A4/58/80mm), encrypted transfer on two distinct physical phones, and signed upgrade over an existing installation were not performed. Same signing certificate as the installed application is required; this artifact is a debug APK.

## Server

Latest server run at initial HEAD passed: https://github.com/nexvary/FG-MikroTik-Manager/actions/runs/37209927517
Includes PostgreSQL isolation, immutable money, RADIUS PAP/accounting packets and replay/precise counters. Live NAS commissioning, production TLS deployment and bidirectional synchronization remain pending.

## Windows

The initial desktop was a static WPF foundation with unwired buttons. Its FG Server login body, login result and RADIUS counters/session types did not match the actual server. Corrected those contracts, verified tenant/branch using /v1/identity, added memory-only token expiry/denied handling, Arabic/English navigation and working RADIUS read/search/filter views. Existing unsupported server mutations are not exposed.

First new Windows acceptance run passed build, client contract tests, publish and launch/navigation smoke:
https://github.com/nexvary/FG-MikroTik-Manager/actions/runs/37229997583

The portable is a development preview. RouterOS HTTPS read views are a subsequent increment and require another successful CI. Remaining parity work includes discovery/binary API/multi-router, router writes, Voucher Studio/Portal Studio, business/POS/accounts/roles/audit UI, server-backed mutations, manual desktop visual QA, packaging/installer upgrade/uninstall and real hardware acceptance. Do not describe the Windows preview as the requested complete product or create a production installer before these gates pass.
