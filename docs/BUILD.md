# Build

## Requirements
- Android SDK with API 35 installed.
- JDK 17.
- Gradle wrapper jar (not generated in this environment).

## App build (debug)
- Ensure `gradle/wrapper/gradle-wrapper.jar` exists (generate via `gradle wrapper` on a machine with Gradle installed).
- `./gradlew :app:assembleDebug`

## Module packaging
1. Copy the built APK to `module/system/priv-app/AppVolumeControl/AppVolumeControl.apk`.
2. Zip the `module/` folder and install via Magisk.
