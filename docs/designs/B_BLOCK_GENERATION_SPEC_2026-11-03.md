# B-block generation: agreed rules and rollout audit

Status: staged implementation, not feature-complete. This file is a design contract and should not be confused with a live UI feature.

## User decisions

- No custom block layout: one four-week block, auto goal, no between-block recovery.
- Custom layout: 1..4 blocks (initial cap), each 1..6 weeks (initial cap), selectable goal (auto/strength/hypertrophy/sport support/balanced/peak-evaluation), relation and 0..7 recovery days only between blocks.
- Exercise identities stay stable within a block; each subsequent block independently reselects using prior *planned* exposure and limited shortfall bonuses. No future observed results are fabricated.
- Select an independent volume pattern for every block. One week only steady. Two weeks steady/progressive; wave and accumulate-recover start at three weeks.
- Wave: 3 = low/high/medium, 4 = low/high/low/high, 5 = low/medium/high/low/high, 6 = low/medium/high/low/medium/high.
- Accumulate/recover: increase through the penultimate week then reduce in the final week; separate between-block recovery is not automatically removed.
- Progression selection by goal: strength load > sets > reps; hypertrophy reps > load > sets; sport support movement quality > reps/sets > load; balanced per exercise; peak-evaluation task-specific.
- Decrease priority: sets > reps > load. Set changes are one set *per main exercise*; repetition changes one per set; barbell load step = 2 x smallest user-profile plate. Main classification reuses metadata.
- User supplies initial load per exercise; no invented initial kilograms. All future weeks are planned at creation; no automatic revision after execution. RPE/recovery cautions are notes for user review.
- If the record is insufficient to support an attempted prescription, present a conservative proposal for explicit acceptance rather than silently claiming reliable calibration.
- Periodization interface: horizontal cards with small line graphs, vertical axis "볼륨", horizontal axis "주차"; amplitude auto by default with limited advanced overrides.

## Implementation gates

1. Isolated block specification and deterministic pattern/step tests. Safe to add without changing the production B12 path.
2. Live B4 route, baseline prescription, preview/edit/persistence and actual CI regression verification.
3. Custom multi-block UX, day-resolution recovery scheduling, block-level exercise reselection and shortfall-aware ranking.
4. Manual start load editing with profile plate unit and future-week propagation; record insufficiency approval and RPE notes.
5. End-to-end audit of UI settings versus saved generated items; previous B12 plans must remain editable/readable.

Legacy twelve-week B behavior remains intact until stage 2 is validated and intentionally cut over. No stage may claim complete adherence based on metadata labels alone.
