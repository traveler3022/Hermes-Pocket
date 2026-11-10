import os
import sys
import time

label = sys.argv[1]
fd = os.open("/etc/passwd", os.O_RDONLY)


def bench(name, fn, n):
    start = time.perf_counter()
    for _ in range(n):
        fn()
    print(f"RESULT {label} {name}_us {(time.perf_counter() - start) / n * 1e6:.2f} rc=0")


bench("getpid", os.getpid, 50000)
bench("fstat", lambda: os.fstat(fd), 20000)
bench("stat_deep", lambda: os.stat("/root/.hermes/hermes-agent/venv/lib"), 20000)
bench("open_close", lambda: os.close(os.open("/etc/passwd", os.O_RDONLY)), 20000)
