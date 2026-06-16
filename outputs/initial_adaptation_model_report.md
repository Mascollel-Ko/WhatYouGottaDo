# Initial Adaptation Model Report

## Model

`InitialAdaptationProfileCalculator` converts `InitialUserProfile` into `InitialAdaptationProfile`.

| 입력값 | 내부 변환값 | 계산 사용 위치 | 최종 판정 영향 |
| --- | --- | --- | --- |
| `strengthTrainingYears` | `resistanceAdaptationScore` | cold-start resistance baseline | 저항 부하 과대평가 방지 또는 보수화 |
| `strengthSessionsPerWeek`, `strengthMinutesPerSession`, `strengthAverageRpe` | `resistanceAdaptationScore` | cold-start resistance baseline | 최근 저항 자극 적응 반영 |
| `squatKg`, `deadliftKg`, `benchPressKg`, `pullUpMaxReps`, `pullUpAddedWeightKg` | `resistanceAdaptationScore` | cold-start resistance baseline | 대표 근력 수준을 보조 반영 |
| `badmintonTrainingYears` | `badmintonAdaptationScore` | court-load baseline | 코트 피로 기준선 조정 |
| `badmintonSessionsPerWeek`, `badmintonMinutesPerSession`, `badmintonAverageRpe` | `badmintonAdaptationScore` | court-load baseline | 최근 코트 수행 적응 반영 |
| strength/badminton weekly volume | `activityAdaptationScore` | systemic/local baseline | 일반 활동량 기준선 조정 |
| `usualSleepHours`, `sleepQuality`, `currentFatigue`, `currentSoreness`, `currentStress`, `currentCondition` | `recoveryCapacityScore` | all cold-start baselines | 회복 여력에 따라 보수/완화 |
| `trainingBreakCategory`, `trainingBreakReason` | `detrainingModifier` | all adaptation scores | 공백 후 기준선 감산 |
| `painAreaTags` | `restrictionProfile` | category/group/body-part sensitivity | 통증 관련 자극 보수화 |
| `avoidMovementTags` | `restrictionProfile` | category/group/body-part sensitivity | 회피 움직임 관련 자극 보수화 |
| `primaryGoal` | `goalSensitivityProfile` | domain-specific sensitivity | 목표별 피로 민감도 조정 |

## Score Direction

- Higher adaptation score raises cold-start tolerance for that domain.
- Lower recovery or recent detraining lowers tolerance.
- Pain and avoid tags do not create diagnosis. They only make matching stimuli more conservative.
- The model is not displayed in normal UI.
