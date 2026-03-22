#!/usr/bin/env bash
set -euo pipefail

out_dir="${1:-overlay-watcher-validation}"
mkdir -p "$out_dir"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH" >&2
  exit 1
fi

echo "Collecting device info..."
adb shell getprop ro.build.version.release > "$out_dir/device_android_release.txt"
adb shell getprop ro.build.version.sdk > "$out_dir/device_sdk.txt"
adb shell getprop ro.product.manufacturer > "$out_dir/device_manufacturer.txt"
adb shell getprop ro.product.model > "$out_dir/device_model.txt"

cat <<'MSG'
Now reproduce the overlay watcher scenarios:
1) Trigger volume overlay while audio plays.
2) Trigger overlay with 2+ active apps.
3) Verify auto-hide + dismiss outside tap.
4) Try focus block + ignore list.
5) If on MIUI/Poco, note observer delays.

Press Enter when done to capture logs.
MSG
read -r

echo "Capturing logcat and dumpsys..."
adb logcat -d -v time > "$out_dir/overlay_watcher_logcat.txt"
adb shell dumpsys audio > "$out_dir/overlay_watcher_dumpsys_audio.txt"

cat <<'MSG'
Saved:
- overlay_watcher_logcat.txt
- overlay_watcher_dumpsys_audio.txt
- device_* metadata

Remember to capture the in-app Diagnostics "Copy bundle" output and save it alongside.
MSG
