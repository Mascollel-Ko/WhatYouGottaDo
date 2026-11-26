# B-Coach strength anchor coverage experiment — 2026-11-26

Base: 7fcbddce2e015be21a94cc4d192baf935cc45a4b (latest successful audit branch CI). Experimental branch; do not merge before validation.

- Keep the existing eight objective weights, exact C32 eligible exercise list, e1RM/RIR/load authority, and user constraints.
- Define the strength opportunity set from eligible catalog equipment/metadata plus incumbent keys, not candidate or historical key union. One task per approved movement anchor. Exact-key dose remains diagnostic and is not double-scored.
- Cap each anchor's deficit credit at its target; extra squat sets cannot fill missing deadlift, horizontal press, vertical pull or vertical press. Raw dose remains the authority for excess and ramp.
- Front squat receives 90% planning credit for the part of the squat target filled by front squat when back squat is available. No preference when back squat is unavailable. This is an unvalidated product preference, not an anatomical/strength-performance claim.
- Compare dedicated 3-set/5-rep missing-anchor insertions and accessory replacements under ordinary time, fixed exercise, calendar, recovery and review constraints. Do not copy weights from another exercise. Existing selected main lifts are protected by the existing policy.
- New B seed evaluation and joint optimizer use capped anchor coverage; legacy B12 comparator and legacy historical reporting are intentionally unchanged.
- The known inconsistent equipment metadata (e.g. BARBELL,TRX_RING for back squat/deadlift) is not changed here; it needs its own canonical audit. Equipment-unavailable anchors are not forced.
- Validation required: full Android Gradle/unit/CI, identical real-profile old/new roster comparison, explicit equipment availability and tight-time cases, locked exercise and recovery/peak cases. New helper tests cover fixed denominator, noncompensation, front variant preference and variant grouping.
