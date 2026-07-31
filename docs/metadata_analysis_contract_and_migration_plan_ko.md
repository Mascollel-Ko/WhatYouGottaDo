# WhatYouGottaDo 메타데이터·분석 계약 재설계 보고서

- 대상 저장소: `Mascollel-Ko/WhatYouGottaDo`
- 검토 기준 커밋: `47f93eadaff64a49f6dc886a9319191c7388029c`
- 기준 앱 버전: `0.5.0.17`
- 문서 목적: OFI, 자동 프로그램 생성, 근육 분석, 배드민턴 전이 분석의 입력 계약을 명확히 하고, 기존 분석 결과를 최대한 보존하면서 문자열 추론 중심의 메타데이터 구조를 `stableKey` 기반 명시적 관계 구조로 이행하기 위한 기준을 확정한다.

---

## 0. v2.1 의미 경계와 구현 상태

### 0.1 고정 속성만 운동 메타데이터다

운동 메타데이터는 운동 자체에 고정된 속성이다. 사용자가 한 기록에서 입력한 중량, 반복수, 세트, 시간, 거리, RPE, RIR, 입력 조합, 세션 목적과 e1RM·총볼륨·주간 성장률 같은 진행 지표는 운동 메타데이터가 아니다. 현재 OFI, 근육 부하, 배드민턴 자극, 잔여 조직 부하와 프로그램 결과도 파생 결과이지 메타데이터가 아니다. 이 제외 개념을 위한 대체 운동 메타데이터 table은 만들지 않는다.

운동 identity와 함께 사용하는 일곱 metadata layer는 다음과 같다.

| 경계 | 고정 관계 예 | 제외되는 값 |
|---|---|---|
| Exercise identity | `stableKey`, 활성·archive·custom 상태 | 세션별 입력값 |
| Movement and anatomy | 동작, 관절, 신체 부위, modality | 기록 입력 모드 |
| Program generation | slot capability, role, group, equipment requirement | 생성된 프로그램·처방 |
| OFI | 고정 축 routing, dose/recovery relation | 현재 OFI·readiness |
| Muscle | 고정 muscle allocation relation | 현재 근육 부하 |
| Badminton | 수행 영역 transfer와 physical quality membership | 현재 전이 자극 |
| Connective tissue | 고정 조직 load/recovery relation | 현재 잔여 조직 부하 |
| Provenance and review | 출처, 검토, 승인 상태 | 진행 분석 결과 |

장비 관계는 자동 프로그램 생성의 필수 소비자이므로 program-generation layer에 둔다. 같은 canonical 장비 관계를 검색, 필터, 표시, 장비 가용성 기능이 읽는 것은 허용한다.

### 0.2 현재 호환 필드와 제거 gate

`progressMetricType`은 target canonical exercise metadata가 아니다. 현재 production 분석, 처방, UI, 백업·복원 소비자가 남아 있으므로 `LEGACY_COMPATIBILITY_READONLY`로 유지한다. 미래 책임은 canonical metadata relation이 아니라 분석·처방 protocol로 이동한다.

호환 필드는 다음 조건을 모두 만족하기 전에는 Room, adapter, backup/restore 또는 production model에서 삭제·이름변경할 수 없다.

```text
production consumer count = 0
AND replacement parity passed
AND backup/restore compatibility preserved
AND rollback path verified
AND removal explicitly approved and documented
```

현재 처리와 미래 목적지는 별개다. `currentDisposition`은 현재 checkout의 필수 처리이고, `eventualReplacementStrategy`는 위 gate 이후의 이동 방향이다. 구형 `recommendedDisposition`은 tooling 호환용 deprecated alias이며 `currentDisposition`과 같아야 한다.

### 0.3 provenance target과 현재 구현의 구분

Audit와 미래 target에서 사용하는 derivation mode는 다음과 같다.

- `RAW_EXPLICIT_VALUE`
- `EXACT_TOKEN_EXPANSION`
- `LEGACY_RESOLVER_EXPLICIT`
- `LEGACY_HEURISTIC_FALLBACK`
- `HUMAN_REVIEWED`
- `NOT_APPLICABLE`

`BASELINE_V1`은 현재 동작 재현용 immutable authority다. heuristic으로 만들어진 현재 관계는 BASELINE_V1에서 `MIGRATED_CURRENT_BEHAVIOR`, `LEGACY_HEURISTIC_FALLBACK`, `UNREVIEWED`, linked issue로 기록한다. 이는 reviewed truth가 아니다. 미래 `REVIEWED_V1`에서는 stableKey 단위 사람 검토 전까지 `UNRESOLVED`이며, 현재 출력과 같다는 이유만으로 `REVIEWED_CANONICAL`이 될 수 없다. migration fidelity와 evidence confidence도 서로 다른 개념이다.

| 구분 | 현재 Phase 0/1 shadow 구현 | 미래 v2.1 target |
|---|---|---|
| Source status | `MIGRATED_CURRENT_BEHAVIOR`, `USER_PERSISTED_EXACT`, `UNRESOLVED` | source와 review 상태를 분리 |
| Relation confidence | scalar `Double` 하나 | migration fidelity와 evidence confidence 분리 |
| `derivationMode` | Kotlin 미구현 | 별도 승인 후 구현 |
| `migrationFidelity` | Kotlin 미구현 | 별도 승인 후 구현 |
| `evidenceConfidence` | Kotlin 미구현 | 별도 승인 후 구현 |
| `REVIEWED_CANONICAL` | Kotlin 미구현 | human approval와 함께 별도 구현 |
| Production consumer | contract repository를 읽지 않음 | parity와 승인 후 별도 cutover |

이번 v2.1 audit은 target schema를 문서와 감사 산출물에만 표현한다. 현재 Kotlin enum/data class, baseline CSV shape, loader, repository, projector, shadow diff는 확장하지 않는다.

### 0.4 migration issue ledger와 프로그램 parity

`metadata_migration_issue_ledger`는 legacy fallback·catch-all·substring·token inference를 evidence 위치와 함께 보존한다. 발견된 current bug나 ambiguity는 이 audit에서 고치지 않는다. 제안된 correction은 별도 검토와 승인을 받아야 한다.

자동 프로그램 parity는 candidate set, filtering/gates, slot capability, ordering, score trace, selected stableKey, prescription, warnings와 최종 schedule 전체를 고정 입력·random seed에서 비교해야 한다. v2.1 audit은 어떤 후보, 순서, 처방, message도 변경하지 않는다.

---

## 1. 결론

현재 앱에는 `stableKey`를 중심으로 운동, 운동기록, 런타임 메타데이터를 연결하는 기반이 이미 존재한다. 연결조직 분석은 운동 `stableKey`에 연결된 승인된 프로토콜과 authority row를 직접 조회하는 방식으로 비교적 올바르게 구현되어 있다.

반면 OFI, 프로그램 생성, 근육 분석, 배드민턴 전이 분석은 다음 방식이 혼재한다.

1. `stableKey`로 운동과 메타데이터를 찾는다.
2. 여러 문자열 필드를 하나의 토큰 집합으로 합친다.
3. `contains`, `split`, 운동명 검색, 코드 일부 일치로 의미를 다시 추정한다.
4. 추정 결과를 분석 또는 프로그램 생성에 사용한다.

이 구조는 메타데이터의 논리 수준을 흐리고, 표시용 코드와 분석용 권위를 혼동하며, 같은 문자열이 여러 분석에 예상치 못한 영향을 주게 만든다.

향후 구조는 다음 원칙으로 전환해야 한다.

> 분석은 번역된 문구, 운동 이름, 코드 조각, 구분자 문자열을 읽지 않는다. 운동 `stableKey`에 직접 연결된 명시적 metadata point와 관계 행만 읽는다.

다만 이 구조 개편은 **현재 분석 의미를 다시 설계하는 작업이 아니다.** 첫 번째 목표는 현재 분석 결과를 그대로 재현하는 명시적 데이터 구조를 만드는 것이다. 새로운 과학적 해석, 계수 변경, 임계값 변경, 프로그램 선정 정책 변경은 완전한 동등성 검증이 끝난 뒤 별도 변경으로 수행해야 한다.

---

## 2. 절대 보존 원칙

### 2.1 기존 분석 결과의 의미를 보존한다

새 구조 도입 과정에서 다음 항목을 동시에 변경하면 안 된다.

- OFI 축 정의, 계수, RPE 반영, 기준선, 감쇠, 임계값, 라벨
- 프로그램 슬롯 구성, 후보 우선순위, 중복 억제, 자동 교체, 세트 처방
- 근육 볼륨 산정식과 현재 집계 단위
- 배드민턴 전이 축, 점수, 등급, 추천 해석
- 연결조직 계산
- 사용자에게 표시되는 분석 결과 문장

첫 이행 단계에서는 기존 알고리듬을 **oracle**로 유지하고, 새 구조가 같은 입력에서 같은 출력을 내는지 비교해야 한다.

### 2.2 하나의 운동은 분석별로 여러 point에 동시에 속할 수 있다

`partition` 원칙은 “운동 하나는 전체 앱에서 딱 한 범주에 속한다”는 뜻이 아니다.

정확한 원칙은 다음과 같다.

- 하나의 **단일선택 축 내부** 값들은 동일 논리 수준이며 서로 배타적이어야 한다.
- 하나의 운동은 서로 다른 분석축 또는 관계 테이블의 여러 행에 동시에 속할 수 있다.
- 다중 기여가 자연스러운 분석은 단일 문자열이 아니라 다중 관계 행으로 표현한다.

예를 들어 백스쿼트는 동시에 다음에 속할 수 있다.

| 분석 | 가능한 명시적 point |
|---|---|
| OFI | 고중량·신경성, 전신 근육, 국소 근육 |
| 프로그램 | 하체 무릎 우세 슬롯 PRIMARY, 하체 메인 역할 ANCHOR |
| 근육 | 대퇴사두근, 둔근, 척추기립근 |
| 배드민턴 | 하체 힘 GENERAL 또는 SUPPORTIVE, 감속 능력 SUPPORTIVE |

이 다중 소속을 `SQUAT_MAIN_HEAVY_LOWER` 같은 하나의 복합 문자열로 압축하면 안 된다.

### 2.3 분석 경로에서 문자열 파싱과 의미 추론을 금지한다

운영 분석 코드에서 다음을 사용해 운동 의미를 판정해서는 안 된다.

```kotlin
split("|")
split(",")
contains("HEAVY")
contains("FOREARM")
exerciseName.contains(...)
stableKey.contains(...)
```

레거시 데이터를 새 구조로 옮기는 일회성 도구에서도 이름·코드 부분 일치에 따른 추정은 금지한다. 이미 존재하는 current resolver output 또는 사람이 승인한 `stableKey`별 매핑만 사용할 수 있다.

### 2.4 누락을 추측으로 감추지 않는다

- 내장 운동: 필수 metadata point 누락 시 CI 실패
- 사용자 운동: 해당 분석에서 `INCOMPLETE_METADATA`로 제외하고 사용자에게 설정 필요를 표시
- 이름이나 다른 필드로 임의 복구 금지

### 2.5 메타데이터 정리는 정보 손실 없이 수행한다

현재 필드 수가 많다는 이유로 바로 삭제하면 안 된다. 각 필드를 다음 중 하나로 분류해야 한다.

1. 분석 권위 필드로 유지
2. 더 명확한 관계 테이블로 분해
3. 표시·설명 전용으로 유지
4. provenance 전용으로 유지
5. 레거시 호환용으로 deprecated
6. 완전 중복으로 제거 가능

삭제 전에 반드시 “어느 분석·프로그램·백업·UI가 읽는가”를 확인한다.

---

## 3. 공통 데이터 모델 원칙

### 3.1 운동 식별

```text
Exercise
- stableKey (PK)
```

운동명, 표시명, 번역명은 식별자가 아니다.

### 3.2 분석 capability

```text
ExerciseAnalysisCapability
- exerciseStableKey
- analysisTypeId
- status: ENABLED / DISABLED / INCOMPLETE
- confidence
- sourceStatus
```

분석 종류 예:

- OFI
- PROGRAM_GENERATION
- MUSCLE_LOAD
- BADMINTON_TRANSFER
- CONNECTIVE_TISSUE

### 3.3 provenance

모든 분석 권위 행에는 최소한 다음이 필요하다.

```text
confidence
sourceStatus
evidenceClaimId 또는 sourceRef
version
humanApprovalStatus
```

표시용 한국어 라벨은 provenance와 분리한다.

---

# 4. OFI 분석 계약

## 4.1 분석 목적

수행한 운동의 dose를 다음 다섯 피로 축에 배분하고, 시간 경과와 개인 기준선을 반영해 현재 피로를 계산한다.

- HIGH_FORCE_NEURAL
- SYSTEMIC_MUSCULAR
- LOCAL_MUSCULAR
- HIGH_SPEED
- REACTIVE

이 다섯 축은 서로 배타적인 분류가 아니다. 운동 하나가 여러 축에 동시에 기여한다.

## 4.2 필요한 canonical point

### A. `ExerciseOfiDoseProfile`

```text
exerciseStableKey
doseBasisId
recoveryProfileId
```

예:

- LOAD_REPETITIONS
- EFFECTIVE_BODYWEIGHT_REPETITIONS
- DURATION_HOLD
- SESSION_DURATION
- EVENT_COUNT
- DISTANCE_TIME
- QUALITY_COUNT

`doseBasisId`는 고정 OFI routing relation이며 세션에서 선택한 기록 입력 모드나 측정값을 저장하지 않는다. 실제 중량·반복·시간·거리·event 입력 해석은 별도 workload protocol 책임이다. `progressMetricType`과 OFI workload basis도 분리한다. 진행 측정 방식과 피로 dose 방식이 우연히 같을 수는 있지만 같은 개념은 아니다.

### B. `ExerciseOfiAxisContribution`

```text
exerciseStableKey
axisId
coefficient
recoveryProfileId
confidence
sourceStatus
version
```

운동 하나당 축별 0개 또는 1개 행을 두며, 여러 축 행이 동시에 존재할 수 있다.

초기 migration의 계수는 새로운 해석으로 정하지 않는다. 현재 release가 같은 운동을 같은 기록에서 계산하는 유효 결과를 재현하도록 추출·고정한다.

### C. `ExerciseOfiComparisonGroup`

```text
exerciseStableKey
comparisonPurpose
groupId
```

목적별 그룹을 분리한다.

- WORKLOAD_BASELINE
- LOCAL_REPEAT_DETECTION
- STRENGTH_COMPARISON

`movementFamily` 하나를 모든 비교 목적에 재사용하지 않는다.

## 4.3 기존 결과 보존 기준

- 동일 백업 입력에서 일별 다섯 축의 반올림 점수는 정확히 동일해야 한다.
- OFI 총점과 readiness label은 정확히 동일해야 한다.
- 내부 double 결과는 정의된 부동소수점 허용오차 이내여야 한다.
- 현재 baseline 선정 순서와 회복 감쇠는 변경하지 않는다.
- 새 구조가 안정화되기 전 기존 OFI 계산기를 제거하지 않는다.

---

# 5. 자동 프로그램 생성 계약

## 5.1 분석 목적

프로그램 생성기는 다음을 수행한다.

1. 프로그램 템플릿 슬롯별 후보 운동 확인
2. 슬롯 내 역할과 적합도 판단
3. 유사 운동 중복 억제
4. 장비, 제외, 선호, 사용 가능성 조건 적용
5. 기존 알고리듬의 우선순위에 따라 최종 운동 선택

운동 이름이나 메타데이터 문자열을 보고 동작 의미를 재추정하는 기능이 아니다.

## 5.2 필요한 canonical point

### A. `PlanningEligibility`

단일값 partition:

- PROGRAM_SELECTABLE
- MANUAL_ONLY
- ANALYSIS_ONLY
- HIDDEN

### B. `ExerciseProgramSlotCapability`

```text
exerciseStableKey
programSlotId
capabilityRole: PRIMARY / SECONDARY / LIMITED
fitScore
confidence
sourceStatus
```

운동 하나는 여러 슬롯 capability를 가질 수 있다.

슬롯은 동일한 논리 수준의 “프로그램 템플릿에서 채워야 하는 기능적 자리”여야 한다.

권장 슬롯 계층:

- LOWER_KNEE_DOMINANT
- LOWER_HIP_DOMINANT
- LOWER_UNILATERAL
- UPPER_HORIZONTAL_PUSH
- UPPER_VERTICAL_PUSH
- UPPER_HORIZONTAL_PULL
- UPPER_VERTICAL_PULL
- TRUNK_ANTI_EXTENSION
- TRUNK_ANTI_ROTATION
- TRUNK_LATERAL_STABILITY
- ROTATIONAL_POWER
- CARRY_CAPACITY
- CALF_ANKLE_CAPACITY
- SHOULDER_SCAPULAR_CAPACITY
- FOREARM_GRIP_CAPACITY
- REACTIVE_JUMP_LANDING
- COD_DECELERATION
- FOOTWORK_REACTION
- RECOVERY_PREHAB
- GENERAL_ACCESSORY

### C. `ExerciseProgramRoleEligibility`

```text
exerciseStableKey
roleId
eligibility
```

역할 예:

- ANCHOR
- SUPPORT
- ACCESSORY
- CORE
- PREHAB
- TRANSFER

### D. `ExerciseVariantGroup`

```text
exerciseStableKey
variantGroupId
```

용도:

- 중복 억제
- 교체 후보
- 거의 동일한 변형 운동 식별

### E. `ExerciseProgressionGroup`

```text
exerciseStableKey
progressionGroupId
```

근력 추이 비교 목적이다. variant group과 분리한다.

## 5.3 기존 결과 보존 기준

고정된 사용자 설정, 장비, 제외·선호 조건, random seed에서 다음이 같아야 한다.

- 후보 집합
- 슬롯 capability
- 후보 정렬
- 선택된 운동 stableKey
- 주차·요일 배치
- 세트·반복·중량·시간 처방
- 최적화 메시지의 의미

새 구조 도입과 동시에 슬롯 정의나 우선순위를 개선하지 않는다.

---

# 6. 근육 분석 계약

## 6.1 분석 목적

운동 기록의 dose를 일관된 근육 분석 단위에 배분해 기간별 볼륨과 추이를 계산한다.

## 6.2 근육 분석 단위

현재 앱 수준에서 우선 유지할 실용적 집계 단위 예:

- QUADRICEPS
- HAMSTRINGS
- GLUTEALS
- CALVES
- HIP_ADDUCTORS
- SPINAL_ERECTORS
- PECTORALS
- LATISSIMUS_DORSI
- UPPER_BACK_SCAPULAR
- DELTOIDS
- ROTATOR_CUFF
- BICEPS
- TRICEPS
- FOREARM_FLEXOR_EXTENSOR
- GRIP
- ANTERIOR_CORE
- LATERAL_CORE
- ROTATIONAL_CORE

이들은 모두 “근육 부하 분석 단위”라는 동일한 수준이어야 한다.

세부 해부 구조가 필요하면 별도 anatomy unit을 분석 unit에 연결한다.

## 6.3 필요한 canonical point

### `ExerciseMuscleContribution`

```text
exerciseStableKey
muscleAnalysisUnitId
contributionRole: PRIMARY / SECONDARY / STABILIZER
contributionCoefficient
confidence
sourceStatus
```

운동 하나는 여러 근육 행을 가진다.

현재의 “주동근은 1.0, 보조근은 0.5” 일괄 규칙은 초기 parity를 위해 유지할 수 있으나, 새 구조에는 운동별 계수를 저장할 수 있어야 한다.

## 6.4 기존 결과 보존 기준

- 현재 muscle bucket별 볼륨과 주간·기간 추이가 동일해야 한다.
- 현재 사용자 화면의 근육 분류 순서와 합계가 동일해야 한다.
- canonical 운동에는 이름 기반 fallback을 사용하지 않는다.
- 새 relation이 없는 사용자 운동은 해당 근육 분석에서 제외하고 불완전 상태를 표시한다.

---

# 7. 배드민턴 전이 분석 계약

## 7.1 분석 목적

각 운동이 배드민턴 수행의 어떤 영역에 어느 수준으로 기여하는지 계산한다.

수행 영역과 일반 신체 능력을 분리한다.

## 7.2 배드민턴 수행 영역

동일 논리 수준의 수행 결과 영역:

- FIRST_STEP_ACCELERATION
- COURT_MOVEMENT_RECOVERY
- COD_DECELERATION
- LUNGE_REACH
- JUMP_LANDING
- OVERHEAD_HITTING_POWER
- RACKET_CONTROL_DURABILITY
- RALLY_REPEATABILITY_CONDITIONING

## 7.3 신체 능력 영역

- LOWER_BODY_FORCE
- UNILATERAL_STABILITY
- ECCENTRIC_DECELERATION_CAPACITY
- REACTIVE_SSC
- ROTATIONAL_POWER
- ROTATIONAL_CONTROL
- SHOULDER_DURABILITY
- GRIP_ENDURANCE
- REACTION_DECISION
- AEROBIC_BASE
- ANAEROBIC_REPEATABILITY

## 7.4 필요한 canonical point

### A. `ExerciseBadmintonTransferPoint`

```text
exerciseStableKey
performanceDomainId
transferLevel: DIRECT / SUPPORTIVE / GENERAL / NONE
contributionWeight
confidence
sourceStatus
```

운동 하나는 여러 performance domain 행을 가질 수 있다.

### B. `ExercisePhysicalQualityPoint`

```text
exerciseStableKey
physicalQualityId
contributionMagnitude
confidence
sourceStatus
```

## 7.5 기존 결과 보존 기준

- 현재 분석 축 포함 여부가 동일해야 한다.
- 현재 transfer type, score, fatigue cost, 추천 정렬이 동일해야 한다.
- `forceType`, `plane`, `movementPattern`, 근육명은 전이 점수를 새로 생성하는 근거로 사용하지 않는다.
- 초기 migration은 현재 mapper가 최종 산출한 축을 `stableKey`별 명시적 row로 고정한다.
- 이후 과학적 재평가는 별도 프로토콜 변경으로 수행한다.

---

# 8. 동작·해부 메타데이터 정리

분석 권위와 사용자 설명을 구분해야 한다.

## 8.1 유지할 표시·설명용 구조

### `ExerciseMovementPatternMembership`

```text
exerciseStableKey
patternId
role: PRIMARY / SECONDARY
```

패턴 예:

- SQUAT
- HINGE
- LUNGE
- HORIZONTAL_PUSH
- VERTICAL_PUSH
- HORIZONTAL_PULL
- VERTICAL_PULL
- CARRY
- TRUNK_ROTATION
- TRUNK_ANTI_ROTATION
- LOCOMOTION
- JUMP
- LANDING
- COD

운동 형식이나 목적을 패턴에 넣지 않는다. `ISOLATION`, `PREHAB`, `MOBILITY`, `TEST`는 별도 축이다.

### `ExerciseJointAction`

```text
exerciseStableKey
jointId
actionId
role: PRIMARY / SECONDARY
```

예:

```text
ex_93538692 | SHOULDER | ABDUCTION | PRIMARY
```

표시:

```text
견관절 외전
```

### `ExerciseBodyRegionMembership`

```text
exerciseStableKey
bodyRegionId
role
```

운동 하나가 상체·몸통·하체에 동시에 관여할 수 있으므로 단일 `UPPER/LOWER/WHOLE_BODY` 문자열로 제한하지 않는다.

### `ExerciseModality`

단일값 partition 예:

- COMPOUND
- SINGLE_JOINT
- LOCOMOTOR_DRILL
- SPORT_SKILL_DRILL
- MOBILITY
- ISOMETRIC_CONTROL
- TEST

### `TrainingGoalMembership`

다중 관계:

- MAX_STRENGTH
- HYPERTROPHY
- POWER
- SPEED
- REACTIVE
- STABILITY
- CONDITIONING
- PREHAB
- RECOVERY

## 8.2 기존 필드 정리표

| 기존 필드 | 처리 원칙 |
|---|---|
| `movementPattern` | relation 기반 pattern membership으로 전환. 분석 권위에서 제거 |
| `movementCategory` | modality, training goal, test/session type으로 분해 |
| `movementFamily` | variant group, program capability, pattern membership으로 분해 후 deprecated |
| `movementSubtype` | exact 운동 식별은 stableKey가 담당. 필요한 관절 동작·변형 정보로 분해 |
| `forceType` | 현재 단일 enum 폐기 또는 biomechanical demand relation으로 분해. 분석 권위에서 제거 |
| `bodyRegion` | 다중 body-region relation으로 전환 |
| `trainingRole` | program role과 training goal로 분리 |
| `primaryMuscles`, `secondaryMuscles` | muscle contribution relation으로 전환 |
| `stabilityRoles` | joint/control demand 또는 program capability로 분해 |
| `fatigueCategories` | OFI axis point로 대체 |
| `adaptiveBaselineGroups` | 목적별 OFI comparison group으로 대체 |
| `badmintonTransferRoles` | transfer point로 대체 |
| `courtMovementTypes` | performance domain 또는 physical quality로 분해 |
| `jointStressTags` | 연결조직 authority와 UI 설명으로 분리 |
| `analysisEligibility` | typed analysis capability relation으로 전환 |
| `progressMetricType` | target canonical metadata에서 제외. 현재는 `LEGACY_COMPATIBILITY_READONLY`, 제거 gate 이후 분석·처방 protocol로 이동 |
| source/confidence 필드 | provenance 구조로 통합 |

## 8.3 레거시 필드 제거 시점

레거시 필드는 다음 조건을 모두 충족한 뒤 제거한다.

1. production consumer count가 0
2. replacement parity 통과
3. 백업·복구 호환성 보존
4. rollback 경로 검증
5. 제거가 명시적으로 승인되고 문서화됨

---

# 9. 기존 분석 결과 보존 전략

## 9.1 기준 커밋 동결

현재 커밋의 분석 동작을 baseline oracle로 지정한다.

```text
47f93eadaff64a49f6dc886a9319191c7388029c
```

## 9.2 Golden fixture 작성

다음 입력 집합을 고정한다.

- 전체 내장 운동 catalogue
- 사용자 운동 대표 사례
- 실제 백업 기반 대표 운동기록
- 고중량, 체중운동, 시간운동, 스포츠 세션, 플라이오메트릭, 배드민턴 드릴
- 프로그램 생성 사용자 설정 조합
- seed와 override가 다른 사례

## 9.3 모듈별 golden output

### OFI

- record contribution
- 다섯 축 raw 값
- baseline
- 일별 점수
- OFI
- readiness label
- caution reason

### 프로그램

- candidate set
- slot capability
- ranking trace
- selected stableKey
- prescription

### 근육

- 운동별 bucket contribution
- 날짜별·기간별 aggregate

### 배드민턴

- transfer type
- transfer axes
- fatigue cost
- score component
- 최종 추천 순서

## 9.4 Shadow mode

기존 분석과 새 분석을 동시에 실행하되 사용자에게는 기존 결과만 표시한다.

```text
old result
new result
diff report
```

차이가 있으면 새 결과를 사용하지 않는다.

## 9.5 Parity gate

### 정확 일치가 필요한 항목

- enum/label/category
- 후보 포함 여부
- 선택 stableKey
- muscle bucket membership
- badminton axis membership
- 프로그램 처방
- OFI 반올림 점수와 최종 라벨

### 수치 허용오차

내부 부동소수점 값만 매우 작은 tolerance를 허용한다. tolerance를 넓혀 의미 있는 차이를 숨기면 안 된다.

## 9.6 변경 분리 원칙

다음은 같은 릴리스에서 수행하지 않는다.

- 구조 migration
- 분석 계수 재보정
- taxonomy 의미 재정의
- UI 해석 문구 변경

먼저 구조만 바꾸고 결과를 동일하게 만든다. 이후 의도적인 분석 개선은 별도 프로토콜·근거·버전으로 수행한다.

---

# 10. Migration 전략

## 10.1 내장 운동

- `stableKey`별 현재 effective analysis output을 baseline으로 추출
- 각 relation row를 명시적으로 생성
- 이름 또는 코드 부분 일치로 생성하지 않음
- 자동 생성 결과는 초안으로만 사용
- 전수 coverage 및 conflict report 생성
- 승인 전 production eligibility 부여 금지

## 10.2 사용자 운동

- 기존 runtime metadata row와 현재 resolved metadata를 기준으로 새 relation을 생성
- raw 문자열을 다시 임의 파싱하지 않음
- 이미 typed `MetadataTokenField.values`로 존재하는 값만 읽을 수 있음
- 의미가 불명확한 필드는 `INCOMPLETE`로 남김
- 이름·stableKey substring·다른 운동 유사성으로 채우지 않음
- 사용자가 편집할 수 있는 UI 제공

## 10.3 백업·복구

새 relation table은 논리 식별자를 기준으로 export/import해야 한다.

- exerciseStableKey
- point/relationship type
- canonical ID
- coefficient/role/level
- provenance

기존 백업은 restore adapter를 통해 새 구조로 이행하되 데이터 손실 없이 보존한다.

---

# 11. 한국어·영어 표시 체계

분류와 분석 계약이 확정된 뒤 표시명을 연결한다.

```text
canonical ID
→ 승인된 한국어 용어
→ 영어 표시명
```

번역 원칙:

- 단어 분해 자동 번역 금지
- 국내 운동학·운동역학·해부학의 공식 용어 우선
- 관절 동작은 굴곡·신전·외전·내전·내회전·외회전 등 일관된 체계 사용
- 동일 canonical concept는 하나의 한국어 용어만 사용
- 애매한 용어는 `검토 필요`로 남기고 임의 번역하지 않음
- locale resource는 표시용이며 저장·분석값을 바꾸지 않음

UI에서는 사용자가 분석에 필요하지 않은 기술 필드를 보지 않도록 메타데이터 수를 정리한다.

기본 정보창은 다음 중심으로 구성한다.

- 주요 동작과 관절 동작
- 주요 근육
- 프로그램 활용
- 피로 특성 요약
- 배드민턴 전이 요약

provenance, 내부 group ID, source status 등은 고급 정보로 분리한다.

---

# 12. 단계별 실행 계획

## Phase 0 — 전수 감사와 baseline 동결

산출물:

- metadata field usage matrix
- stableKey별 4대 분석 coverage report
- 문자열 parsing/contains 사용처 목록
- 기존 분석 golden fixture
- current-output snapshot
- deprecated candidate field 목록

이 단계에서는 production 분석 결과를 변경하지 않는다.

## Phase 1 — typed 계약과 shadow 구조 도입

산출물:

- 새 canonical relation model
- 새 asset 또는 Room schema
- stableKey 기반 repository
- legacy-to-new 명시적 migration
- old/new shadow runner
- parity report

여전히 사용자에게는 기존 분석 결과를 표시한다.

## Phase 2 — 근육 분석 전환

이유: 현재 metadata direct mapping 비중이 높고 범위가 상대적으로 작다.

조건:

- 전 내장 운동 coverage
- golden parity
- user exercise incomplete policy

## Phase 3 — 프로그램 생성 전환

조건:

- 슬롯 capability와 ranking trace 동등성
- 동일 seed에서 동일 프로그램
- rollback 가능

## Phase 4 — 배드민턴 전이 전환

조건:

- transfer axes·score·fatigue cost 동등성
- performance domain과 physical quality 분리 완료

## Phase 5 — OFI 전환

OFI는 가장 민감하므로 마지막에 전환한다.

조건:

- record contribution부터 final label까지 parity
- 실제 사용자 백업 회귀 테스트
- baseline·decay 변화 없음

## Phase 6 — 레거시 제거와 UI 정리

- old parsing and contains inference 제거
- deprecated fields read-only 후 제거
- metadata editor 간소화
- 승인된 한국어/영어 용어 적용
- protocol 문서와 registry 최종 정리

---

# 13. 테스트·CI 필수 조건

## 13.1 구조 테스트

- 모든 내장 stableKey에 필수 relation coverage
- duplicate relationship 금지
- canonical ID 존재 검증
- single-value partition 중복 금지
- multi-membership relation 허용 및 검증
- provenance 누락 금지

## 13.2 금지 구현 테스트

production 분석 소스에서 다음 패턴을 감시한다.

- 운동명 기반 판정
- stableKey substring 판정
- metadata code substring 판정
- raw delimiter split
- fallback guess

예외는 명시된 migration package에만 허용하며, 그 안에서도 이름 추론은 금지한다.

## 13.3 parity 테스트

- OFI exact rounded parity
- program deterministic parity
- muscle aggregate parity
- badminton axis and score parity

## 13.4 문서 Definition of Done

각 단계에서 다음을 같은 작업에 갱신한다.

- canonical protocol 문서
- protocol meta/index/registry
- schema/asset version
- migration guide
- field usage report
- parity report
- release notes
- worklog

---

# 14. 금지 사항

- 구조 개편과 분석 재설계를 동시에 수행
- 기존 결과 차이를 “더 합리적”이라는 이유로 승인
- 운동명 또는 코드 일부 문자열로 관계 생성
- `movementFamily` 또는 `movementSubtype`를 다른 의미의 필드에 재사용
- 단일 문자열에 여러 논리 수준을 압축
- 다중 소속을 억지로 단일 enum에 넣기
- relation 누락 시 자동 추정
- 사용자 운동을 조용히 기본 운동 분류로 대입
- DB 값을 한국어로 저장
- 번역을 먼저 완료한 뒤 taxonomy를 맞추려 하기
- parity가 끝나기 전에 기존 계산기 삭제
- 대규모 전환을 한 커밋·한 릴리스에 수행

---

# 15. 완료 판정

이 재설계는 다음을 모두 만족해야 완료다.

1. 네 분석이 각각 독립된 목적과 typed input contract를 가진다.
2. 운동 하나가 각 분석에서 여러 relation row에 정상적으로 포함된다.
3. production 분석에서 문자열 파싱·부분 일치·운동명 추론이 제거된다.
4. 내장 운동은 필수 metadata coverage 100%다.
5. 사용자 운동은 누락을 추정하지 않고 명확히 보고한다.
6. 기존 분석 결과는 golden fixture에서 동등하다.
7. 메타데이터 필드 수가 목적별로 정리되고 중복·혼합 필드가 deprecated된다.
8. 한국어 UI는 승인된 공식 용어만 표시한다.
9. 백업·복구·마이그레이션·롤백이 검증된다.
10. 프로토콜 문서와 registry가 코드와 일치한다.
