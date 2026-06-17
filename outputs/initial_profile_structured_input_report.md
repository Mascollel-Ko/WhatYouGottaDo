# Initial Profile Structured Input Report

## v0.3.4.4.3 Scope

The initial profile UI keeps free text out of core analysis inputs. `freeNote` remains available only as a note and is not used by readiness calculation.

## Structured Inputs

| Field | UI/input type | Stored value | Analysis use |
| --- | --- | --- | --- |
| `birthYear` | numeric year | `Int?` | stored only for future analysis |
| `sex` | single choice | `MALE/FEMALE/UNSPECIFIED` | stored conservatively, not a strong fatigue factor |
| `strengthTrainingYears` | decimal year input | `Double?` | resistance adaptation |
| `badmintonTrainingYears` | decimal year input | `Double?` | court-load adaptation |
| `strengthSessionsPerWeek` | numeric input | `Double?` | recent resistance capacity |
| `strengthMinutesPerSession` | numeric minutes | `Int?` | recent resistance capacity |
| `strengthAverageRpe` | 1-10 chip | numeric 1-10 | recent resistance load context |
| `badmintonSessionsPerWeek` | numeric input | `Double?` | recent court-load capacity |
| `badmintonMinutesPerSession` | numeric minutes | `Int?` | recent court-load capacity |
| `badmintonAverageRpe` | 1-10 chip | numeric 1-10 | recent court-load context |
| `trainingBreakCategory` | single choice | enum key | detraining modifier |
| `trainingBreakReason` | single choice | enum key | conservative detraining modifier |
| `squatKg/deadliftKg/benchPressKg` | numeric kg | `Double?` | resistance marker score |
| `pullUpMaxReps/pullUpAddedWeightKg` | numeric | `Int?` / `Double?` | resistance marker score |
| `usualSleepHours` | decimal hours | `Double?` | recovery capacity |
| `sleepQuality/currentFatigue/currentSoreness/currentStress/currentCondition` | 1-5 chip | `Int?` | recovery capacity |
| `painAreaTags` | multi-select | comma-separated enum keys | restriction profile |
| `avoidMovementTags` | multi-select | comma-separated enum keys | restriction profile |
| `primaryGoal` | single choice | enum key | goal sensitivity |

## Notes

- `NONE` is mutually exclusive with other multi-select tags.
- Backup/import sanitizes profile enum keys and RPE/rating ranges.
- Existing legacy free-text fields remain for migration/import compatibility only.
