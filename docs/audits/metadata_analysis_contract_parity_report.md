# Metadata analysis contract parity report

## Baseline

- Commit: `47f93eadaff64a49f6dc886a9319191c7388029c`
- Built-in exercises: 224
- Contract: `ANALYSIS_CONTRACT_BASELINE_V1`
- Production cutover: no

## Coverage

| Module | Stable keys covered | Frozen output |
|---|---:|---|
| OFI | 224 | dose basis, five raw axis probe contributions, comparison groups, rounded scores, OFI, readiness label, caution reasons |
| Program relation shadow | 224 | slot capability tiers, role eligibility, variant group, progression group |
| Muscle | 224 | exact current bucket membership and contribution coefficient, including explicit empty/disabled state |
| Badminton | 224 | transfer type/axis points, physical qualities, fatigue-cost category |
| Connective-tissue capability | 224 | exact membership in the existing reviewed RCV catalog; no tissue coefficients copied |

Every built-in stable key has an explicit capability row for all five analysis
types. Shadow output parity covers the four modules named by Phase 1; the
connective-tissue row only confirms exact catalog coverage. Multi-membership is
retained as multiple rows.

## Golden fixtures

The per-exercise probe uses three confirmed sets with 20 kg, 8 repetitions,
600 seconds, and RPE 8 on 2026-01-15. The same fixed input is sent through the
current OFI calculator for every stable key. This fixture is a regression
oracle, not a recommended prescription.

Program golden scenarios cover:

- 5 days / 4 weeks / 45 minutes / badminton support;
- 3 days / 4 weeks / 45 minutes / strength request;
- 4 days / 8 weeks / 60 minutes / badminton support.

The current deterministic builder normalizes unsupported request dimensions;
the golden records the resulting request behavior rather than redefining it.

## Shadow result

`AnalysisContractBaselineTest.baselineAssetMatchesCurrentBuiltInOracle`
regenerates all typed relations from current effective behavior, parses both
the generated and committed assets into typed repositories, and runs
`AnalysisContractShadowParity`.

Expected result:

```text
OFI diffs: 0
Program relation diffs: 0
Muscle diffs: 0
Badminton diffs: 0
```

The same test also requires byte-equivalent normalized CSV output so row
ordering and provenance drift are visible.

## Remaining legacy inference

The legacy path still contains delimiter parsing, partial string matching,
name matching, stable-key matching, and fallback classification. It remains
the production oracle in this phase. The complete inventory is:

- `docs/audits/metadata_parsing_inference_audit.csv`
- `docs/audits/metadata_parsing_inference_audit.md`

The new contract package is guarded by `AnalysisContractArchitectureTest`.
Unknown user mappings remain incomplete; no fallback guess is introduced.

## Known gaps before cutover

- Production program generation currently uses deterministic rule tables and
  does not consume the new relation repository.
- User-created typed relations are projected in memory and are not persisted.
- Joint-action authority is not available in current canonical runtime metadata;
  no joint action is inferred.
- Full analyzer cutover, user-facing incomplete-metadata UI, relation editors,
  and old-field deletion are deferred.

These are cutover blockers, not hidden parity tolerances.
