#!/usr/bin/env bash
# Fetches the built-in Linux runtime for arm64 and x86_64: proot from Termux's
# official packages (installed as jniLibs, so Android extracts them into
# nativeLibraryDir, the only app location that may execve() on targetSdk >= 29)
# and the Alpine minirootfs (bundled as an asset).
set -euo pipefail

REPO="https://packages-cf.termux.dev/apt/termux-main"
ALPINE="https://dl-cdn.alpinelinux.org/alpine/v3.23/releases"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/linux"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fetch_deb() {
  local dir="$1" path="$2" sha="$3"
  local file="$dir/$(basename "$path")"
  curl -fsSL --retry 3 "$REPO/$path" -o "$file"
  echo "$sha  $file" | sha256sum -c - >/dev/null
  (cd "$dir" && ar x "$file" && tar xf data.tar.* && rm -f data.tar.* control.tar.* debian-binary)
}

# abi  termux-arch  alpine-arch  proot-sha  talloc-sha  shmem-sha  alpine-sha
while read -r abi arch alpine_arch proot_sha talloc_sha shmem_sha alpine_sha; do
  dir="$WORK/$abi"
  mkdir -p "$dir"
  fetch_deb "$dir" "pool/main/p/proot/proot_5.1.107.92_${arch}.deb" "$proot_sha"
  fetch_deb "$dir" "pool/main/libt/libtalloc/libtalloc_2.4.3_${arch}.deb" "$talloc_sha"
  fetch_deb "$dir" "pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_${arch}.deb" "$shmem_sha"

  prefix="$dir/data/data/com.termux/files/usr"
  out="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$out"
  install -m 0755 "$prefix/bin/proot" "$out/libproot.so"
  install -m 0755 "$prefix/libexec/proot/loader" "$out/libproot-loader.so"
  install -m 0755 "$(readlink -f "$prefix/lib/libtalloc.so.2")" "$out/libtalloc.so"
  install -m 0755 "$prefix/lib/libandroid-shmem.so" "$out/libandroid-shmem.so"

  mkdir -p "$ASSETS"
  curl -fsSL --retry 3 "$ALPINE/$alpine_arch/alpine-minirootfs-3.23.6-$alpine_arch.tar.gz" -o "$dir/alpine.tar.gz"
  echo "$alpine_sha  $dir/alpine.tar.gz" | sha256sum -c - >/dev/null
  install -m 0644 "$dir/alpine.tar.gz" "$ASSETS/alpine-minirootfs-$abi.tar.gz"
  ls -l "$out" "$ASSETS/alpine-minirootfs-$abi.tar.gz"
done <<'EOF'
arm64-v8a aarch64 aarch64 1f1c983509701f6826f568482c70673ee453a9ba38c9f5fa445a472d6b7524e9 ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da 0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6 b17a57958e29735ff0e6e64254d958e65903687a70ca30cc430b33a965ad49d7
x86_64 x86_64 x86_64 70236632826c30ec0245082b633bbc7ef1e9fa5531bd51bd4f20231bfcdc999b 7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628 ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797 6fc0e3639a1c01f156970d7626aab90bc90697117069dbc39ad880b84efa319a
EOF
