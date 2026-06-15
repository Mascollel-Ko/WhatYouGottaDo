# WhatYouGottaTrain 3.1.3 Today Readiness / Fatigue Engine

Date: 2026-06-15

## Scope

Patch 3.1.3 implements the first user-facing Today Readiness / Fatigue analysis.

The engine answers:

- What is today's readiness status?
- Why is that status shown?
- What training modes are reasonable today?
- What training directions should be adjusted today?
- Which fatigue channels and body parts explain the result?
- How much confidence should the app have in the result?

It does not create injury prediction, medical diagnosis, or overtraining diagnosis.

## Preserved Rules

- `WorkoutSet.confirmed=false` remains a planned set.
- `WorkoutSet.confirmed=true` remains an actual performed set.
- Planned workouts are not used in today's fatigue calculation.
- Completed records only use confirmed sets.
- Exercise classification uses structured metadata through `ExerciseAnalysisMapper`.
- Search may use text matching, but analysis must not classify by exercise-name string parsing.
- Existing records, calendar behavior, record editing, exercise search, and recommendations are not deleted or rewritten.

## Added Source Files

- `analysis/readiness/TodayReadinessModels.kt`
- `analysis/readiness/TodayReadinessConstants.kt`
- `analysis/readiness/DailyAnalysisLoadAggregator.kt`
- `analysis/readiness/ResidualFatigueCalculator.kt`
- `analysis/readiness/StatisticalBaselineCalculator.kt`
- `analysis/readiness/AdaptiveBaselineUpdateEvaluator.kt`
- `analysis/readiness/AdaptiveBaselineCalculator.kt`
- `analysis/readiness/FatiguePressureCalculator.kt`
- `analysis/readiness/RecoverySignalInterpreter.kt`
- `analysis/readiness/PerformanceDropDetector.kt`
- `analysis/readiness/PainGateEvaluator.kt`
- `analysis/readiness/TodayReadinessDecisionEngine.kt`
- `analysis/readiness/TodayReadinessSentenceBuilder.kt`
- `analysis/readiness/FatigueDetailSectionBuilder.kt`
- `analysis/readiness/TodayReadinessEngine.kt`
- `analysis/features/AnalysisFeatureExtractor.kt`
- `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayReadinessEngineTest.kt`

## Data Flow

```text
WorkoutEntry + WorkoutSet
-> ExerciseAnalysisMapper / AnalysisFeatureExtractor
-> DailyAnalysisLoadAggregator
-> ResidualFatigueCalculator
-> StatisticalBaselineCalculator
-> AdaptiveBaselineCalculator
-> FatiguePressureCalculator
-> RecoverySignalInterpreter
-> PerformanceDropDetector
-> PainGateEvaluator
-> TodayReadinessDecisionEngine
-> TodayReadinessSentenceBuilder
-> FatigueDetailSectionBuilder
-> TodayReadinessSummary
-> AnalysisScreen
```

## Output Model

`TodayReadinessSummary` contains:

- `status`: `READY`, `CAUTION`, `FATIGUED`, `LIMITED`
- `headline`
- `shortReason`
- `primaryReasons`
- `recommendedModes`
- `restrictedModes`
- `confidence`: `LOW`, `MEDIUM_LOW`, `MEDIUM`, `HIGH`
- `detailSections`
- `adaptiveBaselineNotes`
- `generatedAt`

Detail section types:

- `SYSTEMIC`
- `NEURAL_HEAVY`
- `NEURAL_SPEED`
- `LOCAL_BODY_PART`
- `BADMINTON_COURT`
- `RECOVERY`
- `PERFORMANCE`
- `PAIN`
- `ADAPTIVE_BASELINE`

## Fatigue Channels

The engine calculates:

- `SYSTEMIC`
- `NEURAL_HEAVY`
- `NEURAL_SPEED`
- `LOCAL_MUSCLE`
- `DECELERATION`
- `ELASTIC_SSC`
- `ROTATION_POWER`
- `ANTI_ROTATION`
- `OVERHEAD_REPETITION`
- `GRIP_FOREARM`
- `BADMINTON_COURT`

Each channel uses the weight fields from exercise metadata:

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

## Base Dose

The load aggregator uses the most stable available record signal:

1. Weighted sets: `weightKg * reps` from confirmed sets.
2. Bodyweight/repetition sets: `totalReps * bodyweightProxy`.
3. Timed drills: `durationMinutes * drillIntensityProxy`.

Only confirmed sets are included.

RPE is applied as a modifier when available:

- `<= 6`: `0.85`
- `7`: `0.95`
- `8`: `1.00`
- `9`: `1.10`
- `10`: `1.20`

Speed/reactive categories use a smaller RPE influence than heavy/systemic categories.

## Badminton Court Load

`BADMINTON_COURT` combines:

- neural speed load
- deceleration load
- elastic SSC load
- overhead repetition load
- grip load
- transfer bonus from `badmintonTransferStrength` and `courtMovementTypes`

The transfer constants are isolated in `TodayReadinessConstants`.

## Local Body Part Fatigue

The engine maps `primaryMuscles` and `secondaryMuscles` metadata into body-part buckets:

- `quads`
- `hamstrings`
- `glutes`
- `calves_achilles`
- `erectors_low_back`
- `chest`
- `lats_upper_back`
- `shoulders`
- `rotator_cuff`
- `elbow_flexors`
- `elbow_extensors`
- `forearm_grip`
- `core_abs_obliques`
- `hips_adductors_abductors`

Primary muscles receive a stronger local load share than secondary muscles.

Extra routing:

- high/moderate axial load adds some low-back/erector load.
- deceleration and elastic work add lower-body landing load.
- overhead load adds shoulder and rotator cuff load.
- grip load adds forearm/grip load.

## Residual Fatigue

`ResidualFatigueCalculator` converts historical daily load into today's remaining load.

Decay profiles:

- `MINIMAL`: D0 1.00, D1 0.15
- `SHORT`: D0 1.00, D1 0.50, D2 0.20
- `MEDIUM`: D0 1.00, D1 0.65, D2 0.40, D3 0.20
- `LONG`: D0 1.00, D1 0.80, D2 0.55, D3 0.35, D4 0.20
- `VERY_LONG`: D0 1.00, D1 0.90, D2 0.70, D3 0.50, D4 0.35, D5 0.20

## Statistical Baseline

`StatisticalBaselineCalculator` calculates:

- rolling mean
- rolling standard deviation
- z-score
- percentile
- EWMA baseline
- pressure candidate
- trend
- confidence

Confidence by available span:

- `0..13` days: `LOW`
- `14..41` days: `MEDIUM_LOW`
- `42..83` days: `MEDIUM`
- `84+` days: `HIGH`

Zero or tiny standard deviation is handled without crashing.

## Adaptive Baseline

`AdaptiveBaselineCalculator` creates category, baseline-group, and body-part tolerances.

The rule is:

```text
high load alone != adaptation
high load + stable recovery + no pain + no performance drop = possible adaptation
```

Successful exposure can raise tolerance slightly.
Failed exposure can block or lower tolerance slightly.

Caps:

- maximum single-run upward adjustment: `5%`
- maximum single-run downward adjustment: `5%`

The baseline is deterministic from records and signals. There is no persistent cache in this patch.

## Recovery / Performance / Pain

Recovery:

- currently uses available `DailyMetric.sleepHours`.
- missing recovery fields reduce confidence rather than forcing new inputs.

Performance:

- compares records by `exerciseId`.
- checks same-load RPE rise, same-load reps drop, and estimated 1RM drop when possible.
- does not over-interpret single records.

Pain:

- handled as a gate, not an average score.
- current app has no normal pain input UI, but the evaluator supports synthetic/test inputs.
- if future UI adds pain fields, connect them here instead of averaging them into load.

## UI

`AnalysisScreen` now shows a Today Readiness card above the existing simple stats.

The card shows:

- status
- confidence
- short summary
- primary reasons
- recommended modes
- restricted/adjusted modes
- expandable detail sections

Existing simple stats remain:

- confirmed set count
- total volume
- total time

## Sentence Policy

The readiness sentence builder avoids these outputs:

- injury-risk prediction phrasing
- medical diagnosis phrasing
- overtraining diagnosis phrasing
- deterministic danger phrasing

Recommended wording stays action-oriented:

- "burden is higher than usual"
- "reduce intensity today"
- "choose lower-load work"
- "more records will stabilize the judgment"

## Tests Added

`TodayReadinessEngineTest` covers:

- confirmed-only load inclusion
- planned workout exclusion
- metadata-based analysis not exercise-name parsing
- heavy/speed/deceleration/elastic/court load separation
- local body part load routing
- recovery decay profile differences
- rolling mean/std/z-score/percentile/EWMA/pressure
- zero standard deviation fallback
- adaptive baseline increase only after successful exposure
- adaptive tolerance changes pressure
- synthetic `READY`, `CAUTION/FATIGUED`, `FATIGUED`, `LIMITED` scenarios
- prohibited sentence expression check
- required detail section coverage

## Verification Status

Attempted command:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat --no-daemon --no-problems-report testDebugUnitTest
```

First unrestricted test run reached Kotlin compilation and exposed a nullable enum comparison issue.
That source issue was fixed.

Further unrestricted Gradle runs were blocked by the environment usage limit. A sandboxed retry could not download the Gradle distribution due network restrictions.

The final `testDebugUnitTest` and `assembleDebug` commands still need to be rerun in Android Studio or after unrestricted Gradle execution is available.

## Remaining TODO

- Run `testDebugUnitTest`.
- Run `assembleDebug`.
- Manual QA on device:
  - open Analysis tab.
  - confirm Today Readiness card appears.
  - confirm existing stats still update from confirmed sets only.
  - confirm planned sets do not change today readiness load.
- Connect future pain/condition inputs to `PainGateEvaluator` and `RecoverySignalInterpreter`.
- Tune constants after real-world logs and method review.

## Post v0.3.4.0 UI Compactness Note

The Analysis tab Today Readiness card collapsed state was made shorter.

Changed:

- card padding and vertical spacing were reduced.
- the readiness status label uses a smaller title size.
- collapsed primary reasons show at most 2 items.
- collapsed recommendation and restriction modes are summarized on one line each.

Unchanged:

- Today Readiness engine calculations.
- expanded detail sections.
- confirmed-only analysis behavior.
