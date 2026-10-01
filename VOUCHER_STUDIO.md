# Smart Creator-style Voucher Scope

FG MikroTik Manager will include a first-class Voucher Studio for MikroTik HotSpot and, when available, RouterOS User Manager.

The objective is feature parity in workflow, not copying Smart Creator source code or visual assets.

## Confirmed Smart Creator-style capabilities

### Voucher generation
- Single voucher or batch generation.
- Up to 5,000 vouchers in one batch.
- Numeric or alphanumeric codes.
- Prefix and suffix.
- Username only or username + password workflows.
- Same username/password or independent random password.
- Select HotSpot profile/package.
- Select HotSpot server.
- Comment/batch label.
- Uptime and traffic limits.
- Preview before writing to RouterOS.

### Router integration
- HotSpot users: create, edit, enable, disable, reset counters and delete.
- Active HotSpot sessions.
- Hosts / callers / connected clients.
- HotSpot user profiles and package management.
- RouterOS User Manager when the package is available.
- Capability detection so unsupported menus are hidden rather than failing.

### Voucher database
Every generated voucher is stored locally in the Android app before/while it is written to the router.

This allows:
- Search by voucher code.
- Retain sales/history after a voucher is removed from RouterOS.
- Batch history.
- Used / unused / active / expired state.
- First use and last use when data is available.
- Uploaded/downloaded bytes.
- Associated router, profile, point of sale and operator.

Secrets will be encrypted at rest with Android Keystore-backed storage.

### Packages and pricing
- Package name.
- Selling price and optional cost.
- Speed/rate profile mapping.
- Validity.
- Uptime quota.
- Data quota.
- Shared users.
- RouterOS profile mapping.
- Sales reporting by package.

### Printing and design
- Built-in voucher templates.
- Visual template editor.
- Logo, network name, contact number and custom text.
- Username, password, serial, price, validity and QR/barcode fields.
- A4 grid printing.
- Bluetooth/USB thermal printing where Android printer APIs/SDKs allow it.
- PDF export.
- Reprint by batch or selected vouchers.

### Reporting
- Daily, weekly and monthly sales.
- Voucher counts by package.
- Used / unused / expired.
- Active sessions.
- Download/upload totals.
- Router health summary.
- PDF/CSV export.

## Architecture decision

Voucher generation is a domain module and does not depend on the RouterOS transport.

Flow:

```
Voucher Studio UI
      |
VoucherGenerator
      |
VoucherBatch
      |
VoucherService
   /       \
Local DB   RouterRepository
              |
      REST / API-SSL / API
```

This keeps generation, local history and printing functional even when the router is temporarily unreachable.

## RouterOS mapping

HotSpot vouchers map to `/ip/hotspot/user`.

Important RouterOS fields include:
- `name`
- `password`
- `profile`
- `server`
- `limit-uptime`
- `limit-bytes-in`
- `limit-bytes-out`
- `limit-bytes-total`

RouterOS User Manager will be implemented as a separate adapter because it is an optional package and its model differs from local HotSpot users.

## Safety

- Generated credentials are never written to logs.
- Batch creation shows a summary before router mutation.
- Large batches run with progress, cancellation and partial-failure reporting.
- Duplicate username checks happen locally and against the target router before commit when practical.
- A failed router write does not destroy the locally generated batch.
- Destructive bulk actions require explicit confirmation.
