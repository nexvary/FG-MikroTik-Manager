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
if ! grep -q 'OK (8 tests)' ui-proof/business-instrumentation.txt; then exit 1; fi
for file in business-en.png business-ar.png business-subscribers-ar.png; do
  adb exec-out run-as com.fgmachines.mikrotikmanager cat "files/$file" > "ui-proof/$file"
  test -s "ui-proof/$file"
done
adb logcat -d -s AndroidRuntime:E > ui-proof/business-runtime.log
if grep -q 'FATAL EXCEPTION' ui-proof/business-runtime.log; then exit 1; fi
