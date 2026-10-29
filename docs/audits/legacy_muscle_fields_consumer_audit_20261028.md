# 레거시 근육 필드 소비자 전수점검 및 단계별 전환 대기열

- 점검일: 2026-10-28 (Asia/Seoul)
- 기준: `main` @ `18b5630e35cf76791037d65855880b8942c1a931`
- 범위: `Exercise.primaryMuscles`/`secondaryMuscles` (1번 레거시)와 `muscle_relations.csv` (2번 정식 근육 관계표)의 **근육 역할 정보**에 한정. 운동 목적/OFI/조직 프로파일 전체 교체 작업이 아님.
- [추적용 CSV](legacy_muscle_fields_migration_queue_20261028.csv): 총 **39개** 경로/검증 항목. `MUS-xx` = 마이그레이션/검증, `CMP-xx` = 호환 유지, `CAN-xx` = 이미 정식 사용. MUS-00과 MUS-00.5는 `CI_VERIFIED`, MUS-01·02는 `IMPLEMENTED_CI_PENDING`, 나머지는 `NOT_STARTED`.
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

## MUS-00 구현 추적 (2026-10-28)

- 구현 커밋: [`1b554df29e930dbfaf04694a8b51bd1227108f05`](https://github.com/Mascollel-Ko/WhatYouGottaDo/commit/1b554df29e930dbfaf04694a8b51bd1227108f05)
- 구현 파일: `app/src/main/java/com/training/trackplanner/data/CanonicalMuscleRoleResolver.kt`; 저장소 통합: `CanonicalExerciseMetadataRepository.muscleRoleResolver()`.
- 새 테스트: `app/src/test/java/com/training/trackplanner/data/CanonicalMuscleRoleResolverTest.kt` (6 cases: 벤치 역할 구분, 스쿼트 안정화, 히스토리/사용자/알 수 없는 운동, override 경고, 관계 없는 정식 운동, 잘못된 관계 거부).
- 자동화: [Android Debug Build #40659366770](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/40659366770) **실행 중**. 통과 전에는 DONE 아님.
- 이 단계는 read-only opt-in API뿐이며 화면, 기존 운동량/피로도 계산, Room, 운동 기록, 백업, 생성 알고리즘을 변경하지 않음.
- *중요*: 기존 필드의 사용자 override는 조회 API의 명시적 `legacyMuscleOverridePresent` 신호를 호출자가 전달해야 감지 가능하다. 기존 사용자 override 자동 판정·병합은 MUS-13/14이며 이 단계에서 구현하지 않았다.
- 이 환경에는 Android SDK/Gradle 실행 구성이 없어 **로컬 Android Gradle 테스트와 APK 빌드 결과는 없음**. CI 결과는 별도로 검증해야 한다.


## MUS-00.5 실제 구현 (2026-10-29)

- 코드: [`445d42a9d147b604885c41674f7277e10dd9249c`](https://github.com/Mascollel-Ko/WhatYouGottaDo/commit/445d42a9d147b604885c41674f7277e10dd9249c)
- 추가: `EffectiveMuscleMetadataResolver.kt` (출처/기존 사용자 근육 수정/명시적 빈값 보존), `CanonicalMusclePresentation.kt` (정식 `MUSCLE_GROUP` 한국어/영어 명칭과 검색 별칭).
- `TrainingRepository.resolvedMuscleMetadata(stableKey)` 및 `resolvedMuscleMetadataByStableKey()`는 Room `exercise_metadata_user_overrides`에서 저장된 필드를 **직접 읽어** 분류한다. 단일/일괄 경로 모두 읽기 전용이고, DB/기록/스키마/CSV 구조는 그대로 둔다.
- 사용자 추가 운동, 히스토리 전용 운동, 알 수 없는 운동, 관계 없는 selectable 운동은 canonical 여섯 역할이 있다고 가장하지 않고 구형 두 필드를 **호환용 별도 데이터**로 돌려준다. 내장 운동에서 `exercise.primaryMuscles` 또는 `exercise.secondaryMuscles` 사용자 override가 하나라도 존재하면 정식 역할을 화면/분석에 자동 투영하지 못하도록 `usesCanonicalRoles=false`다.
- override 일부만 저장된 경우 다른 필드는 현재 materialized `Exercise` 값을 보존한다. 저장된 사용자 수정 문자열을 정식 관계로 추정 변환하거나 저장 데이터를 수정하지 않는다.
- 정식 근육 코드에는 기존 `MetadataDisplayCatalogue.MUSCLE_GROUP`를 사용한다 (`MUSCLE`는 구형 표현용). `NOT_SUPPORTED`와 `REVIEW_REQUIRED`는 표시 후보에서 제외.
- 새 테스트 9개: pure resolver + 실제 Room override DAO 단일/일괄 조회, 사용자 명시적 빈값, 커스텀/히스토리, 코드별 한국어·영어 라벨과 별칭. [CI #40792846388](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/40792846388)는 현재 검증 진행 중이며 성공 전 `DONE` 처리하지 않는다.
- 중요 남은 경계: 사용자 편집기로 기존 변경을 새 정식 역할 구조에 직접 저장하는 작업(MUS-05/13/14)은 **아직 미구현**. 새 계층은 기존 편집/백업 계약을 보존하는 전환 안전장치이며, 레거시 완전 삭제를 의미하지 않는다. 화면/검색/분석 연결도 아직 수행하지 않았다.
- 과거 설치본의 미정리 사용자 변경은 기존 `ExerciseMetadataReconciliationService`로 보정되어야 한다. 이 단계가 과거 모든 DB를 무조건 탐지한다고 주장하지 않는다.

## MUS-01·02 UI 전환 추적 (2026-10-29)

- 화면 전환 기준 코드: [`ab92b31d97849315d59b904d65880e86a974993f`](https://github.com/Mascollel-Ko/WhatYouGottaDo/commit/ab92b31d97849315d59b904d65880e86a974993f). 운동·계획·기록 탭 공통 `ExerciseInfoDialog`, `ExerciseDetailCard`에 MUS-00.5의 `EffectiveMuscleMetadata`를 전달한다.
- 기본 운동의 `PRIMARY / SECONDARY / STABILIZER / GENERAL_PARTICIPATION`를 `MUSCLE_GROUP`로 한국어·영어 표시하고, `NOT_SUPPORTED / REVIEW_REQUIRED`는 확정적 사용 근육으로 표시하지 않는다. 사용자 수정, 사용자 생성, 역사 전용/미등록 운동은 MUS-00.5의 명시적 `legacy` 호환 자료만 표시한다.
- 운동 상세와 영어 요약에서 직접 `Exercise.primaryMuscles/secondaryMuscles`를 읽는 경로를 교체했다. **MUS-03 검색과 MUS-04 하위 분류는 여전히 레거시를 사용하며 이 단계에서 손대지 않았다.**
- UI 크기/가로 여백/정보창 높이를 유지했다. 두 열짜리 좁은 레이블-값 행을 만들지 않았으며, 긴 영어 요약만 `maxLines=3` + ellipsis를 사용하고 전체 역할 이름은 상세 섹션에서 표시한다. 320dp·1.5x 글자 크기 Compose 테스트, 기본 스쿼트·사용자 초기화·한/영 출력 테스트를 추가/갱신했다.
- 기록 및 클라우드/CSV 복원 완료 후 ViewModel의 근육 메타데이터 재조회도 수행하게 수정했다. Room/백업 스키마/계산식/프로그램 생성은 변경하지 않는다.
- 초기 UI 커밋은 이전 tree base 사용 실수로 MUS-00.5 파일을 일시적으로 되돌렸으나, 복원 커밋 `ab92b31d`에서 7개 파일을 원상복구했다. `4a37d483...ab92b31d` 비교 기준 변화는 UI 관련 8개 파일로 한정된다.
- CI는 **최신 통합 커밋의 전체 성공 전까지 완료로 표시하지 않는다.** 로컬 Android SDK가 없어 수동 디바이스 시각 검증도 별도로 필요하다.

## 2026-10-29 MUS-03/04 구현 및 stableKey 재기준화

- 의사결정: 사용자가 보유한 서버 백업은 **최종 1회 마이그레이션**으로, 현재의 `stableKey`에 해당하는 정식 메타데이터를 기준으로 교체한다. 일반 실행 경로에 두 벌의 근육 진실원천을 영구 유지하지 않는다. 단, 사용자 생성/미매핑 운동을 다른 기본 운동으로 추측해 치환하지 않는다. 현재 MUS-03/04는 **읽기 경로 교체**이며 서버 백업이나 로컬 저장값 자체를 변환하지 않는다.
- MUS-03: `CommonUi.kt`의 `Exercise.matchesMetadataQuery`는 기본 운동의 구형 필드를 직접 읽지 않고 `CanonicalMusclePresentation.searchableOptions`의 정식 참여 역할(주동/보조/안정화/일반 참여) 코드를 조회한다. `NOT_SUPPORTED`, `REVIEW_REQUIRED`는 검색에서 제외한다. 사용자 생성/미매핑 항목만 MUS-00.5의 명시적 호환 프로젝션 사용. 운동 탭과 기록/프로그램 운동 선택창의 검색을 함께 교체.
- MUS-04: `ExerciseSubcategoryMapper`는 `primaryMuscles/secondaryMuscles` 직접 읽기와 피로/안정화 태그의 근육 목표 추정을 중단한다. 정식 `PRIMARY` 및 움직임 목적만 분류 근거로 쓰고 `STABILIZER`는 근육 검색에만 포함한다. 기능성 이동 분류는 기존 움직임·배드민턴 목적 기반을 유지한다.
- `ExerciseScreen`: 동일한 검색 후 목록을 서브카테고리 수와 실제 목록에 사용하도록 연결하여 기존 이름 기반 `countsFor` 필터 중복을 제거했다. 화면의 폭/배치/버튼/여백 변경 없음.
- 기존 보존 설계 MUS-00.5의 근육 override 표시 원칙은 아직 상세 화면(MUS-01/02)에 남아 있다. **정식 본문 일원화는 1회 데이터 이관 및 편집기 전환 때 동기화해야 하며**, 이번 단계가 모든 레거시 제거 완료를 의미하지 않는다.
- 새 회귀 검사: 기본 운동의 옛 문자열/사용자 override 비권위 검색, 한/영/코드 검색, 안정화와 NOT_SUPPORTED 구분, 스쿼트·벤치·데드리프트 분류, 공통 검색 집계, 사용자 생성 운동 호환/미등록 안전성. 실제 기기 시각검증과 최종 hosted CI는 별도 판정한다.

## 2026-10-29 MUS-03/04 CI recovery and MUS-08/09 implementation

- Fixed the only failure in CI 40823117863 (2469 tests, 1 failed): the `progressMetricType` inventory row for `ExerciseSubcategoryMapper.kt` was obsolete after MUS-04 removed that code. Removed the row, did not reintroduce a legacy read.
- MUS-08/09: `TrainingRepository` injects `CanonicalExerciseMetadataRepository.muscleRoleResolver()` into `PerformanceTrendSummaryService`; the trend builder passes the lookup to `MuscleLoadInputBuilder`. Built-in `stableKey` is now the source for live 12-bucket muscle graph contributions.
- Use exact relation coefficients for PRIMARY/SECONDARY, not guessed new physiological multipliers; STABILIZER/GENERAL_PARTICIPATION/NOT_SUPPORTED/REVIEW_REQUIRED contribute no muscle-graph dose. Core codes remain in the separate core analysis, not falsely folded into the 12 muscle buckets.
- Exercise set load, bodyweight, RPE, week aggregation, plank/side-plank duration-hold policies, graph UI and stored records are unchanged. The no-lookup API remains only for older isolated direct callers/tests, and user-custom exercise projection remains transient pending the one-time importer. Unknown/history-only keys fail closed. This is not the final backup migration.
- Pending hosted CI + new stableKey source tests before marking MUS-08/09 CI_VERIFIED.

## 2026-10-29 최종 CI 검증 및 작업 목록 정정

- MUS-03/04의 구버전 감사 목록 불일치 수정 및 MUS-08/09의 정식 관계표 기반 그래프 로드 적용 후 최종 구현 커밋: `93bacdaeb60b29f65f3b5b8d97262901cb200095`.
- 검증: [GitHub Actions 40826623096](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/40826623096) 성공. Android JVM 단위 테스트, Release Kotlin 컴파일, Debug APK 조립/서명 검증/업로드 모두 통과.
- 기존 백업의 한 차례 `stableKey` 기준 정식 메타데이터 교체는 여전히 미수행; MUS-05·이후 저장 포맷 이관에서 실시한다. MUS-06/07의 부위별 준비도·피로 분석은 아직 직접 레거시 참조를 사용한다.

## MUS-06/07: readiness role-source migration (2026-10-29)

- Replaced default-activity readiness body-part role input with `CanonicalMuscleRoleResolver.lookup(exercise)` keyed by `stableKey` for all built-in exercises. `ExerciseAnalysisMapper` receives an opt-in role lookup and projects only `PRIMARY` / `SECONDARY`; `STABILIZER`, `GENERAL_PARTICIPATION`, `NOT_SUPPORTED`, `REVIEW_REQUIRED` are not implicitly counted as primary or secondary muscle load.
- `DailyAnalysisLoadAggregator` retains its **distinct** readiness fatigue coefficients (`LOCAL_PRIMARY_SHARE=1.0`, `LOCAL_SECONDARY_SHARE=0.45`), axial / landing / overhead / grip / hold rules, baseline, residual and all other axes. These must not be changed to the muscle-graph SECONDARY coefficient 0.5.
- The resolver is wired into `PerformanceTrendSummaryService` (fatigue trend), `DailyReadinessInputService` / `TodayReadinessEngine` (home status), and `PersonalizedProgramPlanningService` (planning recovery). No UI layout or DB schema changes.
- Custom exercises remain on the explicit legacy data path pending one-time migration. History-only and unknown keys receive no inferred muscle roles. Default no-lookup overloads remain solely for existing isolated consumer/tests until final clean-up.
- New cases: back squat, bench, deadlift, unknown/custom keys, no unexpected change to systemic fatigue; full CI pending.

## MUS-05/10/11/15 interim editor and validator switch — 2026-10-29

- Built-in muscle fields are now READ-ONLY in the metadata editor. Do not accept new primary/secondary muscle edits or same-key copy of legacy muscle columns. The `stableKey` selects canonical roles. The existing read-only exercise information dialog already presents canonical role groups.
- New/custom exercises retain the old token editor as a **temporary** compatibility route until typed custom muscle relation storage and one-time backup conversion are implemented. This is not final legacy deletion.
- `MetadataSanityChecker` and `MetadataReadinessReporter` accept an opt-in canonical role resolver, stop requiring the legacy `primaryMuscles` field for built-in exercises, and explicitly flag built-in keys without canonical relations. Existing no-resolver tests retain prior contract until call sites are converted.
- Copying exercise metadata to an existing built-in cannot change its primary or secondary legacy columns; copying to a new custom exercise still preserves fields. The canonical role resolver, DB, and back-up data are not mutated by these UI changes.
- Hosted CI pending. One-time backup migration, typed custom relation editor, and stored legacy column removal have NOT been implemented yet.
