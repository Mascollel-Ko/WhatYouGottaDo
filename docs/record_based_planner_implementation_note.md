# Record-based planner v0.13.0 implementation note

Baseline: `d299800438410f9022add04f3601d20a3ffd722e`. Canonical protocol: `3.3.0`.

## v0.13 longitudinal state and interruption-aware workload

Runtime: `RECORD_BASED_PLANNER_0.13.0_KOTLIN_1`. The v0.12 sections below describe retained execution machinery; the equations in this section supersede its old soft recovery factor and active-week mean reference.

- Input adapter reads canonical `DailyFatigueCalculator.calculateSeries` for 56 days including all real court physiology. It does not modify OFI, readiness, tissue, posterior or Objective V2 engines. Current/prior adaptation windows remain adjacent 28 days. OFI recent/prior windows are 7 days; personal baseline is -55..-7, excluding the current seven days.
- OFI7 is the recent median; baseline scale=max(8,1.4826*MAD). S=.30*absolute+.25*relative+.15*persistence+.10*positiveTrend+.10*maxAxisElevation+.10*max(readinessSoft,tissueSoft), normalizing available terms only. Absolute=clip((OFI7-55)/35); relative=clip(z/2); persistence=fraction above personal baseline p80; trend=clip((recentMedian-priorMedian)/15,-1,1). Five axes use the same personal robust z rule. Engineering p80 uses nearest-index quantile, not interpolation.
- Readiness soft READY/NORMAL/GOOD=0, CAUTION=.30, FATIGUED=.60, UNKNOWN=.15; tissue NORMAL=0, LOW=.10, MODERATE/ELEVATED=.30, HIGH=.60, UNKNOWN=.10. LIMITED/VERY_HIGH/BLOCKED are not softened away. Exact tissue contributors define local restrictions; severe tissue without local contributors or LIMITED caps the whole plan (.80/.75). Soft-generated human readiness advice is not reinterpreted as an independent hard gate. Explicit validated pain/avoid tags are separate and restrict canonical movement/stress candidates.
- Per-exercise posterior response=tanh(changePercent/5), with at least two observations; confidence=clip(count/6). Raw 28/28 matching: load uses reps ±1 and current RPE <= prior+.5; absent RPE requires exact reps. Reps use load ±2%; RPE uses load ±2% and reps ±1. Each prior set is used at most once per component, nearest reps/load then date/index. Distinct comparison dates, not working-set duplicates, own raw confidence; missing matched RPE scales it by .6. Supported kg metrics only: LOAD_REPS/VOLUME_LOAD/ESTIMATED_1RM. No machine/free-weight conversion.
- Raw response normalizes available .40*clip(loadChange/.05)+.35*clip(repDelta/3)+.25*clip(RPEEfficiency/1.5), each component -1..1. Posterior/raw weights .65/.35 normalize available sources; correlated confidence=max(source confidences), never their sum. Confidence-weighted median per major movement precedes cross-movement aggregation and ±.20 breadth. Missing evidence is unavailable, not a negative observation; accessory PRs do not add major-family votes.
- T normalizes .35*workloadStability+.25*dayStability+.20*densityStability+.20*RPEStability. Stability=clip((current/prior-.60)/.30); RPEStability=clip(1-max(0,drift)/1.5). External/event weeks are excluded from both numerator and observed-week denominator. Unknown low weeks are not automatic tolerance-failure evidence in M.
- P normalizes .50*clip((A+.20)/.80)+.30*T+.20*positiveBreadth. M normalizes .40*clip((-A-.10)/.50)+.25*negativeBreadth+.20*(1-T)+.15*clip(max(0,RPEdrift)/1.5). Priority: hard, LOW-evidence uncertain, M>=.65 with negativeBreadth>=.50 maladaptation, S>=.60/P>=.65/M<.30 productive-high, S>=.60/P>=.45/M<.45 tolerated-high, S>=.60/M>=.40 accumulating, S<.60/P>=.65 productive-normal, else stable. Exactly flat A=0/T=1/breadth=0 yields P=.425 and STABLE, not a falsely positive label.
- HIGH confidence requires >=28 baseline days, >=3 major response families, >=3 active current weeks; MODERATE requires 14/2/2; else LOW. Global soft dose=clip(1-.15*S*(1-P)-.10*M,.80,1); LOW evidence HOLD=1; hard caps remain separate. The capacity planner applies this once, after demand/availability bounds, not again at demand/anchor/horizon/frequency. Local negative response, local tissue and existing lower-anchor court interference remain local. Normal badminton AUTO remains 4..6 weeks; accumulating=4, maladaptation=3, global hard=2; explicit choices win.

### Twelve-week scheduling context (not a physiological capacity estimate)

Hard restrictions also guard prescription progression: severe tissue with no local contributors uses the existing REDUCE prescription, and explicit profile restrictions prevent automatic ADVANCE even when posterior performance is positive. This changes admission to the existing progression actions, not the load/e1RM rules themselves. Canonical daily strain before the first confirmed history date is not counted as observed baseline evidence.

Cutoff-anchored complete 7-day bins, up to 12 weeks, record controllable units/minutes/days, canonical court load, matched major movement performance and median RPE. A two-pass surrounding ±3-week median excludes initial low neighbors; `.625` of the resulting typical units flags low weeks. No name or private-event inference is allowed. Stable preceding two weeks, rapid return within two weeks and no observed deterioration permit EXTERNAL_INTERRUPTION_LIKELY; court >=1.5 surrounding positive median plus return permits EVENT_OR_TAPER_LIKELY. Explicit EXTERNAL/EVENT answers govern low-week context; FATIGUE or broad decline/RPE worsening gives RECOVERY_REDUCTION_LIKELY; otherwise unresolved. Actual physiological history is never removed.

Sustainable runs require >=2 contiguous NORMAL nonzero weeks, successive workload ratio .70..1.40, no broad negative movement majority, matched RPE drift <=1, measurable response >=-.10. Run medians are weighted by duration²*.92^ageWeeks; one extreme week cannot define capacity. HIGH needs measured performance and longest>=4 or multiple successful runs totaling>=4 weeks; MODERATE longest>=3 or total>=4; otherwise LOW. These are engineering evidence-duration rules, not MRV, maximum safe or optimal workload.

Repeated ambiguous low weeks trigger cause/frequency questions alongside the unchanged proactive core three. UNSURE/MIXED remain valid; preferences are additive portable app_meta strings. Frequent external interruptions produce CORE_MUST_DO/IMPORTANT/OPTIONAL_CAPACITY placement tiers, preserve useful units, and protect earlier logical sessions subject to the existing stress/time/distinct-style-day constraints. No weekday names are used.

With HIGH sustainable evidence, productive/tolerated state, no hard restrictions, sufficient requested days/minutes and useful demand, the envelope uses the demonstrated run units as a bound and can use normal-week resistance demand. It never automatically exceeds that evidence. Otherwise existing demand/time mechanics apply without a second systemic factor. The finite allocator and prescription authority are unchanged. SUPPORTIVE coverage cannot suppress a later feasible DIRECT candidate; its provenance remains separate.

### Verification and private comparison

`v013_training_state_cases.py --write` generates 16 raw-input synthetic cases; running without --write checks determinism. `TrainingStateParityTest` compares every typed assessment field and persisted JSON to the independent Python reference, plus capacity, ordering, questions, cutoff, physiological-history and duplicate-tax guards. The supplied private backup's immutable BEFORE was captured at the baseline before production edits. AFTER uses the same request/core answers/cutoff with additional context separately recorded; absent user answers use explicitly labeled UNSURE, never guessed life events. Private JSON, full programs, numerical inputs and round-trip CSV stay under ignored `build/planner-comparison`, never Git. Existing decisions lack the new field legitimately; no Room/app-version/tag change.

## Retained v0.12 execution design (historical recovery equations superseded above)
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
