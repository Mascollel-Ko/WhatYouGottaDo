# WhatYouGottaTrain Phase 2.5 Work README

?묒꽦?? 2026-06-14
?꾨줈?앺듃 ?꾩튂: `C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme`
湲곗닠 ?ㅽ깮: Android native, Kotlin, Jetpack Compose, Material 3, Room SQLite

## 臾몄꽌 媛깆떊 洹쒖튃

?욎쑝濡?湲곕뒫, DB 援ъ“, seed, 遺꾩꽍 ?ㅺ퀎, ?붾㈃ 援ъ“媛 諛붾뚮㈃ ??README? handoff 臾몄꽌瑜??④퍡 媛깆떊?쒕떎.

媛깆떊 ???

- `outputs/phase2_work_readme.md`
- `outputs/phase2_handoff.md`
- `outputs/phase2_data_meaning_mapping.md`
- 遺꾩꽍 ?ㅺ퀎 蹂寃???`docs/analysis_algorithm_design.md`

## ???앸퀎 ?뺣낫

?뚯뒪?명룿?먯꽌 湲곗〈 ?깃낵 異⑸룎?섏? ?딅룄濡????앸퀎?먮? 遺꾨━?덈떎.

| ??ぉ | 媛?|
| --- | --- |
| ???대쫫 | `WhatYouGottaTrain` |
| applicationId | `com.whatyougottatrain.app` |
| namespace / Kotlin package | `com.training.trackplanner` |
| minSdk | 26 |
| targetSdk | 35 |

## ?쒗뭹 泥좏븰

???깆? ?⑥닚 ?대룞?쇱?媛 ?꾨땲??

?듭떖 ?먮쫫? ?ㅼ쓬怨?媛숇떎.

```text
怨꾪쉷 -> ?섑뻾 ?뺤씤 -> 遺꾩꽍 -> ?먮떒 -> ?ㅼ쓬 ?됰룞
```

媛??以묒슂???곗씠???섎????좎??댁빞 ?쒕떎.

```text
WorkoutSet.confirmed = false : ?꾩쭅 ?섑뻾?섏? ?딆? 怨꾪쉷 ?명듃
WorkoutSet.confirmed = true  : ?ㅼ젣 ?섑뻾??湲곕줉 ?명듃
```

遺꾩꽍? 湲곕낯?곸쑝濡?`confirmed=true` ?명듃留??ㅼ젣 ?덈젴 遺?섎줈 蹂몃떎.

## ?꾩옱 ?④퀎

Phase 2??湲곕줉/怨꾪쉷 湲곕뒫??理쒖냼 ?꾩꽦 ?④퀎???
Phase 2.5????湲곕뒫 ?뺤옣???꾨땲???덉젙?? 援ъ“ ?뺣━, 遺꾩꽍 ?ㅺ퀎 怨좎젙 諛⑹?瑜?紐⑺몴濡?吏꾪뻾?덈떎.

## Phase 2.5 援ы쁽 ?붿빟

### 1. MainActivity ?붾㈃ 遺꾨━

`MainActivity.kt`????猷⑦듃? ?섎떒 ??쭔 ?대떦?섎룄濡?以꾩???

遺꾨━???뚯씪:

| ?뚯씪 | ??븷 |
| --- | --- |
| `HomeScreen.kt` | ?? ?ㅻ뒛 ???? ?ㅻ뒛 ?붿빟 |
| `RecordScreen.kt` | ?좎쭨蹂?湲곕줉, ?명듃 ?몄쭛, ?섎㈃/泥댁쨷 |
| `PlanScreen.kt` | ?꾨줈洹몃옩 紐⑸줉, ?곸꽭, ?좎쭨 ?곸슜 |
| `ExerciseScreen.kt` | ?대룞 紐⑸줉, 寃?? 移댄뀒怨좊━ ?꾪꽣 |
| `AnalysisScreen.kt` | confirmed 湲곕컲 ?⑥닚 ?듦퀎 |
| `CommonUi.kt` | 怨듯넻 移대뱶, ?좎쭨, ?대룞 ?좏깮, ?щ㎎ ?ы띁 |

### 2. ?꾨줈洹몃옩 ?곸슜 ??뼱?곌린 UX 媛뺥솕

?꾨줈洹몃옩 ?곸슜 ????좎쭨??湲곗〈 湲곕줉???덉쑝硫??덉쟾 ?뺤씤 ?ㅼ씠?쇰줈洹몃? ?쒖떆?쒕떎.

?쒖떆 ??ぉ:

- ?곹뼢??諛쏅뒗 ?좎쭨 ??
- 湲곗〈 WorkoutEntry ??
- 湲곗〈 confirmed=true ?명듃 ??
- 寃쎄퀬 臾멸뎄: ?쒕뜮?댁벐湲곕? ?좏깮?섎㈃ 湲곗〈 湲곕줉怨??꾨즺 ?명듃媛 ??젣?⑸땲????

?숈옉:

- ??뼱?곌린: 湲곗〈 set ??젣 ??entry ??젣, 洹몃떎???꾨줈洹몃옩 怨꾪쉷 ?앹꽦
- 異붽?: 湲곗〈 湲곕줉 蹂댁〈 ???꾨줈洹몃옩 怨꾪쉷 異붽?
- 痍⑥냼: ?꾨Т 蹂寃??놁쓬

?꾨줈洹몃옩 ?곸슜?쇰줈 ?앹꽦?섎뒗 紐⑤뱺 `WorkoutSet`? 諛섎뱶??`confirmed=false`??

### 3. Seed version / meta ?꾨왂

`app_meta` ?뚯씠釉붿쓣 異붽??덈떎.

?꾩옱 meta key:

| key | ?섎? |
| --- | --- |
| `exercise_seed_version` | ?대룞 seed ?곸슜 踰꾩쟾 |
| `program_seed_version` | ?꾨줈洹몃옩 seed ?곸슜 踰꾩쟾 |

?꾨왂:

- DB migration? additive 諛⑹떇留??ъ슜?쒕떎.
- ?ъ슜??湲곕줉, ?좎쭨 怨꾪쉷, ?ъ슜???앹꽦 ?대룞/?꾨줈洹몃옩????젣?섏? ?딅뒗??
- ?대룞 seed??`stableKey` unique index? insert ignore濡?以묐났??留됰뒗??
- 湲곕낯 ?꾨줈洹몃옩 seed??媛숈? ?꾨줈洹몃옩紐낆씠 ?대? ?덉쑝硫?以묐났 ?쎌엯?섏? ?딅뒗??
- 湲곗〈 DB???꾨줈洹몃옩???덉뼱???ν썑 seed version 利앷? ??鍮좎쭊 湲곕낯 ?꾨줈洹몃옩??異붽??????덈떎.

### 4. 怨좉툒 遺꾩꽍 援ы쁽 湲덉?

Phase 2.5?먯꽌??怨좉툒 遺꾩꽍 ?뚭퀬由щ벉??援ы쁽?섏? ?딆븯??

?꾩옱 遺꾩꽍 ?붾㈃? ?ㅼ쓬 ?⑥닚 ?듦퀎留??쒖떆?쒕떎.

- confirmed set ??
- 珥?蹂쇰ⅷ
- 珥??쒓컙

遺꾩꽍 ?ㅺ퀎 臾몄꽌??蹂꾨룄濡?異붽??덈떎.

```text
docs/analysis_algorithm_design.md
```

臾몄꽌?먮뒗 Raw Data, Feature Extraction, Signal Generation, Judgment, Recommendation, Narrative Rendering 援ъ“瑜??뺤쓽?덈떎.

### 5. Debug DB summary 濡쒓렇

Debug 鍮뚮뱶?먯꽌留?`TrainingDbSummary` 濡쒓렇瑜?異쒕젰?쒕떎.

?뺤씤 ??ぉ:

- Exercise count
- TrainingProgram count
- TrainingProgramItem count
- ?ㅻ뒛 WorkoutEntry count
- ?ㅻ뒛 confirmed set count
- ?ㅻ뒛 unconfirmed set count

production UI?먮뒗 媛쒕컻???뺣낫瑜??몄텧?섏? ?딅뒗??

## 以묒슂??肄붾뱶 洹쒖튃

諛섎뱶???좎???洹쒖튃:

- ?꾨줈洹몃옩 ?곸슜?쇰줈 ?앹꽦?섎뒗 set? `confirmed=false`
- ?명듃 異붽???留덉?留?set??`reps`, `weightKg`, `seconds`瑜?蹂듭궗?섎릺 `confirmed=false`
- 留덉?留?1媛??명듃 ??젣??李⑤떒
- ?명듃 ??젣 ??`setIndex`??1遺???ъ젙??
- 遺꾩꽍 ?⑥닚 ?듦퀎??`confirmed=true`留?吏묎퀎
- ??뼱?곌린???ъ슜?먭? 紐낆떆?곸쑝濡??좏깮??寃쎌슦?먮쭔 ?섑뻾

## 二쇱슂 ?섏젙 ?뚯씪

| ?뚯씪 | 蹂寃??댁슜 |
| --- | --- |
| `MainActivity.kt` | ??猷⑦듃? ?섎떒 ??쭔 ?④? |
| `HomeScreen.kt` | ???붾㈃ 遺꾨━ |
| `RecordScreen.kt` | 湲곕줉 ?붾㈃ 遺꾨━ |
| `PlanScreen.kt` | 怨꾪쉷 ?붾㈃ 遺꾨━, ??뼱?곌린 UX 媛뺥솕 |
| `ExerciseScreen.kt` | ?대룞 ?붾㈃ 遺꾨━ |
| `AnalysisScreen.kt` | 遺꾩꽍 ?붾㈃ 遺꾨━ |
| `CommonUi.kt` | 怨듯넻 UI 遺꾨━ |
| `Entities.kt` | `AppMeta` entity 異붽? |
| `Daos.kt` | app meta DAO, 異⑸룎 ?붿빟/?붾쾭洹?count 荑쇰━ 異붽? |
| `TrainingDatabase.kt` | DB version 2, migration 1->2 異붽? |
| `TrainingRepository.kt` | seed meta, conflict summary, debug summary 濡쒓렇 |
| `TrainingViewModel.kt` | ?꾨줈洹몃옩 ?곸슜 異⑸룎 ?붿빟 議고쉶 異붽? |
| `docs/analysis_algorithm_design.md` | ?ν썑 遺꾩꽍 ?뚭퀬由щ벉 ?ㅺ퀎 臾몄꽌 |

## 鍮뚮뱶 寃利?

?ㅽ뻾 紐낅졊:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat assembleDebug
```

寃곌낵:

```text
BUILD SUCCESSFUL
```

APK:

```text
app/build/intermediates/apk/debug/app-debug.apk
```

## ?뚯뒪??/ 寃利??곹깭

?먮룞 Room in-memory ?뚯뒪?몃뒗 ?대쾲 ?④퀎?먯꽌 異붽??섏? ?딆븯??

?댁쑀:

- ?꾩옱 ?꾨줈?앺듃??test runner / Room test ?섏〈?깆씠 援ъ꽦?섏뼱 ?덉? ?딅떎.
- ???뚯뒪???섏〈??異붽????ㅽ듃?뚰겕? Gradle ?섏〈???닿껐 ?꾪뿕???덈떎.
- Phase 2.5 紐⑺몴媛 ?덉젙?붿씠誘濡?鍮뚮뱶 ?깃났怨??듭떖 肄붾뱶 寃쎈줈 寃利앹쓣 ?곗꽑?덈떎.

????뚯뒪 寃利앹쑝濡??뺤씤???섎?:

- ?꾨줈洹몃옩 ?곸슜 set? `confirmed=false`
- ?명듃 異붽? set? `confirmed=false`
- 留덉?留??명듃 ??젣 李⑤떒
- ??젣 ??`setIndex` ?ъ젙??
- 遺꾩꽍 ?듦퀎??`confirmed=true`留??ы븿

## ?ㅼ쓬 ?묒뾽??二쇱쓽??

1. 怨좉툒 遺꾩꽍? ?꾩쭅 援ы쁽?섏? ?딅뒗?? 蹂꾨룄 ?뱀씤 ??吏꾪뻾?쒕떎.
2. seed version???щ┫ ???ъ슜???곗씠?곕? ??젣?섏? ?딅뒗??
3. ?꾨줈洹몃옩 seed 異붽? ??媛숈? 湲곕낯 ?꾨줈洹몃옩紐낆씠 ?덉쑝硫?以묐났 ?쎌엯?섏? ?딅뒗??
4. UI 遺꾨━ ??湲곕뒫 ?섎???Repository???⑥븘 ?덈떎. confirmed 洹쒖튃? UI?먯꽌 ?ы빐?앺븯吏 ?딅뒗??
5. ?욎쑝濡?蹂寃??묒뾽留덈떎 ??README, handoff, ?곗씠???섎? 留ㅽ븨???④퍡 媛깆떊?쒕떎.

## Phase 2.6 Minor Patch

Phase 3 CSV 諛깆뾽/蹂듭썝 ?꾩뿉 湲곕줉 ?붾㈃ UX? ?닿쾶 ??대㉧ MVP瑜?蹂듦뎄?덈떎.

蹂寃??붿빟:

- 湲곕줉 ?명듃 ?됱쓣 ??以?以묒떖?쇰줈 ?뺤텞
- ?뺤씤 泥댄겕諛뺤뒪瑜??잛닔 / kg / 珥??놁쑝濡??대룞
- ?뺤씤???명듃? 怨꾪쉷 ?명듃瑜??묒? ?됯컧 李⑥씠濡?援щ텇
- ?대룞 entry ?⑥쐞 `?쇨큵 kg` 踰꾪듉 異붽?
- ???명듃??kg ?낅젰 ??鍮?誘명솗???명듃??媛숈? kg ?곸슜 ?덈궡 異붽?
- ?명듃媛 `confirmed=false -> true`濡?諛붾뚮뒗 ?쒓컙 ?닿쾶 ??대㉧ ?쒖옉
- ????誘몃땲 ??대㉧ 異붽?
- ?뚮┝ 梨꾨꼸怨?running / finished notification 異붽?
- ?ㅻ쾭?덉씠 MVP 異붽?
- ?ㅻ쾭?덉씠 drag / delete target / current away session suppression 蹂듦뎄

湲곗〈 rest timer ?ㅺ퀎瑜?Kotlin / Compose 援ъ“濡?蹂듭썝?덈떎.

異붽? ?뚯씪:

- `RestTimerSessionController.kt`
- `RestTimerNotifier.kt`
- `RestTimerOverlayController.kt`
- `RestTimerSoundVibration.kt`
- `RestTimerNavigation.kt`
- `RestTimerState.kt`
- `RestTimerUi.kt`
- `outputs/phase2_6_record_timer_patch.md`

MainActivity??`onResume`, `onPause`, `onDestroy`, ?뚮┝/?ㅻ쾭?덉씠 ?대┃ navigation留??대떦?쒕떎. ??대㉧ ?곹깭, ?뚮┝, ?ㅻ쾭?덉씠 drawing? MainActivity??RecordScreen???ｌ? ?딅뒗??

## Phase 2.6 Build Environment Patch

Gradle problems report ?뚯씪 異⑸룎??留됯린 ?꾪빐 鍮뚮뱶 ?섍꼍留??섏젙?덈떎.

- `gradle.properties`??`org.gradle.problems.report=false` 異붽?
- root `build.gradle.kts`??`cleanBuildReports` ?쒖뒪??異붽?
- `cleanBuildReports`??`build/reports/problems`, `app/build/reports/problems`留???젣?쒕떎
- `assembleDebug` ?ㅽ뻾 ??`cleanBuildReports`媛 癒쇱? ?ㅽ뻾?섎룄濡??곌껐
- `local.properties`??SDK 寃쎈줈瑜?workspace SDK濡?留욎땄
- 湲곕뒫 肄붾뱶, DB schema, CSV, 遺꾩꽍 濡쒖쭅? 蹂寃쏀븯吏 ?딆쓬

寃利?紐낅졊:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

寃곌낵: `BUILD SUCCESSFUL`.

## Phase 2.6 Record UX / Set RPE / Rest Patch

湲곕줉 ?붾㈃???대룞 以??낅젰 以묒떖?쇰줈 ?ㅼ떆 ?뺤텞?덈떎.

蹂寃??붿빟:

- DailyMetric ?섎㈃ / 泥댁쨷 ?낅젰??`而⑤뵒?? 踰꾪듉 ?ㅻ줈 ?④?
- ?대룞 硫붾え, 理쒕??잛닔, ?대룞 ?꾩껜 RPE, 湲곕낯 ?댁떇 ?몄쭛??`?곸꽭` 踰꾪듉 ?ㅻ줈 ?④?
- ?명듃 紐⑸줉????긽 遺媛 ?낅젰蹂대떎 癒쇱? 蹂댁씠?꾨줉 議곗젙
- `BasicTextField` 湲곕컲 compact number input ?ъ슜
- `WorkoutSet.rpe` 異붽?
- `WorkoutSet.restSecondsOverride` 異붽?
- Room DB version 3 additive migration 異붽?
- ?명듃 ?뺤씤 ??effective rest濡??닿쾶 ??대㉧ ?쒖옉
- ?쇨큵 硫붾돱??kg ?ㅼ젙 / 利앷? / 媛먯냼, ?잛닔 利앷? / 媛먯냼, ?댁떇 ?ㅼ젙 / 利앷? / 媛먯냼 異붽?
- 鍮?kg ?명듃 ?먮룞 ?곸슜? 誘명솗??鍮??명듃留???곸쑝濡??좎?
- ?꾨줈洹몃옩 ?곸슜 ??援ъ“??泥섎갑媛믪쓣 `WorkoutEntry.notes`??蹂듭궗?섏? ?딅룄濡??뺣━

?곗씠???섎?:

- `WorkoutSet.rpe`媛 ?명듃蹂?RPE??canonical ?꾩튂
- `WorkoutEntry.rpe`??legacy fallback ?먮뒗 ?대룞 ?꾩껜 泥닿컧
- `WorkoutEntry.restSeconds`??湲곕낯 ?댁떇?쒓컙
- `WorkoutSet.restSecondsOverride`???명듃蹂??댁떇 ?덉쇅媛?
- `WorkoutEntry.notes`???먯쑀 硫붾え / 肄붿튂??硫붾え
- ?명듃??/ 諛섎났??/ 以묐웾 / ?쒓컙? `WorkoutSet`怨?`TrainingProgramItem` 援ъ“???꾨뱶媛 ?먯쿇

異붽? 臾몄꽌:

- `outputs/phase2_6_legacy_record_timer_audit.md`
- `outputs/phase2_6_rest_timer_recovery.md`

## Phase 2.7 Record Calendar UI Patch

湲곕줉 ??留덉씠??UI 媛쒖꽑???곸슜?덈떎.

- `?щ젰` 踰꾪듉???좎쭨 ?대룞 row??`?ㅼ쓬?? ?ㅻⅨ履쎌뿉 諛곗튂
- `而⑤뵒?? / `?대룞 異붽?`瑜?compact row濡??뺣━
- ?닿쾶 ??대㉧ ?쒖떆瑜??곷떒 bar?먯꽌 active set row 蹂댁“ chip?쇰줈 ?대룞
- `RestTimerState.targetSetId`瑜?異붽????⑥씪 active timer瑜??명듃 ?됱뿉 留ㅽ븨
- ?꾨즺 ?대룞??UI?먯꽌 ?곷떒 ?뺣젹
- ?붽컙 湲곕줉 ?붿빟 ?붾㈃ `RecordCalendarScreen.kt` 異붽?
- `DailyRecordSummary`? ?좎쭨 踰붿쐞 summary query 異붽?
- ?ㅽ겕紐⑤뱶??湲곗〈 `darkColorScheme` / `isSystemInDarkTheme()` 湲곕컲 吏???뺤씤

?뺣젹? UI ?쒖떆 ?쒖꽌留?諛붽씀硫?DB row ?쒖꽌, `createdAt`, `entry id`, `setIndex`??蹂寃쏀븯吏 ?딅뒗??

??臾몄꽌:

- `outputs/phase2_7_record_calendar_ui_patch.md`

## Phase 2.7.2 Calendar Lifecycle Patch

?붽컙 罹섎┛?붿쓽 湲곗〈 湲곕줉 lifecycle 湲곕뒫??long press + dialog 諛⑹떇?쇰줈 蹂듭썝?덈떎.

- 怨꾪쉷?쇰줈 ?좎쭨 蹂듭궗
- 湲곕줉?곹깭源뚯? ?좎쭨 蹂듭궗
- ?좎쭨 ?대룞
- ?좎쭨 ??젣
- ?좏깮蹂듭궗
- ????좎쭨 / 踰붿쐞 異⑸룎 ????뼱?곌린 / 異붽? / 痍⑥냼
- ??젣 ???대룞 ??/ ?명듃 ??/ ?꾨즺 ?명듃 ???쒖떆
- DailyMetric? 蹂듭궗 / ?대룞 / ??젣?섏? ?딆쓬

confirmed ?섎?:

- 怨꾪쉷 蹂듭궗? ?좏깮蹂듭궗: 蹂듭궗??紐⑤뱺 set `confirmed=false`
- 湲곕줉?곹깭 蹂듭궗? ?대룞: ?먮낯 confirmed ?곹깭 蹂댁〈
- ??젣: 紐낆떆???뺤씤 ???대룞 entry / set留???젣

??臾몄꽌:

- `outputs/phase2_7_2_calendar_lifecycle_audit.md`
- `outputs/phase2_7_2_calendar_lifecycle_patch.md`

## Phase 3.0.0 Analysis Engine V3 Foundation

This update adds analysis infrastructure only. It does not replace the current analysis tab and does not expose new user-facing analysis methods.

Added source areas:

- `analysis/core`: date provider, windows, input snapshot, input collector.
- `analysis/metrics`: common load, strength, taxonomy, and plan projection metrics.
- `analysis/methods`: analyzer interface, result model, disabled registry.
- `analysis/text`: sentence builder interface and sentence policy.
- `analysis/engine`: V3 facade and dashboard result model.

Important behavior:

- V3 today is based on `AnalysisDateProvider.today()`, defaulting to `LocalDate.now()`.
- Completed input uses only `date <= today` and `confirmed=true` sets.
- Future plan input uses only `date > today` and `confirmed=false` sets.
- New analysis method ids exist only as disabled registry descriptors.
- No DB rows are deleted, rewritten, or migrated by this patch.

Related document:

- `outputs/phase3_0_0_analysis_engine_v3_foundation.md`

## Phase 3.1.1 Fatigue Metadata Foundation

This update prepares Today Fatigue / Readiness analysis metadata without implementing the final fatigue engine or changing the analysis UI.

Added:

- `ExerciseMetadataTaxonomy.kt`
- `ExerciseMetadataMapper.kt`
- `MetadataSanityChecker.kt`
- additive Room migration `3 -> 4`
- seed-wide metadata sanity tests
- `outputs/phase3_1_1_fatigue_metadata_foundation.md`

Current built-in catalog:

- 214 exercises
- metadata confidence: `HIGH=214`, `MEDIUM=0`, `LOW=0`, `NEEDS_REVIEW=0`
- sanity errors: 0

Preserved:

- existing records
- calendar behavior
- record screen behavior
- existing simple analysis tab
- `WorkoutSet.confirmed` semantics

## Phase 3.1.2 Metadata Readiness

This update completes the metadata fields needed by 3.2, 3.3, 3.4, and future balance/safety analysis.

Added:

- progress metric taxonomy
- strength/hypertrophy/main lift grouping
- badminton transfer strength, court movement types, skill targets
- joint stress, stability, mobility, and balance contribution fields
- `analysisEligibility`
- `ExerciseAnalysisMapper`
- `MetadataReadinessReporter`
- expanded `MetadataSanityChecker`

Current readiness:

- total exercises: 214
- fatigueReady: `YES=214`
- progressReady: `YES=214`
- badmintonReady: `YES=214`
- balanceReady: `YES=214`
- NEEDS_REVIEW: none

Related documents:

- `outputs/metadata_analysis_readiness_report.md`
- `outputs/phase3_1_2_metadata_readiness_patch.md`

## Phase 3.1.3 Today Readiness / Fatigue Engine

This update implements the first user-facing Today Readiness card on the Analysis tab.

Added:

- `analysis/readiness` engine package
- `TodayReadinessEngine`
- daily load aggregation from confirmed sets only
- residual fatigue decay by `recoveryDecayProfile`
- statistical baseline calculation
- adaptive baseline calculation
- fatigue pressure calculation
- recovery, performance, and pain-gate interpreters
- readiness decision and sentence builders
- expandable detail sections in `AnalysisScreen`
- unit tests for synthetic readiness scenarios

Preserved:

- `WorkoutSet.confirmed=false` is still plan.
- `WorkoutSet.confirmed=true` is still actual record.
- planned workouts are excluded from today's fatigue calculation.
- existing simple analysis stats remain visible.
- exercise classification uses structured metadata through `ExerciseAnalysisMapper`.

Reference:

- `outputs/phase3_1_3_today_readiness_engine.md`

## Phase 3.2.0 Performance Trend Analysis

This update adds the Analysis tab section `?깃낵 異붿꽭 遺꾩꽍`.

First-screen dashboard:

- one line chart for strength performance
- one line chart for badminton training volume
- one line chart for fatigue composite index
- one short trend sentence
- detail toggle

Rules:

- the first screen shows exactly three single-line charts.
- large numeric score emphasis is avoided.
- detail indicators are shown only after expanding details.
- trend calculations use completed records only.
- planned sets are excluded.
- exercise classification uses structured metadata through `ExerciseAnalysisMapper`.

Added trend package:

- `analysis/trends/PerformanceTrendEngine.kt`
- `analysis/trends/StrengthPerformanceIndexCalculator.kt`
- `analysis/trends/BadmintonTrainingLoadIndexCalculator.kt`
- `analysis/trends/FatigueCompositeIndexCalculator.kt`
- `analysis/trends/PerformanceChartSpecBuilder.kt`
- `analysis/trends/ScatterRelationshipAnalyzer.kt`
- `analysis/trends/DetailChartSelector.kt`

Reference:

- `outputs/phase3_2_0_performance_trend_analysis.md`

## Minor Patch: Record Date Switcher Width

This update tightens the Record tab date navigation row.

Changed:

- `?댁쟾??, `?ㅼ쓬??, and `?щ젰` button labels are unchanged.
- Date navigation buttons now use smaller horizontal padding and no default minimum width.
- The date text is fixed to one line with wrapping disabled.

Preserved:

- No repository, database, analysis, timer, or `confirmed` semantics changed.
- This is a UI-only patch for the Record tab date switcher.

Verification:

- `assembleDebug` succeeded after the patch.

## Phase 3.3.0 Badminton Transfer Analysis

This update adds the Analysis tab section `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍`.

First-screen rule:

- The card body shows exactly one recommendation sentence.
- Ratios, charts, explanations, and Top 5 exercise lists are only shown after expanding details.

Calculation:

- confirmed sets only
- recent 7 days and baseline 28 days
- structured metadata only through `ExerciseAnalysisMapper`
- transfer type is mapped from `badmintonTransferStrength`
- transfer axes are derived from existing badminton, fatigue, movement, laterality, balance, and muscle metadata

Detail views:

- `?꾩씠異?鍮꾩쨷`
- `?꾩씠?좏삎 鍮꾩쨷`
- `理쒓렐 7??vs 28??
- `?대룞蹂??꾩씠 ?먭레 Top 5`

Preserved:

- planned sets remain excluded from actual analysis.
- no DB schema change.
- no exercise name parsing for analysis.
- 3.1 readiness and 3.2 performance trend remain visible.

Reference:

- `outputs/phase3_3_0_badminton_transfer_analysis.md`

## v0.3.4.0 Home Cleanup and Backup Restore

This update cleans the Home screen and re-exposes record backup / restore entry points.

Home changes:

- removed the duplicated `?ㅻ뒛 ???? card.
- kept `?ㅻ뒛 ?붿빟`.
- added `湲곕줉 愿由? below `?ㅻ뒛 ?붿빟`.
- added `湲곕줉 諛깆뾽` and `湲곕줉 蹂듭썝` buttons.

Backup / restore:

- backup exports restore-format CSV through Android document creation.
- restore imports restore-format CSV and legacy `daily_timeseries` CSV.
- restore-format can recreate daily metrics, workout entries, and sets.
- daily_timeseries import restores daily metrics and creates category-level aggregate records because that CSV has no exact set-level detail.

Preserved:

- `confirmed=false` remains planned.
- `confirmed=true` remains completed record.
- v0.3.3.0 analysis and badminton transfer cards remain in place.

Reference:

- `outputs/phase3_4_0_home_backup_restore.md`

## Post v0.3.4.0 Analysis UI Compactness Patch

This minor update reduces the collapsed height of the 3.1 Today Readiness card in the Analysis tab.

UI changes:

- reduced card padding and vertical spacing.
- reduced Today Readiness status typography from headline-sized to title-sized.
- shortened collapsed `二쇱슂 ?댁쑀` to the first 2 items.
- changed collapsed `異붿쿇` and `議곗젅` blocks into one-line summaries.
- kept the expanded detail sections unchanged.

Preserved:

- Today Readiness engine logic is unchanged.
- confirmed-only analysis semantics are unchanged.
- 3.2 performance trend and 3.3 badminton transfer sections remain in place.

## v0.3.4.1 Program Generator and Safe Schedule Overwrite

This update restores the Plan tab as a practical program workflow.

Plan changes:

- programs can be opened, edited, and deleted.
- the program editor can create a metadata-based 4-week skeleton.
- generated skeletons can be reviewed and edited before saving.
- the editor supports goal, weekly days, session minutes, equipment, exclusions, badminton transfer ratio, sport/strength ratio, and periodization type.
- the editor requires a program name before skeleton generation or save.
- `?꾨? ?덈줈 留뚮뱾湲? clears the current preview and regenerates a fresh skeleton from the current inputs.

Generator rules:

- structured exercise metadata is the primary classification source.
- exercise names are not used for analysis classification.
- user exclusion text can filter exercise names because it is direct user input.
- scoring uses goal fit, transfer metadata, equipment, movement needs, fatigue cost, and metadata confidence.
- recent confirmed history can autofill conservative starting weights.

Schedule overwrite:

- applying a program still creates planned sets with `confirmed=false`.
- overwrite now removes only planned-only schedule entries.
- completed records with `confirmed=true` sets are preserved.
- the conflict dialog shows target period, planned entries to replace, new planned entries, and confirmed sets preserved.

Reference:

- `program_skeleton_generator_spec_v0.3.4.1.md`
- `outputs/phase3_4_1_program_generator.md`

Record calendar add-on:

- long-press date menu now includes range delete.
- the selected start/end date range can delete unconfirmed-only sets or all records including confirmed sets.
- unconfirmed-only delete preserves completed records and reorders remaining set indexes.

Back navigation add-on:

- Android back from non-Home tabs returns to Home.
- Android back from Home can close the app.
- Calendar and detail-expanded screens consume back first and return to their prior in-app state.

Rest timer overlay add-on:

- App-outside overlay is suppressed when the confirmed set has no next target.
- This prevents the overlay from remaining after the final set/session.
- Legacy overlay feature parity audit: `outputs/rest_timer_overlay_legacy_audit_v0.3.4.1.md`.

## v0.3.4.2 Sport Session Program Filter

This hotfix prevents sport sessions and match records from entering generated programs.

Changes:

- added `Exercise.activityKind`.
- added `Exercise.planningEligibility`.
- added Room migration `6 -> 7`.
- `ProgramSkeletonGenerator` now hard-filters candidates before scoring.
- only `TRAINING_EXERCISE + PROGRAM_SELECTABLE` can become generated program items.
- `SPORT_SESSION`, `MATCH_RECORD`, `FATIGUE_ONLY`, `ANALYSIS_ONLY`, and `HIDDEN` are excluded from program generation.
- CSV daily-timeseries aggregate exercises are marked fatigue-only.

Preserved:

- sport sessions can remain completed history.
- sport sessions can remain fatigue/readiness/trend inputs.
- existing records, confirmed sets, user plans, and metrics are not deleted.

Reference:

- `outputs/v0.3.4.2_sport_session_program_filter.md`

## v0.3.4.3 Update
- Restored exercise image assets and JSON seed from the provided APK.
- Added exercise image mapping and placeholder-safe detail rendering.
- Added exercise hide/unhide and safe delete management.
- Added exercise master rows to CSV backup/restore.
- Added metadata inventory/gap/enum reports for later analysis/program-generation work.
- Build and APK backup status are reported in the final v0.3.4.3 handoff.

## v0.3.4.3 Verification
- Unit test: `.\gradlew.bat --no-daemon --no-problems-report testDebugUnitTest` 성공.
- Debug build: `.\gradlew.bat --no-daemon --no-problems-report assembleDebug` 성공.
- APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\WhatYouGottaTrain-v0.3.4.3-debug.apk`.


## v0.3.4.3 Startup Hotfix
- 시작 직후 종료 가능성이 있는 JSON seed 우선 초기화 경로를 차단했습니다.
- 복구된 `exercises_seed.json`은 asset/report 원본으로 보존하고, 런타임 기본 seed는 기존 검증된 CSV catalog를 사용합니다.
- 이미지 mapping asset은 CSV seed 운동명 기준으로 계속 적용됩니다.
- `testDebugUnitTest`와 `assembleDebug` 재검증 성공.


## v0.3.4.3 Exercise Info Button Patch
- ?대룞 ??뿉???대룞 ?곗튂 ???곷떒 ?곸꽭 移대뱶媛 諛붾뚮뜕 ?숈옉???쒓굅?덉뒿?덈떎.
- 媛??대룞 ???ㅻⅨ履쎌쓽 `i` 踰꾪듉?쇰줈 ?대룞 ?ъ쭊, ?ㅻ챸, ?곸꽭 ?쒓렇瑜??ㅼ씠?쇰줈洹몄뿉???뺤씤?⑸땲??
- ?대룞 ?좏깮/異붽? ?ㅼ씠?쇰줈洹몄쓽 湲곗〈 ???좏깮 ?숈옉? ?좎??덉뒿?덈떎.
- ??踰꾩쟾? 蹂寃쏀븯吏 ?딆븯?듬땲??

## v0.3.4.4 Update

- Room schema export瑜??쒖꽦?뷀븯怨?DB version 9瑜?異붽??덈떎.
- `initial_user_profiles` ?뚯씠釉? DAO, repository flow, ???붾㈃ ?낅젰 dialog瑜?異붽??덈떎.
- 珥덇린 ?꾨줈?꾩? CSV 諛깆뾽/蹂듭썝???ы븿?쒕떎.
- Today Readiness??cold-start 援ш컙?먯꽌 媛뺥븳 蹂듯빀 ?좏샇媛 ?놁쑝硫?怨쇰룄??`FATIGUED`瑜?`CAUTION`?쇰줈 ??텣??
- 湲곕줉 ???대룞 移대뱶 ?ㅻ뜑瑜???以꾨줈 以꾩씠怨? ?꾨즺 ?명듃??compact row濡??묐뒗??
- 湲곕줉 ??낵 ?대룞 ??뿉??怨듯넻 `ExerciseInfoDialog`瑜??ъ슜?쒕떎.
- 湲곕낯 ?쒓났 ?대룞? 吏곸젒 ??젣蹂대떎 ?④????곗꽑?쒕떎.

## v0.3.4.4.1 Hotfix Update

- 珥덇린 ?꾨줈???듭떖 ?낅젰???먯쑀 ?띿뒪?몄뿉???レ옄/?좏깮/multi-select 以묒떖?쇰줈 諛붽엥??
- DB version 10怨?`MIGRATION_9_10`??異붽??덈떎.
- 湲곗〈 v0.3.4.4 臾몄옄???꾨뱶????젣?섏? ?딄퀬 ?명솚/deprecated ?꾨뱶濡??좎??쒕떎.
- CSV profile row??援ъ“??key瑜?export/import?쒕떎.
- cold-start readiness 蹂댁젙? `usualSleepHours`, `currentCondition`, `trainingBreakCategory`, `trainingBreakReason`, `painAreaTags` 媛숈? 援ъ“??媛믩쭔 ?ъ슜?쒕떎.

## v0.3.4.4.4 Hotfix Addendum

- Version bumped to `v0.3.4.4.4` / DB version `11`.
- `MIGRATION_10_11` is no-op; structured initial profile fields were already present in DB v10.
- Added `InitialAdaptationProfile` and cold-start readiness baseline binding.
- Removed the old fatigued-only profile adjustment pattern.
- Initial profile now can affect READY/CAUTION/FATIGUED outcomes through baseline blending and final summary adjustment.
- CSV profile restore now sanitizes enum keys, RPE, and 1-5 recovery fields.
- Debug APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\3444-TrainingTrackPlanner-v0.3.4.4.4-debug.apk`.

## v0.3.4.5 Current Addendum

- Version bumped to `v0.3.4.5` / `versionCode 304045`.
- Room DB version remains `12`; no new migration was added.
- Exercise seed version bumped to `6`.
- Record tab now supports deleting a whole exercise entry from today's record via the exercise card action row.
- Deleting the last set of an unconfirmed exercise now removes the entry instead of leaving an undeletable empty record.
- Rest timer overlay is hidden when there is no next exercise target.
- Analysis section wording changed from `성과 추세 분석` to `성과 추세`.
- Strength detail wording changed from efficiency to `RPE 대비 운동량`.
- Badminton court volume wording changed to `셔틀 플레이 시간`.
- Metric chips are visible only in trend detail mode.
- Strength and fatigue ranking modes are hidden; badminton ranking remains.
- Relationship analysis uses dropdown X/Y selectors and blocks selecting the same metric on both axes.
- `배드민턴 레슨` was added to the seed data and mapped as `BADMINTON_DIRECT_PLAY`.
- Direct badminton play, footwork/reactive drills, and support training are separated by structured metadata.
- Canonical handoff: `outputs/v0.3.4.5_reproduction_handoff.md`.
- Debug APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\TrainingTrackPlanner-v0.3.4.5-debug.apk`.
