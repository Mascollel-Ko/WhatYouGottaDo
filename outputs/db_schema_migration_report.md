# DB Schema / Migration Report

## 변경

- `app/build.gradle.kts`
  - Room schema export 경로를 `app/schemas`로 설정했다.
  - androidTest assets에 schema 경로를 연결했다.
  - `room-testing`과 AndroidX test dependency를 추가했다.
- `TrainingDatabase.kt`
  - DB version을 8에서 9로 올렸다.
  - `exportSchema = true`로 변경했다.
  - `initial_user_profiles` 테이블을 추가하는 `MIGRATION_8_9`를 추가했다.
- `Entities.kt`, `Daos.kt`
  - `InitialUserProfile` entity와 DAO를 추가했다.

## 생성 schema

- `app/schemas/com.training.trackplanner.data.TrainingDatabase/9.json`

## 테스트

- `testDebugUnitTest`: 성공
- `assembleDebugAndroidTest`: 성공

## 보류

- 기존 테이블 FK 재작성은 하지 않았다.
- 이유: `WorkoutEntry`, `WorkoutSet`, `Exercise`, `TrainingProgramItem`에 FK를 소급 적용하려면 테이블 rebuild가 필요하고, 현재 사용자 데이터 보존이 더 중요하다.
