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
venv/bin/python /opt/bench/micro.py "$L"
