# C17 placement causality and stability audit

## Audit identity

- C16 merge SHA: `128cdf2c359a5cc82a25984924175897586b7624` (PR #8; merged 2026-10-03T15:06:21Z).
- C17 start SHA: `128cdf2c359a5cc82a25984924175897586b7624`.
- C17 audit/test implementation SHA and `lastAuditedCommit`: `e7e66730fb4a1a712da978692e03c6ec4bc93c1b`.
- Final audited code/test SHA: `e7e66730fb4a1a712da978692e03c6ec4bc93c1b`. This audit document is a follow-on documentation commit; its resulting repository HEAD is reported in the completion record.
- Protocol: 3.52.0; runtime: `RECORD_BASED_PLANNER_0.14.4_KOTLIN_1`; app: 0.5.1.5.

## Scope and result

C17 audited the 32 C16 placement/order deltas in `persona0_mixed`, `persona0_reviewed`, `persona3_reviewed`, and `persona4_mixed`. The rows are 16 exact `(stableKey, selectionRole)` pairs across weeks 1 and 2. The two Power additions in `persona3_reviewed` were not modified or authorized.

All 32 rows preserve set count, reps, load/load state, target effort, rest, and weekly frequency. The audit measured 12 `UNNECESSARY_PLACEMENT_DRIFT` rows and 20 `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` rows. It proved zero `NECESSARY_AUTHORIZED_DISPLACEMENT` rows and zero displacements caused by an unauthorized trigger. No exact placement displacement edge existed for any row.

The 12 drift rows meet the counterfactual test: restoring that exact owner-week to its CONTROL comparator day/order, while retaining canonical EXP rows, passed the current placement constraint checks. CONTROL is used only inside this test-side comparison. The production planner never reads CONTROL placement.

The other 20 rows cannot safely be assigned either binary outcome. Their counterfactual is rejected because current tissue projection returns unresolved exercise keys; the `persona3_reviewed` Romanian deadlift rows also cross the day-2 OFI gate at 94 with `RECOVERY_DEBT_HIGH`. An unresolved projection is not proof of a real tissue violation, and it is not proof that the prior placement is valid. No source-emitted trigger edge or complete lower-change placement proof resolves those rows. They remain fail-closed and explicitly unresolved.

## Origin stages and placement policy

| Evidence | Count | Finding |
|---|---:|---|
| First comparator-divergent assignment | 32 | `INITIAL_WEEKLY_PLACEMENT` / `PLACEMENT_ASSIGNED` / `INITIAL_PLACEMENT_POLICY` |
| Accepted later rebalancer moves | 4 | `BOUNDED_DAY_REBALANCER`: core and calves, weeks 1 and 2, all in `persona4_mixed` |
| `POST_SPLIT_WEEKLY_REFLOW` events for these rows | 0 | No moved row was first or finally moved there |
| Exact placement displacement edges | 0 | No row has a source edge with exact from/to, trigger authority, constraint, and necessity evidence |

`TimedWeeklyPlacementPlanner.distributeGreedy` creates empty day buckets for each pass. It sorts by priority, represented gap, style, estimated duration, stable key, and style variant, then chooses a feasible day using lower-stress occupancy, optional schedule tier, total occupied seconds, and day number. It assigns the next order slot. No moved row emitted a `TIMED_EXECUTION_ALLOCATION` session-capacity displacement event. The stage receives no incumbent canonical skeleton or prior canonical placement anchor. Thus its emitted `PLACEMENT_ASSIGNED` event explains where the fresh pass placed a row, but it does not establish that a CONTROL-to-EXPERIMENTAL move was necessary or name an authorized owner that displaced it.

The later rebalancer records four accepted `REBALANCE_OBJECTIVE` moves. It emits the moved owner, before/after placements, and acceptance evidence, but no trigger owner/target or hard-conflict edge. Its score improvement is not by itself placement authority. The destination was accepted by OFI/tissue gates, but necessity and a lower-change alternative were not established.

All 32 moved shared rows are not B5-selected for the current targets; their C17 evidence has no B5 target relation, no exact B6 authority, and no direct B7 attribution. Each first event names the assigned row itself and has no separate `triggerOwner`; its `targetIds` and `qualities` are empty. C10 events sometimes include legacy `authorizedDemandIds`, but those IDs do not constitute B4/B5/B6 authority for the moved owner. The four later rebalancer events likewise name no trigger owner or target. This does not forbid a future narrow derived placement authority; it means none is proven here.

## Counterfactual and stability census

Each counterfactual restores one exact shared owner-week row to the comparator placement, keeps the generated EXP additions, and rechecks sessions, OFI, tissue projection, spacing, frequency, and material parity. The full restoration check is also retained per case in the machine census. A failed check with unresolved tissue is reported as unresolved, never upgraded to a hard violation by inference. Restoring all shared rows at once produced no session-time overflow in any case: `persona0_mixed` max 1,200/3,600 seconds; `persona0_reviewed` 1,020/1,800; `persona3_reviewed` 1,020/1,800; `persona4_mixed` 1,200/5,400. Therefore the counterfactuals show no day-capacity requirement for these placement changes.

| Case | Wk | Exact owner | CONTROL → EXP (day/order) | Origin / final stage | Retain-old-placement result | Classification |
|---|---:|---|---|---|---|---|
| `persona0_mixed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 3/1 → 1/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona0_mixed` | 1 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | 1/2 → 3/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona0_mixed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/3 → 3/3 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona0_mixed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 3/1 → 1/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona0_mixed` | 2 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | 1/2 → 3/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona0_mixed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/3 → 3/3 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona0_reviewed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 2/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona0_reviewed` | 1 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | 4/1 → 2/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_chest_supported_row | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona0_reviewed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 2/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona0_reviewed` | 2 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | 4/1 → 2/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_chest_supported_row | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 6/1 → 4/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona3_reviewed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 2/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; day 2 OFI 94 ([RECOVERY_DEBT_HIGH]); tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 1 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | 4/1 → 2/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_chest_supported_row | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/1 → 6/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 6/1 → 4/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona3_reviewed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 2/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; day 2 OFI 94 ([RECOVERY_DEBT_HIGH]); tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 2 | `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` | 4/1 → 2/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_chest_supported_row | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona3_reviewed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/1 → 6/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 1 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 1/2 → 3/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona4_mixed` | 1 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS` | 3/3 → 1/3 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_reverse_curl | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 1 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 3/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 1 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | 3/2 → 1/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona4_mixed` | 1 | `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS` | 3/4 → 1/4 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_lying_triceps_extension | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 1 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/3 → 3/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 1 | `ex_5ca7133f#COVERAGE_CALVES` | 5/1 → 5/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona4_mixed` | 2 | `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` | 1/2 → 3/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona4_mixed` | 2 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS` | 3/3 → 1/3 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_reverse_curl | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 2 | `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` | 3/1 → 1/1 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: barbell_romanian_deadlift | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 2 | `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL` | 3/2 → 1/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |
| `persona4_mixed` | 2 | `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS` | 3/4 → 1/4 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: dumbbell_lying_triceps_extension | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 2 | `ex_28347c1f#COVERAGE_CORE_DIRECT` | 1/3 → 3/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | FAIL; tissue projection unresolved: ex_28347c1f | `UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT` |
| `persona4_mixed` | 2 | `ex_5ca7133f#COVERAGE_CALVES` | 5/1 → 5/2 | INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY; final stage INITIAL_WEEKLY_PLACEMENT | PASS; prior day/order remains valid | `UNNECESSARY_PLACEMENT_DRIFT` |

The exact before/after prescription fields, B5/B6/materialization evidence, source-event data, counterfactual violations, and trigger sensitivity records for every row are in [`c17-placement-causality-census.json`](c17-placement-causality-census.json). That file is deterministically ordered by case, week, stable key, and role. The test also renders the full hosted report at `app/build/reports/c17-placement-causality-census.json`.

### Case dossiers

**`persona0_mixed` — 6 rows, 3 exact owner-role pairs.** B4 is `RESTORE_PERSONAL_BASELINE` with numeric `PERSONAL_RESTORE_BASELINE` authority. The authorized cold-start owner is `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`; its 2×6, RPE 6.5, `USER_CALIBRATION_REQUIRED` rows are at day 3/order 1 in both weeks and are authorized/materialized by C15. Four shared-row counterfactuals pass (squat and rear-delt fly in both weeks), so those are drift. The two core rows remain unresolved because restoring day 1 fails tissue projection for `ex_28347c1f`; the projection has no blocked units, only an unresolved key. B7 remains eligible for future cutover review; B8 remains CONTROL_REQUIRED.

**`persona0_reviewed` — 4 rows, 2 exact owner-role pairs.** B4 is `RESTORE_PERSONAL_BASELINE` with numeric `PERSONAL_RESTORE_BASELINE` authority. The calibration owner is `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`, with 2×6 at RPE 6.5 and `USER_CALIBRATION_REQUIRED`, placed at day 4/order 1 in both weeks. Restoring the RDL to day 2 and chest-supported row to day 4 fails only the tissue projection for `barbell_romanian_deadlift` and `dumbbell_chest_supported_row` in both weeks. That evidence cannot prove a required cascade or a valid old schedule. B7 remains eligible; B8 remains CONTROL_REQUIRED.

**`persona3_reviewed` — 8 rows, 4 exact owner-role pairs.** B4 is `RESTORE_PERSONAL_BASELINE` with numeric `PERSONAL_RESTORE_BASELINE` authority. The exact Strength calibration owner is `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`, 2×6 at RPE 6.5 with `USER_CALIBRATION_REQUIRED`, day 1/order 2 in both weeks. The squat rows restore successfully and are drift. RDL, chest row, and core rows remain unresolved; restoring the RDL to day 2 yields OFI 94 and `RECOVERY_DEBT_HIGH`, as well as an unresolved RDL tissue projection. A sensitivity-only rerun without the Power row removes the OFI violation but leaves unresolved RDL tissue. This does not establish that Power caused the actual assignment: no source edge connects it, and Power remains `DIRECTION_ONLY` without B6 authority. The two Power additions are at day 2/order 2, each 3×5; their B4 strategy is `DIRECTION_ONLY` and B6 is unavailable. They are excluded from C17 placement authorization and this case remains CONTROL.

**`persona4_mixed` — 14 rows, 7 exact owner-role pairs.** Squat and rear-delt rows (four total) restore successfully and are drift. The two calves deltas are order-only at day 5, comparator order 1 to EXP order 2; retaining order 1 passes the counterfactual, so these are unnecessary renumbering and count within the 12 drift rows. Reverse curl, RDL, triceps extension, and core rows (eight total) remain unresolved because the tissue projection returns those exact keys as unresolved. B4 is `RESTORE_PERSONAL_BASELINE` with numeric `PERSONAL_RESTORE_BASELINE` authority. The calibration owner is `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`, 2×6 at RPE 6.5 with `USER_CALIBRATION_REQUIRED`, day 5/order 1 in both weeks. The accepted rebalancer events move core from its initial day 1 placement to day 3 and calves from initial day 1 to day 5 in each week; they improve a soft balance objective, but emit no causal trigger edge. The old comparator placement for core is not proven valid; calves’ final comparator order change is avoidable.

## B7/B8 status by case

| Case | B7 | B7 reasons | B8 | Exact B8 reason codes |
|---|---|---|---|---|
| `persona0_mixed` | `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` | none | `CONTROL_REQUIRED` | `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY`; `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`; `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`; `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`; `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`; `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY` |
| `persona0_reviewed` | `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` | none | `CONTROL_REQUIRED` | Same six codes as `persona0_mixed` |
| `persona3_reviewed` | `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` | none | `CONTROL_REQUIRED` | The same six codes, plus `B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY` and `B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE` |
| `persona4_mixed` | `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` | none | `CONTROL_REQUIRED` | Same six codes as `persona0_mixed` |

These are the exact status/reason outputs from the current C16 comparison at C17 start; C17A does not modify them. The C17 movement ledger proves avoidable placement drift for 12 rows and leaves 20 rows without enough constraint evidence. It does not claim that all listed B8 codes are caused by each placement row, and it does not reinterpret B8 labels as displacement authority.

## Rebalancer detail

For the core moves, `BOUNDED_DAY_REBALANCER` accepted day 1/order 5 → day 3/order 2 in both weeks with cause `REBALANCE_OBJECTIVE`. Its recorded objective changed from 3 band violations / max 0.70 / total 1.7343 to 3 / 0.4160 / 0.9180. Day-1 OFI changed 94→84 and day-3 0→30; OFI and current restriction gates passed. The event has no trigger owner or target, and the CONTROL day-1 comparator counterfactual still has unresolved core tissue, so the score change proves neither necessity nor comparator validity.

For the calves moves, the rebalancer accepted initial day 1/order 6 → day 5/order 2 in both weeks with the same typed cause. The objective changed from 4 band violations / max 0.70 / total 2.658 to 3 / 0.70 / 1.734. Day-1 OFI changed 98→94 and day-5 0→35; gates passed. The actual CONTROL-to-EXPERIMENTAL delta is only day 5/order 1→2. Restoring that old order is valid, so this order renumbering is drift even though the preceding fresh-layout assignment and later accepted rebalancer move are also recorded.

## Positive reference and other references

`persona2_reviewed` is the positive calibration reference: C15 authorization is `B8_STRENGTH_CALIBRATION_V1`, with two exact calibration rows. The new row is appended at day 1/order 4 in each week, after the three existing day-1 rows at orders 1–3. All 14 shared owner-week rows preserve both day and order. The observed placement condition is that this fresh placement pass had a feasible insertion slot without moving any shared row; the audit does not infer a general capacity guarantee from this single case.

`reviewed_strength_isolated` remains the exact-load `B8_STRENGTH_V1` reference and is unchanged. `reviewed_hypertrophy_isolated` remains CONTROL under its independent provenance/scope blockers. No C14/C15 prescription logic, B4–B6 selection/authority, B7 eligibility, or B8 predicate was changed.

## Aggregate, routes, and C17B decision

| Measure | Before C17 | After C17A |
|---|---:|---:|
| CONTROL | 20 | 20 |
| Strength V1 | 1 | 1 |
| Strength Calibration | 1 | 1 |
| Hypertrophy | 0 | 0 |
| Combined | 0 | 0 |
| B7 `CHANGE_PROVENANCE_UNCLOSED` | 11 | 11 |
| B7 `AFFECTED_TARGET_REMAINS_UNMET` | 9 | 9 |
| B7 `TARGET_REGRESSED` | 1 | 1 |
| Placement rows | — | 32 |
| Shared rows preserving day (of 38) | 8 | 8 |
| Shared rows preserving exact day+order (of 38) | 6 | 6 |
| Moved owner-week rows | 32 | 32 |
| Total absolute day distance | 62 | 62 |
| Order-only rows | 2 | 2 |
| Proven necessary authorized moves | — | 0 |
| Proven avoidable drift | — | 12 |
| Unresolved | — | 20 |

No C17B production repair was warranted. Although 12 placements are proven avoidable, the generation pass has no prior canonical placement state to preserve. Using CONTROL placement in production is prohibited. The 20 other rows lack sufficient tissue projection and/or exact causal proof for a safe displacement rule. No derived placement authority was added, no B8 gate was relaxed, and no route changed. The PR #9 remains a draft pending review of the unresolved audit evidence.

## Validation

- Local compile smoke: `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` — PASS (with process-local `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`).
- Focused C10–C17 suites, including `OwnerAllocationProvenanceTest`, B7/B8 and C10/C11/C13/C16 audits, placement rebalancer/reflow/frequency tests, independence/failure-boundary tests, and C17 classifier/census tests — PASS.
- Full Windows local suite — incomplete. 1,472 tests completed, 0 assertion failures, 0 errors, 3 skips before JBR 21 `robolectric-nativeruntime.dll` raised `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`; Gradle worker IPC then reset. This is not a pass.
- Hosted Linux CI for audit/test commit `e7e66730…`: run [37138009146](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37138009146) passed protocol validation, Community/Cloud contracts, whitespace, full tests, coverage artifact upload, APK assembly, signer validation, and APK upload. Results: 2,239 tests, 0 failures, 0 errors, 4 skips. Standard coverage SHA-256: `55cd3c4e9e58b700ed4577a6c0ce0a99fd847f552a334b45fd0815e6fc8825ab`. Hosted census report: 7,041,386 bytes, SHA-256 `a67f2d8d8613149ce292751a89888f14cca7f8aa1aa49433c7966aef25ccb3aa`. APK artifact `WhatYouGottaDo-debug-apk`: 68,675,787 bytes, SHA-256 `655302095255311608c7605a5be7985729fe614008037cecf9041e02a2bea8fb`; signer validation passed.
- C17 did not alter versions, production routes, B7 counts, B1–B6 semantics, build accounting, or B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9 ordering. Normal build accounting remains CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0; preflight rejection remains 0 / 0 / 0 / 0. CONTROL remains comparator-only; the counterfactual is test-side and invokes no additional production program build.

## Next phase

C18 should first make the remaining 20 decisions knowable: resolve canonical tissue projection for the five unresolved owner keys and provide a genuine canonical incumbent placement input if the product intends week-to-week placement continuity. Then instrument source mutation points to emit exact trigger-owner, target/demand, from/to, constraint-before/after, destination, and necessity evidence. Only those exact edges can support derived placement authority. Power remains a separate blocked concern.
