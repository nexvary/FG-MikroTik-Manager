# Architecture

## Product direction

FG MikroTik Manager is a native Android RouterOS administration client for phones and tablets. It is not a WinBox binary wrapper.

## Design rules

1. **Transport independence** — UI/domain code must not depend on OkHttp, SSH libraries, socket framing, or WinBox internals.
2. **Safe mutations** — read operations and write operations are separated. High-impact changes will require confirmation and validation.
3. **Version-aware commands** — RouterOS commands differ by version/package. Capability discovery must precede feature exposure.
4. **Tablet-first density without desktop clutter** — wide screens use persistent navigation and denser tables; phones use full-screen workflows.
5. **No credential leakage** — secrets are never logged. Persistent credentials require Android Keystore-backed encryption.
6. **No HTTP in production** — REST begins with HTTPS only. Native API-SSL is the preferred legacy-compatible transport.

## Transport roadmap

### REST/HTTPS

Initial implementation for RouterOS 7. RouterOS REST is a JSON wrapper around console/API operations.

### Native RouterOS API / API-SSL

Required for broader RouterOS 6/7 coverage and live/listen-style operations. The implementation will use a dedicated protocol module, with Apache-2.0 `mikrotik-java` evaluated as a dependency/reference.

### SSH terminal

Used for an interactive RouterOS terminal and as an optional fallback for commands that are poorly represented in structured APIs.

### Neighbor discovery

MNDP discovery will be implemented separately from router authentication. Discovery must not implicitly trust a discovered device.

## Planned modules

- Dashboard / resources / health
- Interfaces
- Bridge / VLAN
- IP addresses / ARP / neighbors
- DHCP / DNS
- Routing
- Firewall / NAT / Mangle / Raw
- Queues
- PPP / PPPoE / Hotspot
- Wireless / WiFi
- WireGuard and VPN
- Users / groups / services
- Scripts / scheduler
- Files / backup / restore
- Log
- Tools: ping, traceroute, torch and traffic views
- Terminal
- Multi-router workspaces

## Open-source reference policy

References are used to understand protocol patterns, command coverage and UX decomposition.

- `GideonLeGrange/mikrotik-java` — Apache-2.0; eligible for dependency use after Android compatibility verification.
- `go-routeros/routeros` — MIT; useful for async/listen semantics.
- `tikoci/restraml` — Unlicense; useful for RouterOS version-aware schema generation.
- `nasnet-community/nasnet-panel` — MIT; useful for multi-protocol and module decomposition.
- `2GT-Media-Group-LLC/mikrotik-manager` — AGPL-3.0; feature/safety reference only unless the project deliberately adopts compatible licensing.

No third-party source is copied without a license review and attribution decision.
