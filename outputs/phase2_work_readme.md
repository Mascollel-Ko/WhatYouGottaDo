# WhatYouGottaTrain Phase 2.5 Work README

작성일: 2026-06-14  
프로젝트 위치: `C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme`  
기술 스택: Android native, Kotlin, Jetpack Compose, Material 3, Room SQLite

## 문서 갱신 규칙

앞으로 기능, DB 구조, seed, 분석 설계, 화면 구조가 바뀌면 이 README와 handoff 문서를 함께 갱신한다.

갱신 대상:

- `outputs/phase2_work_readme.md`
- `outputs/phase2_handoff.md`
- `outputs/phase2_data_meaning_mapping.md`
- 분석 설계 변경 시 `docs/analysis_algorithm_design.md`

## 앱 식별 정보

테스트폰에서 기존 앱과 충돌하지 않도록 앱 식별자를 분리했다.

| 항목 | 값 |
| --- | --- |
| 앱 이름 | `WhatYouGottaTrain` |
| applicationId | `com.whatyougottatrain.app` |
| namespace / Kotlin package | `com.training.trackplanner` |
| minSdk | 26 |
| targetSdk | 35 |

## 제품 철학

이 앱은 단순 운동일지가 아니다.

핵심 흐름은 다음과 같다.

```text
계획 -> 수행 확인 -> 분석 -> 판단 -> 다음 행동
```

가장 중요한 데이터 의미는 유지해야 한다.

```text
WorkoutSet.confirmed = false : 아직 수행하지 않은 계획 세트
WorkoutSet.confirmed = true  : 실제 수행한 기록 세트
```

분석은 기본적으로 `confirmed=true` 세트만 실제 훈련 부하로 본다.

## 현재 단계

Phase 2는 기록/계획 기능의 최소 완성 단계였다.  
Phase 2.5는 새 기능 확장이 아니라 안정화, 구조 정리, 분석 설계 고정 방지를 목표로 진행했다.

## Phase 2.5 구현 요약

### 1. MainActivity 화면 분리

`MainActivity.kt`는 앱 루트와 하단 탭만 담당하도록 줄였다.

분리된 파일:

| 파일 | 역할 |
| --- | --- |
| `HomeScreen.kt` | 홈, 오늘 할 일, 오늘 요약 |
| `RecordScreen.kt` | 날짜별 기록, 세트 편집, 수면/체중 |
| `PlanScreen.kt` | 프로그램 목록, 상세, 날짜 적용 |
| `ExerciseScreen.kt` | 운동 목록, 검색, 카테고리 필터 |
| `AnalysisScreen.kt` | confirmed 기반 단순 통계 |
| `CommonUi.kt` | 공통 카드, 날짜, 운동 선택, 포맷 헬퍼 |

### 2. 프로그램 적용 덮어쓰기 UX 강화

프로그램 적용 대상 날짜에 기존 기록이 있으면 안전 확인 다이얼로그를 표시한다.

표시 항목:

- 영향을 받는 날짜 수
- 기존 WorkoutEntry 수
- 기존 confirmed=true 세트 수
- 경고 문구: “덮어쓰기를 선택하면 기존 기록과 완료 세트가 삭제됩니다.”

동작:

- 덮어쓰기: 기존 set 삭제 후 entry 삭제, 그다음 프로그램 계획 생성
- 추가: 기존 기록 보존 후 프로그램 계획 추가
- 취소: 아무 변경 없음

프로그램 적용으로 생성되는 모든 `WorkoutSet`은 반드시 `confirmed=false`다.

### 3. Seed version / meta 전략

`app_meta` 테이블을 추가했다.

현재 meta key:

| key | 의미 |
| --- | --- |
| `exercise_seed_version` | 운동 seed 적용 버전 |
| `program_seed_version` | 프로그램 seed 적용 버전 |

전략:

- DB migration은 additive 방식만 사용한다.
- 사용자 기록, 날짜 계획, 사용자 생성 운동/프로그램을 삭제하지 않는다.
- 운동 seed는 `stableKey` unique index와 insert ignore로 중복을 막는다.
- 기본 프로그램 seed는 같은 프로그램명이 이미 있으면 중복 삽입하지 않는다.
- 기존 DB에 프로그램이 있어도 향후 seed version 증가 시 빠진 기본 프로그램을 추가할 수 있다.

### 4. 고급 분석 구현 금지

Phase 2.5에서는 고급 분석 알고리듬을 구현하지 않았다.

현재 분석 화면은 다음 단순 통계만 표시한다.

- confirmed set 수
- 총 볼륨
- 총 시간

분석 설계 문서는 별도로 추가했다.

```text
docs/analysis_algorithm_design.md
```

문서에는 Raw Data, Feature Extraction, Signal Generation, Judgment, Recommendation, Narrative Rendering 구조를 정의했다.

### 5. Debug DB summary 로그

Debug 빌드에서만 `TrainingDbSummary` 로그를 출력한다.

확인 항목:

- Exercise count
- TrainingProgram count
- TrainingProgramItem count
- 오늘 WorkoutEntry count
- 오늘 confirmed set count
- 오늘 unconfirmed set count

production UI에는 개발자 정보를 노출하지 않는다.

## 중요한 코드 규칙

반드시 유지할 규칙:

- 프로그램 적용으로 생성되는 set은 `confirmed=false`
- 세트 추가는 마지막 set의 `reps`, `weightKg`, `seconds`를 복사하되 `confirmed=false`
- 마지막 1개 세트 삭제는 차단
- 세트 삭제 후 `setIndex`는 1부터 재정렬
- 분석 단순 통계는 `confirmed=true`만 집계
- 덮어쓰기는 사용자가 명시적으로 선택한 경우에만 수행

## 주요 수정 파일

| 파일 | 변경 내용 |
| --- | --- |
| `MainActivity.kt` | 앱 루트와 하단 탭만 남김 |
| `HomeScreen.kt` | 홈 화면 분리 |
| `RecordScreen.kt` | 기록 화면 분리 |
| `PlanScreen.kt` | 계획 화면 분리, 덮어쓰기 UX 강화 |
| `ExerciseScreen.kt` | 운동 화면 분리 |
| `AnalysisScreen.kt` | 분석 화면 분리 |
| `CommonUi.kt` | 공통 UI 분리 |
| `Entities.kt` | `AppMeta` entity 추가 |
| `Daos.kt` | app meta DAO, 충돌 요약/디버그 count 쿼리 추가 |
| `TrainingDatabase.kt` | DB version 2, migration 1->2 추가 |
| `TrainingRepository.kt` | seed meta, conflict summary, debug summary 로그 |
| `TrainingViewModel.kt` | 프로그램 적용 충돌 요약 조회 추가 |
| `docs/analysis_algorithm_design.md` | 향후 분석 알고리듬 설계 문서 |

## 빌드 검증

실행 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat assembleDebug
```

결과:

```text
BUILD SUCCESSFUL
```

APK:

```text
app/build/intermediates/apk/debug/app-debug.apk
```

## 테스트 / 검증 상태

자동 Room in-memory 테스트는 이번 단계에서 추가하지 않았다.

이유:

- 현재 프로젝트에 test runner / Room test 의존성이 구성되어 있지 않다.
- 새 테스트 의존성 추가는 네트워크와 Gradle 의존성 해결 위험이 있다.
- Phase 2.5 목표가 안정화이므로 빌드 성공과 핵심 코드 경로 검증을 우선했다.

대신 소스 검증으로 확인한 의미:

- 프로그램 적용 set은 `confirmed=false`
- 세트 추가 set은 `confirmed=false`
- 마지막 세트 삭제 차단
- 삭제 후 `setIndex` 재정렬
- 분석 통계는 `confirmed=true`만 포함

## 다음 작업자 주의점

1. 고급 분석은 아직 구현하지 않는다. 별도 승인 후 진행한다.
2. seed version을 올릴 때 사용자 데이터를 삭제하지 않는다.
3. 프로그램 seed 추가 시 같은 기본 프로그램명이 있으면 중복 삽입하지 않는다.
4. UI 분리 후 기능 의미는 Repository에 남아 있다. confirmed 규칙은 UI에서 재해석하지 않는다.
5. 앞으로 변경 작업마다 이 README, handoff, 데이터 의미 매핑을 함께 갱신한다.

## Phase 2.6 Minor Patch

Phase 3 CSV 백업/복원 전에 기록 화면 UX와 휴게 타이머 MVP를 복구했다.

변경 요약:

- 기록 세트 행을 한 줄 중심으로 압축
- 확인 체크박스를 횟수 / kg / 초 옆으로 이동
- 확인된 세트와 계획 세트를 작은 색감 차이로 구분
- 운동 entry 단위 `일괄 kg` 버튼 추가
- 한 세트에 kg 입력 시 빈 미확인 세트에 같은 kg 적용 안내 추가
- 세트가 `confirmed=false -> true`로 바뀌는 순간 휴게 타이머 시작
- 앱 안 미니 타이머 추가
- 알림 채널과 running / finished notification 추가
- 오버레이 MVP 추가
- 오버레이 drag / delete target / current away session suppression 복구

기존 rest timer 설계를 Kotlin / Compose 구조로 복원했다.

추가 파일:

- `RestTimerSessionController.kt`
- `RestTimerNotifier.kt`
- `RestTimerOverlayController.kt`
- `RestTimerSoundVibration.kt`
- `RestTimerNavigation.kt`
- `RestTimerState.kt`
- `RestTimerUi.kt`
- `outputs/phase2_6_record_timer_patch.md`

MainActivity는 `onResume`, `onPause`, `onDestroy`, 알림/오버레이 클릭 navigation만 담당한다. 타이머 상태, 알림, 오버레이 drawing은 MainActivity나 RecordScreen에 넣지 않는다.

## Phase 2.6 Build Environment Patch

Gradle problems report 파일 충돌을 막기 위해 빌드 환경만 수정했다.

- `gradle.properties`에 `org.gradle.problems.report=false` 추가
- root `build.gradle.kts`에 `cleanBuildReports` 태스크 추가
- `cleanBuildReports`는 `build/reports/problems`, `app/build/reports/problems`만 삭제한다
- `assembleDebug` 실행 전 `cleanBuildReports`가 먼저 실행되도록 연결
- `local.properties`의 SDK 경로를 workspace SDK로 맞춤
- 기능 코드, DB schema, CSV, 분석 로직은 변경하지 않음

검증 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

결과: `BUILD SUCCESSFUL`.

## Phase 2.6 Record UX / Set RPE / Rest Patch

기록 화면을 운동 중 입력 중심으로 다시 압축했다.

변경 요약:

- DailyMetric 수면 / 체중 입력을 `컨디션` 버튼 뒤로 숨김
- 운동 메모, 최대횟수, 운동 전체 RPE, 기본 휴식 편집을 `상세` 버튼 뒤로 숨김
- 세트 목록이 항상 부가 입력보다 먼저 보이도록 조정
- `BasicTextField` 기반 compact number input 사용
- `WorkoutSet.rpe` 추가
- `WorkoutSet.restSecondsOverride` 추가
- Room DB version 3 additive migration 추가
- 세트 확인 시 effective rest로 휴게 타이머 시작
- 일괄 메뉴에 kg 설정 / 증가 / 감소, 횟수 증가 / 감소, 휴식 설정 / 증가 / 감소 추가
- 빈 kg 세트 자동 적용은 미확인 빈 세트만 대상으로 유지
- 프로그램 적용 시 구조화 처방값을 `WorkoutEntry.notes`에 복사하지 않도록 정리

데이터 의미:

- `WorkoutSet.rpe`가 세트별 RPE의 canonical 위치
- `WorkoutEntry.rpe`는 legacy fallback 또는 운동 전체 체감
- `WorkoutEntry.restSeconds`는 기본 휴식시간
- `WorkoutSet.restSecondsOverride`는 세트별 휴식 예외값
- `WorkoutEntry.notes`는 자유 메모 / 코치성 메모
- 세트수 / 반복수 / 중량 / 시간은 `WorkoutSet`과 `TrainingProgramItem` 구조화 필드가 원천

추가 문서:

- `outputs/phase2_6_legacy_record_timer_audit.md`
- `outputs/phase2_6_rest_timer_recovery.md`

## Phase 2.7 Record Calendar UI Patch

기록 탭 마이너 UI 개선을 적용했다.

- `달력` 버튼을 날짜 이동 row의 `다음날` 오른쪽에 배치
- `컨디션` / `운동 추가`를 compact row로 정리
- 휴게 타이머 표시를 상단 bar에서 active set row 보조 chip으로 이동
- `RestTimerState.targetSetId`를 추가해 단일 active timer를 세트 행에 매핑
- 완료 운동을 UI에서 상단 정렬
- 월간 기록 요약 화면 `RecordCalendarScreen.kt` 추가
- `DailyRecordSummary`와 날짜 범위 summary query 추가
- 다크모드는 기존 `darkColorScheme` / `isSystemInDarkTheme()` 기반 지원 확인

정렬은 UI 표시 순서만 바꾸며 DB row 순서, `createdAt`, `entry id`, `setIndex`는 변경하지 않는다.

새 문서:

- `outputs/phase2_7_record_calendar_ui_patch.md`

## Phase 2.7.2 Calendar Lifecycle Patch

월간 캘린더의 기존 기록 lifecycle 기능을 long press + dialog 방식으로 복원했다.

- 계획으로 날짜 복사
- 기록상태까지 날짜 복사
- 날짜 이동
- 날짜 삭제
- 선택복사
- 대상 날짜 / 범위 충돌 시 덮어쓰기 / 추가 / 취소
- 삭제 전 운동 수 / 세트 수 / 완료 세트 수 표시
- DailyMetric은 복사 / 이동 / 삭제하지 않음

confirmed 의미:

- 계획 복사와 선택복사: 복사된 모든 set `confirmed=false`
- 기록상태 복사와 이동: 원본 confirmed 상태 보존
- 삭제: 명시적 확인 후 운동 entry / set만 삭제

새 문서:

- `outputs/phase2_7_2_calendar_lifecycle_audit.md`
- `outputs/phase2_7_2_calendar_lifecycle_patch.md`

## Phase 3.0.0 Analysis Engine V3 Foundation

This update adds analysis infrastructure only. It does not replace the current analysis tab and does not expose new user-facing analysis methods.

Added source areas:

- `analysis/core`: date provider, windows, input snapshot, input collector.
- `analysis/metrics`: common load, strength, taxonomy, and plan projection metrics.
- `analysis/methods`: analyzer interface, result model, disabled registry.
- `analysis/text`: sentence builder interface and sentence policy.
- `analysis/engine`: V3 facade and dashboard result model.

Important behavior:

- V3 today is based on `AnalysisDateProvider.today()`, defaulting to `LocalDate.now()`.
- Completed input uses only `date <= today` and `confirmed=true` sets.
- Future plan input uses only `date > today` and `confirmed=false` sets.
- New analysis method ids exist only as disabled registry descriptors.
- No DB rows are deleted, rewritten, or migrated by this patch.

Related document:

- `outputs/phase3_0_0_analysis_engine_v3_foundation.md`

## Phase 3.1.1 Fatigue Metadata Foundation

This update prepares Today Fatigue / Readiness analysis metadata without implementing the final fatigue engine or changing the analysis UI.

Added:

- `ExerciseMetadataTaxonomy.kt`
- `ExerciseMetadataMapper.kt`
- `MetadataSanityChecker.kt`
- additive Room migration `3 -> 4`
- seed-wide metadata sanity tests
- `outputs/phase3_1_1_fatigue_metadata_foundation.md`

Current built-in catalog:

- 214 exercises
- metadata confidence: `HIGH=214`, `MEDIUM=0`, `LOW=0`, `NEEDS_REVIEW=0`
- sanity errors: 0

Preserved:

- existing records
- calendar behavior
- record screen behavior
- existing simple analysis tab
- `WorkoutSet.confirmed` semantics

## Phase 3.1.2 Metadata Readiness

This update completes the metadata fields needed by 3.2, 3.3, 3.4, and future balance/safety analysis.

Added:

- progress metric taxonomy
- strength/hypertrophy/main lift grouping
- badminton transfer strength, court movement types, skill targets
- joint stress, stability, mobility, and balance contribution fields
- `analysisEligibility`
- `ExerciseAnalysisMapper`
- `MetadataReadinessReporter`
- expanded `MetadataSanityChecker`

Current readiness:

- total exercises: 214
- fatigueReady: `YES=214`
- progressReady: `YES=214`
- badmintonReady: `YES=214`
- balanceReady: `YES=214`
- NEEDS_REVIEW: none

Related documents:

- `outputs/metadata_analysis_readiness_report.md`
- `outputs/phase3_1_2_metadata_readiness_patch.md`

## Phase 3.1.3 Today Readiness / Fatigue Engine

This update implements the first user-facing Today Readiness card on the Analysis tab.

Added:

- `analysis/readiness` engine package
- `TodayReadinessEngine`
- daily load aggregation from confirmed sets only
- residual fatigue decay by `recoveryDecayProfile`
- statistical baseline calculation
- adaptive baseline calculation
- fatigue pressure calculation
- recovery, performance, and pain-gate interpreters
- readiness decision and sentence builders
- expandable detail sections in `AnalysisScreen`
- unit tests for synthetic readiness scenarios

Preserved:

- `WorkoutSet.confirmed=false` is still plan.
- `WorkoutSet.confirmed=true` is still actual record.
- planned workouts are excluded from today's fatigue calculation.
- existing simple analysis stats remain visible.
- exercise classification uses structured metadata through `ExerciseAnalysisMapper`.

Reference:

- `outputs/phase3_1_3_today_readiness_engine.md`

## Phase 3.2.0 Performance Trend Analysis

This update adds the Analysis tab section `성과 추세 분석`.

First-screen dashboard:

- one line chart for strength performance
- one line chart for badminton training volume
- one line chart for fatigue composite index
- one short trend sentence
- detail toggle

Rules:

- the first screen shows exactly three single-line charts.
- large numeric score emphasis is avoided.
- detail indicators are shown only after expanding details.
- trend calculations use completed records only.
- planned sets are excluded.
- exercise classification uses structured metadata through `ExerciseAnalysisMapper`.

Added trend package:

- `analysis/trends/PerformanceTrendEngine.kt`
- `analysis/trends/StrengthPerformanceIndexCalculator.kt`
- `analysis/trends/BadmintonTrainingLoadIndexCalculator.kt`
- `analysis/trends/FatigueCompositeIndexCalculator.kt`
- `analysis/trends/PerformanceChartSpecBuilder.kt`
- `analysis/trends/ScatterRelationshipAnalyzer.kt`
- `analysis/trends/DetailChartSelector.kt`

Reference:

- `outputs/phase3_2_0_performance_trend_analysis.md`

## Minor Patch: Record Date Switcher Width

This update tightens the Record tab date navigation row.

Changed:

- `이전날`, `다음날`, and `달력` button labels are unchanged.
- Date navigation buttons now use smaller horizontal padding and no default minimum width.
- The date text is fixed to one line with wrapping disabled.

Preserved:

- No repository, database, analysis, timer, or `confirmed` semantics changed.
- This is a UI-only patch for the Record tab date switcher.

Verification:

- `assembleDebug` succeeded after the patch.

## Phase 3.3.0 Badminton Transfer Analysis

This update adds the Analysis tab section `배드민턴 전이 분석`.

First-screen rule:

- The card body shows exactly one recommendation sentence.
- Ratios, charts, explanations, and Top 5 exercise lists are only shown after expanding details.

Calculation:

- confirmed sets only
- recent 7 days and baseline 28 days
- structured metadata only through `ExerciseAnalysisMapper`
- transfer type is mapped from `badmintonTransferStrength`
- transfer axes are derived from existing badminton, fatigue, movement, laterality, balance, and muscle metadata

Detail views:

- `전이축 비중`
- `전이유형 비중`
- `최근 7일 vs 28일`
- `운동별 전이 자극 Top 5`

Preserved:

- planned sets remain excluded from actual analysis.
- no DB schema change.
- no exercise name parsing for analysis.
- 3.1 readiness and 3.2 performance trend remain visible.

Reference:

- `outputs/phase3_3_0_badminton_transfer_analysis.md`

## v0.3.4.0 Home Cleanup and Backup Restore

This update cleans the Home screen and re-exposes record backup / restore entry points.

Home changes:

- removed the duplicated `오늘 할 일` card.
- kept `오늘 요약`.
- added `기록 관리` below `오늘 요약`.
- added `기록 백업` and `기록 복원` buttons.

Backup / restore:

- backup exports restore-format CSV through Android document creation.
- restore imports restore-format CSV and legacy `daily_timeseries` CSV.
- restore-format can recreate daily metrics, workout entries, and sets.
- daily_timeseries import restores daily metrics and creates category-level aggregate records because that CSV has no exact set-level detail.

Preserved:

- `confirmed=false` remains planned.
- `confirmed=true` remains completed record.
- v0.3.3.0 analysis and badminton transfer cards remain in place.

Reference:

- `outputs/phase3_4_0_home_backup_restore.md`

## Post v0.3.4.0 Analysis UI Compactness Patch

This minor update reduces the collapsed height of the 3.1 Today Readiness card in the Analysis tab.

UI changes:

- reduced card padding and vertical spacing.
- reduced Today Readiness status typography from headline-sized to title-sized.
- shortened collapsed `주요 이유` to the first 2 items.
- changed collapsed `추천` and `조절` blocks into one-line summaries.
- kept the expanded detail sections unchanged.

Preserved:

- Today Readiness engine logic is unchanged.
- confirmed-only analysis semantics are unchanged.
- 3.2 performance trend and 3.3 badminton transfer sections remain in place.

## v0.3.4.1 Program Generator and Safe Schedule Overwrite

This update restores the Plan tab as a practical program workflow.

Plan changes:

- programs can be opened, edited, and deleted.
- the program editor can create a metadata-based 4-week skeleton.
- generated skeletons can be reviewed and edited before saving.
- the editor supports goal, weekly days, session minutes, equipment, exclusions, badminton transfer ratio, sport/strength ratio, and periodization type.

Generator rules:

- structured exercise metadata is the primary classification source.
- exercise names are not used for analysis classification.
- user exclusion text can filter exercise names because it is direct user input.
- scoring uses goal fit, transfer metadata, equipment, movement needs, fatigue cost, and metadata confidence.
- recent confirmed history can autofill conservative starting weights.

Schedule overwrite:

- applying a program still creates planned sets with `confirmed=false`.
- overwrite now removes only planned-only schedule entries.
- completed records with `confirmed=true` sets are preserved.
- the conflict dialog shows target period, planned entries to replace, new planned entries, and confirmed sets preserved.

Reference:

- `program_skeleton_generator_spec_v0.3.4.1.md`
- `outputs/phase3_4_1_program_generator.md`

Record calendar add-on:

- long-press date menu now includes range delete.
- the selected start/end date range can delete unconfirmed-only sets or all records including confirmed sets.
- unconfirmed-only delete preserves completed records and reorders remaining set indexes.
