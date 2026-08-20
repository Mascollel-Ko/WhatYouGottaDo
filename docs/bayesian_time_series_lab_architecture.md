# Bayesian Time-Series Lab Architecture

## Status And Authority

The public Analysis Lab time-series route now uses the strict weekly snapshot,
the existing immutable Phase A pipeline, and the strict Bayesian group
Horseshoe v0.7 sampler. The old single-lag exploratory implementation remains
reference/compatibility code only and is not called by the current Lab UI.

Authority is split deliberately:

- this document owns Phase A types, ownership, lineage, row, scaling, and
  future-estimator boundaries;
- `docs/strict_bayesian_lab_app_integration.md` owns weekly preparation,
  invalidation, orchestration, and UI integration;
- `WhatYouGottaDo_Strict_Bayesian_Group_Horseshoe_Sampling_Spec_v0.7` remains
  the Phase B mathematical authority. Production code does not silently amend
  its prior or sampling equations.

The time-series architecture is not currently a registry-managed protocol
family. `docs/protocols/protocol_registry.json` is therefore unchanged.

## Active Pipeline

```text
full workout/check-in/performance history
  -> WeeklyAnalysisFeatureSnapshotService
  -> WeeklyAnalysisFeatureSnapshot (immutable publication)
  -> WeeklySnapshotPhaseAAdapter
  -> StrictPhaseAInputBundle
  -> StrictTimeSeriesPreparationPipeline
  -> PreparedAnalysisContext
  -> BvarPreparedView
  -> PreparedLagComparisonPlan (one common row domain)
  -> PreparedComparisonScalingPlan (one common scaling identity)
  -> FutureBvarComparisonInput
  -> PreparedBvarComparisonDesign
  -> StrictBayesianV07Sampler
  -> StrictBayesianLabUiState
  -> StrictBayesianLabUi
```

No downstream stage may recreate an upstream authority. Raw observations end
at strict ingestion. Display labels never define feature or source identity.

## Dynamic Feature And Source Identity

`StrictSeriesKey` is the pipeline-wide feature identity. It permits canonical
metric features plus dynamic anatomy/exercise features without converting them
back to `TrendMetricId`. `AnalysisFeatureKey` and `AnalysisSourceKey` carry
stable, versioned values. Fingerprints use stable keys and semantic versions,
not localized display names.

Each candidate feature is bound to one candidate source before Phase B. The
source grouping carries the originating prepared-view fingerprint. A grouping,
row plan, scaling plan, or materialized lag design from another root/view is
rejected even when its visible feature names happen to match.

## Calendar, Lifecycle, And Representation

Phase A keeps a continuous Monday-based calendar. Missing weeks are cells, not
deleted rows. `MISSING`, `NOT_APPLICABLE`, `STRUCTURAL_ZERO`, `OBSERVED_VALUE`,
and `CONFLICT` remain distinct.

The 32-week minimum belongs only to the ADF/KPSS diagnostic method. It is not a
universal model-readiness gate. For fewer than 32 eligible contiguous weeks,
an approved, feature-family-specific short-history representation may be used.
That policy is reason-aware and fingerprinted. It does not claim that an
unrun diagnostic proved stationarity. If eligible diagnostics run and conflict
or remain unsupported, the strict pipeline does not silently fall back.

There is no universal 8, 12, 16, 18, or 24-week gate. Required focal and target
features remain mandatory. Optional candidates may be deterministically
reduced only to obtain a feasible common row domain; the focal X is never
dropped to manufacture success.

## Candidate Inclusion

The deterministic eligible candidate set belongs to Phase A and is included
in Phase B. Candidate sources are not preselected by legacy pseudo-evidence or
fixed-shrinkage scores. Candidate features are grouped by versioned source
identity and every source receives the same feature-role and lag dimensions in
a given lag model.

`BvarPreparedView` separates response, candidate, and semantic support
features. The focal feature is always a candidate; support features do not
become model columns.

## Conditional Feature Contract

Conditional RPE/intensity uses a two-stage contract:

1. before row planning, no-exposure `NOT_APPLICABLE` becomes a semantic zero
   carrier while exposure-with-missing remains missing;
2. the final cross-lag common rows are selected;
3. conditional centering/scaling uses exposed comparison rows only;
4. materialization emits zero for carrier rows and the exposed-row deviation
   for exposed rows.

The carrier version, conditional-engineering version, support identity, row
identity, and scaling identity all participate in fingerprints.

## Common Row And Scaling Authority

`RowPlanner.planLagComparison` builds q=1..Pmax plans, intersects their source
weeks, and recreates every lag plan on exactly that ordered common domain. It
degrades Pmax deterministically only when necessary.

`ScalingPlanner.planForComparison` computes one training-row identity and one
set of scaling statistics for the entire lag comparison. All q models share
the same Y rows and response scaling. Mixing a single-lag row/scaling identity
with the multi-lag boundary is forbidden by type and fingerprint checks.

## Strict Phase B v0.7

`StrictBayesianV07Kernel` implements the supplied v0.7 authority:

- one local scale per versioned candidate source group;
- lag variance decay `l^-4`;
- calibrated `tau0(q)` from a fingerprinted prior active-source target;
- separate candidate global scale `gZ` and dynamic scale `tauDyn`;
- inverse-Wishart `Sigma ~ IW(I_m, m + 2)`;
- Makalic-Schmidt inverse-gamma auxiliary updates;
- observation-space collapsed lag weights using `I + X D X'`;
- a fresh Sigma and exact matrix-normal B draw after each selected q;
- official lag probability as the Rao-Blackwellized mean conditional omega;
- visitation frequency retained only as a diagnostic;
- draw-wise recursive responses with draw-wise inverse transformation.

No explicit matrix inverse, hand-written triangular solve, equation-wise
independent posterior, arbitrary jitter, posterior-mean feedback, or raw local
lambda selection threshold is part of the production kernel.

## Sampling And Failure Semantics

`APP_RUNTIME` and `VALIDATION` share the same chain count, warmup shape,
production bounds, lag prior, and posterior kernel. Validation requires higher
ESS and lower MCSE-to-SD. Policy identity is fingerprinted.

Four chains are monitored with rank/folded R-hat, bulk/tail ESS, and MCSE/SD
over functional quantities. Raw local scales are diagnostic-only. Weak but
valid posterior evidence is a successful result with uncertainty, not
"not enough data".

Typed failures distinguish preparation, metadata/representation, focal or
target variation, common-lag rows, scaling, source identity, convergence, lag
mixing, precision, numerical SPD/non-finite state, cancellation, and unexpected
runtime failure.

## App Boundary

`TrainingViewModel` owns `StrictBayesianLabCoordinator`. The coordinator takes
an immutable stable-key request, waits for a fresh snapshot, runs CPU work off
the main thread, exposes named stages, cancels on selection changes, and rejects
stale completion by request token plus snapshot fingerprint.

The strict picker reads snapshot capability descriptors. It does not use the
dashboard 8/12-week window or `AnalysisMetricRegistry.minPoints=8`. The UI
shows posterior medians and 80% intervals and the official Rao-Blackwellized
lag probabilities. Raw local scales and detailed developer diagnostics are not
presented as selection evidence.

## Legacy Boundary

`LegacyTimeSeriesAnalyzer`, its fixed-shrinkage estimators, and compatibility
result models remain for reference tests and saved compatibility only. The
current `AnalysisDestination.LAGGED_LAB` route cannot instantiate or call
them. `tools/check_time_series_numeric_sources.py` guards this consumer cutover
and prevents dashboard-window data from re-entering the strict route.

## Validation Evidence And Limits

Automated coverage includes threshold separation, full-history publication,
lineage/common-row checks, conditional carrier/scaling, tau0 and p0 behavior,
observation/coefficient reference equivalence, deterministic kernel identity,
lag recovery and Rao-Blackwellization, partial-active/high-collinearity and
complete-null fixtures, ordinary/regularized reference behavior, functional
diagnostic gates, APP/VALIDATION policy agreement, and stale-result handling.

The desktop JVM benchmark harness measures weekly publication, Phase A, and a
short validation Phase B separately. It is not Android thermal, battery, or
production-duration evidence. Device profiling remains a follow-up gate.
