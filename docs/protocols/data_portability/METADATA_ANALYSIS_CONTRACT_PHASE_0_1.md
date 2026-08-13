# 메타데이터 분석 계약

| 항목 | 값 |
|---|---|
| Protocol ID | DATA-METADATA-ANALYSIS-CONTRACT |
| Protocol version | 2.0.0 |
| Status | ACTIVE |
| Implementation status | PARTIALLY_IMPLEMENTED |
| Implemented from app version | v0.5.0.16 shadow baseline; role split from v0.5.0.21; bundled authority cutover from v0.5.0.22; Korean display authority from v0.5.0.23; explicit override authority from v0.5.0.25; fieldKey routing from v0.5.0.26; canonical normalization from v0.5.0.32; core/objective analysis cutover from v0.5.0.33 |
| Last audited commit | 532d2343cafd9e54924dc52350c6e108893b4b07 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |

## v0.5.0.33 analysis authority cutover

The approved 241-row core review and generated canonical relation assets are
now production inputs for CoreStimulus V1. Badminton supporting-training
analysis consumes explicit objective-specific relations for exactly nine
objectives through Badminton Objective Stimulus V2. Intrinsic mechanics,
CoreClass, and CoreDirectTarget are not runtime badminton inference inputs.

Both series are rebuilt directly from raw confirmed workout sets on analysis.
Historical identities use the existing compatibility source only when the
current canonical descendants share the same relevant semantic signature.
Stored stableKeys and raw workout records are not rewritten. These projections
have explicit logical calculation versions and no legacy-derived fallback;
zero is a valid new-scale result and old/new scales are never blended.

The cutover adds no Room column, backup field, or second identity owner. OFI,
readiness, fatigue, connective tissue, ProgramBuilder, e1RM, tonnage,
bodyweight load, duration-hold load, and non-core anatomical muscle analysis
remain owned by their existing contracts.

## v0.5.0.32 canonical normalization

The canonical authority continues to use existing multi-valued relation
owners. No Room field, backup field, or parallel ontology was added.
`MOVEMENT_PATTERN:TRUNK_BRACE` was decomposed into independently reviewable
`AXIAL_BRACING`, `ANTI_ROTATION`, `ANTI_LATERAL_FLEXION`, `ANTI_EXTENSION`,
and `DYNAMIC_TRUNK_STABILIZATION` values. Active `TRUNK_ROTATION` remains a
separate movement relation. Multiple relations may coexist for one stableKey.

Intrinsic `plane`, `laterality`, muscle, and fatigue/load metadata remain
canonical facts for their legitimate consumers. They no longer independently
create badminton-transfer axes when canonical badminton authority is present.
Badminton transfer uses transfer level/type, skill target, and physical-quality
relations. Intrinsic anti-rotation does not automatically become badminton
`ANTI_ROTATION_STABILITY`; that requires an explicit badminton relation.

The reviewed shadow artifact covers all 241 selectable identities. It records
five exact rows and 236 intentional removals of obsolete generic-to-badminton
inference, with zero canonical gaps, information loss, semantic expansion, or
ambiguity. `fatigueCost`, OFI signals, ProgramBuilder classification, and
strength classification are unchanged on every row.

## v0.5.0.23 Korean metadata display authority

Workbook sheet `30_METADATA_DISPLAY_LABELS` owns presentation labels and
aliases for canonical metadata. It is outside the analysis relation model:
canonical codes, coefficients, eligibility, formulas, and persisted values
remain unchanged. Android resources are deterministic generated outputs, not
independent authorities.

The typed `MetadataDisplayCatalogue` uses exact namespace/code lookup. Search
may match Korean formal/common aliases, English labels/aliases, or the
canonical code, while rendering uses only the active-locale primary label.
Raw production-code fallback is prohibited. `e1RM` remains exactly `e1RM` by
product-owner decision.
| Supersedes | 없음 |

## v0.5.0.26 field-display routing contract

`ExerciseMetadataFieldPolicyRegistry` owns `fieldKey -> valueKind ->
localizationMode -> displayDomain -> displayDisposition`. Generated CSV/JSON
contracts are deterministic projections of that registry. Android UI callers
provide a fieldKey and canonical value to the thin `MetadataTranslator`; they
do not select a `MetadataDisplayField` domain themselves.

`MetadataDisplayCatalogue` remains the only label and search-alias content
authority. Canonical token sets are translated token by token. Ordinary free
text is passed through. `exercise.category`, `exercise.detail1`, and
`exercise.detail2` are explicit hybrid fields: exact registered values use the
catalogue and arbitrary user text remains byte-for-byte unchanged with no fuzzy
matching. Numeric, actual elapsed duration, and boolean values use typed
formatting. `exercise.archivedAt` retains its semantic value kind but is
`NEVER_DISPLAY`; it is not formatted as an elapsed duration.

Canonical values remain unchanged in Room, overrides, backup, restore,
analysis, and ProgramBuilder. Exercise-name localization is intentionally
outside this phase. Korean reachable production catalogue coverage must remain
complete, while compatibility/search-only aliases stay searchable.

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
- consumer-specific legacy-to-target mapping and semantic review
- compatibility consumer inventory
- stableKey-level legacy inference risk-path impact and separate confirmed-error ledger
- Phase 2A.1 semantic corrections, taxonomy decision matrix, and Korean Level-1 registry preflight
- isolated continuous strength-proxy prior registry with no production consumer

## 4. 비적용 범위

- production analyzer 또는 UI의 새 repository 전환
- 계산식, 임계값, label, scientific protocol 변경
- legacy metadata field 삭제
- user relation editor, Room relation table, backup schema 변경
- v2.2 target provenance model의 Kotlin 구현 또는 REVIEWED_V1 row 추가
- strength-proxy personalised posterior, production output connection, or research-calibrated prior

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

## 8.1 Phase 2A.1 승인 의미

- Legacy `trainingRole` is no longer a production metadata field. Intrinsic
  meaning is represented by typed `TrainingRole` relations and placement is
  represented independently by typed `ProgramSlotCapability` relations.
- Legacy `trainingRole` whitelist rows are no longer current production input.
  Canonical `training_roles.csv` and `program_slot_capabilities.csv` are the
  independent exact relation owners; unlisted exercises receive no relation.
- `training_role_whitelist_reconstruction.csv` remains a frozen 26-row
  historical compatibility baseline. Audit regeneration reads and validates
  it but must not rewrite it from the current seed, which no longer stores the
  legacy field.
- Program placement capability is not an analysis input. Existing Phase 0/1
  `programSlotCapabilities` contract rows remain a separate shadow-contract
  namespace and do not consume the new Room relation.
- `familyId`: target `NONE`, `DERIVED_NONCANONICAL`입니다.
- `loadProfile`: target `NONE`, `LEGACY_COMPOSITE_TO_BE_DECOMPOSED`입니다.
- `sportTransferDirect`: target contract는 closed-world exact whitelist이며 빈 관계는 authoritative `NONE`입니다. 현재 production의 이름 기반 legacy fallback은 별도 sport-transfer cutover 승인 전까지 결과 보존을 위해 유지합니다.
- strength proxy: `metadata/strength_proxy_prior_v1` 아래 isolated prior-only 구조입니다. production strength registry와 Phase 0/1 네 Kotlin 파일은 변경하지 않습니다.
- `MILITARY_PRESS`: 제품 책임자 결정으로 `ex_32219f7a`를 strict standing barbell overhead press direct anchor로 사용합니다. 무릎·엉덩이 drive를 의도하지 않으며 push press/jerk는 별도 stableKey입니다.

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
- Room: 29
- Backup format: 12
- Restore CSV schema: 11
- Program backup schema: 2, 변경 없음

### 15.3 Explicit metadata ownership overlay

The analysis contract consumes effective current metadata: current semantic
canonical seed plus explicit user override rows. It does not treat stale
persisted seed/runtime/relation rows as user authority. User-state fields are
independent and do not enter analysis metadata. Display revision changes do not
trigger semantic reconciliation.

This overlay changes ownership and portability boundaries only. It does not
change analysis formulas, coefficients, eligibility, tissue calculations, OFI,
strength posterior mathematics, or ProgramBuilder behavior.

### 15.1 Phase 0/1 shadow와 v2.2 target

현재 Phase 0/1은 current-behavior reproduction 전용입니다. `AnalysisSourceStatus`는
`MIGRATED_CURRENT_BEHAVIOR`, `USER_PERSISTED_EXACT`, `UNRESOLVED`이고 relation
confidence는 scalar `Double` 하나입니다. `derivationMode`, `migrationFidelity`,
`evidenceConfidence`, human approval의 확장 상태와 `REVIEWED_CANONICAL`은 Kotlin에
구현되어 있지 않습니다. 이 미래 v2.2 target은 별도 승인, model 확장, migration,
parity와 rollback 검증이 필요한 후속 작업입니다.

Heuristic-derived BASELINE_V1 row는 reviewed truth가 아닙니다. audit에서는
`LegacyInferenceRiskPath`와 stableKey별 영향으로 식별합니다. 위험 경로의 존재만으로
확정 오류를 선언하지 않으며, 재현 가능한 current-vs-authority mismatch만 별도
`ConfirmedMetadataIssue`에 기록합니다. 미래 REVIEWED_V1에서는 stableKey 단위 human
review 전까지 `UNRESOLVED`로 취급합니다.

### 15.2 Legacy compatibility removal gate

`progressMetricType`은 target canonical metadata가 아니며 현재
`LEGACY_COMPATIBILITY_READONLY`입니다. production consumer가 0이고 replacement
parity, backup/restore 호환성, rollback 검증, 명시적 제거 승인이 모두 끝나기
전에는 Room, adapter, backup/restore에서 삭제하거나 이름을 바꾸지 않습니다.

`activityKind` 역시 운동학 relation으로 자동 승격하지 않습니다. 현재 target은
`NON_METADATA_LEGACY_COMPATIBILITY / NONE`이며 별도 catalog taxonomy가 승인되기
전까지 `UNRESOLVED`입니다. `analysisEligibility`는 field-wide target을 두지 않고
consumer symbol과 실제 semantic use별로 검토합니다.

`defaultRestSeconds`는 program timing의 fixed property입니다. 초기 이행은 현재 값을
`ExerciseProgramTimingProfile.defaultRestSeconds`로 direct copy하며, 생성된 프로그램의
실제 처방 `restSeconds`와 혼동하지 않습니다.

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractModels.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractAssetLoader.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/UserExerciseAnalysisContractProjector.kt`
- `app/src/main/java/com/training/trackplanner/analysis/contracts/AnalysisContractShadowParity.kt`
- `app/src/main/java/com/training/trackplanner/data/CanonicalExerciseMetadataRepository.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataBackupContract.kt`
- `app/src/main/java/com/training/trackplanner/MetadataTranslator.kt`
- `app/src/main/java/com/training/trackplanner/MetadataDisplayCatalogue.kt`

## 17. 검증 테스트

- `app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractBaselineTest.kt`
- `app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractArchitectureTest.kt`
- `app/src/test/java/com/training/trackplanner/data/ExerciseMetadataFieldRegistryContractTest.kt`
- `app/src/test/java/com/training/trackplanner/MetadataTranslatorTest.kt`
- `app/src/test/java/com/training/trackplanner/MetadataDisplayCatalogueTest.kt`

Baseline test는 relation coverage, structured parity, normalized asset parity,
사용자 incomplete 상태와 production program schedule/stableKey/prescription
golden을 검증합니다.

## 18. 권위 자산

- `app/src/main/assets/metadata/analysis_contract_baseline_v1.csv`
- `app/src/test/resources/analysis-contract/analysis_contract_program_golden_v1.csv`
- `docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx`
- `app/src/main/assets/metadata/canonical_v1/manifest.json`

이 asset은 shadow contract의 권위 자산입니다. 실제 사용자 계산의 권위는
기존 calculator와 기존 reviewed tissue assets입니다.

## 19. 관련 문서

- `docs/metadata_analysis_contract_and_migration_plan_ko.md`
- `docs/audits/metadata_field_usage_matrix.csv`
- `docs/audits/metadata_field_usage_matrix.md`
- `docs/audits/metadata_parsing_inference_audit.csv`
- `docs/audits/metadata_parsing_inference_audit.md`
- `docs/audits/metadata_legacy_to_target_mapping_matrix.md`
- `docs/audits/metadata_mapping_semantic_review.md`
- `docs/audits/metadata_legacy_compatibility_consumers.md`
- `docs/audits/metadata_legacy_inference_risk_paths.md`
- `docs/audits/metadata_inference_stablekey_impact.md`
- `docs/audits/confirmed_metadata_errors.md`
- `docs/audits/metadata_analysis_contract_parity_report.md`
- `docs/audits/metadata_existing_owner_capability_audit.md`
- `docs/audits/force_type_token_audit.md`
- `docs/audits/trunk_brace_decomposition_audit.md`
- `docs/audits/metadata_information_preservation_audit.md`
- `docs/audits/metadata_normalization_shadow_parity_report.md`
- `docs/v0.5.0.18_release_notes.md`

## 20. 변경 이력

- `1.9.0` (2026-08-12): reused the existing canonical relation owners,
  decomposed the overloaded trunk-bracing bucket, switched canonical
  badminton transfer to explicit sport-specific authority, and added
  deterministic 241-identity information-preservation gates.
- `1.8.0` (2026-08-09): authoritative fieldKey display routing, typed and
  hybrid presentation policies, zero-gap Korean production coverage, and
  deterministic routing validation were connected without changing semantic
  or persistence metadata.
- `1.5.0` (2026-08-05): cut bundled metadata reads over to deterministic
  workbook-generated assets and a strict canonical repository. Frozen analysis
  behavior remains unchanged except the approved history/slot/reclassification
  boundaries; reviewed tissue runtime assets remain separate. Relationship
  correctness remains explicitly `NOT_ADJUDICATED`.
- `1.4.0` (2026-08-04): removed the mixed production `trainingRole` field,
  separated intrinsic TrainingRole from ProgramSlotCapability, and documented
  that program placement relations are not analysis inputs.
- `1.3.0` (2026-08-04): recorded the Phase 2A.1 exact legacy whitelist,
  noncanonical family/load boundaries, and isolated strength-proxy prior.
- `1.0.0` (2026-07-30): Phase 0 usage/inference audit와 Phase 1 typed
  stableKey relation baseline, user incomplete projection, shadow parity를
  추가했습니다.
- `1.1.0` (2026-07-31): fixed-property v2.1 의미 경계, read-only compatibility
  removal gate, legacy mapping/consumer/issue audit와 현재 shadow 구현 대 미래
  provenance target 구분을 추가했습니다. Production cutover는 수행하지 않았습니다.
- `1.2.0` (2026-07-31): fixed-property strategy v2.2를 채택하고 program timing,
  legacy compatibility, consumer-specific eligibility 경계를 교정했습니다. 위험 경로와
  확정 오류를 분리하고 20 x 224 stableKey impact audit 계약을 추가했습니다.
