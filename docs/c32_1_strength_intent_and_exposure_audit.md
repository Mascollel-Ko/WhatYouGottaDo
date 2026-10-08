# C32.1 — Planned Strength Intent and Realized Exposure

C32.1 separates the purpose of a planned set from the evidence that the athlete actually performed a Strength exposure. A low realized rep count no longer replaces exact planned intent, and historical low-rep sets without a reliable load or effort signal remain uncertain instead of being counted as zero Strength work.

The approved C32 capability remains bounded project policy, recorded as `USER_APPROVED_PROJECT_POLICY` under `C32_STRENGTH_EXPOSURE_V1`. It is not a claim that these are universally optimal physiological thresholds. The only capable stable keys remain `barbell_back_squat`, `ex_c5043892`, `barbell_deadlift`, `ex_e41f4c2b`, `ex_e41e8dcf`, `barbell_bench_press`, `ex_3a7d3eda`, `ex_32219f7a`, `ex_79f3bdbe`, and `ex_bb4b4276`.

The production conflict was that several approved keys have only `SUPPORTIVE_CAPABILITY` in the broad physical-quality relation table, while C32 grants Strength exposure identity through an exact ten-key policy. Also, historical Strength classification still required the stronger load/reference/effort evidence used by prescription authorization. Finally, history rows did not distinguish an exactly linked planned Strength set from a set whose low repetitions merely resembled one.

`StrengthPlannedSetIntentResolver` now follows the persisted application, workout link, source program/item, progression binding, and exact planned set prescription to restore intent for the exact confirmed entry/set. It requires the approved exercise identity, the canonical Strength selection role, and planned repetitions in 1–6. It does not infer a link from exercise name, date, or similar repetitions. If that exact linkage is unavailable, the record uses the unplanned-history assessment.

For an exactly linked planned Strength set, realized repetitions at or below plan and up to two repetitions above plan receive Strength credit. Above-plan sets retain a typed `OVERPERFORMED` assessment. More than two extra repetitions require any one reliable signal: relative load at least 70% of reference 1RM, observed RPE at least 6, or reliable RIR at most 4. Clearly low relative load together with low effort is reviewed as not Strength; missing evidence leaves the set `UNCERTAIN`. A planned Strength set is not silently relabeled as Hypertrophy because actual repetitions exceeded plan.

For an unplanned historical set, only an approved stable key and 1–6 actual repetitions can be a Strength candidate. One of the same reliable load/effort signals confirms it; absent evidence remains `UNCERTAIN`. Whitelist-external exercise identities, higher-rep unplanned sets, and low-rep Power/task work do not become Strength. Hypertrophy continues through its own existing realization requirements.

The exact ten-key policy now controls B1/B2 exposure identity and B5 Strength eligibility even when legacy physical-quality relations are supportive. C32.1 does not turn that exposure policy into B6 prescription permission. Existing load, reference, calibration, RPE/RIR, capacity, and fail-closed B6 checks remain unchanged. `OVERPERFORMED` is preserved as typed ledger evidence; no progression increment or new dose formula was added.

Uncertain Strength history now remains partial evidence instead of being converted by the shadow B1 fallback into a definitive “develop Strength” decision. The user-facing shortfall notice and no-eligible-owner disposition remain as before. An approved exercise at 7 repetitions is not a Strength set, and an exercise outside the whitelist cannot satisfy Strength regardless of low repetitions or legacy metadata strings.

## Corpus result

The same C31/C32 generated corpus was rerun from start SHA `67eb6a609df8751424298a4b05d33a7353c7b1f4`. In the 22 sparse movement contexts, B3 remains ADDRESS 22, B4 remains 22 direction-only movement targets, B5 movement owners remain selected in the current selection traces, and movement executable/unauthorized rows remain 0/0. The corpus does not gain movement doses or change routing.

Across the full 22 generated cases, the census classifies 108 historical sets as realized Strength, 0 as overperformed, 0 as uncertain, and 573 as not Strength. B1 reports 108 prior-window direct Strength units and 0 current-window direct units; B2 reports 108 direct units, 0 unclassified units, and 79 numeric-baseline-eligible weeks. Sparse Strength owner selection remains 12 contexts in two cases; no sparse Strength B6 target is newly authorized. Four active sparse cases retain their existing typed shortfall notices, while the no-minimum case has no false shortfall. The full generated corpus still contains 30 exact Strength target/role/repetition set rows, each checked against an exact authorized Strength B6 owner.

All 22 generated cases remain CONTROL-routed. Build accounting remains CONTROL 22, EXPERIMENTAL 22, TOTAL 44, THIRD 0. Power and JUMP_LANDING remain closed, and the C31 movement targets remain without executable movement material. The deterministic results are in [`c32.1-strength-intent-exposure-census.json`](c32.1-strength-intent-exposure-census.json).

Protocol advances from 3.64.0 to 3.65.0, and runtime advances from `RECORD_BASED_PLANNER_0.15.6_KOTLIN_1` to `RECORD_BASED_PLANNER_0.15.7_KOTLIN_1`. App remains 0.5.1.5 and Room remains 38; no persistence schema changed.

## Local verification and artifacts

The focused Strength intent/exposure and B1/B2 regressions passed 55 tests. The full local JVM suite passed with 2,364 tests, 0 failures, 0 errors, and 4 skips. `compileDebugKotlin` and `compileDebugUnitTestKotlin` passed, and `assembleDebug` succeeded. The default JBR 21 test worker first hit a Robolectric `robolectric-nativeruntime.dll` access violation without an assertion failure; rerunning the suite with the existing external JDK 17 worker-restart workaround passed. The workaround and its init script remained outside the repository.

The local debug APK is 70,735,541 bytes with SHA-256 `306674E307ECA4DEE0AF606891CEE3DBC527226A323874BB82128EAA589F2E58`. The checked-in deterministic census was regenerated from the test report; its SHA-256 is `2133DE71D898CFFE2E08FF3A581CB759810BEF7ECD94939CE295629570C16EF3`. `lastAuditedCommit` points to the implementation/test commit `a082478506db5026445cc44c50259d7a2df05796`, not this documentation update.
