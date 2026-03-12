# TODO

1. Add feature gating + gray-out states for unsupported APIs (per SDK level and missing permissions) across main UI and overlay.
2. Add per-app custom marker colors/segments for presets (visual cues and app-specific targets).
3. Validate overlay watcher reliability on Android 12-15 (volume-button detection and overlay timing).
4. Validate required privileged permissions on Android 12-15 devices.
5. Validate hidden API policy keys and behavior on Android 12-15.
6. Verify behavior on Android 12-15 and document compatibility.
7. Legacy support plan for API 16-25 (define degraded feature set and app flavor/module strategy).
8. Group presets + rules engine (co-active apps, time windows, and locations).
9. Call handling: pause/mute playback on incoming calls with best-effort restore; log failures.
10. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
11. Audio routing between apps (virtual output sources and sinks).
12. Virtual microphone + effects (voice changer pipeline).
13. Audio sharing to other devices (LAN streaming + Linux client).
