# V2-2 real-history validation

This directory preserves the exact Python v1.0 reference modules supplied with
the V2-2 bundle and the deterministic validation runner used for the
2026-10-12 backup.

The original backup is never edited or copied into the repository. The runner
creates a reduced gzip fixture containing only the twelve columns consumed by
V2-2 and only confirmed strength/functional set rows.

```powershell
python tools/v22-validation/run_v22_real_history_validation.py `
  --backup "C:\path\to\whatyougottado_backup_2026-10-12.csv" `
  --repo .

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.training.trackplanner.data.v22.V22RealHistoryMatrixTest" `
  --tests "com.training.trackplanner.data.v22.V22ProgramIntegrationTest"
```

Generated, reviewed artifacts are under
`docs/validation/v22-real-history-2026-10-12/`. The matrix test compares all
528 Kotlin results against the compact Python semantic results, including the
training-day inference evidence, block boundaries, exercise order, observed
set shapes, role totals, and reallocations.

Reference SHA-256 values:

- `v2_2_generation_v05.py`: `9ea0623f2464db9941028fd78436627cba71b5618b3e5c2168fb458e0dd17142`
- `v2_2_v10_python_engine.py`: `70a5c3fa20f4cd563fd8ea3336c2dccc41c54732e41dfc756e2aa599a4777eeb`
