# Initial Profile Schema Migration Report

## Current Schema Path

- v0.3.4.4.1 introduced DB version `10` and the structured profile columns.
- v0.3.4.4.2 raises DB version to `11`.
- `MIGRATION_10_11` is intentionally no-op because no new columns are required.
- Destructive migration was not used.

## Migration List

| Migration | Purpose |
| --- | --- |
| `MIGRATION_8_9` | Create `initial_user_profiles` with legacy/free-text compatible fields. |
| `MIGRATION_9_10` | Add structured profile columns and migrate obvious legacy values. |
| `MIGRATION_10_11` | Version bump for cold-start readiness binding without schema changes. |

## Structured Columns Preserved

- `birthYear`
- `sex`
- `strengthTrainingYears`
- `badmintonTrainingYears`
- `strengthSessionsPerWeek`
- `strengthMinutesPerSession`
- `strengthAverageRpe`
- `badmintonSessionsPerWeek`
- `badmintonMinutesPerSession`
- `badmintonAverageRpe`
- `trainingBreakCategory`
- `trainingBreakReason`
- `squatKg`
- `deadliftKg`
- `benchPressKg`
- `pullUpMaxReps`
- `pullUpAddedWeightKg`
- `usualSleepHours`
- `sleepQuality`
- `currentFatigue`
- `currentSoreness`
- `currentStress`
- `currentCondition`
- `painAreaTags`
- `avoidMovementTags`
- `primaryGoal`
- `freeNote`

## Compatibility Policy

- Existing workout records, sets, exercises, hidden states, programs, and the profile row are preserved.
- Legacy free-text fields remain in the table for compatibility.
- New readiness logic reads structured fields only.
- v11 schema export is generated during the final build/test pass.
