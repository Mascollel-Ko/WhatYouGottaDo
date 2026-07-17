# Common Terminology

- **피로(fatigue)**: 기록과 메타데이터로 모델링한 현재 부담 상태입니다. 진단이 아닙니다.
- **노출(exposure)**: 특정 입력과 권위 규칙으로 산출한 상대적 부하량입니다. 조직 손상량이 아닙니다.
- **잔여 노출(residual exposure)**: 독립 event 노출에 회복 곡선을 적용한 현재 범위입니다.
- **workload**: 기록된 reps, load, duration, RPE 등으로 만든 계산 입력입니다.
- **volume**: protocol이 정한 반복·중량·시간 기반 누적량입니다.
- **intensity**: 중량, RPE, speed, protocol label 등 현재 구현이 선택한 강도 표현입니다.
- **OFI**: 다섯 canonical 피로 축을 종합한 0~100 요약 지수입니다.
- **readiness**: 피로 외 수행·불편감·회복 신호도 고려하는 별도 훈련 판단 도메인입니다.
- **국소 근육 피로**: 특정 근육/부위에 집중된 modeled fatigue입니다.
- **전신 피로**: 긴 세션·복합운동·sport session의 전신 근육 부담 축입니다.
- **연결조직**: 관절 구조, 연골, 건, 인대, 반월상연골, 관절순, 근막, 디스크 등 별도 load unit 집합입니다.
- **load unit**: unsided tissue state를 추적하는 최소 권위 식별자입니다.
- **joint complex**: 여러 load unit을 사용자 표시용으로 묶는 관절/기능 복합체입니다.
- **protocol**: 입력, 계산/분류, 집계, 출력, 예외를 고정한 제품 계약입니다.
- **product policy**: 연구 효과크기가 아니라 제품이 명시적으로 채택한 제한 규칙입니다.
- **heuristic**: 불완전한 입력에서 일관된 동작을 만들기 위한 engineering 판단입니다.
- **canonical state**: 동일 입력에 대해 제품이 표시하도록 정한 표준 상태입니다.
- **calibration**: 개인 history와 비교할 수 있을 때 상대 위치를 계산하는 과정입니다.

