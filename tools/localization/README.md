# Application localization audit

Run the deterministic inventory with the bundled Python runtime:

```powershell
python tools/localization/localization_authority.py --write
python tools/localization/localization_authority.py
python tools/localization/localization_audit.py --write
python tools/localization/localization_audit.py
python -m unittest discover -s tools/localization/tests
```

`current_baseline_android_en.csv` is the deterministic source for approved
English Android resources that postdate the frozen workbook. Generation checks
each row against the current default-resource key and Korean source text.

The audit inventories default and English Android resources, Korean string
literals in production Kotlin, and all current/history canonical exercise
names. The approved v2 workbook, metadata catalogue, exact resources, dynamic
templates, and checked-in `CODEX_GENERATED_ENGLISH` baseline rows are accepted
presentation routes. The completion gate is zero unexplained Korean text in
English-mode production presentation, not the superseded per-row approval gate.

The Kotlin scan intentionally gates clear UI/accessibility literals while
retaining explicit diagnostic, log, canonical-data, and identifier
classifications in the review artifact. Narrow classification changes must be
reviewable in this script rather than hidden in generated CSV files.
