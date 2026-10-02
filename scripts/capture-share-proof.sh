#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-proof
package=com.fgmachines.mikrotikmanager
adb shell wm size 720x1600
adb shell wm density 320
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent 224
adb shell wm dismiss-keyguard
adb logcat -c
open_share() {
  adb shell am force-stop "$package"
  adb shell am start -n "$package/.MainActivity" --ez ui_demo true --es demo_screen voucher-share
  sleep 3
}
tap_text() {
  adb shell uiautomator dump /sdcard/action.xml
  adb pull /sdcard/action.xml ui-proof/action.xml
  read -r action_x action_y < <(python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
root=ET.parse('ui-proof/action.xml').getroot()
parents={child:parent for parent in root.iter() for child in parent}
label=next(n for n in root.iter('node') if n.get('text')==sys.argv[1])
node=label
while node.get('clickable')!='true' and node in parents: node=parents[node]
assert node.get('clickable')=='true'
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PY
  )
  adb shell input tap "$action_x" "$action_y"
}
assert_chooser() {
  sleep 4
  adb shell dumpsys activity activities > ui-proof/chooser-activity.txt
  adb exec-out screencap -p > "ui-proof/$1.png"
  adb shell uiautomator dump /sdcard/after-share.xml
  adb pull /sdcard/after-share.xml "ui-proof/$1.xml"
  adb logcat -d -s AndroidRuntime:E ActivityTaskManager:I > ui-proof/share-runtime.log
  grep -Eq '(mResumedActivity|topResumedActivity).*ChooserActivity' ui-proof/chooser-activity.txt
  echo "System Android chooser opened for $1"
}
open_share
tap_text 'نص'
assert_chooser share-text-chooser
open_share
tap_text 'صورة + QR'
assert_chooser share-image-chooser
shared_file=$(adb shell run-as "$package" ls cache/exports | tr -d '\r' | grep -E '^voucher-.*\.png$' | tail -n 1)
test -n "$shared_file"
adb exec-out run-as "$package" cat "cache/exports/$shared_file" > ui-proof/shared-voucher.png
python3 - <<'PY'
import struct
from pathlib import Path
v=Path('ui-proof/shared-voucher.png').read_bytes()
assert v[:8]==b'\x89PNG\r\n\x1a\n'
assert struct.unpack('>II',v[16:24])==(900,720)
print('Actual shared voucher PNG exported: 900x720')
PY

# With enlarged font, verify the whole footer can be reached by scrolling.
adb shell wm size 720x1280
adb shell settings put system font_scale 1.3
adb shell am force-stop "$package"
adb shell am start -n "$package/.MainActivity" --ez ui_demo true --es demo_screen login
sleep 4
adb shell input swipe 360 1030 360 250 400
sleep 2
adb exec-out screencap -p > ui-proof/login-large-font-footer.png
adb shell uiautomator dump /sdcard/footer.xml
adb pull /sdcard/footer.xml ui-proof/footer.xml
python3 - <<'PY'
import re,xml.etree.ElementTree as ET
root=ET.parse('ui-proof/footer.xml').getroot()
parents={child:parent for parent in root.iter() for child in parent}
for title in ['اتصال بالراوتر','إنشاء كروت بدون راوتر']:
    labels=[n for n in root.iter('node') if n.get('text')==title]
    found=False
    for label in labels:
        node=label
        while node.get('clickable')!='true' and node in parents: node=parents[node]
        if node.get('clickable')!='true': continue
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        assert y2<=1216 and y2-y1>=80, f'Footer button clipped: {title}'
        found=True
    assert found,f'Cannot reach {title} with larger font'
print('Large-font connect and offline buttons remain reachable above navigation')
PY
adb shell settings put system font_scale 1.0
adb logcat -d -s AndroidRuntime:E > ui-proof/android-runtime.log
if grep -q 'FATAL EXCEPTION' ui-proof/android-runtime.log; then cat ui-proof/android-runtime.log; exit 1; fi
