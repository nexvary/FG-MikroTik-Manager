#!/usr/bin/env python3
"""Wait for FG MTM UI; dismiss only the known emulator Quickstep ANR dialog."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

APP = 'com.fgmachines.mikrotikmanager'


def quickstep_close(root):
    labels = [n.get('text', '') for n in root.iter('node')]
    if not any(t in ("Quickstep isn't responding", 'Quickstep isn’t responding') for t in labels):
        return None
    parents = {c: p for p in root.iter() for c in p}
    for node in root.iter('node'):
        if node.get('resource-id') == 'android:id/aerr_close' or node.get('text') == 'Close app':
            while node.get('clickable') != 'true' and node in parents:
                node = parents[node]
            if node.get('clickable') == 'true':
                bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                    return (bounds[0]+bounds[2])//2, (bounds[1]+bounds[3])//2
    return None


def adb(*args):
    return subprocess.run(['adb', *args], check=True, capture_output=True)


def snapshot(destination, timeout=30):
    path = Path(destination)
    path.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + timeout
    dismissed = False
    last_error = None
    while time.monotonic() < deadline:
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/fg-proof.xml')
            adb('pull', '/sdcard/fg-proof.xml', str(path))
            root = ET.parse(path).getroot()
        except (subprocess.CalledProcessError, ET.ParseError) as error:
            last_error = error
            time.sleep(1)
            continue
        close = quickstep_close(root)
        if close:
            if dismissed:
                raise RuntimeError('Quickstep ANR repeated; emulator is unhealthy')
            print('Dismissing known emulator Quickstep ANR; application errors remain fatal', flush=True)
            adb('shell', 'input', 'tap', str(close[0]), str(close[1]))
            dismissed = True
            time.sleep(1)
            continue
        # Do not dismiss ANRs belonging to FG MTM or any unknown system dialog.
        if any('responding' in n.get('text', '') for n in root.iter('node')):
            raise RuntimeError('Unexpected ANR dialog; inspect ' + str(path))
        if any(n.get('package') == APP and n.get('bounds') != '[0,0][0,0]' for n in root.iter('node')):
            return
        time.sleep(1)
    raise RuntimeError(f'FG MTM did not become visible: {path}; {last_error}')


if __name__ == '__main__':
    snapshot(sys.argv[1])
