# Exercise identity reference audit

Independent read-only re-audit for v0.5.0.7.

## Baseline

- Branch: `codex/reaudit-v0507-exercise-canonicalization`
- Source main SHA: `102086c631fac584614be94d5e35c841cd3c6e57`
- App version: `0.5.0.7 / 500007`
- Room schema: `25`
- Backup format: `8`
- Program backup schema: `1`

## Result

- Normal application identity: `Exercise.stableKey` only.
- Exercise rows: `stableKey` is the Room primary key.
- Workout references: `WorkoutEntry.exerciseStableKey`.
- Program references: `TrainingProgramItem.exerciseStableKey`.
- Current numeric exercise identity columns: none.
- Runtime name/fuzzy/contains identity fallback: none.
- New backup numeric exercise identity columns: none.
- Active built-in exercises: `224`; blank or duplicate stableKeys: `0`.
- Runtime metadata rows: `224`, keyed by the same canonical stableKeys.
- Built-in program items: `753`; invalid canonical stableKey references: `0`.
- Workbook decisions: `26`; identity-reference violations: `0`.

## Runtime boundary

| Boundary | Current behavior | Result |
|---|---|---|
| `Exercise` | Canonical nonblank `stableKey` primary key | PASS |
| `WorkoutEntry` | Indexed nonblank `exerciseStableKey` reference | PASS |
| `TrainingProgramItem` | Indexed nonblank `exerciseStableKey` reference | PASS |
| DAO insert/update | Rejects blank exercise stableKeys | PASS |
| Metadata editor | Preserves stableKey on rename; user keys are UUID-based | PASS |
| Runtime metadata resolver | Exact stableKey catalog lookup | PASS |
| Program seed/runtime | Explicit canonical stableKeys | PASS |
| Backup format 8 | StableKey-only exercise identity | PASS |
| Current-format restore | Exact canonical stableKey resolution | PASS |
| Old-format import | Explicit `LegacyExerciseImportMapper` boundary only | PASS |

## Remaining legacy references

| Location | Remaining use | Classification |
|---|---|---|
| `app/src/main/assets/exercise_legacy_import_map.csv` | 33 exact old-key/name mappings | Allowed import-only compatibility. The workbook's 32 rows are present; the extra `imported_배드민턴 -> ex_ae9ecdbc` row is the reviewed restore hotfix. |
| `ExerciseStableKeyMigration.kt` | Reads pre-schema-25 numeric IDs and old keys | Required one-time migration input with explicit mapping or structured ambiguity. |
| `LegacyExerciseImportMapper.kt` | Resolves old backups | Allowed only at the old-format import boundary; no runtime injection. |
| `app/schemas/.../9.json` through `24.json` | Historical numeric `exerciseId` | Immutable Room migration evidence. |
| `app/schemas/.../25.json` | `sourceExerciseId` in migration diagnostics | Diagnostic source context, not runtime identity. |
| Migration/import tests | Old keys, ID 0, dangling IDs and aliases | Regression fixtures. |
| Historical docs and outputs | Pre-canonical names and IDs | Historical evidence, not executable authority. |
| `exercise_image_mapping.csv` and image asset names | Legacy labels for image recovery | StableKey-first presentation metadata; not exercise identity resolution. |

## Classified search findings

- No current runtime use of `Exercise::id`, `exercise.id`,
  `Map<Long, Exercise>`, `associateBy(Exercise::id)`, `findExerciseById`,
  `getExerciseById`, or `matched?.id`.
- `countProgramItemsForExercise` accepts a stableKey despite its broad method
  name.
- Numeric workout, set, program and program-item IDs remain local row identity,
  not exercise identity.
- Program UI `?: 0L` values are local unsaved row IDs, not exercise keys.
- `contains` matches remain in user search, token analysis and display helpers;
  none resolve exercise identity.
- Image-name fallback is bounded to image presentation recovery after stableKey
  lookup and cannot select, restore, analyze or persist an exercise.

## Conclusion

The current v0.5.0.7 runtime, metadata, program, migration and backup paths
consistently use canonical stableKeys. No implementation correction, schema
change, data regeneration, version bump or release tag is required.

Prior Gradle, protocol, canonicalization and APK validation was intentionally
not rerun for this read-only audit.
