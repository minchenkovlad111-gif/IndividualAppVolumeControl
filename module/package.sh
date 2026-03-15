#!/usr/bin/env sh
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DEFAULT_MODERN_APK="$ROOT_DIR/app/build/outputs/apk/modern/debug/app-modern-debug.apk"
DEFAULT_LEGACY_APK="$ROOT_DIR/app/build/outputs/apk/legacy/debug/app-legacy-debug.apk"
APK_PATH=${1:-"$DEFAULT_MODERN_APK"}
OUT_ZIP=${2:-"$ROOT_DIR/IndividualAppAudioVolumeControl-magisk.zip"}

TARGET_DIR="$SCRIPT_DIR/system/priv-app/IndividualAppAudioVolumeControl"
TARGET_APK="$TARGET_DIR/IndividualAppAudioVolumeControl.apk"

if [ ! -f "$APK_PATH" ]; then
  if [ -f "$DEFAULT_LEGACY_APK" ]; then
    APK_PATH="$DEFAULT_LEGACY_APK"
  else
    echo "APK not found: $APK_PATH" >&2
    exit 1
  fi
fi

mkdir -p "$TARGET_DIR"
cp "$APK_PATH" "$TARGET_APK"

if command -v zip >/dev/null 2>&1; then
  (cd "$SCRIPT_DIR" && zip -r "$OUT_ZIP" .)
  echo "Wrote $OUT_ZIP"
else
  echo "zip not found; install zip or package manually." >&2
  exit 1
fi
