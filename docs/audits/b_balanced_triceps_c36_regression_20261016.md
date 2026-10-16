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

같은 조건으로 `main`과 이 브랜치의 C33 census를 로컬 실행해 비교하면 B5 후보 풀에는 정확히 4개 행이 추가된다.

- `persona1_sparse / ARMS_TRICEPS`: `dumbbell_overhead_triceps_extension` 추가, 기존 선택 유지.
- `persona2_sparse / ARMS_TRICEPS`: 두 overhead extension 추가, 선택 owner가 `dumbbell_lying_triceps_extension`에서 `cable_overhead_triceps_extension`으로 변경.
- `persona4_sparse / ARMS_TRICEPS`: `dumbbell_overhead_triceps_extension` 추가, 기존 선택 유지.

`persona2_sparse`의 선택 변화는 shadow EXP의 B5/B6 증거에만 존재한다. 승인 단위는 주당 8로 동일하고 두 주 모두 finite-capacity에서 제거되어 실제 materialized 단위는 0이다. 세 상황의 최종 route는 모두 `CONTROL`이다.

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

C33의 `candidateRows`는 `268→272`, `legitimatelyRejectedCandidates`는 `241→245`로 변했다. `selectedCandidates=27`, `authorizedMaterialUnits=288`, `actualMaterialUnits=256`, `duplicateCreditTargets=0`, `overfilledTargets=0`, `unauthorizedMaterialRows=0`은 모두 동일하다. 즉 늘어난 네 후보는 새 수치 권한이나 배정량을 만들지 않았다.

같은 코퍼스를 사용하는 C26 next-phase 요약도 동일한 다섯 CONTROL owner 보존을 반영한다. `unexplainedRemovedIdentityAttributions 47→42`, `exactMovementCanonicalReplacementAttributions 43→48`, B11 replacement-but-B7-unclosed owner/movement 행은 각각 `38→39`, `31→32`다. quality 행 7, task 행 0, 무권한 quality 추가 0은 불변이다.

**주의:** B11 canonical replacement *후보*가 있다는 것만으로 실제 B6 승인, 배정, 물리적 대체를 정당화하지 않는다. `exactDisplacementOwnerWeekOccurrences=0`, `unexplainedOwnerCasesWithExactB4B5B6ScheduleMaterialization=0`는 유지한다.

C34의 과거 동결 기준 `c34UnexplainedCaseOwnerPairs=47`은 **기존 스냅샷 역사값**이므로 변경하지 않는다. 이번 C36의 최신 집계 `42`와 구별한다.

## 회귀 테스트 정책

- `StimulusProductionCoverageAuditTest`의 **세 가지 달라진 C36 집계** `94→84`, `47→42`, `38→39`, 동일 원인의 C26 요약, C33 후보/정상 거절 집계 `268→272`, `241→245`를 실제 출력에 맞춰 보정.
- 다섯 개 영향 case에서 기존 삼두 stableKey가 CONTROL과 EXP 양쪽에 존재하며, B5는 올바른 `CANONICAL_STIMULUS_MOVEMENT_ARMS_TRICEPS`를 선택하는지 별도 확인.
- 세 sparse case에서 추가된 정확한 후보 stableKey와 pool 크기를 확인하고, `persona2_sparse`의 새 B5 owner가 finite-capacity로 0세트 materialize되며 최종 CONTROL을 유지하는지 별도 확인.
- 원래의 B6 authorization, finite-capacity, B7/B8 fail-closed, 무단 자극·과다 자극·중복 검사 삭제/완화 금지.
- CI에 `test_physical_quality_relations.py`, `test_b_balanced_target_task_relations.py`도 명시적으로 실행.
- CI 보고물에 전체 C26/C33/C36 JSON을 포함하여 향후 회귀를 추적 가능하게 유지.

## 로컬 검증

- 최종 `:app:testDebugUnitTest`: 365 suites, 2,395 tests, 0 failures, 0 errors, 4 skips. Windows의 누적 Robolectric native 상태를 격리하기 위해 저장소 밖 JDK 17 및 클래스별 test worker 설정을 사용했다. 기본 JBR 21과 JDK 17 `forkEvery=75` 실행은 각각 `robolectric-nativeruntime.dll` native access violation을 재현했으며 애플리케이션 assertion failure는 없었다.
- 최종 metadata authority: 지정 physical-quality 4/4, B overlay 3/3, 전체 16/16 통과. canonical exporter `--check`는 workbook과 생성 asset이 byte-deterministic/current임을 확인했다. 프로토콜 문서는 9 families/36 protocols, Community/Cloud 계약은 9/9 통과했다.
- `:app:lintDebug`는 최초 실행에서 `main`에도 존재하는 API 27 전용 `windowLightNavigationBar`의 기본 `values` 배치를 검출했다. 이 속성만 `values-v27`로 이동한 뒤 통과했다. 검사 억제나 minSdk 변경은 하지 않았다.
- `:app:assembleDebug` 통과. 로컬 APK는 69,042,577 bytes, SHA-256 `B168C9850E94853FD16158E96D4550AE82315D8429ADDED73F0F8223BC3360FC`; `apksigner` v2 검증 통과, signer 1명(Android Debug)이다. 연결된 기기/에뮬레이터가 없어 계측 테스트는 실행하지 않았다.
- 추가 `:app:check`는 debug tests와 lint를 통과한 뒤 `testReleaseUnitTest`에서 2,395 tests 중 136 failures, 4 skips로 종료됐다. 모든 failure는 release Robolectric이 `com.whatyougottado.app/androidx.activity.ComponentActivity`를 manifest activity로 해석하지 못한 공통 초기화 오류다. 기준 `main`에서 대표 테스트를 동일 조건으로 실행해 1/1 같은 오류를 재현했으므로 이번 메타데이터 변경의 회귀나 필수 debug CI 실패로 분류하지 않는다.

## Hosted CI 검증

[GitHub Actions run 38879410925](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38879410925)은 커밋 `2486b7bb587122e5c1ee3e7275ba62d94f891820`에서 성공했다. 문서/계약/whitespace/B metadata 검증, 전체 debug unit tests, coverage와 JUnit 업로드, APK 조립, CI signer 검증, APK 업로드가 모두 통과했다.

- 업로드된 JUnit XML: 365 suites, 2,395 tests, 0 failures, 0 errors, 4 skips.
- 업로드된 APK: 69,056,897 bytes, SHA-256 `460C74D80FA3E57FC4817C9BC1F0CAC61DDA19B9616174566BA5B72673CC2E70`.
- `apksigner`: v2 서명 유효, signer 1명, 인증서 `CN=WhatYouGottaDo CI Debug, OU=Android, O=WhatYouGottaDo, C=KR`, 인증서 SHA-256 `A37B8A11810408005E8DC45BD83BEC29769404CA8E3041D2C41AF546B344D3E4`.
