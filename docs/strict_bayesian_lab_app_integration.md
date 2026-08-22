# Strict Bayesian Lab App Integration

## Status

This is the repository canonical successor to the supplied Strict Bayesian Lab
App Integration Specification v2.2. It owns weekly feature preparation,
snapshot publication, invalidation, app orchestration, and the current Lab UI
boundary. Phase A ownership stays in
`docs/bayesian_time_series_lab_architecture.md`; Phase B mathematics stays in
the supplied group Horseshoe v0.7 specification.

## Weekly Authority

`WeeklyAnalysisFeatureSnapshotService` reads full history rather than the
dashboard chart window. The snapshot carries ordered Monday weeks, explicit
OPEN/CLOSED state, stable feature/source descriptors, semantic cells, exercise
aggregates, source revision, metadata revision, calculator versions, and one
immutable fingerprint.

Display names are presentation only and do not affect snapshot identity.
Conditional exercise RPE publishes both `mean_rpe` and an `on` support feature.
No exposure stays distinguishable from exposure with a missing RPE.

## Publication And Invalidation

`WeeklyAnalysisSnapshotStore` is the single publication owner. Dirty signals
are conflated and debounced. Rebuild runs on `Dispatchers.Default`; partial
snapshots are never published. If a newer revision arrives during a rebuild,
the old result is discarded and the latest revision is rebuilt. `awaitFresh`
returns only a snapshot matching the latest requested revision.

Analysis refresh marks the store dirty after canonical trend/history refresh.
Set recording and ordinary screen navigation do not synchronously run MCMC.
The first Lab request builds a snapshot if no revision has been published.

## Request And Execution

The UI submits `StrictLabAnalysisRequest` using stable `AnalysisFeatureKey`
values for X, Y, controls, and horizon. `StrictBayesianLabCoordinator` owns at
most one job. A changed selection cancels the old job and invalidates its
result. Completion is accepted only when both request token and snapshot
fingerprint still match.

`StrictBayesianLabService` runs preparation, materialization, sampling, and
summarization on a background dispatcher. The UI receives named stages rather
than fabricated percentages: full-history preparation, strict representation,
common-row model construction, chain stabilization, posterior sampling,
reliability checking/extension, and summarization.

Only Analyze is disabled while a run is active. Recording remains available.
The app boundary is `Available` or `Unavailable`; sampling quality is an
independent `STRICT`, `RELAXED`, or `LIMITED` classification. Diagnostic misses
do not become unavailable when a finite interpretable posterior exists. True
blockers carry structured `StrictFailureDiagnostics`; the UI does not infer
meaning from free-form strings.

The user has one Analyze action. Attempt zero automatically runs the strictest
approved path first. If strict diagnostics are not reached, the same prepared
model and chain state continue within the existing relaxed computation budget.
There is no pre-analysis mode selector and no `완화해서 결과 보기` rescue.
`다시 시도` starts a new deterministic attempt only after an unavailable
outcome; it does not select a diagnostic profile.

Before sampling, an `INCONCLUSIVE` series may reuse only its existing approved
family semantic representation. Positive I(0)/I(1) evidence is preserved, and
unsupported or undefined required X/Y series stay blocked. Existing optional
candidate and Pmax degradation run first. If the common-row plan remains
infeasible, removable controls are dropped one at a time by canonical prefit
semantic usability and Phase A is rebuilt after every removal. Conditional RPE
no-exposure `NOT_APPLICABLE` cells use the same zero-carrier semantics in both
ranking and preparation. No route removes X/Y, changes the horizon, lowers
three common rows, or changes v0.7 model equations.

## Picker And Result Presentation

Picker availability comes from snapshot descriptors and strict preflight. It
does not use legacy `minPoints=8`, a dashboard 8/12-week window, or a universal
24/32-week model gate. Disabled features show their actual availability reason.

`Available` first explains the result in plain Korean. The displayed shock is
the production v0.7 focal-X change: the analysis-transformed X increases by one
standardized unit, equal to one sample standard deviation over the canonical
common source weeks. The app does not invent an original-unit conversion. This
metadata is read-only and never feeds back into preparation or sampling.

Each response Y has its own compact IRF-style Canvas, finite posterior medians,
and 80% intervals on the same existing response scale. The graph includes zero,
uses only emitted horizons, and never smooths or combines variables with
different scales. Deterministic wording calls a wholly positive interval an
increasing direction, a wholly negative interval a decreasing direction, and
an interval containing zero uncertain. It identifies the largest absolute
posterior median horizon, says `중앙값 기준`, and explicitly avoids causal or
statistical-significance claims. RELAXED and LIMITED results keep the same
interpretation with their existing diagnostic caution.

The official Rao-Blackwellized lag probabilities remain numerically unchanged.
The compact card explains only which lag model has the highest posterior weight
and directs users to the full response path; it does not describe that weight
as the probability that an effect occurs in a particular week. Raw lag weights
remain in details and TXT. The card also retains `엄격 기준 충족`, `완화 기준
충족`, or `제한적`, plus the ordered automatic-adjustment count. Raw local
Horseshoe scales remain internal.

Every outcome exposes `분석 상세`. The scrollable detail uses the canonical
`BayesianAnalysisReport` to show the original request, effective model,
representation/row/scaling provenance, automatic adjustments, execution-time
sampling criteria, observed recent diagnostic windows, fingerprints, and
posterior summary or terminal blocker. Only genuine blockers use `분석할 수
없음`.

`내보내기` uses SAF `CreateDocument` with `text/plain`; it requests no broad
storage permission and is available for STRICT, RELAXED, LIMITED, and
Unavailable outcomes. The UI and TXT formatter consume the same immutable
report sections. For Available results those sections now include `SHOCK
DEFINITION`, `RESPONSE INTERPRETATION`, `HORIZON RESPONSE`, and `LAG
INTERPRETATION`; the same result-owned presentation object supplies the compact
card, details, and TXT. Raw workout history, posterior draws, and profile data
are excluded.

## Runtime Limits

The app policy uses four chains and bounded stabilization, production, and
precision extension. Cancellation is polled during sampling. The current
implementation has deterministic desktop-JVM stage benchmarks, but no claim is
made about Android battery, thermal behavior, or all-device latency until
instrumented device profiling is run.

## Regression Guards

- full history is independent of dashboard history length;
- snapshot identity ignores localized labels;
- stale results cannot overwrite a newer request;
- selection changes cancel an active run;
- a finite interpretable posterior remains Available even when strict or
  relaxed diagnostic targets are missed;
- the strict APP_RUNTIME policy fingerprint and thresholds remain frozen;
- strict sampling is always attempted first and relaxed computation is bounded
  by the existing approved policy;
- sampling continuation preserves prepared model and sampling identity;
- the same retry attempt is reproducible and a new attempt changes chain seeds;
- control reduction is deterministic, prefit, and followed by full Phase A
  reconstruction;
- X, Y, horizon, and `minimumCommonRows=3` are never reduced;
- `LIMITED` is visibly distinct but retains posterior output;
- one canonical report backs UI detail and TXT export for every outcome;
- the LAGGED_LAB route uses the strict coordinator/catalog;
- legacy analyzer/service construction is forbidden in `TrainingViewModel`;
- dashboard `performanceTrend.metricSeries` is forbidden as strict raw input.
