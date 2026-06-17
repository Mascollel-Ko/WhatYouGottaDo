# Initial Adaptation RPE Cleanup Report

## Summary

Badminton RPE is no longer an initial-profile input or adaptation-score input.

| Input | Direction | Internal calculation use |
| --- | --- | --- |
| strengthAverageRpe | higher means stronger recent strength intensity | used in resistance adaptation and activity adaptation |
| badmintonAverageRpe | removed/unused | not used in `badmintonAdaptationScore` |
| sleepQuality | higher means better | raises recovery capacity |
| currentFatigue | higher means better energy | lowers fatigue penalty through higher recovery capacity |
| currentSoreness | higher means less soreness | lowers soreness penalty through higher recovery capacity |
| currentStress | higher means less stress | lowers stress penalty through higher recovery capacity |
| currentCondition | higher means better | raises recovery capacity |

## Files Modified

- `app/src/main/java/com/training/trackplanner/InitialProfileDialog.kt`
- `app/src/main/java/com/training/trackplanner/analysis/readiness/InitialAdaptationProfile.kt`
- `app/src/test/java/com/training/trackplanner/analysis/readiness/InitialProfileColdStartReadinessTest.kt`

## Compatibility

The `badmintonAverageRpe` DB field remains for older data and backup compatibility. New UI saves keep the previous value without collecting new input, and the calculation ignores it.

## Tests

- `InitialProfileColdStartReadinessTest.badmintonAverageRpeDoesNotAffectBadmintonAdaptation`
- `InitialProfileColdStartReadinessTest.structuredProfileProducesInitialAdaptationScores`