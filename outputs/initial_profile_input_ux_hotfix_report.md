# Initial Profile Input UX Hotfix Report

## Summary

Updated `InitialProfileDialog.kt` so cold-start profile inputs are easier to enter and harder to save incorrectly.

## Changes

- Birth year now uses a digits-only field that allows intermediate input states such as `1`, `19`, `190`, and `1990`.
- Birth year final validation runs only on save and requires a 4-digit year between `1900` and the current year.
- Strength average RPE changed from chip selection to numeric input.
- Strength RPE final validation requires an integer from `1` to `10`.
- Badminton RPE was removed from the initial profile UI.
- Sleep quality, current fatigue, soreness, stress, and condition moved from chip selection to sliders.

## Files Modified

- `app/src/main/java/com/training/trackplanner/InitialProfileDialog.kt`

## Cause

The old numeric field validated range during every keystroke, so partial birth-year input could be rejected before the user finished typing. The chip-based RPE/recovery UI also did not match the requested UX.

## Tests

- Targeted unit tests passed:
  - `FatiguePressureCalculatorHotfixTest`
  - `PerformanceDropDetectorHotfixTest`
  - `InitialProfileColdStartReadinessTest`
  - `RecordCsvBackupRestoreTest`
- Compose UI was compile-checked through unit-test and android-test build tasks.

## Remaining Risk

No interactive device UI test was run in this environment. Behavior was validated by code path and successful Compose compilation.