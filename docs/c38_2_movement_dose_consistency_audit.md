# C38.2 movement/dose 일관성 복구 — 부분 완료

## 판정

**PARTIAL.** 직접 movement coverage를 넓은 운동 슬롯만으로 부여하던 생산 경로를 수정했다. `cable_rear_delt_fly`가 수평 당기기 대표 운동으로 선택되던 잘못된 결과는 실제 generation 테스트에서 사라졌고, 선택·B4/B5/B6·주간 배정·최종 행까지 올바른 수직 당기기 운동으로 연결된다. 다만 기존 C38.1의 25개 미해결 pair를 모두 exact owner-week 원인으로 복구하지 않았고, B8 차단 5건도 의도한 범위의 독립 근거가 없어 승인되지 않는다. B7/B8을 완화하지 않았다.

## 기준과 범위

- 시작 HEAD `01efb9865953cb7a6172c396916667b77dac8610`, 브랜치 `c37-core-prescription-provenance`.
- 작업 종료 전 PR #24는 OPEN/Draft, `origin/main`은 `a4c1a0f4c6c9a35ac67247698a3c897ce2a6e063`; main 병합/수정 없음.
- 시작 시점 C38.1 비교 기준: 22개 생성 사례 중 B7 eligible 7 / ineligible 15, B8 authorized 2 / control 20, B9 EXP 2 / CONTROL 20. C38.1의 25 unique `(case, stableKey, selectionRole)` / 50 owner-week 중 9개는 exact B5 대안 없음, 7개는 B6 권한 부족, 9개는 배정·materialization 부족으로 보고됐다. C38.1에서 UPPER_PULL 대체로 닫았던 11개 pair는 잘못된 rear-delt/미검토 identity 분류가 섞였으므로 유효한 해결로 승계하지 않았다.

## 고친 생산 경로

`CanonicalMovementCoverageResolver`는 canonical `MOVEMENT_PATTERN`, movement family, runtime slot 및 identity review 상태가 서로 맞는 경우에만 direct movement coverage를 준다. `TrainingRepository`에서 읽은 canonical identity/relation이 `PersonalizedProgramPlanningService`, planning snapshot builder와 `AthletePlanningStateBuilder.movementCoverage()`까지 전달된다. 따라서 실행 생성·후보 자격·regional evidence가 예전 넓은 slot 매핑을 그대로 물려받지 않는다. slot 이름 하나로 운동 자격을 발급하지 않는다.

`cable_rear_delt_fly`, `dumbbell_rear_delt_fly`, `ex_7176cbee`, `ex_7deabeba`, `ex_a60496be`는 직접 horizontal/vertical/aggregate pull coverage를 갖지 않는다. 이 운동들의 검증된 어깨 HYP 지역 품질 관계는 별도 regional ledger에서 계속 사용할 수 있다. 같은 방식으로 lateral raise는 vertical-push direct coverage가 아니고, pullover는 vertical-pull direct coverage가 아니며, chest fly는 horizontal-push direct coverage가 아니다. 해당 운동에 정확한 지역 품질 관계가 있을 때만 그 지역 장부에 기여한다. 직접/보조 환산 계수는 변경하지 않았다.

테스트가 canonical selectable identity 242개 전부를 열거하여 runtime slot/family, movement pattern/family/joint action, identity pattern/review, physical-quality region/mode, 최종 coverage 및 사유를 `app/build/reports/c38-2-selectable-movement-semantics.json`에 생성한다. 115개가 direct/exact-region coverage를 통과하고 127개는 movement-direct 자격을 받지 않는다. 28개 identity/canonical movement 충돌과 14개 identity review 미완료 사례는 fail-closed로 남긴다. `chest_supported_row_machine`도 identity review가 끝나지 않아 아직 direct owner가 아니다. 양성 경계는 `ex_fa31f7a6` row = horizontal pull, `ex_dc9e5953`/`ex_e41f4c2b` pull-up 계열 = vertical pull, `ex_32219f7a` overhead press = vertical push, `barbell_bench_press` = horizontal push로 테스트한다.

검사에서 rear-delt fly 다섯 identity의 `BACK_LATS`, `ANTERIOR_CORE`, `LATERAL_CORE` inherited `PRIMARY` muscle relation 10행은 실제 분석/선택/피로 경로에 쓰이는 것으로 확인되지 않아 제거했다. shoulder 관계는 유지했다. 해당 muscle relation은 주간 anatomy feature와 metadata semantic revision에 입력되므로 revision/hash도 갱신했다. 이 정리는 movement coverage 판정의 근거를 대신하지 않는다.

## 동일 22-case 재생 결과

`StimulusProductionCoverageAuditTest.measureUnmodifiedServiceCoverage`를 동일 corpus에서 다시 실행했다. 22건 중 20건은 comparison을 만들었고 2건은 사전 생성 불가였다. 최신 집계는 B7 eligible 8 / ineligible 12(20 comparison), B8 authorized 3 / control 17(20 comparison), B9 HYP 1 + Power/Jump 2 + CONTROL 19(22 generated results, comparison 불가 포함)다. CONTROL 19가 자동 개선 또는 19건의 원인 폐쇄를 뜻하지 않는다. 2건은 유효한 비교 결과가 없고 미승인 변경은 원본 경로를 반환한다.

대표적인 실제 전후 차이는 `persona0_sparse`다. 전에는 `cable_rear_delt_fly`가 `CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL`로 선택되어 UPPER_PULL 부족량을 독점 충족하는 잘못된 후보였다. 수정 후 B4는 기존 need 판단 뒤 지역 HYP cold-start authority 8 equivalent units를 연결하고, B5는 적격 `ex_32eb8457` 수직 당기기를 골랐다. B6는 주 8세트 × 8회, RPE 최소 7, load `USER_CALIBRATION_REQUIRED`를 승인했고 두 주 각각 8세트가 실제 생성됐다. 0.0은 내부 표현값일 뿐 확정 중량이 아니다. UPPER_PULL 전용 quota는 추가하지 않았다. B7은 eligible이나 현재 B8 범위가 STRENGTH라 HYP 변경을 승인하지 않고, B9는 CONTROL을 반환한다. 상세 chain은 생성된 `app/build/reports/c38-2-rear-delt-production-case.json`에 있다.

최신 C35/C36 production census는 75 regional HYP target rows, raw residual 주당 600, authorized 600, funded/materialized 주당 464(2주 horizon 928), unfunded 136이다. B4 필요량을 낮추지 않았고 capacity를 키우지도 않았다. regional overrun 0, duplicate physical rows 0, unauthorized material 0, third build 0이다. 현재 B7 reason에는 change provenance unclosed 12 cases, true target regression 1, collateral regression 1이 남는다. C35 집계 경로에서 B8 authorized 3 / control-required 17이다.

## 이전 제거 pair 재분류와 미완료 범위

기존 C37.5의 32 unique pair 재현 리포트(`app/build/reports/c38-2-prior-removal-reclassification.json`)는 5건 comparison unavailable, 16건은 현재 CONTROL 출력 변경으로 옛 row 자체가 비교에 나타나지 않음, 10건은 exact owner가 여전히 제거, 1건은 같은 운동의 role 변경으로 분류했다. 출력에서 사라진 16건은 provenance 폐쇄가 아니며 해결로 세지 않는다. 이는 C38.1 25 pair 목록과 동일 모집단도 아니므로 25를 32 결과와 빼서 해결 건수를 만들지 않았다.

새 C36 census는 20 comparison 중 unexplained removed identity 19 unique case-owner pairs / 38 owner-week을 기록한다. 이 집계와 C38.1의 기존 25 unique pair를 정확히 pair-to-pair 조인하는 고정 machine-readable C38.1 원본이 없어 9/7/9 각각의 전후 해소 수는 입증되지 않았다. 따라서 기존 25개 중 해결했다고 주장하지 않는다. 현재 제거가 남은 구체 사례에는 squat `CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`, bench `PLANNED_RESISTANCE_LOAD_UNAVAILABLE`, 일부 regional owner의 exact B5/B6 연결 또는 materialization 부족, `cable_overhead_triceps_extension`의 exact alternative 부재가 있다. exact B4/B5/B6 및 owner-week scheduled row가 없으면 기존 운동 제거는 계속 차단 대상이다.

B7 eligible인데 B8이 별도로 거부한 다섯 사례도 그대로 fail-closed다. `persona0_mixed`, `persona0_sparse`, `persona4_mixed`, `reviewed_strength_isolated`는 STRENGTH scope 바깥 HYP/기타 변경, exact B5/B6 및 full materialization/parity 근거 부족을 포함한다. `persona2_sparse`는 HYP scope지만 B6 authorization의 missing week/shortfall이 있다. B8 scope를 넓히거나 predicate를 완화하지 않았다.

따라서 A(기존 9 pair의 typed incumbent preservation), B(7 B6 blocker의 원인별 기록 복구), C(9 schedule/materialization blocker의 실제 재배정 또는 owner-local shortfall provenance), D(B8 5개 scope별 검증) 전체는 끝나지 않았다. 특히 현재 B8 HYP/Power/Jump scope와 별개인 Strength change를 이 작업으로 허가할 근거가 없고, 뒤늦은 row 복사로 provenance를 채울 수도 없다. 이 상태는 요청의 완료 기준을 충족하지 않아 PARTIAL로 판정한다.

## 검증

- production corpus 감사: `StimulusProductionCoverageAuditTest.measureUnmodifiedServiceCoverage` 성공, 22-case 재생.
- focused movement/regional 및 production 반례: `CanonicalMovementCoverageResolverTest`, `RegionalAuthorityTest`, `RegionalBottleneckDiagnosisTest`, `rearDeltFlyCannotSatisfyOrOwnUpperPullInProductionGeneration` 성공.
- 생성 경로 계측: 각 generated case CONTROL 1 + EXP 1, third build 0.
- protocol registry: Protocol 3.78.0 / Runtime `RECORD_BASED_PLANNER_0.15.20_KOTLIN_1`; App 0.5.1.5 / Room 38.
- 로컬 IPC workaround `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`와 저장소 외부 JDK 17 test worker 설정을 사용했다. 전체 JVM suite는 2,446 tests / 0 failures / 0 errors / 4 skipped로 통과했다. 첫 실행에서 cold-start B8 테스트 fixture의 휴식값(행 90초, 승인 처방 120초) 불일치가 드러나 승인 처방과 동일하게 fixture를 수정했고, 해당 테스트와 전체 suite 재실행이 통과했다. 새 native crash는 발생하지 않았다.
- `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` 성공. protocol validator는 9 families / 36 protocols 통과, `git diff --check` whitespace 오류 없음.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 72,729,550 bytes, SHA-256 `66BC11F995D35B372AE6C82D2DAA9439819CB0F9476F95BF57C59DB73C5B77A0`.
- 코드 구현 커밋 `2f14a390b25fa9f0bf045982b1443dc06c9453da`의 Hosted CI [run 38150739169](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38150739169)은 success였다. 원격 unit tests, coverage/report artifact upload, APK assemble, CI keystore signer verification, APK artifact upload가 모두 통과했다.
- Hosted coverage artifact: [Stimulus-production-coverage](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38150739169/artifacts/11706479849), artifact SHA-256 `86e3fbe7625e5789871eb0aadfc52a54dee884e3a2cf40a0527aa399fae727f9`. Hosted signed APK: [WhatYouGottaDo-debug-apk](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38150739169/artifacts/11706203457), 69,217,783 bytes, APK SHA-256 `97C21CE19B416678899C56FB8F1B1D8790E2653A33C1844CAC7FF9A5B5953562` (artifact archive SHA-256 `5e29750db64e63b684469a1451ca96fa3854b4f1a751220a8857479358d525b7`).
- 변경은 Draft PR #24 브랜치에 push했으며 main에는 병합하지 않았다. 이 audit 문서의 후속 변경은 CI run 38150739169 이후 추가되므로, 최종 PR SHA의 CI 결과는 별도로 확인한다. C38.2는 위의 owner-week/B4/B6/B8 미해결 범위 때문에 PARTIAL이다.
