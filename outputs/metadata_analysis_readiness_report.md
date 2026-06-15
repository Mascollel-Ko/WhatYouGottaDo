# Metadata Analysis Readiness Report

Patch: 3.1.2  
Date: 2026-06-15

## Scope

This report checks whether the exercise metadata foundation can support upcoming analysis patches:

- 3.2 Today Readiness / Fatigue Analysis
- 3.3 Strength & Progress Analysis
- 3.4 Badminton Transfer / Court Load Analysis
- Future Balance / Weakness / Safety Analysis

This patch does not implement the final analysis engines.

## Current Schema Check

Existing and retained fields:

- `movementPattern`
- `movementCategory`
- `primaryMuscles`
- `secondaryMuscles`
- `equipment`
- `compoundType`
- `forceType`
- `plane`
- `laterality`
- `axialLoadLevel`
- `trainingRole`
- `badmintonTransferRoles`
- `fatigueCategories`
- `adaptiveBaselineGroups`
- `recoveryDecayProfile`
- `metadataConfidence`
- all fatigue weight fields from 3.1.1

Fields added in 3.1.2:

- `progressMetricType`
- `strengthProgressionGroup`
- `hypertrophyVolumeGroup`
- `mainLiftGroup`
- `accessoryContributionGroup`
- `estimated1RmEligible`
- `volumeLoadEligible`
- `badmintonTransferStrength`
- `courtMovementTypes`
- `badmintonSkillTargets`
- `jointStressTags`
- `stabilityDemandLevel`
- `mobilityDemandLevel`
- `balanceContributionTags`
- `analysisEligibility`

Room migration:

- DB version: 5
- Migration `4 -> 5`: additive Exercise columns only
- No user records, calendar entries, sets, plans, or assets are deleted.

## Mapping Layer

Mapping layer exists:

- `ExerciseMetadataMapper`
- `ExerciseAnalysisMapper`
- `MetadataReadinessReporter`
- `MetadataSanityChecker`

`ExerciseAnalysisMapper` outputs `AnalysisExerciseFeatures`, which contains:

- identity and metadata confidence
- fatigue features
- strength/progress features
- badminton/court features
- balance/safety features
- record-derived values such as completed sets, volume load, RPE, estimated 1RM, duration, planned/completed flags, and record date

The mapper uses structured metadata only. It does not classify by exercise name.

## Readiness Summary

Built-in seed catalog:

- total exercises: 214
- metadata confidence `HIGH`: 214
- metadata confidence `MEDIUM`: 0
- metadata confidence `LOW`: 0
- metadata confidence `NEEDS_REVIEW`: 0

Readiness:

| Area | YES | PARTIAL | NO |
| --- | ---: | ---: | ---: |
| fatigueReady | 214 | 0 | 0 |
| progressReady | 214 | 0 | 0 |
| badmintonReady | 214 | 0 | 0 |
| balanceReady | 214 | 0 | 0 |

Missing fields:

- none found in the built-in seed catalog

NEEDS_REVIEW exercises:

- none

Suspicious mappings:

- none after the 3.1.2 mapper update

## Per-Exercise Rows

`MetadataReadinessReporter.generate(exercises).rows` produces one row per exercise with:

- `exerciseId`
- `exerciseName`
- `metadataConfidence`
- `fatigueReady`
- `progressReady`
- `badmintonReady`
- `balanceReady`
- `missingFields`
- `suspiciousMappings`
- `needsReviewReason`

Current generated result for all 214 built-in exercises:

- `fatigueReady = YES`
- `progressReady = YES`
- `badmintonReady = YES`
- `balanceReady = YES`
- `missingFields = empty`
- `suspiciousMappings = empty`
- `needsReviewReason = empty`

## String Parsing Residues

Remaining `contains` usage:

- `ExerciseScreen.kt`, `CommonUi.kt`: search UI only
- `AnalysisSentencePolicy.kt`: prohibited sentence expression check
- `AnalysisWindow.kt`, common metric files: date/window/token checks, not exercise-name classification
- `RecordScreen.kt`: sport category UI branch
- `TrainingRepository.kt`: time-mode default set behavior
- `SeedData.kt`: legacy seed fallback heuristics

Analysis-facing mapping added in this patch does not depend on exercise-name parsing.

## Sanity Checker Coverage

`MetadataSanityChecker` now validates:

- all weight values in `0.0..1.0`
- fatigue category and weight consistency
- badminton transfer strength, roles, court movement, skill target, and baseline consistency
- progress metric eligibility consistency
- prehab/recovery/test eligibility consistency
- unilateral, rotation, anti-rotation, joint stress, stability, and mobility mapping
- excessive systemic load for isolation/prehab
- blank `analysisEligibility`

Current result:

- sanity errors: 0
- NEEDS_REVIEW rows: 0

## Next Patch

3.2 can use `ExerciseAnalysisMapper` output to build Today Readiness / Fatigue feature extraction.

Recommended next steps:

- compute daily fatigue vectors from confirmed sets only
- keep planned sets separate from completed load
- avoid thresholds until reviewed
- produce internal debug summaries before user-facing fatigue text
