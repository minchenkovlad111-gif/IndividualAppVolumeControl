# TODO

1. Add per-app custom marker colors/segments for presets (visual cues and app-specific targets).
2. Validate overlay watcher reliability on Android 12-15 (volume-button detection and overlay timing).
3. Validate required privileged permissions on Android 12-15 devices.
4. Validate hidden API policy keys and behavior on Android 12-15.
5. Verify behavior on Android 12-15 and document compatibility.
6. Legacy support plan for API 16-25 (define degraded feature set and app flavor/module strategy).
7. Group presets + rules engine (co-active apps, time windows, and locations).
8. Call handling: pause/mute playback on incoming calls with best-effort restore; log failures.
9. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
10. Audio routing between apps (virtual output sources and sinks).
11. Virtual microphone + effects (voice changer pipeline).
12. Audio sharing to other devices (LAN streaming + Linux client).
