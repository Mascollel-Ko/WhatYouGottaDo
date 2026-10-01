# Phase C7 behavior audit

C7 changes canonical B5/B6 authority. The C6 27-case SHA is retained as a comparison reference only; byte parity with legacy output is not an acceptance condition.

- Requested C6 reference in the task: e7e63f0282f9f6071c4a653b8b0ed17ed69aea47.
- Actual starting main when work began: 16b4770bf35e2ac9cbe51f9ad386a064205d5f41 (the C7 implementation commit was already present).
- Core implementation: ac83b1ec02412ccceb15e23791a5cead82a075a6.
- Latest implementation/test commit: fc010865ae637cd4d8b81b9ff53dee1f2e85655d.
- C6 baseline report: app/build/reports/stimulus-production-coverage-c6-baseline.txt; SHA-256 67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63.
- C7 generated report: app/build/reports/stimulus-production-coverage.txt; SHA-256 818e8fa6f67164eeaae0c938273a777d645874cf0eecd17f1e1795dc811434d9.
- C6 route counts: 18 CONTROL, 3 B8_STRENGTH_V1, 1 B8_HYPERTROPHY_V1, 5 no-history preflight rejects.
- C7 route counts: 21 CONTROL, 1 B8_STRENGTH_V1, 5 no-history preflight rejects; no HYPERTROPHY_V1 or combined cutover routes.
- Three generated cases changed route vs C6: persona0_mixed (Strength → CONTROL), persona2_reviewed (Strength → CONTROL), and reviewed_hypertrophy_isolated (Hypertrophy → CONTROL). The other 19 generated cases and five preflight cases retained their route.
- The sole C7 B8/B9 production route is reviewed_strength_isolated; its exact B5 owner and B6 compatible historical prescription pass existing B7/B8 authority and route through the existing B9 path.
- Generated cases preserve build counts CONTROL=1, EXPERIMENTAL=1, TOTAL=2, THIRD=0; all no-history cases stop before builds. There were no third builds or safety-invariant failures.
- B1–B4 exposure, need, successful-dose history, strategy, and target authority were preserved. C7 removes CONTROL-derived owner/prescription inputs from normal B5/B6; actual compatible history now supplies continuity and prescription evidence, constrained by B4 targets and canonical metadata/policy.
- CONTROL remains available for comparison, B7/B8/B9 safety, and rollback. B7/B8 fail closed where exact provenance or executable canonical authority is incomplete; this is expected safety behavior.
- Local Gradle: Gradle 9.3.0; JBR 21.0.11+1-1163.116-jcef; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc (directory exists). Main and unit-test Kotlin compilation passed.
- C7-focused suite: 94 tests, 0 failures, 0 errors, 0 skips.
- Full local suite: 1,431 tests completed with 0 failures, 0 errors, 3 skips before the Windows JBR worker crashed in robolectric-nativeruntime.dll during SQLite nativePrepareStatement; Gradle then saw the worker IPC reset. Classified as a Robolectric native DLL crash, not an assertion failure. Hosted CI is the complete-suite result.
- Hosted CI run [36806675542](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36806675542) passed on fc010865ae637cd4d8b81b9ff53dee1f2e85655d in 11m51s: 2,159 tests, 0 failures, 0 errors, 4 skips. Protocol validation, Community/Cloud contracts, whitespace, coverage/JUnit upload, debug APK assembly, signer validation, and APK upload passed. The uploaded C7 coverage report SHA-256 matches 818e8fa6f67164eeaae0c938273a777d645874cf0eecd17f1e1795dc811434d9. APK artifact WhatYouGottaDo-debug-apk: 68,543,155 bytes; SHA-256 E86FB6916B1E4F9767E56751CB93F28F474B3D451F7D146E5381A35FE443B107.

## Per-case comparison

### persona0_mixed

- **Input case:** persona0_mixed: intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false history=mixed days=2 minutes=60 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 B8_STRENGTH_V1 → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_32eb8457.
- **Identity delta:** added=ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=CONTROL_REQUIRED B7reasons=[] B8reasons=[B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY, B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY, B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION, B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION, B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona0_none

- **Input case:** persona0_none: history=none intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false days=2 minutes=30 equipment=BARBELL,BENCH,DUMBBELL,RACK
- **Route:** C6 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY → C7 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY.
- **C7 phases:** B1–B9 not reached; no confirmed completed workout history in the fixture.
- **Safety/build accounting:** PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0.

### persona0_recent

- **Input case:** persona0_recent: intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false history=recent days=4 minutes=90 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,cable_rear_delt_fly,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,cable_rear_delt_fly,ex_28347c1f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona0_reviewed

- **Input case:** persona0_reviewed: intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false history=reviewed days=5 minutes=30 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_romanian_deadlift,dumbbell_chest_supported_row,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_romanian_deadlift,dumbbell_chest_supported_row,ex_28347c1f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona0_sparse

- **Input case:** persona0_sparse: intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false history=sparse days=3 minutes=60 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** cable_rear_delt_fly.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_32eb8457.
- **Identity delta:** added=ex_1dbee10e,ex_28347c1f,ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona1_mixed

- **Input case:** persona1_mixed: intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false history=mixed days=3 minutes=90 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,cable_rear_delt_fly,dumbbell_goblet_squat,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/NO_MINIMUM_TARGET/NONE.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **EXPERIMENTAL stableKeys:** barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,cable_rear_delt_fly,dumbbell_goblet_squat,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona1_none

- **Input case:** persona1_none: history=none intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false days=3 minutes=60 equipment=
- **Route:** C6 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY → C7 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY.
- **C7 phases:** B1–B9 not reached; no confirmed completed workout history in the fixture.
- **Safety/build accounting:** PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0.

### persona1_recent

- **Input case:** persona1_recent: intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false history=recent days=5 minutes=30 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_3d5719de,ex_64661229,standing_bodyweight_calf_raise.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/NO_MINIMUM_TARGET/NONE.
- **B5 canonical owners:** cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **EXPERIMENTAL stableKeys:** cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_3d5719de,ex_64661229,standing_bodyweight_calf_raise.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona1_reviewed

- **Input case:** persona1_reviewed: intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false history=reviewed days=2 minutes=60 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/NO_MINIMUM_TARGET/NONE.
- **B5 canonical owners:** cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** QUALITY:HYPERTROPHY:cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[TARGET_REGRESSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona1_sparse

- **Input case:** persona1_sparse: intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false history=sparse days=4 minutes=90 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_bench_press.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/NO_MINIMUM_TARGET/NONE.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **EXPERIMENTAL stableKeys:** barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_goblet_squat,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **Identity delta:** added=barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_goblet_squat,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona2_mixed

- **Input case:** persona2_mixed: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=mixed days=4 minutes=30 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** QUALITY:STRENGTH:barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=CONTROL_REQUIRED B7reasons=[] B8reasons=[B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY, B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION, B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION, B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona2_none

- **Input case:** persona2_none: history=none intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false days=4 minutes=90 equipment=CABLE,MACHINE
- **Route:** C6 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY → C7 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY.
- **C7 phases:** B1–B9 not reached; no confirmed completed workout history in the fixture.
- **Safety/build accounting:** PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0.

### persona2_recent

- **Input case:** persona2_recent: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=recent days=2 minutes=60 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_chest_supported_row,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_chest_supported_row,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona2_reviewed

- **Input case:** persona2_reviewed: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=reviewed days=3 minutes=90 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 B8_STRENGTH_V1 → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_3d5719de,standing_bodyweight_calf_raise.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_32eb8457,ex_3d5719de,standing_bodyweight_calf_raise.
- **Identity delta:** added=ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=CONTROL_REQUIRED B7reasons=[] B8reasons=[B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY, B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona2_sparse

- **Input case:** persona2_sparse: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=sparse days=5 minutes=30 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_bench_press.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **Identity delta:** added=barbell_back_squat,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona3_mixed

- **Input case:** persona3_mixed: intent=MIXED goal=BADMINTON_SUPPORT badminton=true history=mixed days=5 minutes=60 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY/DIRECTION_ONLY,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER,ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_314df428,ex_32eb8457.
- **Identity delta:** added=ex_314df428,ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=CONTROL_REQUIRED B7reasons=[] B8reasons=[B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY, B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY, B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE, B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION, B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona3_none

- **Input case:** persona3_none: history=none intent=MIXED goal=BADMINTON_SUPPORT badminton=true days=5 minutes=30 equipment=BARBELL,BENCH,DUMBBELL,RACK
- **Route:** C6 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY → C7 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY.
- **C7 phases:** B1–B9 not reached; no confirmed completed workout history in the fixture.
- **Safety/build accounting:** PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0.

### persona3_recent

- **Input case:** persona3_recent: intent=MIXED goal=BADMINTON_SUPPORT badminton=true history=recent days=3 minutes=90 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,cable_rear_delt_fly,ex_28347c1f,ex_33841b88,ex_421ba24b.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY/DIRECTION_ONLY,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH,ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER,ex_33841b88#CANONICAL_STIMULUS_TASK_ACCELERATION,ex_421ba24b#CANONICAL_STIMULUS_TASK_LUNGE_REACH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,cable_rear_delt_fly,ex_28347c1f,ex_314df428,ex_33841b88,ex_421ba24b.
- **Identity delta:** added=ex_314df428 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona3_reviewed

- **Input case:** persona3_reviewed: intent=MIXED goal=BADMINTON_SUPPORT badminton=true history=reviewed days=4 minutes=30 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_romanian_deadlift,dumbbell_chest_supported_row,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY/DIRECTION_ONLY,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH,ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_romanian_deadlift,dumbbell_chest_supported_row,ex_28347c1f,ex_314df428.
- **Identity delta:** added=ex_314df428 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona3_sparse

- **Input case:** persona3_sparse: intent=MIXED goal=BADMINTON_SUPPORT badminton=true history=sparse days=2 minutes=60 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** cable_rear_delt_fly.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY/DIRECTION_ONLY,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER,ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_314df428,ex_32eb8457.
- **Identity delta:** added=ex_1dbee10e,ex_28347c1f,ex_314df428,ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona4_mixed

- **Input case:** persona4_mixed: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=mixed days=2 minutes=90 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,cable_rear_delt_fly,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,cable_rear_delt_fly,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona4_none

- **Input case:** persona4_none: history=none intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false days=2 minutes=60 equipment=
- **Route:** C6 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY → C7 PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY.
- **C7 phases:** B1–B9 not reached; no confirmed completed workout history in the fixture.
- **Safety/build accounting:** PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0.

### persona4_recent

- **Input case:** persona4_recent: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=recent days=4 minutes=30 equipment=CABLE,MACHINE isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_3d5719de,standing_bodyweight_calf_raise.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_1dbee10e,ex_28347c1f,ex_32eb8457,ex_3d5719de,standing_bodyweight_calf_raise.
- **Identity delta:** added=ex_32eb8457 removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona4_reviewed

- **Input case:** persona4_reviewed: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=reviewed days=5 minutes=60 equipment= isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,barbell_bench_press,barbell_deadlift,barbell_reverse_curl,cable_overhead_triceps_extension,cable_rear_delt_fly,ex_28347c1f,ex_5c8751d2.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** QUALITY:STRENGTH:barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=CONTROL_REQUIRED B7reasons=[] B8reasons=[B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY, B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED, B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION, B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION, B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### persona4_sparse

- **Input case:** persona4_sparse: intent=MIXED goal=FUNCTIONAL_CONDITIONING badminton=false history=sparse days=3 minutes=90 equipment=BARBELL,BENCH,DUMBBELL,RACK isolated=false
- **Route:** C6 CONTROL → C7 CONTROL.
- **CONTROL stableKeys:** barbell_bench_press.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/INTRODUCE_DIRECT_STIMULUS/DIRECTION_ONLY.
- **B5 canonical owners:** barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_bench_press,barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_chest_supported_row,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f.
- **Identity delta:** added=barbell_reverse_curl,barbell_romanian_deadlift,dumbbell_chest_supported_row,dumbbell_lying_triceps_extension,ex_28347c1f,ex_5ca7133f removed=.
- **B6 authorized owners:** .
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[AFFECTED_TARGET_REMAINS_UNMET, CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### reviewed_hypertrophy_isolated

- **Input case:** reviewed_hypertrophy_isolated: intent=HYPERTROPHY_PRIORITY goal=BODYBUILDING badminton=false history=reviewed days=3 minutes=60 equipment= isolated=true
- **Route:** C6 B8_HYPERTROPHY_V1 → C7 CONTROL.
- **CONTROL stableKeys:** cable_rear_delt_fly,ex_28347c1f,ex_284ecca6,ex_6232f4bc.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/NO_MINIMUM_TARGET/NONE.
- **B5 canonical owners:** cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **EXPERIMENTAL stableKeys:** cable_rear_delt_fly,ex_28347c1f,ex_284ecca6,ex_6232f4bc.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** QUALITY:HYPERTROPHY:cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY.
- **B7/B8 outcome:** C7 routeAfter=CONTROL B7=NOT_ELIGIBLE B8=CONTROL_REQUIRED B7reasons=[CHANGE_PROVENANCE_UNCLOSED] B8reasons=[B8_B7_NOT_ELIGIBLE].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=CONTROL builds=1/1/2/0.

### reviewed_strength_isolated

- **Input case:** reviewed_strength_isolated: intent=STRENGTH_PRIORITY goal=STRENGTH badminton=false history=reviewed days=3 minutes=60 equipment= isolated=true
- **Route:** C6 B8_STRENGTH_V1 → C7 B8_STRENGTH_V1.
- **CONTROL stableKeys:** barbell_back_squat,cable_rear_delt_fly,dumbbell_romanian_deadlift,ex_1dbee10e,ex_28347c1f.
- **B4 targets:** CARDIORESPIRATORY_FITNESS/NO_MINIMUM_TARGET/NONE,HYPERTROPHY/NO_MINIMUM_TARGET/NONE,MOBILITY_ROM/NO_MINIMUM_TARGET/NONE,MUSCULAR_ENDURANCE/NO_MINIMUM_TARGET/NONE,POWER/NO_MINIMUM_TARGET/NONE,RAPID_FORCE_PRODUCTION/NO_MINIMUM_TARGET/NONE,REACTIVE_STRENGTH_SSC/NO_MINIMUM_TARGET/NONE,STRENGTH/RESTORE_PERSONAL_BASELINE/PERSONAL_RESTORE_BASELINE.
- **B5 canonical owners:** barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **EXPERIMENTAL stableKeys:** barbell_back_squat,cable_rear_delt_fly,dumbbell_romanian_deadlift,ex_1dbee10e,ex_28347c1f.
- **Identity delta:** added= removed=.
- **B6 authorized owners:** QUALITY:STRENGTH:barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH.
- **B7/B8 outcome:** C7 routeAfter=B8_STRENGTH_V1 B7=ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW B8=AUTHORIZED_FOR_BOUNDED_CUTOVER B7reasons=[] B8reasons=[B8_STRENGTH_V1_AUTHORIZED].
- **Safety/build accounting:** B7shadowOnly=true B7productionAuthority=false B7winner=null B8routingActive=false B8mutationAuthority=false B9source=B8_STRENGTH_V1 builds=1/1/2/0.
