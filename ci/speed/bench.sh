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
APPENV="PATH=$REPO/venv/bin:$PATH VIRTUAL_ENV=$REPO/venv PYTHONPATH=$REPO HERMES_PYTHON_SRC_ROOT=$REPO HERMES_HOME=/root/.hermes"
t app_cat_memory env $APPENV sh -c "cat ~/.hermes/memories/USER.md 2>/dev/null || echo '(not found)'"
t app_rt_query env $APPENV sh -c 'hermes --print-runtime-command'
t app_exec_python env $APPENV sh /opt/bench/exec_python.sh
t app_exec_python_nort env $APPENV sh /opt/bench/exec_python_nort.sh
echo "INFO $L rt_query: $(env $APPENV sh -c 'hermes --print-runtime-command; echo rc=$?' 2>&1 | tail -c 300 | tr '\n' ' ')"
echo "INFO $L exec_python: $(env $APPENV sh /opt/bench/exec_python.sh 2>&1 | tail -c 300 | tr '\n' ' ')"
venv/bin/python /opt/bench/micro.py "$L"
