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

## C22C exact-owner fallback boundary

The production canonical path merges B5 `selectionPlan.materialDemand` into the builder. C21 already filters exact B5 Power owners without B6 before allocation, but it only received the Power owner set. The two selected Task owners were therefore not filtered. `PersonalizedPrescriptionPlanner` dispatched the athletic performance rows to `PerformancePrescriptionResolver`, whose canonical seed lookup supplied numeric timing/set shapes. Thus B5 identity/probe metadata crossed into materialization without a task B6 grant.

C22C passes exact selected `(stableKey, selectionRole)` task-owner identities from canonical B5 into the planner and defers only their material candidates before allocation when no task B6 authority exists. It records `B5_SELECTED_TASK_OWNER_NO_EXECUTABLE_TASK_B6_AUTHORITY`. A same-key/different-role owner, non-material candidate, and unselected task owner are retained. This changes neither B4 target meaning nor B5 selection. No task execution authority model exists, and the two selected owners have no exact-owner personal history, so the existing exact-owner `PERFORMANCE_CONTINUITY` path cannot reintroduce these rows in this corpus.

The exact four-row removal is:

| Owner | Weeks | Before material | Before source | After |
|---|---|---|---|---|
| `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION` | 1, 2 | 4 × 15 sec, rest 60 sec | `CANONICAL_PROGRAM_3_1_7` | no row |
| `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH` | 1, 2 | 5 × 18 sec, rest 60 sec | `CANONICAL_PROGRAM_8_2_57` | no row |

These are the only pre-C22C task-role material rows in the 22 generated corpus cases. All four are deferred. No unauthorized task-shaped row remains in EXP. The `0` values in their old `weightKg` fields were legacy non-weighted placeholders; they were not elevated into a typed task load authority.

## Three badminton-rich case results

| Case | Task targets / B4 | Exact B5 task owners | EXP task rows after | B7/B8 and route |
|---|---|---|---:|---|
| `persona3_recent` | six task targets; task targets remain direction-only, with no numeric units or weekly frequency | `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION` selected for Acceleration and reused for Deceleration, Footwork, Reaction; `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH` selected for Lunge Reach. Five owner/task relations are DIRECT; reuse does not create a separate dose authority. | 0 | The five previously fallback-backed task outcomes (Acceleration, Deceleration, Footwork, Lunge Reach, Reaction) become `REGRESSED`; Jump/Landing has no selected owner or material row. B8 remains `CONTROL_REQUIRED`; route remains CONTROL. This reports loss of unsupported material rather than hiding it. |
| `persona3_reviewed` | six task needs are DEVELOP; B4 remains `DIRECTION_ONLY` without numeric frequency | no exact B5 task owner selected | 0 | task targets remain unmet/unchanged; Power remains without numeric B6 or material rows. No task cutover exists; route remains CONTROL. |
| `persona3_mixed` | six task needs are DEVELOP; B4 remains `DIRECTION_ONLY` without numeric frequency | no exact B5 task owner selected | 0 | task targets remain unmet/unchanged; Power remains without numeric B6 or material rows. No task cutover exists; route remains CONTROL. |

The selected-owner matrix is therefore narrow: one exact owner has DIRECT relations to four task targets, and another has one DIRECT relation to Lunge Reach. The first owner is selected only for Acceleration; the other three relations are reused coverage. Neither owner has exact personal task dose evidence, an approved reviewed category-to-task mapping, numeric B4 frequency, or a task B6. No prescription is authorized for either primary or reused targets.

## Routing, provenance, and safety

C22 adds no task B6 or B8 scope. Strength, Strength Calibration, Hypertrophy, Combined, and Power authority are unchanged. The task cases remain under existing routing requirements; no task work is routed through Strength or Combined. The normal order remains B1–B6 → one EXPERIMENTAL generation/materialization → C20 incumbent stability → CONTROL → comparison → B7 → B8 → B9. No third planner build is added.

The final corpus keeps the route snapshot at CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0. C22 does not modify B7 or B8. B7 reason counts move from the C21 baseline 11 provenance-unclosed / 9 target-unmet / 1 target-regressed to 11 / 9 / 2, with one collateral-target-regression occurrence. The additional regressed case is `persona3_recent`: its five task outcomes now correctly expose that the former fallback rows were carrying the target. The pre-existing `persona1_reviewed` Hypertrophy regression remains. No gate is relaxed to mask either result.

Power remains at 0 numeric authority, 0 executable Power B6, and 0 material Power rows. C20 incumbent checks remain 12 HARD_VALID / 0 HARD_INVALID / 20 UNRESOLVED, with all 12 valid rows preserved and 0 invalid or unresolved rows forced to remain. Build accounting is 22 generated cases at CONTROL 1 + EXPERIMENTAL 1 = 2 builds, THIRD 0; preflight cases use 0 builds.

Protocol/runtime/app advance to `3.57.0` / `RECORD_BASED_PLANNER_0.14.9_KOTLIN_1` / `0.5.1.5` because removing executable-looking material rows is a production planner behavior change. No Room migration or task prescription schema was added. Standard coverage changes from C21's `5BD1E9430352618C6C42F399ED28B8A065908CEDCA8F4448924BD9301CB44BD1` to `4467C1510A07BBE901042E112D68826CD054384C023FCB07DEB3859D043C3EF8`, exactly reflecting the removed unsupported task rows.

## C22 verification and disposition

- C22 start: `3af7c7c7d96923b6218f4466506a278c3ac76f7f`.
- Audit commit: `380f586533b80d198ad49a0d56c55af83c7716a0` (C22A Hosted CI green, run `37254536552`).
- Implementation commit: `c942ec0166225ed15aac159f0e574c0f1a60e510` (Hosted CI run `37258993580` green: 2,283 tests, 0 failures, 0 errors, 4 skips; protocol, Community/Cloud contracts, whitespace, coverage upload, APK assembly, signer, and APK upload all succeeded).
- CONTROL task-row perturbation test commit: `ba63aba9c7c5a26c4850d7036a4d97baca20de1d` (focused local test passed; Hosted CI run `37260023637` green: 2,284 tests, 0 failures, 0 errors, 4 skips).
- `lastAuditedCommit`: `ba63aba9c7c5a26c4850d7036a4d97baca20de1d`.
- The machine-readable final census is [`c22-badminton-task-authority-census.json`](c22-badminton-task-authority-census.json), SHA-256 `2EF46E4A53D905108D3F9CEBACB78997A043A13DA2A6BE7F6B786EE68EE5B466`.
- The final Hosted run passed protocol validation, Community/Cloud contracts, whitespace, full tests, coverage upload, APK assembly, signer validation, and APK upload. Artifacts: `Stimulus-production-coverage` (815,703 bytes) and `WhatYouGottaDo-debug-apk` (65,169,454 bytes zipped; contains `app-debug.apk`, 68,757,703 bytes, SHA-256 `669CCC4832E0BC0B9AFDBFD1B3208AB3A138456DDC2059E875AD714CB7D955B0`).
- Local compile and focused tests passed. Full local unit tests at the implementation commit passed: 2,283 tests, 0 failures, 0 errors, 4 skips. The added single CONTROL-task perturbation integration test also passed locally; the final Hosted suite passed 2,284 tests, 0 failures, 0 errors, 4 skips.
- C22 implementation adds no task B4 numeric authority, task B6, task route, reviewed-guide mapping, generic prescription, B8 predicate, or Power authority. Ordering and two-build accounting are unchanged.

## C22 decision

No badminton task is executable today with the complete repository-backed authority chain. The four seed-backed rows without exact task authority have been removed. All six task families remain without numeric dose/frequency authority. A later phase may add an exact task bridge only after it establishes an approved owner/category/task mapping, numeric frequency source, and lossless prescription representation. A task production route remains a separate future decision.
