# 배드민턴 훈련량 계산

| Field | Value |
|---|---|
| Protocol ID | BADMINTON-VOLUME |
| Protocol version | 1.0.1 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT |
| Last audited commit | 60e21c6b847f1dc2910ddbdc5ee2d4690631cb9e |
| Evidence profile | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

배드민턴 훈련량은 기록 형태에 따라 duration, repetitions 또는 일반 dose를 선택합니다. 서로 다른 세션을 동일한 숫자로 간주하지 않습니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `BADMINTON-VOLUME`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

shuttle-play session은 duration과 intensity를, footwork는 duration과 drill density/reactive weight 또는 repetition weight를 사용합니다. `TEST_ONLY`는 0.35, support exercise는 base dose와 support weight/correction을 사용합니다. multi-label method dose는 목적별 분석을 위해 의도적으로 복제하며 분할하지 않습니다.

## 8. 집계 방식

입력 단위 결과를 해당 protocol의 날짜, 주간 또는 항목 단위로만 집계하며 서로 다른 의미의 축을 임의로 합산하지 않습니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

v0.4.2.16부터 일별 차트는 실제 시작·종료 날짜와 날짜 축을 표시하고, 주별 차트는 공통 Monday-Sunday bucket의 `weekStart`를 그대로 사용합니다. 주의 소유 월은 해당 주 목요일의 월이며, 같은 월이 소유한 주를 시간순으로 `7월 1주`, `7월 2주`처럼 번호 매깁니다. 상세 설명과 접근성 문구는 정확한 날짜 또는 Monday-Sunday 범위를 포함합니다. 축 라벨 밀도를 줄여도 데이터 포인트와 계산값은 제거하지 않습니다.

## 10. 예외 및 fallback

명시적 duration 또는 repetition이 없으면 현재 calculator가 제공하는 support/base dose 경로를 사용하며 센서 값을 추정하지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 이 protocol의 정확한 최초 app version은 추가 Git history 감사가 필요합니다.
- 현재 감사 범위에서 별도 미해결 runtime gap을 확인하지 않았습니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeries.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeries.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngine.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngine.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferConstants.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferConstants.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeriesTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeriesTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt)
- [`app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngineTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngineTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/coach/BadmintonTransferCoverageAnalyzerTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/coach/BadmintonTransferCoverageAnalyzerTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`](../../../app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv)

## 19. 관련 문서

- [`docs/analysis_algorithm_design.md`](../../analysis_algorithm_design.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.1` (2026-07-19): v0.4.2.16의 공통 Monday-Sunday 월-주차 라벨, 정확한 기간 및 접근성 표시 계약을 추가했습니다. 훈련량 계산은 변경하지 않았습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
