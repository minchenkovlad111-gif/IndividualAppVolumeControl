#!/system/bin/sh
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

APK_PATH=${1:-"$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"}
OUT_ZIP=${2:-"$ROOT_DIR/AppVolumeControl-magisk.zip"}

TARGET_DIR="$SCRIPT_DIR/system/priv-app/AppVolumeControl"
TARGET_APK="$TARGET_DIR/AppVolumeControl.apk"

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
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
