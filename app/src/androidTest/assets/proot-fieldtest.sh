# proot field test, run by ProotFieldTest inside the app's own Linux. One RESULT line per test.
N=${N:-15}
r() { echo "RESULT $*"; }
now() { python3 -c 'import time; print(time.time())'; }
dt() { python3 -c "import sys, time; print(round(time.time() - float(sys.argv[1]), 1))" "$1"; }

# 1. npm self-install, offline (the double free seen in hermes update)
curl -fsSL --retry 3 -o /tmp/npm.tgz https://registry.npmjs.org/npm/-/npm-12.0.2.tgz || r npm-download FAILED
f=0; s=$(now)
for i in $(seq 1 $N); do
  rm -rf /tmp/npmst /tmp/npmc; mkdir -p /tmp/npmst /tmp/npmc
  if ! npm_config_cache=/tmp/npmc npm install --global --prefix /tmp/npmst --offline --ignore-scripts --no-audit --no-fund /tmp/npm.tgz >/tmp/npmo 2>&1; then
    f=$((f+1)); echo "npm run $i: $(grep -m1 -iE 'double free|corrupt|abort|signal|err' /tmp/npmo | head -c 200)"
  fi
done
r npm fails=$f/$N time=$(dt $s)s

# 2. Processes: plain spawns, children killed right after fork, Python subprocess churn in threads + fork
s=$(now); for i in $(seq 1 1000); do /bin/true; done; r spawn1000 time=$(dt $s)s
s=$(now); for i in $(seq 1 300); do sleep 5 & kill $! 2>/dev/null; done; wait 2>/dev/null; r forkkill300 time=$(dt $s)s
s=$(now); python3 - <<'P'
import os, random, subprocess, threading
def churn(n):
    for _ in range(n):
        p = subprocess.Popen(["sh", "-c", "sleep 1"])
        if random.random() < 0.7: p.kill()
        p.wait()
ts = [threading.Thread(target=churn, args=(60,)) for _ in range(6)]
[t.start() for t in ts]; [t.join() for t in ts]
pids = []
for _ in range(100):
    pid = os.fork()
    if pid == 0: os._exit(0)
    pids.append(pid)
for p in pids: os.waitpid(p, 0)
P
r pychurn rc=$? time=$(dt $s)s

# 3. git: clone over the network, fsck, status (without link2symlink, as the gateway runs)
rm -rf /tmp/gc; s=$(now)
git clone -q --depth 200 https://github.com/termux/proot /tmp/gc >/tmp/gito 2>&1 && git -C /tmp/gc fsck --no-progress >>/tmp/gito 2>&1 && git -C /tmp/gc status --porcelain >>/tmp/gito 2>&1
rc=$?; r git clone+fsck rc=$rc time=$(dt $s)s; [ $rc = 0 ] || tail -5 /tmp/gito
git clone -q /tmp/gc /tmp/gc2 >>/tmp/gito 2>&1 && git -C /tmp/gc2 fsck --no-progress >>/tmp/gito 2>&1; rc=$?; r git-local-clone+fsck rc=$rc; [ $rc = 0 ] || tail -5 /tmp/gito; rm -rf /tmp/gc2
s=$(now); for i in 1 2 3 4 5; do git -C /tmp/gc status --porcelain >/dev/null; done; r gitstatus5 time=$(dt $s)s
rm -rf /tmp/gc

# 4. pip into a venv from the network, then import
rm -rf /tmp/v; s=$(now)
python3 -m venv /tmp/v >/tmp/pipo 2>&1 && /tmp/v/bin/pip install -q requests rich >>/tmp/pipo 2>&1 && /tmp/v/bin/python -c "import requests, rich"
rc=$?; r pip rc=$rc time=$(dt $s)s; [ $rc = 0 ] || tail -5 /tmp/pipo
rm -rf /tmp/v

# 4b. uv (what hermes update uses): default link mode (hardlinks) and copy mode, then import
rm -rf /tmp/uvb /tmp/uvc /tmp/uv1 /tmp/uv2; s=$(now)
python3 -m venv /tmp/uvb >/tmp/uvo 2>&1 && /tmp/uvb/bin/pip install -q uv >>/tmp/uvo 2>&1
UV=/tmp/uvb/bin/uv
UV_CACHE_DIR=/tmp/uvc $UV venv -q /tmp/uv1 >>/tmp/uvo 2>&1 && UV_CACHE_DIR=/tmp/uvc $UV pip install -q --python /tmp/uv1/bin/python requests rich pyyaml >>/tmp/uvo 2>&1 && /tmp/uv1/bin/python -c "import requests, rich, yaml" >>/tmp/uvo 2>&1
rc=$?; r uv-hardlink rc=$rc; [ $rc = 0 ] || tail -5 /tmp/uvo
UV_CACHE_DIR=/tmp/uvc UV_LINK_MODE=copy $UV venv -q /tmp/uv2 >>/tmp/uvo 2>&1 && UV_CACHE_DIR=/tmp/uvc UV_LINK_MODE=copy $UV pip install -q --python /tmp/uv2/bin/python requests rich pyyaml >>/tmp/uvo 2>&1 && /tmp/uv2/bin/python -c "import requests, rich, yaml" >>/tmp/uvo 2>&1
rc=$?; r uv-copy rc=$rc time=$(dt $s)s; [ $rc = 0 ] || tail -5 /tmp/uvo

# 4c. Startup-like load: import many modules from a cold interpreter, 5 times (Hermes' start is mostly this)
s=$(now); for i in 1 2 3 4 5; do /tmp/uv1/bin/python -c "import asyncio, json, email, http.client, sqlite3, logging, ssl, urllib.request, xml.etree.ElementTree, requests, rich.console, yaml" || echo import-failed; done; r import5 time=$(dt $s)s
rm -rf /tmp/uvb /tmp/uvc /tmp/uv1 /tmp/uv2

# 5. Node: large heap work (V8 reserves big address ranges)
s=$(now); node -e 'const a=[];for(let i=0;i<40;i++)a.push(new Array(1e6).fill(i));console.log(a.length)' >/dev/null 2>/tmp/nodeo
rc=$?; r node-heap rc=$rc time=$(dt $s)s; [ $rc = 0 ] || tail -3 /tmp/nodeo

# 6. syscall micro-bench (same as the phone test)
python3 -c "
import os, time, subprocess as sp
def T(n, f, k):
    s = time.perf_counter()
    for _ in range(k): f()
    print('RESULT us', n, round((time.perf_counter() - s) / k * 1e6, 1))
T('getpid', os.getpid, 20000); T('fstat', lambda: os.fstat(0), 5000); T('stat', lambda: os.stat('/usr'), 5000)
T('open+close', lambda: os.close(os.open('/etc/hostname', os.O_RDONLY)), 3000); T('getcwd', os.getcwd, 5000)
T('spawn', lambda: sp.run(['/bin/true']), 100)"
echo DONE
