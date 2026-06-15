# Phase 2.7.2 Calendar Lifecycle Audit

작성일: 2026-06-15

## 1. 기존 README / handoff에서 확인한 기능

기존 기록 달력은 다음 lifecycle 기능을 제공했다.

- 월간 달력과 날짜별 운동 표시
- 날짜 길게 누른 뒤 다른 날짜에 드롭하여 복사
- 날짜 길게 누른 뒤 다른 날짜에 드롭하여 기록상태 복사
- 날짜 길게 누른 뒤 다른 날짜에 드롭하여 이동
- 삭제 영역에 드롭하여 날짜 기록 삭제
- 선택복사: 복사할 날짜 범위와 붙여넣을 시작 날짜 지정
- 원본 범위 길이만큼 대상 범위에 자동 붙여넣기
- 붙여넣기 대상 날짜에 기록이 있으면 덮어쓰기 / 추가 / 취소 선택
- 붙여넣은 항목은 기본적으로 미확인 계획 상태
- `confirmed` / `set_confirmed` 의미 유지

핵심 의미:

- `WorkoutSet.confirmed=false`: 계획 세트
- `WorkoutSet.confirmed=true`: 실제 수행 기록 세트
- 분석은 confirmed set만 실제 훈련 부하로 본다

## 2. 기존 Java 앱 담당 클래스 / 메서드

문서에서 확인한 담당 클래스:

- `RecordCalendarController.java`
  - 월간 달력
  - 날짜 선택
  - 날짜 drag/drop 복사 / 이동 / 삭제
  - 선택복사 UI 흐름
- `TrainingWorkoutEntryRepository.java`
  - 날짜별 기록 추가 / 조회
  - 날짜 / 항목 복사
  - 날짜 이동 / 삭제
  - 프로그램 적용

## 3. 새 Kotlin / Compose 대응 구조

- `RecordCalendarScreen.kt`
  - 월간 요약 표시
  - 날짜 long press / 메뉴 액션
  - 대상 날짜 선택 모드
  - 삭제 / 충돌 확인 dialog
- `TrainingRepository.kt`
  - `copyDate`
  - `moveDate`
  - `deleteDate`
  - `copyDateRangeAsPlan`
  - conflict summary 계산
- `TrainingViewModel.kt`
  - UI에서 repository 작업 호출
  - summary 조회 전달
- `WorkoutDao`
  - 날짜별 entry + set 조회
  - 날짜별 set / entry 삭제
  - 날짜 / 범위 conflict count

## 4. 이번 단계에서 복원할 기능

- 계획으로 날짜 복사
- 기록상태까지 날짜 복사
- 날짜 이동
- 날짜 삭제
- 선택복사
- 대상 날짜 충돌 처리
  - 덮어쓰기
  - 추가
  - 취소
- 삭제 전 경고
  - 운동 수
  - 세트 수
  - 완료 세트 수
- DailyMetric 보존

## 5. 구현하지 않는 기능과 이유

- drag/drop: Compose 달력 cell drag/drop은 안정성 리스크가 있어 long press + action dialog 방식으로 기능 동등성을 우선한다.
- DailyMetric 복사 / 이동 / 삭제: 수면과 체중은 날짜 고유 컨디션 데이터이므로 운동 기록 lifecycle 대상에서 제외한다.
- CSV 백업 / 복원: Phase 3 범위다.
- 고급 분석 dirty engine: 현재 앱은 Flow 기반 summary / home / analysis 갱신 구조다. 별도 dirty flag 엔진은 없다.
- DB schema 변경: 이번 단계 금지사항이다.

## 6. 수동 QA 체크리스트

기본 날짜 복사:

1. 기록이 있는 날짜 A를 선택
2. `계획으로 복사` 선택
3. 대상 날짜 B 선택
4. B에 같은 운동 / 세트 생성 확인
5. B의 모든 세트가 `confirmed=false`인지 확인

기록상태 복사:

6. confirmed=true 세트가 있는 날짜 A 선택
7. `기록상태까지 복사` 선택
8. 대상 날짜 C 선택
9. C의 confirmed 상태가 A와 같은지 확인

이동:

10. 날짜 A를 날짜 D로 이동
11. A의 운동 기록 삭제 확인
12. D에 기록 생성 확인
13. confirmed 상태 유지 확인

삭제:

14. 날짜 D 삭제 선택
15. 삭제 전 운동 수 / 세트 수 / 완료 세트 수 표시 확인
16. 삭제 후 WorkoutEntry / WorkoutSet 삭제 확인
17. DailyMetric 보존 확인

충돌:

18. 대상 날짜에 기존 기록이 있는 상태에서 복사 시도
19. 덮어쓰기 / 추가 / 취소 표시 확인
20. 취소 시 데이터 변경 없음 확인
21. 추가 시 기존 기록 보존 확인
22. 덮어쓰기 시 기존 set 삭제 후 새 기록 생성 확인

선택복사:

23. 원본 날짜 범위 선택
24. 붙여넣을 시작 날짜 선택
25. 범위 길이만큼 복사 확인
26. 모든 세트가 `confirmed=false`인지 확인
27. 대상 범위 충돌 dialog 확인
