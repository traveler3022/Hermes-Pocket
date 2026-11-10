#!/usr/bin/env bash
# One e2e job on the booted emulator: install the APK, start the mock model, run the suites.
set -u
SUITES="$1"
EXTRA="${2:-}"
OUT="${E2E_OUT:-e2e-out}"
mkdir -p "$OUT"
adb wait-for-device
adb root >/dev/null 2>&1 || true
sleep 3
adb wait-for-device
# The upgrade job starts on the last release (BASE_APK) and installs APK over it later.
adb install -r -g "${BASE_APK:-${APK:-apk/app-debug.apk}}" || { echo "APK install failed"; exit 0; }
nohup python3 e2e/mock_llm.py --port 18700 --log "$OUT/mock_llm.jsonl" > "$OUT/mock_llm.out" 2>&1 &
sleep 2
curl -s localhost:18700/v1/models | head -c 120; echo
python3 e2e/run.py --suites "$SUITES" --out "$OUT" $EXTRA 2>&1 | tee "$OUT/run.txt"
adb exec-out run-as com.hermes.android.debug sh -c 'cat files/linux/rootfs/root/.hermes/logs/gateway_stderr.log 2>/dev/null' > "$OUT/gateway_stderr.log" 2>&1 || true
adb shell dumpsys meminfo com.hermes.android.debug > "$OUT/meminfo.txt" 2>&1 || true
exit 0
