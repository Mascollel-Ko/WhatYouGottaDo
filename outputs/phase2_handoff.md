# WhatYouGottaTrain Phase 2.5 Handoff

?묒꽦?? 2026-06-14
紐⑹쟻: ?ㅼ쓬 ?묒뾽?먭? ?꾩옱 ??援ъ“, ?곗씠???섎?, 鍮뚮뱶 ?곹깭, 二쇱쓽?먯쓣 鍮좊Ⅴ寃??댁뼱諛쏅룄濡??꾨떖?쒕떎.

## ?꾩닔 ?몄닔?멸퀎 洹쒖튃

?욎쑝濡???湲곕뒫?대굹 ?곗씠??援ъ“媛 諛붾뚮㈃ ?ㅼ쓬 臾몄꽌瑜??④퍡 媛깆떊?쒕떎.

- `outputs/phase2_work_readme.md`
- `outputs/phase2_handoff.md`
- `outputs/phase2_data_meaning_mapping.md`
- 遺꾩꽍 ?ㅺ퀎 蹂寃???`docs/analysis_algorithm_design.md`

臾몄꽌瑜??ㅼ뿉 誘몃（硫??ㅼ쓬 ?묒뾽?먭? 肄붾뱶 ?섎?瑜??섎せ ?먮떒?????덈떎. ?뱁엳 `WorkoutSet.confirmed` ?섎?? seed ?꾨왂? 諛섎뱶??理쒖떊 ?곹깭濡??좎??쒕떎.

## ?꾩옱 ?곹깭 ?붿빟

Phase 2.5 ?꾨즺 ?곹깭??

?꾨즺??寃?

- ??鍮뚮뱶 ?깃났
- ???대쫫 `WhatYouGottaTrain`
- `applicationId = com.whatyougottatrain.app`
- `MainActivity.kt` ?붾㈃ 遺꾨━
- ?꾨줈洹몃옩 ?곸슜 ??뼱?곌린 UX 蹂닿컯
- `app_meta` 湲곕컲 seed version ?꾨왂 理쒖냼 援ы쁽
- debug DB summary 濡쒓렇 異붽?
- 怨좉툒 遺꾩꽍 援ы쁽 湲덉? ?좎?
- 遺꾩꽍 ?뚭퀬由щ벉 ?ㅺ퀎 臾몄꽌 ?묒꽦

## 鍮뚮뱶 ?섍꼍

?꾨줈?앺듃 ?꾩튂:

```text
C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme
```

鍮뚮뱶 紐낅졊:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\pki08\Documents\Codex\2026-06-14\files-mentioned-by-the-user-readme\work\android-sdk'
.\gradlew.bat assembleDebug
```

理쒖쥌 寃곌낵:

```text
BUILD SUCCESSFUL
```

李멸퀬:

- `local.properties`??workspace ?대? Android SDK瑜?媛由ы궓??
- sandbox ?덉뿉?쒕뒗 Windows zipfs ?묎렐 臾몄젣濡?KAPT媛 ?ㅽ뙣?????덈떎.
- 媛숈? 紐낅졊??沅뚰븳 諛뽰뿉???ㅽ뻾?섎㈃ 鍮뚮뱶媛 ?깃났?덈떎.

## ?덈? 諛붽씀硫????섎뒗 ?곗씠???섎?

```text
WorkoutSet.confirmed = false : 怨꾪쉷 ?명듃
WorkoutSet.confirmed = true  : ?ㅼ젣 ?섑뻾 湲곕줉 ?명듃
```

???섎???UI, Repository, 遺꾩꽍, seed ?곸슜 紐⑤몢?먯꽌 ?숈씪?댁빞 ?쒕떎.

遺꾩꽍 湲곕낯 洹쒖튃:

```text
?ㅼ젣 ?덈젴 遺??= confirmed=true ?명듃留?
```

`confirmed=false`??怨꾪쉷, 誘명솗???명듃, 怨꾪쉷 以?섏쑉 怨꾩궛?먮뒗 ?ъ슜?????덉?留??ㅼ젣 ?섑뻾?됱쑝濡?蹂대㈃ ???쒕떎.

## ?꾩옱 ?뚯씪 援ъ“

### ??猷⑦듃

`MainActivity.kt`

- `MainActivity`
- `AppTab`
- `TrainingTrackPlannerApp`
- ?섎떒 ???곌껐留??대떦

### ?붾㈃

| ?뚯씪 | ?대떦 |
| --- | --- |
| `HomeScreen.kt` | ?? CTA, ?ㅻ뒛 ???? ?ㅻ뒛 ?붿빟 |
| `RecordScreen.kt` | ?좎쭨蹂??대룞 湲곕줉, set ?몄쭛, daily metric |
| `PlanScreen.kt` | ?꾨줈洹몃옩 紐⑸줉/?곸꽭, ?좎쭨 ?곸슜, ??뼱?곌린 UX |
| `ExerciseScreen.kt` | ?대룞 紐⑸줉, 寃?? 移댄뀒怨좊━ ?꾪꽣, ?곸꽭 移대뱶 |
| `AnalysisScreen.kt` | confirmed 湲곕컲 ?⑥닚 ?듦퀎 |
| `CommonUi.kt` | 怨듯넻 移대뱶, ?좎쭨 ?좏깮, ?대룞 ?좏깮 dialog, ?щ㎎ ?⑥닔 |

### ?곗씠??怨꾩링

| ?뚯씪 | ?대떦 |
| --- | --- |
| `Entities.kt` | Room entity |
| `Daos.kt` | Room DAO |
| `TrainingDatabase.kt` | Room database, migration |
| `TrainingRepository.kt` | seed, 湲곕줉/怨꾪쉷 ?숈옉 ?섎? |
| `SeedData.kt` | CSV 湲곕컲 exercise/program seed |
| `ExerciseTaxonomy.kt` | taxonomy token 寃利??뺢퇋??|
| `TrainingViewModel.kt` | ?붾㈃怨?repository ?곌껐 |

## Program Apply ?먮쫫

?좎쭨 ?곸슜 ?먮쫫:

```text
TrainingProgramItem
-> WorkoutEntry
-> WorkoutSet confirmed=false
```

?좎쭨 怨꾩궛:

```text
startDate + ((weekNumber - 1) * 7) + (dayOfWeek - 1)
```

湲곗〈 湲곕줉???덉쓣 ??

| ?좏깮 | ?숈옉 |
| --- | --- |
| ??뼱?곌린 | 湲곗〈 set ??젣 -> 湲곗〈 entry ??젣 -> ?꾨줈洹몃옩 怨꾪쉷 ?앹꽦 |
| 異붽? | 湲곗〈 湲곕줉 蹂댁〈 -> ?꾨줈洹몃옩 怨꾪쉷 異붽? |
| 痍⑥냼 | ?꾨Т 蹂寃??놁쓬 |

??뼱?곌린 ?ㅼ씠?쇰줈洹몃뒗 ?ㅼ쓬 ?뺣낫瑜?蹂댁뿬以??

- ?곹뼢??諛쏅뒗 ?좎쭨 ??
- 湲곗〈 WorkoutEntry ??
- 湲곗〈 confirmed=true ?명듃 ??
- ?쒕뜮?댁벐湲곕? ?좏깮?섎㈃ 湲곗〈 湲곕줉怨??꾨즺 ?명듃媛 ??젣?⑸땲????

## Seed version ?꾨왂

DB version? 2??

異붽????뚯씠釉?

```text
app_meta
```

而щ읆:

```text
key TEXT PRIMARY KEY
value TEXT
updatedAt INTEGER
```

?꾩옱 meta:

```text
exercise_seed_version = 1
program_seed_version = 1
```

seed ?곸슜 ?먯튃:

- additive migration留??ъ슜
- ?ъ슜??湲곕줉 ??젣 湲덉?
- ?ъ슜???꾨줈洹몃옩 ??젣 湲덉?
- 媛숈? `stableKey` ?대룞 以묐났 ?쎌엯 湲덉?
- 媛숈? 湲곕낯 ?꾨줈洹몃옩紐?以묐났 ?쎌엯 湲덉?
- ?ν썑 湲곕낯 ?꾨줈洹몃옩 異붽? ??seed version???щ━怨?鍮좎쭊 湲곕낯 ?꾨줈洹몃옩留??쎌엯

## 湲곕줉 ?붾㈃ ?듭떖 ?숈옉

?명듃 異붽?:

```text
留덉?留??명듃 reps/weightKg/seconds 蹂듭궗
confirmed=false 濡??앹꽦
```

?명듃 ??젣:

```text
留덉?留?1媛??명듃 ??젣 李⑤떒
??젣 ???⑥? setIndex瑜?1遺???ъ젙??
```

entry ?꾨즺 ?곹깭:

```text
confirmed set??1媛??댁긽?대㈃ completedAt 媛깆떊
?놁쑝硫?completedAt = null
```

?ㅽ룷痢?移댄뀒怨좊━:

- RPE? maxReps ?낅젰??媛뺤젣?섏? ?딅뒗??
- weight ?낅젰??遺꾩꽍 媛뺤젣 議곌굔?쇰줈 蹂댁? ?딅뒗??

## 遺꾩꽍 ?꾩옱 ?곹깭

?꾩옱 遺꾩꽍 ?붾㈃? 怨좉툒 遺꾩꽍???꾨땲??

?쒖떆 ??ぉ:

- confirmed set ??
- 珥?蹂쇰ⅷ
- 珥??쒓컙

DAO 吏묎퀎 湲곗?:

```sql
WHERE workout_sets.confirmed = 1
```

怨좉툒 遺꾩꽍? ?꾩쭅 援ы쁽 湲덉??? ?ㅼ쓬 ?④퀎?먯꽌 蹂꾨룄 ?뱀씤 ??吏꾪뻾?쒕떎.

?ㅺ퀎 臾몄꽌:

```text
docs/analysis_algorithm_design.md
```

臾몄꽌??遺꾩꽍 ?뚯쫰:

- General Fitness
- Strength
- Hypertrophy
- Badminton
- Recovery
- Balance / Injury Risk
- Plan Adherence

## Debug 濡쒓렇

Debug 鍮뚮뱶?먯꽌 `TrainingDbSummary` 濡쒓렇瑜?異쒕젰?쒕떎.

濡쒓렇 ??ぉ:

- exerciseCount
- trainingProgramCount
- trainingProgramItemCount
- todayWorkoutEntryCount
- todayConfirmedSetCount
- todayUnconfirmedSetCount

production UI?먮뒗 ?몄텧?섏? ?딅뒗??

## 寃利??곹깭

?꾨즺:

- `assembleDebug` ?깃났
- ?붾㈃ ?뚯씪 遺꾨━ ??而댄뙆???깃났
- Room migration 而댄뙆???깃났
- seed meta DAO 而댄뙆???깃났
- conflict summary 肄붾뱶 而댄뙆???깃났
- confirmed ?섎? 愿???뚯뒪 寃쎈줈 寃利?

?먮룞 ?뚯뒪??

- ?꾩쭅 ?놁쓬

?댁쑀:

- ?꾩옱 ?꾨줈?앺듃??test runner / Room test ?섏〈?깆씠 ?녿떎.
- ?섏〈??異붽????ㅽ듃?뚰겕? Gradle ?닿껐 由ъ뒪?ш? ?덉뼱 Phase 2.5 ?덉젙??踰붿쐞?먯꽌??蹂대쪟?덈떎.

?ㅼ쓬??異붽???理쒖냼 ?뚯뒪??

1. ?꾨줈洹몃옩 ?곸슜 ?명듃??`confirmed=false`
2. ?명듃 異붽???媛믪쓣 蹂듭궗?섏?留?`confirmed=false`
3. ?명듃 ??젣 ??`setIndex`媛 1遺???ъ젙??
4. 留덉?留?1媛??명듃 ??젣 李⑤떒
5. 遺꾩꽍 ?듦퀎??`confirmed=true`留??ы븿

## ?ㅼ쓬 ?묒뾽 異붿쿇 ?쒖꽌

1. ?ㅼ젣 湲곌린 ?먮뒗 emulator?먯꽌 湲곕낯 ?먮쫫 ?섎룞 QA
2. Room in-memory ?뚯뒪???섍꼍 異붽?
3. ?꾨줈洹몃옩 ?곸슜 寃곌낵 誘몃━蹂닿린
4. 湲곕줉 ??젣/蹂듭궗/?대룞 媛숈? lifecycle 湲곕뒫
5. 遺꾩꽍 repository 遺꾨━
6. 怨좉툒 遺꾩꽍 Phase 蹂꾨룄 ?뱀씤 ??援ы쁽

## ?ㅼ쓬 ?묒뾽??二쇱쓽

- `confirmed` ?섎?瑜??덈? 諛붽씀吏 留?寃?
- seed refresh媛 ?ъ슜???곗씠?곕? ??젣?섍쾶 留뚮뱾吏 留?寃?
- 遺꾩꽍 臾몄옣???⑥젙?곸쑝濡?留뚮뱾吏 留?寃?
- 怨좉툒 異붿쿇/吏꾨떒??Phase 2.5 肄붾뱶???욎? 留?寃?
- ?붾㈃ 遺꾨━ ?꾩뿉???숈옉 ?섎???Repository瑜?湲곗??쇰줈 ?뺤씤??寃?

## Phase 2.6 Handoff Addendum

Phase 2.6? 湲곕줉 ?붾㈃ UX? ?닿쾶 ??대㉧ MVP 蹂듦뎄 ?묒뾽?대떎.

### 湲곕줉 ?붾㈃

蹂寃?

- ?명듃 ?됱쓣 ??以?以묒떖?쇰줈 ?뺤텞
- `?잛닔`, `kg`, `珥?, `?뺤씤`, `??젣`瑜?媛숈? ?됱뿉 諛곗튂
- ?ㅽ룷痢?/ ?좎궛?뚮뒗 kg ?낅젰怨??쇨큵 kg 踰꾪듉???④?
- ?뺤씤???명듃???묒? ??李⑥씠濡?援щ텇
- `?쇨큵 kg` 踰꾪듉?쇰줈 entry ??紐⑤뱺 ?명듃 以묐웾 蹂寃?
- ???명듃 kg ?낅젰 ??鍮?誘명솗???명듃??媛숈? kg ?곸슜 ?덈궡

以묐웾 ?먮룞 ?곸슜? ?ㅼ쓬 ?명듃留???곸쑝濡??쒕떎.

```text
weightKg == 0
manualWeight == false
confirmed == false
```

confirmed=true ?명듃???먮룞 ??뼱?곗? ?딅뒗??

### Rest Timer 援ъ“

湲곗〈 README/handoff??rest timer ?ㅺ퀎瑜?Kotlin/Compose ?깆뿉 留욊쾶 蹂듭썝?덈떎.

| ?뚯씪 | 梨낆엫 |
| --- | --- |
| `RestTimerSessionController.kt` | ?몄뀡 ?곹깭, end timestamp, target record, foreground/background, notification/overlay orchestration |
| `RestTimerNotifier.kt` | notification channel, running notification, finished notification |
| `RestTimerOverlayController.kt` | overlay drawing, dragging, remove, delete target, away-session suppression |
| `RestTimerSoundVibration.kt` | finish sound/vibration |
| `RestTimerNavigation.kt` | notification/overlay click target intent |
| `RestTimerUi.kt` | Compose mini bar, permission hint |

MainActivity媛 ?대떦?섎뒗 寃?

- `onResume`
- `onPause`
- `onDestroy`
- notification / overlay click target navigation

MainActivity媛 ?대떦?섏? ?딅뒗 寃?

- countdown state
- end timestamp
- notification content
- overlay drawing
- sound / vibration

### Persisted Rest Timer Keys

`RestTimerSessionController`媛 愿由ы븳??

```text
rest_end_at
rest_next
rest_target_date
rest_target_entry_id
rest_finished
```

### Overlay Delete Suppression

蹂듦뎄???숈옉:

- ??諛뽰뿉???ㅻ쾭?덉씠瑜??リ굅??delete target???쒕∼?섎㈃ ?꾩옱 away session ?숈븞 ?ㅼ떆 ?쒖떆?섏? ?딅뒗??
- ?깆쑝濡??뚯븘?ㅻ㈃ suppression reset.
- ????대㉧媛 ?쒖옉?섎㈃ suppression reset.

### ?⑥? QA

?ㅼ젣 湲곌린 ?먮뒗 emulator?먯꽌 ?뺤씤?댁빞 ?쒕떎.

- notification 沅뚰븳 ?붿껌
- running notification 媛깆떊
- finished notification
- overlay 沅뚰븳 ?ㅼ젙 ?대룞
- ??諛?overlay ?쒖떆
- overlay drag
- overlay delete target
- delete suppression
- sound / vibration

?먯꽭??QA ?덉감??`outputs/phase2_6_record_timer_patch.md`???덈떎.

## Phase 2.6 Build Environment Patch Handoff

Gradle problems report 異⑸룎留??고쉶?덈떎. 湲곕뒫 肄붾뱶??嫄대뱶由ъ? ?딆븯??

蹂寃??뚯씪:

- `gradle.properties`
- `build.gradle.kts`
- `local.properties`

?댁슜:

- `org.gradle.problems.report=false` 異붽?
- root `cleanBuildReports` ?쒖뒪??異붽?
- `build/reports/problems`, `app/build/reports/problems`留???젣
- root/app `assembleDebug` ?꾩뿉 `cleanBuildReports` ?ㅽ뻾
- `local.properties`瑜?workspace SDK 寃쎈줈濡?蹂寃?
- stale `build/reports/problems` ?대뜑 ??젣

鍮뚮뱶 寃利?

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-problems-report assembleDebug
```

寃곌낵: `BUILD SUCCESSFUL`.

## Phase 2.6 Record UX / Set RPE / Rest Handoff

?대쾲 ?⑥튂??Phase 3 CSV ?꾩뿉 湲곕줉 ?붾㈃怨??닿쾶 ??대㉧ ?ъ슜?깆쓣 蹂듦뎄???묒뾽?대떎. CSV 諛깆뾽/蹂듭썝怨?怨좉툒 遺꾩꽍? 援ы쁽?섏? ?딆븯??

?듭떖 蹂寃?

- `WorkoutSet`??`rpe: Double?` 異붽?
- `WorkoutSet`??`restSecondsOverride: Int?` 異붽?
- Room DB version 3 additive migration 異붽?
- `WorkoutEntry.rpe`??legacy fallback / ?대룞 ?꾩껜 泥닿컧?쇰줈 ?좎?
- ?명듃 異붽? ??reps / weightKg / seconds??蹂듭궗?섏?留?`rpe`? `restSecondsOverride`??蹂듭궗?섏? ?딆쓬
- ?명듃 ?뺤씤 ??timer duration? `set.restSecondsOverride ?: entry.restSeconds`
- `WorkoutEntry.notes`??援ъ“??泥섎갑媛???μ냼媛 ?꾨떂
- ?꾨줈洹몃옩 ?곸슜 ???⑥닚 援ъ“ 泥섎갑 ?띿뒪?몃뒗 notes??蹂듭궗?섏? ?딆쓬

Record UI:

- ?곷떒 DailyMetric ?낅젰? `而⑤뵒?? 踰꾪듉 ?ㅼ뿉 ?묓옒
- ?대룞 硫붾え / maxReps / entry-level RPE / 湲곕낯 ?댁떇 ?몄쭛? `?곸꽭` 踰꾪듉 ?ㅼ뿉 ?묓옒
- ?명듃 ?됱? compact input, RPE chip, ?댁떇 chip 援ъ“
- ?쇨큵 硫붾돱??kg ?ㅼ젙 / 利앷? / 媛먯냼, ?잛닔 利앷? / 媛먯냼, ?댁떇 ?ㅼ젙 / 利앷? / 媛먯냼 吏??
- confirmed=true ?명듃 ?ы븿 蹂寃쎌? ?ъ슜?먭? `?꾨즺 ?명듃 ?ы븿`???좏깮?댁빞 ??

Rest timer:

- 湲곗〈 `RestTimerSessionController`, `RestTimerNotifier`, `RestTimerOverlayController`, `RestTimerSoundVibration` 梨낆엫 遺꾨━ ?좎?
- overlay delete suppression ?좎?
- app shell? lifecycle / intent navigation留??대떦

?좉퇋 臾몄꽌:

- `outputs/phase2_6_legacy_record_timer_audit.md`
- `outputs/phase2_6_rest_timer_recovery.md`

## Phase 2.7 Record Calendar UI Handoff

Phase 2.7? 湲곕줉 ??留덉씠??UI ?⑥튂??

蹂寃??뚯씪 ?듭떖:

- `RecordScreen.kt`
- `RecordCalendarScreen.kt`
- `RestTimerState.kt`
- `RestTimerSessionController.kt`
- `Daos.kt`
- `TrainingRepository.kt`
- `TrainingViewModel.kt`

?숈옉:

- `?щ젰`? ?좎쭨 ?대룞 row??`?ㅼ쓬?? ?ㅻⅨ履쎌뿉 諛곗튂
- `而⑤뵒?? / `?대룞 異붽?`??蹂꾨룄 compact row??諛곗튂
- ?곷떒 mini timer bar??湲곕줉 ?붾㈃?먯꽌 ?쒓굅
- active timer??`targetEntryId` + `targetSetId`媛 留욌뒗 ?명듃 row???쒖떆
- notification / overlay source of truth??怨꾩냽 `RestTimerSessionController`
- ?꾨즺 ?대룞? UI sorting?쇰줈 ?꾩뿉 ?쒖떆
- ?붽컙 summary??DAO query?먯꽌 怨꾩궛

吏묎퀎 湲곗?:

- `confirmed=true`: ?ㅼ젣 ?명듃 ?? 蹂쇰ⅷ, ?쒓컙
- `confirmed=false`: planned / unconfirmed count
- 蹂쇰ⅷ: `reps * weightKg`

?ㅽ겕紐⑤뱶:

- ?쒖뒪???ㅽ겕紐⑤뱶 ?곕룞 吏?먮맖
- ?섎룞 ?좉?? ?놁쓬

臾몄꽌:

- `outputs/phase2_7_record_calendar_ui_patch.md`

## Phase 2.7.2 Calendar Lifecycle Handoff

湲곗〈 Java ?깆쓽 `RecordCalendarController` / `TrainingWorkoutEntryRepository` ??븷 ?쇰?瑜???Kotlin 援ъ“??蹂듭썝?덈떎.

?섏젙 ?뚯씪:

- `RecordCalendarScreen.kt`
- `Daos.kt`
- `TrainingRepository.kt`
- `TrainingViewModel.kt`

??臾몄꽌:

- `outputs/phase2_7_2_calendar_lifecycle_audit.md`
- `outputs/phase2_7_2_calendar_lifecycle_patch.md`

## Phase 3.0.0 Handoff Addendum

Patch 3.0.0 prepares Analysis Engine V3 without changing the user-facing analysis tab.

New packages:

- `com.training.trackplanner.analysis.core`
- `com.training.trackplanner.analysis.metrics`
- `com.training.trackplanner.analysis.methods`
- `com.training.trackplanner.analysis.text`
- `com.training.trackplanner.analysis.engine`

Rules to preserve:

- Do not treat the last workout date as today. Use `AnalysisDateProvider`.
- Actual completed load is `date <= today` and `WorkoutSet.confirmed=true`.
- Future plan projection is `date > today` and `WorkoutSet.confirmed=false`.
- Do not enable new analysis methods until the matching review template is completed.
- Do not expose V3 method results in the normal UI during 3.0.0.

DAO additions are read-only and exist only for V3 input collection:

- `ExerciseDao.allExercises`
- `WorkoutDao.entriesWithSetsUntil`
- `WorkoutDao.entriesWithSetsAfter`
- `DailyMetricDao.metricsUntil`

Reference document:

- `outputs/phase3_0_0_analysis_engine_v3_foundation.md`

## Phase 3.1.1 Handoff Addendum

Patch 3.1.1 adds structured fatigue/readiness metadata only.

New files:

- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt`
- `app/src/main/java/com/training/trackplanner/data/MetadataSanityChecker.kt`
- `app/src/test/java/com/training/trackplanner/data/MetadataSanityCheckerTest.kt`
- `outputs/phase3_1_1_fatigue_metadata_foundation.md`

DB:

- Room version is now 4.
- Migration `3 -> 4` only adds Exercise metadata and fatigue weight columns.
- User records, plans, and sets are not deleted.

Important:

- Do not build final fatigue/readiness judgment on this patch alone.
- Use structured metadata fields, not exercise-name parsing.
- Remaining string heuristics in `SeedData.kt` are legacy fallback for old seed metadata, not the new fatigue mapping path.
- The seed catalog currently validates as 214 HIGH-confidence rows with no NEEDS_REVIEW rows.

## Phase 3.1.2 Handoff Addendum

Patch 3.1.2 adds the remaining metadata and feature-vector path for later analysis engines.

New files:

- `app/src/main/java/com/training/trackplanner/analysis/features/ExerciseAnalysisMapper.kt`
- `app/src/main/java/com/training/trackplanner/analysis/features/MetadataReadinessReporter.kt`
- `outputs/metadata_analysis_readiness_report.md`
- `outputs/phase3_1_2_metadata_readiness_patch.md`

DB:

- Room version is now 5.
- Migration `4 -> 5` only adds Exercise metadata columns.

Rules:

- Future 3.2 fatigue analysis should consume `AnalysisExerciseFeatures`.
- Future 3.3 progress analysis should branch by `progressMetricType`.
- Future 3.4 badminton analysis should branch by `badmintonTransferStrength`, `courtMovementTypes`, and `badmintonSkillTargets`.
- Do not classify exercises by exercise-name string matching in analyzers.

## Phase 3.1.3 Handoff Addendum

Patch 3.1.3 implements Today Readiness / Fatigue analysis and exposes it on the Analysis tab.

New package:

- `app/src/main/java/com/training/trackplanner/analysis/readiness`

Key classes:

- `TodayReadinessEngine`
- `DailyAnalysisLoadAggregator`
- `ResidualFatigueCalculator`
- `StatisticalBaselineCalculator`
- `AdaptiveBaselineCalculator`
- `AdaptiveBaselineUpdateEvaluator`
- `FatiguePressureCalculator`
- `RecoverySignalInterpreter`
- `PerformanceDropDetector`
- `PainGateEvaluator`
- `TodayReadinessDecisionEngine`
- `TodayReadinessSentenceBuilder`
- `FatigueDetailSectionBuilder`

UI connection:

- `TrainingRepository.todayReadinessSummary()` reads exercises, entries until today, and daily metrics.
- `TrainingViewModel.todayReadinessSummary` exposes a state flow.
- `AnalysisScreen` shows the Today Readiness card above existing simple stats.

Critical behavior:

- only `confirmed=true` sets are included in fatigue load.
- `confirmed=false` planned sets are excluded.
- planned future entries are not mixed into today's fatigue.
- exercise classification is driven by `ExerciseAnalysisMapper`, not exercise names.
- pain is handled as a gate, not averaged into a fatigue score.
- sentence output avoids injury prediction, medical diagnosis, overtraining diagnosis, and deterministic danger phrasing.

Adaptive baseline:

- calculated deterministically from recent records and outcome signals.
- high load alone does not raise tolerance.
- successful high exposure can raise tolerance slightly.
- failed exposure blocks or lowers tolerance slightly.
- single-run up/down changes are capped at 5%.

Tests:

- `app/src/test/java/com/training/trackplanner/analysis/readiness/TodayReadinessEngineTest.kt`

Verification note:

- A first Gradle test run reached Kotlin compile and exposed a nullable enum comparison issue.
- That issue was fixed.
- Further unrestricted Gradle execution was blocked by the environment usage limit; sandboxed Gradle cannot download the wrapper distribution.
- Next worker should rerun `testDebugUnitTest` and `assembleDebug`.

Reference:

- `outputs/phase3_1_3_today_readiness_engine.md`

## Phase 3.2.0 Handoff Addendum

Patch 3.2.0 adds the Analysis tab section `?깃낵 異붿꽭 遺꾩꽍`.

New package:

- `app/src/main/java/com/training/trackplanner/analysis/trends`

Key classes:

- `PerformanceTrendEngine`
- `WeeklyAnalysisAggregator`
- `TrendMath`
- `StrengthPerformanceIndexCalculator`
- `BadmintonTrainingLoadIndexCalculator`
- `FatigueCompositeIndexCalculator`
- `TrendForecastRangeCalculator`
- `PerformanceChartSpecBuilder`
- `DetailChartSelector`
- `ScatterRelationshipAnalyzer`
- `PerformanceTrendSentenceBuilder`

UI connection:

- `TrainingRepository.performanceTrendSummary()` reads exercises, entries until today, and daily metrics.
- `TrainingViewModel.performanceTrendSummary` exposes a state flow.
- `AnalysisScreen` renders the Performance Trend card below Today Readiness.

First-screen rules:

- exactly 3 dashboard chart specs.
- each dashboard chart is `ChartType.LINE`.
- each dashboard chart has one visible line.
- `emphasizeValue=false`.
- one short trend sentence only.

Detail rules:

- four sections: strength, badminton, fatigue, relationship.
- strength/badminton/fatigue sections each use one chart slot.
- chart mode controls reset incompatible metric selections.
- relationship section uses scatter plot and trend-only language.

Calculation rules:

- strength performance = intensity 0.50 + volume 0.40 + efficiency 0.10.
- badminton training = court 0.60 + footwork/reactive 0.25 + support 0.15.
- fatigue composite = average standardized fatigue 0.60 + max fatigue 0.25 + recovery/performance penalty 0.15.
- raw kg, raw reps, and raw minutes are not directly added across composite categories.
- all display indices are standardized against personal baselines or fallback to neutral 100 with low confidence.

Verification note:

- Added `PerformanceTrendEngineTest`.
- `testDebugUnitTest` succeeded after updating the safe z-score fallback assertion.
- `assembleDebug` succeeded with `--no-daemon --no-problems-report`.

Reference:

- `outputs/phase3_2_0_performance_trend_analysis.md`

## Minor Patch: Record Date Switcher Width

Files:

- `app/src/main/java/com/training/trackplanner/RecordScreen.kt`

Change:

- The Record tab date row now uses compact `OutlinedButton` padding for `?댁쟾??, `?ㅼ쓬??, and `?щ젰`.
- The date label uses a single line with wrapping disabled, preventing `YYYY-MM-DD` from breaking across lines.

Scope:

- UI-only patch.
- No DB schema changes.
- No repository behavior changes.
- No `WorkoutSet.confirmed` behavior changes.

Verification:

- `assembleDebug` succeeded after the patch.

## Phase 3.3.0 Badminton Transfer Analysis

Files:

- `app/src/main/java/com/training/trackplanner/analysis/badminton`
- `app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonTransferAnalysisEngineTest.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisScreen.kt`
- `app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`

UI connection:

- `TrainingRepository.badmintonTransferSummary()` builds the 3.3 result.
- `TrainingViewModel.badmintonTransferSummary` exposes the result.
- `AnalysisScreen` renders the `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍` card below Today Readiness.

First-screen policy:

- Show only `recommendationSentence`.
- Do not show percentages, charts, reasons, or exercise lists on the first card.

Detail policy:

- One chart/list area is reused.
- Selector options are `?꾩씠異?鍮꾩쨷`, `?꾩씠?좏삎 鍮꾩쨷`, `理쒓렐 7??vs 28??, and `?대룞蹂??꾩씠 ?먭레 Top 5`.

Calculation policy:

- `confirmed=true` sets only.
- `confirmed=false` planned sets excluded.
- no exercise-name classification.
- transfer type comes from `badmintonTransferStrength`.
- transfer axes are derived from existing structured metadata.
- 3.1 readiness is used to avoid strong transfer recommendations on high-fatigue days.

Verification:

- `testDebugUnitTest` succeeded.
- Static search found no exercise-name `.contains` classification in the new main package.

Reference:

- `outputs/phase3_3_0_badminton_transfer_analysis.md`

## v0.3.4.0 Home Cleanup and Backup Restore

Files:

- `app/src/main/java/com/training/trackplanner/HomeScreen.kt`
- `app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
- `app/src/main/java/com/training/trackplanner/data/RecordCsvBackupRestore.kt`
- `app/src/test/java/com/training/trackplanner/data/RecordCsvBackupRestoreTest.kt`
- `app/build.gradle.kts`

Home:

- removed the `?ㅻ뒛 ???? card.
- added `湲곕줉 愿由? under `?ㅻ뒛 ?붿빟`.
- `湲곕줉 諛깆뾽` uses `CreateDocument("text/csv")`.
- `湲곕줉 蹂듭썝` uses `OpenDocument()`.

Repository API:

- `exportRecordsBackup(uri)`
- `importRecordsBackup(uri)`

CSV behavior:

- restore-format CSV exports/imports detailed daily metrics, entries, and sets.
- daily_timeseries CSV imports daily metrics and category-level aggregate records.
- malformed `sleep_hours` and other numeric fields are ignored instead of crashing.
- daily_timeseries aggregate entries use note marker `CSV daily_timeseries import` to avoid duplicate aggregate creation.

Data semantics:

- set `confirmed=true` remains completed.
- set `confirmed=false` remains planned.
- daily_timeseries cannot reconstruct exact original exercises/sets; it restores date-level analysis continuity.

Verification:

- `testDebugUnitTest` succeeded.
- `compileDebugKotlin` succeeded.

Reference:

- `outputs/phase3_4_0_home_backup_restore.md`

援ы쁽:

- ?좎쭨 long press action dialog
- 怨꾪쉷?쇰줈 蹂듭궗
- 湲곕줉?곹깭源뚯? 蹂듭궗
- ?대룞
- ??젣
- ?좏깮蹂듭궗
- 異⑸룎 summary / conflict dialog

Repository API:

- `calendarConflictSummary`
- `copyDate`
- `moveDate`
- `deleteDate`
- `copyDateRangeAsPlan`

?뺤콉:

- DB schema 蹂寃??놁쓬
- DailyMetric? ?좎쭨 怨좎쑀 而⑤뵒?섏씠誘濡?蹂듭궗 / ?대룞 / ??젣?섏? ?딆쓬
- 怨꾪쉷 蹂듭궗? ?좏깮蹂듭궗??`confirmed=false`
- 湲곕줉?곹깭 蹂듭궗? ?대룞? confirmed 蹂댁〈
- ??뼱?곌린 / ??젣???ъ슜???뺤씤 dialog瑜?嫄곗묠

## Post v0.3.4.0 Analysis UI Compactness Patch

Scope:

- `AnalysisScreen.TodayReadinessCard` collapsed layout only.

Changed:

- reduced Today Readiness card padding and internal spacing.
- reduced status typography size.
- limited collapsed primary reasons to 2 items.
- rendered collapsed recommended/restricted modes as one-line summaries.

Unchanged:

- Today Readiness analysis engine.
- detail section content when expanded.
- confirmed-only analysis semantics.
- performance trend and badminton transfer analysis cards.

## v0.3.4.1 Program Generator and Plan Tab Recovery

Scope:

- Plan tab program create/edit/delete.
- Automatic program skeleton generation.
- Safe program application overwrite.
- Program seed/user program data preservation.

Files:

- `program_skeleton_generator_spec_v0.3.4.1.md`
- `outputs/phase3_4_1_program_generator.md`
- `app/src/main/java/com/training/trackplanner/PlanScreen.kt`
- `app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`
- `app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingDatabase.kt`
- `app/src/main/java/com/training/trackplanner/data/Daos.kt`
- `app/src/main/java/com/training/trackplanner/data/Entities.kt`
- `app/src/test/java/com/training/trackplanner/data/ProgramSkeletonGeneratorTest.kt`
- `app/build.gradle.kts`

Program data model:

- `TrainingProgram` now stores optional planning inputs such as goal, weekly training days, session minutes, equipment, exclusions, badminton transfer ratio, sport/strength ratio, periodization type, and updated timestamp.
- Room migration `5 -> 6` is additive only.

Generator:

- `ProgramSkeletonGenerator` builds a 4-week skeleton from exercise metadata and confirmed history.
- It chooses a periodization shape, distributes sessions across weekdays, scores candidates, limits high-fatigue clustering, and produces editable program items.
- Weight suggestions use direct same-exercise history first, similar metadata history second, and otherwise leave kg empty for manual input.
- Program editor generation and save now require a non-blank program name.
- `?꾨? ?덈줈 留뚮뱾湲? clears the current generated preview before creating a fresh skeleton from the current editor inputs.

Overwrite behavior:

- `ProgramApplyConflictSummary` now describes the date range and planned-only entries.
- `Overwrite` deletes only entries with no confirmed set in the program date range.
- Confirmed records are preserved even when overwrite is selected.

Next recommended work:

- Add Room in-memory tests for planned-only overwrite behavior.
- Add explicit condition/injury taxonomy if exclusions need to move beyond user text.
- Add program-level duplicate prevention if multiple saved variants share identical metadata.

Record calendar range delete:

- `RecordCalendarScreen` now has `CalendarRangeDelete` and `PendingRangeDelete`.
- Long-press menu includes `select delete`.
- `TrainingViewModel.deleteDateRange(startDate, endDate, includeConfirmed)` delegates to repository.
- `includeConfirmed=true` removes all entries/sets in range.
- `includeConfirmed=false` removes only unconfirmed sets, deletes empty entries, and reindexes remaining sets.
- Daily metrics are preserved.

Back navigation:

- Root app back goes to Home when the current tab is not Home.
- Home back is left to Android default close behavior.
- Record calendar back closes calendar state and returns to the selected record date.
- Plan create/edit/detail back returns to the prior Plan screen.
- Expanded Analysis detail cards collapse before leaving the Analysis tab.

Rest timer overlay adjustment:

- `RestTimerState.hasNextTarget` tracks whether a confirmed set has a next unconfirmed set to prepare.
- Overlay display now requires `hasNextTarget=true`.
- Last-set timers can still run in-app, but app-outside overlay is not shown when there is no next target.
- Legacy README/handoff audit is documented in `outputs/rest_timer_overlay_legacy_audit_v0.3.4.1.md`.

## v0.3.4.2 Sport Session Program Filter

Scope:

- Program skeleton generation.
- Exercise planning metadata.
- CSV import-created exercise classification.
- Program editor helper text.

Files:

- `app/src/main/java/com/training/trackplanner/data/ExercisePlanning.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt`
- `app/src/main/java/com/training/trackplanner/data/Entities.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingDatabase.kt`
- `app/src/main/java/com/training/trackplanner/data/SeedData.kt`
- `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt`
- `app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt`
- `app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`
- `app/src/main/java/com/training/trackplanner/PlanScreen.kt`
- `app/src/test/java/com/training/trackplanner/data/ProgramSkeletonGeneratorTest.kt`
- `outputs/v0.3.4.2_sport_session_program_filter.md`

Data rule:

- `TRAINING_EXERCISE + PROGRAM_SELECTABLE` is the only program-selectable pair.
- `SPORT_SESSION`, `MATCH_RECORD`, and `FATIGUE_ONLY` records can remain analysis/load inputs but cannot become program items.

Generator behavior:

- candidate filtering happens before scoring.
- high badminton transfer ratio cannot pull badminton matches into the plan.
- fallback candidate pools also use only program-selectable exercises.

Preservation:

- no existing workout record is deleted.
- confirmed/unconfirmed semantics are unchanged.
- sport sessions remain valid fatigue/readiness/trend history.

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
Scope:

- Exercise tab list interaction.
- Shared `ExerciseListItem` row.

Behavior:

- Exercise tab rows no longer replace a top detail card on tap.
- The top detail card is removed from the Exercise tab list.
- Each row exposes a compact `i` button.
- Pressing `i` opens a dialog with `ExerciseDetailCard` plus structured metadata tags.
- Exercise picker dialogs still use row tap for selection.

Validation:

- `testDebugUnitTest` succeeded.
- `assembleDebug` succeeded.
- Debug APK copied to `C:\Users\pki08\Documents\Codex\google_drive_backup\WhatYouGottaTrain-v0.3.4.3-debug.apk`.

## v0.3.4.4 Handoff

- 鍮뚮뱶 湲곗? 踰꾩쟾? `0.3.4.4` / `versionCode 30404`??
- Room schema??`app/schemas/com.training.trackplanner.data.TrainingDatabase/9.json`遺??Git???ы븿?쒕떎.
- migration test 怨④꺽? 異붽??먯?留?v8 schema asset???놁뼱 8->9 ?ㅽ뻾 ?뚯뒪?몃뒗 `@Ignore` ?곹깭??
- 珥덇린 ?꾨줈?꾩? ?쇰컲 UI?먯꽌 ?먯닔/紐⑤뱶紐낆쓣 ?몄텧?섏? ?딅뒗?? Home???쒖큹湲??꾨줈?꾟앹뿉???섏젙?쒕떎.
- ?대룞 ??젣 ?뺤콉: ?ъ슜??異붽? ?대룞 + 誘몄갭議??대룞留???젣, 洹??몃뒗 ?④? ?곗꽑.
- 湲곕줉 ??`?곸꽭` 踰꾪듉? ?꾩옱 ?명듃 ?곸꽭 ?묎린/?쇱튂湲곗슜?대떎. ?대룞蹂?怨쇨굅 湲곕줉 ?곸꽭??v0.3.4.5 ?꾨낫 臾몄꽌 李몄“.

## v0.3.4.4.1 Hotfix Handoff

- 踰꾩쟾? `0.3.4.4.1` / `versionCode 304041`?대떎.
- ??schema??`app/schemas/com.training.trackplanner.data.TrainingDatabase/10.json`?대떎.
- 珥덇린 ?꾨줈??UI?먯꽌 ?듭떖 ?낅젰? ???댁긽 ?먯쑀 ?띿뒪?멸? ?꾨땲??
- `birthYearOrAgeRange`, `gender`, `strengthTrainingAge`, `badmintonTrainingAge`, `painAreas`, `avoidedMovements`, `goals`???명솚?⑹쑝濡??⑥븘 ?덉쑝????遺꾩꽍 ?낅젰?쇰줈 ?곗? ?딅뒗??
- ??profile backup key??enum key / numeric value瑜???ν븳??

## v0.3.4.4.4 Hotfix Addendum

- Version bumped to `v0.3.4.4.4` / DB version `11`.
- `MIGRATION_10_11` is no-op; structured initial profile fields were already present in DB v10.
- Added `InitialAdaptationProfile` and cold-start readiness baseline binding.
- Removed the old fatigued-only profile adjustment pattern.
- Initial profile now can affect READY/CAUTION/FATIGUED outcomes through baseline blending and final summary adjustment.
- CSV profile restore now sanitizes enum keys, RPE, and 1-5 recovery fields.
- Debug APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\3444-TrainingTrackPlanner-v0.3.4.4.4-debug.apk`.

## v0.3.4.5 Current Handoff

- Current app version is `v0.3.4.5` / `versionCode 304045`.
- Room DB version is still `12`; no destructive migration or schema reset was introduced.
- Exercise seed version is `6`.
- Source release commit: `2cdaee6 v0.3.4.5 improve trend UI, deletion UX, and badminton metrics`.
- Release tag: `v0.3.4.5`.
- Canonical current handoff file: `outputs/v0.3.4.5_reproduction_handoff.md`.
- Current document index: `outputs/v0.3.4.5_document_index.md`.
- Record cards expose an exercise-level delete action; last unconfirmed set deletion removes the entry.
- Rest overlay requires a valid next exercise target and no longer appears after the app has no next workout to guide.
- Performance trend UI uses `성과 추세`, `RPE 대비 운동량`, and `셔틀 플레이 시간`.
- Detail chart selectors hide unsuitable strength/fatigue ranking modes and keep badminton ranking.
- Relationship analysis uses dropdown axis selectors and trend-only wording.
- Badminton metric calculation separates shuttle play, footwork/reactive drills, and support training with structured metadata.
- Debug APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\TrainingTrackPlanner-v0.3.4.5-debug.apk`.
