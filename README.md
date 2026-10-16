# Repwise

Move smarter. Get stronger.

[![Download for Android](.github/assets/download-android.svg)](https://github.com/jaineil32624/Fitness-App/raw/refs/heads/main/Repwise.apk)

An offline-first fitness coach that uses your camera to track exercises, count reps, and give real-time form feedback. Your workouts stay on your device.

> Pose estimates and calorie figures are guidance, not medical advice or a substitute for a qualified trainer. Use a clear, well-lit space and stop if you feel pain or dizziness.

Open the APK on your Android device and follow Android's prompts to allow installation from your browser or file manager. The app does not install updates silently.

## Features

- Live camera pose estimation and rep counting for squats, push-ups, jumping jacks, lunges, and planks.
- Form cues based on visible joint angles and body alignment.
- Session timer, estimated calories, and locally stored workout history.
- Dashboard, weekly progress, achievements, profile settings, and exercise recommendations.
- Check and download APK releases from a configurable GitHub repository.
- Core workout analysis runs on-device; internet is only needed for release checks/downloads.

## Requirements

- Android Studio Ladybug (or newer) with Android SDK 35.
- JDK 17 (Android Studio's bundled JBR is suitable).
- Android 8.0 / API 26 or newer. A front-facing camera is recommended.

## Build and install

1. Clone this repository and open its root directory in Android Studio.
2. Allow Gradle to sync and install the Android SDK 35 platform if prompted.
3. Run the `app` configuration on a device or emulator. Grant camera access for live workouts.
4. To build a debug APK, run `.\gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux, or use **Build > Build APK(s)**. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

No camera permission is needed to browse workout history, progress, profile, or recommendations. Live pose processing is local; frames are not uploaded.

## Project structure

```text
app/
  src/main/java/com/repwise/
    MainActivity.kt             Compose screens and navigation
    data/WorkoutStore.kt        Local history and profile persistence
    camera/PoseWorkoutAnalyzer.kt  Pose feature extraction and rep/form logic
    updates/GitHubReleaseClient.kt GitHub release API and APK download
  src/main/res/                 Android manifest, strings, and theme
.github/
  ISSUE_TEMPLATE/               Bug and feature request forms
  workflows/android.yml         CI debug APK build and release publishing
```

## GitHub APK releases

Create a GitHub release with a version tag such as `v1.0.1`. The release workflow builds a signed APK when the repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `ANDROID_STORE_PASSWORD` are configured. Without signing secrets, CI still uploads a debug APK artifact but does not publish an installable release. Configure the owner and repository in **Profile > App updates** to check and download releases from another public repository. Android may ask the user to allow Repwise to install apps from this source.

APK updates are opt-in and are never installed silently. Only install releases from repositories you trust. For production, protect release tags and use a dedicated signing key stored only in GitHub Actions secrets.

## Privacy

Repwise does not send camera frames, pose landmarks, profile details, or workout history to a server. Workout data is saved in app-private preferences. GitHub is contacted only when the user checks for or downloads an app release.

## Limitations

Pose visibility depends on lighting, camera placement, clothing, and the full body being in frame. Rep counting and form feedback are heuristic and may not work for every body type or camera angle. The current calorie value is a rough MET-based estimate using the user's profile weight; it is not a physiological measurement.

## Contributing

Please open an issue before large changes. Keep camera processing on-device, avoid logging personal workout data, and add tests for changes to counting or persistence logic. See the issue templates in `.github/ISSUE_TEMPLATE`.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
