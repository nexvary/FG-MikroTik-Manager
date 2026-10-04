# Optional FG server — development increment

This service is optional; the Android app continues to operate locally. This increment implements versioned event intake, scoped account authentication, a PostgreSQL journal, a bounded worker, immutable financial appends/reversals, and monotonic RADIUS accounting **event** reconciliation. A PAP REST authentication endpoint is also implemented with NAS-key and tenant/branch checks, expiry and failure throttling. The API is not itself a RADIUS UDP server: the included FreeRADIUS 3.x adapter handles packets. Android includes an explicit one-way financial transfer preview. This is not a deployed service or complete bidirectional synchronization; live NAS commissioning remains open.

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

Worker batch: 100 events. Backlog: 10,000 queued events per tenant. One PostgreSQL advisory-locked worker preserves event ordering. These are configured safeguards, not measured throughput/capacity claims. No distributed device merge or offline sync is certified.

## Verification

```sh
pip install -r requirements.txt
FG_DATABASE_URL=postgresql://... python -m unittest discover -s tests -v
```

The CI workflow runs an isolated PostgreSQL 16 service. Tests exercise tenant isolation, role enforcement/revocation/throttling, exact replay/conflict behavior, reversal rules, immutable money and accounting stop/counter rules. CI also exercises FreeRADIUS UDP PAP accept/reject and accounting packets against the actual HTTP API and PostgreSQL. Production TLS, a physical MikroTik, complete Android sync and capacity benchmarks remain release gates. The development HTTP runner permits at most 16 concurrent connections, each with a 15-second socket timeout; an idle REST preconnection cannot monopolize the server.

## PAP REST endpoint and FreeRADIUS adapter

GET `/v1/radius/authenticate` uses Basic subscriber credentials plus `X-FG-NAS-Key`. It returns 204 on success and 403 otherwise. Provision a hashed NAS key through the `nas` CLI command and subscriber verifiers through `radius-user --expires <ISO date with UTC offset>`. The key is printed once for installation into a trusted FreeRADIUS configuration. Use HTTPS and proxy rate limits. Do not expose subscriber credentials in request URLs or logs. No CHAP/MSCHAP/EAP support is claimed. FreeRADIUS 3.x loopback configuration and an isolated packet smoke test are provided under radius/ and scripts/. The isolated PAP accept/reject and Start/Stop packet test passed in CI run 37209810775. Physical NAS tests and production deployment remain open.

FreeRADIUS 3.x module reference: https://github.com/FreeRADIUS/freeradius-server/blob/v3.2.x/raddb/mods-available/rest. Use the configuration syntax for the deployed major version. The supplied loopback smoke configuration is for isolated tests, with disposable credentials; do not copy its shared secret into production.

Android now has an explicit preview transfer screen under Business tools. It requires an HTTPS origin and a server account whose tenant/branch IDs exactly equal the IDs shown in the screen. It transfers financial appends/reversals only, at most 100 per click, compares existing payloads before skipping them, and reports queue states. It never imports remote changes into the phone. Remote history inspection is capped at 10,000 entries. Full branch/customer/plan synchronization is still pending.
