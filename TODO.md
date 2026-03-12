# TODO

1. Validate overlay watcher reliability on Android 12-15 (multi-stream volume detection, auto-hide behavior, MIUI/Poco quirks).
2. Investigate intermittent white screen on resume/reopen (collect repro steps + logcat).
3. Validate required privileged permissions on Android 12-15 devices.
4. Validate hidden API policy keys and behavior on Android 12-15.
5. Verify behavior on Android 12-15 and document compatibility.
6. Validate per-app output device routing on Android 12-15 (Bluetooth/wired/USB/HDMI).
7. Rules editor enhancements: geofencing for location rules. (done)
8. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
9. Audio routing between apps (virtual output sources and sinks).
10. Network audio routing (send/receive per-app audio streams over LAN).
11. Virtual microphone + effects (voice changer pipeline).
12. Audio sharing to other devices (LAN streaming + Linux client).
