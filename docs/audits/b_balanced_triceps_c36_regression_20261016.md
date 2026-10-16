# B 균형형 — 삼두 메타데이터 정정에 따른 C36 회귀 검증

- 기존 main: `010f383a8a4c8fc89a0ffb444c67c9d32a9a017a`.
- 정상 기준 CI: [38812230459](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38812230459).
- 수정 전/후 데이터 감사 CI: [38859623764](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38859623764), [38861453957](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38861453957).
- 대상: `cable_overhead_triceps_extension`, `dumbbell_overhead_triceps_extension`의 `HYPERTROPHY:SHOULDERS:VERTICAL_PRESS` → `HYPERTROPHY:ARMS:GENERAL`, physical-quality manifest SHA-256 갱신.
- 이 문서는 B 메타데이터 정정 효과에 대한 **회귀 감사**이며 새로운 운동량의 연구적 최적성 보증이 아니다.

## 실제 출력 비교

정상 기준/변경 CI의 `reports/stimulus-production-coverage.txt`를 비교했다. 각각 497줄로, **454줄 동일·43줄 변경**. 차이는 다음 분류에 한정된다.

- B5 실험 후보, B6 materialization·authorization
- `EXPERIMENTAL` stableKey/added/removed owner
- 해당 실험 후보의 권한·자극 질의 요약

**실제 CONTROL 운동 stableKey 출력, 최종 route 및 B7/B8 차단 상태는 모든 22개 생성 상황에서 동일**했다. 따라서 이 변화로 새로운 experimental 프로그램이 실서비스에 강제 노출되지는 않았다.

기존에는 EXP에서 오버헤드 삼두 운동을 제거하고 다른 삼두 운동으로 대체하던 5개 상황이 수정 후 그 운동을 그대로 유지한다.

- `persona1_recent`
- `persona1_reviewed`
- `persona2_reviewed`
- `persona4_recent`
- `persona4_reviewed`

위 5개 case × 2개 주간에 해당하는 설명되지 않은 제거 10건이 사라진다.

## C36 전체 JSON에서 확인한 수치

[CI #38861453957의 `c36-execution-provenance-census.json`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38861453957)으로 실제 집계한 결과:

| 대상 | 기존 C36 | 수정 이후 | 해석 |
|---|---:|---:|---|
| unexplained owner-week removal | 94 | 84 | 기존 삼두 owner 제거 10건 감소 |
| unique unexplained case-owner pairs | 47 | 42 | 5개 상황 감소 |
| unexplained에 B11 canonical 대체 후보가 있는 pair | 38 | 39 | 후보 관계 재편; **실제 대체 승인과 구별** |
| final CONTROL route | 22 | 22 | 불변 |
| B7 `CHANGE_PROVENANCE_UNCLOSED` | 21 | 21 | 불변 |
| B8 `CONTROL_REQUIRED` | 22 | 22 | 불변 |
| regional funded/week | 549 | 549 | 불변 |
| 2주 regional materialized | 1,098 | 1,098 | 불변 |
| unauthorized / overrun / duplicated physical rows | 0/0/0 | 0/0/0 | 불변 |
| B4→최종 주당 미배정 | 149 | 149 | 불변 |
| B4/B5/B6 최종 실제 대체증거 없는 unexplained | 0 exact materialized | 0 exact materialized | fail-closed 유지 |

**주의:** B11 canonical replacement *후보*가 있다는 것만으로 실제 B6 승인, 배정, 물리적 대체를 정당화하지 않는다. `exactDisplacementOwnerWeekOccurrences=0`, `unexplainedOwnerCasesWithExactB4B5B6ScheduleMaterialization=0`는 유지한다.

C34의 과거 동결 기준 `c34UnexplainedCaseOwnerPairs=47`은 **기존 스냅샷 역사값**이므로 변경하지 않는다. 이번 C36의 최신 집계 `42`와 구별한다.

## 회귀 테스트 정책

- `StimulusProductionCoverageAuditTest`의 **세 가지 달라진 실제 집계만** `94→84`, `47→42`, `38→39`로 보정.
- 다섯 개 영향 case에서 기존 삼두 stableKey가 CONTROL과 EXP 양쪽에 존재하며, B5는 올바른 `CANONICAL_STIMULUS_MOVEMENT_ARMS_TRICEPS`를 선택하는지 별도 확인.
- 원래의 B6 authorization, finite-capacity, B7/B8 fail-closed, 무단 자극·과다 자극·중복 검사 삭제/완화 금지.
- CI에 `test_physical_quality_relations.py`, `test_b_balanced_target_task_relations.py`도 명시적으로 실행.
- CI 보고물에 전체 C36 JSON을 포함하여 향후 회귀를 추적 가능하게 유지.

이 문서는 PR 검증 전 성공을 주장하지 않는다. 최종 CI 녹색, APK 생성 및 서명 여부는 CI에서 별도 확인한다.
