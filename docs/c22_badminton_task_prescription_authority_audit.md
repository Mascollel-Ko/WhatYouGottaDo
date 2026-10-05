# C22 — Badminton task prescription authority audit

## Scope and conclusion

C21 merged to `main` at `3af7c7c7d96923b6218f4466506a278c3ac76f7f`. C22 started from that same commit. This document first records the C22A baseline; the C22C implementation and final verification are appended in a later commit.

C22 found no exact task prescription authority that currently satisfies the complete B4 → B5 → task execution → materialization chain. The reviewed badminton guides are category-level legacy guidance/fallbacks, not reviewed canonical task B6 authority. Exact personal task prescription evidence and numeric weekly frequency authority are absent in the generated corpus. The app therefore cannot authorize executable task additions from these targets yet.

The audit also found a B5 materialization bypass: two exact B5 task owners in `persona3_recent` became four timed EXP rows through `PerformancePrescriptionResolver`'s canonical-program seed fallback, despite B4 being direction-only and no task-specific B6 existing. C22C will block those rows at the exact B5 task-owner boundary. It will not create a task dose, task B6, B8 scope, or route.

## C21 baseline carried into C22

- C21 merge/main: `3af7c7c7d96923b6218f4466506a278c3ac76f7f`; PR #13 merged 2026-10-05 01:03:14 UTC.
- Protocol/runtime/app at C22 start: `3.56.0` / `RECORD_BASED_PLANNER_0.14.8_KOTLIN_1` / `0.5.1.5`.
- Routes: CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0.
- B7 occurrences: provenance-unclosed 11, target-unmet 9, regressed 1.
- Power: numeric authority 0; executable Power B6 0; material Power rows 0.
- C20 incumbent state: 12 hard-valid, 0 hard-invalid, 20 unresolved; the unresolved rows were not forced to remain.

## Audit method

The deterministic census evaluates all six task targets in each of the 22 generated corpus cases, for 132 task-target rows. It joins B1 task need, B3 decision, B4 target, exact B5 candidate identity and covered targets, exact canonical badminton objective relations, exact `RecordBasedReviewedPolicy` membership, exact-owner confirmed records, current resolver output, final EXP material, and B7/B8/route diagnostics. Five preflight-rejected cases are included in the census with no generated task targets.

The source report is [c22-badminton-task-authority-census.json](c22-badminton-task-authority-census.json). B5's `targetSetsFromExistingPrescription` is retained only as probe evidence; the selection trace itself says `B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY` and compatibility is `DIRECTIONAL_TASK_IDENTITY_ONLY`.

## Corpus totals

- Task targets: 132, six per generated case.
- Direction-only task targets: 24, four cases × six tasks. They occur in `persona3_mixed`, `persona3_recent`, `persona3_reviewed`, and `persona3_sparse`; all other generated task targets are `NO_MINIMUM_TARGET`.
- Exact B5 task-owner rows: 2, both in `persona3_recent`; unique exact task owners: 2.
- Selected owner-to-task relations: 5 DIRECT, 0 SUPPORTIVE. These relations establish task capability/coverage only.
- Exact-owner task observations / personal task prescription authorities: 0.
- Reviewed starter authorities: 0; fully encoded task authorities: 0.
- B4 task weekly units and weekly sessions are absent for all 24 direction-only targets. Available training weekdays are not a required task frequency.
- No task B8 authorization or task route exists.

The six-task matrix is:

| Task | Generated targets | Direction-only | Exact selected-owner evidence | Current executable task authority |
|---|---:|---:|---|---|
| ACCELERATION | 22 | 4 | `ex_33841b88`, DIRECT, primary target | No: B4 frequency absent; STEP guide has no approved task mapping and its time range is not encoded |
| DECELERATION | 22 | 4 | `ex_33841b88`, DIRECT, reused coverage | No: B4 frequency absent; STEP guide has no approved task mapping |
| FOOTWORK | 22 | 4 | `ex_33841b88`, DIRECT, reused coverage | No: B4 frequency absent; STEP→FOOTWORK is a visual alias, not prescription authority; time range is not encoded |
| JUMP_LANDING | 22 | 4 | No selected owner | No exact B5 task owner or guide mapping |
| LUNGE_REACH | 22 | 4 | `ex_421ba24b`, DIRECT, primary target | No: B4 frequency absent; DECELERATION guide is not mapped to LUNGE_REACH and `/side` is not represented |
| REACTION | 22 | 4 | `ex_33841b88`, DIRECT, reused coverage | No: B4 frequency absent; STEP guide is not mapped to REACTION and its range is not encoded |

## Exact selected-owner dossiers

### `persona3_recent` — `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION`

B5 selects it primarily for `TASK:ACCELERATION` and reuses the same identity for `TASK:DECELERATION`, `TASK:FOOTWORK`, and `TASK:REACTION`. The canonical objective catalog has a DIRECT relation for each of those four targets. The reviewed category is `STEP`, with the legacy guide “3 rounds × 10–20 sec”, rest 60 sec. There is no approved category-to-task prescription map; `BadmintonTransferColorPalette` aliases `STEP` to `FOOTWORK` for display color only.

There are zero exact-owner confirmed rows and zero direct task observations. B4 is `DIRECTION_ONLY` and has no weekly session or unit range. The B5 probe reports four existing seed sets, explicitly marked as not target authority.

Before C22C, the actual EXP rows were:

- week 1 and week 2: 4 sets × 15 seconds, rest 60; source `CANONICAL_PROGRAM_3_1_7`; text “15초 x 4 · 이동 후 균형 잡고 정지”.

These rows did not come from the STEP guide. They came from the canonical-program map populated by `PerformancePrescriptionResolver.fromCanonicalPrograms`, which precedes category guide fallback. B6 contains no task authorization object for this owner.

### `persona3_recent` — `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH`

B5 selects it for `TASK:LUNGE_REACH`; its canonical LUNGE_REACH relation is DIRECT. Its reviewed category is `DECELERATION`, whose legacy guide says “3 sets × 5 reps/side”, rest 75 sec. Category membership does not map DECELERATION to LUNGE_REACH, and the set model cannot losslessly represent the `/side` semantic. There are zero exact-owner confirmed rows and zero direct task observations. B4 is `DIRECTION_ONLY`, with no weekly frequency authority; the B5 probe's five sets are not dose authority.

Before C22C, the actual EXP rows were:

- week 1 and week 2: 5 sets × 18 seconds, rest 60; source `CANONICAL_PROGRAM_8_2_57`; text “18초 x 5 · 좌우 감속”.

These rows also came from the canonical-program seed map, not the DECELERATION guide. The `/좌우` text does not establish that the LUNGE_REACH task prescription guide applies to this exact owner/target.

### `persona3_mixed` and `persona3_reviewed`

Both cases have six B4 direction-only task targets, but B5 selected no task owner. Their B5 owners are quality owners (including Power in the input analysis); task targets are not materialized from those owners. C21's Power boundary remains intact. `persona3_mixed` routes to Strength Calibration under its independent Strength authority; no task B6 or task route is implied.

## Reviewed guide source and category mapping

`RecordBasedReviewedPolicy` lists exact stable-key category membership and supplies legacy guides. `PerformancePrescriptionResolver.resolve` uses, in order: recent same-owner execution, canonical-program seed prescription, then the reviewed category guide. This makes the guide a legacy resolver fallback; it does not itself establish canonical B4 dose, task frequency, exact B5 owner applicability, or task B6 authority.

The six task targets do not have an approved category-to-task prescription mapping in canonical task metadata or the reviewed policy. Category labels that happen to match a task name are not enough. `STEP`→`FOOTWORK` is only a UI color alias. `ex_421ba24b` being a DECELERATION-category member does not authorize its LUNGE_REACH B5 role.

The current scalar prescription model cannot preserve the reviewed guide forms:

- “5 reps/side” has no per-side field; storing `reps=5` would drop laterality, and converting it to 10 total would change semantics.
- “10–20 sec” and “8–12 reps” are ranges, while the materialized set model holds a scalar. The legacy guide resolver uses scalar placeholders; those are not lossless range authority.
- RPE is not present in these guides and is not invented by C22.
- Time-based guides have seconds and no task-specific frequency authority. `defaultSchedule()` describes available training weekdays, not required exercise sessions.

Consequently all reviewed guides in this policy are coaching/legacy generation guidance for C22 purposes, not executable canonical task authority. No exact personal task dose can supersede them in this corpus.

## C22C fallback boundary to implement

The production canonical path merges B5 `selectionPlan.materialDemand` into the builder. C21 already filters exact B5 Power owners without B6 before allocation, but it only receives the Power owner set. The two selected Task owners were therefore not filtered. `PersonalizedPrescriptionPlanner` dispatched the athletic performance rows to `PerformancePrescriptionResolver`, whose canonical seed lookup supplied numeric timing/set shapes. Thus B5 identity/probe metadata crossed into materialization without a task B6 grant.

C22C should add a fail-closed materialization boundary for exact B5 task-owner identities. With no task execution authority model, selected task owners should be deferred before allocation. The boundary must not convert `DIRECTION_ONLY` to numeric dose, affect unrelated owner roles, or change B5 selection. Exact repeated-history `PERFORMANCE_CONTINUITY` is a separate existing pathway; the selected C22 task owners have no exact-owner personal history, so it cannot reintroduce these rows in the current corpus.

## Routing, provenance, and safety

C22 adds no task B6 or B8 scope. Strength, Strength Calibration, Hypertrophy, Combined, and Power authority are unchanged. The task cases remain under existing routing requirements; no task work is routed through Strength or Combined. The normal order remains B1–B6 → one EXPERIMENTAL generation/materialization → C20 incumbent stability → CONTROL → comparison → B7 → B8 → B9. No third planner build is added.

The final report will record before/after task-role rows, route and B7 deltas, and confirm Power remains at zero numeric B6 authority and zero material rows. Any B7 count change must identify the exact removed owner/target rows; no B7 predicate will be modified.

## C22A decision

No badminton task is executable today with the complete repository-backed authority chain. C22A establishes that four seed-backed task rows lack exact task authority. C22C should remove those rows and leave all six B4 task targets without numeric dose/frequency authority. A later phase may add an exact task bridge only after it establishes an approved owner/category/task mapping, numeric frequency source, and lossless prescription representation. A task production route remains a separate future decision.
