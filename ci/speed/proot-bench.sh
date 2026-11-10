# Runs inside termux-docker: the app's own proot binaries and arguments (ProotEnvironment.prootArgs).
T=/data/data/com.termux/files/usr/tmp/pb
mkdir -p "$T/lib" "$T/tmp" "$T/bin"
cp /jni/*.so "$T/bin/" && chmod 755 "$T/bin/"*.so
ln -sf "$T/bin/libtalloc.so" "$T/lib/libtalloc.so.2"
export LD_LIBRARY_PATH="$T/lib:$T/bin" PROOT_LOADER="$T/bin/libproot-loader.so" PROOT_TMP_DIR="$T/tmp"
GUEST_ENV="HOME=/root USER=root LANG=C.UTF-8 TERM=xterm-256color TMPDIR=/tmp GIT_OPTIONAL_LOCKS=0 HERMES_DISABLE_LAZY_INSTALLS=1 PATH=/root/.local/bin:/root/.hermes/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
run() {
    local label="$1" extra="$2"; shift 2
    env $extra "$T/bin/libproot.so" --kill-on-exit "$@" -r /rf -b /dev -b /proc -b /sys -b /proc/self/fd:/dev/fd -b /rf/tmp:/dev/shm \
        -w /root /usr/bin/env -i $GUEST_ENV /bin/bash /opt/bench/bench.sh "$label"
}
"$T/bin/libproot.so" -v 1 -0 -r /rf /usr/bin/env -i /bin/true 2>&1 | grep -i seccomp || echo "no seccomp line from proot -v 1"
for i in 1 2 3; do
    run proot_app "" -0
    run proot_no0 ""
    run proot_noseccomp PROOT_NO_SECCOMP=1 -0
done
