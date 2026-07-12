# Phase A time-series reference fixtures

This folder contains the independent NumPy/SciPy reference generator for the
Phase A numeric foundation.

Run from the repository root:

```powershell
python tools/time_series_reference/generate_phase_a_fixtures.py
```

The generated fixtures are deterministic for the fixed RNG seed and include
Python, NumPy, SciPy, schema, seed, tolerance, and source commit provenance.
