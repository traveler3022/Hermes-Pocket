#!/usr/bin/env bash
set -uo pipefail

OUT="build/exhaustive-validation"
mkdir -p "$OUT"

# Keep instrumentation bounded, but do not let its failure prevent diagnostics
# and the independent cold-start probe from running.
if timeout --signal=TERM --kill-after=30s 10m ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace > "$OUT/instrumentation-gradle.log" 2>&1; then
  instrumentation_rc=0
else
  instrumentation_rc=$?
fi
cat "$OUT/instrumentation-gradle.log"

adb devices -l > "$OUT/adb-devices-after-tests.txt" 2>&1 || true
adb logcat -d -v threadtime > "$OUT/instrumentation-logcat.txt" 2>&1 || true

# connectedDebugAndroidTest uninstalls the app when it finishes, so the launch probe below
# found no app ("Activity class ... does not exist") on every run. Put the built APK back.
if ! adb install -r app/build/outputs/apk/debug/app-debug.apk > "$OUT/reinstall.txt" 2>&1; then
  echo "::error::Could not reinstall the debug APK for the launch probe (see reinstall.txt)."
  cat "$OUT/reinstall.txt"
fi

adb logcat -c || true
adb shell am force-stop com.hermes.android.debug || true
if adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.hermes.android.debug/com.hermes.android.MainActivity > "$OUT/cold-start.txt" 2>&1; then
  startup_rc=0
else
  startup_rc=$?
fi
sleep 12

if adb shell pidof com.hermes.android.debug > "$OUT/app-pid.txt" 2>&1 && [[ -s "$OUT/app-pid.txt" ]]; then
  :
else
  echo "::error::Hermes Pocket did not remain alive after launch."
  startup_rc=1
fi

adb shell dumpsys meminfo com.hermes.android.debug > "$OUT/memory-after-launch.txt" 2>&1 || true
adb logcat -d -v threadtime > "$OUT/emulator-logcat.txt" 2>&1 || true
adb shell screencap -p /sdcard/hermes-pocket-validation.png >/dev/null 2>&1 || true
adb pull /sdcard/hermes-pocket-validation.png "$OUT/" >/dev/null 2>&1 || true

if grep -E 'FATAL EXCEPTION|ANR in com\.hermes\.android\.debug' "$OUT/emulator-logcat.txt"; then
  echo "::error::Fatal exception or ANR observed after logcat was cleared."
  startup_rc=1
fi

{
  echo "# Device probe"
  echo
  echo "- Instrumentation exit code: $instrumentation_rc"
  echo "- App launch probe exit code: $startup_rc"
  echo "- Process visible after 12 seconds: $([[ -s "$OUT/app-pid.txt" ]] && echo yes || echo no)"
  echo "- Startup timing: cold-start.txt"
  echo "- Memory snapshot: memory-after-launch.txt; one snapshot is not a leak/stress conclusion"
} > "$OUT/device-probe.md"

if [[ $instrumentation_rc -ne 0 || $startup_rc -ne 0 ]]; then
  exit 1
fi
