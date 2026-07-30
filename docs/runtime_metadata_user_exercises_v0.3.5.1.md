# Runtime metadata for user exercises (v0.3.5.1)

## Scope

v0.3.5.1 extends the existing runtime metadata architecture to exercises created or edited in the app. It does not introduce a second metadata system and does not reclassify the 215 built-in exercises. Legacy external CSV and `daily_timeseries` compatibility are out of scope.

`RuntimeExerciseMetadata` remains the single metadata representation consumed by readiness analysis, daily fatigue, performance trends, badminton analysis, recommendations, and program generation.

## Identity and stableKey

- Existing built-in `stableKey` values are immutable. Seed refresh preserves the installed key and user visibility/archive state.
- A new user exercise receives `user_ex_<UUID>`. The key is never derived from its display name.
- Editing an exercise preserves its key even when its name changes.
- Custom exercises are excluded from seed merge, so a seed row with the same display name cannot overwrite them.
- Startup repairs a blank key only when `isCustom = true`, using the same UUID format.
- `exercises.stableKey` keeps its existing unique index. Insert retries handle the unlikely UUID collision.

## Room schema

Database version 13 adds `runtime_exercise_metadata`, keyed by `stableKey`. `RuntimeExerciseMetadataEntity` has exactly the same 34 logical properties as `RuntimeExerciseMetadata`.

The only persistence differences are Room annotations and a `MetadataTokenField` type converter. The converter stores both the original raw token string and the parsed ordered values, so conversion in either direction is lossless.

Conversions are explicit:

```text
RuntimeExerciseMetadata.toEntity()
RuntimeExerciseMetadataEntity.toRuntimeMetadata()
```

Migration `12 -> 13` only creates the metadata table. It does not rewrite exercises, workout records, user records, or canonical assets.

## Resolution contract

Every consumer receives the same `RuntimeExerciseMetadata` type through this order:

```text
Room runtime metadata override by stableKey
  -> canonical built-in catalog by stableKey
  -> conservative runtime default
```

The default does not parse the exercise name. It uses low stress, no badminton transfer, explicit `NOT_APPLICABLE` classification fields, heuristic/limited source confidence, and `safeForSeedMutation = false`.

Repository entry points create a resolved catalog before invoking:

- readiness and fatigue analysis
- performance trend analysis
- badminton transfer analysis and recommendation
- program generation
- Analysis V3 input collection

`RuntimeExerciseMetadata` is authoritative for analysis, recommendation, and program behavior. Existing fields on `Exercise` remain for identity, display, filtering, current storage compatibility, and restricted legacy fallback when no resolved runtime field can supply older mapper data.

## New and edit UI

The exercise list now exposes `운동 추가`, and management mode exposes `수정`. Both open one full-screen editor with collapsible sections:

1. Basic exercise identity
2. Program and analysis eligibility
3. Movement classification
4. Stress and fatigue metadata
5. Badminton transfer metadata
6. Source/confidence/status metadata
7. Advanced runtime fields

Single-value metadata uses searchable single selection. Token fields use searchable multi-selection and chip summaries. Name, category, description, and rest time use existing basic fields. `stableKey` is read-only and generated on first save. `safeForSeedMutation` is persisted but fixed to `false` in this user-edit flow.

Saving the editor updates the `Exercise` identity row and the matching runtime metadata row in one Room transaction. Reopening resolves the saved Room row first, restoring all metadata values.

## Presentation code and label boundary

Starting with v0.5.0.14, metadata selectors keep canonical values and localized
labels separate:

```text
stored identity and callback: PROGRAM_SELECTABLE
Korean presentation: 프로그램에 사용 가능
English presentation: Available for programs
```

One field-aware display catalogue supplies Korean and English labels, sorting,
and Korean/canonical/English search aliases. The editor, information dialog,
and copy dialog all use this catalogue. Existing readable Korean seed values
remain unchanged. Unknown nonblank values remain selectable and persist
unchanged; the UI identifies them as `등록되지 않은 값 · ORIGINAL_CODE`.

Localization is presentation-only. It does not rewrite Room, canonical assets,
runtime analysis inputs, program-builder inputs, backup rows, or restore rows.
Adding another locale therefore requires no database or backup migration.

The affected UI uses label-above-value selectors, grouped information sections,
wrapping multi-select chips, vertical scrolling, and reachable actions at narrow
widths and larger font scales. Localization work includes this responsive review
rather than string replacement alone.

## Verification

Focused tests cover:

- UUID stable key generation and edit preservation
- built-in seed key preservation and custom seed exclusion
- exact runtime/entity logical field set
- lossless entity round-trip and token conversion
- Room override, canonical, and conservative-default resolver order
- restored user metadata by generated key
- program generation using resolved Room override metadata
- Room migration `12 -> 13` table validation
- localized label completeness across editor defaults, enums, and canonical CSV
- canonical-code callbacks, unknown-value preservation, and multilingual search
- 320dp / 1.5 font-scale Compose presentation with reachable editor actions

Metadata reclassification, built-in catalog regeneration, legacy external import changes, and a separate user metadata taxonomy were deliberately not performed.

## Analysis contract shadow boundary

Starting with v0.5.0.16, persisted scalar values and
`MetadataTokenField.values` may be projected into typed analysis relations for
shadow validation. Missing OFI, muscle, program, or connective-tissue
relationships remain `INCOMPLETE`; they are never guessed from the exercise
name, stableKey fragments, or raw delimiters. The existing runtime metadata
resolver and production calculators remain authoritative.

Verification completed for this change:

- required focused unit tests: passed
- affected readiness, fatigue, badminton, trend, and program tests: passed
- debug unit suite: 99 existing tests passed; the only stale 214-row assertion was corrected to the canonical 215-row contract and its focused rerun passed
- migration Android test sources: compiled successfully
- `assembleDebug`: passed
- APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\v0.3.5.1-TrainingTrackPlanner-debug.apk`
