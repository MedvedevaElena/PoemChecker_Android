# Architecture and behavior

This analysis describes the source reviewed on 2026-10-24, including the combined-hint mode. Paths reflect the subsequent project folder rename to `poem-checker/`. It is based on source inspection; an Android build and device session were not run for this documentation change. The proposed refactoring is described separately in [architecture-plan.md](architecture-plan.md).

## Source map

All Kotlin symbols below currently live in [`MainActivity.kt`](../poem-checker/app/src/main/java/com/example/poemchecker/MainActivity.kt).

| Area | Main symbols | Responsibility |
| --- | --- | --- |
| Entry point | `MainActivity`, `PoemCheckerApp` | Compose root, permission handling, recognition wiring, screen selection |
| State | `PoemEditorState`, `TrainingProgress`, `TrainingMode`, `ScreenMode` | Current draft, active poem, speech result, progress, selected screen/mode |
| Persistence | `SavedPoem`, `AttemptEntity`, DAOs, `PoemDatabase`, `PoemRepository` | Local poems, progress, attempts, observable queries |
| Coordination | `PoemCheckerViewModel` | StateFlow updates, scoring, progress, asynchronous saves |
| Speech | `LiveSpeechRecognizer`, `startLineRecognition` | Continuous segmented recitation and separate line-entry activity |
| Training UI | `TrainingScreen`, `buildLiveLines`, `LiveWordCard` | Line grouping, hints, live match/wrong/missing/extra feedback |
| Other UI | `ExamScreen`, editor cards, `buildHints` | Exam controls, editing, library, history, summaries |
| Comparison | `PoemComparator`, `ComparisonResult`, `AlignedWord` | Normalization, word alignment, score, diff and summaries |

Supporting files are the manifest, resource values, backup XML, and Gradle configuration. No network client, backend, authentication, poem catalog, import/export service, analytics integration, or automated test suite is present in the checked-in app.

## State and data flow

Compose events call ViewModel methods. `PoemEditorState` is a `MutableStateFlow` exposed as `StateFlow`; the UI collects it together with Room-backed poem and attempt flows. A `ScreenMode` branch selects editor, training, or exam rather than a navigation framework.

Recognition progress calls `updateLiveRecognizedText`, which recomputes the comparison. Manual stop or training auto-stop calls `finishRecognition`, which computes the final comparison, updates history/pass flags, may advance the selected mode, then launches progress and attempt persistence.

The ViewModel retains state across ordinary activity recreation, but there is no `SavedStateHandle` or other restoration for unsaved editor/session state after process death. Persisted poems must be loaded again from the local library.

## Learning rules

| Mode | Hints | Passing behavior |
| --- | --- | --- |
| `FIRST_WORDS` | First normalized word per nonempty tokenized line in live UI | At least 85% sets `firstWordsPassed`; completion can select `LAST_WORDS` |
| `LAST_WORDS` | Last normalized word per nonempty tokenized line in live UI | At least 85% sets `lastWordsPassed`; completion can select `NO_HINTS` |
| `FIRST_AND_LAST_WORDS` | Both ends, with a single hint for a one-word line | Updates history/best score only |
| `NO_HINTS` | None | At least 90% sets `finalPassed`, in training or exam |

All in-memory unlock flags default to true and are also set true when loading a poem. Remaining lock-related fields and UI wording reflect an earlier staged flow. Pass flags are sticky until the poem/session is cleared; a subsequent lower score does not revoke them.

Auto-advancement checks the accumulated pass flag rather than just the latest score. Consequently, repeating a previously passed first/last-word mode can advance even after a failed attempt. `loadPoem` chooses its initial mode using persisted `finalPassed`, `noHintsUnlocked`, and `lastWordsPassed`; it does not restore the last selected mode.

## Storage

Room opens `poem_checker_v9.db`, version 1, through a singleton. Schema export is disabled and no migrations are supplied.

| Table | Stored fields |
| --- | --- |
| `poems` | Auto-generated ID, title, full text, first/last pass flags, no-hint unlock flag, final pass flag, comma-separated score history, best score |
| `attempts` | Auto-generated ID, poem ID, mode string, percentage, epoch-millisecond timestamp, first 120 characters of recognized text |

Titles use the first text line, at most 40 characters, with a Russian untitled fallback. Saving an existing poem retains its progress even if the text changes. Clearing the editor resets the active session and does not delete saved poems.

The per-poem score history keeps the latest 20 percentages in chronological order. Attempt queries return the latest 20 rows, newest first; inserts do not trim older rows. Best score includes every practice mode and exams. Attempt mode does not distinguish exam from no-hint practice.

Deleting a poem explicitly deletes its attempts first. There is no foreign key or transaction around the two deletes. Progress updates and attempt inserts also run in separate coroutine jobs, so they are not an atomic record of a session.

The manifest requests microphone permission and enables backup. Backup/transfer rule files contain no exclusions. There is no app-managed audio-file storage in the source. Recognition is delegated to the device provider and can involve external processing; absence of a backend does not establish offline-only speech processing.

## Speech lifecycle

Line entry launches a system `ACTION_RECOGNIZE_SPEECH` activity and appends its first returned result as a new poem line. Continuous recitation uses `SpeechRecognizer`, requests Russian free-form partial results, and combines completed segments with the latest partial segment using spaces.

Final segment results and silence/no-match errors restart recognition while `shouldContinue` is true. Silence timings are requested through intent extras; actual behavior depends on the provider. Other errors stop the listening state and display a toast.

Training auto-stops when `isComparisonComplete` finds no expected word without an actual partner in the exposed alignment. Substitutions count as accounted-for words, so completion does not mean a perfect recitation. Exam mode disables this automatic stopping path and requires manual stop.

## Comparison contract

1. Lowercase using the device locale, apply Unicode NFD normalization, and remove combining marks.
2. Replace `ё` with `е`, replace characters outside Latin `a-z`, Cyrillic `а-я`, digits, newline, and space with spaces, then collapse whitespace. Because combining marks are removed, `й` also normalizes to `и`.
3. Align expected and actual words with dynamic-programming edit distance. Insertion, deletion, and substitution each cost one; reconstruction prefers diagonal substitution/match, then deletion, then insertion on ties.
4. Count exact matches in the full alignment. Score is `floor(matchedCount * 100 / expectedCount)`; empty expected text scores 100. Extra words are displayed but do not directly enter the score denominator.
5. Produce bounded presentation lists: 12 mismatch descriptions, 20 distinct missing words, 20 distinct extras, eight line summaries, and 180 diff tokens/aligned pairs.

Alignment takes O(expected words × actual words) time and memory. It runs synchronously on every recognition update through the ViewModel. Long recitations can therefore affect responsiveness.

Live line grouping uses normalized expected-word counts, independently of spoken line breaks. The editor's `buildHints` instead splits raw lines by whitespace and can retain punctuation. Textual line summaries compare newline positions directly; continuous recognition joins segments with spaces, so these summaries may report later lines missing despite a correct whole-poem alignment.

## Observed issues and follow-up candidates

These are source-level findings, not device-reproduced failures. Address them in focused changes with validation.

| Priority | Finding | Consequence / suggested direction |
| --- | --- | --- |
| High | Auto-stop reads `alignedWords`, truncated to 180 pairs | A long poem can appear complete before its tail is spoken; use complete alignment metadata for completion |
| High | Exam back action only changes the screen | Recognition can continue after leaving the exam; coordinate navigation and recognition cleanup |
| High | Empty poem can open the exam; empty expected text scores 100 | Manual completion can mark an empty session passed; validate tokenized input before exam/start/scoring |
| Medium | `finishRecognition` advances the mode before saving the attempt | A first-word attempt can be recorded as last-word, or last-word as no-hint; capture the attempted mode before transition |
| Medium | Advancement relies on sticky pass flags | A failed repeat of a passed stage can still advance; decide whether advancement should depend on this attempt |
| Medium | Starting/reopening an exam does not clear the previous comparison | Training or stale results can be labeled as the last exam result |
| Medium | Segment restart creates a recognizer without explicitly destroying the previous one | Review resource cleanup and callback ownership across segment replacement and errors |
| Medium | Edits retain old pass flags, best score, and history | A substantially changed poem can remain marked learned; define an edit/reset policy |
| Medium | Global normalization removes the breve from `й` | Distinct words can match; decide and test intended Russian normalization |
| Medium | Android 11+ recognition-service package visibility is not declared with a manifest `<queries>` entry | Validate provider availability checks on target devices before deciding whether to add a recognition-service query |
| Maintenance | Single large Kotlin file, no tests, no CI, unverified wrapper/plugin pairing | Establish a reproducible build and focused comparator/progress coverage before broad refactoring |

The existing README's former statement that stages must unlock before an exam was inconsistent with current state defaults. The root README now reflects free mode selection and the shared no-hint qualification rule.
