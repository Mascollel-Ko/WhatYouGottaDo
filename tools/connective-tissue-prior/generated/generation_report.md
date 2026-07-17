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
- Recovery-engine fingerprint: `8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a`
- Mapping-data fingerprint: `3b04e38df8ea80e376a683b1cba15481cb801f52b7668d5f506b98811b057742`
- Deterministic input checksum: `034fb720d24a2e1749baa4a40d0a78d8d12a866e4a654d8fbbca4a944e80d3d2`
- Deterministic output checksum: `ef6c1d274369e55849a2cee095262cba4b83cfccecf1bdf7968f458f6d77c4eb`
- Canonical registry SHA-256: `2b7b1bdc7453f42e896d19d049f64f55046215cf2c6ff08c305923adc7fe2ce2`

Scenario weights are product policy, not measured population prevalence.
Boundaries use the positive residual distribution; a fully recovered zero state remains below Q30.
Body-mass fitting reuses production ledger normalization. Negligible fitted effects are stored as zero.

| Profile | Units | Body beta | Body median/p95/max error | Intensity light/hard | Intensity median/p95/max error | Weight sensitivity |
|---|---:|---:|---|---|---|---:|
| `PRIOR_FASCIA_LOWER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0 | 0.021938 |
| `PRIOR_FASCIA_SPINE` | 1 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.020408 | 0.01459 |
| `PRIOR_FIBROCARTILAGE_LOWER` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.04 | 0.044041 |
| `PRIOR_FIBROCARTILAGE_UPPER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0 | 0.048606 |
| `PRIOR_FUNCTIONAL_UPPER` | 2 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.017605 | 0.046498 |
| `PRIOR_JOINT_LOWER` | 7 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.006588 | 0.046307 |
| `PRIOR_JOINT_SPINE` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.007309 | 0.021527 |
| `PRIOR_JOINT_UPPER` | 6 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.011713 | 0.020793 |
| `PRIOR_LIGAMENT_LOWER` | 11 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.005893 | 0.064706 |
| `PRIOR_LIGAMENT_UPPER_AXIAL` | 8 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0 | 0.033333 |
| `PRIOR_SPINAL` | 3 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.01484 | 0.045144 |
| `PRIOR_TENDON_LOWER` | 15 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.008482 | 0.040461 |
| `PRIOR_TENDON_UPPER` | 14 | 0 | 0 / 0 / 0 | 0.96 / 1.04 | 0 / 0 / 0.021289 | 0.031241 |

## Safety boundary

- The registry is generated and validated but is not consumed by current UI or classification.
- It does not estimate injury, damage, capacity, or exact biological recovery.
- `meaningfulFloor` is never profile-adjusted.
- Experience is `POLICY_BOUNDED`; it does not alter CurrentLoad or recovery.
