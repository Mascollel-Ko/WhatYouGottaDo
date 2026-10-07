# C31 — Movement B6 authority reuse audit and proven reuse integration

## Result

We audited the 22 movement targets before connecting any prescription authority. None had an existing exact movement B6 prescription, a directly related Quality or approved Task physical row, an authorized alternative in its B5 candidate pool, a history-backed execution authority, or an applicable cold-start calibration path. The evidence therefore supports **0 legal reuse integrations and 22 genuine movement prescription-policy gaps**. No new dose was invented and production planning behavior did not change.

This is the outcome of the expanded C31 scope: implementation was permitted when an existing authority could be connected, but the audit did not prove such authority for these targets. Reusing CONTROL doses, treating a candidate as B6 authority, or ignoring selection-role identity would have crossed the approved authority boundary.

## Baseline and verification

- PR #23, C30, merged from head `ab53c7d5b7143ae7bd8f3fbad76df553b78cb469` at `2026-10-07T17:40:59Z`.
- Merge commit and C31 start: `caea0d2def0fd3317b02101cc154b306ed5dc23c`.
- Merged-main Hosted CI: [run 37660968295](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37660968295), success. Protocol validation, Community/Cloud contracts, whitespace, unit tests, production coverage upload, APK assembly, signer validation, and artifact upload all passed.
- Versions: Protocol `3.63.0`; Runtime `RECORD_BASED_PLANNER_0.15.5_KOTLIN_1`; App `0.5.1.5`; Room `38`.
- C31 audit/test helper: [`C31MovementB6AuthorityReuseCensus.kt`](../app/src/test/java/com/training/trackplanner/data/C31MovementB6AuthorityReuseCensus.kt).
- Machine-readable row-level result: [`c31-movement-b6-authority-reuse-census.json`](c31-movement-b6-authority-reuse-census.json).

The audit generated the same 22 sparse cases through the Room/service path. It examined 208 distinct candidate identities across the movement candidate pools (3–33 per target); the C30 selector metric of 1,106 is candidate evaluation rows and is a different measurement. Every candidate pool had zero candidates with an executable Quality B6 authority. No exact movement-owner B6, same-stableKey cross-role authority, authorized alternative, history-backed execution authority, cold-start calibration candidate, or multi-authority conflict was found.

## Per-target evidence

For each row below, B3 admitted the movement need and B4 created a direction-only target. B5 selected the listed canonical movement owner at rank 1. The candidate count is the full exact movement candidate pool audited for existing authority. Every selected owner ended in `NO_EXECUTABLE_MOVEMENT_AUTHORITY`; all rows are classified `GENUINE_NO_B6_POLICY`.

| Case | Movement target | B5 owner | Exact selection role | Candidates | Existing exact authority / direct reusable row |
|---|---|---|---|---:|---|
| persona0_sparse | CORE_DIRECT | `ex_28347c1f` | `CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT` | 8 | None |
| persona0_sparse | HORIZONTAL_PUSH | `ex_1dbee10e` | `CANONICAL_STIMULUS_MOVEMENT_HORIZONTAL_PUSH` | 9 | None |
| persona1_sparse | ARMS_BICEPS | `barbell_reverse_curl` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_BICEPS` | 11 | None |
| persona1_sparse | ARMS_TRICEPS | `dumbbell_lying_triceps_extension` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_TRICEPS` | 5 | None |
| persona1_sparse | CALVES | `ex_5ca7133f` | `CANONICAL_STIMULUS_MOVEMENT_CALVES` | 3 | None |
| persona1_sparse | CORE_DIRECT | `ex_28347c1f` | `CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT` | 9 | None |
| persona1_sparse | LOWER_KNEE | `dumbbell_goblet_squat` | `CANONICAL_STIMULUS_MOVEMENT_LOWER_KNEE` | 6 | None |
| persona1_sparse | POSTERIOR_CHAIN | `barbell_romanian_deadlift` | `CANONICAL_STIMULUS_MOVEMENT_POSTERIOR_CHAIN` | 4 | None |
| persona2_sparse | ARMS_BICEPS | `barbell_reverse_curl` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_BICEPS` | 17 | None |
| persona2_sparse | ARMS_TRICEPS | `cable_overhead_triceps_extension` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_TRICEPS` | 10 | None |
| persona2_sparse | CALVES | `ex_5c8751d2` | `CANONICAL_STIMULUS_MOVEMENT_CALVES` | 5 | None |
| persona2_sparse | CORE_DIRECT | `ex_28347c1f` | `CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT` | 10 | None |
| persona2_sparse | POSTERIOR_CHAIN | `barbell_deadlift` | `CANONICAL_STIMULUS_MOVEMENT_POSTERIOR_CHAIN` | 16 | None |
| persona2_sparse | UPPER_PULL | `cable_rear_delt_fly` | `CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL` | 33 | None |
| persona3_sparse | CORE_DIRECT | `ex_28347c1f` | `CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT` | 8 | None |
| persona3_sparse | HORIZONTAL_PUSH | `ex_1dbee10e` | `CANONICAL_STIMULUS_MOVEMENT_HORIZONTAL_PUSH` | 9 | None |
| persona4_sparse | ARMS_BICEPS | `barbell_reverse_curl` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_BICEPS` | 11 | None |
| persona4_sparse | ARMS_TRICEPS | `dumbbell_lying_triceps_extension` | `CANONICAL_STIMULUS_MOVEMENT_ARMS_TRICEPS` | 5 | None |
| persona4_sparse | CALVES | `ex_5ca7133f` | `CANONICAL_STIMULUS_MOVEMENT_CALVES` | 3 | None |
| persona4_sparse | CORE_DIRECT | `ex_28347c1f` | `CANONICAL_STIMULUS_MOVEMENT_CORE_DIRECT` | 9 | None |
| persona4_sparse | POSTERIOR_CHAIN | `barbell_romanian_deadlift` | `CANONICAL_STIMULUS_MOVEMENT_POSTERIOR_CHAIN` | 4 | None |
| persona4_sparse | UPPER_PULL | `dumbbell_chest_supported_row` | `CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL` | 13 | None |

The deterministic census embeds the complete target objects, including every candidate identity, the B4/B5 evidence, exact Quality and Task authority overlays, refusal codes, and the 44 C30 owner-week rows. The abbreviated table above is for readability; it does not replace the census.

## Authority reuse findings

| Classification | Targets |
|---|---:|
| Exact same movement-owner authority | 0 |
| Existing Quality prescription can directly satisfy movement | 0 |
| Existing approved Task prescription can directly satisfy movement | 0 |
| Same stable key, authority under another role | 0 |
| History-backed execution authority candidate | 0 |
| Cold-start user-calibration candidate | 0 |
| Multi-authority conflict | 0 |
| Authorized alternative in candidate pool | 0 |
| Genuine no-B6 policy | 22 |

The 22 target-context B6 probes for unrelated selected Quality owners produced refusal reasons: `B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE` 10 times, `HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE` 6 times, and `CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE` 6 times. These are diagnostic refusals on other Quality owners, not movement prescriptions or evidence of reusable movement authority; none materialized.

All three C24 badminton protocols were checked by exact protocol identity. Each is a `STRUCTURED_BADMINTON_DRILL` whose canonical movement coverage is `OTHER`; none is in these movement candidate pools or has a direct canonical movement relation. The eight approved Task rows elsewhere in the corpus therefore cannot be copied or treated as movement execution authority here.

An existing Quality prescription could satisfy a movement target without a second dose only if the same authorized physical row had a direct movement relation and fully matching semantics. No such row existed in these sparse cases. Supportive/incidental relations, shared exercise names, and stable-key matches across roles were not promoted to direct satisfaction. The selector regression test also proves that a different-role prescription under the same stable key does not authorize the selected movement owner.

## Before and after

| Measure | Before C31 audit | After C31 audit |
|---|---:|---:|
| B3 movement ADDRESS | 22 | 22 |
| B4 direction-only movement targets | 22 | 22 |
| B5 selected movement owners | 22 | 22 |
| Execution disposition / classification | `POLICY_UNSUPPORTED` 22 | `GENUINE_NO_B6_POLICY` 22 |
| Existing authorized material satisfying movement | 0 | 0 |
| Newly materialized movement-only physical rows | 0 | 0 |
| Duplicate physical rows | 0 | 0 |
| Unauthorized executable movement rows | 0 | 0 |
| Sparse legacy owner-week rows | 0 / 44 executable after C30 | 0 / 44 executable |

This is a more precise disposition of the same fail-closed result, not a route or prescription change. The 44 prior sparse owner-week identities remain absent as executable rows. No generic dose, default frequency, RPE, zero-load prescription, CONTROL-derived dose, or new calibration policy was introduced.

## C30 artifact hash reconciliation

The two APK digests previously reported for C30 map to two different successful Hosted CI runs and artifact uploads:

| Hosted run / commit | Artifact ID | ZIP SHA-256 | Extracted APK size | APK SHA-256 |
|---|---:|---|---:|---|
| [37614855371](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37614855371), implementation/test `1880d129478927c32b9dc04db91f6b82c2ec60de` | `11479324794` | `c1338cf1a9e0dd92a570ca3f0c4f2339e65101cae9fb609fcee8e2732fdfec5e` | 68,889,247 bytes | `0F53EF10D3752CC1D58258FE60FDFD48C3C2489FE94D0C2C1F95EB83C1299F56` |
| [37616108699](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37616108699), final docs head `ab53c7d5b7143ae7bd8f3fbad76df553b78cb469` | `11480307052` | `65e92ed33f3ec0a5f60b53a2f8cff6aac8ce37ee716067ee0588937d1e4184ea` | 68,889,247 bytes | `AF94E070D4A6FEAAFA51FB86637D25C4B70766A043610DD03A65A68C29B99059` |

Both artifacts are valid outputs from their respective commits/runs. They have equal APK size and distinct byte hashes; this is an artifact identity difference, not a planner behavior discrepancy. C31 will produce a fresh APK artifact for its final main commit.

## Performance and regressions

Three same-environment Room/service corpus runs took 4,662 ms, 4,716 ms, and 4,601 ms (median 4,662 ms; total range 115 ms). Across the 22 generated cases, mean time was 211.8 ms per case, median 165 ms, and maximum 571 ms. The diagnostic observed 1,106 B5 candidate evaluation rows, 123 movement B6 lookups, and 0 repeated identical lookups. This audit adds test-only inspection; the production planner path is unchanged.

The C30 regression corpus remains at B3 ADDRESS 22, B4 targets 22, selected B5 owners 22, and zero movement rows. Power material remains 0; Jump/Landing approved protocols and material remain 0; C24 Task rows remain 8; third planner builds remain 0. C31 changes no B3/B4/B5/B6, routing, B7, or B8 production behavior and does not change versions or persistence schemas.

For that 22-generated-case C30 corpus, routes remain CONTROL 22; B7 reasons remain provenance-unclosed 15, collateral regression 1, and target-regressed 1; B8 reports CONTROL_REQUIRED 15. C20 replay remains 10 HARD_VALID, 0 HARD_INVALID, 0 UNRESOLVED, and 22 NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER, with no forced invalid/unresolved incumbent. Build accounting remains 22 CONTROL + 22 EXPERIMENTAL = 44 total and 0 third builds. These are the sparse audit corpus counts, not the broader 19/1/2 production route census from other generated cases.

The focused audit/selector tests passed. Local full `:app:testDebugUnitTest` passed 2,339 tests, 0 failures, 0 errors, and 4 skips in 15m06s using the repository-external JDK 17 and `forkEvery=75` worker-restart setup. The initial JBR 21 attempt hit the known Windows Robolectric native SQLite access violation in `robolectric-nativeruntime.dll`; it had no assertion failure and its generated crash log was removed. The temporary daemon JVM selection was restored exactly and is not part of the repository changes. Test/instrumentation commit `4f4006915c8fefcb4162c7e684767f99df02e360` is recorded as the protocol's `lastAuditedCommit`.

## Decision

No existing authority can lawfully be connected to these 22 movement targets. They need a future, explicit movement prescription policy if product requirements call for executable movement-only work. Until such policy is approved and typed, the correct result is `GENUINE_NO_B6_POLICY`, with the admitted semantic need preserved but no executable row. C31 therefore does not add a B6 reuse binding, alter candidate ranking, change routes, or generate a new dose.
