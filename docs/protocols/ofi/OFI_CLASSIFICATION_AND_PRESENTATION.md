# OFI 분류와 표시

| Field | Value |
|---|---|
| Protocol ID | OFI-CLASSIFICATION |
| Protocol version | 1.1.1 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.15 |
| Last audited commit | 60e21c6b847f1dc2910ddbdc5ee2d4690631cb9e |
| Evidence profile | USER_APPROVED_POLICY, PRODUCT_POLICY |
| Supersedes | — |

`1.1.0`은 canonical 다섯 축의 분류와 표시 순서를 바로잡은 계약입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

OFI canonical classifier와 축별 경고는 서로 다른 역할을 가집니다. 종합 라벨은 OFI만 결정하고 개별 축 상태가 이를 덮어쓰지 않습니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `OFI-CLASSIFICATION`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

확인된 기록과 effective runtime metadata를 사용합니다. 입력이 protocol별로 제한될 때는 아래 계산 계약과 authority asset이 그 범위를 결정합니다.

## 7. 계산 또는 분류 계약

OFI 상태는 0~39 `LOW`, 40~74 `NORMAL`, 75~86 `ELEVATED`, 87~97 `CAUTION`, 98~100 `HIGH_FATIGUE`입니다. 표시 라벨은 `피로도 낮음`, `피로도 보통`, `피로도 주의`, `피로도 높음`, `피로 심화`입니다. 축 상태는 100 매우 높음, 84~99 높음, 72~83 상승, 35~71 보통, 0~34 낮음입니다.

## 8. 집계 방식

매우 높음 축이 하나 이상이면 그 축만 화면 순서대로 나열합니다. 없으면 높음 축만 나열하며 둘 다 없으면 양호 문구를 표시합니다. 개수 문장은 매우 높음, 높음, 보통, 낮음 순서를 유지합니다.

화면 순서는 `고중량·힘 신경계`, `전신 근육`, `국소 근육`, `고속`, `반응`입니다. 연결조직과 회복 지속은 이 다섯 카드에 포함하지 않습니다.

## 9. 출력과 UI 해석

제목은 `현재 상태: <score> · <canonical label>`입니다. 매우 높음은 `<축> 피로도가 높습니다. 주의하세요.`, 높음은 `<축> 피로도가 높습니다. 스트레스를 줄이면 좋습니다.`, 없으면 `모든 피로도가 양호합니다. 힘차게 운동!`을 사용합니다.

분석 화면의 OFI 시계열은 v0.4.2.16부터 `누적 부담 흐름`으로 표시하고 실제 선택된 rolling 분석 기간의 시작·종료 날짜와 일별 날짜 축을 노출합니다. 이는 Monday-Sunday calendar week로 계산을 변경하지 않으며, 기존 OFI 값, 회복, 축 값과 상태 분류를 그대로 사용합니다.

## 10. 예외 및 fallback

필수 입력이 없으면 현재 코드의 명시된 빈 결과 또는 보수적 기본 경로를 사용하며 값을 추정해 만들지 않습니다.

## 11. 개인화 또는 보정

개인 기록을 사용하는 경우 현재 runtime의 history 범위와 우선순위를 그대로 적용합니다.

## 12. 연구 근거

Evidence profile은 `USER_APPROVED_POLICY, PRODUCT_POLICY`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 현재 감사 범위에서 별도 미해결 runtime gap을 확인하지 않았습니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [`app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabeler.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactory.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactory.kt)
- [`app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/AnalysisChartTemporalPolicy.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt)
- [`app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt)

## 17. 검증 테스트

- [`app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/readiness/TodayFatigueStatusLabelerTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/fatigue/HomeFatigueCardSummaryFactoryTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/readiness/FatiguePresentationMapperTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/readiness/FatiguePresentationMapperTest.kt)
- [`app/src/test/java/com/training/trackplanner/analysis/fatigue/FatigueAnalysisMapperTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/fatigue/FatigueAnalysisMapperTest.kt)
- [`app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisChartTemporalUiTest.kt)

## 18. 권위 자산

- 별도 authority asset 없이 source와 tests가 계약을 고정합니다.

## 19. 관련 문서

- [`docs/v0.4.2.5_release_notes.md`](../../v0.4.2.5_release_notes.md)
- [`docs/v0.4.2.6_release_notes.md`](../../v0.4.2.6_release_notes.md)
- [`docs/v0.4.2.15_release_notes.md`](../../v0.4.2.15_release_notes.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.1.1` (2026-07-19): v0.4.2.16에서 rolling OFI chart의 실제 날짜 범위와 일별 시간축 표시 계약을 추가했습니다. OFI 계산과 분류는 변경하지 않았습니다.
- `1.1.0` (2026-07-18): OFI 카드와 Home/Analysis 요약을 canonical 다섯 축으로 통일했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
