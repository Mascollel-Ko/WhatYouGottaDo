# Connective-tissue prior-baseline generation report

- Status: `DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE`
- Production protocol: `RCV-ALL-0.6|RCV-EXPOSURE-1.1`
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
- Recovery-engine fingerprint: `07a5da10fdd1a1e1069883f0e9da3f893edb4cdbb3317ce93629a1c1224a05dd`
- Mapping-data fingerprint: `087e64ed5c0d38a799ac1fbe9ea260c5119220c4e962a9c547f913423abe1ab7`
- Deterministic input checksum: `a6a90344c1f3ad05500d2101fe7ce8f3ce78df7d328b14d422ceee013819b5ea`
- Deterministic output checksum: `a8b2c8fd6c50fd73111a6fca074521670089a0f2c578f39f397d5fe43f73f58e`
- Canonical registry SHA-256: `0096441bfd923a96949a2df1bdce0f63c7fed6d8c611a854e9df0983b9109725`

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
