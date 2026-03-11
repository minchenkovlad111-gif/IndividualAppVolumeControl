# Status

## Current focus
- Scaffolding the Android app and Magisk module.
- Laying down core playback monitoring + volume application pipeline.
- Capturing research references and architecture notes.

## Latest work
- Added initial app/service skeleton and Magisk module template.
- Added basic UI with active session list and preset list sliders.
- Added app icons, active indicators, and preset search filter.
- Added hidden API bypass attempts via VMRuntime and Settings.Global.
- Added periodic refresh and last-updated indicator in the UI.
- Added Gradle wrapper jar and updated wrapper scripts/config.
- Build attempt blocked by wrapper download (no network access in this environment).
- Added diagnostics screen and runtime stats tracking.
- Added Magisk module packaging script.
- Added diagnostics self-test action and service dump logging.
- Added diagnostics report copy/share actions.
- Documented offline Gradle distribution seeding.
- Added volume percentage labels next to sliders.
- Marked active apps within the presets list.
- Preset list now refreshes active badges when playback changes.
- Added per-app mute button in the UI.
- Added reset button to clear all stored presets.
- Added device/app flags to diagnostics report.
- Added live active package list to diagnostics report.
- Added hidden API attempt status to diagnostics report.
- Added stored preset count to diagnostics report.
- Added preset export/import via clipboard in diagnostics.
- Added "Max active" quick action.
- Added validation checklist to research notes.
- Added toast feedback for preset import.
- Added compatibility tracking doc.
- Pruned stale playback keys after each apply.
- Documented compatibility matrix in README.
- Persisted "show all apps" toggle in UI prefs.
- Added app version info to diagnostics.
- Added label ellipsize for long app names.
- Added notification channel description.
- Added active session count in UI header.
- Added preset count in UI header.
- Ignored metadata keys during preset import.
- Added version field to preset export.
- Added clear filter button to presets list.
- Added replace/share options for preset import/export.
- Added "Mute active" quick action.
- Added export tracking and metadata for presets.
- Added active config count to diagnostics.
- Switched module packaging script to portable shebang.
- Added "Active 50%" quick action.
- Added toggle to include all installed apps in presets list.
- Added permissions reference doc.
- Preset list now prioritizes active apps.
- Documented module packaging script usage.
- Added POST_NOTIFICATIONS permission for Android 13+ foreground notifications.
- Added simple app icon cache in UI.
- Sorted active apps alphabetically by label.

## Last updated
- 2026-03-11
