# FG MTM Qt Desktop 0.3.0

C++20, Qt 6.8.3 Quick/QuickControls2/Network, QML, CMake, MSVC 2022 and Inno Setup: the applicable desktop technologies verified in nexvary/NEXVARY-Avionics-Lab main at ba0c203f55755aab1d22eb0af0cd8bd43f163a9d. WebEngine is unnecessary for the current FG workspace.

Android palette is copied from app/.../ui/Theme.kt, not guessed from screenshots. Arabic RTL/English, resizable desktop layout, Back history, consistent vector navigation icons and independent floating terminal window (F4). Installer contains a product introduction and the FG Machines icon. Existing verified WPF edition is preserved.

Implemented: FG Server session/identity verification, logout, 17 business tables, search, pagination, formula-safe CSV, server diagnostics, RADIUS reads, RouterOS 7 HTTPS REST reads with redaction, organized print commands in a floating terminal. Session/password memory only, HTTPS validation and no redirect following. Large money values are represented as strings in QML and remain exact in export.

## Parity gate — unfinished

Android UI inventory: RouterApp/RouterAdminScreen, AdvancedSetupScreen, HotspotToolsScreen, DnsProtectionScreen, VoucherStudioScreen/VoucherArchiveScreen, BusinessScreen/BusinessAccessScreen/BusinessCloudScreen/BusinessToolsScreen/TeamScreen, AboutDeveloperScreen and CommandCenterSheet.

Still requires porting and acceptance tests: RouterOS 6/7 binary API and discovery; guided HotSpot setup/preview/backup/repair/rollback; subscriber enable/disable/renewal/top-up/profile/kick; bulk vouchers/QR/PDF/thermal print/archive; portal design/installation/restoration; local commerce writes/receipts/invoices/payments/voids/expenses; roles/team/audit/replication conflicts; multi-router monitoring/alerts; DNS protection; advanced write commands and developer/About content. These are not advertised as completed. Terminal currently supports allowlisted print reads, not a complete RouterOS scripting shell.

Windows CI: C++ contract checks, packaged launch and installed launch, screenshot. Physical router and user-server commissioning are separate. Not release-ready until the full parity gate passes.
