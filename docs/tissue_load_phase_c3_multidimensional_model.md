# Phase C3 Multidimensional Tissue-Load Model

## Boundary

Phase C3 stores mechanical observations as a vector. It does not produce runtime fatigue values, a universal burden
score, composite weights, final claims, human approvals, blind reviews, or production profiles.

Each C3 dimension has five independent identities:

1. `tissueId`: the anatomical structure.
2. `mechanicalLoadMode`: compression, tension, directional stress, impact stabilization, or another defined mode.
3. `temporalMetric`: peak, impulse per event, loading rate, cyclic exposure, or another defined time characteristic.
4. `measurementMetric`: what the study measured or modeled.
5. `normalizationBasis`: how the reported value is normalized.

Thus PFJ compression peak, impulse per event, and loading rate are separate dimensions even when one study reports all
three. Tendon force and tendon strain also remain separate measurement families.

## Registries

| Registry | Rows | Responsibility |
| --- | ---: | --- |
| `tissue_mechanical_load_mode_registry_v1.csv` | 17 | Closed mechanical vocabulary and tissue-class boundary |
| `tissue_temporal_metric_registry_v1.csv` | 11 | Closed temporal vocabulary and source/derived boundary |
| `tissue_measurement_metric_registry_v1.csv` | 21 | Measured or modeled outcome and compatibility |
| `tissue_normalization_registry_v1.csv` | 14 | Unit and normalization semantics |
| `tissue_load_dimension_registry_v2.csv` | 42 | Valid lower-limb tissue/load-mode/temporal combinations |
| `tissue_load_dimension_migration_v1.csv` | 33 | Explicit migration for every historical `TissueLoadDimension` |

`TissueMultidimensionalValidator` rejects unregistered vocabulary, invalid cross-products, missing derived formulas,
incompatible normalization, and source-specific composites marked rubric/profile eligible.

## Source-Observed And Derived Metrics

Source-observed rows preserve the study's event, method, condition, unit, and normalization. Application-derived
`CUMULATIVE_SESSION_IMPULSE` is only defined by `SUM_COMPATIBLE_EVENT_IMPULSES_V1`; it requires supported per-event
impulse, a deterministic event count, resolved laterality, and no double counting. Phase C3 defines this contract but does
not populate runtime values. Missing event count remains missing, and duration never implies jump, hop, lunge, or
badminton event count.

## Proxy And Composite Limits

Ground-reaction force, external joint moment, and EMG are not internal tissue force. A C3 claim may use a proxy only with
an explicit validated mapping. `SOURCE_DEFINED_COMPOSITE_INDEX` is source-specific, non-rubric, and non-profile. No fixed
weights combine peak, loading rate, impulse, cyclic exposure, strain, or energy storage into one burden value.

Tendon stress, tendon force, tendon strain, and ligament strain have distinct measurement or normalization identities.
The ACL landing evidence uses `MEASURED_LIGAMENT_STRAIN_PERCENT`; it is never labeled as tendon strain. Source-defined
kinematic stability observations are retained only as explicit proxies and cannot become internal tissue load without a
validated mapping.

## Legacy Compatibility

Historical CSVs and superseded requests keep their original `loadDimension` values and remain parseable. Exact legacy
migrations are limited to values that already encode a mechanical mode and temporal metric. Ambiguous values require
manual review, and generic historical `COMPRESSION` is source-specific rather than silently becoming compression peak.

## Approval scope

The deterministic C3 request hashes all four vocabularies, valid dimensions, migrations, metric extractions, dispositions,
candidates, rubrics, research decisions, canonical mappings, transfer restrictions, historical and new source snapshots,
publication-integrity snapshots, the supersession reference, and the upper-limb backlog. Candidate limitations and rubric
comparison boundaries are scientific payload, so changing them changes the request hash.

Request `TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B` is pending only. Its scope hash is
`48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8`; it does not populate an approval ledger or make
any row production eligible.
