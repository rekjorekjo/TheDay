#!/usr/bin/env python3

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

MAX_APK_SIZE = 200 * 1024 * 1024
REPOSITORY_RELEASE_BASE = "https://github.com/rekjorekjo/TheDay/releases/download"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build/package The Day Classic and Glass release APKs",
    )
    parser.add_argument("--notes", required=True, help="Path to release notes file")
    parser.add_argument(
        "--build",
        action="store_true",
        help="Build both signed release APKs with Gradle before packaging",
    )
    parser.add_argument("--classic-apk", help="Path to a signed Classic release APK")
    parser.add_argument("--glass-apk", help="Path to a signed Glass release APK")
    parser.add_argument(
        "--apk",
        help="Deprecated alias for --classic-apk (kept for Classic-only compatibility)",
    )
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parent.parent
    version_code, version_name, tag_name = read_version_properties(project_root)

    notes_path = Path(args.notes).resolve()
    release_notes = read_release_notes(notes_path)

    if args.apk and args.classic_apk:
        sys.exit("Error: use either --apk or --classic-apk, not both")

    classic_arg = args.classic_apk or args.apk
    glass_arg = args.glass_apk

    if args.apk:
        print("Warning: --apk is deprecated; use --classic-apk instead", file=sys.stderr)

    if args.build:
        if classic_arg or glass_arg:
            sys.exit("Error: --build cannot be combined with --classic-apk/--glass-apk/--apk")
        ensure_release_signing_config(project_root)
        ensure_flutter_module_bootstrapped(project_root)
        build_release_apks(project_root)
        classic_path = discover_built_apk(project_root, "classic")
        glass_path = discover_built_apk(project_root, "glass")
    else:
        if not classic_arg and not glass_arg:
            sys.exit(
                "Error: provide --build, or at least one of --classic-apk / --glass-apk"
            )
        classic_path = Path(classic_arg).resolve() if classic_arg else None
        glass_path = Path(glass_arg).resolve() if glass_arg else None

    dist_dir = project_root / "dist"
    dist_dir.mkdir(parents=True, exist_ok=True)

    generated = []
    if classic_path is not None:
        generated.extend(
            package_edition(
                edition="classic",
                source_apk=classic_path,
                dist_dir=dist_dir,
                version_code=version_code,
                version_name=version_name,
                tag_name=tag_name,
                release_notes=release_notes,
            )
        )

    if glass_path is not None:
        generated.extend(
            package_edition(
                edition="glass",
                source_apk=glass_path,
                dist_dir=dist_dir,
                version_code=version_code,
                version_name=version_name,
                tag_name=tag_name,
                release_notes=release_notes,
            )
        )

    print("\nRelease package ready:")
    for path in generated:
        print(f"  {path}")
    print(f"Version: {version_name} ({version_code})")
    print(f"Tag: {tag_name}")


def ensure_release_signing_config(project_root: Path) -> None:
    properties_file = project_root / "keystore.properties"
    if not properties_file.is_file():
        sys.exit(
            "Error: --build requires keystore.properties so Gradle can sign both release APKs. "
            "Copy keystore.properties.example, fill in your release key details, and keep the file private."
        )


def ensure_flutter_module_bootstrapped(project_root: Path) -> None:
    include_script = project_root / "glass_flutter" / ".android" / "include_flutter.groovy"
    if not include_script.is_file():
        sys.exit(
            "Error: Glass Flutter module is not bootstrapped. "
            "Run scripts/setup_flutter_glass.ps1 on Windows (or .sh on macOS/Linux) "
            "before using --build."
        )


def build_release_apks(project_root: Path) -> None:
    wrapper = project_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        sys.exit(f"Error: Gradle wrapper not found: {wrapper}")

    if os.name != "nt" and not os.access(wrapper, os.X_OK):
        wrapper.chmod(wrapper.stat().st_mode | 0o111)

    command = [
        str(wrapper),
        ":app:assembleClassicRelease",
        ":app:assembleGlassRelease",
    ]
    print("Building signed Classic + Glass release APKs...")
    try:
        subprocess.run(command, cwd=project_root, check=True)
    except subprocess.CalledProcessError as exc:
        sys.exit(f"Error: Gradle release build failed with exit code {exc.returncode}")


def discover_built_apk(project_root: Path, edition: str) -> Path:
    output_dir = project_root / "app" / "build" / "outputs" / "apk" / edition / "release"
    if not output_dir.is_dir():
        sys.exit(f"Error: release output directory not found: {output_dir}")

    candidates = sorted(
        output_dir.glob("*.apk"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    if not candidates:
        sys.exit(f"Error: no APK found in {output_dir}")

    signed = [
        path
        for path in candidates
        if "unsigned" not in path.name.lower() and "debug" not in path.name.lower()
    ]
    if len(signed) != 1:
        names = ", ".join(path.name for path in candidates)
        if not signed:
            sys.exit(
                f"Error: no signed {edition} release APK found in {output_dir}. "
                f"Found: {names}. Check keystore.properties and Gradle signingConfig."
            )
        sys.exit(
            f"Error: multiple signed {edition} release APKs found in {output_dir}: "
            + ", ".join(path.name for path in signed)
        )

    return signed[0].resolve()


def package_edition(
    *,
    edition: str,
    source_apk: Path,
    dist_dir: Path,
    version_code: int,
    version_name: str,
    tag_name: str,
    release_notes: str,
) -> list[Path]:
    validate_apk(source_apk)

    if edition == "classic":
        target_apk_name = f"TheDay-v{version_name}.apk"
        manifest_name = "latest.json"
    elif edition == "glass":
        target_apk_name = f"TheDay-Glass-v{version_name}.apk"
        manifest_name = "latest-glass.json"
    else:
        raise ValueError(f"Unsupported edition: {edition}")

    target_apk_path = dist_dir / target_apk_name
    shutil.copy2(source_apk, target_apk_path)
    apk_size, apk_sha256 = compute_apk_info(target_apk_path)

    latest_json = {
        "schemaVersion": 1,
        "edition": edition,
        "tagName": tag_name,
        "versionName": version_name,
        "versionCode": version_code,
        "releaseNotes": release_notes,
        "apk": {
            "name": target_apk_name,
            "url": f"{REPOSITORY_RELEASE_BASE}/{tag_name}/{target_apk_name}",
            "size": apk_size,
            "sha256": apk_sha256,
        },
    }

    manifest_path = dist_dir / manifest_name
    with open(manifest_path, "w", encoding="utf-8") as file:
        json.dump(latest_json, file, ensure_ascii=False, indent=2)
        file.write("\n")

    print(f"Generated {edition}: {target_apk_path.name}")
    print(f"Generated {edition}: {manifest_path.name}")
    print(f"  Size: {apk_size} bytes")
    print(f"  SHA-256: {apk_sha256}")

    return [target_apk_path, manifest_path]


def read_version_properties(project_root: Path) -> tuple[int, str, str]:
    version_file = project_root / "version.properties"
    if not version_file.is_file():
        sys.exit(f"Error: version.properties not found: {version_file}")

    with open(version_file, "r", encoding="utf-8") as file:
        content = file.read()

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

    return version_code, version_name, f"v{version_name}"


def validate_apk(apk_path: Path) -> None:
    if not apk_path.is_file():
        sys.exit(f"Error: APK not found: {apk_path}")

    file_size = apk_path.stat().st_size
    if file_size == 0:
        sys.exit(f"Error: APK file is empty: {apk_path}")
    if file_size > MAX_APK_SIZE:
        sys.exit(f"Error: APK file exceeds 200 MiB: {apk_path}")

    apk_name_lower = apk_path.name.lower()
    if "debug" in apk_name_lower or "unsigned" in apk_name_lower:
        sys.exit(f"Error: APK appears to be debug or unsigned: {apk_path}")


def read_release_notes(notes_path: Path) -> str:
    if not notes_path.is_file():
        sys.exit(f"Error: Release notes not found: {notes_path}")

    with open(notes_path, "r", encoding="utf-8") as file:
        content = file.read(20001)

    if len(content) > 20000:
        sys.exit(f"Error: Release notes exceed 20,000 characters: {notes_path}")

    return content


def compute_apk_info(apk_path: Path) -> tuple[int, str]:
    file_size = apk_path.stat().st_size
    sha256_hash = hashlib.sha256()
    with open(apk_path, "rb") as file:
        while chunk := file.read(8192):
            sha256_hash.update(chunk)
    return file_size, sha256_hash.hexdigest()


if __name__ == "__main__":
    main()
