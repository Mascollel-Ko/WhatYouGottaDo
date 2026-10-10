# C38.0 — 기존 canonical 운동 저장 보호

## 판단

C37.5의 32개 미해결 관계는 최신 production corpus에서도 그대로 재현됐다. 이는 저장된 사용자의 운동 32개가 실제로 삭제됐다는 뜻이 아니라, 22개 비교 사례에서 CONTROL과 EXP의 `(case, stableKey, selectionRole)` 관계가 32쌍, 64 owner-week에 걸쳐 B7에서 설명되지 않았다는 뜻이다. C38은 근거가 없는 B4/B6 처방을 만들거나 B7/B8 기준을 낮추지 않았다. 대신 실제 Room의 canonical 프로그램을 새 기록 기반 생성 결과로 덮어쓸 때 기존 운동·처방·progression 연결이 승인 없이 사라지는 저장 경계를 막는다.

## 최신 corpus 재현

| 결과 | 건수 |
|---|---:|
| 생성 사례 / CONTROL+EXP build | 22 / 44 |
| 미해결 고유 `(case, stableKey, selectionRole)` | 32 |
| 해당 owner-week 관측 | 64 |
| exact B5 대안 없는 관계 | 9 |
| B5 후보는 있으나 exact B6 권한 없는 관계 | 18 |
| B4/B6 probe는 있으나 funded/materialized 0인 관계 | 5 |
| B7 eligible / not eligible | 4 / 18 |
| B8 authorized / CONTROL required | 1 / 21 |
| B9 EXP / CONTROL | 1 / 21 |
| third build | 0 |
| duplicate physical rows / unauthorized material / regional overrun | 0 / 0 / 0 |

18개의 B6 미승인 관계 중 11개는 `UPPER_PULL` 방향성만 있고 별도 numeric B4가 없다. 이 정책에서 UPPER_PULL은 pull 방향의 균형 관찰로 유지하며, 독립 세트 목표를 만들지 않는다. 나머지 7개는 posterior reference 부족 2, 계획된 load 근거 부족 1, Hypertrophy numeric authority 부족 2, 안전한 load/effort authority 부족 2다. 0 funded 다섯 관계는 `persona1_recent` 종아리 8→0, `persona2_mixed` reverse curl 8→0·bird dog 6→0·single-leg calf raise 8→0, `persona4_recent` 종아리 8→0이다. 실제 주간 배정이 0인 대체 후보는 기존 운동을 대체했다고 인정하지 않는다.

따라서 이 32쌍에서 B4/B5/B6의 기존 차단 원인은 해소되지 않았고, B7/B8/B9 결과도 전후 동일하다. B11 후보, 같은 근육 관계, 동일 stableKey만으로 새 처방이나 제거 근거를 만들지 않았다. C37.5의 상세 owner-week dossier와 census는 그대로 유지했다.

## 저장 경계 수정

`ProgramPlanService.saveGeneratedProgram()`은 Room transaction 안에서 저장된 프로그램과 item/set rows를 읽고, 기존 source snapshot token을 재검증한 다음에만 삭제·재삽입한다. 토큰은 program/item/set 필드뿐 아니라 관련 progression item과 track 상태도 포함하므로, 생성 중 progression 연결이 바뀌면 stale save로 거부된다.

저장된 각 exact `(stableKey, selectionRole, week)` 그룹의 물리 처방이 최종 초안과 완전히 같으면 변경 authority를 요구하지 않는다. `selectionRole`이 다르면 별도 owner로 취급한다. 저장된 owner-week가 바뀌거나 제거될 경우에는 동일 초안 fingerprint, eligible B7, 닫힌 provenance 및 collateral 검사, exact B8 scope/owner, 활성 B9 route와 해당 owner의 B7 attribution이 모두 필요하다. 제거는 추가로 사용자가 승인한 exact replacement edge가 기존 CONTROL 행·대상·대체 owner·해당 주차 materialization을 연결하고 대체 owner가 B8에서 승인돼야 한다. 이 증거가 없으면 transaction을 abort하고 원래 저장 프로그램을 보존한다.

동일 stableKey/role/week의 set prescription 전체가 유지되면 위치가 검증된 변경으로 바뀌더라도 기존 progression item의 logical ID와 track 연결을 새 Room row ID에 다시 연결한다. 처방이 달라지면 이 자동 연결을 하지 않는다. 저장된 row가 아닌 신규 생성 identity는 이 보호 guard에서 authority를 얻지 않으며 기존 B6/B7/B8/B9 경로를 통과해야 한다.

## 검증

- guard 단위 테스트: 변경 없는 보존, 신규 행과 incumbent 삭제의 구분, 무근거 삭제·처방 변경 차단, 같은 key의 role 변경 차단, stale fingerprint 차단, B7/B8/B9가 정확한 owner를 승인한 경우, B8 거부 및 owner 목록 불일치 차단.
- Room persistence 테스트: 오래된 token 거부, fresh token만으로는 기존 행 이동/삭제를 승인하지 않음, 거부 뒤 기존 행 유지, 동일 owner-week 재저장, progression logical ID와 track 유지.
- C37.5 production coverage: 27-case harness 중 22 generated / 5 preflight rejected. C36/C37 census의 32쌍·64 owner-week와 B7/B8/B9 분류를 재현했으며 기존 route/build 안전 invariant를 유지했다.
- C37.5 corpus 실행: `StimulusProductionCoverageAuditTest`, 6 tests, 0 failures, 0 errors, 0 skips.
- Guard 및 Room 집중 테스트: 12 tests, 0 failures, 0 errors, 0 skips. C37.5 corpus 집중 실행은 별도로 6 tests, 0 failures, 0 errors, 0 skips.
- 전체 JVM suite: 364 suites, 2,430 tests, 0 failures, 0 errors, 4 skips. 기본 JBR 21 실행은 Robolectric native SQLite의 `EXCEPTION_ACCESS_VIOLATION`로 worker 연결이 reset되었고, 그 시점의 부분 XML은 1,536 tests / 0 failures / 0 errors / 3 skips였다. 저장소 밖 JDK 17 test worker와 `forkEvery=75`로 다시 실행해 전체 통과를 확인했다. 임시 Gradle init script는 OS temp 아래에서 실행했다.
- `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug`, protocol validator (9 families / 36 protocols), `git diff --check`: 모두 통과.
- Local debug APK: 72,729,550 bytes, SHA-256 `339955FA5BDC04C757111B7E361BEDE9AA1B36BC45215D484449DA2EF2E3CC1C`.
- Protocol/runtime는 저장 mutation contract 때문에 `3.75.0` / `RECORD_BASED_PLANNER_0.15.17_KOTLIN_1`로 갱신했다. App `0.5.1.5`, Room `38`, CSV/cloud backup 계약은 변경하지 않았다.
- 구현·테스트 commit: `df5504cabb2ec31a21a450ce2e4eddfb04e94a6d`.

## Hosted 결과

- 구현/test SHA `a5289ba7d834bd0af5010292a1038c26bfdfe597`의 [Hosted CI run 38069751951](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38069751951)은 성공했다. Hosted JUnit 결과는 364 suites, 2,430 tests, 0 failures, 0 errors, 4 skips다. 프로토콜 검사, community/cloud 계약 검사, coverage·test report 업로드, APK assemble, signer 검증 및 APK 업로드가 모두 통과했다.
- Signed debug APK artifact [11676566552](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38069751951/artifacts/11676566552): 69,201,531 bytes, SHA-256 `CD4512FE86B07E33C94DA80C1A233AAEB1A7BF681CD7CAAA5B3DCD7A58B3D266`.
- Coverage artifact [11675883710](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38069751951/artifacts/11675883710).
- 해당 Hosted run 시점 PR #24은 OPEN/Draft였고 main에 병합되지 않았다. run은 구현·테스트 SHA `a5289ba7d834bd0af5010292a1038c26bfdfe597`를 대상으로 했으며, 당시 `origin/main`은 시작 기준 `a4c1a0f4c6c9a35ac67247698a3c897ce2a6e063`였다.
