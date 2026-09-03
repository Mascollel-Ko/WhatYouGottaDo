# Record-based planner implementation note

Baseline: `f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9`.

The legacy automatic path remains `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder`; its focused tests and parity matrix are the hard regression boundary.

The implemented path reads only confirmed sets on or before a cutoff, resolves exercises by canonical `stableKey`, infers behavior separately from explicit intent, detects coverage gaps before selecting from a small reviewed authority, chooses a 2-6 week re-evaluation horizon, detects/preserves evidenced strength style, validates the projected plan, and adapts it to `GeneratedProgramSkeleton` for the existing editor/save/apply flow. Planning preferences and decision provenance use explicitly portable `app_meta` rows in CSV backup/restore, avoiding a Room schema change while excluding infrastructure metadata.

Reference oracle: offline `wgtd_planner_reference_v0.8` (`REFERENCE_PLANNER_0.8.0`). Production Kotlin will use the repository's richer canonical metadata and badminton V2 authority instead of copying display-name inference or Python runtime code.
