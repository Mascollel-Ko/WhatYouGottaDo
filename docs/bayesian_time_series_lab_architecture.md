# Bayesian Time-Series Lab Architecture

## Status

PHASE A is preparation-only. It creates immutable data, identity, row, scaling, response-scale, and future estimator-input contracts. It does not run a strict BVAR posterior, Bayesian Local Projection, Johansen rank analysis, Bayesian VECM, or automatic endogenous-variable ranking.

The existing app-visible dynamic analysis is compatibility behavior. Its entry point is explicitly named `LegacyTimeSeriesAnalyzer`; its result is not a `StrictPreparationResult` and cannot enter the strict package.

## Strict Pipeline Graph

```text
RawTimeSeriesInput
  -> StrictTimeSeriesPreparationPipeline
  -> CanonicalCalendar
  -> LifecycleValidatedLevelCatalog / LifecycleValidatedLevelSeries
  -> ContiguousUsableSegment
  -> IntegrationOrderAssessment
  -> CanonicalTransformationPlan
  -> TransformedPreparedCatalog / TransformedPreparedSeries
  -> EstimatorRepresentationPlan
  -> ResponseScalePlan
  -> PreparedCandidateCatalog
  -> PreparedAnalysisContext
  -> PreparedEstimatorView
  -> PreparedRowPlan
  -> PreparedScalingPlan
  -> future PHASE B/C/D estimator boundary
```

No stage may recreate an earlier stage. Raw observations terminate in `StrictTimeSeriesIngestion.kt`; all later functions accept stage-specific prepared types.

## Legacy Pipeline Graph

```text
AnalysisLabUi / LaggedTimeSeriesAnalyzer compatibility facade
  -> LegacyTimeSeriesAnalyzer
  -> TimeSeriesAlignmentService
  -> EndogenousVariableSelector
  -> legacy LP / BVAR / VECM / cointegration compatibility helpers
  -> BayesianTimeSeriesResult (legacy compatibility result)
```

The legacy graph may remain app-visible while PHASE B-E are incomplete. It cannot construct strict internal types, influence strict preparation readiness, or label its output as strict preparation or a completed strict Bayesian posterior.

## Package Dependency Rules

- `analysis.lab.pipeline` may import immutable trend metric definitions, raw `TrendDataPoint` only in `StrictTimeSeriesIngestion.kt`, the existing `TimeSeriesAlignmentService` resolver output only at that ingestion boundary, Java/Kotlin standard library types, and its own preparation contracts.
- `analysis.lab.pipeline` must not import `LegacyTimeSeriesAnalyzer`, legacy LP/BVAR/VECM estimators, `CointegrationAnalyzer`, or `EndogenousVariableSelector`.
- Legacy code must not import or instantiate strict pipeline internals.
- Strict result types and legacy result types are distinct.
- `tools/check_time_series_numeric_sources.py` enforces the package boundary and known semantic bypass patterns.

## PHASE Ownership

| Phase | Owns | Explicitly does not own |
|---|---|---|
| A | canonical ingestion, lifecycle validation, contiguous segments, integration assessments, transformation and representation plans, prepared context/views, rows, scaling, response scale, future shock contract, candidate eligibility | posterior estimation or statistical candidate ranking |
| B | NIW/Matrix-Normal Inverse-Wishart BVAR, Minnesota prior, lag/lambda posterior, covariance draws, structural shock identification | raw ingestion, rows, scaling |
| C | Bayesian Local Projection, AR(q) residual model, draw-wise shock propagation, posterior IRF mixture, response reconstruction | implicit shock differencing or UI-defined scales |
| D | Johansen diagnostics, rank posterior, Bayesian VECM | regenerating level/difference pairs |
| E | automatic endogenous-variable statistical ranking, final model comparison, UI labels/results | changing PHASE A preparation identities |

Until PHASE E, strict preparation uses only explicitly required X, Y, and Z. Optional metrics receive eligibility/exclusion diagnostics but no predictive score and no selected set.

## Stage-Type Map

| Stage | Type | Identity source |
|---|---|---|
| raw boundary | `RawTimeSeriesInput` | validated private raw copy; no downstream exposure |
| calendar | `CanonicalCalendar` | complete ordered ISO-Monday week vector |
| lifecycle level | `LifecycleValidatedLevelSeries` | calendar, cells, normalized lifecycle metadata |
| segment | `ContiguousUsableSegment` | exact metric/week/value sequence |
| integration | `IntegrationOrderAssessment` | every segment diagnostic and source level fingerprint |
| transformation | `CanonicalTransformationDecision`, `CanonicalTransformationPlan` | assessment identities and policy |
| transformed | `TransformedPreparedSeries` | level source identity and one canonical decision |
| representation | `EstimatorRepresentationDecision`, `EstimatorRepresentationPlan` | transformation and assessment identities |
| response scale | `ResponseScalePlan` | response transformation and draw-wise inversion policy |
| candidates | `PreparedCandidateCatalog` | eligibility diagnostics and prepared series |
| root | `PreparedAnalysisContext` | all preceding stage identities and request |
| view | `BvarPreparedView`, `BlpPreparedView`, `JohansenPreparedView`, `VecmPreparedView`, `CandidateEligibilityView` | root identity, purpose, metrics, representation |
| rows | `PreparedRowSpecification`, `PreparedRowPlan` | roles, lag, horizon set/policy, purpose, view |
| scaling | `PreparedScalingPlan` | declared training rows, view, row plan, statistics |
| future BVAR posterior identity | `BvarPosteriorSourceIdentity` | source metric, BVAR view, row, scaling, prior, posterior, and eligible source-week identities |
| future shock | `IdentifiedShockPosterior` | draw IDs, weights, prepared-row shocks, covariance fingerprints, and BVAR posterior source identity |

Identity-bearing classes use private constructors, defensive copies, validated factories, and internally computed SHA-256 fingerprints. They are not public data classes, so `copy()` cannot retain stale identity.

## PreparedAnalysisContext Schema

`PreparedAnalysisContext` contains:

- the normalized request;
- one `CanonicalCalendar`;
- lifecycle metadata and validated level series for every requested metric;
- exact contiguous segments and one integration assessment per metric;
- one canonical transformation plan and transformed series;
- one estimator representation plan;
- one response-scale plan per Y;
- one optional candidate eligibility catalog;
- the conservative preparation policy;
- readiness diagnostics stating that no estimator has run;
- one root fingerprint shared by all views, row plans, and scaling plans.

It does not contain posterior draws, IRFs, rank results, a VECM result, or statistically selected optional endogenous metrics.

## Single-Authority Table

`yes` is permitted only in the named authority. A blank/no entry is forbidden for that responsibility.

| Production function | Accepts | Produces | Calendar | Lifecycle | Diagnostics | Transform | Representation | Rows | Scaling | Shock |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `RawTimeSeriesInput.fromTrendSeries/createValidated` | resolver output or one resolved observation per metric/week | sealed raw input | no | no | no | no | no | no | no | no |
| `RawTimeSeriesInput.ingest` via pipeline | raw input, request | calendar + lifecycle level catalog | yes | yes | no | no | no | no | no | no |
| `SegmentAwareIntegrationAssessmentAuthority.assess` | lifecycle level catalog | segments + assessments | no | no | yes | no | no | no | no | no |
| `CanonicalTransformationAuthority.createPlan` via context | assessments, policy | canonical transformation plan | no | no | no | yes | no | no | no | no |
| `TransformedPreparedCatalog.createValidated` via context | level catalog, transformation plan | transformed prepared catalog | no | no | no | applies fixed plan only | no | no | no | no |
| `EstimatorRepresentationPlan.createValidated` via context | transformation plan, assessments | representation plan | no | no | no | no | yes | no | no | no |
| `ResponseScalePlan.createValidated` via context | response transformation | response-scale plan | no | no | no | no | response interpretation only | no | no | no |
| `PreparedAnalysisContext.createValidated` | level catalog, request, policy | root context | no | validates | orchestrates one authority | orchestrates one authority | orchestrates one authority | no | no | no |
| view factories | root context | read-only estimator view | no | no | no | no | selects fixed representation | no | no | no |
| `RowPlanner.plan` | context, view, roles, lag, horizon policy | row plan | no | no | no | no | no | yes | no | no |
| `ScalingPlanner.plan` | context, view, row plan, training rows | scaling plan | no | no | no | no | no | no | yes | no |
| `BvarPosteriorSourceIdentity.createValidated` | future PHASE B input/posterior identity | validated BVAR posterior source identity | no | no | no | no | no | reads row identity | reads scaling identity | no |
| `IdentifiedShockPosterior.createValidated` | future PHASE B draw output and source identity | validated posterior shock bundle over prepared source weeks | no | no | no | no | no | validates source-week domain | no | validates identity only |

Strict helpers may read their accepted stage but may not change calendar, lifecycle, transformation, representation, rows, scaling, or shocks unless the table grants authority.

## Canonical Calendar And Lifecycle

Raw dates are canonicalized once to ISO Monday. The calendar spans every week between its bounds; no missing week is removed. Missing, pre-creation, not-applicable, version-discontinuity, structural-zero, observed, and conflict cells are explicit.

Lifecycle rules are validated before a level series exists:

- activation and availability bounds are ordered ISO Mondays;
- not-applicable and discontinuity ranges are normalized and non-overlapping;
- structural zero requires permission, known activation, an active week, and exact `0.0`;
- pre-creation is strictly before activation;
- observed/missing active cells cannot occupy prohibited lifecycle ranges;
- conflicts carry all raw provenance and never expose a numeric value.

Restricted estimator views retain the root calendar identity. They do not redefine a shorter pseudo-calendar.

## Segment Diagnostic Policy

`SegmentAwareIntegrationAssessmentAuthority` walks lifecycle-validated cells in calendar order. A segment ends at every missing, pre-creation, not-applicable, version-discontinuity, conflict, activation, or availability break. Separated finite cells are never concatenated.

Each segment preserves exact weeks and values. Segments shorter than 32 rows remain in diagnostics as excluded segments. Eligible segments are diagnosed independently with the declared `statsmodels-adfuller-kpss-c` method/version: constant-only ADF with statsmodels-compatible autolag/AIC and MacKinnon p-value/critical values, plus constant-only KPSS with Hobijn auto lag and interpolated reference p-values. A supported I(0) or I(1) assessment requires every eligible segment to agree. Disagreement, unresolved conflict, or unstable evidence is `INCONCLUSIVE`; no eligible segment is `INSUFFICIENT_CONTIGUOUS_SAMPLE`.

## Inconclusive Policies

- Optional metric default: `EXCLUDE_FROM_ELIGIBLE_CANDIDATES`. Diagnostics and level data remain available; no level or difference is guessed.
- Required X/Y/Z default: `FAIL_STRICT_PREPARATION`. The result is `INCONCLUSIVE_TRANSFORMATION`; no context or model-ready representation is returned.
- Explicit transformation policy can only restate the supported canonical assessment. A mismatch fails with `TRANSFORMATION_ASSESSMENT_CONFLICT`; there is no documented fallback override path.

## Canonical Transformation Authority

Supported I(0) defaults to `LEVEL`; supported I(1) defaults to `FIRST_DIFFERENCE`. Explicit log policies are fingerprinted. The transformer accepts only `LifecycleValidatedLevelSeries`, so transformed series cannot be transformed again through the canonical API. First differences keep the full calendar, mark an unavailable first or boundary-crossing cell as missing, and preserve the source level fingerprint.

No selector, view, row planner, scaler, UI, or future estimator may call an independent difference helper or choose a fallback representation.

## Estimator Representation Plan

- PHASE B BVAR: canonical stationary transformed representation.
- PHASE C BLP response: canonical response representation plus response-scale plan; shocks arrive separately as posterior draws.
- PHASE D Johansen: validated I(1) level representation only.
- PHASE D VECM: validated levels and aligned canonical first differences under the same root context.

Level and transformed series coexist in the root context. Stationarization never discards levels.

## Response-Scale Plan

`ResponseScalePlan` distinguishes level, first-difference, log-level, and log-difference estimation. It defines display scale, identity/cumulative/exponential inversion, baseline requirements, exact inversion availability, interpretation label, and `TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS`. UI code may not invent cumulative or percentage meaning.

## Candidate Eligibility Catalog

`PreparedCandidateCatalog` records eligible and excluded optional metrics using lifecycle validity, contiguous samples, integration status, transformation readiness, and data quality only. It contains no legacy LP/BVAR score and no selected optional set. `CandidateEligibilityView` cannot obtain a PHASE A row-ranking plan.

## Role-Aware RowPlanner

Roles are explicit: `SHOCK_SOURCE`, `ENDOGENOUS_STATE`, `RESPONSE`, `CONTEMPORANEOUS_CONTROL`, and `LAGGED_CONTROL`.

- shock source: source and declared shock-estimation lags, no automatic future target;
- endogenous state: source and declared VAR lags;
- response: requested-horizon target, plus source only when the estimator purpose requires it;
- contemporaneous control: source only;
- lagged control: source and declared control lags only.

`HorizonPolicy` supports `PER_HORIZON`, `SHARED_MULTI_HORIZON`, `DECLARED_REFERENCE_HORIZON`, and `NOT_APPLICABLE` for non-horizon estimator rows. Strict requested horizons are exactly 1..8; zero and empty horizon sets are rejected except through the explicit `NOT_APPLICABLE` no-horizon path. Row identity includes purpose, roles, transformations through the view, lag, requested horizon set, nullable reference horizon, horizon policy, canonical weeks, view fingerprint, and root context. No horizon is hard-coded.

## ScalingPlanner

`ScalingPlanner` accepts a prepared view, its row plan, and an explicit non-empty subset of row-plan source weeks. Mean and sample scale are calculated only from those training rows. Excluded, future/test, missing, conflict, pre-creation, not-applicable, and discontinuity cells cannot enter. Scaling fails explicitly for fewer than three training values, non-finite training values, fewer than two distinguishable raw values, or a near-constant sample scale at or below the numeric floor. The scaling fingerprint includes root/view/row identities, ordered training rows, statistics, and policy.

## Future Structural-Shock Contract

`BvarPosteriorSourceIdentity` binds a future BVAR posterior to the strict BVAR view, source metric, row plan, scaling plan, prior fingerprint, BVAR input fingerprint, posterior fingerprint, and the exact BVAR row-plan source-week domain. Caller-supplied subset or superset shock weeks are rejected. `IdentifiedShockPosterior` requires at least two accepted posterior draw IDs, positive normalized weights, one finite prepared-row shock series per draw, one covariance-draw fingerprint per draw, that source identity, ordering and normalization policy, and retained rejected-draw diagnostics. Accepted and rejected draw IDs are unique and disjoint, accepted draws are stored/fingerprinted in canonical draw-id order, rejected diagnostics are canonicalized for fingerprinting, one deterministic mean series cannot satisfy the contract, and shock vectors are not full-calendar vectors.

PHASE A validates this shape only. It does not generate shocks.

## Future Estimator Boundaries

### PHASE B

`FutureBvarInput` accepts only `BvarPreparedView`, its `PreparedRowPlan`, its `PreparedScalingPlan`, and prior identity. A posterior must later retain context/view/row/scaling/prior fingerprints. Raw observations, generic alignments, local transformations, rows, or scaling are forbidden.

### PHASE C

`FutureBlpInput` accepts only `BlpPreparedView`, its row plan, same-root `IdentifiedShockPosterior`, response-scale plans carried by the view, horizon policy, and draw-by-draw propagation policy. It validates that the posterior source metric is the BLP `SHOCK_SOURCE`, BLP source weeks are covered by the posterior eligible source-week domain, response scale identities match the BLP view, and `NOT_APPLICABLE` horizons cannot enter a BLP response. Raw X, optional shock fallback, implicit X difference, one mean shock, local response transformation, and local row selection are forbidden.

### PHASE D

`FutureJohansenInput` accepts a level-only `JohansenPreparedView` and its row plan. `FutureVecmInput` accepts a same-root level/difference `VecmPreparedView`, row plan, and rank configuration identity. Neither may regenerate differences.

### PHASE E

PHASE E may consume `CandidateEligibilityView` and final PHASE B/C estimators to compare candidates on common rows with posterior-aware scores. Until then, automatic ranking is disabled.

## Proxy Performance Posterior Boundary

The v0.5.0.1 major-lift proxy performance model is a separate deterministic derived-state subsystem. It combines confirmed strength sessions with fixed metadata-constrained loadings to provide bench-press, squat and deadlift expectations and historical uncertainty bands. It does not change canonical e1RM observations.

Its posterior means, medians, intervals, innovations and proxy-only weeks are not lifecycle-validated observed cells and must not enter `StrictTimeSeriesIngestion`, `PreparedAnalysisContext`, any strict estimator view, `metricSeries`, or `LegacyTimeSeriesAnalyzer`. A UI chart interval band is historical posterior uncertainty, not `forecastRange` and not an identified structural shock posterior.

A future BVAR/BLP bridge would require a separately approved contract that preserves posterior draws, source lineage, calendar/lifecycle semantics, target-specific model-selection identity and uncertainty through every downstream transformation. A deterministic mean series or interval midpoint is insufficient. v0.5.0.1 implements no such bridge.

## Fingerprint Rules

- semantic content changes identity;
- normalized equivalent lifecycle range order does not;
- map/set iteration order is canonicalized;
- constructors do not accept external fingerprints;
- identity-bearing strict classes do not expose data-class copy;
- a view changes view identity but retains root identity;
- role, purpose, lag, horizon, row, training sample, representation, response scale, and future draw changes are fingerprinted.

## Strict Preparation Result

`StrictTimeSeriesPreparationPipeline` returns only:

- `StrictPreparationResult.Success(context, readinessDiagnostics)`, explicitly stating no estimator has run; or
- `StrictPreparationResult.Failure(code, diagnostics, partialContextWhereSafe)`.

Preparation success is not a completed Bayesian result and is not displayed as one.

## Forbidden Compatibility Paths

Static and focused tests reject:

- raw `TrendDataPoint` outside strict ingestion;
- strict imports of legacy estimators/selector/cointegration;
- finite/null filtering used to compress calendar identity;
- independent or repeated differencing;
- independent transformation, row, scaling, or horizon-1 logic;
- sub-32 integration diagnostics, fixed ADF/KPSS thresholds, legacy confirmed-status vocabulary, explicit transformation fallback, scaling clamps, horizon-zero sentinels, and duplicate row/scaling authorities;
- transformed data in a Johansen view;
- raw/fallback/single-mean/full-calendar BLP shocks;
- caller-controlled shock-week subsets, non-canonical posterior draw fingerprinting, accepted/rejected draw overlap, and strict ingestion duplicate-revision resolution;
- response-scale decisions in UI;
- statistically ranked optional variables in PHASE A;
- public fingerprint injection or stale-copy identity;
- legacy results labeled as strict preparation.

## File Responsibility Map

- `StrictTimeSeriesIngestion.kt`: only raw boundary, existing resolver-output adapter, date canonicalization, calendar construction, lifecycle cell derivation, strict entry point; it consumes one resolved observation/conflict per metric/week and does not decide revision precedence.
- `StrictTimeSeriesStages.kt`: calendar/lifecycle/level/request/result types and fingerprint primitive.
- `StrictTimeSeriesDiagnostics.kt`: contiguous segments and segment-aware integration authority.
- `StrictTimeSeriesRepresentation.kt`: inconclusive policies, canonical transformation, transformed series, estimator representation, response scale, future shock posterior.
- `PreparedAnalysisContext.kt`: root context and eligibility catalog factory.
- `PreparedEstimatorViews.kt`: read-only purpose-specific views.
- `PreparedRowAndScalingPlans.kt`: roles, horizon policies, sole row authority, sole scaling authority.
- `FutureEstimatorBoundaries.kt`: validated PHASE B/C/D input bundles and BVAR posterior source identity only; no estimator math.
- `BayesianTimeSeriesAnalyzer.kt`: explicitly named `LegacyTimeSeriesAnalyzer` compatibility implementation.
- `tools/check_time_series_numeric_sources.py`: numeric and strict architecture guards.

## Legacy Retirement Plan

1. Keep `LegacyTimeSeriesAnalyzer` isolated while strict PHASE B-D estimators do not exist.
2. Implement PHASE B against `FutureBvarInput` only.
3. Implement PHASE C against `FutureBlpInput` only.
4. Implement PHASE D against the level/VECM boundaries only.
5. Implement PHASE E ranking and UI labels only after B-D outputs exist.
6. Replace app-visible legacy routing, then delete compatibility estimators and their generic alignment path.

No PHASE B, C, D, or E estimator implementation is part of this closure.
