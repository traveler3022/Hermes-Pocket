"""Runs Hermes' own shell.exec safety filter (tools.approval_detection) on the app's commands."""
import sys

sys.path.insert(0, "/root/.hermes/hermes-agent")
from tools.approval_detection import detect_dangerous_command, detect_hardline_command  # noqa: E402

for name in ("exec_python_old.sh", "exec_python_new.sh"):
    cmd = open("/opt/bench/" + name, encoding="utf-8").read().strip()
    print("FILTER", name, "hardline=", detect_hardline_command(cmd), "dangerous=", detect_dangerous_command(cmd))
