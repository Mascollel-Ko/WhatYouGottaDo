# C21 — Power dose and execution authority audit

## Identity and baseline

- C20 merge: `69a58df6c210924cafbe7fc6ab478b8cba517c94` (PR #12; merged 2026-10-04 17:04:18 UTC).
- C21 start: `69a58df6c210924cafbe7fc6ab478b8cba517c94`.
- C21 audit/test commit: pending.
- C21 implementation/test commit: pending.
- Final audited commit: pending.
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

The C21 correction is limited to that boundary: a canonical B5 material owner that lacks exact executable B6 authority must not be sent through the generic prescription fallback. The candidate is omitted before allocation/materialization and recorded as deferred. This does not alter B4, B5 selection, B6's fail-closed Power decision, B7, B8, or C20 placement rules. Non-B5 legacy performance continuity keeps its existing behavior.

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

The corpus audit must verify that the unsupported Power rows disappear, while B4 remains direction-only and B6 remains model-unavailable. C20 live incumbent feasibility is remeasured after removing the unauthorized Power material: a changed feasibility count is accepted only with exact before/after evidence. Hard-valid incumbents remain eligible for preservation, hard-invalid incumbents are never forced, unresolved incumbents stay unanchored, and the placement pipeline remains CONTROL-independent. B7/B8 gates are unchanged.

Final route counts, B7 counts, C20 feasibility, local/hosted test results, coverage checksum, and version decision are recorded after the final corpus run below.
