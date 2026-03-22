#!/system/bin/sh
# Capture early boot logs to help diagnose bootloops on device.
# Logs are stored under /data/adb which is device-encrypted (DE) and available early.

LOG_DIR="/data/adb"
LOG_FILE="${LOG_DIR}/individualappaudiovolumecontrol-boot.log"
FALLBACK_DIR="/cache"
FALLBACK_FILE="${FALLBACK_DIR}/individualappaudiovolumecontrol-boot.log"

start_logcat() {
  dir="$1"
  file="$2"
  if [ -d "${dir}" ]; then
    # Rotate to avoid unbounded growth: 4 files, 512KB each.
    logcat -b all -v time -f "${file}" -n 4 -r 512 &
    return 0
  fi
  return 1
}

if ! start_logcat "${LOG_DIR}" "${LOG_FILE}"; then
  start_logcat "${FALLBACK_DIR}" "${FALLBACK_FILE}" || true
fi
