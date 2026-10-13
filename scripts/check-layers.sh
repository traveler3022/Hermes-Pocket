#!/usr/bin/env bash
# Layer boundary check for ui/: a rule in BLOCK fails the build.
set -u
UI=app/src/main/java/com/hermes/android/ui

# The built-in Linux: only through the interfaces in runtime/ (LinuxFileAccess, LinuxFileGate,
# LinuxTools, LinuxDesktopControl). Repositories, storage, files, media and notifications: only
# through their interfaces in domain/.
BLOCK='runtime\.linux\.|rootfsDir|data\.[A-Za-z]*Repository\b|data\.SetupState\b|data\.DownloadStorage\b'
BLOCK+='|data\.Storage(Cleaner|Files)\b|gateway\.(RemoteFiles|StdioGatewayHub)\b'
BLOCK+='|service\.(MediaStreams|ApprovalNotificationManager|AppForegroundState)\b'

status=0
if blocked=$(grep -rnE "$BLOCK" "$UI"); then
    echo "::error::ui/ reaches past its interfaces; use the ones in runtime/ and domain/:"
    echo "$blocked"
    status=1
fi
exit $status
