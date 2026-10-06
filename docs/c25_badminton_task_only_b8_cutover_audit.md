# C25 — bounded badminton task-only B8 cutover

## Baseline and implementation

C24 PR #16 was merged from head `4b78c4d8fce018528c48c273af70f0193d29dfcf` as merge commit `b90663e7533efaa6162864dcce6b491d42b1a3eb` at `2026-10-06T00:37:35Z`. Merged-main Hosted CI run [37394991374](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37394991374) passed all required checks with 2,307 tests, 0 failures, 0 errors, and 4 skips.

C25 started from `b90663e7533efaa6162864dcce6b491d42b1a3eb`. Its final implementation/test commit is `7c5d80bb9f7d849097cfe2cdd86903928d12106c` (`lastAuditedCommit` after Hosted CI). The review is PR [#17](https://github.com/Mascollel-Ko/WhatYouGottaDo/pull/17). Protocol/runtime advance to `3.60.0` / `RECORD_BASED_PLANNER_0.15.2_KOTLIN_1`; App remains `0.5.1.5`. Room stays 38, program backup schema 4, restore schema 13, and Community snapshot schema 2. C25 adds no persistence fields or Room migration.

## Task-only scope and B8 authority

`StimulusProductionMaterialScopeResolver` identifies material owners from actual CONTROL-versus-EXPERIMENTAL owner/role deltas. It resolves `BADMINTON_TASK_V1` only when every changed owner has complete typed B7 provenance from `B6_APPROVED_TASK_PROTOCOL`, with exact current B4 task targets and no removed owners, mixed quality targets, unknown targets, or partial attribution. It does not infer a scope from names, policy categories, display text, or stable keys alone, and it does not try alternative scopes after a rejection.

The new Task B8 branch then requires eligible B7, closed provenance and materialization integrity, no target/collateral regression, no owner removals, unchanged schedule and generation request contract, valid hard program projection, and every material row inside its scheduled day. For each exact owner it verifies B5's primary target and stableKey/selectionRole, direction-only B4 target, one exact approved C24 protocol with `USER_APPROVED_PROJECT_POLICY` provenance, direct task transfer evidence for every attribution, matching lossless persisted B6 materialization, and two distinct placed protocol exposures per week with zero shortfall. The task B8 decision returns no partial authority when any requirement fails. Typed refusals include `B8_BADMINTON_TASK_MATERIAL_SCOPE_MISMATCH`, `B8_BADMINTON_TASK_EXACT_B5_OWNER_REQUIRED`, `B8_BADMINTON_TASK_DIRECT_RELATION_OR_MATERIALIZATION_INVALID`, `B8_BADMINTON_TASK_FREQUENCY_OR_PLACEMENT_SHORTFALL`, and `B8_BADMINTON_TASK_HARD_PROJECTION_INVALID`.

Existing quality B8 predicates remain unchanged. The task branch does not grant Power, JUMP_LANDING, Strength, or Combined authority. C24 remains the single source of the three exact task definitions and their lossless semantics. Six-corner and lateral-shuttle rows keep their protocol-level frequency and non-additive multi-target credit; each physical row is still one physical exposure.

## Whole-skeleton B9 routing

B9 accepts `B8_BADMINTON_TASK_V1` only when its exact `(protocolId, stableKey, selectionRole, authorizedTasks)` identity set matches the current EXP material and its owner identities. On success it returns the already-built `comparison.experimental` object. It never constructs a hybrid from CONTROL and EXPERIMENTAL and never invokes another planner. `CONTROL_ONLY` still returns the original `comparison.control` object as the rollback route.

The dedicated Room/service integration fixture starts with the real `persona3_recent` C24 service result and its eight approved task rows. Its test-only comparator is a projection of that already-built EXP skeleton with only those governed task rows omitted. The recomputed B7 is eligible; Task B8 authorizes the two exact protocol owners; B9 selects the identical EXP object; CONTROL_ONLY selects the identical comparator CONTROL object. Build accounting remains CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0. This test does not make the real `persona3_recent` comparison task-only or change its production route.

Negative tests keep CONTROL for Task mixed with Strength or Power, unresolved JUMP_LANDING, unknown material, unexplained removals, missing/mismatched B6 semantics, wrong stableKey or role, unapproved policy provenance, frequency or placement shortfall, unclosed B7 provenance, invalid hard projection, and changed request contract. The tests do not alter B4/B5/C24 Task B6 or B7 predicates.

## Corpus result

The deterministic machine census is [`c25-bounded-badminton-task-b8-census.json`](c25-bounded-badminton-task-b8-census.json), SHA-256 `802BD8B36A4BBA47001639F6F272B96C53970C8C9109C0AB57C4DC33D5097684`. The standard production coverage report SHA-256 is `1772FD236365E39E4012A3A1DEE1FFCBD190904E92F52C414F46372AF86D3178`; the report now includes the new zero-count Task route source, while generated case routes and program material remain the same.

Across 22 generated cases there are zero pure Task-only scope candidates and zero Task B8-authorized production cases. The sole case with task material is `persona3_recent`, which has eight valid C24 EXP task rows but remains CONTROL: its full comparison has B7 reasons `AFFECTED_TARGET_REMAINS_UNMET` and `CHANGE_PROVENANCE_UNCLOSED`, alongside unrelated quality/task material. C25 does not shop for a Task scope after that mixed/blocked result. Production routes therefore remain CONTROL 19 / Strength V1 1 / Strength Calibration 2 / Hypertrophy 0 / Combined 0.

The corpus has zero Power material rows and zero JUMP_LANDING material rows. B7 remains provenance-unclosed 11 / target-unmet 9 / regressed 1, with zero collateral-regression cases. C20 remains 12 HARD_VALID / 0 HARD_INVALID / 20 UNRESOLVED, and no invalid or unresolved incumbent is forced to remain. The C24 approved task shapes, C21 Power boundary, C20 stability behavior, ordering, and two-build accounting remain intact.

## Validation

Local production and unit-test compilation passed. The focused C25 authority/router suite passed 3 tests, the existing production-quality audit suite passed 3 tests, and the Room/service coverage integration test passed. The full local suite and Hosted CI results for the implementation and final documentation head are recorded in the PR and task completion report.

No production task route was observed in this corpus because it contains no pure Task-only production comparison. The positive fixture proves the exact path without changing a real mixed case or routing persona3_recent artificially.
