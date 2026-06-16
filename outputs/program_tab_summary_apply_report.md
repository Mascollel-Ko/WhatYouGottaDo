# Program Tab Summary and Apply Report

## Changes

- Program list cards now expose an `적용` button in the default card state.
- The apply button opens the existing program apply flow instead of duplicating application logic.
- Existing start-date parsing, conflict summary, append mode, and overwrite mode remain in `ProgramApplyCard`.
- Program detail view now shows day-by-day text summaries.
- Program detail view no longer exposes the apply card or inline item editor.
- Existing program edit screen hides auto skeleton generation controls.
- New program creation still keeps the existing auto skeleton and rebuild controls.

## Files

- `app/src/main/java/com/training/trackplanner/PlanScreen.kt`

## Preserved Behavior

- Program creation still requires a program name.
- `자동으로 골자 만들기` remains available for new programs.
- `전부 새로 만들기` remains available for new programs.
- Program edit still loads existing program items through `skeletonFromProgram`.
- Applying a program still uses `TrainingViewModel.applyProgram`.
- Conflict handling still uses `loadProgramApplyConflictSummary`.

## Detail Summary Format

Program detail rows are rendered as text:

```text
- Exercise name: 3세트 x 8회, 60kg, 휴식 90초, prescription memo
```

Empty days are shown as:

```text
- 휴식 또는 미지정
```

