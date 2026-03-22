# Magisk module

This module installs the IndividualAppAudioVolumeControl APK as a privileged system app and grants the minimum required privileged permission set.

## Install
1. Build the APK (`docs/BUILD.md`).
2. Copy it to `module/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`.
   - Magisk installs this module under `/data/adb/modules/<module>/`.
   - The APK ends up at `/data/adb/modules/<module>/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`
     and is bind-mounted into the system partition at boot (device-dependent target such as `/system/priv-app/` or `/system_ext/priv-app/`).
3. Zip the `module/` folder and flash via Magisk.
4. Or run `module/package.sh` to copy the APK, validate the privapp allowlist against the APK manifest, create the top-level zip, and sync the bundled asset copy used by the app installer.
5. Current module builds intentionally allowlist only `android.permission.MODIFY_AUDIO_ROUTING` to reduce boot-loop risk on enforcing ROMs.
