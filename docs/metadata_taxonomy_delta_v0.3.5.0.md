# Metadata Taxonomy Delta v0.3.5.0

## Scope

This document proposes additive taxonomy and schema directions for a future metadata reclassification patch. It does not modify Kotlin code or seed files in this step.

## Current Repository Baseline

Current enum file:

- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt`

Current legacy token file:

- `app/src/main/java/com/training/trackplanner/data/ExerciseTaxonomy.kt`

Current `Exercise` metadata fields include:

- `movementPattern`
- `movementCategory`
- `primaryMuscles`
- `secondaryMuscles`
- `equipment`
- `equipmentTags`
- `compoundType`
- `forceType`
- `bodyRegion`
- `plane`
- `laterality`
- `axialLoadLevel`
- `trainingRole`
- `stabilityRoles`
- `sportTransferDirect`
- `sportTransferSupportive`
- `badmintonTransferRoles`
- `fatigueCategories`
- `adaptiveBaselineGroups`
- `accessoryRoles`
- `loadProfile`
- `recoveryDecayProfile`
- fatigue weight fields
- progress fields
- badminton transfer fields
- balance/safety fields
- `analysisEligibility`
- `activityKind`
- `planningEligibility`
- `metadataConfidence`

Current limitation:

- Broad enum fields exist, but no explicit `movementFamily`, `movementSubtype`, `muscleContribution`, `programSlot`, `redundancyGroup`, or evidence source fields.
- Current seed tokens mix broad app semantics, human-readable Korean muscle labels, and older detailed token strings.
- `exercises_seed.json` exists as a reference asset but needs encoding/JSON repair before it can be trusted as machine input.

`exercises_seed.json` handling policy:

- Do not use as strict machine input until encoding/malformed string issues are repaired.
- Treat `training_settings_seed.csv` as the runtime seed source for v0.3.5.0 planning.
- Create a separate future repair task before using `exercises_seed.json` for automated metadata migration.
- Do not delete or overwrite `exercises_seed.json` in this patch.

Naming policy:

- Proposed enum-like values use `UPPER_SNAKE_CASE`.
- `evidenceLevel` values intentionally remain the lower-snake controlled values used by `metadata_evidence_sources_v0.3.5.0.md`.
- UI display strings and Korean labels are out of scope.
- `muscleContribution` keys must use `detailedMuscleTag` values, not broad labels such as `GLUTE`, `CHEST`, or `CORE`.

## Proposed Additive Fields

| Candidate field | Purpose | Suggested storage | Notes |
| --- | --- | --- | --- |
| `movementFamily` | Stable family grouping for reclassification templates | enum key string | Examples: `SQUAT`, `HEAVY_HINGE`, `HORIZONTAL_PUSH`, `BADMINTON_FOOTWORK`. |
| `movementSubtype` | Specific family subtype | enum key string or CSV token | Examples: `BACK_SQUAT`, `RDL`, `LATERAL_BOUND_TO_STICK`, `BAND_EXTERNAL_ROTATION`. |
| `detailedMuscleTag` | Canonical detailed muscle tokens | comma list or JSON array | Should normalize Korean labels and current broad tokens. |
| `muscleContribution` | App-internal approximate contribution template | JSON object preferred | Example: `{"QUADRICEPS":0.30,"RECTUS_FEMORIS":0.10,"GLUTE_MAX":0.25}`. Must be heuristic. |
| `programSlot` | Generator slot classification | enum key string | Examples: `MAIN_LOWER_STRENGTH`, `SHOULDER_CARE`, `BADMINTON_FOOTWORK`. |
| `redundancyGroup` | Prevent repeated same-pattern loading in generated sessions | enum key string | Examples: `HEAVY_HINGE`, `HORIZONTAL_PUSH_COMPOUND`. |
| `progressMetricType` | Already exists; may need alias expansion | existing enum | Keep existing enum, add alias rules only if needed. |
| `jointStressTag` | Already exists as `jointStressTags` | existing field | Add validator requirements, not new field unless detail granularity needed. |
| `fatigueProfile` | Higher-level fatigue template | enum key string | Could wrap existing fatigue weights/categories for template assignment. |
| `recoveryDecayProfile` | Already exists | existing enum | Add family-specific defaults, do not replace. |
| `badmintonTransferStrength` | Already exists | existing enum | Keep. |
| `badmintonTransferDirect` | More detailed direct axis | comma list or JSON array | Current `sportTransferDirect` partly covers this but is inconsistent. |
| `badmintonTransferSupportive` | More detailed supportive axis | comma list or JSON array | Current `sportTransferSupportive` partly covers this. |
| `courtMovementType` | Already exists as `courtMovementTypes` | existing field | May need alias mapping from current direct tags. |
| `badmintonSkillTarget` | Already exists as `badmintonSkillTargets` | existing field | May need finer split between skill and physical target. |
| `evidenceSourceGroup` | Source IDs backing family template | comma list or JSON array | Use IDs from `metadata_evidence_sources_v0.3.5.0.md`. |
| `evidenceLevel` | Lowest or primary evidence level for metadata row | enum key string | Values should mirror source document. |
| `metadataReviewNotes` | Manual review notes | text | Non-calculation field. |

## Additive Enum Candidates

### movementFamily

Suggested values:

- `SQUAT_VARIANTS`
- `HEAVY_HINGE`
- `LUNGE_SPLIT_SQUAT`
- `STEP_UP`
- `HIP_THRUST_GLUTE`
- `KNEE_DOMINANT_MACHINE`
- `HAMSTRING_KNEE_FLEXION`
- `CALF_ACHILLES`
- `HORIZONTAL_PUSH_COMPOUND`
- `VERTICAL_PUSH_OVERHEAD`
- `BODYWEIGHT_PUSH`
- `VERTICAL_PULL_COMPOUND`
- `HORIZONTAL_PULL_COMPOUND`
- `DELTOID_ISOLATION`
- `SCAPULAR_CONTROL`
- `ROTATOR_CUFF_PREHAB`
- `ELBOW_FLEXION_BICEPS_CURL_VARIANTS`
- `ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS`
- `ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS`
- `OVERHEAD_TRICEPS_LONG_HEAD_REVIEW`
- `FOREARM_GRIP_ACCESSORY_REVIEW`
- `LOADED_CARRY`
- `TRUNK_ANTI_MOVEMENT`
- `ROTATION_POWER`
- `PLYOMETRIC_JUMP`
- `LATERAL_DECELERATION_SSC`
- `BADMINTON_FOOTWORK_REACTION`
- `BADMINTON_DIRECT_PLAY`
- `GENERAL_CONDITIONING`
- `MOBILITY_RECOVERY`

### movementSubtype

Keep subtype values more granular and source-controlled. Do not try to enumerate every app exercise on the first pass. Suggested first batch:

- `BACK_SQUAT`
- `FRONT_SQUAT`
- `GOBLET_SQUAT`
- `CONVENTIONAL_DEADLIFT`
- `ROMANIAN_DEADLIFT`
- `SINGLE_LEG_RDL`
- `BARBELL_HIP_THRUST`
- `GLUTE_BRIDGE`
- `BARBELL_BENCH_PRESS`
- `INCLINE_PRESS`
- `PULL_UP`
- `LAT_PULLDOWN`
- `BARBELL_ROW`
- `ONE_ARM_ROW`
- `FACE_PULL`
- `BAND_EXTERNAL_ROTATION`
- `PALLOF_PRESS`
- `MED_BALL_ROTATIONAL_THROW`
- `LATERAL_BOUND_TO_STICK`
- `SIX_CORNER_FOOTWORK`
- `RANDOM_REACTION_FOOTWORK`
- `BADMINTON_SESSION`
- `BADMINTON_LESSON`

### detailedMuscleTag

Suggested normalized detailed tags:

- `PECTORALIS_MAJOR_STERNAL`
- `PECTORALIS_MAJOR_CLAVICULAR`
- `PECTORALIS_MINOR`
- `ANTERIOR_DELTOID`
- `LATERAL_DELTOID`
- `POSTERIOR_DELTOID`
- `TRICEPS_BRACHII`
- `BICEPS_BRACHII`
- `BRACHIALIS`
- `BRACHIORADIALIS`
- `FOREARM_FLEXORS`
- `FOREARM_EXTENSORS`
- `GRIP_FLEXORS`
- `LATISSIMUS_DORSI`
- `TERES_MAJOR`
- `RHOMBOIDS`
- `TRAPEZIUS_UPPER`
- `TRAPEZIUS_MIDDLE`
- `TRAPEZIUS_LOWER`
- `SERRATUS_ANTERIOR`
- `ROTATOR_CUFF_EXTERNAL`
- `ROTATOR_CUFF_INTERNAL`
- `QUADRICEPS`
- `RECTUS_FEMORIS`
- `HAMSTRINGS`
- `GLUTE_MAX`
- `GLUTE_MED_MIN`
- `HIP_ADDUCTORS`
- `HIP_FLEXORS`
- `ERECTOR_SPINAE`
- `RECTUS_ABDOMINIS`
- `OBLIQUES`
- `DEEP_CORE`
- `GASTROCNEMIUS`
- `SOLEUS`
- `TIBIALIS_ANTERIOR`

Additive candidate values for later review:

- `ADDUCTOR_MAGNUS`
- `HAMSTRING_HIP_EXTENSION`
- `HAMSTRING_KNEE_FLEXION`
- `FOOT_INTRINSICS`
- `SUBSCAPULARIS`
- `TERES_MINOR_INFRASPINATUS`
- `SUPRASPINATUS`

### programSlot

Suggested values:

- `MAIN_LOWER_STRENGTH`
- `MAIN_UPPER_PUSH`
- `MAIN_UPPER_PULL`
- `SECONDARY_LOWER`
- `SECONDARY_UPPER`
- `HYPERTROPHY_ACCESSORY`
- `POSTERIOR_CHAIN_ACCESSORY`
- `SHOULDER_ACCESSORY`
- `SHOULDER_CARE`
- `ROTATOR_CUFF_CARE`
- `CORE_STABILITY`
- `CORE_ROTATION_POWER`
- `LOADED_CARRY_GRIP`
- `PLYOMETRIC_POWER`
- `DECELERATION_LANDING`
- `BADMINTON_FOOTWORK`
- `BADMINTON_DIRECT_PLAY`
- `CONDITIONING`
- `MOBILITY_RECOVERY`

### redundancyGroup

Suggested values:

- `SQUAT_PATTERN_HEAVY_LOWER`
- `HEAVY_HINGE`
- `LUNGE_UNILATERAL_LOWER`
- `STEP_UP_UNILATERAL_LOWER`
- `HIP_THRUST_GLUTE`
- `KNEE_DOMINANT_MACHINE`
- `HAMSTRING_KNEE_FLEXION`
- `CALF_ACHILLES_ANKLE_STIFFNESS`
- `HORIZONTAL_PUSH_COMPOUND`
- `VERTICAL_PUSH_OVERHEAD`
- `VERTICAL_PULL_COMPOUND`
- `HORIZONTAL_PULL_COMPOUND`
- `DELTOID_ISOLATION`
- `SCAPULAR_CONTROL`
- `ROTATOR_CUFF_PREHAB`
- `ELBOW_FLEXION_CURL`
- `ELBOW_FLEXION_NEUTRAL_PRONATED_CURL`
- `ELBOW_EXTENSION_TRICEPS`
- `OVERHEAD_TRICEPS_LONG_HEAD`
- `FOREARM_WRIST_GRIP`
- `LOADED_CARRY`
- `TRUNK_ANTI_MOVEMENT`
- `ROTATIONAL_POWER`
- `VERTICAL_JUMP_SSC`
- `LATERAL_DECELERATION_SSC`
- `BADMINTON_COURT_FOOTWORK`
- `BADMINTON_DIRECT_PLAY`
- `GENERAL_CONDITIONING`
- `RECOVERY_LOW_LOAD`

### evidenceLevel

Reuse the controlled values from the source document:

- `anatomy_textbook`
- `position_stand`
- `systematic_review`
- `meta_analysis`
- `umbrella_review`
- `emg_study`
- `biomechanics_review`
- `coaching_consensus`
- `governing_body_resource`
- `clinical_reference`
- `heuristic_only`

## Alias Mapping Proposal

| Current broad token | Proposed detailed mapping | Notes |
| --- | --- | --- |
| `KNEE_DOMINANT_LOWER`, `SQUAT_PATTERN` | `movementFamily=SQUAT_VARIANTS` | Split machine and unilateral variants manually. |
| `HIP_HINGE`, `HINGE_LOWER`, `POSTERIOR_CHAIN_STRENGTH` | `movementFamily=HEAVY_HINGE` or `HIP_THRUST_GLUTE` | Hip thrust/bridge should not be collapsed into deadlift. |
| `SINGLE_LEG_STRENGTH` | `LUNGE_SPLIT_SQUAT` or `STEP_UP` | Needs subtype review. |
| `HORIZONTAL_PUSH`, `CHEST_STRENGTH` | `HORIZONTAL_PUSH_COMPOUND` | Incline/flat/close-grip subtypes need review. |
| `PUSH_VERTICAL`, `SHOULDER_STRENGTH` | `VERTICAL_PUSH_OVERHEAD` | Landmine and half-kneeling variants may be stability slots. |
| `PULL_VERTICAL` | `VERTICAL_PULL_COMPOUND` | Pull-up versus pulldown progress metric differs. |
| `PULL_HORIZONTAL`, `SCAPULAR_RETRACTION` | `HORIZONTAL_PULL_COMPOUND` | Horizontal row EMG source still pending. |
| `SHOULDER_EXTERNAL_ROTATION` | `ROTATOR_CUFF_PREHAB` | Recovery-only or shoulder-care support. |
| `SHOULDER_ABDUCTION`, `SHOULDER_ISOLATION` | `DELTOID_ISOLATION` | Distinguish lateral/rear delt. |
| `FOOTWORK_DIRECT`, `BADMINTON_FOOTWORK` | `BADMINTON_FOOTWORK_REACTION` | Split direct play sessions from drills. |
| `BADMINTON_DIRECT_PLAY` | `BADMINTON_DIRECT_PLAY` | Should map to sport session and shuttle play time. |
| `DECELERATION_DIRECT`, `LATERAL_DECELERATION_DIRECT` | `LATERAL_DECELERATION_SSC` | Add deceleration/SSC validator rules. |
| `CARDIO`, `AEROBIC_ANAEROBIC` | `GENERAL_CONDITIONING` | Do not assign detailed muscle contribution by default. |
| `MOBILITY`, `RECOVERY` | `MOBILITY_RECOVERY` | Low fatigue / recovery-only. |

## Horizontal Row Source Update

Deprecated source ID:

- `C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE`

Replacement source ID:

- `C_INVERTED_ROW_EMG_SNARR_ESCO_2013`

Policy:

- Use `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` for verified inverted-row and suspension inverted-row support.
- Do not treat this as exact evidence for barbell row, chest-supported row, cable row, machine row, or one-arm row.
- Keep non-inverted row subtypes on manual review until an additional verified row-family source is accepted.

## Recommended Storage Strategy

Short term:

- Add new columns only after a seed migration plan exists.
- Use comma-separated enum keys for simple multi-value fields if consistent with current code.
- Use JSON object for `muscleContribution` because contribution weights are key/value data.
- Keep `metadataReviewNotes` free text and non-calculation-only.

Medium term:

- Add a generated report that compares broad current fields with proposed detailed fields before writing seed changes.
- Add validator tests before seed mass edit.
- Keep old fields as compatibility layer. Do not delete current broad fields.

## Current Keep List

Keep as-is:

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
- `ProgressMetricType`
- `BadmintonTransferStrength`
- `CourtMovementType`
- `BadmintonSkillTarget`
- `JointStressTag`
- `AnalysisEligibility`
- `ActivityKind`
- `PlanningEligibility`

Reason:

- They already support 3.1 readiness, 3.2 trends, 3.3 badminton transfer, and program generation.
- v0.3.5.0 should extend detailed evidence mapping without breaking existing engines.

## Deprecated Arm Isolation Correction

`BICEPS_TRICEPS_ISOLATION_VARIANTS` is deprecated. It has no reliable internal biceps/triceps subtype split and must not be used as `proposedMovementFamily`, `proposedRedundancyGroup`, `proposedProgramSlot`, alias, or template name. Validator output may mention it only as a deprecated legacy input. Existing v1 reports may contain it; every v2 sidecar occurrence is a validation failure.

`ARM_ISOLATION` and `ARM_ACCESSORY` are also deprecated. Replace them with domain-specific values:

- Program slots: `BICEPS_ACCESSORY`, `BRACHIALIS_FOREARM_ACCESSORY`, `TRICEPS_ACCESSORY`, `TRICEPS_ACCESSORY_REVIEW`, `FOREARM_GRIP_ACCESSORY_REVIEW`.
- Redundancy groups: `ELBOW_FLEXION_CURL`, `ELBOW_FLEXION_NEUTRAL_PRONATED_CURL`, `ELBOW_EXTENSION_TRICEPS`, `OVERHEAD_TRICEPS_LONG_HEAD`, `FOREARM_WRIST_GRIP`.
- Movement families: `ELBOW_FLEXION_BICEPS_CURL_VARIANTS`, `ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS`, `ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS`, `OVERHEAD_TRICEPS_LONG_HEAD_REVIEW`, `FOREARM_GRIP_ACCESSORY_REVIEW`.

Additional detailed muscle tags introduced by the split:

- `TRICEPS_LONG_HEAD`
- `TRICEPS_LATERAL_MEDIAL`
- `FOREARM_STABILIZERS`
- `SHOULDER_STABILIZERS`
- `PRONATOR_SUPINATOR_GROUP`
- `WRIST_STABILIZERS`

The stable biceps template must not include `TRICEPS_BRACHII`. The stable triceps template must not include `BICEPS_BRACHII`. Forearm/grip rows must not inherit either integrated arm template.

## Proposed Next Step

Implement validator/reporting before seed reclassification:

1. Add schema-safe proposed fields or a sidecar metadata CSV/JSON for review.
2. Implement validator requirements from `metadata_validator_requirements_v0.3.5.0.md`.
3. Run report-only classification over all 215 seed exercise rows.
4. Review NEEDS_REVIEW rows manually.
5. Only then write seed updates.
