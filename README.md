# The Prime Truth Studio — Android project (development build)

A native Android Studio project for The Prime Truth news portal.

## Included in this development build
- Branded app screen using the uploaded official transparent PNG logo.
- Camera recording through CameraX, with audio permission and device-dependent Full HD quality selection.
- Import an existing video through the Android system video picker.
- Permanent logo watermark baked into the exported MP4 using AndroidX Media3 Transformer.
- Optional headline strip rendered into the exported video (headline input accepts Assamese, Hindi and English text supported by the phone font).
- Basic trim-in / trim-out fields in seconds.
- Exported MP4 copied to `Movies/ThePrimeTruthStudio`.

## Build and install
1. Install Android Studio and Android SDK Platform 35.
2. Open this project folder in Android Studio.
3. Allow Gradle sync and dependency downloads.
4. Connect an Android phone (USB debugging enabled) or use a suitable emulator.
5. Run the `app` configuration to install the development build.
6. To generate an APK, use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The debug APK, if successfully built, will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## Important status
This archive is Android source code, not a compiled or device-tested APK. The current environment does not have Android Studio / Android SDK / Gradle installed, so compilation and real-device validation have not been performed here. Please build and test before newsroom use.

## Still planned
- Multi-clip timeline, split and join controls.
- Background music import, voiceover recording and audio mixing.
- More lower-third / Breaking News templates and editable reporter/location fields.
- Front/rear camera switch and recording quality selector.
- Watermark size, opacity and position controls.
- Release signing and final installable APK.
