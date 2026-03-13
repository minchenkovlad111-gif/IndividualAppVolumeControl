# Permissions

## Manifest permissions
Note: Some privileged permissions are not exposed as public SDK constants; use their string names in code when checking grants.
### Privileged (privapp whitelist)
- `android.permission.MODIFY_AUDIO_SETTINGS`: Adjust per-player volume via AudioManager/hidden APIs.
- `android.permission.MODIFY_AUDIO_ROUTING`: Required for advanced routing and per-player control on some devices.
- `android.permission.INTERACT_ACROSS_USERS`: Resolve packages across user profiles on multi-user devices.
- `android.permission.QUERY_ALL_PACKAGES`: Enumerate installed apps for presets list.
- `android.permission.FOREGROUND_SERVICE`: Run the playback controller as a foreground service.
- `android.permission.WRITE_SECURE_SETTINGS`: Attempt to relax hidden API policy via Settings.Global.
- `android.permission.BLUETOOTH_CONNECT`: Read Bluetooth codec/device info for output selection (Android 12+).
- `android.permission.RECEIVE_BOOT_COMPLETED`: Restart services on boot/app update when overlay is enabled.

### Non-privileged (manifest/runtime)
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required for media playback foreground service type on Android 14+.
- `android.permission.POST_NOTIFICATIONS`: Allow foreground service notification on Android 13+. Not privileged; granted at runtime.
- `android.permission.BLUETOOTH`: Legacy Bluetooth permission for pre-Android 12 devices.
- `android.permission.READ_PHONE_STATE`: Listen for call state changes to mute playback during calls.

## Privapp whitelist
The Magisk module ships `privapp-permissions-appvolumecontrol.xml` to grant signature/privileged permissions to the app when installed as a system app.

## Validation checklist
- Confirm which permissions are required on Android 12–15 devices.
- Remove any permission not required for core functionality.
- Record device-specific restrictions in docs/RESEARCH.md.
- Use the Diagnostics screen to verify requested/granted permission status.
