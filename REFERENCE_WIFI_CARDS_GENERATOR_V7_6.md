# Reference analysis: WiFi Cards Generator v7.6

This document records behavior observed by static analysis of the user-supplied
Windows installer. The installer and its application were not executed.

## Reference identity

- Installer: `WiFi Cards Generator v7.6(x86).exe`
- Installer SHA-256:
  `ba89c0854da155ff1427d22b38f5c291d9cba3d36c9b8600caadc29bd7ed8779`
- Embedded application: `WiFi Cards Generator.exe`
- Embedded application SHA-256:
  `e263f2153abceabf43a4bbfedf2dc36faa5a3a7086f3efc78bd3e965d546f9dd`
- Application stack: Python 3.13 + Tkinter, packaged with PyInstaller.
- Router connection: SSH, default port 22.

This is a behavioral compatibility reference. FG MikroTik Manager is an
independent Android implementation; source code and visual assets from the
reference application are not copied.

## Connection workflow

The reference application supports:

- Router IP.
- Username and password.
- Default SSH port 22.
- Remember-password option.
- Automatic router/default-gateway detection.
- Server URL derived as `http://<router-ip>`.
- Router statistics after connection:
  - RouterOS version.
  - Board/model.
  - Active HotSpot users.
  - Total HotSpot users.

FG MikroTik Manager should retain its structured REST/API transports and add
SSH command execution because SSH is required for feature parity with this
reference workflow.

## Switching systems

The reference exposes three operating systems/modes:

1. HotSpot
2. User Manager v7
3. PPPoE

### HotSpot

Reads:

- `/ip hotspot user profile print detail without-paging`
- `/ip hotspot print detail without-paging`

Creates users in `/ip hotspot user`.

### User Manager v7

Reads User Manager profiles and verifies that the RouterOS v7 User Manager
package is available.

Creates:

- User Manager users.
- User-to-profile assignments.

### PPPoE

Checks that a PPPoE server is active, loads PPP profiles and creates
`/ppp secret` entries.

## Card creation options

Observed options:

- Wi-Fi/network name.
- Technical-support phone number.
- Server URL.
- Number of cards.
- Price:
  - No price.
  - 5 EGP.
  - 10 EGP.
  - 25 EGP.
  - 50 EGP.
  - 100 EGP.
  - Custom price and currency.
- Classic or custom card design.

### Username modes

- Recharge Card.
- 4–10 digit generated usernames.
- Custom username generator:
  - Custom length.
  - Optional prefix.
  - Numbers only.
  - Letters only.
  - Numbers + letters.

Recharge Card exposes a card length of 5–12 digits, defaulting to 8.

### Password options

- No password.
- Generated password lengths of 4–10 digits.
- Recharge-card behavior.

### Time limits

HotSpot exposes:

- No time limit.
- Add Authority.
- 1, 2, 3 or 4 hours.
- Custom hours.

User Manager exposes:

- No time limit.
- 1–5 hours.
- 1 day.
- 1 week.
- 1 month.

### Data limits

- No data limit.
- 500 MB.
- 1 GB.
- 2 GB.
- 5 GB.
- 10 GB.
- Custom data limit.

### Validity

When the authority/expiry workflow is selected:

- 1–5 hours.
- 1 day.
- 7 days.
- 15 days.
- 30 days.
- Custom days.

## HotSpot authority presets

The reference can install profiles named:

- Limited 1MB → RouterOS rate-limit `1M/1M`.
- Limited 2MB → `1M/2M`.
- Limited 3MB → `1M/3M`.
- Limited 5MB → `2M/5M`.
- Limited 10MB → `3M/10M`.

Although the reference UI says "MB", these are bandwidth rates. The Android UI
will label them as Mbps to avoid confusing megabytes with megabits per second.

The reference also offers automation for:

- Recording first-use time for limited vouchers.
- Periodically deleting expired limited users.
- Deleting users whose uptime or byte quota is exhausted.
- Removing corresponding HotSpot cookies.

The Android implementation will reproduce the behavior with version-aware,
auditable RouterOS scripts rather than blindly pasting opaque scripts.

## Output and printing

The reference generates:

- A RouterOS `.rsc` script.
- An HTML file containing cards.
- QR login links.
- A4 print layout.
- Print / Save as PDF workflow.

Custom card settings include:

- Font size.
- Main font color.
- Price color.
- Support-number color.
- Time/validity color.
- Data-quota color.
- Card-number color.
- Card spacing/margin.
- Optional background image.

FG MikroTik Manager will implement this natively on Android and add direct PDF
export plus Bluetooth/USB thermal-print support where the printer protocol or
vendor SDK allows it.

## Improvement decisions

The Android implementation intentionally improves several design points:

- No dependency on an external QR-code web service; QR codes are generated
  locally so credentials are not sent to a third party.
- Generated router commands are previewable before execution.
- RouterOS values are escaped to reduce command-injection risk.
- Voucher batches are retained locally even if the router later deletes users.
- Credentials will use Android Keystore-backed encrypted storage.
- UI terminology distinguishes Mbps speed from MB/GB data quota.
