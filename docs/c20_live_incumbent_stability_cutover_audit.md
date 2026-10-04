# Phase C20 — live incumbent feasibility and bounded placement stability

## Identity and baseline

- C19 merge commit / C20 start: `46a166499dc2136c73d22e66670d6232d59c21b4`.
- C20A–C implementation: `dea234a622783f79c5374f5607bf709572b0b344`.
- C20A–C test follow-up: `3812c1777638b83cf1b704f01c4be949da49f561`.
- Final C20 implementation/test commit and `lastAuditedCommit`: `6466b7d0cc237caf487cecb250e1a5ad91844566`.
- C19 merge Hosted CI run `37181317374` passed 2,268 tests (0 failures, 0 errors, 4 skips), including protocol, Community/Cloud contracts, whitespace, coverage, APK assembly, signer, and artifact upload.
- C20 protocol/runtime are `3.55.0` / `RECORD_BASED_PLANNER_0.14.7_KOTLIN_1`; app remains `0.5.1.5`.

C20 activates a narrowly bounded placement preference for current-generation rows whose exact persisted `(stableKey, selectionRole, week)` incumbent placement is evaluated `HARD_VALID` against the current canonical generation. Hard-invalid and unresolved placements are not anchored. No B4/B5/B6, prescription, frequency, B7, B8, Power, or Combined policy changed.

## Live feasibility and stale-source protection

The evaluator reuses the current generation's `PlanDayProjection`, canonical week tissue projection, hard session capacity predicate, schedule, exercise identity, and primary-strength spacing policy. It evaluates exact shared owners with the new current-generation prescription and incumbent day/order while current canonical rows remain present. Each individual anchor is checked, then the combined hard-valid set is checked once. A combined-set conflict fails closed for activation. Unresolved projection or tissue inputs remain `UNRESOLVED`; no bodyweight or tissue fallback is introduced.

The 32-row regression census resolves to 12 `HARD_VALID`, 2 `HARD_INVALID`, and 18 `UNRESOLVED`. The combined hard-valid set has no conflicts. Projection accounting recorded 35 candidate-state evaluations, 238 day/OFI projection calls, and 76 tissue projection calls; no generation-scoped cache was used (0 cache hits). Only the 32 shared exact incumbent rows are considered.

`TrainingProgram.updatedAt` is insufficient because item edits, including order edits, can bypass it. The save path therefore checks a deterministic SHA-256 source token captured with the program, items, and item sets. The token includes the persisted lineage/source contract, exact item roles and placement, and fields that a replacement save can overwrite. Immediately before destructive replacement, save reloads this state in the transaction and rejects a mismatch or deleted source with `STALE_INCUMBENT_SOURCE`. A fresh snapshot succeeds; a new program has no stale-source check. C20 required no new Room migration.

## Bounded production activation

Activation runs after EXPERIMENTAL placement and before late CONTROL construction. It receives the production-owned C19 incumbent index and live feasibility result; no CONTROL/comparison/B7/B8 object is read. For the combined exact hard-valid anchors, the activator restores incumbent day/order only when the row identity set is unchanged, non-placement fields remain identical, the combined state is hard-valid, and any non-anchor adjustment is limited to a same-day order slot occupied by an anchor. Duplicate final order slots or wider collateral movement prevent activation. Invalid/unresolved incumbents receive no anchor. New work is retained.

After placement activation, target outcome, materialization, and realization diagnostics are recomputed from the final EXP rows before CONTROL comparison and B7/B8. Existing B8 predicates were not relaxed and no derived displacement authority was added. Preservation events are placement provenance and confer no exercise, prescription, set, quality, frequency, or destination authority.

| Case | Live feasibility | Activation | Exact hard-valid rows restored |
|---|---:|---|---:|
| `persona0_mixed` | 4 valid / 0 invalid / 2 unresolved | `ACTIVATED` | 4 |
| `persona0_reviewed` | 0 / 0 / 4 | `NO_ELIGIBLE_HARD_VALID_ANCHORS` | 0 |
| `persona3_reviewed` | 2 / 2 / 4 | `ACTIVATED` | 2 |
| `persona4_mixed` | 6 / 0 / 8 | `ACTIVATED` | 6 |

The exact owner/week preservation events, pre-activation and post-activation locations, feasibility evidence, reason codes, snapshot token, and case-level decisions are in [`c20-live-incumbent-stability-census.json`](c20-live-incumbent-stability-census.json).

For `persona3_reviewed`, the two incumbent RDL day-2 rows are hard-invalid and are released; that does not authorize the current EXP day-1 destination. The four Power rows remain outside production authority and are unchanged by C20. For `persona0_reviewed`, all four shared rows remain unanchored because their feasibility is unresolved.

`persona2_reviewed` remains the positive stable-insertion reference: it routes to `B8_STRENGTH_CALIBRATION_V1`, has 14 shared owner-week rows unchanged and two calibration rows, and needs no preservation event.

## Placement and semantic results

Across the 32 audited shared owner-week deltas, the exact hard-valid incumbents were restored in 12 rows. Shared placement deltas fell from 32 to 20; summed distance from incumbent day fell from 62 to 42; order-only changes fell from 2 to 0. The remaining 20 are not forced: two hard-invalid incumbents and 18 unresolved incumbents remain unanchored. No frequency, set, rep, load state, target effort, or rest changed.

The generated standard production coverage remains byte-identical at SHA-256 `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`. The route census remains CONTROL 20 / Strength V1 1 / Strength Calibration 1 / Hypertrophy 0 / Combined 0. B7 remains provenance-unclosed 11 / target-unmet 9 / regressed 1. B8 was not modified; the remaining four C15 cases have not been forced through B8. Build order remains B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9, with two builds and no third build.

Activation is idempotent at the placement pass: the focused test reapplies the same exact anchors to an already stabilized synthetic result and observes no further row changes. An unchanged-input persisted end-to-end regenerate/save/regenerate loop remains a separate integration limitation; C20 does not claim that broader UI workflow was exercised.

## Validation

- Focused C10–C20 regression groups: 249 tests across the coverage, authority, planning-independence, persistence/stale-source, provenance, selector, rebalancer, reflow, and C20 placement suites; 0 failures, 0 errors, 0 skips. This includes three persisted-program generations with identical canonical EXP placements and two normal builds per generation.
- Full local `:app:testDebugUnitTest --no-daemon --max-workers=1`: incomplete due Windows JBR 21 Robolectric native crash after 1,505 completed tests; 0 recorded failures, 0 errors, 3 skips. Native frame was `robolectric-nativeruntime.dll`, through `SQLiteConnectionNatives.nativePrepareStatement` during `SQLiteSession.beginTransaction`; Gradle then reported worker IPC connection reset. This is not counted as a full pass.
- C20A–C Hosted CI run `37205894375` passed 2,268 tests (0 failures, 0 errors, 4 skips). C20D implementation and final documentation heads are checked separately.
- C20D local implementation checks: 249 focused tests across 19 concrete C10–C20 test classes (0 failures, 0 errors, 0 skips); protocol validator, Community/Cloud contract tests (9/9), and `git diff --check` passed.
- Full local `:app:testDebugUnitTest` reached 1,505 completed tests before the Windows native crash described above.
- C20D Hosted run and final docs-head Hosted run, including APK and coverage artifact metadata, are recorded in the task closeout after those runs complete.

## Next boundary

C20 closes the bounded hard-valid incumbent preservation case. Hard-invalid incumbents are released and unresolved incumbents remain fail-closed. It does not prove that a released incumbent's new destination is necessary, does not provide displacement authority, and does not address unsupported Power. Any later work must preserve these boundaries and keep exact movement causality separate from incumbent continuity.
