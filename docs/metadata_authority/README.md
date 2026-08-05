# Canonical Exercise Metadata Authority

`WhatYouGottaDo_metadata_authority_v1.xlsx` is the single human-editable
authoring source for bundled exercise metadata. Android never reads XLSX.

## Publishing workflow

```text
Edit workbook
-> run validator/exporter
-> review generated diff
-> run tests
-> commit workbook and generated assets together
```

Run from the repository root with the pinned dependency in
`tools/metadata_authority/requirements.txt`:

```powershell
python tools/metadata_authority/validate_authority_workbook.py --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx
python tools/metadata_authority/export_canonical_metadata.py --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx --output app/src/main/assets/metadata/canonical_v1
python tools/metadata_authority/export_canonical_metadata.py --check --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx --output app/src/main/assets/metadata/canonical_v1
python -m unittest discover -s tools/metadata_authority/tests
```

The exporter is deterministic: stable primary-key sorting, UTF-8, LF
newlines, no timestamps, and a SHA-256 manifest. Generated CSV/JSON assets
must not be hand-edited.

## Korean display terminology authority

`30_METADATA_DISPLAY_LABELS` is the human-editable authority for metadata
display text. Its primary key is `(displayField, canonicalCode)`. The sheet
stores the Korean primary, short, formal, and help labels; Korean and English
search aliases; source tier and references; the Latin-token allowlist; scope;
review state; rationale; and migration notes.

The Korean terminology source order is official Korean anatomy/medical usage,
Korean sports-science usage, then established Korean professional usage.
When an official term differs from the term Korean trainees commonly search,
the common term is the primary label, the official term is retained as the
formal label, and both resolve through aliases. Latin text is rejected unless
the row explicitly allows it. `ESTIMATED_1RM` is the product-owner exception:
its primary label remains exactly `e1RM`.

The exporter generates these presentation files from the sheet:

```text
app/src/main/assets/metadata/canonical_v1/metadata_display_labels_ko.csv
app/src/main/res/values/metadata_display_catalog.xml
app/src/main/res/values-en/metadata_display_catalog.xml
docs/metadata_authority/metadata_display_resource_manifest.json
```

`MetadataDisplayCatalogue` is the only typed boundary for canonical metadata
shown by the Android UI. It displays the active locale while search also
matches canonical codes and both locales' labels and aliases. A production
code with no registry row is a validation failure, not a release fallback.

The publishing workflow is therefore:

```text
Edit 30_METADATA_DISPLAY_LABELS
-> validate terminology coverage
-> generate CSV/XML
-> review the diff
-> run tests
-> commit the workbook and generated files together
```

## Scope contract

Workbook rows distinguish `PRODUCTION_CANONICAL`, `LEGACY_COMPATIBILITY`,
`HISTORY_ONLY`, `RESEARCH_DRAFT`, `DEFERRED_REVIEW`, `NOT_ADJUDICATED`, and
`INTENTIONALLY_NONE`. Runtime production assets exclude research, deferred,
and not-adjudicated rows. The relationship conflict report is explicitly
`NOT_ADJUDICATED`; this release does not claim full relationship correctness.

The 16 `HISTORY_ONLY_GENERIC` identities remain readable, are bootstrapped as
inactive, and cannot enter planning. Their old stableKeys are never inferred
or rewritten to equipment variants. Built-in program seeds containing one are
skipped as a whole.

`researchContextAxisScoreC` is research evidence. It is not the runtime
`runtimeCodContextModifier`; the latter remains in the separately reviewed
tissue runtime contract and stays within its approved maximum of `1.09`.
Research draft tissue rows are not exported into `canonical_v1`.

## Runtime ownership and overrides

`CanonicalExerciseMetadataRepository` owns strict bundled asset loading and
exact stableKey joins. `SeedData` delegates production bootstrap to it.
`ExerciseMetadataMapper` remains only for explicit legacy/import compatibility
and parity tests; it is not a bundled metadata fallback.

Canonical scientific relations and canonical stableKey lineage are protected
from ordinary runtime overrides. Custom exercises and user-authored fields
continue to use the existing persistence path.

## Rollback

Revert the application commit to restore the previous generated asset path and
bootstrap behavior. Room remains schema 27, no destructive migration runs,
and no workout, program, custom exercise, or history-only stableKey is
rewritten by this cutover.

For a display-only rollback, revert the workbook, generated display CSV/XML,
manifest, catalogue, and UI-routing changes together. Do not edit generated
resources independently and do not migrate persisted canonical codes.
