#!/usr/bin/env bash
# Layer boundary check for ui/. A rule in BLOCK fails the build; one in REPORT is only listed,
# until its part of the UI has moved behind an interface and the rule moves up to BLOCK.
set -u
UI=app/src/main/java/com/hermes/android/ui

# The built-in Linux: only through the interfaces in runtime/ (LinuxFileAccess, LinuxFileGate,
# LinuxTools, LinuxDesktopControl). Repositories, storage, files, media and notifications: only
# through their interfaces in domain/.
BLOCK='runtime\.linux\.|rootfsDir|data\.(Session|Groups|Profiles|Projects|ProviderSetup|AppRelease)Repository\b|data\.SetupState\b'
BLOCK+='|data\.Storage(Cleaner|Files)\b|gateway\.(RemoteFiles|StdioGatewayHub)\b'
BLOCK+='|service\.(MediaStreams|ApprovalNotificationManager|AppForegroundState)\b'
# Blocked everywhere but Kanban's files (in progress on its own branch).
BLOCK_BUT_KANBAN='data\.DownloadStorage\b'
# Not moved yet: Kanban.
REPORT='com\.hermes\.android\.data\.([A-Za-z]*Repository|DownloadStorage)\b'

status=0
if blocked=$(grep -rnE "$BLOCK" "$UI"); then
    echo "::error::ui/ reaches past its interfaces; use the ones in runtime/ and domain/:"
    echo "$blocked"
    status=1
fi
if blocked=$(grep -rnE --exclude='Kanban*.kt' "$BLOCK_BUT_KANBAN" "$UI"); then
    echo "::error::ui/ reaches past its interfaces; use the ones in domain/:"
    echo "$blocked"
    status=1
fi
reported=$(grep -rnoE "$REPORT" "$UI" | sed 's#^.*/android/ui/##')
echo "Layer report: $(printf '%s' "$reported" | grep -c .) direct uses of data/ classes in ui/"
[ -n "$reported" ] && echo "$reported"
exit $status
