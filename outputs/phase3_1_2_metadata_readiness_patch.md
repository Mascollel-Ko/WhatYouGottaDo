# Phase 3.1.2 Metadata Readiness Patch

Date: 2026-06-15

## Goal

Patch 3.1.2 checks and completes the metadata path needed by later analysis engines. It does not implement fatigue, strength progress, badminton transfer, or safety judgment engines.

## Added Taxonomy

Added enums:

- `ProgressMetricType`
- `StrengthProgressionGroup`
- `HypertrophyVolumeGroup`
- `MainLiftGroup`
- `AccessoryContributionGroup`
- `BadmintonTransferStrength`
- `CourtMovementType`
- `BadmintonSkillTarget`
- `JointStressTag`
- `StabilityDemandLevel`
- `MobilityDemandLevel`
- `BalanceContributionTag`
- `AnalysisEligibility`

Existing 3.1.1 fatigue and badminton transfer enums were retained.

## Added Metadata Fields

Added to `Exercise`:

- progress fields
- strength/hypertrophy/main lift/accessory grouping fields
- estimated 1RM and volume-load eligibility flags
- badminton transfer strength, court movement types, skill targets
- joint stress, stability, mobility, and balance contribution fields
- multi-value `analysisEligibility`

## Added Mapping / Feature Layer

Added:

- `ExerciseAnalysisMapper`
- `AnalysisExerciseFeatures`
- `MetadataReadinessReporter`
- `ExerciseReadinessRow`
- `MetadataAnalysisReadinessReport`

These objects convert exercise metadata and optional workout records into analysis-ready feature vectors.

## Data Safety

Room migration `4 -> 5` is additive only. Existing user records, plans, calendar data, sets, and seed assets are preserved.

## Test Result

Unit tests verify:

- required metadata fields exist
- weight range consistency
- fatigue category and weight consistency
- badminton transfer and court movement consistency
- progress metric eligibility consistency
- analysis eligibility is non-empty
- prehab/test rows are not mixed into normal progress incorrectly
- `ExerciseAnalysisMapper` maps metadata to features
- `ExerciseAnalysisMapper` does not depend on exercise-name parsing
- planned and completed records are distinguishable
- `MetadataSanityChecker` reports NEEDS_REVIEW fixtures

Result:

- `:app:testDebugUnitTest`: successful

## Build Result

- `assembleDebug`: successful