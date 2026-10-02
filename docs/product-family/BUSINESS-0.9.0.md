# FG MTM 0.9.0 — local subscriber accounts

Continues 0.8.6 in the existing Android app. This is the first local business workflow, not completed ISP billing, RADIUS, Windows or server delivery.

## Implemented

- One versioned SQLite business database in app-private storage. Foreign keys, organization/branch ownership, stable UUID identifiers, scoped queries, indexes, bounded cursor pages (30, max 100). Initial organization/branch UUIDs are created once and survive reopening.
- Manual subscriber registration: name, optional phone/network account, service category and fixed currency. Existing RouterOS users are not automatically imported or provisioned by this screen.
- Charge, received payment, per-subscriber balance and journal. Money uses integer minor units, never Double; six supported two-decimal currencies; Arabic/Persian digit input. Positive balance means debt; negative means credit. Currency totals are never mixed.
- Durable transactional writes with caller-stable operation IDs; exact replay returns the original, mismatched replay is rejected. Payments have negative signed amounts internally. No silent write errors.
- Journal correction creates one full opposite entry with a required reason and reference to the original. Database constraints prevent cross-subscriber/currency reversals, second reversals, reversing a reversal, and editing/deleting journal entries. Original records remain visible.
- Arabic/English UI with in-app back, accessible offline and from the connected main menu, search and paging. Real empty states; no invented revenue. Balance becomes unavailable on load failure, never a fabricated zero.
- Existing encrypted voucher preferences and Keystore aliases are untouched. No migration is claimed yet. The business database uses Android sandbox storage and disabled app backup; no extra database encryption/portable encrypted export has been implemented.

## Signing

The original installed-app signing key has not been recovered. Debug CI remains a test build and does not establish upgrade compatibility. Do not uninstall a populated app to install it. Release builds now require FG_MTM_STORE_FILE, FG_MTM_STORE_PASSWORD, FG_MTM_KEY_ALIAS and FG_MTM_KEY_PASSWORD in the environment and refuse to produce an unsigned release. Key files and keystore.properties are ignored by Git. This is signing configuration, not creation of repository secrets or recovery of the original key.

## Verification gates

- Existing unit/Lint/portal/APK gates plus pure money tests.
- Android instrumentation uses the real SQLite implementation: reopening/persistence, idempotency, concurrent writers, ownership/currency constraints, immutable journal, reversal rules, cursor paging, legacy-preference preservation.
- Compose workflow: create subscriber, charge 1000.10 EGP, receive 400.05 EGP, verify 600.05 EGP due, reverse payment with a reason, verify 1000.10 EGP due, switch Arabic, return to subscriber list. Screenshots are from those actions, not a data mock.
- Run on a disposable emulator: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`, then `bash scripts/capture-ui-proof.sh && bash scripts/test-business-device.sh`.

## Remaining

Signed upgrade/key recovery; real RouterOS/TLS/physical printer testing; indexed voucher migration/export; plans, subscriptions, renewal and router binding/provisioning; invoices/POS/expenses/report exports; employee authorization and branch UI; resellers/wallets; multi-router monitoring; optional FG Server/RADIUS/sync and Windows. No measured enterprise-scale claim. Local branch scoping is not yet employee authentication/authorization.
