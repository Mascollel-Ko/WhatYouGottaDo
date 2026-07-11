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

`TimeSeriesAlignmentService` aligns only common weekly observations. It neither forward-fills missing values nor converts missing history into zero. Candidate screening rejects a series with more than 25 percent missing observations in the required window or insufficient variation. The result keeps the aligned period and count for disclosure.

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

## File / Feature Map

- `BayesianTimeSeriesModels.kt`: request, alignment, diagnostics, IRF, and result contracts.
- `BayesianTimeSeriesSupport.kt`: alignment, screening, stationarity, normal-posterior regression, and matrix helpers.
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
