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
| `app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv` | 257 | `204e74ae94f038842d9887145edbfa173616cd19f914c99ded4fe0a0ba3d7e43` |
| `app/src/main/assets/metadata/canonical_v1/runtime_metadata.csv` | 257 | `781a2b0acd2df110fe859e1306b0030d73017c10a3868fcf814127c6b5500bb0` |
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
| A | `AnalysisEngineV3`, `AnalysisDashboardV3Result`, `AnalysisInputCollector`, `AnalysisInputSnapshot`, `Common*Metrics`, disabled method registry, sentence scaffolding | No product, UI, Lab, persistence, or compatibility capability. A debuggable startup path computed a log-only summary; most outputs were discarded. | Formerly `TrainingRepository.logDebugSummary()` only; no UI/Lab/public repository/backup/Room consumer | None required | `DEAD_NO_CAPABILITY`, retired in `abe140a` | Debug invocation and closed dependency island removed; compile and architecture guard passed. |
| B | `LegacyBadmintonContractOracle` | No. It was an unreferenced test helper and was not an authority asset or compatibility parser. | Declaration only | None required | `DEAD_NO_CAPABILITY`, retired in `abe140a` | Removed after repository-wide reference proof; architecture guard passed. |
| C | `BadmintonTrainingLoadIndexCalculator` legacy court/footwork/support composite | Yes. Current practice-load trend, method examples, detail charts, and Lab series depend on it. | `PerformanceTrendEngine`, `PerformanceChartSpecBuilder`, analysis UI, Analysis Lab, tests | Future explicit `BadmintonPracticeCatalog` + `BadmintonPracticeLoadCalculator`; existing `BadmintonObjectiveStimulusCalculator` remains separate | `PRACTICE_CONTRACT_READY`; composite remains `REPLACEMENT_REQUIRED` | Phase 2A/2B characterized exact practice admission and arithmetic. Implement the practice owner with exact parity, migrate every consumer and saved metric boundary, then retire legacy footwork/support/composite fields. |
| D | Nine-objective badminton stimulus | Yes, current canonical capability. | Trend engine, badminton screens, canonical repository, tests | `BadmintonObjectiveStimulusCalculator` + `CanonicalBadmintonObjectiveCatalog` | `CURRENT_CANONICAL_AUTHORITY` | Preserve all 280 explicit relations and all nine objectives. No migration in this phase. |
| E | `BADMINTON_TRAINING`, `COURT_VOLUME`, `FOOTWORK_REACTIVE`, `BADMINTON_SUPPORT`, `BadmintonWeekIndex` composite fields | Yes until semantically equivalent current metrics are registered and saved selector state is handled. `COURT_VOLUME` currently standardizes the governed practice raw value, while the other IDs retain legacy composite meanings. | Analysis detail UI, Analysis Lab registry/pipeline, chart builders, trend tests | Explicit raw/daily/weekly practice metric plus existing objective-specific metrics, only where consumed | `REPLACEMENT_REQUIRED`; not ready for deletion | Never relabel an old metric ID. Add the practice provider, define standardization/selector compatibility, migrate UI and Lab consumers, and provide saved-state fallback before removal. |
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

- The stale `CommonStrengthMetrics` implementation anchor in `STRENGTH_VOLUME_CALCULATION.md` and the protocol registry was removed with the Phase 1 documentation closeout. Current strength UI and Lab owners are unchanged.
- The current badminton volume protocol explicitly points to `BadmintonTrainingLoadIndexCalculator`. Its practice formula must be audited before a replacement is introduced. Phase 2 is therefore not yet ready for deletion.
- Local body-part fatigue still carries a user-facing capability without a proved canonical replacement. It remains `BLOCKED` rather than being zeroed or removed.
- Strength efficiency has no identified approved canonical replacement. It remains `REPLACEMENT_REQUIRED`.
- Current Room semantic columns have live consumers and were not audited for information-preserving collapse. Schema work is out of scope until those gates are met.

## Phase 2A/2B badminton practice-load contract audit

Phase 2A/2B started from `664c670c8daf7a6118850626823ddf424bcb3808`
with version `0.5.0.37 / 500037` and a clean worktree.

### Canonical identity snapshot and admission paths

[`badminton_practice_admission_set_matrix.csv`](badminton_practice_admission_set_matrix.csv)
materializes every rule below for all 257 current canonical identities. It has
257 data rows and SHA-256
`74614924edb849a794e35de10888d9e094a094156f1c033a453be8e9ce9e4fd5`.
Every `TRUE` row carries the exact stableKey, canonical display name, and the
metadata fields that caused admission. Display names are review labels only;
no admission rule uses them.

| Set | Production owner and rule | Count | Authority character |
|---|---|---:|---|
| A | `BadmintonTrainingLoadIndexCalculator.courtVolumeRaw`: resolved `activityKind == SPORT_SESSION` and exact stableKey in `{ex_ae9ecdbc, ex_badminton_lesson}` | 2 | Explicit identity allowlist plus resolved activity kind |
| B | `CourtDurationRecoveryAnalyzer`: resolved activity is `SPORT_SESSION` or `MATCH_RECORD`, and transfer is `DIRECT` or context contains `BADMINTON`/`COURT` | 2 | Inferred effective-runtime-metadata predicate |
| C | Legacy `footworkReactiveRaw`: not Set A and a configured court movement or legacy transfer role matches its hard-coded reactive lists | 48 | Legacy `Exercise` metadata inference; not canonical objective authority |
| D | Legacy `supportRaw`: not Set A and resolved `badmintonTransferLevel` is `DIRECT`, `SUPPORTIVE`, or `GENERAL` | 222 | Broad effective-runtime-metadata inference |
| E | `BadmintonObjectiveStimulusCalculator`: an explicit objective relation exists and the `Exercise` is not `SPORT_SESSION` | 102 | Explicit 280-row canonical nine-objective authority; 104 related identities minus two sport sessions |
| F | `DailyAnalysisLoadAggregator` yields a positive legacy `BADMINTON_COURT` category for a representative positive base dose when any speed/deceleration/elastic/overhead/grip weight or transfer bonus is positive | 243 | Broad readiness-fatigue feature composition; not practice duration or recovery court exposure |

Sets A and B are currently identical:

| stableKey | Canonical name | activityKind | transfer | sport context |
|---|---|---|---|---|
| `ex_ae9ecdbc` | 배드민턴 | `SPORT_SESSION` | `DIRECT` | `BADMINTON_MATCH`, `BADMINTON_RALLY`, `BADMINTON_DIRECT_TRANSFER` |
| `ex_badminton_lesson` | 배드민턴 레슨 | `SPORT_SESSION` | `DIRECT` | `BADMINTON_LESSON`, `BADMINTON_DIRECT_TRANSFER` |

The important set comparisons are:

| Pair | Intersection | Only first | Only second |
|---|---:|---:|---:|
| A / B | 2 | 0 | 0 |
| A / C | 0 | 2 | 48 |
| A / D | 0 | 2 | 222 |
| A / E | 0 | 2 | 102 |
| A / F | 2 | 0 | 241 |
| C / D | 45 | 3 | 177 |
| C / E | 43 | 5 | 59 |
| C / F | 48 | 0 | 195 |
| D / E | 102 | 120 | 0 |
| D / F | 222 | 0 | 21 |
| E / F | 102 | 0 | 141 |

The full exact memberships and names are in the matrix rather than repeated in
this document. The informative differences include `농구`, `축구`, and
`러닝 풋살` in C but not D; C also includes those three plus `원레그 레그 컬`
and `힙 어덕션` without a canonical objective relation. These results prove
that the legacy footwork, support, objective, and readiness categories are not
aliases for practice.

### Practice versus recovery court exposure

The semantic result is **C. DISTINCT_CONCEPTS**, with currently identical
canonical stableKey sets.

- Practice load is an RPE-adjusted training dose owned by the exact two-key
  shuttle-play allowlist.
- Recovery court exposure is unadjusted court minutes paired to next-day
  check-in/fatigue data. Its metadata predicate also accepts `MATCH_RECORD`.
- A characterization fixture shows that a metadata-valid synthetic
  `MATCH_RECORD` is admitted by recovery exposure but not by the practice
  allowlist. It is an implementation-boundary fixture, not a new canonical
  identity or product approval.
- Current canonical Set A and Set B happen to coincide; that coincidence does
  not transfer authority from one owner to the other.

No display-name fallback, broad `SPORT_SESSION + DIRECT` practice inference,
or semantic metadata reclassification is approved by this audit.

### Exact current practice arithmetic

For an admitted record, the current raw practice value is:

`sum(confirmed set seconds) / 60 * badmintonIntensityFactor(averageRpe)`

| Contract part | Exact current behavior | Classification |
|---|---|---|
| Admission | Resolved `SPORT_SESSION` plus exact key `ex_ae9ecdbc` or `ex_badminton_lesson` | `GOVERNED_PRODUCT_SEMANTIC` for current canonical identities |
| Confirmation | Only confirmed sets contribute duration or set RPE; an all-unconfirmed record contributes zero | `GOVERNED_PRODUCT_SEMANTIC` |
| Duration | Sum confirmed `seconds`, then divide by 60; repetitions and load do not provide a practice fallback | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE source | Average all non-null RPE values on confirmed sets; use `WorkoutEntry.rpe` only when no confirmed set supplies RPE | `GOVERNED_PRODUCT_SEMANTIC` |
| Null RPE | Multiplier `1.00` | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE `<= 6.0` | Multiplier `0.90` | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE `> 6.0 && < 8.0` | Multiplier `1.00` | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE `>= 8.0 && < 9.0` | Multiplier `1.05` | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE `>= 9.0 && < 10.0` | Multiplier `1.10` | `GOVERNED_PRODUCT_SEMANTIC` |
| RPE `>= 10.0` | Multiplier `1.15` | `GOVERNED_PRODUCT_SEMANTIC` |
| Same date | Parseable ISO dates are grouped and all admitted record doses are summed | `GOVERNED_PRODUCT_SEMANTIC` |
| Invalid date | `dailyLoads` silently drops an unparseable date | `CURRENT_IMPLEMENTATION_DETAIL` |
| Weekly raw | Sum records already placed in each caller-supplied `WeeklyTrainingData` bucket; the calculator does not rebucket dates | `GOVERNED_PRODUCT_SEMANTIC` at the supplied-bucket boundary |
| Weekly standardization | Historical-baseline standardization into `courtVolumeIndex` | `LEGACY_COMPOSITE_ONLY` until the live metric migration defines its replacement boundary |
| Zero seconds | Zero practice load and no practice-only daily point | `GOVERNED_PRODUCT_SEMANTIC` |
| Negative seconds | Nonpositive aggregate is collapsed to zero | `CURRENT_IMPLEMENTATION_DETAIL`; invalid input must not become future authority |
| Legacy footwork/support and 0.60/0.25/0.15 `trainingIndex` | Separate inferred components and composite standardization | `LEGACY_COMPOSITE_ONLY` |

### Nine-objective separation and frozen sentinels

Practice load is not one of the nine objectives and is not added to objective
stimulus as a total. The objective calculator skips `SPORT_SESSION` records,
so Sets A and E are disjoint at runtime even though the two practice identities
retain explicit authority rows. The 280 objective rows and all coefficients are
unchanged. `band_pallof_press` and `cable_pallof_press` retain their separately
reviewed `ANTI_ROTATION / SUPPORTIVE` relations, and Copenhagen plank retains
its baseline relations.

### Future replacement boundary

The narrowest future authority is an explicit `BadmintonPracticeCatalog` with
exactly the two current canonical practice stableKeys. No relation enum is
needed yet because the governed arithmetic does not distinguish practice from
lesson. It must not reuse the broader recovery-court metadata predicate.

A future `BadmintonPracticeLoadCalculator` needs only stableKey, date,
confirmed seconds, non-null confirmed-set RPE values, and entry RPE fallback.
Daily grouping and externally supplied week grouping may be adapters around
that calculator. The characterization suite is the exact-parity gate for the
practice capability; legacy footwork/support arithmetic is intentionally not
blessed as the replacement model.

The practice capability is **READY_FOR_REPLACEMENT_IMPLEMENTATION**. The live
`BadmintonTrainingLoadIndexCalculator` is **not ready for deletion** because
`PerformanceTrendEngine`, chart builders, Analysis detail UI, Analysis Lab,
metric IDs, saved selector behavior, and the legacy composite fields still
consume it. No consumer cutover occurs in Phase 2A/2B.

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

## Completed Phase 1 boundary

- `f725d7f` created this capability matrix and froze canonical authority fingerprints before deletion.
- `abe140a` removed only the proven `DEAD_NO_CAPABILITY` V3 island and the unreferenced legacy badminton test oracle.
- `fd3fe5a` added an architecture guard preventing those retired runtime symbols from returning.
- The current-code compatibility-only inventory was regenerated from 74 to 71 rows; the three removed rows were the deleted V3 `progressMetricType` references.
- No replacement calculator was introduced because the retired island exposed no current capability.
- Phases 2 and later were not started. Rows C through P remain live, compatibility-only, replacement-required, or blocked exactly as recorded in the matrix.

Final Phase 1 verification passed on 2026-08-16:

- focused `AnalysisContractAuditArtifactsTest` and canonical-authority coverage;
- full `:app:testDebugUnitTest`;
- `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin`;
- `:app:assembleDebug` and `:app:validateConnectiveTissuePriorBaselines`;
- protocol registry validation, metadata authority workbook validation, deterministic canonical export, and metadata authority Python tests.

The frozen authority files above retain their baseline row counts and SHA-256 fingerprints. No exercise-level semantic authority asset changed.
