# Metadata Field Usage Report v0.3.4.3

## Used In Analysis
- `movementPattern`, `movementCategory`, `primaryMuscles`, `secondaryMuscles`, `equipment`, `compoundType`, `forceType`, `plane`, `laterality`, `axialLoadLevel`, `trainingRole`
- fatigue weights and categories are used by readiness/trend analysis.
- badminton transfer fields are used by transfer score and recommendation builders.

## Used In Program Generation
- `planningEligibility`, `activityKind`, `metadataConfidence`, `equipment`, movement/role/fatigue fields.
- v0.3.4.3 adds `isActive` exclusion for program candidates.

## Used In UI
- Exercise list/search uses name/category/detail fields.
- Exercise detail now uses `imageAssetName` for asset loading with a placeholder fallback.
- Exercise management uses `isActive` and delete reference checks.

## Used In Backup/Restore
- `row_type=exercise` exports/imports name, stableKey, description, imageAssetName, muscles, equipment, core taxonomy, transfer fields, loadProfile, metadataConfidence, isActive, isCustom, needsReview.

## Not Fully Used Yet
- Some v0.3.5/v0.3.6 planning fields remain documented gaps rather than new schema.
