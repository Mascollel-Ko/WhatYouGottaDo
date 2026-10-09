# C35 — Regional Hypertrophy semantics and global capacity priority

C35 separates regional Hypertrophy dose from the aggregate historical Quality H reference and makes the existing finite allocator consume material demand in canonical target-priority order. The B7/B8 guards remain intact.

## Baseline and method

C35 starts from `fe7eed90f5a53fb656406507152c32782aa9e676`, Protocol `3.67.0`, Runtime `RECORD_BASED_PLANNER_0.15.9_KOTLIN_1`, App `0.5.1.5`, Room `38`. The corpus is the same 22 generated cases used by C33/C34.

The B2 `QUALITY:HYPERTROPHY` ledger aggregates direct realized/prescribed H exposure across the whole quality. B4 regional movement doses instead represent distinct region-equivalent residuals such as horizontal push, posterior chain, arms and calves. Before C35, B7's unchanged `numericOutcome` compared raw aggregate CONTROL and EXP units to the aggregate Quality H band. This made regional B4 additions appear to exceed a whole-quality historical reference, even when each regional target was exactly funded and materialized.

C35 leaves the raw aggregate visible and excludes from that aggregate comparison only a newly added physical owner whose evidence proves all of the following: exact B4 movement target and positive regional residual, exact B5 primary movement owner, executable B6 Hypertrophy authorization with the exact residual set count, typed movement B6 authorization, and non-failing target-specific materialization. Existing compatible exposure is not subtracted as a new addition. A physical owner is counted once across regional observations. Non-regional Quality H additions remain part of the aggregate comparison. The movement target's own B7 overrun check is unchanged; an actual regional overrun still produces `TARGET_REGRESSED`.

## The two C34 regression cases

| Case | Aggregate H B4 band | CONTROL raw units / distance | EXP raw units | Exact new regional units excluded | Comparable EXP units / distance | C35 outcome |
|---|---:|---:|---:|---:|---:|---|
| `persona1_mixed` | 3–9 (preferred 3) | 12 / 3 | 48 | 48 | 0 / 3 | `UNCHANGED`; no collateral regression |
| `persona1_reviewed` | 0–6 (preferred 6) | 12 / 6 | 48 | 45 | 3 / 0 | `IMPROVED` |

The previous EXP distances of 39 and 42 came only from comparing regional material with the aggregate band. In `persona1_reviewed`, the remaining 3 aggregate units are the existing/quality-owned portion; the 45 newly authorized regional units are separately covered by their regional B4 residuals. Neither case has a regional overrun, duplicate credit, or unauthorized material. The corrected classification does not close unrelated owner-removal provenance, so both remain CONTROL.

## Finite-capacity priority

`FiniteExecutionAllocator` remains the shared pure kernel and is unchanged. Immediately before allocation, its material candidates are now ordered globally by `PlannedExercise.priority` descending. Equal priorities retain the prior resistance preference, then deterministic `(stableKey, role, styleVariant)` ordering. Existing continuity and core reservation calculations are unchanged; the kernel receives the same candidate demand, only in canonical priority order.

Across the real corpus, 87 material owners competed. The reconstructed pre-C35 ordering and the new ordering both had zero priority inversions for this corpus, and no lower-priority candidate received capacity while a higher-priority candidate was capacity-rejected. A synthetic mixed-domain probe demonstrates the defect the corpus did not happen to exercise: the old resistance-first ordering put a priority-50 resistance owner before a priority-100 non-resistance regional owner (one inversion); C35 orders the priority-100 owner first (zero inversions). Equal-priority output is stable under repeated and reversed-input evaluation.

## Regional accounting and safeguards

Across 88 regional Hypertrophy target rows:

| Measure | Units / count |
|---|---:|
| Raw regional residual | 698 weekly units |
| Authorized whole-set units | 698 |
| Finite allocator funded | 565 |
| Compatible materialized | 549 |
| Unfunded by finite capacity | 133 |
| Funded but not materialized | 16 |
| Regional overrun targets | 0 |
| Duplicate physical set rows | 0 |
| Unauthorized material rows | 0 |

The remaining unfunded/shortfall quantities stay explicit in each target row in the machine census; capacity does not erase the B4 need. C33 selection remains 268 candidates, 27 selected owners and 241 normal rejections. Candidate rejection is not counted as failure.

## B7/B8 and routing

| Measure | C34 before | C35 after |
|---|---:|---:|
| Aggregate-only false H regression cases | 2 | 0 |
| Regional overrun regression targets | — | 0 |
| Collateral regression cases | 1 | 0 |
| `CHANGE_PROVENANCE_UNCLOSED` cases | 21 | 21 |
| Unexplained removed-owner attributions | 47 | 47 |
| Unexplained prescription-change cases | 1 | 1 |
| B8 CONTROL-required cases | 22 | 22 |
| Routes | CONTROL 22 | CONTROL 22 |

The unexplained removal and `persona4_recent` prescription-change blockers were not reclassified. B8 remains CONTROL-required for all cases, and no B7/B8 predicate was relaxed. Build accounting remains CONTROL 22 / EXPERIMENTAL 22 / TOTAL 44 / THIRD 0. Power and JUMP_LANDING material remain zero. Core prescription-shape shortfalls and approved Task semantics are unchanged.

The deterministic per-case, per-target and per-owner allocator evidence is in [`c35-hypertrophy-capacity-census.json`](c35-hypertrophy-capacity-census.json). It includes raw and adjusted aggregate H observations, B4 residuals, B5 owners, B6 status, funded/materialized/shortfall units, exact pre-kernel priority order, B7/B8 outcomes, routes and generation timings.

## Versions and validation record

Protocol/Runtime advance to `3.68.0` / `RECORD_BASED_PLANNER_0.15.10_KOTLIN_1` for the changed B7 comparison semantics and serialized capacity trace. App `0.5.1.5`, Room `38` and persistence schemas are unchanged. The C34 contract remains accepted for exact incumbent lineage compatibility.

The final full-suite census reports 22 generated cases in 6,047 ms total (mean 274.9 ms, median 219 ms, max 763 ms). The production coverage report SHA-256 is `BAC4835B1609119F32C9527052293D68448B653A78CC60C4CE34C8AF8B07A555`; the census SHA-256 is `839002052AC68418D471C4A7E976807CA180D66404B701EB91B00E6073434265`. Local validation passed 2,380 JVM tests (0 failures, 0 errors, 4 skips) using the existing external JDK 17 worker-restart setup after a JBR 21 Robolectric native SQLite access violation; `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `assembleDebug`, protocol documentation validation (9 families / 36 protocols), and `git diff --check` passed. The local debug APK is 70,741,861 bytes with SHA-256 `C533776CA62C58A757A1F8F6582B4ABAB720022CDC44C81A7BCD1A9FFE1DD7F1`. Implementation/test commit `5df9bda8` is `lastAuditedCommit`; Hosted CI and artifact hashes are tracked in the completion report.
