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
# Open the real connection settings and select TLS; do not connect to a router.
for language in login login-en; do
  capture "$language" "tls-$language-before"
  adb shell input swipe 360 1200 360 400 400
  sleep 1
  adb shell uiautomator dump /sdcard/tls.xml
  adb pull /sdcard/tls.xml ui-proof/tls.xml
  read -r tx ty < <(python3 - <<'PYTAP'
import xml.etree.ElementTree as ET, re
root=ET.parse('ui-proof/tls.xml').getroot()
node=next(n for n in root.iter('node') if ' • AUTO' in n.get('text',''))
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PYTAP
)
  adb shell input tap "$tx" "$ty"
  sleep 1
  adb shell input swipe 360 1250 360 350 400
  sleep 1
  adb shell uiautomator dump /sdcard/tls.xml
  adb pull /sdcard/tls.xml ui-proof/tls.xml
  read -r tx ty < <(python3 - <<'PYTLS'
import xml.etree.ElementTree as ET, re
root=ET.parse('ui-proof/tls.xml').getroot()
node=next(n for n in root.iter('node') if n.get('text') == 'API-SSL • TLS')
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PYTLS
)
  adb shell input tap "$tx" "$ty"
  sleep 1
  adb shell input swipe 360 1250 360 600 400
  sleep 1
  adb shell uiautomator dump /sdcard/tls.xml
  adb pull /sdcard/tls.xml "ui-proof/tls-$language.xml"
  grep -q '8729' "ui-proof/tls-$language.xml"
  adb exec-out screencap -p > "ui-proof/tls-$language.png"
done
capture menu 01-main-menu
capture network 02-network
capture system 03-system-security
capture admin-users 04-admin-users
capture vouchers 05-vouchers
capture about 06-about
capture portal-design portal-design
capture commands 07-command-center
capture command-library command-library
capture voucher-share voucher-share
for screen in routes active-vouchers portal-login portal-status advanced readiness doctor wizard repair backup; do
  capture "$screen" "$screen"
done

# Exercise a 360x640dp phone, both directions and actual IME insets.
adb shell wm size 720x1280
adb shell wm density 320
capture login-en login-en-small
capture login login-small
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell input swipe 360 950 360 300 400
adb shell uiautomator dump /sdcard/login-focus.xml
adb pull /sdcard/login-focus.xml ui-proof/login-focus.xml
read -r focus_x focus_y < <(python3 - <<'PYFOCUS'
import xml.etree.ElementTree as ET, re
root = ET.parse('ui-proof/login-focus.xml').getroot()
field = next(n for n in root.iter('node') if n.get('password') == 'true')
x1,y1,x2,y2 = map(int,re.findall(r'\d+',field.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PYFOCUS
)
adb shell input tap "$focus_x" "$focus_y"
sleep 3
adb shell input swipe 360 530 360 200 400
sleep 2
adb exec-out screencap -p > ui-proof/login-keyboard-small.png
adb shell uiautomator dump /sdcard/login-keyboard.xml
adb pull /sdcard/login-keyboard.xml ui-proof/login-keyboard.xml
python3 - <<'PYKEYBOARD'
import xml.etree.ElementTree as ET, re
root=ET.parse('ui-proof/login-keyboard.xml').getroot()
parents={child:parent for parent in root.iter() for child in parent}
buttons=[]
for label in root.iter('node'):
    if label.get('text') != 'اتصال بالراوتر': continue
    node=label
    while node.get('clickable') != 'true' and node in parents: node=parents[node]
    if node.get('clickable') == 'true': buttons.append(node)
assert buttons, 'Connect button cannot be reached while typing'
x1,y1,x2,y2=map(int,re.findall(r'\d+',buttons[0].get('bounds')))
assert y2 <= 1216 and y2-y1 >= 80, 'Connect button clipped or behind system navigation'
print('Small phone connect button reachable with keyboard open', buttons[0].get('bounds'))
PYKEYBOARD
adb shell input keyevent 4
adb shell settings put system font_scale 1.3
capture login login-font-scale-small
adb shell settings put system font_scale 1.0

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
root = ET.parse('ui-proof/portal-design.xml').getroot()
parents = {child: parent for parent in root.iter() for child in parent}
label = next(n for n in root.iter('node') if 'تثبيت صفحة HotSpot' in n.get('text', ''))
button = label
while button.get('clickable') != 'true' and button in parents:
    button = parents[button]
assert button.get('clickable') == 'true', 'No accessible install button'
x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
assert y2 <= 2256, f'Install button overlaps system navigation: {button.attrib}'
assert y2 - y1 >= 120 and x2 > x1, 'Install button has no usable touch area'
print('Portal install button remains above three-button system navigation')
PYTEST
adb logcat -d -s AndroidRuntime:E > ui-proof/android-runtime.log
if grep -q 'FATAL EXCEPTION' ui-proof/android-runtime.log; then
  cat ui-proof/android-runtime.log
  exit 1
fi
