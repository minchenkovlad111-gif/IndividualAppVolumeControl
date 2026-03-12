# Research notes

## Known solutions
- App Volume Control + Audio HeadQuarter: root approach using a companion Magisk module.
- VolumeManager: uses hidden audio APIs (AudioPlaybackConfiguration + IPlayer.setVolume) via Shizuku.
- SoundMaster (ShizuTools): per-app volume using hidden APIs and Shizuku.
- Samsung SoundAssistant: OEM implementation for per-app volume.

## Implications
- The most reliable path on A15 is a privileged app with hidden API access.
- AudioPlaybackConfiguration callbacks provide the per-player hook for volume control.
- Android provides hidden API enforcement settings (`hidden_api_policy`, `hidden_api_policy_pre_p_apps`, `hidden_api_policy_p_apps`) that can be toggled on development devices and via `Settings.Global` with `WRITE_SECURE_SETTINGS`.

## Validation checklist
- Verify hidden API access works on Android 12, 13, 14, 15 (VMRuntime and Settings.Global paths).
- Confirm `AudioPlaybackConfiguration.getPlayerProxy()` and `IPlayer.setVolume()` are callable on each target.
- Validate required permissions on at least one AOSP-based and one OEM device.
- Capture logs from the diagnostics screen and document known failures.
- Use "Retry hidden API" in Diagnostics to capture VMRuntime/policy error details per device.
- Use "Copy bundle" in Diagnostics to capture a single shareable payload during testing.

## UX ideas to validate
- Volume-button overlay listing active apps (AudioHQ-style) for quick control.
- Overlay watcher reliability across Android 12-15 (volume change observer vs. volume keys).
- Quick settings tile for jumping into the overlay.
- Profile-aware app lists for work/personal separation.
- Feature gating: gray out unavailable features per SDK + permission state.

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
- Phase 2: add geofencing as needed.
- Note: current implementation supports optional geofencing via Play services and LocationManager GPS/Network updates with a background toggle.

## Links
- See sources referenced in the project issue description.
- https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
