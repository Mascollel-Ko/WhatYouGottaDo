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

The app boundary distinguishes result availability from sampling quality. A
finite, interpretable posterior is `AVAILABLE`; diagnostic thresholds classify
it as `STRICT`, `RELAXED`, or `LIMITED`. A R-hat, ESS, MCSE/SD, lag-mixing, or
stabilization-cap miss does not by itself make a result unavailable.
`UNAVAILABLE` is reserved for a required-data, identification, unsupported
representation, row-domain, numerical, non-finite, cancellation, stale, or
unexpected blocker that prevents a valid result.

Sampling always starts with the v0.7 STRICT APP_RUNTIME values: four chains,
two consecutive stabilization passes, a 2,000-draw stabilization cap, R-hat
below 1.01, ESS at least 100, and MCSE/SD at most 0.10. The strict policy
fingerprint remains
`caad4a0b3a7f5336596c5a713173aa1cc79d7731b6715ccb1e44cd8eb7851199`.
If those diagnostics are not reached, the same prepared model, chain state,
and sampling identity continue automatically within the approved RELAXED
budget: one stabilization pass, a 4,000-draw stabilization cap, R-hat below
1.05, ESS at least 50, and MCSE/SD at most 0.20. No looser third profile exists.
Production and precision bounds remain 5,000 and 10,000 draws per chain.

Before sampling, approved deterministic Phase A adjustments run
automatically. An `INCONCLUSIVE` series may reuse the existing reviewed
feature-family semantic representation; positive `SUPPORTED_I0`/
`SUPPORTED_I1` evidence is never overwritten, and `UNSUPPORTED` or
unclassified cumulative series remain blocked. After canonical optional
candidate and Pmax degradation, an infeasible common-row plan may remove
removable controls one at a time by fewer semantically usable CLOSED weeks,
then greater effective missingness, then stable feature ID. Conditional RPE
`NOT_APPLICABLE` no-exposure cells count as the same semantic zero carriers
used by canonical Phase A. X, Y, horizon, and `minimumCommonRows=3` are never
reduced or guessed.

Every control-removal attempt recreates the effective request through
`WeeklySnapshotPhaseAAdapter` and the single canonical Phase A pipeline. Row
plans, scaling, source grouping, J/T/q, tau0, materialized designs, and
fingerprints are recalculated. Selection is prefit and deterministic; no
coefficient, posterior, lag probability, or reliability diagnostic can choose
which control is removed. The first feasible specification is final.

Sampling identity is deterministic over the prepared-input fingerprint,
materialized-design fingerprint, initial sampling-policy fingerprint, retry
attempt, and chain index. Automatic diagnostic-budget continuation does not
change that identity or rebuild Phase A. Attempt zero is the first normal run;
a user retry increments the attempt while preserving the requested model.

Four chains are monitored with rank/folded R-hat, bulk/tail ESS, and MCSE/SD
over functional quantities. The final four completed diagnostic windows are
retained as bounded descriptive evidence. Raw local scales remain
diagnostic-only. Mathematically finite medians, intervals, Rao-Blackwellized
lag probabilities, and source summaries stay visible under `LIMITED` quality
with explicit caution instead of being suppressed.

`AnalysisAdjustmentTrace` records ordered semantic fallback, control removal,
optional-candidate reduction, Pmax degradation, sampling-budget extension,
and final diagnostic classification. Events contain only bounded provenance
and before/after fingerprints, never raw workout history. Model-changing
prefit adjustments rebuild canonical Phase A; sampling continuation explicitly
records that model structure did not change.

Typed blockers distinguish preparation, metadata/representation, focal or
target variation, common-lag rows, scaling, source identity, numerical
SPD/non-finite state, cancellation, stale execution, and unexpected runtime
failure. Historical convergence, lag-mixing, and precision failure-code names
may remain inside compatibility paths, but the automatic app path treats their
observations as quality diagnostics whenever a finite posterior exists.
Neither diagnostic profile changes the likelihood, priors, group-Horseshoe
equations, tau0 calibration, observation-space kernel, Sigma/B update order, or
Rao-Blackwellized lag posterior.

## App Boundary

`TrainingViewModel` owns `StrictBayesianLabCoordinator`. The coordinator takes
an immutable stable-key request, waits for a fresh snapshot, runs CPU work off
the main thread, exposes named stages, cancels on selection changes, and rejects
stale completion by request token plus snapshot fingerprint.

The picker reads snapshot capability descriptors and exposes one normal
`Bayesian 분석하기` action. There is no pre-analysis STRICT/RELAXED selector or
manual relaxed-result rescue. It does not use the dashboard 8/12-week window
or `AnalysisMetricRegistry.minPoints=8`.

An `AVAILABLE` card always shows mathematically defined posterior medians,
80% intervals, and lag/source summaries, plus a compact `엄격 기준 충족`,
`완화 기준 충족`, or `제한적` diagnostic label and the automatic-adjustment
count. A true blocker shows `분석할 수 없음`. Both surfaces expose one
scrollable `분석 상세` view backed by `BayesianAnalysisReport`.

The same canonical report powers UI details and Storage Access Framework
`CreateDocument(text/plain)` export for every available classification and for
unavailable outcomes. It contains original/effective requests, model and row
provenance, ordered adjustments, execution-time strict/relaxed policy
snapshots, recent diagnostic windows, posterior or terminal blocker, app
version, and BuildConfig commit SHA. Raw workout history and profile data are
excluded.

The protocol registry was audited for this redesign. The Strict Bayesian Lab
remains an architecture/app integration contract rather than a separately
registry-managed protocol family; the existing registry reference is only a
supporting document of the strength-performance family, so registry ownership
and versions remain unchanged.

## Legacy Boundary

`LegacyTimeSeriesAnalyzer`, its fixed-shrinkage estimators, and compatibility
result models remain for reference tests and saved compatibility only. The
current `AnalysisDestination.LAGGED_LAB` route cannot instantiate or call
them. `tools/check_time_series_numeric_sources.py` guards this consumer cutover
and prevents dashboard-window data from re-entering the strict route.

## Validation Evidence And Limits

Automated coverage includes threshold separation, automatic STRICT/RELAXED/
LIMITED classification, non-blocking finite posterior summaries, unchanged
model identity during sampling continuation, automatic representation and
control adjustments, full-history publication, conditional carrier/scaling,
tau0 and p0 behavior, observation/coefficient reference equivalence,
deterministic kernel identity, lag recovery and Rao-Blackwellization,
functional diagnostic gates, universal reports/export, narrow-screen UI, and
stale/cancellation handling.

The desktop JVM benchmark harness measures weekly publication, Phase A, and a
short validation Phase B separately. It is not Android thermal, battery, or
production-duration evidence. Device profiling remains a follow-up gate.
