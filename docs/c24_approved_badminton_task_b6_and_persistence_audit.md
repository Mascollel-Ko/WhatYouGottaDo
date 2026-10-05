# C24 — Approved Badminton Task B6 and Persistence Audit

## Scope and policy provenance

C24 implements the three exact badminton task protocols explicitly approved for this project. Their dose and frequency are bounded product policy with provenance `USER_APPROVED_PROJECT_POLICY`; they are not presented as universally proven physiological optima. The protocol definitions live in one repository-owned source, `ApprovedBadmintonTaskProtocols`, separate from the legacy `RecordBasedReviewedPolicy` coaching guides.

The exact bindings are:

| Protocol | Exact owner | Authorized task attribution | Within-session prescription | Frequency |
| --- | --- | --- | --- | --- |
| `BADMINTON_SIX_CORNER_FOOTWORK_V1` | `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION` | Acceleration, deceleration, footwork, reaction; each requires a current direct canonical relation | 3 rounds × 10–20 sec, 60 sec rest | 2 protocol exposures/week |
| `BADMINTON_LATERAL_SHUTTLE_LUNGE_V1` | `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH` | Lunge reach, deceleration; each requires a current direct canonical relation | 3 sets × 5 reps/side, 75 sec rest | 2 protocol exposures/week |
| `BADMINTON_SPLIT_STEP_REACTION_V1` | `ex_8e69fc74#CANONICAL_STIMULUS_TASK_REACTION` | Reaction; requires a current direct canonical relation | 3 rounds × 10–20 sec, 60 sec rest | 2 protocol exposures/week |

All three are DRILL activity with `NO_EXTERNAL_LOAD`, no RPE, and non-additive task credit. The split-step definition is available only after the normal B5 selection path chooses this exact owner and role; C24 does not change B5 ranking. JUMP_LANDING has no approved protocol. No category-wide mapping or legacy guide fallback was added.

## Exact B4/B5/B6 boundary

B4 task targets remain direction-only where they were direction-only. C24 does not rewrite their history-derived authority. After B4 and ordinary B5, the exact downstream resolver grants `AUTHORIZED_APPROVED_TASK_PROTOCOL` only when the primary B4 task target is direction-only, the exact stableKey and selectionRole match an approved definition, every protocol-authorized relation is currently DIRECT, and the canonical activity kind is a drill. A protocol attribution is emitted only for targets present in the current B4 plan. This exact user-approved downstream protocol supplies its own two-per-week frequency authority; it is not described as personal baseline evidence or an inferred B4 numeric dose.

One protocol exposure is one physical item. Multiple task attributions do not multiply sets, session time, capacity, tissue, OFI, fatigue, or placement. Task outcome credit is a weekly union capped at the protocol frequency. Independently selected exact protocols may both materialize; overlapping deceleration or reaction credit is capped rather than summed.

Duration-range persistence retains the full 10–20 second range. Capacity estimation uses its authorized 20-second maximum without rewriting the prescription. A same exact protocol is not counted twice on one day; a duplicate-day row is removed and the weekly outcome records a typed shortfall. No second exposure is forced when placement is not safe.

## Lossless saved-program persistence

The stored program item now carries `taskProtocolSemanticsJson`, a versioned typed contract containing protocol ID and version, policy provenance, exact owner and selection role, primary and attributed tasks, direct-transfer evidence, protocol frequency, exposure index, activity semantics, credit semantics, and the complete prescription shape. Display text is never parsed to recover authority. Existing scalar set columns carry only their legacy-compatible projection; the typed field preserves per-side and range meaning.

Room advances from schema 37 to 38 with an additive nullable column on `training_program_items`. Legacy rows remain null and retain their scalar behavior; no protocol, laterality, or range is inferred. The task semantics field is preserved through program save/load/replacement, local program backup/restore, CSV backup/restore, and Community snapshots. Program backup schema advances to 4 and Community snapshot schema to 2, with older versions still accepted. The C20 incumbent source fingerprint includes the new task semantic field so a change cannot evade stale-source detection.

## Corpus result and persona3_recent

The 27-case corpus contains 132 task-target rows, including 24 direction-only targets. Normal B5 selected two exact owners in `persona3_recent`: six-corner for primary Acceleration and lateral shuttle for primary Lunge Reach. Six-corner's approved multi-target contract also attributed Deceleration, Footwork, and Reaction; lateral shuttle attributed Deceleration and Lunge Reach. Each owner produced two distinct-day exposures per week over the two-week generated plan, for eight physical rows total. Each physical row is counted once.

The generated shapes are exactly:

- Six-corner: 3 rounds × 10–20 sec, 60 sec rest, two protocol exposures/week.
- Lateral shuttle: 3 sets × 5 reps/side, 75 sec rest, two protocol exposures/week.

The C22 legacy rows were not restored: six-corner `4 × 15 sec` from `CANONICAL_PROGRAM_3_1_7` and lateral shuttle `5 × 18 sec` from `CANONICAL_PROGRAM_8_2_57`. They were replaced only by the approved exact Task B6 prescriptions. Split-step was not selected in the real corpus; a synthetic exact B5 owner/role test verifies its protocol can be granted when the ordinary selector chooses it.

The five distinct target identities receiving protocol credit in the real corpus are Acceleration, Deceleration, Footwork, Reaction, and Lunge Reach. Target outcomes remain those emitted by the existing outcome engine; C24 does not hardcode an improved/satisfied result. For `persona3_recent`, these five task outcomes are `UNCHANGED` with no unmet reason; unrelated Power, Strength, and JUMP_LANDING targets remain unmet. The case remains on CONTROL because B7 still reports `AFFECTED_TARGET_REMAINS_UNMET` and `CHANGE_PROVENANCE_UNCLOSED`; B8 reports `B8_B7_NOT_ELIGIBLE`. No task route is added.

## Safety and route regressions

- Power numeric authority, executable Power B6, and Power material rows remain zero. Task protocol rows do not receive physical-quality B6 credit.
- JUMP_LANDING remains unapproved and has no protocol material row.
- C20 incumbent status remains 12 HARD_VALID, 0 HARD_INVALID, 20 UNRESOLVED; no invalid or unresolved incumbent is forced to remain.
- Routes remain CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0. No B7 or B8 predicate or scope changes.
- Build accounting remains one CONTROL build plus one EXPERIMENTAL build, total two, with no third build.

The C23 pre-C24 B7 census was provenance-unclosed 11, target-unmet 9, regressed 2, with one collateral regression. C24 measures 11 provenance-unclosed, 9 target-unmet, 1 regressed, and 0 collateral-regression cases. The one fewer regression corresponds to `persona3_recent` no longer losing its five task outcomes after C24 replaced the old unauthorized fallback rows with exact B6-authorized task rows. The existing regression remains. No B7/B8 predicate was relaxed.

## Validation record

- C23 merge SHA: `9663bc7c078229adfb04f23dc66da0eb976778fe`.
- C23 merged-main Hosted CI: run [37303461895](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37303461895), success (protocol, contracts, whitespace, unit tests, coverage, APK assembly, signer, upload).
- C24 start SHA: `9663bc7c078229adfb04f23dc66da0eb976778fe`.
- C24 policy/persistence commit: `b9ed008a7d1f85412caad1224db7f37263d4b4c8`.
- C24 Task B6 implementation/test commit (`lastAuditedCommit`): to be filled after its green Hosted CI.
- C24 final documentation HEAD: to be filled after the final docs commit and CI.
- Standard production coverage SHA-256: `AC796426BEE789B00668744B11B0D5F3701A25037A0878CC1D60D709DAE3C15A`.
- C24 machine census SHA-256: `74D74B57502C8C6F4EDC19AD7D461467F0E447DF350B3CF64D8CFA6ECFBBE0CF`.
- Local compile succeeded; focused C20–C24 and persistence suites passed. The final worker-restarted `:app:testDebugUnitTest` run completed 2,307 tests with 0 failures, 0 errors, and 4 skips. An earlier non-restarted local attempt hit the known Robolectric native SQLite crash; the completed worker-restarted run did not.
- Final Hosted CI run, coverage artifact, and APK artifact details are recorded after completion.

The machine-readable, per-target and per-exposure census is [`c24-badminton-task-b6-persistence-census.json`](c24-badminton-task-b6-persistence-census.json).
