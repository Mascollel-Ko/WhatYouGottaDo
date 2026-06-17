# Program Tab Summary and Apply Report

## Changes

- Program list cards now expose an `?곸슜` button in the default card state.
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
- `?먮룞?쇰줈 怨⑥옄 留뚮뱾湲? remains available for new programs.
- `?꾨? ?덈줈 留뚮뱾湲? remains available for new programs.
- Program edit still loads existing program items through `skeletonFromProgram`.
- Applying a program still uses `TrainingViewModel.applyProgram`.
- Conflict handling still uses `loadProgramApplyConflictSummary`.

## Detail Summary Format

Program detail rows are rendered as text:

```text
- Exercise name: 3?명듃 x 8?? 60kg, ?댁떇 90珥? prescription memo
```

Empty days are shown as:

```text
- ?댁떇 ?먮뒗 誘몄???```