# C26 — B6-to-EXP Materialization and B7 Provenance Consistency

## Result

The 22 Quality rows rejected by B6 were real scheduled `ProgramSkeletonItem`s in the C25 EXP skeleton. They were not merely audit candidates. A generic fallback in canonical B5 demand handling allowed those exact selected Quality owners to reach normal allocation and frequency expansion after B6 had declined executable authority. C26 now removes a denied exact owner before allocation and prevents frequency expansion from supplying a generic prescription to it.

The count of 22 B6-rejected owner-weeks and the count of 22 `UNEXPLAINED_ADDED_IDENTITY` attributions were coincidental: their exact stableKey/role identity intersection is **zero**. The former were unauthorized Quality material; the latter are unrelated sparse-case provenance records.

C26 also closes the two `persona3_recent` badminton role replacements through their existing exact C24 Task B6. The seven remaining Quality replacements stay unclosed because their replacement owners still lack executable Quality B6.

## Baseline and implementation

- C25 started from main `89b454db30444a313afd58f147b6e9378594f27c`.
- PR #18 audit was merged at `6a3f38320d2ded8c7220a9ea72ec613594017868` on 2026-10-06; merged-main Hosted CI run `37433395504` succeeded.
- C26 implementation/test SHA: `b5d4550c4f4f44b52d89128fa9e70d16cce77a9b` (includes the coverage-test memory fix).
- Protocol `3.61.0`; runtime `RECORD_BASED_PLANNER_0.15.3_KOTLIN_1`; app `0.5.1.5`; Room 38.
- No Room, backup, restore, Community, or Cloud schema change.
- The merged C25 main CI run [37433395504](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37433395504) passed protocol validation, Community/Cloud contracts, whitespace, full unit tests, coverage upload, APK assembly, signer validation, and artifact upload.

## Root cause and correction

B5 identifies an owner; it does not grant a prescription. Before C26, the canonical Quality demand path filtered exact Power B5 owners without B6, but did not apply that boundary to all selected Quality owners. An owner with `NO_EXECUTABLE_AUTHORITY` could therefore remain in material demand, fall through `NoExactAuthority` to the generic prescription planner, and be scheduled. Frequency expansion had a separate generic prescription path that could reintroduce such an owner after the initial check.

C26 adds the exact B5-selected Quality owner set to the authorization provider and applies the existing fail-closed B6 demand filter before budgeting and allocation. The filter retains exact executable authority, incumbent preservation, and the existing explicit conflict path. Frequency expansion now also rejects a selected Quality owner unless it has executable exact authority or a valid incumbent-preservation disposition. The existing C21 Power owner source is unioned with the Quality owner set, so this repair does not reopen Power.

B7 also checks the actual CONTROL/EXPERIMENTAL rows for every denied Quality owner-week. Adding the row or changing its prescription raises `B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED`; an unchanged compatible prescription can remain, and placement/order movement alone is not misclassified as a new prescription. This is an integrity alarm, not an authorization path.

The 22 former rows were all executable schedule items and material deltas, with generated prescriptions including `CANONICAL_POSTERIOR_HOLD` prescriptions and provisional no-load shapes. After C26, none exists as an EXP executable row and none is an EXP added-owner/prescription delta. Exact row-by-row evidence is in [the machine-readable census](c26-b6-exp-b7-consistency-census.json).

| B6 refusal reason | Owner-weeks | Earlier generated shape | C26 result |
|---|---:|---|---|
| `CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE` | 6 | 3 sets × 8 reps, 40 kg, `CANONICAL_POSTERIOR_HOLD` | No EXP row |
| `B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE` | 10 | 2 sets × 8 reps, provisional no-load source | No EXP row |
| `HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE` | 4 | 2 sets × 8 reps or 3 × 5 reps, provisional/hold source | No EXP row |
| `PLANNED_RESISTANCE_LOAD_UNAVAILABLE` | 2 | 2 × 8 reps, provisional no-load source | No EXP row |
| **Total** | **22** | All were persisted/scheduled before the fix | **0 executable rows after** |

The 22 exact rows are two owner-weeks each for: `persona0_recent` squat; `persona0_sparse` `ex_32eb8457`; `persona1_mixed` bench press; `persona1_recent` rear-delt fly; `persona1_sparse` bench press; `persona2_recent` bench press; `persona2_sparse` squat; `persona3_recent` squat; `persona3_sparse` `ex_32eb8457`; `persona4_recent` `ex_32eb8457`; and `persona4_sparse` bench press. Each week and exact role is individually enumerated in the census.

## `persona3_recent` Task replacement

Before C26, B11 identified each old badminton objective role as `CANONICAL_REPLACEMENT`, but the B7/C10 replacement consumer required Quality B6 evidence and therefore treated both removals as unexplained. C26 adds a separate exact Task proof path. It requires the same stableKey, exact replacement role, exact approved protocol and task target, `DIRECT` relation for every authorized task attribution, `USER_APPROVED_PROJECT_POLICY`, lossless fully materialized rows, and satisfied protocol frequency/placement. Partial target attribution and wrong protocol/role/semantics are rejected.

| Removed role | Exact replacement | Provenance after C26 |
|---|---|---|
| `ex_33841b88#BADMINTON_OBJECTIVE_` | `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION`, `BADMINTON_SIX_CORNER_FOOTWORK_V1` | Closed for `TASK:ACCELERATION`; exact B6 and approved-policy evidence present |
| `ex_421ba24b#BADMINTON_OBJECTIVE_` | `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH`, `BADMINTON_LATERAL_SHUTTLE_LUNGE_V1` | Closed for `TASK:LUNGE_REACH`; exact B6 and approved-policy evidence present |

This closes those two attribution rows only. `persona3_recent` still has an unexplained removal of `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`; its new Quality squat is denied B6 (`CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`) and no longer appears in EXP. The case therefore remains B7-ineligible and B8 returns `B8_B7_NOT_ELIGIBLE`; B9 selects CONTROL. Its unmet outcomes remain `QUALITY:POWER`, `QUALITY:STRENGTH`, and `TASK:JUMP_LANDING`. Power is direction-only with no material row; JUMP_LANDING is unmet but has no approved protocol or material row. A mixed scope would not repair the open removal provenance or the B7 gate.

## Quality replacements remain fail-closed

Seven non-Task Quality owner removals remain unexplained. B11's `CANONICAL_REPLACEMENT` label is selection evidence, not prescription authority. Since the exact replacement owners do not have executable Quality B6, C26 does not attach replacement provenance to them. Tests keep this distinction explicit.

The separate `TARGET_REGRESSED` case remains `persona1_reviewed`, `QUALITY:HYPERTROPHY`: CONTROL has 12 direct units and EXP has 15 against the configured preferred/max target of 6. B7 continues to reject it; C26 does not alter target math or the regression gate.

## Corpus before and after

| Metric | C25 before | C26 after |
|---|---:|---:|
| Routes: CONTROL / Strength V1 / Strength Calibration / Hypertrophy / Combined | 19 / 1 / 2 / 0 / 0 | 19 / 1 / 2 / 0 / 0 |
| B7 cases: provenance unclosed | 11 | 11 |
| B7 cases: affected target remains unmet | 9 | 0 |
| B7 cases: target regressed | 1 | 1 |
| B7 collateral regression | 0 | 0 |
| Unexplained added identity attributions | 22 | 22 |
| Unexplained removed identity attributions | 9 | 7 |
| Unexplained prescription changes | 1 | 1 |
| B6-denied Quality owner-weeks in EXP | 22 | 0 |
| C20 incumbent feasibility | 12 valid / 0 invalid / 20 unresolved | unchanged |
| Power material / JUMP_LANDING material | 0 / 0 | 0 / 0 |
| Build count per generation | CONTROL 1 / EXP 1 / total 2 / third 0 | unchanged |

The B7 affected-unmet count drops because the formerly unauthorized additions no longer exist as material changes that claim those targets. It does **not** claim that every personal target became satisfied; `persona3_recent` demonstrates the distinction. The 22 unexplained-added attributions remain and are the disjoint sparse-case set, not the 22 removed Quality rows.

## Verification and remaining blockers

Tests cover all four B6 refusal families, pre-allocation deferral, frequency-expansion re-entry, preservation of authorized and compatible material, exact positive Task replacement attribution, and negative Task replacement cases (wrong owner/role/protocol/provenance/transfer, missing B6, partial attribution, mismatched persisted prescription, and frequency/placement mismatch). Quality replacement without executable B6 remains unclosed. The corpus replay verifies the C24/C25 routes, Task rows, Power/JUMP boundaries, C20 incumbent counts, and two-build accounting.

Local compile (`compileDebugKotlin`, `compileDebugUnitTestKotlin`), the exact 27-case C26 coverage test, and full `testDebugUnitTest` passed after the memory fix. The full suite completed 2,313 tests with 0 failures, 0 errors, and 4 skips using the existing external JDK 17 / `forkEvery=75` worker-restart init script. A preceding JBR run stopped after 5m36s in `robolectric-nativeruntime.dll+0x5c22` with a native access violation, without an application assertion failure; the restarted-worker run completed. Protocol documentation validation passed (9 families / 36 protocols), Community/Cloud contracts passed 9/9, and `git diff --check` passed. Standard production coverage report SHA-256: `76F261F28C61E37668A204C8FBDFC107AE4B6233F2943BC942DC9CDE94FC5A5B`.

Implementation/test commit `b5d4550c4f4f44b52d89128fa9e70d16cce77a9b` Hosted run [37453833659](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37453833659) passed 2,313 tests (0 failures, 0 errors, 4 skips), coverage upload, APK assembly, signer validation, and upload. The earlier run on the implementation commit before the test-memory refactor failed only because this large coverage test exhausted the hosted test-worker heap; moving the frozen-baseline parse into a separate helper frame and removing a duplicate full-census render resolved that CI failure. Hosted coverage SHA matches the local standard report above. The uploaded APK is 68,839,623 bytes with SHA-256 `71D744D622DE0C3D9A394CAD295BD0BBCA8FB07FDE5306CAB80D0720463AE590`.

The census file SHA-256 is `D09EB7D7A77D8212371236792E736B1F17CED61308A164B394EADB0C786B6A07`.

The remaining product blockers are not a lack of a Mixed Strength+Task scope alone: (1) seven Quality replacements lack executable Quality B6, (2) sparse-case unexplained added identities remain, and (3) `persona3_recent` still has an unexplained legacy squat removal plus unmet Strength/Power/JUMP_LANDING targets. No new training policy or route is justified by this phase. The next bounded work should first identify whether the seven Quality replacements and sparse additions have existing exact authority that is being lost, or whether they correctly remain unavailable; do not presume a mixed route will resolve them.

The implementation commit, final Hosted CI, coverage artifact, census hash, and APK artifact identity are recorded in the completion report and CI artifacts.
