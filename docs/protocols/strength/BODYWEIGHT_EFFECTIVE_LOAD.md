# 체중 운동 유효 부하

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-BODYWEIGHT-LOAD |
| Protocol version | 1.1.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT; exact stableKey profile authority from v0.5.0.36 |
| Last audited commit | bb045da |
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

확인된 기록의 `exerciseStableKey`, reps, external weight와 해당 날짜의 body weight를 사용합니다. 표시 이름과 runtime metadata text는 profile 선택 입력이 아닙니다.

## 7. 계산 또는 분류 계약

`BodyweightLoadProfileAuthority`가 exact stableKey로 선택한 profile만 사용합니다. 현재 구현된 profile은 다음과 같습니다.

| Profile | Formula | Exact stableKeys |
|---|---|---|
| Bodyweight plus added | `(bodyweight + added) * reps` | `pull_up`, `ex_6466fe77`, `ex_6463edad`, `ex_deca2b61`, `ex_e1894690`, `ex_e41e8dcf`, `ex_e41f4c2b`, `ex_e4f911bb` |
| Inverted row | `(0.60 * bodyweight + added) * reps` | `ex_d9084b5e`, `ex_e159d15a`, `gymnastic_ring_inverted_row`, `suspension_trainer_inverted_row` |
| Push-up | `(0.65 * bodyweight + 0.70 * added) * reps` | `ex_28902b13`, `ex_73b0b63f`, `ex_c4535de3`, `ex_debf6a8b`, `ex_fa2e73b3` |
| Pike push-up | `(0.70 * bodyweight + 0.70 * added) * reps` | `ex_3caa236b` |
| Decline push-up | `(0.80 * bodyweight + 0.70 * added) * reps` | `ex_fb67af37` |

Assisted pull-up과 incline push-up 계산 형태는 과거 heuristic 문서에 있었지만, 현재 검증된 exact stableKey profile이 없습니다. 따라서 v0.5.0.36 runtime은 이름으로 이를 추정하지 않습니다.

지속형 중량 풀업 수행능력 model은 volume 계수를 재사용하지 않고 `bodyweight + added weight`의 총부하를 target state로 사용합니다. assisted pull-up은 `bodyweight - assistance`로 resolve하지만 direct anchor가 아닙니다. 이 total-load contract는 volume 계산식과 모순되지 않으나 서로 다른 output을 소유합니다.

## 8. 집계 방식

입력 단위 결과를 해당 protocol의 날짜, 주간 또는 항목 단위로만 집계하며 서로 다른 의미의 축을 임의로 합산하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

## 10. 예외 및 fallback

reps가 0 이하이거나 body weight/profile이 없으면 null을 반환하고 caller가 raw external-load volume으로 돌아갑니다. exact profile이 없는 stableKey는 이름이 push-up/pull-up처럼 보여도 계수를 얻지 못합니다. historical record에 exact stableKey가 있으면 Exercise DB row가 없어도 profile을 해석합니다.

## 11. 개인화 또는 보정

volume은 해당 날짜 이전 최신 daily body weight를 사용하고 없으면 initial profile body weight를 사용합니다. 지속형 중량 풀업 posterior는 exact-date check-in/metric, 최근 이전 체중, initial profile 순으로 해석하며 오래된 체중에는 추가 uncertainty를 적용합니다. 체중이 없으면 zero를 대입하지 않고 direct observation을 제외합니다.

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
- [`app/src/main/java/com/training/trackplanner/analysis/features/BodyweightLoadProfileAuthority.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/features/BodyweightLoadProfileAuthority.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt)

## 18. 권위 자산

- `BodyweightLoadProfileAuthority`의 exact stableKey map과 parity tests가 authority를 고정합니다.

## 19. 관련 문서

- [`docs/tissue_load_foundation_v1.md`](../../tissue_load_foundation_v1.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.1.0` (2026-08-15): 이름·family·movement·equipment token heuristic을 제거하고 19개 검증 stableKey의 explicit profile authority로 전환했습니다. 지원 key의 수치는 보존하고 미등록 이름은 fail closed 처리합니다.
- `1.0.1` (2026-07-23): 중량 풀업 posterior의 총부하, 체중 출처 우선순위, 당시 bodyweight snapshot과 assisted pull-up 비-direct 경계를 명시했습니다. 기존 volume 계수는 변경하지 않았습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
