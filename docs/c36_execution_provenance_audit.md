# C36 — 실행량 회계와 변경 provenance 감사

기준 main은 `751cb2eff36e4c5969eedd634c0dfecbd13ff70d`였고 Protocol/Runtime은 `3.68.0` / `RECORD_BASED_PLANNER_0.15.10_KOTLIN_1`이었다. C36은 새로운 처방이나 배정 정책을 추가하지 않았다. 실제 B4/B5/B6, finite allocation, 최종 scheduling/materialization, B7 증거를 owner-week 단위로 연결했고, 검증된 finite-capacity 원인을 prescription-change attribution으로 보존했다.

## regional 배정량과 실제 생성량

C35의 `finiteAllocationPriorityOrder` 값은 final regional B4 funding이 아니라, regional demand가 최종 frequency allocator에 들어가기 전의 generic-builder 후보 진단값이었다. 그래서 서로 다른 단계의 숫자를 빼서 materialization 손실로 판단하면 안 된다.

| 단계 | units/week | 의미 |
|---|---:|---|
| B4 raw residual | 698 | 필요한 regional 잔량 |
| C35 finite-funded 진단 | 565 | final frequency allocation 이전의 후보 진단값 |
| C36 exact frequency BASE funded | 549 | 최종 주간 배정/authorization |
| 첫 주 materialized | 549 | 실제 생성된 compatible regional set |
| 2주 horizon materialized | 1,098 | 주당 549 × 2주 |
| B4에서 최종 주간 배정까지 남은 양 | 149 | 698 − 549, capacity shortfall |
| authorization 이후 placement/materialization 손실 | 0 | 정확히 배정된 양은 모두 배치·생성됨 |

C35의 565 funded와 549 materialized 사이 차이 16은 서로 반대 방향의 owner-row 오류가 상쇄된 순액이었다.

- 6개 target-context에서 진단값 48 units가 funded처럼 보였지만 final frequency allocation은 capacity 때문에 0을 승인했다. 이 target들은 `persona2_mixed`의 `ARMS_BICEPS`, `ARMS_TRICEPS`, `CALVES`; `persona2_sparse`의 `ARMS_TRICEPS`, `CALVES`; `persona4_recent`의 `CALVES`다. 해당 후보에는 B5 선택과 B6 cold-start/user-calibration 처방 권한이 있어도, 그 사실이 finite capacity를 넘는 scheduling authority를 주지는 않는다.
- 4개 target-context에서는 이전 진단값이 0이었지만 final BASE authorization과 생성은 각각 주당 8 units였다. 대상은 `persona0_recent/POSTERIOR_CHAIN`, `persona1_recent/CALVES`, `persona4_reviewed/CALVES`, `reviewed_strength_isolated/HORIZONTAL_PUSH`다.

따라서 C35의 `unfunded=133`은 최종 잔량이 아니다. final frequency allocator에 맞춰 계산한 실제 잔량은 주당 149 units, 2주 기준 298 units다. 기존 CONTROL owner에서 동일 처방으로 보존된 regional units는 0이고, 1,098 units는 EXP에서 새로 생성되거나 변경된 양이다. 이 수치에서 regional overrun, duplicate physical rows, unauthorized material, authorization-to-placement shortfall은 모두 0이다. census는 각 target의 B4 residual, B5 owner, B6 state, diagnostic funding, final frequency funding, authorized demand id, week별 materialization을 보존한다.

## 기존 owner 제거 47건

전체 corpus에서 제거 owner는 200 owner-week occurrence, 100 unique `(case, owner)` pair, 24 globally unique owner identity다. 이 중 unexplained 상태는 94 owner-week occurrence, 즉 47 unique `(case, owner)` pair다. 같은 owner가 두 주에 반복되어 occurrence 수가 unique pair 수의 두 배다.

47개 unexplained pair는 다음과 같다.

- 38 pair / 76 owner-week에서 B11 canonical replacement 후보와 정확한 B5 selected alternative가 모두 보인다. 그러나 후보만으로 제거를 정당화할 수 없고, 이들 중 실제 exact B4/B5/B6 schedule-and-materialization chain은 0이다.
- 위 38 pair 중 3 pair / 6 owner-week에는 exact B4/B6 authority가 확인되지만 finite capacity에서 거부되어 최종 schedule/materialization은 0이다. 이 사례는 `barbell_reverse_curl`의 Biceps owner, `ex_5c8751d2`의 `persona2_mixed` Calves owner, `standing_bodyweight_calf_raise`의 `persona4_recent` Calves owner다.
- 나머지 35 pair / 70 owner-week은 exact B6 authority가 없다. 9 pair / 18 owner-week은 selected exact canonical replacement evidence도 없다.
- unexplained rows에 exact allocator/scheduler displacement edge는 0이다.

따라서 47건 중 제거 원인을 새로 증명해 종결한 건은 0이다. B11의 분류, 비슷한 근육/운동, B5 후보 선택은 실제 대체 materialization의 증거가 아니다. 남은 건은 `UNEXPLAINED_REMOVED_IDENTITY`로 유지되며 B7/B8을 통과시키지 않는다. census에는 각 owner-week의 CONTROL row, EXP 동일 owner/다른 role row, B11 target evidence, B5 trace, B6 alternative evidence, allocation/displacement events, materialization trace, B7 attribution이 담겨 있다.

## `persona4_recent` 스쿼트 5→4

두 주 모두 CONTROL은 `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` 40 kg × 8회 × 5세트, day 4였다. EXP는 동일 owner와 load/reps/rest/source를 유지한 4세트였고 day 2에 배치됐다. 이미 initial placement 이전의 `frequencyDemand` funding에서 5세트 요청 중 4세트만 배정되어 placement는 네 세트 처방을 바꾸지 않고 날짜만 이동했다.

정확한 causal evidence는 다음과 같다.

- 해당 exact continuity candidate: requested 5, funded 4, `FINITE_CAPACITY`
- 전체 requested demand 37 units > computed final controllable capacity 28 units
- exact CONTROL row와 EXP row의 집합이 동일하고, EXP는 CONTROL prescription의 첫 네 세트와 정확히 일치
- exact BASE scheduled continuity prescription도 같은 네 세트 prefix, rest, weight source와 일치
- exact B5 selected owner는 없고 exact B6 authorization도 없음

C36은 이 owner-week 변화를 typed `FINITE_CAPACITY_CONTINUITY_ALLOCATION`으로 기록한다. 이는 capacity가 실제로 만든 변화의 원인을 설명하는 provenance일 뿐 B6 처방 authority를 만들지 않으며, production material routing source로도 허용되지 않는다. 잘못된 owner role, capacity가 binding하지 않는 조작값, rest 변경을 넣은 음성 테스트는 모두 `UNEXPLAINED`로 남는다.

## B7/B8, route와 안전성

- prescription-change 표의 두 squat owner-week은 capacity attribution을 갖고, `UNEXPLAINED_PRESCRIPTION_CHANGE`는 0이다.
- 47 unique unexplained removals는 그대로라 B7은 21 case에서 `CHANGE_PROVENANCE_UNCLOSED` / `NOT_ELIGIBLE`이다. 별도 1 case는 `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW`다.
- B8은 22 cases 모두 `CONTROL_REQUIRED`; 선택 route도 CONTROL 22다. 따라서 이 attribution만으로 새 생성기를 강제 활성화하지 않았다.
- build accounting은 CONTROL 22, EXPERIMENTAL 22, total 44, third build 0이다. Power/JUMP_LANDING material은 0이다.
- C35 regional priority/funding 의미는 바꾸지 않았다. B7/B8 predicate도 완화하지 않았다.

## 검증 산출물

Machine-readable owner/week census는 [`c36-execution-provenance-census.json`](c36-execution-provenance-census.json)이다. 최종 census SHA-256은 `628A9DCC9072F49FF55D9C5BCC5CD8C60CB2BD33866A9F0C5F1DC94C5BDAF7BF`이며, 시작 SHA는 `751cb2eff36e4c5969eedd634c0dfecbd13ff70d`, implementation/test SHA는 `0c321c3d415981d9f0c4bc5e761f6ac36e2fe709`이다. 22개 corpus 생성 관측은 총 4,701 ms, 평균 213.68 ms/case, 중앙값 171.5 ms, 최솟값 79 ms, 최댓값 535 ms였다. 이는 해당 테스트 실행의 관측치이며 SLA가 아니다.

Focused `StimulusProductionCoverageAuditTest.measureUnmodifiedServiceCoverage`는 1 test / 0 failures / 0 errors / 0 skips로 통과했다. 전체 JVM suite는 2,380 tests / 0 failures / 0 errors / 4 skips (359 suites)였다. `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `assembleDebug`, protocol documentation validation (9 families / 36 protocols), `git diff --check`가 통과했다. 로컬 debug APK는 70,741,865 bytes, SHA-256 `5B8C6C2B80F08626EB28971E90F1ABEE6667FD48DA17BA13EA8BA375705B2DC8`이다. 검증은 기존 repository-external worker/IPC workaround로 실행했으며 assertion 실패나 native crash는 없었다. Hosted CI 결과는 push 뒤 확인한다. 구현/test commit `0c321c3d415981d9f0c4bc5e761f6ac36e2fe709`가 `lastAuditedCommit`이다.
