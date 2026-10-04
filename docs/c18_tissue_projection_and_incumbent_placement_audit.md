# Phase C18 — Tissue Projection and Incumbent Placement Audit

## Identity and scope

- C17 merge commit / C18 start: `bb8dfca386b8fd296417185a9c105ac2b5e94b0f`.
- C18 audit implementation/test checkpoint: `e32a4cf0f0242d4330e9cfc9a3a2d02cda04c396`.
- C18 audit documentation commit: pending.
- Final C18 implementation/test commit: pending the exact-key tissue repair and final census rerun.
- C17 merged-main Hosted CI run `37158374138`: success; 2,239 tests, 0 failures, 0 errors, 4 skips; signer and APK upload passed.
- C17 baseline: CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0; B7 provenance-unclosed 11, target-unmet 9, regressed 1.
- C17 placement census: 32 deltas, 16 case/owner/role pairs, 12 avoidable-drift classifications, 20 unresolved, 0 proven necessary authorized displacements. All first diverged at `INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY`.
- Protocol/runtime/app at the C18 start: `3.52.0` / `RECORD_BASED_PLANNER_0.14.4_KOTLIN_1` / `0.5.1.5`.

C18 is limited to exact tissue-projection completeness, a shadow contract for production-owned incumbents, and a rerun of the C17 counterfactual. It does not change placement behavior, B7/B8, Power authority, or Combined scope.

## C18A — Exact tissue authority path

The five keys have exact canonical and planning identities and complete RCV mappings. They do not share one failure cause. The bundled authority is joined by exact `stableKey`, then its `loadUnitStableKey` resolves to an exact joint complex and recovery class. The negative controls `barbell_back_squat`, `cable_rear_delt_fly`, and `ex_5ca7133f` use the same successful exact-key path.

| Exact key | Canonical name | RCV authority rows | Dose basis | Exact relation and current C17 input finding |
|---|---|---:|---|---|
| `ex_28347c1f` | 버드독 | 14 | `BODYWEIGHT_REPETITION` | All exact load-unit joins resolve; every authority row carries bodyweight coefficient `0.25`. Runtime dose resolution currently delegates to `BodyweightEffectiveLoadCalculator`, whose exact profile table has no row for this key. C17 also supplies a provisional zero-load row without a bodyweight value. |
| `barbell_romanian_deadlift` | 루마니안 바벨 데드리프트 | 7 | `WEIGHTED_REPETITION` | Exact RCV and load-unit joins exist. The C17 row has `weightKg=0` from `PROVISIONAL_RPE_NO_INVENTED_LOAD`; no recorded external load is present. |
| `dumbbell_chest_supported_row` | 덤벨 체스트 서포티드 로우 | 20 | `WEIGHTED_REPETITION` | Exact RCV and load-unit joins exist, as does an exact dose profile. C17 has only provisional zero load, so the resolver correctly has no positive weighted exposure. |
| `barbell_reverse_curl` | 바벨 리버스 컬 | 11 | `WEIGHTED_REPETITION` | Exact RCV and load-unit joins exist, as does an exact dose profile. C17 has only provisional zero load. |
| `dumbbell_lying_triceps_extension` | 덤벨 라잉 트라이셉스 익스텐션 | 5 | `WEIGHTED_REPETITION` | Exact RCV and load-unit joins exist, as does an exact dose profile. C17 has only provisional zero load. |

The four weighted exercises resolve in projection tests when supplied a valid recorded 20 kg load and confirmed repetitions. Their C17 `0 kg` placeholder is not interpreted as a real zero-load set and is not a metadata defect. The Bird Dog has a distinct adapter omission: the reviewed exact RCV coefficient exists, but the bodyweight-dose adapter does not receive it. A narrow repair can consume that exact coefficient only when the exercise key and `BODYWEIGHT_REPETITION` authority match and actual bodyweight is available. C17's provisional row still cannot produce exposure without bodyweight. No tissue relation, coefficient, or physiology is being authored in C18.

Primary root causes at the audit checkpoint:

- `ex_28347c1f`: `PROJECTION_ADAPTER_OMISSION` for the exact canonical bodyweight coefficient; C17 additionally lacks the input needed to use it.
- The other four keys: `REQUIRED_RECORDED_LOAD_INPUT_ABSENT`, not missing canonical metadata or tissue relations.

Expected repair boundary: unknown identities remain unresolved; Bird Dog without bodyweight remains unresolved; weighted exercises without a positive recorded load remain unresolved. No generic tissue fallback is permitted.

## C18B — Incumbent source audit and shadow model

The existing Room model persists `TrainingProgram.id`/`stableKey` and each `TrainingProgramItem`'s `exerciseStableKey`, `weekNumber`, `dayOfWeek`, and `orderIndex`. It does not persist an explicit `selectionRole`, planner protocol/runtime lineage, or whether a placement was manually edited. `trainingSlot` is overloaded and is not a reliable exact-role identity contract. The generation path builds before `saveGeneratedProgram` receives the existing program ID, so the current generation path cannot recover an exact incumbent `(stableKey, selectionRole, week, day, order)` source before placement. Program-relative placement survives date shifting; the date shift does not change its logical week/day/order.

No new persistence is needed for C18 shadow validation. `C18CanonicalIncumbentPlacementShadow` is a test-only typed index over an explicitly versioned source and exact owner-role rows. It rejects missing/stale source identity, missing roles, duplicate exact owner-week rows, removed owners, and role replacements. Its recommendation preserves an incumbent only when an independent hard-feasibility result is true; false means movement is allowed, and unknown means no recommendation. It never selects an owner or grants prescription/material authority.

The real-case shadow uses fixed historical-placement fixtures, separate for each case, and asserts their exact day/order matches the audited prior positions. It does not derive production state from a CONTROL object, and the production builder does not consume this shadow. Synthetic shadow outcomes are 12 `KEEP_INCUMBENT`, 2 `MOVE_INCUMBENT_HARD_INVALID`, and 18 `NO_ELIGIBLE_INCUMBENT` because unresolved tissue cannot prove feasibility. For `persona2_reviewed`, 14 shared owner-week rows already retain placement and the new calibration row is appended at day 1/order 4.

## C18C — C17 counterfactual rerun before the bounded repair

The memory-bounded census captures only exact-key metadata facts before generating the large corpus. It then reuses the real C17 test-side counterfactual results and records compact constraint snapshots. Re-running the current projection yields 32 rows: 12 `PRIOR_PLACEMENT_VALID`, 2 `PRIOR_PLACEMENT_HARD_INVALID`, and 18 `STILL_UNRESOLVED`. No row gains actual displacement authority. The 12 C17 drift rows remain valid individually. The 2 hard-invalid rows are the `persona3_reviewed` RDL weeks 1 and 2; the remaining rows still contain unresolved tissue inputs.

The full shared-placement restores remain invalid in every case:

| Case | Rows | Full-restore capacity | Full-restore constraints found |
|---|---:|---:|---|
| `persona0_mixed` | 6 | 1200 / 3600 sec maximum | Day 1 OFI 80 in both weeks and Bird Dog unresolved on the restored day. |
| `persona0_reviewed` | 4 | 1020 / 1800 sec maximum | RDL and chest-supported row lack actual weighted dose; Bird Dog is also unresolved in the complete restored week. No session-capacity overflow. |
| `persona3_reviewed` | 8 | 1020 / 1800 sec maximum | Restored day 2 OFI 71 with `RECOVERY_DEBT_HIGH`; Bird Dog, RDL, and chest-supported row have unresolved dose inputs. For the RDL sensitivity rows, omitting unsupported Power removes the OFI 94 finding but leaves RDL tissue unresolved. No source causal edge proves Power caused the production move. |
| `persona4_mixed` | 14 | 1200 / 5400 sec maximum | Restored day 3 OFI 84 with `HIGH_FORCE_NEURAL_CAUTION` and `RECOVERY_DEBT_HIGH`; Bird Dog and the weighted RDL/reverse-curl/triceps rows lack required inputs. |

The four accepted `BOUNDED_DAY_REBALANCER` events are two `ex_28347c1f#COVERAGE_CORE_DIRECT` moves and two `ex_5ca7133f#COVERAGE_CALVES` moves in `persona4_mixed`, all tagged `REBALANCE_OBJECTIVE`. That event records the stage and score-based cause; it does not prove a hard constraint or authorize a move. The restored counterfactual leaves the day-level OFI issue present (on the opposite day for the compared day arrangement), so the audit does not treat score improvement as displacement authority. The calves' same-day order change remains the previously proven unnecessary drift. Core feasibility remains unresolved until the exact Bird Dog input path is repaired and the actual required input is available.

The deterministic machine-readable row ledger is [`c18-tissue-incumbent-placement-census.json`](c18-tissue-incumbent-placement-census.json). CI uploads the generated report at `app/build/reports/c18-tissue-incumbent-placement-census.json`.

## Checkpoint verification and next step

At the audit checkpoint, local focused tests pass, including the 32-row C17/C18 audit, five-key tissue projection tests, resolved-key controls, unknown-key fail-closed test, and incumbent identity/feasibility/ambiguity/version tests. Hosted run `37165377348` for `e32a4cf0f0242d4330e9cfc9a3a2d02cda04c396` is green: protocol validation, Community/Cloud contracts, whitespace, full unit tests (2,255 / 0 failures / 0 errors / 4 skips), coverage, APK assembly, signer validation, and APK upload all passed. Coverage SHA-256 is `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`; APK is 68,675,787 bytes with SHA-256 `935BF230E4BD3AA9990C4CBD4EFA4D724CF72BBD18D5016D4ED169508C674C11`. The generated C18 census is included in the hosted report artifact. The audit checkpoint is therefore clear to proceed to the narrow, exact Bird Dog adapter repair. C18 will retain C17's routes and B7 counts; any output or route difference must be explained by the exact tissue input fix, and no C18 code changes production placement stability.

The current persisted plan is not yet a usable production incumbent because exact role and generation lineage are missing. C19 will need an exact, program-owned incumbent source (or an additive persistence contract) before placement continuity can be wired safely. C18 grants no derived displacement authority.
