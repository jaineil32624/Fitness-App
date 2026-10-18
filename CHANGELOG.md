# Changelog

## 2.0.0

A full rebuild of the app's structure and interface. Version 2 installs over 1.0 (same package and signing key) and keeps existing workout history and profile settings.

### Bugs fixed

| # | Bug | Impact | Fix |
|---|-----|--------|-----|
| 1 | Gradle 8.9 can't run on the JDK 25 bundled with current Android Studio | Project failed to sync or build | Upgraded to Gradle 9.3.1, AGP 8.13.1, Kotlin 2.2.21 |
| 2 | Ending a live workout could crash the app (`RejectedExecutionException`) | Crash when ML Kit delivered a frame result after the camera executor shut down | Late callbacks now run inline and only release the frame |
| 3 | System Back during a live workout left the app immediately | Session lost with no prompt | Back opens a Save / Discard / Keep going dialog |
| 4 | The back arrow saved immediately, even with 0 reps and 0 seconds | Empty sessions counted toward weekly goals and streaks | Sessions with nothing tracked aren't saved; saving needs confirmation |
| 5 | Active time kept counting after the app was backgrounded | Inflated active minutes and calories | Active time only accrues while the screen is visible |
| 6 | Timer used `delay(1000)` increments | Drift, and seconds lost under load | Timers are derived from `SystemClock.elapsedRealtime()` |
| 7 | Workout state lived in `remember {}` | Lost on rotation, theme change, or multi-window | Moved into `AppViewModel`; the analyzer survives camera restarts |
| 8 | The screen turned off mid-workout | Tracking stopped and the phone locked | Screen stays on during live and interval workouts |
| 9 | Skeleton overlay used stretched coordinates | Joints misaligned with the cropped camera preview | Overlay applies the same FILL_CENTER crop and mirroring as the preview |
| 10 | All 33 ML Kit landmarks were trusted, including guessed off-screen joints | The "8+ points visible" check was almost always true; angles came from guesses | Landmarks below 50% in-frame likelihood are ignored |
| 11 | A corrupt stored JSON value threw at startup | Crash on every launch | Defensive parsing falls back to defaults |
| 12 | Camera provider failure threw on the main thread | Crash on devices with a busy or missing camera | Caught, with a message and a front/rear switch |
| 13 | A permanently denied camera permission had no recovery path | User stuck with a toast | Dialog offers to open the app's settings |
| 14 | Default update repository was the placeholder `your-github-owner` | "Check releases" always failed | Defaults to this repository, and v1 placeholders are migrated |
| 15 | The update download poll looped forever if the download was cancelled | Background coroutine never ended | Stops when the download row disappears |
| 16 | Release asset URL and tag were used unchecked | A crafted release could point the download anywhere or inject into the file path | HTTPS GitHub hosts only; the tag is sanitized before use in a file name |
| 17 | Workout music ignored audio focus | Played over calls and other apps | Requests focus; pauses on loss and when headphones are unplugged |
| 18 | The music loop rendered on the main thread when first played | UI jank | Rendering runs on a background thread |
| 19 | No launcher icon | Generic Android icon | Adaptive and monochrome themed icon |
| 20 | Hard-coded light colors and deprecated status-bar APIs | No dark mode; bars wrong on Android 15+ | Material 3 light/dark themes with edge-to-edge |
| 21 | "Recommended for you" always showed the first three exercises, and tapping one only switched tabs | No real recommendations | Coach picks rank neglected moves and muscle groups; tapping opens that exercise |
| 22 | The difficulty setting was saved but never used | No effect | Drives rep targets and the weekly active-minute goal |
| 23 | Hip and torso angles were computed twice under different names | Wasted work per frame | Deduplicated |
| 24 | Plank detection in history compared display strings | Fragile | Uses the typed exercise model |
| 25 | Committed APK was a 104 MB unminified debug build | Within 1 MB of GitHub's file size limit | Minified release build at 24.8 MB |

### Added

- **Design:** Material 3 redesign with gradient hero cards, frosted glass panels over the camera, press-scale micro-interactions, staggered list entrances, animated tab and screen transitions, animated rings, counters, and charts.
- **Onboarding:** four pages with parallax, profile setup, and a reminder opt-in. Skipped automatically for existing users.
- **Planning:** Day, Week, and Month progress views; planned training days; reminders; calendar export; schedule hints.
- **Goals and analytics:** weekly session and minute goals, a fitness score, longest streak, personal bests, top moves, and deletable history.
- **Gamification:** XP, levels, 18 badges, daily challenges, five unlockable themes, and a post-workout celebration screen.
- **Interval circuit:** configurable work, rest, and rounds with audio cues, haptics, pause, and skip.
- **Sound:** five synthesized tracks, a seven-layer ambient mixer, saved mixes, background playback with notification controls, per-exercise recommendations, sound effects and haptics (each toggleable), and a Spotify shortcut.
- **Tablets and landscape:** navigation rail on wide screens, centered content, and a landscape workout layout.
- **Tests:** 32 unit tests covering activity detection, stats, gamification, and reminder scheduling.

### Performance and battery

- Camera analysis runs at about 640×480 with keep-latest backpressure; pausing a workout turns the camera off.
- The audio mixer thread runs only while playing and allocates nothing per sample; volume changes are smoothed.
- Confetti and the equalizer stop drawing frames when idle; stats and XP are computed off the main thread.
- Reminders use inexact alarm windows, so no exact-alarm permission is needed.

### Removed

- Unused `navigation-compose` and instrumentation-test dependencies.
- The single-loop `WorkoutMusicPlayer`, replaced by `AudioEngine`.
