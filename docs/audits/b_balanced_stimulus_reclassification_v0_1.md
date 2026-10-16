# B 균형형 목표 자극 메타데이터 재분류 v0.1
기준 `main` 커밋: `010f383a8a4c8fc89a0ffb444c67c9d32a9a017a` (2026-10-16).

**범위와 보안 경계:** canonical_v1 원본 5종(identity_master, physical_quality_relations, core_relations, muscle_relations, runtime_metadata)을 `stableKey`로 조인해 B 목표 역할을 제안했다. 본 브랜치에서는 프로덕션 canonical 자산, Kotlin, Python v1.0, Room DB, 앱 처방을 **변경하지 않았다**. 이 산출물은 정적 제안이며 학술적 생체역학 검증 또는 숫자 처방 승인이 아니다.

## 전수 대조 집계
| 지표 | 건수 | 뜻 |
|---|---:|---|
| 전체 identity | 253 | 242개 선택 가능 / 11개 history only |
| 직접 근력 approved identity | 10 | 정확한 stableKey/anchor 한정; RPE, 강도, 반복, owner 검증은 별도 |
| STRENGTH 관계는 있으나 exact 승인 아님 | 76 | capability는 있어도 자동 직접 근력 세트 불가 |
| HYPERTROPHY 직접 capability | 139 | 실제 처방 판정과는 다름 |
| CORE 직접 | 31 | 기존 CoreClass.DIRECT / CoreDirectTarget |
| physical_quality 없음 (selectable) | 62 | 그중 20개는 직접 코어로 정상 분류 |
| 직접 quality/core 수치 권한 보류 | 22 | 일부 supportive-only; 자동 누락/오류 판정 금지 |
| IDENTITY REVIEW_REQUIRED | 14 | 사람 검토 필수 |
| IDENTITY REVIEW_RECOMMENDED | 70 | 상세 확인 필요 |
| 스포츠·풋워크 context | 23 | 직접 근력 세트에 합산 불가 |
| 코어 간접 역할 | 183 | 직접 코어 세트와 별도 |
| 검토 queue | 157 | 우선 검토 대상을 전체 열 그대로 분리 |

각 건수는 중복 가능: 스쿼트는 직접 최대근력 승인, HYPERTROPHY capability, 코어 간접 사용 등이 함께 존재한다.

## 열별 원칙
- `approvedStrengthAnchor`: 현행 [CanonicalStrengthExposureCapability](../../app/src/main/java/com/training/trackplanner/data/CanonicalStrengthExposureCapability.kt) 10개 exact identity만 인정. 실제 세트의 수치 크레딧은 운동별 1~6회와 기타 처방 게이트 검증 후 부여.
- `directQualityRelations`, `supportiveQualityRelations`: 원본 capability 관계 보존. 생리적 적응 결과 또는 처방 승인과 혼동 금지.
- `coreClass`와 `coreDirectTarget`: direct 코어와 hidden 간접 자극을 분리. hidden 계수는 직접 코어 주간 세트로 변환하지 않음.
- `primaryMuscleCoverageHints` / `secondaryMuscleCoverageHints`: 원본 muscle 관계의 PRIMARY / SECONDARY를 그대로 보관한 **검토 힌트**. 해당 근육에 1세트씩 직접 목표 충족 credit을 부여하지 않음.
- `canonicalMovementPattern`: 원본 문자열을 변형하지 않고 그대로 보존. 여러 움직임인 경우 임의 단일 목표로 환산 금지.
- `proposedBTargetRoles`: 독립 병렬 역할. 직접 근력, 근비대 가능성, 코어 직접, 움직임 관찰, 기술/종목 맥락을 혼합하지 않음.
- `numericDoseAuthorityStatus`: B에서 수치 처방으로 인정 가능한지 별도로 표시. 정확한 승인 운동이라도 운동 이름만으로 중량·RPE·세트 수를 정하지 않음.
- `auditFlags`, `reviewPriority`: 불명확/미승인/원본검토대상을 알려줄 뿐 직접적인 메타데이터 오류를 확정하지 않음.
- `HISTORY_ONLY` 11건은 기록용으로 보존하고 신규 프로그램 자동 후보로 복원하지 않음.

## 실제 분류 사례
| 운동 | 재분류 결과 | 처리 |
|---|---|---|
| 스쿼트 `barbell_back_squat` | DIRECT_STRENGTH_SQUAT + HYPERTROPHY capability + INDIRECT_CORE | 실제 직접 근력 인정은 처방 검증 후. 근육 PRIMARY 다수를 동시 직접세트로 인정하지 않음 |
| 루마니안 바벨 데드리프트 `barbell_romanian_deadlift` | STRENGTH_CAPABILITY_REVIEW_ONLY + HYPERTROPHY capability | 현행 10개 근력 승인 밖. 강도·RPE 검증 이전에 직접 근력 세트로 인정하지 않음 |
| 밴드 팔로프 프레스 `band_pallof_press` | DIRECT_CORE_ANTI_ROTATION | physical_quality가 없어도 코어 직접 훈련 역할 보존 |
| 버드독 `ex_28347c1f` | DIRECT_CORE_BRACING | movementPattern은 DYNAMIC_TRUNK_STABILIZATION: 코어 직접 subtype의 정합성 검토 |
| 코펜하겐 플랭크 `ex_a8385c4a` | INDIRECT_CORE_EVIDENCE_ONLY / NO_DIRECT_DOSE_AUTHORITY | ANTI_LATERAL_FLEXION 움직임과 core hidden 높은 등급의 관계에 인간 검토 필요 |
| 체스트 서포티드 로우 `ex_8e51640a` | HISTORY_ONLY | 기록으로 보존; 다른 selectable 로우의 운동 식별자로 임의 연결 금지 |

## 남은 작업
1. P1 14건 REVIEW_REQUIRED와 직접 자극 권한이 부족한 22건을 우선 대조. 두 집합은 겹칠 수 있음.
2. STRENGTH capability 76건 중 수평 당기기·보조 움직임의 직접 근력 확대 타당성 검토. 승인 확대는 별도 정책 변경.
3. 코어 DIRECT 31건의 직접 목표 정합성, hidden 계산/표시 한계 검토.
4. 근육 PRIMARY/SECONDARY를 부위별 근비대 직접 등가 세트로 간주할 수 있는지 별도 연구 및 투명한 기여 방식 설계. 근거 부족이면 불확실로 표시.
5. 연구 기반 주간 목표 범위와 개인 7/28/56일 기준선을 결합한 `desiredTarget` / `allocatedTarget` 설계. 예시 20+4세트를 임상/연구 최적량으로 간주 금지.
6. 주 3일×60분을 기준으로 초과 시 +15분 또는 +1일 시나리오를 독립적으로 시간·회복 검증.
7. Python v1.0은 보존하고 canonical 기반 B의 별도 실험으로 진행. Kotlin 이식은 실제 기록·회귀 검증 후 결정.

## 원본 파일
- `app/src/main/assets/metadata/canonical_v1/identity_master.csv`
- `app/src/main/assets/metadata/canonical_v1/physical_quality_relations.csv`
- `app/src/main/assets/metadata/canonical_v1/core_relations.csv`
- `app/src/main/assets/metadata/canonical_v1/muscle_relations.csv`
- `app/src/main/assets/metadata/canonical_v1/runtime_metadata.csv`
