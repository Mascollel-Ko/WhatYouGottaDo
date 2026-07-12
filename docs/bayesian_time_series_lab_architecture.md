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

`TimeSeriesAlignmentService` now builds a continuous weekly calendar grid from the first available week through the last available week. Raw trend dates are canonicalized to ISO Monday week starts before alignment; explicit grid/cell week-start inputs must already be ISO Mondays. Grid bounds are calculated only from requested metrics and their requested lifecycle metadata. Each metric/week cell records whether it is an observed value, structural zero, missing, not applicable, pre-metric-creation, version-discontinuity, or conflict cell. Missing values are not forward-filled and are not converted into numeric zero. Structural zero is allowed only after an explicit `availableFromWeek` or the first valid observation establishes the metric activation week.

Observation conflicts are deterministic. Exact duplicate observations can merge, same-source versioned observations use the latest version, and conflicting values without revision provenance become `CONFLICT` cells rather than being selected by source-string ordering. State/value invariants are enforced at construction: observed values must be finite, structural zeros must be exactly `0.0`, and lifecycle/conflict/missing states cannot carry numeric values.

Lag, first-difference, and horizon rows are valid only when the required calendar weeks exist exactly. A compressed list index is never treated as a week lag by itself. Every candidate source week is either included or excluded with a reason, including initial lag gaps and final target-outside-grid horizons. Excluded rows carry target week, source week, lag weeks, horizon, cell states, and exclusion reason for tests and diagnostics.

Candidate screening rejects a series with more than 25 percent missing observations in the required window or insufficient variation. The result keeps the aligned period and count for disclosure.

## Numeric Foundation

Phase A fixes the numeric backend on Apache Commons Math 3.6.1 through `StableLinearAlgebra`. Model code does not call Commons Math directly except through this wrapper. SPD systems use `CholeskyDecomposition` and `DecompositionSolver.solve`; least squares uses RRQR with SVD fallback; rank and condition number come from singular values; SPD log determinant comes from the Cholesky diagonal. The public condition number is the standard full-rank ratio of largest to smallest singular value, and rank-deficient matrices return infinity rather than a truncated effective condition number.

The wrapper also provides a symmetric-definite generalized eigen primitive for later Johansen work. It whitens `A v = lambda B v` with `B = L L'`, computes `C = L^-1 A L^-T` using Commons Math triangular solves instead of explicit inverse or app-owned triangular substitution, checks relative asymmetry before symmetrization, runs full-spectrum `EigenDecomposition`, restores `v = L^-T q`, normalizes `v' B v = 1`, and fails the whole result if the complete spectrum cannot pass residual and `V' B V` checks.

Strict positive definiteness and regularizable positive definiteness are separate checks. Strict Cholesky returns only when the original matrix is SPD. The regularized path may add bounded scale-relative diagonal jitter for small full-rank numerical noise, but exact singular PSD, rank-deficient, non-finite, non-symmetric, and materially indefinite matrices fail with explicit failure codes. Callers receive the effective matrix, regularization provenance, jitter amount, jitter ratio, numerical rank, condition number, and diagnostics through the result object.

Johansen-form primitives are strict by default. `S00` and `S11` must be strict SPD, and the primitive preserves `S00` solve provenance plus `S11` Cholesky provenance. Bounded regularization remains available only for the general symmetric-definite eigen primitive, where whitening, residuals, normalization, and `V' B V` checks all use the same effective `B` matrix.

`IntegrationOrderAnalyzer` applies both ADF-style and KPSS-style diagnostics to levels and first differences. I(1) variables are first-differenced for the non-cointegrated local-projection/BVAR route; I(0) variables remain in levels. I(2)+ required variables stop the analysis. Inconclusive diagnostics do not force VECM.

## Endogenous System And Lag Selection

The mandatory endogenous block always contains X and every selected Y. `EndogenousVariableSelector` can add candidates only when they pass data-quality screening, the sample-based K cap, a dynamic-system stability check, posterior predictive coverage screening, and positive rolling-origin out-of-sample log predictive-density gain. It does not rank candidates by contemporaneous Pearson correlation.

The product cap is K=8. The sample cap is evaluated from the requested horizon, lag, controls, and deterministic intercept. The selector writes inclusion and exclusion reasons to the result diagnostics.

`BayesianTimeSeriesAnalyzer` evaluates lag candidates p=1 through p=4 with Bayesian model evidence and a decreasing lag prior with rho=0.5. A posterior probability of at least 0.70 uses a representative lag; otherwise all posterior-supported lags are averaged using the same lag posterior for every selected Y and every displayed horizon.

## Model Routes

1. **Bayesian Local Projection** is the default. Every selected Y and horizon has a separate Normal-prior posterior regression containing the Cholesky-identified X shock, lags of the endogenous system, and Z controls. Horizon validity requires `N_h >= max(24, 4d)`.
2. **Bayesian VECM** is available only for an all-I(1) endogenous block when the Johansen-style rank diagnostic and Bayesian rank posterior both support rank 1. The VECM keeps a rank-1 cointegration vector beta, Bayesian adjustment speeds alpha, and Bayesian short-run gamma terms.
3. **Bayesian VAR** is an explicit fallback only when local projection cannot produce every requested horizon and the reduced-form posterior predictive check passes.

The BVAR uses standardized series and empirical-Bayes Minnesota-style normal priors: own lags are shrunk less than cross lags, lag shrinkage decays with kappa=2, and the global shrinkage candidate is selected by model evidence.

## Structural Shocks And Diagnostics

`CholeskyShockIdentifier` constructs a canonical temporal order from metric categories, so X is not automatically first. It uses the reduced-form residual covariance and Cholesky decomposition to derive a one-standard-deviation orthogonal innovation for X. This innovation is supplied to Bayesian Local Projection; BVAR and VECM use the same Cholesky convention for their impulse responses.

The analyzer compares the canonical order with an adjacent same-time-group reordering when one is available. A response-sign or peak-horizon change produces an order-sensitivity warning. The result also records the selected model, horizon reduction, lag posterior, transformations, cointegration decision, automatic endogenous selection, and warnings.

The current cointegration route remains a legacy heuristic isolated behind `CointegrationAnalyzer`. Phase A uses the hardened generalized eigen primitive for the Johansen-form calculation, but it does not claim a full Johansen trace/max-eigen test or a Bayesian rank posterior. Diagnostics explicitly mark this limitation until Phase D replaces the heuristic. The legacy score is diagnostic-only and cannot route to `BAYESIAN_VECM`.

## File / Feature Map

- `BayesianTimeSeriesModels.kt`: request, alignment, diagnostics, IRF, and result contracts.
- `StableLinearAlgebra.kt`: Commons Math backed SPD solve, least squares, Cholesky, SVD, rank, condition number, symmetric eigen, generalized symmetric eigen, and log determinant primitives.
- `BayesianTimeSeriesSupport.kt`: continuous calendar alignment, screening, stationarity, exact lag/difference/horizon primitives, and normal-posterior regression.
- `BayesianLocalProjectionEstimator.kt`: horizon-specific Bayesian LP posterior and rolling-origin predictive score.
- `EndogenousVariableSelector.kt`: data screening, K cap, and greedy rolling-origin endogenous selection.
- `CointegrationAnalyzer.kt`: Johansen-style rank evidence and the rank-1 cointegration vector.
- `BayesianDynamicEstimators.kt`: Minnesota-style BVAR, rank-1 Bayesian VECM, posterior predictive coverage, and dynamic IRFs.
- `CholeskyShockIdentifier.kt`: canonical order, structural shocks, and ordering sensitivity.
- `BayesianTimeSeriesAnalyzer.kt`: transparent model routing and explicit unavailable/fallback handling.
- `AnalysisLabUi.kt`: X/Y/Z selection, 1-8 horizon input, and shared multi-Y IRF result disclosure.
- `LaggedTimeSeriesAnalyzer.kt`: compatibility facade for the old single-Y API; it delegates to the new implementation.

## Boundaries

- Results are exploratory dynamic associations, not confirmed causal effects.
- The tool returns `UNAVAILABLE` instead of showing a correlation-style substitute whenever required diagnostics or horizon sample conditions fail.
- No app version, release tag, backup format, or trend-series generation policy changed as part of this implementation.
- Phase A does not implement BVAR posterior sampling, Bayesian Local Projection posterior mixtures, Johansen trace/max-eigen rank tests, Bayesian VECM, automatic model routing changes, or final IRF UI integration.
