# Phase C4A Axis Rubric Policy

## Score contract

An axis score is a normalized ordinal value inside one declared `axisScaleId`; it is not a physical unit and is not comparable across unrelated scales.

- `0.0`: confirmed non-applicable or biomechanically negligible only. UNKNOWN is never zero.
- `1.0`: low verified or fallback exposure.
- `2.0`: moderate reference exposure.
- `3.0`: high exposure.
- `4.0`: very high exposure within the comparison family.
- `0.5` increments are accepted only when a rubric explicitly contains them.

M, T, and C are not summed, averaged, or assigned universal weights.

## Scale identity

Every scale pins target, axis, physical metric, normalization, measurement family, comparison family, population scope, and condition family. Measured strain and modeled force therefore cannot silently share one scale.

## Rubric kinds

- `ABSOLUTE_INTERVAL`: requires compatible observations, at least two independent sources or 12 same-cohort conditions plus external validation, explicit boundaries and inclusivity, and passed sensitivity analysis.
- `CONDITION_ANCHOR`: exact value and source condition; no neighboring-value classification or interpolation.
- `ORDERING_RULE`: relative order only; no numeric threshold.
- `FAMILY_DEFAULT`: operational-only family or complex fallback.
- `CONSERVATIVE_FALLBACK`: final operational-only non-null fallback.

C4A currently creates 48 family defaults and 48 conservative fallbacks. It creates no absolute intervals, condition anchors, or ordering rules until evidence passes their gates.

## Provenance and fallback

Research scores may be null. Operational scores for applicable targets must be non-null through the ordered ladder: exact condition, compatible stableKey condition, approved close variant, functional complex, movement family, conservative fallback. Each axis stores its own provenance, confidence, source IDs, extraction IDs, rubric, fallback rule, inheritance level, limitations, and coefficient-set ID.

When a complex fallback is used, the allocation policy is parent-only. The same demand is not also added to every child tissue.
