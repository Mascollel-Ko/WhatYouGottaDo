# Canonical muscle relation 762-row provenance audit (provisional, 2026-10-19)

## Scope and risk

Reviewed source: `docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx`,
`10_MUSCLE_REL` (762 relations, 253 identities; base main `13da355c`).
The prior `MIGRATED_CURRENT_BEHAVIOR` source includes physiological role errors:
`ex_eb636bac` triceps extension lists BACK_LATS as PRIMARY,
`ex_bb4b4276` overhead dumbbell press lists BACK_LATS and ANTERIOR_CORE as PRIMARY,
and many compound lifts list postural stabilization as directly hypertrophic PRIMARY.

The row-by-row proposal is in
`docs/audits/canonical_muscle_role_762_review_20261019.csv`.
A deterministic audit classified all 762 rows; **531 had a changed role or coefficient proposal**:
- Original PRIMARY: 630 rows
- Current PRIMARY to STABILIZER: 174 candidate rows
- Current PRIMARY to SECONDARY: 102 candidate rows
- Current PRIMARY to GENERAL_PARTICIPATION: 105 candidate rows
- Other changes include unsupported legacy muscle targets and directly reviewed sports drills

This classification is **not a claim that a human biomechanist individually validated
every single row**. The proposed change status `PROVISIONAL_MECHANICS_V1` prevents
mistaking a deterministic mechanical-screening recommendation for an accepted
scientific prescription rule. Review-dependent identities remain marked
`REVIEW_REQUIRED`. The file also identifies absent primary muscles (e.g. goblet squat
and pistol squat) requiring authority additions. **Those verified prime-mover omissions have since been repaired in this draft branch; see below.**

## Targeted repair following source-level review

The blanket rollback of PR #28 was inappropriate. Its existing role changes were
preserved. The corrective patch `muscle_role_targeted_repairs_20261019.csv`
made only these source-level adjustments:

- 16 **new missing relations** on nine identified exercises, including squat
  knee-/hip-extensor movers, inverted-row back and assisting arm muscles,
  step-down quadriceps and kettlebell swing hip extensors.
- 2 **wrong existing relations corrected**: Cossack squat's mixed
  `ADDUCTOR_ABDUCTOR REVIEW_REQUIRED` became specific `HIP_ADDUCTOR PRIMARY`;
  cross-body hammer curl `BICEPS SECONDARY` became `BICEPS PRIMARY`
  (the current bucket cannot separately express brachialis/brachioradialis).
- 7 corresponding **exercise_bootstrap** primary/secondary muscle token
  corrections, preserving other bootstrap fields and all canonical identity keys.
- Total canonical relations: **778 = 762 existing + 16 additions**.
  The previous 531 provisional role/coefficient proposals are **not discarded**
  and remain a separate review question.
- Regression tests now assert that each directly hypertrophy-capable identity
  has at least one canonical anatomical PRIMARY, and protect already-valid
  triceps, overhead press and squat stabilizer corrections.

This focused repair is intentionally **not** a claim that all 531 original
rule-derived changes are anatomically verified, or that every legacy muscle
display token has been fully audited. PR remains draft pending broader review.

## Interpretation

- PRIMARY: a main force-producing muscle for the described action
- SECONDARY: a meaningful dynamic assister; not a direct dose multiplier
- STABILIZER: postural or joint fixation role; coefficient zero in the legacy
  anatomy weighted-dose graph, **not** zero physiological effort
- GENERAL_PARTICIPATION: mixed sport/footwork exposure; not equivalent to
  isolating a muscle group for hypertrophy
- NOT_SUPPORTED: inherited target cannot be supported by the described mechanics
- REVIEW_REQUIRED: insufficient mechanical certainty; do not infer direct dose

**These values cannot be used as proven equivalent resistance-training sets**.
The new B ledger v1 must not treat a mere migrated PRIMARY token as a reliable
hypertrophy credit without reviewed task + realization evidence.

## Safety and bounded rollout

1. This is an **audit branch / draft-only** workbook proposition until
   all production and quality tests, consumer audits, and review are complete.
2. Existing `stableKey`, identity linkage, set history, B strength anchor 10,
   `core_relations.csv`, Python v1.0 and the B-overlay's
   `numericDoseAuthority=NONE` are not modified.
3. Reclassifying anatomical roles will change retrospective anatomy/weekly
   muscle-load displays if ultimately merged. Do not rewrite raw workout records.
4. Legacy `exercise_bootstrap.primaryMuscles/secondaryMuscles` duplicates the
   roles in a distinct token vocabulary; consistency must be audited and corrected
   before production activation.
5. Field-wide `check`/Gradle/Lint, baseline vs amended case snapshots, and
   actual CSV backtests must pass before branch is eligible for merge.
6. User-defined exercises have no canonical muscle relations. They cannot be
   silently assumed to have zero muscle stimulus.

## Background anatomical principles

- Agonist/synergist/fixator distinctions:
  https://openstax.org/books/anatomy-and-physiology/pages/11-1-interactions-of-skeletal-muscles-their-fascicle-arrangement-and-their-lever-systems
- Resistance multi-joint set attribution limitations:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC6681288/
- Stabilizer definition and uncertainty:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC4403366/

Do not merge a 531-row mechanical proposal solely because structural CI passes.
