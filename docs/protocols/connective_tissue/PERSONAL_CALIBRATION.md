# 연결조직 개인 보정

| Field | Value |
|---|---|
| Protocol ID | CT-PERSONAL-CALIBRATION |
| Protocol version | 1.1.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.7 |
| Last audited commit | 22e51779bbd173e554c3ba1dbeec0fcf13a6ba20 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.1.0`은 기존 개인 history percentile 계약을 유지하면서, offline prior 생성과 bounded profile adjustment의 Phase 1 계약을 추가합니다. 이 버전은 상대 상태 runtime 활성화, 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

현재 연결조직 화면은 실제 운동 기록으로 계산한 잔여 부하를 보여 줍니다. 기록이 부족한 사용자에게도 향후 상대 비교 기준을 제공할 수 있도록, 앱 밖에서 결정론적으로 생성한 제품 prior와 제한적인 프로필 보정 규칙을 준비했습니다. 현재 화면의 `보정 중` 상태와 분류는 아직 바뀌지 않습니다.

## 2. 목적

이 계약은 다음 값을 명확히 분리합니다.

- `CurrentLoad(unit)`: 실제 사용자 기록에서 현재 production exposure/recovery authority가 계산한 잔여 부하입니다.
- `BasePriorBaseline(unit)`: 사용자 history와 무관하게 offline canonical scenario ensemble에서 생성한 제품 prior 분포입니다.
- `AdjustedPriorBaseline(unit, userProfile)`: Base prior의 Q30/Q80/Q95에 보수적인 사용자 프로필 multiplier를 적용한 결과입니다.
- `PersonalBaseline(unit)`: 향후 유효한 개인 history에서 산출할 load-unit별 분포입니다. 이번 Phase 1에는 구현하지 않습니다.
- `w_perUnit`: 향후 PersonalBaseline 신뢰도를 나타낼 load-unit별 혼합 가중치입니다. 이번 Phase 1에는 구현하지 않습니다.
- `FinalBoundary(unit, q)`: 향후 relative-state classifier가 사용할 혼합 경계입니다. 이번 Phase 1에는 활성화하지 않습니다.

미래 혼합식은 다음과 같습니다.

`FinalBoundary(unit, q) = AdjustedPriorBoundary(unit, q) * (1 - w_perUnit) + PersonalBoundary(unit, q) * w_perUnit`

`w_perUnit`은 비교 경계만 혼합하며 `CurrentLoad`를 곱하거나 줄이지 않습니다.

## 3. 적용 범위

Phase 1은 다음에만 적용됩니다.

- production connective-tissue exposure, event ledger와 recovery authority를 직접 재사용하는 offline deterministic generator
- 13개 prior profile과 현재 77개 unsided `loadUnitStableKey`의 명시적 일대일 할당
- local evaluation hour 0~23별 Q30/Q80/Q95와 `meaningfulFloor`
- body mass, habitual intensity와 두 experience domain을 사용하는 순수 bounded adjustment evaluator
- canonical JSON, app-ready JSON, manifest, report, checksums, parity와 drift validation

생성 registry의 lifecycle은 `DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE`입니다.

## 4. 비적용 범위

다음은 이번 Phase 1의 runtime 동작이 아닙니다.

- 현재 `보정 중` UI 교체
- low/normal/high/very-high relative state 분류
- `PersonalBaseline`과 `w_perUnit` 계산
- FinalBoundary 혼합
- 임시 threshold 또는 추정된 개인 capacity
- OFI나 다섯 fatigue axis 변경
- exposure 식, recovery curve, C 값, mapping 또는 `mappingRoleWeight` 변경

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. load unit은 display name이 아니라 canonical unsided stable key로만 연결합니다.

- `priorProfileId`: tissue type, recovery route, unit semantics와 load characteristics를 공유하는 prior profile key
- `meaningfulFloor`: 수치상 near-recovery를 구분하는 양의 경계이며 사용자 프로필로 보정하지 않습니다.
- `SIMULATION_FITTED`: production calculation authority를 사용하는 offline simulation에서 얻은 계수
- `POLICY_BOUNDED`: 생물학적 효과 추정이 아니라 명시적으로 제한한 제품 정책 계수
- `NEUTRAL_NOT_APPLICABLE`: 해당 profile에 적용할 근거가 없어 multiplier 1.0으로 유지하는 계수

## 6. 입력 데이터

Offline generator 입력은 다음과 같습니다.

- production asset protocol `RCV-ALL-0.6 | RCV-EXPOSURE-1.1`
- 77개 load unit, exercise-to-unit authority rows, M/D/I/C 계약과 recovery curve knots
- [`scenario_catalog.json`](../../../tools/connective-tissue-prior/scenario_catalog.json)의 8개 explicit weighted weekly template
- [`prior_profile_registry.json`](../../../tools/connective-tissue-prior/prior_profile_registry.json)의 13개 profile rule와 완전한 stable-key assignment 규칙

Scenario catalogue는 약 2/3/4 exposure days, even/consecutive/weekend scheduling, light/moderate/hard와 occasional very-hard 구성을 포함합니다. 가중치는 측정된 인구 prevalence가 아니라 검토 가능한 제품 정책입니다.

Adjustment evaluator 입력은 `bodyWeightKg`, `strengthTrainingExperienceYears`, `racketSportExperienceYears`, `habitualTrainingIntensity`와 해당 prior profile입니다. 누락 입력은 관련 없는 필드에서 추정하지 않고 neutral 1.0과 provenance를 반환합니다.

## 7. 계산 또는 분류 계약

### Base prior generation

각 profile/scenario를 112 consecutive calendar days 동안 simulation하고 첫 56일을 burn-in으로 버립니다. 마지막 56일의 residual distribution을 local evaluation hour 0~23에서 평가합니다. Generator reference zone은 `Asia/Seoul`이며 exact timestamp를 사용합니다. Runtime의 임의 timezone 지원을 대체하는 새 시간 규칙이 아니라, hour-bucket representation을 생성하기 위한 결정론적 reference입니다.

각 `(priorProfileId, localHour)`는 다음을 만족합니다.

`0 <= meaningfulFloor < Q30 < Q80 < Q95`

완전 회복된 zero state는 Q30 아래이며, quantile은 positive residual distribution에서 계산합니다. `meaningfulFloor`는 가장 작은 양의 residual의 절반을 사용하고 profile adjustment에서 고정합니다.

### User-profile adjustment

`profileMultiplier = clamp(exp(bodyMassLog + habitualIntensityLog + strengthExperienceLog + racketExperienceLog), 0.85, 1.15)`

절대 safety boundary는 `0.80 <= profileMultiplier <= 1.20`입니다. Hard clamp를 먼저 확인한 뒤 정상 작동 범위 0.85~1.15를 적용합니다.

- `AdjustedQ30 = BaseQ30 * profileMultiplier`
- `AdjustedQ80 = BaseQ80 * profileMultiplier`
- `AdjustedQ95 = BaseQ95 * profileMultiplier`
- `AdjustedMeaningfulFloor = BaseMeaningfulFloor`

같은 multiplier를 세 quantile에 적용하므로 shape와 ordering을 보존합니다.

### Body mass

Reference body mass는 75 kg이며 50/65/75/90/105 kg grid를 평가합니다.

`bodyMassLog = bodyMassBeta(profile) * ln(bodyWeightKg / 75)`

Production ledger의 bodyweight normalization을 그대로 통과시킨 결과 이번 registry의 13개 profile 모두 numerical effect가 negligible하여 beta를 0으로 저장했습니다. 비영 계수를 강제하지 않았습니다. 모든 profile의 median/p95/max relative fit error는 0이며, 향후 production semantics가 바뀌면 fingerprint drift가 regeneration을 요구합니다.

### Habitual intensity

`LIGHT`, `NORMAL`, `HARD`는 current-session RPE가 아닌 profile input입니다. 동일한 scenario structure에 intensity distribution만 바꿔 fitting한 multiplier는 각각 0.96, 1.00, 1.04입니다. Profile별 median/p95 error는 0이고 최대 error는 4% 이하입니다.

### Experience

Experience는 simulation이 발견한 생물학적 적응 효과가 아니라 `POLICY_BOUNDED` 제품 prior입니다.

- `< 0.5 years`: -1.0
- `0.5..<2 years`: -0.5
- `2..<5 years`: 0.0
- `5..<10 years`: +0.5
- `>=10 years`: +1.0

Strength/racket domain은 profile별 relevance 0.0~1.0을 갖고 domain당 최대 약 +/-4% 효과를 냅니다. Combined experience contribution은 정상적으로 0.94~1.06에 제한됩니다. Heavy resistance profile은 strength relevance가, direction-change/jump/landing/lunge 관련 profile은 racket relevance가 더 높고 unrelated profile은 0입니다.

## 8. 집계 방식

8개 canonical scenario의 explicit weight를 사용해 profile/hour별 residual samples를 하나의 weighted product prior로 집계합니다. 가중치는 합계 1.0이며 숨겨진 random sampling은 없습니다. Scenario weight를 각각 +/-10% perturb한 sensitivity validation에서 최대 boundary 변화는 6.48% 이하였습니다.

모든 현재 load unit은 정확히 하나의 profile에 할당됩니다. 누락, 중복 authority, display-name fallback, left/right 복제는 허용하지 않습니다.

## 9. 출력과 UI 해석

Canonical registry는 [`connective_tissue_prior_baselines.json`](../../../tools/connective-tissue-prior/generated/connective_tissue_prior_baselines.json)이며 app-ready copy는 [`connective_tissue_prior_baselines_v1.json`](../../../app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json)입니다. 둘의 실제 file SHA-256은 `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`로 동일합니다.

App-ready JSON은 현재 APK asset에 포함되지만 어떤 UI, classifier 또는 service caller도 읽지 않습니다. 이번 단계에서 current user-facing state는 그대로입니다.

향후 UI는 낮은 편/평소 범위/높은 편/매우 높은 편을 주요 결과로 보여 주고 baseline provenance는 화면 하단에 한 번만 표시합니다. 각 tissue row에 provenance를 반복하거나 오해를 부르는 calibration percentage를 표시하지 않습니다.

## 10. 예외 및 fallback

- Profile input 누락: neutral 1.0을 쓰고 `missingInputs`와 coefficient source를 보존합니다.
- Body mass가 finite positive가 아니거나 experience years가 finite non-negative가 아니면 입력을 거부합니다.
- Extreme experience years는 위 band score의 +1.0에서 포화합니다.
- Profile assignment 누락/중복, unknown key, invalid quantile, non-finite output, checksum mismatch 또는 stale production fingerprint는 generation/validation 실패입니다.
- Runtime relative-state fallback은 이번 단계에 없습니다. 기존 `보정 중` 동작을 유지합니다.

## 11. 개인화 또는 보정

현재 implemented history percentile 계약은 동일 load unit의 관찰 history에서만 상대화하며, 56 observation day 미만은 `CALIBRATING`, history 없음은 `UNAVAILABLE`입니다. 이 기존 runtime 경로는 Phase 1 prior를 소비하지 않습니다.

향후 `PersonalBaseline`은 다음 정책을 따를 예정입니다.

- 현재 시점 직전 7개 calendar days 제외; 7 sessions가 아님
- 그 이전 최대 56 valid calendar days 사용
- load-unit별 exposure sufficiency 판단
- long inactivity 이후 stale personal history의 신뢰도 축소 또는 reset
- sufficiency에 따라 `w_perUnit`을 계산하고 comparison boundary만 혼합

이 미래 정책은 `DESIGNED / NOT_YET_RUNTIME_ACTIVE`이며 이번 task에서 production runtime으로 구현하지 않습니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. Production M/D/I/C와 recovery calculation authority는 기존 evidence package를 재사용하지만 scenario weights, prior profile sharing, experience coefficients와 multiplier cap은 직접적인 population effect size가 아닙니다.

## 13. 제품 정책 및 휴리스틱

- 8개 scenario와 weight
- 13개 shared prior profile
- positive-residual quantile policy와 meaningful floor
- normal clamp 0.85~1.15, hard clamp 0.80~1.20
- domain당 약 +/-4% experience coefficient와 relevance
- 112-day simulation, 56-day burn-in, 24 local-hour buckets

위 항목은 deterministic하고 검증 가능하지만 population prevalence, tissue adaptation percentage 또는 injury-risk estimate로 표현하지 않습니다.

### 이중계산 방지

- Current-session RPE는 production `CurrentLoad`의 intensity input입니다.
- Actual historical records는 향후 `PersonalBaseline`과 `w_perUnit`에만 사용합니다.
- Habitual profile intensity는 `AdjustedPriorBaseline`에만 사용합니다.
- Body mass는 production exposure normalization과 동일한 semantics를 거치며 현재 fit이 neutral이면 그대로 0입니다.
- Experience는 `CurrentLoad`, exposure 또는 recovery speed를 바꾸지 않습니다.
- Profile multiplier와 `w_perUnit`은 `CurrentLoad`에 곱하지 않습니다.

## 14. 알려진 한계

- Base prior는 representative product scenarios의 분포이지 population norm이 아닙니다.
- Scenario weight는 product policy이며 실제 사용자 빈도 prevalence가 아닙니다.
- 13개 shared profile은 tissue/load/recovery 특성의 실용적 grouping이며 개별 load unit의 고유 생물학적 distribution을 입증하지 않습니다.
- Reference-zone generation은 24 local-hour compatibility를 제공하지만 DST transition 자체를 population scenario로 model하지 않습니다.
- Body-mass coefficient가 모두 0인 것은 현재 production normalization에서 추가 profile-scale effect가 식별되지 않았다는 뜻이며 body mass가 생물학적으로 무관하다는 뜻이 아닙니다.
- Experience policy는 capacity나 adaptation을 측정하지 않습니다.
- 결과는 진단, injury probability, damage measurement, tissue-capacity measurement 또는 exact biological recovery clock이 아닙니다.
- Current UI와 classifier가 아직 registry를 소비하지 않으므로 사용자-facing benefit은 후속 runtime integration 전까지 활성화되지 않습니다.

## 15. 현재 구현 상태

- Existing personal-history specification: `ACTIVE`
- Existing personal-history runtime: `IMPLEMENTED`
- Base prior protocol: `DESIGNED`
- Canonical artifacts: `GENERATED`
- Coverage, ordering, parity, fit, sensitivity and drift checks: `VALIDATED`
- Relative-state runtime integration: `NOT_YET_RUNTIME_ACTIVE`
- `PersonalBaseline`, `w_perUnit`, FinalBoundary and low/normal/high/very-high classifier: `NOT_YET_RUNTIME_ACTIVE`

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissueCurrentStateServices.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissueCurrentStateServices.kt): 기존 personal-history percentile runtime
- [`app/src/main/java/com/training/trackplanner/analysis/tissue/TissuePriorAdjustment.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/tissue/TissuePriorAdjustment.kt): 아직 caller가 없는 순수 bounded adjustment evaluator
- [`tools/connective-tissue-prior/src/main/kotlin/com/training/trackplanner/analysis/tissue/ConnectiveTissuePriorGenerator.kt`](../../../tools/connective-tissue-prior/src/main/kotlin/com/training/trackplanner/analysis/tissue/ConnectiveTissuePriorGenerator.kt): production authority를 직접 재사용하는 offline generator/validator
- [`app/build.gradle.kts`](../../../app/build.gradle.kts): generator와 validation JavaExec task

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissueAggregationAndRankingTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorBaselineGenerationTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorBaselineGenerationTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorProductionParityTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorProductionParityTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorAdjustmentTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/tissue/TissuePriorAdjustmentTest.kt)

## 18. 권위 자산

- [`scenario_catalog.json`](../../../tools/connective-tissue-prior/scenario_catalog.json)
- [`prior_profile_registry.json`](../../../tools/connective-tissue-prior/prior_profile_registry.json)
- [`connective_tissue_prior_baselines.json`](../../../tools/connective-tissue-prior/generated/connective_tissue_prior_baselines.json)
- [`generation_manifest.json`](../../../tools/connective-tissue-prior/generated/generation_manifest.json)
- [`generation_report.md`](../../../tools/connective-tissue-prior/generated/generation_report.md)
- [`connective_tissue_prior_baselines_v1.json`](../../../app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json)

Canonical registry file SHA-256는 `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`, deterministic output checksum은 `5303516c1b972ce3bdf08eaffcd7c5fe6448bfa64c50abae9790d8d81af0c58e`, production recovery-engine fingerprint는 `8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a`입니다.

## 19. 관련 문서

- [`docs/protocols/connective_tissue/MSCP_DI_EXPOSURE_MODEL.md`](MSCP_DI_EXPOSURE_MODEL.md)
- [`docs/protocols/common/IMPLEMENTATION_STATUS.md`](../common/IMPLEMENTATION_STATUS.md)
- [`docs/protocols/common/LIMITATIONS_AND_SAFETY.md`](../common/LIMITATIONS_AND_SAFETY.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.1.0` (2026-07-17): deterministic offline BasePrior generation, complete stable-key registry, bounded profile adjustment와 future PersonalBaseline/w 경계를 문서화했습니다. Artifacts는 generated/validated이지만 runtime에는 비활성입니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
