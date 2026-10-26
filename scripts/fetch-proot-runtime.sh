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
  fetch_deb "$dir" "pool/main/p/proot/proot_5.1.107.101_${arch}.deb" "$proot_sha"
  fetch_deb "$dir" "pool/main/libt/libtalloc/libtalloc_2.5.0_${arch}.deb" "$talloc_sha"
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
arm64-v8a aarch64 aarch64 a28355e9be257e1f87f4e9294b3118470d4fd7a5442fed8b278f4443c83407d7 556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7 0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6 b17a57958e29735ff0e6e64254d958e65903687a70ca30cc430b33a965ad49d7
x86_64 x86_64 x86_64 85adb8d0d2e1edc6c56e60c17136adf05e54ce75f69407ec7116a4c5997eaed5 b8c6d95f20075dc1f9ec6573575b2444e8d526e48e0d8d6d5cf4e071e6e06530 ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797 6fc0e3639a1c01f156970d7626aab90bc90697117069dbc39ad880b84efa319a
EOF
