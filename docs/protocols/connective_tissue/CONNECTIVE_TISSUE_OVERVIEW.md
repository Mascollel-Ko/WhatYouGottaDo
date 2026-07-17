# 연결조직 부하·회복 개요

| Field | Value |
|---|---|
| Protocol ID | CT-OVERVIEW |
| Protocol version | 2.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.7; relative baseline active from v0.4.2.12 |
| Last audited commit | b95a1684ad8bc0ba82cd5eae52ccb3147eae4d61 |
| Evidence profile | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY |
| Supersedes | CT-OVERVIEW 1.0.0 |

## 1. 일반 사용자용 요약

연결조직 모델은 확인된 운동 기록으로 77개 unsided load unit의 현재 잔여 부하를 계산합니다. 그 값을 조직별 초기 기준과 유효한 개인 운동 기록의 비교 경계에 놓아 `낮은 편`, `평소 범위`, `높은 편`, `매우 높은 편`으로 보여 줍니다. 임상 손상이나 부상 확률을 예측하지 않습니다.

## 2. 목적

현재 제품의 입력, production exposure/recovery, prior/personal baseline, 분류, 집계, 표시와 fallback을 하나의 재현 가능한 canonical flow로 정의합니다.

확인된 workout record → production exposure event → recovery/residual `CurrentLoad` → generated Phase 1 prior lookup → profile-adjusted prior → valid historical segmentation → daily personal residual samples → per-unit `w` → mixed boundaries → relative state → joint aggregation → UI → bottom provenance footer once.

## 3. 적용 범위

239 exercise protocol, 3,507 authority row, 50 protocol class, 13 DI profile, 21 recovery curve/114 knot, 7 routing row, 15 joint complex와 77 load unit을 사용하는 현재 connective-tissue runtime에 적용합니다.

## 4. 비적용 범위

OFI와 다섯 축, ProgramBuilder, strength/badminton volume, 치료 판단, 부상 예측, 센서 기반 조직 측정 또는 다른 protocol family 계산은 포함하지 않습니다.

## 5. 용어

용어는 [TERMINOLOGY.md](../common/TERMINOLOGY.md)를 따릅니다. `CurrentLoad`는 production residual, baseline은 비교 경계, `w_perUnit`은 prior/personal 경계 혼합 weight입니다. Stable key와 enum은 runtime 표기를 유지합니다.

## 6. 입력 데이터

- 확인된 set이 있는 workout record와 effective exercise metadata
- Production load-unit/mapping/DI/context/recovery assets
- App-ready generated prior registry의 stable-key/local-hour mapping
- Existing bodyweight authority와 nullable profile experience/intensity
- 명시 저장된 DailyCheckIn 날짜(개인 history anchor 전용)

## 7. 계산 또는 분류 계약

`CurrentLoad`는 production M/S/P/D/I/C, effort, dose, context modifier와 PCHIP recovery로 계산합니다. Phase 2는 이를 scale하지 않습니다. `TissuePriorAdjustment`와 per-unit personal baseline은 Q30/Q80/Q95 비교 경계만 바꿉니다.

Personal history가 없거나 무효여도 valid prior가 있으면 relative state를 냅니다. 세부 날짜, gap, weighted quantile와 선형 혼합 계약은 [PERSONAL_CALIBRATION.md](PERSONAL_CALIBRATION.md)를 따릅니다.

## 8. 집계 방식

Event는 `TissueRcvLoadKey(loadUnitStableKey, loadDimension)`을 보존하지만 current/personal/prior 비교는 Phase 1과 호환되는 unit aggregate입니다. Joint complex는 child를 합산해 새 점수를 만들지 않고 worst child 상태를 사용합니다.

## 9. 출력과 UI 해석

정확한 상태 label은 `낮은 편`, `평소 범위`, `높은 편`, `매우 높은 편`, genuine authority failure의 `판단 불가`입니다. Old percentile과 calibration percentage를 표시하지 않고 baseline source를 scroll 마지막에 한 번만 보여 줍니다.

## 10. 예외 및 fallback

PersonalBaseline이 empty, degenerate, non-finite 또는 identity-incompatible이면 adjusted prior로 fallback하고 diagnostic을 남깁니다. 한 unit prior가 없으면 그 unit만 unavailable이며 다른 profile을 빌리지 않습니다. Registry 전체가 무효면 old global percentile이나 임시 threshold로 fallback하지 않습니다.

## 11. 개인화 또는 보정

개인 이력은 latest confirmed workout/check-in anchor, 최근 7개 달력 날짜 제외, global/unit gap segmentation, 최대 56 weighted observation days와 distinct exposure sufficiency를 unit별로 적용합니다. Profile multiplier와 `w_perUnit`은 CurrentLoad가 아니라 경계에만 적용됩니다.

## 12. 연구 근거

Evidence profile은 `MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY`입니다. Production exposure/recovery는 기존 evidence authority를 재사용하며 prior scenarios와 calibration policy는 제품 정책입니다.

## 13. 제품 정책 및 휴리스틱

Representative scenario weights, shared prior profile, gap retention, weight ramp, experience coefficient와 multiplier cap은 deterministic product policy이며 population prevalence나 biological capacity로 표현하지 않습니다.

## 14. 알려진 한계

- 일부 recovery/metadata 관계는 class-level 또는 low-confidence proxy입니다.
- Self-entered reps, load, duration, RPE, bodyweight, check-in과 timestamp precision의 품질 한계가 전달됩니다.
- Relative state는 modeled residual의 비교 위치이며 조직 상태 직접 측정이 아닙니다.

## 15. 현재 구현 상태

- Base prior: `DESIGNED / GENERATED / VALIDATED / RUNTIME_ACTIVE / TESTED`
- Per-unit PersonalBaseline, weight, classifier와 provenance UI: `IMPLEMENTED / RUNTIME_ACTIVE / TESTED`
- APK runtime simulation: `ABSENT`

## 16. 구현 위치

- `TissueRcvEventLedger.kt`, `TissueRecoveryEngine.kt`: production load와 recovery
- `TissueCalibrationHistoryPolicy.kt`, `TissuePerUnitWeightPolicy.kt`, `TissuePersonalBaselinePolicy.kt`: personal history
- `TissuePriorRegistry.kt`, `TissueEffectiveBaselinePolicy.kt`: prior lookup, mixing, classification
- `ConnectiveTissueAnalysisService.kt`, `TissueCurrentStateServices.kt`: orchestration와 aggregation
- `ConnectiveTissueAnalysisUi.kt`: presentation

## 17. 검증 테스트

`TissueRcvAssetImportTest`, `TissueRecoveryEngineTest`, `TissueCalibrationHistoryPolicyTest`, `TissuePerUnitWeightPolicyTest`, `TissuePersonalBaselinePolicyTest`, `TissueEffectiveBaselineRuntimeTest`, `TissueAggregationAndRankingTest`, `ConnectiveTissueAnalysisUiTest`가 authority와 product-facing 경계를 검증합니다.

## 18. 권위 자산

- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_asset_manifest_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_load_units_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json`

Prior registry는 77 units, 13 profiles, 24 hours, 936 quantile values와 file SHA-256 `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`를 유지합니다.

## 19. 관련 문서

- [개인 기준과 상대 상태](PERSONAL_CALIBRATION.md)
- [순위와 표시](RANKING_AND_PRESENTATION.md)
- [노출 모델](MSCP_DI_EXPOSURE_MODEL.md)
- [회복 곡선](RECOVERY_CURVES.md)
- [공통 안전 한계](../common/LIMITATIONS_AND_SAFETY.md)

## 20. 변경 이력

- `2.0.0` (2026-07-18): generated prior, per-unit personal baseline, relative-state classifier와 provenance UI를 runtime flow에 활성화했습니다.
- `1.0.0` (2026-07-17): 첫 governed overview를 등록했습니다.
