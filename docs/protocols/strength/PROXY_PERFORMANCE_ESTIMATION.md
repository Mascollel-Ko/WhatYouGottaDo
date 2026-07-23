# 지속형 근력 수행능력 사후분포

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-PROXY-PERFORMANCE |
| Protocol version | 2.0.0 |
| Status | EXPERIMENTAL |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.1 |
| Last audited commit | 43f11ec |
| Evidence profile | DIRECT_RESEARCH_SUPPORT, PRODUCT_POLICY, ENGINEERING_HEURISTIC, LOW_CONFIDENCE_PROXY |
| Supersedes | 1.0.0 |

이 문서는 완료 세션 이벤트로만 갱신되는 벤치프레스, 스쿼트, 데드리프트, 중량 풀업 수행능력 사후분포의 단일 canonical 계약입니다. v0.5.0.1의 화면 진입 시 재계산 프록시 posterior는 권위 경로에서 제외되며, 기존 Epley 계열은 과거 비교값으로만 남습니다.

## 1. 일반 사용자용 요약

운동 세션이 실제로 완료될 때 확인된 세트와 관련 운동의 제한된 신호를 반영해 현재 수행능력의 중앙값과 80% 범위를 갱신합니다. 과거 그래프는 당시 저장된 값을 그대로 보여 주며, 이후 기록 수정·삭제나 체중 변경으로 다시 쓰지 않습니다. 결과는 수행능력 추정이지 직접 측정값이나 경기력 보장이 아닙니다.

## 2. 목적

- 직접 1RM 사이의 간격을 비선형 반복 곡선과 sparse proxy factor로 보수적으로 연결합니다.
- 화면 조회가 아니라 완료 이벤트를 유일한 update 원인으로 만듭니다.
- 세션 전 prior, 세션 관측, 세션 후 posterior와 버전·곡선·체중 출처를 불변 이력으로 보존합니다.
- 신규 target을 enum·Room column 추가 없이 string registry row로 확장할 수 있게 합니다.

## 3. 적용 범위

초기 target key와 직접 anchor stable key는 다음과 같습니다.

| target key | 표시명 | direct anchor | load semantics |
|---|---|---|---|
| `strength.bench_press` | 벤치프레스 | `barbell_bench_press` | `EXTERNAL_LOAD` |
| `strength.back_squat` | 스쿼트 | `barbell_back_squat` | `EXTERNAL_LOAD` |
| `strength.conventional_deadlift` | 데드리프트 | `barbell_deadlift` | `EXTERNAL_LOAD` |
| `strength.weighted_pull_up` | 중량 풀업 | `ex_e41f4c2b` | `BODYWEIGHT_PLUS_ADDED_LOAD` |

입력은 완료 세션의 confirmed set, stable key, 반복수, RPE, 외부중량, 날짜별 체중·초기 profile 체중 및 versioned registry입니다. 출력은 current model state, immutable per-event history, compact evidence, personal curve posterior, 이벤트 상태와 UI 요약입니다.

## 4. 비적용 범위

- 기존 raw volume 및 Epley 공식 환산 series 변경
- 연결조직 77개 load unit, OFI, readiness, fatigue 또는 ProgramBuilder 입력
- display name substring에 의한 authoritative target·curve 매칭
- tissue stress overlap을 exercise transfer로 해석하는 것
- posterior 중앙값을 observed cell, shock 또는 `LegacyTimeSeriesAnalyzer` 입력으로 사용하는 것
- 임상 진단, 부상 예측, 인과 추론 또는 장기 미래 예측

## 5. 용어

- `직접 1RM`: direct anchor에서 1회, RPE 10으로 확인된 당시 resolved total load입니다.
- `세트 기반 수행 추정`: canonical 비선형 repetition curve로 만든 한 세션의 nRM 관측입니다.
- `현재 수행능력 posterior`: 직접·nRM·허용된 sparse proxy evidence를 결합한 현재 filtered distribution입니다.
- `prior`: 해당 event의 evidence를 넣기 직전 분포입니다.
- `immutable history`: 처리 시점의 prior, observation, posterior와 출처를 저장한 행입니다.
- `curve assignment`: 운동별 curve profile, match level과 variance multiplier의 명시적 registry row입니다.
- `proxy loading`: exercise stable key에서 target factor로 향하는 제한된 loading이며 repetition curve assignment와 독립입니다.

## 6. 입력 데이터

확인된 세트만 사용하며 canonical curve 범위는 1~12회입니다. direct target, curve와 proxy는 stable-key registry로만 결정합니다. 미등록 custom 운동은 이름으로 강한 assignment를 얻지 않습니다.

중량 풀업의 primary state는 추가중량이 아니라 `당시 체중 + 당시 추가중량`인 총부하입니다. 체중 우선순위는 exact-date check-in/metric, 가장 최근 이전 값, initial profile입니다. 값이 오래될수록 load variance가 증가하며 체중이 없으면 direct weighted-pull-up observation을 만들지 않습니다. assisted pull-up은 `bodyweight - assistance` semantics이고 direct anchor가 아닙니다.

## 7. 계산 또는 분류 계약

Epley 식은 새 likelihood에 들어가지 않습니다. 곡선 `q(r)`은 `q(1) = 1`인 relative load이며 reviewed knot 사이를 deterministic monotone PCHIP으로 보간합니다. 유효 범위 밖 반복수는 fail closed입니다.

- 1회 RPE 10: observation center는 resolved load와 정확히 같고 `DIRECT_1RM`입니다.
- 다회 RPE 10: `load / q(reps)`의 `STRONG_NRM` 관측이며 개인 curve calibration에 들어갈 수 있습니다.
- RPE < 10: RIR을 정밀 점추정하지 않고 더 큰 uncertainty 또는 conservative lower-bound로 처리합니다.
- RPE 누락: RIR 0으로 가장하지 않고 `MISSING_RPE_LOWER_BOUND`로 처리합니다.
- 같은 exercise·date의 여러 set: 상관된 독립 관측 여러 개가 아니라 한 session observation으로 집계합니다. 모순되는 set은 진단과 추가 분산을 만듭니다.

상태는 target-specific log capacity factor와 다음 shared factor의 sparse schema로 구성됩니다.

`strength.factor.press_shared`, `strength.factor.horizontal_press`, `strength.factor.elbow_extension`, `strength.factor.knee_extension`, `strength.factor.hip_extension_posterior_chain`, `strength.factor.trunk_bracing`, `strength.factor.vertical_pull_shared`, `strength.factor.shoulder_adduction_extension`, `strength.factor.elbow_flexion`, `strength.factor.scapular_depression_control`.

각 target은 `strength.factor.target.<target>` factor를 가집니다. covariance update는 Joseph form, 강제 symmetry와 양의 diagonal floor를 사용합니다. non-finite state·observation·variance는 fail closed입니다. 저장 벡터는 차원, little-endian order와 SHA-256 checksum을 포함하고 covariance는 lower triangle로 pack합니다.

## 8. 집계 방식

완료 상태는 날짜 session key에서 `unconfirmed > 0`이던 상태가 `unconfirmed == 0`이 되고 confirmed set이 하나 이상 남는 전이입니다. 마지막 planned set 삭제도 confirmed set이 남으면 완료할 수 있지만 모든 set 삭제는 event가 아닙니다. PENDING event는 record mutation transaction 안에서 completion fingerprint와 함께 삽입됩니다.

처리는 날짜·event UUID 순으로 결정론적이며 `Dispatchers.Default`에서 실행됩니다. evidence, history, current state, curve posterior와 PROCESSED 상태는 한 transaction으로 commit됩니다. 실패하면 partial posterior row 없이 FAILED/PENDING event를 재시도합니다. 같은 session/completion fingerprint는 두 번째 event를 만들지 않습니다.

과거 history는 event·target 복합키의 filtered snapshot입니다. 미래 smoothing을 하지 않으며 이후 세션, curve 보정, 앱 model version, 원본 수정·삭제는 숫자를 바꾸지 않습니다. 원본 삭제는 `sourceEvidenceStatus`만 변경할 수 있습니다.

## 9. 출력과 UI 해석

target selector는 registry의 enabled target을 읽습니다. primary card는 posterior median, 80% interval, 최신 직접 1RM, 직접/nRM/proxy count, curve calibration, 최근 처리 세션, model/curve version을 보여 줍니다.

이력 상세는 저장된 행의 세션 전 추정·80% 범위, 실제 또는 세트 기반 관측, 세션 후 추정·80% 범위, 중앙값/구간폭 변화, 예측분포 내 위치, 강한 관측 종류, curve profile/match를 보여 줍니다. 중량 풀업은 당시 체중·추가중량·총부하·체중 출처를 저장값으로 표시하고, 현재 카드는 current bodyweight를 사용한 추가중량 equivalent를 별도 표시합니다.

기존 Epley 그래프는 `기존 공식 환산값`으로 표시하며 역사 비교용이고 새 posterior model에 사용되지 않는다고 설명합니다. Lab은 event ledger, fingerprint, model/curve boundary, numerical diagnostics, backup restore와 bootstrap provenance를 보여 주며 Bayesian 시계열 Lab과 명시적으로 분리합니다.

## 10. 예외 및 fallback

- direct target이나 curve assignment가 없으면 강한 이름 기반 추정을 만들지 않습니다.
- exact exercise curve가 없으면 명시적 borrowed assignment 또는 `GENERAL_FALLBACK`을 사용하고 variance multiplier를 높입니다.
- 체중이 필요한 semantics에서 체중을 구하지 못하면 zero를 대입하지 않고 해당 direct observation을 제외합니다.
- observation이 prior lower bound보다 약하면 상태를 억지로 낮추지 않을 수 있습니다.
- source record가 처리 후 삭제돼도 숫자는 유지하고 source availability만 표시합니다.
- model/factor version이 현재 decoder와 호환되지 않으면 state를 재해석하지 않고 저장 history를 표시하며 진단을 남깁니다.

## 11. 개인화 또는 보정

개인 curve는 canonical profile을 중심으로 고정된 bounded theta grid에서만 보정합니다. 다회 RPE 10 strong evidence만 weight update에 사용하고 unrelated exercise를 합치지 않습니다. strong observation 2개 전에는 `CANONICAL_ONLY`, 이후 충분도에 따라 `CALIBRATING`, 서로 다른 rep range 3개와 strong observation 6개 이상이면 `PERSONALIZED`가 될 수 있습니다. posterior weights는 항상 finite, non-negative, sum 1이어야 합니다.

개인 curve가 바뀌어도 이미 저장된 history의 curve profile, match, calibration, interval과 당시 load snapshot은 다시 계산하지 않습니다.

## 12. 연구 근거

곡선의 primary source는 Nuzzo et al., *Sports Medicine* (PMID `37792272`, DOI `10.1007/s40279-023-01937-7`)과 public OSF project `s94gf`입니다. source artifact SHA-256은 다음과 같습니다.

- `Analysis.R`: `37342ab2417fcf7b1e9f12182cab2fc7d0298e0876683090f7960d296cc74c99`
- `Data.csv`: `229dadd1f13bfe7b9f5dd5fd36bcfb6c710f8ac67b08f2be9ed423eb61b72fe5`
- reviewed general table: `da67c15cbca59d77cb037ae8c9a89ec223613233839924eb72047c31cafd9f9d`
- reviewed exercise table: `5c8f8a6cb719f064346e8f9cc910d196daa9c340b86626895e822daf930445aa`

생성된 profile asset checksum은 `29cfcc0013e6a997db199b09a94ba10cff9176a3d916641251c6986e01362b1c`, reviewed source table checksum은 `63dc6bf18f3e48ff201e511a4c42ec9e7f64aaca956acc5232b90942d6e11bc2`입니다. 제품의 proxy loading, process noise와 evidence threshold는 논문 효과크기가 아니라 versioned product policy입니다.

## 13. 제품 정책 및 휴리스틱

- flat barbell bench만 exact bench curve를 사용합니다. close-grip과 dumbbell bench는 명시적 borrowed assignment와 추가 uncertainty를 가집니다.
- overhead press는 general curve를 사용하지만 bench target에 positive proxy loading을 가질 수 있습니다. curve 유사성과 transfer는 별도 축입니다.
- machine chest press는 stack 간 교환 가능성을 가정하지 않고 general curve와 더 큰 uncertainty를 사용합니다.
- leg press stable key `ex_ab468462`만 exact leg-press curve를 사용합니다. squat은 leg-press curve를 사용하지 않습니다.
- back squat, deadlift, weighted pull-up 초기 정책은 general-resistance curve이며 exercise-specific 검증으로 과장하지 않습니다.
- proxy는 target registry의 sparse factor loading만 사용하고 dense exercise-pair matrix를 만들지 않습니다.

현재 model/version boundary는 `strength-performance-model-2.0.0`, `strength-factor-schema-2.0.0`, `strength-target-registry-1.0.0`, `repetition-curve-assets-1.0.0`, `repetition-curve-assignments-1.0.0`입니다.

## 14. 알려진 한계

- general curve는 squat, deadlift, pull-up의 exercise-specific 검증 곡선이 아닙니다.
- RPE와 체중은 사용자 입력 품질에 의존합니다.
- sparse proxy loading과 process variance는 실제 사용자 성과로 추가 보정이 필요한 product policy입니다.
- Room history는 filtered posterior snapshot이며 full posterior draw archive가 아닙니다.
- current state는 model/factor version이 바뀔 때 명시적 compatibility 또는 새 model instance가 필요합니다.
- historical bootstrap은 설치 시점에 보이는 완료 기록을 chronological forward-filtering한 것으로 당시 실제 앱 처리 시각을 복원하지 않습니다.
- instrumentation migration test는 연결된 기기 또는 emulator에서 별도로 실행해야 합니다.

## 15. 현재 구현 상태

- Room version `22`, migration `MIGRATION_21_22`
- tables: `strength_posterior_events`, `strength_posterior_history`, `strength_posterior_model_state`, `strength_curve_posteriors`, `strength_posterior_evidence`
- one-time marker: `strength_posterior_bootstrap_v2`
- completion reasons: `LIVE_SESSION_COMPLETION`, `INITIAL_INSTALLATION_BOOTSTRAP`, `LEGACY_BACKUP_BOOTSTRAP`
- backup row schema version `5`
- new persistent posterior is authoritative; v0.5.0.1 unpersisted proxy engine remains code-level regression/compatibility material but is not built by `PerformanceTrendSummaryService`.

## 16. 구현 위치

- [`StrengthPerformanceRegistry.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt)
- [`RepetitionCurves.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/curve/RepetitionCurves.kt)
- [`StrengthSessionLikelihood.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthSessionLikelihood.kt)
- [`PersonalCurveCalibration.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/PersonalCurveCalibration.kt)
- [`StrengthPosteriorModel.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPosteriorModel.kt)
- [`StrengthPosteriorPersistence.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorPersistence.kt)
- [`StrengthPosteriorUpdateService.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorUpdateService.kt)
- [`StrengthPosteriorBackupCodec.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorBackupCodec.kt)
- [`PersistentStrengthPerformanceSummary.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/PersistentStrengthPerformanceSummary.kt)
- [`AnalysisPersistentStrengthPerformanceUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUi.kt)

## 17. 검증 테스트

- [`RepetitionCurveRegistryTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/curve/RepetitionCurveRegistryTest.kt)
- [`StrengthPerformanceLikelihoodTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceLikelihoodTest.kt)
- [`StrengthPosteriorModelTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/StrengthPosteriorModelTest.kt)
- [`StrengthPosteriorEventIntegrationTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/StrengthPosteriorEventIntegrationTest.kt)
- [`StrengthPosteriorBackupRestoreTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/StrengthPosteriorBackupRestoreTest.kt)
- [`PersistentStrengthPerformanceSummaryTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/PersistentStrengthPerformanceSummaryTest.kt)
- [`AnalysisPersistentStrengthPerformanceUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUiTest.kt)
- [`TrainingDatabaseMigrationTest.kt`](../../../app/src/androidTest/java/com/training/trackplanner/data/TrainingDatabaseMigrationTest.kt)

## 18. 권위 자산

- [`repetition_curve_source_v1.csv`](../../../app/src/main/assets/strength_performance/repetition_curve_source_v1.csv)
- [`repetition_curve_profiles_v1.csv`](../../../app/src/main/assets/strength_performance/repetition_curve_profiles_v1.csv)
- [`repetition_curve_manifest_v1.csv`](../../../app/src/main/assets/strength_performance/repetition_curve_manifest_v1.csv)
- [`repetition_curve_assignments_v1.csv`](../../../app/src/main/assets/strength_performance/repetition_curve_assignments_v1.csv)
- [`strength_target_registry_v1.csv`](../../../app/src/main/assets/strength_performance/strength_target_registry_v1.csv)
- [`strength_proxy_loadings_v1.csv`](../../../app/src/main/assets/strength_performance/strength_proxy_loadings_v1.csv)
- [`generate_strength_repetition_curves.py`](../../../tools/generate_strength_repetition_curves.py)

## 19. 관련 문서

- [`STRENGTH_VOLUME_CALCULATION.md`](STRENGTH_VOLUME_CALCULATION.md)
- [`BODYWEIGHT_EFFECTIVE_LOAD.md`](BODYWEIGHT_EFFECTIVE_LOAD.md)
- [`docs/bayesian_time_series_lab_architecture.md`](../../bayesian_time_series_lab_architecture.md)
- [`docs/protocols/README.md`](../README.md)
- [`docs/v0.5.0.2_release_notes.md`](../../v0.5.0.2_release_notes.md)

## 20. 변경 이력

- `2.0.0` (2026-07-23): Nuzzo 기반 비선형 curve registry, generic four-target/factor model, 중량 풀업 total-load semantics, completion event ledger, immutable filtered history, personal curve state, Room 21→22, exact backup/restore, one-time bootstrap와 persisted UI authority를 등록했습니다.
- `1.0.0` (2026-07-23): v0.5.0.1의 화면 조회 기반 Epley proxy posterior와 세 target 실험 계약을 처음 등록했습니다. 이 엔진은 2.0.0에서 authoritative runtime read path를 넘겼습니다.
