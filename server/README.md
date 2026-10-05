# Optional FG server — development increment

## Web panel and Ubuntu test deployment

The server now serves `/panel/`: Arabic RTL / English, responsive dark UI with silver borders, owner/reader login, scoped identity, RADIUS users/search/status filters, RADIUS sessions/active-only, all 17 synchronized business tables including plans, invoices, team/resellers, router bindings and audit before/after details, searchable ingest events and operation details. A navy control-center layout includes exclusive RADIUS state counts, business record mix, colored state badges and a collapsible phone menu. Tables paginate 25 rows locally without dropping matches; UTF-8 CSV exports all matching records from the currently loaded API result/page, with spreadsheet-formula neutralization. No earnings, router reachability or historical time series are inferred from these counts. Every displayed record is read from the existing authenticated API. There is no sample data in the panel. Counts reflect bounded API result sets, not an unrestricted total. Business records require owner. Financial edits and RADIUS renew/disable/top-up/profile/disconnect are explicitly unavailable in this web increment; use supported Android business operations and the existing provisioning CLI. No cross-branch selector or new tenant permission is invented.

Tokens remain in memory; no local/session storage or password persistence. Reload requires sign-in. Expired/revoked/forbidden sessions clear displayed data. POST `/v1/logout` accepts exactly `{}` and revokes only the supplied hashed bearer token, including idempotent replay. Public UI assets use a fixed whitelist, CSP, frame denial and no-store. Login UI requires HTTPS except loopback for isolated tests. The loopback HTTP API requires a TLS proxy; never expose port 8080 directly. Existing API login lockout remains active; apply upstream request rate limits before general public use. This is a test deployment candidate, not production acceptance or a capacity claim.

Requirements: Ubuntu with Python 3, Docker Engine and Compose V2. Official Docker installation: https://docs.docker.com/engine/install/ubuntu/ . Use a dedicated DNS subdomain pointing to the host. Do not replace another project's proxy/site or take over its ports. Caddy HTTPS reference: https://caddyserver.com/docs/automatic-https .

Extract the tested `FG-Server-Ubuntu.tar.gz` into a new directory. On a dedicated host with ports 80/443/8080 free:

```sh
mkdir -p ~/fg-mtm-server
tar -xzf FG-Server-Ubuntu.tar.gz -C ~/fg-mtm-server
cd ~/fg-mtm-server
bash scripts/start-ubuntu.sh mtm.example.com TENANT_ID BRANCH_ID OWNER_USERNAME
```

Replace all four arguments. Use the exact tenant/branch IDs displayed by **Android → Business tools → Server synchronization** for an existing installation. The script creates a private random database password in `.env`, starts PostgreSQL/API/worker/Caddy and prompts for the owner password without printing it or putting it in command arguments. Panel: `https://mtm.example.com/panel/`. Android origin: `https://mtm.example.com` (without `/panel/`). Verify HTTPS/login, explicit Android sync, matching revision/records and disconnect/reconnect behavior before commissioning routers. A new login for the same username revokes its previous session; provision a separate owner account in the same scope for simultaneous Android/panel use.

On a host already running Caddy/Nginx or other apps, do not run the dedicated-host script. Retain loopback-only API publishing in `compose.yml`; configure a separate HTTPS virtual host forwarding to `127.0.0.1:8080` and provision the owner using the existing CLI. Check port 8080 first; choose a different loopback host port if occupied. Optional `compose.https.yml` is for free public ports, not a replacement for another proxy. The script refuses existing `.env` or occupied listening ports; it never stops existing services or removes volumes.

Keep `.env` and backups private. Before upgrading an existing dedicated test installation:

```sh
umask 077
docker compose -p fg-mtm -f compose.yml -f compose.https.yml exec -T db pg_dump -U fgmtm -Fc fgmtm > fgmtm-backup.dump
test -s fgmtm-backup.dump
```

Retain the original `.env` and project name. Replace reviewed app files, not `.env`; build first, stop only this project's API/worker before migration, then start:

```sh
docker compose -p fg-mtm -f compose.yml -f compose.https.yml build
docker compose -p fg-mtm -f compose.yml -f compose.https.yml stop api worker
docker compose -p fg-mtm -f compose.yml -f compose.https.yml run --rm migrate
docker compose -p fg-mtm -f compose.yml -f compose.https.yml up -d
```

Never use `down -v` for upgrades. No existing deployment was upgraded by this change. CI checks PostgreSQL/logout/session isolation, real HTTP + Chromium login/RTL/mobile menu/search/active sessions/business pagination/17-table selection/filtered CSV download/business minor units/XSS-safe rendering/revocation, JS logic, container packaging and existing FreeRADIUS packets. Browser fixtures are isolated test data only. Live DNS/TLS, physical MikroTik and two physical phones remain **PHYSICAL ACCEPTANCE PENDING**.

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
