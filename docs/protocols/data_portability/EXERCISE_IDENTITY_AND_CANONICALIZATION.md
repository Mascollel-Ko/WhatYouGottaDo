# 운동 identity와 정본화

| Field | Value |
|---|---|
| Protocol ID | DATA-EXERCISE-IDENTITY |
| Protocol version | 1.4.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.6; legacy direct-map correction from v0.5.0.8; restore metadata preservation from v0.5.0.11; workbook authority from v0.5.0.22; self-contained historical restore from v0.5.0.24; explicit metadata ownership from v0.5.0.25; relation normalization from v0.5.0.32 |
| Last audited commit | b44088c2a32d7222d97e5a213a2efea02d250f10 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |

## v0.5.0.32 relation normalization boundary

Movement/trunk-control normalization changes relations, not exercise identity.
All 241 selectable and 16 history-only stableKeys remain unchanged. No Room
schema, backup schema, stableKey migration, historical workout rewrite, or
user-created exercise rewrite is introduced. The authority workbook remains
the source; generated canonical CSV and localized display resources are checked
projections of that workbook.
| Supersedes | 없음 |

## 1. 일반 사용자용 요약

운동 이름을 바꾸거나 앱을 복원해도 같은 운동 기록이 이어지도록, 앱은
`Exercise.stableKey` 하나만 운동 identity로 사용합니다. 이름은 표시용 정보이며
identity가 아닙니다.

## 2. 목적

운동 기록, 프로그램, 분석, 연결조직 metadata와 백업이 설치별 숫자 ID나 변경 가능한
이름에 의존하지 않도록 하나의 canonical identity 계약을 제공합니다.

## 3. 적용 범위

`Exercise`, `WorkoutEntry`, `TrainingProgramItem`, 프로그램 생성과 적용, 분석 입력,
runtime metadata, 연결조직 metadata, 근력 posterior, 백업과 복원에 적용합니다.

## 4. 비적용 범위

workout entry, set, program, program item과 diagnostic report의 local row ID는 이
계약의 대상이 아닙니다. 운동 외 entity의 숫자 ID를 제거하지 않습니다.

## 5. 용어

- `canonical stableKey`: 앱이 저장하고 계산에 사용하는 불변 운동 identity
- `legacy alias`: 과거 백업을 읽는 동안에만 canonical stableKey로 바꾸는 명시적 별칭
- `ambiguous split`: 장비 등 결정 정보가 없어 하나의 canonical target을 고를 수 없는 행
- `canonical catalog`: 정본화 후 활성 built-in 운동 224개

## 6. 입력 데이터

정본 workbook의 26개 결정, 224개 최종 metadata 행, 33개 import-only legacy mapping,
기존 Room 24 exercise/reference 행과 현재 authority assets를 입력으로 사용합니다.

## 7. 계산 또는 분류 계약

정상 runtime은 stableKey exact match만 허용합니다. 이름, contains, fuzzy,
normalized-name fallback은 사용하지 않습니다. 사용자 운동은 이름과 무관한 UUID 기반
stableKey를 한 번 발급하고 rename 시 보존합니다.

Workbook의 merge, rename, split, delete 결정을 순서대로 적용합니다. Import-only
legacy 정책은 검토 완료된 generic RDL을 barbell RDL로, generic half-kneeling
one-arm press 세 key를 dumbbell press로 직접 해석합니다. 이 결정은 장비 추론이나
사용자 선택을 요구하지 않습니다.

복원 중 이미 같은 stableKey로 생성된 Exercise는 이후 WorkoutSet 행 해석에서
identity 대상으로만 재사용합니다. 해당 Exercise의 이름, category, targetMuscles,
기타 사용자 수정 metadata를 seed 값으로 다시 덮어쓰지 않습니다.

## 8. 집계 방식

bulk 처리에서는 exercise를 stableKey로 한 번 preload한 map을 사용합니다. 프로그램
seed 753개 item은 모두 명시적 canonical stableKey를 가지며, future plan도 같은 key를
그대로 사용합니다.

## 9. 출력과 UI 해석

UI는 canonical display name을 표시하되 navigation, selection, chart grouping과
analysis identity는 stableKey를 사용합니다. migration/import issue는 사용자 기록을
조용히 삭제하지 않고 작업 상세 보고서에서 확인할 수 있습니다.

## 10. 예외 및 fallback

legacy backup importer만 `exercise_legacy_import_map.csv`를 사용할 수 있습니다.
canonical key, 명시적 old key, 승인된 exact old-name alias 순으로 해석합니다.
검토되지 않은 ambiguous split은 실패하고 CSV placeholder는 warning과 함께
drop합니다. 삭제된 generic `원암 로우`의 legacy Exercise 정의 행은 warning과 함께
건너뛰며 활성 운동을 다시 만들지 않습니다. 정상 runtime에는 이 fallback이 없습니다.

## 11. 개인화 또는 보정

사용자 metadata override와 근력 posterior는 canonical stableKey에 귀속됩니다. 운동
rename은 override, history 또는 posterior identity를 바꾸지 않습니다. 복원된 runtime
metadata override가 있으면 같은 stableKey의 사용자 수정 Exercise metadata도 이후
set-row import와 seed refresh에서 함께 보존됩니다.

## 12. 연구 근거

이 계약은 과학적 효과 크기가 아니라 데이터 무결성과 설치 간 호환성을 위한 product
policy 및 engineering contract입니다.

## 13. 제품 정책 및 휴리스틱

Room `24 -> 25` migration은 기존 숫자 reference를 old exercise row를 통해 stableKey로
backfill한 뒤 final table에서 exercise ID column을 제거합니다. canonical merge는
명시적 mapping만 사용합니다. ambiguous/dangling/ID 0/blank key는 별도 issue row로
보존해 진단 가능하게 하며 destructive migration을 사용하지 않습니다.

활성 canonical 이름은 `원레그`와 `원암` 표기를 사용합니다. generic `원암 로우`와
CSV restore placeholder는 활성 catalog에 존재할 수 없습니다.

## 14. 알려진 한계

명시적 direct mapping이 없는 과거 ambiguous split과 삭제된 운동을 참조하는
WorkoutEntry/TrainingProgramItem은 기록 손실을 피하기 위해 자동 추측하거나
조용히 버리지 않습니다. 사용자 production DB의 실제 issue 수는 import 시 결정됩니다.

## 15. 현재 구현 상태

- Room schema: `25`
- active built-in exercises: `224`
- program seed item references: `753`
- legacy import mappings: `33`
- normal runtime name fallback: 없음
- destructive migration: 없음

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/data/Entities.kt`
- `app/src/main/java/com/training/trackplanner/data/Daos.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseStableKeyMigration.kt`
- `app/src/main/java/com/training/trackplanner/data/LegacyExerciseImportMapper.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingDatabase.kt`
- `tools/canonicalize_exercise_catalog.py`

## 17. 검증 테스트

- `ExerciseCatalogCanonicalizationTest`
- `ExerciseStableKeyPreservationTest`
- `LegacyExerciseImportMapperTest`
- `BackupRestoreImportBehaviorTest`
- `TrainingDatabaseMigrationTest`
- `ProgramBackupRestoreTest`

## 18. 권위 자산

- `app/src/main/assets/training_settings_seed.csv`
- `app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`
- `app/src/main/assets/exercise_legacy_import_map.csv`
- `app/schemas/com.training.trackplanner.data.TrainingDatabase/25.json`

## 19. 관련 문서

- [백업과 복원](BACKUP_AND_RESTORE.md)
- [근력 운동 catalogue](../strength/STRENGTH_EXERCISE_CATALOGUE.md)
- [배드민턴 운동 catalogue](../badminton/BADMINTON_EXERCISE_CATALOGUE.md)
- [연결조직 load unit catalogue](../connective_tissue/LOAD_UNIT_CATALOGUE.md)

## 19.1 Canonical workbook authority

- Authoring source: `docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx`
- Runtime manifest: `app/src/main/assets/metadata/canonical_v1/manifest.json`
- Strict loader: `CanonicalExerciseMetadataRepository`
- Selectable identities: 241
- History-only identities: 16, readable but inactive and non-selectable
- Relationship adjudication: `NOT_ADJUDICATED`

The workbook/export path replaces normal bundled seed inference. Legacy import
mapping remains isolated and does not rewrite a historical generic stableKey to
an equipment variant.

## 19.2 Self-contained restore identity

Backup format 11 does not use the current catalog as a blanket allowlist.
Current built-in identities resolve by exact stableKey and use the current
canonical display name. A backup identity absent from the current catalog is
materialized with the same stableKey as inactive, history-only data so workout
and program history remain readable. It is not inferred to another exercise by
name, equipment, or movement similarity.

Custom exercise stableKeys and represented editable metadata restore exactly.
Only an explicit reviewed legacy import mapping may change an old identity.
Contradictory definitions and custom/current identity collisions fail preflight
before the restore transaction. Referenced identities without a full exercise
row receive a minimal inactive historical stub rather than being silently
dropped.

## 19.3 Explicit ownership and display-name boundary

For a current built-in, effective metadata is the current semantic canonical
seed plus explicit field-level user override rows. `isActive`, `archivedAt`, and
`needsReview` remain independent user state. Stale persisted seed/runtime/relation
values are never inferred as user intent. Custom and catalogue-missing exercises
retain complete snapshots under exact stableKey identity.

`Exercise.name` remains a seed-owned materialized compatibility field for
current built-ins. It is not identity and is not user-overridable. Localized
display names belong in `ExerciseNameCatalogue` and affect only display revision.
The static production UI audit found five direct render expressions in two
files: three in `CommonUi.kt` and two in
`RuntimeMetadataExerciseEditorDialog.kt`. These compatibility paths are tracked
for future display-catalogue routing; no localization rename is persisted here.

## 20. 변경 이력

- `1.3.0` (2026-08-09): defined current seed plus explicit override ownership,
  independent user state, catalogue-missing reference retention, and the
  seed-owned `Exercise.name` display boundary.
- `1.2.0` (2026-08-06): defined self-contained backup identity semantics.
  Current built-ins keep current canonical names, missing-catalog stableKeys
  remain inactive history, custom identities restore exactly, and the current
  catalog is no longer a blanket restore allowlist.
- `1.1.0` (2026-08-05): made the canonical workbook and deterministic
  `canonical_v1` assets the bundled identity authority. The catalog contains
  241 selectable identities plus 16 inactive history-only compatibility
  identities. History-only keys cannot be selected, reactivated, inferred to
  equipment variants, or used by fresh seed programs. Existing stableKeys are
  not rewritten and Room remains schema 27.
- `1.0.0` (2026-07-28): stableKey-only identity, Room 24→25 migration,
  workbook canonicalization과 import-only legacy mapping 계약을 추가했습니다.
- `1.0.1` (2026-07-29): generic RDL과 generic half-kneeling press의 검토된
  direct import mapping, 삭제된 generic 원암 로우 Exercise 정의의 warning-drop
  계약을 추가했습니다.
- `1.0.2` (2026-07-29): 복원된 동일 stableKey Exercise를 set-row import가 seed
  metadata로 다시 덮지 않는 사용자 metadata 보존 계약을 추가했습니다.
