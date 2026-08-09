#!/usr/bin/env sh
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MODULE_ROOT="$REPO_ROOT/glass_flutter"

if ! command -v flutter >/dev/null 2>&1; then
    echo "Flutter SDK was not found on PATH. Install the current Flutter stable SDK (3.44 or newer), then run this script again." >&2
    exit 1
fi

flutter --version

if [ ! -f "$MODULE_ROOT/.metadata" ]; then
    BOOTSTRAP=$(mktemp -d "${TMPDIR:-/tmp}/theday-glass-bootstrap.XXXXXX")
    trap 'rm -rf "$BOOTSTRAP"' EXIT HUP INT TERM
    echo "Bootstrapping Flutter module metadata..."
    flutter create --no-pub --template module --org io.github.thedayapp --project-name glass_flutter "$BOOTSTRAP"
    cp "$BOOTSTRAP/.metadata" "$MODULE_ROOT/.metadata"
    rm -rf "$BOOTSTRAP"
    trap - EXIT HUP INT TERM
fi

cd "$MODULE_ROOT"
flutter pub get
flutter analyze

if [ ! -f "$MODULE_ROOT/.android/include_flutter.groovy" ]; then
    echo "Flutter did not generate .android/include_flutter.groovy. Run flutter doctor and try again." >&2
    exit 1
fi

echo "The Day Glass Flutter module is ready. Sync the Android project, select glassDebug, and run."
