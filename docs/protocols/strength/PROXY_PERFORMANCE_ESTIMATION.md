# 지속형 근력 수행능력 사후분포

| Field | Value |
|---|---|
| Protocol ID | STRENGTH-PROXY-PERFORMANCE |
| Protocol version | 3.0.2 |
| Status | EXPERIMENTAL |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.3 |
| Last audited commit | 40615fab9c7ff892b0e48dd5a244eeb77e7cf2ee |
| Evidence profile | DIRECT_RESEARCH_SUPPORT, PRODUCT_POLICY, ENGINEERING_HEURISTIC, LOW_CONFIDENCE_PROXY |
| Supersedes | 2.1.0 |

이 문서는 완료 세션 이벤트로만 갱신되는 벤치프레스, 스쿼트, 데드리프트, 중량 풀업 수행능력 사후분포의 단일 canonical 계약입니다. v3은 보고 RPE를 단일 RIR로 치환하지 않고 확률분포로 적분하며, 같은 세션의 공통 컨디션 효과와 exercise-local 수행 변화를 분리합니다. 프록시는 다른 운동의 절대 kg를 옮기지 않고 검토된 shared factor에 local innovation만 전달합니다. v0.5.0.1의 화면 진입 시 재계산 엔진과 기존 Epley 계열은 권위 경로가 아닙니다.

## 1. 일반 사용자용 요약

운동 세션이 실제로 완료될 때 확인된 세트와 관련 운동의 제한된 변화 신호를 반영해 현재 수행능력의 중앙값과 80% 범위를 갱신합니다. RPE는 가능한 RIR들의 확률 혼합으로 처리하고, RPE가 없으면 하한 정보로만 사용합니다. 과거 그래프는 당시 저장된 값을 그대로 보여 주며, 이후 기록 수정·삭제나 체중 변경으로 다시 쓰지 않습니다. 결과는 수행능력 추정이지 직접 측정값이나 경기력 보장이 아닙니다.

## 2. 목적

- 직접 1RM 사이의 간격을 비선형 반복 곡선과 sparse proxy factor로 보수적으로 연결합니다.
- 화면 조회가 아니라 완료 이벤트를 유일한 update 원인으로 만듭니다.
- 세션 전 prior, 세션 관측, 세션 후 posterior와 버전·곡선·체중 출처를 불변 이력으로 보존합니다.
- exercise-local posterior에서 운동 자체 변화를 먼저 추정하고, 검토된 shared innovation만 target posterior에 전달합니다.
- 수정된 likelihood를 별도 revision에서 결정론적으로 재생해 과거 수치와 현재 권위 상태를 함께 보존합니다.
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

### 3.1 Phase 2A.1 prior-only registry boundary

`metadata/strength_proxy_prior_v1`은 차기 모델을 위한 isolated configuration preflight이며 위 production posterior의 입력이 아닙니다. 이 registry는 `BENCH_PRESS`, `BACK_SQUAT`, generic `DEADLIFT`, `WEIGHTED_PULL_UP`, `MILITARY_PRESS` 다섯 target만 갖고 relation을 exact stableKey로만 연결합니다. non-direct 수치는 broad uncertainty의 `PROVISIONAL_PRODUCT_PRIOR / TEMPORARY_APPROVED`이며 과학적으로 검증된 kg 변환계수가 아닙니다.

제품 책임자는 `MILITARY_PRESS` direct anchor를 `ex_32219f7a` (`오버헤드 프레스`)로 확정했습니다. 정본 실행은 서서 수행하는 strict barbell overhead press이며 의도적인 무릎·엉덩이 drive가 없습니다. push press, push jerk, split jerk는 이 anchor가 아니며 별도 stableKey를 가져야 합니다. 기존 `ex_32219f7a` 기록은 현재 strength model에서 이 정본 동작의 기록으로 취급합니다.

한 proxy observation이 여러 target relation을 가져도 future posterior는 이를 독립 관측으로 중복 삽입하지 않습니다. shared factor는 한 번 갱신하고 여러 target posterior가 같은 shared state에 반응해야 합니다. 사용자별 상태는 exercise metadata가 아닌 별도 `UserStrengthProxyPosterior` 개념이며 이번 단계에서는 구현하지 않습니다.

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
- `RIR mixture`: 보고 RPE에 대해 가능한 RIR 값과 확률을 보존한 이산분포입니다.
- `exercise-local posterior`: 동일 stable key 세션만으로 갱신되는 운동 자체의 log-capacity 분포입니다.
- `local innovation`: 현재 세션 likelihood 중심과 직전 local prior 중심의 log 차이입니다.
- `proxy loading`: local innovation을 target의 검토된 shared factor에만 전달하는 제한된 loading이며 repetition curve assignment와 독립입니다.
- `실패 상한`: confirmed 0회·RPE 10 시도에서 당시 resolved load를 수행능력의 보수적 upper-censored 신호로 쓰는 음의 근거입니다.

## 6. 입력 데이터

확인된 세트만 사용하며 canonical curve 범위는 1~20회입니다. direct target과 curve는 stable-key registry로 결정합니다. reviewed stable-key proxy row가 있으면 항상 우선하며, row가 없는 운동은 `estimated1RmEligible=true`, `needsReview=false`인 경우에만 movement pattern, progression group, family, equipment 같은 persisted metadata로 낮은 loading의 proxy를 만들 수 있습니다. display name은 이 fallback에 사용하지 않습니다.

중량 풀업의 primary state는 추가중량이 아니라 `당시 체중 + 당시 추가중량`인 총부하입니다. 체중 우선순위는 exact-date check-in/metric, 가장 최근 이전 값, initial profile입니다. 값이 오래될수록 load variance가 증가하며 체중이 없으면 direct weighted-pull-up observation을 만들지 않습니다. assisted pull-up은 `bodyweight - assistance` semantics이고 direct anchor가 아닙니다.

## 7. 계산 또는 분류 계약

Epley 식은 새 likelihood에 들어가지 않습니다. 곡선 `q(r)`은 `q(1) = 1`인 relative load이며 reviewed knot 사이를 deterministic monotone PCHIP으로 보간합니다. 유효 범위 밖 반복수는 `UNSUPPORTED_REPETITION_RANGE`로 fail closed입니다.

성공 세트의 resolved load를 `w`, 반복수를 `r`, 가능한 RIR을 `k`라 하면 해당 mixture component의 log-capacity 중심은 다음과 같습니다.

`z_k = log(w / q(r + k))`

보고 RPE `e`의 이산 정책 `P(K=k | e)`를 사용해 두 방향 likelihood를 구성합니다.

`L_set(c) = sum_k P(K=k | e) Normal(c; z_k, sigma_k^2)`

- 1회 RPE 10: RIR 0의 점질량이며 `DIRECT_1RM`입니다.
- 다회 RPE 10: `w / q(r)` 중심의 `STRONG_NRM`이며 개인 curve calibration에 들어갈 수 있습니다.
- RPE 6.0~9.5: checked-in `strength-rpe-rir-policy-1.0.0`의 확률질량을 한 번만 반영한 `RPE_MIXTURE_OBSERVATION`입니다. 반 단위가 아닌 값은 인접 정책행을 선형 혼합합니다.
- RPE 누락: RIR 0으로 가장하지 않고 `w / q(r)`를 최소 수행 가능치로 보는 `MISSING_RPE_LOWER_CENSORED`입니다. 별도 양방향 중심을 만들지 않습니다.
- 0회 RPE 10: 성공 관측으로 해석하지 않고 resolved load를 상한으로 보는 `FAILURE_UPPER_CENSORED`입니다.
- 반복수, RPE, 중량 또는 곡선이 지원되지 않으면 값을 보간·대체하지 않고 명시적 제외 근거를 저장합니다.

같은 exercise·date의 세트는 독립 컨디션으로 곱하지 않습니다. 공통 세션 효과 `d ~ Normal(0, tau_session^2)`를 두고 다음을 15-node Gauss-Hermite로 결정론적 적분합니다.

`L_session(c) = integral Normal(d; 0, tau_session^2) product_i L_i(c + d) dd`

이 likelihood와 Gaussian prior는 고정 1,025-point adaptive scalar grid에서 결합합니다. grid 경계 질량이 크면 제한 횟수만 확장하고, fingerprint와 진단을 evidence에 저장합니다. 두 방향 likelihood가 있는 운동은 exercise-local posterior를 먼저 갱신합니다. 첫 proper observation은 local baseline만 만들고 proxy 전이를 하지 않습니다. 이후 proper observation의 local innovation은 다음과 같습니다.

`delta_local = E[log C_session] - E[log C_local_prior]`

reviewed `LOCAL_INNOVATION_SHARED_ONLY` row가 최소 local history를 충족하면 target별 계수 `alpha`와 shared loading vector `h_shared`로만 전이합니다.

`y_proxy = alpha * delta_local`

`H_proxy = alpha * h_shared`

`Var(y_proxy) = Var(delta_local) + sigma_transfer^2`

`H_proxy`의 모든 `strength.factor.target.*` 좌표는 반드시 0입니다. 여러 proxy innovation은 evidence fingerprint 순으로 정렬해 한 Gaussian batch update로 처리합니다. 다른 운동의 절대 kg, target-specific factor, tissue state, OFI, readiness는 이 경로에 들어오지 않습니다.

상태는 target-specific log capacity factor와 다음 shared factor의 sparse schema로 구성됩니다.

`strength.factor.press_shared`, `strength.factor.horizontal_press`, `strength.factor.elbow_extension`, `strength.factor.knee_extension`, `strength.factor.hip_extension_posterior_chain`, `strength.factor.trunk_bracing`, `strength.factor.vertical_pull_shared`, `strength.factor.shoulder_adduction_extension`, `strength.factor.elbow_flexion`, `strength.factor.scapular_depression_control`.

각 target은 `strength.factor.target.<target>` factor를 가집니다. 직접 anchor만 absolute target likelihood를 통해 해당 좌표를 갱신할 수 있습니다. covariance update는 Joseph form, 강제 symmetry와 양의 diagonal floor를 사용합니다. non-finite state·observation·variance는 fail closed입니다. 저장 벡터는 차원, little-endian order와 SHA-256 checksum을 포함하고 covariance는 lower triangle로 pack합니다.

## 8. 집계 방식

완료 상태는 날짜 session key에서 `unconfirmed > 0`이던 상태가 `unconfirmed == 0`이 되고 confirmed set이 하나 이상 남는 전이입니다. 마지막 planned set 삭제도 confirmed set이 남으면 완료할 수 있지만 모든 set 삭제는 event가 아닙니다. PENDING event는 record mutation transaction 안에서 completion fingerprint와 함께 삽입됩니다.

처리는 날짜·event UUID 순으로 결정론적이며 `Dispatchers.Default`에서 실행됩니다. evidence, target history, exercise-local history/state, proxy-transfer history, current state, curve posterior와 PROCESSED 상태는 revision 안에서 transaction으로 commit됩니다. 실패하면 partial posterior row 없이 FAILED/PENDING event를 재시도합니다. 같은 revision/session/completion fingerprint는 두 번째 event를 만들지 않습니다. UI의 `관련 세션` 수는 active revision target history의 distinct event UUID 수이며 direct anchor뿐 아니라 적용된 variation/proxy와 실패 신호도 포함합니다.

과거 history는 event·target 복합키의 filtered snapshot입니다. 미래 smoothing을 하지 않으며 이후 세션, curve 보정, 앱 model version, 원본 수정·삭제는 숫자를 바꾸지 않습니다. 원본 삭제는 `sourceEvidenceStatus`만 변경할 수 있습니다.

원시 사용자 기록과 근력 분석 파생 상태는 별도 수명주기를 가집니다. 운동, 세트, confirmed 상태, 중량, 반복수, RPE, 수행일, 체중, check-in과 profile은 재구축 입력인 원시 데이터이며 correction이 수정하거나 삭제하지 않습니다. event/history/model state/curve posterior/evidence/revision/local state/local history/proxy history는 현재 모델로 다시 만들 수 있는 파생 데이터입니다.

저장된 revision fingerprint가 현재 canonical 모델·registry·RIR·curve·derived-state 경계와 호환되지 않거나 백업 복원으로 원시 입력이 바뀐 경우에만 v0.5.0.4 correction을 한 번 실행합니다. 이때 모든 근력 파생 행과 오래된 strength bootstrap/rebuild/restore marker를 삭제하고, `strength-revision-3.0.0` 하나만 `BUILDING`으로 만든 뒤 완료 세션을 날짜순으로 재생합니다. 모든 event가 `PROCESSED`인 transaction에서만 revision을 `ACTIVE`로 승격하고 `strength_derived_reset_rebuild_0_5_0_4_complete` marker를 기록합니다.

호환 가능한 current ACTIVE revision은 completion marker가 없더라도 행을 재생하지 않고 marker만 복구합니다. correction 실패 시 legacy revision을 복원하지 않으며 current revision은 `FAILED` 상태와 진단 코드를 남기고 다음 startup에서 파생 행을 비운 뒤 재시도합니다. 성공 후 일반 앱 실행이나 분석 화면 진입은 전체 재생을 하지 않습니다. 이후 완료 운동은 current revision에 event/history 한 건씩 순차 추가되고, 같은 모델 revision 안의 기존 snapshot은 이후 운동 때문에 다시 계산되지 않습니다.

## 9. 출력과 UI 해석

target selector는 registry의 enabled target을 고정 순서로 읽고 1~4개를 동시에 선택합니다. 최소 한 target은 항상 남으며 새 target을 선택하면 상세 focus가 그 target으로 이동합니다. `LEVEL`은 저장된 posterior 중앙값과 80% 범위를 절대 kg로 표시하고, 중량 풀업은 추가중량이 아니라 canonical 총부하를 사용합니다. target key별 색상은 스쿼트 `#1565C0`, 벤치프레스 `#D32F2F`, 컨벤셔널 데드리프트 `#2E7D32`, 중량 풀업 `#C2185B`로 고정합니다.

`GROWTH_RATE`는 같은 target의 바로 이전 persisted posterior point를 분모로 `((currentMedian / previousMedian) - 1) * 100`을 표시합니다. 첫 point는 이전 추정이 없어 값과 graph point를 만들지 않습니다. 현재 80% 범위는 이전 중앙값을 분모로 변환하며 정확한 성장률 사후분포라고 부르지 않습니다. 여러 target의 날짜 합집합을 x축으로 사용하되 없는 날짜의 값을 만들거나 저장 이력을 보간하지 않습니다.

chart marker는 selected target의 자체 load scale에서 생성된 직접 RPE/RIR session observation만 사용합니다. proxy 운동의 local kg는 target kg 또는 성장률 marker로 표시하지 않고 focused target의 상세 행에만 남깁니다. 상세는 세션 전 추정, session observation, 세션 후 추정, 중앙값·구간폭 변화, curve/evidence와 local proxy 진단을 한 target에 대해서만 보여 줍니다.

legacy `기존 공식 환산값` Epley card는 제거했습니다. Lab의 기존 세 performance metric key는 호환성을 위해 유지하지만 값과 사용자 문구는 해당 주 마지막 persisted posterior 중앙값입니다. `StrengthAndMuscleMetricSeriesBuilder`는 더 이상 Epley performance series를 만들지 않습니다. Lab 진단은 active/superseded revision, rebuild provenance, event ledger, local state, applied proxy, target-specific proxy violation, fingerprint, model/curve/RIR boundary, numerical diagnostics, backup restore와 bootstrap provenance를 보여 주며 Bayesian 시계열 Lab과 명시적으로 분리합니다.

## 10. 예외 및 fallback

- direct target이나 curve assignment가 없으면 강한 이름 기반 추정을 만들지 않습니다.
- exact exercise curve가 없으면 명시적 borrowed assignment 또는 `GENERAL_FALLBACK`을 사용하고 variance multiplier를 높입니다.
- 체중이 필요한 semantics에서 체중을 구하지 못하면 zero를 대입하지 않고 해당 direct observation을 제외합니다.
- RPE mixture의 지원되는 확률질량이 0.80 미만이면 양방향 관측을 만들지 않습니다.
- lower/upper-censored evidence는 scalar likelihood로 결합하며 임의의 point estimate로 바꾸지 않습니다.
- first proper local observation은 baseline만 설정하고 shared proxy update를 만들지 않습니다.
- source record가 처리 후 삭제돼도 숫자는 유지하고 source availability만 표시합니다.
- model revision, factor schema, target/proxy registry, curve, RIR policy 또는 grid가 호환되지 않으면 해당 revision을 ACTIVE로 사용하지 않습니다.
- 호환되지 않는 revision이나 실패한 rebuild가 있으면 legacy 수치로 fallback하지 않고 재계산 중 또는 재계산 실패 상태를 표시합니다.
- 백업은 원시 운동/profile/체중/exercise 데이터를 복원한 뒤 strength derived row를 권위본으로 채택하지 않고 current canonical revision을 원시 기록에서 재구축합니다.

## 11. 개인화 또는 보정

개인 curve는 canonical profile을 중심으로 고정된 bounded theta grid에서만 보정합니다. 다회 RPE 10 strong evidence만 weight update에 사용하고 unrelated exercise를 합치지 않습니다. strong observation 2개 전에는 `CANONICAL_ONLY`, 이후 충분도에 따라 `CALIBRATING`, 서로 다른 rep range 3개와 strong observation 6개 이상이면 `PERSONALIZED`가 될 수 있습니다. RPE mixture와 censored evidence는 curve 개인화 weight를 직접 갱신하지 않습니다. posterior weights는 항상 finite, non-negative, sum 1이어야 합니다.

개인 curve가 바뀌어도 이미 저장된 history의 curve profile, match, calibration, interval과 당시 load snapshot은 다시 계산하지 않습니다.

## 12. 연구 근거

곡선의 primary source는 Nuzzo et al., *Sports Medicine* (PMID `37792272`, DOI `10.1007/s40279-023-01937-7`)과 public OSF project `s94gf`입니다. source artifact SHA-256은 다음과 같습니다.

- `Analysis.R`: `37342ab2417fcf7b1e9f12182cab2fc7d0298e0876683090f7960d296cc74c99`
- `Data.csv`: `229dadd1f13bfe7b9f5dd5fd36bcfb6c710f8ac67b08f2be9ed423eb61b72fe5`
- reviewed general table: `da67c15cbca59d77cb037ae8c9a89ec223613233839924eb72047c31cafd9f9d`
- reviewed exercise table: `5c8f8a6cb719f064346e8f9cc910d196daa9c340b86626895e822daf930445aa`

생성된 1~20회 profile asset checksum은 `5984112271b8abdc1870b59c786431f23547c6f4a97ab70b33134a1689706c0d`, reviewed source table checksum은 `63dc6bf18f3e48ff201e511a4c42ec9e7f64aaca956acc5232b90942d6e11bc2`입니다. checksum은 UTF-8 text의 CRLF/CR line ending을 LF로 정규화한 canonical bytes에 적용해 Git checkout platform과 무관하게 같은 data를 검증합니다. RPE/RIR PMF, proxy loading, process noise, transfer coefficient와 evidence threshold는 논문 효과크기가 아니라 versioned product policy입니다.

## 13. 제품 정책 및 휴리스틱

- flat barbell bench만 exact bench curve를 사용합니다. close-grip과 dumbbell bench는 명시적 borrowed assignment와 추가 uncertainty를 가집니다.
- overhead press는 general curve를 사용하지만 bench target에 positive proxy loading을 가질 수 있습니다. curve 유사성과 transfer는 별도 축입니다.
- machine chest press는 stack 간 교환 가능성을 가정하지 않고 general curve와 더 큰 uncertainty를 사용합니다.
- leg press stable key `ex_ab468462`만 exact leg-press curve를 사용합니다. squat은 leg-press curve를 사용하지 않습니다.
- back squat, deadlift, weighted pull-up 초기 정책은 general-resistance curve이며 exercise-specific 검증으로 과장하지 않습니다.
- proxy는 target registry의 sparse shared-factor loading만 사용하고 dense exercise-pair matrix나 절대중량 변환표를 만들지 않습니다.
- reviewed row가 없는 e1RM-eligible 운동의 metadata proxy는 squat/knee-dominant, hinge/deadlift, horizontal press와 vertical pull family에만 보수적으로 허용하며 `strength-proxy-metadata-2.0.0`으로 식별합니다.

현재 model/version boundary는 `strength-performance-model-3.0.0`, `strength-revision-3.0.0`, `strength-derived-state-0.5.0.6`, `strength-factor-schema-2.0.0`, `strength-target-registry-1.1.0`, `strength-proxy-registry-2.0.0`, `strength-proxy-metadata-2.0.0`, `strength-rpe-rir-policy-1.0.0`, `strength-scalar-grid-1.0.0`, `repetition-curve-assets-2.0.0`, `repetition-curve-assignments-1.0.0`입니다. 호환되지 않는 이전 derived state는 current summary에 섞지 않고 raw workout에서 한 번 재구축합니다.

## 14. 알려진 한계

- general curve는 squat, deadlift, pull-up의 exercise-specific 검증 곡선이 아닙니다.
- RPE와 체중은 사용자 입력 품질에 의존합니다.
- RPE/RIR 분포, sparse proxy loading, transfer coefficient와 process variance는 실제 사용자 성과로 추가 보정이 필요한 product policy입니다.
- Room history는 filtered posterior snapshot이며 full posterior draw archive가 아닙니다.
- scalar grid와 Gauss-Hermite 적분은 결정론적 수치 근사이며 full posterior draw archive가 아닙니다.
- current state는 model/likelihood/proxy/derived-state 호환성 의미가 바뀔 때만 명시적 correction rebuild가 필요합니다. 일반 startup이나 분석 화면 진입은 rebuild 조건이 아닙니다.
- historical bootstrap은 설치 시점에 보이는 완료 기록을 chronological forward-filtering한 것으로 당시 실제 앱 처리 시각을 복원하지 않습니다.
- instrumentation migration test는 연결된 기기 또는 emulator에서 별도로 실행해야 합니다.

## 15. 현재 구현 상태

- Room version `25`; exercise-key migration 이후 posterior state는 canonical exerciseStableKey만 사용합니다.
- revision tables: `strength_model_revisions`, `strength_exercise_performance_state`, `strength_exercise_performance_history`, `strength_proxy_transfer_history`
- retained tables: `strength_posterior_events`, `strength_posterior_history`, `strength_posterior_model_state`, `strength_curve_posteriors`, `strength_posterior_evidence`
- obsolete parser/provenance markers remain readable: `strength_posterior_bootstrap_v2`, `strength_model_correction_rebuild_0_5_0_3`
- current correction marker: `strength_derived_reset_rebuild_0_5_0_4_complete`
- explicit raw-input rebuild request: `strength_derived_reset_rebuild_required`
- completion/rebuild reasons: `LIVE_SESSION_COMPLETION`, `STRENGTH_DERIVED_RESET_REBUILD_0_5_0_4`
- backup row schema version `6`; older payloads remain parseable, but restored derived strength rows are discarded in favor of deterministic rebuild from restored raw records
- only the compatible current `strength-revision-3.0.0` ACTIVE revision is authoritative; no arbitrary or legacy ACTIVE fallback is allowed.

## 16. 구현 위치

- [`StrengthPerformanceRegistry.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt)
- [`RepetitionCurves.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/curve/RepetitionCurves.kt)
- [`StrengthSessionLikelihood.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthSessionLikelihood.kt)
- [`RpeRirPolicy.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/RpeRirPolicy.kt)
- [`ScalarGridPosterior.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/ScalarGridPosterior.kt)
- [`StrengthExercisePosterior.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthExercisePosterior.kt)
- [`StrengthProxyTransfer.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthProxyTransfer.kt)
- [`PersonalCurveCalibration.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/PersonalCurveCalibration.kt)
- [`StrengthPosteriorModel.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPosteriorModel.kt)
- [`StrengthPosteriorPersistence.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorPersistence.kt)
- [`StrengthModelRevisionPersistence.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthModelRevisionPersistence.kt)
- [`StrengthPosteriorUpdateService.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorUpdateService.kt)
- [`StrengthPosteriorBackupCodec.kt`](../../../app/src/main/java/com/training/trackplanner/data/StrengthPosteriorBackupCodec.kt)
- [`PersistentStrengthPerformanceSummary.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/strengthperformance/PersistentStrengthPerformanceSummary.kt)
- [`AnalysisPersistentStrengthPerformanceUi.kt`](../../../app/src/main/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUi.kt)

## 17. 검증 테스트

- [`RepetitionCurveRegistryTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/curve/RepetitionCurveRegistryTest.kt)
- [`StrengthPerformanceLikelihoodTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceLikelihoodTest.kt)
- [`ScalarGridPosteriorEngineTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/ScalarGridPosteriorEngineTest.kt)
- [`StrengthExerciseLocalPosteriorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/StrengthExerciseLocalPosteriorTest.kt)
- [`StrengthProxyTransferTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/strengthperformance/StrengthProxyTransferTest.kt)
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
- [`rpe_rir_distribution_v1.csv`](../../../app/src/main/assets/strength_performance/rpe_rir_distribution_v1.csv)
- [`generate_strength_repetition_curves.py`](../../../tools/generate_strength_repetition_curves.py)

## 19. 관련 문서

- [`STRENGTH_VOLUME_CALCULATION.md`](STRENGTH_VOLUME_CALCULATION.md)
- [`BODYWEIGHT_EFFECTIVE_LOAD.md`](BODYWEIGHT_EFFECTIVE_LOAD.md)
- [`docs/bayesian_time_series_lab_architecture.md`](../../bayesian_time_series_lab_architecture.md)
- [`docs/protocols/README.md`](../README.md)
- [`docs/v0.5.0.3_release_notes.md`](../../v0.5.0.3_release_notes.md)
- [`docs/v0.5.0.4_release_notes.md`](../../v0.5.0.4_release_notes.md)

## 20. 변경 이력

- `3.0.1` (2026-07-28): exercise identity가 바뀌는 model boundary에서
  derived state를 한 번 비우고 보존된 raw workout을 canonical stableKey로 재생한 뒤
  새 completion event만 순차 반영하도록 고정했습니다.
- `3.0.0` (2026-07-26): known RPE의 discrete RIR mixture, missing-RPE lower censoring, 실패 upper censoring, 15-node same-session 공통효과 적분, 1,025-point scalar grid, exercise-local posterior, shared-only proxy innovation, 1~20회 곡선, Room 23 revision/correction rebuild와 backup schema 6을 등록했습니다.
- `3.0.0` lifecycle correction (2026-07-26): 수학 모델 버전은 유지하면서 `strength-derived-state-0.5.0.4` 호환성 경계를 추가했습니다. 호환되지 않는 파생 상태만 원시 완료 기록에서 한 번 재구축하고, legacy ACTIVE fallback을 제거했으며, 성공 이후에는 새 완료 event만 append합니다.
- `2.1.0` (2026-07-26): 관련 세션 distinct-event 집계, reviewed metadata 기반 e1RM proxy 확장, confirmed 0회·RPE 10 실패 상한, 8주 prior 연쇄 이동 검증, model 2.0.0 compatibility와 platform-independent text checksum을 추가했습니다.
- `2.0.0` (2026-07-23): Nuzzo 기반 비선형 curve registry, generic four-target/factor model, 중량 풀업 total-load semantics, completion event ledger, immutable filtered history, personal curve state, Room 21→22, exact backup/restore, one-time bootstrap와 persisted UI authority를 등록했습니다.
- `1.0.0` (2026-07-23): v0.5.0.1의 화면 조회 기반 Epley proxy posterior와 세 target 실험 계약을 처음 등록했습니다. 이 엔진은 2.0.0에서 authoritative runtime read path를 넘겼습니다.
