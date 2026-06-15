# WhatYouGottaTrain Phase 2.6 Record Timer Patch

작성일: 2026-06-14

## 목적

Phase 3 CSV 백업/복원으로 가기 전에 기록 화면 UX와 휴게 타이머 MVP를 복구했다.

이번 단계에서 CSV 백업/복원과 고급 분석/추천 엔진은 구현하지 않았다.

## 기존 설계 참조

기존 README / handoff의 rest timer 구조를 Kotlin + Compose 앱에 맞게 복원했다.

복원한 책임 분리:

- `RestTimerSessionController`
  - countdown timer state
  - end timestamp
  - next exercise text
  - target record date
  - target entry id
  - running / finished state
  - persisted rest timer preferences
  - foreground / background behavior
  - running notification
  - finished notification
  - overlay show / remove
  - delayed retry after app goes background
  - finish alert handoff to sound / vibration
- `RestTimerNotifier`
  - notification channel
  - running notification
  - finished notification
- `RestTimerOverlayController`
  - overlay drawing
  - overlay dragging
  - overlay remove
  - delete / drop target
  - `dismissedForCurrentAwaySession`
- `RestTimerSoundVibration`
  - finish sound
  - finish vibration

MainActivity는 다음만 담당한다.

- `onResume`
- `onPause`
- `onDestroy`
- notification / overlay click target navigation

## 기록 UI 변경

세트 행을 압축했다.

변경:

- 세트 번호, 횟수, kg, 초, 확인, 삭제를 한 줄에 배치
- 라벨을 짧게 변경
  - 반복횟수 -> 횟수
  - 중량 kg -> kg
  - 시간 초 -> 초
  - 수행 확인 -> 확인
- 확인된 세트는 작은 색 차이로 구분
- 스포츠 / 유산소 카테고리는 kg 입력을 숨김
- 근력 세트는 횟수와 kg가 먼저 보이도록 배치

## 수행 확인 UX

동작:

- 확인 체크 시 `WorkoutSet.confirmed=true`
- 확인 해제 시 `WorkoutSet.confirmed=false`
- `false -> true` 전환 순간에만 휴게 타이머 시작 후보
- 이미 confirmed=true인 세트를 수정해도 타이머를 중복 시작하지 않음
- 홈 / 분석 단순 통계는 기존 Flow와 DAO 집계로 자동 갱신

유지한 의미:

```text
confirmed=false : 계획 세트
confirmed=true  : 실제 수행 기록 세트
```

## 일괄 중량 편집

운동 카드 안에 `일괄 kg` 버튼을 추가했다.

동작:

- kg 값을 입력하면 해당 운동의 모든 세트 `weightKg`를 변경
- 0kg 입력도 허용
- 수동 입력 의미를 보존하기 위해 적용 대상 set의 `manualWeight=true`
- 스포츠 / 유산소 카테고리에는 버튼을 노출하지 않음

## 빈 세트 중량 자동 적용

MVP 동작:

- 한 세트에 0보다 큰 kg를 입력하면 같은 entry 안의 빈 세트를 확인
- 빈 세트가 있으면 안내 카드 표시
- 사용자가 적용하면 `weightKg == 0`, `manualWeight=false`, `confirmed=false`인 세트에만 적용
- 이미 다른 중량이 있거나 confirmed=true인 세트는 자동 덮어쓰지 않음

안내 문구:

```text
나머지 빈 세트에도 60kg을 적용할까요?
```

## 휴게 타이머

세트를 확인하면 `WorkoutEntry.restSeconds` 기준으로 휴게 타이머를 시작한다.

조건:

- `confirmed=false -> true` 전환일 때만 시작
- `restSeconds <= 0`이면 시작하지 않음
- 기존 confirmed 세트 수정으로는 중복 시작하지 않음

앱 안에서는 기록 화면 상단에 미니 타이머를 표시한다.

미니 타이머 표시:

- 남은 시간
- 다음 세트 / 다음 운동 힌트
- 중지 버튼

## 알림 권한 처리

추가 권한:

```xml
android.permission.POST_NOTIFICATIONS
```

처리:

- Android 13 이상에서 권한 상태 확인
- 권한이 없으면 앱 안 타이머만 동작
- 기록 화면에서 짧은 안내와 권한 요청 버튼 제공
- 알림은 같은 notification id를 갱신하여 반복 알림을 만들지 않음
- 타이머 종료 시 finished notification으로 갱신

## 오버레이 권한 처리

추가 권한:

```xml
android.permission.SYSTEM_ALERT_WINDOW
```

처리:

- 권한이 없으면 오버레이를 강제하지 않음
- 기록 화면에서 설정 이동 버튼 제공
- 앱 안에서는 오버레이 표시하지 않음
- 앱 밖으로 나가고 타이머가 active이며 권한이 있으면 오버레이 표시
- 앱으로 돌아오면 오버레이 제거

## 오버레이 delete suppression

기존 앱의 delete suppression을 복구했다.

동작:

- 앱 밖에서 오버레이를 닫거나 삭제 영역으로 드래그하면 `dismissedForCurrentAwaySession=true`
- 현재 away session 동안 timer tick이나 retry가 오버레이를 다시 만들지 않음
- 앱으로 돌아오면 suppression reset
- 새 타이머가 시작되면 suppression reset

## Persisted Preferences

`RestTimerSessionController`가 기존 구조와 같은 key를 사용한다.

```text
rest_end_at
rest_next
rest_target_date
rest_target_entry_id
rest_finished
```

## 아직 남은 제한

- 자동 테스트는 아직 없다.
- 알림 heads-up 동작은 기기 설정에 따라 다를 수 있다.
- 오버레이 드래그 / 삭제는 실제 기기에서 확인해야 한다.
- 알림/오버레이 클릭은 기록 탭과 날짜 이동까지 MVP로 처리한다. entry focus scroll은 아직 정교하지 않다.
- Foreground service는 아직 추가하지 않았다.

## 수동 QA 절차

1. 기록 탭에서 운동 추가
2. 세트 여러 개 추가
3. 세트 행 높이가 과도하지 않은지 확인
4. 세트별 확인 버튼이 횟수 / kg / 초 옆에 있는지 확인
5. 한 세트에 kg 입력 후 나머지 빈 세트 적용 안내 확인
6. `일괄 kg` 버튼으로 같은 운동의 세트 중량 변경 확인
7. 세트 확인 시 휴게 타이머 시작 확인
8. 앱 안에서 미니 타이머 표시 확인
9. 앱 밖으로 나갔을 때 알림 표시 확인
10. 오버레이 권한이 있으면 오버레이 표시 확인
11. 앱으로 돌아오면 오버레이 제거 확인
12. 오버레이를 앱 밖에서 삭제하면 현재 away session 동안 다시 안 뜨는지 확인
13. 새 타이머 시작 시 suppression reset 확인
14. 타이머 종료 시 알림 / 소리 / 진동 확인
15. confirmed=false / true 의미 유지 확인
16. 분석 단순 통계가 confirmed=true 세트만 반영하는지 확인

## 빌드

최종 검증 명령:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat assembleDebug
```

## Phase 2.6 Set RPE / Rest Override Update

추가 변경:

- 수면 / 체중 입력은 기본 펼침 입력칸에서 `컨디션` 버튼 뒤로 이동
- 운동 메모, 최대횟수, 운동 전체 RPE, 기본 휴식 편집은 `상세` 버튼 뒤로 이동
- 세트 목록은 항상 상세 입력보다 먼저 표시
- `CompactNumberField`는 `BasicTextField` 기반 compact 입력으로 구현
- 세트 행은 횟수 / kg 또는 초 / RPE / 확인을 우선 표시
- 휴식 override와 삭제는 낮은 보조 행에 배치

세트별 RPE:

- `WorkoutSet.rpe` 추가
- Room DB version additive migration 적용
- `WorkoutSet.rpe`가 canonical 위치
- `WorkoutEntry.rpe`는 legacy fallback 또는 운동 전체 체감
- migration에서 entry RPE를 세트로 강제 복사하지 않음
- 세트 추가 시 RPE는 복사하지 않음

세트별 휴식:

- `WorkoutSet.restSecondsOverride` 추가
- `WorkoutEntry.restSeconds`는 기본 휴식시간
- `WorkoutSet.restSecondsOverride`는 세트별 예외 휴식시간
- 타이머는 effective rest를 사용

```text
effectiveRest = WorkoutSet.restSecondsOverride ?: WorkoutEntry.restSeconds
```

일괄 메뉴:

- kg 일괄 설정
- kg 일괄 증가
- kg 일괄 감소
- 횟수 일괄 증가
- 횟수 일괄 감소
- 휴식 일괄 설정
- 휴식 일괄 증가
- 휴식 일괄 감소
- 빈 kg 세트에 현재 중량 적용

기본 적용 범위는 미확인 세트다. 완료 세트를 포함하려면 사용자가 `완료 세트 포함`을 명시적으로 선택해야 한다.

notes와 구조화 처방:

- `WorkoutEntry.notes`는 자유 메모 / 코치성 메모로만 사용
- 프로그램 적용 시 `TrainingProgramItem.setCount`, `reps`, `weightKg`, `seconds`가 `WorkoutSet`으로 복사됨
- `3세트 x 10회`, `4세트 x 5회 @ 80kg`, `30초 x 5세트` 같은 구조화 처방값은 notes에 복사하지 않음
- 운동 카드 요약은 notes가 아니라 `WorkoutSet` 목록에서 계산

## Phase 2.7 Minor UI Follow-up

Phase 2.7에서 기록 탭 마이너 UI를 추가 조정했다.

- `컨디션`과 `운동 추가`를 같은 compact row에 배치
- `달력` 버튼은 날짜 이동 row의 `다음날` 오른쪽에 배치
- 기록 화면 상단의 항상 표시되는 mini timer bar 제거
- active rest timer는 해당 set row 보조 chip에 표시
- `RestTimerState.targetSetId`로 active timer와 세트 row를 매핑
- 완료 운동은 UI에서 위쪽으로 정렬
- 월간 캘린더 요약 화면 추가
- 시스템 다크모드 지원 확인

자세한 내용은 `outputs/phase2_7_record_calendar_ui_patch.md`를 참고한다.
