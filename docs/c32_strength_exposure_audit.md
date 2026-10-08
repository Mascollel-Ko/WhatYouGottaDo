# C32 — Exact Strength Exposure Capability

C32 narrows what the planner may count as a Strength exposure. The approved rule is a bounded project policy, not a universally proven physiological optimum: an exercise must have the exact `strengthPossible` capability and the individual prescribed or realized set must contain 1–6 repetitions. Both conditions are required.

The capability is owned by `CanonicalStrengthExposureCapability` and records provenance `USER_APPROVED_PROJECT_POLICY` with policy version `C32_STRENGTH_EXPOSURE_V1`. Its complete approved stable-key set is:

| Exercise | stableKey |
| --- | --- |
| Squat | `barbell_back_squat` |
| Front squat | `ex_c5043892` |
| Deadlift | `barbell_deadlift` |
| Weighted pull-up | `ex_e41f4c2b` |
| Weighted chin-up | `ex_e41e8dcf` |
| Bench press | `barbell_bench_press` |
| Dumbbell bench press | `ex_3a7d3eda` |
| Overhead press | `ex_32219f7a` |
| Dumbbell shoulder press | `ex_79f3bdbe` |
| Seated dumbbell shoulder press without back support | `ex_bb4b4276` |

No movement-family, exercise-name, progression-token, program-slot, or category-wide rule grants the capability. In particular, Romanian deadlift, bodyweight pull-up/chin-up, one-arm dumbbell shoulder press, machines, accessories, and all other non-listed exercises remain ineligible even at low repetitions. Existing metadata such as `STRENGTH_PROGRESS`, `STRENGTH_VOLUME`, and `MAIN_*_STRENGTH` keeps its previous meaning and is not Strength exposure authority.

## One exposure rule across history and plans

Actual history, the exposure ledger, B1/B2 history, B5 Strength candidate eligibility, planned/final set classification, and B6 compatibility consume the same canonical capability-and-repetition resolver. A mixed prescription is classified per set: an approved weighted pull-up at 5/5/8 reps credits only the two five-rep sets as Strength. Zero or invalid repetitions do not create an exposure. Low-rep Power, rapid-force, reactive, badminton-task, and deceleration work remains outside this Strength capability.

Exposure eligibility is separate from prescription authorization. The existing exact B6 checks for owner and role, compatible evidence, load, effort, calibration, and fail-closed conditions remain in force. `strengthPossible` plus 1–6 repetitions grants Strength classification only; it does not approve a load or create a prescription. Hypertrophy classification still follows its own existing realization and prescription rules.

If an active Strength target cannot obtain an eligible owner or an executable B6 prescription, the planner preserves a typed shortfall and presents a localized notice while retaining the intact CONTROL fallback. A `NO_MINIMUM_TARGET` decision is not shown as a shortfall. The notice does not claim hypertrophy work replaced Strength work. No B7/B8 predicate or routing scope changed.

## Corpus result

The prior C31 sparse census had 22 movement contexts: B3 ADDRESS 22, B4 direction-only movement targets 22, B5 selected movement owners 22, and no exact movement B6 reuse. C32 reran those contexts without adding movement doses. B3 ADDRESS, B4 targets, and prior selected movement-owner lineage remain 22; movement executable owner-weeks and unauthorized movement rows remain zero.

In the 22-case generated corpus, the updated Strength need model produced 22 Strength decision contexts: four cases use `INTRODUCE_DIRECT_STIMULUS` and one case has `NO_MINIMUM_TARGET`. B5 selected 12 Strength owner contexts across two sparse cases; no new Strength B6 authorization was created from direction-only targets. Four active cases retain a typed Strength shortfall and user notice. The no-minimum case produces no false shortfall notice. The corpus remains CONTROL-routed; no third build is introduced.

The full generated corpus contains 30 experimental set rows that pass the C32 Strength exposure classifier and the audit verifies their exact owner/role B6 backing. This is distinct from the sparse cases' direction-only targets, which do not gain material. Across 22 generated cases, build accounting remains CONTROL 22, EXPERIMENTAL 22, TOTAL 44, THIRD 0. Power policy/material, JUMP_LANDING policy/material, task policy, C20 incumbent rules, and B7/B8 readiness rules remain unchanged.

The deterministic row-level census is [`c32-strength-exposure-census.json`](c32-strength-exposure-census.json). It records policy provenance, the exact approved keys, each of the 22 sparse target contexts, the C31 comparison, B1–B6 result, shortfall/notice, route, build counts, and corpus timing summary. Three repeated C32 Room/service corpus runs took 4,539 ms, 4,529 ms, and 4,980 ms (median 4,529 ms; mean 4,682.7 ms; range 451 ms). Across 22 cases, that is 212.8 ms mean per case. The full-suite census run measured 3,576 ms total (162.5 ms mean per case, 145.5 ms median, 393 ms maximum). C31's comparable three-run baseline was 4,662 / 4,716 / 4,601 ms (median 4,662 ms). These observations do not show a material slowdown; they are test-corpus measurements, not a latency guarantee.

Protocol version advances from 3.63.0 to 3.64.0 and the runtime advances from `RECORD_BASED_PLANNER_0.15.5_KOTLIN_1` to `RECORD_BASED_PLANNER_0.15.6_KOTLIN_1`. App stays 0.5.1.5 and Room stays 38; no persistence schema changed.

`lastAuditedCommit` is implementation/test commit `25c7b1dc6453166d58fd927d8c6c8f9e0c31720d`. The final census SHA-256 is `AD2A0DED6CDA1D298F856092298150E2B7C42C03B6C02B55E7B0D9681818BC6F`. Hosted run `37720884407` on main passed 2,353 tests (0 failures, 0 errors, 4 skips), protocol validation, Community/Cloud contracts, whitespace, coverage upload, APK assembly, signer verification, and artifact upload. Its APK artifact ID is `11526411968` (ZIP SHA-256 `AFD163498C1D62408CE7A7230EE66B30819E8E1CF5FC2454114F5E074685E22B`); the contained APK is 68,890,335 bytes with SHA-256 `3F620C8680ECD568638E720AF6D6CFCB52325AF0474BC9311E2AB070A3953D12`. Coverage artifact ID `11525403561` contains `stimulus-production-coverage.txt`, SHA-256 `F1819EBBAD421D2652E122547A7C68ACD31BBE7CC54DB4122C7100C3964660D7`.
