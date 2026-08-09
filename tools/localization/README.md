# Application localization audit

Run the deterministic inventory with the bundled Python runtime:

```powershell
python tools/localization/localization_audit.py --write
python tools/localization/localization_audit.py
python -m unittest discover -s tools/localization/tests
```

The audit inventories default and English Android resources, Korean string
literals in production Kotlin, and active built-in exercise names. It does not
invent translations. `MISSING_APPROVED_ENGLISH` and
`MISSING_APPROVED_ENGLISH_NAME` are Translation Gate inputs.

The Kotlin scan intentionally gates clear UI/accessibility literals while
retaining explicit diagnostic, log, canonical-data, and identifier
classifications in the review artifact. Narrow classification changes must be
reviewable in this script rather than hidden in generated CSV files.
