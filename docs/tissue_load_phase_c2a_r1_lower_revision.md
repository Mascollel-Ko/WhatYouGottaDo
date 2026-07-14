# Phase C2A-R1 Lower-Limb Tissue Revision

## Status

`RESEARCH_REVISION_IN_PROGRESS`

The previous pending request is preserved but superseded before approval. No human approval, final claim, blind review,
production profile, runtime calculation, or version change is part of this revision.

## Why The Old Request Was Superseded

- Achilles and PFJ values normalized to bodyweight describe modeled internal force or impulse, not the external exercise resistance.
- Exact seated-calf, bodyweight squat, Bulgarian-squat, lunge, hop, and landing protocols were mapped too broadly to app exercises.
- External-load progression was not established for calf raises, weighted squats, unilateral knee-dominant work, or lunges.
- Split squat, Bulgarian split squat, and rear-foot-elevated split squat have distinct canonical stable keys and cannot be merged by name.
- A 60-degree squat cannot anchor the generic bodyweight squat because the app record does not encode that ROM.
- A study-specific PFJ composite of peak force and impulse cannot define generic `COMPRESSION` bands.
- Peak force, impulse, loading rate, impact, cyclic exposure, tendon force, and tendon strain require separate dimensions and units.
- The lower-limb package does not solve upper-limb evidence coverage.

## Immutable Supersession

- Old request: `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196`
- Old scope: `9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36`
- Resolution: `TISSUE_APPROVAL_RESOLUTION_C2A_R1_9D916660`
- Resolution status: `SUPERSEDED_BEFORE_APPROVAL`
- Replacement research batch: `TISSUE_RESEARCH_C2A_R1_LOWER_REVISED`
- Human approvals: 0
- Formal final claims: 0
- Production profiles: 0

## Candidate Directives

All 12 old candidates have one explicit human research directive. The directives preserve source-specific measurements
where defensible while removing generic bands, narrowing exercise correspondence, separating metrics, and requiring
external-load research. They are research instructions, not production approvals.

## Dimension Contract

PFJ research uses `PEAK_COMPRESSION`, `COMPRESSION_IMPULSE`, `COMPRESSION_LOADING_RATE`, and `IMPACT_IMPULSE`.
The historical generic `COMPRESSION` value remains parseable only for provenance. Patellar tendon `TENDON_STRAIN`
cannot substitute for `PEAK_TENSILE_LOAD`. Achilles peak load and loading rate remain separate even when they share a
source condition.

## Next Steps

1. Re-open authoritative scholarly sources for weighted squat, lunge, split-squat/RFESS, calf, jump, hop, and landing protocols.
2. Publish revised, condition-bounded candidates and metric-specific research decisions without inventing missing bands.
3. Audit exact canonical stable keys and external-load/ROM capabilities.
4. Generate a new deterministic request and exact future statement; do not treat the statement as approval.
5. Queue upper-limb pressing and pulling as `TISSUE_RUBRIC_B2_UPPER_PRESS_PULL` rather than performing a superficial backfill.

## Same-Session Scholarly Re-Audit

The revision re-opened the governed primary studies and reviewed additional primary exercise-biomechanics records. The
research is condition bounded; it does not infer a dose response from exercise names.

- Baxter et al. Achilles progression: [PMID 32658037](https://pubmed.ncbi.nlm.nih.gov/32658037/).
- Achilles heel-raise force, stress, and strain: [PMID 28145739 / PMCID PMC5343533](https://pmc.ncbi.nlm.nih.gov/articles/PMC5343533/).
- Patellar tendon rehabilitation progression: [PMID 37847102 / PMCID PMC10925836](https://pmc.ncbi.nlm.nih.gov/articles/PMC10925836/).
- PFJ peak, impulse, and rate across 35 tasks: [PMID 37272685 / PMCID PMC10315869](https://pmc.ncbi.nlm.nih.gov/articles/PMC10315869/).
- Squat with 35% BW external load: [PMID 11949662](https://pubmed.ncbi.nlm.nih.gov/11949662/).
- Forward/side lunge at 12RM, with/without stride: [PMID 18632195](https://pubmed.ncbi.nlm.nih.gov/18632195/).
- Lunge type and step-height sensitivity: [PMID 35136686](https://pubmed.ncbi.nlm.nih.gov/35136686/).
- Powerlifting squat at 70-90% 1RM: [PMID 40705768](https://pubmed.ncbi.nlm.nih.gov/40705768/).

### Seated and standing calf

The 15 kg thigh load is explicitly confirmed for the seated single-leg protocol. Accessible primary/structured records
did not establish that the same external load governed every seated row, so the old 0.5-0.7 BW range remains a
source-specific range with that uncertainty disclosed. `BW` is now recorded as
`BODY_WEIGHT_NORMALIZED_INTERNAL_TENDON_FORCE`, not exercise resistance. No study in the governed set establishes a
general external-load response for arbitrary seated or standing calf loads. The generic seated-calf LOW rubric is removed;
weighted-calf scaling remains blocked.

### Weighted squat

The bodyweight full-depth patellar-tendon and PFJ measurements remain source-specific. Wallace et al. found that a 35% BW
external load increased PFJ stress at several knee angles, and the 2025 powerlifting study reported high modeled PFJ joint
contact forces at 70-90% 1RM. These studies use different populations, depths, outcomes, and models; they confirm that load
and ROM matter but do not justify a linear multiplier. No comparable 40/60/80% 1RM series was found for patellar tendon
force or strain. Weighted patellar-tendon profiles are therefore `BLOCKED_MISSING_EXTERNAL_LOAD_MODEL`; weighted PFJ
evidence is `EVIDENCE_FOUND_BUT_NOT_COMPARABLE`.

### Split squat, RFESS, and Bulgarian squat

The canonical catalog contains three distinct identities: `ex_f2a79d37` (split squat), `ex_e2efd0fe` (Bulgarian split
squat), and `ex_bb728af2` (rear-foot-elevated split squat). The old source rows say Bulgarian squat, so revised candidates
map to `ex_e2efd0fe`, not RFESS. Rear-foot height, step length, trunk angle, knee travel, load position, tested limb, and ROM
are not interchangeable. No governed tissue-specific external-load response supports copying the bodyweight result to
loaded RFESS or every split-squat variant.

### Lunge variants

The revised Song claim is a study-defined non-jumping lunge reference only. Escamilla et al. demonstrate that lunge
direction, stride, step height, knee angle, and 12RM loading alter PFJ force/stress. Those records do not make forward,
reverse, walking, static, lateral, diagonal, goblet, front-rack, barbell, and plyometric lunges equivalent. No revised claim
is mapped to jump lunge.

### Jump, hop, and landing metrics

Song et al. report peak, impulse, and loading rate separately in one cohort. Revised rows retain all three metrics for the
60-degree squat, full squat, Bulgarian squat, lunge, double-leg drop vertical jump, and single-leg maximal forward hop.
For example, the drop vertical jump has higher peak/rate than the full squat, while the slower lunge has greater impulse
than the drop jump. This is expected metric-specific behavior, not a contradiction. PFJ compression impulse is not relabeled
as general ground-reaction impact impulse.

## Candidate Disposition Summary

| Old candidate | New disposition | Band |
| --- | --- | --- |
| `CLAIM_C_58F3A7A0619274D3` | exact seated measurement narrowed | generic LOW removed |
| `CLAIM_C_44B1199853E615F2` | standing single-leg source claim retained | condition-specific MODERATE retained |
| `CLAIM_C_F4E8375FB29EC165` | repeated-hop close variant retained | limited VERY_HIGH retained |
| `CLAIM_C_6319786B969495EC` | loading rate retained separately | none |
| `CLAIM_C_ABC6C4B0ECA22AC2` | bodyweight full-depth patellar claim narrowed | unsupported band removed |
| `CLAIM_C_BD8915D6D39A0428` | Bulgarian claim narrowed and remapped | unsupported band removed |
| `CLAIM_C_8A2D37D94C1330F7` | 60-degree research record split into three dimensions | LOW removed |
| `CLAIM_C_084C0B3DCC597268` | full-depth PFJ claim split into three dimensions | MODERATE removed |
| `CLAIM_C_0777BA7A1FB88DF1` | Bulgarian PFJ claim remapped and split | MODERATE removed |
| `CLAIM_C_E09E28E71A401E8D` | non-jump lunge claim split | MODERATE removed |
| `CLAIM_C_E540DF218D72E603` | drop-jump claim split | MODERATE removed |
| `CLAIM_C_4D02CF35465C7511` | maximal-hop close variant split | MODERATE removed |

## Revised Rubric Map

| Tissue | Dimension | Band | Anchor | Metric | Review status |
| --- | --- | --- | --- | --- | --- |
| Achilles tendon | peak tensile load | moderate | standing single-leg heel raise | modeled force in BW | revised, pending human approval |
| Achilles tendon | peak tensile load | very high | repeated unilateral hopping close variant | modeled force in BW | revised, pending human approval |

No seated LOW, PFJ composite, patellar tendon, loading-rate, impulse, or externally loaded rubric survives this revision.
Missing bands are left missing.

## Research Decision Matrix

The 13 required tissue/dimension targets have exactly one row in
`tissue_rubric_research_log_revised_v1.csv`. Two Achilles peak anchors produce a partial draft rubric. Condition-specific
Achilles rates and PFJ peak/impulse/rate measurements remain non-comparable as generic bands. Patellar weighted peak load
is blocked for a missing external-load model. Direct Achilles energy, patellar cyclic/eccentric, and PFJ impact-impulse
rubrics remain blocked for insufficient evidence.

## Canonical Mapping Audit

`tissue_canonical_exercise_mapping_audit_v1.csv` is generated from exact canonical stable keys and eight explicit movement
families, not name substrings. It contains 49 current squat, lunge, calf, jump/hop/landing, and badminton-footwork rows.
External-load and ROM capabilities are disclosed rather than inferred from similar names.
