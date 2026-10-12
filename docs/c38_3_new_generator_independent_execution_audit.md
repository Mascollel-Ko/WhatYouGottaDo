# C38.3 새 생성기 독립 실행 감사

## 실행 계약

일반 프로그램 생성은 기록·제약을 읽어 B1–B6를 실행하고 `StimulusProductionExecutionValidator`로 결과를 검증한 뒤 새 생성기 결과를 반환한다. 기본 경로는 CONTROL을 만들지 않는다. B7/B8은 요청자가 비교 진단을 명시한 때만 수행하며, B9는 선택 출처와 진단 상태를 기록한다. B7의 출처 불일치와 B8 `CONTROL_REQUIRED`는 독립 검증을 통과한 결과의 반환을 막지 않는다.

독립 검증을 통과하지 못한 생성은 `StimulusProductionExecutionRejectedException`으로 종료한다. 사용자가 고르지 않은 CONTROL 계획으로 대체하지 않는다. 독립 생성에서 계산된 부족량, 보류 이유, 사용자 중량 보정 요청은 typed result와 프로그램 사용자 안내에 남긴다.

CONTROL은 비교 진단 또는 호출자가 명시한 `CONTROL_ONLY` 경로에서만 생성·선택된다. 프로덕션 기본 경로는 `includeComparisonDiagnostics=false`, `NEW_GENERATOR_ACTIVE`다. 진단 CONTROL은 일회성 비교 입력이며 사용자 저장 프로그램으로 취급하지 않는다.

## 독립 실행 검증 계층

- B1–B4: 사용자 기록과 목표·빈도·시간·장비·필수/제외 조건을 사용해 개인별 필요량과 주간 목표를 만든다. 기존 C38.1/C38.2 canonical movement, UPPER_PULL 통합, 지역 자극 및 리어델트 분류 변경을 보존한다.
- B5: 각 후보가 B4 목표에 실제로 직접 기여하는지, 현재 운동 분류와 장비가 맞는지, 선택된 stableKey/selectionRole이 정당한지 확인한다. 리어델트 플라이는 UPPER_PULL 소유자가 될 수 없다.
- B6: 기록된 강도·중량 근거 또는 승인된 cold-start 사용자 보정을 사용해 세트·반복·중량 상태·RPE를 만든다. 숫자 중량을 근거 없이 추정하지 않는다. B6 권한 없는 운동 행은 실제 계획에 다시 유입되지 않는다.
- 최종 실행 검사: 프로그램 행/세트 중복, schedule의 주간 빈도와 요일, 세션 시간, OFI·관절/건/인대 회복, canonical 분류, 필수/제외 운동, B4→B5→B6 owner 연결, task protocol, B6 물리 세트 수·처방 무결성을 검사한다. 검사기는 CONTROL, B7, B8을 입력받지 않는다.
- 실행 불가 상태: 전체 결과의 실행 조건이 충족되지 않으면 typed 실패와 정확한 부족 코드/사용자 입력 필요를 반환한다. 독립적으로 실행 가능한 부분을 반환하는 경우 빠진 요구량은 별도 unresolved/deferred gap과 사용자 안내로 남긴다.

사용자 저장 프로그램은 `CanonicalIncumbentSaveMutationGuard`와 Room 저장 경계가 실제 영속된 프로그램·행을 기준으로 보호한다. 진단 CONTROL과 저장 전 Preview를 사용자 소유 저장 데이터로 취급하지 않는다. 일반 경로와 명시 진단 경로는 `normalNewGeneratorPreviewSaveRoomReloadAndDateApplyKeepsExactProgram`, `b8DiagnosticModeStillReturnsValidatedNewGeneratorThroughRoomAndDateApplication`에서 Preview→저장→Room 재열기→날짜 적용까지 확인한다.

## 기존 22-case 코퍼스 결과

기존 production-eligible 22개 입력 중 14개는 검증을 통과해 `NEW_GENERATOR` 결과를 반환했고, 3개는 B6 사용자 effort/load 입력이 필요했으며, 5개는 OFI/회복 검사를 통과하지 못해 typed 생성 실패를 반환했다. 같은 실행에 포함한 무기록 사전 입력 5개는 사전 질문/입력 보완 필요로 분류했다. 실패 사례에서는 CONTROL 생성 단계가 호출되지 않는다.

아래 운동은 매주 반복되는 prescription이다. `8×8`은 주 8세트, 세트당 8회다. `보정 필요`는 `0 kg / USER_CALIBRATION_REQUIRED`이며 숫자 중량 대신 사용자 중량 설정이 필요하다는 뜻이다. `N/A`는 외부 중량이 필요하지 않고 RPE 대상이 없는 처방이다. `명시 40 kg`는 기록된 중량 근거를 사용한다. `—`는 RPE가 처방되지 않았음을 뜻한다. schedule 날짜는 요일 번호다.

| 사례 | 반환/진단 | 주간 요일 | 새 생성기가 선택·처방한 운동 (stableKey, selectionRole, 세트×반복, 중량 상태, RPE) |
|---|---|---|---|
| persona0_mixed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 4 | D1 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7, 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A; D4 원암 랫풀다운 `ex_32eb8457` / UPPER_PULL 8×8 보정 필요 RPE7. POSTERIOR_CHAIN은 부족/보류로 기록. |
| persona0_recent | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6 | D1 벤치프레스 `barbell_bench_press` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D2 데드리프트 `barbell_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D4 링 로우 `ex_282a9d87` / UPPER_PULL 8×8 보정 필요 RPE7; D6 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A. Strength 목표 자체는 `NO_EXECUTABLE_STRENGTH_PRESCRIPTION` shortfall로 기록. |
| persona0_reviewed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6, 7 | D6 벤치프레스 `barbell_bench_press` / QUALITY:STRENGTH 2×6 보정 필요 RPE6.5; D1 루마니안 바벨 데드리프트 `barbell_romanian_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D2 클로즈그립 벤치프레스 `ex_27b3deb5` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D4 원암 덤벨 로우 `ex_30a0e9aa` / UPPER_PULL 8×8 보정 필요 RPE7. |
| persona0_sparse | 생성, B7 ELIGIBLE / B8 CONTROL_REQUIRED | 1, 3, 5 | D1 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D5 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A; D3 원암 랫풀다운 `ex_32eb8457` / UPPER_PULL 8×8 보정 필요 RPE7. POSTERIOR_CHAIN은 부족/보류. |
| persona1_reviewed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 4 | D1 케이블 리어델트 플라이 `cable_rear_delt_fly` / QUALITY:HYPERTROPHY 3×10 명시 40 kg RPE7. UPPER_PULL 역할로 분류하거나 합산하지 않음. |
| persona2_mixed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6 | D1 스쿼트 `barbell_back_squat` / QUALITY:STRENGTH 3×5 명시 40 kg RPE— 및 벤치프레스 `barbell_bench_press` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D2 데드리프트 `barbell_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D4 링 로우 `ex_282a9d87` / UPPER_PULL 8×8 보정 필요 RPE7; D6 바벨 리버스 컬 `barbell_reverse_curl` / ARMS_BICEPS 8×8 보정 필요 RPE7. ARMS_TRICEPS는 부족/보류. |
| persona2_sparse | 생성, B7 ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6, 7 | D1 벤치프레스 `barbell_bench_press` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D2 데드리프트 `barbell_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D4 바벨 리버스 컬 `barbell_reverse_curl` / ARMS_BICEPS 8×8 보정 필요 RPE7. Strength target shortfall와 ARMS_TRICEPS 부족을 별도 기록. |
| persona3_mixed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6, 7 | D1 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D2 원암 랫풀다운 `ex_32eb8457` / UPPER_PULL 8×8 보정 필요 RPE7; D4 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A; D6 래터럴 바운드 `lateral_bound_continuous` / QUALITY:POWER 2×4 N/A. BADMINTON_FOUNDATIONAL_ONRAMP 및 POSTERIOR_CHAIN 부족/보류. |
| persona3_reviewed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6 | D6 벤치프레스 `barbell_bench_press` / QUALITY:STRENGTH 2×6 보정 필요 RPE6.5; D1 루마니안 바벨 데드리프트 `barbell_romanian_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D2 클로즈그립 벤치프레스 `ex_27b3deb5` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D4 원암 덤벨 로우 `ex_30a0e9aa` / UPPER_PULL 8×8 보정 필요 RPE7. BADMINTON_FOUNDATIONAL_ONRAMP 부족/보류. |
| persona3_sparse | 생성, B7 ELIGIBLE / B8 AUTHORIZED | 1, 4 | D1 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7 및 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A; D4 원암 랫풀다운 `ex_32eb8457` / UPPER_PULL 8×8 보정 필요 RPE7 및 래터럴 바운드 `lateral_bound_continuous` / QUALITY:POWER 2×4 N/A. BADMINTON_FOUNDATIONAL_ONRAMP 및 POSTERIOR_CHAIN 부족/보류. |
| persona4_recent | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6 | D1 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D2 원암 랫풀다운 `ex_32eb8457` / UPPER_PULL 8×8 보정 필요 RPE7; D4 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A. ARMS_TRICEPS, CALVES, POSTERIOR_CHAIN 부족/보류. |
| persona4_reviewed | 생성, B7 NOT_ELIGIBLE / B8 CONTROL_REQUIRED | 1, 2, 4, 6, 7 | D4 스쿼트 `barbell_back_squat` / QUALITY:STRENGTH 3×5 명시 40 kg RPE—; D1 벤치프레스 `barbell_bench_press` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7 및 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A; D2 데드리프트 `barbell_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D4 링 로우 `ex_282a9d87` / UPPER_PULL 8×8 보정 필요 RPE7; D6 바벨 리버스 컬 `barbell_reverse_curl` / ARMS_BICEPS 8×8 보정 필요 RPE7; D7 덤벨 라잉 트라이셉스 익스텐션 `dumbbell_lying_triceps_extension` / ARMS_TRICEPS 8×8 보정 필요 RPE7. ARMS_TRICEPS 계획 부족도 기록. |
| reviewed_hypertrophy_isolated | 생성, B7 ELIGIBLE / B8 AUTHORIZED | 1, 3, 5 | D1 케이블 리어델트 플라이 `cable_rear_delt_fly` / QUALITY:HYPERTROPHY 3×10 명시 40 kg RPE7. 저항운동 기초 노출은 부족/보류. |
| reviewed_strength_isolated | 생성, B7 ELIGIBLE / B8 CONTROL_REQUIRED | 1, 3, 5 | D5 스쿼트 `barbell_back_squat` / QUALITY:STRENGTH 3×5 명시 40 kg RPE—; D1 루마니안 덤벨 데드리프트 `dumbbell_romanian_deadlift` / POSTERIOR_CHAIN 8×8 보정 필요 RPE7; D3 머신 체스트프레스 `ex_1dbee10e` / HORIZONTAL_PUSH 8×8 보정 필요 RPE7; D5 버드독 `ex_28347c1f` / CORE_DIRECT 6×8 N/A. UPPER_PULL 부족/보류. |

나머지 8개 production 입력은 자동 대체하지 않고 실패 또는 입력 보완을 요구했다.

| 사례 | 반환하지 않은 이유 |
|---|---|
| persona1_mixed | B6 사용자 effort/load 보정 필요: `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY` |
| persona1_recent | B6 사용자 effort/load 보정 필요: `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY` |
| persona1_sparse | B6 사용자 effort/load 보정 필요: `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY` |
| persona2_recent | OFI/회복 한도 실패: W1/W2 day 1 OFI 93, day 4 OFI 79 |
| persona2_reviewed | OFI/회복 한도 실패: W1/W2 day 1 OFI 67; rebalancer가 가능한 배정으로 해결하지 못해 실패 |
| persona3_recent | OFI/회복 한도 실패: W1/W2 day 1과 5 OFI 90; 결합조직 회복 검사도 실패 |
| persona4_mixed | OFI/회복 한도 실패: W1/W2 day 1 OFI 94, day 4 OFI 89 |
| persona4_sparse | OFI/회복 한도 실패: W1/W2 day 1/3/5 OFI 75/77/72 |

무기록 사전 입력 `persona0_none`, `persona1_none`, `persona2_none`, `persona3_none`, `persona4_none`은 B1–B6에 전달하기 전 preflight가 입력을 요구했다. 이들은 생성 실패나 레거시 결과가 아니라 아직 답을 받지 않은 입력 상태다.

전체 실행 자료(요일·운동명·stableKey·역할·세트·반복·중량 상태·RPE·선택 이유·B4/B5/B6 권한·충족/부족량·OFI/회복 검사·진단 코드)는 테스트가 생성한 `app/build/reports/c38-3-new-generator-independent-execution-census.json`에 기록된다. 기존 25/19개 CONTROL 대비 제거 관계는 복구 기준으로 사용하지 않았다.

## 대표 구성 검토

- 스쿼트가 독립 strength B4/B5/B6 요구를 받은 `persona2_mixed`, `persona4_reviewed`, `reviewed_strength_isolated`에 포함되고, 데드리프트는 `persona0_recent`, `persona2_mixed`, `persona2_sparse`, `persona4_reviewed` 등에 포함된다. 각 요청에서 B4가 요구하지 않은 경우까지 보존하지 않는다.
- UPPER_PULL은 링 로우 `ex_282a9d87`, 원암 랫풀다운 `ex_32eb8457`, 원암 덤벨 로우 `ex_30a0e9aa`로 정확히 분류되어 B4의 당기기 자극을 채운다. 리어델트 플라이는 hypertrophy 역할로만 선택된다.
- 훈련 일수는 요청과 B4/B5 제약을 기준으로 사례마다 2·3·4·5일이며, 요구되지 않은 요일을 채우지 않는다.
- 사용자 기록의 `barbell_back_squat` 40kg 기반 처방은 명시 중량 `3×5 @40kg`; 기록된 리어델트 플라이 40kg은 `3×10 @40kg, RPE7`로 실현된다. cold-start/다른 운동은 숫자 중량을 조작하지 않고 사용자 보정 `0kg USER_CALIBRATION_REQUIRED, RPE7`로 표시된다.
- 단일 운동·분할은 legacy CONTROL row와 무관하다. 예를 들어 `persona1_reviewed`의 rear-delt isolation은 UPPER_PULL 역할을 대체하지 않는다.

## 남아 있는 레거시 연결

정상 새 생성 경로에서 제거된 연결은 “CONTROL 생성→B7/B8 승인→B9 fallback”이다. B1–B6 준비, 독립 실행 검증, 기본 반환에는 CONTROL 후보가 없다. `materializeLateControl()`은 `includeComparisonDiagnostics=true`인 경우에만 호출되며, default는 false다. B7/B8 비교 결과는 `StimulusProductionGenerationResult.comparison`의 진단 필드로만 남고 B9 실행은 validated new program을 선택한다. CONTROL이 선택되는 경로는 호출자가 명시한 `CONTROL_ONLY` 진단 경로다.

기존 사용자 기록에서 B2 ledger 등으로 읽는 legacy 호환 데이터와 이미 저장된 구형 프로그램/진행 연결은 분석·저장 호환성을 위해 남아 있다. 이것은 새 프로그램 후보·처방·반환 선택권을 주지 않으며, 영속 Room 행 보호는 계속 적용한다.

## 로컬 및 배포 검증

- 전체 `:app:testDebugUnitTest`: 2,442 tests, 0 failures, 0 errors, 4 optional skips.
- 집중 독립 실행/라우팅/Room 회귀: 통과. 포함 테스트: B7/B8 진단 불승인에도 new 반환, 실제 B6/회복 위험 거부, Preview→저장→Room 재열기→날짜 적용, 사용자가 저장한 원본의 무단 덮어쓰기 방지.
- `python scripts/validate_protocol_docs.py`: 통과 (9개 계열, 36개 프로토콜).
- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug`: 통과. Debug APK `app/build/outputs/apk/debug/app-debug.apk`, 69,255,011 bytes.
- `apksigner verify --print-certs`: 통과. Debug signer SHA-256 `318c7551fde53d6972d03b0c61524245bf2b0c797cb505fa970451d2a746da88`; APK SHA-256 `EC6DAB8C4EFBE6D9915F510AB5323DA300418F4BED0A0E6E0EE42D8227C18076`.
- `git diff --check`: 종료 코드 0 (공백 오류 없음; Git은 CRLF 변환 경고만 표시).
- Hosted GitHub Actions run [38213001269](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38213001269), 구현 SHA `07085e30546c4c5b5d2112552ea877958331035a`: 성공. Protocol/contract/whitespace 검사, 2,442 JVM tests(0 failures, 0 errors, 4 skips), APK 조립, CI 키와 서명 일치 검사, APK 업로드가 모두 통과했다.
- Hosted `Stimulus-production-coverage` artifact(368,395 bytes)에서 census schema v3의 27행을 확인했다: 14 generated, 5 preflight input required, 8 execution rejected(22개 생성 가능 입력 corpus + 별도 5개 no-history preflight). 업로드된 JUnit XML도 2,442/0/0/4를 확인했다.
- Hosted `WhatYouGottaDo-debug-apk` artifact(65,652,981 bytes)를 내려받아 `apksigner`로 재검증했다. APK SHA-256 `6246CAC293C1C0DEC0EA4083C067E261808193C826BAE03FDE9C2179F894A6D8`; CI signing certificate SHA-256 `a37b8a11810408005e8dc45bd83bec29769404ca8e3041d2c41af546b344d3e4`.
- 후속 문서 확인 시 PR #24 HEAD `0831340f76f11ef90ea24101b77a1f4930483003`도 Hosted run [38214350710](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/38214350710)에서 전체 성공(11m47s)했고, APK 서명 검사와 업로드까지 통과했다. PR #24는 `main` 대상 OPEN/DRAFT 상태이며 main에는 병합하지 않았다.
