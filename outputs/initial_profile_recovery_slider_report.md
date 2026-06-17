# Initial Profile Recovery Slider Report

## Slider Direction

All profile recovery sliders use the same direction:

| Field | Left side | Right side | Stored meaning |
| --- | --- | --- | --- |
| sleepQuality | bad | good | higher is better |
| currentFatigue | very tired | very light | higher is better |
| currentSoreness | severe | none | higher is better |
| currentStress | high | low | higher is better |
| currentCondition | bad | good | higher is better |

## Calculation Direction

`InitialAdaptationProfileCalculator.recoveryCapacity()` now treats `currentFatigue`, `currentSoreness`, and `currentStress` as `highIsGood = true`. This matches the slider direction.

## Migration

Room DB version was bumped from `11` to `12`. `MIGRATION_11_12` converts existing v0.3.4.4.4 records:

- `currentFatigue = 6 - currentFatigue`
- `currentSoreness = 6 - currentSoreness`
- `currentStress = 6 - currentStress`

`sleepQuality`, `currentMood`, and `currentCondition` remain unchanged because they already used higher-is-better semantics.

## Backup/Restore

New exports include `profileRecoveryScaleDirection=HIGH_IS_GOOD`. Imports without this key are treated as older backups and convert fatigue/soreness/stress into the new direction.

## Tests

- `InitialProfileColdStartReadinessTest.recoverySliderScoresTreatRightSideAsGood`
- `TrainingDatabaseMigrationTest.migrate11To12InvertsBadRecoveryScalesOnly` was added and android-test compilation passed.