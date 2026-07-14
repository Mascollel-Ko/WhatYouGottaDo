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
