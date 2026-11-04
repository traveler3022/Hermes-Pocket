# Runs inside the guest (chroot or proot). Prints: RESULT <label> <test> <ms> rc=<exit>
L="$1"
REPO=/root/.hermes/hermes-agent
cd "$REPO" || exit 1
t() {
    local n="$1"; shift
    local s=$EPOCHREALTIME
    "$@" >/dev/null 2>&1
    local rc=$?
    local e=$EPOCHREALTIME
    echo "RESULT $L $n $(awk -v a="$s" -v b="$e" 'BEGIN{printf "%.0f",(b-a)*1000}') rc=$rc"
}
t python_startup venv/bin/python -c pass
t import_gateway env PYTHONPATH="$REPO" venv/bin/python -c "import tui_gateway.server"
t hermes_version venv/bin/hermes --version
t git_status git status --porcelain
t spawn100 bash -c 'for i in $(seq 100); do /bin/true; done'
t find_venv find venv -type f
# What Hermes hands shell.exec children (tools/environments/local_pythonpath.py): PYTHONPATH and
# VIRTUAL_ENV stripped, PATH kept with the gateway's venv/bin first.
APPENV="PATH=$REPO/venv/bin:$PATH HERMES_HOME=/root/.hermes"
t app_cat_memory env $APPENV sh -c "cat ~/.hermes/memories/USER.md 2>/dev/null || echo '(not found)'"
t app_exec_python_old env $APPENV sh /opt/bench/exec_python_old.sh
t app_exec_python_new env $APPENV sh /opt/bench/exec_python_new.sh
echo "INFO $L old: $(env $APPENV sh /opt/bench/exec_python_old.sh 2>&1 | tail -c 300 | tr '\n' ' ')"
echo "INFO $L new: $(env $APPENV sh /opt/bench/exec_python_new.sh 2>&1 | tail -c 300 | tr '\n' ' ')"
venv/bin/python /opt/bench/micro.py "$L"
