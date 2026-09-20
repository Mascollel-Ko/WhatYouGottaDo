# Capacity-bound program result

This is the exercise program produced by the capacity-demand tests and the final private-backup audit for implementation commit `129fd8921c9e756c6a88d3ba7e7eaaefa60aceb7`. The backup itself is not stored in the repository.

The experimental program keeps each material owner within its authorized/requested maximum. The frozen CONTROL result is included for parity review; the experimental result is the program produced after bounded material allocation and ordinary continuity restoration.

## Synthetic capacity results

| Case | Capacity | Regional A | Ordinary B | Ordinary C | Total | Unused |
|---|---:|---:|---:|---:|---:|---:|
| A | 19 | 5 / 5 | 6 / 6 | 8 / 8 | 19 | 0 |
| B | 19 | 5 / 5 | 4 / 4 | 4 / 4 | 13 | 6 |
| C | 10 | 5 / 5 | 4 / 4 | 4 / 1 | 10 | 0 |
| Deferred candidate | 19 | 5 / 5 | 4 / 4 | 10 / 9 | 19 | 0 |
| Exhausted material demand | 40 | 5 / 5 | 4 / 4 | 4 / 4 | 16 | 24 |

The deferred case funds C from its existing compatible prescription after the atomic first pass. It does not enlarge B. The exhausted case leaves capacity unused after every material maximum is reached.

## Final experimental program, week 1

The following rows are the final experimental program from the same private-backup audit. `sets` contains the complete prescribed set shape; `rest` is in seconds. `source` is the existing prescription authority recorded by the planner.

| Day | Exercise stable key | Role | Sets | Rest | Source |
|---:|---|---|---:|---:|---|
| 1 | `barbell_bench_press` | `COVERAGE_HORIZONTAL_PUSH` | 2 × 1 rep @ 95 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 1 | `ex_a61f1e96` | `STYLE_HEAVY_HORIZONTAL_PUSH` | 3 × 7 reps @ 56 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 1 | `barbell_romanian_deadlift` | `STYLE_HEAVY_POSTERIOR_CHAIN` | 2 × 8 reps @ 108 kg | 120 | `CANONICAL_POSTERIOR_REDUCE` |
| 2 | `cable_pallof_press` | `PERFORMANCE_CONTINUITY` | 2 × 10 reps @ 20 kg; 1 × 10 reps @ 25 kg | 45 | `RECENT_PERSONAL_EXECUTION_HOLD` |
| 2 | `barbell_deadlift` | `STYLE_HEAVY_POSTERIOR_CHAIN` | 2 × 3 reps @ 190 kg | 180 | `CANONICAL_POSTERIOR_HOLD` |
| 2 | `ex_bb4b4276` | `STYLE_HEAVY_VERTICAL_PUSH` | 2 × 10 reps @ 16 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 2 | `ex_5ca7133f` | `STYLE_HEAVY_CALVES` | 3 × 8 reps @ 10 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 4 | `ex_4255e429` | `PERFORMANCE_CONTINUITY` | 2 × 6 reps / 40 s | 60 | `RECENT_PERSONAL_EXECUTION_HOLD` |
| 4 | `ex_e41f4c2b` | `STYLE_MEDIUM_VERTICAL_PULL` | 3 × 6 reps @ 9 kg | 180 | `CANONICAL_POSTERIOR_REDUCE` |
| 4 | `ex_d5bdffe1` | `STYLE_HEAVY_CORE_DIRECT` | 5 × 10 reps | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 6 | `ex_5ca7133f` | `STYLE_HEAVY_CALVES` | 3 × 8 reps @ 10 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |
| 7 | `barbell_back_squat` | `STYLE_HEAVY_LOWER_KNEE` | 3 × 3 reps @ 145 kg | 180 | `CANONICAL_POSTERIOR_HOLD` |
| 7 | `half_kneeling_single_arm_kettlebell_press` | `STYLE_HEAVY_VERTICAL_PUSH` | 2 × 12 reps @ 8 kg | 120 | `CANONICAL_POSTERIOR_HOLD` |

The experimental result contains 30 resistance sets, 2 structured badminton bouts and 3 athletic-performance bouts. Its bounded material owner is `barbell_bench_press`: requested/max/funded/materialized `2/2/2/2`. Total legitimate demand is 40, funded/materialized demand is 35, total unfunded legitimate demand is 5, bounded-material remainder is 0, unused capacity is 0 and capacity overrun is 0.

## CONTROL parity

CONTROL remains the frozen production result. Its final fingerprint is:

`805c90244c1bac27218aad2f7003566c80b790d2b21417716b973bfdc2620c8f`

The CONTROL rows and resolved request match the start audit exactly. The experimental program is deliberately recorded separately so its bounded material result cannot be mistaken for a CONTROL change.

## Verification

- Focused required verification: 112 tests, 0 failures, 0 errors.
- Full local `testDebugUnitTest`: 1,899 tests, 0 failures, 0 errors, 4 unchanged private-artifact skips.
- `assembleDebug`: passed.
- Protocol documentation validator: passed.
- Android Debug Build #415 for commit `129fd892`: passed.
