# Codex Worklog

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
