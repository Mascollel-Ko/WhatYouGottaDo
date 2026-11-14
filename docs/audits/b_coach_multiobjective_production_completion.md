# B-Coach multi-objective production completion

## Scope, authority, and source drift

- Follow-up work started from `main` at `7191410502c1e67ec56c375c2c1d938aafa96e9a` and was implemented on local branch `fix/b-coach-production-footnotes`.
- The follow-up implementation is commit `4e506163` (`fix: harden B-Coach proposal persistence`). The dedicated branch was pushed after explicit approval and was not merged.
- The request authority was `B_Coach_Multiobjective_Production_Completion_Codex_Prompt_Annotated_2026-11-15.md`. `B_Coach_Multiobjective_Equations_and_Implementation_Footnotes_2026-11-15.md` and the earlier 2026-11-14 audit were supporting references, not independent authorization to invent coefficients.
- The earlier production path normalized an incomplete importance vector, fingerprinted only part of the proposal state, and did not re-read persistence evidence atomically. This follow-up closes those gaps without promoting pending v1.2 equations to automatic authority.
- The shipped boundary remains a safe baseline plus an exact, explicit-review candidate. Unknown Need, recovery caps/decay, normalizers, lambdas, epsilon, and universal load progression remain unavailable rather than being guessed.

## Architecture and authoritative call chain

Generation:

`PlanScreen.generateV22` → `TrainingViewModel.generateV22Program` → `TrainingRepository.generateV22Program` → `V22ProgramPlanningService.generate` → `BBalanced12WeekEngine.build` → independent safe baseline and joint candidate → `BCoachMultiobjectiveProduction.prepare` → safe preview plus optional proposal.

Acceptance and persistence:

`PlanScreen` review dialog → display material change vector and unresolved reason codes → user accepts the exact `proposalFingerprint` → optional manual load calibration → `TrainingRepository.saveGeneratedProgram` → one Room/cloud-revision transaction → `V22ProgramPlanningService.validateAcceptedForPersistence` re-reads history, relevant exercise metadata, and linked evidence → exact structure/evidence verification → `ProgramPlanService.saveGeneratedProgram` → metadata state `ACCEPTED_AND_PERSISTED` → reopen/apply through existing program/calendar services.

The proposal fingerprint binds engine/policy version, source owner, full baseline and candidate skeletons, all request inputs, evidence cutoff/history, relevant exercise metadata, and linked evidence. The persistence structure fingerprint permits only expected post-review edits such as program naming and explicit user load calibration; changes to exercise identity, ordering, sets, repetitions, rest, schedule, or evidence reject the save. Revalidation and persistence share the same Room transaction, so they cannot observe different database snapshots.

Acceptance schema v2 performs this replay validation. Legacy v1 accepted metadata remains editable as already-persisted data; it is not retroactively treated as a pending v2 proposal.

## Authority and promotion matrix

| Component | Production status | Implemented boundary |
|---|---|---|
| Pre-joint safe B plan | `AUTO` | Built independently and returned by default. |
| Whole-block roster/set/repetition candidate | `USER_REVIEW` | Can affect the saved plan only after exact proposal acceptance and atomic revalidation. |
| Session minutes, fixed/allowed weekdays, program bounds, set integrity, duplicate slots/exercises | Typed hard constraint | `PASS`, `FAIL`, or `UNKNOWN`; a failure suppresses the proposal. |
| Explicit goal importance 1–5 | `USER_REVIEW` input | Normalize only when all four values are explicit and valid. A partial vector remains partial; missing is not zero. |
| Fixed block goal/pattern | `AUTO` constraint | Fixed goals are preserved; `BALANCED` has no hidden focus search; only `AUTO` searches multiple goals. |
| Goal Need intervals and direct/secondary credit | `UNKNOWN` / direction only | Typed envelopes and reasons are persisted, but no numeric Need is invented. |
| Six-axis planned/recent recovery load | `PARTIAL` diagnostic | Known lower bounds remain partial when metadata is missing; unknown contributions are not treated as zero. |
| Recovery caps/decay, normalizers, lambdas, epsilon | `UNSUPPORTED` | No fabricated pass, rejection, or automatic weighted winner. |
| Engineering session-time estimate | Hard limit plus explanation | User limit is enforced on all modeled weeks; no limit is `NOT_APPLICABLE`, not numeric zero. |
| Unknown starting load | `HOLD_CALIBRATE` | Remains `USER_CALIBRATION_REQUIRED`; zero kg is never represented as a real prescription. |
| Explicit automatic kg in a new B proposal | `FAIL` | Suppressed even with a known equipment device because V22 has no universal load authority. |
| Main linked progression | Existing `LIVE-RECORD` route | Requires confirmed, identity-matched linked evidence and the existing policy. |
| Non-main automatic kg | `HOLD_CALIBRATE` | No automatic load step; reps/sets may change through the reviewed candidate. |
| Squat curve/comparison and other coverage | Existing `SHADOW` | Diagnostic only; it does not authorize the whole V22 plan. |
| Future weighted-pull-up bodyweight | `UNSUPPORTED` | Recorded unavailable and never fabricated. |
| Multi-set independent success probability | `UNSUPPORTED` | Follow-up sets remain targets, not multiplied independent probabilities. |

## Before/after production example

The public integration fixture uses a two-week, one-day-per-week B plan with `barbell_back_squat` and applies it on 2026-11-16.

| State | Material plan | Load state | Persistence result |
|---|---|---|---|
| Safe baseline | 2 × 5 | `USER_CALIBRATION_REQUIRED`, no kg | Returned before acceptance. |
| Review proposal | 3 × 5 | `USER_CALIBRATION_REQUIRED`, no kg | Material change and unresolved reasons are shown; still not active. |
| Explicitly accepted and calibrated | 3 × 5 at 100 kg | `EXPLICIT_LOAD`, source `B_BLOCK_USER_OVERRIDE` | Exact structure plus allowed user calibration passes atomic validation. |
| Reopened/applied | 3 × 5 at 100 kg | `EXPLICIT_LOAD` | Metadata is `ACCEPTED_AND_PERSISTED`; all three calendar sets reopen unconfirmed at 100 kg. |

The same test inserts a new confirmed 100 kg workout dated before the evidence cutoff and retries the still-pending accepted proposal. Save fails with `B_COACH_ACCEPTANCE_HISTORY_STALE`, and the program count does not change. An already-persisted reopened program remains normally editable afterward. A separate public engine test proves that two complete goal-importance vectors produce deterministic but materially different whole-block plans.

## T01–T44 counterexample matrix

`LocalResult=PASS` means the mapped public executable evidence passed in the 2,806-test local suite. It does not upgrade a `SHADOW`, `TOY`, `UNKNOWN`, or `UNSUPPORTED` physiological rule. `CIResult=PASS` means the complete branch workflow passed at audit commit `3892a6bf` in run `43614686319`.

| ID | TestClass | TestMethod | SourceCommit | LocalResult | CIResult |
|---|---|---|---|---|---|
| T01 | `BCoachJointBlockOptimizerTest` | `T01 another program observations cannot change linked-plan completion` | `49d5ccef` | PASS | PASS |
| T02 | `WorkoutSessionIdentityTest` | `eachProgramApplicationCreatesNewSessionsPerScheduledDay` | `1a9308ca` | PASS | PASS |
| T03 | `BCoachJointBlockOptimizerTest` | `fixedWeekdaysMustRemainFixedAndUnapprovedEquipmentCannotBeInvented` | `49d5ccef` | PASS | PASS |
| T04 | `BCoachMultiobjectiveProductionTest` | `T04 a candidate over the per-session limit is held rather than offered` | `4e506163` | PASS | PASS |
| T05 | `BJointExerciseSetOptimizerTest` | `provisionalStabilizerMetadataDoesNotFreezeReviewedPrimaryAccessory` | `d76965eb` | PASS | PASS |
| T06 | `BCoachMultiobjectiveProductionTest` | `partial importance stays missing and no incomplete vector is normalized`; `T06 zero importance is invalid rather than unset` | `4e506163` | PASS | PASS |
| T07 | `BBalancedFocusSearchPolicyTest` | `T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search` | `49d5ccef` | PASS | PASS |
| T08 | `BBalancedFocusSearchPolicyTest` | `T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search` (`BALANCED_NO_HIDDEN_FOCUS`) | `49d5ccef` | PASS | PASS |
| T09 | `BBlockPurposePrescriptionTest` | `finalPeakWeekUsesOneRepOneSetOfMainExercise` | `9162504b` | PASS | PASS |
| T10 | `V22ProgramIntegrationTest` | `reviewed B candidate materially persists reopens and applies without private fixtures` | `4e506163` | PASS | PASS |
| T11 | `BBlockPrescriptionApplicatorTest` | `fourWeekPatternsChangeActualSetAndRepPrescriptions` | `49d5ccef` | PASS | PASS |
| T12 | `BBalancedFocusSearchPolicyTest` | `T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search` | `49d5ccef` | PASS | PASS |
| T13 | `BCoachExerciseShadowCoverageTest` | `only exact canonical keys receive canonical tier and weighted pullup demands bodyweight` | `44476a1a` | PASS | PASS |
| T14 | `BCoachSquatCurveProgressionRankerTest` | `missing RPE does not fabricate reference e1rm or automatic winner` | `454976ee` | PASS | PASS |
| T15 | `BCoachSquatShadowProgramComparisonTest` | `unavailable posterior still preserves baseline and does not invent weight` | `4ea4fed9` | PASS | PASS |
| T16 | `BCoachSquatRelativeShadowTest` | `T16 observed 205kg one-rep limit is retained as a posterior conflict` | `49d5ccef` | PASS | PASS |
| T17 | `RepetitionCurveRegistryTest` | `canonical assets are finite monotone and invert deterministically` | `49d5ccef` | PASS | PASS |
| T18 | `RepetitionCurveRegistryTest` | `T18 T19 reviewed TOY grid and minimax-regret values stay reproducible` | `49d5ccef` | PASS | PASS |
| T19 | `RepetitionCurveRegistryTest` | `T18 T19 reviewed TOY grid and minimax-regret values stay reproducible` | `49d5ccef` | PASS | PASS |
| T20 | `BCoachSquatCurveProgressionRankerTest` | `unknown set target never rewards extra sets` | `454976ee` | PASS | PASS |
| T21 | `BCoachLinkedSquatWeekPlannerTest` | `unconfirmed or missed previous sets cannot cause progression` | `3225a57f` | PASS | PASS |
| T22 | `BCoachObservedSquatProgressionTest` | `load step above two percent falls back to hold` | `81b65dc6` | PASS | PASS |
| T23 | `RpeRirPolicyTest` | `known RPE likelihood represents RIR uncertainty exactly once` | `47ac7d33` | PASS | PASS |
| T24 | `BBlockEquipmentIncrementsTest` | `oneStepUsesEquipmentDisplayUnit` | `3dcd370b` | PASS | PASS |
| T25 | `StrengthPerformanceLikelihoodTest` | `weighted pull-up resolves total load with bodyweight priority and age uncertainty` | `e6056bf7` | PASS | PASS |
| T26 | `BCoachExerciseShadowCoverageTest` | `only exact canonical keys receive canonical tier and weighted pullup demands bodyweight` | `44476a1a` | PASS | PASS |
| T27 | `BCoachExerciseShadowCoverageTest` | `task exercise above twenty repetitions is observed but not run through e1rm curve` | `44476a1a` | PASS | PASS |
| T28 | `BCoachSquatRelativeShadowTest` | `multiset scenarios do not invent predictive performance for later sets` | `49d5ccef` | PASS | PASS |
| T29 | `BCoachExerciseShadowCoverageTest` | `reviewed RDL proxy relation cannot pretend to be a canonical posterior` | `44476a1a` | PASS | PASS |
| T30 | `BCoachObservedSquatProgressionTest` | `mismatched exercise track cannot source an increment` | `81b65dc6` | PASS | PASS |
| T31 | `BCoachExerciseShadowCoverageTest` | `ordinary barbell lift receives only unranked hold reps and configured-grid actions` | `44476a1a` | PASS | PASS |
| T32 | `BCoachExerciseShadowCoverageTest` | `time hold without mechanical load is task-specific and never uses RIR` | `44476a1a` | PASS | PASS |
| T33 | `BCoachExerciseShadowCoverageTest` | `no observed evidence is explicit unknown rather than zero performance` | `44476a1a` | PASS | PASS |
| T34 | `BCoachMultiobjectiveProductionTest` | `T34 approved Need is absent so extra sets never become an automatic reward` | `49d5ccef` | PASS | PASS |
| T35 | `BCoachMultiobjectiveProductionTest` | `T35 raw recent six-axis load is retained while residual decay remains missing` | `49d5ccef` | PASS | PASS |
| T36 | `BCoachMultiobjectiveProductionTest` | `T36 recovery maximum and mean remain distinct raw values` | `49d5ccef` | PASS | PASS |
| T37 | `BCoachMultiobjectiveProductionTest` | `T37 no candidate claims an approved risk-cap pass when no cap exists` | `49d5ccef` | PASS_WITHHELD | PASS |
| T38 | `BCoachMultiobjectiveProductionTest` | `T38 an unapproved fatigue cap does not hard-reject a reviewable candidate` | `49d5ccef` | PASS | PASS |
| T39 | `BCoachMultiobjectiveProductionTest` | `T39 execution axis reports engineering time without inventing a time-pressure cause` | `49d5ccef` | PASS | PASS |
| T40 | `BCoachMultiobjectiveProductionTest` | `T40 missing lambda epsilon and Need keep the candidate in review` | `49d5ccef` | PASS | PASS |
| T41 | `BCoachMultiobjectiveProductionTest` | `T41 rest-only mutation is not an active production candidate` | `49d5ccef` | PASS | PASS |
| T42 | `BCoachMultiobjectiveProductionTest` | `T42 set action changes both validity route and evaluated ramp value` | `49d5ccef` | PASS | PASS |
| T43 | `BBalancedFocusSearchPolicyTest` | `T43 complete goal vector changes the public whole-block candidate deterministically` | `4e506163` | PASS | PASS |
| T44 | `BCoachMultiobjectiveProductionTest` | `T44 fixed inputs equipment and unavailable bodyweight are traceable` | `49d5ccef` | PASS | PASS |

Additional production-route evidence in `4e506163` covers exact canonical fingerprints, proposal-integrity rejection, manual-kg-only mutation tolerance, stale history/exercise/link rejection, typed unknown/partial/not-applicable values, suppression of unapproved automatic kg, legacy schema compatibility, narrow 320 dp/large-text review UI, exact backup metadata round-trip, and the public accept → calibrate → save → reopen → calendar flow.

## Validation record

Environment: Windows, Android Studio JBR 21, Android SDK `C:/Users/pki08/AppData/Local/Android/Sdk`, repository `gradlew.bat`.

- Targeted production, persistence, backup, focus-sensitivity, integration, and narrow/large-text UI tests passed during implementation. One intermediate test-compilation failure used a Compose assertion unavailable in this dependency version; it was replaced with supported assertions before the passing runs.
- Full clean unit suite used temporary Windows Robolectric isolation (`forkEvery=1`, `maxParallelForks=2`) plus `--max-workers=2`; the two build-script lines were removed immediately afterward. `:app:testDebugUnitTest --rerun-tasks` — **PASS**, 2,806 tests, 0 failures, 0 errors, 25 skips, 430 XML suites, Gradle wall time 11m59s. Skips are existing private fixture-dependent cases; the new production integration is public and passed.
- `:app:lintDebug :app:assembleDebug --no-daemon --max-workers=2` — **PASS**, 3m03s. Lint: 0 errors, 209 warnings, 22 information findings (existing repository findings).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 74,383,963 bytes (70.94 MiB), SHA-256 `EBF043C7B0B138E91E219C970B8410F1890A5D191F1487662CDA1700A0B67A1F`.
- `adb devices -l` returned no connected device, so `connectedDebugAndroidTest` and real-device interaction were not run.
- Hosted CI: branch audit commit `3892a6bf20dc0eda5485901aac2eea96c2220c0b` passed the complete `Android Debug Build` workflow in run `43614686319`, including selected persistence/UI contracts, all debug unit tests, release Kotlin compilation, debug APK signing, and signed non-debuggable optimized APK validation. The starting `main` SHA `7191410502c1e67ec56c375c2c1d938aafa96e9a` also passed baseline run `43599468361`.

## Deliberately withheld automatic decisions

- No numeric Need-band deficit/excess optimization without reviewed task mappings and ranges.
- No recovery-cap rejection or residual decay without reviewed six-axis caps and decay.
- No cross-axis automatic winner from invented normalizers, lambda weights, or epsilon.
- No universal initial kg, Epley fallback, proxy-exercise posterior, cross-exercise kg transfer, or future bodyweight fabrication.
- No automatic non-main kg step. Unknown loads remain manual even after roster/set/rep acceptance.
- No claim that engineering time estimates are observed duration or that missed work proves time pressure.
- No real-device or remote backup-provider validation claim.
