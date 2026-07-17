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
| `OFI_AXES` | OFI 다섯 피로 축 | 신경계, 전신 근육, 국소 근육, 관절·건·충격, 동작·집중 | [열기](ofi/axes/HIGH_FORCE_NEURAL.md) |
| `CONNECTIVE_TISSUE` | 연결조직 부하와 회복 | 조직별 상대 노출, 잔여 노출, 개인 보정과 표시 | [열기](connective_tissue/CONNECTIVE_TISSUE_OVERVIEW.md) |
| `BADMINTON` | 배드민턴 분류와 부하 | 훈련 방법, 전이 분류, 부하와 catalogue | [열기](badminton/BADMINTON_TRAINING_TAXONOMY.md) |
| `STRENGTH` | 근력훈련 분류와 부하 | taxonomy, volume, 체중 운동과 시간 유지 운동 | [열기](strength/STRENGTH_TRAINING_TAXONOMY.md) |
| `PROGRAM_BUILDER` | 자동 프로그램 생성 | 현재 공개 결정론적 자동 골자 생성 계약 | [열기](program_builder/PROGRAM_BUILDER_OVERVIEW.md) |

## Protocol status

| Family | Protocol ID | Korean title | Protocol version | Status | Runtime implementation | Evidence profile | First app version | Last audited commit | Canonical doc |
|---|---|---|---|---|---|---|---|---|---|
| `OFI` | `OFI-CORE` | 종합 피로도 지수(OFI) | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/OFI_CORE.md) |
| `OFI` | `OFI-CLASSIFICATION` | OFI 분류와 표시 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | USER_APPROVED_POLICY, PRODUCT_POLICY | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/OFI_CLASSIFICATION_AND_PRESENTATION.md) |
| `OFI_AXES` | `OFI-AXIS-HIGH-FORCE-NEURAL` | 신경계 피로 축 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/axes/HIGH_FORCE_NEURAL.md) |
| `OFI_AXES` | `OFI-AXIS-WHOLE-BODY` | 전신 근육 피로 축 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/axes/WHOLE_BODY.md) |
| `OFI_AXES` | `OFI-AXIS-LOCAL-MUSCLE` | 국소 근육 피로 축 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/axes/LOCAL_MUSCLE.md) |
| `OFI_AXES` | `OFI-AXIS-HIGH-SPEED` | 관절·건·충격 피로 축 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/axes/HIGH_SPEED.md) |
| `OFI_AXES` | `OFI-AXIS-REACTIVE` | 동작·집중 피로 축 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.6` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](ofi/axes/REACTIVE.md) |
| `CONNECTIVE_TISSUE` | `CT-OVERVIEW` | 연결조직 부하·회복 개요 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY, LOW_CONFIDENCE_PROXY | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/CONNECTIVE_TISSUE_OVERVIEW.md) |
| `CONNECTIVE_TISSUE` | `CT-MSCP-DI-EXPOSURE` | MSCP-DI 노출 모델 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, MECHANISTIC_SUPPORT, PRODUCT_POLICY | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/MSCP_DI_EXPOSURE_MODEL.md) |
| `CONNECTIVE_TISSUE` | `CT-LOAD-UNIT-CATALOGUE` | 연결조직 load unit catalogue | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/LOAD_UNIT_CATALOGUE.md) |
| `CONNECTIVE_TISSUE` | `CT-RECOVERY-CURVES` | 연결조직 회복 곡선 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, LOW_CONFIDENCE_PROXY, PRODUCT_POLICY | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/RECOVERY_CURVES.md) |
| `CONNECTIVE_TISSUE` | `CT-PERSONAL-CALIBRATION` | 연결조직 개인 보정 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.7` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/PERSONAL_CALIBRATION.md) |
| `CONNECTIVE_TISSUE` | `CT-COD-CONTEXT` | 방향전환 context modifier | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | USER_APPROVED_POLICY, PRODUCT_POLICY | `v0.4.2.10` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/COD_CONTEXT_MODIFIER.md) |
| `CONNECTIVE_TISSUE` | `CT-RANKING-PRESENTATION` | 연결조직 순위와 표시 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, USER_APPROVED_POLICY | `v0.4.2.8` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](connective_tissue/RANKING_AND_PRESENTATION.md) |
| `BADMINTON` | `BADMINTON-TAXONOMY` | 배드민턴 훈련 taxonomy | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](badminton/BADMINTON_TRAINING_TAXONOMY.md) |
| `BADMINTON` | `BADMINTON-VOLUME` | 배드민턴 훈련량 계산 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](badminton/BADMINTON_VOLUME_CALCULATION.md) |
| `BADMINTON` | `BADMINTON-TRANSFER` | 배드민턴 전이 분류 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](badminton/BADMINTON_TRANSFER_CATEGORIES.md) |
| `BADMINTON` | `BADMINTON-CATALOGUE` | 배드민턴 운동 catalogue | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `v0.3.5.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](badminton/BADMINTON_EXERCISE_CATALOGUE.md) |
| `STRENGTH` | `STRENGTH-TAXONOMY` | 근력훈련 taxonomy | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, RESEARCH_TRANSFER | `v0.3.5.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/STRENGTH_TRAINING_TAXONOMY.md) |
| `STRENGTH` | `STRENGTH-VOLUME` | 근력훈련 volume 계산 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/STRENGTH_VOLUME_CALCULATION.md) |
| `STRENGTH` | `STRENGTH-BODYWEIGHT-LOAD` | 체중 운동 유효 부하 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/BODYWEIGHT_EFFECTIVE_LOAD.md) |
| `STRENGTH` | `STRENGTH-DURATION-HOLD` | 시간 유지 운동 부하 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `UNKNOWN_PENDING_AUDIT` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/DURATION_HOLD_LOAD.md) |
| `STRENGTH` | `STRENGTH-CATALOGUE` | 근력 운동 catalogue | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | MIXED, RESEARCH_TRANSFER, PRODUCT_POLICY | `v0.3.5.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](strength/STRENGTH_EXERCISE_CATALOGUE.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-OVERVIEW` | 자동 프로그램 생성 개요 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/PROGRAM_BUILDER_OVERVIEW.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-SLOTS` | 프로그램 slot과 role 모델 | `1.0.0` | `ACTIVE` | `IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/SLOT_AND_ROLE_MODEL.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-SCORING` | 운동 선택과 우선순위 | `1.0.0` | `ACTIVE` | `PARTIALLY_IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/EXERCISE_SELECTION_AND_SCORING.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-CONSTRAINTS` | 대체와 제약 규칙 | `1.0.0` | `ACTIVE` | `PARTIALLY_IMPLEMENTED` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `v0.4.2.0` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/REPLACEMENT_AND_CONSTRAINT_RULES.md) |
| `PROGRAM_BUILDER` | `PROGRAM-BUILDER-EVALUATION` | 프로그램 평가 계약 | `1.0.0` | `DRAFT` | `SPECIFICATION_ONLY` | PRODUCT_POLICY, ENGINEERING_HEURISTIC | `—` | `06b65f6cdb243780e97a7464f659219b50010c7c` | [문서](program_builder/PROGRAM_EVALUATION.md) |

## Reading and publishing

- 앱에서는 홈의 `이 앱이 분석하는 것 보기`에서 제품 설명을 거쳐 이 공개 프로토콜 인덱스를 열 수 있습니다.
- 일반 사용자는 각 문서의 `일반 사용자용 요약`, `출력과 UI 해석`, `알려진 한계`를 먼저 읽을 수 있습니다.
- 개발자는 계산 계약과 source/test/asset anchor를 함께 확인해야 합니다.
- 변경은 [`CONTRIBUTING_PROTOCOLS.md`](CONTRIBUTING_PROTOCOLS.md)와 [`PROTOCOL_CHANGE_POLICY.md`](PROTOCOL_CHANGE_POLICY.md)를 따릅니다.
- 기존 문서 분류는 [`LEGACY_DOCUMENT_MAP.md`](LEGACY_DOCUMENT_MAP.md)에 보존합니다.
- canonical Markdown은 향후 GitHub Pages, 앱의 `계산 방식` 화면, public review와 issue/PR contribution에 재사용할 수 있습니다.
- 별도 배포를 만들더라도 이 Markdown repository가 source of truth입니다.
