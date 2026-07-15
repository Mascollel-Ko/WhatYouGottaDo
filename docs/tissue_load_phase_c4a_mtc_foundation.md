# Phase C4A Lower-Limb M/T/C Foundation

Status: `C4A_METADATA_FOUNDATION_AND_RESEARCH_CATALOG_PARTIAL`

## Boundary

M/T/C are independent metadata axes for one exercise, one target, and one explicit condition:

- **M (Magnitude):** size of tissue or joint load under the condition.
- **T (Temporal profile):** how that load develops during the event or phase.
- **C (Mechanical context):** structured movement and loading conditions that expose the target to a mechanism.

Sets, repetitions, weekly accumulation, current fatigue, and historical-session recalculation are outside these axes. No M/T/C sum, average, or universal weighted score is defined.

## Registries

- `tissue_functional_complex_registry_v1.csv` defines 9 complexes and 46 components. Direct child-target outputs always remain separate.
- `tissue_mtc_axis_metric_registry_v1.csv` defines 48 rules: M, T, and C for 16 tissue, complex, or dynamic-stabilization targets.
- `tissue_dynamic_stabilization_profile_registry_v1.csv` keeps hamstring, peroneal, and posterior-tibial stabilization separate from tissue mechanical load.

The typed parser and validator reject missing axes, axis-metric prefix mismatches, context rules without structured fields, duplicate IDs, and dynamic profiles merged into mechanical-load profiles.

## Ontology additions

- Temporal metrics: `EVENT_AVERAGE`, `PHASE_AVERAGE`.
- Measurement metric: `INTERSEGMENTAL_JOINT_FORCE_RESULTANT`.
- Evidence relations: `CADAVERIC_MECHANISM`, `FINITE_ELEMENT_MECHANISM`.

These additions preserve the distinction between averages and peaks, intersegmental resultants and compartment contact forces, and mechanism evidence versus in-vivo magnitude evidence.

## Deferred scope

Hip contact and hip tendon research remain in the lower-priority backlog. This phase adds no runtime tissue-load activation and changes no session fatigue calculation.
