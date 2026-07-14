# Phase C3.1 Tissue Ontology Correction

## Approval boundary

The unapproved C3 request `TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B` remains an immutable historical artifact. Resolution `TISSUE_APPROVAL_RESOLUTION_C3_1_48F86FEE` marks it `SUPERSEDED_BEFORE_APPROVAL` and links the replacement work to `TISSUE_C3_1_ONTOLOGY_CORRECTION`.

No human approval, final claim, blind review, production profile, runtime activation, version change, or release tag is created by this correction.

## Correction scope

C3.1 separates physical mechanical loading from event, movement-phase, position, functional-demand, and tissue-response context. It also corrects ligament tension support, ACL landing-strain semantics, external-load representation, evidence relations, and condition-anchor handling while preserving verified source measurements and historical C3 payloads.

## Mechanical ontology

- Physical mechanical modes: `14` (`17` in C3). `IMPACT_STABILIZATION`, `END_RANGE_STRESS`, and `ENERGY_STORAGE_RELEASE` are not physical directions and are excluded from C3.1 mechanical-mode identity.
- `IMPACT_STABILIZATION` moves to event or functional-demand context.
- `END_RANGE_STRESS` moves to position context while exact angles remain source conditions.
- `ENERGY_STORAGE_RELEASE` moves to tissue-response and functional-demand vocabularies; the underlying tendon mode is `TENSION`.
- `TENSION` explicitly supports tendon, ligament, and fascia.

Event context, movement phase, position context, functional demand, and tissue response are typed but do not alter mechanical dimension identity. Historical C3 enums and CSVs remain parseable and unchanged.

## Corrected dimensions

C3.1 contains `39` valid dimensions and a `42`-row old-to-new accounting map. ACL peak ligament strain becomes `ACL_TENSION_PEAK`. Two Achilles energy-response dimensions retain tensile mechanics while moving energy to response metadata. Three external-loading-rate dimensions previously represented by `IMPACT_STABILIZATION` are blocked because no validated internal mapping exists.

External joint moments, ground-reaction forces, and kinematic stability measures cannot populate internal tissue dimensions unless an explicit validated proxy mapping exists. Unvalidated proxies and context-only evidence are never rubric or profile eligible.
