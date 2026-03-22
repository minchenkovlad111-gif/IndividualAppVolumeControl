#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DEFAULT_MODERN_RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/modern/release/app-modern-release.apk"
DEFAULT_MODERN_RELEASE_UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/modern/release/app-modern-release-unsigned.apk"
DEFAULT_MODERN_DEBUG_APK="$ROOT_DIR/app/build/outputs/apk/modern/debug/app-modern-debug.apk"

APK_PATH=${1:-"$DEFAULT_MODERN_RELEASE_APK"}
OUT_ZIP=${2:-"$ROOT_DIR/IndividualAppAudioVolumeControl-system-app-probe.zip"}

case "$OUT_ZIP" in
  /*) ;;
  *) OUT_ZIP="$ROOT_DIR/$OUT_ZIP" ;;
esac

if [ ! -f "$APK_PATH" ]; then
  for candidate in \
    "$DEFAULT_MODERN_RELEASE_APK" \
    "$DEFAULT_MODERN_RELEASE_UNSIGNED_APK" \
    "$DEFAULT_MODERN_DEBUG_APK"
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

if ! command -v zip >/dev/null 2>&1; then
  echo "zip not found; install zip or package manually." >&2
  exit 1
fi

STAGE_DIR=$(mktemp -d /tmp/appvolumecontrol-probe.XXXXXX)
cleanup() {
  rm -rf "$STAGE_DIR"
}
trap cleanup EXIT INT TERM

cp -R "$SCRIPT_DIR/." "$STAGE_DIR/"
rm -rf "$STAGE_DIR/system/priv-app"
rm -f "$STAGE_DIR/system/etc/permissions/privapp-permissions-appvolumecontrol.xml"
mkdir -p "$STAGE_DIR/system/app/IndividualAppAudioVolumeControl"
cp "$APK_PATH" "$STAGE_DIR/system/app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk"

rm -f "$OUT_ZIP"
(cd "$STAGE_DIR" && zip -r "$OUT_ZIP" . -x '*.zip')
echo "Wrote $OUT_ZIP"
