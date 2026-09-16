# Phase 1-B: local Cloud revision and recovery audit

Baseline: `f8e47838df238c54df62412fcce6ecc902418143`.
DATA-CLOUD-BACKUP remains DRAFT and is now PARTIALLY_IMPLEMENTED. Its 1.1.0 product
contract records the local recovery subset while Cloud transport remains deferred.
No Cloud transport, auth, Cloud worker, settings UI, archive/pending snapshots, merge
or network requests are implemented. The local recovery subset is implemented.

## State and migration

Room 32 → 33 adds only `cloud_backup_state`. Canonical backup stays format 14 /
restore schema 13 (program schema 2). No existing entity columns or identities change.
The singleton DAO creates row 1 lazily/at repository initialization with a random UUID,
never hardware identity. Normal reopen preserves it. Account/base/success IDs start null,
Cloud is disabled, retry state is empty. A fresh installation starts revision 0/pending
false. Migration conservatively gives an existing database revision 1/pending true,
without manufacturing Cloud ancestry or a last-edit timestamp. All existing tables
are untouched. Seeding is infrastructure and does not count as a user operation.

## Transaction mechanism

`withCloudRevision` extends existing Room transactions; it does not queue or replay
mutations and does not replace domain services. Explicit scopes compare persisted
before/after domain values within the same transaction. A changed logical operation
updates pending/revision/time once. Unchanged values and `updatedAt`-only bookkeeping
do not count. `app_meta` uses the existing BackupAppMetaPolicy portability allowlist.
Nested calls on the same database share a coroutine context and coalesce to one count.
Failures roll back both domain and state, including a failure of the revision write.
Ordinary edits never alter the base backup ID. Retry updates are infrastructure only.

Record/set/date operations compare affected dates and their sets (date parameters are
chunked below SQLite's bind limit). Bulk program/exercise operations compare their
listed domain tables; this adds reads proportional to domain size. This is a local
change detector, not another canonical serializer. Future large-database performance
work may narrow the explicit scopes without moving state updates outside transactions.
Only trusted internal table names are used. Raw DAOs remain untracked primitives:
maintenance, migration and restore must not silently become user operations.

Record tracking runs inside the existing strength coordinator's raw transaction.
Derived strength processing retains its existing post-commit boundary; failure of a
later analysis refresh cannot erase either the raw edit or its Cloud pending state.

## Production mutation-path classification

| Path | Classification and transaction owner |
|---|---|
| RecordMutationService add/edit/delete entry, add/delete set, confirmation and field-intent set update | Logical revision inside existing record transaction; no-op set update stays zero; completion/order/progression prescription changes remain part of that one operation. |
| CalendarRecordService move/copy/date or range delete/range copy/future plan push | One logical revision inside existing date mutation transaction, even for many entries/sets. Empty/no-op operations stay zero. Session/source semantics unchanged. |
| TrainingRepository reorderWorkoutEntries | Logical revision when persisted entry order changes because that order is backed up; the local manual-order marker alone is excluded. |
| TrainingRepository createProgram/saveLegacyAutoProgram/saveGeneratedProgram/deleteProgram/addExerciseToProgram/updateProgramItem/deleteProgramItem/createProgramFromRecordRange | One transaction covering ProgramPlanService and portable personalized decision bookkeeping. Child sets, tombstones and progression authoring belong to the same operation. |
| TrainingRepository applyProgramToDates | One logical revision covering all generated workout rows and execution links/prescriptions. |
| TrainingRepository configureProgression/resolveProgression | One logical revision including persisted user decisions and applied set weights. |
| TrainingRepository saveExerciseEditor/resetExerciseMetadataOverride/setExerciseActive/deleteExerciseIfUnused | One logical revision covering editable exercise state, metadata/override and role relations. Rejected/unchanged operations do not count. |
| DailyStatusService saveDailyMetric/upsertDailyCheckIn/deleteDailyCheckIn | Logical revision at public user-operation boundary; metric/check-in dual writes count once. Internal restore helpers remain untracked. |
| TrainingRepository saveInitialUserProfile/addSmashSpeed/deleteSmashSpeed | One logical revision per meaningful operation. |
| PersonalizedProgramPlanningService persistAnswers | Production-injected transaction callback covers portable preference and weekly annotation writes together; computation runs outside it. Saved decision/edit annotations are already inside program-save transactions. |
| Manual backup import/confirmed selectable restore | Implemented for the local recovery phase: preflight, durable `BEFORE_MANUAL_IMPORT` snapshot, then import and external-branch reset in one Room transaction. |
| Daily-timeseries external import | Same protected-import boundary; internal daily helper deliberately does not increment separately for each row. |
| Seed/identity repair/metadata reconciliation, source-ID backfill during export | Infrastructure/migration, no user revision. |
| Derived strength/progression refresh, suggestion recomputation, rebuild/cache/analysis events | Derived from already-persisted raw edits or startup reconstruction; no independent user revision. User progression configuration/resolution is tracked separately above. |
| Theme/locale/navigation/scroll/transient UI, diagnostic reports, retry bookkeeping, nonportable app_meta | Infrastructure/non-portable, no revision. |

Production UI accesses domain mutations through TrainingRepository (records/calendar
also enforce tracking at their service boundary). Program/exercise/progression lower
services remain reusable untracked internals for seeding/import/maintenance; new user
entry points must use the existing repository transaction wrapper. No blanket DAO
write interception is installed.

## Local Recovery and protected boundaries

`LocalRecoveryStore` writes immutable UUID generation directories containing the
canonical CSV gzip (`local_recovery_previous.csv.gz`) and a trusted JSON sidecar. It
hashes the exact compressed bytes, gunzips and runs the existing parser, canonicalizer,
and restore planner before a generation is accepted. The active generation ID is a
portable-excluded `app_meta` pointer, so pointer promotion and trusted Room restore
commit in one Room transaction. Obsolete UUID generations are cleaned only after a
successful promotion; a corrupt active generation is retained.

`LocalRecoveryService` creates a durable snapshot before manual imports and the audited
date deletion, date-range deletion, date/copy range moves, future-plan push, program
application and program deletion operations. Failed
snapshot creation fails closed. Trusted recovery restore performs full user-data
replacement, preserves the current installation ID, restores sidecar account/base/
revision/pending state, preserves the current enablement setting, clears transient
retry/success state, and rotates the previous current database into recovery. Recovery
does not use the external-import branch reset.
The five-minute stabilization boundary is scheduled with one local-only unique
WorkManager job. Repeated edits replace it; the worker calls `refreshStable` with a
revision/time token and exits without writing if the token is stale. No network
constraint or Cloud worker is involved.

## Manual import boundary

`startExternalCloudBranchInTransaction` requires an active Room transaction and sets
base=null, revision=1, pending=true, and last change time, preserving installation ID.
It is called by both canonical and daily-timeseries external import callbacks only
after preflight and a `BEFORE_MANUAL_IMPORT` snapshot, in the same Room transaction as
the import. This phase does not enable Cloud sync.
No arbitrary file is treated as trusted lineage. Current backups contain no Cloud state.
Future Cloud restore/acknowledgement/account switching is outside this phase; there is
no API that invents an acknowledged Cloud backup ID.

## Verification

CloudBackupStateTest covers singleton creation/reopen/concurrency, per-operation and
nested counts, no-ops, unchanged base, both rollback directions, retry/nonportable
exclusions, record/program/calendar/daily/exercise/profile/smash production entry points,
large date scopes, the deferred external-import API, payload exclusion, and migration.
Migration constructs the actual exported Room 32 schema, preserves populated workout/
set/exercise/program/app_meta data, compares every original table before/after, and lets
Room validate 33. Existing Room31 session migration now chains through 33.
September 13/12 → 14/13 tests and session identity tests remain in the regression run.
Final focused run: 258 tests across 38 suites, 255 passed, 3 optional real-data tests
skipped, zero failures/errors. All 13 Cloud-state tests and all 12 session tests passed.
Supplementary ProgramPlanServiceMappingTest and StrengthPosteriorEventIntegrationTest:
17 passed, zero failures/errors/skips. Debug Kotlin compilation passed. Protocol
validator passed for 8 families / 35 protocols; whitespace checks passed. No attached
Android device or configured AVD was available, so instrumentation was not run.
Initial regression failures were resolved: the date-scope bind limit was fixed by
chunking, the Windows migration fixture uses a short file name for SQLite WAL, and
the no-op exercise assertion now exercises an actually unchanged active state instead
of the existing archive operation that intentionally writes a new archivedAt value.
