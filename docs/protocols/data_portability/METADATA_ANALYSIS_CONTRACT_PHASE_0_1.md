# 메타데이터 분석 계약

| 항목 | 값 |
|---|---|
| Protocol ID | DATA-METADATA-ANALYSIS-CONTRACT |
| Protocol version | 1.0.0 |
| Status | ACTIVE |
| Implementation status | PARTIALLY_IMPLEMENTED |
| Implemented from app version | v0.5.0.16 shadow baseline |
| Last audited commit | 47f93eadaff64a49f6dc886a9319191c7388029c |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | 없음 |

## 1. 일반 사용자용 요약

현재 분석 결과는 바뀌지 않습니다. 이 단계는 기존 계산 결과를 운동
`stableKey`별 typed relation으로 동결해 향후 안전한 전환 기준을 만듭니다.

## 2. 목적

분산된 metadata 소비와 fallback을 감사하고, OFI, 프로그램, 근육,
배드민턴, 연결조직 capability를 단일 운동 분류가 아닌 다중 관계로
표현합니다.

## 3. 적용 범위

- 224개 built-in 운동의 current-behavior baseline
- typed relation model과 immutable asset reader
- 사용자 운동의 보수적 shadow projection
- old-vs-new structured parity와 production program golden
- metadata field usage 및 legacy inference audit

## 4. 비적용 범위

- production analyzer 또는 UI의 새 repository 전환
- 계산식, 임계값, label, scientific protocol 변경
- legacy metadata field 삭제
- user relation editor, Room relation table, backup schema 변경

## 5. 용어

- `exerciseStableKey`: 유일한 운동 identity
- `MIGRATED_CURRENT_BEHAVIOR`: 새 연구 결론이 아닌 현재 동작 보존 provenance
- `INCOMPLETE`: exact 관계가 없어 추론하지 않은 상태
- `shadow`: 사용자 출력에 쓰지 않고 기존 결과와 비교하는 경로

## 6. 입력 데이터

Built-in baseline은 `SeedData`, canonical runtime metadata, 기존 calculator와
reviewed 224-key RCV catalog를 입력으로 사용합니다. 사용자 projection은
persisted scalar와 `MetadataTokenField.values`만 읽습니다.

## 7. 계산 또는 분류 계약

모든 관계는 exact `exerciseStableKey`에 연결합니다. 운동명, stableKey
fragment, metadata code substring, raw delimiter를 의미 추론에 사용하지
않습니다.

| 영역 | typed relation |
|---|---|
| Shared | `ExerciseAnalysisCapability` |
| OFI | dose profile, canonical five-axis contribution, purpose-specific comparison group |
| Program | multi-slot capability, role eligibility, variant group, progression group |
| Muscle | multi-unit contribution |
| Badminton | transfer point, physical-quality point, fatigue-cost category |
| Support | movement pattern, joint action, body region, modality, training goal |

OFI axis는 `HIGH_FORCE_NEURAL`, `SYSTEMIC_MUSCULAR`, `LOCAL_MUSCULAR`,
`HIGH_SPEED`, `REACTIVE`의 다섯 개입니다. 연결조직 coefficient는 기존
reviewed authority에 남고 이 baseline에는 exact capability만 기록합니다.

## 8. 집계 방식

관계는 exercise당 한 global category로 축약하지 않습니다. 다중 기여는
각각 별도 row입니다. `OFI_DOSE`와 `OFI_SNAPSHOT`처럼 single-select인
관계는 중복 row를 거부합니다.

Contract logical identity는 `ANALYSIS_CONTRACT_BASELINE_V1`입니다. 현재
asset은 224개 stableKey와 9,781개 relation row를 포함합니다.

## 9. 출력과 UI 해석

새 repository는 정상 UI에 노출되지 않습니다. 정상 화면과 API는 기존
calculator 결과를 계속 표시합니다.

## 10. 예외 및 fallback

Built-in parity mismatch는 테스트 실패입니다. 사용자 관계가 없으면
`INCOMPLETE`로 남기며 name/family 유사도나 다른 운동의 관계로 채우지
않습니다.

## 11. 개인화 또는 보정

사용자 운동 relation은 저장하지 않습니다. persisted runtime metadata의
exact 값만 shadow projection하며 OFI, muscle, connective-tissue와 불명확한
program 관계는 `INCOMPLETE`입니다. `analysisEligibility`를 training goal로
재해석하지 않습니다.

## 12. 연구 근거

이 버전은 과학 모델 변경이 아닙니다. 모든 baseline row의 provenance는
현재 구현 동작이며 새 evidence claim을 만들지 않습니다.

## 13. 제품 정책 및 휴리스틱

기존 동작을 oracle로 고정하고 production cutover를 분리합니다. Immutable
asset과 non-persisted projection을 사용해 Room/backup 변경 없이 rollback할
수 있게 합니다.

## 14. 알려진 한계

- production calculator는 아직 legacy parsing/fallback을 포함합니다.
- public program runtime은 `ProgramAutoBuilder`이며 legacy candidate trace를
  직접 노출하지 않습니다.
- user relation override는 저장되지 않습니다.
- joint action과 training goal은 reviewed authority가 없어 비어 있습니다.

## 15. 현재 구현 상태

- Contract status: `ACTIVE`
- Runtime implementation: `PARTIALLY_IMPLEMENTED`
- Production cutover: 수행하지 않음
- Room: 26, 변경 없음
- Backup format: 9, 변경 없음
- Restore CSV schema: 8, 변경 없음

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractModels.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractAssetLoader.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/UserExerciseAnalysisContractProjector.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractShadowParity.kt`

## 17. 검증 테스트

- `app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractBaselineTest.kt`
- `app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractArchitectureTest.kt`

Baseline test는 relation coverage, structured parity, normalized asset parity,
사용자 incomplete 상태와 production program schedule/stableKey/prescription
golden을 검증합니다.

## 18. 권위 자산

- `app/src/main/assets/metadata/analysis_contract_baseline_v1.csv`
- `app/src/test/resources/analysis-contract/analysis_contract_program_golden_v1.csv`

이 asset은 shadow contract의 권위 자산입니다. 실제 사용자 계산의 권위는
기존 calculator와 기존 reviewed tissue assets입니다.

## 19. 관련 문서

- `docs/metadata_analysis_contract_and_migration_plan_ko.md`
- `docs/audits/metadata_field_usage_matrix.csv`
- `docs/audits/metadata_field_usage_matrix.md`
- `docs/audits/metadata_parsing_inference_audit.csv`
- `docs/audits/metadata_parsing_inference_audit.md`
- `docs/audits/metadata_analysis_contract_parity_report.md`
- `docs/v0.5.0.16_release_notes.md`

## 20. 변경 이력

- `1.0.0` (2026-07-30): Phase 0 usage/inference audit와 Phase 1 typed
  stableKey relation baseline, user incomplete projection, shadow parity를
  추가했습니다.
