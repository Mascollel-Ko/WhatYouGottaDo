# Record-based planner v0.12.0 implementation note

Baseline: `1c6e19343a9c2d091eaa38b061dfd93a1e05d4cb`. Canonical protocol: `3.2.0`.
The user's follow-up permits SUPPORTIVE assistance and proactive core questions. This supersedes the older direct-only candidate policy, not Objective V2 arithmetic.

## Preserved boundaries

- The legacy `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder` path is unchanged. Personalized generation returns the same editable skeleton and reuses save/apply.
- Only confirmed sets on/before cutoff enter history; the strength style/posterior window remains 56 days. Future and unconfirmed work is excluded.
- Representation still uses adjacent 28-day windows and cutoff-anchored seven-day evidence bins. The .25/.50 outlier thresholds, movement priorities, peer medians, normalized retention, weighted/DIRECT separation, Objective V2 coefficients and RPE modifier are unchanged.
- Relative distribution is not physiological sufficiency. No absolute weekly set requirement or equal nine-objective target is defined.
- NEVER_DIRECT_OBSERVED remains optional non-pressure evidence. It cannot change transition, structure, or dose by itself.
- Readiness/OFI/systemic recovery arithmetic and tissue MODERATE=.30 (ELEVATED compatibility) remain unchanged. No posterior or physiological model is redesigned.
- All semantic identities and relations are exact stableKey/typed metadata. Display names, substrings and fuzzy similarity do not classify exercises.

## Execution sequence

Previously: resistance allocation -> separately appended performance work -> fixed item-count/time repair.
Now: snapshot -> representation/gaps/transition -> safe candidate prescriptions -> finite cross-domain allocation -> core/material/discretionary timed placement -> residual repair -> final exact counts/fingerprint.

- Material gaps buy capacity from discretionary incumbent work across resistance, structured badminton and athletic/assistance domains. Units share scheduling capacity, not physiological meaning.
- Existing recent performance continuity participates in the same finite allocation; it is not lost merely because resistance anchors own strength continuity.
- Feasible DIRECT work leads. Explicit SUPPORTIVE relations admit assistance where needed, but GENERAL/LOW and exercise-wide similarity do not. Assistance is separately labeled and never clears a direct-exposure deficit.
- Stability authority plus an explicit SUPPORTIVE relation makes assistance executable as athletic-performance work, not resistance sets or DIRECT exposure.
- Material minimums are two resistance sets or an indivisible reviewed performance prescription. Explicit objective overlap funds one item once; canonical family/redundancy qualities discourage duplicate drills.
- Optional development follows material work and does not overwrite material provenance.
- Multi-day HLM/Madcow/DUP variants retain their distinct-day contracts, strongest legitimate recent-week reference, and existing progression policy. No automatic future-week weight increase is introduced.

## Capacity envelope

Records: explicit days/minutes, available seconds, observed session count, median/p75 controllable units, median session seconds, active-week workload, recovery factor, court context, useful demand, estimated scheduling bound, final allocated units.

For observed history:

```text
dayScale = max(1, requested days / observed weekly days)
timeScale = max(1, requested session seconds / median observed session seconds)
densityBound = ceil(max(resistance baseline, observed controllable weekly units)
                    * systemicDoseFactor * dayScale * timeScale)
scheduleBound = available seconds / max(45, median session seconds / median session units)
capacity = min(useful demand, densityBound, scheduleBound)
```

No-history density is bounded by justified demand; missing per-unit timing uses 135 seconds for the coarse envelope. Sparse history keeps the narrow favorable-recovery minimum-expansion policy. Final placement always checks each actual prescription (work time, or 45-second resistance estimate, plus between-set rest).

- Explicit days and minutes are available capacity, not forced work. More availability releases deferred useful demand, not filler.
- Fixed 4/5-item validation/repair caps and the separate high-court item cap are removed.
- Generic court load stays weekly-normalized recovery/auto-frequency and lower-anchor interference context. It supplies neither objective stimulus nor a second item-count tax.
- Placement funds retained cores, meaningful material work, then continuity incrementally. Typed lower-limb/impact metadata spreads stress. Repair is residual safety projection, not the priority chooser.

## Performance prescription authority

1. Exact recent confirmed execution within 56 days: hold demonstrated set shapes without performance progression.
2. Exact stableKey prescriptions in multi-week canonical program seeds: executable unloaded sets from the earliest block; exclude short assessment programs and loaded test prescriptions.
3. Existing reviewed `ProgramRuleTables` / `ProgramIntensityResolver` badminton prescriptions.

A rest-only timing row cannot define reps, rounds, work duration or load. Unresolved candidates stay deferred with NO_SAFE_PRESCRIPTION_AUTHORITY; no one-off exercise-name prescription is invented.

## Preflight and horizon

- Every preflight asks strength goal, badminton inclusion and free-weight willingness together, even with resolved history/preferences. The scrollable dialog requires valid answers to all three.
- Invalid/UNRESOLVED tokens cannot generate. Dismissal stores nothing. Saved timestamps/profile context remain provenance but no longer suppress questions.
- Prepare fixes cutoff and constraints; generate consumes that preflight once without moving its date boundary.
- Separate AUTO/manual duration and days remain 2..6 weeks and 2..5 days. Explicit duration is authoritative; session minutes remain explicit.
- Normal AUTO horizons respect adaptation intent (badminton minimum four weeks). Actual recovery constraints or sparse history can use a short bridge; unknown intent cannot.
- Legacy ranges and badminton:strength ratio behavior are unchanged; the ratio is not used by personalized generation.

## Persistence and verification

Additive `planningBudget.execution` JSON records candidate audit, prescription source, separate domain counts, direct representation, supportive allocation and deferred reasons. SUPPORTIVE_ONLY_DIRECT_EXPOSURE_NOT_REPLACED means assistance was actually scheduled while direct exposure remains unresolved.

Existing portable `app_meta`, program items and per-set prescriptions carry save/edit/apply/backup/restore; no Room migration. Applied future work remains unconfirmed. Final counts and fingerprint describe returned items.

The independent Python execution reference/golden complements unchanged v0.11 representation golden and the offline v0.10 reference. Tests cover finite allocation, duration/day capacity, safe authority, supportive execution, horizon, proactive-question Compose behavior, all 29 historical personas and opt-in real-backup restore/save/edit/apply/restart/cutoff behavior. Python is test-only; private backup data is not committed.

Limitations: time/stress placement is deterministic heuristic, not an optimal schedule or physiological equivalence model; missing executable authority can still defer a candidate; more time does not require more work once useful demand is exhausted.
