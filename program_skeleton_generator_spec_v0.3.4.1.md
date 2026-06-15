# Program Skeleton Generator Spec v0.3.4.1

## 1. Purpose

The generator creates an editable 4-week training program skeleton from user constraints, existing exercise metadata, and recent training history.

It is not a final coaching engine. It produces a safe starting structure that the user can review, edit, save, and apply to calendar dates.

## 2. User Inputs

- Program name
- Program goal:
  - Badminton support weights
  - Strength
  - Bodybuilding
  - Functional / conditioning
- Training days per week: 2, 3, 4, 5
- Session time: 30, 45, 60, 75 minutes
- Available equipment:
  - Barbell, dumbbell, cable, machine, kettlebell, landmine, band, bodyweight
- Excluded exercise text:
  - user-disliked movement names
  - pain / avoid notes
  - excluded terms from current program generation request
- Badminton transfer ratio:
  - Low = 0.25
  - Normal = 0.40
  - High = 0.55
  - Very high = 0.70
- Sport:strength ratio:
  - Auto
  - 90/10, 70/30, 50/50, 30/70, 0/100
- Periodization type:
  - Auto
  - STEP_DELOAD
  - BADMINTON_WAVE
  - DAILY_UNDULATING
  - LINEAR_STRENGTH

## 3. Recovered Rules From README / Handoff

Recovered from `README(21).md`, `TrainingTrackPlanner_handoff(22) (1).txt`, `csv_format_plan(21).md`, `metadata_taxonomy(4).md`, and current `outputs/phase2_work_readme.md` / `outputs/phase2_handoff.md`:

- Program creation previously supported direct creation and condition-based basic recommendation.
- Sport:strength ratio choices were `90/10`, `70/30`, `50/50`, `30/70`, and `0/100`.
- Equipment filtering used metadata first, then structured fallback.
- Avoided exercises were selected by exercise search in the old app; v0.3.4.1 stores a conservative text-based avoid input and filters by exercise name only for user exclusion, not analysis.
- Program results were saved to `training_programs` and `training_program_items`.
- Program application creates `WorkoutEntry` and `WorkoutSet` rows with `confirmed=false`.
- Recommended starting weights used recent history, e1RM/family information, and conservative fallback.
- Metadata must be preferred over string parsing for analysis/recommendation decisions.
- Completed workout records must never be deleted by program editing or schedule overwrite.

## 4. Metadata Requirements

The generator uses current `Exercise` metadata fields:

- movementPattern
- movementCategory
- primaryMuscles / secondaryMuscles
- equipment / equipmentTags
- compoundType
- forceType
- plane
- laterality
- axialLoadLevel
- trainingRole
- badmintonTransferRoles
- fatigueCategories
- adaptiveBaselineGroups
- recoveryDecayProfile
- badmintonTransferStrength
- courtMovementTypes
- badmintonSkillTargets
- analysisEligibility
- metadataConfidence
- fatigue weight fields

If metadata is missing, the generator lowers confidence and uses conservative defaults.

Exercise name matching is allowed only for user-entered exclusion text and display/search. It is not used for scoring transfer, fatigue, movement pattern, or analysis semantics.

## 5. Periodization Engine

Each program uses a `periodizationType`.

Week plans contain:

- weekIndex
- weekType
- volumeMultiplier
- intensityMultiplier
- heavyExposureLimit
- lowerBodyFatigueLimit
- axialLoadLimit
- plyometricLimit
- deloadFlag

STEP_DELOAD:

- Week 1: volume 0.85, intensity 0.85, heavy exposure 1
- Week 2: volume 1.00, intensity 0.90, heavy exposure 2
- Week 3: volume 1.15, intensity 0.95, heavy exposure 2
- Week 4: volume 0.65, intensity 0.75, heavy exposure 1, deload

BADMINTON_WAVE:

- Week 1: medium
- Week 2: high
- Week 3: low
- Week 4: medium-high transfer / power emphasis

DAILY_UNDULATING:

- Splits week days into heavy, volume, power, and recovery emphasis.

LINEAR_STRENGTH:

- Gradually lowers volume and raises intensity.

Auto selection:

- Badminton ratio high or very high -> BADMINTON_WAVE
- Strength goal -> LINEAR_STRENGTH
- 4+ training days -> DAILY_UNDULATING unless badminton priority is high
- otherwise STEP_DELOAD

## 6. Badminton Transfer Ratio Dropdown

The dropdown is required and affects:

- number of badminton-transfer slots
- exercise score
- transfer axis priority
- fatigue cap
- periodization auto choice
- sport:strength ratio default

Ratio meaning:

It is the share of program resources assigned to high-transfer movement patterns, not a request to fill the plan with high-impact plyometrics.

Transfer fatigue buckets:

- High fatigue:
  - jump, hop-to-stick, heavy lunge, lower power
- Medium fatigue:
  - single-leg RDL, reverse lunge, landmine, rotational core
- Low fatigue:
  - band external rotation, dead bug, ankle stability, scapular care, light grip / forearm

High transfer ratio increases total transfer work, but high-fatigue transfer work remains capped.

## 7. Weekly Volume / Intensity / Heavy Frequency

The generator builds a weekly budget before selecting exercises:

- weekLoadMultiplier
- weekVolumeTarget
- weekIntensityTarget
- heavyExposureLimit
- axialLoadLimit
- plyometricLimit
- badmintonTransferRatio
- deloadFlag

Heavy exposure candidate:

- targetRpe >= 8
- or reps <= 5 on compound lifts
- or intensity-like prescription from week plan

Badminton support safety limits:

- lower heavy exposure <= 2 per week
- hinge heavy exposure <= 1 per week
- high axial days <= 2 per week
- high-fatigue plyometric days <= 1-2 per week

## 8. Exercise Selection Score

Score:

```text
baseGoalScore
+ badmintonTransferRatio * badmintonTransferScore
+ equipmentMatchScore
+ patternNeedScore
+ userPreferenceScore
- fatigueCostPenalty
- redundancyPenalty
- contraindicationPenalty
```

Hard filters:

- equipment must match selected equipment, unless equipment metadata is empty and exercise is bodyweight-like
- excluded exercise text must not match display name
- metadata confidence `NEEDS_REVIEW` is allowed but penalized

Soft balancing:

- avoid repeated movement pattern on the same day
- cap high axial / high deceleration / elastic SSC load
- include upper push, upper pull, lower push, hinge, core / stability across the week

## 9. Fatigue Cost

Base fatigue cost uses metadata:

- systemicLoadWeight
- neuralHeavyWeight
- neuralSpeedWeight
- decelerationWeight
- elasticSscWeight
- axialLoadLevel
- movementCategory
- recoveryDecayProfile

Cost scale:

- 1 = low
- 2 = moderate
- 3 = normal compound
- 4 = high lower / high deceleration
- 5 = very high axial or heavy lower

Daily and weekly caps are derived from session time, training days, badminton ratio, and week multipliers.

## 10. High-Fatigue Exercise Limits

The generator limits:

- high axial lower-body work
- heavy hinge exposure
- high-fatigue plyometric / landing work
- same-day heavy lower + plyometric collision
- deload week volume and intensity

## 11. Weight Autofill From History

Weight source priority:

1. Same exercise recent confirmed sets
2. Same exercise recent best set
3. Similar metadata group:
   - movement pattern
   - equipment
   - primary muscle
4. e1RM-eligible estimate
5. Empty / conservative default

Output source labels:

- DIRECT_HISTORY_HIGH
- DIRECT_HISTORY_MEDIUM
- SIMILAR_EXERCISE_LOW
- ESTIMATED_1RM_MEDIUM
- EMPTY_NEEDS_MANUAL_INPUT

v0.3.4.1 stores the source label in `TrainingProgramItem.prescription`.

Rules:

- manual user edits overwrite generated values.
- dumbbell and one-arm ambiguity is treated conservatively.
- machine and cable carry lower confidence.
- bodyweight and band exercises may remain 0 kg.

## 12. Program Application And Schedule Overwrite

Applying a program calculates:

- startDate
- endDate = startDate + durationDays - 1

Conflict check range is the entire program date range.

Overwrite deletes only planned schedule rows:

- `WorkoutSet.confirmed=false`
- entries whose sets are all unconfirmed

Completed records are preserved:

- confirmed sets are not deleted
- entries with any confirmed set are not deleted
- notes, RPE, and daily metrics are not deleted

Overwrite and apply must run in one DB transaction.

## 13. UI Flow

Plan tab flow:

1. Program list
2. Create or edit program
3. Fill generation inputs
4. Tap `자동으로 골자 만들기`
5. Preview generated weeks/days/items
6. User edits name / items / set / reps / weight / rest / RPE notes
7. Save
8. Select start date
9. Conflict check
10. Cancel or overwrite planned schedule
11. Apply program

## 14. DB / Migration

Additive migration is required for program-level metadata:

- goal
- weeklyTrainingDays
- sessionMinutes
- availableEquipment
- excludedExerciseText
- badmintonTransferRatio
- sportStrengthRatio
- periodizationType
- updatedAt

No completed records are migrated, deleted, or rewritten.

## 15. Tests

Required coverage:

- program deletion removes program items but not workout records.
- generated program has 4 weeks and respects weekly day count.
- badminton ratio changes transfer exercise slot selection.
- periodization applies deload/wave multipliers.
- generated sets are saved to `training_program_items`.
- apply overwrite deletes only planned entries and preserves confirmed records.
- generated schedule creates `confirmed=false` sets.
- history-based weight autofill returns direct history when available.
