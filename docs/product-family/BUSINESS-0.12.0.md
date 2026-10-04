# FG MTM 0.12.0 — local team, router profiles and portable voucher archive

Application ID remains `com.fgmachines.mikrotikmanager`; version 0.12.0/code 20. This extends 0.11.0, preserving business IDs, voucher preferences and the original Android Keystore alias.

## Delivered scopes

1. Branch-owned personnel and reseller directory, enable/disable, descriptive roles. Independent reseller subledger supports deposits, withdrawals, sale-time collected-amount commission and full reversals. Integer minor units, transaction/replay guards, nonnegative balances and one commission per sale. Reverse the commission before canceling its sale. Latest 100 wallet movements; directory is capped at 1000 per branch per local operation.
2. Local router connection profiles: name/group/host/port/protocol/username. Router passwords are not saved. Selecting a profile populates the existing connection form; there is still one active connection. No simultaneous polling or enterprise capacity claim.
3. Password-portable voucher archive. AES-256-GCM/PBKDF2, merges existing batches, skips identical batches and rejects conflicting IDs before one durable commit. Export fails if an existing batch is unreadable, rather than silently losing it. Bounds: 20 MiB and 10,000 batches per portable archive. No RouterOS account creation and no recovery of a lost Android key.

Database v4 is additive; portable business backups accept v2/v3/v4. Router connection profiles are separate from portable business/voucher backups.

## Navigation and layout

In-app and phone Back share navigation history. Closing the terminal retains its parent network page. Network opened from technical settings returns to technical settings, then advanced setup. Personnel and voucher archive roots use safeDrawingPadding to keep content outside system bars. Arabic RTL/English LTR remain intact.

## Verification provenance

Application source: `8d0423358d403bcb21306d6a294e2af4d7043b60`.
Build run: `37153796474`. Actual XML reports: 102 unit tests, zero failures/errors/skips; Lint zero errors, 14 warnings, 3 informational findings. APK assembled successfully.

That run's UI job failed before instrumentation: the emulator launcher displayed “Quickstep isn't responding”, blocking AUTO selection. Retrying the failed job reproduced the launcher dialog. Those failed attempts do **not** prove 36 device tests or personnel/archive screenshots.

Verification scripts were corrected without changing application/instrumentation sources. Reuse is gated by comparison against the original commit and validation that the source run's head SHA matches it. Only the known Quickstep ANR is dismissed; app/unknown ANRs remain fatal. Corrective UI run `37155334857` passed: `OK (36 tests)`, including both NavigationBackTest cases; runtime logs contain no FATAL EXCEPTION. Three requested screenshots were inspected at 720x1600: personnel wallet, voucher archive and router center text/buttons are legible and outside system bars. OperationsUiTest exercised save/select/deposit/back. This does not prove two-phone transfer or physical hardware. The delivered APK is byte-identical to the original build, SHA-256 `fd447ef05295acdbc708d8076d19c0eec432b0205c8c0b9427a29a6050cd3476`. Original run retains its failed conclusion; corrective emulator results are attributed separately.

## Remaining boundaries

Roles are organizational labels, not employee logins or enforced permissions. Wallets are separate from customer revenues/expenses. Router center is local connection management. No real-router, physical-printer or two-phone transfer acceptance test. Original installed-app signing key is unavailable; Debug is not a certified upgrade. Do not uninstall a populated application or clear its data to install it.

## Next dependency order

Use `INSPECTION-2026-10-02.md`, Dependency order, as the authoritative order:

- Stage 4: employee authorization in services and navigation; wallets/branches/imports; unified audit. Existing directory/wallet/branch/import features do not complete authorization or authenticated audit.
- Stage 5: saved routers, bounded monitored sessions, alerts and logical topology, plus real network/printer testing. Existing profiles only complete the saved connection subset.
- Stage 6: optional server contracts/auth/Postgres/workers/accounting/RADIUS/sync with financial append/reversal conflict rules and measured scale.
- Stage 7 follows reusable domain contracts: Windows architecture, tables/printing/secure storage/installer and Windows 10/11 validation.

Stages 4–6 are the next three dependency stages; none should be marked complete from local feature names or a green Android build. Signing recovery and hardware acceptance remain open gates from earlier stages.
