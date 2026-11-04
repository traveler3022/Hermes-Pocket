#!/usr/bin/env python3
"""Writes app/src/main/res/drawable/brand_<slug>.xml for every R.drawable.brand_<slug> that
ui/design/BrandMarks.kt names, from Simple Icons (CC0-1.0). Run after adding a mark there:

    python3 scripts/brand-icons.py
"""
import pathlib
import re
import urllib.request

VERSION = "16.36.0"
ROOT = pathlib.Path(__file__).resolve().parents[1]
MARKS = ROOT / "app/src/main/java/com/hermes/android/ui/design/BrandMarks.kt"
OUT = ROOT / "app/src/main/res/drawable"
TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- {title} mark, Simple Icons {version} (CC0-1.0, simpleicons.org). The brand is its owner's trademark. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="{d}" />
</vector>
"""


def main() -> None:
    slugs = sorted(set(re.findall(r"R\.drawable\.brand_([a-z0-9]+)", MARKS.read_text())))
    for slug in slugs:
        url = f"https://cdn.jsdelivr.net/npm/simple-icons@{VERSION}/icons/{slug}.svg"
        with urllib.request.urlopen(url, timeout=30) as response:
            svg = response.read().decode("utf-8")
        title = re.search(r"<title>(.*?)</title>", svg).group(1).replace("--", "-")
        paths = re.findall(r'<path d="([^"]+)"', svg)
        if len(paths) != 1 or 'viewBox="0 0 24 24"' not in svg:
            raise SystemExit(f"{slug}: expected one path in a 24x24 viewBox")
        (OUT / f"brand_{slug}.xml").write_text(TEMPLATE.format(title=title, version=VERSION, d=paths[0]))
        print(f"brand_{slug}.xml  {title}")


if __name__ == "__main__":
    main()
