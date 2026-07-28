# 백업과 복원

| 항목 | 값 |
|---|---|
| Protocol ID | DATA-BACKUP-RESTORE |
| Protocol version | 1.1.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.5; stableKey-only format from v0.5.0.6 |
| Last audited commit | 401ece4ca451b5303b3607bf8b3462b95f25a581 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | 없음 |

`1.0.0`은 백업·복원 호환성 계약을 처음 canonical protocol로 고정한
버전입니다.

## 1. 일반 사용자용 요약

`v0.5.0.5`부터 기록 백업은 운동 기록뿐 아니라 현재 저장된 프로그램,
프로그램 구성 운동, 삭제한 기본 프로그램 상태까지 한 CSV에 포함합니다.
새 백업을 복원하면 프로그램은 백업 당시 상태로 정확히 교체됩니다. 이전
버전의 백업에는 프로그램 정보가 없으므로, 그런 파일을 복원해도 현재
프로그램은 변경되지 않습니다.

## 2. 목적

현재 training program 정의와 삭제한 built-in program 상태를 기존 기록
백업에 함께 보존하고, legacy file과 새 authoritative snapshot의 의미를
명확하게 구분합니다.

## 3. 적용 범위

이 계약은 `RecordCsvBackupRestore`, `BackupExportService`,
`BackupRestoreImportService`, program seed와 Room program tables에
적용됩니다.

## 4. 비적용 범위

Workout 기록, 프로필, 일별 상태, exercise metadata와 strength analysis
입력의 기존 백업 의미는 바꾸지 않습니다. Backup 암호화, cloud
synchronization, partial merge와 cross-account conflict resolution은
제공하지 않습니다.

## 5. 용어

- `stable key`: 설치와 local Room ID가 바뀌어도 유지하는 identity
- `authoritative snapshot`: 현재 program state 전체를 정확히 대체하는 자료
- `tombstone`: 사용자가 built-in program을 삭제했다는 persistent record
- `legacy file`: `program_snapshot` marker가 없는 이전 CSV

## 6. 입력 데이터

입력은 기존 restore CSV와 새 program snapshot row입니다. Program item은
parent program stable key와 exercise stable key를 필수 identity로
사용합니다. 이름과 category는 사람이 읽을 수 있는 보조 metadata이며 새
schema의 identity fallback이 아닙니다.

## 7. 계산 또는 분류 계약

전체 restore CSV schema는 `8`, program backup schema는 `1`입니다. 새
백업은 다음 row type을 사용합니다.

| Row type | 의미 |
|---|---|
| `program_snapshot` | 완전한 program snapshot의 존재와 schema version |
| `program` | program stable key와 설정 |
| `program_item` | parent program key, exercise stable key와 처방 |
| `program_tombstone` | 사용자가 삭제한 built-in program key |

`program_snapshot` marker가 authoritative 여부를 결정합니다. marker가
있고 program row가 0개인 파일은 유효한 빈 snapshot이며, 복원 시 현재
program을 모두 제거합니다. marker가 없는 legacy 파일은 program 정보가
없는 것으로 간주하여 현재 program, item과 tombstone을 전혀 변경하지
않습니다.

## 8. 집계 방식

Export count는 program, program item과 program tombstone을 각각 셉니다.
Snapshot은 incremental patch가 아니라 export 시점의 전체 program graph를
한 단위로 집계합니다.

## 9. 출력과 UI 해석

백업·복원 완료 결과는 기존 profile/daily/check-in/entry/set 개수와 함께
program, program item, program tombstone 개수를 표시합니다. 0은 오류가
아니며 legacy import 또는 실제 빈 snapshot일 수 있고, 둘은 marker로
구분합니다.

## 10. 예외 및 fallback

Parser는 program table을 변경하기 전에 전체 snapshot을 읽고 다음을
거부합니다.

- conflicting marker 또는 지원하지 않는 future schema
- blank/duplicate program key
- orphan item 또는 blank exercise key
- 잘못된 week/day/order/rest/set/repetition/weight/seconds 값
- 같은 program/day의 중복 item position
- blank/duplicate tombstone
- 같은 key의 program과 tombstone 동시 존재

Marker가 없는 legacy file은 현재 program state를 그대로 둡니다. 새
snapshot의 exercise stable key가 대상 DB에 없으면 이름 fallback을 사용하지
않고 import를 실패합니다.

## 11. 개인화 또는 보정

사용자별 계산 보정은 없습니다. Persistent identity와 tombstone만 사용자
program state를 보존합니다.

## 12. 연구 근거

해당 없음. 이 문서는 데이터 무결성과 호환성을 위한 제품·engineering
계약입니다.

## 13. 제품 정책 및 휴리스틱

### Persistent identity

- Built-in program은 seed asset의 canonical `program_key`를 사용합니다.
- 사용자 program과 새 generated program은 `user_program_` 접두사의
  UUID stable key를 사용합니다.
- 기존 program을 generated 결과로 교체하면 기존 stable key를 보존합니다.
- 이름 수정은 stable key를 바꾸지 않습니다.
- Room local `id`와 표시 이름은 cross-install identity가 아닙니다.
- migration으로 옮긴 unmatched program은 `legacy_program_<old id>` key를
  받습니다. 정확한 seed graph가 하나만 일치할 때만 initialization repair가
  canonical built-in key로 한 번 변경합니다.

### Export

새 export는 export 시점의 전체 program graph를 기록합니다. program은
stable key, item은 parent/week/day/order/exercise key, tombstone은 program
key 순으로 결정론적으로 정렬됩니다. program item의 exercise는 현재
exercise table에서 stable key로 해석합니다. 누락되거나 blank인 exercise
stable key가 있으면 불완전 snapshot을 내보내지 않고 export를 실패합니다.

이름, category와 prescription의 한국어, 쉼표, 따옴표와 줄바꿈은 기존 CSV
escaping 계약으로 보존됩니다. 완료 결과에는 program, program item,
program tombstone 개수가 추가됩니다.

### Import transaction

Authoritative restore는 같은 Room transaction에서 exercise stable key를
canonical exercise row로 먼저 해석한 뒤 현재 item, program,
tombstone을 snapshot으로 교체합니다. local program ID는 새로 발급하고
item parent를 remap합니다. 하나라도 해석 또는 insert가 실패하면 program
graph를 포함한 import transaction 전체를 rollback합니다. 같은 파일을
반복 import해도 최종 상태와 row 수는 같습니다.

### Tombstone과 seed

정상 삭제 경로에서 built-in program을 삭제하면 item과 program 삭제와
tombstone 저장을 한 transaction으로 수행합니다. 사용자 program 삭제는
tombstone을 만들지 않습니다. 같은 canonical key의 program을 저장하거나
복원하면 모순되는 tombstone을 제거합니다.

Seeding은 이름이 아니라 stable key를 기준으로 동작합니다. 기존 key는
사용자가 수정했더라도 덮어쓰지 않고, tombstoned key는 다시 만들지
않습니다. 이후 버전에 새 built-in key가 추가되면 tombstone이 없는 새
key만 삽입합니다.

## 14. 알려진 한계

Program definitions를 교체하거나 삭제해도 이미 calendar에 적용된 workout
entry, confirmed/planned set과 historical analysis input은 삭제하거나
수정하지 않습니다. Program item에는 exercise 이름과 category를 readable
fallback metadata로 함께 저장하지만, 새 schema 복원의 identity는 exercise
stable key만 사용합니다.

새 snapshot은 partial merge가 아니라 authoritative replacement입니다.

## 15. 현재 구현 상태

App `v0.5.0.6`, restore CSV schema `8`, program backup schema `1`에서
구현됩니다.

Room `25`는 `Exercise.stableKey` primary key와 workout/program item의
`exerciseStableKey` foreign key/index를 사용합니다. `24 -> 25` migration은
유효한 numeric exercise reference를 canonical stableKey로 backfill하고 final
table에서 exercise ID column을 제거합니다. ID 0, dangling reference, blank key와
ambiguous split은 structured migration issue로 보존하며 destructive migration을
사용하지 않습니다.

Room `24`에서 도입한 `training_programs.stableKey` unique index와
`training_program_tombstones` table을 추가합니다. `23 -> 24` migration은
program/item row를 보존하고 임시 persistent legacy key를 backfill합니다.
Initialization repair는 exact seed graph가 유일하게 일치할 때만 built-in
key로 승격하며 marker를 기록해 다시 실행하지 않습니다. Destructive
migration은 사용하지 않습니다.

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/data/RecordCsvBackupRestore.kt`
- `app/src/main/java/com/training/trackplanner/data/BackupExportService.kt`
- `app/src/main/java/com/training/trackplanner/data/BackupRestoreImportService.kt`
- `app/src/main/java/com/training/trackplanner/data/BackupPreflightValidator.kt`
- `app/src/main/java/com/training/trackplanner/data/BackupRestoreCanonicalizer.kt`
- `app/src/main/java/com/training/trackplanner/data/DataTransferReport.kt`
- `app/src/main/java/com/training/trackplanner/data/LegacyExerciseImportMapper.kt`
- `app/src/main/java/com/training/trackplanner/data/Entities.kt`
- `app/src/main/java/com/training/trackplanner/data/Daos.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingDatabase.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
- `app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt`

## 17. 검증 테스트

- `ProgramBackupRestoreTest`: legacy non-destructive import, snapshot
  round-trip/idempotence, tombstone/seed evolution, modified built-in,
  empty snapshot, rollback, stable-key repair와 identity policy
- `RecordCsvBackupRestoreTest`: 기존 CSV parser/export compatibility
- `BackupIntegrityBoundaryTest`: all-error preflight, manifest/hash/count와
  destination no-write 보장
- `DataTransferReportStoreTest`: 성공/실패 report persistence와 최근 20개 retention
- `LegacyExerciseImportMapperTest`: exact import-only mapping과 ambiguous rejection
- `BackupRestoreImportBehaviorTest`: 기존 import transaction behavior
- `TrainingDatabaseMigrationTest`: Room `23 -> 24`와 `24 -> 25` row/reference 보존

## 18. 권위 자산

- `app/src/main/assets/training_settings_seed.csv`: built-in program
  `program_key`와 seed graph
- Room exported schema `23.json`, `24.json`, `25.json`: migration boundary
- `app/src/main/assets/exercise_legacy_import_map.csv`: legacy importer 전용 exact mapping

## 19. 관련 문서

- `docs/v0.5.0.5_release_notes.md`: app release scope와 검증 결과
- `docs/v0.5.0.6_release_notes.md`: stableKey-only backup format과 정본화 release
- `docs/protocols/data_portability/EXERCISE_IDENTITY_AND_CANONICALIZATION.md`
- `docs/codex_worklog.md`: implementation worklog

## 20. 변경 이력

- `1.0.0`: authoritative program snapshot, stable identity, tombstone와
  legacy compatibility 계약 추가.
- `1.1.0`: backup format 8 manifest/hash/count 검증, all-error preflight,
  stableKey-only exercise reference, structured report와 Room 24→25 rollback 계약 추가.
