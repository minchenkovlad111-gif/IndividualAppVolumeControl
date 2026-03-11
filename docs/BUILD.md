# Build

## Requirements
- Android SDK with API 35 installed.
- JDK 17.
- Gradle (wrapper to be added).

## App build (debug)
- `./gradlew :app:assembleDebug`

## Module packaging
1. Copy the built APK to `module/system/priv-app/AppVolumeControl/AppVolumeControl.apk`.
2. Zip the `module/` folder and install via Magisk.
