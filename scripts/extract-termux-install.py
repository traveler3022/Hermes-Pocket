"""Prints the Termux install script that TermuxInstaller.generateInstallScript() builds."""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()
body = src[src.index("fun generateInstallScript()"):src.index("fun generateStateResyncScript()")]
actions = {"PROGRESS": "com.hermes.android.INSTALL_PROGRESS",
           "COMPLETE": "com.hermes.android.INSTALL_COMPLETE",
           "ERROR": "com.hermes.android.INSTALL_ERROR"}
out = []
for m in re.finditer(r'appendLine\((?:"((?:[^"\\]|\\.)*)")?\)', body):
    s = m.group(1) or ""
    s = re.sub(r"\$\{BroadcastAction\.(\w+)\.action\}", lambda a: actions[a.group(1)], s)
    s = s.replace("$receiver", "com.hermes.android")
    s = re.sub(r"\\(.)", lambda e: {"n": "\\n"}.get(e.group(1), e.group(1)) if e.group(1) != "\\" else "\\", s)
    out.append(s)
print("\n".join(out))
