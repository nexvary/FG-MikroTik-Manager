# FG MTM 0.13.0 — local employee access

Source/build evidence will be recorded after CI completes. Until then this document describes implementation scope, not passed verification.

## Local access implementation

- Additive business DB migration v4 -> v5. Existing money, branches, employees and immutable entries are retained. Application ID is unchanged. Android version 0.13.0 / code 21.
- Optional enrollment creates one device owner (`owner`). Before enrollment, legacy workflows retain their previous access behavior. Enrollment protects the existing workspace rather than creating another business database.
- Owner assigns logins to existing active employees. Roles derive from the immutable employee role; disabling membership and resetting credentials invalidate sessions on service revalidation.
- Passwords: salted PBKDF2-HMAC-SHA256 verifiers (210,000 iterations), 12–128 characters. No router password storage. Five failed attempts impose a persisted 30-second delay. In-memory sessions expire after 15 minutes, including background time, or manual lock; they are not stored across process restarts.
- Services check permissions and organization/branch scope. Screens hide forbidden operations. Workspace ViewModels, drafts, router sessions and saved navigation are isolated between identities.
- Cashiers can create subscribers and post sales/collections/renewals, but cannot reverse entries, configure plans, enter router tools or administer employees. Technicians can import subscribers/manage routers without financial posting. Read-only employees cannot post. Resellers see only their own wallet and cannot mutate it.
- Owner/admin may manage branches. Branch managers remain scoped to their assigned branch. Only owner can issue/reset employee logins.
- Original voucher archives have no branch ownership. After enrollment they are accessible only to owner/admin; they are not falsely relabeled as branch-owned. Router profile group labels remain labels, not router ownership/access control. Router tools require ROUTER permission and RouterOS's actual credentials.
- New business events carry the local actor ID in the existing immutable audit. Router mutations record PENDING before the call, then ACKNOWLEDGED or REVIEW; command paths only, without password/attribute values. ACKNOWLEDGED means the API accepted the request, not independently verified router state. Interrupted outcomes can remain PENDING.
- Portable business backups intentionally retain their v4 data format and v2/v3/v4 restore support. Employee credential hashes, sessions and throttles are excluded. Restore is blocked after owner enrollment to preserve credential scope; on a new device restore business data first, then create local logins. Original encrypted voucher format and Android key alias are retained.

## Limits and release gate

Local access does not implement centralized identity, RADIUS, remote revocation, biometrics, device-owner recovery or defense against a rooted device/modified app. It does not change RouterOS account policy. Lost owner credentials have no bypass; preserve credentials and verified portable business backup before enrollment. Existing wallets remain separate from customer revenue/expenses. Audit actor IDs do not retroactively identify legacy `local-app` events, and full field-level old/new snapshots/device attribution are not yet implemented.

Physical router/printer/two-phone transfer and original signing-key recovery remain open. Do not uninstall the installed app or clear its data to install a debug APK. This is not a compatible signed production upgrade claim.

## Next batch, using the existing dependency order

Source: `INSPECTION-2026-10-02.md`, Dependency order, items 4–6. Prior prerequisites remain open where specified (especially signing recovery and the indexed voucher migration).

| Stage | Existing implemented scope | Remaining scope / acceptance gate |
|---|---|---|
| 4. Employee authorization, wallets/branches/imports, unified audit | Local logins and service/navigation permissions are introduced here. Local directory, wallet journal, branches and imports already exist. | CI/service/UI verification for this increment; complete actor/device/old-new audit requirements and account administration usability review. This is not centralized enterprise authorization or automatic wallet accounting. |
| 5. Saved routers, bounded monitored sessions, alerts, logical topology, real network/printer tests | Saved local connection metadata and one active session exist in 0.12.0. | Implement bounded polling/session ownership and lifecycle cancellation; freshness/failure states and bounded history; alert deduplication/cooldown/resolution; derived logical topology with unknown links explicit; router/printer hardware evidence. No simultaneous multi-router monitoring claim exists. |
| 6. Optional server contracts/auth/Postgres/workers/accounting/RADIUS/sync and measured scale | Not implemented. Android still operates locally without an FG server. | Define reusable versioned domain/event contracts first; then real server authentication/authorization, Postgres migrations, bounded workers and deduplicated RADIUS/accounting ingestion. Sync must append/reverse immutable finance and quarantine conflicts, never overwrite money. Measure target workloads before capacity claims. |

Windows is item 7 and follows reusable contracts; no Windows application or installer exists. Do not skip to Windows or declare items 4–6 complete because a subset of buttons/tables exists.
