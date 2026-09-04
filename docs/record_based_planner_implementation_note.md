# Record-based planner v0.10.1 implementation note

Correction baseline: `3a95f2d2ebd25850476cbb89f35173419b282bdb`.

The legacy automatic path remains `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder`. Record-based generation remains independent, produces the same editable `GeneratedProgramSkeleton`, and reuses the existing save/apply flow only after successful generation.

## Authority and current-block windows

- Only `confirmed=true` sets on or before the explicit cutoff enter planning history. Future and unconfirmed work is excluded.
- Exercise identity, movement coverage, activity kind, planning eligibility, Objective V2, tissue contribution and candidate admission remain canonical `stableKey`/typed-metadata authorities. Names and substrings do not create semantics.
- Per-anchor strength style, style features and current-block classification use only `cutoff - 55 days ... cutoff`.
- Canonical exercise-local strength posterior change uses the same 56-day window. Fewer than two eligible observations yields `UNKNOWN`, never a fabricated zero or lifetime change.
- Badminton objective drop compares the current 28 days with the immediately prior 28 days. Developmental absence is evaluated over the complete 56-day comparison horizon.
- The 28-day generic court-load total is retained for provenance and divided by four before weekly 180/240 planning thresholds are applied.

## Transition and execution

- Observed style describes history only. Each anchor also records multidimensional features, adaptation state, `StructureTreatment`, `DoseTreatment`, continuity score, local dose factor, and preserved/moderated features.
- Recovery first changes dose. Systemic readiness/OFI pressure affects the global resistance budget; tissue restrictions affect only their explicit stableKeys. Real court cost affects lower-body sport interference even when structured-badminton generation is disabled.
- Gaps first reallocate a finite weekly resistance-set budget. Capacity expands only for the documented minimal representation case; selected gap work is never simply added on top without accounting.
- Resistance working sets and timed structured-badminton bouts are separate quantities, but the resulting items share the same per-session time-capacity validation.
- Multi-day execution is per anchor and constrained by its allocation and recovery. The strongest legitimate exposure in the latest observed week is the load reference.
- Each anchor's retained multi-day variants occupy distinct generated days. A two-day plan keeps the existing HLM/Madcow or DUP pair semantics, including the heavy-exposure moderation pair.
- Canonical posterior change remains continuous through transition calculation, while posterior observation count—not workout-session count—owns response confidence.
- Gap truncation uses the existing HIGH, MEDIUM/MODERATE, LOW order with stable input order for ties. Projection repair retains higher existing selection priority without changing display order, and decision budget/fingerprint are finalized only from repaired returned items.
- There is no unconditional four-set floor and optional/rotated anchors do not force expansion. Narrow favorable-recovery expansion for a selected HIGH resistance gap is explicit as `MINIMAL_CAPACITY_EXPANSION`.
- A generated future week does not automatically increase load. Without new completed evidence, the current planned microcycle repeats.
- Novel exercises keep load unknown and use an RPE/load-finding prescription. Strength intent affects progression and allocation; `PREFER_FAMILIAR`, `WILLING`, `AVOID`, and `UNRESOLVED` have distinct candidate behavior.

## Preflight contract

`preparePersonalizedProgram(...) -> PersonalizedPlanningPreflight` returns all currently material questions together. The UI collects every required answer before the final create action. Dismissal stores nothing.

`generatePreparedPersonalizedProgram(...) -> GeneratedProgramSkeleton` receives the prepared cutoff, explicit constraints and complete answer set. It cannot return another question and does not move to a later date boundary while the user is away.

Record-based duration and weekly days have visibly separate AUTO/manual controls and resolve within 2..6 weeks and 2..5 days. Only an actual user selection overrides AUTO, and selecting AUTO again clears the override. Session minutes remain an explicit constraint. The legacy builder retains its wider ranges, and the legacy badminton-to-strength ratio is ignored on the record-based path.

Strength-intent preferences store the answer timestamp and profile goal at answer time. They become eligible for reconfirmation after 56 days or when the profile goal changes. Planner preferences and decision provenance remain portable `app_meta`; no Room schema migration is required.

## Capability-consumer matrix

| Capability | Canonical producer | Planner effect |
|---|---|---|
| Confirmed cutoff history | Room workout records | future/unconfirmed rows excluded |
| Movement/exercise identity | runtime canonical metadata | exact stableKey anchors, gaps and candidates |
| Strength response | exercise-local posterior history | 56-day response, advance/hold/reduce/review |
| Style and features | confirmed anchor history | observed history separated from next-block treatment |
| Objective exposure | Objective Stimulus V2 | 28+28 day drop and 56-day developmental gaps |
| Generic court load | `BadmintonPracticeLoadCalculator` | weekly-equivalent recovery and lower-body interference |
| OFI/readiness | production fatigue/readiness services | systemic dose and schedule ceiling |
| Tissue RCV | production tissue service | exact-stableKey local dose restriction |
| User intent | preflight answers/preferences | real progression, modality and drill-selection effects |
| Weekly budgets | v0.10 transition/execution policy | finite resistance allocation plus separate drill bouts |

## Verification

`PersonalizedPlannerParityTest` preserves the 29 named v0.8 regression personas as historical-coverage protection and now makes Persona 28 genuinely Madcow-like in both favorable and pressured contexts. `PersonalizedPlannerV010Test` covers fixed windows, continuous recent posterior response, 28+28 objective comparison, court-load normalization, intent/cost separation, global/local recovery, preflight, explicit/reversible AUTO constraints, per-anchor finite allocation and distinct variant days, finalized repair provenance, priority truncation, bounded capacity expansion, strongest latest-week load, no future progression, separate drill dose, and canonical identity.

Intentionally unchanged in v0.10.1: global gap pressure, active-week resistance baseline semantics, the provisional two-bout structured badminton default, conservative `provenTwice` multi-day progression gating, and all existing style/dose detection thresholds.

`RealBackupPersonalizedPlannerE2eTest` now uses prepare-once/answer-all/generate-once. It remains opt-in because the user's backup must never enter source control.

Reference oracle: offline `wgtd_planner_reference_v0_10_FULL`. Its Python code and 22 passing tests are behavioral evidence. Production intentionally retains stronger canonical Android authorities and uses separate resistance-set and timed-drill budgets plus exact local tissue stableKey restrictions.
