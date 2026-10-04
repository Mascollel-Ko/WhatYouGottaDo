# Connective-tissue prior-baseline generation report

- Status: `DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE`
- Production protocol: `RCV-ALL-0.6|RCV-EXPOSURE-1.2`
- Generator: `CT-PRIOR-GENERATOR-1.0.0`
- Scenario catalogue: `CT-PRIOR-SCENARIOS-1.0.0`
- Load units: `77` (100% explicit stable-key coverage)
- Prior profiles: `13`
- Scenario templates: `8`
- Simulation: `112` days, `56`-day burn-in
- Evaluation buckets: local hours `0..23`, reference zone `Asia/Seoul`
- Generated quantiles: `936`
- Simulation validations: `13/13` profiles
- Slower recovery retains more at 24 hours: `true`
- Recovery-engine fingerprint: `347d3e89755094e7ecad4e981fe33b5ab1fd19962f45335a02d55591d25d867d`
- Mapping-data fingerprint: `613646e2385100d7cda1fd69aae9f98703076057b73d5f17c8b5a86bbaf0a1f4`
- Deterministic input checksum: `f95bc0140cd50c72116cb717e1f21caf7c5aed901bddd704ef4545b2e61b49c6`
- Deterministic output checksum: `1e581bf90184cee77c196530f0de8df1af8b836f121b355f7f0700dbe8266fc6`
- Canonical registry SHA-256: `adfcf91b63df9b196f50a9789630444131f192928b3d3ebe3bf756aa732cdabc`

Scenario weights are product policy, not measured population prevalence.
Boundaries use the positive residual distribution; a fully recovered zero state remains below Q30.
Body-mass fitting reuses production ledger normalization. Negligible fitted effects are stored as zero.

| Profile | Units | Body beta | Body median/p95/max error | Intensity light/hard | Intensity median/p95/max error | Weight sensitivity |
|---|---:|---:|---|---|---|---:|
| `PRIOR_FASCIA_LOWER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0 | 0.021938 |
| `PRIOR_FASCIA_SPINE` | 1 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.022921 | 0.045623 |
| `PRIOR_FIBROCARTILAGE_LOWER` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.04 | 0.044041 |
| `PRIOR_FIBROCARTILAGE_UPPER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0 | 0.082278 |
| `PRIOR_FUNCTIONAL_UPPER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.019155 | 0.053508 |
| `PRIOR_JOINT_LOWER` | 7 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.010782 | 0.031964 |
| `PRIOR_JOINT_SPINE` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0.002866 / 0.014843 | 0.036667 |
| `PRIOR_JOINT_UPPER` | 6 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.001276 | 0.080163 |
| `PRIOR_LIGAMENT_LOWER` | 11 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0.023579 / 0.032549 | 0.05537 |
| `PRIOR_LIGAMENT_UPPER_AXIAL` | 8 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.0208 | 0.041353 |
| `PRIOR_SPINAL` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0.000127 / 0.013156 | 0.049775 |
| `PRIOR_TENDON_LOWER` | 15 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.003331 | 0.037726 |
| `PRIOR_TENDON_UPPER` | 14 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0.005803 / 0.04 | 0.033248 |

## Safety boundary

- The registry is generated and validated but is not consumed by current UI or classification.
- It does not estimate injury, damage, capacity, or exact biological recovery.
- `meaningfulFloor` is never profile-adjusted.
- Experience is `POLICY_BOUNDED`; it does not alter CurrentLoad or recovery.
