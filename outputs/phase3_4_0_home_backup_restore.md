# v0.3.4.0 Home Cleanup and Backup Restore Entry Points

## Goal

This patch cleans the Home screen and re-exposes record backup / restore from the first screen.

It also adds CSV restore compatibility for the legacy `daily_timeseries` CSV shape.

## Home Screen

Changed:

- Removed the top `?ㅻ뒛 ???? card.
- Kept the existing `?ㅻ뒛 ?붿빟` section.
- Added `湲곕줉 愿由? below `?ㅻ뒛 ?붿빟`.

Record management buttons:

- `湲곕줉 諛깆뾽`
- `湲곕줉 蹂듭썝`

The buttons use Android document picker contracts:

- backup: `CreateDocument("text/csv")`
- restore: `OpenDocument()`

## Backup Format

`湲곕줉 諛깆뾽` exports restore-format CSV.

Header:

```csv
schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,rpe,max_reps,notes,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,body_weight_kg
```

Exported data:

- daily metrics
- workout entries
- workout sets
- set-level `confirmed`
- set RPE fallback through `rpe`

## Restore Format Import

Restore-format CSV supports:

- `row_type=daily`
- `row_type=set`

Behavior:

- daily rows upsert `daily_metrics`
- set rows are grouped into workout entries
- `set_confirmed=1` restores actual completed sets
- `set_confirmed=0` restores planned sets
- if `set_confirmed` is missing, entry-level `confirmed` is used as fallback
- missing exercises are inserted as minimal imported exercises
- duplicate entries are skipped when date, entry fields, and sets match

## Daily Timeseries Import Compatibility

Legacy `daily_timeseries` CSV is now accepted.

Header shape:

```csv
date,sleep_hours,body_weight_kg,total_entries,confirmed_entries,planned_entries,total_sets,total_reps,total_tonnage_kg,total_seconds,strength_entries,functional_entries,cardio_entries,sports_entries,max_6corner_per_min,max_smash_per_min,exercises_summary
```

Behavior:

- `sleep_hours` and `body_weight_kg` are upserted into `daily_metrics`.
- invalid numeric values are treated as empty values.
- app import does not crash on blank or malformed numeric fields.
- because daily timeseries has no per-exercise or per-set detail, the importer creates aggregate confirmed entries / sets per category count.
- aggregate entries use the note marker `CSV daily_timeseries import`.
- re-import skips aggregate creation for dates that already have that marker.

Limit:

- daily timeseries cannot fully reconstruct exact original exercise/set details.
- it is now compatible enough to restore date-level records and analysis inputs.

## Data Flow After Restore

Restored data writes into Room:

- `daily_metrics`
- `workout_entries`
- `workout_sets`
- minimal imported `exercises` when needed

This means the data is visible to:

- Home `?ㅻ뒛 ?붿빟`
- Record tab date lists
- Analysis tab simple stats
- 3.1 Today Readiness
- 3.2 Performance Trend
- 3.3 Badminton Transfer where imported metadata allows it

## Version

- `versionName = "0.3.4.0"`
- `versionCode = 30400`

## Verification

- `compileDebugKotlin`: passed
- `testDebugUnitTest`: passed