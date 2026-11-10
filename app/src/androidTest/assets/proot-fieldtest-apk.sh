# apk add/del of two small packages; run once with link2symlink (the app's installs) and once
# without (the agent in chat).
apk del -q jq tree >/dev/null 2>&1
apk add -q --no-chown jq tree >/tmp/apko 2>&1; rc=$?
jq --version >/dev/null 2>&1 && tree --version >/dev/null 2>&1; runs=$?
apk info -e jq tree >/dev/null 2>&1; db=$?
echo "RESULT apk-add rc=$rc runs=$runs in-db=$db $(grep -m1 -iE 'error|fail' /tmp/apko | head -c 160)"
apk del -q jq tree >/tmp/apko 2>&1; echo "RESULT apk-del rc=$? $(grep -m1 -iE 'error|fail' /tmp/apko | head -c 160)"
