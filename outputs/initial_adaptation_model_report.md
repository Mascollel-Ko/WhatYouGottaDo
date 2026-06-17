# Initial Adaptation Model Report

## Model

`InitialAdaptationProfileCalculator` converts `InitialUserProfile` into `InitialAdaptationProfile`.

| ?낅젰媛?| ?대? 蹂?섍컪 | 怨꾩궛 ?ъ슜 ?꾩튂 | 理쒖쥌 ?먯젙 ?곹뼢 |
| --- | --- | --- | --- |
| `strengthTrainingYears` | `resistanceAdaptationScore` | cold-start resistance baseline | ???遺??怨쇰??됯? 諛⑹? ?먮뒗 蹂댁닔??|
| `strengthSessionsPerWeek`, `strengthMinutesPerSession`, `strengthAverageRpe` | `resistanceAdaptationScore` | cold-start resistance baseline | 理쒓렐 ????먭레 ?곸쓳 諛섏쁺 |
| `squatKg`, `deadliftKg`, `benchPressKg`, `pullUpMaxReps`, `pullUpAddedWeightKg` | `resistanceAdaptationScore` | cold-start resistance baseline | ???洹쇰젰 ?섏???蹂댁“ 諛섏쁺 |
| `badmintonTrainingYears` | `badmintonAdaptationScore` | court-load baseline | 肄뷀듃 ?쇰줈 湲곗???議곗젙 |
| `badmintonSessionsPerWeek`, `badmintonMinutesPerSession`, `badmintonAverageRpe` | `badmintonAdaptationScore` | court-load baseline | 理쒓렐 肄뷀듃 ?섑뻾 ?곸쓳 諛섏쁺 |
| strength/badminton weekly volume | `activityAdaptationScore` | systemic/local baseline | ?쇰컲 ?쒕룞??湲곗???議곗젙 |
| `usualSleepHours`, `sleepQuality`, `currentFatigue`, `currentSoreness`, `currentStress`, `currentCondition` | `recoveryCapacityScore` | all cold-start baselines | ?뚮났 ?щ젰???곕씪 蹂댁닔/?꾪솕 |
| `trainingBreakCategory`, `trainingBreakReason` | `detrainingModifier` | all adaptation scores | 怨듬갚 ??湲곗???媛먯궛 |
| `painAreaTags` | `restrictionProfile` | category/group/body-part sensitivity | ?듭쬆 愿???먭레 蹂댁닔??|
| `avoidMovementTags` | `restrictionProfile` | category/group/body-part sensitivity | ?뚰뵾 ?吏곸엫 愿???먭레 蹂댁닔??|
| `primaryGoal` | `goalSensitivityProfile` | domain-specific sensitivity | 紐⑺몴蹂??쇰줈 誘쇨컧??議곗젙 |

## Score Direction

- Higher adaptation score raises cold-start tolerance for that domain.
- Lower recovery or recent detraining lowers tolerance.
- Pain and avoid tags do not create diagnosis. They only make matching stimuli more conservative.
- The model is not displayed in normal UI.