# B-Coach three-lift Shadow first-set production bridge

Date: 2026-11-22
Branch: `feat/b-coach-three-lift-shadow-20261122`
Parent branch: `feat/b-coach-squat-shadow-production-bridge-20261121`
Status: **DRAFT, not built or device-verified**

## Exact exercise identity

| UI exercise | stableKey | curve assignment | load semantics |
|---|---|---|---|
| 컨벤셔널 데드리프트 | `barbell_deadlift` | `GENERAL_FALLBACK` with uncertainty | barbell total displayed load |
| 바벨 벤치프레스 | `barbell_bench_press` | `EXACT_EXERCISE` bench curve | barbell total displayed load |
| 덤벨 벤치프레스 | `ex_3a7d3eda` | `BORROWED_WITH_UNCERTAINTY` bench-shaped *prior*, NOT barbell bench posterior | `IMPLEMENT_TOTAL_LOAD`: total displayed load of both dumbbells |

The registered `LOCAL_INNOVATION_SHARED_ONLY` dumbbell-bench loading is NOT evidence that barbell bench's absolute e1RM applies. Each Shadow proposal is anchored to actual confirmed sets for its **same** stableKey. Do not multiply or divide dumbbell kg silently. The display and opt-in clearly state the two-implement total.

## Production scope

1. Review uses an independent short-term `HOLD`, `LOAD_UP_ONE_STEP`, `REPS_UP_ONE` comparison. It is a reproducible **exploratory policy**, not a validated prediction of training success or a four-week adaptation model.
2. Only fully confirmed, exact-exercise, explicitly loaded sets with a supported RPE and recent (<=56 days) record can form an anchor. If a newer unfinished exercise record exists, withhold the recommendation.
3. Barbell increment is two one-side plates; dumbbell increment is exactly the user-entered *displayed total* increment. A historical step mismatch fails closed.
4. Require the exact candidate's earliest occurrence first-set reps and target RIR to match; don't touch user-fixed prescriptions or incompatible predicted B loads. Suggested load needs to remain within five percent of the originally shown B first-set load where that load exists.
5. Three individually default-off review checkboxes; only selected exercises are modified.
6. All selected experiments are applied **after** regular B-Coach proposal fingerprint acceptance and signed together. Only the first planned set of the first occurrence changes; metadata includes stableKey, source evidence hash, kg, reps, and displayed unit. Subsequent sets, weeks, unrelated exercises and historical records are untouched. The original multiobjective score is flagged as based on the prior candidate.
7. **Not included**: extending the Gate H automatic next-linked-workout service (currently `BCoachLinkedSquatNextWeekService`) to these three lifts. It requires per-exercise source/target comparison, equipment and rep schemes, dumbbell total-load validation, and transaction-safe regression before activation.

## Validation pending (do not merge yet)

- Android Gradle unit tests and Robolectric `BCoachAdditionalLiftShadowTest` plus previous `BCoachMultiobjectiveProductionTest`, `BCoachSquatShadowProgramBridgeTest`, `BCoachProductionReviewDialogUiTest`.
- Two-row per-set test: follow-up set, future week and signed audit remain intact after save/reload and program application.
- Per-exercise no-anchor / missing RPE / partial confirmation / stale record / grid conflict / wrong load unit / user fixed / stale proposal fail-closed tests.
- Actual Android review UI at small width, increased font, 3-button and gesture navigation.
- CI status and Debug APK with workflow secrets; absence of a GitHub run is not a pass.
- If all pass, merge PR #89 into PR #88's branch, PR #88 into PR #87's branch, PR #87 into the global optimizer branch and only then consider main according to normal release criteria.
