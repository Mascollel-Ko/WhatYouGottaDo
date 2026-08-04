# WhatYouGottaDo Protocol Library

이 디렉터리는 현재 공식 product protocol의 canonical human-readable authority입니다. 기계 판독 registry는 [`protocol_registry.json`](protocol_registry.json)이며 source code, tests, authority assets와 database contract가 실제 runtime authority입니다. 문서와 runtime이 다르면 mismatch를 `implementationStatus`와 `knownGaps`에 공개합니다.

Protocol version과 application version은 독립적입니다. protocol `1.0.0`은 첫 governed documentation contract라는 뜻이며 과학적 확실성, 임상 타당성, 부상 예측 정확도 또는 model completeness를 뜻하지 않습니다.

## Authority order

1. 현재 runtime source, tests, authority assets와 database contract
2. 이 library의 canonical documents와 registry
3. supporting research, evidence table, audit와 implementation note
4. release note, worklog, handoff와 superseded design

## Family index

| Family | 제목 | 설명 | Index |
|---|---|---|---|
| `OFI` | 종합 피로도 지수 | 다섯 피로 축의 종합 지수와 표시 계약 | [열기](ofi/OFI_CORE.md) |
| `OFI_AXES` | OFI 다섯 피로 축 | 고중량·힘 신경계, 전신 근육, 국소 근육, 고속, 반응 | [열기](ofi/axes/HIGH_FORCE_NEURAL.md) |
| `CONNECTIVE_TISSUE` | 연결조직 부하와 회복 | 조직별 상대 노출, 잔여 노출, 개인 보정과 표시 | [열기](connective_tissue/CONNECTIVE_TISSUE_OVERVIEW.md) |
| `BADMINTON` | 배드민턴 분류와 부하 | 훈련 방법, 전이 분류, 부하와 catalogue | [열기](badminton/BADMINTON_TRAINING_TAXONOMY.md) |
| `STRENGTH` | 근력훈련 분류와 부하 | taxonomy, volume, 체중·시간 유지 운동과 주요 리프트 proxy 수행 추정 | [열기](strength/STRENGTH_TRAINING_TAXONOMY.md) |
| `PROGRAM_BUILDER` | 자동 프로그램 생성 | 현재 공개 결정론적 자동 골자 생성 계약 | [열기](program_builder/PROGRAM_BUILDER_OVERVIEW.md) |
| `DATA_PORTABILITY` | 백업과 복원 | legacy 호환성과 authoritative program snapshot | [열기](data_portability/BACKUP_AND_RESTORE.md) |
| `UI_PRESENTATION` | 제품 UI 표시 | 조용한 표면 계층, 내비게이션, OFI 요약과 강조 정책 | [열기](presentation/QUIET_UI_PRESENTATION.md) |

## Protocol status

| Family | Protocol ID | Korean title | Protocol version | Status | Runtime implementation | Evidence profile | First app version | Last audited commit | Canonical doc |
|---|---|---|---|---|---|---|---|---|---|
| `OFI` | `OFI-CORE` | 종합 피로도 지수(OFI) | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/OFI_CORE.md) |
| `OFI` | `OFI-CLASSIFICATION` | OFI 분류와 표시 | `1.3.0` | `ACTIVE` | `IMPLEMENTED` | USER_APPROVED_POLICY, PRODUCT_POLICY | `v0.4.2.15`; chart dates `v0.4.2.16`; quiet summary `v0.5.0.0`; calendar presentation `v0.5.0.10` | `c2f30971e12849fbc18b7cb3ab97e12499707e23` | [문서](ofi/OFI_CLASSIFICATION_AND_PRESENTATION.md) |
| `OFI_AXES` | `OFI-AXIS-HIGH-FORCE-NEURAL` | 고중량·힘 신경계 피로 축 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/axes/HIGH_FORCE_NEURAL.md) |
| `OFI_AXES` | `OFI-AXIS-WHOLE-BODY` | 전신 근육 피로 축 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/axes/WHOLE_BODY.md) |
| `OFI_AXES` | `OFI-AXIS-LOCAL-MUSCLE` | 국소 근육 피로 축 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/axes/LOCAL_MUSCLE.md) |
| `OFI_AXES` | `OFI-AXIS-HIGH-SPEED` | 고속 피로 축 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/axes/HIGH_SPEED.md) |
| `OFI_AXES` | `OFI-AXIS-REACTIVE` | 반응 피로 축 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.15` | `aa08b49ff183c60c45c9e8bf95a9542df1b592ce` | [문서](ofi/axes/REACTIVE.md) |
| `CONNECTIVE_TISSUE` | `CT-OVERVIEW` | 연결조직 부하·회복 개요 | `2.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY | `v0.4.2.7`; EDU-2 `v0.4.2.13` | `f2479c8cbf89649469495966d3e8cc09ff49ad8d` | [문서](connective_tissue/CONNECTIVE_TISSUE_OVERVIEW.md) |
| `CONNECTIVE_TISSUE` | `CT-MSCP-DI-EXPOSURE` | MSCP-DI 노출 모델 | `1.0.1` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, MECHANISTIC_SUPPORT, PRODUCT_POLICY | `v0.4.2.7` | `22e51779bbd173e554c3ba1dbeec0fcf13a6ba20` | [문서](connective_tissue/MSCP_DI_EXPOSURE_MODEL.md) |
| `CONNECTIVE_TISSUE` | `CT-LOAD-UNIT-CATALOGUE` | 연결조직 load unit catalogue | `1.2.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `v0.4.2.7`; canonical identities `v0.5.0.6` | `401ece4ca451b5303b3607bf8b3462b95f25a581` | [문서](connective_tissue/LOAD_UNIT_CATALOGUE.md) |
| `CONNECTIVE_TISSUE` | `CT-RECOVERY-CURVES` | 연결조직 회복 곡선 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, LOW_CONFIDENCE_PROXY, PRODUCT_POLICY | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/RECOVERY_CURVES.md) |
| `CONNECTIVE_TISSUE` | `CT-PERSONAL-CALIBRATION` | 연결조직 개인 기준과 상대 상태 | `2.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.12` | `b95a1684ad8bc0ba82cd5eae52ccb3147eae4d61` | [문서](connective_tissue/PERSONAL_CALIBRATION.md) |
| `CONNECTIVE_TISSUE` | `CT-COD-CONTEXT` | 방향전환 context modifier | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | USER_APPROVED_POLICY, PRODUCT_POLICY | `v0.4.2.10` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/COD_CONTEXT_MODIFIER.md) |
| `CONNECTIVE_TISSUE` | `CT-RANKING-PRESENTATION` | 연결조직 순위와 표시 | `2.1.1` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, USER_APPROVED_POLICY | `v0.4.2.8`; EDU-2 `v0.4.2.13`; diagnostics internal-only `v0.4.2.14` | `9b9124d1d9cd5b6fcdd42a7578d2295f991bbe1b` | [문서](connective_tissue/RANKING_AND_PRESENTATION.md) |
| `BADMINTON` | `BADMINTON-TAXONOMY` | 배드민턴 훈련 taxonomy | `1.1.0` | `ACTIVE` | `IMPLEMENTED`; identity closeout `ARTIFACT_ONLY` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `UNKNOWN_PENDING_AUDIT`; stableKey/taxonomy closeout `2026-08-04` | `86c56ca4f74c02f4d1da48b4dd985106642ae42b` | [문서](badminton/BADMINTON_TRAINING_TAXONOMY.md) |
| `BADMINTON` | `BADMINTON-VOLUME` | 배드민턴 훈련량 계산 | `1.0.1` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT`; chart dates `v0.4.2.16` | `60e21c6b847f1dc2910ddbdc5ee2d4690631cb9e` | [문서](badminton/BADMINTON_VOLUME_CALCULATION.md) |
| `BADMINTON` | `BADMINTON-TRANSFER` | 배드민턴 전이 분류 | `1.1.0` | `ACTIVE` | `IMPLEMENTED`; relation closeout `ARTIFACT_ONLY` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `UNKNOWN_PENDING_AUDIT`; chart dates `v0.4.2.16`; relation closeout `2026-08-04` | `86c56ca4f74c02f4d1da48b4dd985106642ae42b` | [문서](badminton/BADMINTON_TRANSFER_CATEGORIES.md) |
| `BADMINTON` | `BADMINTON-CATALOGUE` | 배드민턴 운동 catalogue | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `v0.3.5.0`; canonical identities `v0.5.0.6` | `401ece4ca451b5303b3607bf8b3462b95f25a581` | [문서](badminton/BADMINTON_EXERCISE_CATALOGUE.md) |
| `STRENGTH` | `STRENGTH-TAXONOMY` | 근력훈련 taxonomy | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `v0.3.5.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/STRENGTH_TRAINING_TAXONOMY.md) |
| `STRENGTH` | `STRENGTH-VOLUME` | 근력훈련 volume 계산 | `1.0.3` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT`; chart dates/e1RM domain `v0.4.2.16`; persistent posterior boundary `v0.5.0.2` | `43f11ec` | [문서](strength/STRENGTH_VOLUME_CALCULATION.md) |
| `STRENGTH` | `STRENGTH-PROXY-PERFORMANCE` | 지속형 근력 수행능력 사후분포 | `3.1.0` | `EXPERIMENTAL` | `IMPLEMENTED`; relation closeout `ARTIFACT_ONLY` | DIRECT_RESEARCH_SUPPORT, PRODUCT_POLICY, ENGINEERING_HEURISTIC, LOW_CONFIDENCE_PROXY | `v0.5.0.3`; isolated five-target prior preflight `v0.5.0.19`; 17-relation closeout `2026-08-04` | `86c56ca4f74c02f4d1da48b4dd985106642ae42b` | [문서](strength/PROXY_PERFORMANCE_ESTIMATION.md) |
| `STRENGTH` | `STRENGTH-BODYWEIGHT-LOAD` | 체중 운동 유효 부하 | `1.0.1` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT`; weighted pull-up posterior boundary `v0.5.0.2` | `43f11ec` | [문서](strength/BODYWEIGHT_EFFECTIVE_LOAD.md) |
| `STRENGTH` | `STRENGTH-DURATION-HOLD` | 시간 유지 운동 부하 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/DURATION_HOLD_LOAD.md) |
| `STRENGTH` | `STRENGTH-CATALOGUE` | 근력 운동 catalogue | `1.2.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `v0.3.5.0`; canonical identities `v0.5.0.6`; localized presentation `v0.5.0.14` | `8f78c99b11af14c2715a36532d83256e7ebfe4bf` | [문서](strength/STRENGTH_EXERCISE_CATALOGUE.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-OVERVIEW` | 자동 프로그램 생성 개요 | `1.3.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0`; exact manual sets `v0.5.0.12`; exact application `v0.5.0.13`; typed user notices `v0.5.0.14` | `8f78c99b11af14c2715a36532d83256e7ebfe4bf` | [문서](program_builder/PROGRAM_BUILDER_OVERVIEW.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-SLOTS` | 프로그램 slot과 role 모델 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/SLOT_AND_ROLE_MODEL.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-SCORING` | 운동 선택과 우선순위 | `1.0.0` | `ACTIVE` | `PARTIALLY_IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/EXERCISE_SELECTION_AND_SCORING.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-CONSTRAINTS` | 대체와 제약 규칙 | `1.0.0` | `ACTIVE` | `PARTIALLY_IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/REPLACEMENT_AND_CONSTRAINT_RULES.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-EVALUATION` | 프로그램 평가 계약 | `1.0.1` | `DRAFT` | `SPECIFICATION_ONLY` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `—` | `2369d91aaa80351193b20ccc2714d2be11edd3a2` | [문서](program_builder/PROGRAM_EVALUATION.md) |
| `DATA_PORTABILITY` | `DATA-EXERCISE-IDENTITY` | 운동 identity와 정본화 | `1.0.2` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.5.0.6`; legacy direct-map correction `v0.5.0.8`; restore metadata preservation `v0.5.0.11` | `f27463841c60384a0779a60ea92ed82d4d0e2c85` | [문서](data_portability/EXERCISE_IDENTITY_AND_CANONICALIZATION.md) |
| `DATA_PORTABILITY` | `DATA-BACKUP-RESTORE` | 백업과 복원 | `1.2.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.5.0.5`; format 8 `v0.5.0.6`; exact program sets `v0.5.0.12` | `e7d9317cf2ba618b8fadfcdcb772763a32618c09` | [문서](data_portability/BACKUP_AND_RESTORE.md) |
| `DATA_PORTABILITY` | `DATA-METADATA-ANALYSIS-CONTRACT` | 메타데이터 분석 계약 | `1.3.0` | `ACTIVE` | `PARTIALLY_IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.5.0.16` shadow baseline; v2.3 Phase 2A.1 corrections `v0.5.0.19` | `40615fab9c7ff892b0e48dd5a244eeb77e7cf2ee` | [문서](data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md) |
| `UI_PRESENTATION` | `UI-QUIET-PRESENTATION` | 조용한 UI 표시 원칙 | `1.1.0` | `ACTIVE` | `IMPLEMENTED` | USER_APPROVED_POLICY, PRODUCT_POLICY | `v0.5.0.0`; metadata/program results `v0.5.0.14` | `8f78c99b11af14c2715a36532d83256e7ebfe4bf` | [문서](presentation/QUIET_UI_PRESENTATION.md) |

## Reading and publishing

- v0.5.0.21 separates intrinsic `TrainingRole` from placement-only
  `ProgramSlotCapability`. The latter has exactly 26 approved stableKey rows,
  is not an analysis input, and is backed by Room schema 27 and backup format 10.

- v0.5.0.18의 metadata strategy v2.2 audit은 program timing fixed property, legacy compatibility, consumer-specific eligibility를 분리하고 위험 경로와 확정 오류를 별도 산출물로 관리합니다. 현재 Phase 0/1 shadow baseline과 production 계산은 그대로이며 미래 provenance model은 아직 구현되지 않았습니다.
- v0.5.0.19의 metadata strategy v2.3 Phase 2A.1은 legacy role exact whitelist와 closed-world direct transfer를 복구하고 `familyId`/`loadProfile`을 비정본 호환으로 확정합니다. 다섯 target strength-proxy prior는 별도 package/asset에만 존재하며 production posterior에는 연결되지 않습니다.
- 2026-08-04 strength-proxy relation closeout은 5개 direct anchor와 12개 shared-factor proxy의 membership만 artifact로 확정합니다. 7개 provisional relation은 exclusion provenance로 남기며 population/personal alpha와 no-session 주간 latent-state 보간 구현은 `DEFERRED`입니다.
- 2026-08-04 badminton-transfer closeout은 224개 기존 source row를 최신 241개 canonical identity에 정합합니다. source metadata, current runtime mapper, 7개 derived display axis는 별도 층이며 runtime 계산은 변경하지 않습니다. Legacy consumer 대체와 승인된 한국어 정의는 `REVIEW_REQUIRED`입니다.
- v0.4.2.16부터 분석 주간 차트는 Monday-Sunday, Thursday month ownership, 월별 chronological ordinal을 한 shared authority로 사용하며 e1RM은 모든 표시 운동의 union domain을 보존합니다.
- v0.5.0.0부터 주요 화면은 한 섹션당 하나의 의미 있는 surface, 중립 배경, restrained state emphasis와 canonical 다섯 행 OFI 요약을 사용합니다. 이 릴리스에는 bitmap이나 장식 illustration을 추가하지 않습니다.
- v0.5.0.1부터 벤치프레스, 스쿼트, 데드리프트의 관련 운동 수행은 실제 e1RM과 분리된 실험적 proxy posterior로만 표시합니다. posterior는 `metricSeries`, legacy 시계열 분석 또는 strict BVAR/BLP 준비 입력에 들어가지 않습니다.
- v0.5.0.5부터 새 기록 백업은 현재 program graph와 built-in 삭제 tombstone을 authoritative snapshot으로 포함합니다. Marker 없는 legacy 파일은 현재 program state를 변경하지 않습니다.
- v0.5.0.6부터 exercise stableKey가 유일한 운동 identity입니다. Room 25,
  backup format 8, 224개 canonical catalog와 import-only legacy mapping이 같은
  identity 경계를 사용합니다.
- v0.5.0.11부터 복원된 동일 stableKey Exercise와 runtime metadata override는
  후속 set-row import와 seed refresh에서 사용자 수정 metadata를 유지합니다.
- v0.5.0.12부터 수동 프로그램은 set별 처방을 authoritative child row로
  저장하며, 이전 scalar program과 program schema 1 backup은 fallback으로
  계속 읽습니다.
- v0.5.0.13부터 저장 프로그램 적용은 fatigue/readiness와 분리된 exact
  materialization입니다. 모든 운동과 set은 저장된 그대로 unconfirmed
  plan이 되며 분석 결과는 advisory로만 남습니다.
- v0.5.0.14부터 canonical metadata code는 저장·분석에 그대로 유지하고
  field-aware 한국어/영어 label은 presentation에서만 적용합니다. 정상
  사용자 화면은 program diagnostic 또는 enum code를 직접 표시하지 않으며
  unknown metadata 값도 원문을 보존합니다.
- v0.5.0.16은 224개 built-in stableKey의 현재 분석 출력을 typed relation
  baseline으로 동결합니다. 이 관계는 shadow 검증 전용이며 기존 OFI, 프로그램,
  근육, 배드민턴 및 연결조직 계산기가 계속 production authority입니다.
- `CT-PERSONAL-CALIBRATION`의 generated BasePrior, profile adjustment, per-unit PersonalBaseline와 relative-state UI는 v0.4.2.12부터 `DESIGNED / GENERATED / VALIDATED / RUNTIME_ACTIVE / TESTED`입니다. 짧은 history도 prior로 분류하며 `w_perUnit`은 비교 경계에만 적용됩니다.
- 연결조직 교육 설명은 v0.4.2.13부터 `RCV-ALL-0.6-EDU-2`의 77개 하위 조직과 15개 상위 관절군을 완전 커버하며, 한 대화상자에서 `위치`, `주요 기능`, `주로 사용되는 동작`만 보여 줍니다.
- 앱에서는 홈의 `이 앱이 분석하는 것 보기`에서 제품 설명을 거쳐 이 공개 프로토콜 인덱스를 열 수 있습니다.
- 일반 사용자는 각 문서의 `일반 사용자용 요약`, `출력과 UI 해석`, `알려진 한계`를 먼저 읽을 수 있습니다.
- 개발자는 계산 계약과 source/test/asset anchor를 함께 확인해야 합니다.
- 변경은 [`CONTRIBUTING_PROTOCOLS.md`](CONTRIBUTING_PROTOCOLS.md)와 [`PROTOCOL_CHANGE_POLICY.md`](PROTOCOL_CHANGE_POLICY.md)를 따릅니다.
- 기존 문서 분류는 [`LEGACY_DOCUMENT_MAP.md`](LEGACY_DOCUMENT_MAP.md)에 보존합니다.
- canonical Markdown은 향후 GitHub Pages, 앱의 `계산 방식` 화면, public review와 issue/PR contribution에 재사용할 수 있습니다.
- 별도 배포를 만들더라도 이 Markdown repository가 source of truth입니다.
