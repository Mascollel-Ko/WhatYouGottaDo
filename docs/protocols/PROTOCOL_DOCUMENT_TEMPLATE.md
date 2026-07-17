# <Protocol title>

| 항목 | 값 |
|---|---|
| Protocol ID | `<ID>` |
| Protocol version | `1.0.0` |
| Status | `DRAFT` |
| Implementation status | `UNKNOWN_PENDING_AUDIT` |
| Implemented from app version | `UNKNOWN_PENDING_AUDIT` |
| Last audited commit | `<full SHA>` |
| Evidence profile | `<allowed evidence label>` |
| Supersedes | 없음 |

`1.0.0`은 거버넌스 아래에서 처음 고정한 문서 계약 버전이며, 과학적 검증·임상적 정확성·완전성을 뜻하지 않습니다.

## 1. 일반 사용자용 요약

Kotlin을 읽지 않아도 목적과 결과의 의미를 이해할 수 있게 씁니다.

## 2. 목적

이 프로토콜이 해결하는 제품 문제를 씁니다.

## 3. 적용 범위

적용되는 기록, 운동, 화면, 기간을 씁니다.

## 4. 비적용 범위

진단, 치료, 구현되지 않은 입력과 예측을 명시합니다.

## 5. 용어

식별자와 사용자 용어를 정의합니다.

## 6. 입력 데이터

실제로 읽는 입력만 씁니다. 비활성 입력은 비활성이라고 표시합니다.

## 7. 계산 또는 분류 계약

현재 식, 분류, 순서, 경계를 재현 가능하게 씁니다. 구현되지 않은 식 항을 숨기지 않습니다.

## 8. 집계 방식

일·주·기간·사용자 기준 집계를 씁니다.

## 9. 출력과 UI 해석

사용자에게 보이는 값과 내부 값을 구분합니다.

## 10. 예외 및 fallback

누락값, legacy 기록, 빈 데이터 처리와 fail-closed 규칙을 씁니다.

## 11. 개인화 또는 보정

개인 baseline, calibration, 보정 기간과 비활성 보정을 씁니다.

## 12. 연구 근거

저장소에 검증된 근거만 씁니다. 제품 정책 값을 논문 효과크기로 표현하지 않습니다.

## 13. 제품 정책 및 휴리스틱

연구 근거와 별개인 사용자 승인 정책·제품 판단·engineering heuristic을 표시합니다.

## 14. 알려진 한계

노출량은 조직 손상이 아니고, 피로는 진단이 아닙니다. 불확실성과 미구현 항을 씁니다.

## 15. 현재 구현 상태

Specification과 runtime을 별도로 씁니다. runtime·test·asset·문서가 일치하지 않으면 완전하다고 표현하지 않습니다.

## 16. 구현 위치

존재를 확인한 source anchor를 저장소 상대 경로로 씁니다.

## 17. 검증 테스트

존재를 확인한 test anchor와 보장 범위를 씁니다.

## 18. 권위 자산

계산 또는 분류가 실제 읽는 authority asset만 씁니다.

## 19. 관련 문서

Canonical이 아닌 supporting/historical 문서의 역할을 함께 표시합니다.

## 20. 변경 이력

- `1.0.0`: 첫 governed contract.
