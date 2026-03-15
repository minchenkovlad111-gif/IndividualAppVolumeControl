# Build

## Requirements
- Android SDK with API 35 installed.
- JDK 17.
- Gradle wrapper jar (not generated in this environment).

## App build (debug)
- Ensure `gradle/wrapper/gradle-wrapper.jar` exists (generate via `gradle wrapper` on a machine with Gradle installed).
- `./gradlew :app:assembleDebug`
- Modern flavor: `./gradlew :app:assembleModernDebug`
- Legacy flavor (API 16-25): `./gradlew :app:assembleLegacyDebug`
- `./gradlew :app:build` prints expected APK output paths at the end (with a presence check).
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
- Default APK input: modern debug; falls back to legacy debug if the modern APK is missing.

## One-step build + module
- `scripts/build-module.sh` builds the modern debug APK and packages the Magisk module into `IndividualAppAudioVolumeControl-magisk.zip`.
- `./gradlew :app:build` also packages the Magisk module (modern debug) after the build completes.
