# Phase C4B-1 Continuous Axis Scoring and First M/T/C Research Batch

Completion status: `C4B1_CONTINUOUS_AXIS_SCORING_AND_FIRST_RESEARCH_BATCH_PARTIAL`

This package is a draft research layer. It is not a human approval, a formal final claim, a production profile, a runtime coefficient activation, or a request to recalculate historical sessions.

## Baseline and boundary

- Baseline: `a714bb936405fd022c050b24511b8684653a34c7` on current `main`.
- C4A artifacts remain immutable. Catalog inconsistencies are represented by the C4B1 correction overlay rather than rewriting C4A source snapshots.
- New coefficient set: `TISSUE_MTC_C4B1_0_2_0`, semantic version `0.2.0`, status `DRAFT_NON_PRODUCTION`.
- Runtime default: unchanged. All 1,134 operational traces still resolve with `TISSUE_MTC_C4A_0_1_1`.
- Historical session recalculation: false.
- Human approvals, formal final claims, blind reviews, and production profiles: zero.
- App version and release tags: unchanged.
- The six pre-existing dirty `outputs/*` files were not read as research input, rewritten, staged, or committed.

## Recounted C4A inventory

- Canonical exercises: 239.
- Lower-limb applicable exercises: 87.
- Exercise-complex relationships: 378.
- Operational M/T/C traces: 1,134.
- Effective C4A exact stable-key overrides: 0.
- Effective C4A variant profiles: 0.
- Movement-family profile rows: 249.
- Research seeds: 130, comprising 97 bibliographically verified, 3 metric-extracted, and 30 unverified rows.
- Existing C4A metric extractions: 49.
- Existing C4A source conditions: 49.
- Existing coefficient sets before C4B1: 3.
- Human approvals, final claims, blind reviews, and production profiles: 0.

## C4A semantic correction

The 130-row C4A seed catalog is preserved byte-for-byte. The correction overlay contains 43 typed rows and makes the effective C4B1 view fail closed:

- `METRIC_EXTRACTED` cannot coexist with an unresolved extraction status.
- Modeled internal force or contact-force evidence is no longer represented as `CONTEXT_ONLY` solely because it is model-derived.
- Unresolved model validation uses `INTERNAL_MODEL_VALIDATION_PENDING` and remains research-ineligible.
- The three metric-extracted leads have explicit effective evidence semantics: Achilles modeled strain, tibiofemoral intersegmental resultant as an unvalidated proxy, and PCL modeled force.
- Publication-integrity blockers prevent score generation.

## Continuous scoring contract

- Supported axes: mechanical `M/T/C` and optional dynamic-stabilization `D/R/P`.
- Score domain: `0.0000` through `4.0000`; internal calculations retain full precision and persisted values retain at least four decimals.
- `UNKNOWN` is null and never becomes `0.0000`.
- Confidence is axis-specific metadata and never alters score magnitude.
- Cross-axis and whole-vector averaging are forbidden.
- Raw force, stress, strain, impulse, and loading-rate values remain separate physical observations.
- Metric-specific calibration occurs before any same-axis source combination.
- C remains ordering-only in this first batch because no defensible scalar C calibration was established.

## Rubrics, interpolation, and range behavior

- Metric-specific rubrics: 9.
- Anchors: 36, four per rubric.
- Interpolation: monotone `PIECEWISE_LINEAR` for all nine first-batch rubrics.
- Stored condition-axis scores: 69.
- Within calibration range: 68.
- Boundary clamped: 1.
- Below- or above-range unclamped records: 0.
- Injury, rupture, pain, pathological, cadaveric-failure, and tissue-failure thresholds are excluded from ordinary score-4 anchors.

The only clamped score is retained with its unclamped value and `BOUNDARY_CLAMPED` status. No silent extrapolation becomes a fallback.

## Similarity and aggregation

- Similarity policy version: `1.0.0`.
- Aggregation policy version: `1.0.0`.
- Minimum eligible similarity: `0.60`.
- Dominant-source threshold: `0.85` with a minimum `0.15` margin.
- Maximum weighted standard deviation: `0.50`.
- Maximum score range: `1.25`.
- Maximum subgroup mean difference: `0.75`.

M, T, C, D, R, and P use distinct versioned similarity weights. Source selection uses protocol, movement, population, and method similarity, never the resulting tissue score.

Aggregation decisions:

- Exact single-condition: 19.
- Within-condition primary metric: 23.
- Ordering-rule-only C records: 14.
- Multi-source axis aggregates: 0 in this first batch.
- Exclusions: 27 duplicate-condition metrics retained only as supporting evidence and 6 observations outside a defensible calibration domain.

Each same-condition multi-metric group contributes one evidence unit. The primary metric supplies the condition-axis score; secondary metrics remain traceable corroboration without duplicate weight. All 25 source-condition dependency rows are assigned to one of four cohorts.

## First research batch

Four full-text primary studies were reviewed:

- Achilles heel-raise loading, PMID 28145739: https://pmc.ncbi.nlm.nih.gov/articles/PMC5343533/
- Patellar-tendon exercise progression, PMID 37847102: https://pmc.ncbi.nlm.nih.gov/articles/PMC10925836/
- Patellofemoral loading progression, PMID 37272685: https://pmc.ncbi.nlm.nih.gov/articles/PMC10315869/
- Patellar-tendon lunge variants, PMID 31193251: https://pmc.ncbi.nlm.nih.gov/articles/PMC6523035/

The package contains 25 exact source conditions, 89 raw axis observations, 69 calibrated source-condition scores, 56 aggregation decisions, and 33 explicit exclusions.

### Achilles tendon

- Sources reviewed: 1 full text.
- Conditions: 4 heel-raise protocols.
- Observations: 16; calibrated scores: 12.
- M: calibrated from strain as primary with force and stress as same-condition support.
- T: unresolved in the reviewed source.
- C: structured ordering context only, no scalar.
- Rubrics/anchors: 3/12.
- Exact draft axes: 2, for standing bilateral and unilateral bodyweight heel raise.

### Patellar tendon

- Sources reviewed: 2 full texts.
- Conditions: 11.
- Observations: 39; calibrated scores: 27.
- M: peak modeled tendon force.
- T: impulse primary, loading rate supporting.
- C: structured ordering context only, no scalar.
- Rubrics/anchors: 3/12.
- Exact draft axes: 4 for full-depth bodyweight squat and bodyweight Bulgarian squat.
- Variant draft axes: 2 for the study-defined bodyweight forward non-jumping lunge.
- PMID 31193251 stress, impulse, and loading-rate observations remain raw-only because two variants do not establish a defensible four-anchor metric rubric.

### Patellofemoral joint

- Sources reviewed: 1 full text.
- Conditions: 10.
- Observations: 34; calibrated scores: 30.
- M: peak modeled PFJ contact force.
- T: impulse primary, loading rate supporting.
- C: structured ordering context only, no scalar.
- Rubrics/anchors: 3/12.
- Exact draft axes: 4 for full-depth bodyweight squat and bodyweight Bulgarian squat.
- Variant draft axes: 2 for the study-defined bodyweight forward non-jumping lunge.

### Talocrural joint, tibiofemoral joint, and quadriceps tendon

No scalar C4B1 score is published. Each M/T/C gap remains explicit and retains its existing operational fallback. Patellar-tendon values are not copied to quadriceps tendon; PFJ values are not copied to TFJ; complex values are not copied to child tissues.

## Exercise-catalog publication

- Canonical research score rows: 14.
- Evidence-backed exact condition axes: 10 across `ex_bd072cd`, `ex_5ca7133f`, `ex_cb3c4dc2`, and `ex_e2efd0fe`.
- Evidence-backed close-variant axes: 4 for `ex_64644b5e` under the narrow bodyweight forward non-jumping lunge selector.
- Anchor-only variant scores: 4.
- Movement-family profile rows remaining: 249.
- Operational movement-family fallback traces remaining: 1,134.
- Conservative fallback traces in the current lower-limb operational snapshot: 0.

The draft exact and variant rows carry `runtimeEligible=false`. They do not replace the runtime operational score. The machine-readable C4A-to-C4B1 diff records every new research axis as `UNCHANGED` for the operational fallback and `NOT_ACTIVATED` for runtime.

## Coefficient-set identity

- Coefficient set: `TISSUE_MTC_C4B1_0_2_0`.
- Semantic version: `0.2.0`.
- Supersedes research lineage: `TISSUE_MTC_C4A_0_1_2`.
- Source snapshot hash: `25b2f2ffab3f0448a44cc73f3070d1fa7a72790240be72603adaba43f68f7f05`.
- Rubric snapshot hash: `77480d8c981e848082b114e3b98d93d25097afb43441f2b63c31f6c3736bf9c3`.
- Scoring-policy hash: `171db69d5eef2d26c627ee5508a70ea59fa2794205680266445874a986bd4a94`.
- Exercise-catalog snapshot hash: `e622b171dfea8d3440d9cb9e6b415dd3149f5bce751211943666864699081b4a`.
- Immutable C4A coefficient bundle semantic hash: `733ca3f5116db345023d7ae45c2d1ca3cf0bfebef2eebecf983fd3be73c074d1`.

Semantic CSV hashes include the file name and header, sort data rows ordinally, remove an optional UTF-8 BOM, and normalize CRLF/CR to LF. Row order, BOM, and line endings therefore do not change identity; any field change does.

Source snapshot files are the C4B1 correction overlay, source-condition registry, observation and score registries, dependency registry, aggregation decisions and exclusions, research decisions, and gap matrix. The rubric snapshot contains the rubric and anchor registries. The scoring-policy hash contains the axis scoring policy registry. Similarity and aggregation policies are separately versioned.

## File / feature map

- `TissueMtcParser.kt`, `TissueMtcResearchCatalog.kt`: effective C4B1 correction overlay parsing and fail-closed catalog validation.
- `tissue_mtc_seed_semantic_correction_c4b1_v1.csv`: immutable C4A semantic corrections.
- `TissueAxisScoringModels.kt`: six-axis score, evidence-key, provenance, confidence, and selection contracts.
- `TissueAxisSimilarityPolicy.kt`: axis-specific similarity and dominant-source rules.
- `tissue_axis_scoring_policy_v1.csv`: score bounds, precision, null, confidence, and cross-axis policy.
- `tissue_axis_similarity_policy_v1.csv`: six independent similarity weight sets.
- `TissueContinuousAxisScoring.kt`: monotone scoring, explicit clamping, source selection, dependency-aware aggregation, and heterogeneity gates.
- `tissue_metric_continuous_rubric_c4b1_v1.csv`, `tissue_rubric_anchor_c4b1_v1.csv`: nine metric-specific scales and 36 anchors.
- `tissue_source_condition_registry_c4b1_v1.csv`: 25 exact protocol identities.
- `tissue_source_condition_axis_observation_c4b1_v1.csv`: 89 raw M/T/C observations and structured context.
- `tissue_source_condition_axis_score_c4b1_v1.csv`: 69 reproducible metric-calibrated scores.
- `tissue_source_dependency_registry_v1.csv`: cohort/dependency identity.
- `tissue_axis_aggregation_decision_c4b1_v1.csv`, `tissue_axis_aggregation_exclusion_c4b1_v1.csv`: selected evidence, supporting metrics, exclusions, and heterogeneity status.
- `tissue_first_batch_research_decision_c4b1_v1.csv`, `tissue_first_batch_research_gap_matrix_c4b1_v1.csv`: target-level completion and unresolved gaps.
- `tissue_canonical_axis_score_c4b1_v1.csv`: 14 condition-bounded canonical research results with complete lineage.
- `tissue_mtc_exact_stable_key_override_c4b1_v1.csv`: 10 exact-protocol draft mappings.
- `tissue_mtc_variant_profile_c4b1_v1.csv`: 4 narrow lunge variant mappings.
- `tissue_mtc_coefficient_diff_c4a_to_c4b1_v1.csv`: machine-readable C4A-to-C4B1 research-axis diff.
- `tissue_mtc_coefficient_set_manifest_c4b1_v1.csv`: immutable draft identity, hashes, counts, and activation boundary.
- `TissueC4B1CatalogCorrectionTest.kt`, `TissueContinuousAxisContractsTest.kt`, `TissueContinuousAxisScoringTest.kt`, `TissueC4B1ResearchBatchTest.kt`, `TissueC4B1CoefficientSetTest.kt`: correction, scoring, research, publication, hash, and runtime-boundary gates.

## Validation and remaining gaps

Focused C4A/C4B1 catalog, contract, scoring, research-batch, and coefficient-set tests pass. `:app:compileDebugKotlin` passed, the full unit rerun completed 791 tests with zero failures/errors/skips, `:app:assembleDebug` produced the debug APK, and the diff check passed. Push and CI are recorded in the completion report after execution.

The status remains `PARTIAL` because the priority queue was intentionally not inflated with inaccessible or incompatible evidence. Achilles T, scalar C, talocrural M/T/C, TFJ M/T/C, quadriceps-tendon M/T/C, loaded exercise variants, additional independent cohorts, and several queued primary studies remain unresolved. A later C4B-2 batch should add sources only when exact condition identity, metric calibration, source independence, and transfer boundaries are defensible.
