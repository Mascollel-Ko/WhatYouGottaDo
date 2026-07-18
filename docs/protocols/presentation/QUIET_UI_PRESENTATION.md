# 조용한 UI 표시 원칙

| Field | Value |
|---|---|
| Protocol ID | UI-QUIET-PRESENTATION |
| Protocol version | 1.0.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.0 |
| Last audited commit | 7c29fa80b31fad642273e0a3ec5924109dafac21 |
| Evidence profile | USER_APPROVED_POLICY, PRODUCT_POLICY |
| Supersedes | — |

## 1. 일반 사용자용 요약

WhatYouGottaDo의 기본 화면은 장식보다 기록, 현재 상태와 다음 행동을
빠르게 읽게 합니다. 색, 카드와 아이콘은 정보 구조를 보조할 때만
사용합니다.

## 2. 목적

`v0.5.0.0`부터 적용되는 조용하고 깨끗한 훈련·분석 화면의 공통 표시
원칙을 정의합니다.

## 3. 적용 범위

- 하단 내비게이션
- Home과 Analysis의 최상위 정보 계층
- OFI 기본 요약
- 연결조직 분석을 포함한 공용 card, row, chip, spacing과 typography

## 4. 비적용 범위

- OFI, 연결조직, 근력, 배드민턴 또는 ProgramBuilder 계산
- 기록, 백업, 복원과 stable key 동작
- bitmap, anatomy illustration, 장식 banner와 배경 사진

## 5. 용어

- `quiet surface`: 중립색과 제한된 강조를 사용하는 표면
- `section card`: 하나의 의미 단위를 감싸는 최상위 card
- `inner row`: section card 안에서 추가 card 없이 정보를 표시하는 행
- `elevated state`: OFI 축의 높음 또는 매우 높음 상태

## 6. 입력 데이터

이 정책은 기존 화면 상태, 선택 상태, OFI overall 상태와 canonical 다섯
축 상태를 표시 입력으로 사용합니다. 새로운 계산 입력은 만들지 않습니다.

## 7. 계산 또는 분류 계약

이 문서는 계산 또는 분류 공식을 정의하지 않습니다. OFI threshold,
canonical classifier, projected OFI와 축 상태 의미는 기존 계산 결과를
그대로 사용합니다.

## 8. 집계 방식

화면은 계산 결과를 재집계하지 않습니다. Home은 시작 행동과 현재 상태를
요약하고, Analysis는 관련 진입점을 section별 행으로 묶습니다. 상세 값과
기여 운동은 기존 상세 경로에서만 펼칩니다.

## 9. 출력과 UI 해석

하단 탭은 한 Material outlined icon family와 항상 보이는 한국어 라벨을
사용합니다.

| 탭 | 라벨 | 아이콘 의미 |
|---|---|---|
| Home | 홈 | home |
| Record | 기록 | edit note |
| Plan | 계획 | event note |
| Exercise | 운동 | fitness center |
| Analysis | 분석 | analytics |

선택 탭만 하나의 brand color와 옅은 indicator로 구분합니다. 미선택 탭은
중립색을 사용합니다.

OFI 기본 요약은 overall 상태 다음에 canonical 다섯 축을
`고중량·힘 신경계`, `전신 근육`, `국소 근육`, `고속`, `반응` 순서의
텍스트 행으로 표시합니다. 낮음·보통은 중립색이며 높음·매우 높음만
경고색과 굵기로 강조합니다.

## 10. 예외 및 fallback

긴 라벨이나 큰 글꼴은 글자를 줄이지 않고 wrapping 또는 기존 scroll
container로 처리합니다. 아이콘을 표시할 수 없어도 한국어 탭 라벨과
content description이 의미를 유지합니다.

## 11. 개인화 또는 보정

사용자의 dark theme, 글꼴 크기와 platform 접근성 설정을 그대로 따릅니다.
사용자별로 계산식이나 상태 의미를 보정하지 않습니다.

## 12. 연구 근거

이 정책은 임상 또는 생리학 연구 주장이 아니라 사용자 승인 제품 정책과
Material platform 관례에 근거합니다.

## 13. 제품 정책 및 휴리스틱

- 한 섹션에는 원칙적으로 하나의 의미 있는 card/surface만 둡니다.
- 안쪽 항목은 행, 구분선, 여백과 짧은 action을 우선합니다.
- 일반 background와 surface는 중립색을 사용합니다.
- brand tint는 주요 시작 action과 선택 상태에만 제한합니다.
- warning accent는 실제 elevated state에만 사용합니다.
- 주요 화면은 `20dp` 가로 inset과 `16dp` section spacing을 기본으로
  사용합니다.
- card radius 기본값은 `8dp`이며 장식용 큰 둥근 상자를 만들지 않습니다.
- 선택 chip은 Material `FilterChip` semantics를 유지합니다.
- 큰 진행 막대, 축마다 다른 색, 반복 아이콘과 contributor dump를 OFI
  기본 요약에 넣지 않습니다.

## 14. 알려진 한계

- 화면별 오래된 전용 layout은 공용 component를 사용하지 않을 수 있습니다.
- screenshot golden test 체계는 없으며 Compose semantics와 layout 테스트로
  주요 회귀를 보호합니다.

## 15. 현재 구현 상태

- 하단 내비게이션 아이콘과 한국어 라벨: 구현됨
- Home과 Analysis의 중첩 card 축소: 구현됨
- canonical 다섯 축 OFI text summary: 구현됨
- neutral light/dark palette, typography, shape와 spacing: 구현됨
- 큰 글꼴과 dark theme focused Compose 검증: 구현됨

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/MainActivity.kt`
- `app/src/main/java/com/training/trackplanner/CommonUi.kt`
- `app/src/main/java/com/training/trackplanner/HomeScreen.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisScreen.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisHubUi.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt`
- `app/src/main/java/com/training/trackplanner/ConnectiveTissueAnalysisUi.kt`
- `app/src/main/java/com/training/trackplanner/ui/theme/Theme.kt`

## 17. 검증 테스트

- `app/src/test/java/com/training/trackplanner/BottomNavigationUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AnalysisHubUiTest.kt`
- `app/src/test/java/com/training/trackplanner/CurrentFatigueStatusCardUiTest.kt`
- `app/src/test/java/com/training/trackplanner/ConnectiveTissueAnalysisUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AppExplanationUiTest.kt`

## 18. 권위 자산

- 이 canonical 문서
- `docs/protocols/protocol_registry.json`
- 현재 Material Compose theme와 shared UI 구현

## 19. 관련 문서

- [프로토콜 라이브러리](../README.md)
- [OFI 분류와 표시](../ofi/OFI_CLASSIFICATION_AND_PRESENTATION.md)
- [v0.5.0.0 릴리스 노트](../../v0.5.0.0_release_notes.md)

## 20. 변경 이력

- `1.0.0` (2026-07-19): v0.5.0.0 quiet refinement, 하단 내비게이션,
  단일 표면 계층, OFI 텍스트 요약과 restrained emphasis를 등록했습니다.
