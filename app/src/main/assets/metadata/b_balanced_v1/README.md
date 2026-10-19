# B-balanced target task overlay v0.2 (reviewed, non-prescriptive)

This is an **additive specificity dictionary**, not the canonical workbook authority.
It is staged for future B-balanced quality/region/task selection and not currently
loaded by Kotlin production planning or the frozen Python v1.0 reference.

- `targetDomain=MOVEMENT` preserves the reviewed hip adduction/abduction target
  independently from stable-key identity and historical record links.
- `targetDomain=CORE` audits all 31 canonical `CORE_CLASS=DIRECT` exercises and
  refines their existing superclasses into anti-extension, anti-rotation,
  anti-lateral-flexion, axial bracing, dynamic trunk stabilization, rotation
  generation, trunk flexion, or trunk extension tasks.
- Multiple task rows for one exercise are *not* separate physical sets, and
  `numericDoseAuthority=NONE` prohibits generating new or credited training
  sets from this overlay alone.
- `recordingUnit` describes the exercise's existing observable prescription or
  record unit. It does not grant a set multiplier or numeric dose policy.
- This does **not** expand the exact approved maximal-strength identity list.
- Copenhagen plank remains `CORE_CLASS=HIDDEN_HIGH`: its primary canonical
  training target is hip adduction, while direct-core intent depends on support
  height and execution. Its detailed row is therefore `REVIEW_REQUIRED`.
- A strict, read-only `BBalancedTaskCatalog` V2 reader now exists for the B attribution
  ledger. It checks schema/version, exact task tokens, review status and
  `numericDoseAuthority=NONE`, but is **not wired into production selection**.
- `BBalancedStimulusLedgerV1Builder` projects already-confirmed B1 sets into
  non-additive direct Strength/Hypertrophy, observed direct-Core, indirect muscle
  participation, and unresolved evidence across 7/28/56-day windows.
- Before any production activation: validate the full dose-ledger/provenance
  comparison, define and separately approve numeric prescription authority,
  and protect existing B4–B8/control routing.

See `docs/audits/b_balanced_semantic_review_v0_2.md` for evidence and
`tools/metadata_authority/tests/test_b_balanced_target_task_relations.py` for assertions.
