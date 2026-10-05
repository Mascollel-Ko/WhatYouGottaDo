# C23 Task Protocol, Frequency, and Representation Audit

## Scope and result

C23 starts from the merged C22 main commit `392b6e6346ea08b9fc92f61ae99fd99b401793b2`. C22 had already removed the four legacy task rows without exact task B6 authority. This phase adds exact protocol-binding and weekly-frequency authority types, represents reviewed guide shapes losslessly, and evaluates task-authority completeness in shadow. It does not materialize task rows or change B7/B8 routing.

The C23 implementation/test commit is `79cf8614131cdecc50d7685da9f0faee2e9051da`; it is the protocol's `lastAuditedCommit`. The final documentation HEAD is recorded in the PR and completion report after the final docs-only update.

The corpus contains 132 task targets across 22 generated cases in the 27-case corpus; 24 targets remain direction-only. There are two exact B5 owner rows, five exact owner-role-task relationships after reused target coverage is accounted for, and two distinct owner-role identities. None of the 132 targets has a complete task authority. The result is 0 complete, 132 incomplete, 0 conflicting; no task material rows are produced.

The machine-readable, deterministically ordered census is [c23-task-protocol-frequency-representation-census.json](c23-task-protocol-frequency-representation-census.json). It includes all target rows, exact reviewed category membership, canonical task relations, guide shapes, frequency decisions, three real-case dossiers, C20 regression state, routes, B7 summary, and build accounting.

## Exact protocol mapping

The code now requires a binding keyed by the exact `stableKey`, `selectionRole`, canonical task, and reviewed category. A category label or direct relation alone cannot create a binding. A reviewed binding may carry `weeklySessions` only when the approved protocol explicitly owns that numeric frequency; the reviewed-frequency value object rejects any value that does not exactly match its binding. The repository has no approved exact task-protocol binding, so every task has `NO_APPROVED_PROTOCOL_BINDING`; no label mapping was inferred.

| Canonical task | Targets | Direction-only | Reviewed protocol result | Finding |
|---|---:|---:|---|---|
| ACCELERATION | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | The exact selected owner `ex_33841b88` has a DIRECT relation, but its reviewed category is STEP. The ACCELERATION category member `medicine_ball_three_step_acceleration_throw` has only SUPPORTIVE ACCELERATION transfer. |
| DECELERATION | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | Some DECELERATION category members have DIRECT canonical relations, but no project-owned rule binds those exact owner-role-task combinations to the guide. Reused coverage is not a protocol approval. |
| FOOTWORK | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | `ex_33841b88` has a DIRECT FOOTWORK relation but is reviewed as STEP. The repository contains no approved STEP-to-FOOTWORK binding. |
| JUMP_LANDING | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | No exact B5 owner/protocol binding is present in the audited selected-owner rows; some reviewed deceleration relations are only SUPPORTIVE. |
| LUNGE_REACH | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | `ex_421ba24b` has a DIRECT LUNGE_REACH relation and belongs to reviewed category DECELERATION, but no approved DECELERATION-to-LUNGE_REACH binding exists. |
| REACTION | 22 | 4 | NO_APPROVED_PROTOCOL_BINDING | Direct relations exist for reviewed REACTION members and for `ex_33841b88`, but exact task/category protocol bindings have not been approved. |

The reviewed policy currently contains 16 exact stableKey memberships:

| Reviewed category | Exact members | Guide shape |
|---|---|---|
| ACCELERATION | `medicine_ball_three_step_acceleration_throw` | 3 × 5 reps/side; 75 s rest |
| DECELERATION | `ex_314df428`, `ex_421ba24b`, `ex_bc84eb7f`, `lateral_bound_continuous`, `medicine_ball_three_step_deceleration_throw` | 3 × 5 reps/side; 75 s rest |
| REACTION | `ex_8e69fc74`, `ex_c5f4c242` | 3 rounds × 10–20 s; 60 s rest |
| STEP | `ex_33841b88` | 3 rounds × 10–20 s; 60 s rest |
| ANTI_ROTATION | `band_pallof_press`, `cable_pallof_press`, `ex_d5bdffe1`, `kettlebell_halo`, `landmine_anti_rotation`, `vipr_rotational_lift` | 3 × 8–12 reps; 60 s rest |
| ROTATION_GENERATION | `vipr_chop` | 3 × 8–12 reps; 60 s rest |

These are exact policy membership and legacy category guide facts. They do not establish canonical task protocol bindings. In particular, `STEP` is not treated as `FOOTWORK`, `DECELERATION` is not treated as `LUNGE_REACH`, and a category match is not enough to establish task authority. Transfer levels remain exactly those in canonical metadata; SUPPORTIVE is never promoted to DIRECT.

## Frequency authority

No reviewed guide states sessions per week, and no existing B4 task target carries numeric weekly frequency. Default available weekdays describe scheduling availability only. They are not used as frequency authority. The type boundary requires a reviewed frequency to be present on the exact protocol binding itself, so a within-session guide cannot be manually paired with an unrelated numeric sessions/week value.

The new personal-frequency resolver accepts completed, direct task observations only for the exact `(stableKey, selectionRole, task)`. It requires at least two eligible completed weeks and two direct-exposure weeks, and it fails with `CONFLICT` if weekly session counts differ or duplicate week evidence disagrees. This is a conservative, typed evidence rule; it cannot infer a numeric count from sets, reps, duration, court exposure, another owner, or another role. No real corpus owner has matching persisted direct task history, so personal authorities are 0; reviewed frequency authorities are also 0. All 132 target rows have no numeric frequency authority.

## Lossless within-session representation

`TaskPrescriptionShape` separates set/round count, prescription mode, scalar or bounded values, laterality, rest, load mode, activity kind, and optional RPE. It supports and JSON-round-trips:

- scalar repetitions and scalar duration;
- 5 repetitions per side, retaining `PER_SIDE` rather than converting it to ten total repetitions;
- duration ranges such as 10–20 seconds, without choosing a midpoint;
- repetition ranges such as 8–12, without choosing a midpoint.

Invalid mode/value combinations, nonpositive set counts or durations, reversed ranges, a PER_SIDE mode without PER_SIDE semantics, and simultaneous scalar/range values are rejected. The formatter is deterministic and does not parse display text back into authority. Null RPE means the source did not specify RPE. Existing guide shapes intentionally have no load mode or activity kind, so their execution context is incomplete; C23 does not invent either value.

The six canonical reviewed guide forms currently present in the policy (after category de-duplication) are: two reps-per-side categories, two duration-range categories, and two repetition-range categories. Scalar repetition and duration modes are supported by the typed model and synthetic round-trip tests, but no current reviewed category guide uses them. Across the exact reviewed member rows, the guide modes are six reps-per-side, three duration-range, and seven repetition-range instances.

The shape is shadow/intermediate data only in C23. There are no task B6 rows to save, so no Room, backup, restore, Community, or Cloud schema changes were made. Existing persisted program item/set fields are scalar (`reps`, `seconds`, `weightKg`) and cannot preserve task mode, per-side semantics, or ranges. A production C24 materialization must add or reuse a lossless persistence contract before saving task prescriptions; C23 does not claim these shapes already survive saved-program persistence.

## Shadow completeness and exact cases

`TASK_AUTHORITY_COMPLETE` requires an exact task target, exact owner-role, DIRECT canonical relation, exact approved protocol binding, numeric frequency authority, lossless prescription shape with explicit load/activity context, and no conflicting authority. Missing any requirement yields `TASK_AUTHORITY_INCOMPLETE`; conflicting bindings or frequency evidence yield `TASK_AUTHORITY_CONFLICT`. The resolver is shadow-only and is not connected to B6 materialization.

For the six tasks, each has 22 corpus targets and four direction-only targets. Every task has 0 complete, 22 incomplete, 0 conflict rows. Across all 132 targets, the aggregate is 0/132/0. There are 0 exact protocol bindings, 132 missing protocol bindings, 0 binding conflicts, 0 personal frequency authorities, 0 reviewed frequency authorities, and 132 missing-frequency outcomes.

### `ex_33841b88`

This exact stable key is reviewed in category STEP and the selected B5 role is `CANONICAL_STIMULUS_TASK_ACCELERATION`. Its canonical relations are DIRECT for ACCELERATION, DECELERATION, FOOTWORK, and REACTION in the real `persona3_recent` case. ACCELERATION is its primary selection; DECELERATION, FOOTWORK, and REACTION are reused coverage. Reuse does not create protocol authority. The STEP guide is 3 rounds × 10–20 seconds with 60 seconds rest. The range is represented losslessly, but there is no approved STEP-to-task binding, weekly-frequency authority, load mode, or activity-kind context. Exact personal direct task observations are 0 and the historical selection role is unavailable. Completeness is INCOMPLETE for each of the four task attributions.

### `ex_421ba24b`

This exact stable key is reviewed in category DECELERATION and has B5 role `CANONICAL_STIMULUS_TASK_LUNGE_REACH`. Its LUNGE_REACH relation is DIRECT in `persona3_recent`, where LUNGE_REACH is the primary selection. The DECELERATION guide shape is 3 × 5 reps/side with 75 seconds rest. Per-side semantics are now represented losslessly; they are not converted to total reps. Exact personal direct task observations are 0 and the historical selection role is unavailable. No approved DECELERATION-to-LUNGE_REACH binding, weekly-frequency authority, load mode, or activity-kind context exists, so task authority remains INCOMPLETE.

### Real corpus cases

- `persona3_recent` has six moderate-relevance DEVELOP task targets and two primary B5 owner rows. `ex_33841b88` is selected for ACCELERATION and reused for DECELERATION, FOOTWORK, and REACTION; `ex_421ba24b` is selected for LUNGE_REACH. JUMP_LANDING has no selected owner. All six B4 targets are direction-only and all six authorities remain incomplete. Its four pre-C22 task-shaped rows came from legacy fallback sources `CANONICAL_PROGRAM_8_2_57` and `CANONICAL_PROGRAM_3_1_7`, with 5 × 18 seconds and 4 × 15 seconds respectively. Those rows had no exact task B6 authority and remain removed: before 4, after 0. B7 is `NOT_ELIGIBLE` for unmet targets, unclosed provenance, collateral regression, and target regression; B8 remains `CONTROL_REQUIRED` (`B8_B7_NOT_ELIGIBLE`).
- `persona3_reviewed` has six moderate-relevance DEVELOP, direction-only task targets and no exact B5 task owner rows; all remain incomplete. B7 is eligible for future cutover review, but B8 remains `CONTROL_REQUIRED` for empty material authority, prescription change without exact B6, incomplete provenance, missing full B6 materialization, unrelated comparator mutation, and upstream inconsistency. Its route remains CONTROL.
- `persona3_mixed` has six moderate-relevance DEVELOP, direction-only task targets and no exact B5 task owner rows; task material remains 0. B7 is eligible and the existing `B8_STRENGTH_CALIBRATION_V1` route is authorized under its separate Strength authority. Task authority contributes no material or route authority.

## Safety and regression boundaries

- Task rows: 0 after C23; the four legacy fallback rows remain removed.
- Power numeric authority, executable Power B6, and Power material rows: 0 / 0 / 0. Task guide data cannot grant Power authority.
- C20 incumbent regression corpus: 32 rows; 12 HARD_VALID, 0 HARD_INVALID, 20 UNRESOLVED; 12 valid rows preserved, and 0 unresolved rows forced preserved. Combined anchor conflicts: 0.
- Routes are unchanged from C22: CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0.
- B7 is not modified. The current census records 11 provenance-unclosed, 9 unmet, 2 target-regressed including 1 collateral regression, matching the merged C22 census. Earlier phase notes that say one regression are stale relative to this verified C22 artifact.
- Build accounting remains one CONTROL plus one EXPERIMENTAL build, two total, zero third builds; preflight is zero.
- CONTROL output is not an input to protocol mapping, personal frequency, shape construction, or completeness. The census perturbation test verifies invariant task authorities and EXP output when comparator rows are changed.
- Final local validation passed: compile, focused C23/coverage/C20 tests, and full unit tests (2,296 tests, 0 failures, 0 errors, 4 skips) using the existing repository-external worker-restart init script. The standard production coverage artifact SHA-256 is `4467C1510A07BBE901042E112D68826CD054384C023FCB07DEB3859D043C3EF8`; the machine census SHA-256 is `B8C00593E877609D73CE930433FCD2DFDF53F07A747273D5DBB65C02C405C640`.

Protocol is 3.58.0 and runtime is `RECORD_BASED_PLANNER_0.15.0_KOTLIN_1`; app remains 0.5.1.5. The protocol/runtime contract was bumped because typed task protocol/frequency and prescription-shape contracts were added. No Room migration was made.

## C24 prerequisites

Before any task B6 or task row can become executable, the repository still needs an approved exact owner-role-task-reviewed-category protocol binding, numeric weekly task-frequency evidence, explicit load/activity semantics, and a persisted lossless representation for task mode/ranges/laterality. The current census has no task authority ready for B6. C24 must remain fail-closed until those exact inputs exist; it must not restore generic task fallback rows or Power prescriptions.
