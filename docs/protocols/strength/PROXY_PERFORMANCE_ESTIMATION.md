# 주요 리프트 프록시 수행 추정

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-PROXY-PERFORMANCE |
| Protocol version | 1.0.0 |
| Status | EXPERIMENTAL |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.1 |
| Last audited commit | afcf891556b9178ca4e4453031b7184cca0d17af |
| Evidence profile | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC, LOW_CONFIDENCE_PROXY |
| Supersedes | — |

이 문서는 벤치프레스, 스쿼트, 데드리프트의 실제 기록과 관련 운동에서 추론한 수행 상태를 엄격히 구분하는 실험적 제품 계약입니다. 추정치는 측정값이나 실제 e1RM을 대체하지 않습니다.

## 1. 일반 사용자용 요약

확인된 주요 리프트 기록이 없는 주에도 관련 운동의 수행 변화를 참고해 벤치프레스, 스쿼트, 데드리프트의 추정 범위를 보여 줍니다. 화면의 `실제 e1RM`은 사용자가 수행한 기존 계산값이고, `추정 수행 상태`와 `세션 전 예상`은 불확실성을 포함한 별도 모델 결과입니다.

## 2. 목적

불규칙한 주요 리프트 기록 사이에서 관련 운동의 신호를 보수적으로 연결하되, 실제 기록 계열을 오염시키지 않고 어떤 운동이 추정에 영향을 주었는지 설명 가능한 결과를 제공합니다.

## 3. 적용 범위

- 대상 리프트: 벤치프레스, 스쿼트, 데드리프트
- 입력: 확인된 1~12회 반복 set, 유효 부하, RPE, 운동 metadata, 일별 체중과 초기 사용자 체중
- 출력: 주별 prior/posterior 중앙값과 80% 구간, 세션 전 예상 대비 실제 수행, proxy 기여, rolling backtest, model 선택과 진단
- 표시 위치: 근력 분석과 분석 실험실의 별도 실험 영역

## 4. 비적용 범위

- 기존 실제 e1RM, volume, 근육군 분석 또는 workout 기록의 변경
- 미확인 set을 실제 수행으로 취급하는 것
- 연결조직 상태, OFI, readiness 또는 ProgramBuilder 입력
- `metricSeries`에 posterior 값을 관측값처럼 추가하는 것
- `LegacyTimeSeriesAnalyzer` 또는 strict BVAR/BLP 준비 입력으로 posterior를 전달하는 것
- 임상 진단, 부상 예측, 경기력 보장 또는 장기 미래 예측

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다.

- `actualCanonicalE1rmKg`: 기존 Epley 계산으로 얻은 확인된 주요 리프트의 실제 e1RM
- `effortAdjustedPerformanceKg`: RPE가 있으면 RIR을 반영한 모델용 수행 신호
- `prior`: 해당 세션 또는 주의 관측을 아직 반영하지 않은 예상 상태
- `posterior`: 해당 시점까지 허용된 관측을 반영한 추정 상태
- `proxy-only week`: 목표 리프트 직접 기록 없이 관련 운동 신호만 있는 주
- `loading`: 운동 metadata가 고정 latent factor에 기여하는 정도

## 6. 입력 데이터

입력은 `WorkoutEntryWithSets`, exercise stable key와 effective runtime metadata, `DailyMetric.bodyWeightKg`, `InitialUserProfile.bodyWeightKg`입니다. 확인된 set만 사용하고 반복수는 1~12회로 제한합니다. set RPE가 유효하면 우선 사용하며, 없으면 entry RPE를 사용합니다. 둘 다 없으면 수행 신호는 유지하지만 관측 분산을 높입니다. 체중 운동은 기존 `BodyweightEffectiveLoadCalculator`를 그대로 사용합니다. `SPORT_SESSION`과 usable loading이 없는 운동은 proxy 관측이 아닙니다.

## 7. 계산 또는 분류 계약

실제 주요 리프트 e1RM은 기존 식을 그대로 사용합니다.

`actual e1RM = weightKg * (1 + reps / 30)`

모델용 set 수행 신호는 다음과 같습니다.

`effort adjusted = effectiveLoad * (1 + (reps + RIR) / 30)`

RIR은 `10 - RPE`를 0~4로 제한합니다. RPE가 없으면 RIR을 0으로 두되 관측 분산을 높입니다. 한 entry의 여러 set은 반복수, RPE 존재, set 순서, 외부 중량과 metadata 신뢰도를 반영한 품질 가중 중앙값으로 하나의 session 관측이 됩니다.

상태 공간은 정확히 다음 여덟 factor를 사용합니다.

1. `PRESS_SHARED`
2. `HORIZONTAL_PRESS_SPECIFIC`
3. `KNEE_EXTENSION`
4. `HIP_EXTENSION_POSTERIOR_CHAIN`
5. `TRUNK_BRACING`
6. `BENCH_SPECIFIC`
7. `SQUAT_SPECIFIC`
8. `DEADLIFT_SPECIFIC`

전이는 factor별 대각 persistence와 process noise를 사용합니다. covariance update는 Joseph form을 사용하고, symmetry와 Cholesky positive-definite 검사를 거칩니다. 실패 시 bounded jitter만 순서대로 시도하며 복구되지 않으면 해당 update를 신뢰하지 않고 이전 상태를 유지합니다.

## 8. 집계 방식

관측은 날짜, 수행 시각, display order, entry id, stable key 순으로 결정론적으로 정렬합니다. 각 exercise의 log 수행 baseline은 현재 관측 이전 history만 사용하고 최대 64개를 유지합니다. 중앙값과 MAD 기반 scale로 정규화합니다. 상태는 일 단위로 전이하고 Monday-Sunday 주별 prior, posterior, 직접 관측 최대 e1RM, 누적 직접/proxy count를 만듭니다. 같은 entry의 여러 set은 하나의 session 관측으로만 집계합니다.

## 9. 출력과 UI 해석

- `실제 e1RM`: 기존 확인 기록이며 점끼리 임의로 연결하지 않습니다.
- `추정 수행 상태`: 선택된 상태 공간 model의 posterior 중앙값입니다.
- `80% 추정 범위`: historical posterior uncertainty band이며 미래 forecast range가 아닙니다.
- `세션 전 예상`: 해당 목표 세션을 update하기 전 prior predictive 결과입니다.
- `예상 대비 수행`: 실제 canonical e1RM과 model용 effort-adjusted 차이를 각각 명시해 보여 줍니다.
- `proxy 근거`: 관련 운동 loading과 standardized innovation을 실제 기록과 분리해 보여 줍니다.

기본 분석 화면과 실험실 모두 이를 실험적 추정으로 표시하며 실제 기록을 대체하지 않는다고 설명합니다.

## 10. 예외 및 fallback

목표별 rolling backtest는 `M0_LOCF`, `M1_TARGET_ONLY`, `M2_SHARED_FACTORS`, `M3_SHARED_PLUS_TARGET_SPECIFIC`을 비교합니다. prior-only 직접 test가 3개 미만이거나 proxy 관측 4개 또는 서로 다른 proxy 운동 2개 미만이면 M1을 유지합니다. M2/M3는 MAE, RMSE, 80% interval width, Gaussian log predictive density와 충분한 표본에서 coverage gate를 통과할 때만 선택합니다. M3는 직접 test 6개, proxy 관측 8개, 서로 다른 proxy 운동 3개 이상의 추가 조건을 가집니다. 입력 부족 시 값을 발명하지 않고 null/insufficient 상태를 유지합니다.

## 11. 개인화 또는 보정

각 exercise stable key는 자기 자신의 과거 수행으로만 prior-only online baseline을 만듭니다. custom exercise는 resolved runtime metadata가 없으면 cross-loading을 제공하지 않습니다. 체중 운동의 체중은 해당 날짜 이전의 최신 유효 일별 체중을 우선하고, 없으면 초기 사용자 profile 체중을 사용합니다. 다른 사용자의 계수나 population posterior를 주입하지 않습니다.

## 12. 연구 근거

상태 공간 filtering, rolling-origin 검증, robust normalization과 predictive interval은 일반적인 통계 원리를 사용합니다. 그러나 exercise 간 loading 계수와 model 선택 임계값은 이 제품의 metadata taxonomy에 맞춘 제품 정책과 engineering heuristic입니다. 개별 계수가 검증된 생체역학 효과크기라는 뜻은 아닙니다.

## 13. 제품 정책 및 휴리스틱

주요 loading 정책은 다음과 같으며 effective metadata 신뢰도를 곱합니다.

- 벤치프레스 직접: press `0.90`, horizontal press `1.00`, bench-specific `1.00`
- close-grip bench: `0.90 / 0.90 / 0.65`
- dumbbell press: `0.85 / 0.90 / 0.25`; incline 계열 horizontal `0.65`
- machine press: `0.80 / 0.75 / 0.10`
- dip: `0.65 / 0.25 / 0.05`; overhead press: `0.55 / 0.15`; landmine press: `0.45 / 0.10`
- 스쿼트 직접: knee `1.00`, hip `0.75`, trunk `0.65`, squat-specific `1.00`
- 데드리프트 직접: knee `0.45`, hip `1.00`, trunk `0.85`, deadlift-specific `1.00`
- lunge/split squat: `0.85 / 0.55 / 0.35 / 0.20`
- leg press: `1.00 / 0.45 / 0.10 / 0.05`
- front squat: `0.95 / 0.60 / 0.80 / 0.55`
- RDL: `0.15 / 1.00 / 0.70 / 0.40`; good morning: `0.10 / 0.90 / 0.80 / 0.15`
- hip thrust/glute bridge: `0.15 / 0.85 / 0.20 / 0.05`

M2가 M1보다 material improvement로 인정되려면 MAE는 최대 `0.97x`, RMSE는 최대 `1.02x`, 평균 interval width는 최대 `1.75x`, log predictive density 저하는 최대 `0.50`이어야 합니다. 직접 test 5개 이상이면 80% interval coverage가 최소 `0.40`이어야 합니다.

## 14. 알려진 한계

- loading과 noise 계수는 사용자별 calibration이 끝난 연구 모수가 아니라 보수적 제품 휴리스틱입니다.
- 목표 리프트 직접 history가 없으면 kg scale 자체를 만들 수 없어 관련 운동만으로 절대 중량을 발명하지 않습니다.
- metadata가 희소하거나 잘못 분류되면 proxy evidence가 줄거나 잘못된 방향을 가질 수 있습니다.
- RPE는 자기보고 값이며 누락 시 불확실성이 커집니다.
- 80% 구간은 모델 불확실성이지 실제 수행의 보장 범위가 아닙니다.
- posterior draw를 보존하는 strict Bayesian 시계열 model이 아니며 BVAR/BLP causal input으로 사용할 수 없습니다.

## 15. 현재 구현 상태

- Specification status: `EXPERIMENTAL`
- Runtime implementation status: `IMPLEMENTED`
- Model version: `proxy-performance-1.0.0`
- 대상별 model 선택과 fallback은 deterministic rolling backtest로 수행합니다.
- 결과는 `PerformanceTrendSummary.proxyPerformanceSummary`에 별도로 보관하고 기존 `metricSeries`를 변경하지 않습니다.

## 16. 구현 위치

- [`ProxyPerformanceModels.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceModels.kt)
- [`ProxyPerformanceObservationBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceObservationBuilder.kt)
- [`ProxyPerformanceLoadingBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceLoadingBuilder.kt)
- [`ProxyPerformanceKalmanFilter.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceKalmanFilter.kt)
- [`ProxyPerformanceEstimator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceEstimator.kt)
- [`ProxyPerformanceBacktester.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceBacktester.kt)
- [`ProxyPerformanceSummaryBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceSummaryBuilder.kt)
- [`PerformanceTrendSummaryService.kt`](../../../app/src/main/java/com/training/trackplanner/data/PerformanceTrendSummaryService.kt)
- [`AnalysisProxyPerformanceUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisProxyPerformanceUi.kt)

## 17. 검증 테스트

- [`ProxyPerformanceObservationAndLoadingTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceObservationAndLoadingTest.kt)
- [`ProxyPerformanceEstimatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceEstimatorTest.kt)
- [`ProxyPerformanceScaleTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/proxyperformance/ProxyPerformanceScaleTest.kt)
- [`AnalysisProxyPerformanceUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisProxyPerformanceUiTest.kt)
- [`StrengthAndMuscleMetricSeriesBuilderTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/lab/StrengthAndMuscleMetricSeriesBuilderTest.kt)
- [`AnalysisStrengthChartSpecTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisStrengthChartSpecTest.kt)

## 18. 권위 자산

- [`canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`](../../../app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv)

이 CSV의 stable-key 기반 movement, role, family와 confidence metadata가 proxy loading 분류의 authority입니다. 문서의 계수는 runtime `ProxyPerformanceLoadingBuilder`가 최종 authority입니다.

## 19. 관련 문서

- [`STRENGTH_VOLUME_CALCULATION.md`](STRENGTH_VOLUME_CALCULATION.md)
- [`docs/analysis_algorithm_design.md`](../../analysis_algorithm_design.md)
- [`docs/bayesian_time_series_lab_architecture.md`](../../bayesian_time_series_lab_architecture.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.0.0` (2026-07-23): 실제 e1RM과 분리된 metadata-constrained cross-loading, prior-only rolling model 선택, 80% 추정 범위와 실험 UI 계약을 처음 등록했습니다.
