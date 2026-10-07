# C30 — Movement need disposition and canonical target admission

C30 connects existing movement-gap evidence to the canonical B3/B4 planning flow. Before this change, the planner could observe a movement-coverage gap and suggest an exercise candidate, but no canonical block decision admitted that need as a target. B5 and B6 therefore did not receive an authorized target to resolve. C30 makes B3 explicitly decide what the current block should do with each movement need, and creates a B4 target only for an `ADDRESS` decision.

## Decision and authority boundaries

B1 reuses the movement representations already computed in `AthletePlanningState` and the existing typed `ExposureRepresentationPolicy` / adaptation-gap analysis. C30 does not rebuild movement history or infer a disposition from zero exposure alone. B3 consumes the existing movement gap priority, transition-pressure evidence, current block context, and explicit restriction/recovery state. A supported actionable gap with pressure is `ADDRESS`; a low-priority gap without sufficient current pressure or an onramp/recovery context is a typed `DEFER`; a hard movement restriction is `EXCLUDE`; inconsistent or unknown movement evidence remains `UNRESOLVED` with evidence and reason codes.

`ADDRESS` authorizes target admission, not exercise dose. B4 emits a deterministic `MOVEMENT:<coverage>` target with `DIRECTION_ONLY` numeric authority. It contains no sets, reps, load, RPE, or frequency. B5 then runs the existing canonical selector against the admitted target and direct movement metadata. The prior MaterialDemandResolver candidate is not promoted to an owner by virtue of having been suggested. B6 remains the only source of executable prescription authority. C30 only recognizes an exact already-authorized row for the same owner and role, where an existing Quality or approved Task B6 row directly supplies the movement coverage without adding dose. No such reuse occurred in the sparse corpus.

When no exact B6 exists, B3 remains resolved as `ADDRESS`, while execution receives the separate typed `POLICY_UNSUPPORTED` disposition (`NO_EXACT_MOVEMENT_PRESCRIPTION_AUTHORITY`, `NO_APPROVED_MOVEMENT_DOSE_POLICY`, and `MOVEMENT_TARGET_HAS_NO_NUMERIC_DOSE_AUTHORITY`). No generic fallback is invoked and no executable movement row is created. Thus target disposition and execution disposition are not conflated.

The movement decision is part of the existing B3 portfolio and B4 target plan. C30 adds no parallel MovementGate or CoverageGate. B5/B6 run only downstream of B4 admission. MaterialDemandResolver remains candidate/coverage evidence; it cannot admit a target or grant prescription authority.

## C29 sparse identities

The production fixture contains 27 total cases: 22 generated and 5 rejected during preflight. The 22 prior sparse identities are block-level movement needs; the census records their original sparse stableKey/role and all contributing gap codes. A B3/B4 decision is made once per identity for the block. Their two historical owner-weeks are separately expanded in `ownerWeekRows` (44 rows total), so the block-level target is not mistaken for a weekly dose.

| Case | Prior sparse coverage identities | B3 | B4 | B5 | B6 / execution |
|---|---|---:|---:|---:|---|
| `persona0_sparse` | `CORE_DIRECT` (`ex_28347c1f`); `HORIZONTAL_PUSH` (`ex_1dbee10e`) | 2 ADDRESS | 2 direction-only targets | 2 canonical owners | 2 policy unsupported |
| `persona1_sparse` | `ARMS_BICEPS` (`barbell_reverse_curl`); `ARMS_TRICEPS` (`dumbbell_lying_triceps_extension`); `CALVES` (`ex_5ca7133f`); `CORE_DIRECT` (`ex_28347c1f`); `LOWER_KNEE` (`dumbbell_goblet_squat`); `POSTERIOR_CHAIN` (`barbell_romanian_deadlift`) | 6 ADDRESS | 6 direction-only targets | 6 canonical owners | 6 policy unsupported |
| `persona2_sparse` | `ARMS_BICEPS` (`barbell_reverse_curl`); `ARMS_TRICEPS` (`cable_overhead_triceps_extension`); `CALVES` (`ex_5c8751d2`); `CORE_DIRECT` (`ex_28347c1f`); `POSTERIOR_CHAIN` (`barbell_deadlift`); `UPPER_PULL` (`cable_rear_delt_fly`) | 6 ADDRESS | 6 direction-only targets | 6 canonical owners | 6 policy unsupported |
| `persona3_sparse` | `CORE_DIRECT` (`ex_28347c1f`); `HORIZONTAL_PUSH` (`ex_1dbee10e`) | 2 ADDRESS | 2 direction-only targets | 2 canonical owners | 2 policy unsupported |
| `persona4_sparse` | `ARMS_BICEPS` (`barbell_reverse_curl`); `ARMS_TRICEPS` (`dumbbell_lying_triceps_extension`); `CALVES` (`ex_5ca7133f`); `CORE_DIRECT` (`ex_28347c1f`); `POSTERIOR_CHAIN` (`barbell_romanian_deadlift`); `UPPER_PULL` (`dumbbell_chest_supported_row`) | 6 ADDRESS | 6 direction-only targets | 6 canonical owners | 6 policy unsupported |

Each row has current and prior 28-day exposure at zero, but that fact alone does not cause `ADDRESS`: B3 also requires the typed actionable gap/priority and transition-pressure context. The regression test includes a zero-exposure case without actionable pressure and confirms that it does not force target admission. The census carries exact B3 reason/evidence codes, deterministic target identity, exact B5 stableKey/role, B6 statuses and reason codes, and the original identity's two owner-week results.

## Corpus result

| Measure | C30 result |
|---|---:|
| Movement needs / prior identities | 22 |
| B3 ADDRESS / DEFER / EXCLUDE / TRUE_UNRESOLVED | 22 / 0 / 0 / 0 |
| B4 movement targets | 22, all `DIRECTION_ONLY` |
| B5 canonical movement owners | 22 |
| Exact existing B6 authority reused | 0 |
| Execution `POLICY_UNSUPPORTED` | 22 |
| Materialized movement rows | 0 |
| Unauthorized old owner-week rows | 0 of 44 |
| Power material / approved JUMP_LANDING protocol / JUMP_LANDING material | 0 / 0 / 0 |
| Approved C24 task rows retained | 8 |

No movement need disappeared: each now has a canonical target disposition. None became executable because there is still no approved movement dose authority for these exact B5 owners. B7/B8 predicates were not changed. All 22 generated corpus cases remain on CONTROL; the observed B7 reason cases are provenance unclosed 15, collateral regression 1, and target regression 1. The unclosed removal attribution count is 73, now separated by the audit suite into 70 movement target/owner evidence attributions and 3 pre-existing Quality attributions. That evidence is not prescription authority and is not used to relax B7.

Build accounting remains CONTROL 22 / EXPERIMENTAL 22 / TOTAL 44 / THIRD 0 across the 22 generated cases. No hybrid skeleton is constructed. The live C20 replay reports 10 `HARD_VALID`, 0 `HARD_INVALID`, 0 `UNRESOLVED`, and 22 `NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER`; zero invalid or unresolved incumbents were forced to remain. This is the current corpus's evaluated-status split, not the older historical 12/0/20 census.

## Performance and verification

C29's measured 22-case generation time was approximately 3,584 ms total (162.9 ms mean, 144.5 ms median, 369 ms max). C30 observations were 3,340 ms in a focused JBR run, 5,130 ms in the JDK 17 per-class worker run, and 6,651 ms in the final full-suite census (302.3 ms mean, 262 ms median, 770 ms max). The final sample is 3,067 ms higher than C29 (about 139 ms per generated case), so it signals a possible material latency increase; because the measurements ran under different JVM/worker load, they do not isolate the movement-selector cost. The census preserves the final full-suite measurement, and a production-device comparison is still needed before calling the increase stable. These are test-corpus observations, not a latency guarantee. The added B3 pass made 22 movement evaluations and 22 B4 target builds. B5 examined 1,106 candidate rows; B6 made 123 movement-authority lookups, with 0 repeated identical lookups. Candidate catalog scans remain downstream in B5, keeping movement disposition bounded by the finite gap set rather than nesting a catalog scan in B3.

Focused B3/B4/B5/B6, production-coverage, determinism, zero-exposure, exact-authority-reuse, and existing boundary tests passed. Full local and Hosted CI results, artifact identities, and final hashes are recorded in the PR closeout. No Room, backup, restore, Community, Cloud, or app-version changes were made. Protocol advances to `3.63.0`, runtime to `RECORD_BASED_PLANNER_0.15.5_KOTLIN_1`; app remains `0.5.1.5`, Room remains 38.

## Remaining blocker

The need/disposition integration gap is closed for these sparse cases. The remaining execution blocker is an exact movement prescription-authority gap: B4 intentionally has direction-only authority and the selected movement owners have no exact B6 dose source. A future step should first assess whether an existing, explicitly approved dose authority can govern any exact movement owner. It must not infer sets/reps/load/RPE/frequency from zero history, coverage labels, candidate selection, CONTROL rows, or B3 admission.

Machine-readable identity and owner-week evidence: [`c30-movement-disposition-target-admission-census.json`](c30-movement-disposition-target-admission-census.json).
