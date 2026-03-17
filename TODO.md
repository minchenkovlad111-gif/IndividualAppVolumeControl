# TODO

1. Rework overlay header controls into a single dropdown menu (move checkboxes/buttons into the menu).
2. Add an overlay pause toggle that suppresses overlay until re-enabled via notification or app.
3. Move overlay controls to the left of sliders to reduce height.
4. Improve notification controls: add 100% button, custom styling/colors, and next/prev paging for more apps.
5. Ensure overlay ignore list is applied to notification entries (hide ignored apps like System UI).
6. Investigate why Face Unlock Service package is unknown; add a default ignore package once identified.

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
1. Overlay background set to transparent with clear white borders for root and rows.
1. Overlay app names now open App Info on tap (long-press still copies package).
1. Added overlay ignore list (defaults: System UI + Face Unlock label match) and suppress overlay when only ignored apps are active.
1. Added overlay quick actions to block/unblock the focused app (usage access required).
1. Added Usage Access gate/check + diagnostics line for focused-app overlay suppression reliability.
1. Renamed app display label to IndividualAppVolumeControl (including accessibility label).
2. Added focused-app blocklist search/filter in the overlay settings dialog.
3. Added usage-stats foreground fallback for overlay suppression when accessibility focus is stale.
1. Added overlay gating options (only when audio plays, focused-app blocklist, snooze timer).
2. Compact overlay watcher notification text.
3. Updated accessibility service label to include the app name.
1. Added safe active playback config retrieval with diagnostics logging to avoid crashes when permissions are missing.
2. Added overlay/accessibility status to Setup Check + Diagnostics, with quick action buttons to open settings.
3. Added overlay addView failure logging and accessibility-triggered overlay start error toast.
4. Ensured overlay watcher restarts on resume when enabled; added notification action toast when controls are disabled.
1. Rules editor enhancements: geofencing for location rules.
2. Per-app audio attributes overrides UI (usage/content type filters).
3. Avoided Android 14+ boot-time FGS crash by skipping boot-start for foreground services.
4. Added Android 14+ boot notification with user-initiated start action for services.
5. Fixed Manage Apps layout so action buttons stay within the visible area.
