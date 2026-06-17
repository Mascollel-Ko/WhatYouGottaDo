# WhatYouGottaTrain Data Meaning Mapping

?묒꽦?? 2026-06-14
?곸슜 踰붿쐞: Phase 2.5

## 臾몄꽌 紐⑹쟻

??臾몄꽌??DB ?곗씠?곗? ?ㅼ젣 ?대룞 ?섎?瑜??곌껐?쒕떎.
?섏쨷??遺꾩꽍, 異붿쿇, 吏꾨떒??留뚮뱾 ??而щ읆 ?섎?瑜??섎せ ?댁꽍?섏? ?딅룄濡?湲곗????④릿??

?욎쑝濡?DB, seed, taxonomy, 遺꾩꽍 湲곗???諛붾뚮㈃ ??臾몄꽌???④퍡 媛깆떊?쒕떎.

## ?듭떖 ?섎?

媛??以묒슂??洹쒖튃:

```text
WorkoutSet.confirmed = false : 怨꾪쉷 ?명듃
WorkoutSet.confirmed = true  : ?ㅼ젣 ?섑뻾 湲곕줉 ?명듃
```

遺꾩꽍 湲곕낯媛?

```text
?ㅼ젣 ?덈젴 遺??= confirmed=true ?명듃留?
```

`confirmed=false`???ㅼ쓬 ?⑸룄濡쒕쭔 ?대떎.

- ?ㅻ뒛 怨꾪쉷
- 誘명솗???명듃
- 怨꾪쉷 以?섏쑉
- ?덉젙 遺??
- ?ㅼ쓬 ?됰룞 ?꾨낫

?ㅼ젣 ?섑뻾?? 珥?蹂쇰ⅷ, 珥??쒓컙?먮뒗 湲곕낯?곸쑝濡??ы븿?섏? ?딅뒗??

## Seed Source

?꾩옱 seed??assets CSV?먯꽌 ?ㅼ뼱?⑤떎.

```text
app/src/main/assets/training_settings_seed.csv
```

row type:

| row_type | ?섎? | DB 諛섏쁺 |
| --- | --- | --- |
| `exercise` | ?대룞 移댄깉濡쒓렇 | `Exercise` |
| `program` | 湲곕낯 ?꾨줈洹몃옩 ?ㅻ뜑 | `TrainingProgram` |
| `program_item` | ?꾨줈洹몃옩 泥섎갑 ??ぉ | `TrainingProgramItem` |

?대룞 seed???ν썑 ?꾩껜 200??媛??대룞怨?taxonomy 遺꾩꽍???곗씤??

## Seed Version Meta

Phase 2.5?먯꽌 `app_meta` ?뚯씠釉붿씠 異붽??섏뿀??

| key | value ?섎? |
| --- | --- |
| `exercise_seed_version` | ?곸슜???대룞 seed 踰꾩쟾 |
| `program_seed_version` | ?곸슜???꾨줈洹몃옩 seed 踰꾩쟾 |

?꾨왂:

- seed version???щ씪媛硫?鍮좎쭊 湲곕낯 ?곗씠?곕쭔 異붽??쒕떎.
- ?ъ슜??湲곕줉? ??젣?섏? ?딅뒗??
- ?ъ슜???앹꽦 ?대룞/?꾨줈洹몃옩? ??젣?섏? ?딅뒗??
- ?대룞? `stableKey`濡?以묐났??留됰뒗??
- 湲곕낯 ?꾨줈洹몃옩? 媛숈? ?꾨줈洹몃옩紐낆쑝濡?以묐났??留됰뒗??

## Exercise

`Exercise`???⑥닚 ?쒖떆 紐⑸줉???꾨땲??
?ν썑 遺꾩꽍?먯꽌 ?대룞 ?섎?瑜??댁꽍?섎뒗 taxonomy ?먯쿇?대떎.

| ?꾨뱶 | ?ㅼ젣 ?섎? | 遺꾩꽍/湲곕뒫 ?ъ슜 |
| --- | --- | --- |
| `id` | 濡쒖뺄 DB ?대룞 ID | entry/program ?곌껐 |
| `name` | ?ъ슜?먯뿉寃?蹂댁씠???대룞紐?| 寃?? 湲곕줉, 怨꾪쉷 ?쒖떆 |
| `category` | ??遺꾨쪟 | 洹쇰젰?대룞, 湲곕뒫?깆슫?? ?좎궛?뚯슫?? ?ㅽ룷痢?|
| `detail1`, `detail2` | 蹂댁“ ?ㅻ챸 | 寃?? ?곸꽭 ?쒖떆 |
| `mode` | 湲곕줉 諛⑹떇 ?뚰듃 | ?쒓컙 湲곕컲 ?대룞 ?먮떒 |
| `description` | ?ъ슜???ㅻ챸 | ?곸꽭 移대뱶 |
| `defaultRestSeconds` | 湲곕낯 ?댁떇 | entry ?앹꽦, program item ?앹꽦 |
| `familyId`, `familyName` | ?대룞 怨꾩뿴 | 蹂???대룞 鍮꾧탳 |
| `familyRole` | 怨꾩뿴 ????븷 | 二??대룞/蹂댁“ ?대룞 ?먮떒 |
| `familyE1rmMultiplier` | e1RM 蹂??蹂댁젙 | ?ν썑 媛뺣룄 異붿젙 |
| `stableKey` | seed ?덉젙 ??| 以묐났 諛⑹?, seed refresh |
| `movementPattern` | ?吏곸엫 ?⑦꽩 | push, pull, squat ??洹좏삎 遺꾩꽍 |
| `movementCategory` | ?吏곸엫 ?깃꺽 | strength, plyometric ??|
| `primaryMuscles` | 二??ъ슜 洹쇱쑁 | 洹쇱쑁蹂?遺??|
| `secondaryMuscles` | 蹂댁“ 洹쇱쑁 | 蹂댁“ 遺??|
| `equipmentTags` | ?λ퉬 | 媛?ν븳 ?대룞 ?꾪꽣 |
| `forceType` | ?섏쓽 諛⑺뼢/?깃꺽 | ?⑦꽩 遺꾩꽍 |
| `bodyRegion` | ?좎껜 ?곸뿭 | ?곸껜/?섏껜/?꾩떊 洹좏삎 |
| `laterality` | ?묒륫/?몄륫 | 醫뚯슦 洹좏삎, 遺???꾪뿕 ?뚯쫰 |
| `trainingRole` | ?덈젴 紐⑹쟻 | strength, hypertrophy ???뚯쫰 |
| `stabilityRoles` | ?덉젙???붽뎄 | 肄붿뼱, 李⑹?, 洹좏삎 |
| `sportTransferDirect` | ?ㅽ룷痢?吏곸젒 ?꾩씠 | 諛곕뱶誘쇳꽩 ??醫낅ぉ ?뚯쫰 |
| `sportTransferSupportive` | ?ㅽ룷痢?蹂댁“ ?꾩씠 | 蹂댁“ 泥대젰 ?뚯쫰 |
| `accessoryRoles` | 蹂댁“ ?대룞 ??븷 | ?쎌젏 蹂댁셿, 蹂대뵒鍮뚮뵫 蹂댁“ |
| `loadProfile` | 遺???깃꺽 | ?쇰줈/?뚮났 ?댁꽍 |
| `metadataConfidence` | taxonomy ?좊ː??| 遺꾩꽍 ?뺤떊??議곗젅 |

二쇱쓽:

- ?쒓뎅???쒖떆媛믨낵 engine-facing metadata瑜??욎? ?딅뒗??
- 遺꾩꽍?먮뒗 ?뺢퇋?붾맂 taxonomy token???곗꽑 ?ъ슜?쒕떎.
- taxonomy媛 ??? ?좊ː?꾨㈃ ?⑥젙??異붿쿇???쇳븳??

## WorkoutEntry

`WorkoutEntry`???뱀젙 ?좎쭨???뱀젙 ?대룞 臾띠쓬?대떎.
?ㅼ젣 ?섑뻾 ?щ???entry媛 ?꾨땲???섏쐞 `WorkoutSet.confirmed`媛 寃곗젙?쒕떎.

| ?꾨뱶 | ?ㅼ젣 ?섎? | 遺꾩꽍/湲곕뒫 ?ъ슜 |
| --- | --- | --- |
| `date` | 湲곕줉/怨꾪쉷 ?좎쭨 | ?좎쭨蹂?湲곕줉, ?꾨줈洹몃옩 ?곸슜 |
| `exerciseId` | ?곌껐???대룞 ID | taxonomy ?곌껐 |
| `exerciseName` | ?쒖떆???대룞紐??ㅻ깄??| ?대룞紐낆씠 諛붾뚯뼱??湲곕줉 ?쒖떆 ?좎? |
| `category` | ?쒖떆??移댄뀒怨좊━ ?ㅻ깄??| ?ㅽ룷痢??낅젰 ?꾪솕 ??|
| `restSeconds` | ?대룞 ?⑥쐞 ?댁떇 | 諛???댁떇 遺꾩꽍 ?꾨낫 |
| `notes` | 硫붾え ?먮뒗 ?꾨줈洹몃옩 泥섎갑 | 湲곕줉/怨꾪쉷 ?ㅻ챸 |
| `rpe` | ?대룞 ?⑥쐞 泥닿컧 媛뺣룄 | ?ν썑 媛뺣룄/?뚮났 ?먮떒 |
| `maxReps` | 理쒕? 諛섎났 硫붾え | ?ν썑 ?깃낵 異붿젙 |
| `createdAt` | ?앹꽦 ?쒓컖 | 媛숈? ?좎쭨 ?쒖떆 ?쒖꽌 |
| `completedAt` | confirmed set 議댁옱 ???꾨즺 ?쒓컖 | ?꾨즺 ?곹깭 ?쒖떆 |

?ㅽ룷痢?移댄뀒怨좊━??`weight`, `rpe`, `maxReps` ?낅젰??媛뺤젣?섏? ?딅뒗??

## WorkoutSet

`WorkoutSet`? ???깆뿉??媛??以묒슂???뚯씠釉붿씠??

| ?꾨뱶 | ?ㅼ젣 ?섎? | 遺꾩꽍/湲곕뒫 ?ъ슜 |
| --- | --- | --- |
| `entryId` | ?뚯냽 ?대룞 entry | ?좎쭨/?대룞 臾띠쓬 |
| `setIndex` | ?명듃 ?쒖꽌 | UI ?쒖떆, ??젣 ???ъ젙??|
| `reps` | 諛섎났 ??| 蹂쇰ⅷ 怨꾩궛 |
| `weightKg` | 以묐웾 kg | 蹂쇰ⅷ 怨꾩궛 |
| `seconds` | ?섑뻾 ?쒓컙 | ?좎궛???ㅽ룷痢?湲곕뒫???쒓컙 |
| `confirmed` | 怨꾪쉷/?ㅼ젣 湲곕줉 援щ텇 | false=怨꾪쉷, true=?ㅼ젣 |
| `manualWeight` | ?ъ슜?먭? 以묐웾??吏곸젒 吏?뺥뻽?붿? | ?ν썑 ?먮룞 以묐웾 異붿쿇 援щ텇 |

?명듃 異붽? 洹쒖튃:

```text
留덉?留?set??reps, weightKg, seconds 蹂듭궗
confirmed=false
```

?명듃 ??젣 洹쒖튃:

```text
留덉?留?1媛???젣 湲덉?
??젣 ??setIndex瑜?1遺???ъ젙??
```

## DailyMetric

?좎쭨蹂?而⑤뵒???곗씠?곕떎.

| ?꾨뱶 | ?ㅼ젣 ?섎? | 遺꾩꽍/湲곕뒫 ?ъ슜 |
| --- | --- | --- |
| `date` | ?좎쭨 | ?대룞 湲곕줉 ?좎쭨? ?곌껐 |
| `sleepHours` | ?섎㈃ ?쒓컙 | ?뚮났 ?뚯쫰 |
| `bodyWeightKg` | 泥댁쨷 | 泥댁쨷 蹂?? ?곷? 以묐웾 |
| `updatedAt` | ?섏젙 ?쒓컖 | ?숆린???쒖떆 湲곗? |

Phase 2.5?먯꽌????κ낵 ?쒖떆留??쒕떎. 怨좉툒 ?뚮났 ?먮떒? ?꾩쭅 援ы쁽?섏? ?딅뒗??

## TrainingProgram

湲곕낯 ?먮뒗 ?ъ슜???앹꽦 ?꾨줈洹몃옩???ㅻ뜑??

| ?꾨뱶 | ?ㅼ젣 ?섎? | 遺꾩꽍/湲곕뒫 ?ъ슜 |
| --- | --- | --- |
| `id` | ?꾨줈洹몃옩 ID | item ?곌껐 |
| `name` | ?꾨줈洹몃옩紐?| 紐⑸줉 ?쒖떆, seed 以묐났 諛⑹? |
| `durationDays` | ?꾨줈洹몃옩 湲곌컙 | ?곸슜 踰붿쐞 ?ㅻ챸 |
| `createdAt` | ?앹꽦 ?쒓컖 | 紐⑸줉 ?뺣젹 |

seed refresh??媛숈? 湲곕낯 ?꾨줈洹몃옩紐낆씠 ?덉쑝硫?以묐났 ?쎌엯?섏? ?딅뒗??

## TrainingProgramItem

?꾨줈洹몃옩 ?덉쓽 泥섎갑 ??ぉ?대떎.
?좎쭨???곸슜?섎㈃ `WorkoutEntry`? `WorkoutSet`?쇰줈 蹂듭궗?쒕떎.

| ?꾨뱶 | ?ㅼ젣 ?섎? | ?곸슜 寃곌낵 |
| --- | --- | --- |
| `programId` | ?뚯냽 ?꾨줈洹몃옩 | program ?곌껐 |
| `weekNumber` | 二쇱감 | ?좎쭨 怨꾩궛 |
| `dayOfWeek` | ?붿씪 踰덊샇 | ?좎쭨 怨꾩궛 |
| `orderIndex` | ?붿씪 ???쒖꽌 | entry ?앹꽦 ?쒖꽌 |
| `exerciseId` | ?곌껐 ?대룞 ID | `WorkoutEntry.exerciseId` |
| `exerciseName` | ?대룞紐??ㅻ깄??| `WorkoutEntry.exerciseName` |
| `category` | 移댄뀒怨좊━ ?ㅻ깄??| `WorkoutEntry.category` |
| `restSeconds` | ?댁떇 泥섎갑 | `WorkoutEntry.restSeconds` |
| `prescription` | 泥섎갑 硫붾え | `WorkoutEntry.notes` |
| `setCount` | ?앹꽦???명듃 ??| `WorkoutSet` 媛쒖닔 |
| `reps` | 怨꾪쉷 諛섎났 ??| `WorkoutSet.reps` |
| `weightKg` | 怨꾪쉷 以묐웾 | `WorkoutSet.weightKg` |
| `seconds` | 怨꾪쉷 ?쒓컙 | `WorkoutSet.seconds` |

以묒슂:

```text
TrainingProgramItem -> WorkoutSet 蹂????confirmed=false
```

## Program Apply Conflict

?꾨줈洹몃옩 ?곸슜 ????좎쭨??湲곗〈 湲곕줉???덉쑝硫??ъ슜?먭? ?좏깮?쒕떎.

| ?좏깮 | ?곗씠??泥섎━ |
| --- | --- |
| ??뼱?곌린 | 湲곗〈 set ??젣 ??entry ??젣, ?댄썑 怨꾪쉷 ?앹꽦 |
| 異붽? | 湲곗〈 湲곕줉 蹂댁〈, 怨꾪쉷 異붽? |
| 痍⑥냼 | ?꾨Т 蹂寃??놁쓬 |

??뼱?곌린 ??UI????젣?????덈뒗 湲곗〈 ?꾨즺 ?명듃 ?섎? 蹂댁뿬以??
?대뒗 ?ㅼ젣 ?섑뻾 湲곕줉??吏?????덇린 ?뚮Ц?대떎.

## Analysis Current Mapping

?꾩옱 遺꾩꽍 ?붾㈃? ?⑥닚 ?듦퀎??

| 吏??| 怨꾩궛 |
| --- | --- |
| confirmed set ??| `WorkoutSet.confirmed = true` 媛쒖닔 |
| 珥?蹂쇰ⅷ | confirmed set??`reps * weightKg` ??|
| 珥??쒓컙 | confirmed set??`seconds` ??|

怨꾪쉷 ?명듃???ы븿?섏? ?딅뒗??

## Future Analysis Pipeline

怨좉툒 遺꾩꽍? ?꾩쭅 援ы쁽?섏? ?딅뒗??
?ㅺ퀎 臾몄꽌???ㅼ쓬 ?뚯씪???덈떎.

```text
docs/analysis_algorithm_design.md
```

?ν썑 ?뚯씠?꾨씪??

```text
Raw Data
-> Feature Extraction
-> Signal Generation
-> Judgment
-> Recommendation
-> Narrative Rendering
```

遺꾩꽍 ?뚯쫰:

- General Fitness
- Strength
- Hypertrophy
- Badminton
- Recovery
- Balance / Injury Risk
- Plan Adherence

?쒓뎅??遺꾩꽍 臾몄옣? 吏㏐퀬 議곗떖?ㅻ읇寃??대떎.
?⑥젙 ???媛?μ꽦怨??ㅼ쓬 ?됰룞???쒖떆?쒕떎.

## Update Rules

?곗씠???섎? 蹂寃???諛섎뱶???뺤씤?쒕떎.

- confirmed ?섎?媛 ?좎??섎뒗媛
- ?ъ슜??湲곕줉????젣?섏? ?딅뒗媛
- seed refresh媛 以묐났??留뚮뱾吏 ?딅뒗媛
- 遺꾩꽍??planned set???ㅼ젣 遺?섎줈 蹂댁? ?딅뒗媛
- taxonomy token???쒖떆???쒓뎅?댁? ?욎씠吏 ?딅뒗媛
- 臾몄꽌媛 肄붾뱶? 媛숈? ?섎?瑜?留먰븯?붽?

## Phase 2.6 Record Timer Mapping

Phase 2.6?먯꽌 ?닿쾶 ??대㉧媛 異붽??섏뿀吏留?DB ?섎???諛붾뚯? ?딆븯??

??대㉧ ?몃━嫄?

```text
WorkoutSet.confirmed false -> true
```

???꾪솚? ?ㅼ쓬 ??媛吏 ?섎?瑜??숈떆??媛吏꾨떎.

- ?ㅼ젣 ?섑뻾 湲곕줉?쇰줈 ?뺤젙
- ?대떦 entry??`restSeconds` 湲곗? ?닿쾶 ??대㉧ ?쒖옉 ?꾨낫

??대㉧媛 ?쒖옉?섏? ?딅뒗 寃쎌슦:

- `confirmed=true` ?명듃瑜??ㅼ떆 ?섏젙?섎뒗 寃쎌슦
- `confirmed=true -> false`濡??댁젣?섎뒗 寃쎌슦
- `restSeconds <= 0`??寃쎌슦

??대㉧??DB ?덈젴 遺?섎? 怨꾩궛?섏? ?딅뒗??
?덈젴 遺??怨꾩궛? 怨꾩냽 `confirmed=true` ?명듃留??ъ슜?쒕떎.

### Rest Timer Session Data

?닿쾶 ??대㉧ ?몄뀡 ?곹깭??Room DB媛 ?꾨땲??app preferences????ν븳??

| key | ?섎? |
| --- | --- |
| `rest_end_at` | ?닿쾶 醫낅즺 wall-clock timestamp |
| `rest_next` | ?ㅼ쓬 ?명듃 / ?ㅼ쓬 ?대룞 ?덈궡 |
| `rest_target_date` | 湲곕줉 ??쑝濡??뚯븘媛??좎쭨 |
| `rest_target_entry_id` | 湲곕줉 ??뿉??紐⑺몴 entry |
| `rest_finished` | ??대㉧ 醫낅즺 ?곹깭 |

??媛믩뱾? ?ъ슜???대룞 湲곕줉???꾨땲??
???쒕㈃ ?뚮┝怨??ㅻ쾭?덉씠 蹂듦뎄瑜??꾪븳 UI/session ?곹깭??

### Weight Editing Meaning

Phase 2.6?먯꽌 ?쇨큵 kg? 鍮??명듃 kg ?곸슜??異붽??섏뿀??

?쇨큵 kg:

- 媛숈? `WorkoutEntry`??紐⑤뱺 set `weightKg`瑜??ъ슜?먭? ?낅젰??媛믪쑝濡?蹂寃?
- `manualWeight=true`
- ?ㅽ룷痢?/ ?좎궛??entry?먮뒗 ?몄텧?섏? ?딆쓬

鍮??명듃 kg ?곸슜:

- source set??0蹂대떎 ??kg媛 ?낅젰?????꾨낫 UI ?쒖떆
- ?곸슜 ??곸? `weightKg == 0`, `manualWeight == false`, `confirmed == false`
- confirmed=true ?명듃???먮룞 ??뼱?곗? ?딆쓬

??湲곕뒫? ?낅젰 ?몄쓽 湲곕뒫?대떎.
?대룞 seed, taxonomy, 遺꾩꽍 ?뚭퀬由щ벉 ?섎?瑜?諛붽씀吏 ?딅뒗??

## Phase 2.6 Set RPE / Rest Override Mapping

### WorkoutSet.rpe

`WorkoutSet.rpe`媛 ?명듃蹂?RPE??canonical ?꾩튂??

- null?대㈃ ?대떦 ?명듃??RPE??湲곕줉?섏? ?딆? ?곹깭
- 0~10 踰붿쐞留??좏슚
- ???명듃 異붽? ???댁쟾 ?명듃??RPE瑜??먮룞 蹂듭궗?섏? ?딆쓬
- 湲곗〈 `WorkoutEntry.rpe` 媛믪쓣 migration?먯꽌 ?명듃濡?媛뺤젣 蹂듭궗?섏? ?딆쓬

遺꾩꽍 ?곗꽑?쒖쐞:

1. `WorkoutSet.rpe`
2. ?놁쓣 ?뚮쭔 `WorkoutEntry.rpe`瑜?legacy fallback ?먮뒗 ?대룞 ?꾩껜 泥닿컧?쇰줈 ?ъ슜

RPE/RIR-aware e1RM? `WorkoutSet.rpe` 湲곗??쇰줈 怨꾩궛?댁빞 ?쒕떎.
RPE媛 ?녿뒗 ?명듃??raw reps / weight 湲곕컲 異붿젙留?媛?ν븯??
RPE 7 誘몃쭔 ?명듃??PR-like e1RM 洹쇨굅濡??쏀븯寃?蹂닿굅???쒖쇅?????덈떎.

### WorkoutSet.restSecondsOverride

`WorkoutEntry.restSeconds`???대룞 entry??湲곕낯 ?댁떇?쒓컙?대떎.
`WorkoutSet.restSecondsOverride`???뱀젙 ?명듃留??ㅻⅨ ?댁떇?쒓컙???대떎???덉쇅媛믪씠??

??대㉧ 湲곗?:

```text
effectiveRest = WorkoutSet.restSecondsOverride ?: WorkoutEntry.restSeconds
```

?명듃 ?뺤씤 ????대㉧??effective rest媛 0蹂대떎 ???뚮쭔 ?쒖옉?쒕떎.

### WorkoutEntry.notes

`WorkoutEntry.notes`???먯쑀 硫붾え ?먮뒗 肄붿튂??硫붾え??

???媛?ν븳 ??

- 臾대쫷 ?덉そ 留먮━吏 ?딄린
- ?ㅻ뒛? 媛蹂띻쾶
- ?듭쬆 ?덉쑝硫?以묐떒

??ν븯吏 ?딅뒗 寃?

- 3?명듃 x 10??
- 4?명듃 x 5??@ 80kg
- 30珥?x 5?명듃

?명듃??/ 諛섎났??/ 以묐웾 / ?쒓컙??canonical ?꾩튂??`WorkoutSet`?대떎.
?꾨줈洹몃옩 泥섎갑??援ъ“???먯쿇? `TrainingProgramItem.setCount`, `reps`, `weightKg`, `seconds`??

## Phase 2.7 Calendar Summary Mapping

?붽컙 罹섎┛??summary??UI???뚯깮 ?곗씠?곕떎. DB 湲곕줉 ?섎?瑜?諛붽씀吏 ?딅뒗??

`DailyRecordSummary` ?섎?:

| ?꾨뱶 | ?섎? |
| --- | --- |
| `date` | ?붿빟 ????좎쭨 |
| `confirmedSets` | `confirmed=true` ?명듃 ??|
| `plannedSets` | `confirmed=false` ?명듃 ??|
| `totalVolumeKg` | `confirmed=true` ?명듃??`reps * weightKg` ??|
| `totalSeconds` | `confirmed=true` ?명듃??`seconds` ??|
| `entryCount` | ?좎쭨???대룞 entry ??|
| `categorySummary` | ?좎쭨 ?대룞 category ?붿빟 |
| `bodyPartSummary` | `Exercise.bodyRegion` 湲곕컲 蹂댁“ ?붿빟 ?꾨낫 |

罹섎┛??cell? ?ㅼ젣 ?덈젴?됱쓣 `confirmed=true`濡쒕쭔 ?쒖떆?쒕떎.
`confirmed=false`留??덈뒗 ?좎쭨???ㅼ젣 湲곕줉???꾨땲??`怨꾪쉷`?쇰줈 ?쒖떆?쒕떎.

?꾨즺 ?대룞 ?곷떒 ?뺣젹? UI sorting?대떎.
DB row ?쒖꽌, `createdAt`, `entry id`, `setIndex`瑜?諛붽씀吏 ?딅뒗??

?닿쾶 ??대㉧ row ?쒖떆瑜??꾪빐 `RestTimerState.targetSetId`瑜?異붽??덈떎.
??媛믪? UI/session ?곹깭?대ŉ ?대룞 湲곕줉 ?곗씠?곌? ?꾨땲??

## Phase 2.7.2 Calendar Lifecycle Mapping

?щ젰 lifecycle ?묒뾽? `WorkoutEntry`? `WorkoutSet` row瑜?蹂듭궗 / ?대룞 / ??젣?쒕떎. DB schema??蹂寃쏀븯吏 ?딆븯??

### 怨꾪쉷?쇰줈 蹂듭궗

- ?먮낯 ?좎쭨??entry / set??????좎쭨濡?蹂듭궗
- 紐⑤뱺 蹂듭궗 set? `confirmed=false`
- `completedAt=null`
- DailyMetric? 蹂듭궗?섏? ?딆쓬

### 湲곕줉?곹깭源뚯? 蹂듭궗

- ?먮낯 ?좎쭨??entry / set??????좎쭨濡?蹂듭궗
- set??confirmed ?곹깭瑜?蹂댁〈
- ?꾨즺 set???덉쑝硫?copied entry??`completedAt`???덈줈 怨꾩궛
- DailyMetric? 蹂듭궗?섏? ?딆쓬

### ?대룞

- ?먮낯 entry / set??????좎쭨濡?蹂듭궗
- confirmed ?곹깭 蹂댁〈
- ?댄썑 ?먮낯 ?좎쭨???대룞 entry / set ??젣
- DailyMetric? ?대룞?섏? ?딆쓬

### ??젣

- ?ъ슜?먭? ?뺤씤???좎쭨??`WorkoutSet` ??젣
- ?댄썑 `WorkoutEntry` ??젣
- DailyMetric? ??젣?섏? ?딆쓬

### ?좏깮蹂듭궗

- ?먮낯 ?좎쭨 踰붿쐞瑜?????쒖옉 ?좎쭨遺??媛숈? 湲몄씠濡?蹂듭궗
- 紐⑤뱺 set? `confirmed=false`
- DailyMetric? 蹂듭궗?섏? ?딆쓬

### 異⑸룎 泥섎━

| ?좏깮 | ?섎? |
| --- | --- |
| ??뼱?곌린 | ????좎쭨 / 踰붿쐞??湲곗〈 set ??젣 ??entry ??젣, ?댄썑 ?묒뾽 ?ㅽ뻾 |
| 異붽? | 湲곗〈 ???湲곕줉 蹂댁〈 ???ㅼ뿉 異붽? |
| 痍⑥냼 | ?꾨Т 蹂寃??놁쓬 |

??뼱?곌린? ??젣 ?꾩뿉??湲곗〈 ?꾨즺 ?명듃 ?섎? 蹂댁뿬以??
?ㅼ젣 ?덈젴 遺??吏묎퀎??怨꾩냽 `confirmed=true` ?명듃留??ъ슜?쒕떎.

## Phase 3.0.0 Analysis V3 Data Mapping

V3 analysis input keeps actual records and future plans separate.

| V3 field | Source | Meaning |
| --- | --- | --- |
| `today` | `AnalysisDateProvider.today()` | Device-current analysis date, default `LocalDate.now()` |
| `completedEntriesUntilToday` | `WorkoutEntry.date <= today` + `WorkoutSet.confirmed=true` | Actual performed training load only |
| `plannedEntriesFromTomorrow` | `WorkoutEntry.date > today` + `WorkoutSet.confirmed=false` | Future plan projection only |
| `conditionRecordsUntilToday` | `DailyMetric.date <= today` | Sleep and body weight context |
| `exerciseMetadataMap` | `Exercise` taxonomy columns | Metadata-driven classification source |
| `recentWindow` | `AnalysisWindows.recent7Days` | Today-inclusive recent 7-day window |
| `futureWindow` | `AnalysisWindows.future7Days` | Tomorrow-inclusive future 7-day window |

Metric rules:

- Completed load metrics do not use unconfirmed sets.
- Future plan metrics do not use confirmed completed load.
- Taxonomy metrics use metadata fields, not exercise-name parsing.
- Missing taxonomy values are counted as `UNKNOWN`.
- Candidate metrics are not final judgments.

## Phase 3.1.1 Fatigue Metadata Mapping

New `Exercise` fields support future Today Fatigue / Readiness analysis.

| Field | Meaning |
| --- | --- |
| `compoundType` | compound / isolation / hybrid / drill classification |
| `plane` | sagittal / frontal / transverse / multi-planar |
| `axialLoadLevel` | none / low / moderate / high axial loading |
| `badmintonTransferRoles` | badminton transfer roles such as footwork, reaction, deceleration |
| `fatigueCategories` | fatigue channel tags such as systemic, neural speed, deceleration |
| `adaptiveBaselineGroups` | adaptive baseline buckets for future rolling baselines |
| `recoveryDecayProfile` | minimal / short / medium / long / very long decay profile |
| `systemicLoadWeight` | systemic fatigue contribution, 0.0 to 1.0 |
| `neuralHeavyWeight` | heavy neural fatigue contribution, 0.0 to 1.0 |
| `neuralSpeedWeight` | speed/reactive neural contribution, 0.0 to 1.0 |
| `localLoadWeight` | local muscle fatigue contribution, 0.0 to 1.0 |
| `decelerationWeight` | deceleration fatigue contribution, 0.0 to 1.0 |
| `elasticSscWeight` | elastic SSC fatigue contribution, 0.0 to 1.0 |
| `rotationPowerWeight` | rotation power fatigue contribution, 0.0 to 1.0 |
| `antiRotationWeight` | anti-rotation fatigue contribution, 0.0 to 1.0 |
| `overheadSwingWeight` | overhead/shoulder repetition contribution, 0.0 to 1.0 |
| `gripLoadWeight` | grip/forearm fatigue contribution, 0.0 to 1.0 |

Weight scale:

- `0.0`: nearly no contribution
- `0.25`: low
- `0.5`: medium
- `0.75`: high
- `1.0`: very high

The built-in seed catalog maps all 214 exercises through structured seed columns and passes `MetadataSanityChecker`.

## Phase 3.1.2 Analysis Readiness Mapping

New progress fields:

- `progressMetricType`: chooses estimated 1RM, volume load, reps-at-load, time/distance, max-rep test, quality-based, or excluded progress mode.
- `strengthProgressionGroup`: strength progression grouping.
- `hypertrophyVolumeGroup`: hypertrophy volume grouping.
- `mainLiftGroup`: main lift family.
- `accessoryContributionGroup`: accessory contribution family.
- `estimated1RmEligible`: whether estimated 1RM can be calculated.
- `volumeLoadEligible`: whether volume load is meaningful.

New badminton fields:

- `badmintonTransferStrength`: none/general/supportive/direct.
- `courtMovementTypes`: court movement tags.
- `badmintonSkillTargets`: skill transfer targets.

New balance/safety fields:

- `jointStressTags`
- `stabilityDemandLevel`
- `mobilityDemandLevel`
- `balanceContributionTags`

New inclusion field:

- `analysisEligibility`: multi-value eligibility for fatigue, strength progress, hypertrophy volume, badminton transfer, balance, recovery-only, test-only, or exclusion.

`ExerciseAnalysisMapper` converts these fields plus optional workout records into `AnalysisExerciseFeatures`.

## Phase 3.1.3 Today Readiness Data Mapping

Today Readiness uses actual completed records only.

Input boundary:

| Input | Included? | Reason |
| --- | --- | --- |
| `WorkoutSet.confirmed=true`, `date <= today` | yes | actual completed training |
| `WorkoutSet.confirmed=false`, `date <= today` | no | still planned or unconfirmed |
| `WorkoutSet.confirmed=false`, `date > today` | no | future plan, not today's fatigue |
| `TrainingProgramItem` | no direct use | program items affect readiness only after copied into confirmed workout sets |
| `DailyMetric.sleepHours` | yes, if present | recovery context |
| pain inputs | supported by evaluator, no normal UI field yet | gate, not average score |

Feature extraction:

```text
Exercise + WorkoutEntry + WorkoutSet
-> ExerciseAnalysisMapper
-> AnalysisExerciseFeatures
```

The readiness engine uses structured metadata fields:

- fatigue weights
- fatigue categories
- adaptive baseline groups
- recovery decay profile
- primary and secondary muscles
- badminton transfer strength
- court movement types
- analysis eligibility

It must not classify exercise type by checking exercise-name strings.

Load mapping:

| Output load | Source |
| --- | --- |
| `SYSTEMIC` | `baseDose * systemicLoadWeight` |
| `NEURAL_HEAVY` | `baseDose * neuralHeavyWeight` |
| `NEURAL_SPEED` | `baseDose * neuralSpeedWeight` |
| `LOCAL_MUSCLE` | `baseDose * localLoadWeight` |
| `DECELERATION` | `baseDose * decelerationWeight` |
| `ELASTIC_SSC` | `baseDose * elasticSscWeight` |
| `ROTATION_POWER` | `baseDose * rotationPowerWeight` |
| `ANTI_ROTATION` | `baseDose * antiRotationWeight` |
| `OVERHEAD_REPETITION` | `baseDose * overheadSwingWeight` |
| `GRIP_FOREARM` | `baseDose * gripLoadWeight` |
| `BADMINTON_COURT` | speed + deceleration + elastic + overhead + grip + transfer bonus |

Adaptive baseline meaning:

- tolerance is not a fixed global threshold.
- tolerance is recalculated by category, baseline group, and body part.
- high load by itself does not increase tolerance.
- high load with stable recovery and no pain/performance drop can increase tolerance slightly.
- failed recovery or performance drop blocks tolerance increases.

UI mapping:

- `TodayReadinessSummary.status` drives the Analysis tab readiness label.
- `primaryReasons` explains the top signals.
- `recommendedModes` suggests what to do.
- `restrictedModes` lists what to adjust.
- `detailSections` provide deeper metrics without replacing the simple confirmed-set statistics.

## Phase 3.2.0 Performance Trend Data Mapping

Performance Trend uses completed weekly records only.

Input boundary:

| Source | Use in 3.2 |
| --- | --- |
| `WorkoutSet.confirmed=true` | included |
| `WorkoutSet.confirmed=false` | excluded |
| future planned workout | excluded |
| `Exercise` metadata | classification source |
| `DailyMetric.sleepHours` | fatigue/recovery penalty context |
| `DailyMetric.bodyWeightKg` | bodyweight volume proxy when available |

Weekly unit:

- one data point is one week.
- week start defaults to Monday.
- first-screen charts show 8 weeks by default and 12 weeks when history is available.

Standardized score meaning:

| Value | Meaning |
| --- | --- |
| near `100` | near personal recent baseline |
| above `100` | above recent baseline |
| below `100` | below recent baseline |

This is not a public score grade.
The UI should show trend direction rather than emphasize the number.

Strength performance:

```text
StrengthPerformanceIndex =
0.50 * StrengthIntensityIndex
+ 0.40 * StrengthVolumeIndex
+ 0.10 * StrengthEfficiencyIndex
```

Badminton training:

```text
BadmintonTrainingIndex =
0.60 * CourtVolumeIndex
+ 0.25 * FootworkReactiveIndex
+ 0.15 * BadmintonSupportIndex
```

This is badminton-related training volume, not badminton skill.

Fatigue composite:

```text
FatigueCompositeIndex =
0.60 * AverageStandardizedFatigue
+ 0.25 * MaxCategoryFatigue
+ 0.15 * RecoveryPerformancePenaltyScore
```

This uses 3.1-style standardized pressure, percentile, z-score, and recovery/performance signals.
It is not raw load summation.

Detail chart data:

- trend mode uses standardized weekly line series.
- composition mode uses component contribution shares.
- contribution mode compares recent 4 weeks against previous 4 weeks.
- ranking mode shows top recent contributors.
- relationship mode uses weekly scatter points.

Relationship analysis:

- Pearson correlation requires enough weekly points.
- wording must stay as trend/correlation language.
- it must not claim causality.
## Minor Patch Note: Record Date Switcher Width

This UI-only patch changes the Record tab date navigation button width and date wrapping behavior.

Data meaning impact:

- None.
- `WorkoutSet.confirmed=false` still means planned set.
- `WorkoutSet.confirmed=true` still means completed record set.
- Analysis aggregation rules are unchanged.

## Phase 3.3.0 Badminton Transfer Analysis Mapping

This patch adds `諛곕뱶誘쇳꽩 ?꾩씠 遺꾩꽍`.

Data inputs:

- `WorkoutSet.confirmed=true` sets only
- `WorkoutSet.confirmed=false` planned sets are excluded
- `ExerciseAnalysisMapper` structured metadata
- 3.1 `TodayReadinessSummary` for fatigue-aware recommendation gating

Transfer type mapping:

| Metadata | Transfer Type |
| --- | --- |
| `badmintonTransferStrength=DIRECT` | direct |
| `badmintonTransferStrength=SUPPORTIVE` | supportive |
| `badmintonTransferStrength=GENERAL` | general_strength |
| badminton metadata exists but no stronger type | low |
| no badminton metadata | none |

Transfer axis mapping:

| Axis | Structured metadata source |
| --- | --- |
| `deceleration_landing` | fatigue categories, court movement, force type, transfer roles |
| `unilateral_stability` | laterality, balance tags, lunge reach metadata |
| `lateral_movement` | court movement, footwork roles, movement pattern, plane |
| `rotation_control` | rotation / anti-rotation metadata |
| `lower_body_strength` | squat / hinge / lunge patterns and lower-body baseline groups |
| `racket_support` | overhead, shoulder, grip, forearm metadata |
| `aerobic_footwork` | conditioning, skill, footwork persistence metadata |
| `low_fatigue_control` | prehab, stability, mobility, recovery metadata |

Meaning rules:

- The module reports transfer stimulus distribution, not match-performance improvement.
- The first card shows one recommendation sentence only.
- Detail view may show transfer axis share, transfer type share, 7-day vs 28-day comparison, and Top 5 transfer stimulus exercises.
- Exercise names are display values only; they are not used for classification.

## v0.3.4.0 Backup / Restore Mapping

Home `湲곕줉 諛깆뾽` exports restore-format CSV.

Restore-format rows map as follows:

| CSV | DB |
| --- | --- |
| `row_type=daily`, `date`, `sleep_hours`, `body_weight_kg` | `DailyMetric` upsert |
| `row_type=set`, entry columns | `WorkoutEntry` |
| `set_index`, `set_confirmed`, `reps`, `weight_kg`, `seconds`, `rpe` | `WorkoutSet` |

Confirmed semantics:

- `set_confirmed=0` imports as `WorkoutSet.confirmed=false`.
- `set_confirmed=1` imports as `WorkoutSet.confirmed=true`.
- missing `set_confirmed` falls back to entry-level `confirmed`.

Legacy `daily_timeseries` import:

- `sleep_hours` and `body_weight_kg` map to `DailyMetric`.
- invalid numeric values become empty values and must not crash import.
- daily aggregate totals map to generated category-level `WorkoutEntry` and confirmed `WorkoutSet` rows.
- generated aggregate rows are marked with `WorkoutEntry.notes = "CSV daily_timeseries import"`.

Limit:

- `daily_timeseries` lacks exact exercise and set detail.
- imported aggregate records preserve date-level continuity for Home, Record, Analysis, and recommendation inputs, but they are not exact historical set reconstruction.

## v0.3.4.1 Program Generator Data Meaning

Program definition fields:

| Field | Meaning |
| --- | --- |
| `TrainingProgram.goal` | User-facing program goal used by the skeleton generator. |
| `weeklyTrainingDays` | Planned sessions per week for generated skeletons. |
| `sessionMinutes` | Rough time budget used to cap item count. |
| `availableEquipment` | Comma-separated equipment tokens available to the user. |
| `excludedExerciseText` | User-entered exclusion text. Used only as a direct text filter. |
| `badmintonTransferRatio` | Target share for badminton-transfer-supportive work. |
| `sportStrengthRatio` | UI-selected sport/strength emphasis label such as `70/30`. |
| `periodizationType` | Selected or auto-chosen periodization style. |

Generator mapping:

- Exercise selection is based on structured `Exercise` metadata.
- Metadata sources include movement pattern, training role, badminton transfer strength, fatigue categories, equipment, axial load, and analysis eligibility.
- Exercise names are display values and are not used for classification.
- The only name-based filter is the user's explicit exclusion text.

Weight source mapping:

| Source label | Meaning |
| --- | --- |
| `DIRECT_HISTORY_HIGH` | Recent confirmed same-exercise records support the kg suggestion. |
| `DIRECT_HISTORY_MEDIUM` | Same-exercise history exists, but confidence is lower. |
| `SIMILAR_EXERCISE_LOW` | Similar metadata history supports a conservative kg suggestion. |
| `EMPTY_NEEDS_MANUAL_INPUT` | No reliable kg suggestion; user should enter kg manually. |

Program application mapping:

- `TrainingProgramItem` copies to `WorkoutEntry` plus planned `WorkoutSet` rows.
- Program-created sets always import as `WorkoutSet.confirmed=false`.
- `confirmed=false` continues to mean planned but not yet completed.
- `confirmed=true` continues to mean completed record.
- Program overwrite deletes planned-only rows only.
- Entries containing any confirmed set are preserved.

## v0.3.4.2 Sport Session / Program Candidate Split

Exercise planning fields:

| Field | Meaning |
| --- | --- |
| `Exercise.activityKind` | Distinguishes selectable training exercises from sport sessions, match records, or daily metric rows. |
| `Exercise.planningEligibility` | Declares whether an exercise can become a generated program item. |

Allowed program candidate:

| `activityKind` | `planningEligibility` | Program generator |
| --- | --- | --- |
| `TRAINING_EXERCISE` | `PROGRAM_SELECTABLE` | allowed |
| `SPORT_SESSION` | `FATIGUE_ONLY` | excluded |
| `MATCH_RECORD` | `FATIGUE_ONLY` | excluded |
| any | `ANALYSIS_ONLY` / `HIDDEN` | excluded |

Meaning:

- Sport sessions and match records are actual external load, not planned program exercises.
- They may remain in records and analysis inputs.
- They may affect fatigue, recovery, recent load, and trend signals.
- They must not appear in generated program previews, saved `TrainingProgramItem` rows, or program-applied planned `WorkoutEntry` rows.
- `badmintonTransferRatio` means the share of badminton-transfer-supportive training exercises, not a ratio for adding badminton matches.

## Record Calendar Range Delete Mapping

Long-press calendar `select delete` maps to date-range deletion.

| User choice | DB behavior |
| --- | --- |
| unconfirmed only | delete `WorkoutSet` rows where `confirmed=false` in the selected date range |
| including confirmed | delete all `WorkoutSet` and `WorkoutEntry` rows in the selected date range |

Preservation rules:

- DailyMetric rows are not deleted.
- When only unconfirmed sets are deleted, confirmed sets remain actual training records.
- Remaining sets in an entry are reindexed from `setIndex=1`.
- Empty entries are removed after their last unconfirmed set is deleted.

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


## v0.3.4.3 Exercise Info Button Mapping

Exercise detail display is now an explicit row action.

| UI action | Data displayed |
| --- | --- |
| Exercise row `i` button | image asset, description, rest seconds, muscles, equipment, movement/taxonomy tags |
| Exercise row tap in Exercise tab | no detail selection side effect |
| Exercise picker row tap | still selects that exercise for record/program flows |

No DB schema or metadata meaning changed.

## v0.3.4.4 Data Meaning Addendum

- `InitialUserProfile`: 珥덇린 湲곕줉??遺議깊븳 ?쒓린??readiness 蹂댁젙 ?낅젰媛믪씠?? 遺꾩꽍 寃곌낵瑜?怨좎젙 ??ν븯吏 ?딅뒗??
- `WorkoutSet.confirmed=false`: 怨꾪쉷 ?명듃 ?섎? ?좎?.
- `WorkoutSet.confirmed=true`: ?ㅼ젣 ?섑뻾 湲곕줉 ?섎? ?좎?.
- CSV `row_type=profile`: 珥덇린 ?꾨줈??key/value 諛깆뾽 ?됱씠??
- `Exercise.isActive=false`: ?대룞 ?④? ?곹깭?? 怨쇨굅 湲곕줉 ??젣媛 ?꾨땲??
- `Exercise.isCustom=true`: ?ъ슜??異붽? ?대룞?대떎. ??젣 媛?μ꽦 ?먮떒???ъ슜?쒕떎.

## v0.3.4.4.1 Data Meaning Addendum

- `birthYear`: 1900~?꾩옱?곕룄 踰붿쐞???レ옄 異쒖깮?곕룄.
- `sex`: `MALE`, `FEMALE`, `UNSPECIFIED`.
- `strengthTrainingYears`, `badmintonTrainingYears`: ???⑥쐞 ?レ옄 寃쎈젰.
- `trainingBreakCategory`, `trainingBreakReason`: ?대룞 怨듬갚 援ъ“??enum key.
- `painAreaTags`, `avoidMovementTags`: comma-separated enum key list. `NONE`? ?⑤룆 媛믪씠??
- `primaryGoal`: 紐⑺몴 enum key.
- `freeNote`: 蹂댁“ 硫붾え?대ŉ ?쇰줈??怨꾩궛 ?듭떖媛믪씠 ?꾨땲??

## v0.3.4.4.4 Hotfix Addendum

- Version bumped to `v0.3.4.4.4` / DB version `11`.
- `MIGRATION_10_11` is no-op; structured initial profile fields were already present in DB v10.
- Added `InitialAdaptationProfile` and cold-start readiness baseline binding.
- Removed the old fatigued-only profile adjustment pattern.
- Initial profile now can affect READY/CAUTION/FATIGUED outcomes through baseline blending and final summary adjustment.
- CSV profile restore now sanitizes enum keys, RPE, and 1-5 recovery fields.
- Debug APK backup: `C:\Users\pki08\Documents\Codex\google_drive_backup\3444-TrainingTrackPlanner-v0.3.4.4.4-debug.apk`.

## v0.3.4.5 Data Meaning Addendum

- `WorkoutEntry` can now be deleted as a whole exercise record from today's record UI.
- `WorkoutSet.confirmed=false` last-set deletion may remove the parent entry when no sets remain.
- Rest timer overlay visibility now depends on a valid next exercise target, not only timer activity.
- `AnalysisExerciseFeatures.stableKey` and `activityKind` identify stable exercise semantics for analysis calculators.
- `ExerciseActivityKind.SPORT_SESSION` represents direct sport play such as badminton sessions and lessons.
- `BADMINTON_DIRECT_PLAY` maps to direct shuttle play time, not footwork/reactive drill volume.
- `배드민턴 레슨` is a seeded direct badminton play item.
- `BadmintonTrainingLoadIndexCalculator` separates shuttle play, footwork/reactive work, and badminton support work before index calculation.
- `PerformanceTrendSectionType.STRENGTH` and `PerformanceTrendSectionType.FATIGUE` no longer expose ranking mode in v0.3.4.5.
- Relationship scatter points remain weekly trend points and must be described as correlation/tendency only.
- Version facts: app `v0.3.4.5`, version code `304045`, Room DB `12`, exercise seed version `6`.
