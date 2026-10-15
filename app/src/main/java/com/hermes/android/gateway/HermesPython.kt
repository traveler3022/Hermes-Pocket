package com.hermes.android.gateway

import java.util.Base64

/**
 * Runs before every script: when the shell picked Hermes' own interpreter,
 * load Hermes' dependencies the way its launcher does, so `ruamel`/`yaml`
 * import. The shell hands over `hermes --print-runtime-command` (a JSON argv
 * whose last item puts the install on sys.path, then imports hermes_bootstrap).
 */
private val HERMES_RUNTIME_PRELUDE = """
    import json as _json, os as _os, re as _re, sys as _sys
    try:
        _rt = _json.loads(_os.environ.pop('HERMES_POCKET_RT', '') or 'null')
    except ValueError:
        _rt = None
    if isinstance(_rt, list) and len(_rt) > 1 and _os.path.realpath(str(_rt[0])) == _os.path.realpath(_sys.executable):
        _m = _re.search(r"sys\.path\.insert\(0, '([^']+)'\)", str(_rt[-1]))
        if _m:
            for _k in ('PYTHONHOME', 'PYTHONPATH', 'VIRTUAL_ENV'):
                _os.environ.pop(_k, None)
            _sys.path.insert(0, _m.group(1))
            _sys._hermes_pin_default_home = True
            import hermes_bootstrap
            if not _os.environ.get('HERMES_HOME'):
                _os.environ['HERMES_HOME'] = str(__import__('hermes_constants').get_default_hermes_root())
""".trimIndent()

/**
 * shell.exec command that runs [script] in Hermes' Python.
 *
 * Current Hermes runs on its own managed Python, so a bare `python3` on PATH
 * is the system one without ruamel/yaml ("No module named 'ruamel'"). The
 * interpreter comes from `hermes --print-runtime-command`; an older Hermes, or
 * no `hermes` on PATH, falls back to `python3` as before.
 *
 * The gateway's safety filter answers 4005 for both `python3 -c` and
 * `python3 - <<EOF`, so the script travels base64-encoded on stdin.
 */
fun hermesPythonCommand(script: String): String {
    val encoded = Base64.getEncoder().encodeToString(
        (HERMES_RUNTIME_PRELUDE + "\n" + script).toByteArray(Charsets.UTF_8),
    )
    return "py=python3; HERMES_POCKET_RT=\$(hermes --print-runtime-command 2>/dev/null) && " +
        "case \"\$HERMES_POCKET_RT\" in '[\"/'*) py=\${HERMES_POCKET_RT#'[\"'}; py=\${py%%'\"'*};; esac; " +
        "export HERMES_POCKET_RT; printf %s '$encoded' | base64 -d | \"\$py\" -"
}
