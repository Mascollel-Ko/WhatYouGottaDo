# C34 — Removed-identity provenance and regression audit

The 90 unexplained removed-owner attributions were mixed: 43 had an exact authorized movement replacement chain that B7 did not consume, while 47 still lacked enough causal evidence and remain fail-closed.

## Scope and evidence

C34 started from `3b2521d41389e18a1ac45e588e3f9cad44007af3` (Protocol `3.66.0`, Runtime `RECORD_BASED_PLANNER_0.15.8_KOTLIN_1`). The before-state census was generated from the 22-case production comparison before the B7 consumer change and is retained with every removed owner-week dossier in [`c34-removed-identity-provenance-census.json`](c34-removed-identity-provenance-census.json).

The before census contains 200 removed owner-week rows, representing 100 `(case, old owner)` pairs and 24 globally distinct `(stableKey, selectionRole)` identities. B7 marked 90 owner-case attributions (180 owner-week rows) unexplained. There were 43 exact replacement owner-target pairs in that unexplained set. Allocation diagnostics contained no exact `OwnerDisplacementEdge` for any removed owner, so no removal was closed by guessing at capacity or placement events.

## Root cause and bounded change

For 43 attributions, the evidence chain was exact: the old CONTROL owner existed; B11 identified a canonical replacement; B4 had the matching movement target and regional dose; B5 selected the exact replacement owner for that primary target; B6 authorized its regional Hypertrophy prescription; and materialization produced the same direct owner in each removed week for the authorized weekly units. These were movement-to-Hypertrophy canonical replacements, not same-key guesses. Their movement-role identities were:

| Movement target | Exact replacement attributions |
|---|---:|
| HORIZONTAL_PUSH | 12 |
| POSTERIOR_CHAIN | 11 |
| ARMS_BICEPS | 8 |
| CALVES | 7 |
| ARMS_TRICEPS | 3 |
| LOWER_KNEE | 2 |
| **Total** | **43** |

The production defect was a consumer gap: B7 already had typed B11/B5/B6/materialization evidence and recognized canonical replacement for Quality and Task targets, but did not include exact movement targets in that replacement path. C34 adds the movement case only when the exact B4 target, B5 primary owner, B6 authority, materialization trace, role, target, direct relationship, week set, and authorized units all agree. B7's readiness predicate and B8's cutover checks were not weakened.

The other 47 unexplained owner-case attributions remain open: 38 B11 canonical replacements lack the complete exact B4/B5/B6/materialization chain needed for closure, and 9 are ineligible or outranked candidate cases without removal causality. Candidate non-selection is not treated as proof that a CONTROL owner was removed. No allocator/scheduler displacement was asserted without an exact displacement edge.

## Before and after

| Measure | Before C34 | After exact-evidence consumer |
|---|---:|---:|
| B7 cases with `CHANGE_PROVENANCE_UNCLOSED` | 22 | 21 |
| Unexplained removed owner-case attributions | 90 | 47 |
| Unexplained removed owner-week rows | 180 | 94 |
| Exact movement replacement attributions closed | 0 | 43 |
| Unexplained added identities | 0 | 0 |
| Unexplained prescription-change attributions | 1 | 1 |
| `TARGET_REGRESSED` cases | 2 | 2 |
| Collateral regression cases | 1 | 1 |
| Routes | CONTROL 22 | CONTROL 22 |
| B8 authorized / CONTROL-required | 0 / 22 | 0 / 22 |

One case (`persona2_sparse`) becomes B7-eligible after its removal provenance is fully explained. B8 still requires CONTROL because its weekly B6 authorization is missing/short. All cases therefore remain CONTROL; this is expected and preserves the separate cutover guard.

## Remaining regressions are real and stay blocked

Both target regressions are `QUALITY:HYPERTROPHY` and reflect an actual aggregate-dose regression, not a provenance-only classification:

- `persona1_mixed`: CONTROL distance 3 units versus EXP distance 39; the target is marked collateral (`directlyAffected=false`). Six regional movement targets added 48 Hypertrophy set units while the aggregate Quality target's B4 maximum is 9. The same overage caused the collateral regression.
- `persona1_reviewed`: CONTROL distance 6 units versus EXP distance 42; directly affected. Six regional movement targets contributed 45 units against residuals of 8, 8, 8, 5, 8, and 8, alongside 3 existing equivalent units and a 3-set Quality owner. The aggregate B4 maximum is 6.

These cases demonstrate a cross-target budget arbitration gap between regional movement residuals and the aggregate Quality target. C34 does not invent a dose cap or loosen B7: both remain CONTROL until an authorized upstream allocation resolves the conflict.

## Prescription change remains unexplained

`persona4_recent` has two weekly material diffs for `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: CONTROL has 5×8 at 40 kg; EXP has 4×8 at 40 kg. Placement also moved from day 4 to day 2, but allocation diagnostics show the initial placement stage operating on 4 sets and no B5 owner or B6 authority. Thus the observed 5→4 set mutation has no exact authorizing source and remains `UNEXPLAINED_PRESCRIPTION_CHANGE`; it is not closed as a scheduling-only move.

## Preserved safeguards

C33 residual accounting remains bounded: candidates are not all materialized, and the prior corpus had 268 candidate rows, 27 selected owners, 241 normal rejections, zero overfill, zero duplicate credit, zero unauthorized material, and zero unexplained added identities. Power and JUMP_LANDING material remain zero. Build accounting remains CONTROL 22 / EXPERIMENTAL 22 / TOTAL 44 / THIRD 0. No B7/B8 relaxation, CONTROL-derived authority, generic dose, or displacement inference was introduced.

The exact before dossiers and the after per-week attribution status are in the machine-readable census. Protocol and Runtime advance to `3.67.0` and `RECORD_BASED_PLANNER_0.15.9_KOTLIN_1` because B7 now consumes a new exact movement-replacement evidence path. App `0.5.1.5` and Room `38` are unchanged.

The audit/test implementation is commit `a6519964674d39bc69f37010d513f5575c9ab476` (`lastAuditedCommit`). The final census SHA-256 is `F2A1A6C1F71C350DD557764E1F326636CB2F5D9DA2A94EEB2DFE9C6421C69C8A`.
