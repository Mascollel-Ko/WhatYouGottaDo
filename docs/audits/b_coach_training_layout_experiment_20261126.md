# B-Coach training layout preference experiment (2026-11-26)

Base: 19df6b318dc8815bedfd44c39cf9cf7dd1198bda parented by the successful strength-anchor experiment. Experimental branch only.

- Three settings: AUTO_BALANCED (existing recommendation, default), FULL_BODY_DISTRIBUTED, BODY_PART_FOCUSED.
- Keep existing eight objective weights and the strength anchor / front-squat preference. Choose the ordinary recommendation before applying layout preference.
- Use effective typed muscle relations, PRIMARY 1.0, SECONDARY 0.5, exclude STABILIZER. Missing keys are reported in input audit; do not guess user's custom exercise metadata.
- HHI per calendar week/block/muscle, normalized using min(occupied training days, positive exercise occurrences), weighted by role-weighted sets. Exclude single exposures and completely fixed exposures; no penalty for single-day groups. This is placement concentration, not predicted physiological stimulus.
- Layout-only search offers single moves and swaps between already occupied days of the same week and block, no requirement that receiving day has overlap. Repeated key/day patterns applied where compatible. No creation/removal of exercises, sets, changes to reps, load, rest, localId, progression sessions, or week schedule. Main exercise identity can move if otherwise unlocked. User-fixed loads/anchors/progression bindings remain fixed.
- Compare worst existing modeled loss + 0.10 * layout preference loss; AUTO has zero extra loss. New candidates evaluated on existing 8 axes using exact original per-set load resolution and unchanged approval/persistence fingerprints. No new failed constraints. Existing same-family limit retained.
- Budget: up to 3 improving rounds, top 12 preference-improving candidates per round; keep original if no combined improvement. Not exhaustive; no guarantee of zero same-day duplicates. No separate three-way rotation in V1; repeated moves/swaps can realize some multi-step permutations.
- Audits include before/after concentration, comparable volume, unmapped keys, number of layout candidates evaluated, roster fingerprint and preference. A draft's preference is restored from its decision audit if available.
- Tests added for one-sided overlap, primary/secondary versus stabilizer, synthetic high pullup/AB slide fixture (not assumed real IDs), opposite preferences, one day, missing metadata, time cap, explicit locks, repeated patterns, input fingerprint and approval.
- Required before merge: hosted CI, local Gradle if available, real user history A/B for all modes, physical UI width and draft restart tests. Existing equipment metadata discrepancy remains outside scope.
