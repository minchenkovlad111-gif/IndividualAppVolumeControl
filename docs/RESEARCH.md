# Research notes

## Known solutions
- App Volume Control + Audio HeadQuarter: root approach using a companion Magisk module.
- VolumeManager: uses hidden audio APIs (AudioPlaybackConfiguration + IPlayer.setVolume) via Shizuku.
- SoundMaster (ShizuTools): per-app volume using hidden APIs and Shizuku.
- Samsung SoundAssistant: OEM implementation for per-app volume.

## Implications
- The most reliable path on A15 is a privileged app with hidden API access.
- AudioPlaybackConfiguration callbacks provide the per-player hook for volume control.
- Android provides hidden API enforcement settings (`hidden_api_policy`, `hidden_api_policy_pre_p_apps`, `hidden_api_policy_p_apps`), but relying on `WRITE_SECURE_SETTINGS` in a shipped priv-app module increases compatibility risk and should stay optional.
- Cross-profile playback detection requires `INTERACT_ACROSS_USERS` and a matching privapp allowlist entry; keep it optional to avoid boot regressions on enforcing ROMs.
- On enforcing ROMs, the privapp allowlist is a boot-critical surface. Minimize requested privileged permissions first, then add grants back only with device validation.
- Captured ANR evidence on-device shows `ActivityThread.handleStopActivity()` can block in `QueuedWork.waitToFinish()` if the app is still draining `SharedPreferences.apply()` writes; high-frequency diagnostics writes should be coalesced and persisted off that path.
- Notification `RemoteViews` can display progress and click targets, but do not provide a reliable draggable `SeekBar` interaction path for custom per-app sliders in the notification shade. The practical fallback is a visual level bar plus discrete tap actions and an overlay shortcut for full sliders.

## Validation checklist
- Verify hidden API access works on Android 12, 13, 14, 15 (VMRuntime and Settings.Global paths).
- Confirm `AudioPlaybackConfiguration.getPlayerProxy()` and `IPlayer.setVolume()` are callable on each target.
- Validate required permissions on at least one AOSP-based and one OEM device.
- Confirm privileged detection uses both `flags` and `privateFlags`, and that the APK is not overridden by a /data/app update.
- Capture logs from the diagnostics screen and document known failures.
- Use "Retry hidden API" in Diagnostics to capture VMRuntime/policy error details per device.
- Use "Copy bundle" in Diagnostics to capture a single shareable payload during testing.
- Overlay watcher validation steps live in `docs/VALIDATION.md`.

## UX ideas to validate
- Volume-button overlay listing active apps (AudioHQ-style) for quick control.
- Overlay watcher reliability across Android 12-15 (volume change observer vs. volume keys).
- Quick settings tile for jumping into the overlay.
- Profile-aware app lists for work/personal separation.
- Feature gating: gray out unavailable features per SDK + permission state.

## Additional audio settings to consider
- Audio focus policy per app (pause/duck/mute, or ignore focus while muted).
- Auto-resume behavior after pause actions (opt-in, guard against resuming user-paused playback).
- Per-app audio attributes overrides (usage/content type) to affect routing/interruptions.
- Per-app stream mapping (force music vs. notification stream).
- Per-app loudness limiter/compressor (keep levels consistent).
- Per-app balance/mono mix for accessibility.
- Per-app EQ or bass boost (if feasible with DSP stack).
- Bluetooth absolute volume toggle per app or per device.
- Per-app spatial audio enable/disable where available.

## Volume key detection research (local project survey)
- Foreground-only interception: sample code uses `Activity.dispatchKeyEvent` to detect `KEYCODE_VOLUME_UP/DOWN`, but only while the activity is focused.
- Broadcast-based detection: older samples listen for `android.media.VOLUME_CHANGED_ACTION` and compare `EXTRA_VOLUME_STREAM_VALUE` values. This only triggers on actual volume changes and can be unreliable on newer Android versions.
- AccessibilityService path (Keysh, Volta, KeyMapper): use an `AccessibilityService` with `flagRequestFilterKeyEvents` and `canRequestFilterKeyEvents="true"` to receive volume key events system-wide.
- If an AccessibilityService consumes the event (`onKeyEvent` returns `true`), it must re-apply volume changes itself (Volta calls `AudioManager.adjustStreamVolume`) to preserve standard volume behavior.
- If the AccessibilityService returns `false`, the system handles the volume change normally while still allowing the app to react (overlay trigger).

## Legacy version considerations
- `AudioPlaybackConfiguration` arrives in API 26; per-app volume control is limited or impossible below API 26.
- For API 16-25, consider degraded modes (per-stream volume only, read-only app list, or disable per-app controls).
- Decide whether to ship a legacy flavor/module with reduced functionality and explicit UI gating.

## Legacy plan (API 16-25)
- Ship a legacy flavor/module that disables per-app volume controls and overlay, but keeps diagnostics and preset storage UI visible with a clear warning banner.
- Gate controls based on `SDK_INT < 26`: hide active session list, disable per-app sliders, and show a single stream-level volume helper (optional).
- Keep presets import/export working for migration when a user upgrades devices.
- Document that per-app volume is only supported on API 26+ and requires privileged install.
- Status: legacy flavor now ships a minimal presets + diagnostics UI with a warning banner.

## Location rules plan
- Phase 1: store locations (lat/lon/radius) and request live updates with runtime permissions.
- Phase 2: consider geofencing again if we can do it without Play services.
- Note: current implementation relies on LocationManager GPS/Network updates with a background toggle; Play services geofencing was removed.

## Links
- See sources referenced in the project issue description.
- https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
