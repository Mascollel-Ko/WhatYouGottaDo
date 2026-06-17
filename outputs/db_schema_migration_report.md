# DB Schema / Migration Report

## 蹂寃?
- `app/build.gradle.kts`
  - Room schema export 寃쎈줈瑜?`app/schemas`濡??ㅼ젙?덈떎.
  - androidTest assets??schema 寃쎈줈瑜??곌껐?덈떎.
  - `room-testing`怨?AndroidX test dependency瑜?異붽??덈떎.
- `TrainingDatabase.kt`
  - DB version??8?먯꽌 9濡??щ졇??
  - `exportSchema = true`濡?蹂寃쏀뻽??
  - `initial_user_profiles` ?뚯씠釉붿쓣 異붽??섎뒗 `MIGRATION_8_9`瑜?異붽??덈떎.
- `Entities.kt`, `Daos.kt`
  - `InitialUserProfile` entity? DAO瑜?異붽??덈떎.

## ?앹꽦 schema

- `app/schemas/com.training.trackplanner.data.TrainingDatabase/9.json`

## ?뚯뒪??
- `testDebugUnitTest`: ?깃났
- `assembleDebugAndroidTest`: ?깃났

## 蹂대쪟

- 湲곗〈 ?뚯씠釉?FK ?ъ옉?깆? ?섏? ?딆븯??
- ?댁쑀: `WorkoutEntry`, `WorkoutSet`, `Exercise`, `TrainingProgramItem`??FK瑜??뚭툒 ?곸슜?섎젮硫??뚯씠釉?rebuild媛 ?꾩슂?섍퀬, ?꾩옱 ?ъ슜???곗씠??蹂댁〈????以묒슂?섎떎.