# 연결조직 개인 기준과 상대 상태

| Field | Value |
|---|---|
| Protocol ID | CT-PERSONAL-CALIBRATION |
| Protocol version | 2.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.12 |
| Last audited commit | b95a1684ad8bc0ba82cd5eae52ccb3147eae4d61 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | CT-PERSONAL-CALIBRATION 1.1.0 |

## 1. 일반 사용자용 요약

개인 기록이 짧아도 앱은 조직별 초기 기준으로 `낮은 편`, `평소 범위`, `높은 편`, `매우 높은 편` 중 하나를 표시합니다. 기록이 쌓이면 각 조직의 유효한 개인 운동 기록이 비교 경계에 점진적으로 반영됩니다. 기록 반영 정도는 부하나 적응률이 아니며 퍼센트로 표시하지 않습니다.

## 2. 목적

검증된 Phase 1 prior, 프로필 조정, 날짜 기반 개인 residual history와 load-unit별 calibration weight를 하나의 연속적인 경계 혼합 계약으로 정의합니다. 개인 이력이 56일보다 짧다는 이유만으로 주 결과를 숨기지 않으면서 stale history, self-normalization과 이중 적용을 막는 것이 목적입니다.

## 3. 적용 범위

현재 77개 unsided `loadUnitStableKey`, 13개 generated prior profile, local-hour 0~23, confirmed workout record와 persisted DailyCheckIn anchor, Q30/Q80/Q95 relative classifier에 적용합니다.

## 4. 비적용 범위

Exposure M/S/P/D/I/C, COD context, recovery curve/PCHIP, OFI, ProgramBuilder, contributor ratio, tissue mapping을 변경하지 않습니다. Population norm, biological adaptation percentage, tissue capacity, diagnosis 또는 injury probability를 정의하지 않습니다.

## 5. 용어

- `CurrentLoad`: production event ledger와 recovery calculator의 현재 residual 합
- `BasePriorBaseline`: offline generated floor/Q30/Q80/Q95
- `AdjustedPriorBaseline`: profile multiplier를 prior quantile에만 적용한 결과
- `PersonalBaseline`: retained weighted daily residual active distribution의 quantile
- `w_perUnit`: unit별 prior/personal boundary mixture weight
- `effectiveW`: valid personal이면 w, 아니면 0
- `FinalBoundary`: adjusted prior와 personal의 linear mixture
- `relativeBandPosition`: UI percentage가 아닌 internal ranking 위치

## 6. 입력 데이터

- Confirmed set이 하나 이상 있는 workout records와 production tissue events
- Existing canonical bodyweight resolver
- Existing strength training years와 separate badminton/racket-sport years
- Nullable persisted habitual intensity `LIGHT/NORMAL/HARD`
- Explicitly persisted DailyCheckIn dates(anchor only)
- App-ready `connective_tissue_prior_baselines_v1.json`
- Analysis `ZoneId`와 current local hour

## 7. 계산 또는 분류 계약

아래 43개 항목이 canonical runtime 계약입니다.

1. **CurrentLoad**는 production `TissueRcvEventLedgerBuilder`, `TissueResidualCalculator`와 recovery curve가 확인된 기록에서 계산한 실제 잔여 부하입니다.
2. **BasePriorBaseline**은 deterministic offline generator가 13 profiles, 24 local-hour buckets와 77 units에 만든 `meaningfulFloor/Q30/Q80/Q95`입니다.
3. **AdjustedPriorBaseline**은 `TissuePriorAdjustment`가 BasePrior Q30/Q80/Q95에 하나의 bounded profile multiplier를 적용한 값이며 floor는 고정입니다.
4. **PersonalBaseline**은 호환되는 unit의 retained historical daily residual active samples에서 얻은 weighted Q30/Q80/Q95입니다.
5. **w_perUnit**은 각 `loadUnitStableKey`마다 별도로 계산하는 경계 혼합 가중치입니다.
6. **effectiveW**는 PersonalBaseline이 valid하면 w, absent/invalid하면 0입니다.
7. **FinalBoundary**는 `AdjustedPriorBoundary * (1 - effectiveW) + PersonalBoundary * effectiveW`입니다.
8. **w의 역할**: `w`는 비교 경계만 이동합니다. CurrentLoad, exposure, recovery, contributor와 symptom override를 곱하거나 줄이지 않습니다.
9. **latestConfirmationDate**는 분석 ZoneId에서 마지막 confirmed workout local date와 마지막 explicitly persisted DailyCheckIn date 중 늦은 날짜입니다.
10. **Confirmed workout와 DailyCheckIn은 다릅니다.** Check-in은 anchor만 전진할 수 있고 exercise/exposure가 아니며 no-workout gap을 끊지 않습니다.
11. **최근 일곱 달력 날짜**는 anchor A의 `A-6 ... A`이고 PersonalBaseline, span, exposure에서 제외합니다. `A-7`은 eligible이며 recent dates는 CurrentLoad에는 포함됩니다.
12. **일곱 sessions는 금지**합니다. Session, record, exercise date 또는 non-empty date 7개가 아니라 연속 calendar dates 7개입니다.
13. **Global no-workout gap**은 confirmed set이 없는 연속 날짜입니다. 0~6일 include/1.0, 7~13일 exclude/older 1.0, 14~27일 exclude/older 0.5, 28+일 exclude/discard older입니다.
14. **Per-unit no-exposure gap**은 그 unit의 positive production exposure가 없는 연속 날짜입니다. 0~13일 include/1.0, 14~27일 exclude/older 0.5, 28+일 exclude/discard older입니다.
15. **Same-boundary min retention**은 same chronological boundary의 global/unit retention 중 최소값을 한 번만 적용합니다. 0.5와 0.5는 0.25가 아닙니다.
16. **Distinct-boundary cumulative retention**은 서로 다른 medium boundary를 누적할 수 있어 오래된 segment가 0.25가 될 수 있으며 retention 0에서 traversal을 멈춥니다.
17. **weightedValidObservationDays**는 first positive unit exposure부터 cutoff까지 retained calendar-date weights의 합입니다.
18. **weightedDistinctExposureDays**는 positive unit exposure가 하나 이상 있는 distinct retained local dates의 weight 합입니다. 같은 날 여러 exercise/session/set/dimension은 한 번입니다.
19. **w_span**은 `w_span = clamp(weightedValidObservationDays / 56, 0, 1)`입니다.
20. **w_exposure**는 `w_exposure = clamp((weightedDistinctExposureDays - 3) / 9, 0, 1)`입니다. 3일은 0, 4일은 1/9, 12일은 1입니다.
21. **w_perUnit**은 `min(w_span, w_exposure)`이며 finite한 0~1 값입니다.
22. **56은 observation days**이고 56 workouts, sessions 또는 exposures가 아닙니다.
23. **Ordinary recovery days**는 실제 개인 residual distribution의 회복 구간이므로 span과 daily samples에 포함합니다.
24. **Long inactivity dates**는 zero residual이 quantile을 인위적으로 낮추지 않도록 excluded gap 자체를 span/sample에서 뺍니다.
25. **28+ reset**은 global gap이면 모든 unit, unit gap이면 해당 unit의 오래된 history를 폐기합니다.
26. **Per-unit/per-dimension identity**에서 dimensions는 하나의 unit w를 공유하고 current/personal residual은 Phase 1 aggregate prior와 같은 `loadUnitStableKey` 합계로 비교합니다.
27. **Historical residual reconstruction**은 각 retained date의 as-of timestamp에서 production calculator를 다시 실행하고 이후 event를 포함하지 않습니다.
28. **Local-hour parity**는 current local hour H의 prior bucket과 과거 sample 평가 hour를 같게 합니다. `LocalDate + ZoneId`로 순회해 DST를 fixed 24-hour milliseconds로 계산하지 않습니다.
29. **meaningfulFloor**는 numerical near-recovery boundary이며 profile-scale/mix 대상이 아닙니다. `CurrentLoad <= floor`는 LOW입니다.
30. **Active weighted daily samples**는 residual이 floor보다 큰 sample만 quantile에 넣되 ordinary recovery date는 span에 계속 포함합니다.
31. **Weighted quantile convention**은 value와 original index로 stable numeric sort한 뒤 cumulative weight가 `totalWeight * probability` 이상인 첫 값을 택하는 deterministic cumulative nearest-rank입니다.
32. **Invalid personal fallback**은 empty/degenerate/non-finite/negative/identity-mismatch/exposure-zero personal을 버리고 exact adjusted prior와 internal diagnostic을 사용합니다.
33. **Linear boundary mixing**은 Q30/Q80/Q95 모두 같은 effectiveW를 씁니다. w=0 prior, w=1 personal, w=0.5 exact midpoint이며 ordering 실패를 임의 sort하지 않습니다.
34. **Q30/Q80/Q95 classification**은 `load <= floor` 또는 `<Q30` LOW, `==Q30..<Q80` MODERATE, `==Q80..<Q95` HIGH, `>=Q95` VERY_HIGH입니다.
35. **User profile inputs**은 bodyweight, strength years, separate racket years와 habitual intensity이며 missing input은 neutral입니다.
36. **Body-mass coefficient**는 Phase 1 fit에서 현재 모든 profile이 zero여서 prior 추가 효과가 neutral입니다. 체중의 production exposure 역할을 제거한다는 뜻이 아닙니다.
37. **Strength/racket experience relevance**는 generated profile relevance와 five-band score를 사용하며 relevance 0 domain은 effect가 없습니다.
38. **Habitual intensity**는 persistent profile description이고 session RPE, recent mean RPE, volume, OFI 또는 residual에서 유도하지 않습니다.
39. **Profile multiplier caps**는 normal 0.85~1.15, absolute 0.80~1.20이며 prior quantiles만 이동합니다.
40. **Baseline provenance footer**는 relevant effectiveW가 모두 epsilon상 0이면 prior-only, 모두 1이면 personal-only, 나머지는 mixed입니다. Untouched synthetic unit은 mixed를 강제하지 않습니다.
41. **No calibration percentage**: w, span과 exposure sufficiency는 adaptation/accuracy/capacity percentage가 아니며 `보정 중`은 짧은 history의 정상 primary state가 아닙니다.
42. **Symptom override separation**: CAUTION은 최소 HIGH, BLOCK은 최소 VERY_HIGH지만 CurrentLoad, floor, boundaries, w와 provenance는 바꾸지 않습니다.
43. **Not diagnosis**: 결과는 injury probability, damage, capacity, safe-load 보증 또는 exact recovery completion이 아닙니다.

## 8. 집계 방식

Gap 날짜 수는 last active date 다음 날부터 next active date 전날까지 양끝을 포함합니다. Anchor가 7월 17일이면 recent exclusion은 7월 11~17일이고 cutoff 7월 10일부터 eligible입니다.

Newest retained segment부터 역순으로 최대 56 weighted days를 모읍니다. 같은 boundary의 global/unit penalty는 min 한 번, distinct boundary는 cumulative입니다. Current dimension events는 Phase 1 prior와 호환되는 unit aggregate로 합하고 unit의 모든 dimensions가 같은 weight를 공유합니다.

## 9. 출력과 UI 해석

정확한 label은 `낮은 편`, `평소 범위`, `높은 편`, `매우 높은 편`, genuine authority failure의 `판단 불가`입니다. Old percentile, `보정 중`, `56일 보정 후 표시`와 calibration percentage를 표시하지 않습니다.

화면 마지막에는 다음 중 하나를 한 번만 표시합니다.

- `기준 출처: 조직별 초기 기준`
- `기준 출처: 개인 운동 기록`
- `기준 출처: 조직별 초기 기준·개인 운동 기록 혼합`
- mixed일 때 `조직마다 개인 기록의 반영 정도가 다를 수 있습니다.`

## 10. 예외 및 fallback

한 unit mapping이 없으면 그 unit은 unavailable이고 다른 unit의 prior를 빌리지 않습니다. Registry 전체 parsing/schema/protocol/order validation이 실패하면 deterministic unavailable 결과와 stable diagnostic을 냅니다. Old percentile classifier, temporary threshold와 fabricated personal history는 fallback으로 사용하지 않습니다.

## 11. 개인화 또는 보정

Bodyweight는 existing canonical resolver를 재사용합니다. Strength와 badminton years는 별도 persistent numeric authority입니다. Habitual intensity는 nullable `LIGHT/NORMAL/HARD`이며 missing/unknown backup value는 neutral null입니다. Profile multiplier와 personal history가 CurrentLoad에 중복 적용되지 않습니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. Production M/D/I/C와 recovery authority는 기존 evidence package를 재사용하지만 scenario weights, shared profiles, gap/weight/quantile policy와 experience effects는 직접적인 population effect size가 아닙니다.

## 13. 제품 정책 및 휴리스틱

- 8 canonical scenarios와 13 shared profiles
- 112-day prior simulation, 56-day burn-in, 24 local-hour buckets
- Recent-seven exclusion과 6/7/13/14/27/28 gap boundaries
- 56-day span denominator와 3~12 exposure ramp
- Cumulative nearest-rank quantiles
- Experience contribution와 profile multiplier caps

모두 deterministic하고 testable하지만 biological adaptation percentage로 해석하지 않습니다.

## 14. 알려진 한계

- Prior는 representative product scenario distribution이지 population norm이 아닙니다.
- Shared profile과 gap/weight policy는 measured tissue capacity가 아닙니다.
- Self-entered record, check-in, metadata와 timestamp precision의 품질 한계가 전달됩니다.
- 일부 recovery/mapping은 class-level 또는 low-confidence proxy입니다.

## 15. 현재 구현 상태

- Base prior: `DESIGNED / GENERATED / VALIDATED / RUNTIME_ACTIVE / TESTED`
- Profile adjustment: `IMPLEMENTED / RUNTIME_ACTIVE / TESTED`
- Per-unit history, PersonalBaseline와 w: `IMPLEMENTED / RUNTIME_ACTIVE / TESTED`
- Relative classifier와 provenance UI: `IMPLEMENTED / RUNTIME_ACTIVE / TESTED`
- Runtime simulation: `ABSENT`

## 16. 구현 위치

- `TissueCalibrationHistoryPolicy.kt`: anchor, recent dates, gap segments
- `TissuePerUnitWeightPolicy.kt`: span/exposure/unit weight
- `TissuePersonalBaselinePolicy.kt`: historical sampler와 weighted quantiles
- `TissuePriorRegistry.kt`: app-ready asset parser/lookup
- `TissuePriorAdjustment.kt`: bounded profile adjustment authority
- `TissueEffectiveBaselinePolicy.kt`: linear mixing, classifier, relativeBandPosition
- `ConnectiveTissueAnalysisService.kt`: record/ledger/recovery orchestration
- `TissueCurrentStateServices.kt`: aggregation, override, provenance
- `ConnectiveTissueAnalysisUi.kt`: exact labels와 final footer

## 17. 검증 테스트

- `TissueCalibrationHistoryPolicyTest.kt`: date/anchor/DST/global/unit/combined gap
- `TissuePerUnitWeightPolicyTest.kt`: exact formulas와 same-day exposure
- `TissuePersonalBaselinePolicyTest.kt`: weighted quantiles, floor, future leak, fallback
- `TissueEffectiveBaselineRuntimeTest.kt`: registry, mixing, inclusivity, CurrentLoad invariance
- `TissueAggregationAndRankingTest.kt`, `ConnectiveTissueAnalysisUiTest.kt`: ranking/provenance/UI
- `TrainingDatabaseMigrationTest.kt`, `RecordCsvBackupRestoreTest.kt`, `BackupRestoreImportBehaviorTest.kt`: migration/backup
- `ConnectiveTissueAnalysisPerformanceTest.kt`: short/56-day/multi-gap service timing

## 18. 권위 자산

- `tools/connective-tissue-prior/scenario_catalog.json`
- `tools/connective-tissue-prior/prior_profile_registry.json`
- `tools/connective-tissue-prior/generated/connective_tissue_prior_baselines.json`
- `tools/connective-tissue-prior/generated/generation_manifest.json`
- `tools/connective-tissue-prior/generated/generation_report.md`
- `app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json`

Generator `CT-PRIOR-GENERATOR-1.0.0`, schema `1.0.0`, 77 units, 13 profiles, 24 hours, 936 quantiles입니다. Canonical/app-ready SHA-256는 `52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf`, recovery fingerprint는 `8ab9bc79ce452c6f80870cfb30973291bc85749e0d0538dacf4c6ccf9fbbbf6a`, deterministic checksum은 `5303516c1b972ce3bdf08eaffcd7c5fe6448bfa64c50abae9790d8d81af0c58e`입니다. Generator/simulation code는 APK runtime에 들어가지 않습니다.

## 19. 관련 문서

- [연결조직 overview](CONNECTIVE_TISSUE_OVERVIEW.md)
- [순위와 표시](RANKING_AND_PRESENTATION.md)
- [노출 모델](MSCP_DI_EXPOSURE_MODEL.md)
- [공통 구현 상태](../common/IMPLEMENTATION_STATUS.md)
- [공통 안전 한계](../common/LIMITATIONS_AND_SAFETY.md)

## 20. 변경 이력

- `2.0.0` (2026-07-18): Phase 1 prior를 활성화하고 per-unit calendar segmentation, weight, PersonalBaseline, linear mixing, relative states와 single provenance footer를 구현했습니다.
- `1.1.0` (2026-07-17): Offline prior generation과 bounded profile adjustment의 비활성 Phase 1 계약을 추가했습니다.
- `1.0.0` (2026-07-17): 기존 global percentile runtime을 첫 governed contract로 등록했습니다.
