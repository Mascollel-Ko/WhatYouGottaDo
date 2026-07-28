# Exercise identity reference audit

Generated for the v0.5.0.6 stableKey-only migration.

## Result

- Normal application exercise identity: `Exercise.stableKey` only.
- Persisted runtime references: `WorkoutEntry.exerciseStableKey` and
  `TrainingProgramItem.exerciseStableKey`.
- Numeric exercise identity fields in the current Room schema: none.
- Normal runtime name fallback: none.
- New backup numeric exercise ID columns: none.

## Remaining `exerciseId` / `exercise_id` text

| Location | Remaining use | Classification |
|---|---|---|
| `app/src/main/java/com/training/trackplanner/data/ExerciseStableKeyMigration.kt` | Reads the old `exerciseId` columns while migrating schema 24 to 25. | Required one-time migration input; it is not present in the final runtime tables. |
| `app/src/androidTest/java/com/training/trackplanner/data/TrainingDatabaseMigrationTest.kt` | Builds schema-24 fixtures containing `exerciseId`. | Required upgrade fixture proving valid, zero, dangling, custom, and ambiguous references are handled. |
| `app/src/test/java/com/training/trackplanner/data/BackupIntegrityBoundaryTest.kt` | Asserts that `exercise_id` is absent from the new backup header. | Negative regression assertion, not identity storage. |
| `app/src/main/assets/exercise_images/opentraining_exercises.json` | Third-party OpenTraining image-catalog field named `exerciseId` and `sourceExerciseId`. | External image asset identifier; never used as the app exercise identity. |
| `app/schemas/.../9.json` through `24.json` | Historical Room schema snapshots containing the old column. | Immutable migration history required by Room migration tests. |
| `app/schemas/.../25.json` | `sourceExerciseId` in `exercise_identity_migration_issues`. | Diagnostic evidence preserving the old numeric value for unresolved migration review. |
| `ExerciseIdentityMigrationIssue.sourceExerciseId` and its DAO/repository references | Stores and reports the old source ID only when a migration cannot be resolved safely. | Diagnostic row identity context, not a runtime exercise reference. |
| Historical documents and prior generated audit outputs | Describe the pre-v0.5.0.6 schema. | Historical evidence; not executable application behavior. |

## Similar names that remain valid

- `countProgramItemsForExercise(exerciseStableKey)` counts references by canonical
  stable key despite the method's broad English name.
- Workout entry IDs, set IDs, program IDs, and program-item IDs remain numeric
  local row identities by design.
