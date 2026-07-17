# Implementation Status

- `IMPLEMENTED`: 현재 공개 runtime 진입점이 문서 계약을 실행하고 대응 테스트가 있습니다.
- `PARTIALLY_IMPLEMENTED`: 계약 일부만 runtime에 연결되었고 `knownGaps`가 필수입니다.
- `SPECIFICATION_ONLY`: 문서 또는 비활성 코드가 있으나 현재 공개 runtime 진입점은 실행하지 않습니다.
- `NOT_APPLICABLE`: 구현 상태가 해당 문서 목적에 적용되지 않습니다.
- `UNKNOWN_PENDING_AUDIT`: 사실을 확인할 source/test/asset 감사가 아직 끝나지 않았습니다.

문서와 runtime이 다르면 canonical 문서에 specification status, runtime status, gap, last audited commit을 함께 기록합니다. `IMPLEMENTED`인데 required term이 neutral/absent이면 상태를 낮추거나 gap을 등록해야 합니다.

## Generated component lifecycle

Runtime protocol 내부의 비활성 generated component는 protocol 전체의 `implementationStatus`와 별도로 다음 lifecycle을 기록합니다.

- `DESIGNED`: 입력, 출력, authority와 downstream 계약이 정의되었습니다.
- `GENERATED`: deterministic canonical artifact가 repository에 생성되었습니다.
- `VALIDATED`: coverage, parity, ordering, checksums와 drift 검사가 통과했습니다.
- `NOT_YET_RUNTIME_ACTIVE`: artifact 또는 pure evaluator가 존재하지만 공개 UI/classifier/service caller가 아직 사용하지 않습니다.

`GENERATED`나 `VALIDATED`를 runtime activation으로 해석하지 않습니다. `CT-PERSONAL-CALIBRATION`의 Phase 1 BasePrior가 이 상태 조합을 사용합니다.
