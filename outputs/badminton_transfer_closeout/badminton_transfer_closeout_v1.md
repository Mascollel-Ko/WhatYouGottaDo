# Badminton transfer metadata closeout v1

Status: ARTIFACT_ONLY
Date: 2026-08-04

## Result

The existing badminton-transfer metadata was reconciled to the latest 241-row canonical exercise inventory without changing production behavior.

| Measure | Count |
|---|---:|
| Existing populated source rows | 224 |
| Final canonical relation rows | 241 |
| Retained canonical identities | 208 |
| Equipment-variant inheritance rows | 33 |
| Legacy alias migration rows | 21 |
| Historical generic source identities | 16 |
| Legacy aliases ending at historical generics | 4 |
| Unresolved identities | 0 |
| Orphan authoritative relations | 0 |
| Validation PASS | 40 |
| Validation REVIEW_REQUIRED | 2 |
| Validation FAIL | 0 |

## Preserved runtime contract

- Source metadata remains separate from the seven runtime-derived display axes.
- Source transfer levels remain `DIRECT`, `SUPPORTIVE`, `GENERAL`, and `NONE`; runtime `LOW` remains a fallback and is not promoted into the source registry.
- Transfer weights, RPE factors, 7-day/28-day windows, equal axis splitting, fatigue-cost logic, recommendations, charts, colors, and UI are unchanged.
- Equipment splits inherit broad transfer metadata only. Fatigue cost remains runtime-derived and historical generic records are not auto-migrated.

## REVIEW_REQUIRED

1. **Legacy dependency replacement gaps:** 15 audited compatibility dependencies require future row-level parity work before runtime cleanup.
2. **Korean definition gaps:** 95 canonical code definitions remain untranslated/unapproved. Codes and existing display labels are preserved unchanged.

These items do not block the identity closeout and were not converted into PASS.

## Authority

- `badminton_transfer_canonical_registry_v1.csv`: denormalized machine-reviewable relation authority for this closeout.
- `badminton_transfer_stablekey_migration_v1.csv`: identity lineage and import resolution.
- `badminton_transfer_inheritance_review_v1.csv`: equipment split decisions.
- `badminton_transfer_legacy_dependency_audit_v1.csv`: future runtime migration plan.
- `badminton_transfer_validation_v1.csv`: deterministic executed checks.
- `WhatYouGottaDo_배드민턴_전이_메타데이터_정본_v1.xlsx`: human-review companion; CSV and Markdown remain machine authority.
