# Cloud Backup Phase 1-A — local session identity

Baseline: `a56eaad3ad9a1a7afc33cb3cbfff9f963202c6bc`.

The approved `DATA-CLOUD-BACKUP` 1.0.0 document is installed verbatim, with status
DRAFT / SPECIFICATION_ONLY. Cloud runtime remains unimplemented. Local/manual
backup and restore remain governed by DATA-BACKUP-RESTORE.

## Storage and compatibility

- Room 31 → 32 adds non-null `WorkoutEntry.sessionStableKey`.
- Migration visits distinct legacy dates in sorted order, creates one random UUID
  per date, and updates only the new column. IDs, source IDs, sets, confirmed
  flags, program state and progression state are retained. No time-gap inference.
- The SQL empty-string default permits adding the column to existing tables;
  migration replaces every legacy value. Normal DAO insert/update reject blanks.
- Canonical backup format 13 → 14; restore CSV schema 12 → 13. Program backup
  schema remains 2. `session_stable_key` accompanies every exported set row.
- New files preserve exact session keys. Missing/blank keys and contradictory
  keys within one entry fail preflight. Session keys participate in current-state
  fingerprints and new-format content conflict checks.
- Supported older files still parse. On restore, newly inserted entries missing
  session identity get one fresh UUID per source date, scoped to that restore
  transaction. Existing source-ID and legacy duplicate skip behavior is retained;
  an already-present entry is not rewritten simply to assign a session.
- An absent legacy session key alone is not a content conflict. Explicit session
  keys in new files are compared. Re-import does not reassign existing identities.
- No Cloud backup/parent IDs or new lineage metadata enter the user-data payload.
- This is an unreleased local foundation change; app version and release tags are
  unchanged. Deploy only with both the Room migration and format-14 reader/writer.

## Creation and mutation audit

| Path | Session rule |
|---|---|
| Manual record creation (`RecordMutationService`) | Reuse the stored session of the last displayed entry in the selected date context; an empty context starts a fresh UUID. Existing sessions are never regrouped by date. |
| Program application (`ProgramPlanService`) | A fresh UUID per scheduled day per application; all items in that day share it. Reapplying/appending creates new sessions, even on occupied dates. |
| Date move (`CalendarRecordService`) | Preserve session key and `backupSourceId`, including moves onto an occupied date. |
| Date/range copy | New UUID per source session, shared by its copies throughout that operation; fresh entry source IDs. Distinct sessions on a date remain distinct. |
| Future plan push | Preserve session keys. Fully moved entries retain source IDs. A mixed confirmed/planned entry still splits: confirmed original retains source ID; planned remainder gets a new entry source ID, retaining the session key. This existing split can span dates. |
| Entry edit | Reloaded persisted session key wins over caller-supplied identity. |
| Set edits, confirmation, reorder, completion timestamps | Update only the original fields; session and entry identity stay intact. |
| Source-ID backfill | Copies the original entity and changes only `backupSourceId`. |
| Restore/import | Exact key when supplied; legacy grouping described above. Canonicalizer copies retain the new field. Daily-timeseries import creates no entries. |
| OFI/day and tissue/week projections | Read-only synthetic rows, never persisted. Explicit synthetic session tokens keep projection output deterministic and avoid minting portable identities during scoring. |

No stable IDs were added to WorkoutSet or TrainingProgramItem, and
ProgramProgressionItem.logicalItemId remains progression-only.

## Specification and governance notes

- The approved Cloud document uses a custom 20-section structure and backtick-wrapped
  metadata. The validator checks those exact headings for this protocol and
  normalizes backtick-wrapped metadata; all other protocols retain their template.
- The approved document is byte-for-byte unchanged, including its baseline audit
  and future-work list. Its session-foundation items are now implemented by this
  phase, while its Cloud runtime claim remains SPECIFICATION_ONLY.
- The local backup protocol receives only the requested cross-reference. Historical
  version descriptions are not rewritten; current format changes are recorded here.
- No Supabase/R2/auth/networking/workers, Cloud state, merge engine, recovery redesign,
  account archive or Cloud UI are implemented.

## Regression verification

`WorkoutSessionIdentityTest` exercises real Room migration from the exported 31
schema, Room 32 schema validation and reopen, manual edits, copy/move/range/push,
partial plan splits, independent program applications, exact current-format
round trips, formats 11/12/13 compatibility and malformed session rejection.
Existing backup and progression version assertions follow the new format versions.

Final targeted execution: 200 tests across 31 suites; 197 passed, 3 optional
real-data tests skipped, 0 failures/errors. All 11 new session tests passed.
The run included backup, progression, daily-status, record mutation, calendar,
source identity and metadata contract tests.

An earlier broad data-package run executed 672 tests (4 skipped) and found two
failures: a version assertion corrected here and an unrelated Windows CRLF-sensitive
`PlannerIsolationArchitectureTest` import assertion. The latter test and its source
inputs are unchanged from the baseline; its regex retains the carriage return.
No unrelated fix was made. The final targeted run passes the corrected version test.

Protocol validation passes for 8 families / 35 protocols. Whitespace checks pass
for implementation changes; the staged approved Cloud document retains five original
Markdown two-space line breaks flagged by the default whitespace checker. Room migration is exercised using Robolectric with the exported Room 31
schema, followed by Room 32 validation and reopen. No connected device or configured
AVD was available for device instrumentation. Historical files tested here are
representative fixtures, not private real-user backups. The exact commands and
completion SHA are recorded in the task completion report.

## Legacy upgrade and canonical serializer reuse

A parsed old manual file is never itself a future Cloud CURRENT payload. The path is:

`old supported file → restore into Room 32 → normalize identities → current canonical export → clean Room restore`

The upgrade regression fixtures cover format 11/schema 10 without entry source IDs,
format 12/schema 11 with entry source IDs, and format 13/schema 12 with the complete
program execution graph. Session identity is absent from all three fixtures.
Format 14/schema 13 is emitted after restore regardless of the input version.
Legacy source IDs are retained when present; missing IDs use the existing target
installation lineage + workout-entry UUID policy. This is local entry provenance,
not trusted Cloud ancestry. No backup_id/parent_backup_id is introduced.

`BackupExportService.buildCanonicalBackup` owns the existing data loading, identity
normalization, preflight, all authoritative snapshot sections, manifest/hash/counts,
and current-format serialization. It validates the resulting content before returning
`CanonicalBackupContent` (CSV and UTF-8 bytes). Manual URI export calls this same
method, writes its output, and retains file read-back validation and reporting.
`TrainingRepository.canonicalRecordsBackup` exposes it internally without a URI and
runs the generation inside one Room transaction. The manual writer retains its prior
reporting/transaction lifecycle; the new transaction boundary applies to the URI-free
snapshot entry point.

Future Cloud code must reuse this path, not implement a second canonical serializer.
The returned CSV UTF-8 bytes are the current canonical payload. The approved Cloud
specification's `.json.gz` object naming remains unchanged in this phase; later Cloud
transport work must explicitly reconcile that naming/envelope with the CSV payload,
without silently relabeling CSV as JSON or duplicating user-data serialization.
No compression, upload, Cloud lineage or network service is added here.

The graph-bearing upgrade regression also covers exact program items/sets, tombstones,
exercise metadata snapshots/overrides, portable app_meta, profile, daily/check-in,
smash-speed references, current workout identities and all six execution row types.
Manual export and the URI-free API are compared for identical canonical body content.

### Timestamp repair found by the complete upgrade test

The round-trip test exposed an existing restore-order issue: importing daily metrics
could first synthesize a check-in, whose fresh creation timestamp then hid the
explicit check-in row's backed-up `createdAt`. Restore now preserves that backed-up
timestamp for dates absent before the restore. Dates already present in the target
retain their existing creation timestamp, and ordinary check-in edits retain their
prior behavior. This narrow correction is required to prevent authoritative
check-in metadata loss on the requested clean-database upgrade path.

Derived strength state is still rebuilt from raw records by the existing policy;
its rebuild timestamps and event UUIDs are not treated as portable user-data identity.
