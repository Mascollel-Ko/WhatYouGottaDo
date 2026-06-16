# Initial Profile Backup / Restore Hotfix Report

## 변경

- CSV profile row export에 새 구조화 key를 추가했다.
- import 시 새 key를 우선 사용한다.
- v0.3.4.4의 legacy key도 crash 없이 읽는다.

## 새 export key 예

- `birthYear`
- `sex`
- `strengthTrainingYears`
- `badmintonTrainingYears`
- `trainingBreakCategory`
- `trainingBreakReason`
- `painAreaTags`
- `avoidMovementTags`
- `primaryGoal`
- `freeNote`

## legacy 호환

- `gender`: 가능한 경우 `MALE/FEMALE/UNSPECIFIED`로 변환
- `birthYearOrAgeRange`: 4자리 숫자만 `birthYear`로 변환
- `strengthTrainingAge`, `badmintonTrainingAge`: 숫자 추출 가능한 경우만 years로 변환
- `painAreas`, `avoidedMovements`: 구조화 tags가 없으면 `OTHER`로 보수 변환
- `goals`: 일부 키워드만 `primaryGoal`로 변환, 실패 시 `MIXED`

## 테스트

- `restoreCsvExportsAndParsesInitialProfileRows` unit test를 구조화 key 기준으로 보강했다.
