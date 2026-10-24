# Working in PoemsTeacher

## Purpose and source map

Help students learn poems by heart through spoken practice. The current product uses Russian UI and `ru-RU` recognition on Android 9+.

Read `README.md`, `docs/architecture.md`, and `docs/validation.md` before changing behavior. The runnable Android project is `poem-checker/`. This file applies throughout the repository. `docs/architecture-plan.md` records the approved refactor and its implementation status.

Kotlin packages under `app/src/main/java/com/example/poemchecker/`:

- `app/`: dependency container, factories, routes, permission launchers, lifecycle and Back handling.
- `domain/`: Android-independent models, comparator, hints, progress rules, completion operation, repository contract.
- `data/`: Room entities/DAOs/database and transactional repository implementation.
- `speech/`: recognition contract, testable segment controller, Android adapter.
- `ui/`: editor and recitation ViewModels, stateless editor/training/exam screens, components, previews.
- `MainActivity.kt`: Compose entry point only.

Read current source and `git diff` before editing. Preserve user changes. Keep architectural boundaries: the domain must not import Android, Compose, or Room; ViewModels receive dependencies rather than constructing databases; leaf screens receive state and callbacks.

## Behavior to preserve

- Preserve poem line breaks, Russian wording, and recognition language unless the task changes localization.
- All four training modes are freely selectable. First/last-word passing threshold is 85%; no-hint is 90%. Combined hints only update history/best score.
- Pass flags are sticky, but auto-advancement depends on the current attempt's score.
- No-hint training and exam share qualification. Exam-only learned status is a product change.
- Comparison uses normalized exact word matches after edit-distance alignment. Extras have no direct score penalty; existing normalization makes both `ё/е` and `й/и` equivalent.
- Completion uses the full alignment, independent of presentation limits. Hide unspoken non-hint words while listening.
- Keep the immutable attempted mode/reference/poem ID when finishing. Reject callbacks/comparison results from older sessions and input revisions.
- A session may carry a saved poem ID only when its draft matches the saved text, ignoring outer whitespace. Edited drafts practise with a null poem ID until explicitly updated; keep their results separate from saved progress. Changing/reverting text must discard results from the previous draft.
- Queue returned dictation during loading/saving, restore the queue through SavedStateHandle, and append it afterward without swallowing operation errors.
- Exam history/previews must stay hidden until a completed exam result exists and disappear on restart/cancellation.
- Only attempts for saved poems are persisted. Display the latest 20 records without assuming older rows are pruned.
- Back, backgrounding, and disposal cancel capture without saving an incomplete attempt. Resuming must not automatically restart the microphone.

## Implementation guidance

- Keep one Gradle module unless concrete scale or build needs justify more. Prefer focused use cases and manual constructor injection over adding frameworks incidentally.
- Route UI events to ViewModels, expose immutable StateFlow state, and collect it with lifecycle awareness.
- Run alignment on the computation dispatcher. Android recognizer APIs and resource cleanup must run on the main thread. Cancelling a CPU coroutine alone is not a stale-result guard.
- Attempt/progress saves and poem/attempt deletion must remain atomic. Report save errors through state and preserve retry data.
- Database filename remains `poem_checker_v9.db`, version 1, despite the source folder rename. Preserve the application ID. Schema changes require a deliberate version/migration strategy, exported schemas, and compatibility tests; do not use destructive migration fallback.
- Mode names are stored in rows. Renaming enum constants changes stored identifiers. Session kind is not yet persisted separately.
- Do not commit SDK paths, build output, IDE state, credentials, or signing material. Avoid incidental dependency upgrades.

## Validation and reporting

Run relevant commands from `poem-checker/`; see `docs/validation.md`. CI uses JDK 17 on a standard Linux runner to build, run JVM tests, and lint. Device tests exercise actual Room behavior and Compose/navigation on a local emulator. Speech-provider checks remain manual.

Add meaningful regression coverage for changes to comparison, progress, storage, or session lifecycle. Report exactly which checks ran and any blockers. Documentation-only changes need source/link checks and `git diff --check`, not an Android build.

Update documentation when changing architecture, learning/scoring rules, persistence, speech lifecycle, build setup, or CI.
