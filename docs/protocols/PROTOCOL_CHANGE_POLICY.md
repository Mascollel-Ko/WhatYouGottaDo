# Protocol Change Policy

## A. Editorial patch

맞춤법, 링크, 의미를 바꾸지 않는 문장 명료화입니다. 수치·분류·fallback·runtime 동작을 바꾸지 않으며 protocol patch version을 올릴 수 있습니다.

## B. Clarification

기존 동작을 더 명시적으로 설명합니다. runtime 변화가 없고 source/test 감사로 동일성을 확인합니다. 보통 protocol patch version입니다.

## C. Behavioral protocol change

계수, threshold, taxonomy, scoring, mapping, recovery, aggregation 또는 fallback을 바꿉니다. protocol minor 또는 major version, source 변경, tests, release notes, canonical docs 갱신이 모두 필요합니다.

## D. Breaking protocol change

historical interpretation, 저장 형식, event identity 또는 호환성을 바꿉니다. protocol major version, migration, 명시적 release planning이 필요합니다.

## Semantic version guidance

- `PATCH`: 의미가 같은 editorial/clarification.
- `MINOR`: backward-compatible behavioral addition 또는 범위 확장.
- `MAJOR`: 기존 기록의 해석, identity, storage 또는 호환성을 깨는 변경.

앱 버전과 protocol version은 독립적입니다. Protocol version 상승은 과학적 타당성이나 임상적 검증을 보증하지 않습니다.

