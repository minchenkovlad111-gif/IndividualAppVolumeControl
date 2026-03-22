# Magisk module

This module installs the IndividualAppAudioVolumeControl APK as a privileged system app and grants the minimum required privileged permission set.

## Install
1. Build the APK (`docs/BUILD.md`).
2. Copy it to `module/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`.
   - Magisk installs this module under `/data/adb/modules/<module>/`.
   - The APK ends up at `/data/adb/modules/<module>/system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk`
     and is bind-mounted into the system partition at boot (device-dependent target such as `/system/priv-app/` or `/system_ext/priv-app/`).
3. If you use KernelSU, install a metamodule first so KernelSU actually mounts the module `system/` tree at boot.
   - `meta-overlayfs` is the safest default starting point.
   - Without a metamodule, `/system/priv-app/...` and `/system/etc/permissions/...` from this module will not be applied, so the app will not be privileged.
4. Zip the `module/` folder and flash via Magisk or KernelSU.
5. Or run `module/package.sh` to copy the APK, validate the privapp allowlist against the APK manifest, create the top-level zip, and sync the bundled asset copy used by the app installer.
6. Current source/configuration requests `android.permission.MODIFY_AUDIO_ROUTING`, `android.permission.INTERACT_ACROSS_USERS`, and `android.permission.INTERACT_ACROSS_USERS_FULL`; all remain boot-sensitive privileged permissions on enforcing ROMs.
