# Canonical metadata authority tools

The checked-in XLSX is the authoring source. Android never reads XLSX at runtime.

```powershell
python tools/metadata_authority/validate_authority_workbook.py --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx
python tools/metadata_authority/export_canonical_metadata.py --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx --output app/src/main/assets/metadata/canonical_v1
python tools/metadata_authority/export_canonical_metadata.py --check --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx --output app/src/main/assets/metadata/canonical_v1
python -m unittest discover -s tools/metadata_authority/tests
```

The exporter uses UTF-8, LF newlines, stable row ordering, stable JSON key ordering, and no current timestamps. `--check` regenerates into a temporary directory and compares every byte.

Edit the checked-in workbook, run the validator and exporter, review the generated diff, then run tests. Commit the workbook and generated assets together; never hand-edit generated CSV or JSON files.
