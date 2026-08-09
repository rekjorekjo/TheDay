#!/usr/bin/env python3
"""Compare APK composition without requiring Android SDK tools.

Usage:
  python scripts/apk_size_report.py OLD.apk NEW.apk

Reports on-disk APK size, compressed ZIP contribution by broad section, and
largest entry deltas. This is diagnostic only; it does not modify APKs.
"""
from __future__ import annotations

import sys
import zipfile
from collections import defaultdict
from pathlib import Path


def fmt(n: int) -> str:
    sign = "-" if n < 0 else ""
    n = abs(n)
    if n >= 1024 * 1024:
        return f"{sign}{n / (1024 * 1024):.2f} MiB"
    if n >= 1024:
        return f"{sign}{n / 1024:.1f} KiB"
    return f"{sign}{n} B"


def bucket(name: str) -> str:
    if name.startswith("lib/"):
        return "native libs (lib/)"
    if name.startswith("assets/"):
        return "assets/"
    if name.startswith("res/"):
        return "res/"
    if name.startswith("META-INF/"):
        return "META-INF/"
    if name == "resources.arsc":
        return "resources.arsc"
    if name.startswith("classes") and name.endswith(".dex"):
        return "DEX"
    return "other"


def inspect(path: Path):
    if not path.is_file():
        raise SystemExit(f"APK not found: {path}")
    groups = defaultdict(lambda: [0, 0])  # compressed, uncompressed
    entries: dict[str, tuple[int, int]] = {}
    with zipfile.ZipFile(path) as zf:
        for info in zf.infolist():
            groups[bucket(info.filename)][0] += info.compress_size
            groups[bucket(info.filename)][1] += info.file_size
            entries[info.filename] = (info.compress_size, info.file_size)
    return path.stat().st_size, groups, entries


def print_one(label: str, path: Path, result) -> None:
    disk, groups, _ = result
    print(f"\n{label}: {path}")
    print(f"APK on disk: {fmt(disk)}")
    for name, (compressed, raw) in sorted(groups.items(), key=lambda kv: kv[1][0], reverse=True):
        print(f"  {name:<22} {fmt(compressed):>10} compressed   {fmt(raw):>10} raw")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python scripts/apk_size_report.py OLD.apk NEW.apk")
    old_path, new_path = map(lambda s: Path(s).resolve(), sys.argv[1:])
    old = inspect(old_path)
    new = inspect(new_path)
    print_one("OLD", old_path, old)
    print_one("NEW", new_path, new)

    old_disk, old_groups, old_entries = old
    new_disk, new_groups, new_entries = new
    print(f"\nAPK delta: {fmt(new_disk - old_disk)}")
    print("Group deltas (compressed):")
    all_groups = set(old_groups) | set(new_groups)
    for name in sorted(all_groups, key=lambda n: new_groups[n][0] - old_groups[n][0], reverse=True):
        delta = new_groups[name][0] - old_groups[name][0]
        if delta:
            print(f"  {name:<22} {fmt(delta):>10}")

    print("\nLargest entry deltas (compressed):")
    deltas = []
    for name in set(old_entries) | set(new_entries):
        old_size = old_entries.get(name, (0, 0))[0]
        new_size = new_entries.get(name, (0, 0))[0]
        if new_size != old_size:
            deltas.append((new_size - old_size, name, old_size, new_size))
    for delta, name, old_size, new_size in sorted(deltas, reverse=True)[:25]:
        print(f"  {fmt(delta):>10}  {name}  ({fmt(old_size)} -> {fmt(new_size)})")


if __name__ == "__main__":
    main()
