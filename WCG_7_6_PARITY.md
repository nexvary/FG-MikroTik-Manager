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


## Static analysis findings from the supplied installer

The installer was inspected statically without executing it.

- Installer format: NSIS / PE32 x86.
- Embedded application SHA-256: `e263f2153abceabf43a4bbfedf2dc36faa5a3a7086f3efc78bd3e965d546f9dd`.
- Embedded application: PyInstaller CArchive.
- Python runtime: Python 3.13.
- UI toolkit: Tkinter/ttk.
- Router transport in the reference program: Paramiko 4.0.0 over SSH.
- Main application module: `wifi_hotspot`.
- Local settings file name: `hotspot_settings.json`.

### Confirmed UI presets

The v7.6 binary contains the following selectable values:

- Modes: Hotspot, User Manager-V7, PPPoE.
- Username lengths: 4 through 10 digits plus Custom User.
- Recharge Card mode with 11 or 12 digit card length.
- HotSpot can use No Password.
- Time quota: none, 1h, 2h, 3h, 4h, custom hours.
- Data quota: none, 500MB, 1GB, 2GB, 5GB, 10GB, custom.
- User Manager validity: 5 hours, 1 day, 7 days, 15 days, 30 days, custom days.
- Prices: no price, 5/10/25/50/100 EGP, or custom.
- Custom-price currencies include EGP, SAR, AED, KWD, BHD, QAR, OMR, JOD, IQD, LYD, TND, DZD, MAD, SDG, YER, LBP, SYP, MRU, DJF, SOS, ILS, USD, EUR and GBP.
- Custom card styling includes font size, text color, price color, phone color, time color, quota color, WiFi/card-number color, margin, and a user-selected background image.

### Confirmed router operations

Static strings/function names confirm that v7.6:

- Reads HotSpot profiles and HotSpot server names.
- Reads User Manager profiles.
- Reads PPP profiles and checks PPPoE service state.
- Shows router board/resource data and HotSpot active/user counts.
- Can install helper HotSpot profiles/scripts such as Limited 1MB/2MB/3MB/5MB/10MB.
- Generates HotSpot login URLs.
- Generates HotSpot, User Manager and PPPoE RouterOS commands.
- Uploads and executes generated script commands over SSH.
- Writes timestamped HTML card sheets and `.rsc` scripts to the Desktop.
- Uses printable HTML with three cards per row and a Print / Save as PDF action.
- Creates QR images from the HotSpot login URL.

FG MikroTik Manager will implement equivalent workflows independently using Android-native components and RouterOS transports.
