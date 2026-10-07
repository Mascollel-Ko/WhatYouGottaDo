# C28 — Material-demand execution authority and bounded recovery audit

C28 separates a real coverage need from permission to execute a prescription. A candidate remains visible when its dose authority is missing. The builder first checks the finite, existing candidate set for an exact B5/B6-authorized owner; only an exact authorization can materialize. When all supported choices are exhausted, the need remains typed as unresolved. No generic replacement dose is created.

## Starting point and policy boundary

C28 started from C27 merged main `f71e84f02dc69866089535e37fa1627096059590`. PR #20 merged at `2026-10-06T21:02:20Z` with merge SHA and new main SHA `f71e84f02dc69866089535e37fa1627096059590`. Merged-main Hosted run [37498159746](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37498159746) was green, including protocol, Community/Cloud contracts, whitespace, tests, coverage, APK assembly, signer validation, and artifact upload.

The C27 source census recorded 22 sparse added identities and 44 executable owner-week rows without exact B6. The separate 22 C26-denied Quality rows remained absent, and their exact intersection with these sparse identities was zero. The sparse rows had come from `MaterialDemandResolver.resolve` and crossed an unauthorized `generationPrescriptions.prescribe` fallback; they were real scheduled rows, not diagnostics.

C28 changes the production boundary and therefore advances the contract to protocol `3.62.0` and runtime `RECORD_BASED_PLANNER_0.15.4_KOTLIN_1`. App remains `0.5.1.5`; Room remains 38. No backup, restore, Community, or Cloud schema changed.

## Recovery behavior

`MaterialDemandResolver` continues to detect gaps, find/rank candidate identities, and preserve their origin. In canonical EXP generation, candidates carry zero requested sets. That is candidate information, not dose authority.

Before allocation, the builder performs a bounded exact-authority recovery pass over the resolver's finite ranked alternatives. It considers an alternative only if the exact stable key and selection role have an existing executable prescription and are selected by the corresponding B5 authority source (or have the exact regional/task grant). It tracks each attempted owner identity once. A candidate's material-demand origin never supplies sets, reps, load, RPE, or frequency.

Typed B6 resolution distinguishes an owner-selection problem from a missing B4 target, unavailable history reference, missing load input, and unsupported authority. Only owner reselection can be resolved by scanning the already-created finite candidate list. The request's B4/B5/B6 stages have already evaluated the immutable history and user inputs before material-demand completion; repeating those stages with identical inputs cannot create new evidence. If valid history is present on a fresh evaluation, B6 is reevaluated and can authorize it. If an explicit load is required, the result remains `USER_INPUT_REQUIRED`/`NEEDS_LOAD_INPUT`; no weight is synthesized.

This is bounded recovery, not a new dose policy. The synthetic positive fixture proves the second candidate is used only when its exact owner is already B5-selected and has exact B6. Other fixtures prove finite exhaustion, history-reference recovery on reevaluation with real history, explicit load-input handling, and retention of a need when no candidate exists. In the real sparse corpus, the existing exact authorities do not support any of the alternatives, so these needs correctly remain unresolved.

The typed return target controls recovery instead of treating every missing authority as the same terminal denial: an owner gap returns to the finite material-demand candidate selection; a missing numeric target points to B4; a missing posterior/reference points to the existing history resolver; and missing load points to explicit user input. B4, B5, and B6 already run before material-demand completion with the full immutable request/history available. C28 does not rerun an identical stage with identical inputs, because that cannot create evidence; it preserves that upstream destination and retries on a fresh evaluation when new history or user input is present. The reference test now follows that path through a new B6 authorization plan and proves that the resulting exact prescription passes the material-demand gate. Owner alternatives are accepted only when canonical B5 has independently selected the exact stableKey+role and B6 has executable authority for it.

A denied candidate is removed before budgeting. It therefore cannot return through allocation, frequency expansion, residual completion, day rebalancing, post-split reflow, or incumbent preservation. The CONTROL comparator remains a separately built rollback baseline; its legacy shape is not copied into EXP and is not B6 authority. B7 and B8 rules are unchanged.

## Sparse identities and owner-week results

The machine census [`c28-material-demand-execution-authority-census.json`](c28-material-demand-execution-authority-census.json) contains all 22 exact `case/week/stableKey/selectionRole` rows, their pre-C28 prescriptions, gaps, ordered attempted owners, typed recovery result and post-C28 actual scheduled rows. The corresponding per-case totals are:

| Case | Identities | Prior owner-weeks | Coverage needs | Recovery result |
|---|---:|---:|---|---|
| `persona0_sparse` | 2 | 4 | `HORIZONTAL_PUSH`, `CORE_DIRECT` | both finite candidate lists exhausted |
| `persona1_sparse` | 6 | 12 | lower knee, posterior chain, core, calves, biceps, triceps | all six finite candidate lists exhausted |
| `persona2_sparse` | 6 | 12 | posterior chain, upper pull, core, calves, biceps, triceps | all six finite candidate lists exhausted |
| `persona3_sparse` | 2 | 4 | `HORIZONTAL_PUSH`, `CORE_DIRECT` | both finite candidate lists exhausted |
| `persona4_sparse` | 6 | 12 | posterior chain, upper pull, core, calves, biceps, triceps | all six finite candidate lists exhausted |
| **Total** | **22** | **44** | | |

Before C28 the 44 owner-week rows were generic provisional executions, typically 2×8 with 90-second rest and `PROVISIONAL_RPE_NO_INVENTED_LOAD`; the source also rendered explicit load as 0 kg and a display-only RPE range despite missing typed load/effort authority. C28 does not “repair” those shapes. After C28: original candidates authorized 0, alternatives reselected to authorized owners 0, explicit user-input cases 0, unresolved after exhausting existing authority 22 identities / 44 owner-weeks, executable rows 0, unauthorized executable rows 0, and material deltas from these identities 0. No row is resurrected downstream.

## Other checks

`reviewed_hypertrophy_isolated` no longer contains the unauthorized `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN` 2-set experimental prescription. Its prior 3→2 change is absent because there is no experimental row; the underlying coverage need and candidate origin remain available for diagnosis. C28 does not copy CONTROL's three sets.

The C26-denied Quality set remains at zero executable rows and zero material deltas; the C26/C27 exact intersection remains 0. Seven Quality replacement rows remain fail-closed when replacement B6 is absent. C24 Task semantics remain lossless: the generated comparison still contains the existing eight approved task rows, and `persona3_recent` remains CONTROL because its unrelated B7 readiness is not changed. Power authority/B6/material and JUMP_LANDING approved protocol/material remain zero.

C27's route snapshot was CONTROL 19 / Strength V1 1 / Strength Calibration 2 / Hypertrophy 0 / Combined 0. After C28's 22 generated service cases all select CONTROL; five other cases are preflight rejects. This is a consequence of failing closed when unsupported coverage removals leave provenance incomplete, not a routing adjustment. The B7/B8 predicates were not changed. Across the generated corpus, B7 reports `CHANGE_PROVENANCE_UNCLOSED` in 15 cases, `TARGET_REGRESSED` in 1, and collateral regression in 1; these are counts over the current 22-case execution and should not be compared to C27 without respecting the different generated population and changed EXP material.

C20 currently measures 10 hard-valid incumbent rows, 0 hard-invalid, 0 unresolved, and 22 rows not evaluated because no current authorized EXP owner exists. Ten valid incumbents are preserved; none of the 22 not-evaluated rows are forced. The earlier 12/0/20 census is the C27 baseline before those unauthorized coverage owners disappeared. Build accounting remains one CONTROL and one EXPERIMENTAL build per generated case: total 2, third 0 (22/22/44/0 in aggregate). No hybrid skeleton or third build was added.

## Limits and next product step

C28 resolves what existing authority can resolve; it does not invent new resistance prescriptions. The sparse needs have no exact B5/B6-authorized alternative, and the current generation requests contain no new history or load input that could change that result. The appropriate next product decision is whether to expose these exact unmet coverage needs for user resolution or to approve narrowly governed prescription authority for specific exact owner/role pairs. That decision must precede any new dose rule. Power, JUMP_LANDING, mixed B8 scope, and B7/B8 relaxation remain out of scope.

The deterministic census is generated from the real Room/service corpus plus resolver-only inspection; resolver inspection does not call a third planner build. The C28 test asserts input-order determinism and the exact identity/week boundary.

## Validation and artifacts

Implementation/test commit: `acc0787f607b02e8e727e6c7f0daeffb40d3f334`; registry `lastAuditedCommit` points to this SHA. Local Kotlin production and unit-test compilation passed. The full local unit suite passed with 2,329 tests, 0 failures, 0 errors, and 4 skips using the repository-external Windows Gradle worker-restart init script; after the history-recovery integration assertion was added, the focused `StimulusPrescriptionRealizationTest` and `CanonicalB5B6MaterialDemandBoundaryTest` rerun also passed.

Hosted CI on PR #21 docs head `6c5b2ee9e2f23cdf4c9bde5b4c92493aeaff0242` passed in [run 37574846631](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37574846631): 2,329 tests, 0 failures, 0 errors, 4 skips; protocol validation, Community/Cloud contracts, whitespace, production coverage upload, APK assembly, signer validation, and artifact upload all passed. This audit commit adds the verified run and artifact details; the resulting final docs head is validated separately. The hosted coverage report SHA-256 is `C7B885E365315E85261D080447E38FB139FC91263C061D4F2D4C96E63139A56C`; the census SHA-256 is `FF759D0F85ACA4729F1234B9A919E2B82CAD5ABC560A31C349891FA6E3D61696`. Artifact `WhatYouGottaDo-debug-apk` is 68,856,007 bytes; its APK SHA-256 is `76F47579E0D48AAEEBDE1CB40EC9671A3CA4192F2EAD999CB6FB68A9CBCE3B48`. Hosted coverage and APK artifact archives have SHA-256 digests `b0fd237424f806b4572d1c78ccddfc072035a00528e815cea0a857393f171d30` and `6c8ad3a6c69cf407e2f28d34bd80eaf8b3f3066e1d43ad0c3fef40a851384d54`, respectively.
