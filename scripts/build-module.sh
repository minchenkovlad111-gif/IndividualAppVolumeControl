#!/usr/bin/env sh
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

APK_PATH="$ROOT_DIR/app/build/outputs/apk/modern/release/app-modern-release.apk"
OUT_ZIP="$ROOT_DIR/IndividualAppAudioVolumeControl-magisk.zip"

echo "Building APK..."
(cd "$ROOT_DIR" && ./gradlew :app:assembleModernRelease)

echo "Packaging Magisk module..."
"$ROOT_DIR/module/package.sh" "$APK_PATH" "$OUT_ZIP"

echo "Done: $OUT_ZIP"
