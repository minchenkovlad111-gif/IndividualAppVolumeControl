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

## Links
- See sources referenced in the project issue description.
- https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
