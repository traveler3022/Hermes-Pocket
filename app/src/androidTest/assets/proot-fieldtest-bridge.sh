# GuestApkBridge on Android: apk from a shell without link2symlink (as the agent and the terminal
# run), handed to the app. One RESULT line per check.
r() { echo "RESULT $*"; }
q() { ls /run/hermes-apk 2>/dev/null | wc -l; }
n() { c=0; for p in /proc/[0-9]*; do case "$(cat $p/cmdline 2>/dev/null | tr '\0' ' ')" in "/usr/sbin/apk add"*) c=$((c+1));; esac; done; echo $c; }
echo "wrapper: $(command -v apk)"
apk del -q jq tree >/dev/null 2>&1

apk add jq tree >/tmp/b.out 2>&1; rc=$?
r bridge-add rc=$rc in-db=$(apk info -e jq tree >/dev/null 2>&1; echo $?) runs=$(jq --version >/dev/null 2>&1 && tree --version >/dev/null 2>&1; echo $?) dberr=$(grep -c 'failed to write database' /tmp/b.out)
[ $rc = 0 ] || tail -5 /tmp/b.out

apk add no-such-pkg-xyz >/tmp/b.out 2>&1; rc=$?
r bridge-bad-package rc=$rc message=$(grep -c 'no such package' /tmp/b.out)

cd /tmp && rm -f jq-*.apk && apk fetch -q jq; rc=$?; r bridge-fetch-in-cwd rc=$rc file=$(ls /tmp/jq-*.apk 2>/dev/null | wc -l); cd /

b=$(q); apk info -e busybox >/dev/null; rc=$?; r bridge-read-only-local rc=$rc queue-delta=$(( $(q) - b ))

apk fix glibc >/tmp/b.out 2>&1; rc=$?
r bridge-fix-glibc rc=$rc dberr=$(grep -c 'failed to write database' /tmp/b.out) getconf=$(ls /usr/libexec/getconf | wc -l) python-after=$(python3 -c 'print(1)' 2>&1)
[ $rc = 0 ] || tail -5 /tmp/b.out

apk add -q jq & a=$!; apk add -q tree & t=$!; wait $a; ra=$?; wait $t; rt=$?
r bridge-two-at-once rc-jq=$ra rc-tree=$rt

apk del jq tree >/tmp/b.out 2>&1; rc=$?; r bridge-del rc=$rc in-db=$(apk info -e jq >/dev/null 2>&1; echo $?)

H="--no-cache --repositories-file /dev/null -X http://127.0.0.1:18080/alpine"
for sig in INT TERM KILL; do
    (sleep 60 | nc -l -p 18080 >/dev/null 2>&1) &
    sleep 1
    python3 -c "import os, signal, sys; signal.signal(signal.SIGINT, signal.SIG_DFL); os.execvp('apk', ['apk'] + sys.argv[1:])" add $H jq >/tmp/c.out 2>&1 & w=$!
    sleep 5; before=$(n); kill -$sig $w; wait $w 2>/dev/null; rc=$?; sleep 3
    r bridge-caller-$sig apk-before=$before apk-after=$(n) rc=$rc queue=$(q)
    pkill -x nc 2>/dev/null; pkill -x sleep 2>/dev/null
done

apk add -q jq >/tmp/b.out 2>&1; r bridge-after-cancels rc=$? ; apk del -q jq >/dev/null 2>&1

# No answer from the app: the old folder is watched no more, the new one by nothing.
mv /run/hermes-apk /run/hermes-apk.off && mkdir /run/hermes-apk
apk add jq >/tmp/b.out 2>&1; rc=$?
r bridge-no-answer rc=$rc note=$(grep -c 'did not answer' /tmp/b.out) dberr=$(grep -c 'failed to write database' /tmp/b.out)
rm -rf /run/hermes-apk.off /run/hermes-apk
echo BRIDGE-DONE
