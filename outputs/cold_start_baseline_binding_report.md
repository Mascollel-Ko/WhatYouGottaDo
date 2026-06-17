# Cold-start Baseline Binding Report

## Flow

`InitialUserProfile`
-> `InitialAdaptationProfile`
-> `ColdStartBaseline`
-> `ReadinessScoreAdjustment`
-> `Ready / Normal / Caution / Fatigued`

The app status enum currently uses `READY`, `CAUTION`, `FATIGUED`, and `LIMITED`. v0.3.4.4.3 does not replace that enum. The requested "Normal" behavior is handled as a less conservative `READY/CAUTION` threshold effect inside the existing status system.

## Binding Points

- Initial profile is read in `TrainingRepository.todayReadinessSummary()`.
- It is passed through `TodayReadinessEngineInput.initialProfile`.
- `InitialProfileReadinessAdjuster.adaptationFor()` builds `InitialAdaptationProfile`.
- `InitialProfileReadinessAdjuster.adjustBaseline()` blends cold-start tolerances into `AdaptiveBaselineSnapshot` before `FatiguePressureCalculator`.
- `InitialProfileReadinessAdjuster.adjustSummary()` can still adjust the final summary, but it is no longer a fatigued-only downgrade filter.

## Blending

- Very few completed training days: profile weight is high.
- 2-6 week baseline-building range: profile and record baseline are mixed.
- 6+ week range: record baseline is dominant.
- 60+ day gap: returning mode increases profile/recent-state weight again.

## Replacement of Old Behavior

The old pattern:

`if summary.status != FATIGUED return summary`

has been removed.

The new behavior can affect:

- `READY -> CAUTION` when profile recovery/detraining/restriction signals are conservative.
- `CAUTION -> READY` when profile adaptation is high and pressure ratio is not high.
- `CAUTION -> FATIGUED` when conservative profile signals and high pressure align.
- `FATIGUED -> CAUTION` when cold-start pressure is mostly a baseline artifact and the profile is strongly adapted.

Internal scores and weights are not shown in the normal UI.
