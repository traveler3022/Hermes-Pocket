#!/usr/bin/env bash
# Fetches the built-in Linux runtime: proot from Termux's official aarch64
# packages (installed as jniLibs, so Android extracts them into nativeLibraryDir,
# the only app location that may execve() on targetSdk >= 29) and the Alpine
# minirootfs (bundled as an asset).
set -euo pipefail

REPO="https://packages-cf.termux.dev/apt/termux-main"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fetch() {
  local path="$1" sha="$2"
  local file="$WORK/$(basename "$path")"
  curl -fsSL --retry 3 "$REPO/$path" -o "$file"
  echo "$sha  $file" | sha256sum -c - >/dev/null
  (cd "$WORK" && ar x "$file" && tar xf data.tar.* && rm -f data.tar.* control.tar.* debian-binary)
}

fetch pool/main/p/proot/proot_5.1.107.92_aarch64.deb \
  1f1c983509701f6826f568482c70673ee453a9ba38c9f5fa445a472d6b7524e9
fetch pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb \
  ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da
fetch pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb \
  0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6

PREFIX="$WORK/data/data/com.termux/files/usr"
mkdir -p "$OUT"
install -m 0755 "$PREFIX/bin/proot" "$OUT/libproot.so"
install -m 0755 "$PREFIX/libexec/proot/loader" "$OUT/libproot-loader.so"
install -m 0755 "$(readlink -f "$PREFIX/lib/libtalloc.so.2")" "$OUT/libtalloc.so"
install -m 0755 "$PREFIX/lib/libandroid-shmem.so" "$OUT/libandroid-shmem.so"
ls -l "$OUT"

ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.23/releases/aarch64/alpine-minirootfs-3.23.6-aarch64.tar.gz"
ALPINE_SHA256="b17a57958e29735ff0e6e64254d958e65903687a70ca30cc430b33a965ad49d7"
ASSETS="$ROOT/app/src/main/assets/linux"
mkdir -p "$ASSETS"
curl -fsSL --retry 3 "$ALPINE_URL" -o "$WORK/alpine.tar.gz"
echo "$ALPINE_SHA256  $WORK/alpine.tar.gz" | sha256sum -c - >/dev/null
install -m 0644 "$WORK/alpine.tar.gz" "$ASSETS/alpine-minirootfs-aarch64.tar.gz"
ls -l "$ASSETS"
