# Permissions

## Manifest permissions
- `android.permission.MODIFY_AUDIO_SETTINGS`: Adjust per-player volume via AudioManager/hidden APIs.
- `android.permission.MODIFY_AUDIO_ROUTING`: Required for advanced routing and per-player control on some devices.
- `android.permission.INTERACT_ACROSS_USERS`: Resolve packages across user profiles on multi-user devices.
- `android.permission.QUERY_ALL_PACKAGES`: Enumerate installed apps for presets list.
- `android.permission.FOREGROUND_SERVICE`: Run the playback controller as a foreground service.
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required for media playback foreground service type on Android 14+.
- `android.permission.WRITE_SECURE_SETTINGS`: Attempt to relax hidden API policy via Settings.Global.
- `android.permission.POST_NOTIFICATIONS`: Allow foreground service notification on Android 13+.

## Privapp whitelist
The Magisk module ships `privapp-permissions-appvolumecontrol.xml` to grant signature/privileged permissions to the app when installed as a system app.

## Validation checklist
- Confirm which permissions are required on Android 12–15 devices.
- Remove any permission not required for core functionality.
- Record device-specific restrictions in docs/RESEARCH.md.
