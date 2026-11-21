# B-Coach Squat Shadow → actual program: bounded approval bridge

Date: 2026-11-21  
Scope: B-Coach V22 block generation and linked applied squat progression.  
Implementation branch: `feat/b-coach-squat-shadow-production-bridge-20261121`  
Status: **DRAFT / NOT YET APK-VERIFIED**

## 1. Distinguish two existing Shadows

- `BCoachSportShadowReview` re-scores pre-generated whole-block candidates and **remains read-only**. It does not generate new exercise rosters or apply anything.
- `BCoachSquatShadowProgramComparator`/Gate F evaluates short-term actions for the latest confirmed **back squat**, using canonical repetition curve and RPE→RIR discrete evidence. It has **not** validated four-week adaptation, later-set fatigue, independent day-to-day outcome uncertainty or objective coefficients. Do not claim a future success probability.

## 2. What was connected to production

1. When producing a B-Coach candidate, the user selects a reviewed A/B/C plan as before. The read-only squat Shadow is examined against **that exact selected plan**.
2. Only when comparison status, Gate F selection, anchor date (within 56 days), plausible load increment (max 2% relative to HOLD), repetitions, working-set count, exact squat identity and user/manual locks agree, the review UI exposes an **unchecked opt-in** for applying the Shadow's decision to **the first set of the first scheduled back-squat occurrence only**.
3. The opt-in invokes the regular candidate fingerprint/stale-input approval path *before* applying the narrowly scoped first-set adjustment. The accepted program marks the first set `EXPLICIT_LOAD / USER_FIXED`, stores an explicit provenance/evidence audit, and recomputes its accepted structure fingerprint.
4. The original multiobjective scores correspond to the unmodified candidate; the accepted fingerprint remains the original candidate fingerprint for scoring provenance. `markAcceptedAndPersisted` flags the final prescription as adjusted rather than claiming unchanged model scores.
5. Unapproved recommendation B/C candidates, future weeks, follow-up sets, prior confirmed workouts and the separate sports Shadow are unchanged.
6. The existing exact application/track/sequence progression chain continues to support next unstarted back-squat entries after a fully confirmed workout. When source or target sets have different repetitions or loads, production may select only the comparable first set and preserve all other set rows, original prescriptions and user edits. Approval runs inside the database transaction and validates the evidence fingerprint again.
7. Grid mismatch is **not** auto-confirmed just because a plate size was entered. Unsupported evidence, changed rep schemes, an incomplete source, manual override, missing link or previous decision all fail closed.

This is **not** a blanket activation of Gate C's unvalidated policy coefficients, automatic promotion of the Shadow ranking to the recommended whole-block winner, forced increase, or full four-week replanning. It is an explicit bounded connection to a saved program plus the already constrained next-linked-workout path.

## 3. Other-exercise applicability

| Tier | Exact scope | Current available authority | Production status |
| --- | --- | --- | --- |
| A | `barbell_deadlift`, `barbell_bench_press` | Canonical exact-key e1RM posterior and repetition curve route already exists in `StrengthPerformanceRegistry`, `BCoachV2LoadCandidateResolver` | Possible next; needs independently validated confirmed-session link, load-grid and multi-set progression bridge. **Not activated by this change.** |
| A-special | `ex_e41f4c2b` weighted pull-up | Canonical target with `BODYWEIGHT_PLUS_ADDED_LOAD` | Needs **contemporaneous bodyweight + additional weight**, correct total-load/rep curve and grid; cannot copy barbell load arithmetic. Not activated. |
| B | Exercise-local posterior with established baseline and reviewed proxy | `BCoachV2LoadEvidenceReader` can expose a valid `TIER_B` posterior | Must first verify exact stableKey, local history sufficiency, nonstale fit and equipment semantics. No generalized auto progression. |
| C | Other ordinary weighted exercises with confirmed sets but no comparable posterior | `BCoachExerciseShadowCoverage` shows unranked HOLD/REPS_UP/possible equipment-step checks | May propose manual review; no pseudo e1RM or model-derived automatic increase. |
| D | Core, badminton footwork, timed/quality drills, generic recreational sport sessions | Duration / quality / cadence / movement metrics; some have no direct progression contract | Never force back-squat RIR or kg progression. Design task-specific metrics and distinguish actual recreational court activity from programmed drills. |

The four canonical tier-A exact keys are back squat, conventional deadlift, flat bench, and weighted pull-up. Their existence does **not** imply a validated shared next-session policy. The current squat bridge explicitly gates by exact `barbell_back_squat`.

## 4. Required verification before merge

- Run Gradle `BCoachSquatShadowProgramBridgeTest`, `BCoachMultiobjectiveProductionTest`, `BCoachObservedSquatProgressionTest`, `BCoachSquatShadowProgramComparisonTest`, `BCoachSquatCurveProgressionRankerTest`, and production/application persistence tests.
- Test first-set approval with HOLD/LOAD/REPS candidates, invalid plate grid, missing posterior/RIR, stale evidence, user fixed sets, different weekly structure and unrelated A/B/C acceptance.
- Test next-linked-record auto progression after mixed set loads: only approved first set changed, all follow-up source/target rows unchanged. Reject shortened/incomplete source, manually edited future target, superseded applications, split tracks, mismatched reps and delayed dates correctly.
- Test actual small-screen review/dialog and metadata persistence after save/reopen/apply; verify signed Debug APK and CI.
- Never infer release readiness from unexecuted test code or source inspection alone.
