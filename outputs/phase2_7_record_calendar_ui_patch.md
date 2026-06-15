# Phase 2.7 Record Calendar UI Patch

작성일: 2026-06-15

## 목적

기록 탭의 마이너 UI / 사용성 개선을 진행했다. CSV 백업/복원과 고급 분석/추천 엔진은 구현하지 않았다.

## 상단 버튼 compact 배치

기록 탭 상단 액션을 조정했다.

- 날짜 이동 row: `이전날` / 날짜 / `다음날` / `달력`
- 컨디션 row: `컨디션` / `운동 추가`

`컨디션` 버튼은 수면 / 체중 입력을 여는 접힘 패널이다.  
입력값이 있으면 `컨디션 · 수면 7h · 80kg` 형식으로 요약한다.

## 세트 행 휴게 타이머 표시

상단의 큰 휴게 타이머 bar를 기록 화면에서 제거했다.  
현재 active timer는 target set row 보조행에 표시한다.

표시 예:

- `휴식 1:27`
- `휴식 45초`
- `휴식 종료`

작은 `종료` 액션으로 타이머를 중지할 수 있다.

source of truth:

- `RestTimerSessionController`

세트 행은 controller state를 구독해서 표시만 한다.  
타이머 state에 `targetSetId`를 추가해 active timer를 특정 세트 행에 매핑한다.

## 완료 운동 자동 상단 정렬

정렬은 UI 표시 순서만 바꾼다. DB row, `createdAt`, `entry id`, `setIndex`는 변경하지 않는다.

정렬 그룹:

1. 모든 세트가 `confirmed=true`인 운동
2. 일부 세트가 `confirmed=true`인 운동
3. 아직 확인된 세트가 없는 운동

각 그룹 안에서는 `createdAt`, `entry.id` 순서를 유지한다.

## 월간 캘린더 요약

`RecordCalendarScreen.kt`를 추가했다.

흐름:

- 기록 탭 상단 `달력` 버튼 클릭
- 월간 캘린더 화면 표시
- 이전달 / 다음달 / 오늘 이동
- 날짜 클릭 시 해당 날짜 기록 화면으로 이동
- 선택 날짜와 오늘 표시

날짜 cell 표시:

- 운동 종류 요약
- confirmed set 수
- confirmed volume
- confirmed 기록이 없고 planned set만 있으면 `계획` 표시

## 월간 Summary Query / Model

추가 모델:

```kotlin
DailyRecordSummary(
    date,
    confirmedSets,
    plannedSets,
    totalVolumeKg,
    totalSeconds,
    entryCount,
    categorySummary,
    bodyPartSummary
)
```

추가 query:

- `WorkoutDao.observeDailySummariesBetween(startDate, endDate)`

집계 기준:

- 총 세트 수: `confirmed=true`
- 총 볼륨: `confirmed=true` 세트의 `reps * weightKg`
- 총 시간: `confirmed=true` 세트의 `seconds`
- 계획 수: `confirmed=false`
- 운동 종류: 날짜의 `WorkoutEntry.category`
- body part 후보: `Exercise.bodyRegion`

`confirmed=false`는 실제 훈련 부하가 아니라 계획 / 미확인으로만 표시한다.

## 다크모드

지원됨.

확인 내용:

- `Theme.kt`에 `lightColorScheme` 있음
- `Theme.kt`에 `darkColorScheme` 있음
- `isSystemInDarkTheme()` 기준으로 MaterialTheme colorScheme 선택
- 새 캘린더 UI도 `MaterialTheme.colorScheme` 사용

남은 제한:

- 앱 내 수동 다크모드 토글은 구현하지 않았다.
- `Color.White`는 theme의 `onPrimary`, `onSecondary`, `onTertiary` 정의에서만 사용 중이다.

## 수동 QA 체크리스트

기록 상단:

1. `달력`이 `다음날` 오른쪽에 있는지 확인
2. `컨디션`, `운동 추가`가 compact row에 있는지 확인
3. `컨디션` 클릭 시 수면 / 체중 입력이 열리는지 확인
4. 저장 후 condition summary가 갱신되는지 확인

세트별 휴게 표시:

5. 세트 확인 시 해당 세트 행에 휴식 countdown 표시 확인
6. 상단 큰 timer bar가 항상 뜨지 않는지 확인
7. 작은 `종료` 액션으로 타이머 중지 확인
8. background notification / overlay 동작 유지 확인

완료 운동 정렬:

9. 모든 세트 완료 운동이 위쪽으로 이동하는지 확인
10. 일부 완료 운동이 그 다음에 표시되는지 확인
11. DB row 순서가 바뀌지 않는지 확인

월간 캘린더:

12. `달력` 클릭 시 월간 화면 표시 확인
13. 기록 날짜 cell에 종류 / 세트 / 볼륨 표시 확인
14. 계획만 있는 날짜는 `계획`으로 표시되는지 확인
15. 날짜 클릭 시 기록 화면 날짜 이동 확인
16. 오늘 버튼과 선택 날짜 강조 확인

다크모드:

17. 시스템 다크모드에서 기록 화면 확인
18. 캘린더 cell 대비 확인
19. 세트 row / chip / button 대비 확인

## 빌드

검증 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

결과: `BUILD SUCCESSFUL`.

## Phase 2.7.2 Calendar Lifecycle Follow-up

월간 캘린더 날짜 long press action을 추가했다.

- 계획으로 복사
- 기록상태까지 복사
- 이동
- 삭제
- 선택복사

drag/drop 대신 long press + dialog + 대상 날짜 선택 방식이다.

confirmed 처리:

- 계획 복사 / 선택복사: `confirmed=false`
- 기록상태 복사 / 이동: confirmed 보존

DailyMetric은 날짜 고유 컨디션이므로 복사 / 이동 / 삭제하지 않는다.

상세 문서:

- `outputs/phase2_7_2_calendar_lifecycle_audit.md`
- `outputs/phase2_7_2_calendar_lifecycle_patch.md`
