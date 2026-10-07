#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-proof
adb shell wm size 720x1600
adb shell wm density 320
adb shell settings put system font_scale 1.0
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Real Android SQLite and Compose actions; isolated CI emulator only.
adb shell am instrument -w -r com.fgmachines.mikrotikmanager.test/androidx.test.runner.AndroidJUnitRunner > ui-proof/business-instrumentation.txt
cat ui-proof/business-instrumentation.txt
if ! grep -q 'OK (55 tests)' ui-proof/business-instrumentation.txt; then exit 1; fi
for file in connection-discovery-ar.png connection-discovery-en.png connection-fixed-ar.png connection-fixed-en.png network-devices-en.png network-devices-ar.png dns-protection-en.png dns-protection-ar.png access-login-ar.png access-accounts-en.png access-read-only-en.png business-en.png business-ar.png business-subscribers-ar.png business-tools-en.png business-plans-ar.png business-invoices-ar.png business-reports-ar.png business-sales-ar.png router-center-en.png team-wallet-en.png voucher-archive-en.png; do
  adb exec-out run-as com.fgmachines.mikrotikmanager cat "files/$file" > "ui-proof/$file"
  test -s "ui-proof/$file"
done
adb logcat -d -s AndroidRuntime:E > ui-proof/business-runtime.log
python3 - <<'PYCRASH'
from pathlib import Path
log=Path('ui-proof/business-runtime.log').read_text(errors='replace')
blocks=log.split('FATAL EXCEPTION')
app_crashes=[b for b in blocks[1:] if 'Process: com.fgmachines.mikrotikmanager' in b[:2500]]
if app_crashes:
    print('FG MTM application crash detected during device tests')
    print('FATAL EXCEPTION'+app_crashes[-1])
    raise SystemExit(1)
PYCRASH
