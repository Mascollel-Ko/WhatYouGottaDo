# Metadata Validator Requirements v0.3.5.0

## Scope

This document defines validator requirements for the future full seed reclassification. It does not implement validator code.

Current validator:

- `app/src/main/java/com/training/trackplanner/data/MetadataSanityChecker.kt`

Current validator already checks:

- fatigue weight ranges
- required broad metadata fields
- fatigue category and weight consistency
- badminton role and baseline consistency
- badminton transfer strength and eligibility consistency
- progress metric eligibility consistency
- prehab/test misuse
- unilateral/rotation/balance consistency
- high axial load with zero systemic load
- excessive systemic load on isolation/prehab rows
- empty `analysisEligibility`

## Required Future Rules

| Rule ID | Requirement | Severity | Current fields | Future fields |
| --- | --- | --- | --- | --- |
| VAL-001 | `PROGRAM_SELECTABLE` exercise must have `movementPattern`. | ERROR | `planningEligibility`, `movementPattern` | same |
| VAL-002 | `TRAINING_EXERCISE` must have `primaryMuscles` unless explicitly cardio/recovery-only. | ERROR/NEEDS_REVIEW | `activityKind`, `primaryMuscles`, `trainingRole` | same |
| VAL-003 | Strength exercise must have `detailedMuscleTag` and `muscleContribution`; broad muscle tags alone are insufficient after detailed fields exist. | NEEDS_REVIEW until field exists, then ERROR | `movementCategory`, `trainingRole`, `primaryMuscles`, `secondaryMuscles` | `detailedMuscleTag`, `muscleContribution` |
| VAL-004 | `muscleContribution` sum should be approximately 0.90-1.10. | ERROR if far outside; NEEDS_REVIEW if 0.85-0.90 or 1.10-1.15 | none | `muscleContribution` |
| VAL-005 | Main lift must have `progressMetricType`. | ERROR | `trainingRole`, `progressMetricType` | `programSlot`, `progressMetricType` |
| VAL-006 | e1RM target exercise must have `mainLiftGroup`. | ERROR | `estimated1RmEligible`, `mainLiftGroup` | same |
| VAL-007 | Hypertrophy target exercise must have `hypertrophyVolumeGroup`. | ERROR | `volumeLoadEligible`, `hypertrophyVolumeGroup`, `progressMetricType` | same |
| VAL-008 | Sport session must not be `PROGRAM_SELECTABLE`. | ERROR | `activityKind`, `planningEligibility` | same |
| VAL-009 | Direct badminton transfer must have `courtMovementType` unless it is direct play session. | ERROR/NEEDS_REVIEW | `badmintonTransferStrength`, `courtMovementTypes`, `activityKind` | `badmintonTransferDirect` |
| VAL-010 | Landing/deceleration drill must have deceleration or elastic SSC stress. | ERROR | `movementPattern`, `courtMovementTypes`, `decelerationWeight`, `elasticSscWeight` | `fatigueProfile` |
| VAL-011 | Heavy squat/hinge must have axial load. | ERROR | `movementPattern`, `axialLoadLevel`, `loadProfile` | `movementFamily` |
| VAL-012 | Shoulder prehab must have rotator cuff, serratus, lower trap, or scapular tag. | ERROR | `trainingRole`, `primaryMuscles`, `secondaryMuscles`, `balanceContributionTags` | `detailedMuscleTag` |
| VAL-013 | Cable exercise must include cable equipment. | NEEDS_REVIEW | `equipment`, `equipmentTags`, seed mode | same |
| VAL-014 | Machine exercise must include machine equipment. | NEEDS_REVIEW | `equipment`, `equipmentTags`, seed mode | same |
| VAL-015 | Bodyweight exercise must include bodyweight equipment. | NEEDS_REVIEW | `equipment`, `equipmentTags`, seed mode | same |
| VAL-016 | Same generated session must not over-repeat identical `redundancyGroup`. | NEEDS_REVIEW | program items + current metadata | `redundancyGroup` |
| VAL-017 | High axial + high plyometric + high deceleration stress should not cluster in one generated session. | NEEDS_REVIEW | `axialLoadLevel`, `elasticSscWeight`, `decelerationWeight`, program items | `fatigueProfile`, `programSlot` |

## Additional Recommended Rules

| Rule ID | Requirement | Severity |
| --- | --- | --- |
| VAL-018 | `evidenceSourceGroup` must not be blank for manually reviewed rows. | NEEDS_REVIEW |
| VAL-019 | `evidenceLevel=heuristic_only` requires `metadataConfidence` below HIGH unless manually approved. | NEEDS_REVIEW |
| VAL-020 | `BADMINTON_DIRECT_PLAY` must map to `activityKind=SPORT_SESSION` or an explicit direct-play training type. | ERROR |
| VAL-021 | `BADMINTON_DIRECT_PLAY` must not be counted as footwork/reactive drill volume. | ERROR |
| VAL-022 | `movementFamily=HEAVY_HINGE` with `recoveryDecayProfile=SHORT` is suspicious. | NEEDS_REVIEW |
| VAL-023 | `movementFamily=ROTATOR_CUFF_PREHAB` with high systemic load is suspicious. | NEEDS_REVIEW |
| VAL-024 | `programSlot=SHOULDER_CARE` must not use `progressMetricType=ESTIMATED_1RM`. | ERROR |
| VAL-025 | Direct court movement with `planningEligibility=PROGRAM_SELECTABLE` must have fatigue caps in program generator. | NEEDS_REVIEW |
| VAL-026 | `muscleContribution` must not contain unknown muscle keys outside the controlled `detailedMuscleTag` vocabulary. | ERROR |
| VAL-027 | Proposed enum-like values in `movementFamily`, `movementSubtype`, `programSlot`, `redundancyGroup`, badminton support/direct tags, and template keys should be `UPPER_SNAKE_CASE`. | NEEDS_REVIEW |
| VAL-028 | UI display text is excluded from enum naming validation. | INFO |
| VAL-029 | `evidenceSourceGroup` source IDs must exist in `metadata_evidence_sources_v0.3.5.0.md`. | ERROR |
| VAL-030 | Row variants must not rely on deprecated `C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE`; they should reference `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` or another verified/candidate row source. | NEEDS_REVIEW |
| VAL-031 | `BADMINTON_DIRECT_PLAY` must not be `PROGRAM_SELECTABLE`. | ERROR |
| VAL-032 | Badminton lesson/session/match records must not be inserted automatically by the program generator as exercises. | ERROR |

## Detailed Muscle Tag Validator

Controlled detailed muscle tags are defined in `metadata_taxonomy_delta_v0.3.5.0.md`.

Rules:

- If a strength or hypertrophy row has only broad muscles and no `detailedMuscleTag`, report NEEDS_REVIEW.
- If `muscleContribution` contains an unknown muscle key, report ERROR.
- Keep the existing contribution sum rule: acceptable target range is 0.90-1.10, with warnings near the boundary and ERROR for clearly invalid sums.
- `NONE` is allowed for direct sport sessions, cardio without activity-specific evidence, mobility, recovery, and warmup rows where detailed muscle contribution would create false precision.

## Enum Naming Validator

Rules:

- Proposed enum-like values must use `UPPER_SNAKE_CASE`.
- Lower-snake planning keys should report NEEDS_REVIEW.
- UI display names and Korean labels are excluded from this validator.
- `evidenceLevel` values are intentionally lower-snake controlled values from the evidence source document and are excluded from this `UPPER_SNAKE_CASE` rule.

## Evidence Source Validator

Rules:

- Manually reviewed rows must have non-blank `evidenceSourceGroup`.
- Every `sourceId` in `evidenceSourceGroup` must exist in `metadata_evidence_sources_v0.3.5.0.md`.
- `evidenceLevel=heuristic_only` with `metadataConfidence=HIGH` should report NEEDS_REVIEW.
- `needs_network_recheck` sources may be used for planning notes but should not be the sole reason for automatic high-confidence seed changes.
- Candidate sources should remain candidate-only until verification status changes.

## Row Source Validator

Rules:

- Row variants must not reference only the deprecated `C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE`.
- Row variants should reference at least `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` or another verified/candidate row source.
- Snarr/Esco 2013 directly supports inverted row and suspension inverted row. Barbell row, chest-supported row, cable row, machine row, and one-arm row still require manual subtype review.

## Sport Session Validator

Rules:

- `BADMINTON_DIRECT_PLAY` must not be `PROGRAM_SELECTABLE`.
- `BADMINTON_DIRECT_PLAY` should be separated from footwork drill volume.
- Badminton lesson/session/match records must not be automatically inserted by the program generator as exercise items.

## Validation Inputs

Validator should read:

- `Exercise` rows from DB or seed projection
- proposed detailed fields
- program generator output sessions
- evidence source mapping
- current `planningEligibility`
- current `activityKind`

Validator should not:

- infer exercise family from display name except for explicitly marked legacy fallback rows
- modify seed rows automatically
- delete user records
- fail import of older backups solely because detailed evidence fields are missing

## Report Output Requirements

For each failed row:

```text
exerciseId:
stableKey:
exerciseName:
field:
currentValue:
expectedValue:
severity:
reason:
sourceIds:
recommendedAction:
```

For session-level redundancy:

```text
programId:
dayIndex:
redundancyGroup:
count:
highStressFlags:
exercises:
severity:
recommendedAction:
```

## Suggested Implementation Order

1. Implement report-only validator with current fields plus optional future-field parser.
2. Add fixture tests for each required rule.
3. Run validator against current 215 exercise seed rows.
4. Create review CSV for failing rows.
5. Reclassify seed in small family batches.
6. Run program generator redundancy checks after each batch.

## v2 Deprecated Arm Family and Blocker Rules

The following values are legacy-input notes only and are forbidden in v2 proposed output:

- `BICEPS_TRICEPS_ISOLATION_VARIANTS` in `proposedMovementFamily`
- `ARM_ACCESSORY` in `proposedProgramSlot`
- `ARM_ISOLATION` in `proposedRedundancyGroup`

Validation failures:

- A biceps curl family contains `TRICEPS_BRACHII` as a detailed or major contribution tag.
- A triceps isolation family contains `BICEPS_BRACHII` as a detailed or major contribution tag.
- A forearm/grip row inherits the deprecated integrated biceps/triceps template.
- Spider curl, chest-supported row variants, scapular push-up, or bench dip remains in a bench-press family.
- Reverse Nordic curl remains in `HAMSTRING_CURL_VARIANTS`.
- Leg extension or single-leg leg extension remains in the leg-press compound template.
- Split-step lateral lunge remains in the generic strength-lunge template.
- Bodyweight, goblet, or unilateral squat receives `MAIN_LOWER_STRENGTH` or `SQUAT_PATTERN_HEAVY_LOWER` by default.
- Unilateral RDL receives `MAIN_HINGE_STRENGTH` or `HEAVY_HINGE` by default.
- Kettlebell swing receives the deadlift main-strength template.
- Line hop, pogo jump, or jump rope receives the calf-raise strength template.

V2 report validation must assert 215 rows, zero duplicate `stableKey` values, zero unmatched inputs, zero deprecated arm family/slot/group values, and zero blocker-rule failures. These checks are report-only and must not mutate seed or runtime data.

## Risks During Full Reclassification

- Existing user DB rows may have older metadata that does not match seed version 6.
- `exercises_seed.json` currently should not be used as strict machine input until repaired.
- Current broad tokens sometimes encode multiple concepts in one field.
- Korean display muscle names and enum keys are mixed in seed CSV.
- Direct sport sessions can accidentally be treated as training drills if `activityKind` is missing.
- Contribution weights can create false precision; they must stay app-internal and documented as estimates.
