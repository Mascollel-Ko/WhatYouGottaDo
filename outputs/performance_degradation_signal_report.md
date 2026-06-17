# Performance Degradation Signal Report

## Summary

Reduced overstatement in the performance degradation signal. A single small e1RM decrease no longer creates `ELEVATED`.

| Signal | Current weight/threshold | Existing problem | Fix direction |
| --- | --- | --- | --- |
| e1RM drop | small `>=5%`, large `>=8%` | one small drop was treated like a full degradation reason | one small drop is observation only; large/multiple drops or combined signals are required |
| Volume/reps drop | same-load reps drop `>=15%` | appropriate as a stronger signal | kept as a reason |
| RPE increase | same-load RPE increase `>=2` | appropriate as a stronger signal | kept as a reason |
| Consecutive/multiple drops | not explicitly counted before | no distinction between isolated and repeated e1RM movement | added small/large e1RM counters |
| Final level | one reason used to become ELEVATED | too sensitive with sparse data | one reason is NORMAL unless enough records exist; multiple reasons can become ELEVATED/HIGH |

## Files Modified

- `app/src/main/java/com/training/trackplanner/analysis/readiness/PerformanceDropDetector.kt`
- `app/src/test/java/com/training/trackplanner/analysis/readiness/PerformanceDropDetectorHotfixTest.kt`

## Tests

- `singleSmallEstimatedOneRepMaxDropDoesNotBecomeElevated`
- `multiplePerformanceSignalsCanBecomeElevated`

## Remaining Risk

The detector still compares the latest record to the previous record per exercise. More robust trend detection can be deferred to a future analytics patch.