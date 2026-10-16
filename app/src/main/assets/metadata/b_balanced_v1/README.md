# B-balanced target task overlay v0.1 (staged)

This is an **additive specificity dictionary**, not the canonical workbook authority.
It is staged for future B-balanced quality/region/task selection and not currently
loaded by Kotlin production planning or the frozen Python v1.0 reference.

- `targetDomain=MOVEMENT` disambiguates hip adduction from hip abduction even
  though historical workbook jointAction contains both tokens.
- `targetDomain=CORE` refines the existing directly trained `BRACING` superclass
  into anti-rotation, anti-extension, anti-lateral-flexion and dynamic-trunk
  tasks using already present canonical `movement_relations` evidence.
- Multiple task rows for one exercise are *not* separate physical sets, and
  `numericDoseAuthority=NONE` prohibits generating new or credited training
  sets from this overlay alone.
- This does **not** expand the exact approved maximal-strength identity list.
- Copenhagen plank direct-core status and hip legacy `BOUND`/muscle primary
  corrections remain under source-workbook and consumer impact review.
- Before activation: add a typed runtime reader, confirm stableKey identity,
  gate interpretation by version, run B dose-ledger/provenance regressions,
  and separately approve any numeric prescription authority.

See `docs/audits/b_balanced_semantic_review_v0_2.md` for evidence and
`tools/metadata_authority/tests/test_b_balanced_target_task_relations.py` for assertions.
