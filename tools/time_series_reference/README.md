# Phase A time-series reference fixtures

This folder contains the independent NumPy/SciPy reference generator for the
Phase A numeric foundation.

Run from the repository root:

```powershell
& 'C:\Users\pki08\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/time_series_reference/generate_phase_a_fixtures.py
```

The generated fixtures are deterministic for the fixed RNG seed and include
Python, NumPy, SciPy, schema, seed, tolerance, and source commit provenance.
The Phase A fixtures cover standard condition number behavior, strict versus
bounded-jitter Cholesky, full-spectrum generalized eigen validation, and
calendar grid lifecycle cell states.

Run the companion source scan from the repository root:

```powershell
& 'C:\Users\pki08\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/check_time_series_numeric_sources.py
```
