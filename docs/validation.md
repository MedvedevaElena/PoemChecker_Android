# Validation guide

## Build checks

From `poem-checker/`:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Check the configured JDK, SDK platform 34, dependency access, and the wrapper/plugin pairing if configuration fails. Record the actual error before changing toolchain versions. The compilation target is Java 17. There are no checked-in unit or instrumentation test sources, so a successful test task can mean no tests ran.

Documentation-only changes: review against the current source, verify relative Markdown links, and run `git diff --check`. No build or device check was run as part of the initial documentation analysis on 2026-10-24.

## Focused manual scenarios

Use a Russian-capable recognition provider and preserve the reference poem's line breaks. Provider errors and incorrect transcriptions should be distinguished from comparison bugs.

| Area | Scenario and expected baseline |
| --- | --- |
| Entry | Type/paste a multiline poem; dictate another line and verify it appends with a newline |
| Permission | Deny microphone access and verify listening ends; grant it later and verify training and line dictation work |
| Storage | Save, reload, edit/update, and restart the app; saved text and progress should remain. Clear should reset only the session; delete should remove the poem and its attempts |
| Unsaved work | Practise an unsaved poem; in-memory scores update, but no attempt rows should be created |
| Hints | Try all four modes with multiline, blank-line, punctuation, and one-word-line input; first/last/both/no hints should follow the selected mode |
| Live display | Before speaking, see only hint words; while speaking, see green matches, red substitutions, and yellow extras. Unspoken non-hint words appear after stop |
| Pause/restart | Pause between lines and continue; completed segments should remain in the combined transcript |
| Completion | Recite a short poem fully and check training auto-stop; separately stop early and inspect missing words |
| Progress | Check the 85% first/last-word and 90% no-hint thresholds; combined hints should not set pass flags |
| History | On a saved poem, complete attempts and inspect score, timestamp, mode, preview, best score, and latest-20 presentation |
| Exam | Start/stop an exam without hints; inspect the result and learned flag, then leave while listening and check microphone cleanup |
| Lifecycle | Rotate or leave/reopen the app while listening; check resource cleanup and state consistency. Unsaved state after process death is not restored by current code |

Known discrepancies, including attempt-mode recording, exam cleanup, and long-poem completion, are listed in [`architecture.md`](architecture.md). Record these as existing issues when reproduced rather than treating the whole table as a claim of passing behavior.

## Useful future automated coverage

When making relevant behavior changes, consider focused tests for:

- Normalization: punctuation, case, whitespace, digits, `ё/е`, and the current `й/и` equivalence.
- Alignment: exact match, substitution, insertion, deletion, repeats, ordering, and empty input.
- Scoring: integer truncation, extra-word behavior, and complete scores beyond the 180-pair display cap.
- Progress: immediately selectable modes, threshold boundaries, sticky flags, repeated failed attempts, and combined-hint behavior.
- Session completion/persistence: attempted mode before advancement, training versus exam identity, navigation cleanup, and long-poem completion.

These are coverage candidates; no new test framework or tests were added for the documentation task.
