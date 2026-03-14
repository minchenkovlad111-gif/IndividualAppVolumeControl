# TODO

1. Validate overlay watcher reliability on Android 12-15 (multi-stream volume detection, auto-hide behavior, MIUI/Poco quirks).
2. Validate accessibility-service volume key path on Android 12-15 (ensure overlay shows without consuming volume changes).
3. Investigate intermittent white screen on resume/reopen (collect repro steps + logcat).
4. Validate required privileged permissions on Android 12-15 devices.
5. Validate hidden API policy keys and behavior on Android 12-15.
6. Verify behavior on Android 12-15 and document compatibility.
7. Validate per-app output device routing on Android 12-15 (Bluetooth/wired/USB/HDMI).
8. Audio focus rules per app (pause/duck/mute) validation on Android 12-15.
9. Validate per-app mute behavior (mute vs mute+pause) on Android 12-15.
10. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
11. Audio routing between apps (virtual output sources and sinks).
12. Network audio routing (send/receive per-app audio streams over LAN).
13. Virtual microphone + effects (voice changer pipeline).
14. Audio sharing to other devices (LAN streaming + Linux client).

## Done (recent)
1. Added safe active playback config retrieval with diagnostics logging to avoid crashes when permissions are missing.
2. Added overlay/accessibility status to Setup Check + Diagnostics, with quick action buttons to open settings.
3. Added overlay addView failure logging and accessibility-triggered overlay start error toast.
4. Ensured overlay watcher restarts on resume when enabled; added notification action toast when controls are disabled.
1. Rules editor enhancements: geofencing for location rules.
2. Per-app audio attributes overrides UI (usage/content type filters).
3. Avoided Android 14+ boot-time FGS crash by skipping boot-start for foreground services.
4. Added Android 14+ boot notification with user-initiated start action for services.
5. Fixed Manage Apps layout so action buttons stay within the visible area.
