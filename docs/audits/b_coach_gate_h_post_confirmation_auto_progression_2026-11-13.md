# B-Coach Gate H — 확인한 운동기록에서 다음 주 계획을 자동으로 갱신

작성일: 2026-11-13  
상태: **시험 브랜치에만 구현 — main 미반영.** 스쿼트 한정. 신뢰성이 검증되지 않은 Gate F 순위식은 변경하지 않음.

## 이번 실사용 오류

기존 Gate G에서는 기록의 세트 확인이 완료되어도 B-Coach가 호출되지 않았다. 사용자가 **다음 주 날짜**의 아직 확인되지 않은 스쿼트를 별도로 열어야만 Gate G 후보가 평가되었고, 그것도 별도 승인 버튼이 필요했다. 따라서 사용자가 135kg×6회×3세트를 모두 확인하고 RPE 8을 입력해도 다음 주 기록은 그대로 남았다. 이 현상은 기존 앱의 설계 누락이다.

## Gate H 실제 호출 경로

```
RecordScreen 세트 확인/RPE 편집
 -> TrainingViewModel.updateSet(RecordSetEdit)
 -> TrainingRepository.updateSet
 -> RecordMutationService DB 확정
 -> RecordDerivedRefreshCoordinator.afterCommit (기존 경로 유지)
 -> TrainingViewModel.recheckBCoachAfterConfirmedSquat
 -> TrainingRepository.progressBCoachAfterConfirmedSquat
 -> 실제 확인된 세트와 프로그램 링크 검사
 -> StrengthPosteriorUpdateCoordinator.ensureCurrentRevision()
 -> BCoachLinkedSquatNextWeekService.inspect (Gate F 곡선 순위)
 -> 보호 검증 및 현재 증거 지문 재확인
 -> BCoachLinkedSquatNextWeekService.approve(..., automaticallyAfterConfirm = true)
 -> 다음 주 미완료 WorkoutSet / ProgramPrescriptionSet 갱신
 -> ProgressionSuggestion ACCEPTED 감사 기록
 -> 현재 스쿼트 기록 카드에 적용 결과 표시
```

실제 기록을 변경한 뒤 별도 날짜의 기록 화면까지 이동할 필요가 없도록 했다. 사용자 의도가 명확한 **B-Coach 적용 프로그램** 내에서만 후속 주차에 적용한다.

## 판단과 제약

- 스쿼트의 1주차 실제 확인 기록 **135kg×6회×3세트, 세트별 RPE 8**은 전부 확정되었다면 후속 평가의 입력이 된다.
- RPE 8이므로 무조건 중량이 증가하는 것은 아니다. Gate F가 HOLD를 1순위로 정하면 **다음 주 중량을 135kg으로 채우고 동일 반복·세트로 유지한다.** 빈 중량이 채워지는 것도 진행 결과다.
- 1순위 후보는 기존 PCHIP 상대 중량·반복곡선으로 계산하는 목표 RIR·e1RM 오차의 최소최대후회 비교를 재사용한다. 수동으로 LOAD_FIRST / REPS_FIRST를 고를 필요가 없다.
- 실제 전체 세트를 확인했고 각각 RPE가 유효(6~10)하며 중량·반복이 명시되어야 한다. RPE 미입력은 임의로 추정하지 않는다.
- 가장 최근 정식 근력 추정 상태가 방금 확인한 세트를 포함하도록 갱신되어야 한다. 실패하면 자동 변경하지 않고 이유를 표시한다.
- 같은 적용 프로그램의 정확한 다음 주차·같은 요일·같은 squat stableKey인 대상이 **단 하나**여야 한다. 모호하거나 없는 경우 자동 변경하지 않는다.
- 이전 주차 성공 여부는 실제 기록과 원래 프로그램 처방을 비교해 Gate G에서 다시 검사한다. 이미 시작한 미래 기록이나 사용자가 수정한 미래 목표는 절대로 덮어쓰지 않는다.
- **새 Gate G 코드에서 생성된 B 프로그램**에 저장된 현재 원판 규격 스냅샷을 사용한다. 스냅샷이 없는 과거 프로그램은 임의의 원판 간격을 추정하지 않는다.
- 기존 진행 제안에 이미 명시적인 사용자 결정을 내렸다면 존중한다. 같은 대상에 성공한 자동 적용은 한 번만 이루어지고 `B_COACH_GATE_H_AUTO_AFTER_CONFIRM` 사유로 감사 기록에 남긴다.
- 세트 수를 바꾸는 후보가 1순위여도 Gate G에서 미래 세트를 임의 추가/삭제하지 않는다. 모호한 후보는 보류로 처리한다.
- **다른 운동, 전 주차 일괄 수정, 실패한 세션의 하향 조정, 전체 프로그램 회복 재최적화**는 이번 범위가 아니다.

## 결과가 안 보일 때

이제 **확인한 스쿼트 카드 자체**에 다음 주 자동 반영 결과가 나타난다. 실패 시 연결 부재, 다음 주차 없음, 과거 프로그램 원판 정보 누락, 최신 근력 계산 지연, 미완료/RPE 부족, 미래 계획 수동 편집 등을 한국어로 설명한다. 동일 카드에 '다음 주 B-Coach 재평가' 버튼도 마련했다. 이는 실패를 무조건 증량으로 강제하는 기능이 아니라, 현재 저장된 기록으로 재검사하는 동작이다.

다음 주차로 이동해서 적용된 **중량·반복·세트**를 별도로 확인한다. '성공'이라고 표시돼도 실제 이력·수동 편집 보호 규칙과 충돌하는 경우 자동 변경을 보류한다.

## 검증

`BCoachPostConfirmLinkResolverTest`: 135kg×6×3 RPE8, 마지막 세트 미확인, RPE 누락/중량 누락, 적용 프로그램 연결 부재, 다른 적용/요일/주차, 같은 주차 대상 중복, 미래 기록 일부 확인, 과거 날짜, 다른 종목 혼입을 검사한다.

기존 Gate A–G focused 회귀 테스트에 이 테스트를 포함하여, CI가 컴파일·시험하고 기존 서명키로 디버그/성능 APK를 만든다. CI 성공은 코드 컴파일 및 테스트 통과의 의미이고 실제 기기·사용자 데이터의 모든 경우를 보증하지 않는다.
