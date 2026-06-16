# Initial Profile Schema Migration Report

## 변경

- DB version: 9 -> 10
- schema export: `app/schemas/com.training.trackplanner.data.TrainingDatabase/10.json`
- migration: `MIGRATION_9_10`

## 추가 필드

- `birthYear`
- `sex`
- `strengthTrainingYears`
- `badmintonTrainingYears`
- `trainingBreakCategory`
- `trainingBreakReason`
- `squatKg`
- `deadliftKg`
- `benchPressKg`
- `pullUpMaxReps`
- `pullUpAddedWeightKg`
- `usualSleepHours`
- `currentCondition`
- `painAreaTags`
- `avoidMovementTags`
- `primaryGoal`
- `secondaryGoalTags`
- `freeNote`

## migration 정책

- 기존 자유 텍스트 필드는 삭제하지 않는다.
- `gender`는 가능한 경우 `sex`로 변환한다.
- 4자리 숫자 `birthYearOrAgeRange`는 `birthYear`로 변환한다.
- `breakWeeks`와 `breakDueToPain`은 구조화된 공백 category/reason으로 변환한다.
- 해석 불가능한 값은 null/default로 둔다.

## 테스트

- `assembleDebugAndroidTest`로 migration test APK 컴파일 성공.
- `migrate9To10AddsStructuredInitialProfileFields` 테스트를 추가했다.
