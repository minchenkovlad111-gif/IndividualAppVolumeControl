# Research notes

## Known solutions
- App Volume Control + Audio HeadQuarter: root approach using a companion Magisk module.
- VolumeManager: uses hidden audio APIs (AudioPlaybackConfiguration + IPlayer.setVolume) via Shizuku.
- SoundMaster (ShizuTools): per-app volume using hidden APIs and Shizuku.
- Samsung SoundAssistant: OEM implementation for per-app volume.

## Implications
- The most reliable path on A15 is a privileged app with hidden API access.
- AudioPlaybackConfiguration callbacks provide the per-player hook for volume control.

## Links
- See sources referenced in the project issue description.
