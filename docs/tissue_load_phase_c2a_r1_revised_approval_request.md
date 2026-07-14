# Phase C2A-R1 Revised Approval Request

## Status And Scope

- Completion: `REVISED_APPROVAL_PACKAGE_PARTIAL`
- Request: `TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD`
- Scope hash: `74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f`
- Audit: `tissue_research_c2a_r1_4b74d89f1410`
- Audit input hash: `4b74d89f141094625dbd03a334624fa08399e4e69f2fc9bbdf8fa1c0092c5948`
- Scope: 24 revised candidates, 2 revised rubrics, 12 old-candidate dispositions, 5 old-rubric dispositions,
  12 directives, 13 research decisions, 49 canonical mappings, and 10 source/integrity snapshots.
- Material research targets explicitly blocked: 6.

This file is a future approval request, not approval. Human approvals, formal final claims, blind reviews, and all four
production-profile ledgers remain empty.

## Why The Old Request Was Superseded

The historical request confused BW-normalized internal force with exercise resistance, generalized fixed conditions to
arbitrary external loads, lacked weighted squat evidence, merged split-squat variants, used a 60-degree squat as a generic
anchor, and treated a source-specific PFJ peak/impulse composite as generic compression. C2A-R1 separates peak,
impulse, and loading rate, keeps jump and non-jump tasks distinct, and records the missing upper-limb scope.

## Candidate-By-Candidate Disposition

| Candidate | Old status | New disposition | Preserved measurement | Condition/dimension change | Band | Remaining limitation |
| --- | --- | --- | --- | --- | --- | --- |
| `CLAIM_C_58F3A7A0619274D3` | technically re-audited | narrowed | seated Achilles 0.5-0.7 BW modeled force | 15 kg confirmed only for unilateral row; peak force unchanged | generic LOW removed | bilateral load and dose response unresolved |
| `CLAIM_C_44B1199853E615F2` | technically re-audited | unchanged | standing single-leg Achilles peak | exact source condition | MODERATE retained | no weighted transfer |
| `CLAIM_C_F4E8375FB29EC165` | technically re-audited | narrowed | repeated unilateral hop peak | close variant only | VERY_HIGH retained with limits | not every hop-and-stick task |
| `CLAIM_C_6319786B969495EC` | technically re-audited | unchanged | Achilles loading rate | kept separate from peak | none | no rate rubric |
| `CLAIM_C_ABC6C4B0ECA22AC2` | technically re-audited | narrowed | bodyweight full-depth patellar peak | weighted transfer blocked | removed | no external-load model |
| `CLAIM_C_BD8915D6D39A0428` | technically re-audited | remapped | bodyweight Bulgarian patellar peak | maps to Bulgarian, not RFESS | removed | protocol/load transfer blocked |
| `CLAIM_C_8A2D37D94C1330F7` | technically re-audited | generic anchor removed | 60-degree source values | split to PFJ peak/impulse/rate | LOW removed | ROM not recorded by generic app squat |
| `CLAIM_C_084C0B3DCC597268` | technically re-audited | reclassified | full-squat PFJ values | split to peak/impulse/rate | composite removed | weighted transfer non-comparable |
| `CLAIM_C_0777BA7A1FB88DF1` | technically re-audited | remapped/reclassified | Bulgarian PFJ values | Bulgarian exact key; three metrics | composite removed | not RFESS or all split squats |
| `CLAIM_C_E09E28E71A401E8D` | technically re-audited | reclassified | non-jump lunge PFJ values | three metrics; no jump-lunge transfer | composite removed | lunge variants/load placements differ |
| `CLAIM_C_E540DF218D72E603` | technically re-audited | reclassified | drop-jump PFJ values | peak/impulse/rate separated | composite removed | protocol-specific landing task |
| `CLAIM_C_4D02CF35465C7511` | technically re-audited | reclassified | maximal forward-hop PFJ values | peak/impulse/rate separated | composite removed | close variant, not every hop-and-stick |

## Revised Rubric Map

| Tissue | Dimension | Band | Anchor | Metric/condition | Confidence | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Achilles tendon | peak tensile load | MODERATE | standing single-leg heel raise | modeled internal force in BW, exact source condition | moderate | non-production pending decision |
| Achilles tendon | peak tensile load | VERY_HIGH | repeated unilateral hopping close variant | modeled internal force in BW, disclosed transfer limit | low | non-production pending decision |

The generic seated-calf LOW and both PFJ composite rubrics are removed. No HIGH, weighted, PFJ, patellar, rate, cyclic,
energy-storage, strain, eccentric, or impact band is invented.

## Research And Mapping Result

- Research decisions: 1 draft-rubric result, 6 evidence-found-but-not-comparable results, and 6 blocked results.
- Weighted squat and lunge evidence confirms load/ROM/protocol sensitivity but does not supply a cross-study linear model.
- Split squat, Bulgarian split squat, and RFESS retain distinct canonical keys.
- The source composite remains only `STUDY_SPECIFIC_COMPOSITE_LOADING_INDEX`, never generic `COMPRESSION`.
- Canonical audit generation covers 49 exact stable keys and is row-order independent.
- No new governed source was added; the package hashes the existing 10-source verification and integrity snapshots.

## Exact Future Approval Statement

```text
I approve revisedApprovalRequestId=TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD,
approvalScopeHash=74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f,
reviewPath=SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
covering exactly 24 revised claim candidates, 2 revised rubric rows, 12 old-candidate dispositions, 5 old-rubric dispositions, 12 human research directives, 13 research decisions, 49 canonical mappings, and the listed source and publication-integrity snapshots.
I understand this revised package is same-session, non-independent, and non-production until formal promotion.
```

The statement above must be supplied exactly in a later explicit decision. Its presence in this document is not approval.

## Verification

- Approval generator second run: request byte-identical; audit append idempotent.
- Live source identity verification: 10/10 verified.
- Live publication-integrity verification: 10/10 no adverse notice; 0 blockers; committed artifact byte-matched.
- Focused C2A-R1 package/evidence/supersession tests: passed.
- Full unit suite: 713 tests, 0 failures/errors/skips.
- `compileDebugKotlin` and `assembleDebug`: passed.

## Upper-Limb Next Batch

`TISSUE_RUBRIC_B2_UPPER_PRESS_PULL` queues the exact shoulder, elbow, wrist, tendon, and ligament tissues plus bench,
incline, push-up, dip, overhead/landmine press, raises, fly, pull-up/pulldown/row, curl, extension, wrist, and grip families
listed in `tissue_rubric_research_backlog_v1.csv`. C2A-R1 creates no upper-limb claims or profiles.
