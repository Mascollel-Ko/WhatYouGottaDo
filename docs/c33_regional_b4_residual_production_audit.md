# C33 — Regional B4 residual production integration

## Purpose and boundary

C33 connects an already admitted movement need and its regional B4 residual to the ordinary production B5/B6 materialization path. It does not change B1–B4 need or dose decisions, create per-exercise set defaults, add a Core prescription shape, or weaken B7/B8. B5 chooses from the existing ranked candidate pool only when a positive residual exists; B6 receives the exact residual count and supplies the already approved Hypertrophy shape. Existing compatible work is credited before selection.

The path is:

`B3 ADDRESS → B4 movement target + regional dose/residual → B5 exact owner subset → B6 exact residual prescription → allocation/materialization → B7 target/provenance validation → B8 cutover authority`

Hypertrophy cold-start remains an 8-rep anchor within the project-approved 8–12 practical band, with minimum target RPE 7 and `USER_CALIBRATION_REQUIRED` when load is unknown. Compatible successful exercise history may supply 7–15 reps. B6 does not choose weekly set volume; it consumes B4's residual. Core targets remain separate and do not use the Hypertrophy route.

The 8-set fallback is a user-approved project cold-start seed only after B3 admits a need and no personal numeric dose authority is available. These values are conservative product defaults, not universal physiological optima. Existing personal history remains the preferred dose source.

## Sparse corpus results

The C31 source census had 22 movement target contexts and 44 owner-week rows. All 22 contexts remain in the current generation. The current sparse corpus also exposes seven additional B3 movement contexts: `persona0_sparse` and `persona3_sparse` add POSTERIOR_CHAIN and UPPER_PULL; `persona1_sparse`, `persona2_sparse`, and `persona4_sparse` add HORIZONTAL_PUSH. The latest result is 29 targets / 58 owner-week rows.

| Sparse case | Current targets | Outcome breakdown |
|---|---:|---|
| `persona0_sparse` | 4 | 1 exact residual; 1 Core shape unresolved; 1 no B5 owner; 1 no regional dose authority |
| `persona1_sparse` | 7 | 6 exact residuals; 1 Core shape unresolved |
| `persona2_sparse` | 7 | 3 exact residuals; 2 capacity-limited; 1 Core shape unresolved; 1 no regional dose authority |
| `persona3_sparse` | 4 | 1 exact residual; 1 Core shape unresolved; 1 no B5 owner; 1 no regional dose authority |
| `persona4_sparse` | 7 | 5 exact residuals; 1 Core shape unresolved; 1 no regional dose authority |

Within the original 22 C31 targets, 13 now materialize exact regional residuals; the other nine are five Core shape gaps, two finite-capacity limits, and two targets without numeric regional-dose authority. The seven newly visible contexts contribute three exact materializations, two missing B5 owners, and two additional no-dose-authority outcomes.

| Result | Count |
|---|---:|
| Current B3 movement targets | 29 |
| Hypertrophy regional-dose targets | 20 |
| Core-direct targets, with no approved prescription shape | 5 |
| Targets without regional numeric-dose authority | 4 |
| Positive Hypertrophy residual targets | 20 |
| Targets fully materialized to the exact B4 residual | 16 |
| Targets partially materialized | 0 |
| Legitimately unsatisfied targets | 13 |
| Candidate rows / selected owners / rejected candidates | 268 / 27 / 241 |
| B6-authorized weekly units / materialized weekly units | 288 / 256 |
| Targets requiring user load calibration | 18 |
| Overfilled / duplicate-credit / unauthorized targets or rows | 0 / 0 / 0 |

The 13 unsatisfied targets are accounted for, rather than silently dropped: two Hypertrophy targets have no selected B5 owner; two have exact B6 authority but finite weekly capacity rejects the indivisible owner materialization; five Core targets have no approved prescription-shape authority; and four `UPPER_PULL` targets have no regional numeric-dose authority. The two capacity-limited targets retain their exact shortfall/rejection evidence. No unauthorized row is used to fill them.

Candidate rejection is not treated as a failure. In this corpus, the canonical ranking selects the appropriate subset and rejects the remaining candidates with typed selection reasons. A single owner is not duplicated across the same residual merely to increase exercise variety. Where existing ranking scores tie, the pre-existing deterministic stable-key tie-break is recorded as a deterministic tie-break, not as a physiological preference.

## Production and cutover result

The full 22-generated-case corpus still builds exactly one CONTROL and one EXPERIMENTAL program per generated case (44 builds total; third builds 0). The regional production path has no excess units, duplicate credit, or unauthorized material. The overall route remains CONTROL because B7/B8 still see unrelated corpus blockers: removed-identity provenance remains unclosed in the generated cases, and target/collateral regressions remain in the corpus. C33 does not relax those guards or claim that the entire planner has cut over.

Across the full corpus, B7 reports 22 `CHANGE_PROVENANCE_UNCLOSED` cases, two `TARGET_REGRESSED` cases, one collateral regression, and zero cases where an affected target remains unmet. The unexplained-added-identity count is zero; remaining unexplained removals and the one prescription-change attribution continue to be governed by B7. Power and JUMP_LANDING executable material remain zero. The exact corpus comparison is generated by the same service path and is included in the full run artifacts; the sparse target-level census is committed at [`c33-regional-b4-residual-production-census.json`](c33-regional-b4-residual-production-census.json).

The full 22-generated-case route census remains CONTROL 22, with Strength V1, Strength Calibration, Hypertrophy, and Combined at zero. The remaining B7 attribution census is 90 unexplained removed identities across 22 cases plus one unexplained prescription change in `persona4_recent`; there are zero unexplained added identities. The route therefore stays fail-closed while C33's regional additions themselves carry exact B4/B5/B6 explanations.

## Verification

The five-case census took 1,391 ms total (278.2 ms mean, 182 ms median, 618 ms maximum). The focused production, candidate-selection, prescription, cutover, and incumbent-compatibility tests passed (144 tests, 0 failures/errors/skips); the service integration rerun also passed its two tests. The full `:app:testDebugUnitTest` suite passed 2,375 tests (0 failures, 0 errors, 4 skips) using an external JDK 17 test worker with `forkEvery=75`. The default JBR 21 full-suite attempt separately reproduced the known `robolectric-nativeruntime.dll` access violation; it was a native JVM crash, not an assertion failure. No Gradle or JDK workaround was committed.

`compileDebugKotlin`, `assembleDebug`, and `git diff --check` passed. The local debug APK is 70,741,614 bytes with SHA-256 `B6695E7ED4118428C8433B12F3410740F961E7323993A8565889329996592433`. Protocol/runtime/app/Room are `3.66.0` / `RECORD_BASED_PLANNER_0.15.8_KOTLIN_1` / `0.5.1.5` / 38. The machine census SHA-256 is `8DE39C04832480ACD468C6F76A1BCB4FB9195C05659333BB245A44436D75363B`.

Hosted run [37799001025](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37799001025) passed the same 2,375 tests (0 failures, 0 errors, 4 skips), protocol/Community/Cloud/whitespace checks, production coverage upload, APK assembly, signer validation, and artifact upload. APK artifact `11560905734` contains a 68,955,871-byte APK with SHA-256 `7AEFB6EC322D578360EE3E69E4530BF0FF2EDA0A023321C834ED93ADAB586C93`; its ZIP digest is `e854c8a5b256aa78145b50b22071a3b27d7dcae2049879c412b17264adc2dcbe`. Coverage artifact `11560392393` has ZIP digest `fc0e8a0c2f761dae2ba6918b1882904cc206a2dd5f088e592fc287e35704825d`; the uploaded `stimulus-production-coverage.txt` SHA-256 is `ADB08844F60B33591ECBA01B53E3B95D91829A47A487BF3A5D40E2EEB2DCC43A`. Implementation/test commit `6d721211b4b745f6a6020f0e7a8d415eecb3cce4` is pushed to `main`; this census and audit are the documentation follow-up.
