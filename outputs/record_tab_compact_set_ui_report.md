# Record Tab Compact Set UI Report

## 변경

- 운동 카드 헤더를 한 줄로 축소했다.
- 헤더 구성: 운동명, i, 세트+, 일괄, 상세/접기.
- 계획 세트 수, 휴식, 완료 수, 메모, 최대 반복, 전체 RPE 같은 중복 요약은 기본 헤더에서 제거했다.
- 완료 세트는 기본적으로 compact row로 표시한다.

## 완료 세트 compact row

- 표시: 체크박스, 세트 번호, kg, 횟수/초, RPE, 상세.
- kg/횟수/초/RPE는 compact row에서 바로 수정 가능하다.
- 상세을 누르면 기존 expanded 세트 UI가 열린다.
- 접기를 누르면 다시 compact row로 돌아간다.

## 유지한 규칙

- `WorkoutSet.confirmed=false`: 계획 세트
- `WorkoutSet.confirmed=true`: 실제 수행 기록 세트
- 세트 추가/삭제/재정렬 의미는 변경하지 않았다.
- 휴식 타이머 시작 조건은 confirmed false -> true 전환 그대로 유지했다.
