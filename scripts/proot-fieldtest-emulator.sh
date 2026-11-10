#!/usr/bin/env bash
# Runs ProotFieldTest on the booted emulator and keeps its logcat lines (tag PFT).
set -uo pipefail
OUT=build/proot-fieldtest
mkdir -p "$OUT"
adb logcat -c
adb logcat -v time -s PFT:I > "$OUT/pft.log" 2>&1 &
LOGCAT=$!
./gradlew :app:connectedDebugAndroidTest -Phermes.x86_64=true --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hermes.android.runtime.linux.ProotFieldTest
RC=$?
sleep 3
kill $LOGCAT 2>/dev/null
adb logcat -d -v time > "$OUT/logcat-full.log" 2>&1
grep -iE 'proot|F DEBUG|SIGSEGV|SIGABRT|avc: denied' "$OUT/logcat-full.log" | grep -v ' PFT' | tail -200 > "$OUT/crashes-and-denials.log"
echo "gradle rc=$RC" >> "$OUT/pft.log"
exit 0
