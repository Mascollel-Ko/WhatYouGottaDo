# Phase C10 — origin-emitted builder/allocator provenance audit

## Baseline and scope

- PR #1 merged at `2026-10-02T00:20:15Z`; merged C9 main and C10 start SHA: `c3ae813cdab94994ab150af8075cc00d75981af3`.
- C9 implementation/test SHA remains the pre-C10 audit reference: `e882999c2a15a37faae9c854a5b5b74db077708a`.
- C10 implementation/test SHA: `ccb9360522826892d881e1166af84cee7a7b586a` (includes the H-fixture no-set-mutation assertion and accepted residual day-remap instrumentation).
- Final C10 implementation/test SHA: `ccb9360522826892d881e1166af84cee7a7b586a`. The docs-only closeout HEAD is recorded in the completion report and PR #2; this audit cannot embed its own commit object ID.
- Program Builder Protocol remains `3.51.0`; planner runtime remains `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1`; app remains `0.5.1.5`.
- `lastAuditedCommit` will point to the green C10 implementation/test SHA above, not the docs-only closeout.
- C10 separates causal provenance from B4 target authority, B5 owner identity authority, B6 prescription authority, and B8 production authority. A trace explains an accepted change; it does not authorize it.

## Mutation-stage source audit

This matrix records actual capabilities and the C10 source of evidence. Candidate feasibility trials do not enter the final trace. An event is retained only for a selected, accepted allocation or mutation.

| Stage / source | Owner add/remove | Set count | Day/order | Prescription | C10 source evidence |
|---|---|---|---|---|---|
| `MaterialDemandResolver.resolve` / builder demand selection | Adds selected material owner; records an exact role replacement as old-owner removal plus new-owner addition. A candidate omitted upstream is not falsely recorded as a builder removal. | No allocator reduction here. | No. | Resolves the prescription associated with the selected demand. | `MATERIAL_DEMAND` owner/role event at the selected-map write. |
| `FiniteExecutionAllocator.allocate` | Can allocate zero to an exact material owner. | Can reduce/remove or expand a selected owner’s integer allocation. | No. | No. | `FINITE_EXECUTION_ALLOCATION` event carries owner plus role and the requested/allocated counts. Share expansion uses `CAPACITY_SHARE_ALLOCATION`; a capacity boundary uses `CAPACITY_LIMIT`. No cause-owner edge is invented. |
| `BoundedMaterialDemandAllocation` | Can leave an exact material owner unfunded. | Can reduce funded units. | No. | No prescription authorship. | Emits exact owner allocation results from its bounded allocation outcome; the result is not treated as a displacement edge unless the allocator identifies both owners. |
| `TimedExecutionAllocationPlanner.allocate` | Can defer an owner that fails accepted funding/placement. | Restores continuity one set at a time and can stop below requested sets. | It invokes the weekly placer; accepted initial assignments and main-placement rearrangements are captured at that source. | Re-prescribes only through the existing prescription planner for the accepted count. | Typed timed-allocation event plus the source callback from `TimedWeeklyPlacementPlanner`; `fits()` trials carry no persistent trace. |
| `TimedWeeklyPlacementPlanner.distributeGreedy` / `InitialMainPlacement.review` | Can leave a row deferred when no session fits. | No set mutation. | Performs initial bucket insertion and accepted initial-main placement. | No. | Emits `INITIAL_WEEKLY_PLACEMENT` / `INITIAL_MAIN_PLACEMENT` only for the accepted placement; includes exact week-expanded final slot state. |
| `SplitAwareContinuityAllocation.improve` / `MandatoryContinuityPlacement` | Reorganizes authorized atoms; does not invent a new owner. | Can partition an authorized prescription into chunks, with the exact owner total bounded by its authorization. | Can change accepted day/order. | Chunk display text may be rendered from existing exact sets; it does not change B6 set contents. | The accepted before/after layout is recorded after the layout is selected. Rejected full/split trials are not emitted. |
| `ExactAuthorizedRestoration.restore` | Can add a missing exact authorized row. | Can restore only exact authorized sets; cannot author a new set. | Can place a restored row. | Exact B6 values are retained. | `EXACT_PRESCRIPTION_FUNDED_MATERIALIZATION` records accepted row additions, set restoration, placement, order, or prescription state. |
| `ResidualCompletion.run` | Can add a semantic residual owner when eligible. | Its exact restoration substage can restore exact sets; semantic additions remain demand/capacity bounded. | Can remap existing rows when an added day changes the schedule. | A semantic residual uses the existing prescription planner; exact restoration remains exact. | Records accepted residual additions and exact restoration. C10 also records the accepted old-slot → remapped-slot transition at `RESIDUAL_COMPLETION`; rejected added-day candidates are not traced as final moves. |
| `ProgramRepairPolicy.repairWithProvenance` | Can remove a row that fails the accepted session-time fit. | No set rewrite. | No. | No. | Emits exact removed row at the actual filter decision with `SESSION_TIME_LIMIT`. |
| `BoundedDayRebalancer.rebalance` | No add/remove. | Cannot change sets; the regression test fixes whole-item payload. | Accepted whole-row moves/swaps can change day/order. | Cannot change prescription. | Emits `BOUNDED_DAY_REBALANCER` only after the candidate is accepted, with unchanged prescription/set payload. |
| `FrequencyExpansionPlanner.expand` / rollback | Can replicate or drop expansion-only demand. | Can expand/reduce/drop the expansion portion; BASE is preserved. | Accepted frequency-only relocations can move/order rows. | Can use the existing exact/region slice or authorized flexible expansion prescription. | `FREQUENCY_EXPANSION` events retain accepted output origins and typed rollback cause; rejected candidate layouts are not final provenance. |
| `PostSplitWeeklyReflow.review` / `finish` | No add/remove. | Cannot change sets. | Accepted whole-item movement/order changes only. | Cannot change prescription, demand, funding, or progression. | `POST_SPLIT_WEEKLY_REFLOW` events are emitted after accepted moves. Existing immutable-row and fixed-chunk contracts remain. |

`OwnerAllocationProvenance` uses `(stableKey, selectionRole)` as the owner identity. Its before/after state can carry week, day, order, set count, exact set prescriptions, display prescription, and role. Typed causes include the actual allocator/scheduler causes and `UNKNOWN_UNPROVEN`; speculative or guessed string causes are not accepted. `OwnerDisplacementEdge` retains cause owner, displaced owner, stage, typed reason, displaced units, week, target/quality, and authorized demand IDs. No edge is emitted unless a source allocator itself knows both exact identities.

`OwnerAllocationProvenance` carries exact owner `(stableKey, selectionRole)`, stage, action, nullable before/after state, typed cause, target IDs, qualities, authorized demand IDs, evidence codes, and a deterministic mutation sequence. Each state carries nullable week/day/order, set count, exact set prescriptions, prescription text, and selection role. `OwnerDisplacementEdge` carries exact cause and displaced owner identities, stage, typed reason, displaced units, week, target/quality, authorized-demand links, and mutation sequence.

The actual stage enum is `MATERIAL_DEMAND`, `FINITE_EXECUTION_ALLOCATION`, `TIMED_EXECUTION_ALLOCATION`, `INITIAL_WEEKLY_PLACEMENT`, `INITIAL_MAIN_PLACEMENT`, `SPLIT_AWARE_CONTINUITY_ALLOCATION`, `MANDATORY_CONTINUITY_PLACEMENT`, `RESIDUAL_COMPLETION`, `PROGRAM_REPAIR`, `BOUNDED_DAY_REBALANCER`, `FREQUENCY_EXPANSION`, `POST_SPLIT_WEEKLY_REFLOW`, and `EXACT_PRESCRIPTION_FUNDED_MATERIALIZATION`. The actual action enum is `ADDED`, `REMOVED`, `SET_COUNT_REDUCED`, `SET_COUNT_EXPANDED`, `PLACEMENT_ASSIGNED`, `PLACEMENT_MOVED`, `ORDER_CHANGED`, `FREQUENCY_REPLICATED`, `FREQUENCY_DROPPED`, and `PRESCRIPTION_CHANGED`. Typed causes include `CAPACITY_LIMIT`, `CAPACITY_SHARE_ALLOCATION`, `SESSION_TIME_LIMIT`, `OWNER_PRIORITY`, `MATERIAL_DEMAND`, `FREQUENCY_EXPANSION`, `HARD_GATE`, `OFI_GATE`, `TISSUE_GATE`, `REBALANCE_OBJECTIVE`, `POST_SPLIT_REFLOW`, `RESIDUAL_COMPLETION`, `INITIAL_PLACEMENT_POLICY`, `INITIAL_MAIN_PLACEMENT_OBJECTIVE`, `MANDATORY_CONTINUITY`, `AUTHORIZED_SET_LIMIT`, and fail-closed `UNKNOWN_UNPROVEN`.

Persistence decision: **A — diagnostic-only transient fields**. The owner events live in memory from their source mutation through final `PersonalizedDecision` and B7. `ExecutionAllocationTrace.toJson()` includes the additive fields in the existing internal planning diagnostic output, but no backup or progression wire codec stores or restores them. No durable reader migration is needed; older constructor sites use empty defaults. The legacy `constrainedOwnerStableKeys` field remains for compatibility. B7 no longer treats that stable-key set as causal evidence. C10 does not use stable-key-only proof.

Traces are deterministically sorted by exact owner/role, stage, accepted mutation sequence, week/slot, action, and cause; serialized sets/IDs/evidence lists are sorted. The typed list stores accepted events only. Allocation fit trials, rejected rebalancer moves, and rejected reflow/frequency candidates are not retained in the final execution trace.

## Reviewed hypertrophy fixture: exact source tracing

Fixture: `reviewed_hypertrophy_isolated`.

### Posterior-chain owner

Exact owner: `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`; experimental authorized demand ID in the placement trace: `authorized_1`.

The C9 “3 sets → 2 sets” description was a CONTROL-versus-EXPERIMENTAL final comparison. It was not an EXPERIMENTAL 3→2 reduction.

| Boundary | CONTROL | EXPERIMENTAL |
|---|---|---|
| Stage 0 — initial selected demand | 2 sets, exact `COVERAGE_POSTERIOR_CHAIN` role. | 2 sets, exact same owner/role; `authorized_1`. |
| Stage 1 — finite allocation | `FiniteExecutionAllocator.allocate` expands 2→3 in the round-robin shared-unit loop while `material.sum() < gapTarget`. The emitted event is `FINITE_EXECUTION_ALLOCATION / SET_COUNT_EXPANDED / CAPACITY_SHARE_ALLOCATION`, evidence `FINITE_ALLOCATOR_SHARED_UNIT_ALLOCATION`; both exact before and after set lists are retained. | Remains at 2. No `SET_COUNT_REDUCED`, `SET_COUNT_EXPANDED`, or `PRESCRIPTION_CHANGED` event exists for this owner in the final experimental trace. No allocator displacement edge exists. |
| Stage 2 — timed allocation/completion | Final owner remains 3. | Demand remains 2; no source event reduces it. |
| Stage 3 — placement | Accepted weekly assignment is separately traced. | Accepted weekly assignment is separately traced; placement evidence does not explain or authorize a set change. |
| Stage 4 — rebalancing | No set mutation. | No set mutation. |
| Stage 5 — frequency expansion | No final change to this owner’s base allocation. | No set-count or prescription event for this owner. BASE preservation remains enforced. |
| Stage 6 — post-split reflow | Whole-item placement only. | Whole-item placement only; no set/prescription mutation. |
| Stage 7 — final skeleton | Weeks 1 and 2 each contain 3 exact sets. | Weeks 1 and 2 each contain 2 exact sets and the same exact owner role. |

Therefore there is no exact EXPERIMENTAL 3→2 mutation function, cause, or edge to report: the first actual difference is CONTROL’s 2→3 expansion in `FiniteExecutionAllocator.allocate`. Cause owner edge: **not applicable / none emitted**. The fly replacement itself still has exact B4 → B5 owner → B6 hypertrophy authorization → full materialization, but it does not prove that this unrelated posterior-chain owner was displaced. The H case remains fail-closed in CONTROL.

### Core placement owner

Exact owner: `ex_28347c1f#COVERAGE_CORE_DIRECT`; experimental authorized demand ID in the placement trace: `authorized_3`.

- CONTROL final: day 5 / order 1.
- EXPERIMENTAL final: day 1 / order 2.
- First mutation source: `TimedWeeklyPlacementPlanner.distributeGreedy`, at accepted bucket insertion; typed stage/cause: `INITIAL_WEEKLY_PLACEMENT / INITIAL_PLACEMENT_POLICY`.
- Both traces retain the exact before/after owner role, two-set prescriptions, prescription text, and slot for weeks 1 and 2.
- This is an initial placement decision, not a `BoundedDayRebalancer` or `PostSplitWeeklyReflow` move. The origin trace explains where the placement came from; B8 placement/schedule parity policy is unchanged.

## C10A trace coverage and B7 result

The 22 generated cases produce the following final CONTROL/EXPERIMENTAL delta census. Each cell is `final deltas / exact origin trace / still unproven`.

| Delta kind | Deltas / exact / unproven |
|---|---:|
| Added owner | 100 / 100 / 0 |
| Removed owner | 34 / 0 / 34 |
| Set-count change | 2 / 2 / 0 |
| Prescription-only change | 0 / 0 / 0 |
| Placement move | 90 / 90 / 0 |
| Order change | 14 / 14 / 0 |

All 34 unproven removal deltas are week-1/week-2 removals of 17 CONTROL owner/role pairs that are absent from exact EXPERIMENTAL B5 selection and finite demand. They never reach authorized scheduling, so no builder stage removed them and C10 emits no synthetic removal event:

- `persona0_recent`: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`
- `persona0_reviewed`: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`
- `persona1_sparse`: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`
- `persona1_recent`, `persona1_reviewed`, `reviewed_hypertrophy_isolated`: `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`
- `persona1_mixed`, `persona2_recent`, `persona3_reviewed`, `persona4_sparse`, `persona4_mixed`: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`
- `persona2_mixed`, `persona3_recent`, `persona4_reviewed`, `reviewed_strength_isolated`: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`
- `persona3_recent`: `ex_33841b88#BADMINTON_OBJECTIVE_` and `ex_421ba24b#BADMINTON_OBJECTIVE_`

B7 is connected to the typed trace and requires exact owner plus role, exact stage, exact before/after prefix state, a typed cause, a matching directed cause-owner edge, exact B5-added cause owner, executable B6 target/quality authority, exact authorized demand linkage, and no contradictory later owner mutation. The compatibility stable-key field is not sufficient. Placement evidence cannot close a set reduction, and set evidence cannot close a prescription mutation.

No new delta was closed by C10 typed provenance. There are no real source-emitted cause-owner displacement edges in this corpus. `CHANGE_PROVENANCE_UNCLOSED` therefore remains **14 → 14**; the other B7 outcomes remain `AFFECTED_TARGET_REMAINS_UNMET=9` and `TARGET_REGRESSED=1`. Evidence-free deltas remain `UNEXPLAINED`/`INCONCLUSIVE` and blocked. No synthetic or retrospective capacity attribution was used.

## Parity, ordering, accounting, and route decision

| Check | Before / after | Result |
|---|---|---|
| Routes, 27-case corpus | Control 21, Strength 1, Hypertrophy 0, Combined 0 → same | PASS |
| Generated / rejected | 22 / 5 → same | PASS |
| Standard coverage SHA-256 | `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9` → same | PASS |
| B1–B6 science and authority | No source science changes; standard generated-coverage report byte-identical | PASS |
| CONTROL / EXPERIMENTAL program rows | No program-content or route change from instrumentation; H stays CONTROL | PASS |
| Evaluation order | B1–B6 → EXPERIMENTAL → CONTROL → B7/B8/B9 | PASS |
| CONTROL perturbation invariance | CONTROL remains the late comparator and does not decide EXPERIMENTAL allocation | PASS |
| Normal build accounting | CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0 | PASS |
| Canonical expected failure | 1 / 0 / 1 / 0 | PASS |
| Experimental expected failure | 1 / 1 / 2 / 0 | PASS |
| Preflight rejected | 0 / 0 / 0 / 0 | PASS |

Because no new production route or generated program behavior is opened, protocol/runtime/app versions remain `3.51.0` / `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1` / `0.5.1.5`. No Power/task route, B8 gate, B1–B6 science, or third build was added.

## Tests and environment

- Required Java/Gradle environment check: `JAVA_TOOL_OPTIONS` process value was blank; User value was `-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`; `C:\GradleIpc` exists; Gradle wrapper reports Gradle 9.3.0. The process override was supplied for Gradle invocations only; no machine-specific repository setting was added.
- Compile smoke: `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` passed.
- Focused requested suite: 226 tests, 0 failures, 0 errors, 0 skips. It covered `ExecutionAllocationV012Test`, `BoundedDayRebalancerTest`, `PostSplitWeeklyReflowTest`, `FrequencyExpansionPlannerTest`, `AuthorizedPipelineInvariantTest`, `StimulusExperimentalReadinessTest`, `StimulusProductionCutoverAuthorityTest`, `StimulusProductionQualityAuditTest`, `StimulusProductionCoverageAuditTest`, `CanonicalStimulusPlanningIndependenceTest`, `StimulusProductionFailureBoundaryTest`, and the C10 provenance tests. After the accepted residual-day-remap follow-up, `ResidualCompletionTest` and `PostGenerationAuthorityFreezeTest` passed together; the H-origin fixture test also passed with the stronger no-set/no-prescription-mutation assertion.
- Negative provenance cases cover capacity text without an emitted edge, role mismatch despite matching stable key, wrong displaced owner, placement evidence misused for a set reduction, set reduction misused for prescription change, rejected move exclusion, and contradictory later restoration.
- Positive cases cover exact allocator set reduction, exact accepted whole-row move, exact cause-owner edge, and deterministic multi-week event serialization.
- `BoundedDayRebalancerTest` fixes whole-item set/prescription preservation; `PostSplitWeeklyReflowTest` fixes whole-item movement and accepted-only events.
- The full local `:app:testDebugUnitTest --no-daemon` was attempted twice. Both runs ended in JBR 21 `EXCEPTION_ACCESS_VIOLATION` in `robolectric-nativeruntime.dll`, `SQLiteConnectionNatives.nativePrepareStatement` while Room began a transaction, followed by Gradle localhost worker IPC reset. The final attempt had 1,434 tests recorded, 0 assertion failures, 0 test errors, and 3 skips before the native worker died; the task cannot report a complete local suite. This is classified as Robolectric native SQLite / loopback IPC, not an application assertion failure.
- `PlannerIsolationArchitectureTest` passes locally after the separate test-only CRLF normalization commit `7a66bc9f`.

## Hosted CI and artifacts

- Initial implementation run `36965778658` on `be1638f65ab2f3b70bdc96ba154a34b269618552` exposed the stale source-hash fixture in `PostGenerationAuthorityFreezeTest`; the fixture was updated for the additive C10 trace code.
- Corrected implementation run `36967575998` on `d304889c2d19470f72b099f161b8f16ae7b87c94`: green; all CI steps passed. This SHA contains the same production implementation as the final C10 SHA below.
- Final implementation/test SHA `ccb9360522826892d881e1166af84cee7a7b586a`: [Hosted CI run 36968488029](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36968488029) passed every step: protocol validation, Community/Cloud contracts, whitespace, full unit tests, coverage upload, `assembleDebug`, signer validation, and APK upload. JUnit reports total **2,175 tests, 0 failures, 0 errors, 4 skips**.
- APK artifact ID `11209774997`, archive size 65,024,196 bytes, archive digest `sha256:7cf762682c18a703867948e37b2244c52af5c44a85f86329afd18807dba0beba`. Extracted `app-debug.apk`: 68,608,691 bytes; SHA-256 `5F9D51E13826C024E6858D81B3133D0C8D8306620701BE00C3A263DBB5FC3017`.
- Coverage artifact ID `11210239476`, archive size 465,460 bytes, archive digest `sha256:2f02684ae68de6d8a6dd9bccb2f92d3ffa0107b408c4307cc1f01c7d7060d6d8`. Its corpus remains 27 total / 22 generated / 5 rejected, with CONTROL 21 / Strength 1 / H 0 / Combined 0 and B7 `CHANGE_PROVENANCE_UNCLOSED=14`.
- Docs-only final HEAD/run and its artifacts: pending closeout. The final SHA and final docs-only CI result will be recorded in the completion report after the closeout commit and Hosted CI.
