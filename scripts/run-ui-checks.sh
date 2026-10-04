#!/usr/bin/env bash
set -euo pipefail
# Re-run the entire device suite for test-only changes without repeating the unchanged visual matrix.
# This fast path is gated by an exact comparison of all application sources and build settings.
if git log -1 --format=%B | grep -q '\[device-regression\]'; then
  baseline=7c3f3f595baad0f6d7d4f4e349e41fff22aecf64
  git fetch --depth=1 origin "$baseline"
  git diff --exit-code "$baseline" HEAD -- app/src/main app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle.properties gradle
  mkdir -p ui-proof
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell input keyevent 224
  adb shell wm dismiss-keyguard
  adb logcat -c
else
  bash scripts/capture-ui-proof.sh
fi
bash scripts/test-business-device.sh
