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
The first strict request builds a snapshot if no revision has been published.

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
Cancellation, expected unavailability, numerical failure, and unexpected
failure remain separate states. Every failure carries one structured
`StrictFailureDiagnostics` value; the UI does not infer failure meaning from
free-form strings.

The first run is STRICT attempt zero. `다시 시도` remains STRICT and increments
the deterministic retry attempt, so the same prepared input and policy do not
repeat the identical chain. `완화해서 결과 보기` appears only for convergence,
lag-posterior mixing, and Monte Carlo precision failures. It uses the fixed
versioned RELAXED profile and a new attempt identity. Phase A is still prepared
by the same deterministic authority and the v0.7 model is unchanged.

## Picker And Result Presentation

Picker availability comes from snapshot descriptors and strict preflight. It
does not use legacy `minPoints=8`, a dashboard 8/12-week window, or a universal
24/32-week model gate. Disabled features show their actual availability reason.

Success shows posterior medians and 80% intervals, official Rao-Blackwellized
lag probabilities, and source summaries only when reliability permits them. A
broad interval is uncertainty, not failure. Detailed sampler diagnostics and
raw local Horseshoe scales remain internal during success. A RELAXED success
uses the same result layout and carries a persistent `완화된 신뢰도 기준으로
계산된 결과입니다.` notice plus structural `samplingReliabilityMode=RELAXED`
identity.

The failure card keeps a short product-facing reason and next step. Expanding
`자세히` shows the diagnostic ID, stage, affected feature/source, row/lag and
sampling identity, thresholds, observed failing metrics, and technical detail
lines. Lag mixing failures explicitly withhold official Rao-Blackwellized lag
probabilities when their reliability gate failed.

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
- weak valid posterior remains Success;
- the strict APP_RUNTIME policy fingerprint and thresholds remain frozen;
- the same retry attempt is reproducible and a new attempt changes chain seeds;
- RELAXED is offered only for the three sampling reliability failures;
- RELAXED success is visibly and structurally distinct from STRICT;
- the LAGGED_LAB route uses the strict coordinator/catalog;
- legacy analyzer/service construction is forbidden in `TrainingViewModel`;
- dashboard `performanceTrend.metricSeries` is forbidden as strict raw input.
