#!/bin/bash
# Builds app/libs/tsbridge.aar: Tailscale (tsnet) for Android through gomobile.
# Needs Go (version in go.mod), the Android SDK and an NDK. CI runs it before Gradle.
set -euo pipefail
cd "$(dirname "$0")"

TAILSCALE_VERSION=v1.104.0

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  NDK="$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/ndk/* 2>/dev/null | sort -V | tail -1)"
fi
[ -n "$NDK" ] || { echo "No Android NDK found" >&2; exit 1; }
export ANDROID_NDK_HOME="$NDK"
echo "Using NDK $NDK"

go install golang.org/x/mobile/cmd/gomobile@latest golang.org/x/mobile/cmd/gobind@latest
export PATH="$(go env GOPATH)/bin:$PATH"
gomobile init
go get "tailscale.com@$TAILSCALE_VERSION" golang.org/x/mobile@latest
go mod tidy

mkdir -p ../app/libs
# arm64 only: the APK takes every ABI it is given, and phones are arm64 (x86_64 would add ~17 MB).
gomobile bind -target=android/arm64 -androidapi 29 -trimpath "-ldflags=-s -w" \
  -o ../app/libs/tsbridge.aar .
unzip -l ../app/libs/tsbridge.aar | awk '/libgojni.so/{printf "%s: %.1f MB\n", $4, $1 / 1048576}'
