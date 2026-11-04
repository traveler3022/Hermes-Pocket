# Installs Hermes in the rootfs the way ProotLinuxRuntime does (packages, uv, clone, uv sync, precompile).
set -e
apk add --no-chown python3 git curl ca-certificates bash procps-ng libstdc++ libgcc libatomic >/dev/null
export HERMES_HOME=/root/.hermes REPO=/root/.hermes/hermes-agent
mkdir -p "$HERMES_HOME/bin"
curl -LsSf --retry 3 -o /tmp/uv-install.sh https://astral.sh/uv/install.sh
env UV_INSTALL_DIR="$HERMES_HOME/bin" UV_NO_MODIFY_PATH=1 sh /tmp/uv-install.sh >/dev/null
UV="$HERMES_HOME/bin/uv"
git clone --quiet --depth 1 --branch main https://github.com/NousResearch/hermes-agent.git "$REPO"
cd "$REPO"
PY=$(python3 -c "import json; v = json.load(open('pm/lock.json'))['packages']['python']['version']; print('.'.join(v.split('+')[0].split('.')[:2]))" 2>/dev/null || echo 3.14)
export UV_PYTHON_INSTALL_DIR="$HERMES_HOME/python"
"$UV" python find --system "$PY" >/dev/null 2>&1 || "$UV" python install --no-bin "$PY"
export UV_PYTHON="$PY" UV_PROJECT_ENVIRONMENT="$REPO/venv" UV_LINK_MODE=copy
"$UV" sync --locked --no-dev --extra web || { [ -x venv/bin/python ] || "$UV" venv venv; "$UV" pip install --python venv/bin/python -e '.[web]'; }
PYTHONPATH="$REPO" venv/bin/python -c "import tui_gateway.server"
venv/bin/python --version
cp cli-config.yaml.example "$HERMES_HOME/config.yaml" 2>/dev/null || true
mkdir -p "$HERMES_HOME/memories" && echo "# user notes" > "$HERMES_HOME/memories/USER.md"
ln -sf "$REPO/venv/bin/hermes" /usr/local/bin/hermes
echo "INSTALL OK"
