# B-Coach multi-objective production completion

## Scope and source drift

- Work started from `main` at `a41bf89e4b27fc38c1bf4d116d1ea09bbb95b04f` (`Merge B-Coach progression and orphan-safe backup cleanup into main`).
- The reviewed implementation and executable evidence are recorded in local commit `49d5ccef`.
- The user explicitly requested work on `main`, overriding the attached prompt's dedicated-branch instruction.
- The supporting audit `WhatYouGottaDo_BCoach_미적용_설계_수식_총정리_2026-11-14.md` was available and reviewed. Its `LIVE`, `SHADOW`, `PENDING`, `DESIGN`, and `TOY` labels remain authority boundaries.
- This change does not promote the v1.2 equations to a universal automatic optimizer. It ships the production-safe, explicit-review route requested by the audit.

## Architecture and authoritative call chain

`PlanScreen.generateV22` → `TrainingViewModel.generateV22Program` → `TrainingRepository.generateV22Program` → `V22ProgramPlanningService.generate` → `BBalanced12WeekEngine.build` → independent safe baseline and joint candidate skeletons → `BCoachMultiobjectiveProduction.prepare` → safe baseline preview plus an optional fingerprint-bound proposal → explicit user acceptance in `PlanScreen` → edited draft → `TrainingRepository.saveGeneratedProgram` → `ProgramPlanService.saveGeneratedProgram` → reopen/apply through the existing program and calendar services.

The engine now retains the pre-joint-search block result as `reviewBaselineWeeks`. The joint optimizer can still produce a materially different whole-block candidate, but generation returns the safe baseline until the user accepts the exact candidate fingerprint against the unchanged baseline fingerprint. An edit, regeneration, or stale draft invalidates acceptance.

The decision metadata records engine/policy version, source owner, evidence cutoff, baseline/candidate/input SHA-256 fingerprints, normalized explicit goal weights, all eight objective axes, input audit, changes, alternatives, unknowns, adverse scenario, acceptance state, and fallback reason. Metadata is backward-compatible JSON stored in the existing planning metadata field.

## Authority and promotion matrix

| Component | Production status | Implemented boundary |
|---|---|---|
| Pre-joint safe B plan | `AUTO` | Always materialized independently and returned by default. |
| Roster/set/repetition whole-block candidate | `USER_REVIEW` | Can change saved items only after exact fingerprint acceptance. |
| User session minutes, fixed/allowed weekdays, program bounds, set integrity | `AUTO` hard gate | Any failure suppresses the proposal and keeps the safe plan. |
| Explicit goal importance 1–5 | `USER_REVIEW` input | Explicit values normalize; `NULL` remains missing, never zero. |
| Fixed block goal/pattern | `AUTO` constraint | Fixed goals are not replaced; `BALANCED` has one neutral compatibility label and no hidden focus allocation; only `AUTO` searches multiple goals. |
| Goal Need intervals and direct/secondary credit equations | `UNSUPPORTED` for automatic scoring | Raw axis is present with unit, provenance, and missing reason; no invented Need values. |
| Six-axis planned/recent recovery load | `SHADOW` diagnostic | Raw maximum/mean/recent values are visible. Approved caps and decay are unavailable. |
| Cross-axis normalizers, lambdas, epsilon | `UNSUPPORTED` for automatic winner selection | Candidate stays explicit review; no fabricated weighted sum. |
| Engineering session-time estimate | `AUTO` hard limit, `SHADOW` explanation | User limit is enforced on every modeled day; estimate is not called measured time. |
| Unknown starting load | `HOLD_CALIBRATE` | Remains `USER_CALIBRATION_REQUIRED`; 0 kg is never represented as a real prescribed load. |
| Main-exercise linked load progression | Existing `LIVE-RECORD` path | Requires confirmed linked evidence and its existing policy. This change grants no blanket kg authority. |
| Non-main automatic load steps | `HOLD_CALIBRATE` | Always zero, even when an equipment grid is known. Reps/sets may progress. |
| Squat curve/comparison and other exercise coverage | Existing `SHADOW` | Preserved as diagnostics; not treated as approval for the whole V22 B plan. |
| Future weighted-pull-up bodyweight | `UNSUPPORTED` | Recorded as unavailable and never fabricated. |
| Multi-set independent success probability | `UNSUPPORTED` | Follow-up sets are targets, not multiplied independent probabilities. |

## Before/after production example

The deterministic review-gate fixture uses a 14-day B plan applied with a start date of 2026-11-16. The candidate is not active until acceptance.

| State | Calendar slot | Exercise | Prescription | Load state | Key objective values |
|---|---|---|---|---|---|
| Safe baseline | 2026-11-16 (week 1/day 1) | `barbell_back_squat` | 2 × 5 | `USER_CALIBRATION_REQUIRED`, no kg | work sets 2; systemic daily max 2; set delta 0 |
| Proposed | 2026-11-16 (week 1/day 1) | `barbell_back_squat` | 3 × 5 | `USER_CALIBRATION_REQUIRED`, no kg | work sets 3; systemic daily max 3; set delta +1 |
| Accepted and saved | same | same | 3 × 5 | unchanged manual calibration | metadata `ACCEPTED_AFTER_EXPLICIT_USER_REVIEW`; save/reopen/calendar path preserves the accepted plan |

The public cold-start engine test also asserts that the actual joint candidate differs materially from `reviewBaselineWeeks`. A public synthetic integration case accepts a material proposal, saves, reopens, and applies it to a calendar date. The optional private real-history integration case additionally performs explicit starting-load calibration when its fixture is available.

## T01–T44 counterexample matrix

Status means the behavior is executable and regression-tested; it does not upgrade an unapproved physiological formula.

| ID | Status | Executable evidence |
|---|---|---|
| T01 | `AUTO` isolation | `BCoachJointBlockOptimizerTest.T01 another program observations cannot change linked-plan completion` |
| T02 | `AUTO` identity | `WorkoutSessionIdentityTest.eachProgramApplicationCreatesNewSessionsPerScheduledDay` |
| T03 | `AUTO` hard constraint | `BCoachJointBlockOptimizerTest.fixedWeekdaysMustRemainFixedAndUnapprovedEquipmentCannotBeInvented` |
| T04 | `AUTO` hard constraint | `BCoachMultiobjectiveProductionTest.T04 a candidate over the per-session limit is held rather than offered` |
| T05 | `AUTO` evidence isolation | `BJointExerciseSetOptimizerTest.provisionalStabilizerMetadataDoesNotFreezeReviewedPrimaryAccessory` |
| T06 | `USER_REVIEW` input semantics | `BCoachMultiobjectiveProductionTest.null importance stays missing while explicit weights normalize only explicit values` |
| T07 | `AUTO` fixed goal | `BBalancedFocusSearchPolicyTest.T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search` |
| T08 | `AUTO` balanced constraint | same test; asserts `BALANCED_NO_HIDDEN_FOCUS` and one focus evaluation |
| T09 | `AUTO` explicit peak mode | `BBlockPurposePrescriptionTest.finalPeakWeekUsesOneRepOneSetOfMainExercise` preserves explicit 3-rep evaluation unless one-rep mode is chosen |
| T10 | `AUTO` calendar mapping | `BBlockPeriodizationTest.recoveryOnlyOccupiesBlockBoundaries`; `V22ProgramIntegrationTest.new block program with partial recovery survives load calibration and save restore` |
| T11 | `AUTO` fixed pattern | `BBlockPrescriptionApplicatorTest.fourWeekPatternsChangeActualSetAndRepPrescriptions`; `BBlockPeriodizationTest.supportedWaveLengthsHaveTheAgreedShapes` |
| T12 | `AUTO` search boundary | `BBalancedFocusSearchPolicyTest.T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search` |
| T13 | Existing `SHADOW` authority boundary | `BCoachExerciseShadowCoverageTest.only exact canonical keys receive canonical tier and weighted pullup demands bodyweight` |
| T14 | Existing `SHADOW` fail closed | `BCoachSquatCurveProgressionRankerTest.missing RPE does not fabricate reference e1rm or automatic winner` |
| T15 | Existing `SHADOW` fail closed | `BCoachSquatShadowProgramComparisonTest.unavailable posterior still preserves baseline and does not invent weight` |
| T16 | Existing `SHADOW` conflict audit | `BCoachSquatRelativeShadowTest.T16 observed 205kg one-rep limit is retained as a posterior conflict` |
| T17 | `TOY` test only | `RepetitionCurveRegistryTest.canonical assets are finite monotone and invert deterministically` checks monotonicity and unsupported domain |
| T18 | `TOY` test only | `RepetitionCurveRegistryTest.T18 T19 reviewed TOY grid and minimax-regret values stay reproducible` checks the documented RIR table |
| T19 | `TOY` test only | same test checks conditional per-scenario regret and minimax choice |
| T20 | Existing `SHADOW` fail closed | `BCoachSquatCurveProgressionRankerTest.unknown set target never rewards extra sets` |
| T21 | Existing `LIVE-RECORD` gate | `BCoachLinkedSquatWeekPlannerTest.unconfirmed or missed previous sets cannot cause progression`; confirmed case also tested |
| T22 | Existing `SHADOW`/`LIVE-RECORD` hold | `BCoachObservedSquatProgressionTest.load step above two percent falls back to hold`; high-RPE hold in ranker test |
| T23 | Existing `SHADOW` uncertainty | `RpeRirPolicyTest.known RPE likelihood represents RIR uncertainty exactly once`; RPE 8 is a distribution, not exact RIR 2 |
| T24 | Existing equipment-grid rule | `BBlockEquipmentIncrementsTest.oneStepUsesEquipmentDisplayUnit`; one-side 1.25 kg means total 2.5 kg and arbitrary 147.3 kg is not generated |
| T25 | Existing `SHADOW` typed load | `StrengthPerformanceLikelihoodTest.weighted pull-up resolves total load with bodyweight priority and age uncertainty` |
| T26 | `UNSUPPORTED` future value | `BCoachExerciseShadowCoverageTest.only exact canonical keys receive canonical tier and weighted pullup demands bodyweight`; production input audit records no fabricated future bodyweight |
| T27 | Existing `SHADOW` fail closed | repetition-curve registry rejects unsupported repetitions/relative load; task reps above 20 are not passed through e1RM |
| T28 | Existing `SHADOW` boundary | `BCoachSquatRelativeShadowTest.multiset scenarios do not invent predictive performance for later sets` |
| T29 | Existing `SHADOW` boundary | `BCoachExerciseShadowCoverageTest.reviewed RDL proxy relation cannot pretend to be a canonical posterior` |
| T30 | Existing `LIVE-RECORD` identity | `BCoachObservedSquatProgressionTest.mismatched exercise track cannot source an increment` |
| T31 | Existing `SHADOW` observation route | `BCoachExerciseShadowCoverageTest.ordinary barbell lift receives only unranked hold reps and configured-grid actions` |
| T32 | Existing `SHADOW` task route | `BCoachExerciseShadowCoverageTest.time hold without mechanical load is task-specific and never uses RIR` |
| T33 | Existing fail closed | exact-key coverage tests plus `BCoachExerciseShadowCoverageTest.no observed evidence is explicit unknown rather than zero performance` |
| T34 | `USER_REVIEW` | `BCoachMultiobjectiveProductionTest.T34 approved Need is absent so extra sets never become an automatic reward` |
| T35 | `SHADOW` raw recovery | `BCoachMultiobjectiveProductionTest.T35 raw recent six-axis load is retained while residual decay remains missing` |
| T36 | `SHADOW` raw recovery | `BCoachMultiobjectiveProductionTest.T36 recovery maximum and mean remain distinct raw values` |
| T37 | `UNSUPPORTED` without an approved cap | Test proves no candidate claims a cap pass while caps are unavailable; therefore no fabricated exclusion or auto-approval occurs |
| T38 | `USER_REVIEW` | `BCoachMultiobjectiveProductionTest.T38 an unapproved fatigue cap does not hard-reject a reviewable candidate` |
| T39 | `SHADOW` explanation | `BCoachMultiobjectiveProductionTest.T39 execution axis reports engineering time without inventing a time-pressure cause` |
| T40 | `USER_REVIEW` | `BCoachMultiobjectiveProductionTest.T40 missing lambda epsilon and Need keep the candidate in review` |
| T41 | `UNSUPPORTED` action | `BCoachMultiobjectiveProductionTest.T41 rest-only mutation is not an active production candidate` |
| T42 | `USER_REVIEW` active action | `BCoachMultiobjectiveProductionTest.T42 set action changes both validity route and evaluated ramp value` |
| T43 | `USER_REVIEW` mapping trace | `BCoachMultiobjectiveProductionTest.T43 changing only user goal changes mapped input fingerprint deterministically` |
| T44 | `USER_REVIEW` audit | `BCoachMultiobjectiveProductionTest.T44 fixed inputs equipment and unavailable bodyweight are traceable` |

## Validation record

Environment: Android Studio JBR 21, Android SDK at `C:/Users/pki08/AppData/Local/Android/Sdk`, repository `gradlew.bat`, and task-local `GRADLE_USER_HOME`.

- `gradlew.bat :app:testDebugUnitTest --no-daemon` initially reached the test task but the Windows Robolectric native runtime crashed outside the JVM with `EXCEPTION_ACCESS_VIOLATION` in `robolectric-nativeruntime.dll`; this was not an assertion failure.
- The documented local isolation (`forkEvery=1`, `maxParallelForks=2`, `--max-workers=2`) was applied temporarily and removed after validation. Final clean rerun: `gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=2 --rerun-tasks` — **PASS**, 2,797 tests, 0 failures, 0 errors, 25 skips, 430 XML suites, 12m28s. The skips require private real-history/backup fixtures not present in this checkout; the new focus-policy and acceptance/persistence tests use public synthetic fixtures and ran rather than skipped.
- Targeted production, TOY curve, narrow/large-text UI, hard-gate, actual engine material-difference, equipment snapshot, and acceptance/persistence tests also passed individually after the final review edits. The last post-review run covered 23 tests: 21 passed and 2 private-fixture integration cases skipped, with 0 failures and 0 errors.
- `gradlew.bat :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2` — **PASS**, final rerun 2m56s. Lint: 0 errors, 209 warnings, 22 information findings (existing repository findings; largest groups are unused resources, plurals candidates, typography, and dependency updates).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 72,775,986 bytes, SHA-256 `26E0EE1317158EB56B80A82573208D3DFA49D4827BBDD9A9A6B93CB00B183498`.
- `adb devices -l` returned no connected device, so `connectedDebugAndroidTest` and real-device interaction were not run.
- Hosted GitHub Actions was inspected read-only. The starting `main` SHA `a41bf89e4b27fc38c1bf4d116d1ea09bbb95b04f` passed workflow `Android Debug Build` (run `43566793803`). Local completion commits were not pushed, so no hosted run exists for the local changes.

## Deliberately withheld automatic decisions

- No automatic Need-band deficit/excess optimization until reviewed task mappings and numeric ranges exist.
- No automatic recovery-cap rejection or residual decay until reviewed six-axis caps and decay are supplied.
- No automatic cross-axis winner from invented normalizers, lambda weights, or epsilon.
- No universal initial training kg, no Epley fallback, no proxy-exercise posterior, no cross-exercise kg transfer, and no future bodyweight fabrication.
- No automatic non-main load step. Unknown loads remain manual even after a roster/set/rep candidate is accepted.
- No claim that engineering session-time estimates are observed duration or that missed final work proves time pressure.
