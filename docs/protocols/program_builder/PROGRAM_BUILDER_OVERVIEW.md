# 자동 프로그램 생성 개요

| Field | Value |
|---|---|
| Protocol ID | PROGRAM-BUILDER-OVERVIEW |
| Protocol version | 1.3.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.0; exact manual set prescriptions from v0.5.0.12; exact application from v0.5.0.13; typed user notices from v0.5.0.14 |
| Last audited commit | 8f78c99b11af14c2715a36532d83256e7ebfe4bf |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

현재 자동 골자 생성은 AI가 아니라 입력을 정규화하고 명시 rule table과 사용 횟수 기반 선택으로 운동을 배치하는 결정론적 protocol입니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `PROGRAM-BUILDER-OVERVIEW`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

현재 공개 UI의 프로그램명, 기간, 주당 운동일, 하루 시간, 배드민턴 비율과 active exercise catalogue를 사용합니다. history, today 상태, fatigue와 resolved runtime metadata catalogue 인자는 public generator에서 현재 사용되지 않습니다.

## 7. 계산 또는 분류 계약

공개 경로는 `ProgramGenerationService → ProgramSkeletonGenerator → ProgramAutoBuilder`입니다. 현재 UI 입력은 이름, 기간, 주당 운동일, 하루 시간, 배드민턴 비율이며 builder는 goal, equipment, 제외어, sport-strength, periodization과 preferred/excluded stable key를 현재 기본값으로 정규화합니다.

저장 프로그램 적용은 생성이나 재평가가 아니라 exact materialization입니다.
`TrainingViewModel → TrainingRepository → ProgramPlanService` 적용 경로는
fatigue/readiness gate를 입력받지 않습니다. 모든 item은
`ProgramSetPrescriptionResolver`로 해석하고 저장된 운동과 set 처방을
그대로 unconfirmed workout plan으로 만듭니다.

## 8. 집계 방식

기간은 3~8주, 주당 일수는 3~7일, 시간은 30/45/60분으로 정규화하고 각 week/day slot을 독립 생성합니다.

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

candidate가 부족하면 rule table의 deterministic fallback order를 사용하며 무작위 운동을 삽입하지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 공개 runtime은 ProgramGenerationService → ProgramSkeletonGenerator → ProgramAutoBuilder이며 고급 ProgramBuilder reservoir/beam/evaluation/optimization 경로는 호출하지 않습니다.
- 현재 공개 생성기는 history, today, resolved metadata catalogue와 fatigue 입력을 사용하지 않습니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
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
- [`app/src/main/java/com/training/trackplanner/data/ProgramOptimizationPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramOptimizationPolicy.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt)
- [`app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt`](../../../app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt)
- [`app/src/main/java/com/training/trackplanner/PlanScreen.kt`](../../../app/src/main/java/com/training/trackplanner/PlanScreen.kt)
- [`app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt`](../../../app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt)
- [`app/src/main/java/com/training/trackplanner/PlanProgramSections.kt`](../../../app/src/main/java/com/training/trackplanner/PlanProgramSections.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt)
- [`app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/training_settings_seed.csv`](../../../app/src/main/assets/training_settings_seed.csv)

## 19. 관련 문서

- [`docs/v0.4.2.0_release_notes.md`](../../v0.4.2.0_release_notes.md)
- [`docs/v0.5.0.13_release_notes.md`](../../v0.5.0.13_release_notes.md)
- [`docs/v0.5.0.14_release_notes.md`](../../v0.5.0.14_release_notes.md)
- [`docs/v0.3.5.3_program_builder_architecture.md`](../../v0.3.5.3_program_builder_architecture.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

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
