# Phase 2.6 Legacy Record / Rest Timer Audit

## 1. 기존 README / handoff에서 확인한 기록 화면 요구

- 기록 흐름은 계획 세트와 수행 기록 세트를 분리한다.
- `WorkoutSet.confirmed=false`는 미확인 계획 세트다.
- `WorkoutSet.confirmed=true`는 실제 수행 기록 세트다.
- 붙여넣기 / 프로그램 적용 항목은 기본적으로 미확인 계획 상태다.
- 운동별 세트 확인 / 해제, 세트 추가 / 수정 / 삭제가 필요하다.
- 마지막 1개 세트 삭제는 차단한다.
- 세트 삭제 후 같은 운동 안의 `set_index`는 1부터 재정렬한다.
- `세트 +`는 마지막 세트의 중량 / 횟수 / 시간을 복사하되 새 세트는 미확인 상태로 추가한다.
- 일괄 중량 설정과 입력 단위 기준 일괄 증가 / 감소가 필요하다.
- 스포츠 부류는 RPE, 중량, 최대횟수를 강제하지 않는다.
- 시간 제한 / 최대횟수 성격 운동은 세트 확인 시 해당 세트의 시간 입력이 가능해야 한다.
- 사용자가 0kg을 직접 저장한 세트는 `manual_weight=1`로 보호한다.

## 2. 기존 README / handoff에서 확인한 rest timer 요구

- 세트 확인 시 휴게 타이머 시작과 다음 운동 안내가 필요하다.
- 앱 foreground에서는 앱 안 타이머만 표시하고 오버레이는 표시하지 않는다.
- 앱 background에서 휴게 타이머가 실행 중이면 진행 알림과 오버레이를 표시한다.
- 앱으로 돌아오면 오버레이를 제거한다.
- 휴게 종료 시 앱 안에서도 소리 / 진동이 필요하다.
- 앱 밖에서는 종료 알림을 표시한다.
- 운동 여부와 무관한 반복 알림은 만들지 않는다.
- 오버레이는 남은 시간과 다음 운동 / 다음 세트만 표시한다.
- 과거 spark 효과 같은 장식은 제거 대상이다.

## 3. 기존 Java 앱의 rest timer 구성요소

- `RestTimerSessionController.java`
  - active countdown timer
  - end timestamp
  - next exercise text
  - target record date
  - target entry id
  - finished / running state
  - persisted rest timer preferences
  - foreground / background behavior
  - running notification
  - finished notification
  - overlay show / remove
  - delayed retry after app background
  - finish alert handoff to `RestTimerSoundVibration`
- `RestTimerNotifier.java`
  - notification channel
  - running notification
  - finished notification
- `RestTimerOverlayController.java`
  - overlay drawing
  - overlay dragging
  - overlay remove
  - delete target
  - `dismissedForCurrentAwaySession`
- `RestTimerSoundVibration.java`
  - finish sound / vibration

기존 preference key:

```text
rest_end_at
rest_next
rest_target_date
rest_target_entry_id
rest_finished
```

## 4. 새 Kotlin / Compose 앱 대응 파일

- `RestTimerSessionController.kt`
  - 세션 상태, persisted preference, foreground/background, notification/overlay orchestration
- `RestTimerNotifier.kt`
  - notification channel, running / finished notification
- `RestTimerOverlayController.kt`
  - overlay drawing, dragging, remove, delete target, away-session suppression
- `RestTimerSoundVibration.kt`
  - finish sound / vibration
- `RestTimerState.kt`
  - Compose와 controller 사이에서 읽는 immutable timer state
- `RestTimerUi.kt`
  - 앱 안 mini timer bar, permission hint
- `MainActivity.kt`
  - lifecycle delegate와 notification / overlay click target navigation
- `RecordScreen.kt`
  - confirmed false -> true 이벤트에서 effective rest와 target 정보를 controller에 전달

## 5. 구현할 기능 목록

- Gradle problems report 충돌 우회 상태 유지
- `WorkoutSet.rpe` 추가
- `WorkoutSet.restSecondsOverride` 추가
- Room additive migration
- DailyMetric 입력을 기본 접힘 상태로 변경
- 운동 notes / maxReps / entry-level RPE / 기본 rest 편집을 상세 패널로 접기
- compact set row / compact number input 강화
- 세트별 RPE 입력
- 세트별 휴식 override 입력
- effective rest 기반 타이머 시작
- notes와 구조화 처방값 분리
- 일괄 설정 / 증가 / 감소 메뉴 확장
- 빈 kg 세트 자동 적용 prompt 유지 / 정리
- rest timer 구조 기능 동등성 점검
- 문서 갱신

## 6. 구현하지 않을 기능과 이유

- CSV 백업 / 복원: Phase 3 범위다.
- 고급 분석 / 추천 엔진: 별도 승인 전까지 금지다.
- 개인화 중량 자동 추천 엔진: 이번 범위는 빈 세트 적용 UX이며, 분석 기반 추천은 아니다.
- foreground service 수준의 장시간 타이머 서비스화: 이번 범위는 기존 구조 복구 MVP다.
- overlay 애니메이션 / spark 효과: 기존 문서에서 제거 대상으로 확인했다.

## 7. 수동 QA 체크리스트

기록 UI:

1. 기록 화면 상단에 수면 / 몸무게 입력칸이 항상 펼쳐져 있지 않은지 확인
2. `컨디션` 버튼으로 수면 / 몸무게 입력이 열리는지 확인
3. 운동 카드의 메모 / 최대횟수 / 운동 전체 체감 / 기본 휴식 편집이 기본 접힘인지 확인
4. 세트 행 높이와 입력칸 padding이 줄었는지 확인
5. 확인 버튼이 세트 입력 옆에 있는지 확인

세트별 RPE / 휴식:

6. 세트별 RPE 입력 / 비우기 확인
7. 앱 재시작 후 세트별 RPE 유지 확인
8. 세트 추가 시 RPE가 자동 복사되지 않는지 확인
9. 세트별 휴식 override 설정 / 비우기 확인
10. override가 없으면 entry 기본 휴식 사용 확인

메모 / 처방:

11. 프로그램 적용 후 notes에 구조화 처방값이 들어가지 않는지 확인
12. 운동 카드 요약이 notes가 아니라 WorkoutSet에서 계산되는지 확인

일괄 편집:

13. kg 설정 / 증가 / 감소 확인
14. 횟수 증가 / 감소 확인
15. 휴식 설정 / 증가 / 감소 확인
16. confirmed=true 포함 시 사용자 확인 확인
17. 빈 kg 세트 자동 적용이 confirmed=false, weightKg=0 세트만 바꾸는지 확인

휴게 타이머:

18. confirmed=false -> true에서만 타이머 시작 확인
19. effective rest = `set.restSecondsOverride ?: entry.restSeconds` 확인
20. 앱 안 mini timer 확인
21. background notification 확인
22. overlay 권한이 있을 때 background overlay 확인
23. overlay 삭제 후 같은 away session에서 다시 뜨지 않는지 확인
24. 앱 복귀 / 새 타이머에서 suppression reset 확인
25. 종료 알림 / 소리 / 진동 확인
