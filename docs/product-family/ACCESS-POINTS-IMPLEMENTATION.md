# Access point observation implementation

Baseline: `412ac711ad95343861f5876b9532c13a1188e1e1`, branch `dev/product-family`, version 0.17.4.

| Capability | Android | Qt Windows |
|---|---|---|
| Read neighbor, ARP, DHCP, bridge hosts, Ethernet, HotSpot active/hosts | Implemented | Implemented |
| Conservative classification with LLDP enabled capability evidence | Implemented | Implemented |
| MAC deduplication, reject multicast/local bridge entries | Implemented | Implemented |
| Local shop name and explicit user-verified AP / mode mapping | Implemented | Implemented |
| Inferred client association with ambiguous-port / known NAT exclusion | Implemented | Implemented |
| Router and business scope isolation; SQLite observations, 90-day retention | Implemented | Implemented |
| 30-second cached refresh; sequential read-only collection | Implemented | Implemented |
| Custom period comparison of observed clients/accounts | Implemented | Implemented |
| CSV / PDF / XLSX exports | Implemented; needs device verification | Implemented; needs export validation |
| Historical complete sessions / voucher usage / sales attribution | Pending | Pending |
| Traffic deltas / peak hours / growth / anomaly alerts | Pending | Pending |
| Hardware field verification | Pending | Pending |

## Evidence contract

An ARP, DHCP or forwarding-table entry never proves an AP. A configurable hostname never proves hardware type. Enabled LLDP WLAN capability confirms an advertised role; LLDP capabilities plus platform are only likely. User verification is labeled separately. No MAC vendor guessing is used.

An upstream physical interface is not proof of a direct cable. Multiple physical paths produce Unknown port. One candidate AP on the client's forwarding path produces **Inferred**, never Confirmed, association. Known NAT mappings are excluded. Unknown mode does not prove bridge mode. A silent AP cannot be classified Offline from its absence in neighbor discovery.

Only manually requested, locally observed samples are stored. The application does not claim continuously measured historical sessions. Repeated sample rows are not independent sessions. Accounts are not proven voucher cards. No revenue is calculated by multiplying sessions or logins by a price. Unimplemented or unavailable measures remain N/A. Export placeholders explain their absence.

SQLite is separate from the existing business database, does not migrate or delete business data, and stores no credentials. CSV neutralizes formula-like strings; XLSX uses inline strings, not formulas. Uninstallation preserves data.

Sources: RouterOS Neighbor discovery documentation, https://help.mikrotik.com/docs/spaces/ROS/pages/24805517/Neighbor+discovery (reviewed 2026-10-07); existing repository transport and authorization implementations.

This is an implementation checkpoint, not completion of the full product brief.
