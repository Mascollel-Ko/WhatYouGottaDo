# 프로그램 실행·진행 세션·중량 조정 제안 설계서

- 문서 성격: 구현 전 설계 계약 / Codex 입력 문서
- 대상 앱: WhatYouGottaDo
- 저장소: `Mascollel-Ko/WhatYouGottaDo`
- 작성 기준일: 2026-09-06
- 확인한 `main` HEAD: `ae2eb4388a00c23aa536c595d5e99e6d6ae0ed07`
- 확인한 앱 버전: `0.5.1.4` / `501004`
- 확인한 Room schema: `30`
- 확인한 record-based planner runtime: `RECORD_BASED_PLANNER_0.13.1_KOTLIN_1`
- 확인한 `PROGRAM-BUILDER-OVERVIEW` protocol version: `3.3.1`

> 이 문서는 새 기능의 설계 입력이다. 구현 시 현재 `main`을 다시 확인해야 하며 위 SHA/버전이 최신이라고 가정하면 안 된다.
> 구현 완료 시 기존 canonical protocol인 `docs/protocols/program_builder/PROGRAM_BUILDER_OVERVIEW.md`, protocol registry/index, worklog/release documentation도 같은 작업에서 갱신한다.

---

## 1. 문제 정의

현재 저장 프로그램 적용 경로는 `TrainingProgramItem`/`TrainingProgramItemSet`을 날짜별 `WorkoutEntry`/`WorkoutSet`으로 materialize하지만, 적용 후 원 프로그램과 기록 사이의 durable provenance가 끊긴다.

현재 확인한 핵심 상태:

- `ProgramPlanService.applyProgramToDates()`는 프로그램 item/set을 날짜별 unconfirmed workout plan으로 복사한다.
- `WorkoutEntry`에는 source program/application/progression-track identity가 없다.
- `TrainingProgramItem`에는 target RPE가 없다.
- `PersonalizedPrescriptionPlanner`의 `ADVANCE/HOLD/REDUCE/REVIEW`는 프로그램 **생성 시점** 판단이며, 적용 후 실제 세션 결과를 읽어 다음 linked session의 중량을 제안하는 runtime execution layer가 없다.
- 현재 protocol의 “미래 자동 증량 금지”는 유지해야 한다. 새 기능은 미래 성공을 미리 가정하는 것이 아니라, **실제로 완료한 comparable session 이후 바로 다음 comparable session에만 제안을 생성**한다.
- 기록 달력에서 plan-only는 현재 `tertiaryContainer` 계열이고, confirmed historical date는 OFI에 따라 `errorContainer` 방향으로 tint된다.

새 기능의 목적은 다음 흐름을 만드는 것이다.

```text
프로그램 작성
→ 운동별 '진행 세션(Progression Track)' 정의/추론
→ 프로그램 적용 인스턴스 생성
→ 날짜별 계획에 프로그램/트랙 provenance 보존
→ 실제 세션 수행
→ 같은 진행 세션의 직전 실제 수행을 기준으로 다음 중량 제안
→ 사용자가 적용/유지/직접입력
→ 다음 실제 세션이 끝난 뒤 그 다음 제안
```

---

## 2. 절대 원칙

### 2.1 자동 변경이 아니라 “제안”

중량은 자동으로 바꾸지 않는다.

- 앱은 `증량 / 유지 / 감량 / 검토`를 **제안**한다.
- 사용자가 `적용`을 누르기 전에는 기존 계획을 수정하지 않는다.
- 닫기/뒤로가기는 거절이 아니라 `PENDING` 유지다.
- 사용자의 명시 선택이 최종 authority다.

### 2.2 진행 방향의 기준점은 “전 comparable session의 실제 수행”

`INCREASE/HOLD/DECREASE`는 **다음 계획 대비가 아니라 직전 같은 진행 세션의 실제 수행 대비**로 정의한다.

예:

```text
직전 같은 진행 세션 실제: 140kg
현재 다음 계획:          145kg
앱 제안:                 142.5kg
```

판정은 `INCREASE +2.5kg`이다.
`현재 계획 145kg보다 2.5kg 낮다`는 이유로 `DECREASE`라고 부르면 안 된다.

또한 직전 계획이 140kg이었어도 실제 수행을 137.5kg으로 바꿔 완료했다면 다음 진행 기준점은 137.5kg이다.

### 2.3 미래 성공을 연쇄 가정하지 않는다

한 세션 성공 후 남은 모든 미래 세션을 `+2.5, +5, +7.5...`로 연쇄 증량하지 않는다.

```text
세션 A 실제 완료
→ 바로 다음 comparable session B에 제안
→ B 실제 완료
→ 그 다음 comparable session C에 제안
```

만 허용한다.

### 2.4 OFI는 직접 증량/감량 스위치가 아니다

기존 v0.13 계열 원칙을 유지한다.

- 높은 OFI만으로 증량을 금지하거나 감량하지 않는다.
- 실제 comparable-session performance, 실제 RPE, local tissue restriction, 반복된 underperformance가 중심이다.
- local restriction은 해당 운동/트랙에 국소 적용한다.
- global hard restriction과 local restriction을 다시 합치지 않는다.

### 2.5 프로그램 작성 시 target RPE를 새 필수 입력으로 만들지 않는다

현재 프로그램 item/set에는 target RPE가 구조화되어 있지 않다.

따라서:

- **진행 세션 자동 연결/분류에는 RPE를 쓰지 않는다.**
- RPE는 실제 세션 수행 후 `WorkoutSet.rpe`에서 progression suggestion evidence로 사용한다.
- 프로그램 작성 UI에 target RPE 입력을 강제하지 않는다.

---

## 3. 용어

### 3.1 Program Application

하나의 저장 프로그램을 특정 시작일에 한 번 적용한 실행 인스턴스.

같은 프로그램을 9월과 12월에 적용하면 서로 다른 application이다.

### 3.2 진행 세션 / Progression Track

같은 운동이라도 비슷한 처방 구조로 반복되며 서로 progression 근거를 이어받는 운동 세션 계열.

예:

```text
월 Squat 3×5 @ 약 80%
수 Squat 3×10 @ 약 65%
금 Squat 3×5 @ 약 80%
```

자동 추론 결과:

```text
Squat · 5회 진행 세션
월 → 금 → 다음 월 → 다음 금 ...

Squat · 10회 진행 세션
수 → 다음 수 → ...
```

따라서 금요일 스쿼트의 직접 predecessor는 수요일 스쿼트가 아니라 월요일 `3×5` 스쿼트다.

### 3.3 Comparable Session

같은 `ProgramApplication + ProgressionTrack`에 속하고, 해당 target session의 progression base와 비교 가능한 가장 최근 confirmed session.

같은 exerciseStableKey라도 다른 progression track이면 직접 predecessor가 아니다. 다만 장기 posterior/tissue/전체 상태의 contextual evidence에는 들어갈 수 있다.

---

## 4. 프로그램 작성 단계에서 진행 세션을 만든다

### 4.1 모든 적격 program item은 처음부터 track identity를 가질 수 있어야 한다

weighted resistance program item을 프로그램에 추가할 때, 첫 등장부터 내부적으로 progression track을 만들 수 있어야 한다.

사용자에게는 첫 운동부터 복잡한 설정을 강요하지 않는다. 동일 운동이 반복되거나 사용자가 상세를 열 때만 track 정보를 노출해도 된다.

### 4.2 자동 생성 프로그램

record-based planner 등 앱이 구조를 알고 생성한 프로그램은 inference보다 **explicit provenance**가 우선한다.

예:

- HLM `HEAVY` → 다음 `HEAVY`
- HLM `LIGHT` → 다음 `LIGHT`
- HLM `MEDIUM` → 다음 `MEDIUM`
- DUP `STRENGTH` → 다음 `STRENGTH`
- DUP `VOLUME` → 다음 `VOLUME`

앱이 생성 시 이미 알고 있는 style/variant를 버리고 다시 reps/name heuristic으로 추론하지 않는다.

### 4.3 수동 프로그램의 자동 진행 세션 추론

자동 추론 후보가 되려면 우선 동일 `exerciseStableKey`여야 한다.

**다른 stableKey는 자동으로 같은 progression track에 합치지 않는다.**
display name, category string, name substring은 identity authority가 아니다.

#### Prescription Signature

수동 프로그램에서는 다음을 사용해 동일/분리/애매함을 판단한다.

- working set 수
- set별 reps pattern 또는 대표 reps/rep band
- set structure: uniform straight / top+backoff / ramp-like 등
- planned positive load
- canonical strength estimate가 실제로 이용 가능하면 **프로그램 저장 시점** `planned load / 1RM estimate` snapshot
- 기존 typed program style/dayIntensity/trainingSlot provenance가 있으면 보조 근거
- 사용자 또는 planner가 명시한 role/track override

사용하지 않는 것:

- target RPE (현재 프로그램 입력에 없음)
- exercise display name parsing
- 미래 1RM으로 과거 track을 재분류하는 것

#### 1RM 대비 비율

canonical 1RM/strength posterior가 있을 때만 상대강도 snapshot을 만든다.

없으면 값을 발명하지 않는다. 동일 exercise 안의 실제 planned load와 reps/set structure로만 비교한다.

한 번 저장된 signature의 relative-intensity snapshot은 나중에 1RM이 변해도 과거 프로그램의 의미를 뒤집지 않는다.

### 4.4 자동 추론의 기대 사례

반드시 다음 의미를 만족해야 한다.

1. `3×5 @80%` vs `3×5 @82%`
   → 같은 progression track 강한 후보

2. `3×5 @80%` vs `3×10 @65%`
   → 서로 다른 progression track

3. `3×5 @80%` vs `3×5 @65%`, 별도 역할 정보 없음
   → 단순히 reps가 같다는 이유로 자동 합치지 말 것. ambiguous 또는 분리 쪽으로 보수적 처리.

4. 같은 exercise가 프로그램 전체에서 한 progression track만 존재
   → 기본적으로 그 운동의 `메인` track 후보

수치 경계는 생리학적 진리로 표현하지 않는다. 구현상 engineering heuristic이며 테스트로 관리한다.

### 4.5 애매한 경우

저장을 막는 반복 modal을 띄우지 않는다.

권장 UX:

```text
진행 연결 1건 확인 필요
```

작은 배너/표시 → 사용자가 눌렀을 때 bottom sheet.

안전한 기본은 잘못 합치는 것보다 별도 track으로 두는 것이다. unresolved ambiguous track은 자동 중량 제안을 비활성 또는 REVIEW-only로 둘 수 있다.

---

## 5. 메인 / 보조 역할

### 5.1 사용자 선택 가능

사용자 UI에서는 단순하게:

- 자동
- 메인
- 보조

를 제공한다.

한 번 사용자가 수동 지정하면 자동 추론이 다시 덮어쓰지 않는다.

### 5.2 역할은 운동 자체가 아니라 track 속성

잘못된 모델:

```text
Squat = MAIN
```

올바른 모델:

```text
Squat 3×5 track = MAIN
Squat 3×10 track = ASSISTANCE
```

### 5.3 자동 역할 추론

우선순위:

1. 사용자 explicit role
2. planner/generated explicit role
3. manual-program auto inference
4. ambiguous → 자동 확정하지 않음

수동 프로그램의 일반적 신호:

- 같은 운동에 track이 하나뿐이면 MAIN 후보
- 여러 track이면 낮은 reps + 상대적으로 높은 relative intensity + strength-oriented set structure가 MAIN 후보
- 높은 reps + 상대적으로 낮은 intensity는 ASSISTANCE/HYPERTROPHY 후보
- **RPE 절대값은 역할 추론에 사용하지 않는다**

내부적으로 더 세분화한 role이 필요하면 허용한다.

예:

- `PRIMARY_STRENGTH`
- `SECONDARY_STRENGTH`
- `HYPERTROPHY_SUPPORT`
- `TECHNIQUE_LIGHT`

단 사용자 UI는 `메인/보조` 중심으로 단순하게 유지한다.

---

## 6. 진행 세션 UI

항상 체크박스와 설정을 늘어놓지 않는다.

### 6.1 프로그램 item의 기본 표시

예:

```text
스쿼트
3세트 × 5회 × 140kg
진행: 스쿼트 · 5회 · 메인
```

`진행: ...`은 작은 한 줄 summary이며 tap하면 bottom sheet를 연다.

### 6.2 진행 설정 bottom sheet

한 화면 안에서 다음을 수정할 수 있다.

#### 진행 연결

- 자동
- 기존 track 선택
- 새 진행으로 분리
- 진행 제안 사용 안 함

#### 역할

- 자동
- 메인
- 보조

#### 제안 방식

- 앱 기준
- 내 기준
- 직접 판단
- 끔

이 설정들은 card 본문을 계속 밀어내지 않고 bottom sheet 안에 둔다.

---

## 7. 중량 조정 제안 방식

### 7.1 제안 상태

최소 상태:

- `PENDING`
- `ACCEPTED`
- `KEPT_CURRENT_PLAN`
- `MANUAL_OVERRIDE`
- `SUPERSEDED`
- 필요 시 `STALE`

### 7.2 제안 판정

사용자에게 보이는 방향:

- `INCREASE`
- `HOLD`
- `DECREASE`
- `REVIEW`

방향은 항상 **직전 comparable actual progression base 대비**다.

### 7.3 원래 다음 계획을 존중하되 방향 기준으로 쓰지 않는다

수동 프로그램에 이미 다음 중량이 계획돼 있을 수 있다.

예:

```text
직전 실제: 100
다음 원래 계획: 105
앱 판단상 적절한 제안: 102.5
```

표시는:

```text
전 세션 대비 +2.5kg 증량 제안
현재 프로그램 계획 105kg
```

이다.

원래 프로그램의 105kg은 comparator/사용자 선택지이지 progression direction baseline이 아니다.

### 7.4 직전 actual base 산출

진행 트랙마다 `progression base`를 명확히 정의해야 한다.

- uniform straight sets → working-set load
- top-set/backoff → top-set load
- HLM/ramp → planner가 명시한 anchor working load 또는 track에 저장한 base policy
- manual arbitrary set structure가 ambiguous하면 값 발명 금지 → `REVIEW`

“마지막 세트 중량”을 무조건 기준으로 쓰지 않는다.

### 7.5 planned / current plan / actual을 구분한다

최소한 다음 provenance를 잃지 않아야 한다.

1. 원 프로그램의 source prescription
2. 현재 target session에 적용된 current planned prescription
3. confirmed actual performance

사용자가 target session의 중량을 직접 수정했다고 원 프로그램 처방이 사라져서는 안 된다.

구현은 기존 `WorkoutSet`을 무리하게 의미 변경하기보다 별도 linked prescription snapshot/entity를 써도 된다.

---

## 8. 앱 기준 V1: 해석 가능한 규칙 엔진

V1은 개인화 ML을 만들지 않는다.

“앱 기준”은 설명 가능한 deterministic rule profile로 시작한다.

### 8.1 공통 원칙

- comparable session의 목표 세트/반복 달성 여부
- 실제 working-set RPE
- 최근 같은 track의 1~2회 흐름
- local tissue restriction
- current plan
- 과거 동일 stableKey의 실제 load step이 있으면 그 증가 단위

를 사용한다.

### 8.2 기본 행동

일반적 방향:

```text
목표 완전 달성 + 충분한 노력도 여유
→ INCREASE 후보

목표 완전 달성 + 매우 높은 실제 노력도
→ HOLD

한 번의 가벼운 underperformance
→ HOLD

반복된 comparable-session underperformance
→ DECREASE 또는 REVIEW

해당 운동 local tissue restriction
→ INCREASE 금지, HOLD/REVIEW/DECREASE 중 국소 판단

OFI 높음 단독
→ 자동 감량/증량 금지 사유가 아님
```

### 8.3 판정 RPE

프로그램 작성 시 target RPE가 아니라 실제 recorded RPE를 사용한다.

사용자-facing 용어는 `판정 RPE`.

기본 추출 예:

- straight working sets → working sets 중 최고 RPE
- top-set/backoff → top set RPE
- planner가 anchor set을 명시한 구조 → anchor set RPE
- ambiguity → REVIEW

### 8.4 role별 기본 RPE profile

RPE는 “높을수록 메인”이 아니다.

기본 app profile은 role/rep context에 따라 다르게 둘 수 있다.

예시 방향:

- main strength, 약 3~5 reps 계열: 증량 제안 허용 노력도 범위를 비교적 낮게 관리
- assistance/hypertrophy, 중고반복 계열: 더 높은 실제 RPE에서도 성공 세션으로 볼 수 있음

정확한 numeric threshold는 engineering default로 관리하고 사용자-facing 생리학적 법칙처럼 설명하지 않는다.

### 8.5 RPE가 없을 때

RPE가 없으면 한 번의 완료만으로 공격적으로 증량하지 않는다.

기본 app mode는 예를 들어 같은 track에서 반복된 성공 확인을 요구할 수 있다.

사용자 custom rule에서는 RPE 미입력 동작을 선택할 수 있게 한다.

---

## 9. “내 기준” 사용자 규칙 UI

복잡한 IF/AND rule builder를 만들지 않는다.

bottom sheet 안의 단순 form으로 구성한다.

예:

### 다음 중량 제안 기준

**증량 조건**

- `[✓] 계획한 핵심 세트와 반복을 모두 완료`
- 판정 RPE: `[최고 working-set RPE ▼] ≤ [8.5]`
- 필요한 성공 횟수: `[1회 ▼]`

**증량폭**

- `[+2.5 kg ▼]`
- 필요 시 `%` 방식 지원 가능

**실패 처리**

- 첫 실패: `[유지]`
- 연속 실패: `[2회 ▼]` 후 `[-5% ▼]` 또는 지정 kg

**RPE 미입력 시**

- `[제안 보류]`
- `[목표 완료 여부만 사용]`

사용자 설정은 track별로 저장한다.

---

## 10. “직접 판단” 모드

알고리즘이 증감 방향을 정하지 않는다.

다만 다음 UI는 제공한다.

```text
지난 같은 진행 세션
140kg × 5 × 3
최고 RPE 8.5

이번 기존 계획
145kg × 5 × 3

[현재 계획 유지]
[전 세션 중량 사용]
[직접 입력]
```

사용자가 직접 결정하되 progression provenance는 기록한다.

---

## 11. 제안 UI

### 11.1 Record 화면의 기본 card

프로그램에서 온 entry는 운동명 근처에 작은 chip을 붙인다.

```text
스쿼트   [프로그램]
145kg × 5 × 3
```

chip tap:

```text
배드민턴 지원 프로그램
2주차 · 진행: 스쿼트 · 5회 · 메인
```

원 프로그램이 나중에 삭제돼도 프로그램 이름 snapshot과 provenance는 기록에서 남아야 한다.

### 11.2 Pending suggestion은 card를 과도하게 늘리지 않는다

기본 card에는 한 줄만:

```text
지난 같은 진행 세션 기준 +2.5kg 제안
```

tap → bottom sheet.

### 11.3 suggestion bottom sheet

예:

```text
중량 조정 제안

지난 같은 진행 세션
140kg × 5 × 3
최고 RPE 8.0

이번 기존 계획
145kg × 5 × 3

제안
142.5kg × 5 × 3
전 세션 대비 +2.5kg

[142.5kg 적용]
[기존 계획 145kg 유지]
[직접 입력]
```

좁은 화면에서 3개 버튼을 억지로 한 Row에 넣지 않는다.

권장:

- primary action: full-width
- secondary actions: 충분한 폭에서만 Row
- compact width에서는 vertical stack

---

## 12. Program Application / 데이터 provenance

구현 상세 naming은 현재 repo를 감사한 뒤 결정하되 다음 의미가 보존되어야 한다.

### 12.1 Program-level progression track

프로그램 자체에서 track을 저장해야 한다.

필요한 의미:

```text
programStableKey
progressionTrackStableKey
exerciseStableKey
role
roleSource (AUTO / USER / PLANNER)
suggestionMode
signature snapshot
base-load policy
custom rule config
```

### 12.2 Program Application

필요한 의미:

```text
applicationStableKey
sourceProgramStableKey
programNameSnapshot
startDate
appliedAt
progressionSuggestionEnabled
sourceDecisionId? / generation provenance?
```

### 12.3 Applied Workout Link

각 프로그램-origin `WorkoutEntry`가 최소 다음을 추적 가능해야 한다.

```text
workoutEntryId
applicationStableKey
sourceProgramStableKey
sourceProgramItem logical identity
progressionTrackStableKey
sequenceIndex / occurrence identity
programNameSnapshot
track/role snapshot as needed
```

원 프로그램 삭제 시 applied record provenance가 cascade 삭제되어서는 안 된다.

### 12.4 Suggestion entity/log

최소 의미:

```text
sourceWorkoutEntryId
targetWorkoutEntryId
applicationStableKey
progressionTrackStableKey

previousActualBase
targetCurrentPlanBase
suggestedBase

decision
reasonCodes
ruleSnapshot
status
createdAt
resolvedAt
```

---

## 13. 적용·편집·삭제·복사 의미

### 13.1 프로그램 재적용

같은 프로그램을 다시 적용하면 새 `ProgramApplication`.

서로 progression chain을 섞지 않는다.

### 13.2 프로그램 template 편집

이미 적용된 application은 기존 snapshot을 유지한다.

원 template 수정이 과거/현재 application을 조용히 재작성하지 않는다.

### 13.3 프로그램 삭제

과거/적용중 기록의 `[프로그램]` provenance는 유지한다.

### 13.4 plan move / push

프로그램-linked unconfirmed plan을 날짜 이동/밀기하는 기능은 application/track link를 유지해야 한다.

### 13.5 일반 copy

기존 progression occurrence를 단순 복제해 같은 chain sequence가 두 개 생기면 안 된다.

generic copy는 명시적 정책을 둔다.

권장: 기본은 progression chain에서 detached copy로 처리하거나 새 occurrence로 안전하게 재식별한다. 잘못된 duplicate predecessor를 만들지 않는다.

---

## 14. 달력 UI 규칙

### 14.1 현재 노란/tertiary plan-only 색 제거

현재 `RecordCalendarScreen`의 `hasPlanOnly -> tertiaryContainer` 계열은 제거한다.

**모든 plan-only 날짜**는 가장 밝고 흐린 파랑 계열을 사용한다.

미래 plan에 OFI를 발명하지 않는다.

### 14.2 confirmed program-origin 기록

프로그램 application에서 온 confirmed 기록이 있는 날짜는 **파랑 계열**로 표시한다.

OFI `0..100`은 같은 blue hue family 안에서 연함 → 진함으로 연속 표현한다.

```text
낮은 OFI  → 연한 파랑
중간 OFI  → 중간 파랑
높은 OFI  → 진한 파랑
```

`errorContainer` 방향으로 tint하지 않는다.

### 14.3 confirmed non-program 기록

프로그램 provenance가 없는 일반 confirmed 기록은 기존 red/error 계열 OFI 표현을 유지한다.

### 14.4 선택 상태

selected state가 provenance hue를 완전히 덮어쓰지 않도록 한다.

권장: semantic fill은 유지하고 border/emphasis로 selection을 표시한다.

### 14.5 mixed date

같은 날짜에 program-origin confirmed work와 자유 기록이 같이 있어도 program-linked 날짜임을 식별 가능해야 한다.

색만으로 모든 의미를 넣지 않아도 된다. 상세 화면의 `[프로그램]` chip은 item-level truth를 유지한다.

### 14.6 색은 semantic token으로 중앙 관리

Compose 화면 곳곳에 `Color(0xFF...)`를 흩뿌리지 않는다.

light/dark theme에서:

- plan-only palest blue
- program confirmed low/high OFI blue endpoints
- readable content colors

를 semantic token/function으로 중앙 관리하고 contrast를 검증한다.

---

## 15. 가로 폭·줄바꿈·세로 글자화 방지 UI 계약

이번 기능에서 가장 중요한 UI 회귀 방지 조건 중 하나다.

### 15.1 금지

다음 구현을 금지한다.

- 긴 한국어 버튼 3개를 작은 Row에 `weight(1f)`로 억지 배치
- dynamic Korean text에 너무 작은 fixed width
- chip/action이 운동명 영역을 압박해 글자가 한 글자씩 세로처럼 줄바꿈되는 구조
- 불필요하게 큰 좌우 padding을 중첩해서 실제 text width를 줄이는 구조
- `메인`, `보조`, `프로그램`, `직접 입력` 같은 짧은 control label이 두 줄 이상으로 깨지는 구조
- calendar 7열 셀 안에 `프로그램` 같은 긴 텍스트를 추가하여 폭을 더 압박하는 구조

### 15.2 기본 원칙

- phone body/card는 가능한 `fillMaxWidth`
- 일반 phone screen horizontal outer padding은 기존 design system을 따르되 중첩 padding을 감사
- exercise name/설명 영역이 우선적으로 유효 폭을 가져야 함
- 작은 chip은 `maxLines=1`, 필요 시 ellipsis
- track summary는 한 줄 summary + tap detail
- button text는 가능한 한 `maxLines=1`
- 한 줄 버튼이 안 들어가면 버튼을 줄이지 말고 **Row → Column/Flow layout로 전환**
- compact width에서 primary/secondary action을 vertical stack
- 긴 설명은 bottom sheet의 full-width text section으로 이동

### 15.3 검증 폭

최소 다음 조건을 검사한다.

- width 320dp
- width 360dp
- width 411dp
- fontScale 1.0
- fontScale 1.3 이상

확인 항목:

- 한 글자씩 세로로 내려가는 Korean text 없음
- 버튼 label 비정상 줄바꿈 없음
- exercise name이 chip 때문에 과도하게 압축되지 않음
- horizontal clipping 없음
- 중요 정보가 ellipsis 뒤에만 존재하지 않음
- bottom sheet action이 화면 밖으로 밀리지 않음

실제 emulator/screenshot 검증을 하지 못했으면 하지 않았다고 보고한다. 코드만 보고 “시각 검증 완료”라고 주장하지 않는다.

---

## 16. Progression suggestion용 RPE와 role의 관계

중요:

- 프로그램 role 추론에는 RPE를 쓰지 않는다.
- 실제 progression suggestion에는 recorded RPE를 쓴다.
- 낮은 reps의 main strength가 항상 높은 RPE여야 한다는 가정을 금지한다.
- 3~5회 main strength를 RPE 6~8 부근에서 관리하는 사용자가 정상적으로 증량 제안을 받을 수 있어야 한다.
- 8~12회 assistance/hypertrophy 세션은 더 높은 RPE에서도 정상 성공으로 해석될 수 있다.
- exact numeric threshold는 앱 기본 profile 또는 사용자 custom rule의 설정값이며 생리학적 보편 법칙으로 표시하지 않는다.

---

## 17. 증량 단위

사용자 custom rule이 있으면 그 값을 우선한다.

앱 기준 fallback은 해석 가능해야 한다.

권장 순서:

1. 같은 stableKey/track의 최근 실제 positive load step이 충분히 관찰되면 대표 step 사용
2. 부족하면 보수적 percentage fallback + executable rounding
3. 값이 불명확하면 무리해서 invent하지 말고 REVIEW/사용자 입력

기구 이름 문자열로 원판 단위를 추정하지 않는다.

---

## 18. V1 비적용 / 범위 제한

초기 구현에서 불필요하게 범위를 폭발시키지 않는다.

- 개인화 머신러닝/숨은 학습 알고리즘 없음
- 자동으로 모든 미래 세션 연쇄 수정 없음
- plyometric/footwork/court drill의 세션별 자동 load progression 없음
- target RPE 프로그램 입력 필드 강제 추가 없음
- exercise name substring 기반 session identity 없음
- OFI 높은 것만으로 전신 감량 없음
- 기존 `ProgramAutoBuilder`의 unrelated 알고리즘 변경 없음

향후 user acceptance/rejection + 실제 결과를 기반으로 개인화하는 것은 별도 버전에서 가능하다.

---

## 19. 필수 테스트 시나리오

### 19.1 Track inference

- same stableKey `3×5 @80` + `3×5 @82` → same track
- same stableKey `3×5 @80` + `3×10 @65` → separate track
- same stableKey `3×5 @80` + `3×5 @65`, no role → not blindly merged
- different stableKey, same display name/family → never auto merged
- one track for exercise → MAIN auto candidate
- user role override survives subsequent edit/reload
- generated HLM/DUP explicit role/track provenance wins over heuristic
- missing 1RM does not fabricate relative intensity
- RPE absence/presence does not affect authoring-time track inference

### 19.2 Suggestion semantics

- previous actual `140`, current plan `145`, suggested `142.5` → `INCREASE`, not decrease
- previous plan `140`, actual `137.5` → next baseline is `137.5`
- same exercise but different track is not predecessor
- first mild underperformance → HOLD
- repeated comparable underperformance → DECREASE/REVIEW per rule
- high OFI alone with good performance/no local restriction does not block increase
- local tissue restriction blocks increase for related track, not unrelated program work
- custom max-RPE threshold is honored
- RPE-missing behavior is honored
- dismiss → remains pending
- accept → only target current plan changes
- keep current plan → explicit resolved state
- manual input → explicit manual provenance
- accepted suggestion does not mutate all future sessions
- stale source/edited predecessor causes recompute or STALE, not silent use

### 19.3 Program provenance

- applied record shows `[프로그램]`
- program deletion does not erase applied-record provenance
- same program applied twice creates distinct applications
- source template edit does not silently rewrite existing application
- move/push preserves links
- generic copy cannot create duplicate chain identity silently
- backup/restore round-trips applications/tracks/links/suggestion decisions

### 19.4 Calendar

- plan-only uses palest blue, not tertiary/yellow
- program-confirmed OFI=0/50/100 continuously maps within blue family
- non-program confirmed keeps existing red/error behavior
- future plan does not invent OFI
- selected day retains provenance fill with readable selection emphasis
- dark/light contrast remains readable

### 19.5 UI width

Create previews/tests or equivalent coverage for:

- 320dp
- 360dp
- 411dp
- enlarged font

No one-character-per-line Korean labels, no accidental vertical-looking text, no clipped action buttons.

---

## 20. Migration / backup / provenance 요구

현재 확인한 baseline이 여전히 Room 30이면 정상적인 `30 -> 31` migration을 추가한다.
현재 main이 달라졌으면 실제 latest schema에서 다음 migration을 만든다.

- destructive migration 금지
- 기존 workout/program 데이터 보존
- new entities/columns에 stable logical identity
- backup/restore format 및 schema version을 필요한 만큼 갱신
- old backups의 backward compatibility 검사
- new backup round-trip exact provenance 검사
- program source가 없어도 applied record snapshot 복원 가능

관계형 progression graph를 장기적으로 조회해야 하므로 전부 opaque `app_meta` JSON에 숨기는 방식은 피한다. 단 기존 architecture와 audit 결과가 더 나은 구조를 보여주면 그 이유를 보고하고 조정할 수 있다.

---

## 21. 문서화 Definition of Done

이 설계 문서를 구현만 하고 방치하지 않는다.

같은 작업에서 최소:

- 이 설계 문서의 구현 상태/최종 결정 업데이트
- `docs/protocols/program_builder/PROGRAM_BUILDER_OVERVIEW.md`
- `docs/protocols/README.md` 또는 현재 protocol index
- `docs/protocols/protocol_registry.json`
- 관련 worklog / release note
- migration/backup contract 문서가 따로 canonical이면 해당 문서
- calendar OFI presentation 의미가 변경되므로 `OFI_CLASSIFICATION_AND_PRESENTATION.md`의 presentation section

을 현재 repo 규칙에 맞게 갱신한다.

---

## 22. 구현 시 절대 금지

**DO NOT USE THE PONYTAIL PRINCIPLE.**

이 기능은 provenance, Room schema, program application, progression track, suggestion engine, backup/restore, UI semantics가 연결되는 구조적 변경이다.

- correctness를 위해 필요한 구조 리팩터링을 허용한다.
- “최소 diff”를 목표로 데이터 모델을 비정상적으로 우회하지 않는다.
- 반대로 unrelated cleanup/refactor를 섞지도 않는다.
- 현재 legacy `ProgramAutoBuilder` 동작을 이 기능을 핑계로 재설계하지 않는다.

---

## 23. 구현 완료 시 기대 사용자 경험

### 프로그램 편집

```text
스쿼트
3×5 · 140kg
진행: 스쿼트 · 5회 · 메인
제안 기준: 앱 기준
```

수요일 `3×10`은 자동 별도 track.

애매한 것만:

```text
진행 연결 1건 확인 필요
```

### Record

```text
스쿼트   [프로그램]
145kg × 5 × 3

지난 같은 진행 세션 기준 +2.5kg 제안
```

tap:

```text
지난 같은 진행 세션
140kg × 5 × 3 · 최고 RPE 8.0

이번 기존 계획
145kg × 5 × 3

제안
142.5kg × 5 × 3
전 세션 대비 +2.5kg

[142.5kg 적용]
[기존 계획 유지]
[직접 입력]
```

### Calendar

```text
plan only           → 가장 흐린 파랑
program confirmed   → OFI에 따라 연한~진한 파랑
free confirmed      → 기존 OFI red family
```

---

## 24. 성공 기준

이 기능이 성공했다고 판단하려면 다음이 동시에 성립해야 한다.

1. 사용자는 대부분의 프로그램 작성에서 진행 세션을 직접 설정할 필요가 없다.
2. 동일 운동의 서로 다른 처방(`3×5` vs `3×10`)이 잘못 연결되지 않는다.
3. 사용자는 원하면 메인/보조와 track 연결을 직접 override할 수 있다.
4. 중량 변화는 앱이 자동 적용하지 않고 제안한다.
5. 증량/감량 방향은 항상 전 comparable actual session 기준이다.
6. 원래 다음 계획은 보존되고 사용자에게 비교 정보로 제시된다.
7. 프로그램과 기록의 provenance가 삭제/재시작/backup 이후에도 남는다.
8. 달력에서 plan/program provenance가 파랑 계열로 즉시 구분된다.
9. 새로운 UI 때문에 좁은 화면에서 한국어가 세로처럼 깨지거나 쓸데없이 줄바뀌지 않는다.
10. 기존 OFI/recovery/tissue authority를 중복 또는 잘못 재해석하지 않는다.

## 25. 실제 구현 결정 — 실행 레이어 v0.14.0 (2026-09-06)

이 절은 위 설계에 대한 실제 Kotlin 구현을 기록한다. 기준 main은 `ae2eb4388a00c23aa536c595d5e99e6d6ae0ed07`이다. 기존 생성기의 선택/배분 정책은 v0.13.1 그대로 두고 최종 처방에 typed style/variant/anchor/role 출처만 연결했다. 기능 규모에 맞춰 runtime은 `RECORD_BASED_PLANNER_0.14.0_KOTLIN_1`, PROGRAM-BUILDER-OVERVIEW는 3.4.0으로 올렸다. Android 앱 버전 `0.5.1.4` / code `501004`와 release tag는 변경하지 않았다. Ponytail 원칙은 적용하지 않았다.

### 25.1 영속 소유자와 실제 컬럼

Room 30→31은 기존 테이블을 변경하거나 삭제하지 않고 아래 여섯 테이블을 추가한다. 전체 SQL, 인덱스와 FK는 `app/schemas/com.training.trackplanner.data.TrainingDatabase/31.json` 및 `ProgramProgressionMigration.MIGRATION_30_31`에 있다.

| Entity / table | 컬럼 |
| --- | --- |
| `ProgramProgressionTrack` / `program_progression_tracks` | `id, programStableKey, exerciseStableKey, label, role, roleOverride, mode, basePolicy, anchorSetIndex, needsReview`, 아래 `rule_*` |
| `ProgramProgressionItem` / `program_progression_items` | `programItemId, logicalItemId, trackId, linkMode`, 아래 `signature_*` |
| `ProgramApplication` / `program_applications` | `id, programStableKey, programName, startDate, appliedAt` |
| `ProgramWorkoutLink` / `program_workout_links` | `entryId, applicationId, sourceProgramStableKey, sourceItemId, programName, weekNumber, dayOfWeek, trackId, trackLabel, sequence, role, mode, basePolicy, anchorSetIndex, needsReview`, 아래 `rule_*` |
| `ProgramPrescriptionSet` / `program_prescription_sets` | `entryId, setIndex, originalReps, originalKg, originalSeconds, plannedReps, plannedKg, plannedSeconds, plannedSetIndex, originalExists` |
| `ProgressionSuggestion` / `progression_suggestions` | `id, applicationId, trackId, sourceEntryId, targetEntryId, previousActualKg, currentPlanKg, suggestedKg, judgmentRpe, direction, reasons, evidenceHash, resolution, createdAt, resolvedAt, resolvedKg`, 아래 `rule_*` |

- `rule_*`: `version, requireCompletion, rpePolicy, rpeThreshold, successesRequired, incrementKg, firstFailure, failuresBeforeDecrease, decreasePercent, missingRpe`에 각각 `rule_` 접두사를 붙인다.
- `signature_*`: `exerciseStableKey, setCount, repsPattern, baseKg, oneRmSnapshotKg, relativeIntensity, style, variant, trainingSlot, dayIntensity, basePolicy, anchorSetIndex, plannerRole`에 각각 `signature_` 접두사를 붙인다. 작성 시 RPE 필드는 없다.
- 프로그램 템플릿 삭제 FK는 항목 binding까지만 cascade한다. 적용 당시 이름/역할/규칙/원처방은 남는다. 실제 수행 값은 기존 `WorkoutSet`만 소유한다.
- 원처방 세트 순번은 불변이며 현재 계획에서 삭제한 세트는 `plannedSetIndex=null`, 나중에 추가한 세트는 `originalExists=false`다. 첫 확인 이후 목표를 얼리고, 부분 수행 중 구조 변경은 REVIEW로 표시한다.

### 25.2 연결과 제안

- exact stableKey + typed planner style/variant/slot을 먼저 사용한다. 수동 처방은 세트 수/반복 패턴/slot/day intensity와 상대 강도 차이 0.04 이내를 비교한다. 1RM이 없으면 기존 확인 기록의 canonical e1RM을 생성 시 한 번만 snapshot하며, 그것도 없으면 null을 유지하고 중량 차이 비율 4% 이내만 후보로 본다.
- 사용자 역할 override > planner 역할 > 단일 track의 MAIN 자동 후보. 복수 수동 track의 모호한 역할은 AUTO/REVIEW다. 이름·카테고리·장비·RPE로 track을 합치지 않는다.
- 동일 application/track의 앞선 완전 확인 occurrence만 비교한다. 다른 track, 다른 적용본, 부분 수행은 직접 predecessor가 아니다. 날짜 이동은 원래 sequence를 유지한다.
- 기준은 straight-set 실제 중량 또는 explicit anchor다. 임의 혼합 처방에서 마지막 세트 중량을 기준으로 만들지 않는다.
- 앱 기본값: MAIN RPE 상한 8, ASSISTANCE 9, 성공 1회, 첫 미달 HOLD, 연속 2회 미달 시 5% 감량. RPE 누락은 HOLD. anchor 구조의 판단 RPE는 anchor 실제 RPE다. custom form에서 완료 조건, 판단 RPE, 상한, 성공 횟수, kg step, 첫 미달 동작, 감량 조건과 누락 정책을 저장한다. 수치는 생리 법칙이 아닌 버전 1 공학 기본값이다.
- step은 사용자 kg → 두 번 이상 일치하는 관측 증분 → 2%의 0.5kg 반올림(기준의 5% 이내) → REVIEW 순서다. 장비의 실제 증량 단위를 추정하지 않으며 적용 전 확인 문구를 표시한다.
- `140 actual / 145 current plan / 142.5 suggestion`은 **INCREASE +2.5**다. 140을 계획했어도 137.5만 수행했으면 baseline은 137.5다.
- PENDING은 닫아도 남는다. Apply/Keep/Manual만 명시적 결정이며, Apply는 바로 다음 미수행 세션만 수정한다. 미래 세션 일괄 증량은 없다. 증거 hash 변경은 PENDING을 STALE로 만들고 재평가하며 이미 명시적으로 해결한 결정은 덮어쓰지 않는다.
- local restriction은 기존 canonical tissue HIGH/VERY_HIGH의 exact contributor stableKey에만 적용한다. OFI는 증감 방향 입력이 아니며 전역 감량 스위치가 없다.

### 25.3 UI, 이동과 백업

- 초안/저장 프로그램의 짧은 진행 행 → 설정 sheet. 기록의 `[프로그램]` chip → 적용 당시 출처 sheet. 한 줄 pending hint → 비교/사유/전체 폭 세로 버튼 sheet. 한국어/영어 리소스를 사용한다.
- 계획 전용 날짜는 가장 연한 파랑, 프로그램 확인 날짜는 OFI 연한~진한 파랑, 비프로그램 확인 기록은 기존 빨강이다. 선택/검색은 별도 테두리다.
- Move 및 순수 계획 Push는 source identity와 실행 연결을 유지한다. generic copy는 detached다. 부분 수행의 남은 세트만 push하면 새 나머지 기록은 detached이고 원본은 REVIEW다.
- CSV format 13 / restore schema 12 / `PROGRAM_EXECUTION_V1` capability, 기존 program backup schema 2. 명시적인 여섯 `execution_*` JSON 행은 DB app_meta가 아닌 관계 테이블의 portable transport다. UUID와 backupSourceId로 재연결하고 과거 숫자 Room ID에 의존하지 않는다. 이전 format 12/schema 11도 읽는다.
- 삭제된 source/target 기록을 가진 제안 이력은 nullable portable source와 복원 시 ID 0 tombstone으로 남긴다. PENDING은 STALE로 바꾼다. append에서 이미 존재하는 명시적 결정과 적용 snapshot은 보존한다. template/track 정의는 authoritative program snapshot을 따른다.
- unknown enum/rule version, 잘못된 범위, 중복 identity/sequence, 없는 track/application/link, 다른 운동으로 바뀐 source 참조는 거부한다.

### 25.4 의도된 한계

- 개인화 ML, 생리 계산, legacy ProgramAutoBuilder의 선택/수치 규칙은 변경하지 않았다. 이 기능의 제안은 검토 가능한 V1 규칙이다.
- 임의 혼합 중량에서 확정 가능한 기준이 없으면 일괄 base 입력도 하지 않는다. 계획 유지 후 기록 화면에서 각 세트를 수정한다. 없는 draft 연결 대상은 안전한 별도 DIRECT track으로 남긴다.
- 이미 적용한 세션의 규칙은 snapshot이다. 프로그램 편집의 새 규칙은 다음 적용본부터 사용한다. 오래된 실행 이력에서 출처를 이름으로 소급 복구하지 않는다.
- 기존 항목을 재저장하면 UUID/typed intent/사용자 override를 보존한다. 삭제한 항목의 과거 track/제안 이력은 자동 정리하지 않는다.
- `SUPERSEDED`는 wire vocabulary로 예약되어 있고 현재 증거 교체는 STALE로 기록한다. 실제 기기 전체 사용자 여정/물리 기기/원격 CI는 별도 실행 없이는 검증했다고 주장하지 않는다.

### 25.5 검증

검증 명령/최종 집계는 같은 변경의 `docs/CODEX_WORKLOG.md`에 기록한다. 핵심 테스트는 `ProgramProgressionEngineTest`, `ProgramProgressionPersistenceTest`, `ProgramProgressionLayoutTest`, `ProgressionCalendarTest`, 실제 Android `ProgramProgressionMigrationTest`, `ProgramProgressionDeviceLayoutTest`다. UI는 320/360/411dp × fontScale 1.0/1.3에서 실제 line/glyph bounds와 emulator 화면을 함께 확인한다. 테스트용 자료는 합성 데이터이며 사용자 개인 백업을 소스에 추가하지 않는다.

## 26. 수동 진행 세션 교정 — v0.14.1 (2026-09-06)

이 절은 §25의 초안 대상 항목 참조와 재편집 보존 구현을 교정한다. 기준 main은
`339baa919ceca5cb17aef50d23f26d61aa16e026`이다. runtime은
`RECORD_BASED_PLANNER_0.14.1_KOTLIN_1`, PROGRAM-BUILDER-OVERVIEW는 `3.4.1`이다.
앱 버전과 Room 31, CSV format 13 / restore schema 12 / program schema 2는 유지한다.
Ponytail 원칙은 사용하지 않았다.

### 26.1 단일 세션 소유자와 권위

- 초안 `GeneratedProgramSkeleton.progressionSessions`의 `DraftProgressionSession`은
  `ProgramProgressionTrack` 값과 typed source를 보유한다. 세션 키는 UUID이며 어느 멤버의 localId도 아니다.
- `ProgramSkeletonItem.progressionBinding`의 `DraftProgressionBinding`은 sessionKey,
  logicalItemId, linkMode, signature 및 기존 DB에서 복원했는지 나타내는 draft-only persisted 표지를 가진다.
  persisted 표지는 새 초안의 canonical e1RM 초기 snapshot과 기존 null snapshot 보존을 구분한다.
- USER_EXPLICIT > PLANNER_EXPLICIT > AUTO_INFERRED. 역할/override/모드/규칙은 공유 세션에만 저장한다.
  roleOverride=AUTO는 다시 planner role 또는 기존 자동 역할 후보를 허용한다.
- 같은 exact exerciseStableKey만 연결할 수 있다. 명시 분리/연결은 세트 수·반복·중량 편집에도 남는다.
  수동 세션의 비교 불일치는 REVIEW로 표시할 수 있지만 연결을 바꾸지 않는다.
  멤버 삭제는 다른 멤버의 UUID/규칙을 없애지 않는다. 모든 멤버가 없어지면 초안 목록에서만 제외하며
  이미 적용한 이력이나 영속 과거 세션을 자동 삭제하지 않는다.
- `resolveProgressionSession`은 초안과 영속 authoring의 역할/검토 상태 해석을 공유한다.
  `sameTrack` 4%와 세트 수 비교, generated-style drift 및 수치 제안 엔진은 변경하지 않는다.

### 26.2 작성·저장·재편집

- 적격성은 canonical activity kind, progress behavior 및 training-role 관계다. 적격 저항운동은
  0kg/처방 미완성 상태에서도 진행 세션을 선택할 수 있다. 미완성 load의 제안 기준은 REVIEW다.
  sport/plyometric/skill/conditioning은 자동 중량 진행 대상이 아니다. 이름/장비/카테고리 추론은 없다.
- 작성 화면의 짧은 진행 세션 행을 누르면 자동 연결, 동일 운동의 기존 세션, 새 세션, 연결하지 않음,
  역할, 제안 기준을 표시한다. 기존 custom form을 공유하며 ID 대신 실제 처방·역할과 요일을 보여 준다.
  anchor는 탑세트/백오프, 그 외 비균일 구조는 혼합 세트로 표시한다. 반복 주차의 요일은 중복 제거한다.
- 기존 세션을 선택하면 그 세션의 실제 역할/모드/규칙을 form에 로드한다. 새 세션은 별도 UUID로 생성된다.
  연결하지 않음은 항목의 연결 모드이며, 세션의 OFF 제안 모드와 구분한다. 다른 멤버의 모드를 끄지 않는다.
- `programEditorSnapshot`은 program/items/sets/bindings/tracks를 하나의 Room transaction으로 읽는다.
  `skeletonFromProgram` → `hydrateProgression`은 UUID, logicalItemId, 명시 연결, roleOverride,
  mode/rule와 planner style/variant/anchor/role/slot/intensity를 복원한다. 항목만 먼저 도착한 중간 상태로
  초안을 확정하지 않는다. 저장은 그 visible draft graph를 materialize하며 `oldBindings` 우회는 없다.
- 기존 저장본의 e1RM snapshot은 null도 보존한다. 새 초안은 `ProgressionDraftContext`로 적격성과
  기존 canonical 확인 기록 projection의 snapshot을 함께 읽는다. UI와 저장이 같은 snapshot으로
  기존 자동 비교를 사용하며, context 없는 생성/API 초안은 첫 저장 때 기존 경로로 snapshot을 채운다.
  새 사용자 명시 세션은 이 자동 비교보다 우선한다.
- 재저장 때 숫자 programItemId가 바뀌어도 논리 항목 및 세션 UUID는 유지한다. 기존 실행 snapshot은
  변경하지 않으며 새 template 설정은 다음 application부터 적용한다. 기존 backup wire로 같은 graph를 복원한다.

### 26.3 제외 범위와 검증

자동 분류기/4%/세트 수 비교/미수행 predecessor/APP RPE/step fallback/OFI/회복/색상/빈도/선택/legacy
builder는 이 교정의 대상이 아니다. Move 및 완전 미확정 future Push는 application/track/sequence/
처방 출처를 보존한다. generic copy는 detached이며 부분 확인 push 정책은 v0.14.0 그대로다.

추가 검증은 `ProgramProgressionDraftTest`, `ProgramProgressionSessionPersistenceTest`, 갱신된
`ProgramProgressionPersistenceTest`, `ProgramProgressionLayoutTest`, `ProgramProgressionDeviceLayoutTest`다.
실행 결과와 한계는 현재 worklog에 기록한다. schema 변경이 없으므로 새 migration을 만들지 않는다.
