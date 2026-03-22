# Architecture

## Components
- System-privileged app: monitors active audio playback and applies per-app volume via hidden APIs.
- Magisk module: installs the app into `/system/priv-app/` and supplies `privapp-permissions`.
- Diagnostics UI: shows last apply stats, errors, hidden API policy values, and overlay/accessibility status.
- Diagnostics persistence: keeps the latest diagnostics snapshot in-process and flushes it to `SharedPreferences` on a dedicated background thread, avoiding `QueuedWork.waitToFinish()` stalls when activities stop.
- Preset UI: per-app sliders with import/export via clipboard, managed via a separate app selection screen.
- Manage Apps UI: add apps by package name or search installed apps, then show only saved apps on main screen.
- Overlay service: shows a floating per-app slider panel on volume-button changes (multiple stream settings observers + broadcast actions + polling fallback).
- Volume-key accessibility service: listens for volume key events system-wide and signals the overlay without consuming the key (volume still changes).
- Overlay UX: per-app mute toggle, segmented tick markers, snap-to-segment resistance, dropdown header menu, pause/resume toggle, optional auto-refresh, optional auto-hide, and optional gating (only show while audio plays, snooze, focused-app blocklist).
- Overlay permission flow: dedicated screen to request `SYSTEM_ALERT_WINDOW`.
- Quick settings tile: opens the overlay on demand.
- Boot receiver: restarts the playback controller (and overlay watcher if enabled) after reboot/app update.
- Call state listener: mutes active playback during ringing/off-hook calls.
- Foreground notification: persistent status with per-app visual level bars plus tap actions (`+/-`, mute, 25/50/75/100%) and an overlay shortcut.
- Rules engine: optional group presets + conditional overrides (co-active apps/time windows) applied before base presets, plus audio focus actions (pause/duck/mute).
- Per-app mute behavior: choose mute-only or mute+pause when a preset is set to 0%.
- Default mute behavior: global fallback for apps without a per-app override.
- Auto-resume toggle: opt-in to resume playback when pause rules/mute no longer apply.
- Per-app audio attribute overrides: optional usage/content-type filters that limit which playback sessions get controlled.
- Location rules: stored locations (lat/lon/radius) with current location id derived from live location updates.
- Location updates can run in the background via a broadcast receiver when enabled and permitted.
- Optional geofencing via Play services was removed; location rules now rely on LocationManager updates only.
- Build flavors: modern (API 26+) full feature set; legacy (API 16-25) presets/diagnostics only with warning banner.

## Data flow
1. Audio playback changes trigger `AudioManager.AudioPlaybackCallback`.
2. For each active playback configuration, resolve package name.
3. Resolve per-profile user id from the playback UID.
4. Evaluate rules engine overrides (active app + time window conditions) against active players.
5. Lookup per-app volume in `VolumeStore` using `u<userId>:<package>` keys (fall back to global package key).
6. If an audio-attribute override exists, only apply volume/pause actions when the session’s usage/content type matches.
6. If a call is ringing or active, temporarily force volume to 0 for all active players.
7. Call hidden `AudioPlaybackConfiguration.getPlayerProxy()` and `IPlayer.setVolume()`.
   - Fallback: try `setVolume(float,int)` when `setVolume(float)` is unavailable.
7. When volume buttons change any stream volume, `VolumeOverlayService` shows an overlay with active app sliders (unless suppressed by snooze/focus filter/playback gating).
8. If AccessibilityService key filtering is enabled, volume key presses also signal `VolumeOverlayService` directly for more reliable detection.

## Profiles + presets
- Presets are stored per package, with optional profile keys (`u<userId>:<package>`).
- Marker configs are stored per preset key and rendered as per-app markers/segments on sliders.
- Output device configs are stored per preset key and applied per active player when possible.
- The UI can filter active/preset lists by profile while still honoring global presets.
- Manage Apps lets you choose the profile before creating or removing presets.
- Active timestamps are tracked to support app info popups.

## Hidden API strategy
- Attempt `VMRuntime.setHiddenApiExemptions` to relax reflection limits.
- Avoid depending on `WRITE_SECURE_SETTINGS` in the shipped priv-app module; if policy writes are unavailable, Diagnostics should report that and the app should continue with the VMRuntime path only.
- Cache reflected `setVolume` method lookups per player proxy class to reduce per-apply overhead.

## Compatibility
- Target: Android 15 (API 35).
- Minimum: Android 8.0 (API 26) to access AudioPlaybackConfiguration.

## Risks
- Hidden API restrictions may block reflection on newer versions.
- Overbroad privapp allowlists can trigger boot-time package-manager enforcement failures on some Android 15 ROMs.
- Keep privileged permissions to the minimum required for core routing control.
