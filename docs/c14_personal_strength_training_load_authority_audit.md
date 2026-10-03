# Phase C14 — Personal Strength Capacity and Training-Load Authority

## Audit status

- C13 PR #5 merge commit: `f43e6f8e32bd4d8e4317b353dda88d211498a5a8`
- C14 start HEAD: `f43e6f8e32bd4d8e4317b353dda88d211498a5a8`
- C14 implementation commit: `ec9c6a30c9e0e02cddc63c3beedc185bd75b1fb7`.
- C14 negative-test follow-up commit: `8c06e53d451c07c88b4aa48edf13ed11f65ce8e5`; this adds explicit Power and unresolved load-semantics refusal coverage.
- `lastAuditedCommit`: `8c06e53d451c07c88b4aa48edf13ed11f65ce8e5` (implementation/test Hosted CI green).
- The final documentation HEAD is recorded in PR #6 and task closeout. A Git commit cannot embed its own resulting object ID.
- The implementation is shadow-only. It adds no B6 authority and does not change the generated program, routing, or protocol contract.

## External evidence reviewed before implementation

1. Nuzzo et al., *Maximal Number of Repetitions at Percentages of the One Repetition Maximum: A Meta-Regression and Moderator Analysis of Sex, Age, Training Status, and Exercise* (PMID 37792272). The synthesis analyzed 952 repetitions-to-failure tests from 7,289 people and modeled both mean repetitions and between-person variation. The relationship varied by exercise; the authors published separate bench-press and leg-press tables and noted that other exercises need more data. C14 therefore treats these relationships as uncertain priors, not exact personal capacity. [PubMed](https://pubmed.ncbi.nlm.nih.gov/37792272/)
2. Lovegrove et al., *Repetitions in Reserve Is a Reliable Tool for Prescribing Resistance Training Load* (PMID 36135029). In a small sample of 15 novice-trained men, repeated RIR-based prescriptions had high test–retest reliability. Loads reported at 1 RIR differed by exercise and repetition count (including deadlift 5 reps at 84.3% 1RM and bench press 5 reps at 87.3% 1RM). These are cohort results, not universal individual values; they support keeping a rep-max ceiling distinct from a lighter, reserve-bearing training proposal. [PubMed](https://pubmed.ncbi.nlm.nih.gov/36135029/)
3. Zourdos et al., *Novel Resistance Training-Specific Rating of Perceived Exertion Scale Measuring Repetitions in Reserve* (PMID 26049792). The study compared experienced and novice squatters at several intensities and supports RPE/RIR as a practical feedback language, while also showing that experience affects perceived effort at maximal loads. [PubMed](https://pubmed.ncbi.nlm.nih.gov/26049792/)
4. Mansfield et al., *Estimating Repetitions in Reserve for Resistance Exercise: An Analysis of Factors Which Impact on Prediction Accuracy* (PMID 32881842). In 20 trained men tested on bench press and prone row at 60% and 80% 1RM, RIR estimates were below actual reserve on early sets and accuracy improved across sets as participants approached failure. C14 therefore does not treat a reported RPE/RIR value as a perfect measurement and keeps the first inferred proposal conservative. [PubMed](https://pubmed.ncbi.nlm.nih.gov/32881842/)
5. The requested four-exercise RIR paper (PMID 33337690) is marked retracted by PubMed. It is excluded from implementation evidence. [PubMed retraction notice](https://pubmed.ncbi.nlm.nih.gov/33470601/)

## Existing repository machinery and reuse decision

- `StrengthPerformanceLoadResolver` resolves exercise load semantics, including external, implement-total, machine-stack, bodyweight-added, and assistance loads. C14 must keep exact-owner semantics and reject unresolved mechanical load.
- `StrengthSessionLikelihoodBuilder` and `StrengthExercisePosteriorEngine` already form the canonical same-exercise strength-capacity estimator. They use confirmed sets, resolved mechanical load, the assigned repetition curve, and the RPE/RIR distribution. A missing RPE creates a lower-censored likelihood; it does not label the performed repetitions as an RM test.
- `canonicalStrengthSignalsForWindow` exposes an exact stable-key local posterior over the existing 55-day reference window. The current signal carries posterior median, observation count, and source but omits posterior variance/date; C14 will preserve those uncertainty facts if a proposal needs to consume them.
- `CanonicalStrengthReferenceIndex` selects the exact exercise-local reference at the historical set's point in time. The strength proxy registry separately defines any cross-exercise target transfer. C14 will not use that transfer to authorize another exercise's training load.
- `RepetitionCurveRegistry` already owns reviewed curve profiles, assignment match level, source provenance, and curve uncertainty. The bench press has an exercise-specific assignment; `ex_32eb8457` uses the general fallback and must carry that additional uncertainty. C14 will not introduce an independent Epley/Brzycki/Lombardi oracle.
- `RpeRirPolicy` maps reported effort to a distribution over RIR. The estimate is therefore probabilistic; C14 will retain the selected target effort and resulting confidence in typed output.
- `TargetCompatiblePersonalHistory` uses exact stable key plus reviewed realization and load evidence. `StimulusPlannedPrescriptionResolver` already enforces the current Strength range (1–6), reference floor (70%), and planned-load compatibility. These boundaries remain unchanged.
- `StimulusPrescriptionRealizationPlanEngine` and exact B6 materialization are the intended integration path. `ProgramProgressionEngine` already gates later changes on actual completion and observed RPE; C14 will not add a second progression engine.
- `PersonalizedProgramPlanningService` computes B1–B6 before EXPERIMENTAL and CONTROL, and C10/C11 provenance is carried into B7. CONTROL remains outside all capacity and training-load inputs.

## C13 starting evidence for the five REP_RANGE_INCOMPATIBLE rows

The merged C13 census names these five exact owner-target failures:

| Case | Exact owner | C13 B4 target | C13 B6 provisional input |
|---|---|---|---|
| `persona0_mixed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `RESTORE_PERSONAL_BASELINE`, `[0,0,6]` | 8 reps × 2, zero load, provisional RPE 6–8 |
| `persona0_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `RESTORE_PERSONAL_BASELINE`, `[0,0,6]` | 8 reps × 2, zero load, provisional RPE 6–8 |
| `persona2_reviewed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `RESTORE_PERSONAL_BASELINE`, `[0,0,6]` | 8 reps × 2, zero load, provisional RPE 6–8 |
| `persona3_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `RESTORE_PERSONAL_BASELINE`, `[0,0,6]` | 8 reps × 2, zero load, provisional RPE 6–8 |
| `persona4_mixed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `RESTORE_PERSONAL_BASELINE`, `[0,0,6]` | 8 reps × 2, zero load, provisional RPE 6–8 |

In the C13 coverage fixture, each reviewed/mixed Strength history uses confirmed 40 kg sets with RPE 8 across 14 sessions in the 55-day window: 8 reps on days 7–37 and 5 reps on days 42–51 before the 2026-09-20 cutoff. The fixture's history and manually seeded local posterior both belong to the base exercise `barbell_back_squat`; the five failed B6 identities are `ex_32eb8457` or `barbell_bench_press`. C14 therefore records zero owner-local observations and no canonical capacity signal for those selected owners. The squat's evidence is retained for its own owner and is never transferred to the pulldown or bench press.

## C14 type and policy boundary

The implementation will use distinct types for (1) an exact-owner Strength capacity reference, (2) the estimated repetition-max ceiling, (3) a conservative training-load proposal, and (4) observed calibration feedback. A proposal can only use a canonical same-owner reference and the existing curve/RIR machinery. It must remain within 1–6 reps, above the existing 70% reference floor, below the estimated rep-max ceiling, and rounded down using an existing repository increment policy. A displayed capacity ceiling can never be copied directly into the training-load field.

The B4 dose authority remains separate. `DIRECTION_ONLY` cases will be listed and assessed in the final audit. C14 may only preserve an already selected B5 owner and the existing canonical material-demand set count; it may not invent a numeric B4 set target. No B5 ranking, B1–B6 target, Hypertrophy/Power policy, B7/B8 gate, or routing scope is changed by this evidence review.

## Implementation and validation results

### C14A shadow model

The shadow resolver composes the existing exact-exercise posterior with the existing reviewed repetition curve and RPE/RIR distribution. It introduces no second 1RM formula. It only runs when B4 already has numeric Strength dose authority, B5 selected the exact canonical Strength owner for `QUALITY:STRENGTH`, the existing material demand has a positive set count, load semantics resolve, and the same owner has a current, established posterior. The posterior date is constrained to the planning cutoff and the existing 55-day reference window.

The types are `StrengthCapacityReference`, `StrengthRepMaxCeiling`, `StrengthTrainingLoadProposal`, and `StrengthTrainingCalibrationEvidence`. The ceiling is explicitly marked `CAPACITY_CEILING_ONLY_NOT_TRAINING_LOAD`. Proposal construction checks that its owner matches both nested owner identities, its rep count matches the ceiling, the proposed load is positive, below the ceiling, and below its safety cap. A work set with missing RPE is never called an RM; a reference-only path can use only a separate same-owner posterior with multiple two-sided observations.

The shadow prefers, in order, repeated exact-owner Strength-range observations, repeated higher-rep exact-owner observations with effort evidence, and finally a sufficiently supported same-owner canonical posterior paired with higher-rep history. A direct same-rep pattern determines target reps; without one, the product policy selects six reps as the nearest Strength boundary. Historical effort is converted through the existing RIR distribution and curve. For a low-confidence/general-curve path, the resolver uses a more conservative observation quantile and target-reserve quantile. It takes the lower quartile across session-level proposals, rounds down on the app's existing 0.5 kg editable arithmetic grid (not an equipment-availability claim), preserves a minimum 70% same-owner reference floor, and caps a same-rep proposal at the lowest observed same-rep work load. The initial target effort is RPE 6.5; the actual completed RPE remains the input to the existing progression engine.

The cutoff filter for personal curve posteriors uses `updatedAt` and the planning date. A post-cutoff curve calibration cannot influence a retrospective proposal. `StrengthPersonalCurveThetaAsOfTest` verifies same-day inclusion, next-day exclusion, and exact model-revision filtering.

### Five C13 REP_RANGE_INCOMPATIBLE owner-target rows

All five rows have B4 numeric authority `PERSONAL_RESTORE_BASELINE`, with the observed baseline envelope `[0,0,6]` sets. C13's old B6 probe used a provisional 8-repetition prescription × 2 sets with RPE 6–8 and no executable load; eight reps are outside the app's unchanged Strength boundary of 1–6. The C14 resolver did not pick replacement reps or create a training prescription because it could not establish a same-owner capacity signal. The fixture's 40 kg / RPE 8 records and local posterior are on `barbell_back_squat`; using them for another key would be prohibited cross-exercise transfer.

| Case | Exact selected B5 owner | Owner load semantics | Same-owner observations/reference in cutoff | C14 shadow result | Existing B6 / route |
|---|---|---|---|---|---|
| `persona0_mixed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `MACHINE_STACK_LOAD` | 0 observations; no posterior signal; no reps or ceiling | unavailable: `EXACT_OWNER_STRENGTH_SIGNAL_MISSING` | no executable B6; CONTROL |
| `persona0_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | 0 observations; no posterior signal; no reps or ceiling | unavailable: `EXACT_OWNER_STRENGTH_SIGNAL_MISSING` | no executable B6; CONTROL |
| `persona2_reviewed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `MACHINE_STACK_LOAD` | 0 observations; no posterior signal; no reps or ceiling | unavailable: `EXACT_OWNER_STRENGTH_SIGNAL_MISSING` | no executable B6; CONTROL |
| `persona3_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | 0 observations; no posterior signal; no reps or ceiling | unavailable: `EXACT_OWNER_STRENGTH_SIGNAL_MISSING` | no executable Strength B6; CONTROL (Power remains independently unsupported) |
| `persona4_mixed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | 0 observations; no posterior signal; no reps or ceiling | unavailable: `EXACT_OWNER_STRENGTH_SIGNAL_MISSING` | no executable B6; CONTROL |

The shadow census also evaluated six generated Strength resolutions overall: 0 available and 6 unavailable. No exact owner-target proposal was available for activation. There is no held-out same-owner history for these five owners in this fixture, so retrospective load-error metrics are not computable without leakage or invented data.

The synthetic same-owner test fixture exercises the proposed chain separately: with a canonical local 1RM reference of 100 kg and repeated 5-rep work at 78 kg / RPE 8, the resolver selects five reps, estimates a distinct rep-max ceiling in the asserted 85–90 kg band, and proposes a separate reserve-bearing first training load in the asserted 70–75 kg band at target RPE 6.5. The test also verifies `trainingLoad < ceiling`, the existing 70% floor, and owner identity. This demonstrates type/model behavior; it is not used as authority for the five C13 corpus cases.

### Direction-only cases and activation decision

The census contains eight Strength `DIRECTION_ONLY` targets. Each remains `NO_PRESCRIPTION_CHANGE_AUTHORIZED` with `B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`; C14 shadow disposition is `NO_LOAD_AUTHORITY_FROM_DIRECTION_ONLY_B4`. A request to introduce direct Strength stimulus does not specify a numeric dose, and C14's load model cannot invent reps or set count. All eight remain blocked.

C14B activation was rejected because none of the five REP_RANGE rows had an exact-owner posterior/history signal, and all direction-only cases lacked numeric B4 authority. No new B6 authority type was added or consumed. Existing exact compatible B6 authority continues to take precedence; C10/C11 provenance, B7 attribution, B8 materialization/scope, and B9 routing remain untouched.

### Parity and validation

- Routes before and after: CONTROL 21 / Strength 1 / Hypertrophy 0 / Combined 0. The 27-case corpus remains 22 generated and 5 preflight rejected.
- B1–B6 selected owners, material demand, B6 authorization/materialization, CONTROL and EXPERIMENTAL program rows, B7 reasons, B8 decisions, and B9 sources remain unchanged. B7 before/after is `CHANGE_PROVENANCE_UNCLOSED=14`, `AFFECTED_TARGET_REMAINS_UNMET=9`, `TARGET_REGRESSED=1`. Standard coverage SHA-256 remains `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.
- Across the 17 exact selected Strength owner rows, existing B6 authority and full materialization remain 3/3; the other 14 rows remain without executable authority and without materialization. For the five C13 rep-range failures specifically, B6 executable authority/materialization stays 0/0 before and after C14.
- B7 retains 16 `NOT_ELIGIBLE` and 6 `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` outcomes across 22 generated cases. B8 remains `CONTROL_REQUIRED` in 21 cases and authorized for bounded Strength cutover in the single Strength case. B8 reason occurrences are unchanged: `B8_B7_NOT_ELIGIBLE=16`, `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY=3`, `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY=5`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED=3`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION=5`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION=3`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY=4`, `B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE=1`, and `B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY=1`. The remaining single reason occurrence is the authorized Strength status `B8_STRENGTH_V1_AUTHORIZED`.
- Phase order remains `B1-B6 → EXPERIMENTAL → CONTROL → COMPARISON → B7 → B8 → B9`. Build accounting remains CONTROL 1 / EXPERIMENTAL 1 / total 2 / third 0; preflight rejection remains 0 builds.
- Versions remain Program Builder Protocol `3.51.0`, planner runtime `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1`, app `0.5.1.5`. Shadow diagnostics are transient and omitted from the existing compact planning serialization; no backup, Community/Cloud, or protocol schema changed.
- Focused local validation: compile production and unit-test Kotlin sources plus 18 focused test classes, 219 tests, 0 failures, 0 errors, 0 skips. This includes the existing progression policy and the C14 exact-owner, Power/load-semantics refusal, cutoff, and corpus tests.
- Full local `:app:testDebugUnitTest` was attempted. Windows JBR 21 crashed outside the JVM in `robolectric-nativeruntime.dll+0x5c22`, in `SQLiteConnectionNatives.nativePrepareStatement` during a WorkManager/Room transaction. The run had 1,452 completed tests, 0 reported assertion failures, 0 reported test errors, and 3 skips before Gradle's worker loopback connection reset. It is incomplete, not a pass; Hosted Linux CI is authoritative for the complete suite.
- Implementation/test Hosted Android Debug Build run [37091261981](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37091261981) passed on `8c06e53d451c07c88b4aa48edf13ed11f65ce8e5`: protocol validation, Community/Cloud contracts, whitespace, full tests, coverage upload, APK assembly, signer validation, and APK upload all passed. Hosted unit results: 2,212 tests, 0 failures, 0 errors, 4 skips.
- The uploaded [`Stimulus-production-coverage` artifact](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37091261981/artifacts/11263490005) contains the machine-readable C14 census, all 346 test-suite XML files, and the standard coverage report. The report SHA-256 is `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`; the census records 27/22/5 corpus cases, routes 21/1/0/0, 0 available and 6 unavailable Strength shadows, and no B6 activation.
- The uploaded [`WhatYouGottaDo-debug-apk` artifact](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37091261981/artifacts/11262805468) contains `app-debug.apk`, 68,657,843 bytes, SHA-256 `4EA1F1AE6AC0D853BDC7C087C11E5CE81E72C09443A6FEAE4BF96DB0C9462A93`. The Hosted signer validation passed.
- The audit and protocol overview are committed on documentation head `b441a6a5e7c2b0cd3c7edacf4c19a0cec95465d3`; its Hosted CI passed. The final documentation revision's Hosted run and resulting HEAD are recorded in the task closeout because a commit cannot embed its own resulting object ID. PR #6 remains open.
