# Initial Profile / Fatigue Design

## 목적

초기 기록이 적은 사용자에게 개인 기준선 부족만으로 `FATIGUED`가 과도하게 뜨는 문제를 줄인다.

## 저장 데이터

- 체중, 키, 나이/출생연도, 성별
- 최근 4주 근력/배드민턴 빈도, 시간, RPE
- 운동 경력
- 최근 운동 공백
- 평소 수면
- 현재 피로, 근육통, 스트레스
- 통증/주의 부위
- 목표

## 적용 방식

- 기존 Today Readiness 엔진 결과를 먼저 계산한다.
- 결과가 `FATIGUED`가 아니면 변경하지 않는다.
- 기록이 충분한 구간은 기존 개인 기준선을 우선한다.
- cold-start 또는 baseline-building 구간에서 강한 복합 신호가 부족하면 `FATIGUED`를 `CAUTION`으로 낮춘다.
- 고RPE 고부하, 매우 낮은 수면, 통증, 긴 공백 후 복귀 같은 복합 신호가 있으면 기존 `FATIGUED`를 유지한다.

## UI 노출 제한

- 내부 모드명, 점수, 가중치는 일반 UI에 노출하지 않는다.
- 사용자는 Ready / Caution / Fatigued / Limited 상태만 본다.
