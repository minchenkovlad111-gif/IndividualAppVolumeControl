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

## Links
- See sources referenced in the project issue description.
- https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
