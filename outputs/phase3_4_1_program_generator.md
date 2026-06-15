# v0.3.4.1 Program Generator and Schedule Overwrite

Version:

- `versionName = "0.3.4.1"`
- `versionCode = 30401`

Scope:

- Plan tab program creation/edit/delete.
- Metadata-based automatic program skeleton generation.
- Safer program schedule overwrite.
- Design spec: `program_skeleton_generator_spec_v0.3.4.1.md`.

Plan UI:

- Program list now supports open, edit, and delete.
- Program detail keeps week/day item display and item editing.
- Program editor can create or edit a program definition.
- Existing program items are loaded into the editor preview.
- Deleting a program removes only `TrainingProgram` and `TrainingProgramItem` rows.
- Deleting a program does not delete workout records, daily metrics, or applied calendar entries.

Program generator:

- Entry point: `ProgramSkeletonGenerator`.
- Repository entry point: `TrainingRepository.generateProgramSkeleton`.
- The generator uses structured `Exercise` metadata and recent confirmed workout history.
- Exercise name text is used only for user-entered exclusion filtering.
- Scoring uses goal fit, badminton transfer metadata, equipment match, movement pattern needs, fatigue cost, and metadata confidence.
- Supported inputs include goal, weeks, weekly training days, session minutes, equipment, exclusions, badminton transfer ratio, sport/strength ratio, and periodization type.
- Supported periodization outputs include step/deload, badminton wave, daily undulating, and linear strength behavior.

Program editor UX:

- Program name is required before skeleton generation and save.
- `자동으로 골자 만들기` generates a preview from the current editor inputs.
- `전부 새로 만들기` sits below the automatic skeleton button and clears the current preview before generating a fresh skeleton from the current editor inputs.

Generated item behavior:

- Items become `TrainingProgramItem` rows when saved.
- Generated prescription text includes the weight source label for review.
- The preview can edit set count, reps, kg, seconds, rest, and prescription before saving.
- Weight autofill is conservative and uses confirmed history first.
- If history is missing, kg can remain empty for manual input.

Schedule overwrite policy:

- Program application still creates `WorkoutEntry` and `WorkoutSet` rows.
- Program-created sets are always `confirmed=false`.
- `Overwrite` deletes only planned-only schedule entries in the target date range.
- A planned-only entry is an entry with no `confirmed=true` set.
- Existing completed records are preserved.
- The conflict dialog reports the target date range, planned entries to replace, new planned entries, and confirmed sets that will be preserved.

DB migration:

- Room database version is now `6`.
- Migration `5 -> 6` adds optional program definition fields to `training_programs`.
- The migration is additive only and does not delete user data.

Tests:

- `ProgramSkeletonGeneratorTest` covers weekly training day distribution, badminton transfer ratio behavior, and direct-history kg autofill.

Known limits:

- Pain or contraindication filtering is currently limited to user-entered exclusion text plus metadata penalties.
- The generator does not yet have a dedicated injury-condition taxonomy column.
- Applied calendar plans are not rewritten when a saved program definition is edited; the user must apply the program again.
- Room overwrite behavior should receive an in-memory DAO test in a later patch.

## Record Calendar Range Delete Add-on

Scope:

- Record tab calendar long-press menu now includes `select delete`.
- The user long-presses a start date, chooses `select delete`, then taps an end date.
- The app summarizes the selected range before deletion.

Delete choices:

- `delete unconfirmed only`: deletes only `WorkoutSet.confirmed=false` sets in the selected date range.
- `delete including confirmed`: deletes all workout entries and sets in the selected date range.

Data meaning:

- `confirmed=false` still means planned or not completed.
- `confirmed=true` still means completed actual record.
- Deleting unconfirmed-only preserves confirmed sets and reorders remaining set indexes from 1.
- Daily sleep/body-weight metrics are not deleted.

## Ponytail Fallback Rules Applied

The local Ponytail fallback rules were found at:

- `C:/Users/pki08/Documents/Codex/2026-06-15/ponytail-1-2-ponytail-marketplace-codex/AGENTS.md`

Applied scope:

- Kept changes small and inside existing Plan / Calendar / Repository / DAO patterns.
- Avoided new libraries or broad architecture changes.
- Preserved data-loss prevention for confirmed records unless the user explicitly chooses the destructive range-delete option.
- Preserved additive-only DB migration behavior.
- Ran narrow verification: unit tests and debug APK build.

## Back Navigation Policy Add-on

Version remains `0.3.4.1`.

Behavior:

- Pressing Android back outside Home moves the app to Home first.
- Pressing Android back on Home keeps default system behavior, so the app can close.
- Record calendar back first cancels active calendar modes/dialogs, otherwise returns to the selected record date.
- Plan editor back cancels create/edit mode.
- Plan detail back returns to the program list.
- Analysis detail cards close their expanded `details` section before leaving the Analysis tab.

Ponytail safety note:

- This patch only adds Compose `BackHandler` callbacks.
- It does not change DB schema, CSV, analysis formulas, or confirmed/planned data semantics.

## Rest Timer Overlay Add-on

Behavior:

- Rest timer state now records whether there is a next target.
- Overlay is shown only when a timer is active and a next target exists.
- Confirming the final set can keep the in-app timer behavior, but app-outside overlay is suppressed.

Safety note:

- No DB migration.
- No analysis logic change.
- No CSV behavior change.
- Legacy overlay behavior audit: `outputs/rest_timer_overlay_legacy_audit_v0.3.4.1.md`.
