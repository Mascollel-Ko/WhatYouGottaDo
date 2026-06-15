# Phase 2.6 Rest Timer Recovery

작성일: 2026-06-14

이번 작업은 새 타이머 설계가 아니라 기존 Java 앱의 휴게 타이머 구조를 Kotlin / Compose 앱에 기능 동등성 기준으로 복원하는 작업이다.

## 구성요소

- `RestTimerSessionController`: countdown state, end timestamp, next text, target record date / entry id, persisted preferences, foreground / background behavior, notification / overlay orchestration
- `RestTimerNotifier`: notification channel, running notification, finished notification
- `RestTimerOverlayController`: overlay drawing, dragging, remove, delete target, `dismissedForCurrentAwaySession`
- `RestTimerSoundVibration`: finish sound / vibration

## 유지한 preference key

```text
rest_end_at
rest_next
rest_target_date
rest_target_entry_id
rest_finished
```

## RecordScreen 연결

RecordScreen은 타이머 상태를 직접 소유하지 않는다.

- `WorkoutSet.confirmed=false -> true` 변경 이벤트만 controller에 전달
- target record date와 target entry id 전달
- next exercise text 전달
- effective rest 전달

effective rest:

```text
set.restSecondsOverride ?: entry.restSeconds
```

`confirmed=true` 세트를 단순 수정하는 경우 타이머를 중복 시작하지 않는다.  
확인 해제 시 기존 타이머를 임의 중지하지 않는다.

## Notification

- Android notification channel 생성
- running notification 표시
- finished notification 표시
- Android 13 이상에서 `POST_NOTIFICATIONS` 권한 확인
- 권한이 없으면 앱 내부 mini timer는 계속 동작
- 같은 notification id를 갱신하여 반복 알림을 만들지 않음

## Overlay

- `SYSTEM_ALERT_WINDOW` 권한 확인
- 앱 foreground에서는 표시하지 않음
- 앱 background이고 타이머가 active이며 권한이 있을 때 표시
- 앱 복귀 시 제거
- 남은 시간과 다음 세트 / 다음 운동 텍스트 표시
- 드래그 가능
- 닫기 / 삭제 target 제공

## Overlay Delete Suppression

- 앱 밖에서 오버레이를 닫거나 삭제 영역에 드롭하면 `dismissedForCurrentAwaySession=true`
- 같은 away session 동안 timer tick과 delayed retry가 오버레이를 다시 만들지 않음
- 앱으로 돌아오면 suppression reset
- 새 타이머가 시작되면 suppression reset

## 제한

- foreground service 수준의 장시간 백그라운드 타이머 서비스화는 아직 아니다.
- notification heads-up 동작은 기기 설정에 따라 다르다.
- overlay drag / delete는 실제 기기에서 수동 QA가 필요하다.
- notification / overlay click은 기록 탭 날짜 이동까지 MVP로 처리한다. entry focus scroll은 아직 정교하지 않다.

## 수동 QA

1. 세트 확인 시 mini timer 시작
2. 남은 시간 감소 확인
3. 중지 버튼 확인
4. 앱 background 전환
5. running notification 표시 확인
6. overlay 권한이 있으면 overlay 표시 확인
7. overlay drag 확인
8. overlay 닫기 / 삭제 확인
9. 같은 away session에서 overlay가 다시 뜨지 않는지 확인
10. 앱 복귀 후 suppression reset 확인
11. 새 타이머 시작 후 suppression reset 확인
12. 타이머 종료 시 finished notification / sound / vibration 확인
