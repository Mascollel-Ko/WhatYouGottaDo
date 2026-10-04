# Phase C18 — Tissue Projection and Incumbent Placement Audit

## Identity and scope

- C17 merge commit and C18 start: `bb8dfca386b8fd296417185a9c105ac2b5e94b0f`.
- C17 PR #9 ready-for-review: `2026-10-03T22:24:56Z`; merged at `2026-10-03T22:25:06Z`.
- C18 audit implementation/test checkpoint: `e32a4cf0f0242d4330e9cfc9a3a2d02cda04c396`.
- C18 audit checkpoint HEAD: `43be4f108c8c06b65393f8c8c42ed2413ea53b9c`.
- C18 exact tissue-projection implementation/test commit: `afb5a6a3c382d327d12ee98b3d3cc1a2220dd33b`.
- C17 merged-main Hosted CI run `37158374138`: success; 2,239 tests, 0 failures, 0 errors, 4 skips.
- C18 implementation/test Hosted CI run [`37169513778`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37169513778): success on `afb5a6a3c382d327d12ee98b3d3cc1a2220dd33b`.
- C17 baseline: CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0; B7 provenance-unclosed 11, target-unmet 9, regressed 1.
- Protocol/runtime/app after the exact tissue change: `3.53.0` / `RECORD_BASED_PLANNER_0.14.5_KOTLIN_1` / `0.5.1.5`.

C18 resolves the exact tissue-dose adapter omission, defines and tests a production-owned incumbent-placement shadow input, and reruns the C17 counterfactual. It does not change placement behavior, B7/B8, Power authority, or Combined scope.

## C18A — Exact tissue authority path

All five identities have exact canonical exercise metadata, planning metadata, tissue authority rows, and exact load-unit joins. None required new tissue relations or new physiology coefficients. The negative controls `barbell_back_squat`, `cable_rear_delt_fly`, and `ex_5ca7133f` follow the same exact-key metadata → load-unit → tissue projection path.

| Exact key | Canonical name | RCV rows | Exact dose authority | Before / root cause | After and required input |
|---|---|---:|---|---|---|
| `ex_28347c1f` | 버드독 | 14 | `BODYWEIGHT_REPETITION`, coefficient `0.25` on each exact relation | Metadata and all load-unit joins existed. `TissueDoseResolver` delegated to `BodyweightEffectiveLoadCalculator`, whose exact profile table has no Bird Dog row: `PROJECTION_ADAPTER_OMISSION`. C17 records have no bodyweight. | Adapter now consumes the exact RCV coefficient only for this exact stableKey/unit, with valid bodyweight and zero added load. A projection with bodyweight resolves; C17's actual missing-bodyweight input remains unresolved. Added load/assistance semantics remain unresolved. |
| `barbell_romanian_deadlift` | 루마니안 바벨 데드리프트 | 7 | `WEIGHTED_REPETITION` | Exact metadata, tissue rows, and joins exist. The C17 projection has only the provisional no-invented-load zero: `REQUIRED_RECORDED_LOAD_INPUT_ABSENT`. | A valid recorded weighted input resolves. Current C17 projection remains unresolved; no metadata repair was needed. |
| `dumbbell_chest_supported_row` | 덤벨 체스트 서포티드 로우 | 20 | `WEIGHTED_REPETITION`, exact dose profile | Exact authority and joins exist; current C17 projection lacks positive recorded load: `REQUIRED_RECORDED_LOAD_INPUT_ABSENT`. | A valid recorded weighted input resolves. Current C17 projection remains unresolved; no metadata repair was needed. |
| `barbell_reverse_curl` | 바벨 리버스 컬 | 11 | `WEIGHTED_REPETITION`, exact dose profile | Exact authority and joins exist; current C17 projection lacks positive recorded load: `REQUIRED_RECORDED_LOAD_INPUT_ABSENT`. | A valid recorded weighted input resolves. Current C17 projection remains unresolved; no metadata repair was needed. |
| `dumbbell_lying_triceps_extension` | 덤벨 라잉 트라이셉스 익스텐션 | 5 | `WEIGHTED_REPETITION`, exact dose profile | Exact authority and joins exist; current C17 projection lacks positive recorded load: `REQUIRED_RECORDED_LOAD_INPUT_ABSENT`. | A valid recorded weighted input resolves. Current C17 projection remains unresolved; no metadata repair was needed. |

The adapter refuses to infer assistance or added-load semantics from a bodyweight coefficient. Unknown keys still return unresolved. There is no generic bodyweight fallback. Exact positive-input and missing-input cases are covered by `PlanWeekTissueProjectionTest`, `TissueRcvContextModifierTest`, and `C18TissueProjectionAuthorityTest`; the generated baseline checks are covered by the tissue-prior tests. The regenerated numerical prior artifact is tied to RCV `1.2`; its values are generated through the repository authority pipeline, with no hand-edited CSV or new coefficient.

With the required valid fixture inputs, resolution improves from 4/5 before the adapter fix to 5/5 after it: the four weighted owners already resolved when given valid recorded loads, while Bird Dog did not consume its exact coefficient. In the actual C17 corpus, 0/5 of these keys resolve both before and after because its weighted rows have only provisional zero and Bird Dog has no bodyweight. Thus C18 fixes one runtime adapter gap, fixes zero metadata-authority gaps, and intentionally leaves those five current-input cases unresolved under the fail-closed contract.

The runtime calculation and planner protocol versions were advanced because exact recorded Bird Dog bodyweight inputs can now contribute a tissue projection where the prior adapter dropped an existing coefficient. The app version remains unchanged. The standard production-coverage SHA remains `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`.

## C18B — Production incumbent source audit and shadow contract

The current database stores `TrainingProgram.id`/`stableKey`, item `exerciseStableKey`, and program-relative `weekNumber`/`dayOfWeek`/`orderIndex`. It does not persist selection role, protocol/runtime lineage, or whether a placement was manually edited. `trainingSlot` is overloaded and is not an exact-role contract. Generation builds before `saveGeneratedProgram` receives the existing program ID, so the generator cannot currently read an exact incumbent source before placement. Date shifting preserves program-relative placement.

Therefore a complete production incumbent is **not currently available**, and C18 adds no duplicate persistence. The test-only typed `C18CanonicalIncumbentPlacementShadow` requires a compatible source identity and exact `(stableKey, selectionRole, week, day, order)` identity. It rejects missing/stale sources, missing roles, duplicate exact owner-week rows, removed owners, and role replacements. It returns KEEP only when independently supplied hard-feasibility evidence says the old position is valid; hard-invalid permits movement and unknown returns no recommendation. It grants no exercise, dose, prescription, quality, or displacement authority.

The real-case shadow fixtures are explicit historical placements built from the audit ledger; they are not extracted from a CONTROL program in production. A comparator perturbation leaves the incumbent index and EXPERIMENTAL input unchanged. The shadow recommendation counts are 12 KEEP, 2 MOVE because the old position is independently hard-invalid, and 18 NO DECISION because tissue feasibility remains unresolved. For `persona2_reviewed`, 14 shared owner-week rows already retain their positions and the new calibration row appends at day 1/order 4.

## C18C — C17 counterfactual reclassification

C18 reruns the same 32 placement rows across four cases. It records the exact placement, source stage, counterfactual gate facts, and incumbent-shadow outcome in [`c18-tissue-incumbent-placement-census.json`](c18-tissue-incumbent-placement-census.json).

| Result | Rows |
|---|---:|
| C17 proven unnecessary drift, still `PRIOR_PLACEMENT_VALID` | 12 |
| Old placement `PRIOR_PLACEMENT_HARD_INVALID` by an independent hard gate | 2 |
| `STILL_UNRESOLVED` | 18 |
| Actual EXP displacement authority proven | 0 |

The 12 prior drift rows remain valid, including the squat/rear-delt shifts and the persona4 calves order renumberings. Of the 20 earlier unresolved rows, two persona3 RDL rows can now be classified as hard-invalid because restoring them triggers an OFI/recovery gate; 18 still cannot be fully classified. This does not prove the actual EXP destination was necessary or authorized.

The exact per-case summary is:

| Case | Deltas | Prior valid | Prior hard-invalid | Still unresolved | Full-restoration maximum session / cap | Full-restoration hard findings |
|---|---:|---:|---:|---:|---|---|
| `persona0_mixed` | 6 | 4 | 0 | 2 | 1200 / 3600 sec | Day 1 OFI 80 in both weeks; Bird Dog requires missing bodyweight. |
| `persona0_reviewed` | 4 | 0 | 0 | 4 | 1020 / 1800 sec | RDL and chest-supported-row weighted tissue inputs are absent; no session-capacity overflow. |
| `persona3_reviewed` | 8 | 2 | 2 | 4 | 1020 / 1800 sec | Restored day 2 OFI 71 with `RECOVERY_DEBT_HIGH`; Bird Dog, RDL, and chest-supported row also lack required inputs. |
| `persona4_mixed` | 14 | 6 | 0 | 8 | 1200 / 5400 sec | Restored day 3 OFI 84 with `HIGH_FORCE_NEURAL_CAUTION` and `RECOVERY_DEBT_HIGH`; Bird Dog, RDL, reverse curl, and triceps extension lack required inputs. |

The row-level reclassification is shown below; every row originated at `INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY`, and all 32 have `actualDisplacementAuthorityProven=false`. The linked JSON retains each row's full counterfactual violations and gate snapshots.

All 32 rows retain exact material parity: set count, reps, load/load state, target effort, rest, and weekly frequency are unchanged. The differences are day/order placement only.

| Case | Week | Exact owner and role | Old → EXP placement | C17 classification | C18 prior-placement result |
|---|---:|---|---|---|---|
| `persona0_mixed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d3/o1 → d1/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona0_mixed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d3/o1 → d1/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona0_mixed` | 1 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | d1/o2 → d3/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona0_mixed` | 2 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | d1/o2 → d3/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona0_mixed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o3 → d3/o3 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona0_mixed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o3 → d3/o3 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona0_reviewed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d2/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona0_reviewed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d2/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona0_reviewed` | 1 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | d4/o1 → d2/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona0_reviewed` | 2 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | d4/o1 → d2/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona3_reviewed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d6/o1 → d4/o1 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona3_reviewed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d6/o1 → d4/o1 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona3_reviewed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d2/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | PRIOR_PLACEMENT_HARD_INVALID |
| `persona3_reviewed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d2/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | PRIOR_PLACEMENT_HARD_INVALID |
| `persona3_reviewed` | 1 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | d4/o1 → d2/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona3_reviewed` | 2 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | d4/o1 → d2/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona3_reviewed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o1 → d6/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona3_reviewed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o1 → d6/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d1/o2 → d3/o1 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona4_mixed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | d1/o2 → d3/o1 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona4_mixed` | 1 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS` | d3/o3 → d1/o3 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 2 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS` | d3/o3 → d1/o3 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d3/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | d3/o1 → d1/o1 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 1 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | d3/o2 → d1/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona4_mixed` | 2 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | d3/o2 → d1/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona4_mixed` | 1 | `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS` | d3/o4 → d1/o4 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 2 | `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS` | d3/o4 → d1/o4 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o3 → d3/o2 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | d1/o3 → d3/o2 | UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT | STILL_UNRESOLVED |
| `persona4_mixed` | 1 | `ex_5ca7133f#COVERAGE_CALVES` | d5/o1 → d5/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |
| `persona4_mixed` | 2 | `ex_5ca7133f#COVERAGE_CALVES` | d5/o1 → d5/o2 | UNNECESSARY_PLACEMENT_DRIFT | PRIOR_PLACEMENT_VALID |

The two persona3 RDL prior positions are hard-invalid under their restored-day OFI/recovery state; the tissue projection itself remains unresolved because no weighted RDL load exists. This OFI conclusion is independent of the tissue coefficient repair. The RDL sensitivity remains separate from production causality: with the actual case inputs, the restored day reports OFI 94 and `RECOVERY_DEBT_HIGH`. Omitting unsupported Power in the sensitivity case clears that OFI finding but leaves RDL tissue unresolved. No source-emitted causal edge proves Power caused the actual placement move; C18 does not change Power semantics.

The four accepted `BOUNDED_DAY_REBALANCER` events in `persona4_mixed` (two core and two calves) still carry `REBALANCE_OBJECTIVE`. That records a soft score change, not a hard constraint. Calves remain unnecessary order drift. Core still has no displacement authority; the full restoration has an OFI finding on the other day and unresolved tissue inputs. Session-capacity overflow was absent in every full restore.

## Verification and limits

- Routes remain CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0.
- B7 remains provenance-unclosed 11, target-unmet 9, regressed 1. B7/B8 source predicates were not modified.
- B1-B6, placement ranking, and output prescriptions remain unchanged; only the exact tissue projection path and regenerated tissue-prior versioned artifact changed.
- Ordering remains B1-B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9; build accounting remains CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0, with preflight 0 / 0 / 0 / 0.
- Standard coverage SHA-256 remains `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`; C18 census: 32 deltas, 12 prior-valid, 2 hard-invalid, 18 unresolved, 0 displacement authority.
- Local compile and the focused projection/shadow suites pass. The focused tissue/shadow set completed 54 tests; the production coverage audit also passed. After refreshing two snapshot checksums for the versioned generated prior, `TissueEducationalCopyContractTest` (5) and `TissueEffectiveBaselineRuntimeTest` (6) pass.
- The full Windows local suite was attempted. It completed 1,491 tests before JBR 21 crashed in `robolectric-nativeruntime.dll` during Robolectric SQLite `nativePrepareStatement` from WorkManager/`LocalRecoveryScheduler.schedule`; 0 test errors and 3 skips had been reported at the interruption. The two failures were stale whole-artifact checksum expectations from the intentional RCV protocol regeneration and now pass in isolation. This incomplete Windows run is not a full-suite pass; Hosted Linux CI is authoritative.
- C18 Hosted CI: 2,258 tests, 0 failures, 0 errors, 4 skips. Protocol validation, Community/Cloud contracts, whitespace, coverage, APK assembly, signer validation, and upload passed.
- Coverage artifact digest: `sha256:5c65008a2ccf0dd472efd5119a28281302266bd6d439166b58b15185dc1772dc` (artifact ID `11289879590`). APK is 68,692,167 bytes, SHA-256 `F44A41DE481DB8E70657A50254426311AC2EBA964A5F751AB4EA6F1E44B05E4E`; uploaded APK artifact digest: `sha256:6cba3f6efd0f52cd958fb11781d6b565468ca864f3451269afac336d0cd28b45` (artifact ID `11289969524`).
- The final docs-only Hosted CI is required on the final PR HEAD; its run result is recorded in the C18 completion report.

C18 provides no incumbent production source today because persisted exact role and generation lineage are absent and generation is not passed the current program before placement. No placement repair or derived displacement authority is enabled. C19 must first establish a production-owned exact incumbent contract, then preserve valid placements and move only against proven hard constraints with exact source-stage causality.
