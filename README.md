# Individual App Audio Volume Control

A root-required Android app + Magisk module for per-app volume control, designed for Android 15 and intended to support older versions where possible.

## Status
- Early scaffolding: core service, hidden API access attempt, and Magisk module skeleton.

## Repository layout
- `app/`: Android app (system/privileged app)
- `module/`: Magisk module that installs the app as a system-privileged app
- `docs/`: design, research, and build notes
- `TODO.md`: prioritized tasks
- `STATUS.md`: current work log

## Quick start
- Build the app APK (see `docs/BUILD.md`).
- Place the APK at `module/system/priv-app/AppVolumeControl/AppVolumeControl.apk`.
- Zip the `module/` folder and install via Magisk.

## Notes
- The app relies on hidden audio APIs to set per-player volume and therefore needs root + privileged installation.
- Volume control is applied per active playback configuration.
