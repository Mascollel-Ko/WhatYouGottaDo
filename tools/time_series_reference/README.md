# Phase A time-series reference fixtures

This folder contains the independent NumPy/SciPy reference generator for the
Phase A numeric foundation.

Run from the repository root:

```powershell
& 'C:\Users\pki08\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/time_series_reference/generate_phase_a_fixtures.py
```

The generated fixtures are deterministic for the fixed RNG seed and include
Python, NumPy, SciPy, statsmodels, schema, seed, tolerance, and source commit
provenance.
The Phase A fixtures cover standard condition number behavior, high-condition
SPD rejection, exact and numerical rank-deficient PSD rejection, strict versus
bounded-jitter Cholesky, strict Johansen-form provenance, regularized
generalized eigen validation against an effective `B`, calendar grid lifecycle
cell states, metric-bound filtering, explicit activation-policy cases,
quality-summary denominator rules, unique-cell unusable counting with
overlapping diagnostic categories, structural-zero activation boundaries,
observation conflicts, explicit revision-ordering schemes, input-order
invariant conflict resolution, lifecycle metadata fingerprint preservation,
prepared common-row identity, full-calendar transformation preservation,
prepared-series screening contracts, final-week horizon exclusion,
transformation-plan-before-selection, role-aware horizon row requirements,
training-row-only scaling, restricted-view identity, and lifecycle semantic
validation. The integration reference fixture records statsmodels-compatible
constant-only ADF/KPSS intermediate values, the 32-week contiguous segment
minimum, supported I(0)/I(1) decisions, inconclusive singular/constant cases,
strict 1..8 horizon contracts, explicit no-horizon row identity, and declared
scaling failure modes. The contract fixture also records the final strict stage
order, segment boundaries and conservative aggregation, estimator
representations, required/optional inconclusive policies, draw-wise response
reconstruction, BVAR posterior source identity, row-domain multi-draw shock
posterior shape, sole row/scaling authorities, and PHASE E ownership of optional
statistical ranking.

`phase_b_bvar_reference.json` adds the PHASE B independent conjugate BVAR
reference values. It records raw stationary synthetic systems, the common
row-plan standardized sample, design matrix orientation, zero-mean
Minnesota-style prior constants, Matrix-Normal Inverse-Wishart posterior
parameters, exact log marginal likelihoods, joint and marginal lag/lambda
posterior weights, fixed covariance Cholesky structural shocks, reconstruction
values, and variable-order sensitivity. It is a reference-only fixture and is
not used by Android runtime code.

Run the companion source scan from the repository root:

```powershell
& 'C:\Users\pki08\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/check_time_series_numeric_sources.py
```
