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

## 1. 제품 의도

WhatYouGottaDo의 기본 화면은 장식보다 기록, 현재 상태와 다음 행동을 빠르게 읽게 해야 합니다. `v0.5.0.0`의 방향은 조용하고 깨끗한 훈련·분석 제품이며, 색·카드·아이콘은 정보 구조를 보조할 때만 사용합니다.

## 2. 하단 내비게이션

하단 탭은 한 Material outlined icon family와 항상 보이는 한국어 라벨을 사용합니다.

| 탭 | 라벨 | 아이콘 의미 |
|---|---|---|
| Home | 홈 | home |
| Record | 기록 | edit note |
| Plan | 계획 | event note |
| Exercise | 운동 | fitness center |
| Analysis | 분석 | analytics |

선택 탭만 하나의 brand color와 옅은 indicator로 구분합니다. 미선택 탭은 `onSurfaceVariant`를 사용하며 탭별 고유색이나 과한 animation을 두지 않습니다.

## 3. 표면 계층

- 한 섹션에는 원칙적으로 하나의 의미 있는 card/surface만 둡니다.
- 안쪽 항목은 행, 구분선, 여백과 짧은 action으로 구성합니다.
- 안내 문장은 별도 큰 카드보다 중립 텍스트를 우선합니다.
- Home의 요약 숫자, Analysis 허브 항목, 연결조직 하위 조직, 펼친 분석 상세는 중첩 카드 대신 행 구조를 사용합니다.
- 카드 radius의 기본값은 `8dp`이며 큰 장식용 둥근 상자를 만들지 않습니다.

## 4. OFI 기본 요약

OFI 기본 요약은 overall 상태 다음에 canonical 다섯 축을 다음 순서의 텍스트 행으로 표시합니다.

1. 고중량·힘 신경계
2. 전신 근육
3. 국소 근육
4. 고속
5. 반응

각 행은 축 이름과 현재 상태 라벨만 기본으로 보여 줍니다. 낮음·보통은 중립색이며 높음·매우 높음만 경고색과 굵기로 강조합니다. 큰 진행 막대, 축마다 다른 색, 반복 아이콘, contributor chip이나 긴 기여 운동 목록은 기본 요약에 넣지 않습니다. 기여 운동은 기존 펼친 상세 경로에 남깁니다.

이 정책은 OFI 계산, threshold, canonical classifier, projected OFI 또는 축 상태 의미를 변경하지 않습니다.

## 5. 색·글꼴·간격

- light/dark 모두 중립 background와 surface를 기본으로 사용합니다.
- brand tint는 주요 시작 action과 선택 상태처럼 우선순위가 높은 곳에만 사용합니다.
- warning accent는 실제 elevated state에만 사용합니다.
- headline, section title, body와 helper text는 Material typography hierarchy를 유지하며 작은 글꼴로 밀도를 억지로 높이지 않습니다.
- 주요 화면은 `20dp` 가로 inset과 `16dp` section spacing을 기본으로 사용합니다.
- 선택 chip은 Material `FilterChip` semantics와 중립 selected surface를 공유합니다.

## 6. Home과 Analysis

Home은 기록 시작, 프로그램 진입, 현재 상태 요약을 먼저 보여 주는 launchpad입니다. 깊은 분석 내용을 추가하지 않습니다.

Analysis는 피로도, 배드민턴, 근력, 연결조직과 실험실 진입점을 행 목록으로 묶고, 세부 화면에서만 상세 차트와 기여 정보를 펼칩니다.

## 7. 접근성과 비목표

- 한국어 라벨, TalkBack content description, platform selection semantics와 touch target을 유지합니다.
- 큰 글꼴에서는 텍스트 축소 대신 wrapping과 scroll을 허용합니다.
- dark theme의 foreground/background 대비를 유지합니다.
- 이 릴리스는 bitmap, 장식 배너, anatomy illustration, 배경 사진 또는 상시 animation을 추가하지 않습니다.

## 8. 구현 위치

- `app/src/main/java/com/training/trackplanner/MainActivity.kt`
- `app/src/main/java/com/training/trackplanner/CommonUi.kt`
- `app/src/main/java/com/training/trackplanner/HomeScreen.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisScreen.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisHubUi.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt`
- `app/src/main/java/com/training/trackplanner/ConnectiveTissueAnalysisUi.kt`
- `app/src/main/java/com/training/trackplanner/ui/theme/Theme.kt`

## 9. 검증

- `app/src/test/java/com/training/trackplanner/BottomNavigationUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AnalysisHubUiTest.kt`
- `app/src/test/java/com/training/trackplanner/CurrentFatigueStatusCardUiTest.kt`
- `app/src/test/java/com/training/trackplanner/ConnectiveTissueAnalysisUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AppExplanationUiTest.kt`

## 10. 변경 이력

- `1.0.0` (2026-07-19): v0.5.0.0의 quiet refinement, 하단 내비게이션, 단일 표면 계층, OFI 텍스트 요약과 restrained emphasis를 처음 등록했습니다.
