# 연결조직 순위와 표시

| Field | Value |
|---|---|
| Protocol ID | CT-RANKING-PRESENTATION |
| Protocol version | 2.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.8; relative presentation active from v0.4.2.12 |
| Last audited commit | 0974433ef5e21f4a5f2dee8a3bbe72418d9c3f9f |
| Evidence profile | PRODUCT_POLICY, USER_APPROVED_POLICY |
| Supersedes | CT-RANKING-PRESENTATION 1.0.0 |

## 1. 일반 사용자용 요약

분석 화면은 joint complex와 세부 load unit을 분리하고 상대 상태, contributor와 교육 설명을 제공합니다. 개인 이력이 짧아도 조직별 초기 기준을 사용하며 baseline source는 화면 아래에서 한 번만 알립니다.

## 2. 목적

Mixed baseline의 상태, internal ranking, joint aggregation, symptom override와 user-facing presentation을 오해 없이 재현할 수 있게 정의합니다.

## 3. 적용 범위

Connective-tissue summary, top-three joint, expanded child rows, contributor, educational dialog, diagnostics와 final provenance footer에 적용합니다.

## 4. 비적용 범위

Baseline 계산 자체, OFI 표시, Home card 추가, injury risk score, tissue capacity score, 진단과 치료 권고는 포함하지 않습니다.

## 5. 용어

`Canonical status`는 LOW/MODERATE/HIGH/VERY_HIGH/UNAVAILABLE이고 `relativeBandPosition`은 내부 tie-break 위치입니다. `Provenance`는 관련 unit의 effectiveW 분포에서 얻은 prior-only/personal-only/mixed source입니다.

## 6. 입력 데이터

UI는 전체 registry나 calibration percentage를 받지 않습니다. Domain aggregation이 만든 canonical status, educational info, contributor, recovery range, diagnostics와 explicit provenance presentation model만 받습니다.

## 7. 계산 또는 분류 계약

정확한 표시 label은 다음과 같습니다.

| Domain state | 표시 |
|---|---|
| `LOW` | `낮은 편` |
| `MODERATE` | `평소 범위` |
| `HIGH` | `높은 편` |
| `VERY_HIGH` | `매우 높은 편` |
| `UNAVAILABLE` | `판단 불가` |

짧은 개인 history는 valid prior로 분류하므로 `보정 중`이 아닙니다. `판단 불가`는 prior/mapping 등 genuine authority failure에만 사용합니다.

## 8. 집계 방식

Severity를 먼저 비교하고 동률은 `relativeBandPosition`, raw residual, latest contribution, stable key 순으로 정렬합니다. Joint complex는 worst child 상태를 사용합니다. Contributor는 한 unit에서 1위가 2위의 1.5배 이상이면 하나, 아니면 상위 2개이며 joint도 상위 2개입니다.

## 9. 출력과 UI 해석

기존 personal percentile `normalizedScore`는 보편적 mixed-baseline 점수가 아니므로 표시하지 않습니다. `56일 보정 후 표시`, calibration percentage, injury-risk/capacity percentage와 universal 0~100 score도 없습니다. Summary supporting text는 `관절·건·인대 등 연결조직에 남아 있을 상대적인 운동 부하를 확인합니다.`입니다.

## 10. 예외 및 fallback

Trend가 유효하지 않으면 placeholder를 만들지 않고 생략합니다. Genuine unavailable은 `판단 불가`로 표시합니다. Empty contributor와 top area는 기존 neutral no-data resource를 사용합니다.

## 11. 개인화 또는 보정

Prior-only, personal-only, mixed 여부는 domain이 explicit epsilon으로 판정합니다. Untouched catalogue unit과 synthetic `UNOBSERVED` state는 mixed provenance를 강제하지 않으며 relevant unit이 없으면 prior-only입니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, USER_APPROVED_POLICY`입니다. 상태 문구와 화면 계층은 제품 표시 계약이며 임상 분류가 아닙니다.

## 13. 제품 정책 및 휴리스틱

Internal `relativeBandPosition`은 floor 이하 0, floor~Q30 0~1, Q30~Q80 1~2, Q80~Q95 2~3, Q95 초과 bounded monotonic excess입니다. Finite/deterministic ranking 전용이며 percentile이나 risk percentage가 아닙니다.

## 14. 알려진 한계

- Relative labels는 modeled residual을 비교 경계에 놓은 결과입니다.
- Educational text는 일반 정보이며 개인 증상이나 임상 검사를 대체하지 않습니다.
- Diagnostics는 개발/모델 한계를 보조하며 baseline source warning이 아닙니다.

## 15. 현재 구현 상태

- Relative labels: `IMPLEMENTED / RUNTIME_ACTIVE / TESTED`
- Severity-first ranking와 worst-child aggregation: `IMPLEMENTED / TESTED`
- Single final provenance footer: `IMPLEMENTED / TESTED`
- Old displayed percentile/calibration placeholder: `REMOVED`

## 16. 구현 위치

- `TissueEffectiveBaselinePolicy.kt`: classifier와 relativeBandPosition
- `TissueCurrentStateServices.kt`: ranking, joint aggregation, contributor, override, provenance
- `TissueAnalysisUiModels.kt`: Compose-safe model
- `ConnectiveTissueAnalysisUi.kt`: resources, expansion/dialog와 footer

## 17. 검증 테스트

- `TissueEffectiveBaselineRuntimeTest.kt`: exact boundaries와 internal position
- `TissueAggregationAndRankingTest.kt`: severity, worst child, contributor와 provenance
- `ConnectiveTissueAnalysisUiTest.kt`: exact text, footer order/count, large font, dark theme, expansion/dialog

## 18. 권위 자산

- `tissue_rcv_display_contract_v1.csv`
- `tissue_rcv_educational_info_v1.csv`
- `connective_tissue_prior_baselines_v1.json`

## 19. 관련 문서

- [개인 기준과 상대 상태](PERSONAL_CALIBRATION.md)
- [연결조직 overview](CONNECTIVE_TISSUE_OVERVIEW.md)
- [공통 안전 한계](../common/LIMITATIONS_AND_SAFETY.md)

## 20. 변경 이력

- `2.0.0` (2026-07-18): prior/personal relative labels, internal band ranking, old percentile removal과 single provenance footer를 활성화했습니다.
- `1.0.0` (2026-07-17): 첫 governed presentation contract를 등록했습니다.
