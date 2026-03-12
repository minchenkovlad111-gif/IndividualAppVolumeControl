# Individual App Audio Volume Control

A root-required Android app + Magisk module for per-app volume control, designed for Android 15 and intended to support older versions where possible.

## Status
- Core service, UI controls, diagnostics screen, and Magisk module packaging script in place.

## Features
- Active playback list with per-app volume sliders.
- Preset list with search, quick actions, and import/export.
- Volume-button overlay with per-app sliders (requires overlay permission).
- Quick settings tile + notification action to open the overlay.
- Manage Apps screen to add presets per profile (work/personal).
- Diagnostics screen with permission and hidden API status.

## Repository layout
- `app/`: Android app (system/privileged app)
- `module/`: Magisk module that installs the app as a system-privileged app
- `docs/`: design, research, and build notes
- `docs/COMPATIBILITY.md`: device testing matrix
- `docs/PRESETS.md`: preset export/import format
- `TODO.md`: prioritized tasks
- `STATUS.md`: current work log

## Quick start
- Build the app APK (see `docs/BUILD.md`).
- Place the APK at `module/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`.
  - Magisk installs modules under `/data/adb/modules/<module>/`.
  - This maps to `/data/adb/modules/<module>/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`,
    which Magisk overlays into the system partition at boot (typically `/system/priv-app/` or `/system_ext/priv-app/`).
- Zip the `module/` folder and install via Magisk (or run `module/package.sh`).

## Notes
- The app relies on hidden audio APIs to set per-player volume and therefore needs root + privileged installation.
- Volume control is applied per active playback configuration.
