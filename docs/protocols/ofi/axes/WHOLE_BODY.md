# 전신 근육 피로 축

| Field | Value |
|---|---|
| Protocol ID | OFI-AXIS-WHOLE-BODY |
| Protocol version | 1.1.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.15 |
| Last audited commit | aa08b49ff183c60c45c9e8bf95a9542df1b592ce |
| Evidence profile | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.1.0`은 canonical OFI 축 집합과 배드민턴 session 기여 경계를 명시한 계약입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

긴 세션, 복합운동과 sport session이 만드는 전신 근육 부담을 모델링합니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `OFI-AXIS-WHOLE-BODY`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

기여량은 `levelMultiplier * durationModifier * compoundModifier * recordLoad * 50`입니다. 시간 구간은 30분 미만 0.85, 60분 미만 1.0, 90분 미만 1.15, 그 이상 1.30이며 sport 1.20, main/heavy 1.15, isolation 0.70, accessory 0.85를 적용합니다.

## 8. 집계 방식

확인된 set/entry의 일별 raw contribution을 합친 뒤 decay를 적용하고 개인 baseline과 비교합니다. UI 축 이름은 `전신 근육`입니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

## 10. 예외 및 fallback

metadata 수준이 없거나 표준 level과 다르면 0.55를 사용합니다. 확인되지 않은 세트는 계산에 포함하지 않습니다.

## 11. 개인화 또는 보정

잔여량 decay는 SHORT `[1,.35,.1,0]`, MEDIUM `[1,.6,.35,.15,0]`, LONG `[1,.75,.55,.35,.2,.1,0]`, VERY_LONG `[1,.85,.7,.55,.4,.28,.18,.1,0]` 중 metadata profile을 사용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 현재 감사 범위에서 별도 미해결 runtime gap을 확인하지 않았습니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt`](../../../../app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/fatigue/FatigueMath.kt`](../../../../app/src/main/java/com/training/trackplanner/analysis/fatigue/FatigueMath.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueModels.kt`](../../../../app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueModels.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculatorTest.kt`](../../../../app/src/test/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculatorTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/fatigue/OverallFatigueIndexCalculatorTest.kt`](../../../../app/src/test/java/com/training/trackplanner/analysis/fatigue/OverallFatigueIndexCalculatorTest.kt)

## 18. 권위 자산

- 별도 authority asset 없이 source와 tests가 계약을 고정합니다.

## 19. 관련 문서

- [`docs/v0.4.2.6_canonical_ofi_fatigue_pipeline.md`](../../../v0.4.2.6_canonical_ofi_fatigue_pipeline.md)
- [`docs/protocols/README.md`](../../README.md)

## 20. 변경 이력

- `1.1.0` (2026-07-18): canonical 다섯 축 복원과 sport-session systemic 기여를 재감사했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
