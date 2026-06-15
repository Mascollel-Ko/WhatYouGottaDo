# WhatYouGottaTrain Phase 2.5 Handoff

작성일: 2026-06-14  
목적: 다음 작업자가 현재 앱 구조, 데이터 의미, 빌드 상태, 주의점을 빠르게 이어받도록 전달한다.

## 필수 인수인계 규칙

앞으로 앱 기능이나 데이터 구조가 바뀌면 다음 문서를 함께 갱신한다.

- `outputs/phase2_work_readme.md`
- `outputs/phase2_handoff.md`
- `outputs/phase2_data_meaning_mapping.md`
- 분석 설계 변경 시 `docs/analysis_algorithm_design.md`

문서를 뒤에 미루면 다음 작업자가 코드 의미를 잘못 판단할 수 있다. 특히 `WorkoutSet.confirmed` 의미와 seed 전략은 반드시 최신 상태로 유지한다.

## 현재 상태 요약

Phase 2.5 완료 상태다.

완료된 것:

- 앱 빌드 성공
- 앱 이름 `WhatYouGottaTrain`
- `applicationId = com.whatyougottatrain.app`
- `MainActivity.kt` 화면 분리
- 프로그램 적용 덮어쓰기 UX 보강
- `app_meta` 기반 seed version 전략 최소 구현
- debug DB summary 로그 추가
- 고급 분석 구현 금지 유지
- 분석 알고리듬 설계 문서 작성

## 빌드 환경

프로젝트 위치:

```text
C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme
```

빌드 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat assembleDebug
```

최종 결과:

```text
BUILD SUCCESSFUL
```

참고:

- `local.properties`는 workspace 내부 Android SDK를 가리킨다.
- sandbox 안에서는 Windows zipfs 접근 문제로 KAPT가 실패할 수 있다.
- 같은 명령을 권한 밖에서 실행하면 빌드가 성공했다.

## 절대 바꾸면 안 되는 데이터 의미

```text
WorkoutSet.confirmed = false : 계획 세트
WorkoutSet.confirmed = true  : 실제 수행 기록 세트
```

이 의미는 UI, Repository, 분석, seed 적용 모두에서 동일해야 한다.

분석 기본 규칙:

```text
실제 훈련 부하 = confirmed=true 세트만
```

`confirmed=false`는 계획, 미확인 세트, 계획 준수율 계산에는 사용할 수 있지만 실제 수행량으로 보면 안 된다.

## 현재 파일 구조

### 앱 루트

`MainActivity.kt`

- `MainActivity`
- `AppTab`
- `TrainingTrackPlannerApp`
- 하단 탭 연결만 담당

### 화면

| 파일 | 담당 |
| --- | --- |
| `HomeScreen.kt` | 홈, CTA, 오늘 할 일, 오늘 요약 |
| `RecordScreen.kt` | 날짜별 운동 기록, set 편집, daily metric |
| `PlanScreen.kt` | 프로그램 목록/상세, 날짜 적용, 덮어쓰기 UX |
| `ExerciseScreen.kt` | 운동 목록, 검색, 카테고리 필터, 상세 카드 |
| `AnalysisScreen.kt` | confirmed 기반 단순 통계 |
| `CommonUi.kt` | 공통 카드, 날짜 선택, 운동 선택 dialog, 포맷 함수 |

### 데이터 계층

| 파일 | 담당 |
| --- | --- |
| `Entities.kt` | Room entity |
| `Daos.kt` | Room DAO |
| `TrainingDatabase.kt` | Room database, migration |
| `TrainingRepository.kt` | seed, 기록/계획 동작 의미 |
| `SeedData.kt` | CSV 기반 exercise/program seed |
| `ExerciseTaxonomy.kt` | taxonomy token 검증/정규화 |
| `TrainingViewModel.kt` | 화면과 repository 연결 |

## Program Apply 흐름

날짜 적용 흐름:

```text
TrainingProgramItem
-> WorkoutEntry
-> WorkoutSet confirmed=false
```

날짜 계산:

```text
startDate + ((weekNumber - 1) * 7) + (dayOfWeek - 1)
```

기존 기록이 있을 때:

| 선택 | 동작 |
| --- | --- |
| 덮어쓰기 | 기존 set 삭제 -> 기존 entry 삭제 -> 프로그램 계획 생성 |
| 추가 | 기존 기록 보존 -> 프로그램 계획 추가 |
| 취소 | 아무 변경 없음 |

덮어쓰기 다이얼로그는 다음 정보를 보여준다.

- 영향을 받는 날짜 수
- 기존 WorkoutEntry 수
- 기존 confirmed=true 세트 수
- “덮어쓰기를 선택하면 기존 기록과 완료 세트가 삭제됩니다.”

## Seed version 전략

DB version은 2다.

추가된 테이블:

```text
app_meta
```

컬럼:

```text
key TEXT PRIMARY KEY
value TEXT
updatedAt INTEGER
```

현재 meta:

```text
exercise_seed_version = 1
program_seed_version = 1
```

seed 적용 원칙:

- additive migration만 사용
- 사용자 기록 삭제 금지
- 사용자 프로그램 삭제 금지
- 같은 `stableKey` 운동 중복 삽입 금지
- 같은 기본 프로그램명 중복 삽입 금지
- 향후 기본 프로그램 추가 시 seed version을 올리고 빠진 기본 프로그램만 삽입

## 기록 화면 핵심 동작

세트 추가:

```text
마지막 세트 reps/weightKg/seconds 복사
confirmed=false 로 생성
```

세트 삭제:

```text
마지막 1개 세트 삭제 차단
삭제 후 남은 setIndex를 1부터 재정렬
```

entry 완료 상태:

```text
confirmed set이 1개 이상이면 completedAt 갱신
없으면 completedAt = null
```

스포츠 카테고리:

- RPE와 maxReps 입력을 강제하지 않는다.
- weight 입력도 분석 강제 조건으로 보지 않는다.

## 분석 현재 상태

현재 분석 화면은 고급 분석이 아니다.

표시 항목:

- confirmed set 수
- 총 볼륨
- 총 시간

DAO 집계 기준:

```sql
WHERE workout_sets.confirmed = 1
```

고급 분석은 아직 구현 금지다. 다음 단계에서 별도 승인 후 진행한다.

설계 문서:

```text
docs/analysis_algorithm_design.md
```

문서의 분석 렌즈:

- General Fitness
- Strength
- Hypertrophy
- Badminton
- Recovery
- Balance / Injury Risk
- Plan Adherence

## Debug 로그

Debug 빌드에서 `TrainingDbSummary` 로그를 출력한다.

로그 항목:

- exerciseCount
- trainingProgramCount
- trainingProgramItemCount
- todayWorkoutEntryCount
- todayConfirmedSetCount
- todayUnconfirmedSetCount

production UI에는 노출하지 않는다.

## 검증 상태

완료:

- `assembleDebug` 성공
- 화면 파일 분리 후 컴파일 성공
- Room migration 컴파일 성공
- seed meta DAO 컴파일 성공
- conflict summary 코드 컴파일 성공
- confirmed 의미 관련 소스 경로 검증

자동 테스트:

- 아직 없음

이유:

- 현재 프로젝트에 test runner / Room test 의존성이 없다.
- 의존성 추가는 네트워크와 Gradle 해결 리스크가 있어 Phase 2.5 안정화 범위에서는 보류했다.

다음에 추가할 최소 테스트:

1. 프로그램 적용 세트는 `confirmed=false`
2. 세트 추가는 값을 복사하지만 `confirmed=false`
3. 세트 삭제 후 `setIndex`가 1부터 재정렬
4. 마지막 1개 세트 삭제 차단
5. 분석 통계는 `confirmed=true`만 포함

## 다음 작업 추천 순서

1. 실제 기기 또는 emulator에서 기본 흐름 수동 QA
2. Room in-memory 테스트 환경 추가
3. 프로그램 적용 결과 미리보기
4. 기록 삭제/복사/이동 같은 lifecycle 기능
5. 분석 repository 분리
6. 고급 분석 Phase 별도 승인 후 구현

## 다음 작업자 주의

- `confirmed` 의미를 절대 바꾸지 말 것.
- seed refresh가 사용자 데이터를 삭제하게 만들지 말 것.
- 분석 문장을 단정적으로 만들지 말 것.
- 고급 추천/진단을 Phase 2.5 코드에 섞지 말 것.
- 화면 분리 후에도 동작 의미는 Repository를 기준으로 확인할 것.

## Phase 2.6 Handoff Addendum

Phase 2.6은 기록 화면 UX와 휴게 타이머 MVP 복구 작업이다.

### 기록 화면

변경:

- 세트 행을 한 줄 중심으로 압축
- `횟수`, `kg`, `초`, `확인`, `삭제`를 같은 행에 배치
- 스포츠 / 유산소는 kg 입력과 일괄 kg 버튼을 숨김
- 확인된 세트는 작은 색 차이로 구분
- `일괄 kg` 버튼으로 entry 내 모든 세트 중량 변경
- 한 세트 kg 입력 시 빈 미확인 세트에 같은 kg 적용 안내

중량 자동 적용은 다음 세트만 대상으로 한다.

```text
weightKg == 0
manualWeight == false
confirmed == false
```

confirmed=true 세트는 자동 덮어쓰지 않는다.

### Rest Timer 구조

기존 README/handoff의 rest timer 설계를 Kotlin/Compose 앱에 맞게 복원했다.

| 파일 | 책임 |
| --- | --- |
| `RestTimerSessionController.kt` | 세션 상태, end timestamp, target record, foreground/background, notification/overlay orchestration |
| `RestTimerNotifier.kt` | notification channel, running notification, finished notification |
| `RestTimerOverlayController.kt` | overlay drawing, dragging, remove, delete target, away-session suppression |
| `RestTimerSoundVibration.kt` | finish sound/vibration |
| `RestTimerNavigation.kt` | notification/overlay click target intent |
| `RestTimerUi.kt` | Compose mini bar, permission hint |

MainActivity가 담당하는 것:

- `onResume`
- `onPause`
- `onDestroy`
- notification / overlay click target navigation

MainActivity가 담당하지 않는 것:

- countdown state
- end timestamp
- notification content
- overlay drawing
- sound / vibration

### Persisted Rest Timer Keys

`RestTimerSessionController`가 관리한다.

```text
rest_end_at
rest_next
rest_target_date
rest_target_entry_id
rest_finished
```

### Overlay Delete Suppression

복구된 동작:

- 앱 밖에서 오버레이를 닫거나 delete target에 드롭하면 현재 away session 동안 다시 표시하지 않는다.
- 앱으로 돌아오면 suppression reset.
- 새 타이머가 시작되면 suppression reset.

### 남은 QA

실제 기기 또는 emulator에서 확인해야 한다.

- notification 권한 요청
- running notification 갱신
- finished notification
- overlay 권한 설정 이동
- 앱 밖 overlay 표시
- overlay drag
- overlay delete target
- delete suppression
- sound / vibration

자세한 QA 절차는 `outputs/phase2_6_record_timer_patch.md`에 있다.

## Phase 2.6 Build Environment Patch Handoff

Gradle problems report 충돌만 우회했다. 기능 코드는 건드리지 않았다.

변경 파일:

- `gradle.properties`
- `build.gradle.kts`
- `local.properties`

내용:

- `org.gradle.problems.report=false` 추가
- root `cleanBuildReports` 태스크 추가
- `build/reports/problems`, `app/build/reports/problems`만 삭제
- root/app `assembleDebug` 전에 `cleanBuildReports` 실행
- `local.properties`를 workspace SDK 경로로 변경
- stale `build/reports/problems` 폴더 삭제

빌드 검증:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

결과: `BUILD SUCCESSFUL`.

## Phase 2.6 Record UX / Set RPE / Rest Handoff

이번 패치는 Phase 3 CSV 전에 기록 화면과 휴게 타이머 사용성을 복구한 작업이다. CSV 백업/복원과 고급 분석은 구현하지 않았다.

핵심 변경:

- `WorkoutSet`에 `rpe: Double?` 추가
- `WorkoutSet`에 `restSecondsOverride: Int?` 추가
- Room DB version 3 additive migration 추가
- `WorkoutEntry.rpe`는 legacy fallback / 운동 전체 체감으로 유지
- 세트 추가 시 reps / weightKg / seconds는 복사하지만 `rpe`와 `restSecondsOverride`는 복사하지 않음
- 세트 확인 시 timer duration은 `set.restSecondsOverride ?: entry.restSeconds`
- `WorkoutEntry.notes`는 구조화 처방값 저장소가 아님
- 프로그램 적용 시 단순 구조 처방 텍스트는 notes에 복사하지 않음

Record UI:

- 상단 DailyMetric 입력은 `컨디션` 버튼 뒤에 접힘
- 운동 메모 / maxReps / entry-level RPE / 기본 휴식 편집은 `상세` 버튼 뒤에 접힘
- 세트 행은 compact input, RPE chip, 휴식 chip 구조
- 일괄 메뉴는 kg 설정 / 증가 / 감소, 횟수 증가 / 감소, 휴식 설정 / 증가 / 감소 지원
- confirmed=true 세트 포함 변경은 사용자가 `완료 세트 포함`을 선택해야 함

Rest timer:

- 기존 `RestTimerSessionController`, `RestTimerNotifier`, `RestTimerOverlayController`, `RestTimerSoundVibration` 책임 분리 유지
- overlay delete suppression 유지
- app shell은 lifecycle / intent navigation만 담당

신규 문서:

- `outputs/phase2_6_legacy_record_timer_audit.md`
- `outputs/phase2_6_rest_timer_recovery.md`

## Phase 2.7 Record Calendar UI Handoff

Phase 2.7은 기록 탭 마이너 UI 패치다.

변경 파일 핵심:

- `RecordScreen.kt`
- `RecordCalendarScreen.kt`
- `RestTimerState.kt`
- `RestTimerSessionController.kt`
- `Daos.kt`
- `TrainingRepository.kt`
- `TrainingViewModel.kt`

동작:

- `달력`은 날짜 이동 row의 `다음날` 오른쪽에 배치
- `컨디션` / `운동 추가`는 별도 compact row에 배치
- 상단 mini timer bar는 기록 화면에서 제거
- active timer는 `targetEntryId` + `targetSetId`가 맞는 세트 row에 표시
- notification / overlay source of truth는 계속 `RestTimerSessionController`
- 완료 운동은 UI sorting으로 위에 표시
- 월간 summary는 DAO query에서 계산

집계 기준:

- `confirmed=true`: 실제 세트 수, 볼륨, 시간
- `confirmed=false`: planned / unconfirmed count
- 볼륨: `reps * weightKg`

다크모드:

- 시스템 다크모드 연동 지원됨
- 수동 토글은 없음

문서:

- `outputs/phase2_7_record_calendar_ui_patch.md`

## Phase 2.7.2 Calendar Lifecycle Handoff

기존 Java 앱의 `RecordCalendarController` / `TrainingWorkoutEntryRepository` 역할 일부를 새 Kotlin 구조에 복원했다.

수정 파일:

- `RecordCalendarScreen.kt`
- `Daos.kt`
- `TrainingRepository.kt`
- `TrainingViewModel.kt`

새 문서:

- `outputs/phase2_7_2_calendar_lifecycle_audit.md`
- `outputs/phase2_7_2_calendar_lifecycle_patch.md`

## Phase 3.0.0 Handoff Addendum

Patch 3.0.0 prepares Analysis Engine V3 without changing the user-facing analysis tab.

New packages:

- `com.training.trackplanner.analysis.core`
- `com.training.trackplanner.analysis.metrics`
- `com.training.trackplanner.analysis.methods`
- `com.training.trackplanner.analysis.text`
- `com.training.trackplanner.analysis.engine`

Rules to preserve:

- Do not treat the last workout date as today. Use `AnalysisDateProvider`.
- Actual completed load is `date <= today` and `WorkoutSet.confirmed=true`.
- Future plan projection is `date > today` and `WorkoutSet.confirmed=false`.
- Do not enable new analysis methods until the matching review template is completed.
- Do not expose V3 method results in the normal UI during 3.0.0.

DAO additions are read-only and exist only for V3 input collection:

- `ExerciseDao.allExercises`
- `WorkoutDao.entriesWithSetsUntil`
- `WorkoutDao.entriesWithSetsAfter`
- `DailyMetricDao.metricsUntil`

Reference document:

- `outputs/phase3_0_0_analysis_engine_v3_foundation.md`

## Phase 3.1.1 Handoff Addendum

Patch 3.1.1 adds structured fatigue/readiness metadata only.

New files:

- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt`
- `app/src/main/java/com/training/trackplanner/data/MetadataSanityChecker.kt`
- `app/src/test/java/com/training/trackplanner/data/MetadataSanityCheckerTest.kt`
- `outputs/phase3_1_1_fatigue_metadata_foundation.md`

DB:

- Room version is now 4.
- Migration `3 -> 4` only adds Exercise metadata and fatigue weight columns.
- User records, plans, and sets are not deleted.

Important:

- Do not build final fatigue/readiness judgment on this patch alone.
- Use structured metadata fields, not exercise-name parsing.
- Remaining string heuristics in `SeedData.kt` are legacy fallback for old seed metadata, not the new fatigue mapping path.
- The seed catalog currently validates as 214 HIGH-confidence rows with no NEEDS_REVIEW rows.

## Phase 3.1.2 Handoff Addendum

Patch 3.1.2 adds the remaining metadata and feature-vector path for later analysis engines.

New files:

- `app/src/main/java/com/training/trackplanner/analysis/features/ExerciseAnalysisMapper.kt`
- `app/src/main/java/com/training/trackplanner/analysis/features/MetadataReadinessReporter.kt`
- `outputs/metadata_analysis_readiness_report.md`
- `outputs/phase3_1_2_metadata_readiness_patch.md`

DB:

- Room version is now 5.
- Migration `4 -> 5` only adds Exercise metadata columns.

Rules:

- Future 3.2 fatigue analysis should consume `AnalysisExerciseFeatures`.
- Future 3.3 progress analysis should branch by `progressMetricType`.
- Future 3.4 badminton analysis should branch by `badmintonTransferStrength`, `courtMovementTypes`, and `badmintonSkillTargets`.
- Do not classify exercises by exercise-name string matching in analyzers.

## Phase 3.1.3 Handoff Addendum

Patch 3.1.3 implements Today Readiness / Fatigue analysis and exposes it on the Analysis tab.

New package:

- `app/src/main/java/com/training/trackplanner/analysis/readiness`

Key classes:

- `TodayReadinessEngine`
- `DailyAnalysisLoadAggregator`
- `ResidualFatigueCalculator`
- `StatisticalBaselineCalculator`
- `AdaptiveBaselineCalculator`
- `AdaptiveBaselineUpdateEvaluator`
- `FatiguePressureCalculator`
- `RecoverySignalInterpreter`
- `PerformanceDropDetector`
- `PainGateEvaluator`
- `TodayReadinessDecisionEngine`
- `TodayReadinessSentenceBuilder`
- `FatigueDetailSectionBuilder`

UI connection:

- `TrainingRepository.todayReadinessSummary()` reads exercises, entries until today, and daily metrics.
- `TrainingViewModel.todayReadinessSummary` exposes a state flow.
- `AnalysisScreen` shows the Today Readiness card above existing simple stats.

Critical behavior:

- only `confirmed=true` sets are included in fatigue load.
- `confirmed=false` planned sets are excluded.
- planned future entries are not mixed into today's fatigue.
- exercise classification is driven by `ExerciseAnalysisMapper`, not exercise names.
- pain is handled as a gate, not averaged into a fatigue score.
- sentence output avoids injury prediction, medical diagnosis, overtraining diagnosis, and deterministic danger phrasing.

Adaptive baseline:

- calculated deterministically from recent records and outcome signals.
- high load alone does not raise tolerance.
- successful high exposure can raise tolerance slightly.
- failed exposure blocks or lowers tolerance slightly.
- single-run up/down changes are capped at 5%.

Tests:

- `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayReadinessEngineTest.kt`

Verification note:

- A first Gradle test run reached Kotlin compile and exposed a nullable enum comparison issue.
- That issue was fixed.
- Further unrestricted Gradle execution was blocked by the environment usage limit; sandboxed Gradle cannot download the wrapper distribution.
- Next worker should rerun `testDebugUnitTest` and `assembleDebug`.

Reference:

- `outputs/phase3_1_3_today_readiness_engine.md`

## Phase 3.2.0 Handoff Addendum

Patch 3.2.0 adds the Analysis tab section `성과 추세 분석`.

New package:

- `app/src/main/java/com/training/trackplanner/analysis/trends`

Key classes:

- `PerformanceTrendEngine`
- `WeeklyAnalysisAggregator`
- `TrendMath`
- `StrengthPerformanceIndexCalculator`
- `BadmintonTrainingLoadIndexCalculator`
- `FatigueCompositeIndexCalculator`
- `TrendForecastRangeCalculator`
- `PerformanceChartSpecBuilder`
- `DetailChartSelector`
- `ScatterRelationshipAnalyzer`
- `PerformanceTrendSentenceBuilder`

UI connection:

- `TrainingRepository.performanceTrendSummary()` reads exercises, entries until today, and daily metrics.
- `TrainingViewModel.performanceTrendSummary` exposes a state flow.
- `AnalysisScreen` renders the Performance Trend card below Today Readiness.

First-screen rules:

- exactly 3 dashboard chart specs.
- each dashboard chart is `ChartType.LINE`.
- each dashboard chart has one visible line.
- `emphasizeValue=false`.
- one short trend sentence only.

Detail rules:

- four sections: strength, badminton, fatigue, relationship.
- strength/badminton/fatigue sections each use one chart slot.
- chart mode controls reset incompatible metric selections.
- relationship section uses scatter plot and trend-only language.

Calculation rules:

- strength performance = intensity 0.50 + volume 0.40 + efficiency 0.10.
- badminton training = court 0.60 + footwork/reactive 0.25 + support 0.15.
- fatigue composite = average standardized fatigue 0.60 + max fatigue 0.25 + recovery/performance penalty 0.15.
- raw kg, raw reps, and raw minutes are not directly added across composite categories.
- all display indices are standardized against personal baselines or fallback to neutral 100 with low confidence.

Verification note:

- Added `PerformanceTrendEngineTest`.
- `testDebugUnitTest` succeeded after updating the safe z-score fallback assertion.
- `assembleDebug` succeeded with `--no-daemon --no-problems-report`.

Reference:

- `outputs/phase3_2_0_performance_trend_analysis.md`

## Minor Patch: Record Date Switcher Width

Files:

- `app/src/main/java/com/training/trackplanner/RecordScreen.kt`

Change:

- The Record tab date row now uses compact `OutlinedButton` padding for `이전날`, `다음날`, and `달력`.
- The date label uses a single line with wrapping disabled, preventing `YYYY-MM-DD` from breaking across lines.

Scope:

- UI-only patch.
- No DB schema changes.
- No repository behavior changes.
- No `WorkoutSet.confirmed` behavior changes.

Verification:

- `assembleDebug` succeeded after the patch.

구현:

- 날짜 long press action dialog
- 계획으로 복사
- 기록상태까지 복사
- 이동
- 삭제
- 선택복사
- 충돌 summary / conflict dialog

Repository API:

- `calendarConflictSummary`
- `copyDate`
- `moveDate`
- `deleteDate`
- `copyDateRangeAsPlan`

정책:

- DB schema 변경 없음
- DailyMetric은 날짜 고유 컨디션이므로 복사 / 이동 / 삭제하지 않음
- 계획 복사와 선택복사는 `confirmed=false`
- 기록상태 복사와 이동은 confirmed 보존
- 덮어쓰기 / 삭제는 사용자 확인 dialog를 거침
