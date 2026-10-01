# Phase C8 — CONTROL-late production ordering audit

## Audited revision and unchanged versions

- Starting `main`: `f7fa7f209bc1c21d49b9601c9f879ffaa8fbd8d0`.
- C7 implementation/test reference: `fc010865ae637cd4d8b81b9ff53dee1f2e85655d`.
- C8 implementation/test commit and final audited code HEAD: `8b79ceee7b5c7987daa621c43ab2a2734daa1203`.
- Program Builder Protocol: `3.51.0` (unchanged).
- Planner runtime: `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1` (unchanged).
- App version: `0.5.1.5` (unchanged).
- `lastAuditedCommit` points to the C8 implementation/test commit above. This audit document is committed separately so its Hosted CI result can be checked independently.

## Ordering

Before C8, the normal production sequence was:

| Index | Stage |
|---:|---|
| 1 | CONTROL build |
| 2 | B1–B4 canonical planning |
| 3 | B5 selection |
| 4 | B6 pre-authorization |
| 5 | EXPERIMENTAL build |
| 6 | B6 post-materialization and realization |
| 7–10 | comparison, B7, B8, B9 |

After C8, the instrumented normal production sequence is:

| Index | Stage |
|---:|---|
| 1 | Shared preparation, including B1–B4 |
| 2 | B5 selection |
| 3 | B6 pre-authorization |
| 4 | EXPERIMENTAL build |
| 5 | B6 post-materialization and realization |
| 6 | CONTROL build |
| 7 | Late CONTROL parity, audit, and compatibility mirrors |
| 8 | CONTROL/EXPERIMENTAL comparison |
| 9 | B7 |
| 10 | B8 |
| 11 | B9 selection |

Thus EXPERIMENTAL is built before CONTROL, and B6 post-materialization completes before CONTROL exists. The phase-order success test uses `reviewed_strength_isolated`; it remains B7 eligible, B8 authorized, and selected by B9 as `B8_STRENGTH_V1`. The CONTROL-route `reviewed_hypertrophy_isolated` fixture also completes canonical materialization before CONTROL and still selects CONTROL.

## Shared context and CONTROL boundaries

`PreparedCanonicalGenerationContext` holds the single snapshot, state, gaps, intent, resolved request/frequency, prior decision id, legacy diagnostic inputs, and typed B1–B4 result. Both builders receive these same objects/values; answers are persisted once. It contains no CONTROL skeleton/item, incumbent seed/baseline, B7/B8 decision, or routing result.

`CanonicalExperimentalGeneration` contains the one EXPERIMENTAL skeleton, B5 selection, canonical prescription context, B6 authorization, experimental audit, realization plan, and materialization audits. It contains no CONTROL skeleton or CONTROL diagnostics. `generatePreparedProduction()` calls the staged C8 helpers and does not call the CONTROL-first compatibility or combined B6/CONTROL helper.

The only expected CONTROL dependencies before B7 are late-bound safety/comparison inputs, after B6 is already complete:

1. The resolved-request parity check runs on the one materialized CONTROL request and fails closed to that exact CONTROL object with typed `RESOLVED_REQUEST_PARITY` detail if it differs.
2. CONTROL final stimulus audit and compatibility/diagnostic mirrors are attached after materialization; `controlProgramAudit` remains nullable during B1–B6 and is late-bound for comparison.
3. CONTROL and the completed EXPERIMENTAL artifact meet at the comparison seam that supplies B7. No CONTROL value flows back into B1–B6.

## Build accounting and object identity

| Case | CONTROL | EXPERIMENTAL | Total | Third | Result |
|---|---:|---:|---:|---:|---|
| Normal success | 1 | 1 | 2 | 0 | B9 selects the existing exact object from comparison |
| Typed canonical expected failure | 1 | 0 | 1 | 0 | Late CONTROL fallback; typed upstream reason retained |
| Typed experimental expected failure | 1 | 1 | 2 | 0 | Late CONTROL fallback; typed upstream reason retained |
| Preflight rejection | 0 | 0 | 0 | 0 | Rejected before generation |

Tests assert the selected EXPERIMENTAL is the same prebuilt object and the selected CONTROL is the same materialized object. Cancellation during the pre-CONTROL EXPERIMENTAL stage propagates without a CONTROL build. Unexpected `IllegalStateException`/`IllegalArgumentException` failures propagate rather than being converted into fallback.

## C7 semantic and route parity

The C7 reference report SHA-256 is `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`. The C8 report is byte-identical with the same SHA-256. It contains 27 cases: 22 generated and 5 preflight rejected.

| Route | C7 | C8 |
|---|---:|---:|
| CONTROL | 21 | 21 |
| B8_STRENGTH_V1 | 1 | 1 |
| B8_HYPERTROPHY_V1 | 0 | 0 |
| Combined | 0 | 0 |

The byte-identical report preserves CONTROL stable keys and program decisions, B1–B4 targets, B5 selected owners, B6 authorized owners, B7/B8 statuses and reasons, B9 source, and per-case build counts. No routing expansion or gate relaxation occurred.

## Progress and persistence

Canonical, EXPERIMENTAL, and late CONTROL reporters map to increasing ranges `5–34`, `35–68`, and `69–90`. Selection is reported at 93, validation at 98, and completion at 100; the mapper ignores regressions and completion happens only after comparison and selection. Tests verify monotonic progress and a single terminal 100% update.

The EXPERIMENTAL prebuild is not persisted. The exact B9-selected object is returned, whether EXPERIMENTAL or CONTROL. Shared preparation persists answers once. Persistence-purity, selected-object identity, and build-count tests pass.

## Local verification

- Main and test Kotlin compilation passed during the focused run.
- Five focused suites passed after the final C8 changes: 33 tests, 0 failures, 0 errors, 0 skips. Suites: canonical planning independence, source boundary, selection-service integration, realization prescription inputs, and production failure boundary.
- A full local unit run was attempted before the final outer B7/B8 typed-failure guard and source-boundary matcher adjustment. The Windows JBR 21 worker crashed after 1,433 completed tests (0 failures, 0 errors, 3 skips) with `EXCEPTION_ACCESS_VIOLATION` in `robolectric-nativeruntime.dll` at `SQLiteConnectionNatives.nativePrepareStatement`; Gradle then reported a worker/loopback reset. This run is incomplete and is not reported as a pass. Hosted Linux CI below is the full-suite authority.
- `python scripts/validate_protocol_docs.py` passed: 9 families, 36 protocols. `git diff --check` passed.

## Hosted CI and artifacts

Implementation/test push CI: [Android Debug Build run 36887651269](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36887651269), commit `8b79ceee7b5c7987daa621c43ab2a2734daa1203`, conclusion **success**, elapsed 13m14s.

- Protocol validation, Community/Cloud contract tests, whitespace, full `testDebugUnitTest`, coverage upload, `assembleDebug`, signer validation, and APK upload all passed.
- Hosted JUnit artifacts: 340 XML files, 2,162 tests, 0 failures, 0 errors, 4 skips.
- Coverage report SHA-256: `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.
- Coverage artifact `Stimulus-production-coverage`: ID `11175367970`, 292,228 bytes, archive SHA-256 `8c1bf958f6cd84745940ed65f4d5d0f09eacf6ca1f51c0d409a885fa9da1e9be`.
- APK artifact `WhatYouGottaDo-debug-apk`: ID `11175811631`, 64,971,958 bytes, archive SHA-256 `7ff13a763cfd7a8edea0b6a3a3642e74234dc47bab90d1f8cec875cd5f552034`.
- Downloaded `app-debug.apk`: 68,559,539 bytes, SHA-256 `134888ABB57703D4D724C7E55719BAD0EA937A8D6EFAADC426F361A0AB581F0A`. CI signer verification passed against the configured debug keystore.

## Conclusion

Normal production now completes B1–B6 and the single EXPERIMENTAL artifact before building one CONTROL from the same prepared inputs. CONTROL is a late comparison, safety, compatibility, and rollback input only. C7 outputs, policy gates, protocol/runtime/app versions, and routing remain unchanged. No production CONTROL authority remains in B1–B6.
