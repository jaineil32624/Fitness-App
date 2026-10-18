# Repwise

Move smarter. Get stronger.

[![Download for Android](.github/assets/download-android.svg)](https://github.com/jaineil32624/Fitness-App/raw/refs/heads/main/Repwise.apk)

An offline-first fitness coach that uses your camera to track exercises, count reps, and give real-time form feedback. Your workouts stay on your device.

> Pose estimates and calorie figures are guidance, not medical advice or a substitute for a qualified trainer. Use a clear, well-lit space and stop if you feel pain or dizziness.

Open the APK on your Android device and follow Android's prompts to allow installation from your browser or file manager. Version 2 installs over version 1 and keeps your workout history. See [CHANGELOG.md](CHANGELOG.md) for what changed.

## Features

**Training**
- Live camera pose estimation and rep counting for squats, push-ups, jumping jacks, lunges, planks, crunches, shoulder presses, mountain climbers, glute bridges, and high knees.
- Form cues, a skeleton overlay aligned to the preview, per-session rep targets set by your difficulty, and pause / front-rear camera switching.
- Separate elapsed and pose-verified active timers; rests, pauses, and backgrounding never add active time.
- Interval circuit timer with configurable work, rest, and rounds, plus audio countdowns.

**Planning and progress**
- Day, week, and month views: daily drill-down, a weekly active-minutes chart, and a monthly calendar heatmap with planned days.
- Weekly session and active-minute goals, a 0–100 weekly fitness score, streaks, personal bests, and top moves.
- Coach picks that suggest neglected exercises and muscle groups, plus a schedule hint based on when you usually train.
- Workout reminders on your planned days, and one-tap export of your plan to your calendar.

**Gamification**
- XP and levels, 18 badges, a rotating daily challenge, and five accent themes that unlock as you level up.
- A celebration screen after each session with XP count-up, level-ups, badge reveals, and confetti.
- XP, badges, and streaks are always derived from your history, so deleting a session recalculates them correctly.

**Sound**
- Five offline music tracks (Energy, Lo-fi, Classical, Ambient), synthesized on-device so they add nothing to the APK.
- Ambient mixer (rain, ocean, forest, fireplace, café, white and brown noise) with per-layer volume and saved mixes.
- Background playback with notification controls; pauses for calls, other apps, and unplugged headphones.
- Music recommendations per exercise, sound effects for reps, targets, and level-ups, and a Spotify shortcut.

**App**
- Material 3 design with light, dark, and system themes, onboarding, and animated transitions throughout.
- Adaptive layout: bottom navigation on phones, a navigation rail on tablets and foldables, and a landscape workout layout.

## Requirements

- Android Studio Ladybug or newer, with the Android SDK 36 platform.
- JDK 17 or newer. Android Studio's bundled JBR works, including JDK 25.
- Android 8.0 (API 26) or newer to run the app. A front-facing camera is recommended.

## Build and install

1. Clone this repository and open its root directory in Android Studio.
2. Let Gradle sync, and install the Android SDK 36 platform if prompted.
3. Run the `app` configuration on a device or emulator. Grant camera access for live workouts.
4. Build a release APK with `.\gradlew.bat :app:assembleRelease` on Windows or `./gradlew :app:assembleRelease` on macOS/Linux. It is minified and is written to `app/build/outputs/apk/release/`. Without signing secrets it is signed with your local debug key.
5. Run unit tests with `./gradlew :app:testDebugUnitTest`.

No camera permission is needed to browse history, progress, sounds, or your profile. Live pose processing is local; frames are not uploaded.

## Project structure

```text
app/src/main/java/com/repwise/
  MainActivity.kt, RepwiseApplication.kt   Entry points, notification channels
  domain/      Models, stats, coach suggestions, XP/levels/badges/challenges (pure Kotlin, unit tested)
  data/        WorkoutStore: app-private persistence with corruption-safe parsing
  camera/      CameraSession (CameraX + ML Kit) and PoseWorkoutAnalyzer (rep and form logic)
  audio/       AudioEngine mixer, synthesized music and ambient generators, sound effects, playback service
  reminders/   Reminder scheduling, notifications, calendar export
  updates/     GitHub release check and APK download
  ui/          AppViewModel, theme, shared components, and one package per screen
app/src/test/  Unit tests for activity detection, stats, gamification, and reminder scheduling
```

## GitHub APK releases

Create a GitHub release with a version tag such as `v2.0.1`. The release workflow builds a signed APK when the repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `ANDROID_STORE_PASSWORD` are configured. Without signing secrets, CI still uploads a debug APK artifact but does not publish an installable release. **Profile > App updates** checks this repository by default. It only downloads APKs hosted on GitHub over HTTPS, and never installs silently.

## Privacy

Repwise does not send camera frames, pose landmarks, profile details, or workout history to a server. Workout data is saved in app-private storage and included in Android's device backup. GitHub is contacted only when you check for or download an app release.

## Limitations

Pose visibility depends on lighting, camera placement, clothing, and the full body being in frame. Rep counting and form feedback are heuristic and may not work for every body type or camera angle. Calories are a rough MET-based estimate using your profile weight. The Spotify button opens the Spotify app or website; it does not control Spotify playback.

## Contributing

Please open an issue before large changes. Keep camera processing on-device, avoid logging personal workout data, and add tests for changes to counting, stats, or gamification logic. See the issue templates in `.github/ISSUE_TEMPLATE`.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
