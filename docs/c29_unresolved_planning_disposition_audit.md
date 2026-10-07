# C29 — Unresolved planning disposition and efficient re-resolution audit

## Conclusion

The 22 sparse movement-coverage entries are true unresolved **need dispositions**, but their proposed exercises are already correctly deferred from executable material. Every entry comes from a typed movement-exposure gap. The planner preserves the gap, searches exact candidate identities, finds no canonical B4 target or B5/B6 owner for it, and refuses to create a prescription. No evidence says whether each signal is mandatory for this block, should be deferred, or should be excluded. Calling the need resolved would therefore invent a disposition.

This is a target/disposition integration gap at the boundary between movement-gap analysis and canonical B3/B4 planning. It is not a B7/B8 problem, a user-input problem, a placement failure, or a reason to restart the full planner.

## Merge and baseline

PR #21 was verified at `b1e5167d3957729c76852d3b15499978890099e2`, ready for review, mergeable, with required checks green and no blocking review. It was merged on 2026-10-07 at `05:37:51Z`.

- Pre-merge PR head: `b1e5167d3957729c76852d3b15499978890099e2`
- Merge SHA / merged main: `f3498af2055d7a4f6d9989c99d49f1c2042fff84`
- Merged-main Hosted CI: run `37577269160`, success
- C29 branch start: `f3498af2055d7a4f6d9989c99d49f1c2042fff84`

The merged-main run passed protocol validation, Community/Cloud contracts, whitespace, all unit tests, production coverage, APK assembly, signer validation, and artifact upload. The C28 contract remains Protocol 3.62.0, runtime `RECORD_BASED_PLANNER_0.15.4_KOTLIN_1`, app 0.5.1.5, Room 38. C29 changes no version or persisted schema.

The current generated 27-case audit corpus has 22 generated cases and five preflight rejections. All 22 generated cases route to CONTROL in the C28 corpus because the Experimental evaluation fails closed where no executable planning demand remains. The earlier 19 / 1 / 2 route census predates that C28 corpus outcome; C29 does not change either result.

## What the 22 entries represent

The five sparse cases contain 22 distinct `(case, stableKey, selectionRole, coverage)` needs, each previously considered for weeks 1 and 2: 44 owner-week rows in total. Every movement representation is `ABSENT`, with zero current and prior 28-day exposure. The gap source is `MOVEMENT_REPRESENTATION`; the codes are movement signals such as `HORIZONTAL_PUSH`, `CORE_DIRECT`, and `POSTERIOR_CHAIN`.

| Sparse case | Identities | Coverage owner identities | Exact alternative-owner authority probes |
|---|---:|---|---:|
| `persona0_sparse` | 2 | `ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH` (HORIZONTAL_PUSH); `ex_28347c1f#COVERAGE_CORE_DIRECT` (CORE_DIRECT) | 17 |
| `persona1_sparse` | 6 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`; `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`; `dumbbell_goblet_squat#COVERAGE_LOWER_KNEE`; `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`; `ex_28347c1f#COVERAGE_CORE_DIRECT`; `ex_5ca7133f#COVERAGE_CALVES` | 38 |
| `persona2_sparse` | 6 | `barbell_deadlift#COVERAGE_POSTERIOR_CHAIN`; `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`; `cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`; `cable_rear_delt_fly#COVERAGE_UPPER_PULL`; `ex_28347c1f#COVERAGE_CORE_DIRECT`; `ex_5c8751d2#COVERAGE_CALVES` | 91 |
| `persona3_sparse` | 2 | `ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH`; `ex_28347c1f#COVERAGE_CORE_DIRECT` | 17 |
| `persona4_sparse` | 6 | `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`; `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`; `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`; `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`; `ex_28347c1f#COVERAGE_CORE_DIRECT`; `ex_5ca7133f#COVERAGE_CALVES` | 45 |
| **Total** | **22** | **Every identity has weeks 1 and 2 in the before census** | **208** |

Coverage-code counts are: CORE_DIRECT 5, ARMS_BICEPS 3, POSTERIOR_CHAIN 3, ARMS_TRICEPS 3, CALVES 3, HORIZONTAL_PUSH 2, UPPER_PULL 2, and LOWER_KNEE 1. The audit census contains the exact 44 week rows, old prescription shapes, all alternative stableKey/role pairs, and per-case B1–B6 evidence.

## Gate-by-gate finding

**B1 — need evidence.** B1 builds canonical quality and badminton-task need profiles. Its typed profile has no movement-coverage need. The movement evidence is separately present in `AthletePlanningState.movementRepresentations`, and `AdaptationGapAnalyzer` turns it into `MOVEMENT_REPRESENTATION` gaps. So the underlying history signal exists; it has not been admitted to the canonical need contract.

**B2 — dose history.** Movement current/prior exposure evidence exists as representation counts. Canonical B2, `LedgerBackedQualityDoseHistory`, is keyed by trainable quality and is shadow-only for prescription. It has no movement-coverage dose band. No exact movement prescription history or weekly frequency authority is created by these signals.

**B3 — disposition.** The decision portfolio contains quality and badminton-task decisions only. It does not decide whether a `MovementCoverage` gap is a block requirement, an optional candidate, or a deferrable signal. This missing disposition is the first point where the planner cannot answer “must this be filled now?”

**B4 — target.** `StimulusTargetPlan` contains `qualityTargets` and `taskTargets`; it cannot express a movement-coverage target. Across these 22 identities there are zero canonical B4 movement targets and therefore no target ID or numeric authority for the side-path needs.

**B5 — owner.** The canonical selector only selects owners for B4 quality/task targets, so these coverage entries never enter canonical B5. The separate `MaterialDemandResolver` candidate pool was checked against existing exact authority. It made 208 owner-authority probes across the 22 identities and found no valid exact owner. Candidate alternatives are not B5 selections.

**B6 — prescription.** There are zero canonical movement-owner B6 decisions. The material-demand authority resolver ends with `NO_SUPPORTED_AUTHORITY / OWNER_CANDIDATES_EXHAUSTED`; it does not grant a prescription. There is no user-load question to answer for these movement candidates, and no valid prescription or frequency to materialize.

B7 reports comparison readiness; B8 decides whether an already-built Experimental skeleton can be selected; B9 selects the intact CONTROL or Experimental skeleton. None of these gates owns missing need disposition or prescription policy. Their refusal is downstream protection, not the root cause.

## Disposition and root-cause census

| Classification | Need identities | Meaning |
|---|---:|---|
| `TARGET_GENERATION_GAP` | 22 | The movement gap is retained, but it has no canonical B3 disposition or B4 target. |
| `TRUE_UNRESOLVED` need disposition | 22 | Existing rules do not decide whether the underlying gap is mandatory, deferred, or excluded for this block. |
| `USER_INPUT_REQUIRED` | 0 | No valid owner has an otherwise-complete prescription waiting only for user load calibration. |
| `RESOLVED_DEFERRED` need disposition | 0 | The candidate is deferred for lack of authority, but the need itself is not decided as deferred. |
| `RESOLVED_EXCLUDED_OR_INFEASIBLE` | 0 | No schedule, equipment, capacity, tissue, or safety result excludes these needs. |
| `POLICY_UNSUPPORTED` terminal need disposition | 0 | No block-level rule says the need itself is out of policy. |
| Candidate execution policy unsupported | 22 | The candidate cannot become executable under current B4/B5/B6 authority. |

This split is deliberate: `RESOLVED_DEFERRED_NO_EXACT_AUTHORITY` describes the candidate’s executable disposition. `TRUE_UNRESOLVED` describes the retained movement need’s block disposition. The latter cannot be silently erased merely because no authorized prescription was found.

There is no owner-arbitration gap: no alternative has an exact authorized prescription. There is no user-input, placement, or global-balance blocker. If a future rule admits a movement gap as a canonical target, only the local target → B5 → B6 subgraph needs to run again.

## Re-resolution and dependency map

For these 22 cases, the smallest useful return point is a typed movement-need disposition/target-admission step before canonical B4. It can use the already-computed representation and gap evidence. If it emits a canonical target, recompute B4, then B5 and B6. It does not need to rebuild history, quality response, unrelated targets, or CONTROL. If the target is explicitly deferred or excluded, preserve that typed terminal disposition and do not enter B5/B6. If a selected owner later needs user calibration, wait for that input and rerun only that owner’s B6 and downstream execution checks.

| Changed evidence | Invalidate | Retain |
|---|---|---|
| Source history, metadata, or profile | Snapshot/state, movement gaps, B1/B2, then B3–B6 | Nothing derived from the changed source |
| B2 dose history only, with B1 source needs unchanged | B3–B6 | B1 and the unchanged source snapshot |
| B3 decision/disposition | B4–B6 | B1/B2 |
| B4 target | B5/B6 | B1–B3 |
| B5 exact owner | That owner’s B6 | B1–B4 and unrelated B6 owners |
| B6 dose, load, effort, or frequency | Allocation, capacity, placement, tissue/OFI validation, materialization, B7–B9 | B1–B5 |
| Placement/order only | B7–B9 | B1–B6 |

Global program repair is appropriate only after an actual cross-owner or whole-program conflict: session capacity/time overflow, excessive weekly volume, recovery/tissue/OFI conflict, incompatible frequency or placement constraints, target regression, or collateral regression. The 22 movement gaps show none of those conditions. They are local information/authority failures, so a full B1–B8 restart would waste work and repeat independent computations.

## Runtime cost and repeated work

The test-side observer measured the existing Room/service generation pass and planner counters without changing decisions. In the latest Windows/Robolectric full-suite sample, the 22 generation calls took 3.584 seconds total, with a 163 ms mean, 144.5 ms median, and 369 ms maximum. The observed mean from generation start through canonical B1–B4 preparation was 68.7 ms; Experimental build/materialization averaged 28.5 ms over the 15 requests reaching that observer; and the later CONTROL build averaged 59.9 ms over those same 15. The B5/B6 boundary intervals rounded below one millisecond at the observer’s resolution. These timings are a single corpus observation, not a production latency guarantee.

For the current requests the orchestrator runs canonical B1, B2, B3, B4, B5, and B6 planning once per generated request (22 each). B2 also computes one compatibility/legacy quality-dose input per request (22). There are 208 distinct exact alternative-owner authority probes for the sparse movement candidates, zero canonical B6 owner lookups for a movement target, and zero generic prescription-resolver calls for candidates with no exact authority. The probes are generation-level, not repeated once per each of the 44 week rows. Identical `(case, gap, stableKey, role, authority state)` keys repeated: 0.

The builder exposes placement/projection metrics on the 15 requests that reach the post-materialization observer. Their aggregate includes 26 weekly placement calls, 50 placement atoms, 121 day projections, 22 week-tissue projections, and 77 rebalancer candidates. Day-projection memoization had 43 hits / 54 misses; week-tissue memoization had 13 hits / 28 misses. The seven expected fail-closed requests stop before that metrics boundary, so placement metrics are unavailable for those requests rather than reported as zero.

`PlanningComputationMemo` is request-scoped. It caches pure prescription plans by snapshot/intent/item/style, day projections by item list, and week tissue projections by item list plus RPE ceiling. It does not cache B1–B6 objects, movement candidate scans, or exact material-demand authority probes. Existing source runs the material-demand resolver once per request and bounded candidate re-resolution once per retained origin. No full planner restart or third skeleton build occurred. The audit harness separately calls `buildCanonicalStimulusPlanningForPrepared` to capture canonical evidence; those observer-only B1–B4 evaluations are not production generation work and are identified in the census.

The measured alternative pool is finite. The evidence supports a single-pass owner candidate scan, skip any already-attempted exact identity, and zero retry for an unchanged `(target, owner, authority state, failure reason)`. Revisit B4 only after its need/disposition state actually changes. Keep program-level repair passes bounded and reserve them for the global conflicts above; current C29 evidence does not justify a numeric retry budget beyond the existing one-pass local searches.

## Regression state

The corpus remains at CONTROL 22, Strength V1 0, Strength Calibration 0, Hypertrophy 0, Combined 0 for this post-C28 generated set. B7 remains 15 `CHANGE_PROVENANCE_UNCLOSED` cases, zero `AFFECTED_TARGET_REMAINS_UNMET`, one `TARGET_REGRESSED`, and one collateral regression. C20’s current evaluation is 10 `HARD_VALID`, 0 `HARD_INVALID`, 0 `UNRESOLVED`, and 22 `NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER`; forced invalid/unresolved preservation is zero. The older 12 / 0 / 20 count is the C27 placement fixture baseline, not the post-C28 set where some owners no longer have current authorized rows.

The C26-denied Quality owner-weeks remain at zero executable EXP rows and zero material delta, with zero intersection against the 22 sparse movement identities. The eight approved C24 task rows retain their exact semantics. Power remains 0 authority / 0 B6 / 0 material; JUMP_LANDING remains 0 approved protocol / 0 material. Build accounting is one CONTROL and one Experimental skeleton per generated request, two total, zero third builds. No routing, training, persistence, version, or product behavior changed.

## Recommended next bounded phase

Address the **movement-need disposition and canonical target-admission boundary** first. Define, using existing product policy only, when a movement representation gap is an actual block target versus a signal that can be deferred/excluded. If the repository has no such policy, keep it unresolved and request product policy before adding a target or prescription. Do not create generic sets/reps/frequency, promote a supporting candidate to B5, or use CONTROL’s row shape. Once a target is legitimately admitted, connect only that target through existing B5/B6 authority and rerun its affected downstream graph.

Do not start with a global repair loop: no global constraint is implicated. Do not start with a new dose policy: none of the 22 has a canonical target or exact B6 authorization. Keep Power, JUMP_LANDING, mixed scope, B7/B8 predicates, and routing unchanged.
