# WhatYouGottaDo 운동 메타데이터 전략 v2.4

## 0. 문서 지위

- 문서 유형: 운동 메타데이터 분류·정규화·이행 전략
- 적용 대상: 내장 운동 및 사용자 운동의 고정 분류, 고정 관계, 고정 프로그램·분석 파라미터
- 제외 대상: 운동 기록, 세트 입력값, 사용자 선택, 세션 목적, 프로그램의 실제 처방 결과, 분석 결과
- 이전 문서 상태: v2.2를 대체한다.
- v2.3 개정 사유: 승인된 legacy `trainingRole` stableKey whitelist를 복구하고, `familyId`·`loadProfile`·`sportTransferDirect`의 target 의미를 확정하며, production과 분리된 strength-proxy prior 및 Level-1 한국어 taxonomy 검토 초안을 추가한다.
- 핵심 식별자: `exerciseStableKey`

---

# 1. 메타데이터의 정의

이 프로젝트에서 운동 메타데이터는 다음과 같이 정의한다.

> 운동 기록의 내용, 사용자의 선택, 프로그램 배치 결과 또는 분석 결과와 무관하게, 운동에 고정되어 있는 분류, 관계 및 프로그램·분석용 기본 파라미터.

메타데이터는 운동 기록에 따라 변하지 않는다. 운동에 고정된 기본 파라미터와 실제 프로그램 또는 분석이 산출한 결과는 반드시 구분한다.

## 1.1 메타데이터에 포함되는 것

- 운동의 동작 패턴
- 관절 동작
- 운동 사건·국면
- 관여 관절복합체
- 편측성·운동면
- 자동 프로그램 가능 블록
- 프로그램 역할
- 중복·대체·진행 그룹
- 필요 장비
- 자동 프로그램 시간예산에 사용하는 운동별 기본 휴식시간
- OFI 축 연결과 정적 배분 프로필
- 근육 분석 단위와 정적 배분 프로필
- 배드민턴 수행 영역별 전이 수준
- 신체 능력 분류
- 관절·건·인대별 정적 부하 프로필
- 관계별 근거·검토·승인 상태

## 1.2 메타데이터에 포함하지 않는 것

- 중량
- 반복 수
- 세트 수
- 운동 시간
- 거리
- RPE
- RIR
- 사용자가 이번 기록에서 선택한 입력 조합
- 세션의 목적
- 테스트 세션 여부
- 회복 세션 여부
- 진행 지표
- 추정 1RM
- 주간 볼륨
- 현재 OFI 점수
- 근육별 누적 부하
- 배드민턴 전이 자극량
- 조직 잔여부하
- 자동 프로그램의 최종 운동 선택
- 프로그램의 실제 처방 중량·반복·시간·휴식시간

위 값은 운동 기록, 사용자 상태, 프로그램 설정 또는 분석 프로토콜의 영역이다.

예외적으로 `defaultRestSeconds`처럼 운동에 고정되어 자동 프로그램의 시간예산 계산에 사용되는 기본값은 메타데이터에 포함한다. 이는 생성된 프로그램의 실제 `restSeconds`와 동일한 개념이 아니다.

## 1.3 v2.3 승인 경계

- legacy `trainingRole`은 정확히 승인된 `exerciseStableKey`에만 존재한다. 이름·카테고리·장비·동작 문자열로 만들지 않으며, 빈 값은 관계 없음이다. 이 값은 최종 `ProgramRoleRef`가 아니다.
- `familyId`의 target relation은 `NONE`이고 disposition은 `DERIVED_NONCANONICAL`이다. 목적별 reviewed relation으로 필요한 유사성을 계산하며 다른 universal family를 만들지 않는다.
- `loadProfile`의 target relation은 `NONE`이고 disposition은 `LEGACY_COMPOSITE_TO_BE_DECOMPOSED`이다. 현재 저장 필드는 호환을 위해 유지하되 새 정본 소비자는 사용하지 않는다.
- `sportTransferDirect`의 target contract는 complete closed-world whitelist다. 명시된 관계만 direct이고, 빈 값은 unresolved가 아닌 authoritative `NONE`이다. 현재 production의 이름 기반 legacy fallback은 sport-transfer cutover가 별도 승인될 때까지 결과 보존을 위해 그대로 유지한다.
- strength-proxy prior는 별도 versioned asset과 package에만 존재하며 production strength posterior에 연결하지 않는다.
- `MILITARY_PRESS`의 direct anchor는 제품 책임자 결정에 따라 `ex_32219f7a` (`오버헤드 프레스`)다. 의미는 서서 수행하는 strict barbell overhead press이며 의도적인 무릎·엉덩이 drive가 없다. push press, push jerk, split jerk는 별도 stableKey가 필요하다. 기존 `ex_32219f7a` 기록은 현재 strength model에서 이 정본 동작의 기록으로 취급한다.

---

# 2. 최상위 구조

```text
Exercise Identity
├─ Level 1 운동학·해부학 분류
├─ Level 2 프로그램 생성 분류·고정 파라미터
├─ Level 3 OFI 고정 분석 프로필
├─ Level 4 근육 고정 분석 프로필
├─ Level 5 배드민턴 전이 분류
├─ Level 6 연결조직 고정 분석 프로필
└─ Level 7 근거·검토·승인
```

운동 식별 기반을 제외하면 실제 메타데이터는 7개 상위 계층으로 구성한다.

---

# 3. 공통 설계 원칙

## 3.1 `stableKey`만 분석 식별자로 사용한다

```text
exerciseStableKey
→ exact typed relation
→ 분석 또는 프로그램 생성
```

금지:

```text
운동명으로 추론
stableKey 일부 문자열로 추론
contains
startsWith
endsWith
split 후 의미 추론
영문 코드 조각으로 의미 추론
```

운동명과 번역명은 표시용이다.

## 3.2 한 필드는 한 질문에만 답한다

예:

- `MovementPattern`: 어떤 전신 동작 패턴인가?
- `JointAction`: 어느 관절에서 어떤 동작이 일어나는가?
- `BodyRegion`: 어느 상위 관절복합체가 관여하는가?
- `ProgramBlockCapability`: 자동 프로그램의 어느 블록에 들어갈 수 있는가?
- `MuscleContribution`: 운동 dose를 어느 근육 분석 단위에 배분하는가?
- `BadmintonTransferRelation`: 어느 수행 영역에 어떤 수준으로 전이되는가?

서로 다른 논리 수준을 한 코드에 섞지 않는다.

금지 예:

```text
SHOULDER_ACCESSORY
FOREARM_GRIP_SUPPORT
BADMINTON_OVERHEAD_POWER_TEST
SQUAT_MAIN_HEAVY_LOWER
```

## 3.3 다중관계는 여러 행으로 저장한다

운동 하나는 여러 관계를 동시에 가질 수 있다.

```text
백스쿼트
→ OFI 여러 축
→ 프로그램 여러 블록
→ 여러 근육
→ 여러 관절복합체
→ 여러 조직
→ 여러 배드민턴 수행 영역
```

다중값을 구분자로 합쳐 저장하지 않는다.

금지:

```text
HIP_COMPLEX|KNEE_COMPLEX|ANKLE_COMPLEX
QUADRICEPS,GLUTEALS,SPINAL_ERECTORS
```

허용:

```text
squat_key, HIP_COMPLEX
squat_key, KNEE_COMPLEX
squat_key, ANKLE_COMPLEX
```

## 3.4 같은 논리 수준은 등록형 참조 사전으로 강제한다

자유 문자열 ID를 사용하지 않는다.

```text
movement_pattern_ref
joint_complex_ref
joint_action_ref
program_block_ref
program_role_ref
muscle_analysis_unit_ref
badminton_performance_domain_ref
physical_quality_ref
tissue_ref
```

관계 테이블은 반드시 해당 참조 사전의 외래키를 사용한다.

## 3.5 정적 분류와 고정 분석 파라미터를 구분한다

### 분류 메타데이터

- 동작 패턴
- 관절 동작
- 관절복합체
- 프로그램 블록
- 프로그램 역할
- 장비
- 근육 분석 단위
- 배드민턴 수행 영역
- 신체 능력
- 조직 관계

### 고정 분석 파라미터

- OFI routing coefficient
- 근육 allocation coefficient
- 조직 load coefficient
- 고정 recovery profile
- 프로그램 block fit score
- 수행 영역별 transfer level

둘 다 운동 기록에 따라 변하지 않지만, 후자는 분류라기보다 분석용 고정 파라미터다.

## 3.6 동적 결과는 메타데이터에 저장하지 않는다

```text
Workout record
+ fixed metadata
+ analysis protocol
+ user history
+ time window
= dynamic result
```

동적 결과를 메타데이터 관계 테이블에 넣지 않는다.


## 3.7 운동별 고정 기본값과 실제 처방 결과를 분리한다

운동별 고정 기본값은 운동 기록과 무관하게 동일 운동에 붙는 값이며, 자동 프로그램 또는 분석이 계산을 시작할 때 사용하는 입력이다.

예:

```text
ExerciseProgramTimingProfile.defaultRestSeconds
= 운동별 고정 기본 휴식시간

GeneratedProgramExercise.restSeconds
= 프로그램 목표·강도·시간 제약을 반영한 실제 처방 결과
```

고정 기본값은 메타데이터에 포함할 수 있지만, 실제 처방 결과를 같은 필드나 같은 관계로 저장하지 않는다.

---

# 4. 운동 식별 기반

## 질문

> 이 운동은 무엇인가?

## `ExerciseIdentity`

```text
stableKey
displayNameKo
displayNameEn
isBuiltIn
isActive
```

규칙:

- `stableKey`는 유일하고 정확히 일치해야 한다.
- 대소문자·공백을 자동 보정하지 않는다.
- 운동명 변경은 분석 연결을 바꾸지 않는다.
- 별칭은 검색 편의를 위한 별도 표시 자산으로 둔다.

---

# 5. Level 1 — 운동학·해부학 분류

## 목적

운동이 어떻게 움직이고 어느 상위 해부학적 부위가 관여하는지 표현한다.

## 1-1 전신 동작 패턴

### 질문

> 이 운동의 전신 또는 몸통 수준 동작 패턴은 무엇인가?

### `MovementPatternRef`

초기 후보:

```text
KNEE_DOMINANT_SQUAT
HIP_HINGE
SPLIT_STANCE_LUNGE
HORIZONTAL_PUSH
VERTICAL_PUSH
HORIZONTAL_PULL
VERTICAL_PULL
CARRY
LOCOMOTION
JUMP
ROTATION
ANTI_ROTATION
ANTI_EXTENSION
ANTI_LATERAL_FLEXION
```

### `ExerciseMovementPattern`

```text
exerciseStableKey
movementPatternId
relationRole
provenanceId
```

`relationRole`:

```text
PRIMARY
SECONDARY
```

여기에 넣지 않는 값:

```text
ISOLATION
ACCESSORY
PREHAB
WRIST_EXTENSION
SHOULDER_SUPPORT
BADMINTON_FOOTWORK
```

## 1-2 관절 동작

### 질문

> 어느 관절복합체에서 어떤 운동학적 동작이 발생하는가?

### `JointComplexRef`

15개 상위 관절복합체:

```text
CERVICAL_COMPLEX
THORACIC_COMPLEX
LUMBAR_COMPLEX
LUMBOSACRAL_COMPLEX
SACROILIAC_COMPLEX
HIP_COMPLEX
PATELLOFEMORAL_COMPLEX
KNEE_COMPLEX
ANKLE_COMPLEX
TARSAL_COMPLEX
SHOULDER_COMPLEX
ELBOW_FLEXION_EXTENSION_COMPLEX
RADIOULNAR_ROTATION_COMPLEX
WRIST_COMPLEX
HAND_GRIP_COMPLEX
```

### `JointActionRef`

예:

```text
FLEXION
EXTENSION
ABDUCTION
ADDUCTION
INTERNAL_ROTATION
EXTERNAL_ROTATION
PRONATION
SUPINATION
DORSIFLEXION
PLANTARFLEXION
RADIAL_DEVIATION
ULNAR_DEVIATION
```

### `JointComplexActionRef`

허용 가능한 관절·동작 조합을 등록한다.

예:

```text
SHOULDER_COMPLEX + ABDUCTION
WRIST_COMPLEX + EXTENSION
RADIOULNAR_ROTATION_COMPLEX + PRONATION
```

### `ExerciseJointAction`

```text
exerciseStableKey
jointComplexId
jointActionId
actionRole
provenanceId
```

`actionRole`:

```text
PRIMARY
SECONDARY
STABILIZING
```

공식 한국어 표시명은 관절과 동작의 승인된 조합으로 관리한다.

```text
SHOULDER_COMPLEX + ABDUCTION
→ 견관절 외전
```

## 1-3 운동 사건·국면

### 질문

> 이 운동에서 가속·감속·착지·반응성 반동 등 어떤 사건이 발생하는가?

### `MovementEventRef`

```text
ACCELERATION
DECELERATION
CHANGE_OF_DIRECTION
TAKEOFF
LANDING
REACTIVE_BOUNCE
CYCLIC_LOCOMOTION
```

### `ExerciseMovementEvent`

```text
exerciseStableKey
movementEventId
relationRole
provenanceId
```

`relationRole`:

```text
PRIMARY
SECONDARY
```

운동 사건은 수행 영역이나 신체 능력과 구분한다.

## 1-4 관여 관절복합체

### 질문

> 이 운동에 어떤 상위 관절복합체가 관여하는가?

### `ExerciseBodyRegion`

```text
exerciseStableKey
jointComplexId
involvementRole
provenanceId
```

`involvementRole`:

```text
PRIMARY
SECONDARY
STABILIZING
LOAD_TRANSFER
```

이 관계는 다음만 말한다.

```text
KNEE_COMPLEX가 관여한다.
```

다음은 말하지 않는다.

```text
슬개건에 어떤 부하가 얼마나 걸리는가
슬개대퇴관절에 압박 부하가 얼마나 걸리는가
```

조직별 부하는 Level 6이 담당한다.

## 1-5 편측성과 운동면

### `LateralityRef`

```text
BILATERAL
UNILATERAL
ALTERNATING
ASYMMETRIC
```

### `MovementPlaneRef`

```text
SAGITTAL
FRONTAL
TRANSVERSE
MULTIPLANAR
```

### `ExerciseKinematicProfile`

```text
exerciseStableKey
lateralityId
movementPlaneId
provenanceId
```

이 값은 운동의 고정적인 운동학적 설명과 필터에 사용할 수 있다.

다만 OFI, 근육, 배드민턴 전이 또는 조직 부하를 이 값으로 다시 추론하지 않는다.

---

# 6. Level 2 — 프로그램 생성 분류·고정 파라미터

## 목적

자동 프로그램 생성기가 운동 후보를 구성하고, 장비·중복·대체·역할을 판단하며, 사용자가 입력한 일별 운동 가능시간 안에 프로그램을 맞추도록 운동별 시간예산 기본값을 제공한다.

정규화 이후에도 기존 자동 프로그램의 후보 구조, 시간 맞춤 결과 및 최종 골자가 달라지지 않아야 한다.

## 2-1 프로그램 사용 가능 상태

### `PlanningEligibilityRef`

```text
PROGRAM_SELECTABLE
MANUAL_ONLY
ANALYSIS_ONLY
HIDDEN
```

### `ExercisePlanningProfile`

```text
exerciseStableKey
planningEligibilityId
provenanceId
```

## 2-2 프로그램 가능 블록

### 질문

> 이 운동은 자동 프로그램의 어느 기능적 블록에 들어갈 수 있는가?

### `ProgramBlockRef`

초기 후보:

```text
LOWER_KNEE_DOMINANT
LOWER_HIP_DOMINANT
LOWER_UNILATERAL
UPPER_HORIZONTAL_PUSH
UPPER_VERTICAL_PUSH
UPPER_HORIZONTAL_PULL
UPPER_VERTICAL_PULL
TRUNK_ANTI_EXTENSION
TRUNK_ANTI_ROTATION
TRUNK_LATERAL_STABILITY
ROTATIONAL_POWER
PLYOMETRIC_LANDING
COD_DECELERATION
FOOTWORK_REACTION
SHOULDER_CAPACITY
FOREARM_GRIP_CAPACITY
RECOVERY_PREHAB
GENERAL_ACCESSORY
```

### `ExerciseProgramBlockCapability`

```text
exerciseStableKey
programBlockId
capabilityRole
fitScore
provenanceId
```

`capabilityRole`:

```text
PRIMARY
SECONDARY
LIMITED
```

`fitScore`는 프로그램 후보 정렬에 사용하는 고정 적합도다.

운동 하나는 여러 블록에 들어갈 수 있다.

## 2-3 프로그램 역할

### 질문

> 자동 프로그램에서 이 운동을 어떤 역할로 사용할 수 있는가?

### `ProgramRoleRef`

```text
STRENGTH_MAIN
STRENGTH_SUPPORT
POWER
ACCESSORY
PREHAB
CONDITIONING
SKILL
RECOVERY
```

### `ExerciseProgramRole`

```text
exerciseStableKey
programRoleId
eligibility
provenanceId
```

`eligibility`:

```text
ELIGIBLE
INELIGIBLE
INCOMPLETE
```

신체 부위와 역할을 합친 코드를 사용하지 않는다.

금지:

```text
SHOULDER_ACCESSORY
```

허용:

```text
bodyRegion = SHOULDER_COMPLEX
programRole = ACCESSORY
```

## 2-4 중복·대체·진행 그룹

### 질문

> 이 운동은 어떤 목적의 운동군에 속하는가?

### `GroupPurposeRef`

```text
REDUNDANCY_CONTROL
SUBSTITUTION
STRENGTH_PROGRESSION
WORKLOAD_BASELINE
LOCAL_REPEAT_DETECTION
```

### `ExerciseGroupRef`

```text
groupId
groupPurposeId
displayNameKo
displayNameEn
```

### `ExerciseGroupMembership`

```text
exerciseStableKey
groupId
priority
provenanceId
```

하나의 그룹을 서로 다른 목적으로 재사용하지 않는다.

## 2-5 장비 요구사항

### 질문

> 이 운동을 실제로 프로그램 후보로 선택하려면 어떤 장비 조건이 충족되어야 하는가?

### `EquipmentRef`

```text
equipmentId
displayNameKo
displayNameEn
```

### 단순 구조

```text
ExerciseEquipmentRequirement
- exerciseStableKey
- equipmentId
- requirementRole
- provenanceId
```

`requirementRole`:

```text
REQUIRED
OPTIONAL
ALTERNATIVE
```

### 복합 조건이 필요한 경우

```text
ExerciseEquipmentOptionGroup
- optionGroupId
- exerciseStableKey

ExerciseEquipmentOptionItem
- optionGroupId
- equipmentId
```

규칙:

```text
같은 option group 안의 장비 = AND
서로 다른 option group = OR
```

장비가 바뀌면 운동의 역학·중량 의미·stableKey가 달라지는 경우에는 대체 장비로 묶지 않고 별도 운동으로 둔다.

### 장비 관계의 소비 범위

장비 관계는 자동 프로그램 생성에서 필수로 소비되므로 Level 2에 배치한다. 그러나 장비 관계 자체는 운동의 고정 성질이며 소비자를 프로그램 생성기에 한정하지 않는다. 동일한 canonical 장비 관계를 다음 기능에서도 사용할 수 있다.

- 운동 검색과 필터
- 홈짐·시설 적합성 표시
- 사용자 보유 장비 적합성
- 운동 정보 UI
- 자동 프로그램 후보 필터

다른 소비자가 필요하다는 이유로 별도의 장비 문자열이나 중복 장비 테이블을 만들지 않는다.

## 2-6 프로그램 시간 프로필

### 질문

> 자동 프로그램 생성기가 운동의 예상 소요시간을 계산할 때 사용할 운동별 기본 휴식시간은 얼마인가?

### `ExerciseProgramTimingProfile`

```text
exerciseStableKey
defaultRestSeconds
provenanceId
```

`defaultRestSeconds`의 의미:

```text
운동에 고정된 시간예산 기본 파라미터
```

자동 프로그램은 이 값을 세트 간 휴식시간의 기본값으로 사용하여 사용자가 입력한 일별 운동 가능시간 안에 프로그램을 맞춘다.

개념 경계:

```text
ExerciseProgramTimingProfile.defaultRestSeconds
= 운동별 고정 기본값

GeneratedProgramExercise.restSeconds
= 실제 생성된 프로그램의 처방 결과
```

따라서 `defaultRestSeconds`는 삭제 대상이 아니며 `ExerciseProgramBlockCapability`에 넣지 않는다. 독립된 프로그램 시간 프로필로 관리한다.

초기 이행에서는 현재 값을 그대로 보존한다.

```text
conversionMode = DIRECT_COPY
```

단, 실제 값이 heuristic fallback으로 생성된 경우에는:

```text
conversionMode = CURRENT_RESOLVER_OUTPUT
derivationMode = LEGACY_HEURISTIC_FALLBACK
linkedRiskPathId = required
```

로 기록한다.

## 2-7 자동 프로그램 parity 계약

정규화 전후에 다음이 같아야 한다.

```text
프로그램 목표와 주차 구조
요일 구조
기능적 블록 구성
블록별 후보 stableKey 집합
PRIMARY / SECONDARY / LIMITED 판정
장비 필터 결과
일별 운동 가능시간에 따른 세션 시간예산 계산
기본 휴식시간을 반영한 예상 운동시간
시간 부족 시 세트 축소·운동 제외 결과
사용자 제외 운동 처리
필수 운동 처리
중복 억제
대체 후보 순서
메인·보조·파워·프리햅 역할
최종 선택 stableKey
세트·반복·중량·시간 처방
주차별 볼륨·강도 변화
디로드 여부
```

단, 처방값 자체는 운동 메타데이터가 아니라 프로그램 생성 프로토콜이 계산한다.

---

# 7. Level 3 — OFI 고정 분석 프로필

## 목적

운동 기록의 dose가 OFI 5축으로 어떻게 전달되는지 정적인 연결과 배분 규칙을 정의한다.

## 3-1 OFI 축 관계

### `OfiAxisRef`

```text
HIGH_FORCE_NEURAL
SYSTEMIC_MUSCULAR
LOCAL_MUSCULAR
HIGH_SPEED
REACTIVE
```

### `ExerciseOfiAxisProfile`

```text
exerciseStableKey
ofiAxisId
routingCoefficient
recoveryProfileId
provenanceId
```

내장 운동은 5축 모두 명시한다.

```text
routingCoefficient = 0
```

은 해당 축에 기여하지 않음을 의미한다.

중요:

```text
routingCoefficient
= 운동 자체의 고정 배분값

actual axis contribution
= 운동 기록 dose × routingCoefficient × OFI 프로토콜
```

실제 OFI 점수는 메타데이터가 아니다.

## 3-2 회복 프로필

### `OfiRecoveryProfileRef`

운동 또는 OFI 축에 연결되는 고정 회복 감쇠 프로필을 관리한다.

회복 프로필은 실제 현재 회복량이 아니라 분석에 사용하는 고정 곡선 선택값이다.

## 3-3 OFI 비교 그룹

### `OfiComparisonPurposeRef`

```text
WORKLOAD_BASELINE
LOCAL_REPEAT_DETECTION
STRENGTH_COMPARISON
```

### `ExerciseOfiComparisonGroup`

```text
exerciseStableKey
groupId
priority
fallbackPolicy
provenanceId
```

문자열 의미 추론으로 그룹을 찾지 않는다.

---

# 8. Level 4 — 근육 고정 분석 프로필

## 목적

운동 기록의 유효 dose를 어떤 근육 분석 단위에 어떻게 배분할지 정의한다.

## 4-1 근육 분석 단위

### `MuscleAnalysisUnitRef`

초기 전환에서는 현재 앱에서 실제 집계하는 근육 분석 단위를 우선 보존한다.

예:

```text
QUADRICEPS_COMPLEX
HAMSTRING_COMPLEX
GLUTEAL_COMPLEX
CALF_PLANTARFLEXOR_COMPLEX
HIP_ADDUCTOR_COMPLEX
SPINAL_EXTENSOR_COMPLEX
PECTORAL_COMPLEX
LATISSIMUS_DORSI
SCAPULAR_RETRACTOR_ELEVATOR_COMPLEX
DELTOID_COMPLEX
ROTATOR_CUFF_COMPLEX
ELBOW_FLEXOR_COMPLEX
ELBOW_EXTENSOR_COMPLEX
FOREARM_FLEXOR_PRONATOR_COMPLEX
FOREARM_EXTENSOR_SUPINATOR_COMPLEX
HAND_GRIP_FLEXOR_COMPLEX
ANTERIOR_TRUNK_COMPLEX
LATERAL_TRUNK_COMPLEX
ROTATIONAL_TRUNK_COMPLEX
```

상위 범주와 하위 근육을 같은 사전에 섞지 않는다.

## 4-2 근육 기여 관계

### `ExerciseMuscleContribution`

```text
exerciseStableKey
muscleAnalysisUnitId
contributionRole
allocationCoefficient
provenanceId
```

`contributionRole`:

```text
PRIMARY
SECONDARY
STABILIZER
```

중요:

```text
allocationCoefficient
= 운동 dose의 정적 근육 배분값

actual muscle load
= 운동 기록 dose × allocationCoefficient × 근육 분석 프로토콜
```

실제 근육 부하와 누적값은 메타데이터가 아니다.

---

# 9. Level 5 — 배드민턴 전이 분류

## 목적

운동이 어떤 배드민턴 수행 영역과 어떤 수준으로 연결되고, 어떤 신체 능력을 훈련하는지 정의한다.

## 5-1 배드민턴 수행 영역

### `BadmintonPerformanceDomainRef`

검토 후보:

```text
FIRST_STEP_ACCELERATION
COURT_MOVEMENT_RECOVERY
COD_DECELERATION
LUNGE_REACH
JUMP_LANDING
OVERHEAD_HITTING
RACKET_CONTROL
RALLY_REPEATABILITY
```

모두 경기 수행 결과 영역이라는 동일한 논리 수준이어야 한다.

## 5-2 수행 영역별 전이 수준

### `TransferLevelRef`

```text
DIRECT
SUPPORTIVE
GENERAL
LOW
NONE
```

### `ExerciseBadmintonTransferRelation`

```text
exerciseStableKey
performanceDomainId
transferLevelId
provenanceId
```

전이 수준은 운동과 수행 영역의 정적 관계 등급이다.

실제 전이 자극량이나 `contributionWeight`를 저장하지 않는다.

```text
actual transfer stimulus
= 운동 기록 dose
× 강도 보정
× RPE 보정
× transfer level 정책 가중치
```

실제 자극량은 분석 결과다.

## 5-3 신체 능력 분류

### `PhysicalQualityRef`

검토 후보:

```text
LOWER_BODY_FORCE
UNILATERAL_STABILITY
ECCENTRIC_DECELERATION_CAPACITY
REACTIVE_SSC
ROTATIONAL_POWER
ROTATIONAL_CONTROL
SHOULDER_DURABILITY
GRIP_ENDURANCE
REACTION_DECISION
AEROBIC_BASE
ANAEROBIC_REPEATABILITY
```

### `ExercisePhysicalQuality`

```text
exerciseStableKey
physicalQualityId
provenanceId
```

근거 없는 `contributionMagnitude = 1.0`을 저장하지 않는다.

---

# 10. Level 6 — 연결조직 고정 분석 프로필

## 목적

운동이 어떤 관절·건·인대에 어떤 유형과 상대적 크기의 부하를 주는지 정의한다.

관절·건·인대는 별도 조직 엔터티로 유지한다.

## 6-1 조직 사전

### `TissueRef`

```text
tissueStableKey
tissueKind
parentJointComplexId
displayNameKo
displayNameEn
```

`tissueKind`:

```text
JOINT
TENDON
LIGAMENT
```

## 6-2 부하 유형

### `TissueLoadTypeRef`

예:

```text
TENSILE
COMPRESSIVE
SHEAR
TORSIONAL
STABILITY_DEMAND
IMPACT
SSC_REACTIVE
```

## 6-3 운동·조직 부하 관계

### `ExerciseTissueLoadRelation`

```text
exerciseStableKey
tissueStableKey
loadTypeId
loadCoefficient
recoveryProfileId
provenanceId
```

중요:

```text
Level 1-4 BodyRegion
= 어느 상위 복합체가 관여하는가

Level 6 TissueLoadRelation
= 그 안의 어떤 조직에 어떤 부하가 얼마나 연결되는가
```

BodyRegion만 보고 TissueLoad를 자동 생성하지 않는다.

## 6-4 조직별 회복 프로필

### `TissueRecoveryProfileRef`

조직별 고정 회복 곡선과 knot를 참조한다.

실제 잔여부하와 회복 상태는 운동 기록과 시간으로 계산한다.

---

# 11. Level 7 — 근거·검토·승인

## 목적

각 관계가 어디에서 왔고, 현재 동작 재현용인지, 검토된 canonical 값인지, 오류 후보인지 명시한다.

## 7-1 계약 버전

### `MetadataContractVersion`

```text
contractVersion
baselineCommit
status
createdAt
```

## 7-2 관계 provenance

### `RelationProvenance`

```text
sourceStatus
migrationFidelity
derivationMode
evidenceConfidence
humanApprovalStatus
contractVersion
reviewNote
```

### `sourceStatus`

```text
MIGRATED_CURRENT_BEHAVIOR
REVIEWED_CANONICAL
USER_PERSISTED_EXACT
UNRESOLVED
```

### `migrationFidelity`

```text
EXACT
APPROXIMATE
NOT_APPLICABLE
```

### `derivationMode`

```text
RAW_EXPLICIT_VALUE
EXACT_TOKEN_EXPANSION
LEGACY_RESOLVER_EXPLICIT
LEGACY_HEURISTIC_FALLBACK
HUMAN_REVIEWED
```

의미:

- `RAW_EXPLICIT_VALUE`: 원본 authority 필드에 명시적으로 존재한 값
- `EXACT_TOKEN_EXPANSION`: 구분자 목록을 의미 추론 없이 행으로 분리한 값
- `LEGACY_RESOLVER_EXPLICIT`: 명시적 legacy 규칙 또는 exact mapping으로 산출된 값
- `LEGACY_HEURISTIC_FALLBACK`: 원본 값이 없거나 불충분해 키워드·이름·범용 fallback으로 산출된 값
- `HUMAN_REVIEWED`: 사람이 stableKey별 의미와 근거를 검토해 승인한 값

`derivationMode`는 값이 어떻게 만들어졌는지를 나타낸다. 이는 기존 동작 재현 정확도나 과학적 근거 수준과 별개의 축이다.

### `evidenceConfidence`

```text
UNREVIEWED
LOW
MODERATE
HIGH
```

### `humanApprovalStatus`

```text
UNREVIEWED
REVIEWED
APPROVED
REJECTED
```

중요:

```text
migrationFidelity
= 기존 동작을 얼마나 정확히 재현했는가

derivationMode
= 그 값이 원본 명시값·정확한 변환·heuristic 중 어떤 방식으로 생성되었는가

evidenceConfidence
= 과학적 근거가 얼마나 강한가
```

세 개념을 하나의 `confidence = 1.0`으로 합치지 않는다.

## 7-3 근거 출처

### `EvidenceSource`

```text
sourceId
sourceType
citation
locator
urlOrIdentifier
```

### `ProvenanceEvidenceLink`

관계 하나에 여러 출처를 연결한다.

## 7-4 Legacy inference risk ledger

heuristic 또는 fallback 코드 경로가 존재한다는 사실과 실제 운동 분류 오류가 확인되었다는 사실을 구분한다.

### `LegacyInferenceRiskPath`

```text
riskPathId
affectedFile
affectedSymbol
legacyField
derivationMode
severity
evidenceLocations
impactEvaluationStatus
linkedConfirmedIssueIds
```

`impactEvaluationStatus`:

```text
NOT_EVALUATED
STABLEKEY_IMPACTED
NOT_TRIGGERED_FOR_BUILT_INS
USER_EXERCISE_ONLY
```

이 ledger의 한 행은 다음만 의미한다.

> 특정 코드 경로가 명시적 stableKey 관계가 아닌 heuristic 또는 fallback으로 값을 제조하거나 대체할 가능성이 있다.

위험 경로가 존재한다는 이유만으로 `CURRENT_BUG_PRESERVED` 또는 실제 오분류라고 단정하지 않는다.

## 7-5 Confirmed metadata issue ledger

실제 stableKey별 영향과 현재 결과를 확인한 뒤에만 확정 이슈로 등록한다.

### `ConfirmedMetadataIssue`

```text
issueId
riskPathId
exerciseStableKey
rawSourceValue
rawValuePresent
fallbackTriggered
fallbackInput
fallbackOutput
currentEffectiveRelation
expectedOrTargetRelation
affectedModule
affectedConsumers
baselineOutput
outputWithoutFallback
actualOutputDifference
confirmedErrorStatus
severity
parityImpact
approvalStatus
proposedResolution
targetVersion
evidence
```

`confirmedErrorStatus`:

```text
CONFIRMED_CLASSIFICATION_ERROR
VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION
STRUCTURAL_AMBIGUITY
MISSING_AUTHORITY
NOT_TRIGGERED_FOR_BUILT_INS
USER_EXERCISE_ONLY_RISK
```

`CURRENT_BUG_PRESERVED`는 stableKey별 현재 결과가 실제로 잘못되었다고 확인된 경우의 이행 상태로만 사용한다.

### 처리 절차

```text
legacy inference 경로 발견
→ LegacyInferenceRiskPath에 등록
→ 내장 운동 stableKey별 fallback 발동 여부와 결과 조사
→ 실제 영향이 확인된 stableKey만 ConfirmedMetadataIssue에 등록
→ 현재 동작은 BASELINE_V1에 보존
→ 오류·영향 범위를 사용자에게 보고
→ REVIEWED_V1 수정 후보 작성
→ 사용자 승인
→ 별도 변경으로 적용
```

오류를 baseline에 묻어두거나, 위험 경로만으로 실제 오류라고 단정하거나, 보고 없이 임의로 수정하지 않는다.

---

# 12. 분석 capability 상태

각 운동이 어느 분석에 참여할 수 있는지 명시적으로 관리한다.

### `AnalysisTypeRef`

```text
OFI
PROGRAM_GENERATION
MUSCLE_LOAD
BADMINTON_TRANSFER
CONNECTIVE_TISSUE
STRENGTH_PERFORMANCE
```

### `ExerciseAnalysisCapability`

```text
exerciseStableKey
analysisTypeId
status
provenanceId
```

`status`:

```text
ENABLED
DISABLED
INCOMPLETE
```

규칙:

- 내장 운동의 필수 관계 누락은 CI 실패로 처리한다.
- 사용자 운동의 관계 누락은 `INCOMPLETE`로 표시한다.
- 이름이나 다른 필드로 누락 관계를 추론하지 않는다.

---

# 13. 기존 비정규화 메타데이터 이행 전략

## 13.1 목표

현재 비정규화 메타데이터를 새 관계 구조로 옮기더라도 기존 분석과 자동 프로그램 결과를 먼저 동일하게 재현한다.

## 13.2 두 자산을 분리한다

### `BASELINE_V1`

```text
sourceStatus = MIGRATED_CURRENT_BEHAVIOR
```

의미:

> 현재 앱이 실제로 내던 결과를 그대로 재현하기 위한 데이터

### `REVIEWED_V1`

```text
sourceStatus = REVIEWED_CANONICAL
```

의미:

> 동일 논리 수준과 근거 검토를 거친 목표 데이터

검토된 값이 더 합리적이어도 baseline을 덮어쓰지 않는다.

## 13.3 변환 방식

기존 필드별로 다음 중 하나를 지정한다.

```text
DIRECT_COPY
CURRENT_RESOLVER_OUTPUT
SPLIT_EXACT_TOKENS
UNRESOLVED
DEPRECATED_NO_SUCCESSOR
LEGACY_COMPATIBILITY_READONLY
```

### `DIRECT_COPY`

의미가 명확한 필드를 그대로 이전한다.

### `CURRENT_RESOLVER_OUTPUT`

현재 production resolver가 산출하는 최종 결과를 stableKey별 관계로 동결한다. 다만 원본 명시값과 heuristic fallback을 반드시 구분한다.

#### BASELINE_V1 규칙

현재 production 결과 재현에 실제로 사용되는 heuristic fallback은 baseline에서 제거하지 않는다. 대신 다음을 강제한다.

```text
sourceStatus = MIGRATED_CURRENT_BEHAVIOR
derivationMode = LEGACY_HEURISTIC_FALLBACK
humanApprovalStatus = UNREVIEWED
linkedIssue = required
```

즉 baseline은 현재 동작을 재현하지만, 그 값을 검토된 진실로 표시하지 않는다.

#### REVIEWED_V1 규칙

원본 authority 값이 비어 있고 legacy heuristic으로만 만들어진 관계는 자동으로 reviewed canonical 값으로 승격하지 않는다.

```text
reviewed status = UNRESOLVED
```

사람이 stableKey별 의미와 근거를 검토한 뒤에만 `HUMAN_REVIEWED`와 `REVIEWED_CANONICAL`로 승격한다.

### `SPLIT_EXACT_TOKENS`

기존 구분자 목록을 의미 추론 없이 정확한 행으로 분리한다.

### `UNRESOLVED`

기존 데이터만으로 새로운 관계를 확정할 수 없다.

### `DEPRECATED_NO_SUCCESSOR`

기존 필드가 불필요하거나 동적 결과와 혼합되어 새 메타데이터에 대응 항목이 없다.

### `LEGACY_COMPATIBILITY_READONLY`

새 `REVIEWED_V1` canonical 분류나 분석 관계로 승격하지 않지만, 기존 production consumer가 남아 있어 즉시 삭제할 수 없는 필드다.

규칙:

- 기존 저장·백업·복원 형태를 읽기 전용으로 유지한다.
- 새 relation-driven 코드가 이 필드를 새로운 권위로 읽기 시작하면 안 된다.
- 신규 write path를 추가하지 않는다. 기존 호환성 보존에 필요한 write가 있다면 consumer와 제거 조건을 명시한다.
- consumer 수가 0이 되기 전에는 필드, Room column, CSV column, adapter, parser를 삭제하지 않는다.
- 제거는 별도 승인된 cleanup task에서만 수행한다.

삭제 허용 조건:

```text
production consumer count = 0
AND replacement parity passed
AND backup/restore compatibility preserved
AND rollback path verified
AND removal approved and documented
```

### `progressMetricType` 특별 처리

`progressMetricType`은 운동의 최종 canonical 분류축으로 승격하지 않는다. 그러나 현재 OFI·프로그램·UI·백업 및 legacy parsing 경로에서 소비되고 있으므로 다음과 같이 처리한다.

```text
conversionMode = LEGACY_COMPATIBILITY_READONLY
targetLayer = NON_METADATA_COMPATIBILITY_OR_ANALYSIS_PROTOCOL
targetRelation = NONE
eventualReplacementStrategy =
    REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY
```

현재 consumer의 파일·함수·읽기 목적을 전수 조사하여 machine-readable mapping matrix에 기록한다. 삭제 허용 조건을 모두 만족하기 전에는 `progressMetricType`을 삭제·rename·schema migration하면 안 된다.

### `defaultRestSeconds` 특별 처리

```text
targetLayer = PROGRAM_GENERATION
targetRelation = ExerciseProgramTimingProfile
conversionMode = DIRECT_COPY
```

이 값은 자동 프로그램의 일별 시간예산 계산에 사용되는 운동별 고정 기본값이다. 실제 생성 프로그램의 `restSeconds`와 구분한다.

현재 값, ProgramBuilder 시간 맞춤 로직, 후보·세트 축소·운동 제외 결과를 변경하지 않는다.

### `activityKind` 특별 처리

현재 감사에서 다음처럼 매핑하는 것은 금지한다.

```text
activityKind
→ MOVEMENT_ANATOMY
→ ExerciseMovementAnatomyRelation
```

`SPORT_SESSION`, `SKILL_SESSION`, `TEST_SESSION`, `RECOVERY_SESSION` 등은 운동의 고정 운동학적 분류가 아니다.

초기 이행:

```text
currentDisposition = LEGACY_COMPATIBILITY_READONLY
targetLayer = NON_METADATA_LEGACY_COMPATIBILITY
targetRelation = NONE
mappingStatus = UNRESOLVED
```

향후 고정적인 운동 카탈로그 종류가 필요하면 별도의 `CatalogItemKind` taxonomy를 설계하고 승인한다. 기존 `activityKind`를 자동 승격하지 않는다.

### `analysisEligibility` 특별 처리

`analysisEligibility`는 여러 분석과 프로그램 자격을 한 필드에 혼합한 legacy 값이다. 모든 소비처를 `ExerciseProgramCapability`로 일괄 매핑하면 안 된다.

변환 단위:

```text
legacyField
+ storageOwner
+ token
+ consumerFile
+ consumerSymbol
+ actualSemanticUse
→ targetLayer
→ targetRelation
```

목표 예:

```text
OFI consumer
→ ExerciseAnalysisCapability(OFI)

muscle consumer
→ ExerciseAnalysisCapability(MUSCLE_LOAD)

badminton consumer
→ ExerciseAnalysisCapability(BADMINTON_TRANSFER)

strength-performance consumer
→ ExerciseAnalysisCapability(STRENGTH_PERFORMANCE)

program consumer
→ ExercisePlanningProfile 또는 ExerciseProgramBlockCapability
```

검토 전에는:

```text
currentDisposition = KEEP_CURRENT_BEHAVIOR
mappingStatus = UNRESOLVED
```

로 두고 자동 전환하지 않는다.

## 13.4 Mapping Matrix의 지위와 승인 상태

legacy-to-target mapping matrix는 다음 두 기능을 분리해야 한다.

```text
consumer inventory
target mapping decision
```

필드명이나 코드 키워드로 자동 생성한 target relation은 승인된 migration specification이 아니다.

각 행에 다음 상태를 둔다.

```text
AUTO_CANDIDATE
SEMANTICALLY_REVIEWED
APPROVED
REJECTED
UNRESOLVED
```

규칙:

```text
AUTO_CANDIDATE
→ 탐색용 제안
→ REVIEWED_V1 입력 금지

SEMANTICALLY_REVIEWED
→ field + consumer symbol + 실제 사용 의미 검토 완료
→ 사용자 승인 전 REVIEWED_V1 입력 금지

APPROVED
→ REVIEWED_V1 입력 가능

REJECTED
→ target 관계로 사용 금지

UNRESOLVED
→ 정답 관계 미확정
```

키워드 기반 `Replacement-Relation` 같은 도구 출력은 기본적으로 `AUTO_CANDIDATE`다. 사람이 검토하고 승인하기 전에는 canonical mapping으로 취급하지 않는다.

---

# 14. 운영 저장 구조

## 14.1 내장 운동

권장:

```text
versioned immutable canonical asset
```

내장 운동 관계 전체를 Room에 중복 저장할 필요는 없다.

## 14.2 사용자 운동과 override

권장:

```text
Room typed relation tables
```

## 14.3 Effective resolver

```text
built-in canonical relation
+ exact stableKey user override
= effective relation
```

분석기와 자동 프로그램 생성기는 effective resolver만 읽는다.

---

# 15. 자동 프로그램 전환 계약

메타데이터 정규화 후에도 기존 자동 프로그램의 골자가 달라지면 안 된다.

## 15.1 비교 구조

```text
기존 비정규화 metadata
→ legacy ProgramBuilder
→ result A

새 typed relation
→ relation-driven ProgramBuilder
→ result B
```

## 15.2 필수 비교 항목

- 프로그램 목표
- 기간
- 주당 일수
- 주차 구조
- 요일 구조
- 프로그램 블록 구성
- 블록별 후보 stableKey
- 후보 우선순위
- 장비 필터
- 사용자 제외 운동
- 사용자 필수 운동
- 중복 억제
- 대체 후보 순서
- 메인·보조·파워·프리햅 역할
- 최종 stableKey
- 세트 수
- 반복 수
- 중량
- 시간
- RPE 범위
- 휴식
- 주차별 강도·볼륨
- 디로드
- 다른 주차가 재사용하는 공통 프로그램 구조

## 15.3 차이 분류

```text
MIGRATION_IMPLEMENTATION_ERROR
MISSING_PROGRAM_RELATION
CURRENT_PROGRAM_BUG_PRESERVED
REVIEWED_CORRECTION_CANDIDATE
INTENTIONAL_CHANGE_APPROVED
```

승인되지 않은 차이가 있으면 production 전환을 금지한다.

---

# 16. 분석별 전환 순서

권장 순서:

```text
근육
→ 프로그램
→ 배드민턴
→ OFI
```

연결조직은 이미 별도 authority 구조가 있으므로 기존 reviewed row와 exact-key parity를 우선 확인한다.

각 모듈은 다음 순서로 전환한다.

```text
legacy authoritative
→ relation-driven shadow
→ parity report
→ 오류·쟁점 보고
→ feature flag cutover
→ 안정화
→ legacy reader 제거
```

한 번에 전체 분석을 전환하지 않는다.

---

# 17. 필수 검증 테스트

## 17.1 스키마 무결성

- 모든 관계의 exact stableKey FK
- 등록되지 않은 참조 ID 거부
- 단일 profile 중복 거부
- 다중 relation 중복 거부
- 음수 coefficient 거부
- provenance 누락 거부
- 잘못된 관절·동작 조합 거부
- 자유 문자열 relation ID 금지

## 17.2 taxonomy

- 각 참조 사전이 하나의 질문에만 답하는지 검증
- 복합 코드 탐지
- 상위·하위 수준 혼합 탐지
- 공식 한국어 용어표 완전성
- 자동 underscore 번역 금지
- deprecated code가 REVIEWED_V1에 남지 않는지 검증

## 17.3 프로그램 parity

- 모든 프로그램 목표
- 주 3~7일
- 모든 기간·주차
- 세션 시간 경계
- 장비 조합
- 제외·필수 운동
- 사용자 운동
- 후보 부족
- 중복 억제
- 운동 교체
- 배드민턴 비율
- 피로·회복 조건
- 기존 저장 프로그램 재활용

## 17.4 OFI parity

- 운동별 5축 기여
- 일별 점수
- 기준선
- 회복 감쇠
- 종합 OFI
- readiness
- 축별 경고와 최종 문구

## 17.5 근육 parity

- 운동 하나의 다중 근육 배분
- 여러 운동의 동일 근육 누적
- 맨몸 유효부하
- duration hold
- 누락 시 INCOMPLETE

## 17.6 배드민턴 parity

- 수행 영역과 신체 능력 분리
- 영역별 transfer level
- 기록 dose·중량·RPE
- 다중 영역 분배
- 7일·28일 집계
- 상위 기여 운동

## 17.7 연결조직 parity

- 기존 reviewed authority row
- 관절·건·인대 분리
- exact tissueStableKey
- 조직별 load type
- 회복 프로필
- 누락 시 추측 금지

---

# 18. 금지 구현

## 18.1 범용 EAV

금지:

```text
exercise_metadata(
    exerciseStableKey,
    fieldName,
    value
)
```

이 구조는 논리 수준과 허용값을 강제하지 못한다.

## 18.2 구분자 저장

금지:

```text
A|B|C
A,B,C
```

## 18.3 분석기 내부 의미 복원

금지:

```text
contains("SQUAT")
contains("FOREARM")
contains("OVERHEAD")
name matching
stableKey fragment matching
```

## 18.4 누락값 임의 fallback

금지:

```text
unknown
→ 이름이 비슷하므로 임의 분류
```

허용:

```text
unknown
→ INCOMPLETE
→ 오류·쟁점 Ledger
```

## 18.5 동적 값 저장

금지:

- 현재 OFI 점수
- 주간 근육 부하
- 배드민턴 자극량
- 조직 잔여부하
- 사용자 기록 방식
- 프로그램 처방 결과

---

# 19. 다음 작업

## Phase 2A — 의미 매핑 교정 및 stableKey 영향 감사

이 단계는 문서·감사·테스트 전용이며 생산 동작을 변경하지 않는다.

### Task A — Mapping generator 의미 교정

다음 매핑을 고친다.

```text
defaultRestSeconds
→ ExerciseProgramTimingProfile

activityKind
→ targetRelation = NONE
→ NON_METADATA_LEGACY_COMPATIBILITY

analysisEligibility
→ field + token + consumer symbol + actual semantic use별 분해
```

`legacyField → 하나의 target relation` 방식의 일괄 매핑을 금지한다.

### Task B — Mapping 상태 도입

1,254행 기존 mapping matrix에 최소한 다음을 추가한다.

```text
consumerSemanticUse
mappingStatus
reviewEvidence
reviewedBy
approvedBy
```

모든 자동 생성 행의 초기값은 `AUTO_CANDIDATE` 또는 `UNRESOLVED`다.

### Task C — 20개 위험 경로의 stableKey별 영향 조사

224개 내장 운동에 대해 각 위험 경로의 실제 발동 여부를 조사한다.

```text
riskPathId
exerciseStableKey
rawSourceValue
rawValuePresent
fallbackTriggered
fallbackInput
fallbackOutput
affectedRelation
affectedConsumers
baselineOutput
outputWithoutFallback
actualOutputDifference
reviewClassification
```

### Task D — 확정 오류 보고서 분리

다음 파일을 별도로 생성한다.

```text
confirmed_metadata_errors.csv
confirmed_metadata_errors.md
```

실제로 잘못된 것으로 확인된 stableKey만 포함한다.

### Task E — 자동 프로그램 parity 유지

다음은 변경하지 않는다.

```text
defaultRestSeconds 값
운동별 예상시간 계산
일별 가능시간 적용
세트 축소
시간 부족 시 운동 제외
프로그램 블록
후보 순서
최종 운동
처방 결과
```

### Phase 2A 금지 범위

```text
ProgramBuilder 생산 코드 변경 금지
자동 프로그램 결과 변경 금지
analysis/contracts Kotlin 변경 금지
analysis_contract_baseline_v1.csv 변경 금지
Room schema 변경 금지
backup/restore schema 변경 금지
REVIEWED_V1 row 생성 금지
Kotlin provenance model 확장 금지
```

## Phase 2B — Target taxonomy 및 관계 의미 승인

Phase 2A 결과를 사용자에게 보고하고 승인받은 뒤에만 진행한다.

- 7개 상위 계층의 참조 사전 확정
- 공식 한국어 표시명 확정
- `fitScore`의 metadata/policy 지위 확정
- 근육 coefficient의 multiplier/share 계약 확정
- OFI 정적 계수와 synthetic snapshot 분리
- 배드민턴 static transfer와 dynamic contribution 분리
- `BodyRegion`의 canonical 저장 여부 확정

## Phase 3 — Kotlin provenance 모델 확장

Phase 2B 승인 후 별도 작업으로 진행한다.

```text
REVIEWED_CANONICAL
derivationMode
migrationFidelity
evidenceConfidence
humanApprovalStatus
```

이 단계에서도 BASELINE_V1은 덮어쓰지 않으며 production analyzer cutover는 별도 승인 작업으로 남긴다.

---

# 20. 완료 기준

다음 조건을 모두 만족해야 메타데이터 정규화가 완료된 것으로 본다.

- 운동 기록·진행 지표·분석 결과가 메타데이터 문서에서 제거됨
- 모든 내장 운동이 필수 typed relation을 보유함
- 각 관계 ID가 승인된 참조 사전을 사용함
- 한 필드가 한 질문에만 답함
- 다중관계가 구분자 문자열이 아닌 행으로 저장됨
- 프로그램 자동 생성의 전체 골자와 일별 운동 가능시간에 따른 시간 맞춤 결과가 유지됨
- `defaultRestSeconds`가 `ExerciseProgramTimingProfile`로 분리되고 실제 처방 `restSeconds`와 구분됨
- OFI·근육·배드민턴·연결조직 parity가 검증됨
- heuristic 위험 경로가 `LegacyInferenceRiskPath`에 기록됨
- stableKey별 실제 영향이 확인된 오류만 `ConfirmedMetadataIssue`에 기록되고 사용자에게 보고됨
- 사용자 운동은 누락 관계를 추론하지 않고 INCOMPLETE로 처리됨
- 분석기가 이름·문자열 조각·legacy parser를 사용하지 않음
- BASELINE_V1과 REVIEWED_V1이 분리됨
- Room·백업·프로토콜 문서가 동일 metadata contract version을 참조함
- `LEGACY_COMPATIBILITY_READONLY` 필드가 consumer 0·parity·backup·rollback·승인 조건 전에는 삭제되지 않음
- baseline의 heuristic-derived 관계가 `LEGACY_HEURISTIC_FALLBACK`과 Issue Ledger로 추적됨
- REVIEWED_V1이 heuristic-only 관계를 검토 없이 canonical truth로 승격하지 않음
- `activityKind`가 운동학 relation으로 자동 승격되지 않음
- `analysisEligibility`가 모든 소비처에서 프로그램 capability로 일괄 매핑되지 않음
- `AUTO_CANDIDATE` mapping이 승인 없이 REVIEWED_V1에 사용되지 않음

# 21. v2.4 canonical workbook authority cutover

## 21.1 Authority and generated runtime assets

Human edits are made only in
`docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx`.
`tools/metadata_authority` validates and deterministically exports
`app/src/main/assets/metadata/canonical_v1`. Android does not parse XLSX and
normal bundled loading has no seed/name/category inference fallback.

The publishing sequence is fixed:

```text
Edit workbook -> validate/export -> review generated diff -> run tests
-> commit workbook and assets together
```

## 21.2 Approved cutover boundaries

- `mappingConfidence` records mapping confidence only;
  `identityDecisionStatus` records retention/approval decisions.
- The 16 `HISTORY_ONLY_GENERIC` identities stay readable, inactive, and
  non-selectable. Historical stableKeys are not mapped to equipment variants.
- `single_leg_rdl` and `ex_bd072cd` keep history-only relations; active slots
  belong only to their explicitly approved concrete variants.
- `ex_8824026f` is `STRENGTH` and `ACCESSORY_SLOT` with approved provenance.
- `ExerciseProgramTimingProfile.defaultRestSeconds` contains the exact old
  effective value for all 241 selectable exercises. It is not generated
  prescription `restSeconds`.

## 21.3 Runtime ownership and compatibility

`CanonicalExerciseMetadataRepository` owns bundled identity and fixed relation
loading. `SeedData` is a bootstrap facade. `ExerciseMetadataMapper` is retained
only for legacy/import compatibility and legacy parity fixtures; it is not a
normal bundled metadata source. Protected scientific relations and stableKey
lineage override ordinary persisted metadata, while custom exercise/user fields
retain their existing persistence behavior.

Room stays at schema 27. Backup format and restore schema do not change. No
workout, saved program, custom exercise, user ID, or generic history stableKey
is rewritten.

## 21.4 Scope and scientific boundaries

Production export excludes `RESEARCH_DRAFT`, `DEFERRED_REVIEW`, and
`NOT_ADJUDICATED` relation rows. Relationship correctness remains explicitly
`NOT_ADJUDICATED`. `researchContextAxisScoreC` is not
`runtimeCodContextModifier`; research scores such as 7.8 or 9.3 are never used
as runtime multipliers. The reviewed tissue runtime contract remains separate,
with ordinary multiplier 1.00 and approved maximum 1.09.

## 21.5 Rollback

Revert the 0.5.0.22 application commit to restore the previous asset/bootstrap
path. No Room rollback or user-data rewrite is necessary because this cutover
has no schema migration and performs no generic-to-variant history rewrite.
