# Phase 1-B1: local Cloud revision audit

Baseline: `890d08c89bd71c9ffc0db7a9e8c19c7b820d62ce`.
DATA-CLOUD-BACKUP remains DRAFT and is now PARTIALLY_IMPLEMENTED. Its 1.0.1 product
contract is unchanged: this update synchronizes implementation status and anchors.
No Cloud transport, auth, workers, settings UI, recovery/archive/pending snapshots,
merge or network requests are implemented.

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
| Manual backup import/confirmed selectable restore | Deferred to next recovery phase; existing import transaction has explicit TODO and tested transaction-only branch reset API. |
| Daily-timeseries external import | Same deferred protected-import boundary; internal daily helper deliberately does not increment separately for each row. |
| Seed/identity repair/metadata reconciliation, source-ID backfill during export | Infrastructure/migration, no user revision. |
| Derived strength/progression refresh, suggestion recomputation, rebuild/cache/analysis events | Derived from already-persisted raw edits or startup reconstruction; no independent user revision. User progression configuration/resolution is tracked separately above. |
| Theme/locale/navigation/scroll/transient UI, diagnostic reports, retry bookkeeping, nonportable app_meta | Infrastructure/non-portable, no revision. |

Production UI accesses domain mutations through TrainingRepository (records/calendar
also enforce tracking at their service boundary). Program/exercise/progression lower
services remain reusable untracked internals for seeding/import/maintenance; new user
entry points must use the existing repository transaction wrapper. No blanket DAO
write interception is installed.

## Manual import boundary and known gap

`startExternalCloudBranchInTransaction` requires an active Room transaction and sets
base=null, revision=1, pending=true, and last change time, preserving installation ID.
It must be called by BackupRestoreImportService / DailyTimeseriesImportService only
when the later phase supplies the protected recovery lifecycle. Those existing
importers are intentionally unchanged apart from TODOs: their writes do not currently
update Cloud revision/lineage. This phase therefore must not enable Cloud sync.
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
