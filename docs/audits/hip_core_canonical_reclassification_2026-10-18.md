# 고관절·코어 canonical 재분류 감사 (2026-10-18)

## 범위와 결정 원칙

- 기준 커밋: `24d238eea9eee2702cf80a6344400e1e0bb0c6e4`.
- stableKey, 세션/기록 identity, 근력 승인 운동 10개, Python v1.0 엔진은 변경하지 않는다.
- `CORE_CLASS`, 상위 `DIRECT_TARGET`, 세부 과제, 기록 단위는 서로 다른 계약이다.
- 세부 과제가 둘이어도 실제 세트는 하나다. 모든 B overlay 행은
  `numericDoseAuthority=NONE`이며 새 세트 또는 주간 용량을 만들 수 없다.
- 해부학적 활성화만으로 `DIRECT`를 부여하지 않는다. 운동의 주된 수행 과제가
  몸통 움직임 또는 안정화 제어일 때만 기존 직접 분류를 유지한다.

## 세부 코어 과제 정의

| 코드 | 정의·포함 기준 | 제외 기준 |
|---|---|---|
| `ANTI_EXTENSION` | 외부 신전 모멘트에 맞서 갈비뼈-골반 정렬을 유지 | 일반적인 복합 리프트의 자세 유지 |
| `ANTI_ROTATION` | 외부 회전 토크에 맞서 몸통 회전을 제한 | 회전을 능동적으로 만드는 동작 |
| `ANTI_LATERAL_FLEXION` | 외부 측굴 모멘트에 맞서 몸통을 정렬 | 단순 편측 사지 사용만 있는 동작 |
| `AXIAL_BRACING` | 축성 부하를 몸통 강성으로 전달하며 이동 또는 유지 | 무부하 정적 버티기 전부를 포괄하지 않음 |
| `DYNAMIC_TRUNK_STABILIZATION` | 사지 움직임 중 몸통·골반 정렬을 계속 재조절 | 정적 홀드만 있는 동작 |
| `ROTATION_GENERATION` | 몸통·골반의 능동 회전/회전 파워 생성 | 회전 저항만 하는 동작 |
| `TRUNK_FLEXION` | 몸통 굴곡 또는 후방 골반경사가 처방 목표 | 고관절 굴곡만으로 반복하는 변형 |
| `TRUNK_EXTENSION` | 몸통 신전근이 능동적으로 신전/유지 토크 생성 | 힙 힌지의 간접 척추 안정화 |

버드독·데드버그류는 사지 움직임 속에서 몸통 근육 동원이 달라지는 과제이고,
플랭크·사이드 플랭크는 정적 몸통 안정화 과제라는 근전도 근거가 있다
([Ekstrom et al.](https://pubmed.ncbi.nlm.nih.gov/22436839/),
[Souza et al.](https://pubmed.ncbi.nlm.nih.gov/11689975/)). 파머스 캐리는 보행 중
부하 전달과 몸통 활성 요구가 있으므로 `AXIAL_BRACING`으로 분리했다
([Møller et al.](https://pubmed.ncbi.nlm.nih.gov/38665162/)).

## 고관절 3종 수정

| 운동 | 이전 | 최종 |
|---|---|---|
| `cable_hip_adduction` | `HIP_ABDUCTION|HIP_ADDUCTION`; `BOUND/PLYOMETRIC`; `ADDUCTOR_ABDUCTOR`, `ANTERIOR_CORE`, `GLUTES` 모두 PRIMARY | `HIP_ADDUCTION`; `ISOLATION/HYPERTROPHY`; `HIP_ADDUCTOR` PRIMARY |
| `hip_adduction_machine` | `HIP_ABDUCTION|HIP_ADDUCTION`; `BOUND/PLYOMETRIC`; `ADDUCTOR_ABDUCTOR`, `ANTERIOR_CORE`, `GLUTES` 모두 PRIMARY | `HIP_ADDUCTION`; `ISOLATION/HYPERTROPHY`; `HIP_ADDUCTOR` PRIMARY |
| `ex_728da646` 힙 어브덕션 | `HIP_ABDUCTION|HIP_ADDUCTION`; `BOUND/PLYOMETRIC`; `GLUTES` PRIMARY + `ANTERIOR_CORE` SECONDARY | `HIP_ABDUCTION`; `ISOLATION/HYPERTROPHY`; `GLUTE_MEDIUS` PRIMARY |

내전과 외전 머신/밴드 조건에서 서로 다른 근육 활성 패턴이 관찰되므로 반대
관절작용을 동시 주목표로 남기지 않았다
([Brandt et al.](https://pubmed.ncbi.nlm.nih.gov/24377067/)). 기존 taxonomy에는
개별 내전근(adductor longus/brevis/magnus, gracilis, pectineus)을 안전하게
구분할 근거와 코드가 없으므로 새 구성근별 코드는 만들지 않고 기존 명시적
`HIP_ADDUCTOR`를 사용했다. `ADDUCTOR_ABDUCTOR`는 과거 분석 버킷으로만
호환 유지하며 canonical 해부학 관계에는 사용하지 않는다.

`BOUND`는 운동 하위유형 표시에 소비되고, 주변 bootstrap의 fatigue/deceleration/
elastic SSC 필드는 별도의 분석 입력으로 소비된다. 운동 패턴·범주·force type은
분리 근력운동에 맞게 고쳤다. 그러나 기존 배드민턴 전이 계약과 결합된 감속/SSC
수치 재기준화는 이번 범위의 새 수치 정책이 되므로 태그와 가중치는 그대로 두었다.

## 직접 코어 31개 전수 결과

| stableKey | 운동 | 기존 상위 역할 | 최종 세부 과제 | 기록 단위 |
|---|---|---|---|---|
| `band_pallof_press` | 밴드 팔로프 프레스 | ANTI_ROTATION | ANTI_ROTATION | 부하×반복 |
| `cable_pallof_press` | 케이블 팔로프 프레스 | ANTI_ROTATION | ANTI_ROTATION | 부하×반복 |
| `landmine_anti_rotation` | 랜드마인 안티로테이션 | ANTI_ROTATION | ANTI_ROTATION | 부하×반복 |
| `plate_rotational_press_out` | 플레이트 로테이셔널 프레스아웃 | ANTI_ROTATION | ANTI_ROTATION | 부하×반복 |
| `dumbbell_farmer_carry` | 덤벨 파머스 캐리 | BRACING | AXIAL_BRACING | 부하×거리/시간 |
| `kettlebell_farmer_carry` | 케틀벨 파머스 캐리 | BRACING | AXIAL_BRACING | 부하×거리/시간 |
| `ex_28347c1f` | 버드독 | BRACING | DYNAMIC_TRUNK_STABILIZATION + ANTI_ROTATION | 맨몸 좌우 반복 |
| `ex_2a826c82` | 할로우 바디 홀드 | BRACING | ANTI_EXTENSION | 유지 시간 |
| `ex_a44ae2ca` | 플랭크 | BRACING | ANTI_EXTENSION | 유지 시간 |
| `ex_a9b52886` | 마운틴 클라이머 | BRACING | DYNAMIC_TRUNK_STABILIZATION + ANTI_EXTENSION | 시간/반복 |
| `ex_d5bdffe1` | 데드버그 | BRACING | DYNAMIC_TRUNK_STABILIZATION + ANTI_EXTENSION | 맨몸 좌우 반복 |
| `ex_f6d43398` | 사이드 플랭크 | BRACING | ANTI_LATERAL_FLEXION | 유지 시간 |
| `band_lift` | 밴드 리프트 | ROTATION_GENERATION | ROTATION_GENERATION | 좌우 반복 |
| `band_woodchop` | 밴드 우드찹 | ROTATION_GENERATION | ROTATION_GENERATION | 좌우 반복 |
| `cable_lift` | 케이블 리프트 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `cable_woodchop` | 케이블 우드찹 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `dumbbell_woodchop` | 덤벨 우드찹 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `landmine_rotation` | 랜드마인 좌우 회전 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `med_ball_rotational_scoop_toss` | 메디신볼 로테이셔널 스쿱 토스 | ROTATION_GENERATION | ROTATION_GENERATION | 파워 좌우 반복 |
| `med_ball_rotational_slam` | 메디신볼 회전 아래로 슬램 | ROTATION_GENERATION | ROTATION_GENERATION | 파워 좌우 반복 |
| `medicine_ball_rotational_throw` | 메디신볼 회전 옆으로 던지기 | ROTATION_GENERATION | ROTATION_GENERATION | 파워 좌우 반복 |
| `medicine_ball_three_step_acceleration_throw` | 메디신볼 3스텝 가속 던지기 | ROTATION_GENERATION | ROTATION_GENERATION | 파워 좌우 반복 |
| `medicine_ball_three_step_deceleration_throw` | 메디신볼 3스텝 감속 던지기 | ROTATION_GENERATION | ROTATION_GENERATION | 파워 좌우 반복 |
| `vipr_chop` | 바이퍼 회전 아래로 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `vipr_rotational_lift` | 바이퍼 회전 위로 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `vipr_rotational_press_out` | 바이퍼 로테이셔널 프레스아웃 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `vipr_step_and_rotate` | 바이퍼 스텝 앤 로테이트 | ROTATION_GENERATION | ROTATION_GENERATION | 부하×좌우 반복 |
| `ex_284ecca6` | 슈퍼맨 | TRUNK_EXTENSION | TRUNK_EXTENSION | 맨몸 반복/시간 |
| `ex_644e0f9d` | V업 | TRUNK_FLEXION | TRUNK_FLEXION | 맨몸 반복 |
| `ex_a345e30b` | 캡틴체어 레그 레이즈 | TRUNK_FLEXION | TRUNK_FLEXION | 맨몸 반복 |
| `ex_b3f447be` | 행잉 레그 레이즈 | TRUNK_FLEXION | TRUNK_FLEXION | 맨몸 반복 |

우드찹·리프트·메디신볼 던지기는 몸통을 고정하는 운동이 아니라 회전력을
운동사슬로 전달하는 과제이므로 `ROTATION_GENERATION`을 유지한다
([Earp & Kraemer](https://pmc.ncbi.nlm.nih.gov/articles/PMC4925984/)). V업과 레그
레이즈는 고관절 굴근 기여가 크며, 후방 골반경사/몸통 굴곡 없이 다리만 드는
변형은 이 세부 역할의 포함 기준에서 제외한다
([Park et al.](https://pubmed.ncbi.nlm.nih.gov/34455371/),
[Youdas et al.](https://pubmed.ncbi.nlm.nih.gov/27065536/)). 현재 stableKey의
운동 설명이 복부 훈련 변형을 가리키므로 DIRECT 상위 계약은 유지하되 이
기술 경계를 문서화했다.

## 직접·간접 경계와 보류

`ex_a8385c4a` 코펜하겐 플랭크는 내전근 부하가 주된 운동 목표라는 직접적인
힘 측정 근거가 있어 `HIP_ADDUCTOR` PRIMARY로 수정했다
([Polglass et al.](https://pubmed.ncbi.nlm.nih.gov/41931009/)). 측면 코어는
SECONDARY(0.5)로 남겼다. 짧은 레버/긴 레버, 지지 높이, 골반 정렬, 수행자의
의도에 따라 측굴 저항이 직접 목표가 될 수 있으나 generic stableKey로 이를
구분할 수 없다. 따라서 `HIDDEN_HIGH`를 유지하고 세부 과제 행만
`ANTI_LATERAL_FLEXION / REVIEW_REQUIRED / NONE`으로 기록했다.

HIDDEN_HIGH 45, HIDDEN_MODERATE 82, HIDDEN_LOW 56개 중 이름·movement pattern상
오분류 위험이 높은 플랭크/캐리/회전/레그 레이즈/브레이스 후보를 대조했다.
데드리프트·RDL·싱글레그 RDL은 몸통 강성이 중요하지만 힌지 부하가 주목표여서
HIDDEN_HIGH를 유지했고, 미니밴드 사이드워크와 사이드 스텝 후 정지는 몸통
안정화가 보조 요구라 HIDDEN_MODERATE를 유지했다. 다른 DIRECT 승격은 없었다.
기존 HIDDEN 계수 0.8/0.4/0.15는 직접 세트 환산에 사용하지 않는다.

## 계산 영향과 호환성

동일한 확정 세트 `100 kg × 10회`를 해부학 관계 계수에 넣은 비교는 다음과 같다.

| 운동 | 이전 해부학 부하 | 최종 해부학 부하 |
|---|---|---|
| 케이블/머신 내전 | ADDUCTOR_ABDUCTOR 1000 + ANTERIOR_CORE 1000 + GLUTES 1000 | HIP_ADDUCTOR 1000 |
| 힙 어브덕션 | GLUTES 1000 + ANTERIOR_CORE 500 | GLUTE_MEDIUS 1000 |
| 코펜하겐 플랭크 | ADDUCTOR_ABDUCTOR/ANTERIOR_CORE/GLUTES/LATERAL_CORE 각 1000 | HIP_ADDUCTOR 1000 + LATERAL_CORE 500 |

레거시 근육 추세 버킷은 호환성을 위해 `HIP_ADDUCTOR → ADDUCTOR_ABDUCTOR`,
`GLUTE_MEDIUS → GLUTES` 정규화를 유지한다. 따라서 두 내전 운동은 기존의
GLUTES 1000이 제거되고 ADDUCTOR_ABDUCTOR 1000만 남으며, 힙 어브덕션은
coarse GLUTES 1000이 유지된다. 기존 감속·SSC·neural-speed 수치는 변경하지
않았다. 기록 원문과 stableKey는 바꾸지 않지만
과거 세션을 다시 분석하면 위 자극·피로 축 결과는 정정된 값으로 달라진다.

31개 직접 코어의 상위 관계와 `CoreStimulusCalculator` 입력은 바꾸지 않았으므로
확정 세트 하나는 계속 직접 코어 세트 하나로만 집계된다. B overlay의 다중 세부
역할은 보고·향후 선택 의미만 제공하고 CONTROL/EXPERIMENTAL 처방에는 연결되지
않는다.

## 로컬 검증 결과

- 권위 workbook validator: 253 identity, 242 selectable, 762 muscle relation,
  1,825 display row 구조로 통과. 공식 exporter `--check`도 byte-deterministic 통과.
- Python 메타데이터: 17/17 통과. 프로토콜: 9 families / 36 protocols 통과.
  Community/Cloud 계약: 9/9 통과.
- 관련 Kotlin 계약과 전체 `:app:testDebugUnitTest`: 365 suites, 2,395 tests,
  0 failures, 0 errors, 4 conditional skips. Windows native 누적 상태를 피하기 위해
  저장소에 포함하지 않는 `forkEvery=1`, 2-worker 격리를 사용했다.
- 기준/수정 후 생성기 보고서 18개 비교: 14개 SHA-256 완전 동일. C20/C33/C35/C36
  4개는 시간 필드와 메타데이터 snapshot token만 변경됐고, 이를 제외한 CONTROL/
  EXPERIMENTAL 선택·처방·B4/B5/B6/B7/B8 구조는 모두 동일했다.
- `:app:lintDebug`와 `:app:assembleDebug` 통과. APK는 69,045,273 bytes,
  SHA-256 `7F0462554BE0B9795CEEF6998BB2DF5E196D5501DCAA0310D803E3037021C50B`,
  APK Signature Scheme v2 서명 1개(`CN=Android Debug`)로 검증됐다.
- `:app:check`는 debug/lint 뒤 release Robolectric UI가
  `RoboMonitoringInstrumentation.java:101`에서 실패했다. 동일 테스트를 깨끗한
  `origin/main` 24d238e에서 같은 JBR/worker 조건으로 실행해 같은 실패를 재현했으므로
  이번 메타데이터 변경의 회귀가 아니다.
- 연결된 기기/에뮬레이터가 없어 계측 테스트는 실행하지 않았다.
