# Phase C11 — B5 non-selection provenance audit

## Commit and baseline

- C10 merge commit and C11 start `main` HEAD: `c6d648215d630c088bac1c10e9dd2e69870c8832`.
- The merged-main CI run [36979078881](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36979078881) passed: 2,175 tests, 0 failures, 0 errors, 4 skips, including coverage, APK assembly, signer validation, and upload. Starting routes were CONTROL 21 / Strength 1 / Hypertrophy 0 / Combined 0; standard coverage SHA-256 was `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.
- C11 implementation/test commit: `a4e897077811b1f6cb953114912242ccc03fa3d7`.
- Final code HEAD audited here: `a4e897077811b1f6cb953114912242ccc03fa3d7`. The later documentation-only final HEAD and its Hosted CI run are recorded in the task closeout; a Git commit cannot contain its own resulting object ID.
- Program Builder Protocol: `3.51.0`; planner runtime: `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1`; app: `0.5.1.5`.
- `lastAuditedCommit` is the implementation/test SHA above, not the documentation commit.

## B5 semantics and evidence model

C11 leaves B4 targets, B5 eligibility, ranking, candidate order, selected candidates, and `MaterialDemand` unchanged. B5 still runs before either program is compared and receives no CONTROL owner, role, prescription, or priority input.

For eligible candidates, the stored tuple is the selector's existing lexicographic comparator, in order: `targetCompatibleHistory` descending, `recentHistory` descending, `contextHistory` descending, `anchorContinuity` descending, `repeatedRecentSessions` descending, `freeWeightCompatible` descending, `highConfidence` descending, `redundant` ascending, then `stableKey` ascending. The tuple is evidence only; C11 adds no score or ranking term. `firstDifferingField` reports the first comparator field that distinguishes a candidate from the exact selected alternative. A stable-key-only win is labeled `DETERMINISTIC_STABLE_KEY_TIE_BREAK`, not a quality advantage.

The transient `StimulusCandidateDispositionIndex` records deterministic per-target/catalog dispositions, including direct-capability relevance, whether B5 required a new selection, typed actual gate reasons, exact candidate and selected-alternative tuples, the selected exact `(stableKey, selectionRole)`, and target coverage. Its row schema is `StimulusCandidateDisposition(targetId, stableKey, canonicalSelectionRole, directTargetCandidate, selectionRequired, status, reasons, candidateRanking, selectedInstead, selectedInsteadRanking, firstDifferingField, targetCoveredBySelectedOwner)`. The exact comparator fields are `TARGET_COMPATIBLE_HISTORY`, `RECENT_HISTORY`, `CONTEXT_HISTORY`, `ANCHOR_CONTINUITY`, `REPEATED_RECENT_SESSIONS`, `FREE_WEIGHT_COMPATIBLE`, `HIGH_CONFIDENCE`, `REDUNDANT`, and `STABLE_KEY`.

The actual typed reason set is `NO_DIRECT_CAPABILITY`, `ASSESSMENT_ONLY`, `TASK_ACTIVITY_NOT_SELECTABLE`, `PLANNING_NOT_SELECTABLE`, `EXPLICIT_PROFILE_RESTRICTION`, `USER_EXCLUDED`, `TISSUE_RESTRICTED`, `EQUIPMENT_UNAVAILABLE`, `FREE_WEIGHT_POLICY`, `GENERIC_COURT_ACTIVITY`, `NO_MINIMUM_TARGET`, `REDUCTION_DOES_NOT_AUTHORIZE_SELECTION`, `DISTRIBUTION_ONLY`, `TARGET_UNRESOLVED`, `STRATEGY_DOES_NOT_AUTHORIZE_SELECTION`, `LOWER_RANK_THAN_SELECTED_CANDIDATE`, `REDUNDANT_WITH_SELECTED_OWNER`, `DETERMINISTIC_STABLE_KEY_TIE_BREAK`, `TARGET_ALREADY_COVERED_BY_SELECTED_OWNER`, `NO_SAFE_PRESCRIPTION_AUTHORITY`, and `MINIMUM_PRESCRIPTION_EXCEEDS_SESSION_TIME`.

The late comparison adds `StimulusNonSelectionProvenance` with the exact omitted CONTROL owner identity, pair-level classification, and per-target evidence only after the index is complete. The B5 index itself contains no CONTROL-derived fields. Diagnostics are not added to compact JSON, backup, progression, or wire persistence; the decision is transient and requires no schema migration.

Statuses distinguish `SELECTED`, `REUSED_FOR_TARGET`, `INELIGIBLE`, `ELIGIBLE_NOT_SELECTED`, `MATERIALIZATION_FAILED`, `SELECTION_NOT_REQUIRED`, `TARGET_ALREADY_COVERED`, `NOT_RELEVANT_TO_TARGET`, and `UNPROVEN`. Typed reasons correspond to existing direct-capability, task/planning eligibility, profile exclusion, tissue, equipment, free-weight, minimum-target, distribution, strategy, prescription materialization, ranking, and target-coverage gates. The B7 authority check consumes typed status/classification and exact identities, with no reason-string parsing.

## CONTROL removal census

The coverage audit contains 27 cases: 22 generated and 5 preflight rejected. It found 34 removed owner-week rows: 17 exact CONTROL owner/role pairs repeated across weeks 1 and 2. For each pair, the top-level classification is `CANONICAL_REPLACEMENT`: the same stable key was actually selected under its canonical role for at least one exact current B4 direct target. That classification explains B5 non-selection and does not authorize deletion. The per-target evidence below preserves mixed outcomes such as no current selection demand, reuse, target already covered, or an outranking loss.

| Case | Exact CONTROL owner and role | Relevant current B4 target dispositions |
|---|---|---|
| `persona0_recent` | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | `QUALITY:STRENGTH` — same key `SELECTED`; `QUALITY:HYPERTROPHY` — `SELECTION_NOT_REQUIRED / NO_MINIMUM_TARGET`. |
| `persona0_reviewed` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona1_mixed` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:HYPERTROPHY` — same key `SELECTED`; strength — no minimum target. |
| `persona1_recent` | `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL` | `QUALITY:HYPERTROPHY` — same key `SELECTED`. |
| `persona1_reviewed` | `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL` | `QUALITY:HYPERTROPHY` — same key `SELECTED`; its tuple records target-compatible history, recent history, context history, anchor continuity, and six repeated recent sessions. |
| `persona1_sparse` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | hypertrophy — same key `SELECTED`; strength — no minimum target. |
| `persona2_mixed` | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona2_recent` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona3_recent` | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona3_recent` | `ex_33841b88#BADMINTON_OBJECTIVE_` | `TASK:ACCELERATION` — same key `SELECTED`; exact canonical owner is reused to cover direct deceleration, footwork, and reaction targets. |
| `persona3_recent` | `ex_421ba24b#BADMINTON_OBJECTIVE_` | `TASK:ACCELERATION` — eligible but not selected; `ex_33841b88` wins only at `STABLE_KEY`. All substantive tuple fields tie: target-compatible/recent/context/anchor history false, repeated sessions 0, free-weight compatible true, confidence false, redundancy false. It is selected under its own canonical role for `TASK:LUNGE_REACH`; deceleration, footwork, and reaction are already covered by `ex_33841b88`. |
| `persona3_reviewed` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona4_mixed` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona4_reviewed` | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `persona4_sparse` | `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |
| `reviewed_hypertrophy_isolated` | `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL` | `QUALITY:HYPERTROPHY` — exact same key selected under `CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`. This proves the fly's canonical replacement only. |
| `reviewed_strength_isolated` | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | `QUALITY:STRENGTH` — same key `SELECTED`; hypertrophy — no minimum target. |

For the pair-level census, a pair is classified as `CANONICAL_REPLACEMENT` if at least one exact current B4 direct target selected the same stable key under its canonical role. Under that explicit aggregation rule, counts are `CANONICAL_REPLACEMENT=17`; `OUTRANKED_FOR_RELEVANT_TARGET=0`; `TARGET_ALREADY_COVERED=0`; `INELIGIBLE_FOR_CURRENT_TARGET=0`; `NO_CURRENT_B4_SELECTION_DEMAND=0`; `MATERIALIZATION_FAILED=0`; `UNPROVEN=0`. The counts sum to 17 exact owner/role pairs. At target level, no-minimum, already-covered, and one stable-key-tie outranking dispositions remain visible as listed above. The week-expanded census is 34 pair-level `CANONICAL_REPLACEMENT` explanations. None is, by itself, B7 removal authority.

### Hypertrophy owners with unresolved C10 deltas

- `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`: for `QUALITY:HYPERTROPHY`, disposition is `NOT_RELEVANT_TO_TARGET / NO_DIRECT_CAPABILITY`. The C10 final 3-versus-2 comparison was not a B5 omission explanation or a new H target. The original C10 trace shows CONTROL's exact allocator expansion from 2 to 3 and EXPERIMENTAL's unchanged two-set demand; no displacement edge exists. The set delta remains unproven for B7.
- `ex_28347c1f#COVERAGE_CORE_DIRECT`: for `QUALITY:HYPERTROPHY`, disposition is `NOT_RELEVANT_TO_TARGET / NO_DIRECT_CAPABILITY`. Its C10 placement trace explains the accepted initial placement difference, while B8 schedule parity remains in force.
- `reviewed_hypertrophy_isolated` retains the exact fly B4→B5→B6→materialization replacement, but the posterior-chain comparison and unchanged placement policy keep the case on CONTROL.

## C11A parity and C11B B7 consumption

The disposition index is diagnostic and does not change B5's `selectedCandidates`, material demand, target coverage, B6 authorization/realization, EXPERIMENTAL rows, or CONTROL rows. The standard 27-case coverage output remains byte-identical at SHA-256 `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.

B7 consumes only exact `CANONICAL_REPLACEMENT` target rows from the late non-selection record when the B5 index contains the matching direct target and exact canonical owner/role. The existing B6 executable authority, full materialization, target outcome, collateral safety, contradictory-provenance, and B8 scope/parity gates remain mandatory. `OUTRANKED_FOR_RELEVANT_TARGET`, `TARGET_ALREADY_COVERED`, `INELIGIBLE_FOR_CURRENT_TARGET`, `NO_CURRENT_B4_SELECTION_DEMAND`, `MATERIALIZATION_FAILED`, and `UNPROVEN` are diagnostic only. Same stable key under a different selection role is not exact owner preservation.

On the real corpus, the B7 census remains `CHANGE_PROVENANCE_UNCLOSED=14`, `AFFECTED_TARGET_REMAINS_UNMET=9`, and `TARGET_REGRESSED=1`. No removal blocker was closed by explanation alone. Routes remain CONTROL 21, Strength 1, Hypertrophy 0, Combined 0; `reviewed_strength_isolated` remains `B8_STRENGTH_V1` and `reviewed_hypertrophy_isolated` remains CONTROL.

## Invariants and tests

| Invariant | Result |
|---|---|
| B1–B4 targets and science unchanged | PASS |
| B5 eligibility, comparator, ordering, selected owners, and demand unchanged | PASS |
| B6 authority and realization unchanged | PASS |
| CONTROL absent from B1–B6 and B5 disposition construction | PASS |
| CONTROL perturbation leaves B5 disposition index unchanged | PASS |
| Program coverage/report SHA unchanged | PASS |
| Ordering B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7/B8/B9 | PASS |
| Normal build counts CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0 | PASS |
| Expected-failure counts and preflight rejected counts unchanged | PASS |

Build accounting remains normal generated `CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0`; canonical expected failure `1 / 0 / 1 / 0`; experimental expected failure `1 / 1 / 2 / 0`; and preflight rejected `0 / 0 / 0 / 0`.

Positive tests cover exact same-key canonical replacement, an eligible candidate losing on exact lexicographic evidence, target coverage/reuse, deterministic stable-key tie transparency, and materialization status. Negative tests cover CONTROL-only identity injection, supportive-only capabilities, absent B4 demand, materialization failure, same key with wrong role, and B7 refusal when replacement authority, target outcome, or collateral conditions are missing. Existing C10 provenance negative tests continue to reject synthetic causes, wrong owner/role edges, mismatched action types, rejected moves, and contradictory later mutations. The focused suite includes `StimulusTargetCandidateSelectorTest`, `StimulusSelectionProgramComparisonTest`, `StimulusExperimentalReadinessTest`, `StimulusProductionQualityAuditTest`, `StimulusProductionCoverageAuditTest`, `CanonicalStimulusPlanningIndependenceTest`, `StimulusProductionFailureBoundaryTest`, `OwnerAllocationProvenanceTest`, and `C10OriginMutationInvestigationTest`.

Focused C11 selector/readiness/coverage tests passed locally. The broader requested focused suite passed before the final test-only additions; the updated selector test and updated corpus coverage audit were rerun separately and passed. The final Hosted CI run supplies the authoritative aggregate unit-test count. Full local `:app:testDebugUnitTest` was attempted but JBR 21 crashed in `robolectric-nativeruntime.dll` at `SQLiteConnectionNatives.nativePrepareStatement`: 1,434 completed tests, 0 assertion failures, 0 errors, 3 skips before worker termination. The subsequent localhost IPC reset was a consequence of that native worker exit. No CRLF architecture failure occurred in completed tests.

## Hosted CI and artifacts

Implementation/test PR: [#3](https://github.com/Mascollel-Ko/WhatYouGottaDo/pull/3). Implementation/test Hosted CI run [36988506371](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36988506371) passed protocol validation, Community/Cloud contracts, whitespace, full tests, coverage upload, `assembleDebug`, signer validation, and APK upload. The suite contained 2,181 tests, 0 failures, 0 errors, and 4 skips across 342 JUnit XML files.

- Coverage artifact `Stimulus-production-coverage`: ID `11218249976`, size 467,433 bytes, archive digest `sha256:267dcdabfdbbe9e701c9328be2e0daeac52cf6b4b94e6c019e364d09ce09b216`. The standard generated coverage report remains SHA-256 `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.
- APK artifact `WhatYouGottaDo-debug-apk`: ID `11219415270`, size 65,039,332 bytes, archive digest `sha256:cf88d27883ddadba38d44ae53039e9ce8c808ebda3c6767efa430acd8c8387c3`. Extracted `app-debug.apk`: 68,625,075 bytes, SHA-256 `8259A46F1883D89EDA87BB62F016675DC2F3E8666F0E0196A64B8014C0155C09`; signer validation passed.
- The final docs-only HEAD is checked by its own Hosted CI run. Its final SHA and run/artifact metadata are included in the completion report and PR check history.
