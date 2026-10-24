# Architecture and behavior

Implemented and validated on 2026-10-24. The app uses one Android Gradle module, MVVM, immutable StateFlow screen state, constructor injection, and separate domain, data, speech, and UI packages. The implementation sequence and deferred choices are recorded in [architecture-plan.md](architecture-plan.md).

## Source map

Paths below are relative to `poem-checker/app/src/main/java/com/example/poemchecker/`.

| Location | Responsibility |
| --- | --- |
| `MainActivity.kt` | Activity, edge-to-edge setup, Compose entry point |
| `app/AppContainer.kt` | Application container, Room repository, ViewModel factories, recognition adapter |
| `app/PoemCheckerApp.kt` | Routes, microphone permission and line-dictation launchers, lifecycle cleanup, system Back |
| `domain/model/` | Android-independent poem, attempt, progress, session snapshot, and completed result |
| `domain/comparison/PoemComparator.kt` | Normalization, full word alignment, completion metadata, score and bounded diff summaries |
| `domain/training/` | Hint generation, progress policy, completed-recitation operation |
| `domain/repository/PoemRepository.kt` | Persistence contract consumed by the ViewModels and learning operation |
| `data/local/` | Room entities, DAOs and database |
| `data/repository/RoomPoemRepository.kt` | Domain/entity mapping and transactional persistence |
| `speech/` | Replaceable speech client, segmented recognition controller, Android provider adapter |
| `ui/editor/` | Draft/library management ViewModel, immutable editor state, stateless screen |
| `ui/recitation/` | Training/exam session state and ViewModel |
| `ui/training/`, `ui/exam/` | Stateless practice and exam screens |
| `ui/components/`, `ui/ScreenPreviews.kt` | Shared rendering and previews without database or microphone dependencies |

The domain has no Android, Compose, or Room imports. The data implementation maps entities to domain models. The application container wires dependencies; ViewModels do not construct databases or receive an Application. A manual container is sufficient at this size.

## State and navigation

Compose routes collect ViewModel state with `collectAsStateWithLifecycle` and pass values/callbacks to screens. The editor and recitation have separate state owners. Training and exam share recitation logic with an explicit `SessionKind`.

The editor is the hub for two sibling destinations. A small saved route enum and `BackHandler` provide navigation; toolbar Back and Android system Back use the same cleanup action. A navigation library is not currently needed for this three-screen flow.

Opening a session captures a new ID, poem ID, reference text, mode, and kind. Empty tokenized references are rejected. Starting a fresh attempt clears the previous transcript, comparison, and completed result. Exam mode always uses `NO_HINTS` and only shows a completed exam result after manual finish. Previous attempt history, including recognized-text previews, stays hidden until an exam is completed and is hidden again on restart or cancellation.

Recognition callbacks carry the session ID. Comparison runs on an injected computation dispatcher, and the ViewModel accepts results only for the latest session/input revision. Cancelling a comparison job does not guarantee the CPU loop stops immediately; revision checks prevent stale output from changing the active UI.

Finishing freezes the session and transcript, computes the final score, applies progress rules, and saves through one repository operation. While saving, repeated finishes and navigation to another session are blocked. Failed saves expose a retry action and retain the original attempt mode. Completion updates the editor's progress and next selected mode.

Leaving or backgrounding cancels microphone capture without recording a completed attempt. Returning does not automatically restart capture. `onCleared` and composition disposal also release capture. Small saved-poem references use `SavedStateHandle`; a saved poem can reload after process death, while an unsaved draft and active recitation are not durably restored. Dictation results arriving during loading or saving are queued in `SavedStateHandle` and appended in order afterward. A failed load/save still retains the returned lines and its error message.

## Learning rules

| Mode | Hints | Passing behavior |
| --- | --- | --- |
| `FIRST_WORDS` | First word per line | At least 85% sets the first-stage pass flag and selects last words for the next attempt |
| `LAST_WORDS` | Last word per line | At least 85% sets the last-stage pass flag and selects no hints for the next attempt |
| `FIRST_AND_LAST_WORDS` | Both ends, one hint for a one-word line | Updates history and best score only |
| `NO_HINTS` | None | At least 90% marks the poem learned in training or exam |

All modes are selectable immediately; obsolete unlock flags have been removed from domain/UI state. The legacy database column remains for compatibility. Pass flags are sticky, but a failed repeat no longer advances the mode based on an earlier pass.

Hints remain visible while listening. Unspoken non-hint words remain hidden until listening stops. Matches are green, substitutions/missing words red, extras yellow, and unspoken hints blue. Live grouping uses normalized expected-word counts per line. Editor hints retain the original text's whitespace-based words and punctuation.

## Comparison contract

1. Lowercase using the device locale, apply Unicode NFD normalization, and remove combining marks. Replace `ё` with `е`. This existing rule also makes `й` and `и` equivalent.
2. Replace characters outside Latin `a-z`, Cyrillic `а-я`, digits, newline, and space with spaces; collapse whitespace.
3. Align expected and actual words with edit distance. Insertions, deletions, and substitutions cost one; reconstruction prefers diagonal match/substitution, deletion, then insertion on ties.
4. Score is `floor(matchedCount * 100 / expectedCount)`. Extras are reported without a direct score penalty. The comparator's empty-reference convention remains 100%, but session validation prevents empty recitations.
5. Keep full aligned words for completion and live grouping. Bound only presentation summaries: 12 mismatch descriptions, 20 distinct missing words, 20 distinct extras, eight line summaries, and 180 diff tokens.

Training auto-stops when every expected word has an actual partner in the full alignment. Substitutions count as accounted for, so completion does not imply a perfect score. Exam requires manual stop.

Alignment takes O(expected words × actual words) time and memory. Line summaries still compare newline positions directly; continuous recognition joins segments with spaces, so these summaries can incorrectly describe later lines as missing despite a correct overall alignment.

## Persistence and compatibility

Room still opens `poem_checker_v9.db`, version 1, under application ID `com.example.poemchecker`. Schema export is now enabled, and the baseline JSON is tracked in `app/schemas/`. Its identity matches the original database; an instrumentation test opens a legacy SQL fixture and verifies its poem, progress, and attempt survive. No schema migration is needed for these package moves.

| Table | Stored fields |
| --- | --- |
| `poems` | ID, title, text, first/last pass flags, legacy no-hint unlock flag, final pass flag, comma-separated score history, best score |
| `attempts` | ID, poem ID, mode name, percentage, epoch-millisecond timestamp, recognized-text preview |

Titles use the trimmed text's first line, at most 40 characters, with a Russian untitled fallback. Saving an edited poem retains progress. Clearing the editor does not delete saved poems.

Only saved poems whose current draft matches the saved text persist attempts (outer whitespace is ignored). The editor tracks the saved text and saved progress separately. Unsaved edits use a null session poem ID and keep practice results in memory until an explicit save; they cannot update the older database text’s progress. Changing the reference text clears its transient comparison/results and starts from the last saved progress, so reverting a draft cannot transfer its practice results to the original poem. The existing policy of retaining previously saved progress on explicit edits remains unchanged. Recording an attempt and updating progress share a Room transaction; deleting a poem and its attempts also shares a transaction. Attempt recording changes progress without overwriting poem text. A forced-insert-failure test verifies that progress is rolled back with the failed attempt.

History keeps the latest 20 percentages chronologically. Attempt queries show the latest 20 rows newest first without pruning older rows. Best score includes every mode. Database mode strings still do not distinguish no-hint training from exams; explicit kind currently belongs to session state only. There are no foreign keys or additional indices; adopting them needs a separate versioned migration.

## Speech and privacy

Line entry launches a system recognition activity. Continuous recitation uses a replaceable segmented client. Each finished segment releases its Android recognizer before scheduling another; stopped sessions invalidate queued restarts and old callbacks. Silence/no-match errors retry, with a three-consecutive-empty-segment limit. Permanent errors release capture and expose an error state.

The Android adapter requests Russian free-form partial recognition, uses application context, and calls recognizer APIs on the main thread. The manifest declares microphone permission and recognition-service package visibility. Recognition availability and offline operation depend on the installed provider; no on-device-only guarantee is made.

There is no application backend, network client, authentication, or app-managed audio-file storage. Recognized previews are stored locally. Android backup remains enabled with no exclusions configured.

## Remaining choices and limitations

- Real-provider microphone accuracy and device-specific pause behavior still require manual testing.
- Unsaved draft/process-death recovery could be added through persistent draft storage.
- Poem edits retain old progress; an edit/reset or revision policy is a separate product decision.
- Russian normalization, extra-word penalties, and exam-only qualification remain unchanged pending product decisions.
- Textual line summaries need an alignment-based implementation for reliable line-level feedback.
- Existing lint warnings concern older SDK/dependency versions; there are no lint errors. Dependency/platform upgrades are a separate maintenance task.
