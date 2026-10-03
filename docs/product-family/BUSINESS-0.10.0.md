# FG MTM 0.10.0 — ten local business stages after 0.9.0

This increment implements ten bounded stages in the existing Android app. It is not completion of the whole ISP/Server/Windows roadmap. Direct RouterOS features remain independent.

| Stage | Delivered scope | Explicit boundary |
|---|---|---|
| 1. Plans | Branch-owned immutable priced plans, service/currency, 1–3660 days, paged list | No speed/quota provisioning or plan edits; create another plan for changed terms |
| 2. Local subscriptions | Atomic renewal, append after existing period or today, exact replay, end date | Local calendar periods only; no RouterOS activation/suspension or recurring scheduler |
| 3. Invoices / collection | Snapshotted customer/plan/price, optional partial/full receipt in same transaction, newest-first cancellation reverses charge and linked receipt | Internal subscription document, not a statutory tax invoice; cancellation does not physically refund money; no PDF invoice printer yet |
| 4. Expenses | Categorized expenses with currency, note, immutable original and full reversal | No suppliers, accounts payable or approval system |
| 5. Reports | Date-filtered net charges/receipts/expenses by currency, separately labeled all-time balance | No mixed-currency sums, no invented net-profit KPI; reports use posting dates |
| 6. CSV export | Streaming snapshot of selected branch/date ledger and expenses, UTF-8 BOM, quoting, spreadsheet formula neutralization; system document picker | Contains customer information; amount column is integer minor units; not a complete backup |
| 7. Branches | Add/select branches, existing subscriber and all new business operations scoped to current branch | Local separation, not employee authentication; no interbranch transfers |
| 8. Subscriber import | Template, strict UTF-8 CSV preview, row errors, account conflicts, atomic import and deterministic retry IDs | 1000 rows per operation; manual contacts only; no router credentials/provisioning |
| 9. Portable backup | All business branches in authenticated AES-256-GCM file; random salt/IV; PBKDF2-HMAC-SHA256 210000 iterations, password >=12 characters; transactional restore to unused store only | 20 MiB clear payload per backup; excludes legacy voucher Keystore data and router credentials; passwords cannot be recovered; no overwrite restore |
| 10. Audit | Insert audit for new business events, immutable journal, scope and cursor pagination; restore event | Starts at migration, actor is local app (not identified employee); not tamper-proof on compromised devices |

## Data integrity

Explicit additive SQLite v1→v2 migration preserves existing subscriber/ledger rows and IDs, settings and encryption aliases. No destructive migration. Foreign keys, constraints and triggers enforce ledger scope/currency, immutable records, valid invoice linkage and reversals. Local subscription end is exclusive; a 30-day plan has exactly 30 local calendar days. Cancellation must start with the latest unvoided period. Restore validates the schema, columns, foreign keys, linked invoice state and authenticated payload before transaction commit. Failure rolls back the seed data and trigger changes.

Money remains integer minor units. Operations carry durable caller IDs; conflicting replay is rejected. Import preview is revalidated inside the write transaction. Backup uses a consistent database transaction and fixed allowlisted table/column names, never user-provided SQL. File access uses Android's system document picker without broad storage permissions. Financial export streams the cursor. Plans use a bounded page; invoices/expenses/audit use sequence cursors. Branch choice currently loads the branch catalog; enterprise branch scale is not claimed.

UI: existing Arabic RTL/English theme, in-app back, scrollable forms, lifecycle-stable operation IDs, busy/error states, no automatic network side effects. Backup passwords are not placed in SavedStateHandle/preferences; reselect after process recreation. An interrupted export can leave a partial/empty document; the UI does not report success and explains retry. DB writes and crypto run off the main thread.

## Verification commands

```bash
node scripts/test-portal-status.cjs
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash scripts/capture-ui-proof.sh
bash scripts/test-business-device.sh
```

The last two commands target a disposable emulator, not a populated phone. Instrumentation tests cover actual v1 schema migration, replay/concurrency, invoice rollback/cancellation, expenses/reports, branch boundaries, import conflicts, export escaping, encrypted round-trip/wrong-password/tamper/no-overwrite, audit paging, and real Compose AR/EN forms including activity recreation. Final results are recorded with the release delivery; source test presence alone is not a passing claim.

## Still outstanding

Original release signing key/reliable upgrade path, legacy voucher portable backup/migration, RouterOS-bound paid plans/renewal and scheduled expiry, real-router/printer/device tests, employee permissions, reseller wallets, statutory invoices/POS products, central server/RADIUS/accounting/sync, Windows and measured enterprise scale. Debug CI signing is still not certified compatible with an installed app. Never uninstall a populated app to try this build.

Official implementation references: Android [cryptography](https://developer.android.com/privacy-and-security/cryptography) and [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
