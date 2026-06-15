# Analysis Algorithm Design

Phase 2.5 does not implement advanced analysis. This document fixes the future shape of the analysis engine so the app does not prematurely lock itself into badminton, strength, hypertrophy, or recovery-only logic.

## Core Rule

- `WorkoutSet.confirmed = false` means a planned set.
- `WorkoutSet.confirmed = true` means an actually performed set.
- Training load analysis uses confirmed sets by default.
- Planned sets may be used for adherence, readiness, and next-action comparison, but not as completed load.

## Raw Data

Raw data is stored without forcing one analysis lens.

- `Exercise`: taxonomy, movement pattern, muscles, equipment, sport transfer, loading profile, confidence.
- `WorkoutEntry`: date, exercise identity, category, default rest, notes, legacy or whole-exercise RPE, max reps.
- `WorkoutSet`: reps, weight, seconds, confirmed state, manual weight, set RPE, set rest override.
- `DailyMetric`: sleep and body weight by date.
- `TrainingProgram` and `TrainingProgramItem`: intended plan and prescription.

## Feature Extraction

Feature extraction converts raw rows into reusable signals.

- Confirmed set count.
- Planned set count.
- Plan completion ratio.
- Volume load from reps and weight.
- Time load from seconds.
- Exercise family exposure.
- Movement pattern exposure.
- Body region exposure.
- Primary and secondary muscle exposure.
- Rest and density estimates.
- Set-level RPE when available.
- Entry-level RPE only as legacy fallback or whole-exercise effort context.
- Max-rep markers when available.
- Sleep and body-weight context.

Features should be lens-neutral where possible. A feature may support multiple lenses.

Set-level RPE is the canonical RPE source. Future RPE/RIR-aware e1RM should use `WorkoutSet.rpe` first. `WorkoutEntry.rpe` may be used only when set RPE is missing and the analysis clearly treats it as fallback context.

Rest density should use effective rest:

```text
WorkoutSet.restSecondsOverride ?: WorkoutEntry.restSeconds
```

## Signal Generation

Signals are short interpretations of features. They should not be final advice.

- Load increased quickly.
- Confirmed work is below plan.
- One movement pattern dominates.
- Lower-body unilateral work is missing.
- Sleep context may limit readiness.
- Sport-supportive work is present.
- Direct sport exposure is low.

Signals should keep source evidence attached. For example, a signal should know which dates, exercises, and set counts produced it.

## Judgment

Judgment combines signals into a cautious assessment.

Supported lenses:

- General Fitness
- Strength
- Hypertrophy
- Badminton
- Recovery
- Balance / Injury Risk
- Plan Adherence

Judgment must remain lens-aware. The same signal can mean different things in different contexts.

Examples:

- A high volume day may support hypertrophy.
- The same day may reduce recovery margin.
- Missing unilateral work may matter more for badminton and injury-risk balance than for a basic strength block.

## Recommendation

Recommendations choose the next action.

Recommendations should be small and concrete:

- Confirm today’s completed sets.
- Reduce intensity today.
- Add one low-load support movement.
- Keep the plan unchanged.
- Move a planned session to tomorrow.
- Add recovery before increasing load.

Recommendations must not delete, overwrite, or rewrite user data automatically.

## Narrative Rendering

The narrative layer turns judgments and recommendations into Korean text.

Korean sentence rules:

- Keep sentences short.
- Aim for around 35 Korean characters per sentence.
- End one analysis block in 2 to 4 sentences.
- Use the order: state -> evidence -> action.
- Avoid long compound sentences.
- Avoid translated phrasing.
- Avoid excessive modifiers.
- Expose only one number when possible.
- Do not overstate risk.

Avoid:

- "과훈련입니다."
- "부상 위험이 높습니다."
- "회복이 완전히 부족합니다."

Prefer:

- "부하가 빠르게 늘었습니다."
- "회복 여지가 부족할 수 있습니다."
- "오늘은 강도를 낮추는 편이 좋습니다."

## Phase Boundary

The current Phase 2 analysis screen must remain simple statistics:

- confirmed set count
- total volume
- total time

Advanced algorithm work requires separate approval in a later phase.

## Phase 3.0.0 V3 Foundation

Analysis Engine V3 is prepared as infrastructure only.

Date handling:

- The analysis date comes from `AnalysisDateProvider.today()`.
- The default provider returns `LocalDate.now()`.
- Tests and debug checks can use `FixedAnalysisDateProvider`.
- The last workout date must not be treated as today.

Input separation:

- Actual records: `WorkoutEntry.date <= today` and `WorkoutSet.confirmed=true`.
- Future plans: `WorkoutEntry.date > today` and `WorkoutSet.confirmed=false`.
- Today unconfirmed sets are not completed load.
- Future plan sets are not completed load.

Common metrics:

- `CommonLoadMetrics`
- `CommonStrengthMetrics`
- `CommonTaxonomyMetrics`
- `CommonPlanProjectionMetrics`

These metrics are reusable feature extraction. They do not create final judgments, risk labels, or recommendations.

Extension points:

- `AnalysisMethod`
- `AnalysisMethodResult`
- `AnalysisMethodRegistry`
- `AnalysisSentenceBuilder`
- `AnalysisSentencePolicy`
- `AnalysisEngineV3`

All planned methods remain disabled in 3.0.0:

- `workload_recovery_v1`
- `strength_load_v1`
- `badminton_transfer_v1`
- `balance_safety_v1`
- `plan_adherence_v1`

Review templates live under `docs/analysis_method_reviews/`. Do not add thresholds or claims until the relevant method review is completed.

## Phase 3.1.1 Fatigue Metadata Boundary

Today Fatigue / Readiness is not implemented yet.

This patch only adds the metadata foundation:

- exercise taxonomy enums
- fatigue channel weights
- badminton transfer roles
- adaptive baseline groups
- recovery decay profile
- sanity checker

Future fatigue/readiness analysis must use these structured fields.

Do not classify exercises by checking exercise names such as squat, deadlift, footwork, or band. Seed fallback heuristics may remain isolated, but analyzers must consume structured metadata only.

## Phase 3.1.2 Feature Mapping Boundary

Future analyzers should consume `AnalysisExerciseFeatures` from `ExerciseAnalysisMapper`.

The feature object preserves:

- metadata confidence
- analysis eligibility
- fatigue weights and categories
- progress metric mode
- badminton transfer fields
- balance and safety tags
- completed/planned record distinction

The mapper may calculate neutral record features such as completed sets, total volume, RPE summaries, duration, and estimated 1RM candidate. It must not render advice or make readiness judgments.

## Phase 3.1.3 Today Readiness Implementation

Today Readiness is now implemented as the first V3-style user-facing analysis block.

Implemented layers:

- raw record collection from Room through `TrainingRepository`
- structured feature extraction through `ExerciseAnalysisMapper`
- daily load aggregation
- residual fatigue decay
- statistical baseline calculation
- adaptive baseline calculation
- pressure calculation
- recovery signal interpretation
- performance drop detection
- pain gate evaluation
- final readiness decision
- short sentence rendering
- detail section rendering

Boundary:

- planned workouts are excluded from today's fatigue.
- completed load means `WorkoutSet.confirmed=true`.
- future plan projection is still separate from Today Readiness.
- no injury prediction or medical diagnosis is rendered.
- no exercise-name string parsing is allowed in the analyzer path.

Readiness statuses:

- `READY`: most pressure is normal or low.
- `CAUTION`: one or more signals suggest lower intensity.
- `FATIGUED`: major fatigue channels are high or recovery/performance signals push the result upward.
- `LIMITED`: pain gate or strong combined signals require narrower exercise choices.

Adaptive baseline rule:

```text
high load alone does not increase tolerance
high load + stable recovery + no pain + no performance drop can increase tolerance slightly
```

The adaptive baseline is deterministic in 3.1.3. It is recalculated from records and available signals rather than saved as a persistent cache.

Future tuning:

- validate constants against real training logs.
- connect richer condition and pain fields if the record UI adds them.
- keep sentence blocks short and action-focused.
- keep forbidden deterministic risk/diagnosis wording out of user-facing text.

## Phase 3.2.0 Performance Trend Analysis

3.2.0 adds summary trend charts. It does not replace 3.1 readiness.

First-screen indicators:

- Strength Performance
- Badminton Training Volume
- Fatigue Composite

First-screen rule:

```text
3 charts
3 total lines
one short trend sentence
no large score emphasis
```

Common normalization:

- calculate each raw component first.
- normalize each component against personal baseline.
- compose only standardized component scores.
- fallback to neutral 100 with low confidence when data is insufficient.

Strength:

```text
StrengthPerformanceIndex =
0.50 * StrengthIntensityIndex
+ 0.40 * StrengthVolumeIndex
+ 0.10 * StrengthEfficiencyIndex
```

Badminton:

```text
BadmintonTrainingIndex =
0.60 * CourtVolumeIndex
+ 0.25 * FootworkReactiveIndex
+ 0.15 * BadmintonSupportIndex
```

This is training volume, not skill.

Fatigue:

```text
FatigueCompositeIndex =
0.60 * AverageStandardizedFatigue
+ 0.25 * MaxCategoryFatigue
+ 0.15 * RecoveryPerformancePenaltyScore
```

This is a chart compression of 3.1-style fatigue signals.
It must not raw-sum kg, reps, and minutes.

Detail structure:

- Strength explanation
- Badminton training explanation
- Fatigue explanation
- Relationship analysis

Sections 1 to 3 have one chart slot each.
Chart type changes must reset incompatible metric selections.

Relationship analysis:

- use weekly scatter points.
- require enough points before interpreting correlation.
- use trend wording only.
- do not claim causality.
