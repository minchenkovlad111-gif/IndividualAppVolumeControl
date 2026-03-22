# Build

## Requirements
- Android SDK with API 35 installed.
- JDK 17.
- Gradle wrapper jar (not generated in this environment).
- The project pins the Gradle daemon heap to `-Xmx4g` in `gradle.properties`; AGP 8.13.2 lint analysis and modern release dexing were both able to OOM under Gradle's default 512 MiB heap.

## App build (debug)
- Ensure `gradle/wrapper/gradle-wrapper.jar` exists (generate via `gradle wrapper` on a machine with Gradle installed).
- `./gradlew :app:assembleDebug`
- Modern flavor: `./gradlew :app:assembleModernDebug`
- Legacy flavor (API 16-25): `./gradlew :app:assembleLegacyDebug`
- Fast Kotlin-only validation for modern-source changes: `./gradlew :app:compileModernDebugKotlin`
- `./gradlew :app:build` prints expected APK output paths at the end (with a presence check).
- Plain `gradle build` also reads the project `gradle.properties`, so the same heap sizing applies if you use a system Gradle install instead of the wrapper.
- APK outputs:
- `app/build/outputs/apk/modern/debug/app-modern-debug.apk`
- `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`
- If you see wrapper download errors in restricted environments, run with `GRADLE_USER_HOME=/tmp/gradle`.
- The wrapper needs to download the Gradle distribution; pre-seed `GRADLE_USER_HOME` if network access is blocked.
- Offline seeding: run `./gradlew --version` once on a machine with network, then copy the resulting `GRADLE_USER_HOME/wrapper/dists/gradle-8.13-bin/` folder into the offline environment.
- AGP 8.13.2 is used and is compatible with compileSdk 35 (no suppression needed).

## Module packaging
1. Build the APK.
2. Run `module/package.sh` (optionally pass APK path and output zip path).
- Default output: `IndividualAppAudioVolumeControl-magisk.zip`.
- Default APK input: signed modern release; falls back to unsigned release outputs, then debug artifacts if release outputs are missing.
- The installed Android package name is `fuck.individualappaudiovolumecontrol.droidvendorssuck`.
- The Magisk module mount path remains `system/priv-app/IndividualAppAudioVolumeControl/IndividualAppAudioVolumeControl.apk` to avoid unnecessary module path churn.
- On KernelSU, a metamodule is required for the module `system/` tree to mount. If no metamodule is active, flashing the ZIP is not enough to make the app a privileged system app.
- `module/package.sh` now recreates the output archive from scratch before zipping, so stale APK paths are not retained across rebuilds.
- `module/package.sh` validates that the APK package name matches the privapp XML, derives expected allowlisted permissions from any `AndroidManifest.xml` under `app/src` that mark permissions with `tools:ignore="ProtectedPermissions"`, and rejects stale XML entries before it writes the ZIP.
- The module intentionally keeps the privapp allowlist minimal. Current source/configuration requests `MODIFY_AUDIO_ROUTING` and `INTERACT_ACROSS_USERS`; both should be treated as boot-critical on enforcing ROMs and revalidated on-device.
- Packaging the top-level module ZIP also syncs the bundled asset copy used by the in-app installer, so both distribution paths share the same archive.
- For bootloop diagnosis, the module `service.sh` captures logcat to `/data/adb/individualappaudiovolumecontrol-boot.log` (rotating) on boot, with a fallback to `/cache/individualappaudiovolumecontrol-boot.log` if `/data` is unavailable. Module id defaults to `individualappaudiovolumecontrol`.

## One-step build + module
- `scripts/build-module.sh` builds the signed modern release APK and packages the Magisk module into `IndividualAppAudioVolumeControl-magisk.zip`.
- `./gradlew :app:build` also packages the Magisk module (signed modern release) after the build completes.

## Bootloop triage module
- If a priv-app install still bootloops, build an APK-only probe module with no privapp XML:
- `module/package-system-app-probe.sh app/build/outputs/apk/modern/release/app-modern-release.apk`
- Output: `IndividualAppAudioVolumeControl-system-app-probe.zip`
- This stages the APK under `system/app/` instead of `system/priv-app/` and removes `privapp-permissions-appvolumecontrol.xml`.
- If the probe module boots, the failure is in the privileged install path (`priv-app` / privapp allowlist enforcement). If it still bootloops, the issue is the APK itself or a broader KernelSU/module interaction.
- Next isolation step: build a `priv-app` probe APK with `MODIFY_AUDIO_ROUTING` removed from the merged manifest:
- `gradle :app:assembleModernProbe`
- `module/package-privapp-no-protected-perms-probe.sh app/build/outputs/apk/modern/probe/app-modern-probe.apk`
- Output: `IndividualAppAudioVolumeControl-privapp-no-protected-perms-probe.zip`
- This keeps the APK under `system/priv-app/` but removes both the protected permission request and the privapp XML.
- If this probe boots, `MODIFY_AUDIO_ROUTING` is the specific boot trigger on that ROM. If it still bootloops, the ROM is rejecting the app as a priv-app even without protected permissions.
