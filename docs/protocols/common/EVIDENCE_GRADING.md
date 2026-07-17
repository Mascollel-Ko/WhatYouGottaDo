# Evidence Grading

| Label | 의미 |
|---|---|
| `DIRECT_RESEARCH_SUPPORT` | 현재 조건과 결과를 직접 다룬 검증된 human evidence |
| `RESEARCH_TRANSFER` | 인접 운동·조건·집단에서 제한적으로 전이한 근거 |
| `MECHANISTIC_SUPPORT` | 생체역학·기전·ex vivo 결과가 방향성을 지지 |
| `PRODUCT_POLICY` | 제품이 경계와 목적을 명시해 채택한 규칙 |
| `ENGINEERING_HEURISTIC` | 결정론적 fallback·정렬·완화용 구현 휴리스틱 |
| `USER_APPROVED_POLICY` | 사용자가 범위와 값을 명시적으로 승인한 제품 정책 |
| `LOW_CONFIDENCE_PROXY` | 직접 근거가 없어 class/proxy로 제한 사용 |
| `MIXED` | 위 유형이 결합되며 문서에서 항목별로 구분 |

Direct human evidence, mechanistic evidence, ex vivo evidence, class-level transfer, proxy evidence, expert/product judgment, user-approved policy, engineering heuristic을 서로 바꾸어 부르지 않습니다. Evidence strength와 implementation status는 독립적입니다. 강한 근거가 아직 미구현일 수 있고, 구현된 제품 정책이 직접 연구 효과크기는 아닐 수 있습니다.

