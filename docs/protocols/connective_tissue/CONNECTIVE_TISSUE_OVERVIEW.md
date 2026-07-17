# 연결조직 부하·회복 개요

| Field | Value |
|---|---|
| Protocol ID | CT-OVERVIEW |
| Protocol version | 1.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.7 |
| Last audited commit | 06b65f6cdb243780e97a7464f659219b50010c7c |
| Evidence profile | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

연결조직 모델은 239개 운동의 관절 구조, 연골, 건, 인대, 반월상연골, 관절순, 근막, 디스크 등 load unit별 상대 노출과 회복 추세를 계산합니다. 임상 손상이나 부상 확률을 예측하지 않습니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `CT-OVERVIEW`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

현재 authority는 239 exercise protocol, 3,507 authority row, 50 protocol class, 13 DI profile, 21 recovery curve/114 knot, 7 routing row, 15 joint complex, 77 unsided load unit을 사용합니다.

## 8. 집계 방식

동일 exercise/event/loadUnit/profile 식별자의 event를 보존하고 load unit별 독립 회복 clock을 적용합니다. joint complex는 자식 값을 합하지 않고 worst/max 상태로 요약합니다.

## 9. 출력과 UI 해석

사용자는 joint complex와 세부 조직을 구분해 보며 contributor는 load unit 우선입니다. 조직별 교육 문구는 별도 authority asset에서 읽습니다.

## 10. 예외 및 fallback

history가 없으면 `UNAVAILABLE`, 관찰 56일 미만이면 `CALIBRATING`입니다. 증상 CAUTION/BLOCK은 canonical state를 각각 최소 HIGH/VERY_HIGH로 올립니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

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
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvEventLedger.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvEventLedger.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngine.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngine.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueCurrentStateServices.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueCurrentStateServices.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngineTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngineTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_asset_manifest_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_asset_manifest_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv)

## 19. 관련 문서

- [`docs/tissue_load_foundation_v1.md`](../../tissue_load_foundation_v1.md)
- [`docs/v0.4.2.7_release_notes.md`](../../v0.4.2.7_release_notes.md)
- [`docs/v0.4.2.8_release_notes.md`](../../v0.4.2.8_release_notes.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
