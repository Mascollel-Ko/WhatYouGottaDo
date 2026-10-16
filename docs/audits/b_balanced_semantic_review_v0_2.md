# B 균형형 정밀 메타데이터 검증 v0.2

기준: `main` `010f383a8a4c8fc89a0ffb444c67c9d32a9a017a`. 이전 재분류 253개 운동 중 검토 후보 **157개**를 실제 `identity_master`, `physical_quality_relations`, `core_relations`, `movement_relations` 원본과 대조했다. `exercise_bootstrap`에서 이미 확인한 움직임·주동근 정보도 반영했다.

> **범위:** 기계적으로 전수 대조하여 후속 검토 경로를 부여하고, 구체적인 오분류 가능성은 개별적으로 확인했다. **157개 모두를 전문적으로 승인 완료한 것은 아니다.** 원본 canonical CSV, sha256 manifest, Kotlin, Python v1.0, main 모두 불변. 이 결과는 실제 앱 처방 권한을 늘리지 않는다.

## 결과
- 검토 목록 157행 전부 후속 조치 하나로 분류. 서로 겹칠 수 있는 사유별 건수:
  - `SOURCE_IDENTITY_REVIEW_RECOMMENDED` 70건
  - `INTENTIONAL_STRENGTH_GATE_KEEP` 76건
  - `SPECIFIC_SEMANTIC_PROPOSAL` 7건
  - `SOURCE_IDENTITY_REVIEW_REQUIRED` 14건
  - `NONNUMERIC_ROLE_NEEDS_DOMAIN_REVIEW` 22건
- 주된 후속 행동별 건수 (서로 배타적):
  - `KEEP_CURRENT_WITH_SOURCE_REVIEW_NOTICE` 50건
  - `KEEP_EXACT_STRENGTH_GATE` 68건
  - `REVIEW_SPECIFIC_FIELD_PROPOSAL` 7건
  - `SOURCE_IDENTITY_DOMAIN_REVIEW` 11건
  - `PRESERVE_NONNUMERIC_UNTIL_REVIEW` 21건
- `10`개 운동에 **17개 필드별 수정/재분류 후보**를 제시.

## 수정 가치가 높은 항목

**오버헤드 삼두 익스텐션 2개:** `pq_070`, `pq_077`은 현재 `HYPERTROPHY:SHOULDERS:VERTICAL_PRESS`로 기록되어 있다. 실제 elbow extension / triceps 운동이므로 **`HYPERTROPHY:ARMS:GENERAL`**로 조정하는 안이 강하다. 비교대상 `dumbbell_lying_triceps_extension`은 이미 `ARMS:GENERAL`. 팔을 머리 위에 두었다고 수직 프레스가 되는 것은 아니다. 삼두 팔꿈치 신전 기능과 오버헤드 익스텐션의 근비대 연구도 방향을 뒷받침함 (https://www.ncbi.nlm.nih.gov/sites/books/NBK536996/ ; https://www.tandfonline.com/doi/full/10.1080/17461391.2022.2100279). **아직 실제 CSV를 변경하지 않음.**

**힙 내전 2개 / 외전 1개:** `cable_hip_adduction`, `hip_adduction_machine`, `ex_728da646`에는 원본 `jointAction=HIP_ABDUCTION|HIP_ADDUCTION`과 `bootstrap.movementPattern=BOUND`가 공존한다. 이름·운동 설명대로 B의 하위 목표는 **HIP_ADDUCTION vs HIP_ABDUCTION**으로 분리해야 한다. 힙 어덕션의 `GLUTE_MEDIUS,HIP_ADDUCTOR,CORE` PRIMARY 및 `ADDUCTOR_ABDUCTOR` 합성 코드도 직접 목표량 집계에 그대로 쓰면 오산 위험이 있다. 출처 anatomy: https://www.kenhub.com/en/library/anatomy/hip-joint . 레거시 소비자와 manifest를 확인하기 전에는 원본 코드 일괄 변경 금지.

**직접 코어 BRACING 세분화:** 버드독·데드버그·플랭크·사이드 플랭크 모두 현행 DIRECT/BRACING 상위 역할은 유지한다. `movement_relations`가 보유한 `DYNAMIC_TRUNK_STABILIZATION`, `ANTI_ROTATION`, `ANTI_EXTENSION`, `ANTI_LATERAL_FLEXION`을 B의 상세 훈련 과제로 추가한다. 이 세부 태그는 독립 세트 수를 여러 개로 늘리는 근거는 아니다.

**코펜하겐 플랭크:** `HIDDEN_HIGH`와 `ANTI_LATERAL_FLEXION`의 공존 자체는 데이터 오류가 아니다. 주된 고관절 내전 훈련과 몸통 안정화 기여를 분리해 검토; 자동 DIRECT 승격 금지.

## 오류가 아닌 것
- 76개 STRENGTH capability 운동이 기존 10개 exact 근력 credit에서 제외된 것은 승인 경계다. 76개 모두 오류라는 의미가 아님.
- `PRIMARY` 근육 관계가 세트 × 해당 근육의 직접 유효 세트 1개를 보증하지 않는다.
- 코어 `HIDDEN_HIGH` 계수를 직접 코어 세트 수로 변환하지 않는다.
- history-only 식별자 11개를 새 프로그램 후보로 승격시키지 않는다.

## 정밀 검증 후 필요한 다음 단계
1. `pq_070`, `pq_077` 등 실제 오분류를 변경할 경우: manifest sha256 및 semantic revision 갱신, 훈련 ledger/지역 역할 소비자/회귀 테스트 필수.
2. 힙 내전/외전 task 분리: 상세 자극 목표 스키마를 만들고 기존 `ADDUCTOR_ABDUCTOR` 합성 코드를 안전하게 참조.
3. 코어 상세 task를 direct class와 별도 컬럼으로 생성하고 31개 직접 코어 운동에 대해 재검토.
4. 최소 근력 10개 exact 게이트를 자동 해제하지 말고 76개 후보를 개별 연구·처방 검증.
5. 검증된 도메인 다음에만 주당 훈련량 목표 수치화 및 시간/회복 최적화 연결.

생성 파일: `b_balanced_semantic_review_triage_v0_2.csv`, `b_balanced_semantic_correction_proposals_v0_2.csv`. 모든 제안은 `AUDIT_BRANCH_PROPOSAL_ONLY`다.
