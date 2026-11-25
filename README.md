# The Prime Truth Studio — Android project

Native Android newsroom recording and video-editing app for The Prime Truth.

## Recording Studio
- CameraX video recording with microphone audio.
- Portrait and landscape layouts.
- Device-dependent Full HD recording selection.
- Automatic recording file creation in the app's Movies folder.
- Permanent square-logo watermark and optional headline export from the Recording Studio.
- Import an existing video and open the Editing Studio.

## Editing Studio (integrated)
- Import multiple video clips in one picker operation.
- Preview selected clips with playback and seek controls.
- Visible horizontal clip timeline with clip selection.
- Trim selected clips by start and end time in seconds.
- Split selected clips at the preview playhead.
- Remove clips and move clips earlier/later in the timeline.
- Join clips in timeline order by exporting them as one composition.
- Add a looping background-music track.
- Record a voice-over in-app and include it as an audio track.
- Apply the square Prime Truth logo and optional headline/lower-third to every video clip.
- Export the complete timeline to MP4 and copy it to `Movies/ThePrimeTruthStudio`.

## Build
The project uses Android Gradle Plugin 8.9.2, Kotlin 2.1.10, Java 17, compile SDK 35 and AndroidX Media3 1.6.1.

### GitHub Actions
The included workflow `.github/workflows/android-apk.yml` builds a debug APK on pushes to `main` and on manual workflow dispatch. Download the artifact named `The-Prime-Truth-Studio-debug-apk` from the successful workflow run.

### Android Studio
1. Open the project folder in Android Studio.
2. Allow Gradle sync and dependency downloads.
3. Build using **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Important validation notes
- This ZIP is Android source code, not a prebuilt APK.
- This environment does not have the Android SDK/Gradle toolchain installed, so a successful compilation and Galaxy A51 device test cannot be claimed here.
- Media3 Composition supports sequential clip composition and additional audio sequences, but the source media must meet Media3's track/format requirements. Crossfades between tracks are not implemented in this version.
- Recording resolution is device-dependent; the camera requests Full HD where supported.
- Export can be slow and may fail for unsupported/corrupt source media or insufficient storage. Test with short MP4 clips first and retain original recordings.
- This is a development build. Test exports before using the app for time-critical newsroom publication.

## Current scope
The editor now has functional timeline controls for import, trim, split, remove, reorder, background music, voice-over, headline/logo overlays and composition export. Dedicated transition effects, animated title templates, keyframe animation, adjustable watermark placement/opacity, and independent audio gain/fade automation are not included yet.
