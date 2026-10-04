# C21 — Power dose and execution authority audit

## Identity and baseline

- C20 merge: `69a58df6c210924cafbe7fc6ab478b8cba517c94` (PR #12; merged 2026-10-04 17:04:18 UTC).
- C21 start: `69a58df6c210924cafbe7fc6ab478b8cba517c94`.
- C21A audit/test commit: `266537f942e582a104f6f1a2556fe7000deceba2`.
- C21 implementation/test commit and `lastAuditedCommit`: `0bd67e9cff3de1bf7d12aa803445e6df773657e5`.
- C21 `lastAuditedCommit`: `0bd67e9cff3de1bf7d12aa803445e6df773657e` (implementation/test commit; documentation-only commits do not replace it).
- C20 protocol/runtime/app: `3.55.0` / `RECORD_BASED_PLANNER_0.14.7_KOTLIN_1` / `0.5.1.5`.
- Verified C20 merged-main CI: run `37219120715`, success; 2,273 tests, 0 failures, 0 errors, 4 skips. Protocol, Community/Cloud contracts, whitespace, tests, coverage, APK assembly, signer validation, and artifact upload all passed.
- C20 route baseline: CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0.
- C20 B7 baseline: provenance-unclosed 11, target-unmet 9, target-regressed 1.
- C20 placement policy remains active: hard-valid incumbent placements are preserved, hard-invalid placements released, and unresolved placements unanchored.

## C21A — exact Power evidence

The deterministic pre-change corpus contains 27 cases: 22 generated and 5 preflight rejected. All 22 generated cases expose a Power target, but only the four `persona3_*` cases have a numeric-looking B5 Power owner candidate. Those four B4 Power targets are `DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY / DIRECTION_ONLY`; their weekly direct-unit and weekly-session targets are absent. They have no owner-local Power history. The corpus contains no direct Power observations for another owner either. B2 therefore has no observable personal Power dose from which to construct numeric B4/B6 authority.

In `persona3_reviewed`, B1 records a low-confidence, moderate-relevance Power `DEVELOP` need, with zero direct/supportive Power units in the preceding 28 days. B1 also reports low-confidence badminton task needs. B3 marks the related tasks for `INTRODUCE_DIRECT_STIMULUS`, while explicitly retaining direction-only task authority. B4 keeps both Power and the DECELERATION task at `DIRECTION_ONLY`, with no weekly unit/session bounds. B5 selects the exact owner `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER`; its evidence says the three-set count came from an existing prescription probe, and explicitly says that probe set count is not target authority.

Canonical physical metadata does establish a reviewed, prescription-dependent `DIRECT_CAPABILITY` relation for POWER (`pq_022`, unilateral lower, plyometric). The canonical badminton objective catalog links this exercise to DECELERATION at `SUPPORTIVE`, not `DIRECT`, along with other supportive objectives. The runtime exercise is the bodyweight unilateral `원레그 홉 투 스틱` (`ATHLETIC_PERFORMANCE_DRILL`). This capability metadata establishes that the exercise can express Power; it does not authorize numeric weekly work.

The existing `RecordBasedReviewedPolicy` places `ex_314df428` in its exact DECELERATION membership, which contains five stable keys. The DECELERATION guide says 3 sets, 5 reps, 75 seconds rest, and `3세트 x 5회/side`. This is a reviewed category-level performance guide. It does not establish an exact owner-specific Power prescription or a weekly frequency. The program set/record shape stores an integer repetition count but has no per-side field, so it cannot losslessly encode the guide's `/side` meaning. The available-week schedule is a calendar of possible training days, not exercise frequency authority.

No direct same-owner Power history, different-owner Power dose, task/court exposure, or other personal evidence in this corpus supplies an observable exact prescription. In particular, the corpus has zero confirmed rows and zero classified direct-Power observations for `ex_314df428`. C14 Strength e1RM/RIR machinery is not used, and no velocity, RPE, or generic Power dose is inferred.

## Why EXP had 3×5 while B6 rejected Power

The two rows in each of the four `persona3_*` cases (eight rows total) came from an authority bypass, not from B6. During B5 candidate probing, `PersonalizedPrescriptionPlanner` dispatches `ATHLETIC_PERFORMANCE_DRILL` to `PerformancePrescriptionResolver`. With no recent personal execution or canonical performance override, that resolver reads the legacy reviewed DECELERATION category guide and returns three 5-rep sets with 75-second rest and source `REVIEWED_BADMINTON_RULE_DECELERATION`.

Later, canonical B6 receives the Power target and correctly returns `MODEL_UNAVAILABLE / CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE`; the resolution is `REALIZATION_MODEL_UNAVAILABLE`, and materialization is `NOT_MATERIALIZED` with `NO_EXECUTABLE_AUTHORIZATION`. However, `PersonalizedProgramBuilder.buildCore` handled `ExactOwnerPrescriptionResolution.NoExactAuthority` by calling the generic `generationPrescriptions.prescribe(...)` fallback. That fallback again invoked `PerformancePrescriptionResolver`, placing the reviewed 3×5 shape into EXP despite the absence of numeric B4 and executable B6 authority. The old row's displayed text looked executable, but its B6 audit correctly had no authorized prescription.

The C21 correction is limited to that boundary: an exact canonical B5-selected Power owner that lacks executable B6 authority must not be sent through the generic prescription fallback or performance-continuity re-entry. That exact owner is omitted before allocation/materialization and recorded as deferred. Other B5 qualities and non-B5 legacy performance continuity keep their existing behavior.

## Decision: no reviewed starter bridge in C21

The DECELERATION guide does not satisfy the requested complete chain for an executable Power dose. The blockers are independent and exact:

1. B4 has no numeric Power unit or session target and no weekly frequency authority.
2. The exercise-to-badminton DECELERATION relation is `SUPPORTIVE` and does not supply direct task prescription authority.
3. The guide is category-wide across five keys, not an exact B5 owner-and-quality Power authority.
4. The guide's `5/side` laterality cannot be represented losslessly by the current program set model.
5. There is no compatible personal owner-local dose history to replace those gaps.

Therefore C21's exact persona3 outcome is `POWER_REMAINS_DIRECTION_ONLY`. No `REVIEWED_TASK_STARTER_DOSE`, Power B4 numeric authority, Power B6 execution authority, Power route, or Combined route is added. The reviewed guide remains its original source of truth and is not copied into a second authority table.

## C21C — corpus and safeguards

The pre-change full corpus census is preserved in [`c21-power-dose-authority-census-before-filter.json`](c21-power-dose-authority-census-before-filter.json). It records 8 provisional Power rows, 0 numeric Power authorities, 0 personal Power authorities, 0 reviewed-starter authorities, and 4 still-direction-only Power targets. The final post-filter census is [`c21-power-dose-authority-census.json`](c21-power-dose-authority-census.json) (to be regenerated after the implementation commit).

The corpus confirms the unsupported Power rows disappear while B4 remains direction-only and B6 remains model-unavailable. No Power target has numeric authority, no Power row is executable/materialized, and no Power route is supported. The category guide remains the existing guide source; C21 does not add a second rule table.

## C21C — final corpus and safety results

The post-filter corpus contains 27 cases (22 generated, 5 preflight rejected), 22 Power targets, 4 direction-only Power targets, and 4 exact B5 Power rows for one unique owner. Numeric Power authorities: 0; personal-history authorities: 0; reviewed-starter authorities: 0; still direction-only: 4; fully materialized Power prescriptions: 0. The exact selected owner has 0 compatible personal Power observations.

The former `persona3_*` Power rows are all absent (8 provisional rows before, 0 after). This is an authority-boundary correction: B5 identity without exact B6 no longer re-enters via the legacy guide. B4/B5/B6 policy, B7/B8 predicates, Power scope, and Combined scope remain unchanged. No generic Power dose, RPE, velocity, or Strength-derived load rule was introduced.

Routes after the correction are CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0. `persona3_mixed` independently qualifies for the existing Strength Calibration route; it has no Power material. `persona3_reviewed` remains CONTROL: its Power target stays direction-only and its existing Strength V1 cutover blockers remain. The four `persona3_*` Power targets do not route through a Power or Combined path.

B7 remains provenance-unclosed 11, target-unmet 9, target-regressed 1. B8 was not modified. The C20 incumbent policy remains active: 12 HARD_VALID incumbents preserved, 0 HARD_INVALID, and 20 UNRESOLVED; 0 invalid or unresolved rows were forced to stay. The two former hard-invalid RDL results became unresolved after the unsupported Power exposure was removed; RDL tissue evidence remains unresolved, so C20 correctly does not anchor them. No third program build was introduced; CONTROL and EXPERIMENTAL ordering remains unchanged and CONTROL is not a dose source.

The standard production coverage checksum changed from C20's `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB` to `5BD1E9430352618C6C42F399ED28B8A065908CEDCA8F4448924BD9301CB44BD1` because the 8 unauthorized Power rows were removed. This is the intended output delta. Protocol/runtime were bumped to `3.56.0` / `RECORD_BASED_PLANNER_0.14.8_KOTLIN_1`; app remains `0.5.1.5`. The incumbent source-contract index accepts C19 and C20 protocol/runtime pairs so the version bump does not discard persisted incumbent continuity.

Local compile smoke passed for `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin`. The full local unit suite passed: 2,278 tests, 0 failures, 0 errors, 4 skips. The first full attempt found eight failures in `BoundedMaterialDemandTest`, whose private reflection helper still called `buildCore` with its pre-C21 argument count; the helper was updated to pass the new empty exact-Power-owner set, its focused suite passed, and the full rerun passed. Protocol documentation validation passed. Focused C21/C20 tests and this full suite use the external Gradle worker-restart init script; it is not part of the repository.

The final deterministic report is [`c21-power-dose-authority-census.json`](c21-power-dose-authority-census.json), SHA-256 `EFC2C14F7248FB17BE99CF52BF07E02BE51FA46BF3924BB10521F181F7E7BECB`. It records 27 corpus cases, 22 generated, 5 preflight rejected, 22 Power targets, 4 direction-only targets, 4 exact B5 owner rows, 0 numeric/personal/reviewed-starter Power authorities, 0 materialized Power rows, and 0 Power-authorized/routed cases. The coverage SHA is `5BD1E9430352618C6C42F399ED28B8A065908CEDCA8F4448924BD9301CB44BD1`. C20 feasibility after the output correction is 12 HARD_VALID / 0 HARD_INVALID / 20 UNRESOLVED, with 12 preserved and 0 non-valid anchors forced. Routes are CONTROL 19 / Strength V1 1 / Strength Calibration 2 / H 0 / Combined 0; B7 remains 11 / 9 / 1.

The eight legacy rows are all removed from `persona3_mixed`, `persona3_recent`, `persona3_reviewed`, and `persona3_sparse`. In `persona3_reviewed`, the exact B5 Power owner remains selected for the direction-only target, while B6 still reports `MODEL_UNAVAILABLE / CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE`, materialization remains absent, and the route remains CONTROL. `persona3_mixed` routes through its independently valid Strength Calibration authority; this does not authorize Power. The unsupported Power rows are not preserved as incumbents; removal also changes two RDL incumbent feasibility results from hard-invalid to unresolved because their previous recovery finding depended on the unauthorized Power rows. C20 keeps those RDLs unanchored.

The implementation/test commit is the `lastAuditedCommit`; documentation-only commits do not replace the audited code SHA. The implementation run is linked in the validation record, and PR #13's latest check validates the final audit and census revision.

## Exact `persona3_reviewed` disposition

- B1 Power: `DEVELOP`, moderate relevance, low confidence; no direct/supportive Power dose in the recent ledger. B1 task needs include badminton DECELERATION at low confidence.
- B2 Power: no observable owner-local set prescription and no compatible direct Power observation. The selected owner's same-key confirmed-set count is zero.
- B3: introduces direct stimulus for the relevant quality/task, but the strategy is directional. It does not invent numeric dose.
- B4 Power: `DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY`, `DIRECTION_ONLY`; weekly units and weekly sessions are absent. DECELERATION task target is also direction-only.
- B5: selects `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER` for the exact quality target. The probe notes the legacy guide's three sets, but explicitly marks that count as not being target authority.
- Canonical physical relation: `pq_022`, Power `DIRECT_CAPABILITY`, unilateral-lower/plyometric and prescription-dependent. The canonical badminton DECELERATION relation is `SUPPORTIVE`.
- Reviewed guide: DECELERATION, 3 sets × 5 reps/side, 75-second rest, `RULE_TABLE`. It is one category-level guide shared by five keys; no weekly exercise frequency is specified. The program prescription model has no exact per-side representation.
- Existing candidate shape: generated during the performance-drill resolver probe using `REVIEWED_BADMINTON_RULE_DECELERATION`; the later builder fallback materialized it despite missing B6. It was therefore a provisional legacy shape, not a B4 or B6 prescription.
- B6: `MODEL_UNAVAILABLE`, `CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE`; no authorized prescription. After the C21 boundary change, materialization is absent and the case remains CONTROL for independent B8 Strength-scope blockers.

This is why the C21 result is `POWER_REMAINS_DIRECTION_ONLY`, rather than a reviewed starter authorization. The implementation removes the ungrounded row at the B5/B6 material-demand boundary. It does not reinterpret a task guide as a Power target, infer weekly frequency from available training days, turn `5/side` into five total reps, or treat missing load/RPE as permission to synthesize fields.

## C20 and routing interaction

The C20 placement pass still runs in its established location after EXPERIMENTAL placement and before CONTROL construction. C21 changes no C20 feasibility or preservation logic. The prior unsupported Power row contributed to two persona3 RDL recovery findings; with that row gone, those RDL placements are now `UNRESOLVED` because exact RDL tissue input is still missing, rather than hard-invalid. They remain unanchored. This distinction is recorded in the census; neither an unresolved placement nor the legacy CONTROL location becomes authority.

`persona3_mixed` changes from CONTROL to the already existing Strength Calibration route because its exact Strength calibration evidence and B8 scope pass after the unauthorized Power material is removed. Its Power target remains direction-only and has no material rows. `persona3_reviewed` does not gain a Power or Combined route: its Strength-side B8 reasons remain independent of the removed Power shape. Thus this route delta is not evidence of Power authorization.

## Validation record

| Check | Result |
|---|---|
| C21A evidence audit | Complete; exact category guide rejected as a numeric Power bridge |
| C21B/C implementation | Exact B5-selected Power demand without exact B6 is deferred before allocation; no generic rule |
| C21 synthetic boundary tests | Pass: missing exact B6 defers; exact B6 retains; same key/wrong role does not borrow; nonmaterial owner is not deferred |
| Focused production/continuity tests | Pass: coverage audit, incumbent index, B5/B6 material-demand boundary |
| Local compile | Pass: `compileDebugKotlin` and `compileDebugUnitTestKotlin` |
| Local full unit suite | Pass: 2,278 tests, 0 failures, 0 errors, 4 skips |
| Protocol documentation validator | Pass: 9 families, 36 protocols |
| `git diff --check` | Pass before implementation commit |
| Hosted implementation CI | Pass: [run 37230591031](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37230591031), 2,278 tests, 0 failures, 0 errors, 4 skips; protocol, Community/Cloud contracts, whitespace, coverage upload, APK assembly, signer validation, and APK upload all succeeded |

The post-filter machine report is linked above. Its SHA-256 is `EFC2C14F7248FB17BE99CF52BF07E02BE51FA46BF3924BB10521F181F7E7BECB`. Post-filter production coverage is `5BD1E9430352618C6C42F399ED28B8A065908CEDCA8F4448924BD9301CB44BD1`; the change from the C20 checksum is exactly the omission of eight unauthorized Power rows. C21 keeps the application version at `0.5.1.5` and introduces no Room migration.

Hosted run 37230591031 uploaded coverage artifact `Stimulus-production-coverage` (artifact ID `11313856973`) and debug APK artifact `WhatYouGottaDo-debug-apk` (artifact ID `11314136349`). The APK is 68,757,703 bytes with SHA-256 `56577DCBBE674D33B5FC7280C012DCB4F025DBB1BC69CEFA98000805BE53FC60`. The PR implementation head was `0bd67e9cff3de1bf7d12aa803445e6df773657e5`; after this report and raw census are committed, final documentation CI remains required.
