# DB Integrity Follow-up Report

## 현재 적용

- `WorkoutEntry` 삭제 시 관련 `WorkoutSet` 삭제는 repository/DAO 흐름으로 처리한다.
- `Exercise` 삭제는 repository에서 참조 여부를 검사한다.
- `Exercise`가 `WorkoutEntry` 또는 `TrainingProgramItem`에서 참조되면 삭제하지 않는다.
- 기본 제공 운동은 참조가 없어도 삭제보다 숨김을 우선한다.

## 이번 버전에서 FK를 적용하지 않은 이유

- 기존 SQLite 테이블에 FK/cascade를 추가하려면 새 테이블 생성, 데이터 복사, 기존 테이블 삭제/rename이 필요하다.
- 현재 앱은 사용자 기록, 프로그램, 운동 seed가 이미 존재하므로 rebuild migration은 v0.3.4.4 범위보다 리스크가 크다.

## 향후 권장

- 다음 DB 구조 변경 시 schema 9 기준 migration test를 먼저 활성화한다.
- `workout_sets.entryId -> workout_entries.id`는 cascade 후보.
- `training_program_items.programId -> training_programs.id`는 cascade 후보.
- `workout_entries.exerciseId -> exercises.id`는 restrict/no action 후보.
- `training_program_items.exerciseId -> exercises.id`는 restrict/no action 후보.
