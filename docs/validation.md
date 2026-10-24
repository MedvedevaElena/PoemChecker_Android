# Validation guide

## Build, unit tests, and lint

Use JDK 17 and Android SDK platform 34. Open `poem-checker/` in Android Studio, or run from that directory:

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS, select JDK 17 in Android Studio's Gradle settings. In a terminal with JDK 17 installed, `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` selects it for subsequent commands.

The same three tasks run in [the GitHub workflow](../.github/workflows/android.yml) on a standard Linux runner with JDK 17. Code pushes to `master` and code pull requests trigger checks; workflow dispatch is also available. The job cancels superseded runs, has a 15-minute timeout, caches Gradle dependencies, and does not upload APK artifacts. Device tests stay outside this small CI job.

The checked-in Gradle 9.3.0, AGP 8.5.2, Kotlin 1.9.24 and JDK 17 combination passed locally. Existing SDK/dependency age warnings and Gradle deprecation warnings remain. Record concrete failures before changing toolchain versions.

Reports, relative to `poem-checker/`:

- JVM tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint: `app/build/reports/lint-results-debug.html`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Emulator tests

Start an Android emulator and select it explicitly when several devices are attached:

```sh
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Replace the serial with the value shown by `adb devices`. Instrumentation reports appear under `app/build/reports/androidTests/connected/debug/`. These tests do not require a speech provider or use a physical device's microphone.

## Verified baseline

On 2026-10-24, the final refactor passed:

- Debug APK build and Android test APK compilation with JDK 17.
- 40 JVM tests, including normalization/alignment, thresholds, hints, fake-repository editor operations, stale-session rejection, duplicate-finish prevention, save retry, and fake-engine speech cleanup.
- Eight Android 14 emulator tests: five Room tests and three Compose/navigation tests.
- Lint with zero errors. Remaining warnings concern older target/dependency versions.

Room tests cover transactional rollback after a failed attempt insert, atomic deletion, latest-20 queries without pruning, reopening storage, and opening the original version-1 schema with saved text/progress/attempts. The exported schema identity also matches the pre-refactor generated database.

Compose tests cover hiding unspoken non-hint words, hiding incomplete exam results, and returning from an exam to the same draft with system Back. Real-provider microphone behavior was not validated by these automated tests.

## Manual speech and lifecycle checks

Use a Russian-capable recognition provider and preserve reference line breaks. Separate provider transcription errors from comparison bugs.

| Area | Scenario and expected behavior |
| --- | --- |
| Entry | Type/paste a multiline poem; dictate a line and verify it appends. Cancelling dictation should add nothing |
| Permission | Deny access and verify listening ends with an error; grant it later and retry |
| Live hints | Try first/last/both/no hints with blank and one-word lines; only hints and spoken words should appear while listening |
| Pause/restart | Pause between lines and continue; finalized segments should remain in the transcript. Three consecutive empty segments should stop retries and offer another start |
| Completion | Recite a short poem fully for training auto-stop; stop early to inspect missing words; verify long poems do not stop at the display limit |
| Exam | Start and stop manually; no reference hints or partial result should be labeled as a completed exam |
| Navigation | Leave training/exam using toolbar or system Back while listening; microphone activity should stop without creating an attempt |
| Background/rotation | Background or rotate during capture; capture should stop and require an explicit restart. A save already in progress should complete or roll back atomically |
| Persistence | Save, practice, reload and restart; progress/attempts should remain. Clear should reset only the session; delete should remove the poem and its attempts |

Unsaved drafts are not durably restored after process death. No-hint practice still grants learned status, and edits still retain previous progress. These are documented product rules/limitations rather than regressions.

For documentation-only changes, compare against source, verify relative Markdown links, and run `git diff --check`. Do not report unexecuted builds or device checks as passing.
