# Explicit Metadata Override And Safe Restore Baseline

Baseline: `6e6f9fbedd1c121398afd3a92c9dd7217b6d4a6a` (`origin/main`)

## Metadata writes

- `ExerciseMetadataEditorService.saveExerciseEditor` currently updates the materialized `Exercise` row and replaces one complete `runtime_exercise_metadata` row. It does not record field-level user intent.
- `resetExerciseMetadataOverride` deletes the runtime row and reapplies the current seed while preserving `isActive`, `archivedAt`, and accumulated `needsReview`.
- `TrainingRepository.upsertSeedExercises` currently treats the presence of a runtime row as a built-in override and skips seed convergence. This cannot distinguish user edits from stale seed rows.
- `seedExerciseRoleRelations` treats `BACKUP_RESTORE` relation provenance as override authority. The editor does not currently expose TrainingRole or ProgramSlotCapability editing.
- Custom exercises are complete user-owned `Exercise` plus runtime metadata rows. Built-in `Exercise.name` is currently editable in the dialog even though canonical identity uses `stableKey`.

## Metadata restore and identity

- Format 11 serializes effective `exercise_metadata_snapshot` rows plus runtime and relation rows. Restore currently applies those snapshots directly to current built-ins.
- `BackupRestoreCanonicalizer` resolves by exact stableKey, approved legacy alias, backup definition, or minimal stub. Current catalogue membership is not a blanket allowlist.
- `BackupRestoreImportService` restores current built-ins, custom exercises, and catalogue-missing identities, but it has no explicit override authority or represented-zero distinction.
- `RuntimeExerciseMetadataResolver` currently gives a persisted runtime row broad authority, protecting only canonical name, history-only eligibility, recovery profile, seed mutation safety, and app cue.
- Numeric Room IDs are local. Program restore already uses `TrainingProgram.stableKey` plus logical item position. Format 11 smash parent references still serialize a source numeric workout ID.

## Workout identity and dependents

- `WorkoutEntry.backupSourceId` has a nullable unique Room index. Export invents an `entry:{id}:{createdAt}` value for null rows but does not persist it.
- Creation paths are `RecordMutationService`, `CalendarRecordService`, `ProgramPlanService`, and `BackupRestoreImportService`. Date copy currently duplicates an existing source ID; ordinary local creation leaves it null.
- `WorkoutSet.entryId` is an owned-child reference without a Room FK. Deletion code explicitly removes sets first.
- `SmashSpeedRecord.parentWorkoutEntryId` is an optional reference without a Room FK. It is currently restored as an unremapped source numeric ID.
- Daily metrics, daily check-ins, and unparented smash-speed records are independent date-keyed domains and are not workout-owned.

## app_meta inventory

- Local infrastructure: `exercise_seed_version`, `program_seed_version`, `program_stable_key_repair_version`, `data_transfer_report_*`, strength rebuild/bootstrap/provenance markers, and the new lineage/reconciliation/revision markers required by this task.
- Existing backup carries only the strength posterior bootstrap marker as source provenance; generic app_meta replacement does not currently exist.
- No existing app_meta key is treated as general portable user state. User profile and daily state use their own tables.

## Backup domains and current semantics

- Exercises/runtime metadata/relations: merge by stableKey; current name protection; effective snapshot restore.
- Workouts/sets: append, skip exact legacy duplicates, and reject same source ID with divergent content.
- Programs/items/item sets/tombstones: complete snapshot replacement using program stableKey and logical item position.
- Profile: singleton replacement. Daily metrics/check-ins: date-key upsert/merge. Smash speeds: append unless date/attempt/speed/note duplicate.
- Strength posterior payload: not restored as authority; raw workout history schedules a current-model rebuild.
- Restore is one Room transaction after parsing/canonicalization, but there is no user-selectable preflight or transaction-start content fingerprint.

This audit freezes existing non-selectable-domain semantics. The task changes only explicit metadata ownership, workout overlap handling, active exercise-list handling, and the integrity fixes required for their reference graph.
