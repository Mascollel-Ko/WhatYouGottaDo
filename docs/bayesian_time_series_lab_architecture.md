# Bayesian Time-Series Lab Architecture

## Scope

The Analysis Lab time-series tool estimates exploratory weekly dynamic responses. It does not turn correlation into causality and does not silently substitute a correlation or a simple lagged regression when a dynamic model cannot be estimated.

## Previous Audit

Before this change, `LaggedTimeSeriesAnalyzer` joined one X and one Y by week and fitted a single ridge-like regression over a short fixed lag range. It was not a VAR, VECM, or structural IRF implementation. The result UI therefore now routes to the Bayesian dynamic analysis result instead of presenting the older calculation as an IRF.

## User Inputs

- X: exactly one structural-shock target.
- Y: one or more response metrics. Only these selected responses are displayed.
- Z: zero or more exogenous controls. Z never enters the Cholesky ordering.
- Horizon: default 2 weeks, selectable from 1 through 8. The estimator calculates h=0 through the used horizon. h=0 is labelled as a contemporaneous response, not a causal effect.

## Preprocessing And Screening

`TimeSeriesAlignmentService` now builds a continuous weekly calendar grid from the first available week through the last available week. Raw trend dates are canonicalized to ISO Monday week starts before alignment; explicit grid/cell week-start inputs must already be ISO Mondays. Grid bounds are calculated only from requested metrics and their requested lifecycle metadata. Each metric/week cell records whether it is an observed value, structural zero, missing, not applicable, pre-metric-creation, version-discontinuity, or conflict cell. Missing values are not forward-filled and are not converted into numeric zero. Lifecycle activation is explicit: `EXPLICIT_METADATA_ONLY` uses only `availableFromWeek`, `FIRST_OBSERVATION_ALLOWED` must be requested before the first valid observation can define activation, and `REGISTRY_DEFINED` is reserved for canonical registry metadata. Non-structural metrics do not treat the first observation as metric creation by default.

Observation conflicts are deterministic and typed. Exact duplicate observations can merge, explicitly declared revision-number, version-sequence, or authoritative-revision-time values can supersede older revisions only within one shared ordering scheme, and conflicting values without valid ordering provenance become `CONFLICT` cells rather than being selected by input order, source-string ordering, ordinary observation time, or lexical version ordering. Heterogeneous revision schemes, partial revision metadata, and tied highest revisions with different values remain unresolved conflicts. Conflict cells preserve candidate count, candidate values/states/sources/revisions, selected candidate when one exists, and the selection rule. State/value invariants are enforced at construction: observed values must be finite, structural zeros must be exactly `0.0`, lifecycle/missing states cannot carry numeric values, and conflict cells require provenance.

Lag, first-difference, and horizon rows are valid only when the required calendar weeks exist exactly. A compressed list index is never treated as a week lag by itself. Every candidate source week is either included or excluded with a reason, including initial lag gaps and final target-outside-grid horizons. Excluded rows carry target week, source week, lag weeks, horizon, cell states, and exclusion reason for tests and diagnostics.

Data quality is no longer a single ambiguous missing-rate concept. Each prepared series has total weeks, observed/structural-zero/missing/pre-creation/not-applicable/version-discontinuity/conflict counts, model-eligible week count, usable/unusable counts, raw missing rate, unusable rate, coverage rate, and contiguous usable-run diagnostics. `MetricDataQualitySummary` is derived from cells through a factory so transformation-failure diagnostics cannot double-count a cell that is already missing, conflicted, or version-discontinuous. Pre-creation and not-applicable cells are excluded from the model-eligible denominator. Candidate screening consumes `PreparedMetricSeries` from the canonical grid and rejects conflict/version-discontinuity cells, high unusable rate, and insufficient variation. The deprecated raw screening adapter now requires an already validated alignment and cannot silently rebuild prepared series with default lifecycle metadata.

`PreparedMetricSeries` is factory-created and carries a deterministic lifecycle metadata fingerprint. The factory validates lifecycle cell semantics: structural zeros require an enabled structural-zero policy and activation boundary, observed values cannot sit in not-applicable or version-discontinuity ranges, explicit not-applicable/discontinuity cells must be backed by metadata or source-cell provenance, conflicts require provenance, and duplicate lifecycle ranges normalize into stable fingerprints while overlapping discontinuity ranges fail. Restricting a series to a view range and stationarizing it preserve lifecycle metadata unchanged while recalculating cell-derived quality summaries and contiguous segments. If a prepared operation expects lifecycle metadata and cannot find it, it fails instead of substituting a new default object.

`TimeSeriesTransformationPlan` is created before automatic endogenous selection. The plan records the integration diagnostic, selected transformation, decision reason, policy version, and deterministic fingerprint for every metric. I(0) metrics stay in levels, I(1) metrics are transformed once into first differences, I(2)+ metrics are excluded, inconclusive optional candidates are excluded, and required user-selected X/Y/Z metrics may use a documented level fallback without claiming confirmed stationarity. `PreparedCandidateCatalog` then exposes only the validated transformed prepared series; selector ranking and final model preparation consume this same catalog.

`PreparedTimeSeriesSystem` is the selector-facing row contract. It validates that every included series shares the exact same week vector and preparation version, derives common usable rows under `COMMON_USABLE_ROWS`, and records deterministic row fingerprints from source week, target week, lag weeks, ordered metric IDs, metric roles, transformations, lag, requested horizon set, horizon policy, preparation version, and row policy. Variable roles are explicit: shock sources and endogenous states require source and declared lag rows, responses require the actual requested-horizon target, contemporaneous controls require source rows only, and lagged controls require only declared lags. Automatic endogenous candidate ranking and stability checks now use the same transformed prepared catalog and the same common source-week set instead of realigning raw `TrendDataPoint` maps or comparing baseline and candidate scores over different samples.

## Numeric Foundation

Phase A fixes the numeric backend on Apache Commons Math 3.6.1 through `StableLinearAlgebra`. Model code does not call Commons Math directly except through this wrapper. SPD systems use `CholeskyDecomposition` and `DecompositionSolver.solve`; least squares uses RRQR with SVD fallback; rank and condition number come from singular values; SPD log determinant comes from the Cholesky diagonal. The public condition number is the standard full-rank ratio of largest to smallest singular value, and rank-deficient matrices return infinity rather than a truncated effective condition number.

The wrapper also provides a symmetric-definite generalized eigen primitive for later Johansen work. It whitens `A v = lambda B v` with `B = L L'`, computes `C = L^-1 A L^-T` using Commons Math triangular solves instead of explicit inverse or app-owned triangular substitution, checks relative asymmetry before symmetrization, runs full-spectrum `EigenDecomposition`, restores `v = L^-T q`, normalizes `v' B v = 1`, and fails the whole result if the complete spectrum cannot pass residual and `V' B V` checks.

Strict positive definiteness and regularizable positive definiteness are separate checks. Strict Cholesky returns only when the original matrix is square, finite, symmetric within tolerance, Cholesky-decomposable without jitter, full numerical rank, finite-conditioned, below `MAX_CONDITION_NUMBER`, and has finite factor entries. The regularized path may add bounded scale-relative diagonal jitter for small full-rank numerical noise, but exact singular PSD, rank-deficient, non-finite, non-symmetric, ill-conditioned, and materially indefinite matrices fail with explicit failure codes. Callers receive the original/effective matrix, strict failure code, regularization attempt/success flags, jitter amount, jitter ratio, numerical rank, condition number, minimum eigenvalue, matrix scale, and diagnostics through the result object.

Johansen-form primitives are strict by default. `S00` and `S11` must be strict SPD, and the primitive preserves `S00` solve provenance plus `S11` Cholesky provenance. Bounded regularization remains available only for the general symmetric-definite eigen primitive, where whitening, residuals, normalization, and `V' B V` checks all use the same effective `B` matrix.

`IntegrationOrderAnalyzer` applies both ADF-style and KPSS-style diagnostics to levels and first differences. I(1) variables are first-differenced for the non-cointegrated local-projection/BVAR route; I(0) variables remain in levels. I(2)+ required variables stop the analysis. Inconclusive diagnostics do not force VECM.

## Endogenous System And Lag Selection

The mandatory endogenous block always contains X and every selected Y. `EndogenousVariableSelector` can add candidates only after the descriptor-wide transformation plan and transformed prepared catalog exist. It can add candidates only when they pass transformed prepared data-quality screening, the sample-based K cap, a dynamic-system stability check on role-aware prepared common rows, posterior predictive coverage screening, and positive rolling-origin out-of-sample log predictive-density gain computed over the same requested-horizon common source weeks for baseline and expanded candidate systems. It does not rerun stationarity diagnostics, rank candidates in levels when the final system uses differences, rank by contemporaneous Pearson correlation, or accept raw `TrendDataPoint` maps.

The product cap is K=8. The sample cap is evaluated from the requested horizon, lag, controls, and deterministic intercept. The selector writes inclusion and exclusion reasons to the result diagnostics.

`BayesianTimeSeriesAnalyzer` evaluates lag candidates p=1 through p=4 with Bayesian model evidence and a decreasing lag prior with rho=0.5. A posterior probability of at least 0.70 uses a representative lag; otherwise all posterior-supported lags are averaged using the same lag posterior for every selected Y and every displayed horizon.

## Model Routes

1. **Bayesian Local Projection** is the default. Every selected Y and horizon has a separate Normal-prior posterior regression containing the Cholesky-identified X shock, lags of the endogenous system, and Z controls. Horizon validity requires `N_h >= max(24, 4d)`.
2. **Bayesian VECM** is not routed in Phase A. The legacy cointegration screen is diagnostic-only until Phase D adds validated rank diagnostics and a supported cointegration vector.
3. **Bayesian VAR** is an explicit fallback only when local projection cannot produce every requested horizon and the reduced-form posterior predictive check passes.

The BVAR uses standardized series and empirical-Bayes Minnesota-style normal priors: own lags are shrunk less than cross lags, lag shrinkage decays with kappa=2, and the global shrinkage candidate is selected by model evidence. Compatibility scoring and stability checks calculate scaling parameters only from the validated estimation/training source-week set, not from excluded calendar weeks, future test rows, conflict weeks, pre-creation cells, or version-discontinuity ranges.

## Structural Shocks And Diagnostics

`CholeskyShockIdentifier` constructs a canonical temporal order from metric categories, so X is not automatically first. It uses the reduced-form residual covariance and Cholesky decomposition to derive a one-standard-deviation orthogonal innovation for X. This innovation is supplied to Bayesian Local Projection; BVAR and VECM use the same Cholesky convention for their impulse responses.

The analyzer compares the canonical order with an adjacent same-time-group reordering when one is available. A response-sign or peak-horizon change produces an order-sensitivity warning. The result also records the selected model, horizon reduction, lag posterior, transformations, cointegration decision, automatic endogenous selection, and warnings.

The current cointegration route remains a legacy heuristic isolated behind `CointegrationAnalyzer`. Phase A uses the hardened generalized eigen primitive for the rank-one screen calculation, but it does not claim a full Johansen trace/max-eigen test or any posterior rank result. Diagnostics explicitly mark this limitation until Phase D replaces the heuristic. The legacy score is diagnostic-only and cannot route to `BAYESIAN_VECM`.

## File / Feature Map

- `BayesianTimeSeriesModels.kt`: request, alignment, lifecycle metadata provenance/fingerprints, explicit revision schemes, conflict provenance, transformation-plan/catalog contracts, variable role row requirements, prepared-series factory, prepared-system row identity, data-quality, diagnostics, IRF, and result contracts.
- `StableLinearAlgebra.kt`: Commons Math backed SPD solve, least squares, Cholesky, SVD, rank, condition number, symmetric eigen, generalized symmetric eigen, and log determinant primitives.
- `BayesianTimeSeriesSupport.kt`: continuous calendar alignment, lifecycle activation/preservation, explicit conflict resolution, transformation-plan creation, prepared candidate catalog creation, prepared-series screening, alignment reconstruction from prepared series, stationarity, exact lag/difference/horizon primitives, and normal-posterior regression.
- `BayesianLocalProjectionEstimator.kt`: horizon-specific Bayesian LP posterior and rolling-origin predictive score with exact validated slicing and optional prepared common-row source-week filtering.
- `EndogenousVariableSelector.kt`: transformed prepared-system data screening, requested-horizon K cap, role-aware common-row rolling predictive scoring, and greedy rolling-origin endogenous selection.
- `CointegrationAnalyzer.kt`: legacy rank-one heuristic score; diagnostic-only and not model-routing input.
- `BayesianDynamicEstimators.kt`: Minnesota-style BVAR, rank-1 Bayesian VECM, posterior predictive coverage, dynamic IRFs, optional prepared common-row source-week filtering for selector stability checks, and training-sample-only scaling.
- `CholeskyShockIdentifier.kt`: canonical order, structural shocks, and ordering sensitivity.
- `BayesianTimeSeriesAnalyzer.kt`: transparent model routing and explicit unavailable/fallback handling.
- `AnalysisLabUi.kt`: X/Y/Z selection, 1-8 horizon input, and shared multi-Y IRF result disclosure.
- `LaggedTimeSeriesAnalyzer.kt`: compatibility facade for the old single-Y API; it delegates to the new implementation.

## Call-Graph Audit

- Analyzer request entry: `BayesianTimeSeriesAnalyzer.analyze()` aligns raw requested metrics only to establish the canonical base calendar, then builds the descriptor-wide level catalog, derives `TimeSeriesTransformationPlan`, creates `PreparedCandidateCatalog`, reconstructs required/final alignments from the transformed prepared catalog, and only then calls `EndogenousVariableSelector.select()`.
- Prepared selector entry point: `EndogenousVariableSelector.select()` screens candidates through `usablePreparedCandidate()`, builds role-aware `PreparedTimeSeriesSystem` objects for candidate systems using the actual requested horizon, and passes the derived common source-week set into rolling predictive scoring and stability checks.
- Candidate rolling scorer: baseline and expanded candidates both use the expanded system's role-aware common source-week set. Controls are not required to have future response targets unless explicitly declared as responses.
- Restriction/slicing: `restrictToWeeks()` rebuilds validated cells, quality summaries, prepared series, contiguous segments, and grid state for a contiguous view. LP rolling folds discard invalid restrictions instead of falling back to shallow `alignment.copy(...)`.
- Scaling: BVAR/VECM compatibility paths standardize from the validated estimation/training row sample, not the full calendar vector.
- Raw-series overloads: `TimeSeriesAlignmentService.align()` remains the ingestion boundary from UI/trend data into prepared cells. `usableCandidate()` is deprecated compatibility code and no production selector path calls it.
- Raw bypass prohibition: `EndogenousVariableSelector` no longer imports `TrendDataPoint`, accepts raw maps, or calls `alignmentService.align()`.
- Metadata copy points: `alignObservations()` creates metadata only for truly metadata-free sources; `restrictToWeeks()` and `transformWithPlan()` require existing prepared lifecycle metadata and preserve its fingerprint.
- Quality-summary constructors: production code uses `MetricDataQualitySummary.fromCells()` through `PreparedMetricSeries.createValidated()`; manual summary and contiguous-segment injection are statically scanned.
- Revision-resolution callers: `alignObservations()` groups observations by metric/week and calls `resolveObservationConflict()`, which validates explicit revision schemes and returns conflict cells for heterogeneous, partial, or tied-different revisions.
- Prepared-system callers: automatic selector ranking/stability uses `PreparedTimeSeriesSystem.createValidated()` with variable role requirements; later PHASE B estimators should use the same system boundary rather than raw `TrendDataPoint` maps.

## Boundaries

- Results are exploratory dynamic associations, not confirmed causal effects.
- The tool returns `UNAVAILABLE` instead of showing a correlation-style substitute whenever required diagnostics or horizon sample conditions fail.
- No app version, release tag, backup format, or trend-series generation policy changed as part of this implementation.
- Phase A does not implement BVAR posterior sampling, Bayesian Local Projection posterior mixtures, Johansen trace/max-eigen rank tests, Bayesian VECM, automatic model routing changes, or final IRF UI integration.

## Downstream Contract Map

- Phase B may rely on strict SPD failures for rank-deficient or ill-conditioned covariance inputs, regularization provenance on any jittered solve, `PreparedMetricSeries`/`PreparedTimeSeriesSystem` as the estimator-facing boundary, transformation plans created before selector ranking, common-row fingerprints for lag and horizon selection, conflict-free included rows, preserved lifecycle metadata, role-aware controls, and unique-cell `MetricDataQualitySummary.unusableRate` rather than a legacy missing-rate field.
- Phase C may rely on transformed series preserving the complete weekly calendar, source-cell provenance, explicit unavailable first-difference cells instead of compressed week vectors, exact restricted views, training-only scaling, requested-horizon row requirements, and row fingerprints that can prove BVAR shock and BLP response alignment.
- Phase D may rely on strict `S00`/`S11` Johansen-form primitives, complete generalized-eigen diagnostics, preserved lifecycle/version-discontinuity metadata after slicing and transformation, lifecycle semantic validation before covariance matrices, unresolved conflict cells being excluded from numeric prepared values, and legacy cointegration being unable to route as a supported rank result.
- Phase E must display unavailable/legacy diagnostics as diagnostic-only and must not label the legacy score as a posterior probability, validated rank, or Johansen trace result.
