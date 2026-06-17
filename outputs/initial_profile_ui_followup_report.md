# Initial Profile UI Follow-up Report

## Changes

- `strengthAverageRpe` remains a structured numeric field and now accepts one decimal place.
- The save path validates strength RPE as `1.0..10.0`.
- `badmintonAverageRpe` remains unused by the initial adaptation calculation and is not reintroduced to the UI.
- Goal chips now render two per row to prevent long labels from becoming cramped.
- Single-choice and multi-choice chip renderers defensively skip blank labels.

## Files

- `app/src/main/java/com/training/trackplanner/InitialProfileDialog.kt`

## Behavior

| Input | UI behavior | Stored value |
| --- | --- | --- |
| Strength RPE blank | Allowed | `null` |
| Strength RPE `7` | Allowed | `7.0` |
| Strength RPE `7.5` | Allowed | `7.5` |
| Strength RPE `10.0` | Allowed | `10.0` |
| Strength RPE `10.5` | Blocked by save validation | Not saved |
| Strength RPE `abc` | Cannot be entered through the numeric filter | Not saved |

## Compatibility

No DB migration was required. The existing `strengthAverageRpe: Double?` field already supports decimal values.