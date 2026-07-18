# 연결조직 load unit catalogue

| Field | Value |
|---|---|
| Protocol ID | CT-LOAD-UNIT-CATALOGUE |
| Protocol version | 1.1.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.7; EDU-2 copy active from v0.4.2.13 |
| Last audited commit | f2479c8cbf89649469495966d3e8cc09ff49ad8d |
| Evidence profile | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER |
| Supersedes | CT-LOAD-UNIT-CATALOGUE 1.0.0 |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

77개 unsided load unit을 조직 종류와 15개 joint complex에 연결합니다. 각 하위 조직과 상위 관절군에는 사용자가 찾을 수 있는 위치, 실제 기능, 부담을 받는 동작과 부하 증가 조건을 설명하는 교육 정보가 하나씩 있습니다. 좌우를 추정해 새 identity를 만들지 않습니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `CT-LOAD-UNIT-CATALOGUE`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 교육 설명은 canonical load-unit/joint mapping, 조직 종류, primary load mode와 mapped exercise context를 함께 사용하며 `tissue_rcv_educational_info_v1.csv`가 최종 표시 authority입니다.

## 7. 계산 또는 분류 계약

load unit stable key, tissue type, joint complex, display order와 authority relationship을 asset 그대로 읽습니다. 조직 유형을 generic joint score 하나로 축소하지 않습니다. 교육 문구는 77개 하위 항목을 먼저 개별 검토하고, 각 parent는 실제 mapped children의 공통 기능을 종합하되 child 이름이나 설명을 연결해 만들지 않습니다.

## 8. 집계 방식

입력 단위 결과를 해당 protocol의 날짜, 주간 또는 항목 단위로만 집계하며 서로 다른 의미의 축을 임의로 합산하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다. 교육 문구는 일상적인 body landmark를 먼저 쓰고, 조직 종류에 맞는 기능 동사를 사용하며, 별도 확인된 손상 기전이 없으면 `부담이 커질 수 있습니다` 수준의 보수적인 표현을 사용합니다.

## 10. 예외 및 fallback

필수 입력이 없으면 현재 코드의 명시된 빈 결과 또는 보수적 기본 경로를 사용하며 값을 추정해 만들지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 현재 감사 범위에서 별도 미해결 runtime gap을 확인하지 않았습니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Educational coverage: `77 LOAD_UNIT + 15 JOINT_COMPLEX = 92/92`, metadata `RCV-ALL-0.6-EDU-2`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetRepository.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetRepository.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvModels.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvModels.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueRcvAssetImportTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueEducationalCopyContractTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueEducationalCopyContractTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_joint_complexes_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_joint_complexes_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_tissues_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_tissues_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_tissue_relationships_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_tissue_relationships_v1.csv)
- [`app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_educational_info_v1.csv`](../../../app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_educational_info_v1.csv)

## 19. 관련 문서

- [`docs/v0.4.2.8_release_notes.md`](../../v0.4.2.8_release_notes.md)
- [`docs/protocols/README.md`](../README.md)
- [92개 교육 문구 검토 자료](../../reviews/connective_tissue_educational_copy_review_v2.md)

## 20. 변경 이력

- `1.1.0` (2026-07-18): 77개 child와 15개 parent의 EDU-2 설명, hierarchy 작성 원칙과 완전 커버리지 검증을 추가했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
