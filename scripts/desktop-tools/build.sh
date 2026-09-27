#!/bin/sh
# Builds the desktop programs the built-in Linux's package repo lacks (x11vnc, xdotool,
# openbox, xprop, xclip, xterm) inside the same Linux, then packs them for the app.
# Run in a bellsoft/alpaquita-linux-base:stream-glibc container; writes /out/desktop-tools-$(uname -m).tar.gz.
set -eu
OUT=/tmp/stage; J=$(nproc)
# Each step's output goes to /tmp/$1.log, printed only if the step fails.
# (Not run as an if/|| condition: that would switch off set -e inside the step.)
t() { s=$(date +%s); set +e; ( set -ex; exec >/tmp/$1.log 2>&1; "$@" ); rc=$?; set -e
  if [ $rc -ne 0 ]; then tail -60 /tmp/$1.log; echo "== $1 FAILED"; exit 1; fi
  echo "== $1 took $(( $(date +%s)-s ))s"; }
deps() {
  apk add -q --no-chown gcc make glibc-dev pkgconf curl cmake autoconf automake libtool util-macros xorgproto \
    libx11-dev libxtst-dev libxi-dev libxinerama-dev libxkbcommon-dev libxext-dev libxfixes-dev libxdamage-dev \
    libxrandr-dev libxcursor-dev libxrender-dev libxft-dev libxmu-dev libxaw-dev libxt-dev libxpm-dev ncurses-dev \
    fontconfig-dev freetype-dev zlib-dev libjpeg-turbo-dev openssl-dev libpng-dev pango-dev libxml2-dev glib-dev xz tar
}
src() { cd /tmp && rm -rf "$1" && mkdir "$1" && curl -fsSL --retry 3 "$2" | tar -x"${3:-z}" --strip-components=1 -C "$1" && cd "$1"; }
xdotool() { src xdotool https://github.com/jordansissel/xdotool/archive/refs/tags/v3.20211022.1.tar.gz
  make -j"$J" WITHOUT_RPATH_FIX=1 && make install PREFIX=/usr/local DESTDIR=$OUT WITHOUT_RPATH_FIX=1; }
libvnc() { src libvncserver https://github.com/LibVNC/libvncserver/archive/refs/tags/LibVNCServer-0.9.15.tar.gz
  cmake -S . -B b -DCMAKE_INSTALL_PREFIX=/opt/vnc -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DBUILD_SHARED_LIBS=OFF \
    -DWITH_GNUTLS=OFF -DWITH_GCRYPT=OFF -DWITH_SDL=OFF -DWITH_GTK=OFF -DWITH_EXAMPLES=OFF -DWITH_TESTS=OFF \
    -DWITH_SYSTEMD=OFF -DWITH_LZO=OFF -DWITH_FFMPEG=OFF
  cmake --build b -j"$J" && cmake --install b; }
x11vnc() { src x11vnc https://github.com/LibVNC/x11vnc/archive/refs/tags/0.9.17.tar.gz
  autoreconf -fi
  PKG_CONFIG_PATH=/opt/vnc/lib/pkgconfig:/opt/vnc/lib64/pkgconfig ./configure -q --prefix=/usr/local
  make -j"$J" && make install DESTDIR=$OUT; }
openbox() { src openbox https://github.com/Mikachu/openbox/archive/refs/tags/release-3.6.1.tar.gz
  ./bootstrap
  ./configure -q --prefix=/usr/local --disable-nls --disable-startup-notification --disable-imlib2 \
    --disable-librsvg --disable-static
  # Only the man pages need docbook-to-man; stand in for it.
  printf '#!/bin/sh\nexit 0\n' > /usr/local/bin/docbook-to-man && chmod +x /usr/local/bin/docbook-to-man
  make -j"$J" && make install DESTDIR=$OUT; }
xprop() { src xprop https://www.x.org/releases/individual/app/xprop-1.2.8.tar.xz J
  ./configure -q --prefix=/usr/local && make -j"$J" && make install DESTDIR=$OUT; }
xclip() { src xclip https://github.com/astrand/xclip/archive/refs/tags/0.13.tar.gz
  autoreconf -fi; ./configure -q --prefix=/usr/local
  make -j"$J" && make install DESTDIR=$OUT; }
xterm() { src xterm https://invisible-island.net/archives/xterm/xterm-403.tgz
  ./configure -q --prefix=/usr/local --enable-wide-chars --with-freetype --disable-setuid --disable-setgid
  make -j"$J" && make install-bin DESTDIR=$OUT; }
scrot() {
  # scrot needs imlib2, which the repo lacks: this stand-in saves the X display with Pillow.
  mkdir -p $OUT/usr/local/bin && cat > $OUT/usr/local/bin/scrot <<'SCROT'
#!/bin/sh
# scrot stand-in (Hermes Android): `scrot [-o] FILE` saves the X display ($DISPLAY) as an image.
out=""; for a in "$@"; do case "$a" in -*) ;; *) out="$a" ;; esac; done
[ -n "$out" ] || out="$(date +%Y-%m-%d-%H%M%S)_scrot.png"
for py in /root/.hermes/hermes-agent/venv/bin/python python3; do
  "$py" -c 'import PIL' 2>/dev/null && exec "$py" -c 'import os, sys; from PIL import ImageGrab; ImageGrab.grab(xdisplay=os.environ.get("DISPLAY", ":99")).save(sys.argv[1])' "$out"
done
echo "scrot: no Python with Pillow found" >&2; exit 1
SCROT
  chmod 755 $OUT/usr/local/bin/scrot; }
rm -rf $OUT; mkdir -p $OUT /out
t deps; t xdotool; t libvnc; t x11vnc; t openbox; t xprop; t xclip; t xterm; t scrot
rm -rf $OUT/usr/local/share/man $OUT/usr/local/share/doc $OUT/usr/local/include $OUT/usr/local/lib/pkgconfig
find $OUT -name "*.la" -delete
# The apk packages these binaries load their libraries from, for the app's package list.
for f in $(find $OUT -type f \( -path "*/bin/*" -o -name "*.so*" \)); do
  file_libs=$(LD_LIBRARY_PATH=$OUT/usr/local/lib ldd "$f" 2>/dev/null | awk '/=>/ {print $3}')
  for l in $file_libs; do case "$l" in $OUT/*) ;; /*) apk info -q --who-owns "$l" 2>/dev/null ;; esac; done
done | sed 's/-[0-9].*//' | sort -u > $OUT/usr/local/share/hermes-desktop-deps.txt
echo "--- runtime packages:"; cat $OUT/usr/local/share/hermes-desktop-deps.txt | tr '\n' ' '; echo
echo "--- missing libraries:"; for f in $(find $OUT -type f -path "*/bin/*"); do LD_LIBRARY_PATH=$OUT/usr/local/lib ldd "$f" 2>/dev/null | grep "not found" || true; done
tar -C $OUT -czf /out/desktop-tools-$(uname -m).tar.gz .
ls -la /out; find $OUT -type f -path "*/bin/*" | sort
