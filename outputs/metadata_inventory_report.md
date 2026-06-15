# Metadata Inventory Report v0.3.4.3

## Existing Storage
- Room entity: `Exercise`
- Seed CSV: `training_settings_seed.csv`
- Recovered seed JSON: `exercises_seed.json`
- Image mapping CSV: `exercise_image_mapping.csv`
- Backup/restore CSV: `row_type=exercise` rows now preserve master data keys.

## Exercise Fields Present
Core fields include name, category, detail1, detail2, mode, description, rest, family, stableKey, movementPattern, movementCategory, muscles, equipment, forceType, bodyRegion, plane, laterality, axialLoadLevel, trainingRole, badminton transfer fields, fatigue weights, progress fields, balance/safety fields, activityKind, planningEligibility, metadataConfidence.

## v0.3.4.3 Fields Added
- `imageAssetName`
- `isActive`
- `archivedAt`
- `isCustom`
- `needsReview`

## Assets
- Restored local image assets: 190
- Exercise seed rows from JSON: 213
- Auto image mappings: 179

## Ponytail Note
Project-local Ponytail rules were not found. External Ponytail AGENTS.md was found and applied. Scope was limited to recovery, mapping, hide/delete, backup/restore, and reports.
