# Frozen Legacy Auto Skeleton audit fixtures

Frozen source: `f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9`.
Starting app source for this isolation: `a53f419ed723945d30016419453afb292ac3fe44`.

The 360-case golden and exercise input snapshot in
`app/src/test/resources/program-authority/legacy-frozen/` were exported by
`ExportFrozenLegacyGoldenTest.kt` running against the **historical implementation**,
not against the separated production implementation.

For a separately reviewed historical re-audit:

1. Create a detached worktree at the full frozen SHA, without restoring the current application.
2. Place the exporter test in that worktree's `app/src/test/java/com/training/trackplanner/data/`.
3. Run `:app:testDebugUnitTest --tests '*ExportFrozenLegacyGoldenTest'` there.
4. Inspect `build/frozen-legacy-golden/`. Compare it to the committed fixture; do not replace the
   committed fixture in response to a failing current parity test.

The historical run checked all generated weeks: active weekdays equal materialized item weekdays.
The fingerprint serializes every non-static field of the entire historical result tree, including
normalized request, all week-plan fields, explicit schedules, all item fields and set rows,
notices, warnings and template markers. Type names are excluded solely to permit mechanical renames.
Unordered sets/maps are canonicalized; list order is preserved. The exercise TSV is the exact
active, non-history-only historical catalogue projection consumed by the old builder.

`python tools/legacy_auto/verify_frozen_sources.py` is a separate read-only source audit.
It compares ten files to the frozen Git blobs. The generation service's only non-rename adaptation
is to call the same builder directly after loading ExerciseDao, dropping the compatibility wrapper
whose history/date/fatigue arguments were unused. The current generic draft editor and day editor
remain separate from the mechanically copied Legacy editor helpers.

Additional Record-Based regression evidence:
`app/src/test/resources/program-authority/record_based_a53f419_29.csv` was exported in another
detached worktree at the starting SHA using its existing 29-persona test and the field-tree
serializer in `SeparationFingerprint.kt`. Only random decision ID and wall-clock generation time
are normalized. Request, final output, progression intent and all decision/state/gap/budget
provenance remain in the fingerprint. Column 3 records pre-existing active-but-empty days.
`22_sparse_two_week_horizon` retains `3:1|3:2|3:4|3:6`; this task does not repair that algorithm.

Ponytail is retired and forbidden. No-History V2 is future work, not this implementation.
