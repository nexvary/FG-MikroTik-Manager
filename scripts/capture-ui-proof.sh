#!/usr/bin/env bash
set -euo pipefail

mkdir -p ui-proof
adb shell wm size 720x1600
adb shell wm density 320
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent 224
adb shell wm dismiss-keyguard
adb logcat -c

capture() {
  local screen="$1" file="$2"
  adb shell am force-stop com.fgmachines.mikrotikmanager
  adb shell am start -n com.fgmachines.mikrotikmanager/.MainActivity --ez ui_demo true --es demo_screen "$screen"
  sleep 6
  test -n "$(adb shell pidof com.fgmachines.mikrotikmanager)"
  adb exec-out screencap -p > "ui-proof/$file.png"
  test -s "ui-proof/$file.png"
}

capture menu 01-main-menu
capture network 02-network
capture system 03-system-security
capture admin-users 04-admin-users
capture vouchers 05-vouchers
capture about 06-about
capture commands 07-command-center
for screen in routes active-vouchers portal-login portal-status advanced readiness doctor wizard repair backup; do
  capture "$screen" "$screen"
done

adb shell wm size 1080x2400
adb shell wm density 480
capture vouchers vouchers-1080
adb logcat -d -s AndroidRuntime:E > ui-proof/android-runtime.log
if rg -q 'FATAL EXCEPTION' ui-proof/android-runtime.log; then
  cat ui-proof/android-runtime.log
  exit 1
fi
