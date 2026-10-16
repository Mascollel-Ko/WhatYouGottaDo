# B 균형형 메타데이터 실제 반영 v0.3

기준 원본 `main`: `010f383a8a4c8fc89a0ffb444c67c9d32a9a017a`.

## 이번 브랜치의 실제 canonical 수정

- `app/src/main/assets/metadata/canonical_v1/physical_quality_relations.csv`
  - `pq_070` `cable_overhead_triceps_extension`: `HYPERTROPHY:SHOULDERS:VERTICAL_PRESS` → `HYPERTROPHY:ARMS:GENERAL`
  - `pq_077` `dumbbell_overhead_triceps_extension`: 같은 수정.
  - 이유: overhead는 상완 위치이고 해당 운동의 주된 관절 작용은 **팔꿈치 신전**, 훈련 부위는 삼두. 이 두 운동을 어깨 수직 프레스 직접 자극으로 인정할 수 없다.
  - `relationId`, `stableKey`, `qualityId`, 직접 자극 가능성과 `prescriptionDependent=YES`, `reviewStatus=PASS` 유지. 행수 **279 불변**.
  - 각각의 `provenance`와 `notes`는 정밀 재검토를 기록하도록 수정.
- `app/src/main/assets/metadata/canonical_v1/manifest.json`
  - 위 파일의 SHA256을 `5319b3db25df03eccefc7f7356f2e261d6d35d53254af2dd884731899e6baf01`에서
    `ddfbf2c1c36f9813f0fdfe6733f82b4897cf071ba5e2582ef89ccd010647cc8e`로 갱신.
  - canonical 행수 및 workbook authority hash는 그대로.
- `tools/metadata_authority/tests/test_physical_quality_relations.py`
  - 두 익스텐션이 `ARMS/GENERAL`인지 확인하고 실제 OHP의 `VERTICAL_PRESS`는 유지하는 회귀 테스트 추가.

## 추가 B 목표 분류 자산 (미활성 단계)

- `app/src/main/assets/metadata/b_balanced_v1/target_task_relations.csv`: 7개 운동/9개 행.
  - HIP_ADDUCTION (케이블 어덕션·머신 어덕션), HIP_ABDUCTION (힙 어브덕션)
  - 버드독 `DYNAMIC_TRUNK_STABILIZATION` + `ANTI_ROTATION`
  - 데드버그 `DYNAMIC_TRUNK_STABILIZATION` + `ANTI_EXTENSION`
  - 플랭크 `ANTI_EXTENSION`, 사이드 플랭크 `ANTI_LATERAL_FLEXION`
- 모든 관계의 `numericDoseAuthority=NONE`: 분류 상세화 자체로 세트 권한을 만들지 않는다.
- 기존 코어 `DIRECT/BRACING` 상위 분류와 `CORE_CLASS`는 유지한다.
- 해당 파일은 **현행 Python v1.0·Kotlin 생성기에서 아직 읽지 않는다**. 향후 B 신규 생성기의 typed reader로 연결할 때 실제 소비된다.
- `tools/metadata_authority/tests/test_b_balanced_target_task_relations.py`에서 overlay 관계, source identity, 코어 직접 역할·움직임 정합성 검증.

## 이번 범위에서 하지 않은 수정

- `identity_master.csv`, `movement_relations.csv`, `exercise_bootstrap.csv`, `muscle_relations.csv`, `core_relations.csv` 수정 없음.
- 내전/외전 운동의 레거시 `BOUND`와 내전근·중둔근 주동근 충돌은 **canonical workbook의 authoring authority**와 downstream 영향검사가 필요하므로 원본 변경 보류.
- 코펜하겐 플랭크를 직접 코어로 승격하지 않음.
- 10개 exact 근력 운동 승인 범위, 76개 capability/credit 경계 변경 없음.
- 주간 목표 세트 20+4안이나 보조·코어 세트 목표 수치 확정하지 않음.
- 기존 v2.2 Python v1.0 동결 및 Kotlin 실서비스 라우터/처방 경계 유지.

## 필요한 실행 검증

1. `python -m unittest discover -s tools/metadata_authority/tests` (특히 새 회귀 테스트 두 개).
2. `python tools/metadata_authority/export_canonical_metadata.py --check --workbook docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx --output app/src/main/assets/metadata/canonical_v1`.
3. `python scripts/validate_protocol_docs.py`.
4. `./gradlew testDebugUnitTest` 및 `assembleDebug` (CI 준비 환경에서 실행).
5. 실제 B 생성기가 추후 overlay를 소비하게 될 경우 별도 7/28/56일 ledger 및 전체 계획 결과 회귀 테스트.

**본 커밋들은 감사 브랜치에만 존재하며, main 반영은 CI 및 리뷰 이후 별도 판단한다.**
