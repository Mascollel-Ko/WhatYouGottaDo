# B-Coach v2.2.1 precision repair completion

## Scope and release identity

- Branch: `feat/b-coach-v2-global-optimizer`
- v2.2.1 starting commit: `03036a9b34b266ca24e2783724ec3d43dd64f82c`
- Original v2.2 base commit: `4aef2bc0fed3969c53bdc4bf04106db07ea1047e`
- Engine: `B_COACH_V2_2_1_GLOBAL_OPT`
- Frozen policy: `B_COACH_V2_2_1_POLICY_2026_11_15`
- Coefficient provenance: `PRODUCT_HEURISTIC_NOT_CLINICAL`
- Request authority: `WhatYouGottaDo — B-Coach v2.2.1 정밀 보완 Codex 작업 지시`; `BCOACH_V2_2_1_REPAIR_NOTES.md` supplies [주1]-[주4]. The earlier v2.2 design and implementation instructions remain reference material.

This keeps the v2.2 production optimizer, Need model, six recovery axes, eight objectives, three scenarios, Pareto frontier, minimax regret, Room 42/backup formats, and explicit candidate approval. v2.2.1 repairs three verified evidence paths without changing those policies. Nothing is persisted until the user selects a concrete candidate and explicitly approves it.

## v2.2.1 precision repair record

- Set-level `loadSource` is now authoritative. `USER_FIXED` preserves only that set's positive finite kilograms; known automatic sources are recalculated even if the legacy item source says manual. The item source is consulted only for `LEGACY_UNKNOWN`. Invalid fixed kilograms fail closed to typed calibration rather than being silently replaced.
- Candidate set addition clears copied kilograms, source, RIR, intensity, and evidence to a calibration-required set. Removing a tail that contains a user-fixed set is rejected. Repetition mutation retains fixed kilograms but runs capacity/RIR evaluation again. Mixed fixed/automatic sets retain the exact source split through approval, Room persistence, reopen, and calendar application.
- Calendar application now requires and uses the same canonical metadata repository already held by `TrainingRepository`. The live badminton objective catalog, runtime metadata, cutoff, generic-session authority, direct-drill de-duplication, and six-axis aggregation therefore match generation. Missing metadata fails explicitly before workout mutation.
- Historical and planned effective dose share one set-dose function and one relative-intensity credit. Historical relative intensity uses the highest valid, cutoff-bounded same-`stableKey` e1RM from the existing load resolver/model evidence; it is computed once per optimizer input, not per candidate. Missing RPE remains a labeled neutral-effort uncertainty and never becomes invented `RIR=0`; missing capacity keeps neutral relative-intensity credit with an explicit reason code.
- Proposal validation recomputes a fingerprint with the engine/policy/owner stored in the accepted decision. Already accepted v2.2 proposals therefore remain replay-valid after the v2.2.1 version bump while tampered, stale-history, stale-metadata, and structurally edited proposals still fail.

## v2.2 correction record

- Capacity and training load are separate. All valid completed rows for the exact `stableKey` compete by e1RM; the highest is retained even when it is more than 20% above the center. Invalid units, repetitions, non-finite values, and duplicates are excluded. A no-RPE single is the lifted weight itself (`180×1 -> 180 kg`), while other no-RPE rows are labeled completed-repetition lower bounds.
- A current personal repetition curve remains preferred. When direct evidence exceeds the stored model center, capacity bounds are scaled to the higher valid direct capacity without discarding the personal curve. Otherwise Epley uses `capacity / (1 + (reps + RIR)/30)`.
- First-set training load is `0.97 * capacity * relativeLoad`. A known equipment grid floors the result and creates a prescribed load; a missing grid retains the unrounded `estimatedLoadKg` and never copies the historical load. For `180×1`, no RPE, target `12@RIR3`, no grid, the result is `116.4 kg estimated`, not `180×12`.
- Default follow-up factors are `0.985` for sets 2-3 and `0.97` for sets 4-5, floored again to the actual equipment grid. Two or more matching same-exercise sessions replace the default with the median observed set-to-first-set ratio.
- `setPrescriptions` is authoritative. `USER_FIXED`, `PERSONAL_MODEL`, `DIRECT_HISTORY_ESTIMATE`, `HISTORY_HOLD`, `CALIBRATION_REQUIRED`, `NON_LOAD_METRIC`, and `LEGACY_UNKNOWN` are persisted set sources; `EXPLICIT_LOAD` alone is not manual authority. An automatic positive load reaches calendar rows with `manualWeight=false`; an actual user weight edit changes the source to `USER_FIXED` and clears stale calculated RIR/intensity.
- Each resistance set stores `freshRir` and fatigue-adjusted `expectedRir` (working RIR), relative intensity, estimated load, and evidence version. The same set-level working RIR/intensity maps feed both dose and recovery. The working-RIR heuristic uses set index, repetitions, relative intensity, and rest, with bounded fatigue rather than assuming that a back-off load equalizes all sets.
- Effective dose uses calculated working RIR before target RIR. Effort credit is continuous: `1.0` through RIR 4, linear to `.75` at RIR 6, linear to `.50` at RIR 8, then `.50`. Strength and hypertrophy receive bounded relative-intensity credit; hypertrophy is not made directly proportional to absolute kilograms.
- Recovery is now `Σ(axis coefficient × set adjustment)` rather than set count alone. The adjustment uses relative intensity, repetitions/exposure, and working RIR; each factor is bounded to `0.5..1.5`. Recent resistance history uses actual weight/repetitions/RPE-derived effort, and the existing six axes, four-day kernels, scenarios, and application-date refresh remain intact.
- Confirmed badminton duration, RPE, and direct drill metadata are converted through `BadmintonPracticeLoadCalculator`, added to same-day resistance load, and distributed over the six axes. A generic session row is authoritative for total duration/load when detailed drills share its session identity, preventing generic-plus-drill double counting. Badminton history is part of input/history fingerprints and therefore invalidates candidate caches.
- Room schema 42 adds the six provenance fields to `training_program_item_sets` and `workout_sets`. CSV restore schema 14 / backup format 15 / program snapshot schema 6 carry the same fields; older rows import without guessing and become `LEGACY_UNKNOWN` at persistence.
- Candidate search resolves loads after every set/repetition mutation. Its local cache key binds the full raw prescription, request/history/equipment input fingerprint, model source/evidence versions, staleness, and RIR-policy version. Progression scoring reads per-set RIR/intensity maps.
- Need personalization now admits a week only when that specific task has positive related evidence. Unrelated lower-body weeks therefore cannot lower chest/back/direct-core Need, while a new plan's zero dose still produces a real deficit.
- Recovery dates use an explicit planning start when supplied; otherwise the audit records and uses the next Monday after the cutoff. Monday remains `1` and Sunday `7`. Calendar application recalculates all three recovery scenarios for the actual applied start date and stores them under `applicationRecoveryAudit` without pretending that this recovery-only refresh reran Pareto selection.
- `V22ProgramPlanningService` passes the linked-evidence-enriched `coachRequest` through skeleton construction, model reading, optimization, and diagnostics. Accepted alternatives store their own set-load instructions and full fingerprint. Post-approval load edits are preserved and mark `scoresReflectCurrentPrescription=false` unless the plan is regenerated.

Epley regression fixture for capacity `180 kg`, `2.5 kg` grid, factor `0.97`: `5@RIR3=137.5`, `8@RIR3=127.5`, `10@RIR3=120`, `12@RIR3=115`, and `8@RIR4=122.5 kg`. The default three-set `8@RIR3` prescription is `127.5 / 125 / 125 kg`.

## Production call chain

`PlanScreen` → `TrainingRepository` → `V22ProgramPlanningService` → safe/joint seed generation → `BCoachV2LoadCandidateResolver` → `BCoachV2DoseEvaluator` / `BCoachV2ScheduleLoad` → `BCoachV2GlobalSelector` → review dialog → exact candidate approval → transactional evidence/structure replay → save/reopen/calendar flow.

The proposal fingerprint binds the baseline, recommended candidate, full request, evidence cutoff, history, relevant exercise metadata, and linked evidence. The accepted structure fingerprint excludes only program naming and explicit post-approval kg calibration. Exercise identity, weekday, order, sets, repetitions, rest, and evidence drift reject persistence.

## Frozen policy boundary

| Area | Production rule |
|---|---|
| Goal importance | Missing values use the existing B recommendation when confirmed history exists; otherwise `3`. All four effective values are normalized. |
| Need | Task-level center `D0`, up to four complete history weeks, max 0.5 personal blend, stage/recovery multiplier, and `[floor(0.85D), ceil(1.20D)]`. PEAK is explicitly exempt with zero Need. |
| Effective dose | Canonical muscle/core/badminton relations; repeated aliases use max credit per muscle. Unknown resistance receives `0.75` set credit, never zero. |
| Load | Exact stableKey only. Tier A/B use current personal models and curves. Direct same-exercise history supplies the highest valid e1RM. A known grid creates a down-rounded prescription; a missing grid creates a labeled estimate, never a history copy. Tier D uses a non-load metric; otherwise typed calibration. |
| Weighted pull-up | A future/current bodyweight is never fabricated. Without current bodyweight, a model-derived total-system load is downgraded to calibration; external added load is never confused with total-system load. |
| Recovery | Six canonical axes, per-set `0.5..1.5` adjustment, actual resistance/badminton history, four-day kernels, next-Monday or explicit-start calendar mapping, planned/recent separation, q75 normalization, and max/mean pressure diagnostics. No clinical cap or injury probability is claimed. |
| Objectives | Goal deficit `.40`, excess `.09`, progression `.16`, recovery `.14`, ramp `.07`, execution `.06`, plan change `.04`, uncertainty `.04`; sum `1.0`. |
| Scenarios | `LOW_STRESS`, `BASE`, `HIGH_STRESS`; Need, indirect credit, recovery kernels, and model capacity/RIR vary by scenario. |
| Selection | Hard-constraint filter → Pareto frontier → three-scenario minimax regret → deterministic ties by base loss, anchors, change count, and canonical hash. |
| Search budget | Frozen ceiling: beam `10`, rounds `12`, candidate evaluations `4000`, finalists `12`, displayed alternatives `2`. Current local action lattice changes set/repetition prescriptions while preserving fixed focus, rest, and order; the existing joint seed supplies exercise/day structure. |
| UI/persistence | The review shows actual or `~estimated` load, target/fresh/working RIR, source, and equipment-grid requirement inside the scrollable prescription summary; it retains at most two alternatives and requires select + approve. Room/calendar/CSV preserve set provenance. `INPUT_CONFLICT` is not silently saved. |

Exact visible disclaimer:

> 이 추천은 운동 기록·선택한 목표와 시범 계산 규칙을 바탕으로 생성한 훈련 계획입니다. 일부 자극·회복 점수는 아직 과학적으로 검증된 개인 예측값이 아니며, 미확정 중량은 직접 입력할 수 있습니다.

## Historical v2 three-candidate decision fixture

The following values were emitted by `T44` before the v2.1 prescription correction. They are retained only as the v2 baseline comparison; v2.1 deliberately changes versioned fingerprints and may change losses when an equipment grid permits calculated loads.

| Candidate | Prescription and load evidence | Eight BASE losses `(deficit, excess, progression, recovery, ramp, execution, change, uncertainty)` | Scenario weighted losses `(low, base, high)` | Pareto / regret | Result |
|---|---|---|---|---|---|
| `C01_32307a7021` `SAFE_BASELINE` | `barbell_back_squat`, week 1/day 1, `2×5`, `100 kg`, `EXPLICIT_LOAD`, Tier C same-stableKey hold | `(0.416667, 0, 0.35, 0.284062, 0.265, 0, 0, 0.4)` | `(0.276611, 0.296985, 0.300284)` | yes / `0.000000` | recommended |
| `C02_a57f50abd5` `JOINT_SEED` | same identity/day, `3×5`, `100 kg`, same evidence | `(0.375, 0, 0.35, 0.348471, 0.2525, 0, 0.25, 0.4)` | `(0.294565, 0.298461, 0.301848)` | yes / `0.017954` | alternative 1 |
| `C04_fce0689849` `SAFE_BASELINE>REMOVE_ONE_SET_barbell_back_squat` | same identity/day, `1×5`, `100 kg`, same evidence | `(0.458333, 0, 0.35, 0.186175, 0.265, 0, 0.25, 0.4)` | `(0.298649, 0.309948, 0.312690)` | yes / `0.022038` | alternative 2 |

This fixture intentionally recommends the unchanged plan: the three-set alternative reduces deficit but raises recovery/change loss, while the one-set alternative lowers recovery but raises deficit/change loss. That is a valid global result, not a forced-change rule.

## Counterexample matrix T01–T44

All rows are executable methods in `BCoachMultiobjectiveProductionTest`. `PASS` means the final local run completed the assertion; it does not promote product heuristics to clinical claims.

| ID | Covered counterexample | Result |
|---|---|---|
| T01 | Linked evidence remains source-program scoped. | PASS |
| T02 | Copied plan identity cannot mutate recorded evidence. | PASS |
| T03 | Fixed weekdays are a hard constraint across every program week. | PASS |
| T04 | Session-time overflow is checked on every week/day. | PASS |
| T05 | Unreviewed automatic exercise is rejected and baseline preserved. | PASS |
| T06 | Missing goal values become finite defaults/recommendations, never zero. | PASS |
| T07 | Fixed-block focus boost is exactly `0.25`. | PASS |
| T08 | BALANCED uses the normalized effective goal vector. | PASS |
| T09 | PEAK stores zero Need and is score-exempt. | PASS |
| T10 | Select → approve → calibrate → persistence replay preserves exact structure. | PASS |
| T11 | Volume patterns retain distinct stage sequences. | PASS |
| T12 | Search cannot rewrite a fixed block focus. | PASS |
| T13 | Tier A is restricted to four exact stable keys. | PASS |
| T14 | Missing RPE creates a conservative typed estimate without copying history. | PASS |
| T15 | Stale posterior downgrades one exercise without blocking the plan. | PASS |
| T16 | Stale model is reported while usable direct history remains a typed estimate. | PASS |
| T17 | Canonical curve is monotone and inversion deterministic. | PASS |
| T18 | A `1.25 kg` plate becomes a `2.5 kg` total barbell grid. | PASS |
| T19 | Model load stays below capacity and is viable in all capacity scenarios. | PASS |
| T20 | Nine and eleven effective sets both satisfy Need `[8,12]`. | PASS |
| T21 | Unconfirmed/unusable rows cannot create Tier C load. | PASS |
| T22 | Existing explicit manual load is preserved exactly. | PASS |
| T23 | RPE policy keeps a probability distribution over RIR. | PASS |
| T24 | Barbell uses `2×plate`; dumbbell/machine use display units. | PASS |
| T25 | Missing future bodyweight is not fabricated; weighted pull-up model load calibrates. | PASS |
| T26 | Local posterior is consumed only for the exact same stableKey. | PASS |
| T27 | Repetitions above 20 do not enter the canonical curve. | PASS |
| T28 | Metadata contains no invented multi-set success probability. | PASS |
| T29 | Duplicate muscle aliases use max, not additive credit. | PASS |
| T30 | Another exercise history cannot replace same-exercise evidence. | PASS |
| T31 | Tier C estimates from recent same-stableKey evidence or calibrates. | PASS |
| T32 | Duration work uses a non-load metric without forced RIR. | PASS |
| T33 | No history still yields a concrete four-week review proposal. | PASS |
| T34 | Hypertrophy importance 4 blends to center 10 and Need `[8,12]`. | PASS |
| T35 | Confirmed history and future planned load stay separate in recovery. | PASS |
| T36 | Spaced sessions have no higher interference than adjacent sessions. | PASS |
| T37 | No clinical recovery cap or injury probability is emitted. | PASS |
| T38 | Recovery is a finite soft objective, not an invented hard ban. | PASS |
| T39 | Time upper bound includes setup, active, rest, warm-up, and factor. | PASS |
| T40 | All eight objectives are finite in all three scenarios. | PASS |
| T41 | Rest/order are preserved and are not search actions. | PASS |
| T42 | Set mutation reevaluates Need, time, recovery, and ramp. | PASS |
| T43 | Reversed goal importance changes effective input and is explainable. | PASS |
| T44 | Identical inputs reproduce hashes, recommendation, alternatives, and audit. | PASS |

## Worked checks S01–S08

| ID | Worked check | Result |
|---|---|---|
| S01 | No history: missing four-goal vector becomes `3/3/3/3`. | PASS |
| S02 | Hypertrophy importance 4: `D0=10`, Need `[8,12]`. | PASS |
| S03 | Strength importance 5: `D0=6`, Need `[5,8]`. | PASS |
| S04 | Primary + secondary duplicate uses max; another secondary muscle contributes `0.5`. | PASS |
| S05 | Ramp: `10→12 = 0`, `10→15 = 0.6`. | PASS |
| S06 | Eight objective coefficients sum to `1.0`. | PASS |
| S07 | Given losses select candidate B by minimax regret (`A=.20`, `B=.15`). | PASS |
| S08 | Missing resistance is typed calibration and never displayed as `0 kg`/`null`. | PASS |

An additional persistence test rejects both evidence drift (`B_COACH_ACCEPTANCE_HISTORY_STALE`) and post-approval structural edits (`B_COACH_ACCEPTANCE_STRUCTURE_STALE`). The Compose UI test verifies the exact disclaimer, concrete prescription and axis detail, selectable alternatives, and explicit approval.

## v2.1 counterexamples T45-T54

| ID | Covered counterexample | Result |
|---|---|---|
| T45 | Epley intermediate calculation, 0.97 factor, down-grid, and all five 180 kg fixtures. | PASS |
| T46 | A personal repetition curve wins over Epley and capacity remains 180 kg. | PASS |
| T47 | No-RPE direct history becomes a labeled completed-reps lower bound, not a failure claim. | PASS |
| T48 | User-fixed `60/80/100 kg` sets remain distinct and item-level weight cannot overwrite them. | PASS |
| T49 | Default automatic three-set output is `127.5/125/125 kg`. | PASS |
| T50 | `8 -> 10` reps recomputes load, per-set RIR, and progression score; repeated input is deterministic. | PASS |
| T51 | Two lower-body weeks personalize quads but leave chest Need at its default; zero chest plan dose still creates deficit. | PASS |
| T52 | Wednesday/Sunday generation maps to the same next Monday; explicit start changes recovery and input fingerprint. | PASS |
| T53 | Each chosen alternative persists its own set loads/fingerprint; later manual kg edits invalidate the old score claim. | PASS |
| T54 | Two matching multi-set sessions replace the default follow-up factor with the observed median. | PASS |

## v2.2 counterexamples 01-46

The new assertions are grouped by the six authority sections while retaining every numbered case. They run in `BCoachMultiobjectiveProductionTest`, `V22BadmintonHistoryIntegrationTest`, `ProgramEffortPersistenceTest`, and `TrainingDatabaseMigrationTest`.

| IDs | Coverage | Result |
|---|---|---|
| 01-10 | Highest valid e1RM, >20% high record retention, no-RPE single, RPE/RIR capacity, `180×1 -> 116.4 estimated`, no history copy, grid floor, personal curve priority, unchanged capacity, weighted-pull-up guard. | PASS |
| 11-17 | Three per-set loads/RIR values, 8→10 recomputation, `60/80/100` manual preservation, automatic source, fresh vs working RIR, observed back-off personalization, deterministic replay. | PASS |
| 18-23 | Working-RIR effort, target-vs-calculated precedence, strength relative intensity, non-proportional hypertrophy load, continuous RIR boundaries, non-load/core-compatible handling. | PASS |
| 24-30 | Repetition/intensity/effort axis differences, speed/deceleration exposure, `0.5..1.5` bounds, nonzero unknown-set load, actual-start recovery refresh. | PASS |
| 31-36 | Real badminton duration/RPE, same-day combination, generic/drill de-duplication, drill-axis metadata, no-history generation, fingerprint/cache invalidation. | PASS |
| 37-42 | Automatic/manual flags, user edit transition, program reopen, calendar propagation, CSV round trip, old backup parse. | PASS |
| 43 | Room 41→42 migration and `LEGACY_UNKNOWN` defaults for both set tables. | PASS (compiled; device execution depends on connection) |
| 44-46 | Approved alternative prescription/RIR preservation, approval/structure replay guard, progression/copy/calendar regression suite. | PASS |

## v2.2.1 repair counterexamples

| Authority | Covered counterexample | Result |
|---|---|---|
| [주1] / T60 | Mixed `USER_FIXED`/automatic sets, item-manual with set-auto, legacy fallback, fixed-set add/remove/repetition mutation, invalid fixed load, and select/approve replay. | PASS |
| [주1] / persistence | Mixed set kilograms, manual flags, and sources survive Room save/reopen and calendar application. | PASS |
| [주2] | Direct-drill-only history, generic session plus drill de-duplication, two application start dates, matching history fingerprint/count, and missing-metadata fail-before-mutation. | PASS |
| [주3] / T61 | Equal plan/history relative intensity, no-RPE labeling, missing-reference neutral fallback, highest valid reference, future-row exclusion, candidate-independent reference, and Need regression. | PASS |
| Compatibility | An accepted v2.2 proposal validates under its recorded version tuple after the v2.2.1 version bump. | PASS |

## Validation record

Environment: Windows, Android Studio JBR 21, Android SDK `C:/Users/pki08/AppData/Local/Android/Sdk`.

- Final normal-worker targeted and Gradle run (`BCoachMultiobjectiveProductionTest`, `BCoachApplicationRecoveryMetadataTest`, `ProgramEffortPersistenceTest`, `lintDebug`, `assembleDebug`, `compileReleaseKotlin`, `compileDebugAndroidTestKotlin`): **PASS**, 79 targeted tests, 0 failures, 0 errors, 0 skips, 6m33s. Lint: 0 errors, 209 warnings.
- Final full `testDebugUnitTest --no-daemon --max-workers=2`: **PASS**, 2,857 tests, 0 failures, 0 errors, 25 skips, 432 XML suites, 9m24s with temporary `forkEvery=1` / `maxParallelForks=2` worker isolation. The first unisolated run terminated in the known Windows `robolectric-nativeruntime.dll` `EXCEPTION_ACCESS_VIOLATION`, outside the JVM and without an assertion failure. Both temporary isolation lines were removed immediately after the final full pass; they are not part of the delivered diff.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 76,434,235 bytes, SHA-256 `A3D4D3BFD415302D223B454AFCE425DF29252BF17BCAE0891BA270FC0573C44F`.
- `adb devices -l` returned no connected device, so `connectedDebugAndroidTest` and real-device interaction were not run.

## Explicit limitations

- Need, recovery, effort, and uncertainty coefficients are frozen product heuristics, not clinical or injury predictions.
- Unknown starting loads without usable capacity remain calibration-required. Usable exact-stableKey capacity with an unknown equipment increment produces a visible estimate, not a prescription. Direct-history Epley estimates remain labeled lower bounds when RPE is absent.
- `freshRir`/working-RIR and per-set recovery adjustments are explicit bounded product heuristics, not observed physiology. They are invalidated by a user load edit until recalculated.
- Badminton drill-axis distribution depends on reviewed exercise metadata. When metadata is missing, conservative nonzero systemic/local or generic axis fallbacks are used rather than fabricating a detailed exposure.
- The optimizer does not invent future bodyweight, unavailable equipment, cross-exercise capacity, or independent follow-up-set success probabilities.
- Real-device interaction is outside the local JVM/Compose validation unless a device is connected.
- Calendar application refreshes the recovery axis for the actual start date, but it does not rerun candidate search or Pareto/minimax selection after approval.
