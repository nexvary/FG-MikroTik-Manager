# Voucher Studio

FG MikroTik Manager includes a first-class Voucher Studio for MikroTik
HotSpot, RouterOS User Manager v7 and PPPoE.

The implementation target is the complete workflow observed in the
user-supplied WiFi Cards Generator v7.6 application, combined with a safer,
native Android architecture.

See `REFERENCE_WIFI_CARDS_GENERATOR_V7_6.md` for the verified behavioral
reference.

## Generator

- Single voucher or batch generation.
- Up to 5,000 vouchers per batch.
- HotSpot, User Manager v7 and PPPoE.
- Numeric, letters-only or alphanumeric usernames.
- Recharge Card mode.
- Prefix and suffix.
- No password, same-as-username or independent random password.
- Profile and server/service selection.
- Uptime and byte quotas.
- Validity/expiry metadata.
- Wi-Fi name, support number, server URL, price and currency.
- Preview before any router mutation.

## Router integration

### HotSpot

- Load HotSpot user profiles.
- Load HotSpot servers.
- Create/edit/disable/delete users.
- Active sessions and hosts.
- Reset counters.
- Authority/rate presets.
- Expired/quota-exhausted cleanup.

### User Manager v7

- Detect User Manager package/version.
- Load User Manager profiles.
- Create users.
- Assign profiles.

### PPPoE

- Detect active PPPoE service.
- Load PPP profiles.
- Create PPP secrets.

## Connection transports

The reference Windows app uses SSH on port 22. FG MikroTik Manager will support
SSH in addition to the structured RouterOS REST/API transports.

Transport selection remains isolated from the voucher domain:

```
Voucher Studio
      |
VoucherGenerator
      |
VoucherBatch
      |
RouterOsVoucherScriptBuilder
      |
VoucherService
   /        \
Local DB    RouterRepository
               |
       REST / API-SSL / API / SSH
```

## Voucher database

Every generated voucher is stored locally before/while it is written to the
router.

Tracked information includes:

- Batch ID.
- Router.
- Backend (HotSpot/User Manager/PPPoE).
- Username.
- Encrypted password where retention is enabled.
- Profile/package.
- Price/currency.
- Generated/used/active/expired state.
- First and last use where available.
- Uploaded/downloaded bytes.
- Operator/device.
- Reprint history.

## Printing and design

- Classic template.
- Custom template.
- Logo/background image.
- Wi-Fi name.
- Support number.
- Card serial.
- Username/password.
- Price/currency.
- Time limit.
- Validity.
- Data quota.
- Local QR-code generation.
- A4 grid PDF.
- Direct Android printing.
- Bluetooth/USB thermal printing through supported printer protocols/SDKs.

## HotSpot authority presets

Compatibility presets mirror the reference application:

- 1 Mbps: 1M/1M.
- 2 Mbps: 1M/2M.
- 3 Mbps: 1M/3M.
- 5 Mbps: 2M/5M.
- 10 Mbps: 3M/10M.

The Android UI uses Mbps terminology even where the legacy reference calls
these profiles "MB".

## Safety and reliability

- Generated credentials are never logged.
- QR generation is local; login credentials are not sent to a QR web service.
- RouterOS values are escaped before script generation.
- Large batches expose progress, cancel and partial-failure state.
- Duplicate checks occur locally and against the target router when practical.
- Failed router writes do not destroy the local batch.
- Destructive bulk actions require confirmation.
- Scripts are previewable/exportable before execution.
- RouterOS capabilities are detected before exposing version-specific features.
