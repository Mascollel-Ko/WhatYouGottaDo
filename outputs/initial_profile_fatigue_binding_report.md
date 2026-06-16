# Initial Profile Fatigue Binding Report

## 수정 파일

- `InitialProfileReadinessAdjuster.kt`

## 변경

- cold-start readiness 보정에서 구조화 필드를 우선 사용한다.
- `usualSleepHours`, `currentCondition`, `trainingBreakCategory`, `trainingBreakReason`, `painAreaTags`를 사용한다.
- 자유 텍스트 `painAreas`, `goals`, `strengthTrainingAge`, `badmintonTrainingAge`는 핵심 피로 보정에 사용하지 않는다.

## 보수 처리

- 초기 프로필이 없어도 crash 없이 기존 readiness 결과를 사용한다.
- 구조화 값이 부족하면 기존 기록과 daily metric 기반으로만 판단한다.
- 내부 모드명과 계산값은 일반 UI에 노출하지 않는다.
