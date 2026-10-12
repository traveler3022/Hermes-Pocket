#!/usr/bin/env bash
# Layer boundary check for ui/. A rule in BLOCK fails the build; one in REPORT is only listed,
# until its part of the UI has moved behind an interface and the rule moves up to BLOCK.
set -u
UI=app/src/main/java/com/hermes/android/ui

# The built-in Linux's files: only through runtime.LinuxFileAccess.
BLOCK='rootfsDir|runtime\.linux\.(GuestFiles|LinuxFilesProvider|ProotEnvironment\.rootfsDir)\b'
# Not moved yet: the rest of the built-in Linux, and repositories without an interface.
REPORT='import com\.hermes\.android\.(runtime\.linux\.[A-Za-z]+|data\.[A-Za-z]*Repository)$'

status=0
if blocked=$(grep -rnE "$BLOCK" "$UI"); then
    echo "::error::ui/ reaches into the Linux files directly; use LinuxFileAccess:"
    echo "$blocked"
    status=1
fi
reported=$(grep -rnoE "$REPORT" "$UI" | sed 's#^.*/android/ui/##')
echo "Layer report: $(printf '%s' "$reported" | grep -c .) direct imports of runtime.linux / repositories in ui/"
[ -n "$reported" ] && echo "$reported"
exit $status
