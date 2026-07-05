#!/usr/bin/env bash
# Compare the vendored protocol reference (docs/upstream-reference/) against
# the latest upstream NousResearch/hermes-agent to detect protocol drift
# BEFORE it shows up as mystery bugs in the app.
#
# Usage:
#   scripts/check-upstream-drift.sh            # report drift (exit 1 if any)
#   scripts/check-upstream-drift.sh --update   # overwrite snapshot with latest
#                                              # (then update README pin + commit)
set -euo pipefail

REPO="NousResearch/hermes-agent"
REF_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/upstream-reference"

# vendored-name : upstream-path
FILES=(
  "gatewayTypes.ts:ui-tui/src/gatewayTypes.ts"
  "gatewayClient.ts:ui-tui/src/gatewayClient.ts"
  "createGatewayEventHandler.ts:ui-tui/src/app/createGatewayEventHandler.ts"
  "turnController.ts:ui-tui/src/app/turnController.ts"
  "ws.py:tui_gateway/ws.py"
)

MODE="check"
[[ "${1:-}" == "--update" ]] && MODE="update"

SHA=$(git ls-remote "https://github.com/$REPO.git" refs/heads/main | cut -f1)
if [[ -z "$SHA" ]]; then
  echo "error: could not resolve upstream main (network?)" >&2
  exit 2
fi
echo "upstream main: $SHA"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

DRIFT=0
for entry in "${FILES[@]}"; do
  name="${entry%%:*}"
  path="${entry#*:}"
  url="https://raw.githubusercontent.com/$REPO/$SHA/$path"
  if ! curl -fsS --max-time 60 "$url" -o "$TMP/$name"; then
    echo "  !! fetch failed: $path" >&2
    DRIFT=1
    continue
  fi
  if [[ "$MODE" == "update" ]]; then
    cp "$TMP/$name" "$REF_DIR/$name"
    echo "  updated: $name"
  elif ! diff -q "$REF_DIR/$name" "$TMP/$name" >/dev/null; then
    DRIFT=1
    added=$(diff "$REF_DIR/$name" "$TMP/$name" | grep -c '^>' || true)
    removed=$(diff "$REF_DIR/$name" "$TMP/$name" | grep -c '^<' || true)
    echo "  DRIFT: $name (+$added / -$removed lines) — see: diff docs/upstream-reference/$name <(curl -fsS $url)"
  else
    echo "  ok: $name"
  fi
done

if [[ "$MODE" == "update" ]]; then
  echo
  echo "Snapshot updated to $SHA."
  echo "Now update the pinned commit + date in docs/upstream-reference/README.md"
  echo "and commit the whole directory together."
elif [[ "$DRIFT" -eq 1 ]]; then
  echo
  echo "Protocol drift detected. Review diffs, port what matters to the app,"
  echo "then run with --update to move the pin."
  exit 1
else
  echo
  echo "No drift — vendored reference matches upstream main."
fi
