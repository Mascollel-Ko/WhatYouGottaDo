# CONTROL 19건의 실제 병목 감사

## 결론

현재 생성기의 가장 큰 정체 원인은 **B8에 Mixed Strength + Task scope가 없어서가 아니다.** CONTROL 19건 중 “각 material이 이미 독립적으로 정당한데 Strength와 승인 Task가 같이 있다는 이유만으로” 막힌 사례는 **0건**이다.

우선 확인된 것은 더 앞단의 authority/material 일관성 문제다. 실제 EXP에는 **B6가 `NO_EXECUTABLE_AUTHORIZATION`이라고 판단한 Quality owner-week row 22개가 11개 사례에서 추가**되어 있다. 동시에 일부 CONTROL owner 교체는 B11에서 `CANONICAL_REPLACEMENT`로 식별되지만 B7에는 설명되지 않은 제거로 남는다. 이 상태에서 scope를 더 열면 원인을 해결하지 않고 안전문만 넓힌다.

## C25 merge와 고정 baseline

PR #17의 최종 head는 `3d571ce768df1897e488015e298623c47c3dcef9`이며 `2026-10-06T05:12:34Z`에 merge됐다. Merge SHA와 새 main HEAD는 `89b454db30444a313afd58f147b6e9378594f27c`다. Merged-main Hosted CI run [37417396504](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37417396504)는 protocol validation, Community/Cloud contracts, whitespace, unit tests, production coverage, APK assembly, signer validation, artifact upload 모두 성공했다.

C25 baseline은 유지됐다: Protocol `3.60.0`, Runtime `RECORD_BASED_PLANNER_0.15.2_KOTLIN_1`, App `0.5.1.5`, Room `38`, program backup schema `4`, restore schema `13`, Community snapshot schema `2`. Routes는 CONTROL 19 / Strength V1 1 / Strength Calibration 2 / Hypertrophy 0 / Combined 0이다. B7은 provenance 11 / unmet 9 / regressed 1 / collateral regression 0이다. C20는 HARD_VALID 12 / HARD_INVALID 0 / UNRESOLVED 20, invalid/unresolved 강제 보존 0이다. Power authority/B6/material은 모두 0이며 JUMP_LANDING protocol/material도 0이다. Build accounting은 case당 CONTROL 1, EXPERIMENTAL 1, TOTAL 2, THIRD 0이다.

C24의 승인 Task 프로토콜도 보존됐다. 실제 corpus의 Task material은 `persona3_recent`에만 있으며 8행이다. C25의 전용 pure-task Room/service fixture는 B8 `BADMINTON_TASK_V1` authorization과 B9 EXP route 및 CONTROL rollback을 통과하지만, real corpus에는 순수 Task-only route 후보가 0건이다.

## 19개 CONTROL 사례 전수

이 표는 실제 Room/service corpus에서 B4→B5→B6→EXP/CONTROL 비교→B7→material scope/B8→B9 순서로 수집했다. CONTROL은 비교 근거일 뿐 canonical authority로 사용하지 않았다. 각 row의 정확한 주/주차별 prescription·placement before/after 및 typed evidence는 [machine census](next-phase-bottleneck-census.json)에 들어 있다.


### persona0_mixed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`; removed owners 없음.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY`, `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=2; added [`ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [없음]; shared changed [`ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_COLD_START_USER_CALIBRATION` / `REQUIRES_USER_LOAD_INPUT` (`COLD_START_EXACT_OWNER_HISTORY_ABSENT`, `COLD_START_LOAD_USER_CALIBRATION_REQUIRED`, `COLD_START_STRENGTH_6_REP_CALIBRATION`, `COLD_START_TARGET_RPE_ENCODED`). Removed-owner 판정: 제거 owner 없음.


### persona0_recent

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, ORDER_CHANGE=4, REMOVED_OWNER=2; added [`barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`]; shared changed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`). Removed-owner 판정: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona0_reviewed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY`, `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=4, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [`barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`].

- Exact B6: QUALITY:STRENGTH `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_COLD_START_USER_CALIBRATION` / `REQUIRES_USER_LOAD_INPUT` (`COLD_START_EXACT_OWNER_HISTORY_ABSENT`, `COLD_START_LOAD_USER_CALIBRATION_REQUIRED`, `COLD_START_STRENGTH_6_REP_CALIBRATION`, `COLD_START_TARGET_RPE_ENCODED`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona0_sparse

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`; removed owners 없음.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=6, PLACEMENT_CHANGE=2; added [`ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [없음]; shared changed [`cable_rear_delt_fly#COVERAGE_UPPER_PULL`].

- Exact B6: QUALITY:STRENGTH `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`). Removed-owner 판정: 제거 owner 없음.


### persona1_mixed

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:HYPERTROPHY`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, ORDER_CHANGE=2, PLACEMENT_CHANGE=10, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [`barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `cable_rear_delt_fly#STYLE_MODERATE_HORIZONTAL_PULL`, `cable_rear_delt_fly#STYLE_VOLUME_HORIZONTAL_PULL`, `dumbbell_goblet_squat#COVERAGE_LOWER_KNEE`, `ex_5ca7133f#COVERAGE_CALVES`].

- Exact B6: QUALITY:HYPERTROPHY `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`: `NO_EXECUTABLE_AUTHORIZATION` / `CONDITIONAL_ON_UNPERSISTED_EFFORT` (`PLANNED_RESISTANCE_LOAD_UNAVAILABLE`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona1_recent

- B7: `NOT_ELIGIBLE`; reason `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: 없음; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=8, REMOVED_OWNER=2; added [`cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`]; removed [`cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`]; shared changed [`cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_3d5719de#COVERAGE_ARMS_BICEPS`, `standing_bodyweight_calf_raise#COVERAGE_CALVES`].

- Exact B6: QUALITY:HYPERTROPHY `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`: `NO_EXECUTABLE_AUTHORIZATION` / `CONDITIONAL_ON_UNPERSISTED_EFFORT` (`HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE`). Removed-owner 판정: `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona1_reviewed

- B7: `NOT_ELIGIBLE`; reason `TARGET_REGRESSED`. 직접 unmet: 없음; regression: `QUALITY:HYPERTROPHY`; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=6, REMOVED_OWNER=2; added [`cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`]; removed [`cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`]; shared changed [`barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:HYPERTROPHY `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`: `AUTHORIZED_EXISTING_COMPATIBLE` / `FULLY_ENCODED` (`HYPERTROPHY_PRESCRIPTION_ALREADY_COMPATIBLE`). Removed-owner 판정: `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona1_sparse

- B7: `NOT_ELIGIBLE`; reason `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: 없음; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=14, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`, `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_goblet_squat#COVERAGE_LOWER_KNEE`, `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_5ca7133f#COVERAGE_CALVES`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [없음].

- Exact B6: QUALITY:HYPERTROPHY `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`: `NO_EXECUTABLE_AUTHORIZATION` / `CONDITIONAL_ON_UNPERSISTED_EFFORT` (`HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona2_mixed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, ORDER_CHANGE=4, PLACEMENT_CHANGE=8, REMOVED_OWNER=2; added [`barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`]; shared changed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`, `barbell_deadlift#COVERAGE_POSTERIOR_CHAIN`, `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`, `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_EXISTING_COMPATIBLE` / `FULLY_ENCODED` (`EXISTING_COMPATIBLE_PRESCRIPTION`). Removed-owner 판정: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona2_recent

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, ORDER_CHANGE=2, PLACEMENT_CHANGE=12, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [`barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`, `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`, `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_5ca7133f#COVERAGE_CALVES`].

- Exact B6: QUALITY:STRENGTH `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona2_sparse

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`; removed owners 없음.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=14; added [`barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`, `barbell_deadlift#COVERAGE_POSTERIOR_CHAIN`, `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`, `cable_rear_delt_fly#COVERAGE_UPPER_PULL`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_5c8751d2#COVERAGE_CALVES`]; removed [없음]; shared changed [없음].

- Exact B6: QUALITY:STRENGTH `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`). Removed-owner 판정: 제거 owner 없음.


### persona3_recent

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `MATERIAL_SCOPE_MIXED_OR_UNSUPPORTED`, `REMOVED_OWNER_CHANGE_PRESENT`, `UNSUPPORTED_TARGET_COMBINATION`; removed owners `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`, `ex_33841b88#BADMINTON_OBJECTIVE_`, `ex_421ba24b#BADMINTON_OBJECTIVE_`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=6, PLACEMENT_CHANGE=6, REMOVED_OWNER=6; added [`barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`, `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION`, `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH`]; removed [`barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`, `ex_33841b88#BADMINTON_OBJECTIVE_`, `ex_421ba24b#BADMINTON_OBJECTIVE_`]; shared changed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`, `barbell_deadlift#COVERAGE_POSTERIOR_CHAIN`, `cable_rear_delt_fly#COVERAGE_UPPER_PULL`].

- Exact B6: QUALITY:STRENGTH `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`); Task B6 `BADMINTON_LATERAL_SHUTTLE_LUNGE_V1` `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH`, status `AUTHORIZED_APPROVED_TASK_PROTOCOL`, provenance `USER_APPROVED_PROJECT_POLICY`, tasks `DECELERATION, LUNGE_REACH` (4 material rows); Task B6 `BADMINTON_SIX_CORNER_FOOTWORK_V1` `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION`, status `AUTHORIZED_APPROVED_TASK_PROTOCOL`, provenance `USER_APPROVED_PROJECT_POLICY`, tasks `ACCELERATION, DECELERATION, FOOTWORK, REACTION` (4 material rows). Removed-owner 판정: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`; `ex_33841b88#BADMINTON_OBJECTIVE_`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`; `ex_421ba24b#BADMINTON_OBJECTIVE_`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### persona3_reviewed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY`, `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=6, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [`barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_COLD_START_USER_CALIBRATION` / `REQUIRES_USER_LOAD_INPUT` (`COLD_START_EXACT_OWNER_HISTORY_ABSENT`, `COLD_START_LOAD_USER_CALIBRATION_REQUIRED`, `COLD_START_STRENGTH_6_REP_CALIBRATION`, `COLD_START_TARGET_RPE_ENCODED`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona3_sparse

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`; removed owners 없음.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=6; added [`ex_1dbee10e#COVERAGE_HORIZONTAL_PUSH`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [없음]; shared changed [없음].

- Exact B6: QUALITY:STRENGTH `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`). Removed-owner 판정: 제거 owner 없음.


### persona4_mixed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY`, `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=8, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [`barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_COLD_START_USER_CALIBRATION` / `REQUIRES_USER_LOAD_INPUT` (`COLD_START_EXACT_OWNER_HISTORY_ABSENT`, `COLD_START_LOAD_USER_CALIBRATION_REQUIRED`, `COLD_START_STRENGTH_6_REP_CALIBRATION`, `COLD_START_TARGET_RPE_ENCODED`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona4_recent

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `STRENGTH_V1` / `RESOLVED_STRENGTH`; reasons 없음; removed owners 없음.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2; added [`ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [없음]; shared changed [없음].

- Exact B6: QUALITY:STRENGTH `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`). Removed-owner 판정: 제거 owner 없음.


### persona4_reviewed

- B7: `ELIGIBLE`; reason 없음. 직접 unmet: 없음; regression: 없음; provenance closed `True`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY`, `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED`, `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION`, `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION`, `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=6, REMOVED_OWNER=2; added [`barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`]; removed [`barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`]; shared changed [`barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `cable_overhead_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`].

- Exact B6: QUALITY:STRENGTH `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `AUTHORIZED_EXISTING_COMPATIBLE` / `FULLY_ENCODED` (`EXISTING_COMPATIBLE_PRESCRIPTION`). Removed-owner 판정: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


### persona4_sparse

- B7: `NOT_ELIGIBLE`; reason `AFFECTED_TARGET_REMAINS_UNMET`, `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: `QUALITY:STRENGTH`; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=14, REMOVED_OWNER=2; added [`barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`, `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`, `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`, `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`, `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`, `ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_5ca7133f#COVERAGE_CALVES`]; removed [`barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`]; shared changed [없음].

- Exact B6: QUALITY:STRENGTH `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`: `NO_EXECUTABLE_AUTHORIZATION` / `FULLY_ENCODED` (`B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`). Removed-owner 판정: `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`: B11 `CANONICAL_REPLACEMENT`, B7 closure `False`.


### reviewed_hypertrophy_isolated

- B7: `NOT_ELIGIBLE`; reason `CHANGE_PROVENANCE_UNCLOSED`. 직접 unmet: 없음; regression: 없음; provenance closed `False`; collateral regression free `True`.

- Material scope: `None` / `PARTIAL_PROVENANCE`; reasons `MATERIAL_PROVENANCE_PARTIAL`, `REMOVED_OWNER_CHANGE_PRESENT`; removed owners `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`.

- B8: scope `STRENGTH_V1`, status `CONTROL_REQUIRED`, reasons `B8_B7_NOT_ELIGIBLE`. B9: route `CONTROL`, reasons `B9_B8_CONTROL_REQUIRED`.

- 실제 owner 변경: ADDED_OWNER=2, PLACEMENT_CHANGE=2, PRESCRIPTION_CHANGE=2, REMOVED_OWNER=2; added [`cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`]; removed [`cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`]; shared changed [`ex_28347c1f#COVERAGE_CORE_DIRECT`, `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`].

- Exact B6: QUALITY:HYPERTROPHY `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`: `AUTHORIZED_EXISTING_COMPATIBLE` / `FULLY_ENCODED` (`HYPERTROPHY_PRESCRIPTION_ALREADY_COMPATIBLE`). Removed-owner 판정: `cable_rear_delt_fly#STYLE_MEDIUM_HORIZONTAL_PULL`: B11 `CANONICAL_REPLACEMENT`, B7 closure `True`.


## 원인군과 우선순위

실제 결과는 다음과 같다.

| 원인군 | 사례 수 | 실제 의미 |
|---|---:|---|
| B7에서 직접 target 미충족 | 9 | Strength 8건, Hypertrophy 1건. 해당 target의 정확한 numeric/execution authority가 없거나 로드 입력이 해결되지 않았다. |
| B7 provenance 미종결 | 11 | 22 unexplained added identity, 9 unexplained removed identity, 1 prescription-change attribution. 원인 종류별 case 수는 겹친다. |
| 직접 target regression | 1 | `persona1_reviewed`의 `QUALITY:HYPERTROPHY`; 12→15 weekly direct units로 이미 초과한 상한에서 더 멀어졌다. |
| B7은 통과했지만 B8에서 거부 | 6 | 4개 cold-start는 사용자 load input이 필요하고, 나머지 2개도 residual removal/placement/prescription 변화가 남는다. |
| Strength + 승인 Task 혼합만이 blocker | **0** | Mixed scope 부재만으로 설명되는 CONTROL 사례가 없다. |

이 수치는 중복 가산하지 않는다. 9 unmet, 11 provenance, 1 regression은 서로 다른 B7 reason의 case count이며, 일부 사례는 두 reason을 동시에 가진다. 그 결과 B7에서 멈춘 CONTROL은 13건이고, B7을 통과했지만 B8에서 멈춘 CONTROL은 6건이다.

### `CHANGE_PROVENANCE_UNCLOSED = 11`

B7의 11은 세 attribution 유형으로 나뉜다. occurrence 수와 영향을 받은 case 수를 분리했다.

| Unclosed attribution | Occurrences | Cases | Exact contents |
|---|---:|---:|---|
| `UNEXPLAINED_ADDED_IDENTITY` | 22 | 5 | 다섯 sparse 사례에 coverage/support owner가 들어왔으나 B7 attribution에 근거가 없다. |
| `UNEXPLAINED_REMOVED_IDENTITY` | 9 | 7 | 아래 9개 exact owner-role 제거. B11은 모두 `CANONICAL_REPLACEMENT`라고 분류한다. |
| `UNEXPLAINED_PRESCRIPTION_CHANGE` | 1 | 1 | `reviewed_hypertrophy_isolated`: `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`, 두 주 모두 3→2 sets. |

Added owner 22건은 `persona0_sparse`(2), `persona1_sparse`(6), `persona2_sparse`(6), `persona3_sparse`(2), `persona4_sparse`(6)에 있다. exact key와 role 전체는 census의 `unclosedAttributions` 및 `materialDeltas`에서 확인할 수 있다. 9개 제거 identity는 `persona0_recent` squat style role 1, `persona1_mixed` bench coverage 1, `persona1_recent` rear-delt style 1, `persona1_sparse` bench coverage 1, `persona2_recent` bench coverage 1, `persona3_recent` squat style 1 + 두 `BADMINTON_OBJECTIVE_` task role 2, `persona4_sparse` bench coverage 1이다.

B11의 `CANONICAL_REPLACEMENT`만으로 제거를 승인하지는 않는다. B7의 exact replacement 처리 코드를 따라가면 품질 owner 제거는 대체 owner의 exact B5와 executable B6가 있어야 닫힌다. 이 9개 중 7개 quality replacement는 B6가 실제로 거절한다: 2개 posterior reference unavailable, 2개 B4 numeric authority does not authorize B6, 2개 hypertrophy target numeric authority unavailable, 1개 planned resistance load unavailable. 따라서 이 7개는 provenance 문자열만 연결해 닫으면 안 된다.

남은 2개는 `persona3_recent`의 `ex_33841b88#BADMINTON_OBJECTIVE_`와 `ex_421ba24b#BADMINTON_OBJECTIVE_`다. B11은 각 exact task replacement를 SELECTED로 확인했고, 새 task role에는 `USER_APPROVED_PROJECT_POLICY`의 exact Task B6가 실제로 물질화돼 있다. 하지만 C10의 제거 대체 경로는 quality `StimulusPrescriptionAuthorization`만 검사하고 이 Task protocol authority를 읽지 않아 두 제거가 B7에서 계속 unexplained다. 기존 typed authority를 B7에 연결하지 못한 좁은 구현 gap이다. 두 제거만 닫아도 persona3의 Strength unmet와 squat replacement blocker가 남아 route되지는 않는다.

### `AFFECTED_TARGET_REMAINS_UNMET = 9`

직접 영향을 받은 unmet target은 `QUALITY:STRENGTH` 8건과 `QUALITY:HYPERTROPHY` 1건이다. Task/Power/JUMP_LANDING은 이 B7 reason의 직접 affected target이 아니다.

| Target 및 cases | Actual missing evidence |
|---|---|
| Strength: `persona0_recent`, `persona2_sparse`, `persona3_recent` | B5가 exact owner를 골랐지만 B6 `NO_EXECUTABLE_AUTHORIZATION`: `CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`. 새 squat row는 남아 있어도 정확한 canonical posterior reference authority가 없어 target은 미충족이다. |
| Strength: `persona0_sparse`, `persona2_recent`, `persona3_sparse`, `persona4_recent`, `persona4_sparse` | B6 `NO_EXECUTABLE_AUTHORIZATION`: `B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE`. B4에는 새로운 numeric dose를 허용하는 authority가 없다. |
| Hypertrophy: `persona1_mixed` | B4는 personal restore target을 가지고 B5는 `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`를 골랐지만 B6는 `PLANNED_RESISTANCE_LOAD_UNAVAILABLE`로 거부한다. Exact load authority/입력이 부족하다. |

따라서 이것은 주로 주간 frequency나 placement shortfall이 아니다. 상위 단계에서 exact prescription을 실행할 권한이 없다. B5 선택 자체가 B6 허가를 대신하지 않는다.

### `TARGET_REGRESSED = 1`

`persona1_reviewed`의 `QUALITY:HYPERTROPHY` 하나다. B4의 개인 복원 target은 주 direct units max 6, preferred 6이었다. 실제 audit은 CONTROL 12 direct units, EXP 15 direct units로 기록했고, 거리(distance)는 6에서 9로 나빠졌다. B5가 `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`를 선택했고 B6는 `AUTHORIZED_EXISTING_COMPATIBLE` / `FULLY_ENCODED`였다. 즉 authority 부재가 아니라, 이미 target 상한보다 높은 기존 dose 위에 같은 exercise의 신규 3-set/week quality owner를 추가한 계획 결과다. B7이 regression을 차단하는 것은 맞다. 이 target regression은 provenance나 scope로 고칠 수 없다.

## `persona3_recent` 상세

이 사례는 Mixed scope가 필요한 증거가 아니다.

- **승인된 Task:** `ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION`와 `ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH`; 두 protocol 합계 8개 EXP row(2주 × 프로토콜당 주 2회). 각각 `BADMINTON_SIX_CORNER_FOOTWORK_V1`(3 rounds × 10–20 sec, 60 sec rest)와 `BADMINTON_LATERAL_SHUTTLE_LUNGE_V1`(3 × 5/side, 75 sec rest)이며 B6/policy provenance는 정확하다. Task target attribution도 각 protocol의 approved direct target set을 따른다.
- **승인되지 않은 Strength:** `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`가 주 1·2에 3×8, 40 kg, `CANONICAL_POSTERIOR_HOLD`로 EXP에 추가됐다. 그러나 B6는 두 주 모두 `NO_EXECUTABLE_AUTHORIZATION / CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`다. B5 선택과 legacy-shaped row가 B6 권한을 만들지 않는다. B4 Strength target은 `DIRECTION_ONLY`이고 target outcome은 `TARGET_UNMET`다.
- **CONTROL owner 제거:** `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` (기존 5×8, 40 kg)와 두 exercise의 `BADMINTON_OBJECTIVE_` role이 빠졌다. B11은 세 건 모두 canonical replacement로 분류하지만 B7은 unexplained removal로 남긴다. Task 두 건은 새 exact Task B6가 있지만 제거 attribution 경로가 그것을 소비하지 않는다. squat replacement는 exact B6가 없으므로 계속 막아야 한다.
- **기타 shared changes:** `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH`, `barbell_deadlift#COVERAGE_POSTERIOR_CHAIN`, `cable_rear_delt_fly#COVERAGE_UPPER_PULL`의 placement/row 변경이 있다. 이들도 approved Task material이 아니다. 주차별 실제 day/order는 census의 `materialDeltas`에 있다.
- **Unmet 분리:** B7 affected unmet은 `QUALITY:STRENGTH` 하나다. `QUALITY:POWER`와 `TASK:JUMP_LANDING`은 all-unmet outcomes에 나타나지만 material row가 없고 B7 directly affected target도 아니다. Power는 C21 경계대로 direction-only이며 B6/material 0이다. JUMP_LANDING은 승인 protocol/B6/material 0인 policy-closed target이다. 따라서 둘 다 현재 B8의 material blocker가 아니다.
- **Gate order:** scope는 `PARTIAL_PROVENANCE`이고 B8은 먼저 `B8_B7_NOT_ELIGIBLE`을 반환한다. Mixed Strength + Task scope가 있어도 B7 미통과를 건너뛸 수 없고, 이 사례를 EXP route로 보내지 않는다.

## 다음 후보 비교

| 후보 | 현재 CONTROL 영향 | 실제로 route를 열 가능성 | 현재 authority 및 위험 |
|---|---:|---|---|
| Provenance closure hardening | B7 11건 | provenance-only 3건은 B7에 접근하지만 이후 B8도 남아 있어 보장 없음 | 22 added identities는 미설명이고, quality removed 7건은 B6 거절이라 닫을 권한이 없다. 순수 closure 완화는 위험. |
| Exact Strength authority/material completion | Strength unmet 8건; 별도로 B8의 cold-start 4건 | 일부는 사용자가 load를 입력하거나 실제 personal/reference evidence를 제공하면 가능 | 5개는 B4 numeric dose가 없고 3개는 posterior reference가 없다. 새로운 dose를 추정하면 안 된다. 4 cold-start는 허가됐지만 user load input 없이는 full material이 아니다. |
| Mixed Strength + Badminton Task cutover | 현재 mixed material 사례 1 (`persona3_recent`) | **Mixed만 blocker인 사례 0건**; 추가 scope로 route되지 않음 | B7 unmet Strength, B6-denied squat, unclosed removals가 그대로 남는다. |
| JUMP_LANDING policy/metadata | `persona3_recent` all-unmet 결과 1건 | 지금은 없음 | material 0, direct B7 affected 0, 승인 policy 없음. 새로운 제품 정책이 필요하다. |
| Power authority | `persona3_recent` all-unmet 결과 1건 | 지금은 없음 | B4 direction-only, B6/material 0. C21 fail-closed가 의도된 상태다. |
| Incumbent removal/change provenance | 9 removed identity attribution, 7 cases | 그중 task B6가 이미 있는 제거 2건은 설명 연결 가능하지만 persona3는 Strength blocker가 남음 | B11 정보는 있으나 7 quality replacements는 B6가 실제 거절한다. C20 incumbent 안정성 12/0/20은 보존해야 한다. |
| Target regression repair | `persona1_reviewed` 1건 | B7를 통과시킬 수 있어도 현재 quality/B8 scope 및 residual owner changes를 다시 검증해야 함 | B4/B6는 있고 회귀는 명확하다. 작은 고정 범위이지만 전체 19건 정체를 설명하지는 않는다. |
| 현 상태 유지 | 즉시 route 증가 없음 | 없음 | gates는 안전하게 막지만 B6가 허가하지 않은 22 quality owner-week row가 EXP 비교에 존재하고, 2 Task replacement provenance link가 누락된 관측된 일관성 문제가 남는다. |

## 추천하는 다음 단일 작업

**다음 bounded 작업은 `B6 → EXP materialization → B7 attribution` 일관성 hardening이어야 한다.** 이를 Mixed scope나 새 prescription 정책으로 이름 붙이지 않는다.

정확한 범위는 census에서 잡힌 22개의 Quality owner-week addition을 만드는 경로를 좁혀, `NO_EXECUTABLE_AUTHORIZATION`인 처방이 EXP material로 나타나지 않도록 하거나(그것이 non-executable preview라면 typed non-material 상태로 분리해 B7/B8 material로 취급되지 않도록) 하는 것이다. 동시에 기존 C10/B11 경로에 exact Task B6 evidence를 소비하는 제거-대체 attribution을 추가하되, 실제 B6가 없는 7 quality replacements는 계속 거부한다. B7/B8을 느슨하게 하지 않고, 2×6 cold-start의 사용자 load input 요구도 보존한다.

이 추천은 즉시 route 수를 늘리는 약속이 아니다. 오히려 비교 입력에 실행 권한 없는 Quality row가 들어오는 현재의 명백한 계약 모순을 없애야 이후 어떤 scope 결과도 믿을 수 있다. B7/B8, C20, C21 Power, C24 Task 정책을 그대로 두는 안전한 코드 작업이다. 실제 19건만 보면 Mixed scope 추가는 근거가 없다.

## 검증 및 작업 경계

- C25 merge-main CI green: run `37417396504`; required protocol/contracts/whitespace/test/coverage/APK/signer/upload steps all green.
- Merged-main Hosted CI의 `stimulus-production-coverage.txt` SHA-256은 `1772FD236365E39E4012A3A1DEE1FFCBD190904E92F52C414F46372AF86D3178`이다.
- Local compile은 `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` 성공했다. Focused Room/service coverage audit도 통과했고 결정적 census를 생성했다. JSON 크기 최적화 후 재실행한 focused test는 `BUILD SUCCESSFUL`이었다.
- Local 전체 `:app:testDebugUnitTest`는 5분 38초 후 테스트 assertion 실패 없이 중단됐다. 268개 test suite에서 1,514 tests 완료, failures 0, errors 0, skips 3이 기록됐고, 다음 Room/WorkManager 경로에서 JBR 21.0.11+1-1163-jcef의 `robolectric-nativeruntime.dll+0x5c22`가 `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`를 냈다. 문제 프레임은 Robolectric SQLite `nativePrepareStatement`였으며 Gradle Test Executor 1 종료로 전체 run은 incomplete다. 이를 pass로 집계하지 않는다. Hosted CI의 전체 run은 green이다.
- Audit PR의 첫 Hosted 전체 run은 2,310 tests를 실행한 뒤 assertion failure 없이 census 직렬화 중 `JSONObject`에서 `OutOfMemoryError`가 났다. 중복 CONTROL 객체 배열을 제거하고 full-corpus 재렌더 결정을 작은 sample로 제한한 뒤 compact census 출력으로 peak allocation을 낮췄다. 최종 검증 결과는 [PR #18 Hosted check](https://github.com/Mascollel-Ko/WhatYouGottaDo/pull/18)에서 확인한다.
- `JAVA_TOOL_OPTIONS` 사용자 설정은 `-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`이고 `C:\GradleIpc`가 존재한다. 프로세스 환경에 값을 전달하지 않은 첫 확인은 loopback connection 오류였고, process-level 설정 후 Gradle compile과 focused suite는 성공했다.
- Audit diagnostic은 기존 Room/service corpus 결과를 test side에서 렌더한다. planner 호출을 추가하지 않으며 CONTROL=1, EXPERIMENTAL=1, TOTAL=2, THIRD=0을 유지한다.
- 이번 변경은 test/audit/documentation 전용이다. Production B4/B5/B6/B7/B8/B9, version, persistence schema, Power, JUMP_LANDING, Mixed scope, C20 incumbent behavior를 수정하지 않았다.
- C25 census에는 22 generated cases (19 CONTROL 포함), exact B4/B5/B6, owner-week material delta before/after, B7 attribution/target outcomes, scope/B8/B9, build accounting가 들어 있다. SHA-256: `05475A42C026915768AF708A51297D767139CF7E569DFCBBFB998B30AFAA85D4`.
