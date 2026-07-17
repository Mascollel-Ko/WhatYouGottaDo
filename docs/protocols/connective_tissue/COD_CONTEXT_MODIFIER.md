# 방향전환 context modifier

| Field | Value |
|---|---|
| Protocol ID | CT-COD-CONTEXT |
| Protocol version | 1.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.10 |
| Last audited commit | 06b65f6cdb243780e97a7464f659219b50010c7c |
| Evidence profile | USER_APPROVED_POLICY, PRODUCT_POLICY |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

승인된 방향전환 운동과 하체 load unit 조합에만 제한 modifier C를 한 번 적용합니다. 연구 effect size가 아니라 명시적인 user-approved product policy입니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `CT-COD-CONTEXT`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

22 exact exercise key와 35 eligible lower-limb load unit의 770 exact pair rule을 사용합니다. tier는 9개 1.09, 6개 1.06, 7개 1.04이며 upper limb, spine, pelvis는 1.0입니다. guardrail은 1.10이고 1.10/1.08 rule은 없습니다.

## 8. 집계 방식

exact exercise key와 exact load unit pair에 한 번만 적용하며 broad name/category inference나 중복 곱셈을 하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

## 10. 예외 및 fallback

필수 입력이 없으면 현재 코드의 명시된 빈 결과 또는 보수적 기본 경로를 사용하며 값을 추정해 만들지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `USER_APPROVED_POLICY, PRODUCT_POLICY`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

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

- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetRepository.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetRepository.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueContextModifier.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueContextModifier.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueCodContextAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueCodContextAuthorityTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvContextModifierTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvContextModifierTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/tissue_load_v1/cod_context_modifier_rules_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/cod_context_modifier_rules_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/cod_context_load_unit_eligibility_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/cod_context_load_unit_eligibility_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/cod_context_exercise_tiers_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/cod_context_exercise_tiers_v1.csv)

## 19. 관련 문서

- [`docs/v0.4.2.10_release_notes.md`](../../v0.4.2.10_release_notes.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
