# Architecture

## Components
- System-privileged app: monitors active audio playback and applies per-app volume via hidden APIs.
- Magisk module: installs the app into `/system/priv-app/` and supplies `privapp-permissions`.
- Diagnostics UI: shows last apply stats, errors, and hidden API policy values.
- Preset UI: per-app sliders with import/export via clipboard.

## Data flow
1. Audio playback changes trigger `AudioManager.AudioPlaybackCallback`.
2. For each active playback configuration, resolve package name.
3. Lookup per-app volume in `VolumeStore`.
4. Call hidden `AudioPlaybackConfiguration.getPlayerProxy()` and `IPlayer.setVolume()`.
   - Fallback: try `setVolume(float,int)` when `setVolume(float)` is unavailable.

## Hidden API strategy
- Attempt `VMRuntime.setHiddenApiExemptions` to relax reflection limits.
- As a fallback, try setting hidden API policy via `Settings.Global` (requires `WRITE_SECURE_SETTINGS`).
- Cache reflected `setVolume` method lookups per player proxy class to reduce per-apply overhead.

## Compatibility
- Target: Android 15 (API 35).
- Minimum: Android 8.0 (API 26) to access AudioPlaybackConfiguration.

## Risks
- Hidden API restrictions may block reflection on newer versions.
- Privileged permissions are required to access playback configuration details.
