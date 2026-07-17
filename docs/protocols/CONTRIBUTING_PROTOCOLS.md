# Protocol Contribution Guide

공개 사용자는 오류 보고, 분류 변경, 새 논문, 식 변경, counterexample, pull request, experimental protocol 토론을 제안할 수 있습니다. 문서 변경만으로 runtime 동작을 몰래 바꿀 수는 없습니다.

## 필수 제안 정보

모든 제안은 다음을 포함해야 합니다.

- 영향받는 protocol ID
- 문제 설명
- 현재 동작
- 제안 동작
- 근거 또는 rationale
- research-supported인지 product policy인지
- 수치 예시
- backward-compatibility 영향
- migration 영향
- 추가 또는 수정할 테스트
- 갱신할 canonical 문서

## 제안 유형별 경로

- 오류: 재현 입력, 현재 출력, 예상 출력과 source/test anchor를 제시합니다.
- 분류 변경: stable key와 현재/제안 taxonomy를 제시합니다.
- 논문: 저장소의 evidence provenance 절차를 따르며 제목만으로 runtime 권위를 만들지 않습니다.
- 식 변경: 기존 식과 새 식의 수치 비교, 경계값, historical interpretation 영향을 제시합니다.
- counterexample: 최소 재현 데이터와 개인정보를 제거한 결과를 제시합니다.
- pull request: runtime 변경, protocol version, 테스트, release note를 같은 변경 범위에서 정합시킵니다.
- experimental protocol: `EXPERIMENTAL` 또는 `DRAFT`로 시작하고 구현 상태를 과장하지 않습니다.

## 리뷰 원칙

Evidence strength와 implementation status는 독립적으로 심사합니다. 문서가 설명을 명확히 해도 수치 동작이 바뀌면 editorial patch가 아니라 behavioral protocol change입니다.

