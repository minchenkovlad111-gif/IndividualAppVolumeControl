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

PKG="fuck.individualappaudiovolumecontrol.droidvendorssuck"
TAG="IndividualAppAudioVolumeControl"
PERMS="
android.permission.INTERACT_ACROSS_USERS
android.permission.INTERACT_ACROSS_USERS_FULL
"

wait_for_boot() {
  while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done
}

list_users() {
  cmd package list users 2>/dev/null | sed -n 's/.*UserInfo{\([0-9]*\):.*/\1/p'
}

log_note() {
  if command -v log >/dev/null 2>&1; then
    log -t "${TAG}" "$*"
  fi
}

check_perm() {
  user_id="$1"
  perm="$2"
  if command -v cmd >/dev/null 2>&1; then
    cmd package check-permission "${perm}" "${PKG}" "${user_id}" 2>/dev/null | grep -q '^granted$'
    return $?
  fi
  return 1
}

grant_perm_user() {
  user_id="$1"
  perm="$2"

  if command -v pm >/dev/null 2>&1; then
    pm grant --user "${user_id}" "${PKG}" "${perm}" >/dev/null 2>&1 || true
  fi

  if command -v cmd >/dev/null 2>&1; then
    cmd package grant --user "${user_id}" "${PKG}" "${perm}" >/dev/null 2>&1 || true
  fi

  if check_perm "${user_id}" "${perm}"; then
    log_note "permission granted user=${user_id} perm=${perm}"
  else
    log_note "permission not granted user=${user_id} perm=${perm} (privapp allowlist still required on most ROMs)"
  fi
}

grant_perms_any_case() {
  if ! command -v cmd >/dev/null 2>&1 && ! command -v pm >/dev/null 2>&1; then
    return 0
  fi

  users="$(list_users)"
  if [ -z "${users}" ]; then
    users="0"
  fi

  for user_id in ${users}; do
    for perm in ${PERMS}; do
      grant_perm_user "${user_id}" "${perm}"
    done
  done
}

wait_for_boot
grant_perms_any_case
