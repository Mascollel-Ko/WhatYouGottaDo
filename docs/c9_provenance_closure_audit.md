# Phase C9 — Canonical change-provenance closure audit

## Revision and investigation order

- Start HEAD: `24a3135c849c4d14f67479e88048bb5f3e7c293c`.
- Baseline main compilation passed before production edits (7m03s).
- The unchanged C8 production code was rerun through all 27 cases; 22 generated, five no-history preflight rejections. Diagnostic test output was saved before production edits.
- Implementation/test commit: `e882999c2a15a37faae9c854a5b5b74db077708a`.
- Final repository HEAD is the separate documentation commit containing this audit; its exact hash and final CI are recorded in the delivered completion report (a commit cannot embed its own content hash).
- Pull request: [C9 exact canonical change provenance](https://github.com/Mascollel-Ko/WhatYouGottaDo/pull/1).

## Root cause recorded before implementation

`reviewed_hypertrophy_isolated` is **B. TRUE_SAFETY_BLOCK**, not a proven false-negative.
The H owner role replacement is already explained by `B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE`.
The exact unexplained owner is `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`: each of weeks 1 and 2 loses its third set (3 -> 2) at day 1/order 1, and the prescription text changes from three to two sets.
It has no selected B5 owner, no exact B6 authorization, and is absent from `constrainedOwnerStableKeys` (which contains only `ex_28347c1f` and `cable_rear_delt_fly`).
The existing exact-prefix rule also requires unchanged prescription text. Inventing a displacement trace or dropping that requirement cannot establish the missing causality.
Root cause class: **C/E: unrelated shared-owner prescription/set-count mutation with unproven allocator causality**.
Additionally `ex_28347c1f#COVERAGE_CORE_DIRECT` moves from day 5/order 1 to day 1/order 2, and B8 reports partial material provenance. B7 currently does not attribute this placement-only delta; C9 must not bypass B8 to route it.

### Exact H fixture delta (same in both weeks)

| Owner (stableKey # selectionRole) | CONTROL day/order | EXPERIMENTAL day/order | Prescription delta |
|---|---|---|---|
| cable_rear_delt_fly # STYLE_MEDIUM_HORIZONTAL_PULL | 3/2 | absent | removed legacy owner; already attributed to exact canonical replacement |
| cable_rear_delt_fly # CANONICAL_STIMULUS_QUALITY_HYPERTROPHY | absent | 5/1 | added; 3 x 10 @ 40 kg, targetRpeMin=7, rest=150, TARGET_COMPATIBLE_PERSONAL_HYPERTROPHY_HISTORY |
| ex_284ecca6 # COVERAGE_POSTERIOR_CHAIN | 1/1 | 1/1 | 3 -> 2 x 8 @ 0 kg, targetRpeMin=null; text 3 -> 2 sets; rest=90 and PROVISIONAL_RPE_NO_INVENTED_LOAD unchanged |
| ex_6232f4bc # COVERAGE_LOWER_KNEE | 3/1 | 3/1 | unchanged 2 x 8 @ 0 kg, rest=90, PROVISIONAL_RPE_NO_INVENTED_LOAD |
| ex_28347c1f # COVERAGE_CORE_DIRECT | 5/1 | 1/2 | same 2 x 8 @ 0 kg, rest=90, PROVISIONAL_RPE_NO_INVENTED_LOAD; placement changed |

CONTROL has four owners, EXPERIMENTAL four: one added, one removed, three shared, one prescription-changed shared owner. Legacy fly prescription was 5 x 6 @ 40 kg, targetRpeMin=null, rest=150, CANONICAL_POSTERIOR_HOLD.
All set indices are the sequential 1..N shown by each row's set count; seconds=0. The exact full rows, B4/B5/B6 objects, realization plan, materialization audit and B7 attributions are retained in the census artifacts.

B4 H: RESTORE_PERSONAL_BASELINE / PERSONAL_RESTORE_BASELINE; units min/preferred/max=0/6/6, sessions=0/2/2.
B5: exact canonical fly owner, only QUALITY:HYPERTROPHY.
B6: same owner + HYPERTROPHY + QUALITY:HYPERTROPHY, AUTHORIZED_EXISTING_COMPATIBLE, FULLY_ENCODED; authorized 3 x 10 @ 40 kg / RPE >=7 / rest=150.
Materialization integrity PASS: 3 authorized/3 materialized/3 compatible sets in each week, no shortfall or overrun, FULLY_MATERIALIZED.
Target outcome PASS: H UNCHANGED within band (units/sessions distances both 0 before and after), collateralRegressionFree=true.
These facts explain the fly only. They do not authorize the posterior-chain mutation.

## Before census

Generated=22; CONTROL=21; Strength=1; Hypertrophy=0; Combined=0; no-history rejection=5.

```text
B7 counts={AFFECTED_TARGET_REMAINS_UNMET=9, CHANGE_PROVENANCE_UNCLOSED=14, TARGET_REGRESSED=1}
B8 counts={B8_B7_NOT_ELIGIBLE=16, B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY=1, B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY=3, B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE=1, B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY=5, B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED=3, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION=5, B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION=3, B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY=4, B8_STRENGTH_V1_AUTHORIZED=1}
```

### Fallback classification

Primary mutually exclusive classes: A=0, B=20, C=1. Persona3 recent/reviewed/sparse also have unsupported POWER material scope, in addition to their primary safety failures. A zero count means no complete causal proof was found; it is not permission to manufacture evidence.

| Case | Class | Evidence/blocker |
|---|---|---|
| persona0_mixed | B. TRUE_SAFETY_BLOCK | B8 missing executable exact B6/full materialization and/or partial material provenance |
| persona0_recent | B. TRUE_SAFETY_BLOCK | barbell_back_squat#STYLE_HEAVY_LOWER_KNEE: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona0_reviewed | B. TRUE_SAFETY_BLOCK | barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED |
| persona0_sparse | B. TRUE_SAFETY_BLOCK | ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_ADDED_IDENTITY; ex_28347c1f#COVERAGE_CORE_DIRECT: UNEXPLAINED_ADDED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona1_mixed | B. TRUE_SAFETY_BLOCK | barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona1_recent | B. TRUE_SAFETY_BLOCK | cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED |
| persona1_reviewed | B. TRUE_SAFETY_BLOCK | TARGET_REGRESSED |
| persona1_sparse | B. TRUE_SAFETY_BLOCK | barbell_reverse_curl#COVERAGE_ARMS_BICEPS: UNEXPLAINED_ADDED_IDENTITY; barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN: UNEXPLAINED_ADDED_IDENTITY; dumbbell_goblet_squat#COVERAGE_LOWER_KNEE: UNEXPLAINED_ADDED_IDENTITY; dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS: UNEXPLAINED_ADDED_IDENTITY; ex_28347c1f#COVERAGE_CORE_DIRECT: UNEXPLAINED_ADDED_IDENTITY; ex_5ca7133f#COVERAGE_CALVES: UNEXPLAINED_ADDED_IDENTITY; barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED |
| persona2_mixed | B. TRUE_SAFETY_BLOCK | B8 missing executable exact B6/full materialization and/or partial material provenance |
| persona2_recent | B. TRUE_SAFETY_BLOCK | barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona2_reviewed | B. TRUE_SAFETY_BLOCK | B8 missing executable exact B6/full materialization and/or partial material provenance |
| persona2_sparse | B. TRUE_SAFETY_BLOCK | barbell_deadlift#COVERAGE_POSTERIOR_CHAIN: UNEXPLAINED_ADDED_IDENTITY; barbell_reverse_curl#COVERAGE_ARMS_BICEPS: UNEXPLAINED_ADDED_IDENTITY; cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS: UNEXPLAINED_ADDED_IDENTITY; cable_rear_delt_fly#COVERAGE_UPPER_PULL: UNEXPLAINED_ADDED_IDENTITY; ex_28347c1f#COVERAGE_CORE_DIRECT: UNEXPLAINED_ADDED_IDENTITY; ex_5c8751d2#COVERAGE_CALVES: UNEXPLAINED_ADDED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona3_mixed | C. OUT_OF_SCOPE | POWER material scope unsupported; missing executable exact B6/full materialization |
| persona3_recent | B. TRUE_SAFETY_BLOCK | barbell_back_squat#STYLE_HEAVY_LOWER_KNEE: UNEXPLAINED_REMOVED_IDENTITY; ex_33841b88#BADMINTON_OBJECTIVE_: UNEXPLAINED_REMOVED_IDENTITY; ex_421ba24b#BADMINTON_OBJECTIVE_: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona3_reviewed | B. TRUE_SAFETY_BLOCK | barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED |
| persona3_sparse | B. TRUE_SAFETY_BLOCK | ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_ADDED_IDENTITY; ex_28347c1f#COVERAGE_CORE_DIRECT: UNEXPLAINED_ADDED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| persona4_mixed | B. TRUE_SAFETY_BLOCK | barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED |
| persona4_recent | B. TRUE_SAFETY_BLOCK | AFFECTED_TARGET_REMAINS_UNMET |
| persona4_reviewed | B. TRUE_SAFETY_BLOCK | B8 missing executable exact B6/full materialization and/or partial material provenance |
| persona4_sparse | B. TRUE_SAFETY_BLOCK | barbell_reverse_curl#COVERAGE_ARMS_BICEPS: UNEXPLAINED_ADDED_IDENTITY; barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN: UNEXPLAINED_ADDED_IDENTITY; dumbbell_chest_supported_row#COVERAGE_UPPER_PULL: UNEXPLAINED_ADDED_IDENTITY; dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS: UNEXPLAINED_ADDED_IDENTITY; ex_28347c1f#COVERAGE_CORE_DIRECT: UNEXPLAINED_ADDED_IDENTITY; ex_5ca7133f#COVERAGE_CALVES: UNEXPLAINED_ADDED_IDENTITY; barbell_bench_press#COVERAGE_HORIZONTAL_PUSH: UNEXPLAINED_REMOVED_IDENTITY; CHANGE_PROVENANCE_UNCLOSED, AFFECTED_TARGET_REMAINS_UNMET |
| reviewed_hypertrophy_isolated | B. TRUE_SAFETY_BLOCK | ex_284ecca6#COVERAGE_POSTERIOR_CHAIN: UNEXPLAINED_PRESCRIPTION_CHANGE; CHANGE_PROVENANCE_UNCLOSED |

## Implementation boundary

No fallback in categories B/C will be passed. Required negative tests reproduced five unsafe attribution acceptances in the old B7 helper (materialization mismatch, unrelated replacement role, contradictory disappearance trace, target/quality mismatch, conflicting quality authority). The C9 change tightens the existing attribution helpers to demand the exact existing B5/B6/materialization proof, retaining valid compatible/repaired and canonical-replacement positives. It does not relax the posterior-chain block, alter B1-B6 semantics, change B8/B9 or introduce a new provenance subsystem.

## Provenance rule before and after

| Path | Before | After |
|---|---|---|
| Canonical role replacement | same exercise selected by B5 plus owner-matched non-null authorized prescription/status | exactly one added canonical owner whose role matches its target; exact B5 coverage; quality agrees with target; fully encoded exact B6 authority; nonempty valid weekly materialization; no conflicting/ambiguous owner or contradictory old-owner trace |
| Shared exact owner prescription | owner-grouped B6 status and subset validation | same checks plus exact B5 target coverage, lossless owner/quality/target matching, executable effort and conflicting-owner veto |
| Valid compatible / safe repair | B6_EXISTING_OWNER_PRESCRIPTION / B6_SAFE_REPAIRED_PRESCRIPTION | same sources, with explicit evidenceSources for B5, exact B6 and weekly subset |
| Owner-local downstream reduction | allocator trace + same-slot exact set-prefix reduction | unchanged |
| Added B5 identity, target outcome, B6 integrity, B8, B9 | existing fail-closed machinery | unchanged |

No stableKey-only closure is added. Matching an exercise key only locates a possible replacement; all authority and materialization checks use both key and role, with quality and target retained in each authorization row. An H authorization is never used as Strength authority or vice versa. Multiple distinct prescriptions for the same owner fail closed.

## Positive and negative evidence

The pre-fix readiness run completed 26 tests with five intentional C9 failures, reproducing unsafe closure for contradictory disappearance, target/quality mismatch, conflicting multi-quality authority, mismatched materialization, and unrelated replacement role.
The new suite also preserves exact reused compatible and safe-repair sources, canonical role replacement, unrelated-owner mutation blocking, and missing disappearance evidence. The real H fixture's unresolved posterior-chain delta is explicitly asserted, not converted into a positive route fixture. A separately labeled synthetic allocator seam checks that exact prefix reduction needs its own trace and unchanged non-set fields; it does not alter the measured corpus or claim the real fixture has that evidence.

## Version decision

Do not claim the conditional `3.52.0` / `0.14.4` cutover: no H production cutover is justified. Protocol/runtime/app stay `3.51.0` / `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1` / `0.5.1.5`; the after-census parity check confirms unchanged production routes. This follows C8's version-retention convention for unchanged production behavior: stricter rejection of malformed audit evidence and richer evidence-source diagnostics do not add a production route or change a generated prescription. The malformed-input B7 behavior does change and is explicitly tested; no claim of byte-identical B7 diagnostics is made.

## After census and route review

All 22 cases preserve their route; no route expansion or golden update. CONTROL=21, Strength=1, H=0, Combined=0. Five no-history rejections remain unchanged. B7 reason counts remain CHANGE_PROVENANCE_UNCLOSED=14, AFFECTED_TARGET_REMAINS_UNMET=9, TARGET_REGRESSED=1; all B8 counts are identical to the before table above. The H fixture remains NOT_ELIGIBLE / changeProvenanceClosed=false / collateralRegressionFree=true, B8 CONTROL_REQUIRED with B8_B7_NOT_ELIGIBLE (default fallback scope STRENGTH_V1; not an H authorization), B9 CONTROL.

The standard coverage report is byte-identical before and after: SHA-256 `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`. The detailed census differs only in five B7 lines containing more explicit evidenceSources; all B4/B5/B6 objects, realization/materialization audits, allocator traces, schedule/rows and build counts are unchanged. There are no newly explained corpus deltas and no claim that lowering the blocker count is required.

Full reproducible typed-object diagnostics are archived as [before census](audits/c9/before-census.txt.gz) and [after census](audits/c9/after-census.txt.gz). Decompress with Python `gzip` or another gzip reader. Each includes every generated case's targets, exact selected and authorized owners, owner delta sets, every B7 attribution, B7/B8 reasons, routes, rows, materialization and realization evidence. Hosted coverage additionally includes the plain text after census.


### Before/after reason counts

| Gate / reason | Before | After |
|---|---:|---:|
| B7 `AFFECTED_TARGET_REMAINS_UNMET` | 9 | 9 |
| B7 `CHANGE_PROVENANCE_UNCLOSED` | 14 | 14 |
| B7 `TARGET_REGRESSED` | 1 | 1 |
| B8 `B8_B7_NOT_ELIGIBLE` | 16 | 16 |
| B8 `B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY` | 1 | 1 |
| B8 `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY` | 3 | 3 |
| B8 `B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE` | 1 | 1 |
| B8 `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY` | 5 | 5 |
| B8 `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED` | 3 | 3 |
| B8 `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION` | 5 | 5 |
| B8 `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION` | 3 | 3 |
| B8 `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY` | 4 | 4 |
| B8 `B8_STRENGTH_V1_AUTHORIZED` | 1 | 1 |

## Unchanged authority, ordering and build accounting

Production diff is limited to `StimulusExperimentalReadiness.kt`. No B1–B6 semantic change; no ledger/need/baseline/strategy/target/ranking/prescription-science change; no B8 or B9 policy change; no new builder or CONTROL authority. `CanonicalStimulusPlanningIndependenceTest` (19 tests) and `StimulusProductionFailureBoundaryTest` (6 tests) pass. These retain CONTROL perturbation invariance, canonical -> EXPERIMENTAL -> late CONTROL -> B7/B8/B9 ordering, exact selected object identity and normal/expected-failure build accounting.

| Path | CONTROL | EXPERIMENTAL | Total | Third |
|---|---:|---:|---:|---:|
| Normal | 1 | 1 | 2 | 0 |
| Canonical expected failure | 1 | 0 | 1 | 0 |
| Experimental expected failure | 1 | 1 | 2 | 0 |
| Preflight rejected | 0 | 0 | 0 | 0 |

## Local validation

- Pre-change main Kotlin compile: PASS.
- Post-change main and unit-test Kotlin compile: PASS.
- Initial focused run: 10 suites / 135 tests / 0 failures / 0 errors / 0 skips, including all requested B7/B8, prescription, B5, quality/coverage, C8 independence and failure-boundary suites.
- Additional allocator/B9/accounting/integration/progress run: 5 suites / 16 tests / 0 failures / 0 errors / 0 skips. Across both focused runs, using the latest coverage-suite result: **14 unique suites / 149 tests / 0 failures / 0 errors / 0 skips**. Full local result is recorded below.
- Protocol validation: PASS (9 families, 36 protocols).
- Community/Cloud contracts: PASS (9 tests).

## Remaining blocker classes and next phase

1. Owner-local causal evidence missing for unrelated additions/removals, set-count reductions and placement changes. The H posterior-chain/core deltas belong here; B6 fly authority cannot be borrowed.
2. Missing/full-materialization executable authority and target outcome failures (nine unmet, one regression).
3. POWER/task material changes outside current production scope (primary persona3_mixed, additional scope blockers in other persona3 cases).

Recommended next phase: investigate typed builder/allocator provenance for actual owner-role demand changes and scheduling, without changing target science or opening POWER/task routes. C9 stops here; no such next-phase change is implemented.

## Full local result: two separate Windows failures

The full local `:app:testDebugUnitTest --no-daemon` run was attempted and is **incomplete, not a pass**: 259 JUnit XML files, 1,433 completed tests, 1 failure, 0 XML errors, 3 skips; Gradle failed after 13m42s.

1. `PlannerIsolationArchitectureTest.frozenPlannerImportsOnlyOwnTypesIdentityDaoAndNeutralRowNoticePrimitives` is an assertion failure in the existing source-scanning test. Its `[^\n]+` regex retains Windows CR, so allowed `data.Exercise` is read as `data.Exercise\r`. The failing `LegacyAutoCandidateAuthority.kt` is byte-identical to Start HEAD after CRLF-to-LF normalization; no legacy import or architecture code changed. Hosted Linux passes the same test. This assertion is recorded separately, not mislabeled as the native crash or hidden in a zero-failures claim.
2. The JBR 21 worker subsequently crashed with `EXCEPTION_ACCESS_VIOLATION (0xc0000005)` in `robolectric-nativeruntime.dll`, at `SQLiteConnectionNatives.nativePrepareStatement`. The JVM produced `hs_err_pid24520.log`; Gradle reported a worker connection reset. This matches the existing Windows Robolectric/native SQLite failure category. No production changes were made to work around it.

Hosted Linux is the full-suite authority. All C9 focused suites passed locally before the full-run attempt.

## Hosted implementation CI and artifacts

Implementation/test commit `e882999c2a15a37faae9c854a5b5b74db077708a` passed [Android Debug Build 36941948777](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36941948777). All recorded steps succeeded: protocol validation, Community/Cloud contracts, whitespace, full debug unit tests, production coverage upload, assembleDebug, signer validation, APK upload.

- Hosted JUnit: **340 XML files, 2,171 tests, 0 failures, 0 errors, 4 skips**.
- Standard coverage SHA-256: `818e8fa6f67164eeaae0c938273a777d645874cf0eecd17f1e1795dc811434d9` (C8/local/Hosted identical).
- Detailed census SHA-256: `4f7558359c1d89177a90da54e536ec725ed780b3c8e19375f66bfbad72c6e823` (local/Hosted byte-identical).
- [Stimulus-production-coverage artifact](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36941948777/artifacts/11200344569): ID `11200344569`, 450,950 bytes, archive digest `a199a0c02d6dba25514413733d77fe74e0cf1c04ae476c1ecb06e9c189a9649e`.
- [WhatYouGottaDo-debug-apk artifact](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36941948777/artifacts/11201395805): ID `11201395805`, 64,972,221 bytes, archive digest `a2f076e9ebda5ec25d181c0f0859d94fde7586fdd7b48a109a04e1891c6bc3d6`.
- Downloaded `app-debug.apk`: 68,559,539 bytes; **SHA-256 `71e4e7aab47e02b6aa8d4e3cde12570b59bb2cd0b57acfde485c9b76804ea81b`**. This is distinct from the archive digest. CI verified the signer against its configured debug keystore.

`lastAuditedCommit` in PROGRAM_BUILDER_OVERVIEW and protocol_registry points to this green implementation/test commit, not this docs-only audit revision. The docs-only final HEAD receives separate CI; its exact hash/result and final-head APK are recorded in the delivered completion report after that run finishes.
