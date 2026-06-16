# Initial Profile Backup / Restore Report

## 백업

- restore CSV에 `row_type=profile` row를 추가했다.
- profile row는 `profile_key`, `profile_value`로 저장한다.
- 기존 daily/set/exercise row와 같은 파일에 포함된다.

## 복원

- `RecordCsvBackupRestore.parse()`가 profile row를 읽는다.
- repository import 시 `InitialUserProfileDao.upsert()`로 저장한다.
- profile row가 없는 과거 CSV도 실패하지 않는다.

## 호환성

- daily_timeseries CSV는 기존 경로로 계속 복원된다.
- `sleep_hours`는 `toDoubleOrNull()` 기반으로 파싱되어 빈 값/오류 값에서 crash하지 않는다.

## 테스트

- `restoreCsvExportsAndParsesInitialProfileRows` unit test 추가.
