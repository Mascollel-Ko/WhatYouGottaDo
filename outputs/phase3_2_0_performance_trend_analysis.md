# WhatYouGottaTrain 3.2.0 Performance Trend Analysis

Date: 2026-06-15

## Scope

Patch 3.2.0 adds the Analysis tab section:

```text
성과 추세 분석
```

This is a summary dashboard plus detail indicators. It is not a precision diagnosis screen.

First-screen dashboard lines:

1. 근력운동 퍼포먼스
2. 배드민턴 훈련량
3. 피로도 종합지수

The first screen shows only three line charts and one short trend sentence.
It does not show large numeric scores, recommendation lists, or decomposed sub-lines.

## Preserved Rules

- `WorkoutSet.confirmed=false` is planned work.
- `WorkoutSet.confirmed=true` is completed work.
- Trend indicators use completed records only.
- Planned workouts are excluded.
- Exercise classification uses structured metadata through `ExerciseAnalysisMapper`.
- Search may use text matching, but analysis logic must not classify by exercise-name parsing.
- 3.1 Today Readiness remains in place.
- 3.2 fatigue composite is a chart summary of 3.1-style fatigue signals, not a replacement decision engine.

## Added Source Files

- `analysis/trends/PerformanceTrendModels.kt`
- `analysis/trends/PerformanceTrendConstants.kt`
- `analysis/trends/TrendMath.kt`
- `analysis/trends/WeeklyAnalysisAggregator.kt`
- `analysis/trends/StrengthPerformanceIndexCalculator.kt`
- `analysis/trends/BadmintonTrainingLoadIndexCalculator.kt`
- `analysis/trends/FatigueCompositeIndexCalculator.kt`
- `analysis/trends/TrendForecastRangeCalculator.kt`
- `analysis/trends/DetailChartSelector.kt`
- `analysis/trends/ScatterRelationshipAnalyzer.kt`
- `analysis/trends/PerformanceTrendSentenceBuilder.kt`
- `analysis/trends/PerformanceChartSpecBuilder.kt`
- `analysis/trends/PerformanceTrendEngine.kt`
- `app/src/test/java/com/training/trackplanner/analysis/trends/PerformanceTrendEngineTest.kt`

## Modified Source Files

- `AnalysisScreen.kt`
- `TrainingViewModel.kt`
- `TrainingRepository.kt`

## First Screen

`AnalysisScreen` now shows:

- 3.1 Today Readiness card
- 3.2 Performance Trend card
- existing simple confirmed-set stats

The 3.2 card contains:

- title: `성과 추세 분석`
- one short trend sentence
- three dashboard chart specs
- detail toggle

Dashboard chart rules:

- each chart has `ChartType.LINE`
- each chart has `visibleLineCount == 1`
- each chart has `emphasizeValue == false`
- forecast range is a shaded band when available

## Common Standardization

All composite indices use `TrendMath`.

Functions:

- `safeDivide`
- `clamp`
- `higherIsBetterScore`
- `lowerIsBetterScore`
- `percentileScore`
- `zScoreBasedScore`
- `weightedMean`
- rolling baseline helper

Caps:

- normal standardized score: `50..160`
- fatigue score: `50..170`

Baseline priority:

- rolling median over available weekly history
- fallback to available nonzero values
- fallback score `100` with low confidence when baseline is missing

## Weekly Aggregation

`WeeklyAnalysisAggregator` uses Monday as week start.

Rules:

- 1 point = 1 week
- default visible window = 8 weeks
- extended window = 12 weeks when enough history exists
- completed records only
- planned sets excluded

## Strength Performance Index

Formula:

```text
StrengthPerformanceIndex =
0.50 * StrengthIntensityIndex
+ 0.40 * StrengthVolumeIndex
+ 0.10 * StrengthEfficiencyIndex
```

### StrengthIntensityIndex

Uses e1RM:

```text
e1RM = weight * (1 + reps / 30)
```

Included sets:

- confirmed
- `estimated1RmEligible=true`
- reps `1..12`
- weight > 0
- not prehab / mobility / recovery

Exercise score:

```text
100 * weeklyExerciseIntensity / exerciseBaseline
```

Exercise role weights are in `PerformanceTrendConstants`.

### StrengthVolumeIndex

Raw volume is calculated per completed strength-like set.

Weighted set:

```text
weight * reps
```

Bodyweight set:

```text
bodyweightProxy * reps
```

Final:

```text
StrengthVolumeIndex =
0.75 * volumeScore
+ 0.25 * effectiveSetScore
```

### StrengthEfficiencyIndex

If RPE is unavailable:

```text
StrengthEfficiencyIndex = 100
confidence = LOW
```

If RPE is available:

```text
efficiencyRaw = weeklyStrengthWork / avgHardSetRpe
```

Same-load RPE comparison is used as a secondary score when comparable records exist.

## Badminton Training Index

This is a badminton-related training volume indicator.
It is not a badminton skill or performance score.

Formula:

```text
BadmintonTrainingIndex =
0.60 * CourtVolumeIndex
+ 0.25 * FootworkReactiveIndex
+ 0.15 * BadmintonSupportIndex
```

### CourtVolumeIndex

Uses direct badminton transfer records with court movement metadata.

```text
courtVolumeRaw =
durationMinutes * sessionCompletionFactor * sessionIntensityFactor
```

### FootworkReactiveIndex

Uses structured court movement and transfer roles:

- split step
- first step
- lateral move
- reaction random
- deceleration
- jump landing
- footwork / reaction / deceleration roles

No exercise-name parsing is used.

### BadmintonSupportIndex

Uses:

- `badmintonTransferStrength`
- fatigue categories such as deceleration, elastic SSC, rotation, overhead, grip

Support weights and corrections are isolated in constants.

## Fatigue Composite Index

The fatigue composite is a chart compression of 3.1-style fatigue signals.

It does not raw-sum loads.
It standardizes category pressure, percentile, and z-score.

Category score:

```text
0.50 * pressureScore
+ 0.35 * percentileScore
+ 0.15 * zScoreBasedScore
```

If z-score is invalid:

```text
0.60 * pressureScore
+ 0.40 * percentileScore
```

Group scores:

- systemic
- strength
- badminton
- local body part
- recovery/performance/pain penalty

Final:

```text
FatigueCompositeIndex =
0.60 * AverageStandardizedFatigue
+ 0.25 * MaxCategoryFatigue
+ 0.15 * RecoveryPerformancePenaltyScore
```

Higher means higher fatigue burden.

## Detail Sections

The detail area has four sections:

1. 근력운동 해설
2. 배드민턴 훈련 해설
3. 피로도 해설
4. 관계 분석

Sections 1 to 3 have one chart slot each.

Available modes:

- trend
- composition
- contribution
- ranking

The relationship section uses scatter plot.

## Chart Selection Rules

`DetailChartSelector` enforces:

- line + line is allowed.
- bar + bar is allowed.
- horizontal bar + horizontal bar is allowed.
- line + pie is not allowed.
- line + bar is not allowed.
- scatter is only used in the relationship section.
- changing from trend mode to another mode reduces selection to one metric.

## Scatter Analysis

`ScatterRelationshipAnalyzer` uses weekly points.

Default relation:

```text
X = BadmintonTrainingIndex
Y = FatigueCompositeIndex
```

Pearson correlation requires:

- at least 10 points
- nonzero x variance
- nonzero y variance

Interpretation uses trend language only.
It does not make causal statements.

Allowed wording:

- "경향이 있습니다."
- "함께 나타나는 편입니다."
- "기록이 부족해 판단이 제한적입니다."

Forbidden wording:

- cause
- certainty
- diagnosis
- injury prediction

## Tests Added

`PerformanceTrendEngineTest` checks:

- dashboard has exactly three line chart specs
- each dashboard chart has one line
- dashboard values are not emphasized as scores
- strength formula uses `0.50 / 0.40 / 0.10`
- planned sets are excluded
- badminton trend uses metadata and avoids skill-gain wording
- fatigue composite uses pressure, percentile, z-score, and recovery penalty
- local body part fatigue is not buried in average
- selector rejects mixed chart types
- scatter hides correlation when point count is insufficient
- trend package does not classify by exercise-name string parsing

## Verification Status

Gradle execution is currently blocked in this environment:

- sandboxed Gradle cannot download the Gradle distribution.
- unrestricted Gradle execution was previously blocked by the environment usage limit.

Required next commands when execution is available:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat --no-daemon --no-problems-report testDebugUnitTest
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

## Remaining TODO

- Run unit tests and `assembleDebug`.
- Device QA the new Analysis tab section.
- Tune constants after real records are reviewed.
- Consider adding UI test dependencies later for chart-slot assertions.
