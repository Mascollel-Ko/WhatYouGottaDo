# 레거시 근육 필드 소비자 전수점검 및 단계별 전환 대기열

- 점검일: 2026-10-28 (Asia/Seoul)
- 기준: `main` @ `18b5630e35cf76791037d65855880b8942c1a931`
- 범위: `Exercise.primaryMuscles`/`secondaryMuscles` (1번 레거시)와 `muscle_relations.csv` (2번 정식 근육 관계표)의 **근육 역할 정보**에 한정. 운동 목적/OFI/조직 프로파일 전체 교체 작업이 아님.
- [추적용 CSV](legacy_muscle_fields_migration_queue_20261028.csv): 총 **38개** 경로/검증 항목. `MUS-xx` = 마이그레이션/검증, `CMP-xx` = 호환 유지, `CAN-xx` = 이미 정식 사용. **모두 `NOT_STARTED`**.
- **이번 커밋은 문서만 추가한다. 실행 코드, 메타데이터, DB, 백업 포맷은 전혀 변경하지 않는다.**

## 판정 원칙

옛 근육 필드를 **운동 분류·화면 표시·근육별 분석·피로 분배의 진실 원천으로 읽는 코드는 교체**한다. 반면 이 필드를 **Room 저장, 과거 백업, 사용자 수정 및 복원 호환에 보관하는 코드는 당장 삭제하지 않는다.** 동일 문자열 `primaryMuscles`라도 B 엔진처럼 `muscleRelations`에서 파생된 로컬 변수는 레거시 소비자가 아니다.

정식 근육 관계표의 역할은 `PRIMARY / SECONDARY / STABILIZER / GENERAL_PARTICIPATION / NOT_SUPPORTED / REVIEW_REQUIRED`. `CanonicalExerciseMetadataRepository.muscleRelations()`를 통해 읽는다. 원본 편집 권위는 `docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx`, 생성물은 `app/src/main/assets/metadata/canonical_v1/*.csv`. 생성 CSV 수작업 수정 금지. 현재 manifest의 `relationshipAdjudication=NOT_ADJUDICATED`; 역할 일부는 잠정 분류이므로 의학적 확정치나 등가 근비대 세트로 간주하지 않는다.

## 확인된 경로 및 판정표

| ID | 단계/판정 | 파일 (상대경로) | 실제 사용·위험 | 전환 목표 |
|---|---|---|---|---|
| `MUS-00` | `P0/ADD` | `data/CanonicalExerciseMetadataRepository.kt` (`202-209;615-638`) | stableKey별 정식 근육 역할 조회 계층 추가 | canonical muscles; custom/override/history policy |
| `MUS-01` | `P1/REPLACE` | `CommonUi.kt` (`549-555`) | 상세 주동근/보조근에 옛 문자열 사용 | six canonical roles; stabilizer display |
| `MUS-02` | `P1/REPLACE` | `CommonUi.kt` (`352-373`) | 영어 카드 요약에 옛 문자열 사용 | canonical localized display |
| `MUS-03` | `P2/REPLACE` | `CommonUi.kt` (`657-671`) | 운동 검색이 옛 근육 문자열 사용 | canonical searchable codes and user custom fallback |
| `MUS-04` | `P2/REPLACE` | `ExerciseSubcategoryMapper.kt` (`111-131`) | 근육 문자열로 가슴/등/팔/코어 하위 분류 | movement semantics and role-aware canonical targets |
| `MUS-05` | `P4/REVIEW_EDITOR` | `RuntimeMetadataExerciseEditorDialog.kt` (`147-153`) | 레거시 두 필드의 직접 편집 UI | typed edit/override design; no user data loss |
| `MUS-06` | `P3/REPLACE` | `analysis/features/ExerciseAnalysisMapper.kt` (`158-162`) | 분석 특징에 옛 주동근/보조근 전달 | canonical role-aware inputs for specific consumers |
| `MUS-07` | `P3/REPLACE` | `analysis/readiness/DailyAnalysisLoadAggregator.kt` (`93-100;147-166;210-230`) | 옛 근육 기준 부위별 로드 primary 1.0 secondary 0.45 | canonical role-to-region; separate fatigue policy |
| `MUS-08` | `P3/REPLACE` | `analysis/lab/MuscleLoadInputBuilder.kt` (`7-35`) | 근육량 기여도 옛 근육 기준 primary 1.0 secondary 0.5 | canonical roles/coefficients and explicit bucket mapping |
| `MUS-09` | `P3/REWIRE` | `analysis/lab/StrengthAndMuscleMetricSeriesBuilder.kt` (`20-63;86-106`) | MUS-08 레거시 계산기 호출 | inject relation catalog; preserve hold-specific handling |
| `MUS-10` | `P4/REPLACE_CHECK` | `data/MetadataSanityChecker.kt` (`243-267`) | 옛 primaryMuscles의 비어있음 검사 | activity-appropriate canonical completeness |
| `MUS-11` | `P4/REPLACE_CHECK` | `analysis/features/MetadataReadinessReporter.kt` (`182-205`) | 옛 primaryMuscles의 비어있음 검사 | activity-appropriate canonical readiness coverage |
| `MUS-12` | `P3/LIMIT_COMPAT` | `data/MuscleGroupKeyNormalizer.kt` (`5-44`) | 문자열 alias 추론과 내전/외전 혼동 위험 | exact canonical code map; legacy alias for compatibility only |
| `MUS-13` | `P4/REVIEW_OVERRIDE` | `data/ExerciseMetadataEditorService.kt` (`96-135;212-223`) | 기존 사용자 편집/override 저장 | typed override or explicit fallback; preserve user edits |
| `MUS-14` | `P4/REVIEW_OVERRIDE` | `data/ExerciseMetadataEffectiveStateResolver.kt` (`23-77`) | 기본 데이터와 레거시 사용자 수정을 합성 | typed relation resolution and provenance |
| `MUS-15` | `P4/REVIEW_COPY` | `data/RuntimeExerciseMetadataResolver.kt` (`316-329`) | 운동 간 복사에 옛 근육 필드 포함 | stableKey-safe typed copy or custom compatibility |
| `MUS-16` | `P3/VERIFY_ONLY` | `data/PerformanceTrendSummaryService.kt` (`64-76`) | 근육량 계산 시계열을 분석 탭에 전달 | series comparison after MUS-09 |
| `MUS-17` | `P3/VERIFY_ONLY` | `analysis/readiness/TodayReadinessEngine.kt` (`26-65`) | 레거시 기반 부위별 피로가 준비도에 전달 | readiness regression after MUS-07 |
| `MUS-18` | `P3/VERIFY_ONLY` | `analysis/readiness/ResidualFatigueCalculator.kt` (`18-59`) | 부위별 부하 감쇠 | verify updated upstream not rewrite decay |
| `MUS-19` | `P3/VERIFY_ONLY` | `analysis/readiness/StatisticalBaselineCalculator.kt` (`43-60`) | 부위별 기준선 계산 | verify baseline revision |
| `MUS-20` | `P3/VERIFY_ONLY` | `analysis/readiness/FatiguePressureCalculator.kt` (`34-51`) | 부위별 압력 계산 | verify updated inputs |
| `MUS-21` | `P3/VERIFY_ONLY` | `data/PersonalizedProgramPlanningService.kt` (`1563-1617`) | 준비도 결과를 프로그램 회복 신호로 전달 | compare CONTROL/EXPERIMENTAL outcome |
| `MUS-22` | `P3/VERIFY_ONLY` | `data/TodayStatusSummaryService.kt` (`13-18`) | 홈 현재/예상 준비도 출력 | compare current/projected |
| `CMP-01` | `KEEP/KEEP_COMPAT` | `data/Entities.kt` (`8-26`) | Room 레거시 열 | do not drop database fields now |
| `CMP-02` | `KEEP/KEEP_COMPAT` | `data/CanonicalExerciseMetadataRepository.kt` (`125-170;502-520`) | bootstrap의 옛 근육 필드 읽기 | keep bootstrap; switch semantic consumers |
| `CMP-03` | `KEEP/KEEP_COMPAT` | `data/ExerciseMetadataBackupContract.kt` (`144-145;559-560;629-634`) | 레거시 백업 및 사용자 편집 계약 | versioned migration; no blind deletion |
| `CMP-04` | `KEEP/KEEP_COMPAT` | `data/RecordCsvBackupRestore.kt` (`625-630;1414-1418;1794-1798`) | 기존 CSV primary_muscles/secondary_muscles | preserve old backup roundtrip |
| `CMP-05` | `KEEP/KEEP_COMPAT` | `data/SafeBackupRestore.kt` (`412-439`) | SQL 백업 필드 | preserve old restore |
| `CMP-06` | `KEEP/KEEP_COMPAT` | `data/BackupRestoreCanonicalizer.kt` (`147-155;365-371`) | 복원 필드 복사 및 정리 | preserve old custom records |
| `CMP-07` | `KEEP/KEEP_COMPAT` | `data/BackupRestoreImportService.kt` (`913-949`) | 레거시 운동 메타데이터 입력 | preserve stableKey/import |
| `CMP-08` | `KEEP/KEEP_COMPAT` | `data/TrainingRepository.kt` (`1359-1386`) | 복원/시드 데이터의 근육 문자열 복사 | keep ingestion while consumers migrate |
| `CMP-09` | `KEEP/KEEP_COMPAT` | `data/ExerciseStableKeyMigration.kt` (`54-83;350-356;426-435`) | 과거 Room 스키마 이전 | never edit historical DB migrations |
| `CMP-10` | `KEEP/KEEP_COMPAT` | `data/TrainingRoleSplitMigration.kt` (`schema SQL`) | 과거 Room 스키마 이전 | never edit historical DB migrations |
| `CMP-11` | `KEEP/KEEP_COMPAT` | `app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv` (`primaryMuscles;secondaryMuscles`) | 생성된 운동 기본 필드 | edit workbook only; regenerate later |
| `CMP-12` | `KEEP/KEEP_COMPAT` | `app/src/main/assets/metadata/canonical_v1/metadata_field_display_contract.json` (`legacy field keys`) | 필드 표시/번역 계약 | retain until replacement editors are ready |
| `CMP-13` | `KEEP/REVIEW_EXTERNAL` | `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv` (`search hit`) | 필드명 존재; 실제 소비 경로 미검증 | separately verify tissue system before schema removal |
| `CAN-01` | `KEEP/ALREADY_CANONICAL` | `data/v22/BBalanced12WeekEngine.kt` (`264-281`) | primaryMuscles는 정식 muscleRelations에서 생성된 로컬 변수 | no change; false-positive name |
| `CAN-02` | `KEEP/ALREADY_CANONICAL` | `data/v22/V22ProgramPlanningService.kt` (`muscleRelations passed`) | 정식 relation을 B 엔진에 전달 | no change |

### 직접 근거

1. `CommonUi.kt`: 운동 상세 549–555, 영어 요약 352–373, 근육명 검색 657–671이 옛 필드 직접 사용.
2. `ExerciseSubcategoryMapper.kt` 111–131: 근육명 문자열을 다른 움직임 태그들과 혼합하여 가슴/등/팔/코어 등 하위 분류. 안정화 근육을 목표근으로 오인할 위험.
3. `MuscleLoadInputBuilder.kt` 8–17: 레거시 주동근에 1.0, 보조근에 0.5 배분. `StrengthAndMuscleMetricSeriesBuilder.kt` 57–60이 호출; 플랭크 등 duration-hold 전용 경로는 별도.
4. `ExerciseAnalysisMapper.kt` 161–162 -> `DailyAnalysisLoadAggregator.kt` 93–96, 147–166: 준비도용 신체부위 부하는 주동 1.0, 보조 **0.45**. 그래프의 0.5와 별도 정책. 감속/착지/축부하/오버헤드/그립 가산은 별도이므로 그대로 보전.
5. `TodayReadinessEngine.kt`은 위 부위별 부하를 잔여 부담·준비도에 쓰며, `PersonalizedProgramPlanningService.kt` 1569–1617은 준비도 요약을 계획 회복 입력에 보낸다. **간접 경로**이지 모든 프로그램 선별이나 정식 OFI가 레거시 근육을 사용한다는 증거는 아니다.
6. `MetadataSanityChecker.kt` 246과 `MetadataReadinessReporter.kt` 185: `primaryMuscles`의 존재만으로 일부 품질판정. 정식 관계에는 PRIMARY가 없는 스포츠/풋워크도 있어 무조건 필수로 검사하면 안 된다.
7. `MuscleGroupKeyNormalizer.kt` 18: 구형 문자열 alias의 `ADDUCTOR`와 `ABDUCTOR`를 `HIP_ADDUCTOR`로 묶는 분기; 정식 `muscleCode`를 이 alias 추론에 통과시키지 말 것.
8. `ExerciseMetadataBackupContract.kt` 144–145 및 629–633은 예전 필드를 편집 가능·백업 가능 항목으로 정의. `ExerciseMetadataEffectiveStateResolver.kt`는 canonical bootstrap과 사용자 override를 합친다. 그러므로 **내장 운동의 사용자 수정과 사용자 추가 운동**을 위한 대응 정책이 먼저 필요하다.
9. `BBalanced12WeekEngine.kt` 264–280의 `primaryMuscles`는 옛 `Exercise.primaryMuscles`가 아니라 정식 `muscleRelations`에서 도출되므로 **교체 제외**.

## 실행 순서: 하나씩 별도 커밋

- **0단계 MUS-00**: 정확한 stableKey 기반 정식 관계 조회기/테스트. 내장·히스토리 전용·사용자 추가·사용자 override에 대해 `출처`와 `존재/미존재`를 명시. 아직 실서비스 출력은 변경하지 않는다.
- **1단계 MUS-01~03**: 화면 상세/영문 요약/검색만 정식 근육 관계표로 연결. 안정화 근육을 보조근에 섞지 않고 별도 표시.
- **2단계 MUS-04**: 하위 카테고리만 별도 교체. 움직임 분류와 사용자 검색을 보호.
- **3단계 MUS-08/09/12 + MUS-16**, 이후 **MUS-06/07 + MUS-17~22**: 근육량 그래프부터 교체하고, 그다음 별도 커밋에서 부위별 피로/준비도를 교체. 각 단계에서 기존/변경 시계열, 회복압력, 홈 예상치, CONTROL/EXPERIMENTAL 생성 결과 차이를 비교. **수치 변경은 의도와 근거를 문서화**.
- **4단계 MUS-05/13~15**: 편집기 및 사용자 override 정책 확정 후 연동. 사용자 추가 운동은 정식 카탈로그에 stableKey가 없으므로 무조건 근육 정보 없음으로 처리하면 안 된다. 신형 typed override 또는 명시적인 레거시 fallback을 구현.
- **5단계 MUS-10/11**: 유효성 검사기 교체. 스포츠 훈련은 `GENERAL_PARTICIPATION`만 있을 수 있으므로 유형별 조건.
- **최종 정리 CMP 항목**: 과거 CSV/Room 업그레이드와 사용자 override의 복구 성공이 확인되기 전까지 문자열 열과 구형 DB 마이그레이션은 제거 금지.

## 필수 검증 및 변경 금지 범위

- 스쿼트(코어 안정화), 벤치(어깨·삼두 보조), 데드(등·코어 안정화), 고블릿·피스톨·박스·스케이터 스쿼트, 리버스 리스트컬, 글루트 브릿지, 사용자 생성 운동, 내장 운동의 사용자 override를 회귀 검증.
- 정식 muscleCode 예: `QUADS`, `HAMSTRINGS`, `BACK_LATS`, `ANTERIOR_CORE`, `FOREARM_GRIP`. 구형 `QUADRICEPS`, `HAMSTRING`, `LAT`, `CORE`를 맹목적으로 동치로 변환하지 말고 목적(시각화/부담/계획)별 명시적 매핑.
- `NOT_SUPPORTED`/`REVIEW_REQUIRED`/막연한 `GENERAL_PARTICIPATION`에 직접적인 근비대 기여를 부여하지 않음. `STABILIZER`의 근비대 계수 0은 생리적 부하 0을 의미하지 않음. 코어 직접 훈련은 별도 `core_relations.csv`에서 구분.
- `stableKey`, 과거 운동 기록/세트, 사용자 수정, 백업/복원, 진행 세션 연결, 수동 계획을 보존; 기록 원본을 재작성하지 않음.
- 각 구현 후 `./gradlew testDebugUnitTest assembleDebug` 등 로컬 검증, 메타데이터/내보내기 일치 검증, 호스팅 CI 통과 여부 기록. 실제 환경에 맞는 Gradle task 명칭은 확인 후 기록할 것.
- 단순 정적 검색 결과가 전수 완결의 보장은 아니다. 다음 구현 담당자는 로컬 저장소에서 `rg 'primaryMuscles|secondaryMuscles|primary_muscles|secondary_muscles|MuscleLoadInputBuilder|bodyPartLoads'`를 재실행하여 빠진 경로를 추가한다.
- 완료한 항목에만 CSV의 `status=DONE`, `implementation_sha`, `validation`을 채움. CI만 통과하고 사용자 데이터 호환을 검증하지 않았다면 완료가 아님.

## 선행 자료
- `docs/audits/canonical_muscle_762_workbook_review_20261019.md`: 역할 제안의 잠정성, 중복된 bootstrap 근육 문자열, 데이터 보전 주의사항.
- `docs/audits/metadata_existing_owner_capability_audit.md`: 기존 권위 매핑; 옛 문자열을 `KEEP_AS_CANONICAL_METADATA`로 분류한 행은 신규 6역할 관계표의 우선 소비 권위를 보장한다는 뜻이 아님.
