# Permissions

## Manifest permissions
Note: Some privileged permissions are not exposed as public SDK constants; use their string names in code when checking grants.
### Privileged (privapp whitelist)
- `android.permission.MODIFY_AUDIO_ROUTING`: Required for advanced routing and per-player control on some devices.
- `android.permission.INTERACT_ACROSS_USERS`: Required to enumerate and resolve active playback from other Android users/profiles.
- `android.permission.INTERACT_ACROSS_USERS_FULL`: Required on some ROMs for reliable cross-profile audio control and user-scoped context access.

### Non-privileged (manifest/runtime)
- `android.permission.MODIFY_AUDIO_SETTINGS`: Adjust per-player volume via AudioManager/hidden APIs.
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required for media playback foreground service type on Android 14+.
- `android.permission.FOREGROUND_SERVICE`: Run the playback controller as a foreground service.
- `android.permission.QUERY_ALL_PACKAGES`: Enumerate installed apps for presets list (subject to package visibility policy, not privapp XML).
- `android.permission.POST_NOTIFICATIONS`: Allow foreground service notification on Android 13+. Not privileged; granted at runtime.
- `android.permission.BLUETOOTH`: Legacy Bluetooth permission for pre-Android 12 devices.
- `android.permission.BLUETOOTH_CONNECT`: Read Bluetooth codec/device info for output selection (Android 12+).
- `android.permission.READ_PHONE_STATE`: Listen for call state changes to mute playback during calls.
- `android.permission.RECEIVE_BOOT_COMPLETED`: Restart services on boot/app update when overlay is enabled.

## Accessibility service
- The volume-key overlay listener is implemented as an `AccessibilityService` with `flagRequestFilterKeyEvents`.
- The service entry is protected by `android.permission.BIND_ACCESSIBILITY_SERVICE` (no runtime prompt), but it must be enabled in Accessibility settings (or via `WRITE_SECURE_SETTINGS` for system installs).

## Privapp whitelist
The Magisk module ships `privapp-permissions-appvolumecontrol.xml` to grant signature/privileged permissions to the app when installed as a system app.
The current privileged app package name is `fuck.individualappaudiovolumecontrol.droidvendorssuck`, and the whitelist XML must target that exact package.
The whitelist must stay aligned with every privileged permission requested in the manifest or boot-time package scan can fail on enforcing ROMs.
Packaging now derives the expected allowlist from manifest permissions marked with `tools:ignore="ProtectedPermissions"` and rejects stale XML entries so the whitelist cannot silently drift.
Cross-profile detection via root `dumpsys` is only a fallback for visibility. Actual foreign-profile control still depends on receiving real `AudioPlaybackConfiguration` objects and resolving package/user data successfully, which in practice means the privapp path for `INTERACT_ACROSS_USERS(_FULL)` must work on-device.
The module service also performs a best-effort post-boot `pm grant` / `cmd package grant` attempt for `INTERACT_ACROSS_USERS` and `INTERACT_ACROSS_USERS_FULL` across detected users, and logs the result with tag `IndividualAppAudioVolumeControl`. This is only a fallback probe: on most ROMs these signature|privileged permissions still cannot be granted dynamically unless the privapp install path is already correct.

## Validation checklist
- Confirm which permissions are required on Android 12–15 devices.
- Remove any permission not required for core functionality.
- Record device-specific restrictions in docs/RESEARCH.md.
- Use the Diagnostics screen to verify requested/granted permission status.
