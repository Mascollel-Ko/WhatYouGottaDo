# Level 1 한국어 reference registry 초안

- 상태: `DRAFT`
- 기준 commit: `40615fab9c7ff892b0e48dd5a244eeb77e7cf2ee`
- 기계 판독 자산: `metadata_level1_korean_reference_registry_draft.csv`

이 초안은 `MovementPatternRef`, `JointComplexRef`, `JointActionRef`,
`MovementEventRef`, `MovementPhaseRef`, `MovementPlaneRef`, `LateralityRef`,
`EquipmentRef`만 다룹니다. 각 행은 수기 한국어·영어 label과 한국어 정의 및
논리 질문을 갖습니다. 영문 code를 underscore로 나눠 자동 번역하지 않습니다.

모든 행은 아직 `REVIEW_REQUIRED`입니다. 이 파일은 production display catalog나
Room 값을 변경하지 않으며 Phase 2B 승인 전 `REVIEWED_V1` 관계로 승격되지 않습니다.

Legacy alias는 현재 저장값의 round-trip을 위한 검토 단서일 뿐 canonical code와
동일하다고 자동 가정하지 않습니다. 특히 복합 장비 문자열은 향후
`ExerciseEquipmentRequirement`의 `REQUIRED`, `OPTIONAL`, `ALTERNATIVE` 관계로
분리해야 합니다.
