# Codex Worklog

## Tissue Load Foundation v1 - Commit 1

- Baseline: Phase A final commit `c5aaaa0a01b289d50e5d277ef4d0fcb7a0ea6a1f`, fast-forwarded to `origin/main`; the tissue commits were then reapplied on that base.
- Cause: the scalar joint/tendon/impact fatigue axis discards tissue class, anatomical structure, and load dimension.
- Changes: added a 61-structure catalog, tissue-specific dimensions and evaluation states, long-form profile/rubric schemas, a 14,579-row explicit scope manifest for all 239 canonical stable keys, deterministic audit hashes, typed parsers, exact-key repository lookup, offline validators, and focused tests.
- Result: metadata foundation stage is valid but remains `FOUNDATION_PARTIAL`; all scopes are `NOT_YET_EVALUATED`, all profile files are empty, and no production eligibility or human approval was created.
- Network: permission approved; NCBI parsed pass; Crossref 404 after bounded encoded/literal attempts; capability `PARTIAL_SOURCE_VERIFICATION_AVAILABLE`.
- Preserved: existing six fatigue axes, OFI, fatigue wording, readiness, backup, ProgramBuilder, Bayesian, and time-series behavior.
- Tests: `:app:testDebugUnitTest --tests "*TissueMetadataFoundationTest*"` and `:app:compileDebugKotlin` passed.

## Tissue Load Foundation v1 - Commit 2

- Cause: source existence, bibliographic identity, claim support, blind review, and human approval are distinct gates and must not collapse into one citation field.
- Changes: added evidence registry, draft/blind/final claim ledgers, source-verification artifact, empty human batch-approval schema, typed evidence parsers, fail-closed validators, and bounded NCBI/Crossref tooling.
- Result: one preflight identity is recorded as `UNVERIFIED`; verified-source count remains zero, and no claim, blind review, final claim, production row, or human approval exists.
- Network capability remains `PARTIAL_SOURCE_VERIFICATION_AVAILABLE`.
- Tests: metadata/evidence focused tests and `:app:compileDebugKotlin` passed; `verify_tissue_sources.ps1` returned NCBI pass, Crossref 404, and `PARTIAL_SOURCE_VERIFICATION_AVAILABLE`.

## Tissue Load Foundation v1 - Commit 3

- Cause: the legacy scalar/tag layer, current record fields, and exercise laterality metadata could otherwise be mistaken for production tissue bands, event counts, or performed-side observations.
- Changes: added one review-only migration decision for all 42 canonical legacy field/token identities; a 12-basis record capability audit; explicit missing-input, side, and calculation states; an empty evidence-gated modifier schema; typed parsers, validators, deterministic generators, and focused tests.
- Result: legacy tags can seed later review but cannot assign bands; unavailable event counts stay `MISSING_RECORD_INPUT`; performed side stays `UNSIDED` and unresolved; broad tags are not equally split; no modifier factor or interaction was invented.
- Preserved: existing bodyweight and duration-hold authorities, entities, DAO/schema, backup, six fatigue axes, OFI, readiness, ProgramBuilder, Bayesian, and time-series behavior.
- Status: `FOUNDATION_PARTIAL`; recovery and shadow exposure plumbing remain for Commit 4.
- Tests: `:app:testDebugUnitTest --tests "*TissueMetadataFoundationTest*" --tests "*TissueEvidenceValidatorTest*" --tests "*TissueRecordContractsTest*" :app:compileDebugKotlin` passed.

## Tissue Load Foundation v1 - Commit 4

- Cause: the metadata and record contracts needed deterministic calculation plumbing before later evidence batches can be tested, but connecting unreviewed rows to OFI/readiness would be unsafe.
- Changes: added confirmed-record dose resolution, fail-closed modifier composition, exact-key independent tissue exposure, side-preserving diagnostics, calendar 24-hour/72-hour/7-day windows, hierarchical snapshots, an empty recovery schema, and a standalone shadow pipeline.
- Scientific boundary: no numeric production profile, modifier, decay kernel, full backfill, or human approval was created. Explicit non-production fixture weights are opt-in and rejected by the default evidence gate.
- Result: the four-commit deterministic foundation is `FOUNDATION_COMPLETE`; production eligibility remains false and later rubric research, blind review, human approval, and canonical backfill remain separate work.
- Preserved: existing six fatigue axes, OFI, labels/warnings, readiness, ProgramBuilder, bodyweight coefficients, hold behavior, Bayesian/time-series code, Room schema, backup, and UI.
- Tests: focused shadow/tissue suite plus `compileDebugKotlin` passed; full `:app:testDebugUnitTest :app:assembleDebug` passed; deterministic asset generation and `git diff --check` passed.
- Source verification: NCBI PMID/title parsing passed, Crossref remained HTTP 404, capability remained `PARTIAL_SOURCE_VERIFICATION_AVAILABLE`, and the source stayed `UNVERIFIED`.
- Output hygiene: the six known generated `outputs/*` changes were left unstaged and are not part of this work.

## v0.4.0.8 Daily Service Extraction

- 작업 목표: `TrainingRepository`에 남아 있던 DailyMetric, DailyCheckIn, daily readiness input 책임 일부를 behavior-preserving service로 분리.
- 원인: v0.4.0.7까지 record mutation, backup, performance trend summary는 분리되었지만 daily condition/readiness 입력 조립은 repository에 남아 있었다.
- 수정 내용:
  - `DailyStatusService` 추가.
  - `DailyReadinessInputService` 추가.
  - `TrainingRepository`의 DailyMetric/DailyCheckIn public API는 유지하고 내부 구현을 service로 위임.
  - canonical `sleepHours` 병합 로직을 service로 이동.
  - today readiness input 조립을 service로 이동.
- 수정 이유: Home readiness, Daily Check-in, projected fatigue는 사용자 체감 기능이라 로직 변경 없이 책임만 줄이는 것이 가장 안전하다.
- 수정 결과:
  - `TrainingRepository`가 daily status/readiness input 작업을 직접 수행하지 않고 service에 위임한다.
  - ViewModel/UI call site는 변경하지 않았다.
  - backup/export/import, metadata, record mutation, analysis, plan, UI 경로는 변경하지 않았다.
- 수정한 파일 목록:
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/DailyStatusService.kt`
  - `app/src/main/java/com/training/trackplanner/data/DailyReadinessInputService.kt`
- 새 service/class/file:
  - `DailyStatusService`
  - `DailyReadinessInputService`
- 실행한 테스트:
  - Focused daily/check-in/readiness/backup regression tests: passed.
- 커밋 해시:
  - `ca2c8bca6e5d586811f312019612723f48f3bec2` `refactor(repository): extract daily metric services`
- main push 여부: final release verification 후 push 예정.
- tag push 여부: `v0.4.0.8` final release verification 후 push 예정.
- 다음 작업 후보:
  - `homeTodaySummary` 계산 조립을 별도 service로 분리할지 검토.
  - `coachingSignalsSummary` 입력 조립도 추후 별도 service 후보.
- 주의할 점:
  - `sleepHours` canonical source는 계속 `DailyMetric.sleepHours`다.
  - DailyCheckIn score/null/default 의미는 바꾸지 않았다.
  - backup import/export 내부 DailyMetric/DailyCheckIn 처리 로직은 이번 작업에서 건드리지 않았다.

## v0.4.0.8 Final Status Correction

- 확인 일시: 2026-07-03
- main push 여부: 완료.
- tag push 여부: `v0.4.0.8` 완료.
- 기준: `main`, `origin/main`, `v0.4.0.8`이 모두 `5387eb6401fd112e8e2f8827be7417efbfeeea60`을 가리킴을 확인.
- 비고: 이전 worklog의 "push 예정/tag 예정" 문구는 최종 release 후 상태로 보정함.

## v0.4.0.9 Home Summary Service Extraction

- 작업 목표: `TrainingRepository`의 Home summary, coaching signals, today status 조립 책임 일부를 behavior-preserving service로 분리.
- 원인: v0.4.0.8까지 daily status/readiness input service는 분리되었지만 Home summary, projected fatigue 조립, coaching signals 입력 조립은 repository에 남아 있었다.
- 수정 내용:
  - `HomeSummaryService` 추가.
  - `CoachingSignalsSummaryService` 추가.
  - `TodayStatusSummaryService` 추가.
  - `TrainingRepository`의 public API는 유지하고 내부 구현을 service로 위임.
- 수정 이유: Home 상태와 코칭 요약은 사용자 체감 화면이라 로직 변경 없이 read-only 조립 책임만 줄이는 것이 가장 안전하다.
- 수정 결과:
  - `TrainingRepository`가 Home/coaching/today status 조립을 직접 수행하지 않고 service에 위임한다.
  - ViewModel/UI call site는 변경하지 않았다.
  - readiness, projected fatigue, coaching signal priority/fallback/text, DailyMetric/DailyCheckIn, record, metadata, backup, analysis, plan, UI 동작은 변경하지 않았다.
- 수정한 파일 목록:
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/HomeSummaryService.kt`
  - `app/src/main/java/com/training/trackplanner/data/CoachingSignalsSummaryService.kt`
  - `app/src/main/java/com/training/trackplanner/data/TodayStatusSummaryService.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.0.9_release_notes.md`
- 새 service/class/file:
  - `HomeSummaryService`
  - `CoachingSignalsSummaryService`
  - `TodayStatusSummaryService`
- 실행한 테스트:
  - Focused Home/CoachingSignals/TodayReadiness/DailyCheckIn/RecordCsvBackupRestore tests: passed.
  - Full `testDebugUnitTest` / `assembleDebug`: passed.
- 커밋 해시:
  - `0af5e383bb31238f553817a77554ef58270d99aa` `refactor(repository): extract home summary services`
- main push 여부: final release verification 후 push 예정.
- tag push 여부: `v0.4.0.9` final release verification 후 push 예정.
- 다음 작업 후보:
  - `fatigueAnalysisHistory` 또는 badminton transfer read-only summary 조립 분리 검토.
- 주의할 점:
  - Home summary text, warning, confidence, projected fatigue 의미는 변경하지 않았다.
  - coaching signals source/priority/fallback은 변경하지 않았다.
  - backup/import/export 내부 경로는 이번 작업에서 건드리지 않았다.

## v0.4.0.9 Baseline Verification

- Checked at: 2026-07-03 08:44 +09:00
- CLI result: `HEAD`, `origin/main`, and `v0.4.0.9` all point to `038a97918ac8bceb194670915d304b33a0f2d114`.
- `TrainingRepository.kt` line count: 1826.
- GitHub web UI appeared stale/inconsistent in some views, but the local Git baseline is consistent.
- Next work proceeds as `v0.4.1.0` Program planning service extraction.

## v0.4.1.0 Program Planning Service Extraction

- Baseline: CLI confirmed `HEAD`, `origin/main`, and `v0.4.0.9` all pointed to `038a97918ac8bceb194670915d304b33a0f2d114` before v0.4.1.0 work.
- Baseline repair: not needed.
- Work target: reduce the remaining `TrainingRepository` god object responsibility by extracting program planning / persistence / apply orchestration.
- Cause: after v0.4.0.6-v0.4.0.9 service extractions, program save/apply/delete/read orchestration still lived directly in `TrainingRepository`.
- Changes:
  - Added `ProgramPlanService`.
  - Delegated program observation, item observation, create/save/delete, item mutation, conflict summary, and apply-to-dates calls from `TrainingRepository`.
  - Moved program apply transaction logic, generated-program persistence mapping, program-item reindexing, program date mapping, and program prescription note wiring into the service.
  - Left `generateProgramSkeleton`, `ProgramSkeletonGenerator`, ProgramBuilder validation, plan UI, record mutation, metadata, backup, analysis, readiness, and home paths unchanged.
- Reason: program persistence/apply orchestration is a bounded repository responsibility that can be extracted without changing caller-facing APIs or generation behavior.
- Result:
  - `TrainingRepository.kt` line count changed from 1826 to 1652 before release docs.
  - `ProgramPlanService.kt` contains the moved orchestration logic.
  - TrainingRepository public API and ViewModel/UI call sites remain stable.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.0_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `ProgramPlanService`
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*Program*" --tests "*Plan*" --tests "*RecordCsvBackupRestoreTest" --tests "*RecordMutation*"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `84a2416` `docs(worklog): record v0.4.0.9 baseline verification`
  - `aaa88dc` `refactor(repository): extract program planning service`
- main push status: pending final verification.
- tag push status: `v0.4.1.0` pending final verification.
- Next work candidates:
  - Continue repository read-only summary extraction only if another bounded responsibility remains clear.
  - Avoid ProgramBuilder algorithm refactors in repository extraction releases.
- Cautions:
  - ProgramBuilder / ProgramBuilderValidation behavior was not changed.
  - DAO call order and transaction boundaries inside moved program persistence/apply code were preserved.
  - record/metadata/backup/analysis/readiness/home/UI paths were not modified.

## v0.4.1.0 Final Status Correction

- 확인 일시: 2026-07-03 11:01 +09:00
- 기준 commit: `23e88bf41f9731470de715aa0951c84d2bba8093`
- `origin/main`: `23e88bf41f9731470de715aa0951c84d2bba8093`
- `v0.4.1.0`: `23e88bf41f9731470de715aa0951c84d2bba8093`
- main push status: completed.
- tag push status: `v0.4.1.0` completed.
- 비고: 이전 worklog의 pending 문구는 release/tag push 전 기록이며, 이 항목으로 최종 상태를 보정함.

## v0.4.1.1 Smash Speed Service Extraction

- Checked at: 2026-07-03 11:32 +09:00
- Baseline: latest `origin/main` at `c17a6ee939dad316d161afe7425cc7abdbfa8166`; `v0.4.1.0` tag remains at `23e88bf41f9731470de715aa0951c84d2bba8093`.
- Work target: extract smash speed read/write responsibility from `TrainingRepository`.
- Cause: v0.4.1.0 repository audit identified smash speed read/write as the smallest low-risk remaining repository responsibility.
- Changes:
  - Added `SmashSpeedService`.
  - Delegated `observeSmashSpeedsForDate`, `addSmashSpeed`, and `deleteSmashSpeed` from `TrainingRepository`.
  - Added `SmashSpeedServiceTest` for add attempt-index behavior and delete behavior.
  - Bumped app version to `v0.4.1.1` / `401001`.
  - Added `docs/v0.4.1.1_release_notes.md`.
- Reason: smash speed is a small DAO-only path, safer than calendar copy/delete or restore/import extraction.
- Result:
  - `TrainingRepository.kt` line count changed from 1652 to 1647 before release docs.
  - Public repository APIs and ViewModel/UI call sites remain stable.
  - Lab metric, backup/export/import, record, metadata, analysis, plan, home, readiness, and UI paths were not changed.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/SmashSpeedService.kt`
  - `app/src/test/java/com/training/trackplanner/data/SmashSpeedServiceTest.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.1_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `SmashSpeedService`
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*SmashSpeed*" --tests "*AnalysisMetricRegistryTest" --tests "*RecordCsvBackupRestoreTest"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `b322637` `refactor(repository): extract smash speed service`
- main push status: pending final verification.
- tag push status: `v0.4.1.1` pending final verification.
- Next work candidate:
  - Re-check `TrainingRepository.kt` line count and audit before deciding whether `CalendarRecordService` is worth the risk.
- Cautions:
  - Calendar copy/delete remains intentionally untouched because confirmed/unconfirmed, overwrite, timestamp, and display-order behavior is riskier.
  - Backup/import/export and Lab metric calculation were not modified.

## v0.4.1.1 Calendar Record Extraction Audit

- Checked at: 2026-07-03 +09:00
- Baseline: `HEAD`, `origin/main`, and `v0.4.1.1` all pointed to `9e1bcafb3cfc19e5903b63cf5d89dec846c9def0`.
- Work target: document CalendarRecordService extraction risk before code changes.
- Cause: date-level record copy/delete touches confirmed/unconfirmed state, overwrite behavior, completed timestamps, display order, set order, ProgramPlanService boundaries, and backup/restore separation.
- Changes:
  - Audited date delete/range delete behavior.
  - Audited single-date copy, move, range copy, and preserve-state copy behavior.
  - Audited batch edit status-copy boundaries.
  - Audited RecordMutationService single entry/set mutation boundaries.
  - Audited ProgramPlanService apply-to-dates boundaries.
  - Audited backup/restore boundaries.
  - Added `docs/v0.4.1.1_calendar_record_extraction_audit.md`.
- Reason: a documentation-only audit is safer than immediately extracting CalendarRecordService after multiple repository refactors.
- Result:
  - production code changed: no.
  - test code changed: no.
  - version/tag changed: no.
  - Minimum future extraction candidate is limited to calendar conflict summary plus date copy/delete helpers.
- Modified files:
  - `docs/codex_worklog.md`
  - `docs/v0.4.1.1_calendar_record_extraction_audit.md`
- Next work candidate:
  - Possible `CalendarRecordService`, only if it keeps `TrainingRepository` public APIs stable and does not touch `RecordMutationService`, `ProgramPlanService`, backup/restore, DAO queries, UI, version, or schema.
- Cautions:
  - Keep ProgramPlanService planned-only overwrite separate from calendar overwrite.
  - Keep RecordMutationService single entry/set completion and display-order logic separate.
  - Do not reuse calendar copy/delete helpers in restore/import.

## v0.4.1.2 Calendar Record Service Extraction

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `cb36b0fd2a3585167e43c2b13b71f7ed5ab83a11`; `v0.4.1.1` tag remains at `9e1bcafb3cfc19e5903b63cf5d89dec846c9def0`.
- Work target: extract only the audited low-risk calendar record delete/copy/move/range-copy responsibility from `TrainingRepository`.
- Cause: v0.4.1.1 calendar extraction audit identified a minimal service boundary for date-level record operations.
- Changes:
  - Added `CalendarRecordService`.
  - Delegated `calendarConflictSummary`, `deleteDate`, `deleteDateRange`, `copyDate`, `moveDate`, and `copyDateRangeAsPlan`.
  - Moved private `copyEntriesToDate`, `dateRange`, and `nextCreatedAt` helpers into the new service.
  - Bumped app version to `v0.4.1.2` / `401002`.
  - Added `docs/v0.4.1.2_release_notes.md`.
- Reason: this is the smallest useful repository extraction after the audit; higher-risk program apply, record mutation, backup/restore, DAO/schema, and UI paths stay untouched.
- Result:
  - `TrainingRepository.kt` line count changed from 1647 to 1510 before release docs.
  - Public repository APIs and ViewModel/UI call sites remain stable.
  - confirmed/unconfirmed handling preserved.
  - overwrite/append behavior preserved.
  - completedAt/firstConfirmedAt behavior preserved.
  - displayOrder/setIndex behavior preserved.
  - ProgramPlanService, RecordMutationService, backup/restore, DAO/schema, metadata, analysis, home, readiness, and UI paths were not changed.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/data/CalendarRecordService.kt`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.2_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `CalendarRecordService`
- Tests run:
  - `.\\gradlew.bat --version`: passed after one quoting-only retry.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RecordBulkEditTest" --tests "*RecordCsvBackupRestoreTest" --tests "*RecordMutation*" --tests "*ProgramPlan*"`: passed.
  - `.\\gradlew.bat :app:compileDebugAndroidTestKotlin`: passed.
- Commit hash:
  - `1c88ece` `refactor(repository): extract calendar record service`
- main push status: pending final verification.
- tag push status: `v0.4.1.2` pending final verification.
- Next work candidate:
  - Re-check `TrainingRepository.kt` after this extraction before choosing another responsibility. Do not assume program apply or restore/import is safe.
- Cautions:
  - ProgramPlanService planned-only overwrite remains separate.
  - RecordMutationService single entry/set completion and display-order rules remain separate.
  - Calendar helpers must not be reused for backup restore/import without a separate audit.

## v0.4.1.3 Analysis Summary Service Extraction

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `39026404e4d6a9ee712421f486797a6630a50369`; `v0.4.1.2` tag points to the same commit.
- Existing audit map reused:
  - `docs/v0.4.1.0_repository_audit.md` lists `AnalysisSummaryService` as a medium-risk bounded extraction candidate.
  - `docs/v0.4.1.1_calendar_record_extraction_audit.md` confirmed calendar/backup boundaries remain separate.
- Current-code consistency check:
  - `fatigueAnalysisHistory`, `badmintonTransferSummary`, and `badmintonTransferCoverageSummary` still lived in `TrainingRepository`.
  - The three methods were read-only summary assembly.
  - They used the same runtime metadata catalog resolver path and clear `entriesWithSetsUntil(todayString)` date-window semantics.
  - No Lab/Bayesian/lagged analysis helper was shared with the moved code.
- Work target: extract only those read-only analysis summary methods into `AnalysisSummaryService`.
- Cause: `TrainingRepository` still owned bounded fatigue and badminton transfer summary assembly after prior repository extractions.
- Changes:
  - Added `AnalysisSummaryService`.
  - Delegated `fatigueAnalysisHistory`, `badmintonTransferSummary`, and `badmintonTransferCoverageSummary`.
  - Bumped app version to `v0.4.1.3` / `401003`.
  - Added `docs/v0.4.1.3_release_notes.md`.
- Reason: this is the smallest useful analysis extraction; calculation engines, metric registries, metadata resolver behavior, and UI paths stay untouched.
- Result:
  - `TrainingRepository.kt` line count changed from 1510 to 1489 before release docs.
  - Public repository APIs and ViewModel/UI call sites remain stable.
  - metadata resolver path preserved.
  - date-window/history range preserved.
  - empty/insufficient fallback behavior preserved by leaving calculators/analyzers unchanged.
  - fatigue/badminton transfer calculations were not changed.
  - Lab/Bayesian/lagged analysis, backup, record, calendar, program, home, readiness, and UI paths were not changed.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/data/AnalysisSummaryService.kt`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.3_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `AnalysisSummaryService`
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*DailyFatigueCalculatorTest" --tests "*BadmintonTransferAnalysisEngineTest" --tests "*BadmintonTransferCoverageAnalyzerTest" --tests "*RuntimeExerciseMetadataResolverTest" --tests "*ExerciseSeedMetadataPolicyTest" --tests "*RecordCsvBackupRestoreTest" --tests "*AnalysisMetricRegistryTest" --tests "*PerformanceTrendSummaryServiceTest" --tests "*StrengthAndMuscleMetricSeriesBuilderTest"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `cefe906` `refactor(repository): extract analysis summary service`
- main push status: pending final verification.
- tag push status: `v0.4.1.3` pending final verification.
- Next work candidate:
  - Re-check remaining repository responsibilities before choosing another extraction. Do not assume restore/import, metadata editor, or seed/bootstrap is safe.
- Cautions:
  - Do not move calculation engines into this service.
  - Do not merge this service with PerformanceTrendSummaryService unless metric ownership is re-audited.
  - Do not change metadata override priority or runtime catalog resolution in a repository extraction release.

## v0.4.1.4 Read Query and Program Generation Service Extraction

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `cc92d8bc954d4dba6473b8dfabddc5e93dcdf7fe`; `v0.4.1.3` tag points to the same commit.
- Existing audit map reused:
  - `docs/v0.4.1.0_repository_audit.md` lists direct read-only DAO flows/counts as low-risk cleanup and program generation input assembly as a medium-risk bounded candidate.
  - `docs/v0.4.1.1_calendar_record_extraction_audit.md` confirmed calendar/program apply/backup boundaries remain separate.
- Current-code consistency check:
  - simple read-only query/observe/count functions still lived in `TrainingRepository`.
  - `generateProgramSkeleton` input assembly still lived in `TrainingRepository`.
  - read query responsibility and program generation responsibility were independent.
  - `ProgramSkeletonGenerator`, `ProgramBuilder`, and `ProgramBuilderValidation` did not need changes.
- Work target:
  - Commit 1: extract read query service.
  - Commit 2: extract program generation service.
- Cause: `TrainingRepository` still owned simple read query facade methods and program skeleton generation input assembly after prior extractions.
- Changes:
  - Added `RepositoryReadQueryService`.
  - Delegated simple exercise, analysis stats, profile, entries, set counts, and daily summary read queries.
  - Added `ProgramGenerationService`.
  - Delegated `generateProgramSkeleton` input assembly.
  - Bumped app version to `v0.4.1.4` / `401004`.
  - Added `docs/v0.4.1.4_release_notes.md`.
- Reason: these are bounded responsibilities; keeping them as separate services avoids coupling read queries to program generation.
- Result:
  - `TrainingRepository.kt` line count changed from 1489 to 1479 before release docs.
  - Commit 1 and Commit 2 were kept separate.
  - The two services do not reference each other.
  - Public repository APIs and ViewModel/UI call sites remain stable.
  - `ProgramSkeletonGenerator`, `ProgramBuilder`, and `ProgramBuilderValidation` were not changed.
  - generated program behavior meaning was not changed.
  - metadata resolver path preserved.
  - fatigue calculation input meaning preserved.
  - backup, record, calendar, program persistence, analysis, home, readiness, and UI paths were not changed.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/data/RepositoryReadQueryService.kt`
  - `app/src/main/java/com/training/trackplanner/data/ProgramGenerationService.kt`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.4_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `RepositoryReadQueryService`
  - `ProgramGenerationService`
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed after Commit 1.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ProgramBuilder*" --tests "*ProgramBuilderValidation*" --tests "*ProgramSkeletonGeneratorTest*" --tests "*ProgramPlan*" --tests "*ProgramGeneratorUsesResolvedRuntimeMetadataTest*" --tests "*RecordCsvBackupRestoreTest*" --tests "*AnalysisMetricRegistryTest*"`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed after Commit 2.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `c2c7fcc` `refactor(repository): extract read query service`
  - `9662021` `refactor(repository): extract program generation service`
- main push status: pending final verification.
- tag push status: `v0.4.1.4` pending final verification.
- Next work candidate:
  - Re-check remaining repository responsibilities before choosing another extraction. Restore/import, seed/bootstrap, and metadata editor remain higher risk.
- Cautions:
  - Keep read query and program generation services independent.
  - Do not route program generation through read query service unless a future audit proves value.
  - Do not change ProgramBuilder or metadata resolver behavior in repository extraction releases.

## v0.4.1.5 Calendar/Analysis Service Test Hardening

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `92475f56cab4d4ce18f4cf20a85ca84d50551e0d`; `v0.4.1.4` tag points to the same commit.
- Work target:
  - Add dedicated regression tests for `CalendarRecordService`.
  - Add dedicated regression tests for `AnalysisSummaryService`.
- Cause:
  - Recent repository extractions moved behavior-sensitive calendar record orchestration and read-only analysis summary assembly into separate services.
  - Those services needed focused regression tests before further repository cleanup.
- Reason for test-only work:
  - This is a stabilization release, not a feature or refactor release.
  - Production Kotlin code was intentionally left unchanged.
- Changes:
  - Added JVM unit-test Room/Robolectric support in `app/build.gradle.kts`.
  - Added `CalendarRecordServiceTest`.
  - Added `AnalysisSummaryServiceTest`.
  - Bumped app version to `v0.4.1.5` / `401005`.
  - Updated `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json` app version fields.
  - Added `docs/v0.4.1.5_release_notes.md`.
- Calendar test cases added:
  - `copyDateKeepConfirmedTruePreservesMixedSetState`
  - `copyDateKeepConfirmedFalseCopiesAsPlan`
  - `copyDateOverwriteClearsDestinationBeforeCopy`
  - `copyDateAppendKeepsDestinationAndAddsCopiedRecords`
  - `deleteDateRangeWithoutConfirmedKeepsConfirmedSetsAndReindexes`
  - `deleteDateRangeWithConfirmedDeletesAllRecordsInRange`
  - `copyDateRangeAsPlanCopiesOffsetsAsUnconfirmed`
  - `moveDateCopiesToTargetAndDeletesSource`
  - `calendarConflictSummaryCountsExistingDatesEntriesAndSets`
- Analysis test cases added:
  - `fatigueAnalysisHistoryReturnsRepresentativeSeriesForConfirmedRecords`
  - `fatigueAnalysisHistoryEmptyDataKeepsSafeFallbackSeries`
  - `badmintonTransferSummaryUsesWindowedConfirmedTransferRecords`
  - `badmintonTransferCoverageSummaryReturnsNormalPathForTransferRecords`
  - `persistedRuntimeMetadataOverrideEnablesBadmintonTransferSummary`
  - `analysisSummariesIgnoreFutureAndOutOfWindowRecords`
- Audit preservation coverage:
  - confirmed/unconfirmed copy behavior.
  - overwrite/append calendar copy behavior.
  - completedAt/firstConfirmedAt behavior for state-preserving copies.
  - display/set ordering behavior under existing calendar policy.
  - date/LocalDate offset handling.
  - fatigue empty fallback and representative series behavior.
  - badminton transfer metadata override and date-window behavior.
- Production code changed:
  - No production Kotlin code changed.
  - No Room entity/DAO/schema changed.
  - No service extraction or behavior change was made.
- Found gaps / TODO:
  - No production bug was identified.
  - The first `AnalysisSummaryServiceTest` run failed at compile time because the test referenced sample-count fields that are not exposed by `BadmintonTransferMetrics`; the test was corrected to assert public summary invariants instead.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `app/src/test/java/com/training/trackplanner/data/CalendarRecordServiceTest.kt`
  - `app/src/test/java/com/training/trackplanner/data/AnalysisSummaryServiceTest.kt`
  - `docs/v0.4.1.5_release_notes.md`
  - `docs/codex_worklog.md`
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*CalendarRecordServiceTest*"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*AnalysisSummaryServiceTest*"`: passed after correcting the test-only compile error above.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RecordBulkEditTest*" --tests "*RecordCsvBackupRestoreTest*" --tests "*RuntimeExerciseMetadataResolverTest*" --tests "*ExerciseSeedMetadataPolicyTest*" --tests "*AnalysisMetricRegistryTest*" --tests "*StrengthAndMuscleMetricSeriesBuilderTest*"`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `0cc1456` `test(repository): cover calendar record service behavior`
  - `b8d7c6e` `test(repository): cover analysis summary service behavior`
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.5` pending final release commit and push.
- Next work candidate:
  - Do not continue repository refactors until these new tests are observed in CI.
  - Re-audit any future restore/import, seed/bootstrap, or metadata editor extraction separately.
- Cautions:
  - Keep these tests behavior-focused; do not turn them into calculation-spec tests unless the calculation engine itself is being changed.
  - Calendar append ordering is covered through the existing visible-order policy without changing the audited copied-batch `displayOrder` behavior.

## v0.4.1.6 Exercise Metadata Editor Test-First Extraction

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `b5734aff26e967a48ef8ea1e9fde256043d28411`; `v0.4.1.5` tag points to the same commit.
- Existing audit map reused:
  - `docs/v0.4.1.0_repository_audit.md` identified exercise metadata editor / runtime metadata override persistence as a higher-risk remaining repository responsibility.
- Current-code consistency check:
  - `exerciseEditorData`, `saveExerciseEditor`, `resetExerciseMetadataOverride`, runtime metadata resolution facade methods, `setExerciseActive`, and `deleteExerciseIfUnused` still lived in `TrainingRepository`.
  - Seed/bootstrap, backup/restore, resolver algorithm, analysis/fatigue/badminton transfer/Lab, UI/ViewModel, and Room schema changes were not required.
- Work target:
  - Phase 1: lock existing exercise metadata editor behavior with focused regression tests.
  - Phase 2: extract the locked behavior into `ExerciseMetadataEditorService`.
  - Phase 3: bump to `v0.4.1.6` and run focused + full verification.
- Cause:
  - `TrainingRepository` still owned metadata editor data assembly, editor save, override reset, runtime metadata facade resolution, active/archive toggling, and unused custom exercise deletion after prior repository extractions.
- Changes:
  - Added `ExerciseMetadataEditorBehaviorTest`.
  - Added `ExerciseMetadataEditorService`.
  - Delegated existing `TrainingRepository` public APIs to the new service.
  - Bumped app version to `v0.4.1.6` / `401006`.
  - Updated `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json` app version fields.
  - Added `docs/v0.4.1.6_release_notes.md`.
- Phase 1 test cases added:
  - `exerciseEditorDataForNewExerciseReturnsCustomDraftDefaultsAndSortedCopySources`
  - `exerciseEditorDataForExistingExerciseReturnsEffectiveMetadataAndExcludesSelfCopySource`
  - `saveExerciseEditorCreatesCustomExerciseWithUniqueStableKeyAndOverride`
  - `saveExerciseEditorUpdatesExistingExerciseWhilePreservingStableKey`
  - `saveExerciseEditorValidationFailsWithoutPartialWrites`
  - `resetExerciseMetadataOverrideForSeedExerciseDeletesOverrideAndRestoresSeedRow`
  - `resetExerciseMetadataOverrideForMissingExerciseReturnsFalse`
  - `resolveRuntimeMetadataAndByExerciseIdReflectOverridePriorityForAllExercises`
  - `setExerciseActivePreservesMetadataOverride`
  - `deleteExerciseIfUnusedDeletesOnlyUnusedCustomExerciseAndItsOverride`
- Phase 1 result:
  - Initial test run exposed a test-only assertion issue around localized validation message substrings.
  - Test was adjusted to assert the existing validation failure type and no partial writes.
  - No production bug was identified.
- Phase 2 extraction:
  - Moved editor data assembly, save validation/transaction, override reset transaction, runtime metadata resolution facade, active/archive update, unused custom exercise deletion, and user stable-key generation into `ExerciseMetadataEditorService`.
  - Kept `TrainingRepository` as the public facade.
  - Kept ViewModel/UI call sites unchanged.
  - Kept seed/bootstrap, backup/restore, metadata resolver semantics, analysis/fatigue/badminton transfer/Lab, home/readiness, and program behavior unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataEditorService.kt`
  - `app/src/test/java/com/training/trackplanner/data/ExerciseMetadataEditorBehaviorTest.kt`
  - `docs/v0.4.1.6_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `ExerciseMetadataEditorService`
  - `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataEditorService.kt`
- New test file:
  - `app/src/test/java/com/training/trackplanner/data/ExerciseMetadataEditorBehaviorTest.kt`
- `TrainingRepository.kt` line count:
  - Before: 1479
  - After: 1376
- Tests run:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ExerciseMetadataEditor*"`: failed once due test-only validation-message assertion; passed after test-only correction.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ExerciseMetadataEditor*"` after extraction: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RuntimeExerciseMetadataResolverTest*" --tests "*ExerciseSeedMetadataPolicyTest*" --tests "*RecordCsvBackupRestoreTest*" --tests "*AnalysisSummaryServiceTest*"`: passed.
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `132c559` `test(repository): cover exercise metadata editor behavior`
  - `ceca98b` `refactor(repository): extract exercise metadata editor service`
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.6` pending final release commit and push.
- Remaining risk areas:
  - Seed/bootstrap repair and backup/restore internals remain out of scope.
  - Runtime metadata resolver algorithm and canonical metadata policy should stay separately audited before any future changes.
- Next work candidate:
  - Re-audit remaining `TrainingRepository` responsibilities after observing this metadata editor extraction in CI.

## v0.4.1.7 Backup Restore Importer Test-First Extraction

- Checked at: 2026-07-03 +09:00
- Baseline: latest `origin/main` at `946467e6590b5cbb3a795b73221552922f0df55a`; `v0.4.1.6` tag points to the same commit.
- Work target:
  - Phase 1: add focused restore import behavior tests before production changes.
  - Phase 2: move restore CSV import orchestration out of `TrainingRepository` into `BackupRestoreImportService`.
  - Phase 3: bump to `v0.4.1.7` and run focused + full verification.
- Cause:
  - `TrainingRepository` still owned the restore CSV import body after earlier backup import/export orchestration extraction.
  - Restore import is high risk because it restores exercises, runtime metadata overrides, daily metrics, check-ins, smash speed rows, workout entries, and sets.
- Current-code consistency check:
  - `TrainingRepository.importRecordsBackup(...)` still used `BackupImportService`.
  - `TrainingRepository.importRestoreCsv(...)` still contained restore body logic before this work.
  - Existing daily-timeseries import remained separate and was not moved.
- Phase 1 test coverage added:
  - `restoreBackupPreservesRuntimeMetadataOverridePrecedence`
  - `restoreBackupPreservesCustomExerciseStableKeyAndOverride`
  - `restoreBackupUsesDailyMetricSleepAsCanonicalCheckInSleep`
  - `restoreBackupPromotesCheckInSleepWhenDailyMetricMissing`
  - `restoreBackupSkipsDuplicateSmashSpeedRows`
  - `restoreBackupGroupsSetsPreservesStateAndSkipsDuplicateEntries`
- Phase 1 result:
  - Focused restore behavior tests passed before production extraction.
  - No production bug was identified.
- Phase 2 extraction:
  - Added `BackupRestoreImportService`.
  - Moved restore CSV import orchestration to the service.
  - Kept `TrainingRepository.importRecordsBackup(...)` public API unchanged.
  - Kept `BackupImportService` dispatch behavior unchanged.
  - Kept daily-timeseries import behavior unchanged.
  - Kept shared restore helper methods in `TrainingRepository` and injected them into the new service to avoid changing shared daily-timeseries helper behavior.
- Behavior preserved:
  - Runtime metadata override restore and `safeForSeedMutation = false`.
  - Custom exercise stableKey handling.
  - DailyMetric / DailyCheckIn sleep canonicalization and promotion.
  - Smash speed duplicate skip.
  - Workout entry/set grouping, confirmed/unconfirmed restore, duplicate skip, and result counts.
  - Backup format, export format, metadata resolver semantics, analysis, UI, Room schema, and ViewModel call sites.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/BackupRestoreImportService.kt`
  - `app/src/test/java/com/training/trackplanner/data/BackupRestoreImportBehaviorTest.kt`
  - `docs/v0.4.1.7_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `BackupRestoreImportService`
  - `app/src/main/java/com/training/trackplanner/data/BackupRestoreImportService.kt`
- New test file:
  - `app/src/test/java/com/training/trackplanner/data/BackupRestoreImportBehaviorTest.kt`
- `TrainingRepository.kt` line count:
  - Before: 1376
  - After: 1223
- Tests run:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*BackupRestoreImportBehaviorTest*"` before extraction: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RecordCsvBackupRestoreTest*" --tests "*ExerciseMetadataEditorBehaviorTest*" --tests "*RuntimeExerciseMetadataResolverTest*" --tests "*ExerciseSeedMetadataPolicyTest*"`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin` after extraction: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*BackupRestoreImportBehaviorTest*"` after extraction: passed.
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `b2f0c87` `test(backup): cover restore import behavior`
  - `e9efd1a` `refactor(repository): extract backup restore import service`
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.7` pending final release commit and push.
- Remaining risk areas:
  - Restore helper internals remain sensitive and should not be reformatted or "improved" without dedicated tests.
  - Daily-timeseries import remains in `TrainingRepository`; do not merge it into restore service without a separate audit.
- Next work candidate:
  - Re-audit the remaining import/daily-timeseries helper boundary before another repository extraction.

## v0.4.1.8 Backup Restore Importer Extraction Completion

- Checked at: 2026-07-04 +09:00
- Baseline: latest `origin/main` at `9248ec2e55dd8615b8d409b9b770cde51b30640f`; `v0.4.1.7` tag points to the same commit.
- Work target:
  - Verify that the v0.4.1.7 backup restore importer extraction is fully wired into production flow.
  - Remove any remaining private `TrainingRepository.importRestoreCsv(...)` body only if it still exists.
  - Bump release metadata to `v0.4.1.8`.
- Current-code consistency check:
  - `TrainingRepository.importRecordsBackup(...)` already delegates `restoreImporter` to `backupRestoreImportService::importRestoreCsv`.
  - `BackupRestoreImportService.importRestoreCsv(...)` exists and is used by the production import flow.
  - Private `TrainingRepository.importRestoreCsv(...)` is already absent.
  - `importDailyTimeseriesCsv(...)` remains in `TrainingRepository`, unchanged.
- Cause:
  - The follow-up request assumed v0.4.1.7 had added the restore service/tests but had not completed wiring/removal.
  - CLI inspection showed the extraction completion was already present in the v0.4.1.7 baseline.
- Changes:
  - No production Kotlin refactor was needed.
  - Bumped `versionName` to `v0.4.1.8` and `versionCode` to `401008`.
  - Updated canonical metadata manifest app version fields to `v0.4.1.8`.
  - Added `docs/v0.4.1.8_release_notes.md`.
  - Appended this worklog entry.
- Behavior preserved:
  - Backup CSV format unchanged.
  - Restore semantics unchanged.
  - Import result counts and duplicate detection unchanged.
  - Runtime metadata override restore and `safeForSeedMutation = false` unchanged.
  - Custom exercise stableKey restore unchanged.
  - DailyMetric / DailyCheckIn sleep canonicalization unchanged.
  - Smash speed restore unchanged.
  - Workout entry/set restore and confirmed/unconfirmed handling unchanged.
  - Metadata resolver semantics, analysis, UI, Room schema, and ViewModel call sites unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `docs/v0.4.1.8_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - None.
- `TrainingRepository.kt` line count:
  - Before: 1223
  - After: 1223
- Tests run:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*BackupRestore*" --tests "*RecordCsvBackupRestoreTest*" --tests "*ExerciseMetadataEditorBehaviorTest*" --tests "*RuntimeExerciseMetadataResolverTest*" --tests "*ExerciseSeedMetadataPolicyTest*"`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.8` pending final release commit and push.
- Remaining risk areas:
  - Daily-timeseries import remains in `TrainingRepository` and should not be moved without a separate audit.
  - Restore helper callbacks remain behavior-sensitive; avoid cleanup-only rewrites without tests.
- Next work candidate:
  - Re-audit remaining daily-timeseries import and restore helper boundaries before any further backup import extraction.

## v0.4.1.9 Daily-Timeseries Import Test-First Extraction

- Checked at: 2026-07-04 +09:00
- Baseline: latest `origin/main` at `f6484def5831d12d71ad9ab01e697fd4b11be907`; `v0.4.1.8` tag points to the same commit.
- Work target:
  - Add focused daily-timeseries CSV import behavior tests first.
  - Move the daily-timeseries import body out of `TrainingRepository`.
  - Keep `TrainingRepository.importRecordsBackup(...)` public API unchanged.
  - Bump release metadata to `v0.4.1.9`.
- Current-code consistency check:
  - `TrainingRepository.importRecordsBackup(...)` already delegates restore import to `backupRestoreImportService::importRestoreCsv`.
  - `TrainingRepository.importRecordsBackup(...)` still delegated daily-timeseries import to private `::importDailyTimeseriesCsv` before this work.
  - `TrainingRepository.importDailyTimeseriesCsv(...)` still contained the daily-timeseries import body before this work.
- Phase 1 test coverage added:
  - `dailyTimeseriesImport_importsMetricsAndGeneratedEntries`
  - `dailyTimeseriesImport_skipsGeneratedEntriesWhenDateAlreadyImported`
  - `dailyTimeseriesImport_keepsExistingNullOverwritePolicyForPartialMetricRows`
  - `dailyTimeseriesImport_reportsWarningsAndSkipsInvalidDateRows`
- Phase 1 result:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*DailyTimeseriesImportBehaviorTest*"` passed before production extraction.
- Phase 2 extraction:
  - Added `DailyTimeseriesImportService`.
  - Moved the daily-timeseries import flow out of `TrainingRepository`.
  - Wired `BackupImportService` `dailyTimeseriesImporter` to `dailyTimeseriesImportService::importDailyTimeseriesCsv`.
  - Removed the private `TrainingRepository.importDailyTimeseriesCsv(...)` body.
  - Kept daily-timeseries category mapping and imported exercise creation as repository callbacks to avoid changing existing metadata/category helper behavior.
- Behavior preserved:
  - Daily-timeseries CSV format unchanged.
  - Date parsing and warning behavior unchanged.
  - DailyMetric upsert behavior unchanged.
  - sleepHours/bodyWeightKg null overwrite behavior unchanged.
  - Duplicate date entry skip behavior unchanged.
  - Import result counts unchanged.
  - Backup restore behavior unchanged.
  - Metadata resolution, analysis, UI, Room schema, and ViewModel call sites unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
  - `app/src/main/java/com/training/trackplanner/data/DailyTimeseriesImportService.kt`
  - `app/src/test/java/com/training/trackplanner/data/DailyTimeseriesImportBehaviorTest.kt`
  - `docs/v0.4.1.9_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `DailyTimeseriesImportService`
  - `app/src/main/java/com/training/trackplanner/data/DailyTimeseriesImportService.kt`
- New test file:
  - `app/src/test/java/com/training/trackplanner/data/DailyTimeseriesImportBehaviorTest.kt`
- `TrainingRepository.kt` line count:
  - Before: 1223
  - After: 1125
- Tests run:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*DailyTimeseriesImportBehaviorTest*"` before extraction: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed after extraction.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*DailyTimeseries*" --tests "*BackupRestore*" --tests "*RecordCsvBackupRestoreTest*" --tests "*AnalysisSummaryServiceTest*"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RuntimeExerciseMetadataResolverTest*" --tests "*ExerciseSeedMetadataPolicyTest*" --tests "*ExerciseMetadataEditorBehaviorTest*"`: passed.
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - `4328edf` `test(backup): cover daily timeseries import behavior`
  - `22bf844` `refactor(repository): extract daily timeseries import service`
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.9` pending final release commit and push.
- Remaining risk areas:
  - The daily-timeseries category mapping remains in `TrainingRepository` as a callback; move it only with a separate small test if needed.
  - Backup restore and daily-timeseries import should remain separate services unless a future audit shows a safe shared boundary.
- Next work candidate:
  - Re-audit remaining `TrainingRepository` callbacks/helpers after observing v0.4.1.9 in CI.

## v0.4.1.10 ProgramBuilder Policy Extraction

- Checked at: 2026-07-04 +09:00
- Baseline: latest `origin/main` at `c44c1537f3953375175e84ea2d7779372110a60f`; `v0.4.1.9` tag points to the same commit.
- Work target:
  - Add focused ProgramBuilder prescription/duration/session-fit behavior tests first.
  - Move only the tested prescription policy helpers out of `ProgramBuilder`.
  - Keep generated program behavior and ProgramBuilder public behavior unchanged.
  - Bump release metadata to `v0.4.1.10`.
- Current-code consistency check:
  - `ProgramBuilder.kt` still owned `prescribe`, `exerciseCount`, `warmupReserveSeconds`, `estimateItemDurationSeconds`, and `fitRequiredPrescription`.
  - Those helpers were separable without changing candidate selection, template selection, fatigue gate filtering, warnings, validation, repository, Room, metadata, backup, or analysis paths.
- Phase 1 test coverage added:
  - exercise count minute bands.
  - warmup reserve minute bands.
  - role-based sets/reps and volume/fatigue factors.
  - timed exercise seconds and label format.
  - fatigue gate RPE cap.
  - item duration estimate.
  - required-slot fit reduction.
- Phase 1 result:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ProgramPrescriptionPolicyBehaviorTest*"` passed before extraction.
- Phase 2 extraction:
  - Added `ProgramPrescriptionPolicy`.
  - Moved `prescribe`, `exerciseCount`, `warmupReserveSeconds`, `estimateItemDurationSeconds`, and `fitRequiredPrescription` out of `ProgramBuilder`.
  - Updated `ProgramBuilder` to delegate only those helpers to the new policy.
  - Updated the behavior test to call `ProgramPrescriptionPolicy` directly after extraction.
- Behavior preserved:
  - Candidate selection unchanged.
  - Template selection unchanged.
  - Fatigue gate semantics unchanged.
  - Generated program output shape, warnings, and validation unchanged.
  - Repository, ViewModel/UI, Room schema, metadata, backup, and analysis paths unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/assets/metadata/canonical_exercise_metadata_manifest.json`
  - `app/src/main/java/com/training/trackplanner/data/ProgramBuilder.kt`
  - `app/src/main/java/com/training/trackplanner/data/ProgramPrescriptionPolicy.kt`
  - `app/src/test/java/com/training/trackplanner/data/ProgramPrescriptionPolicyBehaviorTest.kt`
  - `docs/v0.4.1.10_release_notes.md`
  - `docs/codex_worklog.md`
- New service/class/file:
  - `ProgramPrescriptionPolicy`
  - `ProgramPrescription`
  - `docs/v0.4.1.10_release_notes.md`
- New test file:
  - `app/src/test/java/com/training/trackplanner/data/ProgramPrescriptionPolicyBehaviorTest.kt`
- `ProgramBuilder.kt` line count:
  - Before: 978
  - After: 898
- Tests run:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ProgramPrescriptionPolicyBehaviorTest*"` before extraction: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed after extraction.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ProgramPrescriptionPolicyBehaviorTest*"` after extraction: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*ProgramPrescription*" --tests "*ProgramBuilder*" --tests "*ProgramSkeletonGenerator*" --tests "*ProgramGeneratorUsesResolvedRuntimeMetadata*" --tests "*ProgramTemplateCatalog*" --tests "*ProgramGeneratedQuality*" --tests "*ProgramFallbackRolePolicy*" --tests "*ProgramCoveragePolicy*" --tests "*ProgramArchitectureFoundation*"`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*RecordMutation*" --tests "*BackupRestore*" --tests "*DailyTimeseries*"`: passed.
  - final `.\\gradlew.bat --version`, `compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` pending release verification.
- Commit hash:
  - `01f2e18` `test(program): cover prescription and duration policies`
  - `0361f82` `refactor(program): extract prescription policy`
  - release commit pending.
- main push status: pending final release commit and push.
- tag push status: `v0.4.1.10` pending final release commit and push.
- Remaining risk areas:
  - `ProgramBuilder` still owns candidate scoring and slot selection; do not move those without separate tests.
  - `ProgramPrescriptionPolicy.fitRequiredPrescription` preserves the existing label behavior when reducing set count.
- Next work candidate:
  - Re-audit remaining `ProgramBuilder` scoring/selection helpers after observing v0.4.1.10 in CI.

## v0.4.2.4 Fatigue OFI Status / Axis Warning Separation

- Baseline:
  - Started from latest `main` at `495d27d` after the v0.4.2.3 fatigue/readiness wording hotfix.
- Work target:
  - Keep the Home "현재 피로도 상태" overall label based only on canonical OFI.
  - Move axis-specific high/very-high guidance into a separate current-axis message.
  - Keep projected/expected fatigue wording separate from current-axis warnings.
- Cause:
  - The Home card combined an OFI score with an axis-derived label, so one very-high axis could make a normal OFI day look like an overall deep fatigue state.
- Changes:
  - Added `TodayFatigueStatusLabeler.currentSummary(...)` for OFI label plus separate current-axis guidance.
  - Added `TodayFatigueStatusLabeler.axisSummary(...)` for analysis readiness UI axis guidance.
  - Added `axisMessage` and `levelCountMessage` to `HomeFatigueCardSummary`.
  - Updated Home and analysis readiness UI to show current-axis guidance below the overall status.
  - Updated focused tests for OFI/axis separation, very-high priority over high axes, high-only axes, all-good message, canonical axis order, and level-count totals.
  - Bumped version metadata to `v0.4.2.4` / `402004`.
- Behavior preserved:
  - OFI calculation unchanged.
  - Fatigue axis calculation unchanged.
  - Readiness thresholds unchanged.
  - Projected/expected fatigue calculation and wording path unchanged except for avoiding current-label coupling.
  - Plan, record, backup, metadata, and program logic unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt`
  - `app/src/main/java/com/training/trackplanner/HomeScreen.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueModels.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactory.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt`
  - `docs/v0.4.2.4_release_notes.md`
  - `docs/codex_worklog.md`
- Focused test result:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*TodayFatigueStatusLabelerTest*" --tests "*HomeFatigueCardSummaryFactoryTest*"`: passed.
- Final verification:
  - First `.\\gradlew.bat :app:testDebugUnitTest` attempt hit a transient Windows file-lock delete failure on `R.jar`.
  - Retried `.\\gradlew.bat :app:testDebugUnitTest`: passed.
- Final verification:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
  - `.\\gradlew.bat :app:assembleDebug`: passed.
- Commit hash:
  - pending.
- main push status:
  - pending.
- tag push status:
  - `v0.4.2.4` pending.

## v0.4.2.5 Fatigue Wording Hotfix

- Baseline:
  - Started from latest `origin/main` at `355f7b5` / `v0.4.2.4`.
- Work target:
  - Change only displayed strings for the separated OFI status and axis warning UI.
  - Preserve OFI classifier logic, fatigue calculations, readiness thresholds, and projected/expected fatigue logic.
- Cause:
  - v0.4.2.4 separated OFI status from axis warnings, but final Korean copy needed exact wording and canonical axis names.
- Changes:
  - Changed the Home current line from `현재 피로도: <score> · <label>` to `현재 상태: <score> · <OFI label>`.
  - Changed OFI display labels to include `피로도`, e.g. `피로도 보통`.
  - Restored axis names `관절/건/충격` and `회복 지속`.
  - Updated very-high, high, and all-good axis guidance strings.
  - Bumped version metadata to `v0.4.2.5` / `402005`.
- Behavior preserved:
  - OFI canonical classifier still owns the overall status label.
  - Axis warnings still do not overwrite the OFI label.
  - Count line remains `축별 상태: 매우 높음(n), 높음(m), 보통(l), 낮음(r)`.
  - Projected/expected fatigue logic unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/training/trackplanner/HomeScreen.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt`
  - `docs/v0.4.2.5_release_notes.md`
  - `docs/codex_worklog.md`
- Focused test result:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*TodayFatigueStatusLabelerTest*" --tests "*HomeFatigueCardSummaryFactoryTest*"`: passed.
- Final verification:
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
- Commit hash:
  - pending.
- main push status:
  - pending.
- tag push status:
  - `v0.4.2.5` pending.

## v0.4.2.6 Fatigue Axis Warning Wording Follow-up

- Baseline:
  - Started from latest `origin/main` at `c27061c` / `v0.4.2.5`.
- Work target:
  - Keep axis names in warning copy without repeating them awkwardly.
  - Remove generic `해당 스트레스` wording from current-axis warning copy.
- Cause:
  - The previous wording either used generic wording or, during correction, repeated the axis list twice.
- Changes:
  - Very-high axis message now uses `<축 이름> 피로도가 높습니다. 주의하세요.`
  - High axis message now uses `<축 이름> 피로도가 높습니다. 스트레스를 줄이면 좋습니다.`
  - Bumped version metadata to `v0.4.2.6` / `402006`.
- Behavior preserved:
  - OFI canonical classifier still owns the overall status label.
  - Axis warnings still do not overwrite the OFI label.
  - Axis level count line unchanged.
  - Projected/expected fatigue logic unchanged.
- Modified files:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt`
  - `docs/v0.4.2.6_release_notes.md`
  - `docs/codex_worklog.md`
- Focused test result:
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*TodayFatigueStatusLabelerTest*" --tests "*HomeFatigueCardSummaryFactoryTest*"`: passed.
- Commit hash:
  - pending.
- main push status:
  - pending.
- tag push status:
  - `v0.4.2.6` pending.

## v0.4.2.6 Canonical OFI Fatigue Pipeline Cleanup

- Baseline:
  - Started from latest `origin/main` at `f7ab373` / `v0.4.2.6`.
- Work target:
  - Make the Analysis fatigue screen and Home fatigue card use canonical OFI as the single user-facing fatigue source.
  - Keep readiness safety guardrails, performance decrease, discomfort, and movement limitation signals separate from fatigue-axis judgement.
- Cause:
  - The fatigue UI still mixed canonical OFI, readiness presentation axes, readiness detail sections, and recovery residual wording.
  - This could expose `회복 지속` as a user-facing fatigue axis and blur the difference between fatigue load and safety/readiness signals.
- Changes:
  - Canonical OFI aggregation now uses only five displayed fatigue axes.
  - Displayed axes are limited to `신경계`, `전신 근육`, `국소 근육`, `관절·건·충격`, and `동작·집중`.
  - `회복 지속` is removed from fatigue-axis selector, graph series, current-axis summary, cause analyzer axes, and weekly burden display.
  - Analysis top card now shows canonical current state, judgement, axis warning, and count line from `DailyFatigueState`.
  - Removed the `세부 판단 보기` readiness detail UI from the fatigue screen.
  - Added `수행 감소 및 불편감 신호 정리` for performance/discomfort safety signals.
  - Kept `수면 보정 코칭 신호` as a separate coaching signal block.
  - Updated single-axis warning copy so the axis name appears once and `해당 스트레스` is not used.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt`
  - `app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt`
  - `app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/coach/CoachCheckInInterpreter.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/coach/CoachFatigueCauseAnalyzer.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/coach/JointTendonWarningAnalyzer.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/FatigueAnalysisMapper.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/FatigueAnalysisModels.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactory.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/fatigue/ui/FatigueAnalysisControls.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/lab/AnalysisMetricRegistry.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/trends/PerformanceTrendSentenceBuilder.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/coach/CoachFatigueCauseAnalyzerTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/fatigue/FatigueAnalysisMapperTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt`
  - `docs/v0.4.2.6_canonical_ofi_fatigue_pipeline.md`
  - `docs/codex_worklog.md`
- Behavior preserved:
  - Readiness safety guardrails remain available as separate coaching signals.
  - projected/expected fatigue calculation is not intentionally changed.
  - versionName/versionCode unchanged.
  - no tag created.
- Existing dirty files:
  - `outputs/*` dirty files were not touched or staged.
- Verification:
  - `.\\gradlew.bat --version`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest --tests "*TodayFatigueStatusLabelerTest*" --tests "*HomeFatigueCardSummaryFactoryTest*" --tests "*FatigueAnalysisMapperTest*" --tests "*CoachFatigueCauseAnalyzerTest*" --tests "*CoachCheckInIntegrationTest*"`: passed.
  - `.\\gradlew.bat :app:compileDebugKotlin`: passed.
  - `.\\gradlew.bat :app:testDebugUnitTest`: passed.
- Commit hash:
  - pending.
- main push status:
  - pending.
- tag push status:
  - no new tag for this cleanup.

## Canonical OFI Fatigue Detail UI Follow-up

- Baseline:
  - Started from latest `origin/main` at `fa9ea52` / `v0.4.2.6`.
- Work target:
  - Add an expandable five-axis detail view to `주의할 피로 축과 주요 기여 운동`.
  - Combine performance/discomfort and sleep coaching blocks into `인식 신호`.
- Cause:
  - The collapsed fatigue card did not let users compare all canonical axes or see axis-specific exercise contributors.
  - Recognition signals were split across two top-level cards.
- Changes:
  - Added canonical axis contribution scores to the existing fatigue-cause model without changing OFI calculation or thresholds.
  - Added `자세히 보기` / `접기` state; expanded content always shows 신경계, 전신 근육, 국소 근육, 관절·건·충격, and 동작·집중.
  - Shows contributors only for high or very-high axes, deduplicates equal display names, and applies the 1.5x top-contributor rule.
  - Replaced the two separate signal cards with one `인식 신호` card containing performance/discomfort and sleep subsections.
- Behavior preserved:
  - Canonical OFI remains the only fatigue-state source in the fatigue screen.
  - Raw scores, thresholds, z-scores, percentiles, stable keys, and fallback IDs are not displayed.
  - Performance/discomfort and sleep signals remain separate from OFI axis judgement.
  - versionName/versionCode and tags are unchanged.
- Modified files:
  - `app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt`
  - `app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/coach/CoachAnalysisModels.kt`
  - `app/src/main/java/com/training/trackplanner/analysis/coach/CoachFatigueCauseAnalyzer.kt`
  - `app/src/test/java/com/training/trackplanner/FatigueAxisCauseCardTest.kt`
  - `app/src/test/java/com/training/trackplanner/analysis/coach/CoachFatigueCauseAnalyzerTest.kt`
  - `docs/v0.4.2.6_canonical_ofi_fatigue_pipeline.md`
  - `docs/codex_worklog.md`
- Verification:
  - `.\gradlew.bat --version`: passed.
  - Focused fatigue UI and cause tests: passed.
  - `.\gradlew.bat :app:testDebugUnitTest`: passed.
  - GitHub Actions: pending.
- Commit hash:
  - pending.
- main push status:
  - pending.
- tag push status:
  - no new tag requested.
## Bayesian Time-Series Lab Dynamic Analysis

- Baseline:
  - Started from latest `origin/main` at `4b7d4be323eb893f7ef15787e8d4d6f60706945e`.
  - Existing `outputs/*` changes were already dirty and were not modified or staged.
- Audit result:
  - The prior lab path was a weekly single-Y ridge-like lag regression. It did not implement a multivariate BVAR, Bayesian VECM, structural shock identification, or true IRF routing.
- Changes:
  - Added aligned weekly preprocessing, candidate screening, ADF/KPSS-style integration diagnostics, first-difference transformations for non-cointegrated I(1) series, Bayesian lag posterior selection, and horizon reduction from 8 down to 1.
  - Added X-one/Y-many/Z-zero-or-more request handling and UI disclosure for selected model, actual horizon, lag posterior, transformations, aligned period, automatic endogenous variables, and Cholesky ordering.
  - Added rolling-origin log-predictive-density endogenous-variable selection with a sample-based K cap and explicit selection diagnostics.
  - Added Cholesky one-standard-deviation structural shocks, canonical temporal ordering, adjacent-order sensitivity diagnostics, Minnesota-style BVAR empirical-Bayes shrinkage, rank-1 Bayesian VECM, and explicit BVAR fallback.
  - Preserved the old `LaggedTimeSeriesAnalyzer` public compatibility facade by delegating it to the new analyzer.
- New files:
  - `BayesianTimeSeriesModels.kt`
  - `BayesianTimeSeriesSupport.kt`
  - `BayesianLocalProjectionEstimator.kt`
  - `BayesianDynamicEstimators.kt`
  - `CointegrationAnalyzer.kt`
  - `CholeskyShockIdentifier.kt`
  - `EndogenousVariableSelector.kt`
  - `BayesianTimeSeriesAnalyzer.kt`
  - `docs/bayesian_time_series_lab_architecture.md`
- Focused verification:
  - `:app:compileDebugKotlin`: passed.
  - `:app:testDebugUnitTest --tests "*LaggedTimeSeriesAnalyzerTest*"`: passed.
- Version and release:
  - Version name/code unchanged as requested.
  - No release tag created or moved.
- Commit:
  - `feat(analysis): implement Bayesian time series lab`.
- Pending at this log point:
  - main push and GitHub Actions verification.

## Time-Series Analysis Phase A Numeric Foundation

Cause
- Started from latest `origin/main` at `25cb01448ea5926841a4b89d9738da135704059a` on branch `feat/time-series-phase-a`.
- The previous lab implementation included hand-coded matrix inverse, determinant, Cholesky decomposition, and power-iteration eigen logic in the analysis path.
- The alignment layer compressed the time axis to common observed weeks, which made lag, first-difference, and horizon rows depend on list positions rather than exact calendar weeks.
- Existing `outputs/*` files were dirty before this work and were intentionally ignored and not staged.

Pre-change numeric audit

| File | Function | Current algorithm | Problem | Replacement |
|---|---|---|---|---|
| `BayesianTimeSeriesSupport.kt` | `BayesianLinearRegression.invert` | Gauss-Jordan inverse | explicit inverse and pivot logic lived in app code | `StableLinearAlgebra.solveSpd` |
| `BayesianTimeSeriesSupport.kt` | `BayesianLinearRegression.logDeterminant` | custom elimination determinant | determinant path was not SPD-specific | `StableLinearAlgebra.logDetSpd` |
| `BayesianDynamicEstimators.kt` | `cholesky` | hand-coded Cholesky | custom SPD decomposition and positivity threshold | `StableLinearAlgebra.cholesky` adapter |
| `CointegrationAnalyzer.kt` | `analyze` | `S11^-1 S10 S00^-1 S01` via custom inverse | explicit inverse and asymmetric product | `StableLinearAlgebra.johansenFormEigen` |
| `CointegrationAnalyzer.kt` | `largestEigenvalue` | Rayleigh quotient over dominant vector | single eigenvalue only | full generalized eigen spectrum |
| `CointegrationAnalyzer.kt` | `dominantEigenvector` | power iteration | custom eigen solver and no residual validation | Commons Math `EigenDecomposition` through whitening |
| `BayesianTimeSeriesSupport.kt` | `TimeSeriesAlignmentService.align` | common observed weeks only | compressed time axis hid calendar gaps | continuous weekly calendar grid with cell states |

Changes
- Added `implementation("org.apache.commons:commons-math3:3.6.1")`.
- Added `StableLinearAlgebra` with SPD solves, least-squares solves, Cholesky, rank, condition number, singular values, symmetric eigen, generalized symmetric eigen, SPD log determinant, symmetry checks, positive-definite checks, and relative asymmetry.
- Replaced app-owned inverse/determinant/Cholesky/power-iteration calls in the time-series lab path.
- Added `TimeSeriesCalendarGrid`, `TimeSeriesCell`, `TimeSeriesCellState`, `TimeSeriesModelRow`, and row exclusion reasons.
- Changed alignment to keep continuous weekly calendar weeks and distinguish observed values, structural zero, and missing cells.
- Added exact lag, first-difference, horizon, and row-builder primitives.
- Added Python/SciPy fixture generator under `tools/time_series_reference/` plus deterministic generated JSON fixtures.
- Added `tools/check_time_series_numeric_sources.py` to block direct inverse, hand eigen, hand determinant, and direct decomposition usage outside the wrapper.

Reason
- Cholesky is the correct primitive for SPD systems and log determinants.
- RRQR/SVD avoids normal-equation inverse for least-squares fallback.
- Whitening `A v = lambda B v` through `B = L L'` avoids forming `B^-1 A` and preserves the symmetric-definite eigenproblem.
- Continuous calendar weeks make missing values explicit and prevent W04 from becoming a fake lag-1 successor of W02.

Result
- PHASE A now provides a verified numeric foundation and time-indexing foundation only.
- Kotlin tests compare Commons Math results to NumPy/SciPy golden fixtures for solves, SVD, symmetric eigen, generalized eigen, and Johansen-form primitive.
- PHASE B BVAR posterior, PHASE C Bayesian Local Projection rebuild, PHASE D full Johansen/VECM, PHASE E UI integration, version bump, and tags remain intentionally untouched.

Tests
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed using bundled Python plus installed SciPy.
- `python tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat --version`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StableLinearAlgebraTest*" --tests "*TimeSeriesCalendarGridTest*" --tests "*LaggedTimeSeriesAnalyzerTest*"`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.
- APK size: before 43,073,867 bytes; after 44,234,574 bytes; delta +1,160,707 bytes.

File/Feature Map
- `StableLinearAlgebra.kt`: Commons Math wrapper and bounded jitter policy.
- `BayesianTimeSeriesModels.kt`: calendar grid, cell states, model rows, and row exclusion contracts.
- `BayesianTimeSeriesSupport.kt`: continuous grid alignment, exact lag/difference/horizon helpers, and wrapper-backed posterior regression.
- `BayesianDynamicEstimators.kt`: wrapper-backed Cholesky and valid calendar row filtering.
- `BayesianLocalProjectionEstimator.kt`: exact horizon/difference/lag row construction.
- `CointegrationAnalyzer.kt`: wrapper-backed Johansen-form primitive.
- `StableLinearAlgebraTest.kt`: Kotlin golden fixture comparisons.
- `TimeSeriesCalendarGridTest.kt`: continuous weekly grid and exact row exclusion coverage.
- `tools/time_series_reference/*`: independent Python/SciPy reference generator and fixtures.
- `tools/check_time_series_numeric_sources.py`: forbidden numeric source scanner.

Remaining
- BVAR posterior sampling, Bayesian LP posterior rebuild, full Johansen trace/max-eigen testing, Bayesian VECM routing, and final UI changes are deferred until explicit Phase B-D/E approval.

## Time-Series Analysis Phase A Hardening Correction

Cause
- Started from `feat/time-series-phase-a` at `ef26caa79f25b059b10443a06a568ecbd527dbda`.
- Phase A needed a correction pass before review because the numeric wrapper still hid too much Cholesky regularization provenance, condition number handling needed to be the standard full-rank definition, generalized eigen handling needed all-or-fail validation, calendar cell states needed lifecycle provenance, and direct triangular substitution needed to be blocked by the static scanner.
- Existing dirty `outputs/*` files remained outside the work and were not staged.

Changes
- Reworked `StableLinearAlgebra` results to expose strict/regularized Cholesky provenance, jitter amount, jitter ratio, condition number, and diagnostics.
- Kept triangular solves inside the wrapper through Commons Math calls and removed app-owned triangular substitution from the shock path.
- Changed condition number behavior so rank-deficient matrices return infinity, while a separate effective condition number remains available when needed.
- Changed generalized symmetric eigen output to return the complete validated spectrum or fail the whole result.
- Added lifecycle-aware `TimeSeriesObservation`, `MetricLifecycleMetadata`, calendar cell source/version/missing metadata, and strict continuous-grid validation.
- Fixed exact row exclusion provenance so target/source/lag cells keep distinct roles and target horizon indexing cannot overwrite same-metric lag references.
- Isolated the current cointegration route as a legacy heuristic in diagnostics instead of presenting it as a Bayesian rank posterior.
- Expanded the static numeric source scan to detect direct triangular solver names and diagonal triangular division patterns.
- Regenerated Phase A fixtures with standard condition number and lifecycle cell-state cases.

Reason
- Phase A is the numerical and calendar foundation. It should fail loudly when foundational invariants are not met instead of silently dropping eigenpairs, compressing week gaps, or hiding regularization.
- Strict SPD and regularizable SPD are different contracts; callers need to know whether jitter affected a result.
- Calendar rows must be auditable by input/lifecycle metadata, not just an enum state.

Result
- PHASE A remains a foundation-only branch. No app version, tag, release, or Phase B implementation was added.
- Numeric, calendar, row exclusion, and source-scan invariants are now covered by focused tests and deterministic fixtures.
- GitHub Actions is intended to run on the feature branch after the correction commit is pushed.

Tests
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed with bundled Python.
- `python tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat --version`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StableLinearAlgebraTest*" --tests "*TimeSeriesCalendarGridTest*" --tests "*LaggedTimeSeriesAnalyzerTest*"`: passed after fixing RHS shape and bounded jitter margin.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.

Remaining
- GitHub Actions branch verification must be dispatched after push.
- BVAR posterior sampling, Bayesian LP posterior mixtures, full Johansen trace/max-eigen testing, Bayesian VECM replacement, and final UI changes remain deferred until explicit Phase B-D/E approval.

## Time-Series Analysis Phase A Second Hardening Correction

Cause
- Started from `feat/time-series-phase-a` at `f7e1046be1fbb718ba06fa97230173a635117438`.
- The follow-up PHASE A audit found remaining contract gaps: singular PSD matrices could still be regularized, Johansen-form provenance was not preserved enough, generalized eigen validation could mix original/effective `B`, calendar bounds could be influenced by unrequested metrics, structural zero activation was too broad, conflicts were resolved by arbitrary ordering, state/value contradictions were not rejected everywhere, stationarization collapsed lifecycle provenance, legacy cointegration still routed to VECM, and final/initial source weeks were not fully diagnosed.
- Existing dirty `outputs/*` files remained ignored and were not staged.

Changes
- Added explicit Cholesky failure codes and effective matrix/numerical-rank provenance.
- Rejected exact singular PSD and rank-deficient matrices before bounded jitter.
- Made Johansen-form eigen strict by default and preserved `S00` solve plus `S11` Cholesky provenance.
- Ensured generalized eigen regularized mode validates residuals, normalization, and orthogonality against the same effective `B` matrix.
- Canonicalized raw trend dates to ISO Monday and required explicit grid/cell week starts to be Mondays.
- Limited grid bounds to requested metrics and requested lifecycle metadata.
- Made structural-zero fill start only after explicit availability or first valid observation.
- Converted unresolvable duplicate observations into `CONFLICT` cells instead of selecting by source string.
- Enforced state/value invariants for observations and cells.
- Preserved lifecycle/source-cell provenance through stationarization and recalculated transformed missing rates.
- Disabled legacy heuristic cointegration from `BAYESIAN_VECM` routing and renamed the score away from posterior-probability wording.
- Changed exact-row diagnostics so every candidate source week is included or excluded with boundary reasons.

Reason
- PHASE A must be a trustworthy numeric/calendar foundation before later Bayesian phases use it.
- Regularization cannot hide rank deficiency, calendar gaps cannot be silently compressed, and a diagnostic-only cointegration heuristic cannot drive model routing.

Result
- PHASE A now fails explicit bad inputs earlier and records the provenance needed by later Johansen/rank-posterior work.
- PHASE B/C/D/E work remains unstarted.
- No version, tag, main merge, or release work was performed.

Tests
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed with bundled Python.
- `python tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat --version`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StableLinearAlgebraTest*" --tests "*TimeSeriesCalendarGridTest*" --tests "*LaggedTimeSeriesAnalyzerTest*"`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.
- GitHub Actions is pending until the correction commit is pushed.

File/Feature Map
- `StableLinearAlgebra.kt`: Cholesky failure codes, strict Johansen primitive, effective-B generalized eigen validation.
- `BayesianTimeSeriesModels.kt`: cell/observation invariants, conflict state, routing-safe cointegration diagnostic fields.
- `BayesianTimeSeriesSupport.kt`: canonical weeks, metric-bound filtering, structural-zero activation, conflict handling, transformed provenance, complete row exclusion diagnostics.
- `CointegrationAnalyzer.kt`: legacy heuristic remains diagnostic-only.
- `BayesianTimeSeriesAnalyzer.kt`: VECM routing requires explicit future routing support and rejects invalid requested horizons.
- `AnalysisLabUi.kt`: displays legacy heuristic score without Bayesian posterior wording.
- `StableLinearAlgebraTest.kt`, `TimeSeriesCalendarGridTest.kt`, `LaggedTimeSeriesAnalyzerTest.kt`: regression coverage for the corrected contracts.
- `tools/time_series_reference/*`: regenerated edge-case fixtures.
- `tools/check_time_series_numeric_sources.py`: scanner blocks legacy posterior naming and direct routing flags.

Remaining
- Dispatch GitHub Actions after commit push.
- PHASE B posterior work, PHASE C LP rebuild, PHASE D Johansen/Bayesian VECM, and PHASE E UI integration remain blocked until explicit approval.

## Time-Series Analysis Phase A Final Contract Hardening

Cause
- Started from `feat/time-series-phase-a` at `c462b3d36d19b3e415e1489eb54dd95a300abad4`.
- The final PHASE A audit found downstream-risk gaps that could leak into later phases: strict Cholesky success did not gate rank and condition number hard enough, generalized eigen validation needed explicit finite checks, lifecycle activation still allowed implicit first-observation creation, conflict provenance needed typed ordering, transformed calendars could be compressed, candidate screening could still bypass prepared cells, and legacy cointegration names still sounded too much like validated rank output.
- Existing dirty `outputs/*` files were preserved and not staged.

Changes
- Tightened strict SPD success to require full numerical rank, finite condition diagnostics, `MAX_CONDITION_NUMBER`, finite factors, and no regularization.
- Preserved regularized SPD provenance: strict failure code, attempt/success flags, effective matrix, jitter, jitter ratio, minimum eigenvalue, matrix scale, and bounded attempts.
- Added explicit generalized-eigen finite gates for transformed matrices, eigenvalues, eigenvectors, B-metric normalization, residuals, and B-orthogonality.
- Added `MetricActivationPolicy`, typed `ObservationRevision`, conflict provenance, `MetricDataQualitySummary`, and immutable `PreparedMetricSeries`.
- Changed structural-zero and pre-creation classification so first observation only establishes activation when explicitly enabled.
- Changed `stationarize()` to preserve the full weekly calendar; first-difference first cells become unavailable instead of dropping week 1.
- Unified automatic candidate screening on prepared series and deprecated the raw screening adapter.
- Renamed legacy cointegration fields to `legacySuggestedRank` and `legacyRankOneStatistic`, with method `LEGACY_RANK_ONE_HEURISTIC`.
- Extended the Python fixture generator and static source scan for the final Phase A contracts.

Reason
- Later Bayesian phases need one safe input boundary and one calendar contract. Rank-deficient or ill-conditioned covariance, hidden jitter, unresolved conflicts, compressed calendars, and misleading legacy rank diagnostics must fail before model fitting rather than contaminating posterior work.

Result
- PHASE A now guarantees strict SPD/regularized SPD provenance, finite generalized-eigen diagnostics, explicit lifecycle activation, typed conflict resolution, structured data-quality summaries, full-calendar transformations, prepared-series candidate screening, and legacy diagnostic isolation.
- PHASE A still deliberately does not implement NIW BVAR posterior sampling, Bayesian Local Projection posterior mixtures, Johansen trace/max-eigen tests, Bayesian rank posterior, Bayesian VECM, posterior IRFs, or final UI integration.

Tests
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed with bundled Python.
- `python tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StableLinearAlgebraTest*" --tests "*TimeSeriesCalendarGridTest*" --tests "*LaggedTimeSeriesAnalyzerTest*"`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.
- GitHub Actions: pending after push.

File/Feature Map
- `StableLinearAlgebra.kt`: final strict/regularized SPD gates and finite generalized-eigen validation.
- `BayesianTimeSeriesModels.kt`: activation policy, typed revisions, conflict provenance, data-quality summaries, prepared-series boundary, and legacy diagnostic names.
- `BayesianTimeSeriesSupport.kt`: explicit lifecycle activation, typed conflict resolution, prepared candidate screening, full-calendar stationarization, transformed quality summaries, and prepared series.
- `EndogenousVariableSelector.kt`: candidate screening now consumes prepared series instead of raw maps.
- `CointegrationAnalyzer.kt`: legacy rank-one heuristic stays diagnostic-only and routing-disabled.
- `BayesianTimeSeriesAnalyzer.kt`: legacy diagnostic routing checks use renamed diagnostic-only fields.
- `StableLinearAlgebraTest.kt`, `TimeSeriesCalendarGridTest.kt`, `LaggedTimeSeriesAnalyzerTest.kt`: regression coverage for the final PHASE A contracts.
- `tools/time_series_reference/*`: deterministic fixture coverage for numeric/calendar/prepared-series cases.
- `tools/check_time_series_numeric_sources.py`: scanner for rank/condition gates, old legacy names, missing-rate direct reads, and dropped-week transforms.

Downstream Contract Map
- PHASE B may rely on strict covariance rejection, regularization provenance, prepared-series inputs, and unusable-rate summaries.
- PHASE C may rely on uncompressed transformed calendars and source-cell provenance.
- PHASE D may rely on strict Johansen-form primitives and complete generalized-eigen diagnostics, while legacy rank-one heuristics cannot route.
- PHASE E may rely on legacy diagnostics being labeled diagnostic-only and not as posterior probabilities or validated rank results.

Remaining
- Final local verification, commit hash, branch push, and GitHub Actions run ID must be recorded after completion.
- All posterior/model-building work remains PHASE B or later and requires explicit approval.

## Time-Series Analysis Phase A Prepared-Data Pipeline Correction

Cause
- Started from `feat/time-series-phase-a` at `257df8d155cd45e134a120d2f3255be2e71d1776`.
- The follow-up PHASE A audit confirmed four prepared-data defects:
  - `EndogenousVariableSelector` still used raw `TrendDataPoint` maps for predictive ranking and stability checks.
  - `MetricDataQualitySummary.unusableRate` could double-count one cell when a state and transformation failure overlapped.
  - Lifecycle metadata could be replaced by empty/default metadata during restriction, stationarization, or fallback preparation.
  - Revision conflict resolution could compare incompatible ordering schemes or select arbitrarily among tied highest revisions.
- Existing dirty `outputs/*` files were preserved and were not staged or modified intentionally.

Changes
- Added explicit `RevisionOrderingScheme`, lifecycle metadata provenance/fingerprints, `PreparedTimeSeriesSystem`, deterministic row fingerprints, model-eligible/usable/unusable counts, and factory-only `MetricDataQualitySummary` / `PreparedMetricSeries` creation.
- Changed conflict resolution so identical duplicates merge, heterogeneous schemes conflict, partial revision metadata conflicts, tied highest revisions with different values conflict, ordinary observation time is not revision authority, and input order is not a tie-break.
- Changed quality summaries to compute `unusableCount` from unique model-eligible cells rather than summing overlapping diagnostic subsets.
- Preserved lifecycle metadata through `restrictToWeeks` and `stationarize`; missing prepared lifecycle metadata now fails instead of silently substituting defaults.
- Changed `EndogenousVariableSelector` to consume a prepared catalog and `PreparedTimeSeriesSystem` common rows for screening, rolling predictive scoring, and stability checks.
- Added optional common source-week filtering to rolling LP scoring and dynamic-system stability fitting.
- Extended the static scan to block selector raw-series bypasses, manual summary/series construction outside the model factory, input-order conflict tie-breaks, source-string revision ordering, and unsafe lifecycle default replacement.
- Added prepared-pipeline fixtures and contract tests for unique unusable counting, lifecycle fingerprints, revision scheme conflicts, permutation invariance, prepared-series validation, and prepared-system row identity.

Reason
- PHASE B must not compare BVAR candidates over different samples, inflate unusable rates, lose lifecycle metadata before lag selection, or let conflict values enter covariance estimation.
- PHASE C must be able to prove BVAR shock and BLP response calendar alignment and must not inherit compressed or stale transformed rows.
- PHASE D must not difference across lifecycle/version boundaries or let conflicting revisions enter Johansen matrices.

Result
- Selector candidate screening, ranking, and stability checks now use the same prepared calendar, transformed values, lifecycle states, conflict decisions, row policy, and common source-week identity.
- Each eligible cell is counted exactly once as usable or unusable; transformation-failure count remains a diagnostic subset.
- Lifecycle metadata fingerprints remain stable under valid slicing and stationarization.
- Revision conflict resolution is explicit, scheme-safe, tie-safe, and permutation-invariant.
- PHASE B/C/D estimator work was not started.

Tests
- `.\gradlew.bat --version`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*TimeSeries*"`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*TimeSeriesPreparedPipelineContractTest" --tests "*TimeSeriesCalendarGridTest" --tests "*LaggedTimeSeriesAnalyzerTest" --tests "*StableLinearAlgebraTest"`: passed.
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed with bundled Python.
- `python tools/check_time_series_numeric_sources.py`: passed with bundled Python.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.
- GitHub Actions: pending until this correction commit is pushed.

File/Feature Map
- `BayesianTimeSeriesModels.kt`: lifecycle provenance/fingerprints, explicit revision schemes, unique-cell quality summaries, prepared-series factory, prepared-system row identity.
- `BayesianTimeSeriesSupport.kt`: conflict resolution rules, prepared alignment reconstruction, metadata-preserving restriction/stationarization, factory-based summaries.
- `EndogenousVariableSelector.kt`: prepared catalog input, prepared-system common rows, raw selector bypass removal.
- `BayesianTimeSeriesAnalyzer.kt`: one prepared catalog for automatic selection and final selected-system alignment.
- `BayesianLocalProjectionEstimator.kt`: rolling predictive scoring can use prepared common source-week rows.
- `BayesianDynamicEstimators.kt`: selector stability checks can use prepared common source-week rows.
- `TimeSeriesPreparedPipelineContractTest.kt`: regression coverage for the four confirmed defects and prepared-system contracts.
- `TimeSeriesCalendarGridTest.kt`: revision-ordering expectation updated to the explicit scheme rule.
- `tools/check_time_series_numeric_sources.py`: static source guards for selector/raw, construction, lifecycle, and conflict-resolution bypasses.
- `tools/time_series_reference/*`: prepared-pipeline contract fixture and README coverage update.
- `docs/bayesian_time_series_lab_architecture.md`: prepared boundary, row identity, downstream contract map, and file responsibility map update.

Downstream Contract Map
- PHASE B may rely on `PreparedTimeSeriesSystem`, common usable rows, deterministic row fingerprints, conflict-free included rows, preserved lifecycle metadata, and unique-cell unusable rates.
- PHASE C may rely on full-calendar transformed series, source-cell provenance, and row fingerprints for shock/response alignment.
- PHASE D may rely on preserved lifecycle/version-discontinuity metadata and unresolved conflicts being excluded from numeric prepared rows.

Call-Graph Audit
- Selector entry point: `BayesianTimeSeriesAnalyzer.analyze()` prepares the catalog and calls `EndogenousVariableSelector.select()` with prepared series.
- Raw-series overloads: `TimeSeriesAlignmentService.align()` remains the ingestion boundary; `usableCandidate()` remains deprecated compatibility only.
- Prepared entry points: `alignmentFromPrepared()`, `PreparedMetricSeries.createValidated()`, and `PreparedTimeSeriesSystem.createValidated()`.
- Metadata-copy points: `alignObservations()`, `restrictToWeeks()`, and `stationarize()`; restriction/stationarization now preserve lifecycle fingerprints.
- Quality-summary constructors: only `MetricDataQualitySummary.fromCells()` inside the model factory path.
- Revision-resolution callers: `alignObservations()` delegates grouped duplicate/conflict observations to `resolveObservationConflict()`.

Remaining
- Push the branch and dispatch GitHub Actions for `feat/time-series-phase-a`.
- PHASE B posterior sampling, PHASE C Bayesian Local Projection rebuild, PHASE D Johansen/Bayesian VECM, and final UI work remain explicitly out of scope.

## Time-Series Analysis Phase A End-to-End Preparation Contract Correction

Cause
- Started from `feat/time-series-phase-a` at `291e968aaae7883c38af4076487e31480131dc52`.
- The final end-to-end PHASE A audit confirmed four remaining contract gaps:
  - automatic variable selection still happened before the final transformation plan;
  - common-row identity treated every metric too much like an undifferentiated response and was not role/horizon explicit enough;
  - slicing and compatibility scaling could bypass the validated prepared-data sample;
  - `PreparedMetricSeries` did not fully validate lifecycle metadata semantics against cell states.
- Existing dirty `outputs/*` files were preserved and were not staged or modified intentionally.

Changes
- Added immutable transformation-plan and prepared-candidate-catalog contracts.
- Changed `BayesianTimeSeriesAnalyzer` to derive the transformation plan before selector invocation, build the transformed prepared catalog once, and use that same transformed catalog for selector ranking and final model preparation.
- Changed inconclusive transformation handling: optional automatic candidates are excluded; required user-selected X/Y/Z metrics can use a documented level fallback without being labeled confirmed stationary.
- Added variable roles, row requirements, requested-horizon policy, and row-specification fingerprints.
- Changed automatic selector candidate comparisons to use the requested horizon and role-aware common source rows for both baseline and expanded systems.
- Removed the local-projection shallow slicing fallback.
- Changed BVAR/VECM compatibility scaling to calculate standardization parameters from the validated estimation/training rows only.
- Strengthened lifecycle metadata validation for structural zeros, pre-creation, not-applicable, version-discontinuity, observed values, conflicts, metadata range order, and deterministic range fingerprints.
- Extended static checks for selector ordering, horizon hard-coding, selector diagnostic recomputation, shallow LP slicing, and full-series standardization.
- Extended reference fixtures and docs for transformation planning, role-aware rows, training-only scaling, restricted-view identity, and lifecycle semantic validation.

Reason
- PHASE B must not select variables on level data while fitting differences, compare candidates over different samples, let controls unnecessarily shrink response rows, or scale covariance inputs on excluded/future rows.
- PHASE C must not let BVAR shock and BLP response paths use different transformed samples, stale sliced alignments, or horizon-1 candidate assumptions.
- PHASE D must not let lifecycle-inconsistent cells, version-discontinuity crossings, or stale restricted views enter cointegration matrices.

Result
- PHASE A now guarantees transformation-before-selection, one transformed prepared catalog for selector/final preparation, explicit variable-role row identity, requested-horizon propagation, exact common-row comparison, exact slicing, training-sample scaling, lifecycle semantic validation, and static safeguards against the known bypasses.
- PHASE B/C/D estimator work remains unstarted.
- No app version, tag, main merge, or UI work was performed.

Tests
- `.\gradlew.bat --version`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `python tools/time_series_reference/generate_phase_a_fixtures.py`: passed with bundled Python.
- `python tools/check_time_series_numeric_sources.py`: passed with bundled Python.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*TimeSeriesPreparedPipelineContractTest"`: initially failed on derived lifecycle discontinuity strictness, then passed after allowing source-derived lifecycle states.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*TimeSeries*"`: initially failed because an old fixture used a version-discontinuity observation without metadata, then passed after adding explicit lifecycle metadata to the fixture.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*TimeSeriesPreparedPipelineContractTest" --tests "*TimeSeriesCalendarGridTest" --tests "*LaggedTimeSeriesAnalyzerTest" --tests "*StableLinearAlgebraTest"`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.
- GitHub Actions: pending until this correction commit is pushed.

File/Feature Map
- `BayesianTimeSeriesModels.kt`: transformation plan/catalog models, variable-role row requirements, row-specification fingerprints, lifecycle semantic validation, metadata range fingerprint normalization.
- `BayesianTimeSeriesSupport.kt`: transformation-plan creation, transformed prepared-catalog creation, metadata-preserving exact restriction, plan-backed stationarization.
- `BayesianTimeSeriesAnalyzer.kt`: transformation-before-selection orchestration and final preparation from the same transformed catalog.
- `EndogenousVariableSelector.kt`: requested-horizon candidate scoring and role-aware common-row requirements.
- `BayesianLocalProjectionEstimator.kt`: exact validated rolling-fold slicing only.
- `BayesianDynamicEstimators.kt`: allowed-row/training-row-only scaling for compatibility BVAR/VECM paths.
- `TimeSeriesPreparedPipelineContractTest.kt`: regression coverage for transformation plan, shared transformed catalog, role-aware controls, horizon/role fingerprints, training-only scaling, and lifecycle semantic validation.
- `TimeSeriesCalendarGridTest.kt`: lifecycle metadata fixture updated for explicit version-discontinuity semantics.
- `tools/check_time_series_numeric_sources.py`: static guards for the end-to-end PHASE A bypasses.
- `tools/time_series_reference/*`: deterministic contract fixtures and README coverage for the new prepared-data contracts.
- `docs/bayesian_time_series_lab_architecture.md`: architecture, call-graph, file map, and downstream contract map update.

Downstream Contract Map
- PHASE B dependencies: one transformation plan before selection, role-aware common rows, requested-horizon fingerprints, common baseline/candidate source weeks, training-only scaling, lifecycle-consistent covariance inputs.
- PHASE C dependencies: exact restricted views, no shallow sliced alignments, no future-target requirements for contemporaneous controls, BVAR/BLP sample identity through row fingerprints.
- PHASE D dependencies: lifecycle metadata/cell-state consistency, preserved discontinuity provenance through transformations, no stale restricted lifecycle definition, and no lifecycle-prohibited cells in prepared systems.
- Prohibited legacy/raw paths: selector raw `TrendDataPoint` ranking, selector stationarity recomputation, horizon-1 candidate hard-coding, shallow `alignment.copy(...)` slicing, full-calendar standardization before allowed-row restriction, and transformed-series fallback to levels.

Remaining risks
- Later PHASE B/C/D still need real posterior estimators, fold-specific transformation policy decisions, and final UI integration.
- GitHub Actions run ID must be recorded after branch push.

## Time-Series Analysis PHASE A Single-Authority Architecture Closure

Cause
- Started from exact `feat/time-series-phase-a` baseline `e716163ef22fb0116725251179657233f7577e6f`.
- Calendar construction, lifecycle interpretation, integration diagnostics, transformation, estimator representation, row eligibility, scaling, response interpretation, shock identity, and compatibility estimation were still distributed across generic alignment helpers.
- Local guards could not prevent a later estimator from rebuilding an earlier decision or using a legacy score in a strict path.
- The approved pre-existing dirty `outputs/*` files were preserved, never modified intentionally, never staged, and never committed.

Changes
- Commit A `deddfc366e1da6e4ea054f65e3f1a3e398320e5d` (`refactor(analysis): separate strict and legacy time-series paths`): added the isolated `analysis.lab.pipeline` package, immutable raw/calendar/lifecycle/level stages, the sole raw ingestion boundary, strict preparation result types, strict dependency checks, and the explicitly named `LegacyTimeSeriesAnalyzer` compatibility facade.
- Commit B `5d6ffadd426596ac960953ff8ae91d610bf44723` (`refactor(analysis): add segment-aware representation contracts`): added exact contiguous-segment diagnostics, conservative integration aggregation, separate required/optional inconclusive policies, one canonical transformation authority, exact-once transformed series, BVAR/BLP/Johansen/VECM representation decisions, response-scale plans, and the multi-draw future shock-posterior contract.
- Commit C `73c0dbd6f9940b98b202354c816e7799adcd1e37` (`refactor(analysis): centralize prepared context row and scaling authority`): added `PreparedAnalysisContext`, eligibility-only candidate catalog, purpose-specific estimator views, explicit variable roles and horizon policies, the sole `RowPlanner`, the sole training-row `ScalingPlanner`, and validated future PHASE B/C/D input bundles.
- Commit D `pending (this commit)` (`refactor(analysis): close strict time-series preparation architecture`): added the production strict entry point, end-to-end preparation test, availability-end and deterministic conflict closure, legacy result disclosure, semantic static scans, updated contract fixtures, and the complete strict/legacy architecture and downstream responsibility maps.

Reason
- PHASE B-D need types that make raw data, stale fingerprints, locally selected rows, unrestricted scaling, transformed-level confusion, implicit shock differencing, and UI-defined response scales impossible to pass accidentally.
- PHASE E owns statistical optional-variable ranking; PHASE A now exposes eligibility and exclusion diagnostics only.
- A single root context is smaller and safer than continuing to add compatibility checks to every future estimator.

Result
- `StrictTimeSeriesPreparationPipeline` is the strict production entry and returns preparation readiness only.
- Raw `TrendDataPoint` ends in `StrictTimeSeriesIngestion.kt`; no strict downstream helper receives raw observations.
- One continuous ISO-Monday calendar and one lifecycle authority feed exact, non-concatenated segments.
- Required inconclusive X/Y/Z fails; optional inconclusive metrics are excluded without level/difference fallback.
- I(1) transforms once, while level and transformed series remain together under one root identity.
- BVAR, BLP, Johansen, and VECM representations are distinct and factory-created from the root context.
- Rows are role/horizon/purpose aware; contemporaneous controls do not require future targets.
- Scaling uses only declared training rows.
- Future BLP must accept a draw-specific `IdentifiedShockPosterior` and draw-wise response-scale policy; one deterministic mean shock is invalid.
- Legacy compatibility analysis remains isolated and explicitly labeled; no PHASE B/C/D/E estimator or final UI integration was started.

Tests
- Baseline `git fetch --all --tags`, branch switch/pull, exact HEAD check, status, log, and `git diff --name-only -- outputs`: passed; only six approved `outputs/*` paths were dirty.
- First Gradle command with Android Studio JBR and repo-local Gradle home, `.\gradlew.bat --version`: passed (Gradle 9.3.0, Java 21.0.10).
- Commit A gate: bundled-Python `tools/check_time_series_numeric_sources.py` passed; `:app:compileDebugKotlin` passed; `*StrictTimeSeriesArchitectureTest` initially exposed a Kotlin synthetic-constructor test assumption, then passed after excluding synthetic constructors.
- Commit B gate: `:app:compileDebugKotlin` passed; strict representation/architecture tests initially exposed an over-specific I0 fixture expectation, then all 10 tests passed with the conservative disagreement invariant.
- Commit C gate: `:app:compileDebugKotlin` passed; context tests first found a nonexistent fixture enum and then an intentionally excessive outlier that correctly made integration inconclusive; both fixtures were corrected and all 17 strict tests passed.
- Commit D fixture generation: bundled-Python `tools/time_series_reference/generate_phase_a_fixtures.py` passed.
- Commit D static scan: bundled-Python `tools/check_time_series_numeric_sources.py` passed.
- Commit D focused gate: `.\gradlew.bat :app:testDebugUnitTest --tests "*StrictTimeSeries*" --tests "*PreparedAnalysisContextContractTest" --tests "*TimeSeriesPreparedPipelineContractTest" --tests "*TimeSeriesCalendarGridTest" --tests "*LaggedTimeSeriesAnalyzerTest" --tests "*StableLinearAlgebraTest"` passed.
- Final `.\gradlew.bat :app:testDebugUnitTest`: passed.
- Final `.\gradlew.bat :app:compileDebugKotlin`: passed.
- Final `.\gradlew.bat :app:assembleDebug`: passed.
- GitHub Actions: pending until Commit D is pushed; final run ID, SHA, and result belong in the completion report.

File/Feature Map
- `analysis/lab/pipeline/StrictTimeSeriesStages.kt`: immutable lifecycle, calendar, level-series, request/result, and fingerprint foundation.
- `analysis/lab/pipeline/StrictTimeSeriesIngestion.kt`: raw observation boundary, canonical week/calendar construction, conflict/lifecycle derivation, and strict production entry.
- `analysis/lab/pipeline/StrictTimeSeriesDiagnostics.kt`: contiguous segments and segment-aware integration assessment authority.
- `analysis/lab/pipeline/StrictTimeSeriesRepresentation.kt`: inconclusive policies, transformation, transformed catalog, estimator representation, response scale, and future shock posterior.
- `analysis/lab/pipeline/PreparedAnalysisContext.kt`: root context and candidate eligibility catalog.
- `analysis/lab/pipeline/PreparedEstimatorViews.kt`: BVAR/BLP/Johansen/VECM/candidate read-only views.
- `analysis/lab/pipeline/PreparedRowAndScalingPlans.kt`: variable roles, horizon identity, row authority, and scaling authority.
- `analysis/lab/pipeline/FutureEstimatorBoundaries.kt`: validated future BVAR/BLP/Johansen/VECM input bundles without estimator math.
- `BayesianTimeSeriesAnalyzer.kt`, `LaggedTimeSeriesAnalyzer.kt`, `AnalysisLabUi.kt`: explicitly named legacy compatibility routing and disclosure.
- `StrictTimeSeriesArchitectureTest.kt`: raw/calendar/conflict/identity/package-boundary contracts.
- `StrictTimeSeriesRepresentationContractTest.kt`: segment/inconclusive/transformation/representation/response/shock contracts.
- `PreparedAnalysisContextContractTest.kt`: context/view/row/horizon/scaling/candidate-deferral contracts.
- `StrictTimeSeriesEndToEndTest.kt`: production-like raw-to-context closure across lifecycle states, I(0), I(1), optional inconclusive, multiple Y/Z, and horizons.
- `tools/check_time_series_numeric_sources.py`: numeric and strict semantic architecture scan.
- `tools/time_series_reference/*`: independent numeric fixtures plus explicit single-authority contract tables.
- `docs/bayesian_time_series_lab_architecture.md`: strict/legacy graphs, phase ownership, stage map, authority audit, and downstream contracts.

Remaining
- PHASE B: real NIW/Minnesota BVAR posterior and draw-specific structural shock identification.
- PHASE C: real Bayesian Local Projection with draw-wise shock propagation and response reconstruction.
- PHASE D: validated Johansen diagnostics, rank posterior, and Bayesian VECM.
- PHASE E: automatic endogenous-variable ranking, model comparison, and final UI labels/integration.
- None of those estimators or UI stages were started in this task.

## PHASE A final shock/revision identity closure

Cause
- Continued only on `feat/time-series-phase-a` from exact baseline `2269947fb0c34c2cf52528f4d99ea97e78c2d370`.
- The remaining PHASE A gaps were narrow: BVAR shock weeks still allowed caller-supplied subsets, shock posterior fingerprints still depended on accepted draw input order, and strict ingestion could still manufacture conflict cells from unresolved duplicate raw observations.
- Approved dirty `outputs/*` files were left untouched and unstaged.

Changes
- Commit pending (`fix(analysis): close strict shock and revision identity gaps`): made `BvarPosteriorSourceIdentity` require eligible source weeks to exactly equal `input.rowPlan.rows.map { it.sourceWeek }`.
- Canonicalized `IdentifiedShockPosterior` accepted draw ordering and rejected-diagnostic ordering before storing and fingerprinting.
- Added accepted/rejected draw ID disjointness and duplicate rejected-draw validation.
- Routed `RawTimeSeriesInput.fromTrendSeries` through the existing `TimeSeriesAlignmentService` resolver and added `fromResolvedAlignment` as the strict adapter.
- Changed raw strict ingestion to reject duplicate metric/week observations and to consume one resolved observation or explicit conflict result per metric/week.
- Preserved conflict provenance in strict provenance fields without adding a second resolver, RowPlanner, ScalingPlanner, context, pipeline layer, or estimator implementation.

Reason
- PHASE B/C estimators must not infer shock domains, posterior draw identity, or revision precedence outside the PHASE A authorities.
- Exact row-plan equality blocks fake or partial shock domains.
- Canonical draw identity makes semantic posterior identity independent of caller list ordering.
- Existing typed revision/conflict resolution remains the sole revision authority; strict ingestion is now only a consumer.

Result
- Caller-controlled shock-week subset/superset inputs are rejected.
- Shock posterior fingerprints are stable across accepted draw and rejected diagnostic permutation.
- Raw duplicate revision bypass is rejected at the strict boundary; resolver output and explicit conflict provenance survive strict ingestion.
- PHASE B, C, D, and E estimator implementation remains unstarted.

Tests
- `.\gradlew.bat --version`: passed with Android Studio JBR and repo-local `.gradle-user-home`.
- Bundled Python `tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StrictTimeSeriesRepresentationContractTest" --tests "*StrictTimeSeriesArchitectureTest" --tests "*StrictTimeSeriesIntegrationContractTest" --tests "*StrictTimeSeriesEndToEndTest"`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest`: passed.
- `.\gradlew.bat :app:assembleDebug`: passed.

Ponytail review
- Ponytail was available in `full` mode as an over-engineering guard.
- Review result: no new planner/resolver/context/pipeline abstraction was added; the fix reused existing PHASE A authorities.

Remaining
- PHASE B: actual BVAR posterior and structural shock estimation.
- PHASE C: actual BLP estimation using draw-by-draw shock posterior input.
- PHASE D: Johansen/VECM estimators.
- PHASE E: candidate ranking/model comparison/UI integration.

## PHASE A strict integration and input-contract stabilization

Cause
- Continued only on `feat/time-series-phase-a` from exact baseline `6fa92e3de21d8188ad33c8bc25b5e62ef7718878`.
- Segment diagnostics still used app-local ADF/KPSS thresholds and permissive short samples rather than the independent statsmodels reference contract.
- Required/optional inconclusive handling, explicit transformation mismatch, row horizon identity, and scaling edge cases needed fixed tests and source guards before posterior-boundary work.
- The approved dirty `outputs/*` files were left untouched and were not staged.

Changes
- Commit 1 pending (`fix(analysis): finalize strict integration and input contracts`): replaced legacy confirmed-status terminology with supported/inconclusive vocabulary, raised contiguous integration diagnostics to the 32-week minimum, and added statsmodels-compatible constant-only ADF/KPSS diagnostics with intermediate reference fields.
- Added OLS support to `StableLinearAlgebra` so the strict diagnostics can route numerical fitting through the approved wrapper.
- Made explicit transformation mismatches fail as `TRANSFORMATION_ASSESSMENT_CONFLICT` instead of falling through a documented fallback path.
- Restricted product horizons to 1..8, added explicit `HorizonPolicy.NOT_APPLICABLE` for no-horizon row plans, and removed zero/empty sentinel behavior.
- Replaced scaling clamping with declared failure modes for too-short, non-finite, indistinguishable, and near-constant training samples.
- Added `StrictTimeSeriesIntegrationContractTest`, updated strict context/representation/end-to-end tests, and added deterministic statsmodels reference fixtures plus `statsmodels==0.14.6` to the reference requirements.
- Updated `tools/check_time_series_numeric_sources.py`, `tools/time_series_reference/README.md`, and `docs/bayesian_time_series_lab_architecture.md` for the stricter PHASE A contracts.

Reason
- PHASE A must decide statistical support, transformation, rows, horizons, and scaling deterministically before PHASE B/C/D estimators exist.
- Required metrics should fail closed when inconclusive; optional metrics should be excluded without fallback modeling.
- No future estimator should be able to reintroduce fixed thresholds, horizon-zero sentinels, implicit transformation overrides, scaling clamps, or duplicate row/scaling authorities.

Result
- Strict integration support now matches deterministic statsmodels ADF/KPSS fixture statistics and preserves inconclusive/singular cases.
- Required and optional inconclusive behavior is fixed by contract tests.
- Row and scaling inputs have explicit strict identity and failure boundaries.
- PHASE B, C, D, and E estimator implementation remains unstarted.

Tests
- `.\gradlew.bat --version`: passed with Android Studio JBR and repo-local `.gradle-user-home`.
- Bundled Python `pip install statsmodels`: passed for reference tooling only.
- Bundled Python `tools/time_series_reference/generate_phase_a_fixtures.py`: passed; statsmodels emitted expected KPSS boundary warnings.
- Bundled Python `tools/check_time_series_numeric_sources.py`: passed.
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StrictTimeSeriesIntegrationContractTest" --tests "*StrictTimeSeriesRepresentationContractTest" --tests "*PreparedAnalysisContextContractTest" --tests "*StrictTimeSeriesEndToEndTest" --tests "*StrictTimeSeriesArchitectureTest"`: initially exposed outdated test fixtures and then passed after switching to deterministic integration fixtures.

## PHASE A posterior-boundary row-domain stabilization

Cause
- Continued on `feat/time-series-phase-a` after Commit 1 `83037b0`.
- `IdentifiedShockPosterior` still accepted shock series sized to the full canonical calendar rather than the prepared BVAR source-row domain.
- `FutureBlpInput` only checked same-root shock identity and did not verify source metric, response scale identity, BLP horizon policy, or shock-week coverage.
- The approved dirty `outputs/*` files remain untouched and unstaged.

Changes
- Commit 2 pending (`fix(analysis): bind shock posterior to prepared row identity`): added the single allowed production identity `BvarPosteriorSourceIdentity` in `FutureEstimatorBoundaries.kt`.
- Bound future BVAR posterior identity to source metric, BVAR view, BVAR row plan, scaling plan, prior fingerprint, BVAR input fingerprint, posterior fingerprint, and eligible source weeks.
- Changed `IdentifiedShockPosterior` to require draw-specific shock vectors over `BvarPosteriorSourceIdentity.eligibleSourceWeeks`, not the full calendar.
- Strengthened `FutureBlpInput.createValidated` to require BLP row/view identity, non-`NOT_APPLICABLE` horizon policy, matching shock source metric, matching response-scale fingerprints, and BLP source-week coverage by the shock posterior domain.
- Added focused posterior-boundary coverage to `StrictTimeSeriesRepresentationContractTest`.
- Updated architecture docs, README text, and static source guards for row-domain shock identity.

Reason
- PHASE C BLP must consume posterior shocks generated from the same strict root and eligible BVAR source rows.
- Full-calendar shock vectors allow fake zero/fallback shocks to line up visually while bypassing prepared row identity.
- Binding posterior shocks to the prepared row domain prevents future estimators from mixing BVAR and BLP samples silently.

Result
- Future shock posterior shape is now tied to prepared BVAR source rows and source identity.
- Future BLP input rejects wrong shock source metrics, incomplete shock-week coverage, response-scale identity drift, and invalid no-horizon policies.
- No PHASE B, C, D, or E estimator implementation was started.

Tests
- `.\gradlew.bat :app:compileDebugKotlin`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --tests "*StrictTimeSeriesRepresentationContractTest" --tests "*PreparedAnalysisContextContractTest" --tests "*StrictTimeSeriesIntegrationContractTest" --tests "*StrictTimeSeriesEndToEndTest" --tests "*StrictTimeSeriesArchitectureTest"`: passed.
- Bundled Python `tools/check_time_series_numeric_sources.py`: passed.
## Phase B1 lower-limb tissue rubric research infrastructure

Cause
- The tissue foundation had no populated rubric research ledger, and its sole Achilles preflight row paired the correct PMID/title with an unrelated or unresolved DOI.

Changes
- Commit `41c6209` (`fix(fatigue): repair tissue source verification and add research log`): preserved `PREFLIGHT_32658037`, replaced the incorrect DOI with the DOI parsed from official PubMed metadata, and recorded a matched Crossref verification artifact.
- Generalized `verify_tissue_sources.ps1` to process every registry row with parsed NCBI/Crossref comparisons, bounded retries, deterministic timestamps/hashes, PMID-only/DOI-only handling, and duplicate bibliographic identity detection.
- Added typed Phase B1 research-decision and target-exercise review schemas, parsers, contracts, validators, and empty ledgers ready for evidence population.
- Kept publication integrity at `STATUS_UNKNOWN`; no blind review, final claim, human approval, production profile, scope promotion, runtime integration, version, or tag was added.

Reason
- Bibliographic identity must be repaired before draft biomechanical claims are attached, while immutable source IDs and fail-closed production gates preserve provenance.

Result
- The corrected Achilles source is identity-verified but remains explicitly non-production.
- The next commits can record exactly 31 tissue/dimension decisions and 15 canonical exercise outcomes without overloading final-claim or production-profile fields.

Tests
- Official NCBI/Crossref live verification: passed for PMID `32658037` and DOI `10.1249/MSS.0000000000002459`.
- Focused Kotlin and offline artifact validation: passed.
## Phase B1 lower-limb draft evidence batch

Cause
- The verified registry still had no lower-limb primary-study batch, explicit tissue/dimension decisions, canonical exercise review outcomes, or condition-bounded draft claims.

Changes
- Commit `ae09c39` (`data(fatigue): add lower-limb tissue rubric evidence drafts`): added 10 officially identity-verified source rows, 12 condition-bounded draft claims, 31 research decisions, and 15 target-exercise review rows for batch `TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE`.
- Added a deterministic Phase B1 draft-asset generator and focused contract tests.
- Kept only Achilles peak tensile load and patellofemoral compression open as draft-rubric candidates; force, strain, translation, contact, impulse, stability, and energy outcomes were not silently pooled.
- Recorded `KNEE_ACL × VALGUS` as `OUT_OF_SCOPE_AFTER_AUDIT` because the requested research question is not an allowed dimension in the current canonical ACL catalog.

Reason
- Condition-specific model results can support draft anchors, but incompatible metrics and protocols must remain blocked or non-comparable instead of being converted into convenient bands.

Result
- All 31 targets and all 15 canonical exercises now have explicit, machine-validated research outcomes.
- Blind review, final claims, human approval, production profiles, and scope promotion remain empty.

Tests
- Official NCBI/Crossref live verification: 10/10 `PMID_AND_DOI_VERIFIED` and `MATCHED`.
- Focused `TissuePhaseB1ResearchTest`, `TissueEvidenceValidatorTest`, and `TissueMetadataFoundationTest`: passed.

## Phase B1 lower-limb draft rubrics and blind-review handoff

Cause
- Commit 2 left the two defensible tissue/dimension questions as researched draft candidates but had not yet created typed rubric rows, a historical evidence-batch audit, or a technically blinded Phase C handoff.

Changes
- Commit 3 (this commit, `data(fatigue): add lower-limb tissue rubric anchors`) adds five draft-only rubric rows: three within-study Achilles peak-tensile-load anchors and two source-index patellofemoral compression anchors.
- Expanded the rubric model/parser with typed status, assignment method, confidence, research-decision, draft-claim, preparer, blind-review, and human-approval fields.
- Added Phase B1 rubric validation for source/claim/decision identity, tissue/dimension consistency, complete anchor conditions, duplicate identities, invalid bounds, final-column contamination, prohibited approval, and target-exercise links.
- Updated all 15 exercise review outcomes to 6 direct anchors, 7 transfer references, and 2 no-comparable-source outcomes.
- Preserved the historical foundation audit row and appended `tissue_rubric_b1_73bbb560046d` with `PRODUCTION_REVIEW_REQUIRED`, 29 warnings, 0 failed invariants, and input hash `73bbb560046d5ee8c2da1305a0305929cbf609be8815d93edaf3897c4472f851`.
- Added `export_tissue_blind_review_package.ps1`, the Phase B1 report, and the updated foundation responsibility map.

Reason
- A partial source-specific rubric is defensible; a forced complete matrix is not. Missing bands and 29 non-rubric target decisions remain explicit instead of being converted into invented thresholds.
- Phase C reviewers need source locators and conditions without the proposed bands, values, confidence, assignment method, rationale, or research-use conclusion.

Result
- Phase B1 status is `RUBRIC_DRAFT_COMPLETE_PENDING_BLIND_REVIEW`.
- Draft claims: 12; draft rubric rows: 5; decisions: 31; reviewed exercises: 15.
- Production profile rows, production-eligible profiles, blind reviews, final claims, and human approvals remain 0.
- The 14,579-row scope manifest and all existing fatigue, OFI, readiness, ProgramBuilder, Room, backup, and Bayesian/time-series behavior remain unchanged.
- App version and release tags remain unchanged.

Validation
- Live official NCBI/Crossref verification: 10/10 `PMID_AND_DOI_VERIFIED` and `MATCHED`; two fixed-time runs and the committed artifact are byte-identical at SHA-256 `d78d5e3cfbd26f5e153fda06cd3cf6555fe9af7e60ebce7747ebc0a0afd63c59`.
- Foundation/record/B1 generators in a temporary directory: 61 tissues, 14,579 scope rows, two historical audit rows, deterministic on repeated B1 generation.
- Blind package: 12 items, 13 required fields, 0 forbidden fields, deterministic SHA-256 `c9c9463a7882929494919ed3a4ce3aab52d3c7f743f95c0fcd608526957e52e0`; proposed bands, values, and original rationale are absent.
- Focused `*Tissue*` unit tests: passed, including rejection of `APPROVED`, human approval, and draft IDs in final-claim columns.
- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest`: passed.
- `:app:assembleDebug`: passed.
- `git diff --check`: passed for intended files.

Phase C handoff
- Run `tools/export_tissue_blind_review_package.ps1` with an explicit temporary output path.
- Perform independent evidence assessment before creating blind-review rows, final claims, or human approval.
- Full 239-exercise profile backfill remains a later phase after Phase C approval.

## Phase C same-session lower-limb evidence re-audit

Cause
- Phase B1 had 12 condition-bounded draft claims and five draft rubric rows, but no second source-locator check, technical claim-candidate ledger, or machine-readable record of the two explicit user interpretation decisions.
- The same Codex session performed this work, so it could not honestly claim independent blind review.

Changes
- Commit `2cedc6b` (`feat(fatigue): add same-session tissue evidence reaudit`) added typed re-audit, claim-candidate, and user-adjudication schemas plus parsers, validators, deterministic IDs, contract tests, and the explicit `SAME_SESSION_EVIDENCE_REAUDIT` / `NOT_INDEPENDENT` boundary.
- Commit `4f62580` (`data(fatigue): reaudit lower-limb tissue evidence claims`) live-revalidated all 10 source identities, reopened every locator used by the 12 draft claims, and generated 12 non-production technical candidates without changing Phase B1 historical inputs.
- Commit 3 (`data(fatigue): adjudicate lower-limb tissue rubric candidates`) records exactly two user adjudications, re-audits all five current rubric rows, appends historical audit `tissue_reaudit_c_94c15f4d43e8`, and finalizes the Phase C report.
- The seated Achilles draft representation was corrected from a synthetic 0.6 BW point to the reported 0.5-0.7 BW range across bilateral and unilateral seated conditions with 15 kg on the thigh.
- The Achilles hop transfer remains `CLOSE_VARIANT` / `CLOSE_VARIANT_TRANSFER`; the PFJ bands explicitly use a 50% peak plus 50% impulse composite loading index as a compression proxy.

Result
- Completion state is `EVIDENCE_REAUDIT_COMPLETE_PENDING_BATCH_APPROVAL`.
- Claims: 12 re-audited, 12 candidates, 4 retained, 8 corrected, 0 blocked.
- Rubrics: 5 re-audited, 2 retained with limitations, 3 corrected, 0 blocked; `HIGH` remains intentionally undefined.
- User adjudications: 2 interpretation decisions, both `isBatchApproval=false` and `productionEligibilityEffect=NONE`.
- Blind reviews, formal final claims, human batch approvals, and production profile rows remain 0.
- Existing six-axis fatigue, OFI, readiness, ProgramBuilder, Room/backup, and Bayesian/time-series behavior remain unchanged.

Validation
- Live NCBI/Crossref source identity revalidation: 10/10 `PMID_AND_DOI_VERIFIED` and `MATCHED`; committed artifact remained byte-identical at SHA-256 `d78d5e3cfbd26f5e153fda06cd3cf6555fe9af7e60ebce7747ebc0a0afd63c59`.
- Focused `TissuePhaseCReauditTest`, `TissuePhaseB1ResearchTest`, and `TissueMetadataFoundationTest`: passed after Phase C adjudication/rubric generation.
- Combined Phase C input SHA-256: `94c15f4d43e843cd0238b1dd276d83e962bdf846a28a110330c5a970c0a64463`.
- Full compile, unit test, assemble, diff check, push, and GitHub Actions results are recorded in the completion report after they run.

## Phase C2A approval architecture and request package

Cause
- The formal validator supported only blind review, PFJ intervals were ambiguous at `0.333`, publication integrity was unknown, and no immutable human approval request bound the complete scientific scope.

Changes
- Commit `f13bdcd` adds explicit blind and same-session final-claim paths, expanded empty final/approval schemas, strict path validators, and focused regression tests.
- Commit `64af57a` adds decimal-safe rubric endpoint semantics, the 10-source publication-integrity artifact and live verifier, stricter future production gates, and focused boundary/integrity tests.
- Commit 3 (`data(fatigue): prepare lower-limb tissue approval package`) adds deterministic scope hashing and request/ingestion contracts, pending request `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196`, audit `tissue_approval_c2a_cbc8f749c37d`, the complete human-readable package, documentation, and final tests.
- The request scope is 12 candidates, five rubrics, 10 sources, and two interpretation adjudications at SHA-256 `9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36`.
- Current source and publication snapshots are `03216998a8d5dd728ae538fdeb771431d0af69d710d321ed267e5b1cd82b37e8` and `bde38622731a2919141bbed967cffe7121d89cfac30ae87f8834badef006825f`.

Reason
- Approval must be a human decision over exact immutable scientific data. A same-session technical re-audit, user interpretation adjudication, automated audit, casual continuation instruction, or AI-prepared package cannot substitute for that decision.

Result
- Completion status is `APPROVAL_PACKAGE_READY`.
- The request generator is byte-for-byte reproducible and prints the exact required approval statement.
- PFJ LOW is `(-infinity, 0.333]`; MODERATE is `(0.333, 0.667]`, using `BigDecimal` comparisons.
- All 10 sources have checked integrity rows; 10 have no adverse notice found, with zero corrections, retractions, expressions of concern, or unable-to-verify rows.
- Human approvals, formal final claims, blind reviews, and production profiles remain 0.
- Existing six-axis fatigue, OFI, readiness, ProgramBuilder, Room/backup, and Bayesian/time-series behavior are unchanged.

Validation
- Focused approval-request, final-claim path, rubric boundary, publication-integrity, historical Phase C, and validator tests passed.
- Deterministic approval generator repeated with byte-identical request, audit, and report outputs.
- `:app:compileDebugKotlin` passed; full `:app:testDebugUnitTest --rerun-tasks` passed 696 tests with 0 failures/errors/skips; `:app:assembleDebug` passed. Final diff check, push, and GitHub Actions are recorded in the completion report.

Next
- Await an explicit decision containing the exact request ID, scope hash, review path, candidate count, rubric count, and same-session limitation statement.
- Then ingest only that decision, promote only approved rows, and defer Phase D1 profile backfill until the formal final-claim gate passes.

## Phase C2A-R1 approval supersession

Cause
- Human review did not approve C2A request `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196` and identified condition-transfer, external-load, canonical-variant, and PFJ metric defects.

Changes
- Preserved the old request byte-for-byte and added append-only resolution `TISSUE_APPROVAL_RESOLUTION_C2A_R1_9D916660`.
- Added 12 explicit research directives, one per old claim candidate, without adding an approval row.
- Added explicit PFJ peak/impulse/loading-rate dimensions and a distinct patellar tendon strain dimension.
- Updated parsing, validation, ingestion rejection, tests, and the C2A-R1 research handoff.

Result
- The old request cannot be ingested when the resolution ledger is supplied.
- Human approvals, final claims, blind reviews, and production profiles remain zero.
- Revised evidence research is pending; no runtime or app-version behavior changed.

Validation
- `TissueApprovalSupersessionTest` and `TissueApprovalRequestTest`: passed.
- Full compile/test/assemble and CI: pending until the three-commit revision is complete.

## Phase C2A-R1 lower-limb evidence and rubric revision

Cause
- The superseded package merged PFJ peak and duration-sensitive metrics, overextended exact source tasks to generic app exercises, and lacked external-load evidence.

Changes
- Reworked 12 old candidates into 24 condition-bounded revised rows: six Achilles/patellar rows plus 18 PFJ peak, impulse, and loading-rate rows.
- Removed generic seated-calf LOW and both PFJ composite rubrics; retained only two narrow Achilles peak-load rubric candidates.
- Added 13 complete research-decision targets and explicit blocked statuses instead of inventing intervals or external-load multipliers.
- Generated an exact-stable-key audit of 49 canonical squat, lunge, calf, jump/hop/landing, and footwork exercises.
- Preserved the PFJ composite only as a non-production source-specific metric role.

Result
- Bodyweight evidence cannot populate weighted profiles; Bulgarian, RFESS, and split squat remain distinct; non-jumping lunge evidence cannot populate jump lunge.
- Human approvals, final claims, blind reviews, and production profiles remain zero.

Validation
- `TissueC2AR1EvidenceRevisionTest` and `TissueApprovalSupersessionTest`: passed.
- Canonical mapping audit regeneration was deterministic at SHA-256 `91C6EA3A4DBCCE3AF369F73C863906D04DAC374ABF298CF0B60EF8D1EF86D2AC`.
- Full Gradle validation and CI: pending until the revised approval package is complete.

## Phase C2A-R1 revised lower-limb approval package

Cause
- The corrected evidence, dispositions, and explicit blocks needed a new immutable scope; the superseded 12/5 request could not be reused.

Changes
- Added deterministic generator `generate_tissue_c2a_r1_approval_package.ps1`, revised request `TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD`, scope hash `74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f`, and append-only audit `tissue_research_c2a_r1_4b74d89f1410`.
- Added exact future approval wording, an explicit upper-limb B2 backlog, and package tests covering hashes, old-request immutability, empty promotion ledgers, partial status, and backlog scope.

Result
- Completion is `REVISED_APPROVAL_PACKAGE_PARTIAL`: 24 revised candidates and 2 rubrics are packaged, while 6 material research targets remain explicitly blocked.
- Human approvals, formal final claims, blind reviews, and production profiles remain zero; no version, runtime, or UI behavior changed.

Validation
- Generator second run: request byte-identical and audit idempotent.
- `TissueC2AR1ApprovalPackageTest`, `TissueC2AR1EvidenceRevisionTest`, and `TissueApprovalSupersessionTest`: passed.
- Live source verification: 10/10 identities verified; only the curated `verificationNotes` wording differed from the temporary live artifact.
- Live publication-integrity verification: 10/10 no adverse notice, 0 blockers; temporary artifact byte-matched the committed snapshot.
- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest --rerun-tasks`: 713 tests, 0 failures/errors/skips.
- `:app:assembleDebug`: passed.
- Initial GitHub Actions run `29337225710` exposed two platform-only immutability-test failures: Windows checked out the historical CSV with BOM/CRLF while Linux used LF. The scientific CSV content was unchanged.
- The old-request test now hashes UTF-8 text after removing an optional BOM and normalizing CRLF to LF; any other payload change still fails.
- Corrected focused tests: passed. Full rerun, follow-up push, and replacement CI: pending.

## Phase C3 multidimensional tissue-load reconstruction

Baseline
- Started from `b307fbd69319cf5c7dded24baedd6719befba71a` on current `main`.
- Preserved the six pre-existing dirty `outputs/*` files without staging, rewriting, or using them as destinations.
- App version and all runtime fatigue, OFI, readiness, ProgramBuilder, Room, backup, time-series, and UI paths remain unchanged.

### Commit 1 - `fix(fatigue): supersede revised tissue approval package`

Cause
- The unapproved C2A-R1 package combined mechanical mode and temporal exposure too broadly for a defensible approval.

Changes and result
- Added append-only resolution `TISSUE_APPROVAL_RESOLUTION_C3_MD_R1_74ECC664` while preserving request
  `TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD` and its normalized payload hash.
- Added ingestion rejection and immutable-request tests. Approval, final-claim, blind-review, and profile ledgers stayed empty.

### Commit 2 - `refactor(fatigue): add multidimensional tissue load ontology`

Cause
- A flat `loadDimension` could not distinguish peak, impulse, loading rate, strain, source-specific metrics, and cumulative dose.

Changes and result
- Added typed registries for 17 mechanical modes, 11 temporal metrics, 21 measurement metrics, 14 normalizations,
  42 valid tissue dimensions, and 33 legacy migrations.
- Added fail-closed parsing and validation without activating the model at runtime.

### Commit 3 - `data(fatigue): re-research multidimensional lower-limb tissue loads`

Cause
- Existing source measurements and all 24 revised candidates needed exact metric re-extraction and lower-limb gap research.

Changes and result
- Re-extracted all 10 existing sources, added 5 dual-verified primary sources, created 49 metric rows, 24 dispositions,
  30 condition-bounded candidates, 2 partial rubrics, 48 research decisions, and 49 transfer-correspondence rows.
- Added deterministic generation, compatibility checks, dose guards, and anomaly detection. Twenty-seven targets remain blocked.

### Commit 4 - `data(fatigue): prepare multidimensional tissue approval package`

Cause
- The rebuilt scientific scope needed a new immutable request rather than reuse of either superseded scope.

Changes and result
- Added deterministic generator `generate_tissue_c3_approval_package.ps1`, pending request
  `TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B`, scope hash
  `48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8`, and append-only audit
  `tissue_c3_md_a35cbac6c9d7`.
- Added a candidate-by-candidate human review table, metric-compatible rubric table, dimensional availability comparison,
  exact future statement, and upper-limb backlog `TISSUE_C3_MULTIDIMENSIONAL_UPPER_PRESS_PULL_R1`.
- Completion remains `MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL`. The statement is a template, not an approval.

File and feature map
- `TissueMultidimensionalModels/Parser/Validator`: ontology ownership and invalid-combination rejection.
- `tissue_*_registry_v1.csv`, `tissue_load_dimension_registry_v2.csv`: typed vocabularies and valid combinations.
- `tissue_load_dimension_migration_v1.csv`: explicit legacy disposition.
- `TissueC3ResearchModels/Parser/Validator`: extraction, candidate, rubric, decision, transfer, dose, and anomaly contracts.
- `tissue_source_metric_extraction_v1.csv`: source-observed metric rows with exact conditions.
- `tissue_evidence_claim_candidates_multidimensional_v1.csv`: non-production multidimensional candidates.
- `tissue_load_rubric_v2.csv`: two narrow, compatible, partial Achilles peak anchors.
- `tissue_research_decision_c3_v1.csv`: researched, incomparable, and blocked lower-limb targets.
- `tissue_review_batch_approval_request_c3_v1.csv`: exact pending scope and future decision template.
- `TissueC3ApprovalPackageTest`: scope determinism, immutability, partial audit, presentation, and empty-ledger gates.
- `tissue_load_phase_c3_upper_backlog.md`: separate upper-limb batch boundary.

Validation
- Generator idempotence: passed with byte-identical request, audit, backlog, approval document, and upper-limb document.
- Focused C2A-R1/C3 approval tests and the complete `*Tissue*` test set: passed.
- Compile, full unit rerun, assemble, diff check, push, and CI are recorded in the final completion report after execution.

## Phase C3.1 tissue ontology correction

Baseline
- Started from `883acc20934df290f9cba60adb397033b043a0c8` on current `main` and preserved the six existing dirty `outputs/*` files.
- No runtime, UI, database, backup, fatigue, OFI, readiness, ProgramBuilder, time-series, version, or tag path changed.

### Commit 1 - `fix(fatigue): supersede c3 tissue approval request` (`f40be6c`)
- Preserved the old C3 request and scope hash, appended `TISSUE_APPROVAL_RESOLUTION_C3_1_48F86FEE`, blocked ingestion, and kept all promotion ledgers empty.

### Commit 2 - `refactor(fatigue): separate tissue mechanics from event context` (`aa797c9`)
- Added 14 physical modes, typed context/phase/position/demand/response registries, ligament tension support, 39 corrected dimensions, and complete 42-dimension migration accounting.

### Commit 3 - `data(fatigue): correct tissue evidence load semantics` (`36b7fb7`)
- Reclassified ACL strain to tension without inventing direction, corrected weighted heel-rise added/total load, added explicit evidence relations, and accounted for all 188 affected historical identities.

### Commit 4 - `data(fatigue): prepare corrected c3 tissue approval package` (pending)
- Added two exact-condition anchors, zero interval/ordering rubrics, a deterministic replacement request and audit row, exact future statement, full human-readable review tables, and scope/immutability/no-interpolation tests.
- Completion remains `C3_1_ONTOLOGY_CORRECTION_PACKAGE_PARTIAL` because 27 research targets remain blocked. Human approvals, final claims, blind reviews, and production profiles remain zero.
- Replacement request: `TISSUE_APPROVAL_REQUEST_C3_1_A00141AC34448C59`; scope hash: `a00141ac34448c5904db5aff2a514c599b8d47582e0d5bbac6bb752a85bb3b06`.

File and feature map
- `TissueC31Models/Parser/Validator`: corrected ontology, evidence, guidance parsing, and fail-closed condition-anchor matching.
- `tissue_*_c3_1_v1.csv` plus typed context registries: non-production corrected scientific snapshots and complete dispositions.
- `generate_tissue_c3_1_*`: deterministic ontology, research, guidance, request, audit, and approval-document generation.
- `TissueC31*Test`: supersession, ontology, source correction, external-load, proxy, anchor, hash, and production-boundary regression gates.

Validation
- C3.1/C3 focused approval, ontology, research, supersession, determinism, and historical-scope tests: passed.
- Generator idempotence: passed for guidance, request, audit, and approval document with byte-identical second output.
- Live source identity: 15/15 verified. Live publication integrity: 15/15 no adverse notice, 0 blockers.
- `:app:compileDebugKotlin`: passed. `:app:testDebugUnitTest --rerun-tasks`: 748 tests, 0 failures/errors/skips. `:app:assembleDebug`: passed.
- Push and CI: pending final execution.

## Phase C4A M/T/C metadata foundation and research catalog

Baseline
- Started from `3bf12db97af0ce414552b1aa475d5d3081fd9e8b` on current `main`.
- Preserved the six pre-existing dirty `outputs/*` files without restoring, staging, or using them as generated destinations.
- No app version, release tag, runtime session calculation, historical session, database, backup, or UI behavior changed.

### Commit 1 - `fix(fatigue): supersede invalid c3.1 tissue approval package` (`17f5333`)
- Preserved request `TISSUE_APPROVAL_REQUEST_C3_1_A00141AC34448C59` and scope hash, appended resolution `TISSUE_APPROVAL_RESOLUTION_C4A_A00141AC`, and prevented future approval ingestion.

### Commit 2 - `refactor(fatigue): add mtc tissue metadata foundation` (`4e9a3ca`)
- Added nine functional complexes, 46 components, 48 base M/T/C metric rules, and three separate dynamic-stabilization profiles without modifying historical C3/C3.1 enums or registries.

### Commit 3 - `feat(fatigue): add axis rubrics and versioned coefficient sets` (`e0d582a`)
- Added 48 scales, 96 operational-only fallback rubrics, 48 axis-specific provenance rows, a six-level fallback ladder, and non-production coefficient set `TISSUE_MTC_C4A_0_1_0`.

### Commit 4 - `feat(fatigue): bridge tissue metadata to exercise catalog` (`3a1402a`)
- Audited 239 canonical exercises and 64 movement families; mapped 87 lower-limb exercises to 378 complex relationships with 1,134 non-null M/T/C traces under superseding draft coefficient set `TISSUE_MTC_C4A_0_1_1`.

### Commit 5 - `data(fatigue): add lower-limb mtc research seed catalog`
- Added 130 structured research leads; NCBI verified 97 bibliography rows, and 3 linked seeds already have metric extraction. Thirty discovery/KCI leads remain unverified.
- Added 49 explicit source conditions, 49 versioned corrected extractions, 30 exact-condition parity candidates, and 27 complex-axis research-gap rows.
- Added immutable non-production research manifest `TISSUE_MTC_C4A_0_1_2`, superseding `0.1.1` with the corrected source hash while leaving existing operational traces on `0.1.1`.
- Corrected hop-load contamination, average-versus-peak semantics, PCL modeled-force semantics, tibiofemoral resultant semantics, and empty-load rendering without mutating C3.1 history.
- Added a research planning package only. Human approvals, formal final claims, blind reviews, and production profiles remain zero.

Validation
- `TissueC4AResearchCatalogTest`: passed after correcting the fixture's Korean-source count from 11 to the actual 12; no production defect was found.
- Focused C4A and historical tissue tests, full compile/test/assemble, push, and CI are recorded after final execution.

## Phase C4B-1 continuous axis scoring and first M/T/C research batch

Baseline
- Started from `a714bb936405fd022c050b24511b8684653a34c7` on current `main` and preserved the six pre-existing dirty `outputs/*` files.
- No app version, tag, runtime fatigue calculation, historical session, database, backup, ProgramBuilder, time-series, or UI path changed.
- Completion is `C4B1_CONTINUOUS_AXIS_SCORING_AND_FIRST_RESEARCH_BATCH_PARTIAL`.

### Commit 1 - `fix(fatigue): normalize c4a research catalog semantics` (`5d16f4a`)
- Added an immutable 43-row C4B1 correction overlay instead of rewriting the C4A seed catalog.
- Rejected contradictory extracted statuses, typed model-validation-pending evidence, corrected three metric-extracted evidence relations, and retained research-ineligible fail-closed behavior.

### Commit 2 - `refactor(fatigue): add continuous axis scoring contracts` (`f349c27`)
- Added generic M/T/C/D/R/P continuous-score, evidence-key, provenance, confidence, and axis-specific similarity contracts.
- Added six distinct versioned similarity weight sets with minimum similarity `0.60`, dominant threshold `0.85`, and margin `0.15`.

### Commit 3 - `feat(fatigue): add metric rubrics and aggregation policy` (`dbcb1ec`)
- Added transparent piecewise scoring, explicit unclamped/clamped status, exact/dominant/aggregate selection, dependency weighting, and heterogeneity gates.
- Confidence remains separate from magnitude; UNKNOWN stays null; cross-axis and vector-wide averaging remain impossible.

### Commit 4 - `data(fatigue): extract first mtc research batch` (`f8545a5`)
- Reviewed four full-text primary sources and recorded 25 conditions, 89 raw observations, 69 calibrated scores, 56 decisions, and 33 exclusions.
- Added nine metric-specific rubrics and 36 anchors for Achilles, patellar-tendon, and PFJ M/T evidence.
- Preserved scalar C as ordering-only; deferred talocrural, TFJ, and quadriceps-tendon axes; retained PMID 31193251 lunge measurements as raw-only where no defensible four-anchor rubric exists.

### Commit 5 - `feat(fatigue): publish draft c4b1 coefficient set` (this commit)
- Added draft set `TISSUE_MTC_C4B1_0_2_0` with 14 canonical condition-bounded axes: 10 exact and 4 narrow lunge variants.
- Added a machine-readable C4A diff and semantic source/rubric/scoring hashes. All 1,134 C4A operational traces remain unchanged and all C4B1 publication rows are runtime-ineligible.
- Added coefficient publication, semantic-hash, C4A immutability, fallback, exact/variant, and no-false-zero tests.
- Added `docs/tissue_load_phase_c4b1_continuous_axis_scoring.md` with research boundaries, counts, source links, coefficient identity, file map, and deferred work.

Validation
- Focused C4A/C4B1 catalog, contract, scoring, research-batch, and coefficient-set tests: passed.
- `:app:compileDebugKotlin`: passed.
- Full `:app:testDebugUnitTest --rerun-tasks`: 791 tests, 0 failures/errors/skips.
- `:app:assembleDebug`: passed; debug APK created.
- Diff check: passed. Push and CI: pending final execution.

Remaining work
- Achilles T and scalar C remain unresolved.
- Talocrural, TFJ, and quadriceps-tendon M/T/C remain deferred.
- Loaded exercise variants and additional independent cohorts require exact-condition research before any broader transfer.
- Human approvals, formal final claims, blind reviews, and production profiles remain zero; runtime activation and historical recalculation remain false.

## RCV-ALL-0.6 Phase 1 unsided tissue-state identity

Baseline and package audit
- Started from latest `origin/main` at `586e43140ec13863410bfbb5d6f7c6de6a51cba6`; it contains package baseline `d3be2a9af81bc42b8733fd953cc2cdc770be186b`.
- Preserved the six pre-existing dirty `outputs/*` files without restoring, staging, or using them as generated destinations.
- Verified the manifest-listed Markdown files and the attached authority workbook by byte count and SHA-256.
- Authority workbook `전체239개_연결조직_MSCP_D_권위본_REL-MSCP-D-INTEGRATED-0.3_UNSIDED.xlsx`: SHA-256 `efa3f0c47c4f5bf0ae634ed7e8656162ac6552b7a86659da28096cc257c50144`; 239 exercises, 3,507 authoritative score rows, 77 load units, and one unresolved generic exercise.
- Required workbook `전체239개_MSCP-DI_회복프로토콜_RCV-ALL-0.6_UNSIDED.xlsx` is absent. The available RCV-ALL-0.5 workbook is explicitly superseded and was not used.

Implemented Phase 1
- Removed execution side from `TissueLoadKey`; tissue-state equality, aggregation, ordering, and snapshots now use only tissue class, tissue/load-unit ID, and load dimension.
- Removed `SIDE_UNRESOLVED` as a calculation blocker and removed side-resolution fields/gaps from derived exposure and snapshot models.
- Retained `performedSide` and side-allocation resolution only as optional execution dose/context diagnostics. Missing, left, and right execution context all accumulate into the same unsided state without a 50:50 split.
- Marked `lateralityCoverageStatus` as `NOT_APPLICABLE` in the committed audit manifest and deterministic generators while preserving the existing audit schema.
- Updated foundation documentation and regression tests for the corrected unsided invariant.

Scope held back
- No reviewed exercise-load rows were imported, rescored, or activated.
- No DI profile, recovery curve, routing, exact timestamp, immutable event ledger, aggregation/ranking service, Analysis UI, OFI summary, readiness, ProgramBuilder, database, backup, app version, or release tag changed.
- Phase 2 and later remain blocked until the exact manifest-listed RCV-ALL-0.6 recovery workbook is supplied.

Validation
- Pre-change `:app:testDebugUnitTest --tests "*Tissue*"`: passed.
- Focused `TissueExposureShadowPipelineTest`, `TissueRecordContractsTest`, and `TissueMetadataFoundationTest`: passed after the unsided refactor.
- Post-change `:app:testDebugUnitTest --tests "*Tissue*"`: passed.
- `:app:testDebugUnitTest`: 791 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug`: passed; debug APK generated.
- PowerShell parser validation for the three modified tissue generators: passed.

## v0.4.2.7 - RCV-ALL-0.6 Phases 2 through 5

Baseline and scope
- Continued from verified Phase 1 commit
  `b23c6ed3dd36dc7d6e4fd0de3075a2c517c130de`; `origin/main` remained its
  ancestor throughout implementation.
- Reused the existing RCV-ALL-0.6 handoff rather than creating a new audit.
- Preserved all six pre-existing dirty `outputs/*` files untouched and
  uncommitted.
- Used only the two authoritative unsided workbooks:
  `efa3f0c47c4f5bf0ae634ed7e8656162ac6552b7a86659da28096cc257c50144`
  and
  `beb599e6a53fec92f999d1174bbb35ed31092aff56ad90ee0149e61dd4c615c9`.

Phase 2 - `6356224 feat(tissue): import reviewed rcv all assets`
- Added deterministic OpenXML generation of 16 production CSV assets.
- Imported and validated 239 exercise mappings, 3,507 authority rows,
  50 protocol classes, 13 DI profiles, 21 curves/114 knots, 7 routing rows,
  15 joints, and 77 unsided load units.
- Preserved exactly one unresolved generic mapping.

Phase 3 - `1f409b6 feat(tissue): add immutable recovery event engine`
- Added nullable `WorkoutEntry.performedAt`, Room schema 19, and migration
  18 to 19. First confirmation sets exact time; legacy rows remain null.
- Added deterministic derived events, independent clocks, conservative
  date-only ranges, and bounded shape-preserving PCHIP.
- Calendar copies clear `performedAt` and never invent an observation time.

Phase 4 - `98a7126 feat(tissue): aggregate and rank connective tissue state`
- Added unsided load-unit/dimension residual sums, deterministic ranking,
  exact 1.5 contributor selection, joint max/worst summaries, duplicate event
  collapse, 56-day calibration, and symptom override.
- Confirmed all 15 joints and 77 load units remain visible and unsided.

Phase 5 - `3fb7a09 feat(tissue): add connective tissue analysis surface`
- Added read-only Room assembly, ViewModel state, the fourth Analysis
  destination, all-joint drill-down, contributors, recovery range/history,
  evidence/timestamp/calibration/symptom diagnostics.
- Added a separate worst-status/top-three summary and deep link to the OFI
  card. No connective-tissue value enters legacy OFI arithmetic.

Behavior boundaries
- Legacy OFI calculations, five axes, canonical messages, projected fatigue,
  readiness, ProgramBuilder, record mutation, and analysis engines are
  unchanged.
- Tissue identity, recovery clocks, state, ranking, and UI are unsided.
- Values describe modeled recovery/load state, not injury probability or
  diagnosis.

Validation
- Focused tissue asset/import, DI, event ledger, PCHIP, aggregation, ranking,
  UI mapping, and unsided tests: passed.
- Legacy OFI wording regression tests: passed.
- `:app:compileDebugKotlin`: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:testDebugUnitTest`: passed.
- `:app:assembleDebug`: passed; debug APK generated.
- Release target: `v0.4.2.7` / `402007`.
- Release commit, main push, and tag push: pending.

Remaining risks
- Legacy records remain date-only and use conservative residual ranges.
- The current symptom input is global rather than per tissue.
- Personal normalized status remains calibrating until 56 observation days.

## v0.4.2.8 - Connective-tissue UI separation and educational metadata

Baseline and scope
- Started from verified `origin/main` commit
  `9b2bb2bc257b44108f24a1208e6eb04b58a4684c` (`v0.4.2.7`).
- Preserved all six pre-existing user-owned `outputs/*` modifications without
  editing, staging, or committing them.
- Kept the RCV-ALL-0.6 numeric engine, OFI calculation, readiness,
  ProgramBuilder, record, backup, and import/export behavior out of scope.

### Commit 1 - `d6bf57e feat(tissue): add educational metadata catalog`
- Added deterministic educational metadata for exactly 15 joint complexes and
  77 unsided load units.
- Added typed parser/model support and fail-closed coverage validation for
  missing, blank, duplicate, unknown, or side-specific entries.
- Educational asset SHA-256:
  `8089265e3e436179f4bace5dbf7edd0e1ac4ace339e975fa7d3e41b5786d460e`.
- Deterministic regeneration and focused asset-import tests passed.

### Commit 2 - `2a71d71 fix(tissue): separate connective tissue from ofi card`
- Removed connective-tissue rows from the existing OFI state card.
- Added a separate connective-tissue summary/navigation card after the OFI
  cards, including the existing calibration state when data is insufficient.
- Preserved the five-axis OFI calculation, classifier, canonical Korean copy,
  axis distribution, and OFI contribution card.
- Focused OFI, Home summary, and tissue aggregation tests passed.

### Commit 3 - `55cf3cc feat(tissue): add ranked top three and info dialogs`
- Reused the immutable ranked result to show three complexes initially and all
  ranked complexes after expansion.
- Added saveable expansion and child-row state without duplicate sorting or
  recalculation in Compose.
- Added accessible information actions for every joint complex and child load
  unit. Dialog content comes from the generated metadata asset rather than
  composable hardcoding.
- Focused top-three, expansion, lookup, accessibility-description, and tissue
  aggregation tests passed.

### Release - `chore(release): bump version to v0.4.2.8`
- Updated `versionName` to `v0.4.2.8` and `versionCode` to `402008`.
- Added `docs/v0.4.2.8_release_notes.md`.
- Full `:app:testDebugUnitTest`: 816 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and
  `:app:assembleDebug`: passed.
- Main push, annotated tag, and GitHub Actions verification follow this release
  commit and are reported in the final task completion report.

Behavior boundaries
- Connective tissue is a separate analysis domain and is not an OFI axis.
- OFI still uses exactly five axes and unchanged calculations, thresholds,
  canonical labels, messages, and counts.
- RCV event derivation, PCHIP recovery, calibration, unsided aggregation, and
  ranking are unchanged.

Remaining limitations
- Educational descriptions are general, non-diagnostic information and do not
  define injury risk, safe limits, healing time, or treatment.
- Legacy date-only recovery ranges and the global symptom input retain their
  existing v0.4.2.7 limitations.

## v0.4.2.9 - Unified daily condition and LOCAL_MUSCLE presentation

Baseline and safety
- Started from `1090eb6a0d4beb144e7a3cefd369138c06b25fe9`, where local
  `main`, `origin/main`, and `v0.4.2.8` matched.
- Confirmed `origin/main` was an ancestor and there were no unfinished source
  changes from the preceding release.
- Left all six user-owned `outputs/*` modifications untouched and unstaged.

### Commit 1 - `c0da539 feat(condition): unify canonical daily condition data`
- Cause: Home wrote `DailyCheckIn`, while Record wrote `DailyMetric`, so the
  same local date could diverge.
- Made `DailyCheckIn` the canonical full daily-condition record and added
  nullable `bodyWeightKg: Double?`.
- Added Room schema 20 and migration 19 to 20. Later reliable `updatedAt`
  wins sleep conflicts; ties preserve the existing check-in and missing values
  are filled from the metric row. Historical weight is never fabricated.
- Kept `DailyMetric` as an atomic compatibility projection for established
  analysis and body-weight effective-load consumers.
- Routed restore and daily-timeseries writes through `DailyStatusService`.
- Backup round trips preserve decimal weight and older backups remain valid.
- Focused persistence/import/backup tests, Kotlin compile, and Android-test
  compile passed.

### Commit 2 - `a0810fe feat(condition): share daily condition editor across home and record`
- Reused one `DailyConditionEditorDialog` for both entry points.
- Home edits today; Record edits its selected `LocalDate`.
- Added `몸무게` with `kg`, comma/point decimal parsing, inline validation,
  accessible semantics, clearing, and `rememberSaveable` form state.
- Removed the Record-only `saveDailyMetric` ViewModel/repository API.
- Both screens observe the same canonical flow, so same-date changes propagate
  without restart and historical edits do not touch today.
- Shared-editor contract and focused storage tests passed.

### Commit 3 - `e2293cd fix(fatigue): show fatigued muscles for local axis`
- Reused readiness `highBodyParts` as the existing current muscle-fatigue
  authority; no UI-side fatigue algorithm was added.
- LOCAL_MUSCLE now shows `피로한 근육` and up to three canonical Korean muscle
  names ordered by score and stable key.
- Exact empty text: `두드러지게 피로한 근육이 없습니다.`
- Card title: `주의할 피로 축과 주요 내용`.
- Other OFI axes still show existing contributing exercises.
- OFI score/status, five-axis classifier, thresholds, canonical Korean
  messages, readiness, and recovery remain unchanged.

### Release - `chore(release): bump version to v0.4.2.9`
- Target: `versionName v0.4.2.9`, `versionCode 402009`.
- Full unit suite: 829 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and
  `:app:assembleDebug`: passed.
- Final release SHA is the commit referenced by `origin/main` and annotated
  tag `v0.4.2.9`; push/tag/CI status is reported in the completion report.

Behavior boundaries
- No new use of daily body weight was added to OFI, readiness, exercise volume,
  ProgramBuilder, calories, connective tissue, or recovery.
- Connective-tissue v0.4.2.8 calculation and UI contracts are unchanged.
- LOCAL_MUSCLE contribution data remains available to existing consumers even
  though its primary UI now shows muscles.

## v0.4.2.10 - Limited COD/deceleration context modifier C

Baseline and safety
- Started from `d5cebbaa6db2e1692c43f9ec7859f3571681ef0c`; local `main`,
  `origin/main`, and `v0.4.2.9` matched.
- Confirmed `origin/main` was an ancestor of HEAD and no unfinished source
  changes existed.
- Preserved all six user-owned `outputs/*` modifications without editing,
  staging, or committing them.

### Commit 1 - `6902ba5 feat(tissue): add bounded COD context authority`
- Added exact 22-exercise tiers: 9 at `1.09`, 6 at `1.06`, and 7 at `1.04`.
- Added a complete 77-row unsided load-unit eligibility authority with 35
  reviewed hip/knee/ankle/foot units eligible and 42 explicit neutral rows.
- Added 770 deterministic exercise/load-unit rules, a pure stable-key resolver,
  and strict count/factor/foreign-key/duplicate/provenance validation.
- The broad modifier schema remains empty and no display-name runtime matching
  was introduced.

### Commit 2 - `495546c fix(tissue): apply bounded COD context once per event`
- Changed event exposure from `(M / 10) x D x I` to
  `(M / 10) x D x I x C_resolved`.
- Resolves C once after the final exercise/load-unit event key is known.
- Exposes rule ID, resolution status, policy version, and neutral reason on
  each derived event.
- `mappingRoleWeight` remains `1.0`; S and P remain non-multiplicative.
- Exact fixture checks passed: `0.5232` at `1.09`, `0.5088` at `1.06`,
  `0.4992` at `1.04`, and `0.48` at `1.00`.
- Duplicate component-row coverage proved that C is multiplied only once.

### Commit 3 - `85e1252 docs(tissue): define bounded COD runtime contract`
- Updated the foundation contract with the 22-exercise whitelist, `1.09`
  approved maximum, `1.10` unused guardrail, tissue-specific scope, neutral
  mapping role, and product-policy evidence language.
- Documented that events and personal 56-day distributions are generated on
  read, so no Room migration or stale persisted tissue cache exists.

### Release - `chore(release): bump version to v0.4.2.10`
- Target: `versionName v0.4.2.10`, `versionCode 402010`.
- Added `docs/v0.4.2.10_release_notes.md`.
- Full `:app:testDebugUnitTest`: 841 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and
  `:app:assembleDebug`: passed.
- Push, annotated tag, and CI results are recorded in the final completion
  report after release verification.

Behavior boundaries
- No authority M/S/P value, dose normalization, effort selection, curve,
  recovery routing, calibration algorithm, OFI, readiness, ProgramBuilder,
  bodyweight volume, daily-condition, LOCAL_MUSCLE, UI, or backup behavior was
  changed.
- Upper-body, spinal, and pelvic load units always resolve C to `1.00`.
- Exact factors are bounded product policy, not literature effect sizes or
  injury predictions.

## Canonical Protocol Library - documentation governance

Baseline and safety
- Started from local and remote `main` SHA
  `06b65f6cdb243780e97a7464f659219b50010c7c` (`v0.4.2.10`).
- `origin/main` was an ancestor of HEAD and local main had no unfinished
  source changes.
- Application identity remains `versionName v0.4.2.10`,
  `versionCode 402010`; this documentation-only task does not create an app
  version bump or release tag.
- Preserved all six user-owned `outputs/*` modifications untouched, unstaged,
  and uncommitted.

Canonical library
- Added 6 protocol families and 28 registered canonical protocols.
- Added 28 canonical family documents, each with the required metadata table
  and 20 governed sections.
- Added one JSON registry, human indexes, a protocol template, evidence and
  implementation vocabularies, contribution guidance, change policy, and
  limitations/safety guidance.
- Added a 249-row legacy inventory covering 99 existing Markdown documents,
  149 existing metadata/seed assets, and one root ProgramBuilder specification.
- Historical handoffs, release notes, worklogs, research, evidence tables,
  authority assets, audits, and superseded designs remain in place and are not
  treated as current canonical authority.

Runtime audit results
- OFI documents record the active five-axis formula, classifier thresholds,
  exact Korean presentation messages, axis warning priority, decay behavior,
  and current LOCAL_MUSCLE detail.
- Connective-tissue documents record the active MSCP-DI exposure formula,
  exact current authority counts, independent event recovery, PCHIP curves,
  personal calibration, ranking, symptom overrides, and bounded COD modifier C.
- Badminton and strength documents record the current deterministic dose,
  transfer, bodyweight, duration-hold, taxonomy, and catalogue behavior.
- ProgramBuilder documents explicitly record that the public runtime is
  `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder`.
  `PROGRAM-BUILDER-SCORING` and `PROGRAM-BUILDER-CONSTRAINTS` are
  `PARTIALLY_IMPLEMENTED`; `PROGRAM-BUILDER-EVALUATION` is
  `SPECIFICATION_ONLY` because the public result has `evaluation = null`.
- Six implemented protocols use `UNKNOWN_PENDING_AUDIT` for their exact first
  app version rather than fabricating a Git-history date.

Validation and governance
- Added `scripts/validate_protocol_docs.py` using only the Python standard
  library. It checks registry JSON, IDs, statuses, semantic versions,
  documents, headings, metadata, anchors, assets, links, gaps, placeholders,
  unregistered canonical files, and contradictory implementation claims.
- Added protocol issue and pull-request templates.
- Added protocol validation and `git diff --check` before Android setup in the
  existing Android Debug Build workflow; Gradle behavior is unchanged.

Commits
- `64aa404 docs(protocols): establish canonical registry and governance`
- `0376021 docs(protocols): document OFI fatigue contracts`
- `3bc96ee docs(protocols): document connective tissue contracts`
- `ed451d5 docs(protocols): document training and builder contracts`
- `chore(docs): validate and index protocol library` (pending at log write)

Verification
- Bundled Python `scripts/validate_protocol_docs.py`: passed,
  6 families and 28 protocols.
- Documentation-scope `git diff --check`: passed.
- Workflow structure inspected; no extra dependency or Gradle coupling added.
- Android build intentionally not run because no Kotlin, resource, authority
  data, database, UI, calculation, metadata value, or runtime file changed.

Known unresolved documentation gaps
- Exact first app versions for BADMINTON taxonomy/volume/transfer and
  STRENGTH volume/bodyweight/duration-hold require Git-history audit.
- Advanced ProgramBuilder scoring, constraints, evaluation, and optimization
  classes remain disconnected from the public generator path.
- Some connective-tissue recovery classes and metadata rows use class-level or
  low-confidence proxies; the per-asset provenance remains authoritative.

## v0.4.2.11 - In-app first-user explanation flow

Baseline and safety
- Started from local and remote `main` SHA
  `497e5a00f2bc7116fcb4664894ecd0d8182e6311`, containing annotated release
  `v0.4.2.10`.
- Confirmed `origin/main` was an ancestor of HEAD and no unfinished source
  work preceded this task.
- Preserved all six user-owned `outputs/*` modifications untouched, unstaged,
  and uncommitted.

### Commit 1 - `22cdb60 feat(info): add in-app product explanation screens`
- Added the `app_explanation`, `analysis_guide`, and
  `calculation_principles` screen content using the existing Compose
  architecture.
- Added all approved Korean titles and paragraphs as Android string resources.
- Added one named public protocol URL and a system-browser launcher that
  handles unavailable browser activities without crashing.
- No analytical or persistence behavior changed.

### Commit 2 - `fa0b72a feat(home): add clean explanation entry and navigation`
- Added exactly one compact transparent row directly below
  `프로그램으로 시작하기`.
- Kept the row outside the initial-profile card with no subtitle, explanatory
  paragraph, banner, badge, modal, or duplicated Home entry.
- Added parent-aware explanation navigation and system-back behavior without
  adding a second navigation framework.

### Commit 3 - `abd9404 test(info): cover explanation flow and responsive layout`
- Added exact resource-copy, route, public-URL, source-boundary, and
  missing-browser tests.
- Added Robolectric Compose checks for the Home row, all three screens,
  navigation callbacks, 360dp one-line titles, 320dp reachability, font scale
  1.3, and light/dark themes.
- Reused the existing Robolectric stack instead of introducing a screenshot
  framework solely for these screens.
- Focused AppExplanation suite: 12 tests passed.

### Release - `chore(release): bump version to v0.4.2.11`
- Target: `versionName v0.4.2.11`, `versionCode 402011`.
- Added `docs/v0.4.2.11_release_notes.md` and linked the in-app explanation
  entry from the public protocol library README.
- Full `:app:testDebugUnitTest`: 853 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and
  `:app:assembleDebug`: passed; debug APK generated.
- Final commit, push, tag, and CI status are recorded after remote
  verification.

Behavior boundaries
- OFI, five fatigue axes, connective-tissue exposure/recovery, C modifiers,
  calibration, exercise metadata, badminton and strength volume,
  ProgramBuilder, daily-condition persistence, backup, database schema, and
  exercise catalogue behavior are unchanged.
- The Home screen gained one compact row only; detailed explanation remains on
  separate scrollable screens.

## Connective-tissue relative-load baseline Phase 1

Baseline and safety
- Started from completed local `main` SHA
  `8130e5e27bdf0674f9f2483b8a660363968925de`; it matched `origin/main`, and
  `origin/main` was confirmed as an ancestor.
- Application identity remains `versionName v0.4.2.11`,
  `versionCode 402011`. This is not a runtime feature release and creates no
  release tag.
- Preserved all six user-owned `outputs/*` modifications untouched, unstaged,
  and uncommitted.

Discovery and design lock
- Production calculation authority is
  `TissueRcvEventLedgerBuilder`/`tissueRcvInitialExposure` for M/D/I/C
  exposure and `TissueResidualCalculator` with
  `TissueRecoveryCurveRepository` for PCHIP recovery and residual load.
- The production asset identity is
  `RCV-ALL-0.6 | RCV-EXPOSURE-1.1`.
- Current authority contains 77 unsided load-unit stable keys, 239 exercises,
  3,507 exercise/load-unit rows, 21 recovery curves, 114 knots, and 7
  recovery routes.
- Runtime current-state evaluation uses exact event timestamps when available
  and the device local timezone. The generator preserves timestamp-compatible
  output as 24 local-hour buckets and uses `Asia/Seoul` only as its fixed
  deterministic simulation reference.
- Double-counting boundaries were locked before implementation: current
  session RPE/bodyweight remain production `CurrentLoad` inputs; habitual
  profile intensity affects only AdjustedPrior; future history affects only
  PersonalBaseline and `w_perUnit`; experience never changes exposure or
  recovery.

### Commit 1 - `df081c3 feat(tissue): generate deterministic prior baselines`
- Added a Kotlin/JVM offline generator under
  `tools/connective-tissue-prior/` and Gradle generate/validate commands.
- The generator directly reuses production event-ledger and recovery classes;
  no second exposure, recovery, interpolation, or residual implementation was
  added.
- Added 8 explicit weighted scenarios covering 2/3/4-day patterns, 112-day
  simulation, 56-day burn-in, and local hours 0..23.
- Added 13 explicit prior profiles and complete exact-one assignment for all
  77 stable keys. Missing mappings, duplicate authority, display-name fallback,
  and sided duplication fail generation.
- Generated 312 profile/hour buckets and 936 ordered quantiles. Canonical and
  app-ready registry SHA-256 is
  `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`.
- Recovery-engine fingerprint is
  `8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a`;
  deterministic output checksum is
  `5303516c1b972ce3bdf08eaffcd7c5fe6448bfa64c50abae9790d8d81af0c58e`.
- Added coverage, quantile, simulation, deterministic drift, non-consumption,
  and production parity tests.

### Commit 2 - `22e5177 feat(tissue): add bounded prior profile adjustment`
- Added the pure `TissuePriorAdjustment` evaluator with no repository, Android,
  UI, CurrentLoad, event-ledger, or recovery side effects.
- Uses one multiplier for Q30/Q80/Q95 and leaves `meaningfulFloor` unchanged.
- Missing input is neutral with provenance; invalid non-finite, negative
  bodyweight, and negative experience are rejected.
- Body-mass fitting over 50/65/75/90/105 kg found the current production
  normalization numerically neutral for all 13 profiles, so beta remains zero
  rather than forcing a false effect. Median/p95/max fit error is zero.
- Habitual intensity is simulation-fitted at LIGHT 0.96, NORMAL 1.00, HARD
  1.04; profile maximum fit error is at most 4%.
- Strength and racket experience use the exact five-band score and
  profile-specific 0..1 relevance with `POLICY_BOUNDED` domain effects of
  about +/-4%. Combined experience is limited to 0.94..1.06.
- Normal multiplier range is 0.85..1.15 and absolute hard boundary is
  0.80..1.20. Exhaustive supported profile/input-grid tests are finite,
  ordered, bounded, and deterministic.

### Commit 3 - protocol governance and Phase 1 boundary documentation
- Commit: `ec5afb9 docs(tissue): govern prior baseline phase one`.
- Expanded the existing `CT-PERSONAL-CALIBRATION` canonical document instead
  of creating a 29th canonical protocol document.
- Registered all nine Phase 1 and future downstream components with authority,
  inputs, outputs, provenance, policy status, schema/generator versions,
  checksum, limitations, dependency, and lifecycle.
- Updated `CT-MSCP-DI-EXPOSURE`, the protocol index, implementation status,
  safety limitations, machine registry, and documentation tests.
- Phase 1 lifecycle is explicitly
  `DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE`.

### Commit 4 - generated artifact file-checksum correction
- Commit: `577e2db fix(tissue): checksum generated registry file bytes`.
- Final verification found that the manifest hashed canonical JSON content
  before the writer appended its standard newline. Numerical content and
  deterministic output were unchanged, but the reported artifact SHA did not
  equal the committed file bytes.
- The generator now hashes the exact bytes it writes. Manifest, report,
  canonical documentation and machine registry use actual file SHA-256
  `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`.
- Added a regression test comparing manifest checksums with both committed
  canonical and app-ready file bytes.

### Commit 6 - cross-platform fingerprint normalization
- Commit: pending at worklog write.
- The first GitHub Actions run exposed a Windows/Linux drift false positive:
  production text inputs were fingerprinted as raw CRLF/LF bytes.
- Fingerprint inputs are now normalized to UTF-8 LF before hashing. Numerical
  baseline values are unchanged; source/mapping fingerprints and dependent
  checksums are now cross-platform deterministic.
- Added direct CRLF/LF parity coverage. The failed CI run was not accepted as
  completion and is superseded only after the follow-up run passes.

Runtime boundaries and remaining work
- Current `보정 중` UI and current classification are unchanged.
- The app-ready JSON is intentionally not read by any production caller.
- `PersonalBaseline`, seven-calendar-day exclusion, up-to-56-valid-day
  history, inactivity policy, `w_perUnit`, FinalBoundary mixing, and
  low/normal/high/very-high relative classification remain future work.
- OFI and its five axes were not changed.
- Scenario weights are product policy, not population prevalence. Experience
  is a bounded policy prior, not a measured biological capacity effect.
- Reference-zone simulation does not model DST transitions as separate
  scenarios.

Verification
- `.\gradlew.bat --version`: passed with Android Studio JBR and repository
  `.gradle-user-home`.
- `generateConnectiveTissuePriorBaselines`: passed repeatedly. Two final
  regenerations produced identical canonical, manifest, report, and app-ready
  file hashes.
- `validateConnectiveTissuePriorBaselines`: passed.
- Focused generator/parity/adjustment/documentation suite: 4 suites, all
  passed.
- Bundled Python `scripts/validate_protocol_docs.py`: passed with 6 families
  and the existing 28 protocols.
- Full `:app:testDebugUnitTest`: 876 tests, 0 failures, 0 errors, 0 skipped.
- `:app:compileDebugKotlin`: passed.
- `:app:assembleDebug`: passed; `app-debug.apk` generated.
- Final remote ancestry, push, and CI status are reported after remote
  verification.

## v0.4.2.12 connective-tissue relative baseline Phase 2

### Baseline and design lock
- Started from `main` at Phase 1 handoff commit
  `d83ae6441642fb5f8f7f41dc332d9c7ad9a225ad`; `HEAD` equals
  `origin/main`, and only the pre-existing user-owned `outputs/*` files are
  dirty.
- Re-ran `generateConnectiveTissuePriorBaselines` and
  `validateConnectiveTissuePriorBaselines`. Both passed and produced no
  tracked source or artifact diff. The handoff remains 77 load units, 13
  profiles, 24 local-hour buckets, 936 quantile values, registry file SHA-256
  `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`,
  recovery fingerprint
  `8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a`,
  and deterministic output checksum
  `5303516c1b972ce3bdf08eaffcd7c5fe6448bfa64c50abae9790d8d81af0c58e`.
- Current `CurrentLoad` rows are dimension-level
  `TissueRcvLoadKey(loadUnitStableKey, loadProfileP)` residual sums. The Phase
  1 asset instead maps each load-unit stable key to one aggregate prior
  profile, whose simulation sums production residuals. Phase 2 therefore
  uses one aggregate current/personal baseline identity per
  `loadUnitStableKey`; dimensions share that unit's calibration weight.
- Current `normalizedScore`/joint `displayScore` is a personal-history
  percentile. It is shown as a numeric score and is not compatible with the
  new mixed prior/personal boundaries, so Phase 2 replaces it with internal
  `relativeBandPosition` ranking semantics and removes the numeric UI score.
- Current `observationDays` runs from the first confirmed workout date to
  wall-clock today. History is gated globally at 56 days and reconstructs 56
  prior end-of-day samples. It has no check-in anchor, no recent-seven
  calendar exclusion, no global/unit gap segmentation, and no local-hour
  parity with the Phase 1 bucket.
- Runtime analysis uses an injected `ZoneId` defaulting to
  `ZoneId.systemDefault()`. Confirmed sets are the workout authority;
  planned/unconfirmed-only entries are already excluded. `DailyCheckIn` is a
  Room row keyed by ISO local date and will be read only as an anchor; it will
  not become an exposure or break a workout gap.
- The canonical body-weight resolver is
  `BodyweightEffectiveLoadCalculator.bodyWeightFor(date, dailyMetrics,
  initialProfile)`. Connective-tissue analysis currently reads only initial
  profile weight; Phase 2 will reuse the canonical resolver without applying
  profile multipliers to `CurrentLoad`.
- Separate numeric `strengthTrainingYears` and `badmintonTrainingYears`
  already exist in `InitialUserProfile` and its editor/backup path. A stable
  habitual intensity field does not exist and must be added with a nullable
  neutral default, explicit Room migration, backup compatibility, and the
  existing profile editor. Recent/session RPE will not be used as a substitute.
- No production prior loader exists. The app-ready JSON schema is `1.0.0`,
  protocol `RCV-ALL-0.6|RCV-EXPOSURE-1.1`, generator
  `CT-PRIOR-GENERATOR-1.0.0`; it contains stable-key assignments, 13 profile
  coefficient records, and ordered floor/Q30/Q80/Q95 values for every local
  hour. Runtime lookup will be stable-key only, with no display-name fallback
  and no generator/simulation code in the APK.
- Room is currently version 20. Profile backups are generic key/value rows;
  adding the one missing enum requires export/import mapping while older
  backups remain valid. Existing navigation, top-three expansion,
  educational dialogs, OFI, exposure/recovery authorities, contributor ratio,
  ProgramBuilder, and user-owned `outputs/*` remain outside this change.

### Phase 2 implementation commits

#### `1a4b058 feat(tissue): add calendar history segmentation`
- Added explicit chronological segments using the later of the last confirmed
  workout date and persisted DailyCheckIn date as the anchor.
- Excludes exactly A-6 through A, keeps A-7 eligible, and treats check-ins as
  anchor-only data that neither creates exposure nor breaks a workout gap.
- Implements global 6/7/13/14/27/28 and unit 13/14/27/28 boundaries,
  same-boundary minimum retention, distinct-boundary cumulative retention,
  local-date/DST traversal, and 56 weighted-day traversal.

#### `a44b379 feat(tissue): add per-unit calibration weights`
- Computes one weight per `loadUnitStableKey` and shares it across dimensions.
- Uses `w_span = days / 56`,
  `w_exposure = clamp((distinctExposureDays - 3) / 9, 0, 1)`, and their
  minimum. Same-day exercises, sets, sessions, and dimensions count once.

#### `c619396 feat(tissue): derive weighted personal baselines`
- Reconstructs historical residuals with the production calculator at the
  current local hour and prevents future-event leakage.
- Uses deterministic cumulative weighted nearest-rank Q30/Q80/Q95 over
  samples strictly above the unscaled meaningful floor.
- Empty, non-finite, degenerate, or identity-incompatible personal baselines
  remain invalid and fall back to prior rather than fabricating quantiles.

#### `b95a168 feat(tissue): activate prior mixing and relative states`
- Added the production prior registry parser and exact stable-key/local-hour
  lookup for the validated app-ready asset.
- Reused `TissuePriorAdjustment`, mixed prior/personal boundaries linearly,
  replaced the old percentile with internal `relativeBandPosition`, and
  classified exact floor/Q30/Q80/Q95 boundaries.
- `CurrentLoad` remains the production residual sum and is never multiplied
  by profile adjustment or calibration weight. Symptom overrides remain
  separate and unchanged.

#### `36f2f40 feat(profile): add tissue prior profile inputs`
- Added nullable `habitualTrainingIntensity` to the existing initial-profile
  authority and profile dialog using `LIGHT/NORMAL/HARD` stable values and
  `가벼운 편/보통/강한 편` resources.
- Added Room 20→21 migration/schema and backup export/import compatibility.
  Missing and unknown old-backup values resolve to neutral null without an
  onboarding loop. Existing strength and badminton/racket years and canonical
  bodyweight resolver are reused rather than duplicated.

#### `0974433 feat(tissue-ui): show relative states and provenance footer`
- Shows exact relative labels, removes normal `보정 중`, old percentile and
  `56일 보정 후 표시`, and keeps expansion/dialog/contributor behavior.
- Adds one low-emphasis final footer for prior-only, personal-only, or mixed
  source. Untouched catalogue units do not force mixed provenance.
- Compose coverage verifies exact strings, one final footer, large font, dark
  theme, expansion and educational dialogs.

#### `63af300 test(tissue): measure relative baseline analysis paths`
- Added a production-service/in-memory Room measurement that includes asset
  loading, event ledger, recovery, history, prior/personal mixing and
  aggregation for all 77 units.
- Local measured build times: short 10-record history 159 ms; approximately
  56-valid-day 71-record history 228 ms; 107-record history with multiple
  excluded gaps 217 ms. Timing is reported rather than used as a flaky CI
  ceiling; complete 77-unit output is asserted.

### Protocol activation
- Rewrote the existing `CT-PERSONAL-CALIBRATION` canonical document as
  protocol 2.0.0, including all 43 CurrentLoad/prior/personal/date/gap/weight/
  quantile/mixing/profile/provenance/safety concepts.
- Updated `CT-OVERVIEW` and `CT-RANKING-PRESENTATION` to 2.0.0, the protocol
  index, machine registry, implementation lifecycle and common safety limits.
- Registry lifecycle is now
  `DESIGNED / GENERATED / VALIDATED / RUNTIME_ACTIVE / TESTED` with the first
  runtime-active commit recorded as
  `b95a1684ad8bc0ba82cd5eae52ccb3147eae4d61`.
- Kept the existing 28 registered protocols and did not add a duplicate
  personal-calibration document.
- The first documentation-validator run correctly rejected the rewritten
  documents for not using the library's exact 20-heading schema and for
  referencing a not-yet-created release note. The documents were restructured
  without weakening the validator; bundled-Python validation now passes for 6
  families and 28 protocols. The focused documentation test also passes.

### Preserved boundaries
- OFI and its five axes, exposure M/S/P/D/I/C, bounded COD context, recovery
  curves/PCHIP, mappings, unsided identities, contributors, ProgramBuilder,
  strength/badminton volume and DailyCheckIn meaning remain unchanged.
- The generator remains offline; no simulation code or manually invented
  threshold entered the APK.
- All six pre-existing `outputs/*` modifications remain untouched, unstaged,
  and outside every commit.

### Release verification and lifecycle-test correction
- Focused Phase 2 verification covered 11 suites and 74 tests across calendar
  segmentation, per-unit weights, personal baselines, prior mixing,
  classification/ranking, profile UI, backup restore, Compose UI,
  documentation and production-service performance; all passed.
- The first full unit run completed 916 tests with one failure in the obsolete
  Phase 1 assertion that required the generated prior to have no runtime
  consumers. Commit `bbc4658` kept the generated artifact lifecycle and
  checksums immutable while constraining runtime consumption to exactly
  `TissuePriorRegistry.kt` and `TissueEffectiveBaselinePolicy.kt`.
- The corrected full run passed 916 tests with 0 failures, 0 errors and 0
  skipped. `compileDebugKotlin`, `compileDebugAndroidTestKotlin` and
  `assembleDebug` passed.
- The explicit Room 20→21 migration test ran on a headless Android 17 emulator
  and passed. Existing bodyweight, strength years and badminton years remain
  intact; habitual intensity starts as neutral null.
- Repeated prior generation and validation passed with no generated tracked
  drift. Bundled-Python protocol validation passed for 6 families and all 28
  protocols. Publication integrity checked 10 sources, found all 10 required
  no-adverse notices and reported 0 blockers.
- Latest local production-service measurements were 78 ms for 10 records,
  159 ms for approximately 56 valid days/71 records and 168 ms for a
  107-record history with multiple excluded gaps. These are observations, not
  machine-independent performance thresholds.
- Release identity is `v0.4.2.12 / 402012`. The release commit, main push, CI
  result and annotated tag are intentionally completed only after this local
  verification; their remote results are recorded in the final task report.

## v0.4.2.13 connective-tissue educational copy rewrite

### Baseline and scope
- Started from latest `origin/main` commit
  `7e2431539aa257ed0b89bf79c836ea80ec9f9942`, tagged `v0.4.2.12`, with app
  identity `v0.4.2.12 / 402012`.
- Derived the real inventory from runtime assets: 15 `JOINT_COMPLEX`, 77
  `LOAD_UNIT`, 92 total unique educational entries, with complete parent-child
  mapping and no missing or extra key.
- Kept this change presentation-only. OFI, M/S/P/D/I/C, event exposure,
  recovery/PCHIP, prior registry, PersonalBaseline, `w_perUnit`, canonical
  state, contributor ranking, ProgramBuilder, stable keys and hierarchy were
  not edited.

### Child-first copy and parent synthesis
- Commit `879614f` rewrote every one of the 77 child load-unit entries
  individually. Locations begin with practical body landmarks; functions use
  tissue-appropriate verbs; movement contexts describe representative
  exercises and a structure-specific condition that can increase load.
- Commit `07edd83` then synthesized all 15 parent joint-complex entries from
  their actual completed child sets. Parent text describes the whole region
  and is neither a child list nor copied child prose.
- All 92 entries now use `RCV-ALL-0.6-EDU-2`. The educational CSV checksum is
  `c7b458ec4f84ef9cd6603d5ee786011358ebe4f535be7d9d91bccf56738f6d1b`.
  `shortDescriptionKo` remains schema-compatible and internal but is blank in
  the final authority.
- Explicit injury-mechanism wording is used in 0 entries. Every entry uses
  conservative relative-load wording and does not infer injury probability.

### Dialog and executable contract
- Commit `648b00c` kept one existing information dialog and exactly the three
  headings `위치`, `주요 기능`, `주로 사용되는 동작`, followed by the
  existing disclaimer. Duplicate display-name/description rows and middle-dot
  sentence joins were removed; long copy is vertically scrollable.
- Commit `f2479c8` preserved `metadataVersion` in the model/parser and extended
  the existing runtime validator for EDU-2 consistency, complete coverage,
  canonical names, natural sentence endings, a documented 1,000-character
  hard field limit, obsolete boilerplate and exact complete-entry duplicates.
- Added `TissueEducationalCopyContractTest` for 92-row coverage, hierarchy,
  report synchronization, validator rejection paths and exact numerical
  authority fingerprints. `ConnectiveTissueAnalysisUiTest` now covers the
  three-field dialog, parent/child buttons and large-font scrolling.
- Added the supporting review
  `docs/reviews/connective_tissue_educational_copy_review_v2.md`: all 77
  children appear before their mapped parent in 15 groups, and all 92 entries
  record provenance, injury-wording decision and semantic review result. It is
  supporting material, not a 29th canonical protocol.

### Verification so far
- `TissueRcvAssetImportTest`: passed after child and parent rewrites.
- `TissueEducationalCopyContractTest`, `TissueRcvAssetImportTest` and
  `ConnectiveTissueAnalysisUiTest`: 19 focused tests passed together.
- The expanded copy/UI focused run passed 16 tests after adding negative
  validator cases. One intermediate test compile used UI-model property names
  against the domain fixture, and one child-title assertion saw both the row
  and dialog title; both test-fixture issues were corrected without changing
  production behavior.
- Numerical authority SHA-256 values remain exactly:
  prior `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`,
  exercise-load authority
  `7efd022c6b7b1ec3b927bfd81b61c6ac5195425da8b7f2e607b057f2ee529ac5`,
  recovery knots
  `0282bcf10426dfea744aa20aa3500cac960ad43950e4c057ab05dfa0b9311837`,
  and load units
  `36ace1900713e91301d67c5375db80a951c51dcd34d9476ee75eae0e5a2f371`.
- Baseline debug APK size was 46,172,499 bytes. Final APK size, full Gradle
  verification, push, CI and annotated tag are recorded after release
  verification.
- The six pre-existing user-owned `outputs/*` modifications remain untouched
  and unstaged.

### Final local verification and release identity
- Focused educational/parser/UI and numerical connective-tissue regression
  covered 75 tests across 11 suites; all passed.
- Full `:app:testDebugUnitTest` covered 923 tests in 156 suites with 0
  failures, 0 errors and 0 skipped.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` and
  `:app:assembleDebug` passed with Android Studio JBR and the repository-local
  `.gradle-user-home`.
- `validateConnectiveTissuePriorBaselines` passed. Asset manifest/hash checks
  passed. Bundled-Python protocol validation passed for 6 families and the
  unchanged 28 protocols. Publication integrity checked 10 sources, found all
  10 required no-adverse notices and reported 0 blockers.
- Final debug APK size is 46,182,271 bytes, +9,772 bytes (+0.021%) from the
  46,172,499-byte v0.4.2.12 baseline. No image, SVG, audio, video, font,
  external library or other binary asset was added.
- Release identity is `v0.4.2.13 / 402013`. The release commit is the commit
  containing this final verification record; push, CI and annotated-tag
  results are recorded in the final task report.

## v0.4.2.14 connective-tissue diagnostics and imported badminton hotfix

### Baseline and cause
- Started from latest `origin/main` at
  `934c6cc85b3e9f8e3d807780687d4a329261ece7`, tagged `v0.4.2.13`, with app
  identity `v0.4.2.13 / 402013`.
- `TissueAnalysisUiMapper` copied domain diagnostics into user-facing state,
  and Compose rendered both child diagnostics and a gray diagnostic card.
- Restore rows with the explicit legacy key `imported_배드민턴` bypassed the
  existing reviewed `ex_ae9ecdbc` badminton authority.

### Hotfix
- Commit `9b9124d1d9cd5b6fcdd42a7578d2295f991bbe1b` removes diagnostics from the
  connective-tissue presentation model and rendering while retaining them in
  domain state for logs and tests. The baseline-source footer remains.
- `BackupRestoreImportService` now canonicalizes only
  `imported_배드민턴 -> ex_ae9ecdbc` for restored exercise, set and runtime
  metadata rows. It does not perform display-name matching and creates no new
  tissue mapping.
- The integrated Room regression proves the restored entry uses the canonical
  exercise, reaches the complete existing badminton authority-key set, keeps
  the 45-minute duration and RPE 8 effort, and leaves event repetitions at
  zero.

### Verification and release
- Focused `BackupRestoreImportBehaviorTest` and
  `ConnectiveTissueAnalysisUiTest` covered 18 tests with 0 failures.
- Full `:app:testDebugUnitTest` covered 924 tests across 156 suites with 0
  failures, errors or skipped tests. `:app:compileDebugKotlin` and
  `:app:assembleDebug` passed.
- `validateConnectiveTissuePriorBaselines` passed. Protocol documentation
  validation passed for 6 families and the unchanged 28 protocols.
- Push, GitHub Actions and final tag are completed before the release is
  reported.
- Release identity is `v0.4.2.14 / 402014`.
- The six pre-existing user-owned `outputs/*` modifications remain untouched
  and unstaged.

## v0.4.2.15 canonical OFI five-axis correction

### Baseline and cause
- Started from latest `origin/main` at
  `dbad9fe02d1a6e709c59c65149ba5ce7c9be7bf5`, app identity
  `v0.4.2.14 / 402014`.
- OFI had regressed to legacy `관절·건·충격` and `동작·집중` axes even though
  connective-tissue load/recovery is a separate 77-unit protocol.

### Calculation and presentation correction
- Commit `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` restores exactly
  `고중량·힘 신경계`, `전신 근육`, `국소 근육`, `고속`, `반응` in the OFI state,
  aggregation, classifier, warnings, Home/Analysis summaries and UI targets.
- `HIGH_SPEED` derives from speed, sprint, plyometric/SSC, acceleration,
  first-step, court-movement and footwork metadata plus duration/RPE workload.
- `REACTIVE` independently derives from reaction, decision, visual tracking,
  direction change, random/beep, reactive agility, footwork, agility, shuttle
  and court-movement metadata.
- Canonical badminton metadata `ex_ae9ecdbc` now has regression coverage for
  non-zero systemic, high-speed and reactive contributions and independent
  variation of the two new axes.
- `JOINT_TENDON_CAUTION` is removed from OFI. Joint/tendon coaching remains
  discomfort-driven and separate from OFI.

### Preserved boundaries
- Connective-tissue mappings, exposure/recovery math, 77 load units, priors,
  `PersonalBaseline`, `w_perUnit`, classifications and educational copy are
  unchanged. The fixed tissue fixture remains raw `100.0`, adjusted `120.0`.
- Strength/badminton volume protocols, ProgramBuilder and exercise stable keys
  are unchanged.
- Existing OFI protocol documents were updated in place to `1.1.0`; no
  duplicate protocol document was added.

### Verification and release
- Focused OFI/Home/Analysis/readiness/tissue tests passed.
- Full `:app:testDebugUnitTest` produced 928 tests across 156 suites with zero
  failures, errors or skipped tests.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` and
  `:app:assembleDebug` passed. The debug APK is 46,182,267 bytes.
- `:app:validateConnectiveTissuePriorBaselines` passed. Protocol documentation
  validation passed for 6 families and 28 protocols. Publication integrity
  checked 10 sources, found all 10 required no-adverse notices and reported 0
  blockers.
- Push, GitHub Actions and the final annotated tag are completed before the
  release is reported.
- Release identity is `v0.4.2.15 / 402015`.
- The six pre-existing user-owned `outputs/*` modifications remain untouched
  and unstaged.

## v0.4.2.16 analysis chart temporal context and e1RM domain correction

### Baseline and cause
- Started from latest `origin/main` at
  `5eac6594b97504a9cacc87f478d7921e8fbad599`, tagged `v0.4.2.15`, with app
  identity `v0.4.2.15 / 402015`.
- Daily and weekly analysis charts lacked enough date context. Weekly
  badminton volume and transfer views could format the same bucket
  independently.
- The card titled `이번 주 누적 부담` actually used the selected rolling
  fatigue-analysis window, so its title implied a different period.
- `AnalysisTrendChart` used the first e1RM series length for horizontal
  positions. One bench observation could therefore truncate longer squat and
  deadlift histories.

### Commits and implementation
- `e65722fba9c54fd8dba529ebf3d3c09ac3535e98` adds
  `AnalysisChartTemporalPolicy`, the single Monday-Sunday authority. Thursday's
  month owns a week, owned weeks are numbered chronologically, year boundaries
  stay unambiguous, and label thinning never removes data points.
- `c8ce0f9aeb40982e818c932e413a77024a453b5b` makes the e1RM X domain the
  complete union between the earliest and latest displayed exercise
  observations. Missing weeks remain absent, a one-point series remains one
  point, and the Y range uses every finite visible value.
- `9dced078349cea2c49658401d16bbf58f2d830bf` adds visible date/week axes,
  exact period subtitles and temporal accessibility descriptions. Badminton
  weekly volume and transfer stimulus share one `weekStart` formatter.
- The rolling fatigue card is now `누적 부담 흐름`; it displays the exact
  selected start/end dates and preserves every OFI date and value.
- `60e21c6b847f1dc2910ddbdc5ee2d4690631cb9e` adds focused daily, weekly,
  badminton, fatigue, strength and accessibility regression tests.
- `095a68f` updates the existing analysis and protocol documents in place. No
  canonical protocol was added; the library remains 6 families and 28
  protocols.

### Preserved boundaries
- Badminton volume/transfer values, taxonomy and colors are unchanged.
- OFI formulas, recovery, five-axis state and classification are unchanged.
- e1RM formula and weekly maximum selection are unchanged.
- Strength effective volume, muscle mappings, rep-range definitions and ratio
  calculations are unchanged.
- Connective-tissue analysis, ProgramBuilder, workout records and stable keys
  are unchanged.

### Verification and release
- Focused chart/date/e1RM/fatigue coverage: 65 tests, all passed.
- Full `:app:testDebugUnitTest`: 946 tests across 159 suites with zero
  failures, errors or skipped tests.
- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` and
  `:app:assembleDebug` passed.
- `:app:validateConnectiveTissuePriorBaselines` passed.
- Protocol documentation validation passed for 6 families and the unchanged
  28 protocols. `git diff --check` passed.
- Release identity is `v0.4.2.16 / 402016`.
- Main push, GitHub Actions and annotated tag are completed before release is
  reported.
- The six pre-existing user-owned `outputs/*` modifications remain untouched
  and unstaged.

## v0.5.0.0 quiet UI refinement

### Baseline and intent
- Started from latest `origin/main` at
  `7105d96d2209d30e0c1bb8c6a97a5c8e3b318243`, tagged `v0.4.2.16`, with app
  identity `v0.4.2.16 / 402016`.
- The release is a product presentation milestone: quieter navigation,
  shallower surface hierarchy, a calmer OFI default summary and shared visual
  primitives. It does not change calculations or add product features.
- Baseline debug APK size was `46,198,651` bytes.

### Implementation commits
- `483d6b79600c27f7c3893429b6cd0aaf16374978` replaces `H/R/P/E/A` with
  outlined Material home, edit-note, event-note, fitness-center and analytics
  icons. Korean labels, one selected indicator and neutral unselected states
  remain visible.
- `034a1e51739aa76343ff7056675a73d97a0438ca` groups Analysis entries into
  row-based sections and removes nested filled surfaces from Home metrics,
  fatigue/recognition detail, connective-tissue child rows and expanded
  performance detail.
- `e08a46846c0f80982c4dbe2b58bd7e1d8c7ec912` places the canonical five OFI
  axes directly below the overall state. Ordinary states are neutral; only
  high and very-high states use strong warning emphasis. Contributor details
  remain behind the existing detail action.
- `7c29fa80b31fad642273e0a3ec5924109dafac21` centralizes the neutral
  light/dark palette, typography weights and shape scale, aligns major-screen
  spacing, converts helper cards to unframed text and unifies analysis/fatigue
  selectors on Material `FilterChip`.

### Files and responsibility
- `MainActivity.kt`: bottom navigation mapping, selected/unselected treatment.
- `CommonUi.kt`: helper text, compact metrics, shared choice chip and screen
  insets.
- `HomeScreen.kt`: primary/secondary action hierarchy and compact summary.
- `AnalysisScreen.kt`, `AnalysisHubUi.kt`, `AnalysisDetailScreens.kt`,
  `AnalysisCoachUi.kt`, `AnalysisChartUi.kt`: quieter analysis hierarchy,
  grouped entry rows and canonical OFI summary.
- `ConnectiveTissueAnalysisUi.kt`: row/divider child presentation; calculations,
  educational dialogs and baseline-source footer remain unchanged.
- `ui/theme/Theme.kt`: neutral palette, typography and shapes.
- New UI tests: `BottomNavigationUiTest`, `AnalysisHubUiTest` and
  `CurrentFatigueStatusCardUiTest`.

### Governance and preserved boundaries
- Added one canonical `UI-QUIET-PRESENTATION` product policy and registered it
  in the existing protocol library.
- Updated `OFI-CLASSIFICATION` in place to `1.2.0`; no duplicate OFI authority
  was created.
- No images, illustrations, anatomy diagrams, banners, photos or animation
  assets were added.
- OFI, connective-tissue, strength, badminton, ProgramBuilder, record,
  backup/restore and stable-key behavior are unchanged.

### Verification status
- Focused navigation, Home entry, Analysis hub, OFI label/summary,
  connective-tissue and dark/large-font Compose coverage passed.
- `:app:compileDebugKotlin` passed after each implementation stage.
- Final full unit tests, Android-test compilation, assemble, protocol
  validation, APK size, release commit, main push, CI and tag are recorded
  after the release gate completes.
- The six pre-existing user-owned `outputs/*` modifications remain untouched
  and unstaged.
