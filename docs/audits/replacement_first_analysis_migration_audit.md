# Replacement-first analysis migration audit

| Field | Value |
|---|---|
| Baseline | `88e40145be8caefb35794b9ff11c48b97c53929d` (`v0.5.0.37`) |
| Audit date | 2026-08-16 |
| Scope | Current runtime analysis, UI/Lab consumers, persistence compatibility, and canonical authorities |
| Rule | A legacy implementation is retired only after its capability is replaced and verified, unless it is proven `DEAD_NO_CAPABILITY`. |

## Frozen semantic authority snapshot

No authority asset is changed by this phase. CSV row counts exclude the header.

| Authority asset | Rows | SHA-256 |
|---|---:|---|
| `docs/metadata_authority/core_training_classification_review_2026-08-13.csv` | 241 | `3c819568012cd17726486e7f3e21cac972c95eec1736e8ab038e9edc1c3fa954` |
| `docs/metadata_authority/badminton_objective_relations_v2_authority.csv` | 280 | `bbd4277111e52fc37a09840ebe41ef0dbe91347b9d17bbff6b4dac9a4cf47a56` |
| `app/src/main/assets/metadata/canonical_v1/identity_master.csv` | 257 | `e790eae55f94e2ea9644078114315b1febf2d7b2eb1c0f602232f1a42bd26eb4` |
| `app/src/main/assets/metadata/canonical_v1/core_relations.csv` | 272 | `cc82347feef75a3d27de0802856111f80bc96a0ba91552ebb5178786bf617489` |
| `app/src/main/assets/metadata/canonical_v1/badminton_objective_relations.csv` | 280 | `bbd4277111e52fc37a09840ebe41ef0dbe91347b9d17bbff6b4dac9a4cf47a56` |
| `app/src/main/assets/metadata/canonical_v1/badminton_relations.csv` | 1,865 | `e9d22c985b6985e7e210cd6a0b16d3864ba4611af07912273e75ab153cd42d2b` |
| `app/src/main/assets/metadata/canonical_v1/movement_relations.csv` | 2,253 | `858219fdc1bb577249565af15a5bcaf93c94a8259bd7450876735b70f07e2709` |
| `app/src/main/assets/metadata/canonical_v1/muscle_relations.csv` | 797 | `733337040bdd0aad150fcc1a2313f299ec26676e043761cdc2733ed3de78c6ca` |
| `app/src/main/assets/metadata/canonical_v1/strength_proxy_relations.csv` | 17 | `4ab67d84d1cea801b2c08dc272200d4170da6a62506403aa330a0e7e95573b8f` |
| `app/src/main/assets/metadata/strength_proxy_prior_v1/strength_proxy_relations_v1.csv` | 24 | `1514638184e077281243fdf746826193d44fd2e35e5a2139deaf328eaf61a52d` |
| `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv` | 257 | `e096fdd3b969beb159af889079ceb866d9de9aab9de22d986baa9288f2851b8c` |
| `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv` | 3,637 | `f900e57fd1845a43d16a143d278067de9daa6c4921d77c0135b678fd8a149423` |
| `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_dose_profiles_v1.csv` | 33 | `e3ed3d10cb72b4c2a03caa3defa6a7a8208f59421c4e32ac5526554b74986d8e` |
| `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_tissue_relationships_v1.csv` | 110 | `6af87c881667e5db48a632c7686898cf55a6d96b8469e6bb7d18282a09ff5099` |
| `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_recovery_curve_knots_v1.csv` | 114 | `9fc93d788ab9217d8508a679a93bba320a80b2cffa6c40e45136b8180b1e2b6b` |
| `app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json` | n/a | `0096441bfd923a96949a2df1bdce0f63c7fed6d8c611a854e9df0983b9109725` |

Sentinel verification on the baseline passed in `CanonicalAnalysisAuthorityTest`:

- `band_pallof_press`: explicit `ANTI_ROTATION / SUPPORTIVE` relation retained.
- `cable_pallof_press`: explicit `ANTI_ROTATION / SUPPORTIVE` relation retained.
- These two rows are individually approved and are not inferred from `CoreDirectTarget`.
- `ex_a8385c4a` (Copenhagen plank): existing `DECELERATION` and `FOOTWORK` relations retained without reinterpretation.
- No documentation/asset disagreement requiring `BLOCKED_FOR_SEMANTIC_REVIEW` was found for these sentinels.

## Capability migration matrix

| Family | Legacy symbol or capability | Required now | Real consumers | Canonical replacement owner | Contract and status | Cutover / deletion gate |
|---|---|---|---|---|---|---|
| A | `AnalysisEngineV3`, `AnalysisDashboardV3Result`, `AnalysisInputCollector`, `AnalysisInputSnapshot`, `Common*Metrics`, disabled method registry, sentence scaffolding | No product, UI, Lab, persistence, or compatibility capability. A debuggable startup path computes a log-only summary; most outputs are discarded. | `TrainingRepository.logDebugSummary()` only; no UI/Lab/public repository/backup/Room consumer | None required | `DEAD_NO_CAPABILITY` | Remove the debug invocation and its closed dependency island; compile and architecture guard must pass. |
| B | `LegacyBadmintonContractOracle` | No. It is an unreferenced test helper and is not an authority asset or compatibility parser. | Declaration only | None required | `DEAD_NO_CAPABILITY` | Remove after repository-wide reference proof. |
| C | `BadmintonTrainingLoadIndexCalculator` legacy court/footwork/support composite | Yes. Current practice-load trend, method examples, detail charts, and Lab series depend on it. | `PerformanceTrendEngine`, `PerformanceChartSpecBuilder`, analysis UI, Analysis Lab, tests | Separate `BadmintonPracticeLoad` owner plus existing `BadmintonObjectiveStimulusCalculator` | `REPLACEMENT_REQUIRED` | Audit governed practice formula, provide practice calculator, characterize, wire all consumers, then retire composite fields. |
| D | Nine-objective badminton stimulus | Yes, current canonical capability. | Trend engine, badminton screens, canonical repository, tests | `BadmintonObjectiveStimulusCalculator` + `CanonicalBadmintonObjectiveCatalog` | `CURRENT_CANONICAL_AUTHORITY` | Preserve all 280 explicit relations and all nine objectives. No migration in this phase. |
| E | `BADMINTON_TRAINING`, `COURT_VOLUME`, `FOOTWORK_REACTIVE`, `BADMINTON_SUPPORT`, `BadmintonWeekIndex` composite fields | Yes until semantically equivalent current metrics are registered and saved selector state is handled. | Analysis detail UI, Analysis Lab registry/pipeline, chart builders, trend tests | Explicit practice metric and objective-specific metrics, only where consumed | `REPLACEMENT_REQUIRED` | Never relabel an old metric ID. Add providers and selector fallback before removal. |
| F | `StrengthPerformanceIndexCalculator` composite performance/intensity/volume/efficiency | Yes. The UI and Lab still consume all four concepts. | `PerformanceTrendEngine`, strength detail UI, Lab, tests | Persistent posterior for modeled performance; exact-load/muscle builders for volume; no approved efficiency replacement yet | `REPLACEMENT_REQUIRED` | Split capabilities, verify each contract, and keep the calculator until every live metric has a provider. |
| G | Persistent strength posterior | Yes, current canonical modeled-performance authority. | Repository rebuild/event lifecycle, strength UI, history persistence | `StrengthPerformanceRegistry` and persistent posterior services | `CURRENT_CANONICAL_AUTHORITY` | Preserve posterior math, events, revisions, proxies, fingerprints, uncertainty, and raw evidence. |
| H | Strength volume/intensity/efficiency presentation | Yes. | Strength detail UI and Lab metric registry | `StrengthAndMuscleMetricSeriesBuilder` and exact load policies cover part of volume; remaining intensity/efficiency semantics need explicit verification | `REPLACEMENT_EXISTS_NOT_WIRED` | Characterize current outputs and identify the exact missing providers; do not invent a new efficiency formula. |
| I | `DailyAnalysisLoadAggregator` eleven-category and body-part loads | Yes today; it drives readiness and weekly fatigue trends. | `TodayReadinessEngine`, `PerformanceTrendEngine`, readiness tests | Canonical `DailyFatigueCalculator` for OFI load; local body-part replacement is unresolved | `REPLACEMENT_REQUIRED` | Canonical fatigue adapter, projected-fatigue parity, custom-exercise handling, and local presentation replacement/retention must all be resolved first. |
| J | `TodayReadinessEngine` | Yes as an interpreter of sleep, subjective recovery, pain, performance drop, and coaching state. It must stop owning duplicate fatigue arithmetic later. | `TodayStatusSummaryService`, `PhaseAwareTodayStatusBuilder`, Home/Coach UI | Future canonical readiness-fatigue adapter backed by `DailyFatigueCalculator` | `REPLACEMENT_REQUIRED` | Preserve interpreter capabilities while replacing only its load input. |
| K | `DailyFatigueCalculator` and five OFI axes | Yes, current canonical fatigue authority. | Home, analysis, history, projection, tests | Existing canonical OFI profiles and calculator | `CURRENT_CANONICAL_AUTHORITY` | Preserve axes, thresholds, baselines, decay, classifier, projection, history, and governed custom behavior. |
| L | Local-muscle/body-part readiness presentation | Yes; it remains visible in fatigue detail and weekly fatigue output. | Readiness detail builders, fatigue pressure, trend charts | No approved physiology-equivalent replacement proved | `BLOCKED` | Requires an approved canonical presentation contract or exact-parity adapter. Do not silently remove or invent math. |
| M | `ExerciseAnalysisMapper` / `AnalysisExerciseFeatures` universal bridge | Yes for current readiness, strength, badminton, and performance-drop consumers. | `AnalysisFeatureExtractor`, `DailyAnalysisLoadAggregator`, `PerformanceDropDetector`, both legacy trend calculators | Domain-specific core, badminton, OFI, muscle, tissue, and strength inputs | `REPLACEMENT_REQUIRED` | Late-stage removal only after each consumer uses its own typed owner. Do not create a V2 feature bundle. |
| N | Legacy semantic columns on `Exercise` | Many still have runtime readers/writers and carry unresolved information. | Mapper, readiness, trend calculators, metadata editor/backup paths, ProgramBuilder | Domain-specific normalized authorities, where already approved | `BLOCKED` | Per-field zero-consumer and information-preservation proof is required before any Room change. |
| O | Legacy backup/import parsers and stableKey mappings | Yes for historical compatibility. | Backup/restore services and tests | Compatibility boundary mapping into current stableKey/raw-record model | `COMPATIBILITY_ONLY_KEEP` | Keep old DTO fields/readers as needed; do not expose them as current analysis authority. |
| P | Room migrations and schema snapshots | Yes for database upgrade compatibility. | `TrainingDatabase` migrations and migration tests | Compatibility boundary only | `COMPATIBILITY_ONLY_KEEP` | No schema deletion until runtime cutover, information audit, and real migration fixtures pass. |
| Q | Canonical metadata authority assets | Yes, frozen semantic input. | Canonical metadata repository and all domain calculators | Files fingerprinted above | `CURRENT_CANONICAL_AUTHORITY` | Relation sets and checksums must remain unchanged throughout architecture-only phases. |
| R | Historical generated metadata/audit references to retired runtime paths | Historical context can remain, but must not claim current runtime authority. | Documentation only | This audit plus current protocol registry | `HISTORICAL_DOCUMENTATION_ONLY` | Update current canonical docs when a path retires; keep historical release notes immutable. |

## Current discrepancies and blockers

- `STRENGTH_VOLUME_CALCULATION.md` and its registry entry still list `CommonStrengthMetrics` as a current implementation anchor, although that result is only created inside the log-only V3 island and is not consumed by the current strength UI or Lab. This is a documentation/runtime path mismatch and may be corrected mechanically when the island is retired; it is not an exercise-semantic decision.
- The current badminton volume protocol explicitly points to `BadmintonTrainingLoadIndexCalculator`. Its practice formula must be audited before a replacement is introduced. Phase 2 is therefore not yet ready for deletion.
- Local body-part fatigue still carries a user-facing capability without a proved canonical replacement. It remains `BLOCKED` rather than being zeroed or removed.
- Strength efficiency has no identified approved canonical replacement. It remains `REPLACEMENT_REQUIRED`.
- Current Room semantic columns have live consumers and were not audited for information-preserving collapse. Schema work is out of scope until those gates are met.

## Phase 1 deletion proof

The V3 island is eligible for removal because:

1. `AnalysisEngineV3` is instantiated only in `TrainingRepository.logDebugSummary()` under `FLAG_DEBUGGABLE`.
2. Its output is not returned, persisted, exported, restored, displayed, or registered in Analysis Lab.
3. The only logged values are diagnostic counts/candidates; no user-visible or historical capability depends on them.
4. `AnalysisMethodRegistry` has no enabled implementation.
5. `LegacyBadmintonContractOracle` has no caller and does not own canonical badminton authority.
6. `SystemAnalysisDateProvider` remains outside the deletion closure because current services use it.
7. Frozen semantic assets are not inputs being rewritten by this deletion.

Verification gates are the focused canonical-authority test, Kotlin compilation, the new retirement architecture guard, the full unit suite, Android-test compilation, and debug assembly.
