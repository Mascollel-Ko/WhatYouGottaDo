# Initial Profile Structured Input Report

## 수정 파일

- `app/src/main/java/com/training/trackplanner/InitialProfileDialog.kt`
- `app/src/main/java/com/training/trackplanner/data/Entities.kt`

## 제거한 핵심 자유 텍스트 입력

- 출생연도/나이
- 성별
- 근력운동 경력
- 배드민턴 경력
- 운동 공백 이유
- 통증/주의 부위
- 피하고 싶은 움직임
- 주요 목표

## 새 입력 방식

- 출생연도: 숫자, 1900년부터 현재 연도까지
- 성별: `MALE`, `FEMALE`, `UNSPECIFIED`
- 운동 경력: 년 단위 숫자
- 최근 4주 RPE: 1~10 chip 선택
- 수면 질/피로/근육통/스트레스/컨디션: 1~5 chip 선택
- 운동 공백: enum chip
- 통증/주의 부위: multi-select enum tag
- 피하고 싶은 움직임: multi-select enum tag
- 목표: enum chip

## 남긴 자유 텍스트

- `freeNote`만 남겼다.
- `freeNote`는 피로도 계산의 핵심 입력으로 사용하지 않는다.
