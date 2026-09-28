# Post-B15 production coverage and fallback audit

Baseline: `ff2148d381b22ae107957e790373ac5c79e988fa`. Protocol remains `3.50.0`.
This is an internal diagnostic and test audit. Scope selection, B6 prescriptions,
B7/B8 gates, B9 routing, build invocation boundaries and persistence are unchanged.

## Coverage by stage, from code

`R` means prescription-classified evidence, `P` means canonical capability-proxy
evidence, `D` means a diagnostic decision/target row (including NONE/UNRESOLVED),
`C` means conditional on target strategy and an eligible catalog candidate, and
`X` means executable authority with all existing gates satisfied. Presence in a
row is not evidence that the quality can cross the production gate.

| Stage | Strength | Hypertrophy | Power | RFD | SSC | Muscular endurance | Cardio | Mobility |
|---|---|---|---|---|---|---|---|---|
| B1 Need | R; response model | R; no response model | P | P | P | P | P | P |
| B2 History | R | R | P | P | P | P | P | P |
| B3 Decision | D | D | D | D | D | D | D | D |
| B4 Target | D | D | D | D | D | D | D | D |
| B5 Selection | C | C | C | C | C | C | C | C |
| B6 Executable | X | X | No | No | No | No | No | No |
| B7 Attribution | B5/B6 | B5/B6 | B5 only | B5 only | B5 only | B5 only | B5 only | B5 only |
| B8 Authority | X, alone or S+H | X, alone or S+H | No | No | No | No | No | No |
| B9 Production | X, permitted mode | X, permitted mode | No | No | No | No | No | No |

Code anchors (paths relative to repository root):

- `app/src/main/java/com/training/trackplanner/data/ExercisePhysicalQuality.kt`,
  `TrainableQuality`: exactly the eight qualities above.
- `data/personalized/AthleteStimulusNeed.kt`, `AthleteStimulusNeedEngine.analyze`:
  maps all enum entries; only Strength has a response calculation. Non-Strength
  response stays `INSUFFICIENT_EVIDENCE`. `qualityRelevance` handles all eight.
- `data/personalized/StimulusEvidenceSemantics.kt`, `evidenceBasisForQuality` and
  `qualityObservationDisposition`: S/H require reviewed prescription realization;
  the remaining qualities use canonical capability evidence, not executable dose.
- `data/personalized/LedgerBackedQualityDoseHistory.kt`, `analyze`: all eight
  receive weekly evidence, bands and observability. Generic court observations
  remain context and do not become quality dose. Unclassified sources cannot
  establish an exact numeric baseline.
- `data/personalized/StimulusTrainingDecisionPortfolio.kt`, `build`, and
  `StimulusTargetPlan.kt`, `build`: preserve every incoming quality. B4 numeric
  authority depends on personal baseline observability; a target row may have no
  minimum target or unresolved authority.
- `data/personalized/StimulusCandidateSelection.kt`, `build`, `selectionAllowed`,
  `eligibleCandidates`: accepts all quality targets and task targets, constrained
  by strategy, direct catalog capability, equipment, exclusions and recovery.
  Only S/H have prescription-compatible history ranking. A candidate identity
  does not grant prescription authority.
- `data/personalized/StimulusPrescriptionRealization.kt`, `resolveTarget`, and
  `StimulusPlannedPrescriptionResolver.kt`: explicitly bound to S/H. Other
  qualities produce `REALIZATION_MODEL_UNAVAILABLE`; task identity selection is
  also not task prescription authority.
- `data/personalized/StimulusExperimentalReadiness.kt`, `attributeChanges`:
  B5 additions inherit covered target IDs without a quality whitelist; B6 shared
  prescription changes inherit only executable authorization target IDs. Reuse,
  removal and unexplained changes remain separately attributed.
- `data/personalized/StimulusProductionCutoverAuthority.kt`, scope enum and audit,
  and `StimulusProductionRouter.kt`, resolver and router: only S, H and S+H;
  provenance, materialization and B9 identity gates remain required.

The abbreviated `data/` paths above have prefix
`app/src/main/java/com/training/trackplanner/`.

## Scope and fallback diagnostics

`resolve` retains its exact nullable production contract. `resolveDetailed`
observes the same comparison and carries that scope unchanged, along with an
exclusive status, all material quality IDs, owner identities, missing owner
provenance, unknown target IDs and sorted secondary reasons. An unsupported
quality can also have an unknown target ID; both reasons survive. S+H plus
another quality adds `THIRD_QUALITY_PRESENT`. Removal-only changes are reported
as ambiguous scope rather than no material change.

The service still uses `scope ?: STRENGTH_V1` for the existing B8 audit. The
generation result's internal `diagnostics.scopeResolution` is independent of
that B8 scope, so a failed resolution is not reported as successful Strength
resolution. Known upstream failures retain the outer reason plus the typed
canonical reason and detail. Cancellation/unexpected failures still propagate.

For CONTROL, primary-stage precedence is: explicit CONTROL policy, upstream
failure, B6 materialization integrity failure, no material, failed scope
resolution, failed authorization for a material owner, B7 readiness, B8 authority,
then B9 contract. Exactly one primary is counted. This is a reproducible earliest
observed blocking boundary, not a claim that later failures have no other causes.
All B7/B8/B9 reasons remain available. Diagnostics never select a program.

## Corpus method and limits

`StimulusProductionCoverageAuditTest` calls real Room-backed repository preflight
and, when preparation succeeds, `generatePreparedPersonalizedProgramEvaluation`. Each case has a fresh seeded
database and fixed cutoff `2026-09-20`; results are not copied, repaired or supplied
with fabricated B8 authority. The corpus combines five intent/context settings
(Strength, Hypertrophy, mixed, badminton performance, low/non-badminton) with five
history settings (none, one session, recent 2–3 weeks, reviewed history across an
eight-week window, mixed repetition/quality history). Weekly days span 2–5,
session time spans 30/60/90 minutes, and equipment spans free weights,
machine/cable and unrestricted mixed equipment.

These 25 cases sample the axes rather than exhaust their Cartesian product. Two
isolated-owner reviewed-history cases retain the existing B14 fixture method as
reference cases and are labeled separately. Seeded posterior history is a test
fixture, not a population sample or empirical clinical outcome.

The older B15 combined success fixture composes separate service comparisons and
supplies B8 authority. It remains a routing contract test, but its constructed
successes are excluded from this real-service coverage aggregate.

The initial corpus run exposed the existing no-history precondition in
`PlanningHistorySnapshotBuilder.build`: it requires at least one confirmed set.
The audit records that precise preflight rejection separately, with no program,
no B6/B7/B8/B9 result and no builds. It does not seed substitute history to force
a CONTROL result. This is an input boundary, not a routing defect; production
code and the precondition remain unchanged. Unexpected exceptions fail the test.

Every record includes targets (strategy and numeric authority), material
qualities/owners, resolution, B6 resolutions/authorizations/conflicts, B7/B8/B9,
selected program, primary/secondary reasons and actual build counts. Records and
summaries are sorted; reversed input order must render identically. The report
is written to `app/build/reports/stimulus-production-coverage.txt`.

The executed corpus produced 27 total cases: 22 generated cases and 5
`PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY` cases. Among generated cases, B9
selected Strength 3 times, Hypertrophy once, combined zero times and CONTROL
18 times. The exclusive primary fallback counts were `NO_MATERIAL_CHANGE 14`,
`SCOPE_RESOLUTION 3` and `B6_EXECUTION_AUTHORITY 1`; no upstream, B7, B8 or B9
primary failure occurred in this corpus. The remaining three generated cases
were successful production routes. Every generated case recorded
`CONTROL=1`, `EXPERIMENTAL=1`, `TOTAL=2`, `THIRD=0`.

Unsupported material blockers were observed for POWER (2) and
`REACTIVE_STRENGTH_SSC` (2). RFD, muscular endurance, cardio and mobility had
zero material blockers. These counts come from governed material provenance;
the capability target rows for unsupported qualities were not counted as
blockers. The report contains the complete per-case B1–B9 record.

An unsupported blocker requires a CONTROL result and a governed material scope
that fails specifically for unsupported quality. A need or target row alone is
never a blocker. Per-quality blocker counts may overlap in a multi-quality case;
exclusive primary-stage counts never overlap.

## Legacy dependency audit

| Dependency | Classification | Code evidence and implication |
|---|---|---|
| CONTROL is built first | INPUT_SEED_DEPENDENCY | `generatePreparedProduction` builds CONTROL before experimental evaluation. Recognized failure can stop before the second invocation; successful comparison paths build 1+1. |
| Canonical target plan stored in CONTROL | CANONICAL_DATA_HOST_DEPENDENCY | `generatePrepared` computes B1/B2/B3/B4 and attaches `stimulusTargetPlanShadow`; materialization comparison extracts it from `control.personalizedDecision`. Missing plan aborts canonical evaluation. |
| EXPERIMENTAL request | REQUEST_DEPENDENCY | Materialization comparison uses `val request = control.request` for duration, weekly days and builder request. |
| B5 identity selection | INPUT_SEED_DEPENDENCY | Selector consumes CONTROL stable keys/direct-capability identities to avoid additions and to form reuse/reference traces. It is not merely a post-generation comparison. |
| B6 current prescriptions | PRESCRIPTION_BASELINE_DEPENDENCY | `control.items` creates the exact owner/role prescription table passed into authorization and conflict localization before EXPERIMENTAL build. |
| B7 collateral and B8/B9 safety | SAFETY_COMPARATOR_ONLY | B7 compares prescriptions, removed owners, target distances and fingerprints; B8 checks schedule/owner parity; B9 selects original CONTROL or EXPERIMENTAL. |

These anchors are in `PersonalizedProgramPlanningService.kt`,
`StimulusCandidateSelection.kt`, `StimulusPrescriptionMaterialization.kt`,
`StimulusExperimentalReadiness.kt` and `StimulusProductionCutoverAuthority.kt`.
The target data host and request plumbing are potential migration seams, but the
code does not establish that they are temporary; no dependency is labeled
`TEMPORARY_MIGRATION_DEPENDENCY` merely on that assumption. CONTROL is currently
both an essential canonical input and a safety comparator/rollback program.
