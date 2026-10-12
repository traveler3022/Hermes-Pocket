#!/usr/bin/env bash
# Layer boundary check for ui/. A rule in BLOCK fails the build; one in REPORT is only listed,
# until its part of the UI has moved behind an interface and the rule moves up to BLOCK.
set -u
UI=app/src/main/java/com/hermes/android/ui

# The built-in Linux: only through the interfaces in runtime/ (LinuxFileAccess, LinuxFileGate,
# LinuxTools, LinuxDesktopControl). Sessions: only through domain/session/.
BLOCK='runtime\.linux\.|rootfsDir|data\.SessionRepository\b'
# Not moved yet: repositories without an interface.
REPORT='import com\.hermes\.android\.data\.[A-Za-z]*Repository$'

status=0
if blocked=$(grep -rnE "$BLOCK" "$UI"); then
    echo "::error::ui/ reaches past its interfaces; use the ones in runtime/ and domain/:"
    echo "$blocked"
    status=1
fi
reported=$(grep -rnoE "$REPORT" "$UI" | sed 's#^.*/android/ui/##')
echo "Layer report: $(printf '%s' "$reported" | grep -c .) direct imports of repositories in ui/"
[ -n "$reported" ] && echo "$reported"
exit $status
