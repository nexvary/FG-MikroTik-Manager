# WiFi Cards Generator v7.6 — Functional Parity Matrix

Reference application supplied by the project owner:
- Product: WiFi Cards Generator v7.6
- Version resource: 7.6.0.0
- Windows architecture: x86
- Installer: NSIS
- Publisher/copyright metadata: Bassem Magdy (2026)
- Installer SHA-256: ba89c0854da155ff1427d22b38f5c291d9cba3d36c9b8600caadc29bd7ed8779

This document defines **functional parity**, not source-code or visual-asset copying. FG MikroTik Manager is an independent Android implementation.

## Baseline functions confirmed from the supplied binary metadata and the author's public product documentation

| Capability | Reference app | FG target |
|---|---:|---:|
| Arabic card-generation workflow | Yes | Required |
| MikroTik HotSpot vouchers | Yes | Required |
| User Manager vouchers | Yes | Required |
| PPPoE/broadband accounts | Yes | Required |
| Router IP / username / password connection | Yes | Required |
| Offline card generation | Yes | Required |
| Automatic RouterOS RSC script generation | Yes | Required |
| Direct router provisioning when connected | Yes | Required |
| Username length configuration | Yes | Required |
| Password length/configuration | Yes | Required |
| Network name on printed card | Yes | Required |
| Support/contact number on card | Yes | Required |
| Time/validity quota | Yes | Required |
| Data quota | Yes | Required |
| Card price | Yes | Required |
| QR code | Yes | Required |
| Printable HTML output | Yes | Required |
| RouterOS 6.x and 7.x workflow | Claimed by reference app | Required, verified per transport |
| Multiple card generation | Yes | Required |
| Saved printable cards | Yes | Required |

## FG extensions beyond parity

- Native Android phone/tablet UI.
- REST/HTTPS + native API/API-SSL adapters.
- Router discovery.
- Multiple saved routers.
- Android Keystore-backed credential storage.
- Local voucher database and batch history.
- Duplicate prevention.
- Partial-failure recovery for large batches.
- A4 PDF export.
- Bluetooth/USB thermal printer workflow.
- QR login payload templates.
- CSV export/import.
- Search, reprint and reissue.
- Sales reports.
- Router health and active-session monitoring.
- Full RouterOS management modules in the same app.
- Arabic RTL and English LTR.

## Modes

### HotSpot
Maps generated credentials to RouterOS HotSpot users and profiles.

### User Manager
Separate adapter. Capability is shown only when User Manager is installed/available.

### PPPoE
Creates/manages PPP secrets and maps packages to PPP profiles.

### Offline
Generates credentials, QR payloads and printable/exportable cards without a live router. Provisioning can be deferred.

## Output formats

- Direct RouterOS provisioning.
- `.rsc` export for manual import.
- Printable HTML.
- PDF.
- CSV.
- Shareable batch package.

## Legal/engineering boundary

The reference EXE is used to identify product behavior and interoperability requirements. No proprietary source, artwork, trademarks, or binary resources are copied into FG MikroTik Manager.
