# Validation

## Overlay watcher reliability (Android 12-15)

Goal: confirm the volume-button overlay triggers reliably across devices/ROMs and hides appropriately.

### Checklist
1. Overlay enabled, accessibility volume key listener enabled, overlay permission granted.
2. Volume buttons pressed while audio is playing in a single app: overlay appears and shows that app.
3. Two apps playing (e.g., music + game video): overlay shows both, counts match active sessions.
4. Auto-hide behavior: overlay hides after the configured timeout.
5. Dismiss outside tap enabled: tapping outside closes overlay.
6. Suppress focused apps list: when focused app is blocked, overlay does not show.
7. Ignore apps list: ignored packages do not appear and do not trigger overlay when they are the only active app.
8. OEM quirks: verify on MIUI/Poco if volume-change observer is delayed or suppressed.
9. Device rotation and lock/unlock: overlay behavior is stable.
10. Foreground app changes while overlay is open: list updates without flicker or duplication.

### Evidence to capture
- Diagnostics screen bundle (use the in-app "Copy bundle" button).
- `adb shell dumpsys audio` (or `media.audio_flinger` if needed).
- `adb logcat` filtered for the app process during the repro window.
- Manual confirmation notes (device/ROM/version, what was validated).

### Suggested capture flow
1. `adb logcat -c`
2. Reproduce steps 2-10 above.
3. `adb logcat -d > overlay_watcher_logcat.txt`
4. `adb shell dumpsys audio > overlay_watcher_dumpsys_audio.txt`
5. Save the diagnostics bundle text from the app.

### Confirmed devices
- POCO F1 — crDroid (Android 15 / API 35): overlay watcher works. Diagnostics bundle captured on 2026-03-22; overlay signals show broadcast + poll activity, overlay last show reason "broadcast". Active packages included `com.android.systemui` and `org.lineageos.lineagesettings`. Hidden API policy remains locked without `WRITE_SECURE_SETTINGS` (expected in current privapp config).

### Script helper
Use `scripts/validate-overlay-watcher.sh` to gather the basic device + log artifacts after reproduction.
