# FG MikroTik Manager

Native Android management client for MikroTik RouterOS, designed for phones and tablets.

> Status: early foundation. The project is being built as a native Android application, not as a WinBox wrapper or Windows emulator.

## Goals

- Fast phone UI and WinBox-like tablet workspace.
- RouterOS 7 REST/HTTPS transport first.
- Native RouterOS API/API-SSL transport for RouterOS 6/7.
- Multi-router profiles and sessions.
- Interfaces, Bridge, VLAN, IP, DHCP, DNS, Routes, Firewall, NAT, Mangle.
- Queues, PPP/PPPoE, Hotspot, Wi-Fi, WireGuard/VPN.
- Files, backup/restore, logs, scripts, scheduler, terminal and troubleshooting tools.
- Live traffic/health monitoring.
- Safe change workflow with confirmations, validation and recovery.

## Architecture

The UI never talks directly to HTTP, sockets or RouterOS commands. All router access goes through a transport contract so REST, API/API-SSL and SSH can coexist without duplicating UI logic.

Initial layers:

```
Compose UI
   |
ViewModel / State
   |
RouterRepository
   |
RouterOsTransport
   |-- REST HTTPS (phase 1)
   |-- API-SSL / API (planned)
   |-- SSH terminal (planned)
```

## Security baseline

- HTTPS is the default REST transport.
- Cleartext HTTP is disabled in the Android manifest.
- Credentials are not logged.
- Password persistence is intentionally deferred until encrypted storage is implemented.
- Destructive actions will require explicit confirmation and will be isolated from read-only commands.

## Open-source research

Useful references reviewed before implementation:

- GideonLeGrange/mikrotik-java — Apache-2.0 RouterOS API client.
- go-routeros/routeros — MIT RouterOS API client and async/listen patterns.
- tikoci/restraml — Unlicense RouterOS REST/OpenAPI schema tooling.
- nasnet-community/nasnet-panel — MIT management-panel architecture and multi-protocol ideas.
- 2GT-Media-Group-LLC/mikrotik-manager — AGPL-3.0 feature/safety reference only; code is not copied into this repository.

The implementation in this repository is written independently. License compatibility is reviewed before importing any third-party source.

## Trademark

MikroTik and RouterOS are trademarks of SIA Mikrotīkls. FG MikroTik Manager is an independent project and is not an official MikroTik application.
