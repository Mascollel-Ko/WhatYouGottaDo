# Phase 2.7.2 Calendar Copy / Delete Recovery Patch

작성일: 2026-06-15

## 목적

기존 앱 달력의 기록 lifecycle 기능을 Kotlin / Compose 앱에 복원했다. DB schema, CSV, 고급 분석은 변경하지 않았다.

## 기존 문서에서 확인한 요구

- 월간 달력과 날짜별 운동 표시
- 날짜 길게 누른 뒤 대상 날짜로 복사
- 날짜 길게 누른 뒤 기록상태까지 복사
- 날짜 이동
- 날짜 삭제
- 삭제 영역 드롭 삭제
- 선택복사
- 대상 날짜 충돌 시 덮어쓰기 / 추가 / 취소
- 붙여넣은 항목은 기본적으로 미확인 계획 상태
- confirmed 의미 유지

Compose drag/drop은 안정성 리스크가 있어 long press + dialog + 대상 날짜 선택 방식으로 기능 동등성을 우선했다.

## UI 흐름

월간 캘린더에서 날짜를 길게 누르면 액션 dialog가 열린다.

액션:

- 계획으로 복사
- 기록상태까지 복사
- 이동
- 선택복사
- 삭제

복사 / 이동:

1. 원본 날짜 long press
2. 액션 선택
3. 대상 날짜 선택
4. 대상 날짜에 기존 기록이 있으면 충돌 dialog 표시
5. 덮어쓰기 / 추가 / 취소 선택

선택복사:

1. 원본 시작 날짜 long press
2. 선택복사 선택
3. 원본 끝 날짜 선택
4. 붙여넣을 시작 날짜 선택
5. 대상 범위 충돌 확인

삭제:

1. 원본 날짜 long press
2. 삭제 선택
3. 운동 수 / 세트 수 / 완료 세트 수 확인
4. 삭제 확정

## Repository / ViewModel / DAO

DAO 추가:

- `entriesWithSets(date)`
- `countDatesWithEntries(dates)`
- `countSetsOnDates(dates)`

Repository 추가:

- `calendarConflictSummary(dates)`
- `copyDate(sourceDate, targetDate, keepConfirmed, conflictMode)`
- `moveDate(sourceDate, targetDate, conflictMode)`
- `deleteDate(date)`
- `copyDateRangeAsPlan(sourceStart, sourceEnd, targetStart, conflictMode)`

ViewModel 추가:

- conflict summary 조회
- 날짜 복사
- 기록상태 복사
- 이동
- 삭제
- 선택복사

## 계획 복사

`계획으로 복사`는 원본 날짜의 `WorkoutEntry`와 `WorkoutSet`을 대상 날짜로 복사한다.

- 모든 복사 세트는 `confirmed=false`
- `completedAt=null`
- reps / weightKg / seconds / rpe / restSecondsOverride / manualWeight는 복사
- DailyMetric은 복사하지 않음

## 기록상태까지 복사

`기록상태까지 복사`는 confirmed 상태를 보존한다.

- `confirmed=true`는 true로 복사
- `confirmed=false`는 false로 복사
- 완료 세트가 있는 entry는 새 `completedAt`을 부여
- 실제 기록 복제이므로 일반 계획 복사와 UI 문구를 분리

## 이동

이동은 원본 날짜 기록을 대상 날짜로 옮긴다.

- confirmed 상태 보존
- 대상 추가 / 덮어쓰기 후 원본 날짜의 운동 entry / set 삭제
- DailyMetric은 이동하지 않음
- 동일 날짜 이동은 no-op 처리

## 삭제

삭제는 해당 날짜의 운동 기록만 삭제한다.

- `WorkoutSet` 먼저 삭제
- `WorkoutEntry` 삭제
- DailyMetric은 삭제하지 않음

삭제 전 표시:

- 날짜
- 운동 수
- 세트 수
- 완료 세트 수
- DailyMetric은 보존된다는 안내

## 충돌 처리

대상 날짜 또는 대상 범위에 기존 기록이 있으면 충돌 dialog를 표시한다.

- 덮어쓰기: 대상 set 삭제 후 entry 삭제, 이후 복사 / 이동
- 추가: 대상 기록 보존 후 뒤에 추가
- 취소: 아무 변경 없음

표시:

- 대상 날짜 수
- 기존 운동 수
- 기존 세트 수
- 기존 완료 세트 수

## confirmed 의미 보존

- 계획 복사: 모든 set `confirmed=false`
- 기록상태 복사: 원본 set의 confirmed 보존
- 이동: 원본 set의 confirmed 보존
- 선택복사: 모든 set `confirmed=false`
- 삭제: 사용자가 명시적으로 확인한 날짜의 운동 기록만 삭제

실제 훈련 부하 집계는 계속 `confirmed=true` 세트만 기준으로 한다.

## 월간 Summary 갱신

Room Flow 기반이다. copy / move / delete 후 `workout_entries`, `workout_sets`가 변경되면 다음 화면이 자동 갱신된다.

- 기록 탭 selected date
- 월간 캘린더 summary
- 홈 요약
- 단순 분석 통계

별도 analysis dirty flag 엔진은 아직 없다.

## 남은 제한

- drag/drop UI는 아직 복원하지 않았다.
- 삭제 영역 드롭 대신 long press + 삭제 dialog 방식이다.
- 선택복사는 기본 계획 복사만 지원한다.
- DailyMetric까지 복사 / 이동 / 삭제하는 옵션은 없다.

## 수동 QA

1. 날짜 A를 `계획으로 복사`해 날짜 B에 붙여넣기
2. B의 모든 세트가 `confirmed=false`인지 확인
3. 날짜 A를 `기록상태까지 복사`해 날짜 C에 붙여넣기
4. C의 confirmed 상태가 A와 같은지 확인
5. 날짜 A를 날짜 D로 이동
6. A 기록 삭제, D 기록 생성 확인
7. 날짜 D 삭제 전 운동 / 세트 / 완료 세트 수 표시 확인
8. 삭제 후 DailyMetric 보존 확인
9. 대상 충돌 시 덮어쓰기 / 추가 / 취소 확인
10. 선택복사로 날짜 범위 복사 확인
11. 복사 / 이동 / 삭제 후 월간 summary 갱신 확인

## 빌드

검증 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

결과: `BUILD SUCCESSFUL`.
