# Exercise Metadata Reclassification Plan v0.3.5.0

## Scope

This plan creates exercise-family templates before seed-wide reclassification. It does not modify `training_settings_seed.csv`, `exercises_seed.json`, Kotlin enums, Room schema, or the program generator.

Current repository connection:

- Seed has 215 `row_type=exercise` rows in `training_settings_seed.csv`.
- `training_settings_seed.csv` is the runtime seed source for v0.3.5.0 planning.
- `exercises_seed.json` is present but currently fails strict JSON parsing in PowerShell because of malformed or encoding-broken strings. Treat it as a reference asset only until repaired.
- Runtime classification is stored on `Exercise` and mapped through `ExerciseMetadataMapper`.
- Program generation uses `activityKind`, `planningEligibility`, movement/fatigue/transfer fields, and conservative name matching only for user-entered exclusions.

`exercises_seed.json` handling policy:

- Do not use as strict machine input until encoding/malformed string issues are repaired.
- Treat `training_settings_seed.csv` as the runtime seed source for v0.3.5.0 planning.
- Create a separate future repair task before using `exercises_seed.json` for automated metadata migration.
- Do not delete or overwrite `exercises_seed.json` in this patch.

Template format:

```text
exerciseFamily:
typicalMovementPatterns:
typicalMovementSubtypes:
typicalEquipment:
typicalPrimaryMuscles:
typicalSecondaryMuscles:
typicalStabilizers:
defaultMuscleContributionTemplate:
typicalStrengthRole:
typicalProgramSlot:
typicalRedundancyGroup:
typicalProgressMetricType:
typicalFatigueProfile:
typicalBadmintonTransferDirect:
typicalBadmintonTransferSupportive:
typicalAnalysisEligibility:
sourceIds:
manualReviewRequiredWhen:
```

Naming policy:

- Enum-like planning keys use `UPPER_SNAKE_CASE`.
- Human-readable exercise examples may remain plain language.
- Display labels in UI are out of scope.
- Muscle tags use the proposed `detailedMuscleTag` vocabulary from `metadata_taxonomy_delta_v0.3.5.0.md`.
- `defaultMuscleContributionTemplate` values are heuristic app-internal templates, not exact physiological measurements.

## Family Templates

```text
exerciseFamily: SQUAT_VARIANTS
typicalMovementPatterns: SQUAT, KNEE_DOMINANT_LOWER
typicalMovementSubtypes: BACK_SQUAT, FRONT_SQUAT, GOBLET_SQUAT, HACK_SQUAT, BOX_SQUAT
typicalEquipment: BARBELL, DUMBBELL, KETTLEBELL, MACHINE, BODYWEIGHT
typicalPrimaryMuscles: QUADRICEPS, RECTUS_FEMORIS, GLUTE_MAX
typicalSecondaryMuscles: HAMSTRINGS, ERECTOR_SPINAE, GLUTE_MED_MIN
typicalStabilizers: DEEP_CORE, OBLIQUES, HIP_ADDUCTORS
defaultMuscleContributionTemplate: QUADRICEPS 0.30, RECTUS_FEMORIS 0.10, GLUTE_MAX 0.25, HAMSTRINGS 0.08, ERECTOR_SPINAE 0.10, DEEP_CORE 0.07, GLUTE_MED_MIN 0.05, HIP_ADDUCTORS 0.05
typicalStrengthRole: MAIN_STRENGTH or SECONDARY_STRENGTH
typicalProgramSlot: MAIN_LOWER_STRENGTH
typicalRedundancyGroup: SQUAT_PATTERN_HEAVY_LOWER
typicalProgressMetricType: ESTIMATED_1RM for barbell main lifts; VOLUME_LOAD or REPS_AT_LOAD for accessory variants
typicalFatigueProfile: SYSTEMIC, NEURAL_HEAVY, LOCAL_MUSCLE, HIGH or MODERATE axial load
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: LOWER_STRENGTH_SUPPORT, KNEE_CONTROL, HIP_STABILITY_SUPPORT
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_6, B_ACSM_2009_PMID19204579, B_NSCA_ESSENTIALS_5E, C_GLUTE_MAX_REVIEW_PMC7039033
manualReviewRequiredWhen: machine variant, single-leg variant, front squat, low-load rehab squat, pain-limited squat
```

```text
exerciseFamily: DEADLIFT_HINGE_VARIANTS
typicalMovementPatterns: HINGE, HIP_HINGE, POSTERIOR_CHAIN_STRENGTH
typicalMovementSubtypes: CONVENTIONAL_DEADLIFT, ROMANIAN_DEADLIFT, STIFF_LEG_DEADLIFT, TRAP_BAR_DEADLIFT, GOOD_MORNING, SINGLE_LEG_RDL
typicalEquipment: BARBELL, DUMBBELL, KETTLEBELL, MACHINE
typicalPrimaryMuscles: GLUTE_MAX, HAMSTRINGS, ERECTOR_SPINAE
typicalSecondaryMuscles: LATISSIMUS_DORSI, GRIP_FLEXORS, TRAPEZIUS_MIDDLE, DEEP_CORE
typicalStabilizers: GLUTE_MED_MIN, OBLIQUES, RHOMBOIDS
defaultMuscleContributionTemplate: GLUTE_MAX 0.25, HAMSTRINGS 0.25, ERECTOR_SPINAE 0.20, LATISSIMUS_DORSI 0.08, GRIP_FLEXORS 0.07, DEEP_CORE 0.07, TRAPEZIUS_MIDDLE 0.04, GLUTE_MED_MIN 0.04
typicalStrengthRole: MAIN_STRENGTH for heavy deadlift; SECONDARY_STRENGTH or ACCESSORY for RDL/single-leg variants
typicalProgramSlot: MAIN_HINGE_STRENGTH or POSTERIOR_CHAIN_ACCESSORY
typicalRedundancyGroup: HEAVY_HINGE
typicalProgressMetricType: ESTIMATED_1RM for main barbell hinge; VOLUME_LOAD or REPS_AT_LOAD for accessories
typicalFatigueProfile: SYSTEMIC, NEURAL_HEAVY, LOCAL_MUSCLE, GRIP_FOREARM, LONG or VERY_LONG decay
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: POSTERIOR_CHAIN_CAPACITY, HIP_STABILITY_SUPPORT
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_6, C_DEADLIFT_EMG_PMC7046193, C_GLUTE_MAX_REVIEW_PMC7039033
manualReviewRequiredWhen: single-leg, high lumbar stress, low-load rehab hinge, kettlebell swing or power hinge
```

```text
exerciseFamily: LUNGE_SPLIT_SQUAT_VARIANTS
typicalMovementPatterns: LUNGE, SQUAT, UNILATERAL_LOWER
typicalMovementSubtypes: FORWARD_LUNGE, REVERSE_LUNGE, WALKING_LUNGE, SPLIT_SQUAT, BULGARIAN_SPLIT_SQUAT, LATERAL_LUNGE
typicalEquipment: BODYWEIGHT, DUMBBELL, BARBELL, KETTLEBELL
typicalPrimaryMuscles: QUADRICEPS, RECTUS_FEMORIS, GLUTE_MAX
typicalSecondaryMuscles: HAMSTRINGS, HIP_ADDUCTORS, GASTROCNEMIUS, SOLEUS
typicalStabilizers: GLUTE_MED_MIN, DEEP_CORE, OBLIQUES, TIBIALIS_ANTERIOR
defaultMuscleContributionTemplate: QUADRICEPS 0.25, RECTUS_FEMORIS 0.10, GLUTE_MAX 0.25, HAMSTRINGS 0.10, HIP_ADDUCTORS 0.08, GASTROCNEMIUS 0.05, SOLEUS 0.05, GLUTE_MED_MIN 0.05, DEEP_CORE 0.04, OBLIQUES 0.03
typicalStrengthRole: SECONDARY_STRENGTH or ACCESSORY
typicalProgramSlot: UNILATERAL_LOWER_STRENGTH
typicalRedundancyGroup: LUNGE_UNILATERAL_LOWER
typicalProgressMetricType: REPS_AT_LOAD or VOLUME_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, SYSTEMIC moderate, DECELERATION if dynamic/lateral/front lunge
typicalBadmintonTransferDirect: LUNGE_REACH for badminton-specific front/lateral lunge drills
typicalBadmintonTransferSupportive: HIP_STABILITY_SUPPORT, KNEE_CONTROL
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER if court-specific, BALANCE
sourceIds: A_OPENSTAX_11_6, E_BADMINTON_LUNGE_PMC7648456, C_GLUTE_MED_MIN_META_PMC7727410
manualReviewRequiredWhen: loaded heavy, ballistic, front-court badminton intent, knee pain tag, lateral plane emphasis
```

```text
exerciseFamily: STEP_UP_VARIANTS
typicalMovementPatterns: SQUAT, LUNGE, UNILATERAL_LOWER
typicalMovementSubtypes: STEP_UP, LATERAL_STEP_UP, HIGH_BOX_STEP_UP, WEIGHTED_STEP_UP
typicalEquipment: BOX, DUMBBELL, BARBELL, BODYWEIGHT
typicalPrimaryMuscles: QUADRICEPS, RECTUS_FEMORIS, GLUTE_MAX
typicalSecondaryMuscles: HAMSTRINGS, GASTROCNEMIUS, SOLEUS, HIP_ADDUCTORS
typicalStabilizers: GLUTE_MED_MIN, DEEP_CORE, OBLIQUES, TIBIALIS_ANTERIOR
defaultMuscleContributionTemplate: QUADRICEPS 0.25, RECTUS_FEMORIS 0.08, GLUTE_MAX 0.30, HAMSTRINGS 0.08, GASTROCNEMIUS 0.05, SOLEUS 0.05, GLUTE_MED_MIN 0.10, DEEP_CORE 0.05, OBLIQUES 0.04
typicalStrengthRole: ACCESSORY or SECONDARY_STRENGTH
typicalProgramSlot: UNILATERAL_LOWER_ACCESSORY
typicalRedundancyGroup: STEP_UP_UNILATERAL_LOWER
typicalProgressMetricType: REPS_AT_LOAD or VOLUME_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, moderate unilateral stability
typicalBadmintonTransferDirect: NONE by default
typicalBadmintonTransferSupportive: HIP_STABILITY_SUPPORT, KNEE_CONTROL, LOWER_STRENGTH_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_6, C_GLUTE_MED_MIN_META_PMC7727410, C_GLUTE_REHAB_EMG_PMC3201064
manualReviewRequiredWhen: plyometric step-up, high box, lateral step-up, knee pain, sport-specific naming
```

```text
exerciseFamily: HIP_THRUST_GLUTE_BRIDGE_VARIANTS
typicalMovementPatterns: HINGE, HIP_EXTENSION, GLUTE_STRENGTH
typicalMovementSubtypes: BARBELL_HIP_THRUST, GLUTE_BRIDGE, SINGLE_LEG_BRIDGE, MACHINE_HIP_THRUST
typicalEquipment: BARBELL, MACHINE, BENCH, BODYWEIGHT, BAND
typicalPrimaryMuscles: GLUTE_MAX
typicalSecondaryMuscles: HAMSTRINGS, GLUTE_MED_MIN, HIP_ADDUCTORS
typicalStabilizers: DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: GLUTE_MAX 0.45, HAMSTRINGS 0.15, GLUTE_MED_MIN 0.12, HIP_ADDUCTORS 0.08, DEEP_CORE 0.10, OBLIQUES 0.05, QUADRICEPS 0.05
typicalStrengthRole: SECONDARY_STRENGTH or ACCESSORY
typicalProgramSlot: GLUTE_POSTERIOR_CHAIN_ACCESSORY
typicalRedundancyGroup: HIP_THRUST_GLUTE
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, moderate systemic if heavily loaded, low axial compared with deadlift
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: POSTERIOR_CHAIN_CAPACITY, HIP_STABILITY_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: C_HIP_THRUST_REVIEW_PMC6544005, C_GLUTE_MAX_REVIEW_PMC7039033, C_GLUTE_REHAB_EMG_PMC3201064
manualReviewRequiredWhen: single-leg bridge, rehab-only bridge, heavy barbell variant
```

```text
exerciseFamily: LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS
typicalMovementPatterns: SQUAT, KNEE_DOMINANT_LOWER
typicalMovementSubtypes: LEG_PRESS, SINGLE_LEG_LEG_PRESS, HACK_SQUAT_MACHINE, LEG_EXTENSION
typicalEquipment: MACHINE, LEG_PRESS_MACHINE, HACK_SQUAT_MACHINE, LEG_EXTENSION_MACHINE
typicalPrimaryMuscles: QUADRICEPS, RECTUS_FEMORIS
typicalSecondaryMuscles: GLUTE_MAX, HAMSTRINGS, GASTROCNEMIUS
typicalStabilizers: DEEP_CORE, GLUTE_MED_MIN
defaultMuscleContributionTemplate: QUADRICEPS 0.40, RECTUS_FEMORIS 0.12, GLUTE_MAX 0.18, HAMSTRINGS 0.08, GASTROCNEMIUS 0.05, GLUTE_MED_MIN 0.05, DEEP_CORE 0.05, HIP_ADDUCTORS 0.07
typicalStrengthRole: SECONDARY_STRENGTH or ACCESSORY
typicalProgramSlot: LOWER_MACHINE_STRENGTH
typicalRedundancyGroup: KNEE_DOMINANT_MACHINE
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, SYSTEMIC moderate if heavy, axial load lower than barbell squat
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: LOWER_STRENGTH_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_6, B_ACSM_2009_PMID19204579
manualReviewRequiredWhen: machine path changes hip/knee emphasis, single-leg, rehab intent
```

```text
exerciseFamily: HAMSTRING_CURL_VARIANTS
typicalMovementPatterns: ISOLATION, KNEE_FLEXION
typicalMovementSubtypes: LYING_LEG_CURL, SEATED_LEG_CURL, BALL_LEG_CURL, NORDIC_CURL
typicalEquipment: MACHINE, LEG_CURL_MACHINE, BODYWEIGHT, BALL
typicalPrimaryMuscles: HAMSTRINGS
typicalSecondaryMuscles: GASTROCNEMIUS, GLUTE_MAX
typicalStabilizers: GLUTE_MED_MIN, DEEP_CORE
defaultMuscleContributionTemplate: HAMSTRINGS 0.65, GASTROCNEMIUS 0.10, GLUTE_MAX 0.05, GLUTE_MED_MIN 0.08, DEEP_CORE 0.07, HIP_ADDUCTORS 0.05
typicalStrengthRole: ACCESSORY
typicalProgramSlot: POSTERIOR_CHAIN_ACCESSORY
typicalRedundancyGroup: HAMSTRING_KNEE_FLEXION
typicalProgressMetricType: VOLUME_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, SHORT to MEDIUM decay
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: POSTERIOR_CHAIN_CAPACITY
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME
sourceIds: A_OPENSTAX_11_6, B_ACSM_2009_PMID19204579
manualReviewRequiredWhen: Nordic/eccentric variant, rehab-only variant, no-load bodyweight variant
```

```text
exerciseFamily: CALF_RAISE_ANKLE_STIFFNESS_VARIANTS
typicalMovementPatterns: ISOLATION, LOCOMOTION, HOP_SUPPORT
typicalMovementSubtypes: STANDING_CALF_RAISE, SEATED_CALF_RAISE, POGO_HOP, ANKLE_STIFFNESS_DRILL
typicalEquipment: MACHINE, DUMBBELL, BARBELL, BODYWEIGHT
typicalPrimaryMuscles: GASTROCNEMIUS, SOLEUS
typicalSecondaryMuscles: TIBIALIS_ANTERIOR
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: GASTROCNEMIUS 0.35, SOLEUS 0.30, TIBIALIS_ANTERIOR 0.10, DEEP_CORE 0.05, GLUTE_MED_MIN 0.05, QUADRICEPS 0.05, HAMSTRINGS 0.05, HIP_ADDUCTORS 0.05
typicalStrengthRole: ACCESSORY or PLYOMETRIC
typicalProgramSlot: ANKLE_CALF_SUPPORT
typicalRedundancyGroup: CALF_ACHILLES_ANKLE_STIFFNESS
typicalProgressMetricType: VOLUME_LOAD or QUALITY_BASED
typicalFatigueProfile: LOCAL_MUSCLE, ELASTIC_SSC for hops/pogos
typicalBadmintonTransferDirect: NONE by default
typicalBadmintonTransferSupportive: ANKLE_SSC_SUPPORT, LANDING_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER for SSC drills, BALANCE
sourceIds: A_OPENSTAX_11_6, E_BADMINTON_LUNGE_PMC7648456
manualReviewRequiredWhen: pogo/plyometric intent, Achilles pain tag, high jump volume
```

```text
exerciseFamily: BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS
typicalMovementPatterns: PUSH_HORIZONTAL
typicalMovementSubtypes: BARBELL_BENCH_PRESS, DUMBBELL_BENCH_PRESS, INCLINE_PRESS, DECLINE_PRESS, MACHINE_CHEST_PRESS
typicalEquipment: BARBELL, DUMBBELL, MACHINE, BENCH
typicalPrimaryMuscles: PECTORALIS_MAJOR_STERNAL, PECTORALIS_MAJOR_CLAVICULAR, ANTERIOR_DELTOID
typicalSecondaryMuscles: TRICEPS_BRACHII, SERRATUS_ANTERIOR
typicalStabilizers: ROTATOR_CUFF_EXTERNAL, ROTATOR_CUFF_INTERNAL, DEEP_CORE
defaultMuscleContributionTemplate: PECTORALIS_MAJOR_STERNAL 0.30, PECTORALIS_MAJOR_CLAVICULAR 0.15, ANTERIOR_DELTOID 0.20, TRICEPS_BRACHII 0.20, SERRATUS_ANTERIOR 0.05, ROTATOR_CUFF_EXTERNAL 0.05, DEEP_CORE 0.05
typicalStrengthRole: MAIN_STRENGTH or SECONDARY_STRENGTH
typicalProgramSlot: UPPER_PUSH_STRENGTH
typicalRedundancyGroup: HORIZONTAL_PUSH_COMPOUND
typicalProgressMetricType: ESTIMATED_1RM for barbell bench; VOLUME_LOAD for dumbbell/machine accessories
typicalFatigueProfile: SYSTEMIC moderate, NEURAL_HEAVY moderate-to-high, LOCAL_MUSCLE
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: UPPER_PUSH_CAPACITY, SHOULDER_CONTROL if relevant
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_5, C_BENCH_INCLINE_EMG_PMC7579505, B_ACSM_2009_PMID19204579
manualReviewRequiredWhen: incline/decline angle, close-grip, machine path, shoulder pain, dumbbell unilateral
```

```text
exerciseFamily: OVERHEAD_PRESS_LANDMINE_PRESS_VARIANTS
typicalMovementPatterns: PUSH_VERTICAL
typicalMovementSubtypes: BARBELL_OVERHEAD_PRESS, DUMBBELL_SHOULDER_PRESS, LANDMINE_PRESS, HALF_KNEELING_LANDMINE_PRESS
typicalEquipment: BARBELL, DUMBBELL, LANDMINE, KETTLEBELL
typicalPrimaryMuscles: ANTERIOR_DELTOID, LATERAL_DELTOID, TRICEPS_BRACHII
typicalSecondaryMuscles: PECTORALIS_MAJOR_CLAVICULAR, SERRATUS_ANTERIOR, TRAPEZIUS_UPPER
typicalStabilizers: ROTATOR_CUFF_EXTERNAL, ROTATOR_CUFF_INTERNAL, TRAPEZIUS_LOWER, DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: ANTERIOR_DELTOID 0.25, LATERAL_DELTOID 0.15, TRICEPS_BRACHII 0.20, PECTORALIS_MAJOR_CLAVICULAR 0.08, SERRATUS_ANTERIOR 0.08, TRAPEZIUS_UPPER 0.06, ROTATOR_CUFF_EXTERNAL 0.06, ROTATOR_CUFF_INTERNAL 0.04, TRAPEZIUS_LOWER 0.04, DEEP_CORE 0.04
typicalStrengthRole: MAIN_STRENGTH, SECONDARY_STRENGTH, or STABILITY for landmine/half-kneeling
typicalProgramSlot: VERTICAL_PUSH_STRENGTH
typicalRedundancyGroup: VERTICAL_PUSH_OVERHEAD
typicalProgressMetricType: ESTIMATED_1RM for barbell press; VOLUME_LOAD or QUALITY_BASED for landmine stability variants
typicalFatigueProfile: LOCAL_MUSCLE, OVERHEAD_REPETITION, SYSTEMIC moderate, ANTI_ROTATION for unilateral variants
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY, OVERHEAD_POWER_SUPPORT, ANTI_ROTATION_STABILITY
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: painful overhead range, unilateral/half-kneeling, landmine arc, shoulder-care intent
```

```text
exerciseFamily: PUSH_UP_VARIANTS
typicalMovementPatterns: PUSH_HORIZONTAL
typicalMovementSubtypes: PUSH_UP, DIAMOND_PUSH_UP, INCLINE_PUSH_UP, DECLINE_PUSH_UP, TEMPO_PUSH_UP
typicalEquipment: BODYWEIGHT, BENCH, BAND
typicalPrimaryMuscles: PECTORALIS_MAJOR_STERNAL, TRICEPS_BRACHII
typicalSecondaryMuscles: PECTORALIS_MAJOR_CLAVICULAR, ANTERIOR_DELTOID, SERRATUS_ANTERIOR
typicalStabilizers: ROTATOR_CUFF_EXTERNAL, ROTATOR_CUFF_INTERNAL, DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: PECTORALIS_MAJOR_STERNAL 0.25, PECTORALIS_MAJOR_CLAVICULAR 0.10, TRICEPS_BRACHII 0.22, ANTERIOR_DELTOID 0.13, SERRATUS_ANTERIOR 0.10, DEEP_CORE 0.10, OBLIQUES 0.05, ROTATOR_CUFF_EXTERNAL 0.05
typicalStrengthRole: ACCESSORY or SECONDARY_STRENGTH
typicalProgramSlot: UPPER_PUSH_ACCESSORY
typicalRedundancyGroup: BODYWEIGHT_HORIZONTAL_PUSH
typicalProgressMetricType: REPS_AT_LOAD or VOLUME_LOAD with bodyweight proxy
typicalFatigueProfile: LOCAL_MUSCLE, moderate systemic if high volume
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_CONTROL, TRUNK_CONTROL_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390
manualReviewRequiredWhen: plyometric push-up, handstand/pike variant, shoulder pain
```

```text
exerciseFamily: PULL_UP_CHIN_UP_LAT_PULLDOWN_VARIANTS
typicalMovementPatterns: PULL_VERTICAL
typicalMovementSubtypes: PULL_UP, CHIN_UP, WEIGHTED_PULL_UP, ASSISTED_PULL_UP, LAT_PULLDOWN
typicalEquipment: PULLUP_BAR, BODYWEIGHT, CABLE, MACHINE
typicalPrimaryMuscles: LATISSIMUS_DORSI, TERES_MAJOR
typicalSecondaryMuscles: BICEPS_BRACHII, BRACHIALIS, BRACHIORADIALIS, TRAPEZIUS_LOWER, TRAPEZIUS_MIDDLE
typicalStabilizers: GRIP_FLEXORS, DEEP_CORE, ROTATOR_CUFF_EXTERNAL
defaultMuscleContributionTemplate: LATISSIMUS_DORSI 0.32, TERES_MAJOR 0.10, BICEPS_BRACHII 0.14, BRACHIALIS 0.07, BRACHIORADIALIS 0.06, TRAPEZIUS_LOWER 0.08, TRAPEZIUS_MIDDLE 0.06, GRIP_FLEXORS 0.08, DEEP_CORE 0.05, ROTATOR_CUFF_EXTERNAL 0.04
typicalStrengthRole: MAIN_STRENGTH or SECONDARY_STRENGTH
typicalProgramSlot: VERTICAL_PULL_STRENGTH
typicalRedundancyGroup: VERTICAL_PULL_COMPOUND
typicalProgressMetricType: ESTIMATED_1RM for weighted pull-up; REPS_AT_LOAD or VOLUME_LOAD for pulldown/assisted work
typicalFatigueProfile: LOCAL_MUSCLE, NEURAL_HEAVY if weighted, GRIP_FOREARM
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: GRIP_FOREARM, SHOULDER_DURABILITY
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, C_LAT_PULLDOWN_EMG_PMC449729, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: weighted pull-up, grip variation, behind-neck pulldown, shoulder pain; lat pulldown evidence should be used as vertical-pull support rather than exact pull-up truth
```

```text
exerciseFamily: ROW_VARIANTS
typicalMovementPatterns: PULL_HORIZONTAL
typicalMovementSubtypes: BARBELL_ROW, YATES_ROW, ONE_ARM_ROW, CABLE_ROW, CHEST_SUPPORTED_ROW, INVERTED_ROW, SUSPENSION_ROW
typicalEquipment: BARBELL, DUMBBELL, CABLE, MACHINE, TRX_RING, BODYWEIGHT
typicalPrimaryMuscles: LATISSIMUS_DORSI, RHOMBOIDS, TRAPEZIUS_MIDDLE
typicalSecondaryMuscles: POSTERIOR_DELTOID, BICEPS_BRACHII, BRACHIALIS, BRACHIORADIALIS
typicalStabilizers: TRAPEZIUS_LOWER, ROTATOR_CUFF_EXTERNAL, GRIP_FLEXORS, DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: LATISSIMUS_DORSI 0.25, RHOMBOIDS 0.15, TRAPEZIUS_MIDDLE 0.15, POSTERIOR_DELTOID 0.12, BICEPS_BRACHII 0.10, BRACHIALIS 0.05, BRACHIORADIALIS 0.04, GRIP_FLEXORS 0.05, TRAPEZIUS_LOWER 0.04, DEEP_CORE 0.03, OBLIQUES 0.02
typicalStrengthRole: MAIN_STRENGTH, SECONDARY_STRENGTH, or ACCESSORY
typicalProgramSlot: HORIZONTAL_PULL_STRENGTH
typicalRedundancyGroup: HORIZONTAL_PULL_COMPOUND
typicalProgressMetricType: ESTIMATED_1RM for heavy barbell row; VOLUME_LOAD for cable/machine/dumbbell rows
typicalFatigueProfile: LOCAL_MUSCLE, NEURAL_HEAVY moderate for heavy barbell, GRIP_FOREARM, ANTI_ROTATION for unilateral
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY, ANTI_ROTATION_STABILITY, GRIP_FOREARM
typicalAnalysisEligibility: FATIGUE, STRENGTH_PROGRESS, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, C_INVERTED_ROW_EMG_SNARR_ESCO_2013, D_SCAP_ROTATOR_CUFF_PMC2857390
manualReviewRequiredWhen: unsupported heavy hinge row, chest-supported row, one-arm anti-rotation intent, barbell row, cable row, or machine row. Snarr/Esco directly supports inverted row and suspension inverted row only; do not overextend it to every row subtype without manual review.
```

```text
exerciseFamily: LATERAL_RAISE_REAR_DELT_RAISE_VARIANTS
typicalMovementPatterns: ISOLATION
typicalMovementSubtypes: LATERAL_RAISE, CABLE_LATERAL_RAISE, REAR_DELT_RAISE, REVERSE_FLY
typicalEquipment: DUMBBELL, CABLE, MACHINE, BAND
typicalPrimaryMuscles: LATERAL_DELTOID, POSTERIOR_DELTOID
typicalSecondaryMuscles: TRAPEZIUS_UPPER, TRAPEZIUS_MIDDLE, ROTATOR_CUFF_EXTERNAL
typicalStabilizers: SERRATUS_ANTERIOR, DEEP_CORE
defaultMuscleContributionTemplate: LATERAL_DELTOID 0.35, POSTERIOR_DELTOID 0.25, TRAPEZIUS_UPPER 0.10, TRAPEZIUS_MIDDLE 0.08, ROTATOR_CUFF_EXTERNAL 0.10, SERRATUS_ANTERIOR 0.07, DEEP_CORE 0.05
typicalStrengthRole: ACCESSORY
typicalProgramSlot: SHOULDER_ACCESSORY
typicalRedundancyGroup: DELTOID_ISOLATION
typicalProgressMetricType: VOLUME_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY if controlled low-load
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390
manualReviewRequiredWhen: high-load cheating, pain, prehab intent
```

```text
exerciseFamily: FACE_PULL_LOWER_TRAP_SERRATUS_VARIANTS
typicalMovementPatterns: PREHAB, PULL_HORIZONTAL, SCAPULAR_CONTROL
typicalMovementSubtypes: FACE_PULL, LOWER_TRAP_RAISE, Y_RAISE, SERRATUS_WALL_SLIDE, PUSH_UP_PLUS
typicalEquipment: CABLE, BAND, DUMBBELL, BODYWEIGHT
typicalPrimaryMuscles: SERRATUS_ANTERIOR, TRAPEZIUS_LOWER, TRAPEZIUS_MIDDLE, POSTERIOR_DELTOID
typicalSecondaryMuscles: ROTATOR_CUFF_EXTERNAL, RHOMBOIDS
typicalStabilizers: DEEP_CORE, ROTATOR_CUFF_INTERNAL
defaultMuscleContributionTemplate: SERRATUS_ANTERIOR 0.20, TRAPEZIUS_LOWER 0.20, TRAPEZIUS_MIDDLE 0.15, POSTERIOR_DELTOID 0.15, ROTATOR_CUFF_EXTERNAL 0.12, RHOMBOIDS 0.08, DEEP_CORE 0.05, ROTATOR_CUFF_INTERNAL 0.05
typicalStrengthRole: PREHAB or ACCESSORY
typicalProgramSlot: SHOULDER_CARE
typicalRedundancyGroup: SCAPULAR_CONTROL
typicalProgressMetricType: NOT_PROGRESS_TARGET or QUALITY_BASED
typicalFatigueProfile: LOW_FATIGUE_REHAB, LOCAL_MUSCLE low-to-moderate
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY, OVERHEAD_CONTROL
typicalAnalysisEligibility: RECOVERY_ONLY, BALANCE, BADMINTON_TRANSFER if shoulder-care support
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390, D_PHYSIOPEDIA_SCAP_DYSKINESIA
manualReviewRequiredWhen: loaded high-RPE rows mislabeled as prehab, painful shoulder, unclear scapular target
```

```text
exerciseFamily: EXTERNAL_ROTATION_INTERNAL_ROTATION_VARIANTS
typicalMovementPatterns: PREHAB, ISOLATION
typicalMovementSubtypes: BAND_EXTERNAL_ROTATION, CABLE_EXTERNAL_ROTATION, INTERNAL_ROTATION, SIDE_LYING_EXTERNAL_ROTATION
typicalEquipment: BAND, CABLE, DUMBBELL
typicalPrimaryMuscles: ROTATOR_CUFF_EXTERNAL or ROTATOR_CUFF_INTERNAL
typicalSecondaryMuscles: POSTERIOR_DELTOID, TRAPEZIUS_LOWER, TRAPEZIUS_MIDDLE
typicalStabilizers: SERRATUS_ANTERIOR, DEEP_CORE
defaultMuscleContributionTemplate: ROTATOR_CUFF_EXTERNAL 0.40, ROTATOR_CUFF_INTERNAL 0.15, POSTERIOR_DELTOID 0.10, TRAPEZIUS_LOWER 0.10, TRAPEZIUS_MIDDLE 0.08, SERRATUS_ANTERIOR 0.10, DEEP_CORE 0.07
typicalStrengthRole: PREHAB
typicalProgramSlot: ROTATOR_CUFF_CARE
typicalRedundancyGroup: ROTATOR_CUFF_PREHAB
typicalProgressMetricType: NOT_PROGRESS_TARGET or QUALITY_BASED
typicalFatigueProfile: LOW_FATIGUE_REHAB, local shoulder low
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY, OVERHEAD_REPETITION_SUPPORT
typicalAnalysisEligibility: RECOVERY_ONLY, BALANCE, BADMINTON_TRANSFER
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: heavy cable loading, pain, rehab-only clinical use; internal rotation rows should rebalance ROTATOR_CUFF_INTERNAL as primary
```

`BICEPS_TRICEPS_ISOLATION_VARIANTS`, `ARM_ACCESSORY`, and `ARM_ISOLATION` are deprecated legacy inputs. They are not templates or aliases and must not be emitted by v2 sidecar generation.

```text
exerciseFamily: ELBOW_FLEXION_BICEPS_CURL_VARIANTS
typicalMovementPatterns: ELBOW_FLEXION, SUPINATED_CURL, BICEPS_ACCESSORY
typicalMovementSubtypes: EZ_BAR_CURL, DUMBBELL_CURL, ONE_ARM_DUMBBELL_CURL, CABLE_CURL, INCLINE_DUMBBELL_CURL, PREACHER_CURL, CONCENTRATION_CURL, SPIDER_CURL
typicalEquipment: DUMBBELL, CABLE, BARBELL, EZBAR, MACHINE
typicalPrimaryMuscles: BICEPS_BRACHII, BRACHIALIS, BRACHIORADIALIS
typicalSecondaryMuscles: FOREARM_FLEXORS, GRIP_FLEXORS
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: BICEPS_BRACHII 0.45, BRACHIALIS 0.18, BRACHIORADIALIS 0.12, FOREARM_FLEXORS 0.10, GRIP_FLEXORS 0.08, DEEP_CORE 0.07
typicalStrengthRole: ACCESSORY
typicalProgramSlot: BICEPS_ACCESSORY
typicalRedundancyGroup: ELBOW_FLEXION_CURL
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, ELBOW_FLEXION, GRIP_FOREARM low-to-moderate, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: GRIP_FOREARM_SUPPORT only when grip involvement is meaningful
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: elbow pain, unusual shoulder position, grip-specific subtype ambiguity
```

```text
exerciseFamily: ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS
typicalMovementPatterns: ELBOW_FLEXION, NEUTRAL_GRIP_CURL, PRONATED_GRIP_CURL
typicalMovementSubtypes: HAMMER_CURL, ROPE_HAMMER_CURL, CROSS_BODY_HAMMER_CURL, ONE_ARM_HAMMER_CURL, REVERSE_CURL
typicalEquipment: DUMBBELL, CABLE, BARBELL, EZBAR
typicalPrimaryMuscles: BRACHIALIS, BRACHIORADIALIS, BICEPS_BRACHII
typicalSecondaryMuscles: FOREARM_EXTENSORS, FOREARM_FLEXORS, GRIP_FLEXORS
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: BRACHIALIS 0.28, BRACHIORADIALIS 0.25, BICEPS_BRACHII 0.18, FOREARM_EXTENSORS 0.10, FOREARM_FLEXORS 0.08, GRIP_FLEXORS 0.07, DEEP_CORE 0.04
typicalStrengthRole: ACCESSORY
typicalProgramSlot: BRACHIALIS_FOREARM_ACCESSORY
typicalRedundancyGroup: ELBOW_FLEXION_NEUTRAL_PRONATED_CURL
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, ELBOW_FLEXION, GRIP_FOREARM moderate, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: GRIP_FOREARM_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: elbow or wrist pain, mixed grip subtype, pronation/supination loading
```

```text
exerciseFamily: ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS
typicalMovementPatterns: ELBOW_EXTENSION, TRICEPS_ISOLATION
typicalMovementSubtypes: TRICEPS_PUSHDOWN, ONE_ARM_TRICEPS_PUSHDOWN, DUMBBELL_KICKBACK, CABLE_ONE_ARM_TRICEPS_EXTENSION, LYING_TRICEPS_EXTENSION
typicalEquipment: DUMBBELL, CABLE, BARBELL, EZBAR
typicalPrimaryMuscles: TRICEPS_BRACHII, TRICEPS_LONG_HEAD, TRICEPS_LATERAL_MEDIAL
typicalSecondaryMuscles: FOREARM_STABILIZERS
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: TRICEPS_BRACHII 0.45, TRICEPS_LONG_HEAD 0.20, TRICEPS_LATERAL_MEDIAL 0.20, FOREARM_STABILIZERS 0.07, DEEP_CORE 0.08
typicalStrengthRole: ACCESSORY
typicalProgramSlot: TRICEPS_ACCESSORY
typicalRedundancyGroup: ELBOW_EXTENSION_TRICEPS
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, ELBOW_EXTENSION, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: UPPER_PUSH_SUPPORT only if needed
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE
sourceIds: A_OPENSTAX_11_5
manualReviewRequiredWhen: elbow pain, shoulder-extension-biased kickback, mixed compound press
```

```text
exerciseFamily: OVERHEAD_TRICEPS_LONG_HEAD_REVIEW
typicalMovementPatterns: ELBOW_EXTENSION, OVERHEAD_TRICEPS_EXTENSION, SHOULDER_FLEXION_POSITION
typicalMovementSubtypes: OVERHEAD_TRICEPS_EXTENSION, ONE_ARM_OVERHEAD_TRICEPS_EXTENSION
typicalEquipment: DUMBBELL, CABLE, EZBAR
typicalPrimaryMuscles: TRICEPS_LONG_HEAD, TRICEPS_BRACHII, TRICEPS_LATERAL_MEDIAL
typicalSecondaryMuscles: SHOULDER_STABILIZERS, ROTATOR_CUFF_EXTERNAL
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: REVIEW; conservative template only after shoulder-position evidence review
typicalStrengthRole: ACCESSORY_REVIEW
typicalProgramSlot: TRICEPS_ACCESSORY_REVIEW
typicalRedundancyGroup: OVERHEAD_TRICEPS_LONG_HEAD
typicalProgressMetricType: VOLUME_LOAD or REPS_AT_LOAD
typicalFatigueProfile: LOCAL_MUSCLE, ELBOW_EXTENSION, OVERHEAD_REPETITION_REVIEW, SHOULDER_STRESS_REVIEW
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: OVERHEAD_TOLERANCE_REVIEW
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BALANCE, BADMINTON_TRANSFER_REVIEW
sourceIds: A_OPENSTAX_11_5, D_SCAP_ROTATOR_CUFF_PMC2857390
manualReviewRequiredWhen: always; this remains a review family until long-head and shoulder-stress mapping is approved
```

```text
exerciseFamily: FOREARM_GRIP_ACCESSORY_REVIEW
typicalMovementPatterns: WRIST_FLEXION, WRIST_EXTENSION, FOREARM_PRONATION_SUPINATION, GRIP_SUPPORT
typicalMovementSubtypes: WRIST_CURL, REVERSE_WRIST_CURL, WRIST_EXTENSION, PRONATION_SUPINATION
typicalEquipment: DUMBBELL, CABLE, BARBELL
typicalPrimaryMuscles: FOREARM_FLEXORS, FOREARM_EXTENSORS, PRONATOR_SUPINATOR_GROUP, GRIP_FLEXORS
typicalSecondaryMuscles: WRIST_STABILIZERS
typicalStabilizers: DEEP_CORE
defaultMuscleContributionTemplate: REVIEW; subtype-specific wrist and pronation/supination template required
typicalStrengthRole: ACCESSORY_REVIEW
typicalProgramSlot: FOREARM_GRIP_ACCESSORY_REVIEW
typicalRedundancyGroup: FOREARM_WRIST_GRIP
typicalProgressMetricType: VOLUME_LOAD, REPS_AT_LOAD, or QUALITY_BASED by subtype
typicalFatigueProfile: LOCAL_MUSCLE, GRIP_FOREARM, WRIST_FOREARM_REVIEW, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: GRIP_FOREARM_SUPPORT
typicalAnalysisEligibility: FATIGUE, HYPERTROPHY_VOLUME, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_11_5, E_BADMINTON_INJURY_PMC7205924
manualReviewRequiredWhen: always until wrist-flexion, wrist-extension, and pronation/supination subtype templates are split
```

```text
exerciseFamily: LOADED_CARRY_VARIANTS
typicalMovementPatterns: CARRY, ANTI_LATERAL_FLEXION, ANTI_ROTATION
typicalMovementSubtypes: FARMER_CARRY, SUITCASE_CARRY, RACK_CARRY, OVERHEAD_CARRY, ONE_ARM_CARRY
typicalEquipment: DUMBBELL, KETTLEBELL, BARBELL
typicalPrimaryMuscles: GRIP_FLEXORS, FOREARM_FLEXORS, TRAPEZIUS_UPPER, DEEP_CORE, OBLIQUES
typicalSecondaryMuscles: GLUTE_MAX, GLUTE_MED_MIN, SERRATUS_ANTERIOR, ROTATOR_CUFF_EXTERNAL
typicalStabilizers: ERECTOR_SPINAE, HIP_ADDUCTORS, SOLEUS, GASTROCNEMIUS
defaultMuscleContributionTemplate: GRIP_FLEXORS 0.18, FOREARM_FLEXORS 0.10, TRAPEZIUS_UPPER 0.12, DEEP_CORE 0.20, OBLIQUES 0.15, GLUTE_MED_MIN 0.08, GLUTE_MAX 0.07, ERECTOR_SPINAE 0.05, SERRATUS_ANTERIOR 0.03, ROTATOR_CUFF_EXTERNAL 0.02
typicalStrengthRole: STABILITY or ACCESSORY
typicalProgramSlot: CARRY_CORE_GRIP
typicalRedundancyGroup: LOADED_CARRY
typicalProgressMetricType: TIME_OR_DISTANCE or VOLUME_LOAD
typicalFatigueProfile: GRIP_FOREARM, ANTI_ROTATION, SYSTEMIC moderate, LOCAL_MUSCLE
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: GRIP_FOREARM, ANTI_ROTATION_STABILITY
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_10_2, A_OPENSTAX_11_5, E_CORE_BADMINTON_META_PMC11168634
manualReviewRequiredWhen: overhead carry, one-arm carry, pain, very heavy systemic loading
```

```text
exerciseFamily: ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS
typicalMovementPatterns: ANTI_ROTATION, ANTI_EXTENSION, ANTI_LATERAL_FLEXION
typicalMovementSubtypes: PALLOF_PRESS, DEAD_BUG, PLANK, SIDE_PLANK, BIRD_DOG, AB_WHEEL
typicalEquipment: CABLE, BAND, BODYWEIGHT, WHEEL
typicalPrimaryMuscles: DEEP_CORE, OBLIQUES, RECTUS_ABDOMINIS
typicalSecondaryMuscles: GLUTE_MAX, GLUTE_MED_MIN, SERRATUS_ANTERIOR
typicalStabilizers: ERECTOR_SPINAE, ROTATOR_CUFF_EXTERNAL, HIP_ADDUCTORS
defaultMuscleContributionTemplate: DEEP_CORE 0.30, OBLIQUES 0.25, RECTUS_ABDOMINIS 0.15, GLUTE_MED_MIN 0.08, GLUTE_MAX 0.07, SERRATUS_ANTERIOR 0.05, ERECTOR_SPINAE 0.05, HIP_ADDUCTORS 0.05
typicalStrengthRole: STABILITY, PREHAB, ACCESSORY
typicalProgramSlot: CORE_STABILITY
typicalRedundancyGroup: TRUNK_ANTI_MOVEMENT
typicalProgressMetricType: QUALITY_BASED or TIME_OR_DISTANCE
typicalFatigueProfile: ANTI_ROTATION, LOCAL_MUSCLE, LOW systemic
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: ANTI_ROTATION_STABILITY, TRUNK_CONTROL_SUPPORT
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: A_OPENSTAX_10_2, E_CORE_BADMINTON_META_PMC11168634
manualReviewRequiredWhen: loaded cable variant, pain, advanced ab wheel, rotational instead of anti-rotation intent
```

```text
exerciseFamily: ROTATION_POWER_VARIANTS
typicalMovementPatterns: ROTATION
typicalMovementSubtypes: MED_BALL_ROTATIONAL_THROW, CABLE_ROTATION, LANDMINE_ROTATION, VIPR_ROTATION
typicalEquipment: MEDICINE_BALL, CABLE, LANDMINE, VIPR
typicalPrimaryMuscles: OBLIQUES, DEEP_CORE, GLUTE_MAX
typicalSecondaryMuscles: LATISSIMUS_DORSI, SERRATUS_ANTERIOR, HIP_ADDUCTORS
typicalStabilizers: GLUTE_MED_MIN, ROTATOR_CUFF_EXTERNAL, TRAPEZIUS_LOWER
defaultMuscleContributionTemplate: OBLIQUES 0.25, DEEP_CORE 0.15, GLUTE_MAX 0.18, LATISSIMUS_DORSI 0.10, SERRATUS_ANTERIOR 0.08, HIP_ADDUCTORS 0.08, GLUTE_MED_MIN 0.06, ROTATOR_CUFF_EXTERNAL 0.05, TRAPEZIUS_LOWER 0.05
typicalStrengthRole: POWER
typicalProgramSlot: CORE_ROTATION_POWER
typicalRedundancyGroup: ROTATIONAL_POWER
typicalProgressMetricType: QUALITY_BASED
typicalFatigueProfile: ROTATION_POWER, NEURAL_SPEED moderate, LOCAL_MUSCLE
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: ROTATION_POWER, OVERHEAD_POWER_SUPPORT
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: E_BADMINTON_RESISTANCE_CORE_PMC12176550, E_CORE_BADMINTON_META_PMC11168634
manualReviewRequiredWhen: medicine-ball throw intensity, shoulder pain, technique quality is unknown
```

```text
exerciseFamily: PLYOMETRIC_JUMP_VARIANTS
typicalMovementPatterns: JUMP, HOP, BOUND
typicalMovementSubtypes: SQUAT_JUMP, COUNTERMOVEMENT_JUMP, BROAD_JUMP, BOX_JUMP, POGO_HOP, DEPTH_JUMP
typicalEquipment: BODYWEIGHT, BOX
typicalPrimaryMuscles: GLUTE_MAX, QUADRICEPS, GASTROCNEMIUS, SOLEUS
typicalSecondaryMuscles: HAMSTRINGS, RECTUS_FEMORIS, TIBIALIS_ANTERIOR
typicalStabilizers: GLUTE_MED_MIN, HIP_ADDUCTORS, DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: GLUTE_MAX 0.18, QUADRICEPS 0.22, RECTUS_FEMORIS 0.08, GASTROCNEMIUS 0.14, SOLEUS 0.10, HAMSTRINGS 0.08, GLUTE_MED_MIN 0.07, HIP_ADDUCTORS 0.05, TIBIALIS_ANTERIOR 0.04, DEEP_CORE 0.04
typicalStrengthRole: PLYOMETRIC or POWER
typicalProgramSlot: PLYOMETRIC_POWER
typicalRedundancyGroup: VERTICAL_JUMP_SSC
typicalProgressMetricType: QUALITY_BASED or MAX_REPS_TEST for tests
typicalFatigueProfile: ELASTIC_SSC, NEURAL_SPEED, DECELERATION for landings
typicalBadmintonTransferDirect: JUMP_LANDING if court-specific
typicalBadmintonTransferSupportive: LOWER_POWER, LANDING_SUPPORT
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: B_NSCA_ESSENTIALS_5E, E_BADMINTON_TRAINING_EFFECTS_PMC12239426, E_BADMINTON_RESISTANCE_CORE_PMC12176550
manualReviewRequiredWhen: depth jump, high volume, test-only, Achilles/knee pain
```

```text
exerciseFamily: LATERAL_BOUND_LANDING_DECELERATION_VARIANTS
typicalMovementPatterns: BOUND, HOP, DECELERATION
typicalMovementSubtypes: LATERAL_BOUND, BOUND_TO_STICK, HOP_TO_STICK, SINGLE_LEG_LANDING, DECELERATION_STEP
typicalEquipment: BODYWEIGHT, CONE_MARKER
typicalPrimaryMuscles: GLUTE_MAX, GLUTE_MED_MIN, QUADRICEPS, GASTROCNEMIUS, SOLEUS
typicalSecondaryMuscles: HAMSTRINGS, HIP_ADDUCTORS, TIBIALIS_ANTERIOR
typicalStabilizers: DEEP_CORE, OBLIQUES
defaultMuscleContributionTemplate: GLUTE_MAX 0.18, GLUTE_MED_MIN 0.14, QUADRICEPS 0.18, GASTROCNEMIUS 0.12, SOLEUS 0.10, HAMSTRINGS 0.08, HIP_ADDUCTORS 0.08, TIBIALIS_ANTERIOR 0.05, DEEP_CORE 0.04, OBLIQUES 0.03
typicalStrengthRole: PLYOMETRIC, SPEED_REACTIVE, SKILL
typicalProgramSlot: DECELERATION_LANDING
typicalRedundancyGroup: LATERAL_DECELERATION_SSC
typicalProgressMetricType: QUALITY_BASED
typicalFatigueProfile: DECELERATION, ELASTIC_SSC, NEURAL_SPEED, LOCAL_MUSCLE
typicalBadmintonTransferDirect: DECELERATION, JUMP_LANDING, LATERAL_MOVE
typicalBadmintonTransferSupportive: ANKLE_SSC_SUPPORT, KNEE_CONTROL
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: B_NSCA_DECELERATION_RESOURCE, E_BADMINTON_LUNGE_PMC7648456
manualReviewRequiredWhen: high intensity, random reaction, pain, insufficient landing quality metadata
```

```text
exerciseFamily: FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS
typicalMovementPatterns: FOOTWORK, LOCOMOTION, REACTIVE
typicalMovementSubtypes: SIX_CORNER_FOOTWORK, SPLIT_STEP_DRILL, RANDOM_BEEP_FOOTWORK, REACTION_STEP, CHANGE_OF_DIRECTION_DRILL
typicalEquipment: BODYWEIGHT, CONE_MARKER
typicalPrimaryMuscles: GLUTE_MAX, GLUTE_MED_MIN, QUADRICEPS, GASTROCNEMIUS, SOLEUS
typicalSecondaryMuscles: HAMSTRINGS, HIP_ADDUCTORS, TIBIALIS_ANTERIOR, DEEP_CORE
typicalStabilizers: OBLIQUES
defaultMuscleContributionTemplate: GLUTE_MAX 0.15, GLUTE_MED_MIN 0.12, QUADRICEPS 0.18, GASTROCNEMIUS 0.12, SOLEUS 0.10, HAMSTRINGS 0.08, HIP_ADDUCTORS 0.10, TIBIALIS_ANTERIOR 0.05, DEEP_CORE 0.06, OBLIQUES 0.04
typicalStrengthRole: SKILL, SPEED_REACTIVE, CONDITIONING
typicalProgramSlot: BADMINTON_FOOTWORK
typicalRedundancyGroup: BADMINTON_COURT_FOOTWORK
typicalProgressMetricType: QUALITY_BASED or TIME_OR_DISTANCE
typicalFatigueProfile: NEURAL_SPEED, DECELERATION, ELASTIC_SSC, BADMINTON_COURT
typicalBadmintonTransferDirect: FOOTWORK, REACTION, DECELERATION, LUNGE_REACH, JUMP_LANDING as applicable
typicalBadmintonTransferSupportive: CONDITIONING
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER, BALANCE
sourceIds: E_BWF_COACH_EDUCATION, E_BADMINTON_LUNGE_PMC7648456, B_NSCA_DECELERATION_RESOURCE
manualReviewRequiredWhen: test-only drill, random reaction, match/session versus drill, high volume
```

```text
exerciseFamily: BADMINTON_SESSION_SPORT_RECORDS
typicalMovementPatterns: FOOTWORK, SPORT_SESSION, LOCOMOTION
typicalMovementSubtypes: BADMINTON_SESSION, BADMINTON_LESSON, BADMINTON_MATCH_RECORD, PRACTICE_GAME
typicalEquipment: BODYWEIGHT, RACKET optional future tag
typicalPrimaryMuscles: NONE by default for detailed muscle contribution
typicalSecondaryMuscles: broad exposure tags only when duration/intensity supports them
typicalStabilizers: court movement stability
defaultMuscleContributionTemplate: NONE; use sport-session load and court exposure instead of detailed muscle contribution
typicalStrengthRole: SKILL or CONDITIONING
typicalProgramSlot: NOT_PROGRAM_SELECTABLE
typicalRedundancyGroup: BADMINTON_DIRECT_PLAY
typicalProgressMetricType: TIME_OR_DISTANCE or QUALITY_BASED
typicalFatigueProfile: BADMINTON_COURT, NEURAL_SPEED, DECELERATION, OVERHEAD_REPETITION, GRIP_FOREARM depending duration/intensity
typicalBadmintonTransferDirect: DIRECT_SPORT_PARTICIPATION, FOOTWORK, CONDITIONING
typicalBadmintonTransferSupportive: NONE
typicalAnalysisEligibility: FATIGUE, BADMINTON_TRANSFER; not PROGRAM_SELECTABLE
sourceIds: E_BWF_COACH_EDUCATION, E_BADMINTON_INJURY_PMC7205924, E_BADMINTON_TRAINING_EFFECTS_PMC12239426
manualReviewRequiredWhen: lesson versus match, imported aggregate, unknown duration, program generator accidentally includes it
```

```text
exerciseFamily: CARDIO_RECORDS
typicalMovementPatterns: LOCOMOTION, CONDITIONING
typicalMovementSubtypes: TREADMILL, BIKE, ROWER, SWIMMING, STAIR_CLIMBER, JUMP_ROPE
typicalEquipment: TREADMILL, BIKE, ROWER, MACHINE, BODYWEIGHT
typicalPrimaryMuscles: activity-specific or blank if unknown
typicalSecondaryMuscles: broad exposure only
typicalStabilizers: low to moderate postural stability
defaultMuscleContributionTemplate: NONE unless activity-specific source exists
typicalStrengthRole: CONDITIONING
typicalProgramSlot: CONDITIONING
typicalRedundancyGroup: GENERAL_CONDITIONING
typicalProgressMetricType: TIME_OR_DISTANCE
typicalFatigueProfile: SYSTEMIC low-to-moderate, recovery decay by duration/intensity
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: CONDITIONING if badminton goal
typicalAnalysisEligibility: FATIGUE, possibly BADMINTON_TRANSFER as GENERAL only
sourceIds: B_NSCA_ESSENTIALS_5E, B_ACSM_2026_PMID41843416
manualReviewRequiredWhen: jump rope should move to SSC/ankle group, rower strength-like load, HIIT/SIT intensity
```

```text
exerciseFamily: MOBILITY_WARMUP_RECOVERY_EXERCISES
typicalMovementPatterns: MOBILITY, PREHAB, RECOVERY
typicalMovementSubtypes: HIP_MOBILITY, SHOULDER_MOBILITY, ANKLE_MOBILITY, WARMUP_CIRCUIT, RECOVERY_WALK, LIGHT_ACTIVATION
typicalEquipment: BODYWEIGHT, BAND, FOAM_ROLLER optional future tag
typicalPrimaryMuscles: target region only if clear
typicalSecondaryMuscles: stabilizers or blank
typicalStabilizers: mobility/stability target
defaultMuscleContributionTemplate: NONE for hypertrophy-style contribution; use mobility/stability target tags
typicalStrengthRole: MOBILITY, PREHAB, RECOVERY
typicalProgramSlot: WARMUP_RECOVERY
typicalRedundancyGroup: RECOVERY_LOW_LOAD
typicalProgressMetricType: NOT_PROGRESS_TARGET or QUALITY_BASED
typicalFatigueProfile: LOW_FATIGUE_REHAB, MINIMAL recovery decay
typicalBadmintonTransferDirect: NONE
typicalBadmintonTransferSupportive: SHOULDER_DURABILITY, HIP_MOBILITY, ANKLE_MOBILITY if relevant
typicalAnalysisEligibility: RECOVERY_ONLY, BALANCE
sourceIds: A_OPENSTAX_10_2, D_SCAP_ROTATOR_CUFF_PMC2857390, C_GLUTE_REHAB_EMG_PMC3201064
manualReviewRequiredWhen: loaded mobility, high-volume activation, pain-specific rehab, unclear body region
```

## Current Repository Fit

Already supported:

- Broad movement/fatigue/progress/badminton/balance fields exist on `Exercise`.
- `planningEligibility` already separates program-selectable training exercises from sport sessions and analysis-only records.
- `MetadataSanityChecker` already checks broad consistency rules.
- `ExerciseAnalysisMapper` already creates feature vectors without exercise-name parsing.

Needs v0.3.5.0+ design work:

- `movementFamily` and `movementSubtype`
- detailed muscle tags and contribution templates
- `programSlot`
- `redundancyGroup`
- evidence source fields
- validator rules for detailed contribution and redundancy

## Deprecated / Replaced Source Notes

- `C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE` is no longer used as a plan `sourceIds` value.
- Row variants now reference `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` as the verified inverted-row/suspension-row EMG support source.
- `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` does not automatically validate all row subtypes. Barbell row, chest-supported row, one-arm dumbbell row, cable row, and machine row still require manual subtype review.

## Manual Review Priority

1. Direct badminton play and sport sessions: prevent program selection and metric mixing.
2. Heavy squat/hinge: avoid redundant high axial stress in generated sessions.
3. Shoulder prehab and overhead support: prevent broad shoulder tags from hiding rotator cuff/scapular distinctions.
4. Row/pull variants: use verified inverted-row evidence only within its scope and review other row subtypes manually.
5. Plyometric/deceleration drills: ensure load, SSC, and deceleration tags are not missing.
