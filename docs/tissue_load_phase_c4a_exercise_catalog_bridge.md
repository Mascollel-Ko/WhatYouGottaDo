# Phase C4A Exercise-Catalog Bridge

## Live catalog audit

- Canonical exercises: 239 exact stable keys.
- Canonical `movementFamily` vocabulary: 64 values.
- Audited lower-limb applicable movement families: 23.
- Audited not-applicable or deferred families: 41.
- Exercises with lower-limb C4A applicability: 87.
- Explicit exercise-complex relationships: 378.
- M/T/C operational traces: 1,134.

The live catalog contains `movementFamily`, `movementSubtype`, and `programSlot`; it does not contain a `trainingRole` biomechanics field. Mapping uses exact `stableKey` and exact canonical `movementFamily` values. Exercise display names and substring matching are absent from the generated mapping contract.

## Sparse inheritance

Bridge resolution priority is exact stableKey plus condition, stableKey base, approved close variant, movement-family profile, functional-complex default, then conservative fallback. Exact overrides and approved variants are currently empty rather than fabricated. The bridge includes 249 movement-family profiles, 27 complex defaults, and 27 conservative fallbacks.

Condition modifiers are first-class source-condition fields: laterality, external load and placement, added and total mass, percent 1RM, ROM, velocity, impact and landing type, rebound or stick, rear-foot elevation, direction, drop height, surface, perturbation expectation, and knee or ankle angle.

## Completeness and boundaries

All nine functional complexes have explicit M/T/C rules after the bridge extension. Every applicable relationship resolves non-null M, T, and C operational scores with provenance, confidence, fallback rule, resolution path, and `TISSUE_MTC_C4A_0_1_1`. Research scores remain null where no compatible observation exists; UNKNOWN is never converted to zero.

Complex fallback allocation is parent-only. The bridge does not duplicate the same fallback across the parent and all children. It activates no runtime session calculation and writes no production profile.
