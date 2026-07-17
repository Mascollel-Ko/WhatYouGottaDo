# WhatYouGottaDo Documentation

이 디렉터리는 문서의 목적과 권위 수준을 구분합니다.

- `docs/protocols/`: 현재 제품 프로토콜의 canonical specification과 구현 감사 상태입니다.
- `docs/proposals/`: 아직 승인되거나 구현되지 않은 변경 제안입니다.
- `docs/archive/`: 보존이 필요한 역사 문서를 향후 이동할 수 있는 위치입니다. 현재 작업은 기존 문서를 이동하지 않습니다.
- `docs/*release_notes.md`: 앱 버전별 변경 기록이며 현재 프로토콜의 단일 권위가 아닙니다.
- `docs/*worklog.md`, `docs/codex_worklog.md`: 작업 과정과 검증 기록입니다.
- 그 밖의 설계·연구·감사·handoff 문서는 supporting 또는 historical 자료입니다.

현재 동작을 확인할 때는 [Protocol Library](protocols/README.md)를 먼저 읽고, registry가 가리키는 source/test/authority anchor로 런타임을 교차 확인합니다.

