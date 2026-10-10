# C37.5 — 미해결 운동 제거 원인별 재현과 B7/B8 검증

## 결론

이 감사의 32쌍은 실제 사용자 프로그램에서 삭제된 운동 32종이 아니다. 최신 C36 실행에서 `UNEXPLAINED_REMOVAL`로 잡힌 **고유 (case, stableKey, selectionRole) 32쌍**, 즉 2주 코퍼스 기준 **64 owner-week 관측**이다. 해당 32쌍에는 B7이 승인한 정확한 교체 materialization이 하나도 없다. B9는 B8 승인 1개 사례를 제외하고 정확한 CONTROL 원본을 반환했으므로, CONTROL 사례의 사용자가 보던 저장·생성 결과에서 이 32개 운동은 제거되지 않았다.

따라서 C37.5는 미해결 건수를 0으로 만들기 위해 B7/B8을 약화하지 않았다. 최신 evidence를 각 주차별로 기계 판독 census에 고정하고, 실제로 발견된 교체 선택 취소·재선택 및 저장 재검증 회귀를 수정했다.

## 최신 코퍼스 진단

| 최신 원인 분류 | 고유 owner pair | 확인된 실제 단계 | 처리 |
|---|---:|---|---|
| `NO_EXACT_B5_ALTERNATIVE` | 9 | CONTROL owner를 대체할 exact B5 선택 증거 없음 | 기존 CONTROL 보존; 대체 추론 금지 |
| `B5_CANDIDATE_BUT_NO_EXACT_B6_AUTHORITY` | 18 | B5 후보만으로 실행 권한을 만들 수 없음 | 기존 CONTROL 보존; 정확한 B6 처방 또는 B4 수치 근거 없으면 보류 |
| `B4_B6_AUTHORIZED_BUT_NOT_SCHEDULED_OR_MATERIALIZED` | 5 | B4/B6 probe는 허용했지만 주간 실행 배정에서 `FINITE_CAPACITY`, funded 0, schedule/materialization 0 | capacity 부족 유지; 무배정 대체를 완료로 취급하지 않음 |
| **합계** | **32** | 64 owner-week | 32쌍 모두 B7 unexplained 상태 유지 |

18쌍의 세부 B6 경계는 다음과 같다.

- 11쌍은 `UPPER_PULL` 방향성 movement target과 B5 identity 후보는 있으나 B4가 수치 용량을 정하지 않은 경로다. B5의 `B5_DOES_NOT_GRANT_DOSE_AUTHORITY`가 명시되어 있으며, 실제 Hypertrophy 주간 numeric target/B6 prescription이 없다.
- 2쌍은 바벨 백 스쿼트의 `CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`.
- 1쌍은 벤치프레스의 `PLANNED_RESISTANCE_LOAD_UNAVAILABLE`.
- 2쌍은 `HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE`.
- 2쌍은 `SAFE_LOAD_OR_EFFORT_AUTHORITY_UNAVAILABLE`.

B11 `CANONICAL_REPLACEMENT` 분류나 B5 후보는 위 B4/B6/실제 배정 체인을 대체하지 않는다. 같은 stableKey에서 role이 바뀐 경우도 exact target·authority·materialization이 없으면 종목 대체 또는 제거 근거로 처리하지 않았다.

5개 B4/B6 후보의 owner-week 원인은 모두 finite funding에서 멈췄다.

- `persona1_recent`: standing bodyweight calf raise, 8 requested → 0 funded.
- `persona2_mixed`: barbell reverse curl, 8 → 0; bird dog, 6 → 0; single-leg calf raise, 8 → 0.
- `persona4_recent`: standing bodyweight calf raise, 8 → 0.

각 후보의 exact rank·`FINITE_CAPACITY` 거부·주간 수치·배정 이벤트·materialization 결과는 [machine-readable census](c37_5-removal-repair-census.json)에 owner-week 단위로 기록했다. 이들은 “운동을 배정했다”가 아니라 B6 허용 이후에도 finite allocator가 자금을 주지 않은 사례다.

## 전체 B7/B8/B9

22 generated cases의 결과는 다음과 같다.

- B7: 18 `NOT_ELIGIBLE` (`CHANGE_PROVENANCE_UNCLOSED`), 4 `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW`.
- B8: 1 `AUTHORIZED_FOR_BOUNDED_CUTOVER`, 21 `CONTROL_REQUIRED`.
- B9: 1 `B8_POWER_JUMP_V1`, 21 `CONTROL`.

21 CONTROL의 독립 원인은 18건의 B7 provenance 미폐쇄, 2건의 Strength-only B8 scope에서 필요한 exact Strength B5/B6/완전 material 증거가 없거나 범위 밖인 상황, 1건의 Hypertrophy B6 주간 누락·shortfall이다. 나머지 1개 `persona3_mixed`만 기존 B7/B8 Power/Jump scope를 통과했다. B7 eligible을 곧 B8 권한으로 간주하지 않았다.

코퍼스 전체에서 regional overrun 0, duplicate physical row 0, unauthorized material units 0, unexplained prescription change 0, third build 0이다. `persona4_recent`의 스쿼트는 이번 재현에서 5→4 처방 변경이 없고, 날짜만 달라졌다.

## 이번 구현 변경

1. 저장 시 B5/B6 전체 재검증은 그대로 유지한다. 재검증마다 변하는 `PersonalizedPlanningDecision.decisionId`와 `generatedAtEpochMillis`만 stale fingerprint에서 제외했다. 이 값은 실행 계획이 아니라 감사 실행 식별·시각이다. plan rows, prescription, authority, frequency, target, progression 등 나머지 decision 필드는 계속 fingerprint에 포함되므로 실제 처방이나 권한 변경은 저장을 막는다.
2. 이미 검증·적용한 replacement preview에는 `기존 운동으로 되돌리기` 동작을 추가했다. 되돌리기는 validation의 정확한 keep rows를 복원하고 replacement rows만 제거하며, source fingerprint를 다시 비교한다. 증거가 stale하거나 row가 바뀌면 실패 폐쇄한다. 되돌린 뒤 다른 제안을 다시 선택할 수 있다.
3. 교체 흐름 통합 테스트는 실제 Room에 저장하고 연결을 닫은 뒤 DB를 다시 열어 Repository에서 draft를 다시 읽는다. 정확한 exercise key/role, 주차·요일·순서, 세트별 reps/weight/duration/RPE/load state/rest 및 progression signature를 비교한 뒤 날짜 세션에 적용해 같은 처방·세션 연결을 확인한다.

이 변경은 B4/B5/B6의 처방 권한을 새로 만들지 않고, B7/B8/B9 predicate도 변경하지 않는다. 코퍼스의 32개 unresolved pair는 근거가 없는 상태 그대로 남는다. 이 단계에서 해결된 기능은 검증된 교체를 취소·재선택할 수 있게 한 UI/저장 일관성 결함이다.

## 실행 시간

현재 동일 22-case 생성 계측은 median 279 ms/case, mean 307.5 ms/case, max 827 ms/case였다. 변경은 생성 hot path에 추가 탐색을 넣지 않고 저장 시 volatile audit id/time만 fingerprint에서 정규화한다. 따라서 이 변경으로 생성 시간이 악화됐다는 징후는 없으며, 별도의 before/after 속도 향상 주장은 하지 않는다.

## 로컬 검증 결과

- Focused replacement model/UI 및 실제 Room save→close→reopen→date-apply 통합 검증: 통과. 되돌린 keep 초안으로 돌아온 후 두 번째 다른 제안을 다시 검증·적용했다.
- 전체 `:app:testDebugUnitTest`: 2,420 tests, failures 0, errors 0, skips 4.
- `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug`: 모두 통과. 전체 suite는 알려진 native runtime 문제를 피하기 위해 저장소 밖 JDK 17 worker 설정으로 실행했고 이번 run에서 native crash는 재현되지 않았다.
- Protocol validator: 9 families / 36 protocols 통과. `git diff --check` 통과.
- Local APK: 72,729,550 bytes, SHA-256 `158CE46BBD2C6F70D0EAEBB3E857F6210AC6FC00BFB99A27A9C88090396FBFA2`.

## 남은 한계

PR #24는 Draft로 유지한다. Hosted CI는 이 변경을 push한 뒤 확인한다. 32쌍의 향후 해소에는 각각 실제 B4 numeric authority, exact B6 authority, allocator/scheduler가 생성한 owner-week 인과 사건 중 필요한 증거가 여전히 필요하다. 그 근거가 없는 동안 B9 CONTROL은 정상 fail-closed 결과다.
