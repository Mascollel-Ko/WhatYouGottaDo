# B-Coach Gate E — Other-exercise Shadow coverage and squat audit

Date: 2026-11-12
Source branch: `test/b-coach-squat-shadow-gate-d-20261112`
Status: **EXPERIMENTAL, READ-ONLY, NOT PRODUCTION APPROVED**

## Gate D device-report audit

The user's device comparison showed a B-plan squat occurrence (3 sets × 6 reps in each of four weeks, user to calibrate kg), plus four independent *single-session* benchmark actions based on the last confirmed set:
- HOLD 140 kg × 5;
- LOAD_UP_ONE_STEP 143 kg × 5;
- REPS_UP_ONE 140 kg × 6;
- LOAD_UP_ONE_STEP_REPS_DOWN_ONE 143 kg × 4.

Six scenario-policy results split three votes for HOLD and three for LOAD_UP_ONE_STEP. There is **no unique robust winner** and neither experimental outcome is an approved prescription.

**Critical differences from v1.2 architecture:**

1. Gate D's `CENTRAL_80/90` denote *the anchor RPE→RIR discrete-mixture quantiles*, **not** the canonical capacity posterior's 10/50/90 or 5/50/95 percentiles. These uncertainty sources are not interchangeable.
2. The existing squat `capacity` posterior is read and checked for tension, but absolute capacity uncertainty is deliberately canceled in the current **relative** RIR projection. This is a valid small-slice experiment but does not implement v1.2's posterior-based `ProgressionFit`.
3. “Target RIR mass 90%” is conditional mixture mass under the reported RPE and assumed curve. It is **not** a calibrated chance that a future workout will succeed.
4. The first anchor working set alone is modeled. In a 3-set candidate, the two follow-up sets have no modeled fatigue or completion probability.
5. The frozen four-action benchmark excludes set changes, load-down proposals, and substitutions. It cannot prove the globally best exercise/plan choice.
6. HOLD is the last **observed** 140 × 5, not the B generator's separate **unweighted** 3 × 6 plan.
7. User input one-side plate 1.5 kg gives a 3 kg total step; recorded 2.5 kg gaps trigger a review. No automatic unit conversion/setting rewrite.
8. Three unvalidated coefficient policies and two RIR-quantile bands change the winner. Numerical ranks cannot yet be written into prescriptions.

## What Gate E actually adds

A post-generation, **unranked evidence coverage** inspection for all planned exercises other than the already audited back squat. Results are ephemeral and visible in an independent read-only screen; nothing is persisted or applied as future loads. The current B generator, actual records, linked applications, CSV semantics and existing squat Gate D are unchanged.

| Tier | Eligible scope | Gate E behavior | Still missing |
| --- | --- | --- | --- |
| A | Exact canonical keys for conventional deadlift, flat bench, weighted pull-up | Shows observed anchor and whether the canonical bridge is ready; weighted pull-up fails closed without BW+added-load accounting | Live posterior + correct curve + BW scenarios + independent validation |
| B | Reviewed proxy-local relationship | Shows local model eligibility *unverified*, never treats proxy as a main-lift absolute e1RM | Proper exercise-local posterior and calibration state |
| C | Ordinary load × reps records with exact stableKey | Shows hold, repetition step, and *conditional* displayed-unit increment as **unranked** possibilities | Comparable session grouping, target effort, multi-set and progress evidence, reviewed objective weights |
| D | Duration/movement/other tasks or missing numeric evidence | Keeps task-specific review/unknown rather than inventing kilograms or e1RM | Sport/core task-specific measures, quality and progression contracts |

The audit uses only confirmed historical rows up to `asOf`. V22HistoryRow does **not** retain a session ID, so same-day rows cannot be interpreted as one complete session. Unknown observations remain unknown, not zero. Exact current equipment increments are UI-fed and a mismatch with observed load differences is a **review signal**, not a hard proof of bad settings.

## Criteria before automatic selection of other exercises

- Authoritative, read-only per-target/per-exercise posterior and curve as-of bridge with no future leakage; weighted pull-up must use actual contemporaneous bodyweight.
- Verified RPE/RIR compatibility and posterior predictive calibration including near-1RM conflicts; independent future-session variability must not be replaced by anchor RPE mixture.
- Candidate actions for load/reps/sets/time/quality according to each training task, not forced squat action sets.
- All working sets, total volume, time, day/week distribution, existing badminton load and recovery uncertainty in the same plan comparison.
- Approved or explicitly scenario-tested objective coefficients and scenario families; no stable result if sensitivity reverses selection.
- User-fixed equipment, days, goals, patterns, applications and recorded history remain hard constraints.
- Unit and regression tests, reproducibility, independent calibration review and real-device comparison **before** merging or using numbers in saved plans.

Gate E is deliberately **coverage and data-authority preparation**, not a claim that the coach algorithm has been extended and validated for all exercises.
