# Build

## Requirements
- Android SDK with API 35 installed.
- JDK 17.
- Gradle wrapper jar (not generated in this environment).

## App build (debug)
- Ensure `gradle/wrapper/gradle-wrapper.jar` exists (generate via `gradle wrapper` on a machine with Gradle installed).
- `./gradlew :app:assembleDebug`
- If you see wrapper download errors in restricted environments, run with `GRADLE_USER_HOME=/tmp/gradle`.
- The wrapper needs to download the Gradle distribution; pre-seed `GRADLE_USER_HOME` if network access is blocked.
- Offline seeding: run `./gradlew --version` once on a machine with network, then copy the resulting `GRADLE_USER_HOME/wrapper/dists/gradle-8.13-bin/` folder into the offline environment.
- AGP 8.13.2 is used and is compatible with compileSdk 35 (no suppression needed).

## Module packaging
1. Build the APK.
2. Run `module/package.sh` (optionally pass APK path and output zip path).

## One-step build + module
- `scripts/build-module.sh` builds the debug APK and packages the Magisk module into `AppVolumeControl-magisk.zip`.
