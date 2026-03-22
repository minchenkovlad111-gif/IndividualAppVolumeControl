# TODO

## High priority
1. Validate overlay watcher reliability on Android 12-15 (multi-stream volume detection, auto-hide behavior, MIUI/Poco quirks). See `docs/VALIDATION.md`.
2. Validate accessibility-service volume key path on Android 12-15 (ensure overlay shows without consuming volume changes).
3. Re-validate renamed package `fuck.individualappaudiovolumecontrol.droidvendorssuck` installs cleanly as a priv-app via the Magisk/KernelSU module on Android 12-15 with the trimmed `MODIFY_AUDIO_ROUTING`-only privapp allowlist.
4. Validate the `SetupCheckActivity` ANR fix on-device, especially open/close transitions to Settings while audio is playing.
5. Validate required privileged permissions on Android 12-15 devices.
6. Validate hidden API policy keys and behavior on Android 12-15.
7. Verify behavior on Android 12-15 and document compatibility.
8. Validate per-app output device routing on Android 12-15 (Bluetooth/wired/USB/HDMI).
9. Audio focus rules per app (pause/duck/mute) validation on Android 12-15.
10. Validate per-app mute behavior (mute vs mute+pause) on Android 12-15.
11. Optional capture/record fallback when pausing is impossible (investigate legality + storage impact).
12. Audio routing between apps (virtual output sources and sinks).
13. Network audio routing (send/receive per-app audio streams over LAN).
14. Virtual microphone + effects (voice changer pipeline).
15. Audio sharing to other devices (LAN streaming + Linux client).
16. Validate cross-profile (work profile) playback detection and controls on Android 12-15.

## Done (recent)
1. Audited the remaining high-frequency `SharedPreferences.apply()` paths and moved active-app timestamps, location updates, overlay state, volume presets, and notification paging onto a deferred background-commit store to reduce `QueuedWork.waitToFinish()` ANR risk.
1. Added a per-active-app notification presentation mode that keeps one foreground controller notification alive and posts one secondary notification per active app.
1. Fixed slider tick/percentage guide alignment so the visible 10% guides use the same horizontal insets as the actual `SeekBar` track in the main UI and overlay.
1. Added playback-controller notification persistence diagnostics plus a forced foreground-notification refresh path, so Diagnostics can show service state, notification enablement, and channel importance.
1. Investigated a real custom notification volume slider per app; `RemoteViews` drag constraints make it impractical, so the notification now uses per-app visual level bars plus discrete tap controls and the limit is documented.
1. Made notification presentation configurable between multi-app pages and a single-app pager, with `Next` / `Prev` single-notification paging kept as the fallback mode.
1. Limited root fallback to the no-`INTERACT_ACROSS_USERS` case and added a manual cross-profile scan button in the main UI.
1. Reduced root fallback churn by caching `su` checks longer and reusing root `dumpsys` results across short UI/overlay refresh windows.
1. Made manual `Show overlay now` display a test preview card when no app is playing audio, without affecting normal volume-button overlay behavior.
1. Added search fields to Setup Check and Diagnostics to filter long report/checklist rows in place.
1. Added an overlay package overview dialog on app-name tap, with a confirmation-backed ignore action scoped to the tapped profile when available.
1. Added `com.android.location.fused` to the overlay ignore defaults.
1. Validated overlay watcher reliability on POCO F1 (crDroid, Android 15 / API 35). Diagnostics bundle recorded in `docs/VALIDATION.md`.
1. Renamed the source tree under `app/src/main`, `app/src/modern`, and `app/src/legacy` to match the package namespace `fuck.individualappaudiovolumecontrol.droidvendorssuck`.
1. Pinned selected apps to the top of searchable multi-select app dialogs (overlay ignore/block lists and rules app picker).
1. Renamed the Android app package/namespace to `fuck.individualappaudiovolumecontrol.droidvendorssuck` while keeping folder structure and Magisk mount paths unchanged.
2. Trimmed the module privapp allowlist back to `android.permission.MODIFY_AUDIO_ROUTING` only to reduce boot-loop risk on enforcing Android 15 ROMs and KernelSU installs.
3. Switched Magisk packaging to a signed release APK and added a privapp allowlist validator to catch package/permission drift before flashing.
4. Removed unnecessary `FOREGROUND_SERVICE` privapp grant and stripped AndroidX startup/profileinstaller components from the app manifest merge.
5. Synced the bundled in-app module asset from the main packaged ZIP to avoid divergence between install paths.
6. Tightened the privapp validator to reject stale XML entries as well as missing reviewed grants.
7. Switched privapp validation to derive expected allowlisted permissions from the source manifest’s protected-permission markers.
8. Made privapp validation scan all manifests under `app/src` and reject allowlisted permissions not requested by the APK.
9. Removed Play Services geofencing dependency and stubbed geofence handling for the modern build.
10. Removed the geofencing toggle from the Rules UI since Play Services geofencing is gone.
11. Removed geofencing preference storage and diagnostics output.
12. Removed remaining geofencing code paths and GeofenceRuleManager class.
1. Hardcoded Face Unlock Service package `co.aospa.sense` into the overlay ignore defaults.
1. Added next/prev paging for notification controls when more apps are active.
1. Reworked overlay header controls into a single dropdown menu.
2. Added an overlay pause toggle (menu + watcher notification) to suppress overlay until resumed.
3. Moved overlay row controls to the left of sliders to reduce height.
1. Notification now uses OLED styling, adds 100% button, and hides ignored apps.
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
