# 근력훈련 volume 계산

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-VOLUME |
| Protocol version | 1.2.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT; CoreStimulus V1 from v0.5.0.33; weekly core presentation from v0.5.0.34 |
| Last audited commit | 6498ab7 |
| Evidence profile | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

## v0.5.0.33 CoreStimulus V1

CoreStimulus is a separate strength-analysis projection and does not change
tonnage, e1RM, bodyweight effective load, or duration-hold calculations. Each
confirmed set contributes one base unit multiplied only by its `CoreClass`
coefficient and the shared mild RPE modifier.

Class coefficients are `DIRECT=1.00`, `HIDDEN_HIGH=0.80`,
`HIDDEN_MODERATE=0.40`, `HIDDEN_LOW=0.15`, and `NONE=0.00`. RPE modifiers are
`<7=0.90`, `<8=1.00`, `<9=1.05`, `<10=1.10`, and `>=10=1.15`; unavailable or
non-finite RPE uses `1.00`. Kilograms, effective kilograms, repetitions, and
seconds are not multipliers. The `0.80/0.40/0.15` values are research-informed
ordinal product-policy priors, not direct physiological or EMG effect-size
estimates.

Generic sport-practice records are excluded by structured `activityKind`.
Historical compatible stableKeys are replayed from raw confirmed records
without rewriting identity.

## v0.5.0.34 weekly core presentation

The main strength-analysis core graph projects authoritative daily
`DailyCoreStimulus` rows into canonical Monday-Sunday weeks. Each week sums
DIRECT and INDIRECT independently, TOTAL is their exact sum, and missing weeks
are zero rather than carry-forward. The graph remains a true stacked area with
INDIRECT below DIRECT and TOTAL as the upper boundary. The footer describes the
latest displayed week rather than lifetime cumulative values.

This is a presentation projection only. CoreStimulus V1 arithmetic and version,
its class/RPE coefficients, raw-record replay, and cumulative compatibility
fields remain unchanged.

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

확인된 set의 중량·반복·시간을 운동별 정확한 정책에 따라 volume load로 만들고 movement와 muscle 단위로 집계합니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `STRENGTH-VOLUME`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

우선 duration-hold load, 다음 bodyweight effective load, 마지막 `reps * weightKg` raw load를 사용합니다. reps 또는 positive load가 없는 raw set은 0이며 RPE 7 이상을 hard-set candidate로 봅니다.

## 8. 집계 방식

전체, exercise, movement pattern, muscle group volume과 movement set count를 합산합니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

v0.4.2.16부터 e1RM, 근육군 운동량 비율과 반복수 구간 비율 추이는 공통 Monday-Sunday 월-주차 라벨과 정확한 날짜 범위를 노출합니다. 메인 운동 e1RM 차트의 X domain은 표시된 모든 운동 관측 주의 최솟값부터 최댓값까지의 완전한 주간 domain입니다. 각 운동의 기존 주간 maximum e1RM만 표시하며, 관측이 없는 주는 null로 남겨 0 또는 forward-fill을 만들지 않고 gap을 연결하지 않습니다. Y domain은 모든 표시 운동의 유한한 관측값으로 계산합니다.

v0.5.0.2의 지속형 수행능력 posterior도 이 canonical Epley series를 수정하지 않습니다. 기존 series는 UI에서 `기존 공식 환산값`으로 명시하며 역사 비교용입니다. 새 model은 Epley를 likelihood로 사용하지 않고 `persistentStrengthPerformanceSummary`와 별도 UI layer에만 존재하며 실제 관측 metric, tissue/OFI/readiness 또는 시계열 shock으로 취급하지 않습니다.

## 10. 예외 및 fallback

필수 입력이 없으면 현재 코드의 명시된 빈 결과 또는 보수적 기본 경로를 사용하며 값을 추정해 만들지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 이 protocol의 정확한 최초 app version은 추가 Git history 감사가 필요합니다.
- 이 volume 계약이 처음 도입된 정확한 app version은 추가 Git history 감사가 필요합니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/metrics/CommonStrengthMetrics.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/metrics/CommonStrengthMetrics.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/features/DurationHoldLoadCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/features/DurationHoldLoadCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/lab/StrengthAndMuscleMetricSeriesBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/lab/StrengthAndMuscleMetricSeriesBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/core/CoreStimulusCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/core/CoreStimulusCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/core/CoreStimulusWeeklySeries.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/core/CoreStimulusWeeklySeries.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisStrengthTrendSections.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisStrengthTrendSections.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculatorTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/features/DurationHoldLoadCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/features/DurationHoldLoadCalculatorTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/lab/StrengthAndMuscleMetricSeriesBuilderTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/lab/StrengthAndMuscleMetricSeriesBuilderTest.kt)
- [`app/src/test/java/com/training/trackplanner/AnalysisStrengthChartSpecTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisStrengthChartSpecTest.kt)
- [`app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/core/CoreStimulusCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/core/CoreStimulusCalculatorTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/core/CoreStimulusWeeklySeriesTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/core/CoreStimulusWeeklySeriesTest.kt)
- [`app/src/test/java/com/training/trackplanner/CoreStimulusChartSpecTest.kt`](../../../app/src/test/java/com/training/trackplanner/CoreStimulusChartSpecTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/canonical_v1/core_relations.csv`](../../../app/src/main/assets/metadata/canonical_v1/core_relations.csv)

## 19. 관련 문서

- [`docs/analysis_algorithm_design.md`](../../analysis_algorithm_design.md)
- [`PROXY_PERFORMANCE_ESTIMATION.md`](PROXY_PERFORMANCE_ESTIMATION.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.3` (2026-07-23): 기존 Epley series를 `기존 공식 환산값` 경계로 명시하고 event-driven nonlinear posterior가 volume/e1RM 관측을 변경하거나 소비하지 않는다고 갱신했습니다.
- `1.0.2` (2026-07-23): canonical actual e1RM과 실험적 proxy posterior의 데이터·UI 경계를 명시했습니다. 기존 actual e1RM 및 volume 계산식은 변경하지 않았습니다.
- `1.0.1` (2026-07-19): v0.4.2.16의 공통 주차 표시와 e1RM 다중 시리즈 union-domain/null-gap 계약을 추가했습니다. e1RM 및 volume 계산식은 변경하지 않았습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
