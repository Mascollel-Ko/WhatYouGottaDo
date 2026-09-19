# AthleteNeedsProfile shadow comparison

## Shadow target portfolio

The post-generation audit sequence is now:

`GeneratedProgramSkeleton → AthleteNeedsProfile → TrainingDecisionPortfolio → TargetStimulusPlan → TargetPlanComparison`.

Need is not dose. Dose is not schedule. The portfolio expresses an ordinal
strategy, while the target plan supplies numeric direct-dose envelopes only
when a personal successful-dose baseline exists. Positive response does not
automatically authorize more volume. `NO_MINIMUM_TARGET` is not target zero;
incidental and supportive exposure remain allowed. A novel stimulus without
personal dose history remains direction-only. Supportive exposure is not
numerically exchangeable with direct exposure. Quality target envelopes are
overlapping semantic views, not additive weekly workload budgets.

Comparison labels are `PLANNED_CAPABILITY_COVERAGE`; they are an audit
approximation and not proof of realized physiological stimulus. Domain volume
baseline and quality stimulus baseline are separate contracts.

The target history exposes both weekly direct dose and exposure-week direct
dose. Weekly values include zero-quality exposure in eligible active normal
weeks; exposure-week values include only weeks with direct exposure. Their
frequency is historical evidence, not schedule placement. Inactive or
`excludedFromTolerance` weeks are omitted. `STRENGTH`/`HYPERTROPHY` history is
prescription-aware through the shared provisional classifier, while
`POWER`/RFD/SSC retain capability exposure semantics. Ambiguous prescriptions
remain explicitly ambiguous, and capability comparison does not prove future
realized stimulus.

The planned side of the comparison is a capability projection with planned
units per week, exposed-week count/frequency, and median units per exposed week.
It is compared against historical frequency and preferred exposed-week dose in
separate `weeklyDoseStatus`, `frequencyStatus`, and
`exposureWeekDoseStatus` dimensions for qualities and meaningful tasks. The
historical frequency is scaled to the plan duration with a transparent ±1
planned exposure-week tolerance. A zero weekly Q25 is not a zero-frequency
target: if historical exposure frequency is positive, a plan with no exposed
weeks cannot be `WITHIN_TARGET_BAND`. No future realized-stimulus or repetition
heuristic is inferred.

This is an audit trace beside the existing `AdaptationGapAnalyzer`. It does
not feed exercise selection, weekly volume, sets, reps, load, frequency,
placement, or any execution allocator.

## Regional bottleneck shadow diagnosis

The regional layer is parallel to the global target comparison, not a
replacement for it. One immutable `RegionalEvidenceIndex` is built per
personalized planning operation in a single pass over the governed 56-day
history. It keeps weekly direct dose, dose in exposed weeks, and exposure-week
frequency separate, includes zero regional stimulus in eligible active weeks,
and excludes inactive or canonical `excludedFromTolerance` weeks according to
the existing policy.

Regional ownership remains `MovementCoverage` for lower-knee, posterior-chain,
calves, horizontal push, vertical push, horizontal pull, and vertical pull.
Typed `PhysicalQualityRegion`/`PhysicalQualityMode` qualifiers and the shared
provisional realized-stimulus classifier distinguish STRENGTH-like direct work
from HYPERTROPHY-like support. Regional strength response reuses canonical
posterior changes and observation counts; no population strength norm or
second performance model is introduced.

Low exposure is not automatically a deficit. A positive personal strength
response suppresses unsupported `EXPOSURE_LIMITED` and
`MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING` conclusions. When response is not
positive, exposure, recovery restriction, and specific-lift continuity are
considered before the late-stage morphological-capacity training hypothesis.
That hypothesis means only that additional hypertrophy support may be a useful
next training hypothesis; it is not a claim about measured muscle size and is
capped at `MODERATE` confidence. Diagnoses remain
`shadowOnly=true`/`prescriptionAuthority=false`.

The collapsed summary separately describes up to three deterministic labels
from the actual final generated skeleton. It describes program composition,
not causal diagnostic authority, and expanded regional details translate the
evidence into Korean without exposing raw enums or reason codes.

## Decision boundary

- `Exposure is not response`: repeated set rows cannot produce `POSITIVE_RESPONSE`. STRENGTH alone currently has canonical response authority, using the median posterior change for relevant stableKeys exposed to strength work in the current 28-day window. Every other quality remains `INSUFFICIENT_EVIDENCE` until a canonical outcome exists.
- `Underrepresentation != Need`: low exposure is only a candidate until goal relevance and response evidence are considered.
- `High workload != Overload`: high tolerated volume with a positive strength response produces `MAINTAIN_OR_PROGRESS`, never an automatic reduction.
- `Need != Execution feasibility`: recovery, OFI, and tissue/RCV restrictions are execution modifiers and do not erase a quality need.
- Generic badminton participation is recorded as sport context/load/tolerance and is never counted as structured developmental direct or supportive task exposure.
- Badminton intent without a task-specific user priority makes the six mapped tasks `MODERATE`, not universally `HIGH`; quality relevance follows PRIMARY → MODERATE and SUPPORTIVE → LOW ordinal propagation without coefficients or multi-task escalation.
- A badminton execution `REDUCE` trace requires `courtDeviation > 0`, `lowerNegativeEvidence > 0`, and constrained recovery/tissue evidence together. Absolute court load has no reduction authority.

## Representative comparisons

| Existing gap signal | Shadow Needs result | Reason |
|---|---|---|
| A movement share is lower than a peer reference | `NO_EXTRA_NEED` when goal relevance is low | The engine does not convert a distribution gap into a need without user relevance. |
| No recent POWER relation | `DEVELOP` for a badminton-performance goal | Task-derived POWER relevance is MODERATE and current direct exposure is absent. |
| High resistance volume with improving posterior, stable RPE, and high completion | `MAINTAIN_OR_PROGRESS` for STRENGTH | Positive response is continuity evidence; high exposure is not overload by itself. |
| High badminton load at the athlete's normal baseline | Resistance quality decision is unchanged | Absolute court volume is not a suppression signal. |
| Acute court deviation plus lower deterioration and recovery/tissue worsening | Need remains visible; execution modifier is `REDUCE`, `HOLD`, or `SUBSTITUTE` | Safety/feasibility is separate from the quality decision. |
| Generic court exposure with no structured direct task relation | `UNKNOWN` task exposure | Sport context is not treated as a developmental stimulus. |

## Exposure field interpretation

`unit` means one confirmed planning set record. `session` means one unique
training date. Canonical fields are `recent7dUnits`, `current28dUnits`,
`previous28dUnits`, `context56dUnits`, `directUnits`, `supportiveUnits`, and the
structured-task `*Units`/`*Sessions` pairs. Deprecated JSON `*Bouts` aliases are
retained for compatibility. The reps-only strength-like/hypertrophy-like label
is a provisional prescription-shape description for exposure audit only and
never participates in response classification.

## Synthetic coverage

`AthleteNeedsProfileTest` covers A–N: exposure-not-response for hypertrophy,
cardio, and power; canonical strength response and relevant-key filtering;
generic-court exclusion; structured badminton and athletic drill inclusion;
task and quality relevance ceilings; direct strength priority; and the three
court-reduction boundary cases.

Real fixture planner parity remains governed by the existing planner tests;
the shadow profile is attached only to the persisted decision trace after the
program skeleton is built.

## Real-backup audit trace

The existing real-backup planner fixtures remain the human-review source. A
review trace should list each quality's relevance, current exposure, response,
decision, confidence, direct units, and supportive units, plus each task's
relevance, structured direct/supportive units, context load, and decision.
Production runtime output is unchanged apart from the additive shadow JSON.
The expected interpretation is: improving exposed STRENGTH may be
`MAINTAIN_OR_PROGRESS`; high HYPERTROPHY exposure without an outcome remains
`INSUFFICIENT_EVIDENCE` and may be `MAINTAIN`; low POWER exposure under general
badminton intent may be MODERATE/`DEVELOP`; generic badminton context is not
duplicated as structured cardio or task development.
