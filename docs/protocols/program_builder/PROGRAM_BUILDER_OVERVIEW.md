# 자동 프로그램 생성 개요

| Field | Value |
|---|---|
| Protocol ID | PROGRAM-BUILDER-OVERVIEW |
| Protocol version | 3.2.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.0; independent record-based builder from v0.5.1.4; planner v0.12.0 from 2026-09-05 |
| Last audited commit | 092803213aa517f1bd899b7a07d83dd4638da81d |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

프로그램 만들기에는 서로 독립적인 두 결정론적 경로가 있습니다. 기존 자동 골자 생성은 기존 입력과 rule table을 그대로 사용합니다. 기록 기반 경로는 완료 기록, 명시 답변, canonical metadata를 사용해 설명 가능한 다주 계획을 만듭니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `PROGRAM-BUILDER-OVERVIEW`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

기존 자동 경로는 프로그램명, 기간, 주당 운동일, 하루 시간, 배드민턴 비율과 active exercise catalogue를 사용합니다. 기록 기반 경로는 생성 cutoff 이하의 `confirmed=true` set, canonical exercise `stableKey`, resolved runtime metadata, 초기 profile, 저장된 사용자 의도와 이번 실행의 세 가지 사전 답변만 사용합니다. 미래 기록과 미확정 set은 입력에서 제외합니다.

## 7. 계산 또는 분류 계약

공개 경로는 `ProgramGenerationService → ProgramSkeletonGenerator → ProgramAutoBuilder`입니다. 현재 UI 입력은 이름, 기간, 주당 운동일, 하루 시간, 배드민턴 비율이며 builder는 goal, equipment, 제외어, sport-strength, periodization과 preferred/excluded stable key를 현재 기본값으로 정규화합니다.

기록 기반 경로는 `TrainingViewModel → TrainingRepository → PersonalizedProgramPlanningService`이며 기존 `ProgramAutoBuilder`를 호출하거나 수정하지 않습니다. `PlanningHistorySnapshotBuilder`가 시점 고정 snapshot을 만들고, `PlannerActivityDomainResolver`가 typed role/capability, canonical activity kind, progress metric과 runtime metadata로 저항운동·구조화 배드민턴 드릴·athletic-performance drill·일반 코트 세션을 분리합니다. `MovementExposureRepresentationAnalyzer`와 `BadmintonObjectiveRepresentationAnalyzer`가 관찰된 분포를 계산하고 `AdaptationGapAnalyzer`는 그 상태를 기존 우선순위 사다리로 변환합니다. 운동 선택은 별도의 reviewed stableKey authority에서 수행합니다. `PersonalizedProgramBuilder`는 주간 구조와 set별 처방을 materialize하고 기존 editor/save/apply 형식으로 변환합니다. projection repair가 필요하면 기존 선택 priority로 보존 항목을 결정한 뒤, 반환할 최종 item으로 실제 세트 수·drill bout·fingerprint를 다시 확정합니다.

질문은 기록만으로 의도를 안전하게 확정할 수 없고 답변이 실제 생성 결과를 바꿀 때만 표시합니다. `preparePersonalizedProgram`이 현재 필요한 질문을 한 번에 반환하고, 사용자가 모두 답한 뒤 `generatePreparedPersonalizedProgram`이 고정된 cutoff와 명시 조건으로 중단 없이 생성합니다. 답변은 관찰 사실로 취급하지 않고 `EXPLICIT_USER` provenance로 저장합니다. 근력 의도는 답변 시각과 당시 profile goal을 함께 저장하며 56일 경과 또는 goal 변경 시 다시 확인할 수 있습니다.

Program candidate admission is exact stableKey authority. The typed
`ProgramCandidateAuthority` view is derived directly from `ProgramRuleTables`;
names, metadata labels, core tokens, or similarity cannot add an exercise.
The approved set remains 59 keys. The disconnected advanced builder is guarded
against reintroduction and was removed after confirming zero production
consumers.

저장 프로그램 적용은 생성이나 재평가가 아니라 exact materialization입니다.
`TrainingViewModel → TrainingRepository → ProgramPlanService` 적용 경로는
fatigue/readiness gate를 입력받지 않습니다. 모든 item은
`ProgramSetPrescriptionResolver`로 해석하고 저장된 운동과 set 처방을
그대로 unconfirmed workout plan으로 만듭니다.

## 8. 집계 방식

기존 자동 경로는 기간을 3~8주, 주당 일수를 3~7일, 시간을 30/45/60분으로 정규화합니다. 기록 기반 경로의 기간과 주당 일수는 별도 control에서 기본 `AUTO`로 보이며 각각 2~6주와 2~5일에서 결정됩니다. 사용자는 명시 override 뒤 다시 AUTO로 돌아갈 수 있습니다. 기존 자동 경로의 더 넓은 범위와 배드민턴:근력 비율은 유지되지만 그 비율은 기록 기반 계산에 쓰지 않습니다. 모든 week/day/set은 명시적으로 생성하며, 운동 anchor는 cutoff까지의 완료 기록과 실제 progression만 사용합니다.

현재 블록 판단의 per-anchor strength style, style feature 및 canonical strength posterior 변화는 cutoff를 끝으로 하는 최근 56일만 사용합니다. 노출 표현 비교는 현재 `cutoff-27..cutoff`와 직전 `cutoff-55..cutoff-28`의 인접 28일 창을 사용하고, confidence는 각 창을 cutoff에 고정한 네 개의 7일 bin으로 계산합니다. ISO 주차는 사용하지 않습니다. 일반 코트 부하는 28일 원시량과 주간 환산량을 함께 보존하며 180/240 임계값은 주간 환산량에만 적용합니다.

움직임 표현 단위는 확인된 저항운동 working set 1개이며 생리적 dose가 아닙니다. 현재·직전 share는 활성 required movement 집합 안에서 정규화합니다. 개인 비교는 `currentShare / priorShare`이고, peer 비교는 target을 제외한 같은 base-priority의 positive exposure가 둘 이상일 때 그 median을 사용합니다. `0.25`와 `0.50`은 큰 분포 차이를 찾는 engineering outlier rule일 뿐 충분량 또는 최적량 임계값이 아닙니다. 상태는 `ABSENT`, `STRONG_UNDERREPRESENTATION_SIGNAL`, `UNDERREPRESENTATION_SIGNAL`, `NO_CLEAR_DEFICIT_SIGNAL`, `UNKNOWN`이며 `NO_CLEAR_DEFICIT_SIGNAL`은 충분하다는 뜻이 아닙니다. 현재 저항운동이 전혀 없으면 개별 부재 provenance는 보존하되 하나의 `RESISTANCE_FOUNDATIONAL_ONRAMP`만 생성합니다.

Objective V2는 기존 transfer coefficient와 RPE modifier를 유지하되 weighted exposure와 DIRECT-only exposure를 별도로 저장합니다. share의 개인 retention을 주된 비교로 사용하고 세 개 이상의 positive peer objective median은 약한 보조 신호로만 사용합니다. peer-only 신호는 HIGH gap이 될 수 없습니다. `DIRECT_DROP`은 직전 DIRECT가 최근 창에서 사라진 사실이고, `NEVER_DIRECT_OBSERVED`는 자동 gap이 아닌 발달 관찰입니다. 후자는 강한 gap 뒤 spare capacity에서 block당 최대 한 후보만 고려하며 PRESERVE anchor를 밀어내지 않습니다. 아홉 objective를 같은 목표량으로 가정하지 않습니다.

anchor 전환의 `gapPressure`는 `contributesTransitionPressure=true`인 gap만 사용합니다. 선택적 `BADMINTON_DEVELOP_*` 관찰은 후보·설명·저장 provenance에는 남지만 단독으로 rotation, structure 또는 dose 전환을 바꾸지 않습니다. 회복 입력에서 production tissue `MODERATE`는 명시적으로 `.30`에 매핑하며 과거 `ELEVATED` 토큰도 같은 값으로 호환합니다. 그 밖의 readiness, OFI와 systemic recovery 식은 유지합니다.

수동 프로그램은 자동 생성 범위와 별개입니다. 기록 달력에서 선택한
inclusive 날짜 범위는 첫 날짜를 1주차 월요일로 매핑하고 날짜 간 빈칸을
그대로 둡니다. 같은 날짜의 동일 운동 기록도 합치지 않으며 각 기록과
set 순서를 별도 program item과 set prescription으로 보존합니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

v0.5.0.14부터 optimization action과 `PROGRAM_...` warning은 내부 trace와
diagnostic으로만 유지합니다. 정상 완료 화면은
`ProgramUserNoticeCode`와 정수 인자만 전달받고 Android presentation
boundary에서 현재 locale의 문장으로 변환합니다. 따라서 domain/data
계층은 Android `Context`에 의존하지 않으며, 정상 사용자 화면은 action
code나 enum 이름을 직접 표시하지 않습니다.

저장된 프로그램 상세는 실제 운동이 있는 날짜만 표시하고, 각 운동을
read-only card로 보여 줍니다. 운동 identity를 현재 catalogue에서 해석할 수
있으면 기존 `ExerciseInfoDialog`를 열며, 그렇지 않아도 저장된 이름과
처방 snapshot은 계속 표시합니다.

표시된 처방과 적용 결과는 같은 canonical resolver를 사용합니다. 피로도,
readiness, OFI와 연결조직 분석은 정보와 권고이며 저장 프로그램을 자동으로
삭제, 축소, 교체하거나 변경하지 않습니다.

## 10. 예외 및 fallback

기존 경로는 candidate가 부족하면 기존 rule table의 deterministic fallback order를 사용합니다. 기록 기반 경로도 무작위 운동이나 이름 유사도 fallback을 쓰지 않습니다. reviewed authority에서 유효한 stableKey를 찾지 못하거나 projection validation을 통과하지 못하면 실패를 표시하며 기존 editor 내용을 덮어쓰지 않습니다. 재시도는 같은 기록 기반 경로만 다시 실행합니다.

## 11. 개인화 또는 보정

기록 기반 경로는 반복된 실제 운동의 연속성을 우선하되 관찰 style을 미래 처방으로 직접 복사하지 않습니다. 각 anchor에 대해 관찰 style과 다차원 feature, 적응 상태를 계산하고 `StructureTreatment`와 `DoseTreatment`를 별도로 결정합니다. 근력 반응은 최근 56일 canonical posterior의 실제 변화율에 `tanh(changePercent / 5)`를 적용하고, confidence는 posterior 관찰 수를 6으로 나눈 값을 0..1로 제한합니다. 변화가 없거나 관찰이 2개 미만이면 중립/0 confidence입니다. 회복은 우선 전체 또는 해당 stableKey의 dose를 낮추고, gap은 저항·구조화 배드민턴·athletic/보조운동이 공유하는 유한 실행 용량 안에서 재배분합니다. 저항 working set, 구조화 배드민턴 bout, athletic-performance bout는 서로 다른 단위로 관리하면서 같은 세션 시간 한도를 공유합니다.

multi-day style variant는 anchor별로 서로 다른 생성일에 배치합니다. 2일 계획은 기존 처리 의미에 따라 HLM/Madcow를 `HEAVY+LIGHT` 또는 heavy 완화 시 `LIGHT+MEDIUM`, DUP를 `STRENGTH+VOLUME` 또는 strength 완화 시 `VOLUME+MODERATE`로 제한합니다. gap truncation은 기존 HIGH, MEDIUM/MODERATE, LOW 순서를 사용하고 같은 priority에서는 원래 domain 순서를 보존합니다.

주간 저항 목표에는 고정 4세트 하한이 없습니다. 기록/회복 목표 안에서 `PRESERVE`와 가능한 `PRESERVE_CORE_REBALANCE` 연속성을 먼저 유지하고, `PARTIAL_CONTINUITY`와 `ROTATE_EMPHASIS`는 유한 예산에서 0이 될 수 있습니다. 모든 anchor를 유지하려고 용량을 늘리지는 않습니다. 단, 낮은 기록 용량·양호한 회복·선택된 HIGH 저항 gap이 동시에 있고 최소 2세트를 다른 방식으로 배정할 수 없으면 `MINIMAL_CAPACITY_EXPANSION`으로 제한적 확장을 기록하고 preview에 표시합니다.

직접 노출은 exact objective의 canonical DIRECT 관계로만 인정합니다. 사용자 요청에 따라 SUPPORTIVE 관계도 필요한 보조운동 후보로 사용하되 직접 노출이나 목표 충족으로 승격하지 않습니다. GENERAL/LOW는 후보 관계를 대신하지 않습니다. 안정성 typed authority와 명시적 SUPPORTIVE 관계가 함께 있는 운동은 athletic/보조 실행으로 배정할 수 있습니다. 처방은 최근 56일 실제 완료 구조 → exact stableKey 다주 canonical seed의 실행 가능한 무부하 처방 → 기존 reviewed rule 순서로 해석합니다. 휴식 시간만 있는 timing row로 반복·라운드·운동시간을 발명하지 않습니다.

`TOP_SET_BACKOFF`, `STRAIGHT_5X5`, `MADCOW_LIKE_HLM_RAMPING`, `DUP_LIKE_UNDULATING`, `HEAVY_LIGHT_MEDIUM` 등은 각 anchor의 실제 feature와 할당 budget이 허용할 때만 알아볼 수 있는 형태로 이어집니다. multi-day style의 중량 기준은 마지막 세션이 아니라 최근 관찰 주의 가장 강한 정당한 노출입니다. 생성된 미래 주차는 새 완료 근거가 없으므로 자동 증량하지 않고 같은 현재 microcycle을 반복할 수 있습니다. 일반 배드민턴 세션은 실제 회복 비용으로 계속 반영되지만 구조화된 배드민턴 목표 자극으로 계산하지 않습니다.

### v0.12 실행 용량과 사전 확인

실행 순서는 representation/gap → 처방 authority → 유한 cross-domain 배분 → 실제 시간 배치 → residual repair → 반환 item 기준 count/fingerprint입니다. 보조 및 경기력 훈련을 저항 예산 밖에 덧붙이지 않습니다. 의미 있는 최소 처방을 먼저 배정하고 discretionary 연속성 용량을 양보할 수 있습니다. 동일 exact objective를 공유하는 항목은 중복 투입하지 않으며 typed family/redundancy로 서로 다른 훈련 질을 구분합니다.

용량 envelope는 명시 주당 일수·세션 분·가용 초, 최근 session median/p75 단위·median 시간, active-week controllable workload, 기존 recovery factor, court context, useful demand, 시간상 bound와 최종 실제 단위를 보존합니다. density bound는 기존 관찰 workload × systemic dose factor × max(1, 요청 일수/관찰 일수) × max(1, 요청 세션 초/관찰 median 초)입니다. 최종 용량은 useful demand·density bound·추정 시간 bound의 최소값이며 실제 처방 시간으로 다시 확인합니다. 상세 식과 결측/희소 기록 정책은 implementation note에 있습니다.

고정 4/5개 item 제한과 high-court 별도 item cap은 제거했습니다. 코트 부하는 기존 recovery/자동 빈도/lower-anchor interference 문맥만 유지하며 objective 자극을 만들지 않습니다. 실제 처방과 세트 간 휴식이 시간 한도를 결정하고 typed 하체/impact 항목을 분산합니다. 시간이 늘어도 정당한 demand가 소진됐으면 filler를 만들지 않습니다.

매 preflight에서 근력 목표·배드민턴 포함·프리웨이트 수용의 세 질문을 먼저 합니다. 저장 선호나 관찰 기록이 질문을 생략하지 않으며 모든 유효 답변을 선택해야 생성할 수 있습니다. 취소는 저장하지 않고 UNRESOLVED로 임시 단기 계획을 만들지 않습니다. 정상 AUTO badminton-support horizon은 adaptation minimum 4주를 지키며 실제 회복 제한/희소 이력의 bridge만 짧아질 수 있습니다. 명시 기간은 그대로 우선합니다.

portable app_meta의 planningBudget.execution은 최종 direct representation과 supportive allocation을 분리합니다. SUPPORTIVE_ONLY_DIRECT_EXPOSURE_NOT_REPLACED는 보조운동은 실제 배정됐지만 직접 노출은 아직 없다는 뜻입니다. Room schema 및 기존 적용 confirmed=false 계약은 유지합니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 기존 자동 생성기는 history, today, resolved metadata catalogue와 fatigue 입력을 사용하지 않습니다. 이는 기록 기반 경로와 의도적으로 분리된 기존 계약입니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.
- 기록 기반 결과는 의료·부상 판단이나 최적성 보장이 아니며, 선택된 horizon 끝에서 재평가하는 제품 휴리스틱입니다.
- 이 모델은 관찰된 상대 분포와 직접 노출 소실만 판단합니다. 절대 주간 세트 목표, 생리적 충분성, 아홉 badminton objective의 동일 목표 비중은 정의하지 않습니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- v0.12.0 record-based boundary: 기존 자동 버튼 바로 아래 별도 버튼으로 진입하며, 56일 완료 기록 snapshot, 인접 28+28일 representation 비교, 매번 세 가지 핵심 선호를 확인하는 scrollable preflight 질문, 명시적이고 복귀 가능한 AUTO 기간·일수, per-anchor structure/dose 전환, 유한 cross-domain 실행 budget, 실제 처방 시간 기반 배치, residual projection repair, final-item decision provenance와 기존 editor/save/apply 재사용을 제공합니다.
- 개인화 선호와 최근 decision provenance는 portable `app_meta`로 백업·복원되며 로컬 seed/rebuild/lineage metadata는 이식하지 않습니다.
- v0.5.0.6 identity boundary: built-in program seed 753개 item은 모두
  explicit canonical stableKey를 사용하며 display name lookup으로 identity를 만들지 않습니다.
- v0.5.0.12 manual program boundary: `training_program_item_sets`가 있으면
  set별 reps/weight/seconds가 authoritative하며, 자식 row가 없는 기존
  program item은 scalar setCount/reps/weight/seconds를 반복하는 legacy
  fallback으로 해석합니다.
- v0.5.0.13 application boundary: scalar/child storage 형식, 적용 날짜와
  현재 fatigue/readiness 상태에 관계없이 저장된 모든 운동과 set을
  unconfirmed plan으로 정확히 적용합니다.
- v0.5.0.14 presentation boundary: optimization trace는 안정적인 내부
  action code를 유지하고, 완료 화면은 typed notice를 한국어/영어
  resource로 변환해 별도 항목과 severity로 표시합니다.
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/data/ProgramGenerationService.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramGenerationService.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramAutoBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramAutoBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt`](../../../app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt)
- [`app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`](../../../app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedPlanningModels.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedPlanningModels.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PlanningHistorySnapshotBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PlanningHistorySnapshotBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/AthletePlanningStateBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/AthletePlanningStateBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/ExposureRepresentation.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/ExposureRepresentation.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedDecisionComponents.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedDecisionComponents.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramRuleTables.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramRuleTables.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramCandidateAuthority.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramCandidateAuthority.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramExerciseSpec.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramExerciseSpec.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt)
- [`app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt`](../../../app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt)
- [`app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`](../../../app/src/main/java/com/training/trackplanner/TrainingViewModel.kt)
- [`app/src/main/java/com/training/trackplanner/PlanScreen.kt`](../../../app/src/main/java/com/training/trackplanner/PlanScreen.kt)
- [`app/src/main/java/com/training/trackplanner/PlanGeneratedPreview.kt`](../../../app/src/main/java/com/training/trackplanner/PlanGeneratedPreview.kt)
- [`app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt`](../../../app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt)
- [`app/src/main/java/com/training/trackplanner/PlanProgramSections.kt`](../../../app/src/main/java/com/training/trackplanner/PlanProgramSections.kt)

- [`ExecutionAllocationPlanner.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/ExecutionAllocationPlanner.kt)
- [`PerformancePrescriptionResolver.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PerformancePrescriptionResolver.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramCandidateAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramCandidateAuthorityTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderParityMatrixTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderParityMatrixTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerParityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerParityTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerV010Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerV010Test.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/ExposureRepresentationV011Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/ExposureRepresentationV011Test.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt)
- [`app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt)

- [`ExecutionAllocationV012Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/ExecutionAllocationV012Test.kt)
- [`PersonalizedPlanningQuestionUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/PersonalizedPlanningQuestionUiTest.kt)
- [`RealBackupPersonalizedPlannerE2eTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/RealBackupPersonalizedPlannerE2eTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/training_settings_seed.csv`](../../../app/src/main/assets/training_settings_seed.csv)
- [`tools/planner_reference/v011_exposure_representation_reference.py`](../../../tools/planner_reference/v011_exposure_representation_reference.py)
- [`tools/planner_reference/fixtures/v011_exposure_representation_golden.json`](../../../tools/planner_reference/fixtures/v011_exposure_representation_golden.json)

- [`v012_execution_allocation_reference.py`](../../../tools/planner_reference/v012_execution_allocation_reference.py)
- [`v012_execution_allocation_golden.json`](../../../tools/planner_reference/fixtures/v012_execution_allocation_golden.json)

## 19. 관련 문서

- [`docs/v0.4.2.0_release_notes.md`](../../v0.4.2.0_release_notes.md)
- [`docs/v0.5.0.13_release_notes.md`](../../v0.5.0.13_release_notes.md)
- [`docs/v0.5.0.14_release_notes.md`](../../v0.5.0.14_release_notes.md)
- [`docs/v0.3.5.3_program_builder_architecture.md`](../../v0.3.5.3_program_builder_architecture.md)
- [`docs/protocols/data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md`](../data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md): slot relation shadow와 공개 generator golden을 정의하며 production generator는 변경하지 않습니다.
- [`docs/protocols/README.md`](../README.md)

- [기록 기반 planner 구현 노트](../../record_based_planner_implementation_note.md)
- [기록 기반 planner 릴리스 노트](../../v0.5.1.4_record_based_planner_release_notes.md)

## 20. 변경 이력

- `3.2.0` (2026-09-05): 유한 cross-domain 재배분, 처방 기반 시간 용량, canonical seed 처방, 명시적 SUPPORTIVE 보조운동과 proactive 세 질문을 연결했습니다. representation 임계값·Objective V2 계수·strength posterior·tissue 회복식·legacy builder는 변경하지 않았습니다.

- `3.1.1` (2026-09-04): 비압력 발달 gap이 실제 anchor 전환 압력에 기여하지 않는 실행 계약을 고정하고, production tissue `MODERATE`를 `.30`으로 명시 매핑했으며, Python/Kotlin badminton parity가 raw 기록에서 실제 production analyzer를 통과하도록 강화했습니다.
- `3.1.0` (2026-09-04): binary presence gap을 보수적 exposure representation 신호로 교체하고, 동일 priority movement peer median, normalized personal retention, Objective V2 weighted/DIRECT 분리, 최대 한 optional developmental candidate, typed athletic domain과 별도 bout budget, reviewed prescription gate를 계약화했습니다. 충분성·절대 주간 최소량·아홉 objective 동일 목표는 도입하지 않았습니다.
- `3.0.1` (2026-09-04): per-anchor multi-day 배치, repair 이후 budget/fingerprint 확정, 연속 posterior 입력, Persona 28 비교 계약, gap priority, 설명 가능한 최소 용량 확장, optional-anchor 유한 배정과 명시적 AUTO override를 보정했습니다. global gap pressure, active-week baseline, 2-bout drill 기본값, 보수적 진행과 기존 임계값은 유지했습니다.
- `3.0.0` (2026-09-04): 기록 기반 planner를 v0.10으로 올려 56일 current-block window, per-anchor style feature와 structure/dose 전환, 주간 저항·drill 분리 budget, 일괄 preflight, AUTO 조건, 최근 주 strongest load 기준과 미래 자동 증량 금지를 계약화했습니다.
- `2.0.0` (2026-09-03): 기존 자동 builder를 유지한 채 완료 기록 기반의 독립 builder, 조건부 의도 질문, 동적 2~6주 horizon, strength-style 보존, provenance 및 portable backup 계약을 추가했습니다.
- `1.5.0` (2026-08-15): deleted the zero-consumer advanced ProgramBuilder reservoir/beam/evaluation stack, retained the public deterministic pipeline and 59-key authority, and verified the public golden matrix unchanged.
- `1.4.0` (2026-08-15): ProgramRuleTables의 59 exact stableKey를 sole candidate authority로 고정하고 192-scenario public output parity를 추가했습니다.
- `1.3.0` (2026-07-30): 내부 optimization action과 사용자 완료 문구를
  typed notice 및 locale resource 경계로 분리했습니다.
- `1.2.0` (2026-07-30): 저장 프로그램 적용을 fatigue/readiness와 분리한
  exact materialization 계약을 추가했습니다.
- `1.1.0` (2026-07-30): 기록 범위의 exact manual program 변환, set별
  authoritative prescription, legacy scalar fallback과 저장 프로그램 card
  표시 계약을 추가했습니다.
- `1.0.1` (2026-07-28): program seed, generation spec와 future entry의
  exercise reference를 canonical stableKey-only로 고정했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
