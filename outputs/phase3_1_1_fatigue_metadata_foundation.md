# Phase 3.1.1 Fatigue Metadata Foundation

Date: 2026-06-15

## Goal

This patch prepares Today Fatigue / Readiness analysis without implementing the final fatigue judgment engine.

No new analysis UI was added.

## Current Codebase Audit

Exercise catalog storage:

- Built-in seed data lives in `app/src/main/assets/training_settings_seed.csv`.
- The current seed file contains 214 exercise rows, 12 program rows, and 753 program item rows.
- Runtime exercise rows are stored in the Room `exercises` table.

Current metadata:

- Existing `Exercise` fields already included legacy metadata such as `movementPattern`, `movementCategory`, `primaryMuscles`, `secondaryMuscles`, `equipmentTags`, `forceType`, `bodyRegion`, `laterality`, `trainingRole`, `stabilityRoles`, `sportTransferDirect`, `sportTransferSupportive`, `accessoryRoles`, `loadProfile`, and `metadataConfidence`.
- Seed CSV also already included structured columns: `movement_pattern`, `movement_category`, `force_type`, `plane`, and `is_unilateral`.

Existing taxonomy:

- `ExerciseTaxonomy.kt` existed before this patch.
- It was narrower than the fatigue/readiness taxonomy needed for 3.1.1.

String parsing audit:

- Search UI still uses `contains` for text search in `ExerciseScreen.kt` and `CommonUi.kt`. This is not exercise classification.
- `RecordScreen.kt` still checks `entry.category == "스포츠"` for sport-specific input presentation.
- `TrainingRepository.kt` still checks `exercise.mode.contains("시간")` for default time-set behavior.
- `SeedData.kt` still contains legacy fallback string heuristics for old seed metadata generation.
- New fatigue/readiness metadata mapping uses structured seed columns or existing metadata fields, not exercise-name parsing.

## Added Taxonomy

New enum taxonomy lives in `ExerciseMetadataTaxonomy.kt`:

- `MovementPattern`
- `MovementCategory`
- `CompoundType`
- `FatigueForceType`
- `Plane`
- `FatigueLaterality`
- `AxialLoadLevel`
- `FatigueTrainingRole`
- `BadmintonTransferRole`
- `FatigueCategory`
- `AdaptiveBaselineGroup`
- `RecoveryDecayProfile`
- `MetadataConfidence`

## Data Model Changes

Room `Exercise` was extended additively with:

- `equipment`
- `compoundType`
- `plane`
- `axialLoadLevel`
- `badmintonTransferRoles`
- `fatigueCategories`
- `adaptiveBaselineGroups`
- `recoveryDecayProfile`
- `systemicLoadWeight`
- `neuralHeavyWeight`
- `neuralSpeedWeight`
- `localLoadWeight`
- `decelerationWeight`
- `elasticSscWeight`
- `rotationPowerWeight`
- `antiRotationWeight`
- `overheadSwingWeight`
- `gripLoadWeight`

DB version is now 4. Migration `3 -> 4` only adds columns and does not delete or rewrite user records.

## Mapping Strategy

`ExerciseMetadataMapper` maps the full seed catalog from structured seed columns first:

- `movement_pattern`
- `movement_category`
- `force_type`
- `plane`
- `is_unilateral`

Existing installed DB rows are refreshed only when the new metadata fields are blank or old confidence tokens are present.

Mapping rules are metadata-based:

- Heavy lower compound work gets systemic/heavy neural load, axial load, and lower baseline groups.
- Upper compound work gets upper push/pull groups.
- Isolation work gets high local load and low systemic load.
- Prehab/mobility work gets minimal or short recovery profiles.
- Reactive/footwork work gets neural speed and badminton court groups.
- Plyometric, hop, jump, and bound patterns get elastic SSC load.
- Deceleration/COD work gets deceleration load and baseline group.
- Rotation and anti-rotation patterns get their own fatigue categories and baseline groups.
- Grip/forearm and overhead/shoulder work get dedicated weights.

## Sanity Checker

`MetadataSanityChecker` validates:

- all weights are in `0.0..1.0`
- REACTIVE implies neural speed load
- DECELERATION implies deceleration load
- ELASTIC_SSC implies elastic SSC load
- ROTATION_POWER implies rotation power load
- ANTI_ROTATION implies anti-rotation load
- court transfer roles imply `BADMINTON_COURT`
- isolation/prehab/systemic load mismatches
- high axial load with zero systemic load
- weakly supported `VERY_LONG` recovery profile
- LOW / NEEDS_REVIEW confidence reporting

## Current Seed Report

- Total exercise count: 214
- `HIGH`: 214
- `MEDIUM`: 0
- `LOW`: 0
- `NEEDS_REVIEW`: 0
- Sanity errors: 0
- NEEDS_REVIEW exercise list: empty

## Tests

Added `MetadataSanityCheckerTest`.

Validated:

- all seed weights are in range
- REACTIVE rows have neural speed load
- DECELERATION rows have deceleration load
- ELASTIC_SSC rows have elastic SSC load
- ROTATION_POWER rows have rotation power load
- ANTI_ROTATION rows have anti-rotation load
- badminton court transfer rows have `BADMINTON_COURT`
- PREHAB rows do not have excessive systemic load
- ISOLATION rows do not have excessive systemic load
- checker reports a crafted NEEDS_REVIEW fixture

## Not Implemented

- No Today Fatigue / Readiness judgment engine.
- No fatigue score.
- No final recovery recommendation.
- No new analysis tab cards.
- No paper-derived thresholds.

## Next Patch

3.1.2 or 3.2.0 should implement the first fatigue/readiness feature extraction layer using the new structured metadata. The next patch should still avoid final advice until thresholds and wording are reviewed.
