# B-Coach v2 multi-objective production completion

## Scope and release identity

- Branch: `feat/b-coach-v2-global-optimizer`
- Base commit: `f3fcd179e0701bd83859714f72e67ed0576c0701`
- Engine: `B_COACH_V2_GLOBAL_OPT`
- Frozen policy: `B_COACH_V2_POLICY_2026_11_15`
- Coefficient provenance: `PRODUCT_HEURISTIC_NOT_CLINICAL`
- Request authority: `B_Coach_v2_Implementation_Codex_Instruction_2026-11-15.md` and the pasted Codex request. The design/equation documents are implementation references.

This replaces the earlier review-gate scorer with one production path. The existing safe B plan and joint optimizer remain seed providers; v2 resolves typed load evidence, computes complete four-week Need and dose, rejects hard-constraint failures, scores eight finite losses in three scenarios, forms a Pareto frontier, and recommends by minimax regret. Nothing is persisted until the user selects a concrete candidate and explicitly approves it.

## Production call chain

`PlanScreen` → `TrainingRepository` → `V22ProgramPlanningService` → safe/joint seed generation → `BCoachV2LoadCandidateResolver` → `BCoachV2DoseEvaluator` / `BCoachV2ScheduleLoad` → `BCoachV2GlobalSelector` → review dialog → exact candidate approval → transactional evidence/structure replay → save/reopen/calendar flow.

The proposal fingerprint binds the baseline, recommended candidate, full request, evidence cutoff, history, relevant exercise metadata, and linked evidence. The accepted structure fingerprint excludes only program naming and explicit post-approval kg calibration. Exercise identity, weekday, order, sets, repetitions, rest, and evidence drift reject persistence.

## Frozen policy boundary

| Area | Production rule |
|---|---|
| Goal importance | Missing values use the existing B recommendation when confirmed history exists; otherwise `3`. All four effective values are normalized. |
| Need | Task-level center `D0`, up to four complete history weeks, max 0.5 personal blend, stage/recovery multiplier, and `[floor(0.85D), ceil(1.20D)]`. PEAK is explicitly exempt with zero Need. |
| Effective dose | Canonical muscle/core/badminton relations; repeated aliases use max credit per muscle. Unknown resistance receives `0.75` set credit, never zero. |
| Load | Exact stableKey only. Tier A is four frozen keys; Tier B uses a current local posterior; Tier C holds a recent same-exercise load; Tier D uses a non-load metric; otherwise typed calibration. |
| Weighted pull-up | A future/current bodyweight is never fabricated. Without current bodyweight, a model-derived total-system load is downgraded to calibration; a recent same-exercise external load may still be held. |
| Recovery | Six canonical axes, four-day kernels, explicit planned/recent separation, q75 normalization, and max/mean pressure diagnostics. No clinical cap or injury probability is claimed. |
| Objectives | Goal deficit `.40`, excess `.09`, progression `.16`, recovery `.14`, ramp `.07`, execution `.06`, plan change `.04`, uncertainty `.04`; sum `1.0`. |
| Scenarios | `LOW_STRESS`, `BASE`, `HIGH_STRESS`; Need, indirect credit, recovery kernels, and model capacity/RIR vary by scenario. |
| Selection | Hard-constraint filter → Pareto frontier → three-scenario minimax regret → deterministic ties by base loss, anchors, change count, and canonical hash. |
| Search budget | Frozen ceiling: beam `10`, rounds `12`, candidate evaluations `4000`, finalists `12`, displayed alternatives `2`. Current local action lattice changes set/repetition prescriptions while preserving fixed focus, rest, and order; the existing joint seed supplies exercise/day structure. |
| UI/persistence | The review shows the exact disclaimer, recommended candidate plus at most two alternatives, axis/scenario values, and requires select + approve. `INPUT_CONFLICT` is not silently saved. |

Exact visible disclaimer:

> 이 추천은 운동 기록·선택한 목표와 시범 계산 규칙을 바탕으로 생성한 훈련 계획입니다. 일부 자극·회복 점수는 아직 과학적으로 검증된 개인 예측값이 아니며, 미확정 중량은 직접 입력할 수 있습니다.

## Concrete three-candidate decision fixture

The following values were emitted by `T44` from the production selector with identical inputs: a two-week squat fixture, confirmed same-exercise `100 kg` evidence, effective goal importance `3/3/3/3`, and cutoff `2026-11-15`. Values are rounded here only for readability; canonical hashes and stored doubles are unrounded.

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
| T14 | Missing RPE keeps direct history without model fabrication. | PASS |
| T15 | Stale posterior downgrades one exercise without blocking the plan. | PASS |
| T16 | Stale model cannot overwrite a recent same-exercise hold. | PASS |
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
| T31 | Tier C holds recent same-stableKey load or calibrates. | PASS |
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

## Validation record

Environment: Windows, Android Studio JBR 21, Android SDK `C:/Users/pki08/AppData/Local/Android/Sdk`.

- `compileDebugKotlin` plus targeted `BCoachMultiobjectiveProductionTest` and `BCoachProductionReviewDialogUiTest`: **PASS**.
- Full `testDebugUnitTest --rerun-tasks --no-daemon --max-workers=2`: **PASS**, 2,834 tests, 0 failures, 0 errors, 25 skips, 430 XML suites, 12m01s. Windows Robolectric workers used temporary `forkEvery=1` / `maxParallelForks=2` isolation after reproducing a `robolectric-nativeruntime.dll` access violation; both temporary lines were removed immediately after the passing run.
- `lintDebug assembleDebug compileReleaseKotlin compileDebugAndroidTestKotlin --no-daemon --max-workers=2`: **PASS**, 4m49s. Lint: 0 errors, 209 warnings, 22 information findings.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 74,392,297 bytes, SHA-256 `365E0CD748CD67453382E742D7197BA4932989BF5528AEC5F1613087DFA2086D`.
- The official `tools/generate_metadata_analysis_contract_audit.ps1 -CompatibilityOnly` exporter regenerated the current `progressMetricType` consumer inventory; the final contract test passed.
- `adb devices -l` returned no connected device, so `connectedDebugAndroidTest` and real-device interaction were not run.

## Explicit limitations

- Need, recovery, effort, and uncertainty coefficients are frozen product heuristics, not clinical or injury predictions.
- Unknown starting loads remain calibration-required; exact kg is emitted only from user-fixed load, current exact-stableKey model evidence with a known equipment grid, or a recent same-exercise hold.
- The optimizer does not invent future bodyweight, unavailable equipment, cross-exercise capacity, or independent follow-up-set success probabilities.
- Real-device interaction is outside the local JVM/Compose validation unless a device is connected.
