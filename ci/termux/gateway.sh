# Starts the gateway the way TermuxBridge.startGateway() does, then probes it like the app.
set -x
export HERMES_HOME="${HERMES_HOME:-$HOME/.hermes}"
mkdir -p "$HERMES_HOME/logs" "$HERMES_HOME/web_dist_placeholder/assets"
echo '<!doctype html><title>Hermes2</title>' > "$HERMES_HOME/web_dist_placeholder/index.html"
export HERMES_DASHBOARD_SESSION_TOKEN="citoken123"
export HERMES_WEB_DIST="$HERMES_HOME/web_dist_placeholder"
HERMES_CMD="$PREFIX/bin/hermes"
for c in "$PREFIX/lib/hermes-agent/bin/hermes"; do [ -x "$c" ] && HERMES_CMD="$c"; done
ls -la "$HERMES_CMD"
"$HERMES_CMD" --version
nohup "$HERMES_CMD" dashboard --host 127.0.0.1 --port 9119 --no-open --skip-build \
    > "$HERMES_HOME/logs/gateway_stdout.log" 2>&1 &
PID=$!
ok=0
for i in $(seq 1 90); do
    kill -0 $PID 2>/dev/null || { echo "DASHBOARD EXITED after ${i}s"; break; }
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:9119/ || true)
    if [ "$code" != "000" ]; then echo "HTTP / -> $code after ${i}s"; ok=1; break; fi
    sleep 1
done
if [ $ok = 1 ]; then
    echo "--- websocket handshake"
    curl -si --http1.1 --max-time 5 -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
        -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
        "http://127.0.0.1:9119/api/ws?token=citoken123" | head -5
fi
echo "--- gateway_stdout.log"
cat "$HERMES_HOME/logs/gateway_stdout.log"
