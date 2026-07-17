# 체중 운동 유효 부하

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-BODYWEIGHT-LOAD |
| Protocol version | 1.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT |
| Last audited commit | 06b65f6cdb243780e97a7464f659219b50010c7c |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

코드가 정확히 식별한 일부 체중 운동만 명시 계수로 유효 부하를 계산하고 다른 운동으로 넓혀 적용하지 않습니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `STRENGTH-BODYWEIGHT-LOAD`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

pull-up/chin-up/dip은 `(bodyweight + added) * reps`, assisted pull-up은 `max(bodyweight - assist, 0) * reps`, inverted row는 `(0.60 * bodyweight + added) * reps`입니다. push-up 계수는 기본 0.65, decline 0.80, pike 0.70, incline 0.55이고 added weight에는 0.70을 적용합니다.

## 8. 집계 방식

입력 단위 결과를 해당 protocol의 날짜, 주간 또는 항목 단위로만 집계하며 서로 다른 의미의 축을 임의로 합산하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

## 10. 예외 및 fallback

reps가 0 이하이거나 body weight/profile이 없으면 null을 반환하고 caller가 raw external-load volume으로 돌아갑니다. generic row나 관련 없는 calisthenics에는 계수를 추정 적용하지 않습니다.

## 11. 개인화 또는 보정

해당 날짜 이전 최신 daily body weight를 사용하고 없으면 initial profile body weight를 사용합니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 이 protocol의 정확한 최초 app version은 추가 Git history 감사가 필요합니다.
- 계수는 exercise-specific product heuristic이며 정확한 최초 app version은 추가 감사가 필요합니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculator.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt)

## 18. 권위 자산

- 별도 authority asset 없이 source와 tests가 계약을 고정합니다.

## 19. 관련 문서

- [`docs/tissue_load_foundation_v1.md`](../../tissue_load_foundation_v1.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
