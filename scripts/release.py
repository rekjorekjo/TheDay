#!/usr/bin/env python3

import argparse
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="Package release APK")
    parser.add_argument("--apk", required=True, help="Path to release APK")
    parser.add_argument("--notes", required=True, help="Path to release notes file")
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parent.parent

    version_code, version_name, tag_name = read_version_properties(project_root)

    apk_path = Path(args.apk).resolve()
    validate_apk(apk_path)

    notes_path = Path(args.notes).resolve()
    release_notes = read_release_notes(notes_path)

    dist_dir = project_root / "dist"
    dist_dir.mkdir(parents=True, exist_ok=True)

    target_apk_name = f"TheDay-v{version_name}.apk"
    target_apk_path = dist_dir / target_apk_name

    shutil.copy2(apk_path, target_apk_path)

    apk_size, apk_sha256 = compute_apk_info(target_apk_path)

    latest_json = {
        "schemaVersion": 1,
        "tagName": tag_name,
        "versionName": version_name,
        "versionCode": version_code,
        "releaseNotes": release_notes,
        "apk": {
            "name": target_apk_name,
            "url": f"https://github.com/rekjorekjo/TheDay/releases/download/{tag_name}/{target_apk_name}",
            "size": apk_size,
            "sha256": apk_sha256,
        },
    }

    latest_json_path = dist_dir / "latest.json"
    with open(latest_json_path, "w", encoding="utf-8") as f:
        json.dump(latest_json, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"Generated: {target_apk_path}")
    print(f"Generated: {latest_json_path}")
    print(f"Version: {version_name} ({version_code})")
    print(f"Size: {apk_size} bytes")
    print(f"SHA-256: {apk_sha256}")


def read_version_properties(project_root: Path) -> tuple[int, str, str]:
    version_file = project_root / "version.properties"
    if not version_file.is_file():
        sys.exit(f"Error: version.properties not found: {version_file}")

    with open(version_file, "r", encoding="utf-8") as f:
        content = f.read()

    version_code_match = re.search(r"^versionCode\s*=\s*(\d+)\s*$", content, re.MULTILINE)
    if not version_code_match:
        sys.exit("Error: version.properties: versionCode must be a positive integer")

    version_code = int(version_code_match.group(1))
    if version_code <= 0:
        sys.exit("Error: version.properties: versionCode must be a positive integer")

    version_name_match = re.search(r"^versionName\s*=\s*(.+?)\s*$", content, re.MULTILINE)
    if not version_name_match:
        sys.exit("Error: version.properties: versionName must use X.Y.Z")

    version_name = version_name_match.group(1)
    if not re.match(r"^\d+\.\d+\.\d+$", version_name):
        sys.exit("Error: version.properties: versionName must use X.Y.Z")

    tag_name = f"v{version_name}"

    return version_code, version_name, tag_name


def validate_apk(apk_path: Path) -> None:
    if not apk_path.is_file():
        sys.exit(f"Error: APK not found: {apk_path}")

    file_size = apk_path.stat().st_size
    if file_size == 0:
        sys.exit(f"Error: APK file is empty: {apk_path}")

    max_size = 200 * 1024 * 1024  # 200 MiB
    if file_size > max_size:
        sys.exit(f"Error: APK file exceeds 200 MiB: {apk_path}")

    apk_name_lower = apk_path.name.lower()
    if "debug" in apk_name_lower or "unsigned" in apk_name_lower:
        sys.exit(f"Error: APK appears to be debug or unsigned: {apk_path}")


def read_release_notes(notes_path: Path) -> str:
    if not notes_path.is_file():
        sys.exit(f"Error: Release notes not found: {notes_path}")

    with open(notes_path, "r", encoding="utf-8") as f:
        content = f.read(20001)

    if len(content) > 20000:
        sys.exit(f"Error: Release notes exceed 20,000 characters: {notes_path}")

    return content


def compute_apk_info(apk_path: Path) -> tuple[int, str]:
    file_size = apk_path.stat().st_size

    sha256_hash = hashlib.sha256()
    with open(apk_path, "rb") as f:
        while chunk := f.read(8192):
            sha256_hash.update(chunk)

    sha256_hex = sha256_hash.hexdigest()

    return file_size, sha256_hex


if __name__ == "__main__":
    main()