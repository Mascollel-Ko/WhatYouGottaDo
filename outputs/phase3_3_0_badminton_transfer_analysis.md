# Patch 3.3.0 Badminton Transfer Analysis

## Goal

Patch 3.3.0 adds the Analysis tab section `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍`.

The first card intentionally shows only one recommendation sentence. Detailed ratios, charts, and top exercises are hidden behind `?먯꽭??蹂닿린`.

## Added Files

- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngine.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferModels.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferConstants.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferMetadataMapper.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferScoreCalculator.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferRecommendationBuilder.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferInsightBuilder.kt`
- `app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferChartDataBuilder.kt`
- `app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngineTest.kt`

## Modified Files

- `app/src/main/java/com/training/trackplanner/AnalysisScreen.kt`
- `app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`

## Data Meaning

- Only `WorkoutSet.confirmed=true` sets are included.
- `WorkoutSet.confirmed=false` remains a planned set and is excluded.
- No DB schema change was made.
- Exercise classification is based on structured metadata through `ExerciseAnalysisMapper`.
- Exercise name string parsing is not used.

## Transfer Type Mapping

Existing metadata is reused:

- `badmintonTransferStrength = DIRECT` maps to `direct`.
- `SUPPORTIVE` maps to `supportive`.
- `GENERAL` maps to `general_strength`.
- badminton-related metadata without a stronger type maps to `low`.
- missing metadata maps to `none`.

Weights:

- direct: `1.0`
- supportive: `0.6`
- general_strength: `0.25`
- low: `0.1`
- none: `0.0`

## Transfer Axis Mapping

Axes are derived from structured fields:

- `deceleration_landing`: fatigue category, court movement, transfer role, skill target, and force type.
- `unilateral_stability`: laterality, balance tags, lunge reach tags.
- `lateral_movement`: court movement, footwork roles, locomotion/footwork patterns, frontal plane.
- `rotation_control`: rotation and anti-rotation patterns, fatigue categories, skill targets, balance tags.
- `lower_body_strength`: squat, hinge, lunge, lower-body baseline groups.
- `racket_support`: overhead, shoulder care, grip, forearm, and related muscle tags.
- `aerobic_footwork`: conditioning, skill drill, footwork speed, court repetition metadata.
- `low_fatigue_control`: prehab, stability, mobility, recovery, low-fatigue rehab metadata.

When an exercise maps to multiple axes, its transfer stimulus is split equally across those axes.

## Calculation

Windows:

- recent window: 7 days
- baseline window: 28 days

Load fallback order:

1. reps-based completed set load with intensity and RPE factors
2. time-based drill load
3. completed set count

Transfer stimulus:

`transferStimulus = exerciseLoad * transferWeight`

Key outputs:

- `totalTransferStimulus7d`
- `totalTransferStimulus28d`
- `transferRatio7dTo28dAverage`
- `axisShare7d`
- `axisShare28d`
- `transferTypeShare7d`
- `topTransferExercises7d`
- `recommendedAxis`
- `recommendationSentence`
- `cautionLevel`
- `detailInsightText`

## UI

First card:

- title: `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍`
- body: exactly one recommendation sentence
- button: `?먯꽭??蹂닿린`

Example sentence:

- `?ㅻ뒛? 媛먯냽쨌李⑹? ?쒖뼱 ?대룞??異붿쿇?쒕┰?덈떎.`

Details:

- `?꾩씠異?鍮꾩쨷`
- `?꾩씠?좏삎 鍮꾩쨷`
- `理쒓렐 7??vs 28??
- `?대룞蹂??꾩씠 ?먭레 Top 5`

Only one detail chart area is shown, and the selector changes the displayed data.

## Fatigue Connection

The module receives the 3.1 Today Readiness result.

- `LIMITED` or very high detail level: recommends recovery first.
- `FATIGUED` or high detail level: recommends low-fatigue supplementary work.
- normal fatigue: recommends the lowest-priority missing transfer axis.

High-fatigue days do not recommend high-cost transfer work first.

## Phrase Policy

Used terms:

- `?꾩씠 鍮꾩쨷`
- `?꾩씠 ?먭레`
- `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍`

Avoided:

- match-performance claims
- causal performance improvement claims
- large numeric scores on the first card

## Verification

- `testDebugUnitTest`: passed
- Static check: new badminton analysis main package has no exercise-name `.contains` classification.