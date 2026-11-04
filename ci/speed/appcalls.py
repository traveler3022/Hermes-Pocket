"""Writes the exact shell.exec commands the app sends (HermesPython.kt + ConfigUtils.kt) into OUT_DIR."""
import base64
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
out = pathlib.Path(sys.argv[1])
kt = root / "app/src/main/java/com/hermes/android"


def trim_indent(s):
    lines = s.split("\n")
    if lines and not lines[0].strip():
        lines = lines[1:]
    if lines and not lines[-1].strip():
        lines = lines[:-1]
    ind = min((len(l) - len(l.lstrip()) for l in lines if l.strip()), default=0)
    return "\n".join(l[ind:] if l.strip() else "" for l in lines)


def raw(path, name):
    m = re.search(r"val " + name + r' = """(.*?)""".trimIndent\(\)', path.read_text(), re.S)
    return trim_indent(m.group(1))


prelude = raw(kt / "gateway/HermesPython.kt", "HERMES_RUNTIME_PRELUDE")
yaml_compat = raw(kt / "ui/viewmodel/ConfigUtils.kt", "YAML_COMPAT")
# Same shape as ConfigViewModel.loadProviders: config.yaml through yaml, printed as JSON.
script = """import json, yaml, pathlib
home = pathlib.Path.home() / '.hermes'
cp = home / 'config.yaml'
cfg = (yaml.safe_load(cp.read_text()) if cp.exists() else {}) or {}
print(json.dumps({'custom_providers': cfg.get('custom_providers') or [], 'model': cfg.get('model')}, default=str))"""
enc = base64.b64encode((prelude + "\n" + yaml_compat + "\n" + script).encode()).decode()
(out / "exec_python.sh").write_text(
    "py=python3; HERMES_POCKET_RT=$(hermes --print-runtime-command 2>/dev/null) && "
    "case \"$HERMES_POCKET_RT\" in '[\"/'*) py=${HERMES_POCKET_RT#'[\"'}; py=${py%%'\"'*};; esac; "
    f"export HERMES_POCKET_RT; printf %s '{enc}' | base64 -d | \"$py\" -\n")
(out / "exec_python_nort.sh").write_text(f"printf %s '{enc}' | base64 -d | python3 -\n")
print("wrote", out / "exec_python.sh", len(enc), "base64 chars")
