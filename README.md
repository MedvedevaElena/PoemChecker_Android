# PoemsTeacher

An Android app that helps students learn poems by heart: enter a poem, practise reciting it with word hints, and compare speech recognition results with the original text. The current interface and speech recognition language are Russian.

The Android project lives in [`poem-checker/`](poem-checker/). It is a single-module Kotlin app using Jetpack Compose, Android speech recognition, and a local Room database. There is no application backend in this repository.

## Current learning flow

1. Type or paste a poem, preserving line breaks, or dictate one line at a time.
2. Save it to retain progress and attempts across app restarts. Unsaved poems can also be practised, but their attempts are not stored.
3. Choose hints: first word, last word, both ends of each line, or no hints. All four modes are currently available immediately.
4. Start training and grant microphone access. Recognized words appear as colored cards; unspoken hint words stay visible in blue. Other unspoken words are hidden while listening and revealed afterward.
5. Stop manually or let training stop when its comparison considers the poem complete. Scores of at least 85% mark the first-word or last-word stage as passed. The app can then advance the selected mode.
6. Practise without hints or open the separate exam screen. A score of at least 90% in `NO_HINTS` marks the poem as learned, including during ordinary training.

The score is the percentage of original words matched after normalization and alignment. Extra spoken words are reported but do not directly reduce the score. This measures recognized text agreement; it does not assess expression, pronunciation, or whether recognition errors came from the student.

## Development

Open `poem-checker/` in Android Studio. Configure an Android SDK containing platform 34 and set its location through Android Studio or an untracked `local.properties` file. Use JDK 17 for Gradle, matching CI. Java/Kotlin compilation also targets Java 17. On macOS, select the installed JDK 17 in Android Studio’s Gradle settings.

Commands from the repository root:

```sh
cd poem-checker
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

The debug APK normally appears at `app/build/outputs/apk/debug/app-debug.apk`, relative to the Android project. The refactor was validated with JDK 17: debug build, lint with no errors, 40 JVM tests, and eight Android 14 emulator tests passed. Actual microphone recognition still needs device/provider checks.

| Setting | Checked-in value |
| --- | --- |
| Minimum Android version | Android 9 / API 28 |
| Compile / target SDK | 34 / 34 |
| Application ID / namespace | `com.example.poemchecker` |
| App version | `0.9` (`versionCode = 10`) |
| Gradle wrapper | 9.3.0 |
| Android Gradle plugin | 8.5.2 |
| Kotlin / KSP | 1.9.24 / 1.9.24-1.0.20 |
| Compose compiler / BOM | 1.5.14 / 2024.09.00 |
| Room | 2.6.1 |

The checked-in toolchain has been exercised locally with JDK 17. Existing Gradle deprecation and dependency-age warnings remain; upgrades should be deliberate changes.

Use a device or emulator with an installed Russian-capable speech recognition provider to exercise microphone flows. Offline availability depends on that provider; the app does not explicitly request on-device-only recognition. The app stores poem text, progress, timestamps, and short recognized-text previews locally. Android backup is enabled, and no exclusions are configured.

## Continuous integration

[Android checks](https://github.com/MedvedevaElena/PoemChecker_Android/actions/workflows/android.yml) runs on code pushes to `master`, pull requests, or manual dispatch. One standard Linux runner uses JDK 17 to build the debug app, run unit tests, and lint. Gradle dependencies are cached; no APK artifacts are uploaded. Documentation-only changes skip automatic runs.

You can continue developing on macOS. Local Room and Compose/navigation tests run with `./gradlew :app:connectedDebugAndroidTest` against an emulator; they are not part of the Linux CI job. See [validation.md](docs/validation.md) for device selection and reports.

## Project documentation

- [`AGENTS.md`](AGENTS.md): working instructions for coding assistants.
- [`docs/architecture.md`](docs/architecture.md): source map, state, storage, comparison rules, and observed issues.
- [`docs/architecture-plan.md`](docs/architecture-plan.md): approved refactoring stages and implementation status.
- [`docs/validation.md`](docs/validation.md): build checks and manual regression scenarios.
- [`poem-checker/README.md`](poem-checker/README.md): short Russian overview.
