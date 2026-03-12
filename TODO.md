# TODO

1. Validate overlay watcher reliability on Android 12-15 (volume-button detection and overlay timing).
2. Validate required privileged permissions on Android 12-15 devices.
3. Validate hidden API policy keys and behavior on Android 12-15.
4. Verify behavior on Android 12-15 and document compatibility.
5. Validate per-app output device routing on Android 12-15 (Bluetooth/wired/USB/HDMI).
6. Legacy support plan for API 16-25 (define degraded feature set and app flavor/module strategy).
7. Group presets + rules engine (co-active apps, time windows, and locations).
8. Call handling: pause/mute playback on incoming calls with best-effort restore; log failures.
9. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
10. Audio routing between apps (virtual output sources and sinks).
11. Network audio routing (send/receive per-app audio streams over LAN).
12. Virtual microphone + effects (voice changer pipeline).
13. Audio sharing to other devices (LAN streaming + Linux client).
