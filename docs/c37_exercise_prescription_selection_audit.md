# C37 — 운동 처방·선택 흐름 통합 현황

## 결과 범위

C37은 기준 `a4c1a0f4c6c9a35ac67247698a3c897ce2a6e063`에서 이어서 작업했다. 현재 계약은 Protocol `3.72.0`, Runtime `RECORD_BASED_PLANNER_0.15.14_KOTLIN_1`, App `0.5.1.5`, Room `38`이다. C33의 region별 B4 잔량 → B5 후보 부분집합 → B6 처방 → finite allocation/배치 → B7/B8 검증 경로를 보존했다.

기본 C37은 직접 코어의 실행 형태와 exact B7 원인 증거를 연결한다. C37.1은 사용자가 승인한 Power/Jump-Landing 용량·처방 정책을 B4/B5/B6 경로까지 연결했고, C37.2는 B7/B8/B9에서 전체 검증을 통과한 범위만 제한적으로 라우팅한다. Keep/replace proposal UI는 별도 미완료 범위이며, 승인되지 않은 EXP 결과를 저장 전 확정안이나 선택 가능한 제안으로 노출하지 않는다.

## 직접 코어 B4/B5/B6

`CORE_DIRECT`는 기존 `CoreClass.DIRECT`, `CoreDirectTarget`, canonical movement metadata를 사용한다. B4의 기존 cold-start `USER_APPROVED_PROJECT_POLICY` 목표인 주 6 direct sets를 유지하며, B6는 exact B4 residual set 수만 소비한다.

- 반복 기록만 있는 canonical direct-core 운동은 운동의 canonical 기록 모드가 반복형일 때만 cold-start 8회 anchor를 사용한다. 이는 이미 승인된 Hypertrophy 시작 형태의 재사용이며, 근비대 최적 반복수 주장이 아니다.
- 개인의 exact exercise confirmed history가 7–15회이면 기존 수행 형태를 재사용한다. 복부 굴곡·신전 계열만 기존 Hypertrophy minimum-effort 조건(RPE 7)을 유지한다. 브레이싱·기술 제어는 RPE 7을 강제하지 않는다.
- 정적 운동은 exact personal duration 기록이 있어야 한다. 초·지속 시간을 새로 추정하지 않는다.
- 외부 부하가 필요한데 중량 authority가 없으면 `USER_CALIBRATION_REQUIRED`를 사용한다. 0kg를 실제 중량 처방으로 취급하지 않는다.
- B5 후보는 exact canonical direct-Core profile과 실행 형태가 확인될 때만 선정 가능하다. B6 권한이 없으면 materialization을 허용하지 않는다.

## B7 원인 연결과 corpus 재실행

Core role 대체는 B7이 다음의 정확한 연결을 모두 확인할 때만 종결한다: exact B4 Core target과 잔량, exact B5 owner/role, quality가 없는 Core movement B6 grant, 동일 prescription, 주차별 materialization. 일반 reason string이나 stableKey 일치만으로 닫지 않는다. 승인된 사용자 load 입력이 필요한 경우에도 exact typed calibration evidence와 set shape가 모두 있어야 한다.

22개 generated corpus 결과는 [`c37-exercise-prescription-selection-census.json`](c37-exercise-prescription-selection-census.json)에 저장했다.

- C36 재실행의 제거 occurrence 200건 / unique `(case, owner)` 100쌍 중 unexplained는 64 owner-week / 32 unique 쌍이다. exact Core B7 closure는 32 owner-week / 16 case-owner 쌍이다. 용량 때문에 materialize되지 않은 Core owner-week 2건은 계속 미해결이다.
- 현재 잔여 제거 후보 중 B11 canonical replacement와 exact B5 대안이 보이는 23 unique 쌍이 있지만, 아직 해당 쌍에 exact B4/B5/B6 주간 materialization 증거가 모두 연결된 사례는 0이다. 후보 선정만으로 기존 운동 제거를 승인하지 않았다.
- `persona4_recent` 스쿼트는 이번 corpus에서 이전 5→4 보고가 재현되지 않았다. CONTROL과 EXP 모두 `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`, 40 kg × 8회 × 5세트이고, day 4에서 day 2로만 이동했다. 최신 dossier는 `NO_SHARED_OWNER_PRESCRIPTION_CHANGE`다. 전체 corpus의 다른 처방 변경 4 owner-week는 finite-capacity attribution을 가지며 unexplained 처방 변경은 0이다.
- 해당 스쿼트 continuity candidate는 frequency stage에서 5 requested / 5 funded, rejection `FUNDED`로 관찰됐다. exact B5/B6 owner 권한은 없으므로 이를 새 처방 권한으로 해석하지 않는다. 현재 생성 결과는 기존 다섯 세트 보존과 날짜 이동뿐이다.
- C33 regional H 회계의 88 target rows에서 raw residual 698 units/week, legacy 진단 funding 581, exact final frequency BASE funding 533, 최종 1주 materialization 533, 2주 horizon 1,066이다. 배정 후 배치 손실 0, regional overrun 0, duplicate physical rows 0, unauthorized units 0이다. B4 잔량과 최종 배정량 차이는 capacity shortfall로 남고 need 삭제로 바꾸지 않는다.
- 최신 C33 census는 candidate 245, selected owner 27, 정상 탈락 218, core direct target 5, 그중 4 target이 두 주 동안 materialize되고 1 target은 finite capacity로 0 materialize 됨을 기록한다. 전체 C36 실행은 CONTROL 22 / EXPERIMENTAL 22 / total 44 / third build 0이다.
- 동일 22-case 실행의 planner wall time은 합계 6,269 ms, 평균 285.0 ms/case, 중앙값 224 ms, 최솟값 89 ms, 최댓값 689 ms였다. 한 corpus 실행에서 관찰된 수치이며 성능 SLA 비교는 아니다.

## Power·점프착지의 C37.1 B4/B5/B6 처방 경로

Power와 점프착지는 모든 사용자에게 필요한 주간 목표로 만들지 않는다. B4는 B1/B3의 `DEVELOP` need가 있을 때만 수치 권한을 검토한다. 하체 Strength/Hypertrophy workload는 이번 계획에서 exact B5/B6 승인된 직접 세트만 사용하고, 한 physical set이 두 quality/region 표현에 있으면 한 번만 센다. 추정 불완전한 resistance workload는 0으로 바꾸지 않고 `WORKLOAD_UNKNOWN`으로 남긴다. 배드민턴은 기록이 없는 주를 0분으로 넣지 않으며, 최근 28일 안에서 관측된 완전 ISO 주만 집계하고 명시적으로 제외된 interruption 주는 빼며, 유효 주가 없으면 UNKNOWN을 보수적 MODERATE cap column으로 처리한다.

사용자 승인 프로젝트 cap은 신규 Power+Jump/Landing 세트가 공유한다. 하체 resistance LOW/MODERATE/HIGH와 badminton LOW/MODERATE/HIGH cap 표는 각각 `6/4/2`, `4/2/2`, `2/2/0`이고 상체 Power cap은 `6/4/2`다. 전신 운동은 상체·하체 중 작은 cap, 새 P/J 합계는 주 8세트 이하로 제한한다. 이 값은 mandatory target이나 physiological optimum이 아니다. need가 승인되어도 최초 도입은 주 2세트부터 검토하며, B6는 정확한 B4 세트 수를 소비한다.

B5에는 해당 stableKey의 canonical `DIRECT_CAPABILITY`, `PASS` physical-quality relation과 Power의 `BALLISTIC/PLYOMETRIC` 또는 Jump/Landing의 `LANDING` mode가 필요하다. SUPPORTIVE relation은 직접 자격으로 승격하지 않는다. 첫 cold-start shape는 `2–4 sets × 3–6 reps`, 4회 anchor, 120초 초기 휴식이며, 세트 수는 B4 권한이다. 명시적인 canonical bodyweight semantics가 확인되면 외부부하 없음으로 실행할 수 있다. 외부부하 운동은 장비 종류만으로 1RM 계수를 추정하지 않고 calibration을 요구한다. 50% general Power와 70% Power Clean은 적합성이 명시되고 동일 운동의 신뢰 가능한 1RM이 확인된 경우에만 사용자 확인용 제안이며, 점프 스쿼트는 일반 스쿼트 1RM을 재사용하지 않는다. 현재 데이터 모델이 좌우 착지/반복 의미를 손실 없이 저장하지 못하면 해당 unilateral shape는 보류한다.

22-case corpus는 27건 중 22 생성 / 5 preflight rejection이다. generated case의 Power target row는 22이며, 이 중 B4 cold-start numeric authority·exact B5 owner·B6 승인·완전 materialization까지 이어진 것은 3건이다. owner는 `lateral_bound_continuous` 하나이며, 주 2세트가 2주 horizon에 걸쳐 6 physical rows로 생성됐다. personal Power numeric dose authority는 0건이다. `REACTIVE_STRENGTH_SSC` 방향 target row는 22건이지만 B1 need는 0, numeric B4는 0, B5/B6/materialization은 0이다. C24의 `TASK:JUMP_LANDING`은 별도 기존 프로토콜이며 이 변경으로 수정되지 않았다. 기존 C21 frozen reference 8 Power rows 대비 새 authority filter 결과는 6 rows다.

## C37.2 — 정확한 Power/Jump B8/B9 제한 라우팅

B8은 `POWER_JUMP_V1`을 별도 scope로 검증한다. 기존 B7 provenance closure와 regression 검사, 정확한 B3 need 및 B4 주간 set units, B5 stableKey/role와 trace, B6의 typed execution authority, 모든 주차의 완전한 materialization, schedule/program contract 보존, hard projection을 다시 확인한다. Power/Jump와 함께 들어가는 Task 및 Movement owner도 각자의 exact Task protocol 또는 B4/B5/B6 materialization 증거가 있어야 한다. B9는 세 authority identity 집합을 대조한 뒤 이미 생성된 EXP object만 선택한다. 어떤 predicate도 완화하지 않았다.

같은 stableKey의 내부 role 변경은 자동 대체로 보지 않는다. B7이 정확한 canonical Movement role replacement를 분류하고, 동일 물리 운동의 새 role이 exact B5 owner이며 B6가 승인한 movement target임을 증명할 때만 그 제거를 B8이 허용한다. 다른 CONTROL owner removal은 계속 차단된다.

현재 동일한 27-case corpus에서 B4 numeric Power authority / B5 owner / B6 authorization / full materialization은 3건, B7 eligible은 1건, B8 authorized와 B9 `B8_POWER_JUMP_V1` route도 1건이다. `persona3_mixed`만 주 2세트 × 4회 `lateral_bound_continuous`를 두 주에 materialize하고, 동시에 exact B6가 확인된 `CORE_DIRECT` 및 `HORIZONTAL_PUSH` movement owners도 검증해 통과했다. 나머지 21건은 CONTROL이다. Jump/Landing need, numeric B4, B5 owner, B6, materialization은 모두 0건이므로 Jump/Landing production route는 열리지 않았다.

기존 지역 Hypertrophy authority는 exact canonical direct regional relation을 요구한다. `UPPER_PULL`는 일부 case에서 그 regional target qualifier로 직접 계산되지 않아 현재 `DIRECTION_ONLY`로 남는다. 이것을 임의로 horizontal/vertical pull 두 region으로 확장하거나 8세트를 강제하지 않았다. 개인 numeric evidence와 direct metadata가 없는 경우 cold-start 8을 적용하지 않는다.

## 시간 배분·후보 및 사용자 교체

기존 canonical B5 ranking과 전체 finite allocation priority, scheduler 및 hard feasibility 검사를 유지했다. 남은 후보가 있다는 이유만으로 전부 넣지 않는다. 최신 corpus에서 218개 후보는 정상 탈락으로 기록된다. finite capacity shortfall은 B4 요구량과 분리해 보존된다. 새 시간·회복 정책을 만들지 않았다.

현재 저장 전 `ProgramSkeletonPreview`는 초안의 수동 편집 기능을 제공하지만, planner가 생성한 keep/replace 선택지 모델, 복수 교체 조합 검증, stale proposal 무효화/재검증, 선택 결과의 proposal provenance 기능은 구현하지 않았다. 이 UI는 이번 B8/B9 scope와 별도로 남는다. B8/B9가 승인한 결과만 정상 경로로 라우팅하며, CONTROL 기록을 B6 prescription authority로 사용하지 않았다.

## B7/B8, 호환성 및 검증

최신 route는 CONTROL 21 / `B8_POWER_JUMP_V1` 1이다. 전체 corpus의 B7은 eligible 4, not eligible 18이며 `CHANGE_PROVENANCE_UNCLOSED`가 18 case에 남는다. B8은 21건 `CONTROL_REQUIRED`, 1건 `AUTHORIZED_FOR_BOUNDED_CUTOVER`다. 승인된 한 사례 밖의 P/J scope는 계속 fail-closed다. C24 Task authority, Legacy Auto, Strength/Hypertrophy 정책과 분리는 유지된다. Room/backup schema 및 App version은 변경하지 않았다. 이전 C36/C37 incumbent source 지원도 유지된다.

## 로컬 검증 기록

- Power/Jump cap matrix와 처방 shape, B8 exact authority, B9 route 및 22-case C37/C36 regression을 포함한 집중 실행은 40 tests, 0 failures/errors로 통과했다.
- 첫 전체 `:app:testDebugUnitTest` 실행은 1,520 tests 처리 후 JBR 21의 `robolectric-nativeruntime.dll`에서 native `EXCEPTION_ACCESS_VIOLATION`으로 종료됐다. 그 시점의 결과 XML에는 assertion failure 0건, test error 0건이었다. 저장소 밖 JDK 17 + 직렬 worker `forkEvery=75` workaround로 재실행해 전체 2,408 tests, 0 failures, 0 errors, 4 skips로 통과했다.
- `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug`, protocol validation 및 `git diff --check`가 통과했다. 기존 deprecation/nullability compile warning은 남아 있다.
- 로컬 debug APK는 70,743,219 bytes이며 SHA-256은 `7A596CF2537DADA1A917707630365D5AF397CAB61EC2AF63105984E65AF6DFDA`다. compact C37 census SHA-256은 `897BEE81141083CE4606FD59CEB218B82877FBE375BE1E55F1D26E021C92911D`다.
- C37.2 구현·테스트 commit `2e115315ede6ffedae206e0fc33ad7a5fc4e5357`을 `lastAuditedCommit`으로 기록했다. Hosted CI run [38025498649](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38025498649)은 테스트, coverage 업로드, APK assembly, signer verification, APK 업로드까지 성공했다. APK artifact ID `11660795094` (artifact ZIP 65,480,844 bytes), APK 69,070,559 bytes, SHA-256 `7EE09BA5DAAC03841FFADEADDF465F1724BE358D853CFF9D761C82BA4960E25B`; coverage artifact ID `11660625144`. PR #24는 전체 사용자 선택/교체 범위와 Jump/Landing의 실제 B1 필요 경로가 아직 완성되지 않아 Draft로 유지한다.

### 남은 작업 경계

이 문서는 전체 사용자 요청 중 keep/replace 선택 화면 완료 선언이 아니다. C37.2는 corpus에서 정확한 B7/B8/B9 증거가 확보된 한 P/J 사례만 생산 경로로 라우팅한다. 나머지 사례는 CONTROL에 남고, Jump/Landing은 아직 need가 없어 materialization하지 않는다. Planner 생성 교체안, 조합 재검증, stale proposal 무효화와 저장 전 사용자 선택 UI는 별도 남은 작업이다.
