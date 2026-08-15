# 배드민턴 전이 분류

| Field | Value |
|---|---|
| Protocol ID | BADMINTON-TRANSFER |
| Protocol version | 2.3.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT; explicit canonical transfer authority from v0.5.0.32; Badminton Objective Stimulus V2 from v0.5.0.33; legacy seven-axis runtime retired in v0.5.0.35; explicit objective relation authority from v0.5.0.37 |
| Last audited commit | e2fb51f |
| Evidence profile | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY |

## v0.5.0.37 explicit objective V2 authority

`badminton_objective_relations_v2_authority.csv` is the explicit reviewed
projection consumed by the nine-objective runtime. It contains exactly 280
exercise/objective rows over nine objectives: 278 preserve
`INHERITED_FROM_EXPLICIT_BADMINTON_RELATION_V1` provenance and two preserve the
separate product-approved Pallof provenance. Generation no longer interprets
legacy relation tokens to create current objective rows. No exercise/objective
pair or transfer level was added, removed, or upgraded by this materialization.

## v0.5.0.34 objective presentation and reviewed Pallof decision

The default badminton analysis selection is the complete canonical nine-objective
list in enum order. A manually chosen subset remains supported. Both recent
7-day versus overlapping 28-day-normalized comparison and weekly charts retain
selected zero-valued objectives as semantic legend and accessibility entries;
zero remains numerically zero. The 7-day and 28-day windows, normalization,
coefficients, and overlapping multi-objective accounting are unchanged.

`band_pallof_press` and `cable_pallof_press` now have explicit
`ANTI_ROTATION / SUPPORTIVE` product-owner decisions from
`badminton_objective_review_decisions_2026-08-14.csv`. Their generated rows use
`USER_APPROVED_BADMINTON_OBJECTIVE_2026_08_14`, a nonblank review reason, and
no fabricated legacy evidence key. Existing inherited `DECELERATION` and
`FOOTWORK` relations remain and were not revalidated in this focused decision.

`ex_a8385c4a` (Copenhagen plank) retains its inherited `DECELERATION` and
`FOOTWORK` supportive relations and gains neither `ANTI_ROTATION` nor
`LUNGE_REACH`. Those inherited Copenhagen mappings remain a separate semantic
review debt.

## v0.5.0.35 legacy seven-axis retirement

The historical seven-axis score, coverage summary, coach recommendation, state,
and UI paths were removed. The canonical nine-objective V2 calculator and its
reviewed relations are unchanged and are now the only runtime transfer-analysis
authority. The old share-based coverage calculation was not projected onto the
nine objectives because objective stimulus intentionally overlaps.

## v0.5.0.33 explicit objective stimulus cutover

The canonical objectives are `ACCELERATION`, `DECELERATION`, `FOOTWORK`,
`JUMP_LANDING`, `LUNGE_REACH`, `REACTION`, `CONDITIONING`,
`ROTATION_GENERATION`, and `ANTI_ROTATION`. Relations are objective-specific
and carry `DIRECT`, `SUPPORTIVE`, `GENERAL`, `LOW`, or `NONE` independently.
The generated `badminton_objective_relations.csv` is the runtime authority.

`ROTATION_GENERATION` replaces canonical `ROTATION_POWER`. Compatibility
aliases are accepted only when explicit legacy badminton evidence supports
rotation. `ANTI_ROTATION` is independent: axial bracing, CoreClass, or a core
direct target never grants badminton authority. The machine-readable rotation
audit records every reviewed creation and rejection.

Badminton Objective Stimulus V2 uses one unit per confirmed set, the mild RPE
modifier, and the objective's transfer coefficient: `DIRECT=1.00`,
`SUPPORTIVE=0.60`, `GENERAL=0.25`, `LOW=0.10`, `NONE=0.00`. Kilograms,
repetitions, and seconds are not multipliers. Multi-objective stimulus is
intentionally overlapping and is not divided by objective count, so its sum is
not total physical workload. All nine objectives remain present even at zero.

Historical raw records are replayed through compatible canonical identity
semantics without rewriting stored stableKeys. Objective UI consumers accept
only `BADMINTON_OBJECTIVE_STIMULUS_V2`; there is no fallback to old derived
values or mixed scale.

## v0.5.0.32 canonical source boundary

Visible transfer axes are derived from explicit canonical badminton transfer
types, skill targets, and physical qualities. Generic `FRONTAL` plane,
unilateral structure, shoulder/forearm/back muscle participation, fatigue/load
tags, conditioning role, and generic bracing are not transfer evidence.
Explicit deceleration/landing, footwork/lateral, lunge, rotation, racket, and
anti-rotation relations continue to produce their reviewed axis or objective.

`fatigueCost` remains a separate runtime derivation and retains its original
load inputs and thresholds. The 241-identity audit confirms exact before/after
fatigue cost as well as unchanged OFI, program, and strength classifications.
| Supersedes | — |

`1.1.0`은 현재 동작과 artifact-only stableKey 정본화의 경계를 함께 기록합니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

근력·보조 운동이 배드민턴 수행 목적에 얼마나 직접 연결되는지와 어떤 transfer axis를 지원하는지 분류해 최근 분포를 요약합니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `BADMINTON-TRANSFER`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

확인된 set마다 base unit 1.0에 mild RPE modifier와 해당 objective의 transfer coefficient를 곱합니다. 한 set이 여러 objective를 지원하면 각 objective에 독립적으로 전량 반영하며 균등 분할하지 않습니다.

## 8. 집계 방식

canonical 9 objective별 일간 자극을 합산하고 주간 표시로 집계합니다. objective availability는 값이 0이어도 유지합니다. 중량, 반복수, 초는 이 objective 자극의 multiplier가 아닙니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

v0.4.2.16부터 주별 전이 자극 차트는 주별 배드민턴 훈련량 차트와 동일한 `AnalysisChartTemporalPolicy`를 사용합니다. Monday-Sunday 주의 목요일이 속한 월이 그 주를 소유하며, 같은 `weekStart`는 두 차트에서 항상 같은 compact label과 정확한 날짜 범위를 가집니다. 이 표시 변경은 전이 자극값, taxonomy와 category color를 변경하지 않습니다.

## 10. 예외 및 fallback

명시적 objective relation이 없으면 objective 자극은 0입니다. Generic badminton practice/session은 별도 practice-load 분석에는 남지만 9개 objective로 확산되지 않습니다. 이전 derived scale이나 이름 추측 fallback은 사용하지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

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

- [`app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveAuthority.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveAuthority.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculator.kt)
- [`app/src/main/java/com/training/trackplanner/data/CanonicalExerciseMetadataRepository.kt`](../../../app/src/main/java/com/training/trackplanner/data/CanonicalExerciseMetadataRepository.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeries.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeries.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeriesTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/BadmintonTrainingMethodSeriesTest.kt)
- [`app/src/test/java/com/training/trackplanner/BadmintonObjectiveSelectionStateTest.kt`](../../../app/src/test/java/com/training/trackplanner/BadmintonObjectiveSelectionStateTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveAuthorityTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculatorTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/CanonicalAnalysisAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/CanonicalAnalysisAuthorityTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicyTest.kt)
- [`app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt)

## 18. 권위 자산

- [`app/src/main/assets/metadata/canonical_v1/badminton_objective_relations.csv`](../../../app/src/main/assets/metadata/canonical_v1/badminton_objective_relations.csv)
- [`docs/metadata_authority/badminton_objective_review_decisions_2026-08-14.csv`](../../metadata_authority/badminton_objective_review_decisions_2026-08-14.csv)
- [`docs/audits/core_badminton_rotation_objective_audit.csv`](../../audits/core_badminton_rotation_objective_audit.csv)
- [`tools/metadata_authority/analysis_cutover_authority.py`](../../../tools/metadata_authority/analysis_cutover_authority.py)

## 19. 관련 문서

- [`docs/metadata_evidence_sources_v0.3.5.0.md`](../../metadata_evidence_sources_v0.3.5.0.md)
- [`docs/protocols/data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md`](../data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md): 현재 transfer/quality/fatigue-cost 출력을 stableKey별 shadow relation으로 동결합니다.
- [`outputs/badminton_transfer_closeout/badminton_transfer_closeout_v1.md`](../../../outputs/badminton_transfer_closeout/badminton_transfer_closeout_v1.md): 241개 canonical identity 정합성 결과와 남은 검토 항목입니다.
- [`outputs/badminton_transfer_closeout/badminton_transfer_relation_normalization_v1.md`](../../../outputs/badminton_transfer_closeout/badminton_transfer_relation_normalization_v1.md): source relation과 runtime-derived axis의 정규화 준비 경계입니다.
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `2.2.0` (2026-08-15): legacy seven-axis runtime/coverage/recommendation authority를 제거하고 nine-objective V2를 sole runtime authority로 고정했습니다.
- `1.1.0` (2026-08-04): 최신 241-row identity에 source metadata를 정합하고 source/runtime/display axis 층을 명시했습니다. runtime 계산은 변경하지 않았습니다.
- `1.0.1` (2026-07-19): v0.4.2.16에서 주별 훈련량과 전이 자극이 동일한 월-주차 표시 권한을 사용하도록 문서화했습니다. 전이 계산과 색상은 변경하지 않았습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.

## 21. source metadata와 retired seven-axis의 경계

배드민턴 전이는 다음 세 층을 구분합니다.

1. canonical runtime authority: explicit nine-objective relations and their per-objective transfer levels
2. practice-load authority: badminton session duration/RPE classification, independent from objective stimulus
3. historical-only layer: the removed mapper and seven derived display axes retained only in Git history and audit artifacts

The historical layer is not a fallback. Runtime code does not derive or display
the seven retired axes, and it does not convert their old coverage shares into
the overlapping nine-objective V2 scale.

## 22. artifact-only identity closeout

- 224개 기존 source row를 최신 241개 canonical exercise identity에 대조했습니다.
- 208개 retained identity는 기존 값을 유지했습니다.
- 33개 equipment-specific identity는 generic source의 넓은 배드민턴 전이 의미를 명시적 lineage와 함께 상속했습니다.
- historical generic identity는 다시 selectable하게 만들지 않았고, legacy alias는 migration artifact에서만 canonical identity로 연결했습니다.
- legacy movement field 의존성은 별도 audit에 남겼으며 runtime mapper를 교체하지 않았습니다.
- canonical code와 한국어 display/definition은 분리했습니다. 미승인 한국어 정의는 `REVIEW_REQUIRED`입니다.

이 closeout은 `ARTIFACT_ONLY`입니다. transfer weight, RPE factor, 7일/28일 window, equal axis split, fatigue cost, 추천, 차트, 색상, UI를 변경하지 않습니다.

## 23. 변경 이력

- `2.3.0` (2026-08-15): materialized the existing 280-row, nine-objective V2 relationship set as explicit authority and retired legacy relation-token inference without numerical change.
