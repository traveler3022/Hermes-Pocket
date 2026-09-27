"""Installs programs the built-in Linux's own repo lacks from Debian's official archive.

usage: debian_fetch.py SUITE PACKAGE...

Debian is glibc like this Linux (and on an older glibc), so its builds run here. No dpkg:
each .deb (checksum from Debian's index) is unpacked under /opt/debian, its library dirs are
registered with ldconfig and its programs linked into /usr/local/bin.
"""
import hashlib
import io
import lzma
import os
import platform
import subprocess
import sys
import tarfile
import time
import urllib.request

MIRROR = "https://deb.debian.org/debian"
ROOT = "/opt/debian"
ARCH = {"aarch64": "arm64", "x86_64": "amd64"}[platform.machine()]
TRIPLET = {"arm64": "aarch64-linux-gnu", "amd64": "x86_64-linux-gnu"}[ARCH]


def get(url):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=60) as reply:
                return reply.read()
        except Exception as e:
            if attempt == 4:
                raise SystemExit(f"download failed: {url}: {e}")
            time.sleep(2 * (attempt + 1))


def index(suite):
    raw = lzma.decompress(get(f"{MIRROR}/dists/{suite}/main/binary-{ARCH}/Packages.xz")).decode()
    packages = {}
    for block in raw.split("\n\n"):
        fields = dict(l.split(": ", 1) for l in block.splitlines() if ": " in l and not l.startswith(" "))
        if "Package" in fields:
            packages[fields["Package"]] = fields
    return packages


def unpack(deb, root):
    """A .deb is an ar archive; its data.tar.* holds the files."""
    pos = 8
    while pos < len(deb):
        name = deb[pos:pos + 16].decode().strip().rstrip("/")
        size = int(deb[pos + 48:pos + 58])
        body = deb[pos + 60:pos + 60 + size]
        pos += 60 + size + (size & 1)
        if name.startswith("data.tar"):
            with tarfile.open(fileobj=io.BytesIO(body)) as tar:
                tar.extractall(root, filter="tar")
            return
    raise SystemExit("not a .deb: no data.tar")


def main(suite, names):
    packages = index(suite)
    for name in names:
        fields = packages.get(name) or sys.exit(f"{name}: not in Debian {suite}")
        deb = get(f"{MIRROR}/{fields['Filename']}")
        if hashlib.sha256(deb).hexdigest() != fields["SHA256"]:
            sys.exit(f"{name}: checksum mismatch")
        unpack(deb, ROOT)
        print(f"{name} {fields['Version']}", flush=True)
    os.makedirs("/etc/ld.so.conf.d", exist_ok=True)
    with open("/etc/ld.so.conf.d/hermes-debian.conf", "w") as conf:
        conf.write(f"{ROOT}/usr/lib/{TRIPLET}\n{ROOT}/lib/{TRIPLET}\n")
    subprocess.run(["ldconfig"], check=True)
    os.makedirs("/usr/local/bin", exist_ok=True)
    for program in os.listdir(f"{ROOT}/usr/bin"):
        link = f"/usr/local/bin/{program}"
        if os.path.lexists(link):
            os.remove(link)
        os.symlink(f"{ROOT}/usr/bin/{program}", link)
    # openbox reads its config from /etc/xdg (its build's path), not from under ROOT.
    if os.path.isdir(f"{ROOT}/etc/xdg/openbox") and not os.path.exists("/etc/xdg/openbox"):
        os.makedirs("/etc/xdg", exist_ok=True)
        os.symlink(f"{ROOT}/etc/xdg/openbox", "/etc/xdg/openbox")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
