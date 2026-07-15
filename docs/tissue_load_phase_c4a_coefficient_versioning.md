# Phase C4A Coefficient Versioning

`TISSUE_MTC_C4A_0_1_0` is a `DRAFT_NON_PRODUCTION` metadata set. It pins semantic hashes for the source snapshot, rubrics, canonical exercise catalog, complex registry, and axis registry. It has no effective date or publication date and activates no runtime calculation.

Future coefficient updates append a new manifest row with a new `coefficientSetId`, semantic version, hashes, change reason, and `supersedesCoefficientSetId`. Existing rows are immutable.

Future runtime integration must record `coefficientSetIdUsed` with each derived result. Existing results remain `AS_RECORDED`; optional recomputation is stored as `REPROCESSED_WITH_CURRENT_METADATA` without overwriting the original. A time series may not mix coefficient regimes without an explicit representation or regime boundary, including Bayesian analysis inputs.

This phase changes no session schema, fatigue formula, historical record, Bayesian model, or UI.
