#!/bin/sh
# apk, run for this Linux by the Hermes app (GuestApkBridge.kt). Android denies apps hard links,
# and apk makes them to save its database and for packages that ship them (glibc does). Only a
# proot the app starts can have link2symlink, which turns those into copies and symlinks; the
# rest of this Linux runs without it, because uv breaks under it. The app writes this file on
# every start, so edits here are lost. HERMES_APK_DIRECT=1 or /usr/sbin/apk runs apk directly.
REAL=/usr/sbin/apk
[ -x "$REAL" ] || REAL=/sbin/apk
[ -n "$HERMES_APK_DIRECT" ] && exec "$REAL" "$@"

# Only commands that write go to the app; info, search, list and the like read and run here.
case " $* " in
    *" add "* | *" del "* | *" fix "* | *" upgrade "* | *" update "* | *" cache "* | *" fetch "* | \
    *" index "* | *" mkndx "* | *" mkpkg "* | *" extract "* | *" convdb "* | *" convndx "* | *" adbsign "*) ;;
    *) exec "$REAL" "$@" ;;
esac

Q=/run/hermes-apk
id="$$-$(date +%s)"
req="$Q/$id"
mkdir -p "$req" 2>/dev/null || exec "$REAL" "$@"
printf '%s\0' "$@" > "$req/args"
pwd > "$req/cwd"
echo $$ > "$req/pid"
: > "$req/out"
# Ctrl-C or a kill stops the apk the app runs, as it would stop one running here.
trap '[ -f "$req/apkpid" ] && kill -INT "$(cat "$req/apkpid")" 2>/dev/null; : > "$req/cancel"; exit 130' INT TERM HUP
: > "$Q/$id.ready"

# The app takes a request at once. If it doesn't answer, apk runs here as it did before.
i=0
while [ ! -f "$req/taken" ]; do
    i=$((i + 1))
    if [ $i -gt 25 ]; then
        rm -rf "$req" "$Q/$id.ready"
        echo "apk: the Hermes app did not answer; running apk directly (its database may not be saved)" >&2
        exec "$REAL" "$@"
    fi
    sleep 0.2
done
app=$(cat "$req/taken")

off=0
while :; do
    fin=0
    [ -f "$req/rc" ] && fin=1
    size=$(wc -c < "$req/out")
    if [ "$size" -gt "$off" ]; then
        tail -c +$((off + 1)) "$req/out" | head -c $((size - off))
        off=$size
    fi
    [ $fin = 1 ] && break
    if [ -n "$app" ] && [ ! -d "/proc/$app" ]; then
        echo "apk: the Hermes app stopped while apk was running" >&2
        rm -rf "$req"
        exit 1
    fi
    sleep 0.2
done
rc=$(cat "$req/rc")
rm -rf "$req"
exit "${rc:-1}"
