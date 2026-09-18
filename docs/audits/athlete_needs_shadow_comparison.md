# AthleteNeedsProfile shadow comparison

This is an audit trace beside the existing `AdaptationGapAnalyzer`. It does
not feed exercise selection, weekly volume, sets, reps, load, frequency,
placement, or any execution allocator.

## Decision boundary

- `Underrepresentation != Need`: low exposure is only a candidate until goal relevance and response evidence are considered.
- `High workload != Overload`: high tolerated volume with a positive strength response produces `MAINTAIN_OR_PROGRESS`, never an automatic reduction.
- `Need != Execution feasibility`: recovery, OFI, and tissue/RCV restrictions are execution modifiers and do not erase a quality need.
- Generic badminton context is recorded separately from structured direct task exposure.

## Representative comparisons

| Existing gap signal | Shadow Needs result | Reason |
|---|---|---|
| A movement share is lower than a peer reference | `NO_EXTRA_NEED` when goal relevance is low | The engine does not convert a distribution gap into a need without user relevance. |
| No recent POWER relation | `DEVELOP` for a badminton-performance goal | The quality is goal-relevant and current direct exposure is absent. |
| High resistance volume with improving posterior, stable RPE, and high completion | `MAINTAIN_OR_PROGRESS` for STRENGTH | Positive response is continuity evidence; high exposure is not overload by itself. |
| High badminton load at the athlete's normal baseline | Resistance quality decision is unchanged | Absolute court volume is not a suppression signal. |
| Acute court deviation plus lower deterioration and recovery/tissue worsening | Need remains visible; execution modifier is `REDUCE`, `HOLD`, or `SUBSTITUTE` | Safety/feasibility is separate from the quality decision. |
| Generic court exposure with no structured direct task relation | `UNKNOWN` task exposure | Sport context is not treated as a developmental stimulus. |

## Synthetic coverage

`AthleteNeedsProfileTest` covers positive high-volume strength response, an
irrelevant low-power goal, a badminton-relevant absent-power case, and
insufficient history with explicit `UNKNOWN_USER_PRIORITY`.

Real fixture planner parity remains governed by the existing planner tests;
the shadow profile is attached only to the persisted decision trace after the
program skeleton is built.
