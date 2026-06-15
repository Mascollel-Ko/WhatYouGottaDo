# WhatYouGottaTrain Data Meaning Mapping

작성일: 2026-06-14  
적용 범위: Phase 2.5

## 문서 목적

이 문서는 DB 데이터와 실제 운동 의미를 연결한다.  
나중에 분석, 추천, 진단을 만들 때 컬럼 의미를 잘못 해석하지 않도록 기준을 남긴다.

앞으로 DB, seed, taxonomy, 분석 기준이 바뀌면 이 문서도 함께 갱신한다.

## 핵심 의미

가장 중요한 규칙:

```text
WorkoutSet.confirmed = false : 계획 세트
WorkoutSet.confirmed = true  : 실제 수행 기록 세트
```

분석 기본값:

```text
실제 훈련 부하 = confirmed=true 세트만
```

`confirmed=false`는 다음 용도로만 쓴다.

- 오늘 계획
- 미확인 세트
- 계획 준수율
- 예정 부하
- 다음 행동 후보

실제 수행량, 총 볼륨, 총 시간에는 기본적으로 포함하지 않는다.

## Seed Source

현재 seed는 assets CSV에서 들어온다.

```text
app/src/main/assets/training_settings_seed.csv
```

row type:

| row_type | 의미 | DB 반영 |
| --- | --- | --- |
| `exercise` | 운동 카탈로그 | `Exercise` |
| `program` | 기본 프로그램 헤더 | `TrainingProgram` |
| `program_item` | 프로그램 처방 항목 | `TrainingProgramItem` |

운동 seed는 향후 전체 200여 개 운동과 taxonomy 분석에 쓰인다.

## Seed Version Meta

Phase 2.5에서 `app_meta` 테이블이 추가되었다.

| key | value 의미 |
| --- | --- |
| `exercise_seed_version` | 적용된 운동 seed 버전 |
| `program_seed_version` | 적용된 프로그램 seed 버전 |

전략:

- seed version이 올라가면 빠진 기본 데이터만 추가한다.
- 사용자 기록은 삭제하지 않는다.
- 사용자 생성 운동/프로그램은 삭제하지 않는다.
- 운동은 `stableKey`로 중복을 막는다.
- 기본 프로그램은 같은 프로그램명으로 중복을 막는다.

## Exercise

`Exercise`는 단순 표시 목록이 아니다.  
향후 분석에서 운동 의미를 해석하는 taxonomy 원천이다.

| 필드 | 실제 의미 | 분석/기능 사용 |
| --- | --- | --- |
| `id` | 로컬 DB 운동 ID | entry/program 연결 |
| `name` | 사용자에게 보이는 운동명 | 검색, 기록, 계획 표시 |
| `category` | 큰 분류 | 근력운동, 기능성운동, 유산소운동, 스포츠 |
| `detail1`, `detail2` | 보조 설명 | 검색, 상세 표시 |
| `mode` | 기록 방식 힌트 | 시간 기반 운동 판단 |
| `description` | 사용자 설명 | 상세 카드 |
| `defaultRestSeconds` | 기본 휴식 | entry 생성, program item 생성 |
| `familyId`, `familyName` | 운동 계열 | 변형 운동 비교 |
| `familyRole` | 계열 내 역할 | 주 운동/보조 운동 판단 |
| `familyE1rmMultiplier` | e1RM 변환 보정 | 향후 강도 추정 |
| `stableKey` | seed 안정 키 | 중복 방지, seed refresh |
| `movementPattern` | 움직임 패턴 | push, pull, squat 등 균형 분석 |
| `movementCategory` | 움직임 성격 | strength, plyometric 등 |
| `primaryMuscles` | 주 사용 근육 | 근육별 부하 |
| `secondaryMuscles` | 보조 근육 | 보조 부하 |
| `equipmentTags` | 장비 | 가능한 운동 필터 |
| `forceType` | 힘의 방향/성격 | 패턴 분석 |
| `bodyRegion` | 신체 영역 | 상체/하체/전신 균형 |
| `laterality` | 양측/편측 | 좌우 균형, 부상 위험 렌즈 |
| `trainingRole` | 훈련 목적 | strength, hypertrophy 등 렌즈 |
| `stabilityRoles` | 안정성 요구 | 코어, 착지, 균형 |
| `sportTransferDirect` | 스포츠 직접 전이 | 배드민턴 등 종목 렌즈 |
| `sportTransferSupportive` | 스포츠 보조 전이 | 보조 체력 렌즈 |
| `accessoryRoles` | 보조 운동 역할 | 약점 보완, 보디빌딩 보조 |
| `loadProfile` | 부하 성격 | 피로/회복 해석 |
| `metadataConfidence` | taxonomy 신뢰도 | 분석 확신도 조절 |

주의:

- 한국어 표시값과 engine-facing metadata를 섞지 않는다.
- 분석에는 정규화된 taxonomy token을 우선 사용한다.
- taxonomy가 낮은 신뢰도면 단정적 추천을 피한다.

## WorkoutEntry

`WorkoutEntry`는 특정 날짜의 특정 운동 묶음이다.  
실제 수행 여부는 entry가 아니라 하위 `WorkoutSet.confirmed`가 결정한다.

| 필드 | 실제 의미 | 분석/기능 사용 |
| --- | --- | --- |
| `date` | 기록/계획 날짜 | 날짜별 기록, 프로그램 적용 |
| `exerciseId` | 연결된 운동 ID | taxonomy 연결 |
| `exerciseName` | 표시용 운동명 스냅샷 | 운동명이 바뀌어도 기록 표시 유지 |
| `category` | 표시용 카테고리 스냅샷 | 스포츠 입력 완화 등 |
| `restSeconds` | 운동 단위 휴식 | 밀도/휴식 분석 후보 |
| `notes` | 메모 또는 프로그램 처방 | 기록/계획 설명 |
| `rpe` | 운동 단위 체감 강도 | 향후 강도/회복 판단 |
| `maxReps` | 최대 반복 메모 | 향후 성과 추정 |
| `createdAt` | 생성 시각 | 같은 날짜 표시 순서 |
| `completedAt` | confirmed set 존재 시 완료 시각 | 완료 상태 표시 |

스포츠 카테고리는 `weight`, `rpe`, `maxReps` 입력을 강제하지 않는다.

## WorkoutSet

`WorkoutSet`은 이 앱에서 가장 중요한 테이블이다.

| 필드 | 실제 의미 | 분석/기능 사용 |
| --- | --- | --- |
| `entryId` | 소속 운동 entry | 날짜/운동 묶음 |
| `setIndex` | 세트 순서 | UI 표시, 삭제 후 재정렬 |
| `reps` | 반복 수 | 볼륨 계산 |
| `weightKg` | 중량 kg | 볼륨 계산 |
| `seconds` | 수행 시간 | 유산소/스포츠/기능성 시간 |
| `confirmed` | 계획/실제 기록 구분 | false=계획, true=실제 |
| `manualWeight` | 사용자가 중량을 직접 지정했는지 | 향후 자동 중량 추천 구분 |

세트 추가 규칙:

```text
마지막 set의 reps, weightKg, seconds 복사
confirmed=false
```

세트 삭제 규칙:

```text
마지막 1개 삭제 금지
삭제 후 setIndex를 1부터 재정렬
```

## DailyMetric

날짜별 컨디션 데이터다.

| 필드 | 실제 의미 | 분석/기능 사용 |
| --- | --- | --- |
| `date` | 날짜 | 운동 기록 날짜와 연결 |
| `sleepHours` | 수면 시간 | 회복 렌즈 |
| `bodyWeightKg` | 체중 | 체중 변화, 상대 중량 |
| `updatedAt` | 수정 시각 | 동기화/표시 기준 |

Phase 2.5에서는 저장과 표시만 한다. 고급 회복 판단은 아직 구현하지 않는다.

## TrainingProgram

기본 또는 사용자 생성 프로그램의 헤더다.

| 필드 | 실제 의미 | 분석/기능 사용 |
| --- | --- | --- |
| `id` | 프로그램 ID | item 연결 |
| `name` | 프로그램명 | 목록 표시, seed 중복 방지 |
| `durationDays` | 프로그램 기간 | 적용 범위 설명 |
| `createdAt` | 생성 시각 | 목록 정렬 |

seed refresh는 같은 기본 프로그램명이 있으면 중복 삽입하지 않는다.

## TrainingProgramItem

프로그램 안의 처방 항목이다.  
날짜에 적용하면 `WorkoutEntry`와 `WorkoutSet`으로 복사된다.

| 필드 | 실제 의미 | 적용 결과 |
| --- | --- | --- |
| `programId` | 소속 프로그램 | program 연결 |
| `weekNumber` | 주차 | 날짜 계산 |
| `dayOfWeek` | 요일 번호 | 날짜 계산 |
| `orderIndex` | 요일 내 순서 | entry 생성 순서 |
| `exerciseId` | 연결 운동 ID | `WorkoutEntry.exerciseId` |
| `exerciseName` | 운동명 스냅샷 | `WorkoutEntry.exerciseName` |
| `category` | 카테고리 스냅샷 | `WorkoutEntry.category` |
| `restSeconds` | 휴식 처방 | `WorkoutEntry.restSeconds` |
| `prescription` | 처방 메모 | `WorkoutEntry.notes` |
| `setCount` | 생성할 세트 수 | `WorkoutSet` 개수 |
| `reps` | 계획 반복 수 | `WorkoutSet.reps` |
| `weightKg` | 계획 중량 | `WorkoutSet.weightKg` |
| `seconds` | 계획 시간 | `WorkoutSet.seconds` |

중요:

```text
TrainingProgramItem -> WorkoutSet 변환 시 confirmed=false
```

## Program Apply Conflict

프로그램 적용 대상 날짜에 기존 기록이 있으면 사용자가 선택한다.

| 선택 | 데이터 처리 |
| --- | --- |
| 덮어쓰기 | 기존 set 삭제 후 entry 삭제, 이후 계획 생성 |
| 추가 | 기존 기록 보존, 계획 추가 |
| 취소 | 아무 변경 없음 |

덮어쓰기 전 UI는 삭제될 수 있는 기존 완료 세트 수를 보여준다.  
이는 실제 수행 기록을 지울 수 있기 때문이다.

## Analysis Current Mapping

현재 분석 화면은 단순 통계다.

| 지표 | 계산 |
| --- | --- |
| confirmed set 수 | `WorkoutSet.confirmed = true` 개수 |
| 총 볼륨 | confirmed set의 `reps * weightKg` 합 |
| 총 시간 | confirmed set의 `seconds` 합 |

계획 세트는 포함하지 않는다.

## Future Analysis Pipeline

고급 분석은 아직 구현하지 않는다.  
설계 문서는 다음 파일에 있다.

```text
docs/analysis_algorithm_design.md
```

향후 파이프라인:

```text
Raw Data
-> Feature Extraction
-> Signal Generation
-> Judgment
-> Recommendation
-> Narrative Rendering
```

분석 렌즈:

- General Fitness
- Strength
- Hypertrophy
- Badminton
- Recovery
- Balance / Injury Risk
- Plan Adherence

한국어 분석 문장은 짧고 조심스럽게 쓴다.  
단정 대신 가능성과 다음 행동을 제시한다.

## Update Rules

데이터 의미 변경 시 반드시 확인한다.

- confirmed 의미가 유지되는가
- 사용자 기록이 삭제되지 않는가
- seed refresh가 중복을 만들지 않는가
- 분석이 planned set을 실제 부하로 보지 않는가
- taxonomy token이 표시용 한국어와 섞이지 않는가
- 문서가 코드와 같은 의미를 말하는가

## Phase 2.6 Record Timer Mapping

Phase 2.6에서 휴게 타이머가 추가되었지만 DB 의미는 바뀌지 않았다.

타이머 트리거:

```text
WorkoutSet.confirmed false -> true
```

이 전환은 다음 두 가지 의미를 동시에 가진다.

- 실제 수행 기록으로 확정
- 해당 entry의 `restSeconds` 기준 휴게 타이머 시작 후보

타이머가 시작되지 않는 경우:

- `confirmed=true` 세트를 다시 수정하는 경우
- `confirmed=true -> false`로 해제하는 경우
- `restSeconds <= 0`인 경우

타이머는 DB 훈련 부하를 계산하지 않는다.  
훈련 부하 계산은 계속 `confirmed=true` 세트만 사용한다.

### Rest Timer Session Data

휴게 타이머 세션 상태는 Room DB가 아니라 app preferences에 저장한다.

| key | 의미 |
| --- | --- |
| `rest_end_at` | 휴게 종료 wall-clock timestamp |
| `rest_next` | 다음 세트 / 다음 운동 안내 |
| `rest_target_date` | 기록 탭으로 돌아갈 날짜 |
| `rest_target_entry_id` | 기록 탭에서 목표 entry |
| `rest_finished` | 타이머 종료 상태 |

이 값들은 사용자 운동 기록이 아니다.  
앱 표면 알림과 오버레이 복구를 위한 UI/session 상태다.

### Weight Editing Meaning

Phase 2.6에서 일괄 kg와 빈 세트 kg 적용이 추가되었다.

일괄 kg:

- 같은 `WorkoutEntry`의 모든 set `weightKg`를 사용자가 입력한 값으로 변경
- `manualWeight=true`
- 스포츠 / 유산소 entry에는 노출하지 않음

빈 세트 kg 적용:

- source set에 0보다 큰 kg가 입력될 때 후보 UI 표시
- 적용 대상은 `weightKg == 0`, `manualWeight == false`, `confirmed == false`
- confirmed=true 세트는 자동 덮어쓰지 않음

이 기능은 입력 편의 기능이다.  
운동 seed, taxonomy, 분석 알고리듬 의미를 바꾸지 않는다.

## Phase 2.6 Set RPE / Rest Override Mapping

### WorkoutSet.rpe

`WorkoutSet.rpe`가 세트별 RPE의 canonical 위치다.

- null이면 해당 세트의 RPE는 기록되지 않은 상태
- 0~10 범위만 유효
- 새 세트 추가 시 이전 세트의 RPE를 자동 복사하지 않음
- 기존 `WorkoutEntry.rpe` 값을 migration에서 세트로 강제 복사하지 않음

분석 우선순위:

1. `WorkoutSet.rpe`
2. 없을 때만 `WorkoutEntry.rpe`를 legacy fallback 또는 운동 전체 체감으로 사용

RPE/RIR-aware e1RM은 `WorkoutSet.rpe` 기준으로 계산해야 한다.  
RPE가 없는 세트는 raw reps / weight 기반 추정만 가능하다.  
RPE 7 미만 세트는 PR-like e1RM 근거로 약하게 보거나 제외할 수 있다.

### WorkoutSet.restSecondsOverride

`WorkoutEntry.restSeconds`는 운동 entry의 기본 휴식시간이다.  
`WorkoutSet.restSecondsOverride`는 특정 세트만 다른 휴식시간을 쓴다는 예외값이다.

타이머 기준:

```text
effectiveRest = WorkoutSet.restSecondsOverride ?: WorkoutEntry.restSeconds
```

세트 확인 시 타이머는 effective rest가 0보다 클 때만 시작한다.

### WorkoutEntry.notes

`WorkoutEntry.notes`는 자유 메모 또는 코치성 메모다.

저장 가능한 예:

- 무릎 안쪽 말리지 않기
- 오늘은 가볍게
- 통증 있으면 중단

저장하지 않는 것:

- 3세트 x 10회
- 4세트 x 5회 @ 80kg
- 30초 x 5세트

세트수 / 반복수 / 중량 / 시간의 canonical 위치는 `WorkoutSet`이다.  
프로그램 처방의 구조화 원천은 `TrainingProgramItem.setCount`, `reps`, `weightKg`, `seconds`다.

## Phase 2.7 Calendar Summary Mapping

월간 캘린더 summary는 UI용 파생 데이터다. DB 기록 의미를 바꾸지 않는다.

`DailyRecordSummary` 의미:

| 필드 | 의미 |
| --- | --- |
| `date` | 요약 대상 날짜 |
| `confirmedSets` | `confirmed=true` 세트 수 |
| `plannedSets` | `confirmed=false` 세트 수 |
| `totalVolumeKg` | `confirmed=true` 세트의 `reps * weightKg` 합 |
| `totalSeconds` | `confirmed=true` 세트의 `seconds` 합 |
| `entryCount` | 날짜의 운동 entry 수 |
| `categorySummary` | 날짜 운동 category 요약 |
| `bodyPartSummary` | `Exercise.bodyRegion` 기반 보조 요약 후보 |

캘린더 cell은 실제 훈련량을 `confirmed=true`로만 표시한다.  
`confirmed=false`만 있는 날짜는 실제 기록이 아니라 `계획`으로 표시한다.

완료 운동 상단 정렬은 UI sorting이다.  
DB row 순서, `createdAt`, `entry id`, `setIndex`를 바꾸지 않는다.

휴게 타이머 row 표시를 위해 `RestTimerState.targetSetId`를 추가했다.  
이 값은 UI/session 상태이며 운동 기록 데이터가 아니다.

## Phase 2.7.2 Calendar Lifecycle Mapping

달력 lifecycle 작업은 `WorkoutEntry`와 `WorkoutSet` row를 복사 / 이동 / 삭제한다. DB schema는 변경하지 않았다.

### 계획으로 복사

- 원본 날짜의 entry / set을 대상 날짜로 복사
- 모든 복사 set은 `confirmed=false`
- `completedAt=null`
- DailyMetric은 복사하지 않음

### 기록상태까지 복사

- 원본 날짜의 entry / set을 대상 날짜로 복사
- set의 confirmed 상태를 보존
- 완료 set이 있으면 copied entry의 `completedAt`을 새로 계산
- DailyMetric은 복사하지 않음

### 이동

- 원본 entry / set을 대상 날짜로 복사
- confirmed 상태 보존
- 이후 원본 날짜의 운동 entry / set 삭제
- DailyMetric은 이동하지 않음

### 삭제

- 사용자가 확인한 날짜의 `WorkoutSet` 삭제
- 이후 `WorkoutEntry` 삭제
- DailyMetric은 삭제하지 않음

### 선택복사

- 원본 날짜 범위를 대상 시작 날짜부터 같은 길이로 복사
- 모든 set은 `confirmed=false`
- DailyMetric은 복사하지 않음

### 충돌 처리

| 선택 | 의미 |
| --- | --- |
| 덮어쓰기 | 대상 날짜 / 범위의 기존 set 삭제 후 entry 삭제, 이후 작업 실행 |
| 추가 | 기존 대상 기록 보존 후 뒤에 추가 |
| 취소 | 아무 변경 없음 |

덮어쓰기와 삭제 전에는 기존 완료 세트 수를 보여준다.  
실제 훈련 부하 집계는 계속 `confirmed=true` 세트만 사용한다.

## Phase 3.0.0 Analysis V3 Data Mapping

V3 analysis input keeps actual records and future plans separate.

| V3 field | Source | Meaning |
| --- | --- | --- |
| `today` | `AnalysisDateProvider.today()` | Device-current analysis date, default `LocalDate.now()` |
| `completedEntriesUntilToday` | `WorkoutEntry.date <= today` + `WorkoutSet.confirmed=true` | Actual performed training load only |
| `plannedEntriesFromTomorrow` | `WorkoutEntry.date > today` + `WorkoutSet.confirmed=false` | Future plan projection only |
| `conditionRecordsUntilToday` | `DailyMetric.date <= today` | Sleep and body weight context |
| `exerciseMetadataMap` | `Exercise` taxonomy columns | Metadata-driven classification source |
| `recentWindow` | `AnalysisWindows.recent7Days` | Today-inclusive recent 7-day window |
| `futureWindow` | `AnalysisWindows.future7Days` | Tomorrow-inclusive future 7-day window |

Metric rules:

- Completed load metrics do not use unconfirmed sets.
- Future plan metrics do not use confirmed completed load.
- Taxonomy metrics use metadata fields, not exercise-name parsing.
- Missing taxonomy values are counted as `UNKNOWN`.
- Candidate metrics are not final judgments.

## Phase 3.1.1 Fatigue Metadata Mapping

New `Exercise` fields support future Today Fatigue / Readiness analysis.

| Field | Meaning |
| --- | --- |
| `compoundType` | compound / isolation / hybrid / drill classification |
| `plane` | sagittal / frontal / transverse / multi-planar |
| `axialLoadLevel` | none / low / moderate / high axial loading |
| `badmintonTransferRoles` | badminton transfer roles such as footwork, reaction, deceleration |
| `fatigueCategories` | fatigue channel tags such as systemic, neural speed, deceleration |
| `adaptiveBaselineGroups` | adaptive baseline buckets for future rolling baselines |
| `recoveryDecayProfile` | minimal / short / medium / long / very long decay profile |
| `systemicLoadWeight` | systemic fatigue contribution, 0.0 to 1.0 |
| `neuralHeavyWeight` | heavy neural fatigue contribution, 0.0 to 1.0 |
| `neuralSpeedWeight` | speed/reactive neural contribution, 0.0 to 1.0 |
| `localLoadWeight` | local muscle fatigue contribution, 0.0 to 1.0 |
| `decelerationWeight` | deceleration fatigue contribution, 0.0 to 1.0 |
| `elasticSscWeight` | elastic SSC fatigue contribution, 0.0 to 1.0 |
| `rotationPowerWeight` | rotation power fatigue contribution, 0.0 to 1.0 |
| `antiRotationWeight` | anti-rotation fatigue contribution, 0.0 to 1.0 |
| `overheadSwingWeight` | overhead/shoulder repetition contribution, 0.0 to 1.0 |
| `gripLoadWeight` | grip/forearm fatigue contribution, 0.0 to 1.0 |

Weight scale:

- `0.0`: nearly no contribution
- `0.25`: low
- `0.5`: medium
- `0.75`: high
- `1.0`: very high

The built-in seed catalog maps all 214 exercises through structured seed columns and passes `MetadataSanityChecker`.

## Phase 3.1.2 Analysis Readiness Mapping

New progress fields:

- `progressMetricType`: chooses estimated 1RM, volume load, reps-at-load, time/distance, max-rep test, quality-based, or excluded progress mode.
- `strengthProgressionGroup`: strength progression grouping.
- `hypertrophyVolumeGroup`: hypertrophy volume grouping.
- `mainLiftGroup`: main lift family.
- `accessoryContributionGroup`: accessory contribution family.
- `estimated1RmEligible`: whether estimated 1RM can be calculated.
- `volumeLoadEligible`: whether volume load is meaningful.

New badminton fields:

- `badmintonTransferStrength`: none/general/supportive/direct.
- `courtMovementTypes`: court movement tags.
- `badmintonSkillTargets`: skill transfer targets.

New balance/safety fields:

- `jointStressTags`
- `stabilityDemandLevel`
- `mobilityDemandLevel`
- `balanceContributionTags`

New inclusion field:

- `analysisEligibility`: multi-value eligibility for fatigue, strength progress, hypertrophy volume, badminton transfer, balance, recovery-only, test-only, or exclusion.

`ExerciseAnalysisMapper` converts these fields plus optional workout records into `AnalysisExerciseFeatures`.

## Phase 3.1.3 Today Readiness Data Mapping

Today Readiness uses actual completed records only.

Input boundary:

| Input | Included? | Reason |
| --- | --- | --- |
| `WorkoutSet.confirmed=true`, `date <= today` | yes | actual completed training |
| `WorkoutSet.confirmed=false`, `date <= today` | no | still planned or unconfirmed |
| `WorkoutSet.confirmed=false`, `date > today` | no | future plan, not today's fatigue |
| `TrainingProgramItem` | no direct use | program items affect readiness only after copied into confirmed workout sets |
| `DailyMetric.sleepHours` | yes, if present | recovery context |
| pain inputs | supported by evaluator, no normal UI field yet | gate, not average score |

Feature extraction:

```text
Exercise + WorkoutEntry + WorkoutSet
-> ExerciseAnalysisMapper
-> AnalysisExerciseFeatures
```

The readiness engine uses structured metadata fields:

- fatigue weights
- fatigue categories
- adaptive baseline groups
- recovery decay profile
- primary and secondary muscles
- badminton transfer strength
- court movement types
- analysis eligibility

It must not classify exercise type by checking exercise-name strings.

Load mapping:

| Output load | Source |
| --- | --- |
| `SYSTEMIC` | `baseDose * systemicLoadWeight` |
| `NEURAL_HEAVY` | `baseDose * neuralHeavyWeight` |
| `NEURAL_SPEED` | `baseDose * neuralSpeedWeight` |
| `LOCAL_MUSCLE` | `baseDose * localLoadWeight` |
| `DECELERATION` | `baseDose * decelerationWeight` |
| `ELASTIC_SSC` | `baseDose * elasticSscWeight` |
| `ROTATION_POWER` | `baseDose * rotationPowerWeight` |
| `ANTI_ROTATION` | `baseDose * antiRotationWeight` |
| `OVERHEAD_REPETITION` | `baseDose * overheadSwingWeight` |
| `GRIP_FOREARM` | `baseDose * gripLoadWeight` |
| `BADMINTON_COURT` | speed + deceleration + elastic + overhead + grip + transfer bonus |

Adaptive baseline meaning:

- tolerance is not a fixed global threshold.
- tolerance is recalculated by category, baseline group, and body part.
- high load by itself does not increase tolerance.
- high load with stable recovery and no pain/performance drop can increase tolerance slightly.
- failed recovery or performance drop blocks tolerance increases.

UI mapping:

- `TodayReadinessSummary.status` drives the Analysis tab readiness label.
- `primaryReasons` explains the top signals.
- `recommendedModes` suggests what to do.
- `restrictedModes` lists what to adjust.
- `detailSections` provide deeper metrics without replacing the simple confirmed-set statistics.

## Phase 3.2.0 Performance Trend Data Mapping

Performance Trend uses completed weekly records only.

Input boundary:

| Source | Use in 3.2 |
| --- | --- |
| `WorkoutSet.confirmed=true` | included |
| `WorkoutSet.confirmed=false` | excluded |
| future planned workout | excluded |
| `Exercise` metadata | classification source |
| `DailyMetric.sleepHours` | fatigue/recovery penalty context |
| `DailyMetric.bodyWeightKg` | bodyweight volume proxy when available |

Weekly unit:

- one data point is one week.
- week start defaults to Monday.
- first-screen charts show 8 weeks by default and 12 weeks when history is available.

Standardized score meaning:

| Value | Meaning |
| --- | --- |
| near `100` | near personal recent baseline |
| above `100` | above recent baseline |
| below `100` | below recent baseline |

This is not a public score grade.
The UI should show trend direction rather than emphasize the number.

Strength performance:

```text
StrengthPerformanceIndex =
0.50 * StrengthIntensityIndex
+ 0.40 * StrengthVolumeIndex
+ 0.10 * StrengthEfficiencyIndex
```

Badminton training:

```text
BadmintonTrainingIndex =
0.60 * CourtVolumeIndex
+ 0.25 * FootworkReactiveIndex
+ 0.15 * BadmintonSupportIndex
```

This is badminton-related training volume, not badminton skill.

Fatigue composite:

```text
FatigueCompositeIndex =
0.60 * AverageStandardizedFatigue
+ 0.25 * MaxCategoryFatigue
+ 0.15 * RecoveryPerformancePenaltyScore
```

This uses 3.1-style standardized pressure, percentile, z-score, and recovery/performance signals.
It is not raw load summation.

Detail chart data:

- trend mode uses standardized weekly line series.
- composition mode uses component contribution shares.
- contribution mode compares recent 4 weeks against previous 4 weeks.
- ranking mode shows top recent contributors.
- relationship mode uses weekly scatter points.

Relationship analysis:

- Pearson correlation requires enough weekly points.
- wording must stay as trend/correlation language.
- it must not claim causality.
## Minor Patch Note: Record Date Switcher Width

This UI-only patch changes the Record tab date navigation button width and date wrapping behavior.

Data meaning impact:

- None.
- `WorkoutSet.confirmed=false` still means planned set.
- `WorkoutSet.confirmed=true` still means completed record set.
- Analysis aggregation rules are unchanged.

## Phase 3.3.0 Badminton Transfer Analysis Mapping

This patch adds `배드민턴 전이 분석`.

Data inputs:

- `WorkoutSet.confirmed=true` sets only
- `WorkoutSet.confirmed=false` planned sets are excluded
- `ExerciseAnalysisMapper` structured metadata
- 3.1 `TodayReadinessSummary` for fatigue-aware recommendation gating

Transfer type mapping:

| Metadata | Transfer Type |
| --- | --- |
| `badmintonTransferStrength=DIRECT` | direct |
| `badmintonTransferStrength=SUPPORTIVE` | supportive |
| `badmintonTransferStrength=GENERAL` | general_strength |
| badminton metadata exists but no stronger type | low |
| no badminton metadata | none |

Transfer axis mapping:

| Axis | Structured metadata source |
| --- | --- |
| `deceleration_landing` | fatigue categories, court movement, force type, transfer roles |
| `unilateral_stability` | laterality, balance tags, lunge reach metadata |
| `lateral_movement` | court movement, footwork roles, movement pattern, plane |
| `rotation_control` | rotation / anti-rotation metadata |
| `lower_body_strength` | squat / hinge / lunge patterns and lower-body baseline groups |
| `racket_support` | overhead, shoulder, grip, forearm metadata |
| `aerobic_footwork` | conditioning, skill, footwork persistence metadata |
| `low_fatigue_control` | prehab, stability, mobility, recovery metadata |

Meaning rules:

- The module reports transfer stimulus distribution, not match-performance improvement.
- The first card shows one recommendation sentence only.
- Detail view may show transfer axis share, transfer type share, 7-day vs 28-day comparison, and Top 5 transfer stimulus exercises.
- Exercise names are display values only; they are not used for classification.

## v0.3.4.0 Backup / Restore Mapping

Home `기록 백업` exports restore-format CSV.

Restore-format rows map as follows:

| CSV | DB |
| --- | --- |
| `row_type=daily`, `date`, `sleep_hours`, `body_weight_kg` | `DailyMetric` upsert |
| `row_type=set`, entry columns | `WorkoutEntry` |
| `set_index`, `set_confirmed`, `reps`, `weight_kg`, `seconds`, `rpe` | `WorkoutSet` |

Confirmed semantics:

- `set_confirmed=0` imports as `WorkoutSet.confirmed=false`.
- `set_confirmed=1` imports as `WorkoutSet.confirmed=true`.
- missing `set_confirmed` falls back to entry-level `confirmed`.

Legacy `daily_timeseries` import:

- `sleep_hours` and `body_weight_kg` map to `DailyMetric`.
- invalid numeric values become empty values and must not crash import.
- daily aggregate totals map to generated category-level `WorkoutEntry` and confirmed `WorkoutSet` rows.
- generated aggregate rows are marked with `WorkoutEntry.notes = "CSV daily_timeseries import"`.

Limit:

- `daily_timeseries` lacks exact exercise and set detail.
- imported aggregate records preserve date-level continuity for Home, Record, Analysis, and recommendation inputs, but they are not exact historical set reconstruction.

## v0.3.4.1 Program Generator Data Meaning

Program definition fields:

| Field | Meaning |
| --- | --- |
| `TrainingProgram.goal` | User-facing program goal used by the skeleton generator. |
| `weeklyTrainingDays` | Planned sessions per week for generated skeletons. |
| `sessionMinutes` | Rough time budget used to cap item count. |
| `availableEquipment` | Comma-separated equipment tokens available to the user. |
| `excludedExerciseText` | User-entered exclusion text. Used only as a direct text filter. |
| `badmintonTransferRatio` | Target share for badminton-transfer-supportive work. |
| `sportStrengthRatio` | UI-selected sport/strength emphasis label such as `70/30`. |
| `periodizationType` | Selected or auto-chosen periodization style. |

Generator mapping:

- Exercise selection is based on structured `Exercise` metadata.
- Metadata sources include movement pattern, training role, badminton transfer strength, fatigue categories, equipment, axial load, and analysis eligibility.
- Exercise names are display values and are not used for classification.
- The only name-based filter is the user's explicit exclusion text.

Weight source mapping:

| Source label | Meaning |
| --- | --- |
| `DIRECT_HISTORY_HIGH` | Recent confirmed same-exercise records support the kg suggestion. |
| `DIRECT_HISTORY_MEDIUM` | Same-exercise history exists, but confidence is lower. |
| `SIMILAR_EXERCISE_LOW` | Similar metadata history supports a conservative kg suggestion. |
| `EMPTY_NEEDS_MANUAL_INPUT` | No reliable kg suggestion; user should enter kg manually. |

Program application mapping:

- `TrainingProgramItem` copies to `WorkoutEntry` plus planned `WorkoutSet` rows.
- Program-created sets always import as `WorkoutSet.confirmed=false`.
- `confirmed=false` continues to mean planned but not yet completed.
- `confirmed=true` continues to mean completed record.
- Program overwrite deletes planned-only rows only.
- Entries containing any confirmed set are preserved.

## Record Calendar Range Delete Mapping

Long-press calendar `select delete` maps to date-range deletion.

| User choice | DB behavior |
| --- | --- |
| unconfirmed only | delete `WorkoutSet` rows where `confirmed=false` in the selected date range |
| including confirmed | delete all `WorkoutSet` and `WorkoutEntry` rows in the selected date range |

Preservation rules:

- DailyMetric rows are not deleted.
- When only unconfirmed sets are deleted, confirmed sets remain actual training records.
- Remaining sets in an entry are reindexed from `setIndex=1`.
- Empty entries are removed after their last unconfirmed set is deleted.
