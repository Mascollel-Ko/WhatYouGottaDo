# MSCP-DI 노출 모델

| Field | Value |
|---|---|
| Protocol ID | CT-MSCP-DI-EXPOSURE |
| Protocol version | 1.0.1 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.7 |
| Last audited commit | 22e51779bbd173e554c3ba1dbeec0fcf13a6ba20 |
| Evidence profile | MIXED, RESEARCH_TRANSFER, MECHANISTIC_SUPPORT, PRODUCT_POLICY |
| Supersedes | — |

`1.0.1`은 현재 runtime 계산을 바꾸지 않고, offline prior generator가 같은 production authority를 재사용한다는 경계를 문서화합니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

각 confirmed training event를 운동과 load unit의 authority row에 연결하고 magnitude, dose, intensity, context를 상대 노출로 변환합니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `CT-MSCP-DI-EXPOSURE`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

활성 runtime 식은 `E0 = (M / 10) * D_normalized * I_selected * C_resolved`입니다. `D_normalized = ln(1 + raw/reference) / ln(2)`를 0~2.5로 제한합니다. `S`는 rapidity provenance, `P`는 회복 curve routing이며 `mappingRoleWeight`는 예약값 1.0입니다.

Phase 1 prior generator는 이 식, production event ledger, PCHIP recovery curve와 residual accumulation을 직접 호출합니다. 별도 근사식이나 Python recovery implementation은 없습니다. Synthetic records도 유효한 exercise mapping과 production dose/intensity resolver를 통과합니다.

## 8. 집계 방식

GROUP/component 중복은 합하지 않고 max로 해결합니다. exact event identity는 record/loadUnit/profile이며 event ledger는 계산 뒤 임의로 재작성하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

## 10. 예외 및 fallback

56일 동일 exercise/dose basis median reference가 없으면 authority default와 current resolver 경로를 사용합니다. 이미 RPE-adjusted dose이면 intensity `I=1`입니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, RESEARCH_TRANSFER, MECHANISTIC_SUPPORT, PRODUCT_POLICY`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- M, S, C, P, D, I의 근거 강도는 load unit과 exercise별로 다르며 일부는 class-level proxy입니다.
- Offline prior의 scenario ensemble은 product policy이며 population prevalence가 아닙니다. Prior generation은 runtime CurrentLoad 식이나 recovery parameter를 변경하지 않습니다.
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
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueDoseResolver.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueDoseResolver.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueModifierResolver.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueModifierResolver.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngineTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngineTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_asset_manifest_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_asset_manifest_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_di_profiles_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_di_profiles_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_score_contract_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_score_contract_v1.csv)

## 19. 관련 문서

- [`docs/tissue_load_phase_c3_multidimensional_model.md`](../../tissue_load_phase_c3_multidimensional_model.md)
- [`docs/tissue_load_phase_c4b1_continuous_axis_scoring.md`](../../tissue_load_phase_c4b1_continuous_axis_scoring.md)
- [`docs/protocols/connective_tissue/PERSONAL_CALIBRATION.md`](PERSONAL_CALIBRATION.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.1` (2026-07-17): offline prior generation이 production M/D/I/C, event ledger와 recovery authority를 직접 재사용하며 runtime 계산은 바꾸지 않는다는 경계를 추가했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
