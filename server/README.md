# Optional FG server — development increment

This service is optional; the Android app continues to operate locally. This increment implements versioned event intake, scoped account authentication, a PostgreSQL journal, a bounded worker, immutable financial appends/reversals, and monotonic RADIUS accounting **event** reconciliation. A PAP REST authentication endpoint is also implemented with NAS-key and tenant/branch checks, expiry and failure throttling. It is not a deployed service, a RADIUS UDP server, or an Android sync integration. Do not point live NAS traffic at it yet.

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

`radius.accounting` body: `nas`, `session`, `user`, `status` (Start/Interim-Update/Stop), integer `seconds`, `input_octets`, `output_octets`. Values are cumulative, including gigawords already combined by the trusted adapter. Session identity is scoped by tenant/branch/NAS/session. Counter regression, missing Start, user mismatch and attempts to reopen a stopped session are quarantined. This is an accounting ingestion contract; FreeRADIUS packet authentication, adapter, subscriber credential provisioning and live NAS tests remain required.

GET `/v1/events?after=0`: scoped status page, 100 rows maximum; use the last `seq` as cursor. QUEUED means accepted for processing, not financially applied. APPLIED means the worker committed. QUARANTINED/CONFLICT require review. No route overwrites Android financial records.

Worker batch: 100 events. Backlog: 10,000 queued events per tenant. One PostgreSQL advisory-locked worker preserves event ordering. These are configured safeguards, not measured throughput/capacity claims. No distributed device merge or offline sync is certified.

## Verification

```sh
pip install -r requirements.txt
FG_DATABASE_URL=postgresql://... python -m unittest discover -s tests -v
```

The CI workflow runs an isolated PostgreSQL 16 service. Tests exercise tenant isolation, role enforcement/revocation/throttling, exact replay/conflict behavior, reversal rules, immutable money and accounting stop/counter rules. Production TLS, a physical MikroTik, a RADIUS adapter, Android sync and capacity benchmarks remain release gates.

## PAP REST endpoint (integration pending)

GET `/v1/radius/authenticate` uses Basic subscriber credentials plus `X-FG-NAS-Key`. It returns 204 on success and 403 otherwise. Provision a hashed NAS key through the `nas` CLI command and subscriber verifiers through `radius-user --expires <ISO date with UTC offset>`. The key is printed once for installation into a trusted FreeRADIUS configuration. Use HTTPS and proxy rate limits. Do not expose subscriber credentials in request URLs or logs. No CHAP/MSCHAP/EAP support is claimed. FreeRADIUS configuration parsing and real packet tests are not yet complete.

Reference: https://www.freeradius.org/documentation/freeradius-server/4.0.0/howto/modules/rest/configuration.html (the deployed FreeRADIUS major version must match its own configuration syntax).
