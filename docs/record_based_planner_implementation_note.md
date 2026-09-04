# Record-based planner v0.11.1 implementation note

v0.11 implementation baseline: `517f43d5743daccf431d87af31870a2bcf65bb53`.
v0.11.1 correction baseline: `be70308a79dd1cc76cec55d04dce9d4852f054d1`.

The legacy automatic path remains `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder`. Record-based generation remains independent, produces the same editable `GeneratedProgramSkeleton`, and reuses the existing save/apply flow only after successful generation.

## Authority and current-block windows

- Only `confirmed=true` sets on or before the explicit cutoff enter planning history. Future and unconfirmed work is excluded.
- Exercise identity, movement coverage, activity kind, planning eligibility, Objective V2, tissue contribution and candidate admission remain canonical `stableKey`/typed-metadata authorities. Names and substrings do not create semantics.
- Per-anchor strength style, style features and current-block classification use only `cutoff - 55 days ... cutoff`.
- Canonical exercise-local strength posterior change uses the same 56-day window. Fewer than two eligible observations yields `UNKNOWN`, never a fabricated zero or lifetime change.
- Exposure representation compares `cutoff - 27 ... cutoff` with the adjacent `cutoff - 55 ... cutoff - 28` window. Evidence stability uses exactly four cutoff-anchored seven-day bins, never ISO calendar weeks.
- The 28-day generic court-load total is retained for provenance and divided by four before weekly 180/240 planning thresholds are applied.

## Transition and execution

- The planner does not calculate exposure sufficiency. It records `ABSENT`, strong/ordinary underrepresentation signals, `NO_CLEAR_DEFICIT_SIGNAL`, or `UNKNOWN`; no state claims physiological adequacy.
- Movement representation counts one confirmed resistance working set as one distribution unit. Shares are normalized inside the active required set; personal retention compares current share with prior share. Peer reference requires two positive same-priority peers and uses their median.
- The centralized `.25/.50` ratios are engineering outlier rules, not biological thresholds. Low evidence suppresses relative conclusions, while factual absence remains visible. Zero current resistance creates one `RESISTANCE_FOUNDATIONAL_ONRAMP`, not independent severe gaps for every domain.
- Objective V2 preserves existing weighted coefficients/RPE modifiers and separately counts DIRECT-only exposure. Personal normalized-share decline is primary; a median of at least three positive peer objectives is secondary and peer-only evidence is capped at MODERATE.
- `DIRECT_DROP` remains factual disappearance. `NEVER_DIRECT_OBSERVED` is developmental evidence, contributes no normal transition pressure, and can produce at most one optional block candidate after stronger work.
- Transition `gapPressure` filters on `contributesTransitionPressure`; retaining a non-pressure developmental gap for provenance or optional selection cannot change an anchor's rotation, structure, or dose result by itself.
- Production tissue `MODERATE` maps explicitly to `.30`; legacy/test `ELEVATED` remains a `.30` compatibility alias. Readiness, OFI, tissue thresholds, local stableKey restriction and the systemic recovery formula are otherwise unchanged.
- Direct remediation requires an exact DIRECT relation for the objective. A novel athletic/badminton candidate must have reviewed `ProgramRuleTables`/`ProgramIntensityResolver` authority or legitimate recent personal prescription history; otherwise `NO_SAFE_PRESCRIPTION_AUTHORITY` is retained and no prescription is invented.
- Typed role/capability, canonical activity kind, progress behavior and runtime metadata classify `RESISTANCE`, `STRUCTURED_BADMINTON_DRILL`, `ATHLETIC_PERFORMANCE_DRILL`, `GENERIC_COURT_SESSION`, and `OTHER`. Names and display categories are not semantic inputs.

- Observed style describes history only. Each anchor also records multidimensional features, adaptation state, `StructureTreatment`, `DoseTreatment`, continuity score, local dose factor, and preserved/moderated features.
- Recovery first changes dose. Systemic readiness/OFI pressure affects the global resistance budget; tissue restrictions affect only their explicit stableKeys. Real court cost affects lower-body sport interference even when structured-badminton generation is disabled.
- Gaps first reallocate a finite weekly resistance-set budget. Capacity expands only for the documented minimal representation case; selected gap work is never simply added on top without accounting.
- Resistance working sets, structured-badminton bouts and athletic-performance bouts are separate quantities, but the resulting items share the same per-session time-capacity validation.
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
| Movement representation | confirmed resistance sets + typed activity domain | current/prior normalized share and same-priority peer signal |
| Objective exposure | Objective Stimulus V2 | 28+28 weighted/direct representation and factual DIRECT drop |
| Generic court load | `BadmintonPracticeLoadCalculator` | weekly-equivalent recovery and lower-body interference |
| OFI/readiness | production fatigue/readiness services | systemic dose and schedule ceiling |
| Tissue RCV | production tissue service | exact-stableKey local dose restriction |
| User intent | preflight answers/preferences | real progression, modality and drill-selection effects |
| Weekly budgets | v0.10 transition/execution policy | finite resistance allocation plus separate drill bouts |

## Verification

`PersonalizedPlannerParityTest` preserves the 29 named historical regression personas. `PersonalizedPlannerV010Test` retains all v0.10.1 correction contracts. `ExposureRepresentationV011Test` checks the typed activity resolver, exact window boundaries, movement and Objective V2 representation states, DIRECT/weighted separation, behavioral non-pressure transition invariance, production tissue status mapping, direct candidate authority and Kotlin/Python golden parity. Its raw-history badminton cases construct `PlanningSetRecord` and canonical Objective V2 relations, then compare Python golden results with the real `BadmintonObjectiveRepresentationAnalyzer` output.

Intentionally unchanged in v0.11.1: the 56-day style/posterior window, transition priority ladder, recovery arithmetic other than the missing production `MODERATE` token mapping, conservative `provenTwice` progression, existing Objective V2 coefficients/RPE modifier, legacy builder, app version and Room schema. No absolute weekly-set target or equal nine-objective target was added.

`RealBackupPersonalizedPlannerE2eTest` now uses prepare-once/answer-all/generate-once. It remains opt-in because the user's backup must never enter source control.

The unchanged v0.10 transition/execution oracle remains offline `wgtd_planner_reference_v0_10_FULL`. The new layer has an independent test-only reference at `tools/planner_reference/v011_exposure_representation_reference.py` and deterministic fixture at `tools/planner_reference/fixtures/v011_exposure_representation_golden.json`. No Python runtime dependency enters Android production.
