# Architecture

## Components
- System-privileged app: monitors active audio playback and applies per-app volume via hidden APIs.
- Magisk module: installs the app into `/system/priv-app/` and supplies `privapp-permissions`.
- Diagnostics UI: shows last apply stats, errors, and hidden API policy values.
- Preset UI: per-app sliders with import/export via clipboard, managed via a separate app selection screen.
- Manage Apps UI: add apps by package name or search installed apps, then show only saved apps on main screen.
- Overlay service: shows a floating per-app slider panel on volume-button changes (multiple stream settings observers + broadcast actions + polling fallback).
- Overlay UX: per-app mute toggle, segmented tick markers, snap-to-segment resistance, optional auto-refresh, and optional auto-hide.
- Overlay permission flow: dedicated screen to request `SYSTEM_ALERT_WINDOW`.
- Quick settings tile: opens the overlay on demand.
- Call state listener: mutes active playback during ringing/off-hook calls.
- Foreground notification: persistent status with quick per-app +/- controls (prototype).

## Data flow
1. Audio playback changes trigger `AudioManager.AudioPlaybackCallback`.
2. For each active playback configuration, resolve package name.
3. Resolve per-profile user id from the playback UID.
4. Lookup per-app volume in `VolumeStore` using `u<userId>:<package>` keys (fall back to global package key).
5. If a call is ringing or active, temporarily force volume to 0 for all active players.
6. Call hidden `AudioPlaybackConfiguration.getPlayerProxy()` and `IPlayer.setVolume()`.
   - Fallback: try `setVolume(float,int)` when `setVolume(float)` is unavailable.
7. When volume buttons change any stream volume, `VolumeOverlayService` shows an overlay with active app sliders.

## Profiles + presets
- Presets are stored per package, with optional profile keys (`u<userId>:<package>`).
- Marker configs are stored per preset key and rendered as per-app markers/segments on sliders.
- Output device configs are stored per preset key and applied per active player when possible.
- The UI can filter active/preset lists by profile while still honoring global presets.
- Manage Apps lets you choose the profile before creating or removing presets.
- Active timestamps are tracked to support app info popups.

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
