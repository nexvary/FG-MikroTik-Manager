# WiFi Cards Generator v7.6 parity plan

Reference executable supplied by the project owner:

- File: `WiFi Cards Generator v7.6(x86).exe`
- Type: Windows PE32 / NSIS installer
- SHA-256: `ba89c0854da155ff1427d22b38f5c291d9cba3d36c9b8600caadc29bd7ed8779`

The executable is treated as a behavioral reference. It is not bundled, redistributed, patched, or executed by this project.

## Functions confirmed for the reference application

Public documentation for WiFi Cards Generator v7.6 confirms these workflows:

- Connect to MikroTik using router IP, username and password.
- HotSpot voucher generation.
- User Manager voucher generation.
- Broadband / PPPoE account generation.
- Offline card generation when the router is not reachable.
- Network name and support/contact number on cards.
- Configurable username/password lengths.
- Time quota and data quota.
- Commercial card price.
- QR-assisted login.
- RouterOS `.rsc` script generation.
- Printable HTML output.

A developer portfolio describing Wifi Cards Generator also confirms a card-design workflow with:

- Importing card background images.
- Adding text, images, lines and borders.
- Freely positioning design elements.
- Importing usernames from CSV.
- Configuring A4 rows and columns.
- Generating PDF.
- Save/share/direct print.

## FG MikroTik Manager implementation matrix

| Capability | Status |
| --- | --- |
| Secure RouterOS connection | Implemented foundation |
| Offline voucher generator | Implemented |
| Batch generation up to 5000 | Implemented |
| HotSpot mode | Generator + RSC implemented |
| User Manager mode | Generator + RSC implemented |
| PPPoE mode | Generator + RSC implemented |
| Username/password format | Implemented |
| Time quota | Implemented |
| Data quota | Implemented |
| Network/support/price metadata | Implemented in card/export model |
| CSV export | Implemented |
| Printable HTML export | Implemented foundation |
| QR login payload | Implemented |
| Direct RouterOS batch upload | Next |
| Card template editor | Next |
| A4 row/column designer | Next |
| PDF renderer | Next |
| Android share/print | Next |
| CSV import | Next |
| Local voucher history/database | Next |
| Bluetooth thermal print | Planned |
| User Manager capability detection | Planned |
| RouterOS 6 native API/API-SSL | Planned |

## Architecture

The v7.6 parity work keeps three independent stages:

1. **Generate** credentials locally.
2. **Export/print** without requiring router connectivity.
3. **Commit** the selected batch to RouterOS using the appropriate adapter.

This mirrors the useful offline behavior of the reference application while preventing router transport failures from destroying a generated batch.

## RouterOS mappings

- HotSpot users: `/ip hotspot user`
- User Manager users: `/user-manager user`
- User Manager profile assignments: `/user-manager user-profile`
- PPP/PPPoE users: `/ppp secret`

The Android implementation will prefer direct API/API-SSL/REST writes when connected and retain `.rsc` export as a portable/manual fallback.
