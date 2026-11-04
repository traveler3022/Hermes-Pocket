import collections
import statistics
import sys

data = collections.defaultdict(list)
bad = []
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    parts = line.split()
    if len(parts) >= 5 and parts[0] == "RESULT":
        _, label, test, value, rc = parts[:5]
        data[(label, test)].append(float(value))
        if rc != "rc=0":
            bad.append(line.strip())
labels = ["chroot", "proot_app", "proot_no0", "proot_noseccomp"]
tests = sorted({t for _, t in data}, key=lambda t: (t.endswith("_us"), t))
print(f"{'test':16}" + "".join(f"{l:>22}" for l in labels))
for t in tests:
    base = statistics.median(data[("chroot", t)]) if data[("chroot", t)] else None
    row = f"{t:16}"
    for l in labels:
        v = data[(l, t)]
        if not v:
            row += f"{'-':>22}"
            continue
        m = statistics.median(v)
        ratio = f" ({m / base:.1f}x)" if base else ""
        row += f"{m:>14.1f}{ratio:>8}"
    print(row)
print("units: ms, *_us = microseconds per call; (Nx) = vs chroot")
for b in bad:
    print("NONZERO:", b)
