# FG MTM 0.8.0 — phone UI and HotSpot voucher lifecycle

Based on `dev/phase-1-foundation` at `5c44c6c7358bd7fbf838da1ab8937c09a4240eda`. Existing MNDP discovery, Auto transport, RouterOS API and encrypted voucher history remain in place.

## Changes

- Compact shared header, navy/blue design, balanced admin actions, readable numeric voucher inputs, Wi-Fi/wireless fallback, visible administrator creation and Command Center. Sensitive admin/router changes require confirmation.
- HotSpot provisioning creates real users with profile, usage allowance, data limit, batch ID and optional absolute-expiry scheduler. Partial batches report each failure; PDF generation never counts as router activation.
- Active vouchers read user and active-session data from RouterOS. Enable/disable, disconnect, delete, usage-time extension and profile changes use the existing authenticated transport and refresh afterward. Reprint/QR use this phone's encrypted archive; passwords unavailable in that archive are not guessed.
- Portal design includes network name, support, welcome, logo, color, terms and HTTPS website link. Installation uploads into a new directory, verifies contents and saves the old profile directory settings before switching. Existing portal files are preserved.
- Mobile Arabic/English login, CHAP/HTTPS POST authentication, session status and logout. No router management credentials or RouterOS API in customer assets. HTTP PAP is refused.
- QR opens the configured portal with a username fragment, immediately removes that fragment from browser history and prefills the code. Different voucher passwords remain manual. A code used as its own password is a bearer credential: protect the printed card/QR; scanner apps may retain it. No separate password is put in query parameters. PPPoE QR is a setup card; offline output is explicitly unactivated.
- A4 multi-card PDF, thermal-size PDF and PNG exports. Usage duration and absolute expiry have separate controls.
- HotSpot wizard summarizes and confirms changes, creates pool/profile/optional DHCP/server, and rolls back newly created items on failure. Advanced Setup detects client interfaces, proposes a nonoverlapping subnet, creates missing gateway/pool/DHCP/HotSpot/profile/portal settings, and adds scoped client NAT only if none serves that subnet. Existing WAN, administrators and working NAT remain unchanged. Upstream internet configuration is not guessed. Use a separate management connection because enabling HotSpot can interrupt the phone's guest connection.

## Advanced Setup

The main menu starts with Advanced Setup; router status is inside its compact health summary. Readiness/Network Doctor/client checks inspect real tables and live ping outcomes. Missing permissions and unmeasured values are unknown, not fabricated scores. ICMP failure does not establish an internet outage because ICMP may be filtered.

Reviewed plans reject WAN selection, overlapping subnets, invalid pools, and configurations changed after preview. An encrypted backup must be verified before applying changes. Backup passwords and the change journal are protected with Android Keystore AES-GCM. The wizard reuses existing items; applying a second plan to an already configured network does not duplicate them. Sensitive manual manager changes also require backup and confirmation.

Backup/export/list/restore, DNS cache flush, DNS repair, administrator and Command Center shortcuts are wired to the existing transport and screens. Restart and restore warn about disconnection. High-risk firewall/WAN/bridge repairs remain explicit manual operations, rather than a speculative automatic rewrite. Firewall drop rules are flagged for review; their presence alone is not called a proven conflict.

Voucher Studio performs readiness preflight. It blocks HotSpot activation when configuration is not verified and opens Advanced Setup; Offline creation remains available.

## Validation

One final aggregate run after changes: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — PASS, 47 unit tests, zero failures; lint zero errors, four warnings (newer Activity dependency available, modifier placement, JavaScript for local portal preview, backup rules), two informational hints; debug APK built.

Isolated RouterOS CHR 7.19.1, using the app's actual API transport/repository/HotspotManager classes:

- Wizard setup: PASS.
- Portal upload/content verification/rollback pointer: PASS.
- Two real `/ip/hotspot/user` records with usage and 100 MiB total limit: PASS.
- Add 30 minutes changes actual `limit-uptime` from 1h to 1h30m: PASS.
- Disable/enable: PASS.
- Total data remaining from RouterOS `remain-bytes-total`: 104857600 B verified on the served status page.
- Customer HTTP CHAP login: PASS; served status identifies the voucher and reports 1h30m remaining; logout: PASS.
- Absolute expiry scheduler disables the voucher on RouterOS 7 and removes itself: PASS.
- Delete: PASS.
- Advanced Setup creates a new third-interface gateway, pool, DHCP, scoped NAT, HotSpot profile/server and portal after verifying an AES-encrypted backup: PASS.
- Second plan makes no duplicate changes: PASS.
- Configuration export and backup inventory: PASS.
- Restore accepted, RouterOS rebooted, and the new third-interface configuration was removed as expected: PASS.
- Browser QR prefill, immediate fragment removal, JavaScript CHAP authentication, status and logout: PASS.
- Deep internet probe reported 3 sent/0 received in the isolated lab; internet access is not claimed from this evidence.
- MD5 implementation: matches standard empty, ASCII and binary-challenge vectors.

Physical Wi-Fi/captive-portal auto-launch, MNDP broadcast on a real LAN, User Manager/RADIUS configuration, printer hardware and compatibility with an existing production APK signing key are outside this isolated lab's evidence. User Manager availability is checked and unavailable configurations receive a HotSpot alternative. The deliverable is a debug-signed test APK, not a production signing-key release.
