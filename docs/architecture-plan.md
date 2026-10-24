# Architecture improvement plan

Approved and implemented on 2026-10-24. The proposal below records the original rationale and stages. See [architecture.md](architecture.md) for the actual current implementation.

## Implementation status

- Stages 1–2: build baseline established, comparison/progress/hint tests added, repository injected, Room separated.
- Stages 3–5: pure learning rules, immutable sessions, stale-result guards, replaceable speech client, lifecycle cleanup, separate editor/recitation state, and stateless screens implemented.
- Stage 6: attempt/progress writes and deletion are transactional; schema version 1 is exported and legacy data compatibility is tested. Foreign keys, indices, and persisted session kind remain deferred because they require a schema migration.
- Stage 7: one Linux GitHub Actions job builds, runs unit tests, and lints on code pushes/pull requests. Device tests run locally to keep CI small; no APK artifacts are uploaded.
- Navigation uses the existing three-destination flow with explicit Back handling, rather than adding a navigation-library dependency. Theme remains the existing MaterialTheme.
- Local validation: JDK 17 debug build, lint with no errors, 40 unit tests, and eight Android 14 emulator tests passed. Real-provider microphone checks remain manual.

## Recommended approach

Keep one `:app` Gradle module initially. Use MVVM with immutable screen state and unidirectional data flow, constructor-injected dependencies, a Room-backed data layer, and a small set of pure Kotlin learning components. Package boundaries are sufficient for this app's current size. This adapts Android's [architecture recommendations](https://developer.android.com/topic/architecture/recommendations) to the project.

Introduce focused domain components for comparison and progress because these rules are already complex and need independent tests. Use cases should encapsulate meaningful operations such as finishing a recitation; avoid adding a use-case class for every repository call. Android describes the [domain layer as optional](https://developer.android.com/topic/architecture/domain-layer), useful for complexity and reuse.

## Problems to address

| Current design | Practical problem | Proposed boundary |
| --- | --- | --- |
| All application code in a 1,510-line `MainActivity.kt` | UI, storage, recognition, and learning rules change together | Separate packages and files with explicit ownership |
| `AndroidViewModel` constructs Room and its repository | Tests need Android setup and cannot easily replace dependencies | Plain `ViewModel`, constructor injection, application container and factory |
| One editor state also owns training and exam | Previous results and listening flags can leak between screens | Separate editor state and a recitation session shared by training/exam |
| Pass rules and mode transitions embedded in the ViewModel | Sticky flags affect advancement; attempted mode can be lost | Pure `ProgressPolicy` and an immutable session snapshot |
| Completion uses presentation-limited alignment | A 180-pair rendering cap can stop a longer recitation early | Full comparison metadata separate from UI limits |
| Root composable creates a long-lived recognition wrapper | Navigation and segment replacement do not consistently release resources | Explicit recognition adapter and session lifecycle owner |
| Progress save and attempt insert launch independently | Partial writes and changing active state can corrupt session accounting | One suspend repository operation using a database transaction |
| Room models are used directly by the UI | Schema details leak into presentation and learning rules | Small domain models and mapping at the persistence boundary |

## Proposed package structure

Under `poem-checker/app/src/main/java/com/example/poemchecker/`:

```text
MainActivity.kt                 # Compose entry point
app/
  AppContainer.kt              # Database, repositories, dispatchers, factories
  PoemCheckerApp.kt            # App UI and route wiring
data/
  local/                      # Room database, entities, DAOs, migrations
  repository/                 # Room-backed repository implementation and mapping
domain/
  model/                      # Poem, Attempt, TrainingMode, Progress, SessionSnapshot
  comparison/                 # Normalization, alignment, full comparison result
  training/                   # Hint policy, progress policy, session completion
  repository/                 # PoemRepository contract used by consumers
speech/
  SpeechRecognitionClient.kt  # Small replaceable recognition contract
  AndroidSpeechRecognitionClient.kt
ui/
  editor/                     # Route, screen, EditorUiState, EditorViewModel
  recitation/                 # Session state and RecitationViewModel
  training/                   # Training route and screen
  exam/                       # Exam route and screen
  components/                 # Reusable visual components
  theme/
```

The domain packages should contain no Android, Compose, Room, or resource imports. The data implementation depends on domain contracts; the app container constructs concrete dependencies. UI ViewModels consume domain models and repository interfaces. The speech adapter owns Android-specific recognition, while screen lifecycle code owns when a session may listen.

Training and exam should share one recitation implementation with an explicit session kind. Separate ViewModels for every visible screen would duplicate most of their logic; introduce more owners only when responsibilities diverge. Keep the editor separate because draft editing and library management have a different lifecycle.

## Incremental implementation

Each stage should be a separate reviewable commit or PR. Keep extraction changes separate from intentional behavior fixes.

| Stage | Work | Completion criteria |
| --- | --- | --- |
| 1. Establish a baseline | Verify the JDK/Gradle/AGP combination; establish debug build and lint; record device behavior. Extract comparison into a standalone Kotlin file and add focused tests | Repeatable commands; characterization tests for current scoring, normalization, substitutions, omissions, extra words, and long input |
| 2. Separate dependencies and storage | Move Room types and repository into their packages. Add a repository interface, constructor injection, an application container, and ViewModel factory. Keep the schema and queries unchanged | ViewModel tests can use an in-memory fake repository; extraction preserves saved data and existing behavior |
| 3. Extract learning rules | Introduce full comparison metadata, `ProgressPolicy`, hints, and an immutable `SessionSnapshot` containing poem ID, reference text, mode, kind, and session ID. Move CPU-heavy alignment to an injected computation dispatcher and ignore stale results | Completion works beyond the display cap; finishing records the original mode; callbacks from old sessions cannot modify the active session; threshold tests pass |
| 4. Own speech lifecycle | Extract recognition behind the replaceable adapter; define idle/listening/stopping/error states and explicit finish/cancel paths. Route Back, system Back, backgrounding, and disposal through the same cleanup policy | Fake-client tests cover pause/restart/errors/cancel; device checks show no microphone activity after leaving a session and no duplicate finish/save |
| 5. Separate screen state and UI | Create editor and recitation ViewModels, screen-specific immutable state, and stateless screen components. Collect state with lifecycle awareness. Add back-stack handling for editor/training/exam, choosing a navigation library version compatible with the verified toolchain | Editor and exam do not reuse stale results; UI renders from state and invokes callbacks; previews work without Room or a microphone |
| 6. Make persistence consistent | Atomically save a completed attempt and progress; make poem/attempt deletion atomic. Export the current Room schema before introducing schema changes. Add foreign key/index and explicit session kind with tested migrations if adopted | Transaction failure leaves no partial attempt/progress update; migration tests preserve existing poem and attempt data |
| 7. Add ongoing checks | Add GitHub Actions for debug build, JVM tests, and lint. Add focused Room instrumentation tests and Compose tests for permission/navigation/session flows; keep real-provider speech checks manual | CI runs on pull requests; contributors can reproduce checks locally; documentation matches the resulting architecture |

For stage 3, cancelling a coroutine alone is insufficient for a CPU-bound alignment loop: either make the work cooperatively cancellable or reject results whose session/input revision is outdated. Marshal `SpeechRecognizer` calls onto the main thread even when comparison runs on a computation dispatcher.

For stage 4, Android requires main-thread recognition calls and destruction when a recognizer is no longer needed. Its API is also not intended for indefinite continuous recognition. Design bounded recitation sessions with explicit cancellation, safe segment reuse/replacement, and provider-aware retries. See the [SpeechRecognizer API contract](https://developer.android.com/reference/android/speech/SpeechRecognizer).

For stage 5, use `collectAsStateWithLifecycle` at route boundaries and pass state/callbacks to leaf composables. Restore small navigation/session references through `SavedStateHandle`, persist drafts in Room if process-death recovery is required, and restart microphone capture only through an explicit user action. See [Compose state ownership](https://developer.android.com/develop/ui/compose/state-hoisting).

For stage 6, keep the existing database filename `poem_checker_v9.db` and application ID during refactoring. Renaming either is separate from renaming the source folder and can prevent existing installs from accessing their data. Follow the [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions) and avoid destructive migration fallback.

## Decisions to keep separate from structural changes

Keep current thresholds, free mode selection, extra-word scoring, and shared no-hint qualification while moving code. Then make explicit, tested decisions on:

- Whether learned status requires an exam or can still come from no-hint training.
- Whether a failed repeat of a passed stage should auto-advance.
- Whether editing poem text should reset progress, preserve it, or create a new revision.
- Whether `й` and `и` should compare as distinct letters and whether extras should reduce scores.
- Whether detailed attempt history needs retention limits or filtering by practice/exam.

Treat the empty-input exam, incorrect attempt-mode recording, stale exam results, long-poem completion, and recognition cleanup as focused correctness fixes during their respective stages. Mark intended changes in tests rather than freezing known bugs as permanent requirements.

## First implementation slice

Start with a reproducible build and comparison extraction/tests, then inject the repository. These give later session and UI changes a reliable way to detect regressions. Hilt, multiple Gradle modules, and broad dependency upgrades can be evaluated when project scale or concrete build requirements justify them.
