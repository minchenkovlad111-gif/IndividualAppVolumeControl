# TODO

1. Add feature gating + gray-out states for unsupported APIs (per SDK level and missing permissions) across main UI and overlay.
2. Apply tick markers + snap-to-segment behavior in the main UI sliders (match overlay UX).
3. Add per-app custom marker colors/segments for presets (visual cues and app-specific targets).
4. Add overlay auto-refresh preference surfaced in main UI (persisted setting + description).
5. Validate overlay watcher reliability on Android 12-15 (volume-button detection and overlay timing).
6. Validate required privileged permissions on Android 12-15 devices.
7. Validate hidden API policy keys and behavior on Android 12-15.
8. Verify behavior on Android 12-15 and document compatibility.
9. Legacy support plan for API 16-25 (define degraded feature set and app flavor/module strategy).
10. Group presets + rules engine (co-active apps, time windows, and locations).
11. Call handling: pause/mute playback on incoming calls with best-effort restore; log failures.
12. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
13. Audio routing between apps (virtual output sources and sinks).
14. Virtual microphone + effects (voice changer pipeline).
15. Audio sharing to other devices (LAN streaming + Linux client).
