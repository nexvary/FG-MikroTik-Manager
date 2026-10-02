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

capture login 00-login
capture menu 01-main-menu
capture network 02-network
capture system 03-system-security
capture admin-users 04-admin-users
capture vouchers 05-vouchers
capture about 06-about
capture portal-design portal-design
capture commands 07-command-center
for screen in routes active-vouchers portal-login portal-status advanced readiness doctor wizard repair backup; do
  capture "$screen" "$screen"
done

adb shell wm size 1080x2400
adb shell wm density 480
capture vouchers vouchers-1080
capture login login-1080
capture about about-1080
capture portal-design portal-design-1080
adb shell uiautomator dump /sdcard/portal-design.xml
adb pull /sdcard/portal-design.xml ui-proof/portal-design.xml
python3 - <<'PYTEST'
import xml.etree.ElementTree as ET, re
nodes = ET.parse('ui-proof/portal-design.xml').getroot().iter('node')
button = next(n for n in nodes if 'تثبيت صفحة HotSpot' in n.get('text', ''))
x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
assert y2 <= 2256, f'Install button overlaps system navigation: {button.attrib}'
assert y2 - y1 >= 60 and x2 > x1, 'Install button has no usable touch area'
print('Portal install button remains above three-button system navigation')
PYTEST
adb logcat -d -s AndroidRuntime:E > ui-proof/android-runtime.log
if rg -q 'FATAL EXCEPTION' ui-proof/android-runtime.log; then
  cat ui-proof/android-runtime.log
  exit 1
fi
