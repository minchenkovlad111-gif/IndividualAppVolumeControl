#!/usr/bin/env sh
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DEFAULT_MODERN_RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/modern/release/app-modern-release.apk"
DEFAULT_MODERN_RELEASE_UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk"
DEFAULT_LEGACY_RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/legacy/release/app-legacy-release.apk"
DEFAULT_LEGACY_RELEASE_UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/legacy/release/app-legacy-release-unsigned.apk"
DEFAULT_MODERN_DEBUG_APK="$ROOT_DIR/app/build/outputs/apk/modern/debug/app-modern-debug.apk"
DEFAULT_LEGACY_DEBUG_APK="$ROOT_DIR/app/build/outputs/apk/legacy/debug/app-legacy-debug.apk"
APK_PATH=${1:-"$DEFAULT_MODERN_RELEASE_APK"}
OUT_ZIP=${2:-"$ROOT_DIR/IndividualAppAudioVolumeControl-magisk.zip"}
ASSET_ZIP="$ROOT_DIR/app/src/main/assets/IndividualAppAudioVolumeControl-magisk.zip"
VALIDATOR="$ROOT_DIR/scripts/validate-privapp-permissions.sh"
PERMISSIONS_XML="$SCRIPT_DIR/system/etc/permissions/privapp-permissions-appvolumecontrol.xml"
SOURCE_MANIFEST="$ROOT_DIR/app/src"

case "$OUT_ZIP" in
  /*) ;;
  *) OUT_ZIP="$ROOT_DIR/$OUT_ZIP" ;;
esac

TARGET_DIR="$SCRIPT_DIR/system/priv-app/IndividualAppAudioVolumeControl"
TARGET_APK="$TARGET_DIR/IndividualAppAudioVolumeControl.apk"

if [ ! -f "$APK_PATH" ]; then
  for candidate in \
    "$DEFAULT_MODERN_RELEASE_APK" \
    "$DEFAULT_MODERN_RELEASE_UNSIGNED_APK" \
    "$DEFAULT_LEGACY_RELEASE_APK" \
    "$DEFAULT_LEGACY_RELEASE_UNSIGNED_APK" \
    "$DEFAULT_MODERN_DEBUG_APK" \
    "$DEFAULT_LEGACY_DEBUG_APK"
  do
    if [ -f "$candidate" ]; then
      APK_PATH="$candidate"
      break
    fi
  done
fi

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
cp "$APK_PATH" "$TARGET_APK"

sh "$VALIDATOR" "$TARGET_APK" "$PERMISSIONS_XML" "$SOURCE_MANIFEST"

if command -v zip >/dev/null 2>&1; then
  rm -f "$OUT_ZIP"
  rm -f "$SCRIPT_DIR/$(basename "$OUT_ZIP")"
  (cd "$SCRIPT_DIR" && zip -r "$OUT_ZIP" . -x '*.zip')
  echo "Wrote $OUT_ZIP"
  if [ "$OUT_ZIP" != "$ASSET_ZIP" ]; then
    mkdir -p "$(dirname "$ASSET_ZIP")"
    cp "$OUT_ZIP" "$ASSET_ZIP"
    echo "Synced $ASSET_ZIP"
  fi
else
  echo "zip not found; install zip or package manually." >&2
  exit 1
fi
