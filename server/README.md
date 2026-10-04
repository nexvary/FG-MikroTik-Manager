# Optional FG server — development increment

This service is optional; the Android app continues to operate locally. This increment implements versioned event intake, scoped account authentication, a PostgreSQL journal, a bounded worker, immutable financial appends/reversals, and monotonic RADIUS accounting **event** reconciliation. A PAP REST authentication endpoint is also implemented with NAS-key and tenant/branch checks, expiry and failure throttling. The API is not itself a RADIUS UDP server: the included FreeRADIUS 3.x adapter handles packets. Android 0.15.0 includes explicit bidirectional business synchronization for a selected branch. This is not a deployed service; live NAS commissioning and physical acceptance remain open.

## Isolated setup

Python 3.12 / PostgreSQL 16. Set `FG_DB_PASSWORD` to a unique URL-safe random password (not a production password reused elsewhere), then:

```sh
docker compose up -d --build
docker compose run --rm api python -m fg_server account --tenant example --branch main --username owner --role owner
```

Passwords are entered interactively, never as command-line arguments. Publish the loopback-only port 8080 only behind an HTTPS reverse proxy with request rate limits. Database ports are not published. A service shell/DB administrator can revoke an account; every request rechecks enabled state. There is no password recovery or public signup. Keep database backups before upgrading. The image and dependencies need an explicit release lock before production deployment.

## Contract v1

POST `/v1/login`: JSON `username`, `password`; response is a 15-minute bearer token. A new sign-in revokes the previous session. Tokens are stored hashed. Passwords use PBKDF2-SHA256 with 600,000 iterations and per-account random salts. Five failures lock an existing login for at least 30 seconds. Reverse proxy rate limiting is still required, including unknown usernames.

POST `/v1/events`: bearer authentication, JSON array of 1–100 events, at most 1 MiB total. Tenant and branch are derived from the authenticated account, not accepted from the body. Exact event fields: `version:1`, `id`, `device`, `kind`, `body`. Device is asserted by the client, not hardware attestation.

`ledger.append` body: `subscriber`, `currency`, integer `amount_minor`, nullable `reversal_of`, `note`. An identical event ID and payload is idempotent. A reused ID with a changed payload is recorded as CONFLICT and does not replace the original. Reversal must match the original subscriber/currency/opposite amount and cannot reverse a reversal or reverse twice. Financial UPDATE/DELETE is blocked at the database level. Missing/out-of-order references are quarantined; manual reconciliation is required, not silent retries that alter money.

`radius.accounting` body: `nas`, `session`, `user`, `status` (Start/Interim-Update/Stop), integer `seconds`, `input_octets`, `output_octets`. Values are cumulative, including gigawords already combined by the trusted adapter. Session identity is scoped by tenant/branch/NAS/session. Counter regression, missing Start, user mismatch and attempts to reopen a stopped session are quarantined. The FreeRADIUS adapter combines octets/gigawords without float conversion and uses a stable payload digest to deduplicate retransmissions. NAS identity and branch come from the provisioned key, not packet-supplied attributes. Live NAS tests remain required.

GET `/v1/events?after=0`: scoped status page, 100 rows maximum; use the last `seq` as cursor. QUEUED means accepted for processing, not financially applied. APPLIED means the worker committed. QUARANTINED/CONFLICT require review. No route overwrites Android financial records.

Worker batch: 100 events. Backlog: 10,000 queued events per tenant. One PostgreSQL advisory-locked worker preserves event ordering. These are configured safeguards, not measured throughput/capacity claims. Business replica merge uses an explicit revision contract described below; physical two-phone acceptance is still pending.

## Verification

```sh
pip install -r requirements.txt
FG_DATABASE_URL=postgresql://... python -m unittest discover -s tests -v
```

The CI workflow runs an isolated PostgreSQL 16 service. Tests exercise tenant isolation, role enforcement/revocation/throttling, exact replay/conflict behavior, reversal rules, immutable money and accounting stop/counter rules. CI also exercises FreeRADIUS UDP PAP accept/reject and accounting packets against the actual HTTP API and PostgreSQL. Production TLS, a physical MikroTik, physical two-phone synchronization and capacity benchmarks remain release gates. The development HTTP runner permits at most 16 concurrent connections, each with a 15-second socket timeout; an idle REST preconnection cannot monopolize the server.

## PAP REST endpoint and FreeRADIUS adapter

GET `/v1/radius/authenticate` uses Basic subscriber credentials plus `X-FG-NAS-Key`. It returns 204 on success and 403 otherwise. Provision a hashed NAS key through the `nas` CLI command and subscriber verifiers through `radius-user --expires <ISO date with UTC offset>`. The key is printed once for installation into a trusted FreeRADIUS configuration. Use HTTPS and proxy rate limits. Do not expose subscriber credentials in request URLs or logs. No CHAP/MSCHAP/EAP support is claimed. FreeRADIUS 3.x loopback configuration and an isolated packet smoke test are provided under radius/ and scripts/. The isolated PAP accept/reject and Start/Stop packet test passed in CI run 37209810775. Physical NAS tests and production deployment remain open.

FreeRADIUS 3.x module reference: https://github.com/FreeRADIUS/freeradius-server/blob/v3.2.x/raddb/mods-available/rest. Use the configuration syntax for the deployed major version. The supplied loopback smoke configuration is for isolated tests, with disposable credentials; do not copy its shared secret into production.

## Android business synchronization

GET `/v1/business/sync`: bearer authentication, owner role only. Returns `revision` and `records` for the authenticated tenant/branch. POST accepts exactly `revision`, `device`, `records` and returns `accepted`, `revision` after commit. Initial revision zero. Each record has exactly `table`, `id`, `body`. Limits: 50,000 records, 20 MiB serialized records, 21 MiB HTTP body. Integer minor units are mandatory.

Business scope: organization/current branch, subscribers, plans, ledger, invoices/voids, expenses/reversals, import batches, payment details, router bindings, network jobs, sales/voids, staff directory, reseller ledger and audit history/device/before/after details. Local staff password verifiers, router passwords, tokens, encrypted voucher archives and physical network confirmations are excluded. Imported network jobs require local REVIEW; acknowledgement does not prove router execution.

Android Business tools → Server synchronization requires an HTTPS origin and owner credentials. For an existing installation provision the account using tenant/branch IDs displayed there. On an unused phone only the explicit Join checkbox aligns its empty store with an existing server branch before local owner enrollment. Populated or enrolled stores are rejected. Sync is explicitly initiated; credentials are never saved for periodic sync.

The client pulls, validates the merged preview using SQLite constraints, posts the revision, then atomically imports after acknowledgement. Financial replacements/deletions are rejected. Three-way merge handles staff activation against the previous accepted baseline. Other conflicts require review. Concurrent revisions return 409; retry from a fresh pull. Lost acknowledgements can replay identical requests. Writes do not have automatic network retries. Original audit device attribution is retained; imports also add local audit records. Device identity is client asserted, not hardware attestation.

Snapshot versions are append-only and database protected. Financial appends also enter the existing immutable server ledger. Identical legacy events replay as APPLIED without another financial insert; changed events are quarantined. Legacy server money absent from a supplied snapshot blocks sync instead of deleting or inventing metadata.

Deploy the updated API image/schema before using Android 0.15.0 sync. Back up the database first. Migration adds replica tables without deleting existing events or money. Production deployment/TLS, physical routers and two-phone acceptance remain unverified.
