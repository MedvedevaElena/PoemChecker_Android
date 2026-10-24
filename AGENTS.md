# Working in PoemsTeacher

## Purpose and scope

Help students learn poems by heart through guided spoken practice and comparison with a reference poem. The product currently targets Russian-speaking users on Android 9+.

Read `README.md`, `docs/architecture.md`, and `docs/validation.md` before changing behavior. The runnable project is `poem-checker/`, not the repository root. This file applies throughout the repository.

`docs/architecture-plan.md` describes proposed future work; its target packages and stages are not yet implemented.

## Source of truth

- Nearly all application code is in `poem-checker/app/src/main/java/com/example/poemchecker/MainActivity.kt`: models, Room, repository, ViewModel, speech recognition, Compose screens, and comparator.
- Build versions live in the root and app `build.gradle.kts` files inside `poem-checker/` and its `gradle/wrapper/gradle-wrapper.properties`.
- Read current code and `git diff` before editing. Preserve existing user changes. Documentation describes the working tree and may include behavior that has not yet been committed.
- Distinguish implemented behavior from intended improvements. The issue list in `docs/architecture.md` is an analysis, not authorization to fix everything.

## Behavioral constraints

- Preserve poem line breaks: hints and live word grouping depend on them.
- Keep Russian UI wording and `ru-RU` recognition unless the task explicitly changes localization.
- All four training modes are freely selectable. Do not restore sequential locks based on old README descriptions or the remaining `*Unlocked` fields.
- First/last-word pass threshold is 85%; no-hint pass threshold is 90%. Combined hints only update score history and best score.
- No-hint training and the exam share a training mode and pass flag. A separate exam-only qualification would be a product behavior change.
- Comparison currently uses normalized exact word matches after edit-distance alignment. Extra words have no direct score penalty. Changing normalization, alignment, or scoring requires explicit attention to changed learning outcomes.
- Only attempts for saved poems are persisted. The UI shows the latest 20 attempt records, but older database records are not pruned.
- The training screen hides unspoken non-hint words while listening. Preserve this when changing live rendering.

## Implementation guidance

- Keep changes focused. Splitting the large Kotlin file is a possible future refactor, not a prerequisite for every fix.
- Follow the existing Compose, StateFlow, coroutine, and repository patterns. Account for asynchronous callbacks and state changes when saving attempts or progress.
- Room database version is 1 with schema export disabled. Any schema change needs a deliberate version/migration strategy that preserves saved poems and attempts.
- `TrainingMode.name` is stored in attempt rows. Renaming enum values changes stored identifiers; consider existing data.
- Manage recognition lifecycle across starts, segment restarts, stops, navigation, and disposal. Do not assume silence always ends a student's recitation or that recognition runs offline.
- Keep local SDK paths, build output, IDE state, credentials, and signing material out of version control. Do not commit generated artifacts or introduce dependency upgrades incidentally.

## Validation and reporting

Run relevant checks from `poem-checker/` as described in `docs/validation.md`. There are currently no checked-in automated tests. Documentation-only work can be verified through source comparison, link checks, and `git diff --check`; an Android build is not required for prose-only edits.

For behavior changes, use the focused manual scenarios and add meaningful automated coverage when appropriate, particularly for comparison and progress transitions. Report exactly which checks ran and any environment blockers. Never describe unexecuted builds or device checks as passing.

Update the documentation when changing training rules, scoring, persistence, speech lifecycle, architecture, or build setup.
