# FG MTM 0.8.6 — explicit secure API connections

Continues 0.8.5 on dev/product-family. Adds API-SSL to the existing transport and repository, plus a collapsed AR/EN connection-settings panel with AUTO, API, API-SSL and HTTPS and validated custom ports. Selecting a protocol resets the port to its standard value. AUTO retains its existing API/HTTPS negotiation; explicit TLS never enters AUTO or falls back to plaintext.

TLS uses the platform trust store, endpoint identity validation, and TLS 1.2/1.3. The handshake completes before RouterOS credentials are written. Routers need a certificate trusted by the app platform and matching the entered DNS name or IP SAN. Self-signed, expired or mismatched certificates are rejected; no trust-all option or anonymous cipher mode. No CA import UI in this stage. Port 8729 is the default and can be changed.

The existing read reconnect / no mutation replay / explicit close rules are retained. Tests use a local TLS fixture with a test CA: accepted matching certificate, untrusted chain, mismatched host, expired certificate, and plaintext endpoint. None prove interoperability with a physical RouterOS router. UI proof opens and selects TLS in both languages.

Application ID unchanged; versionCode 15. Existing debug-signing incompatibility remains unresolved. Do not uninstall an existing app or claim this debug APK is a compatible upgrade. Release signing recovery, business database, financial workflows, Windows and FG Server remain outstanding.

References: https://manual.mikrotik.com/docs/developer-guides/api/ and https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html
