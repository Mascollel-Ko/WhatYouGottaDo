# Analysis Fatigue Metric Consistency Report

## Summary

Fixed a consistency issue where visible low/zero fatigue metrics could still produce a high fatigue label because z-score or percentile fallback was allowed to dominate.

| Metric | Display value | Level input | Level condition | Fixed |
| --- | --- | --- | --- | --- |
| Baseline ratio | pressure ratio, or unavailable when baseline is missing | `pressure` | ratio below `0.75` now forces LOW | Yes |
| Residual fatigue | current residual load | `currentResidualLoad` | zero or floor-level residual now forces LOW | Yes |
| Fatigue level | LOW/NORMAL/ELEVATED/HIGH/VERY_HIGH | residual, pressure, z-score, percentile, confidence | z-score/percentile cannot override zero residual or low ratio | Yes |

## Files Modified

- `app/src/main/java/com/training/trackplanner/analysis/readiness/FatiguePressureCalculator.kt`
- `app/src/test/java/com/training/trackplanner/analysis/readiness/FatiguePressureCalculatorHotfixTest.kt`

## Cause

The previous implementation calculated the highest level from pressure, z-score, and percentile. If residual load was zero but old statistical values were high or stale, the final label could become HIGH even though the visible residual and ratio did not support it.

## Tests

- `zeroResidualWithHighStatsDoesNotBecomeHighFatigue`
- `lowRatioDoesNotBecomeHighFatigue`
- `missingBaselineWithZeroResidualDoesNotRenderAsHighRatio`
- `actualHighLoadCanStillBecomeHighFatigue`

## Remaining Risk

If a section displays a label from a non-pressure gate, the UI must still show that reason explicitly. No such additional hidden gate was changed in this hotfix.