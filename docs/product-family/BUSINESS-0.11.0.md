# FG MTM 0.11.0 — first three integration stages

Continues 0.10.0 in the same Android app/database. No server/Windows rewrite.

## 1. Controlled RouterOS application

An invoice can prepare a durable network intent and explicitly apply/reconcile it on the connected router. Router identity is bound to a hardware serial or license software/system ID, not just an IP/name. Account ID/name/service are checked. A RouterOS ISO-date clock matching the phone within five minutes is required. Existing speed profiles are selected, not invented. No router passwords are saved.

HotSpot applies the profile, enables the account, removes the old uptime cap in favor of calendar expiry, and sets the byte limit to the captured current traffic plus the entered MiB allowance (zero means unlimited). The desired absolute limit is durably saved once, so retries do not add more quota. PPPoE uses profile and expiry only, with zero allowance. Existing sessions are disconnected so the profile applies at reconnection. Preview explains these effects.

A recurring router scheduler checks calendar expiry each minute, including after restart, provided RouterOS clock/scheduler are functioning. It guards the account ID/name before disabling it and removing matching active sessions. Scheduler ownership/conflicts are checked and expiry protection is read back before account enablement. Network errors are not automatically replayed. PENDING/REVIEW/VERIFIED/SUSPENDED states survive restart. Reconciliation uses the same frozen intent, never creates a second charge. Only the latest active invoice can be applied; cancellation requires explicit verified service suspension first. Restoration marks imported network confirmations REVIEW.

**Boundary:** applying after local renewal requires explicit preview/confirmation; it is not unattended provisioning on receipt of an electronic payment. No background app connection, server retry daemon, RADIUS, router backup rollback or distributed lock across external administrators exists. Local financial commits and RouterOS changes are deliberately separate durable operations. Expiry accuracy depends on correct router time and scheduler permissions; hardware deployment/reboot/clock-loss behavior requires real-router acceptance testing. Verification with a simulated transport does not certify RouterOS scripting execution.

## 2. Router import

Projected API reads request account ID/name/profile/disabled/service, never passwords. Reads HotSpot and PPP secrets explicitly configured for pppoe; generic `any` and other PPP services are excluded. Preview search, 50-row pages, selection and new-account currency. A matching branch account is linked instead of duplicated. Multiple matches, a changed RouterOS ID or another branch's binding are conflicts. Entire selected import is transactional and repeat-safe. Limits are 5000 retrieved records and 1000 selected per operation, not licensing caps. Transport currently materializes the projected result before preview; enterprise streaming/import scale is not claimed.

## 3. Payments and receipts / manual POS

Cash, Vodafone Cash, InstaPay, Bank and Other with optional reference, recorded with financial transaction/replay checks. Methods and references included in CSV. Existing payments without metadata remain unspecified; a provider transfer is never asserted as verified. Applies to standalone payments, subscription invoice collection and manual sales.

Manual sales accept up to 30 lines (name, quantity, unit price), compute integer totals, and atomically post charge/optional collection. Immutable sale and full cancellation preserve originals. This is not inventory, purchasing, supplier accounting, fiscal tax integration or a statutory invoice system. Receipts for sales/subscriptions include customer, lines/plan, issue-time collection, method/reference and cancellation status. HTML is escaped, contains no JavaScript/external resources, and is handed to Android's print framework for print/Save as PDF. Physical printer services remain unverified.

## Data and verification

Additive SQLite v1/v2→v3 migration. Existing subscriber/financial IDs and legacy voucher keys are preserved. Portable backups export v3, accept v2/v3 snapshots into unused stores, validate sale totals/reversals and restore network jobs as REVIEW. No destructive upgrade.

Commands (JDK17, Gradle8.10.2, SDK35):

```bash
node scripts/test-portal-status.cjs
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash scripts/capture-ui-proof.sh
bash scripts/test-business-device.sh
```

Last commands are only for a disposable emulator. New tests target scheduler-before-enable, lost acknowledgments without automatic replay, absolute quota retry, wrong router/clock/account blocking, escaping, import conflicts, durable job/cancellation lock, sale/payment replay/reversal, v2 upgrade/v3 backup compatibility, escaped receipt and real Compose sales/payment-method flow. Final delivery records actual results.

Debug signing remains unsuitable as a certified upgrade to a populated installed app; original signing key is still missing. Do not uninstall an existing app to try this build.
