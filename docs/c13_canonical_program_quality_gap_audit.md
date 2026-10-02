# Phase C13 — canonical program quality gap audit

## Commits and baseline

- PR #4 was marked review-ready at `2026-10-02T15:51:31Z` and merged at `2026-10-02T15:51:44Z`.
- PR #4 head before merge: `f716149cf98b21ab536636a685f34ae41283c799`.
- C12 merge commit and C13 start HEAD: `14157618c803c16927257c9f00aa0e6c586fd5c5`.
- Blocking reviews and requested changes: none. PR check `Test and assemble debug APK` passed before merge.
- Merged-main Hosted CI run [37029941228](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37029941228) passed protocol validation, Community/Cloud contracts, whitespace, full unit tests, coverage upload, APK assembly, signer validation, and APK upload. Results: 2,189 tests, 0 failures, 0 errors, 4 skips.
- C13 implementation/test commit and `lastAuditedCommit`: `853eb8fb1518fa62ec815c86fe95b9175dc2bbf0`.
- Final documentation HEAD is recorded in the PR #5 final commit and task closeout. A Git commit cannot embed its own resulting object ID.
- Protocol/runtime/app remain `3.51.0` / `RECORD_BASED_PLANNER_0.14.3_KOTLIN_1` / `0.5.1.5`.

## Method and unchanged production behavior

- The real service corpus contains 27 cases: 22 generated and 5 preflight-rejected. Routes remain CONTROL 21, Strength 1, Hypertrophy 0, Combined 0.
- The standard coverage report remains byte-identical at SHA-256 `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`.
- The audit uses the service's existing CONTROL-free `buildCanonicalStimulusPlanningForPrepared` seam before normal generation. B1 comes from its canonical need profile, B2 from its canonical dose-history result, B3 from its canonical decision portfolio, and B4 from its target plan. All 22 recomputed target plans are asserted equal to the live comparison target plan. The seam consumes the same prepared request, answers, metadata, and Room history; it does not construct or inspect CONTROL.
- C10 and C11 traces remain attached to exact owner/role and target evidence. The census also records B5 selection, B6 authorities/realizations/materializations, final EXPERIMENTAL set classification, target outcomes, B7/B8 reasons, and B9 route. The raw report is deterministic and emitted as `app/build/reports/c13-canonical-quality-gap-census.json` for the Hosted coverage artifact; the local generated JSON is 4,986,105 bytes with SHA-256 `C5C2E3CBE31EF5FC8C32DC592B9F1CCC909AF6F0FFC46B00DE329E98D10CD9CD`.
- Changes are test diagnostics and a CI artifact path only. B1–B6 selection/authority, CONTROL and EXPERIMENTAL program contents, production gates, scope, routing, protocol versions, and build count do not change.

## Cohort counts and first shortfall stage

The 10 primary target-not-satisfied cases and 8 C12-primary B6 cases are disjoint. Target-outcome classification is based on the canonical B4 target and the final EXPERIMENTAL audit; CONTROL distances are comparison context only.

| Target-gap classification | Targets |
|---|---:|
| `TARGET_HAS_NO_EXECUTABLE_B6` | 9 |
| `TARGET_REGRESSED` | 1 |
| `AUTHORIZED_BUT_NOT_FULLY_MATERIALIZED` | 0 |
| `B5_SELECTED_INSUFFICIENT_IDENTITY` | 0 |
| `BUILDER_CAPACITY_CONSTRAINED` | 0 |
| `PRESCRIPTION_CLASS_MISMATCH` | 0 |
| `HISTORY_BASELINE_EDGE_CASE` | 0 |
| `TARGET_ACCOUNTING_MISMATCH` | 0 |
| `MULTI_TARGET_COMPETITION` | 0 |
| `OTHER_PROVEN` / `UNRESOLVED` | 0 / 0 |

| First shortfall stage | Targets |
|---|---:|
| B5 selection | 0 |
| B6 authority | 8 |
| B6 realization | 1 |
| Final target audit | 1 |
| Material demand through exact materialization, inclusive | 0 |
| `NONE` | 0 |

The eight B6-authority first failures are Strength direction-only cases with no numeric target and no direct-compatible final work. The Hypertrophy mixed case reaches B6 realization but its final whole-program weekly dose is also 3 units above the B4 maximum; the B6 failure and the over-band outcome are both retained. The reviewed Hypertrophy case has exact B6 authority and full materialization, but final exposure is above both B4 upper bounds and is classified `TARGET_REGRESSED`.

## Ten target-not-satisfied dossiers

The B2 values below are from the canonical dose-history result. “Incompatible units” is the exact two-week final EXPERIMENTAL owner/set classification; it is not a numeric B4 shortfall.

| Case | B1 / B2 / B3 | B4 target | Exact B5 owner and B6 evidence | Final canonical outcome; first stage |
|---|---|---|---|---|
| `persona0_recent` | HIGH / DEVELOP; no personal baseline, median 0, 3 eligible weeks; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 has no executable authorization (`CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE`) | 0 direct units/week, 0 sessions/week, 6 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona0_sparse` | HIGH / DEVELOP; no personal baseline, median 0, 1 eligible week; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 does not authorize change because B4 has direction-only authority | 0 direct units/week, 0 sessions/week, 4 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona1_mixed` | HIGH / DEVELOP; normal completed weeks, median 3, 7 eligible weeks; RESTORE | Hypertrophy units `[3,3,9]`; sessions `[1,1,3]` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`; no executable B6 authorization (`PLANNED_RESISTANCE_LOAD_UNAVAILABLE`) | 12 direct units/week and 3 sessions/week; 3 units above B4 max; `TARGET_UNMET`; B6 realization. The final overage is an additional program outcome failure, not proof that B6 caused it. |
| `persona1_reviewed` | HIGH / DEVELOP; normal completed weeks, median 6, 6 eligible weeks; RESTORE | Hypertrophy units `[0,6,6]`; sessions `[0,2,2]` | `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY`; existing compatible prescription, exact B6 authority, 3 authorized and 3 materialized units | 15 direct units/week and 3 sessions/week; 9 units and 1 session above B4 max; outcome `REGRESSED`; final target audit |
| `persona2_recent` | MODERATE / DEVELOP; no personal baseline, median 0, 3 eligible weeks; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 cannot change prescription under direction-only authority | 0 direct units/week, 0 sessions/week, 22 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona2_sparse` | MODERATE / DEVELOP; no personal baseline, median 0, 1 eligible week; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`; no executable posterior reference | 0 direct units/week, 0 sessions/week, 14 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona3_recent` | MODERATE / DEVELOP; no personal baseline, median 0, 3 eligible weeks; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH`; no executable posterior reference | 0 direct units/week, 0 sessions/week, 14 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona3_sparse` | MODERATE / DEVELOP; no personal baseline, median 0, 1 eligible week; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 does not authorize change under direction-only authority | 0 direct units/week, 0 sessions/week, 4 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona4_recent` | MODERATE / DEVELOP; no personal baseline, median 0, 3 eligible weeks; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 does not authorize change under direction-only authority | 0 direct units/week, 0 sessions/week, 14 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |
| `persona4_sparse` | MODERATE / DEVELOP; no personal baseline, median 0, 1 eligible week; INTRODUCE | Strength `DIRECTION_ONLY`; no numeric band | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`; B6 does not authorize change under direction-only authority | 0 direct units/week, 0 sessions/week, 12 incompatible units over 2 weeks; `TARGET_UNMET`; B6 authority |

The eight direction-only rows do not have a numeric unit shortfall to report. Their exact failure is that the final two-week skeleton contains no set classified DIRECT for Strength; the existing 8–12-rep/provisional sets are incompatible with the Strength direct-rep class. B4 has not authorized a numeric prescription change, so B6 correctly does not invent one. The target audit's direct-presence failure is supported by the exact final set classifier.

## Eight C12-primary B6 dossiers

C12 assigned eight cases a B6-primary label. C13 checks exact selected owner/quality rows instead of repeating that case label as fact: only six cases have a truly incomplete selected-target B6 row. The 8 cases contain 9 selected owner-target rows because `persona3_reviewed` has both Power and Strength rows; 7 rows fail exact B6 authority/materialization.

| Case | Selected owner-quality row(s) | B6 authority and realization | Materialization | B7 result |
|---|---|---|---|---|
| `persona0_mixed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | No executable authorization; planned `8–12 × 2`, RPE 6–8 is not target-compatible and safe load/effort authority is unavailable | Not materialized; 0/0 | Eligible for future cutover review; B8 still rejects the case |
| `persona0_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Same provisional `8–12 × 2`, RPE 6–8; no executable target-compatible prescription | Not materialized; 0/0 | Not eligible |
| `persona1_recent` | `cable_rear_delt_fly#CANONICAL_STIMULUS_QUALITY_HYPERTROPHY` | Hypertrophy target is direction-only; no numeric authority for a change. Candidate authorization is conditional on unpersisted effort | Not materialized; 0/0 | Not eligible |
| `persona2_mixed` | `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Existing compatible personal history; fully encoded B6 authority | Fully materialized; 3/3 | Eligible for review, but B8 has separate provenance/upstream/unrelated-change blockers |
| `persona2_reviewed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Provisional `8–12 × 2`, RPE 6–8; no safe Strength-compatible authorization | Not materialized; 0/0 | Eligible for review; B6 remains missing |
| `persona3_reviewed` | `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER` and `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Power: model unavailable (`CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE`). Strength: provisional `8–12 × 2`, RPE 6–8 with no safe target-compatible authority | Both not materialized; 0/0 | Not eligible |
| `persona4_mixed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Provisional `8–12 × 2`, RPE 6–8; no safe Strength-compatible authorization | Not materialized; 0/0 | Not eligible |
| `persona4_reviewed` | `barbell_back_squat#CANONICAL_STIMULUS_QUALITY_STRENGTH` | Existing compatible personal history; fully encoded B6 authority | Fully materialized; 3/3 | Eligible for review, but B8 still rejects the case |

Across the 7 failed owner-target rows, typed C13 causes are `REP_RANGE_INCOMPATIBLE=5`, `TARGET_DOSE_WITHOUT_PRESCRIPTION=1`, and `MODEL_UNAVAILABLE_TRUE_GAP=1`. No row failed after authorization, and there is no partial-materialization case. The two fully authorized/materialized Strength rows show why a case-primary classifier must not be read as an exact selected-owner B6 failure; their complete B6 rows still do not discharge the unrelated B7/B8 requirements.

## B7/B8 blockers and the canonical funnel

| B7 reason/outcome | Cases |
|---|---:|
| `CHANGE_PROVENANCE_UNCLOSED` | 14 |
| `AFFECTED_TARGET_REMAINS_UNMET` | 9 |
| Both provenance and unmet | 8 |
| Provenance only | 6 |
| Unmet only | 1 |
| A target outcome is `REGRESSED` | 1 |

| B8 reason | Occurrences |
|---|---:|
| `B8_B7_NOT_ELIGIBLE` | 16 |
| `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY` | 5 |
| `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION` | 5 |
| `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY` | 4 |
| `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY` | 3 |
| `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED` | 3 |
| `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION` | 3 |
| `B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY` | 1 |
| `B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE` | 1 |
| `B8_STRENGTH_V1_AUTHORIZED` | 1 |

The distinct owner-quality funnel is 26 active B4 targets → 26 exact B5-selected owner-quality identities → 5 exact executable B6 authorities → 5 full materializations → 4 final B4-compatible whole-program outcomes → 4 accepted B7 target outcomes among fully materialized identities. The fifth materialized identity is `persona1_reviewed/HYPERTROPHY`, whose whole-program target exceeds B4's weekly unit and session maxima. The raw report separately labels whole-program compatibility that is independent of exact B6; that independent tally must not be interpreted as an authorized funnel pass.

| Quality | B4 target requested | B5 selected | Exact B6 | Fully materialized | Compatible after full materialization | B7 target accepted after full materialization |
|---|---:|---:|---:|---:|---:|---:|
| Strength | 17 | 17 | 3 | 3 | 3 | 3 |
| Hypertrophy | 5 | 5 | 2 | 2 | 1 | 1 |
| Power | 4 | 4 | 0 | 0 | 0 | 0 |
| RFD, SSC, endurance, cardio, mobility | 0 | 0 | 0 | 0 | 0 | 0 |

| Repeated owner-quality identity | Selected | Exact B6 | B6 failure | Target unmet |
|---|---:|---:|---:|---:|
| `ex_32eb8457 / STRENGTH` | 6 | 0 | 6 | 3 |
| `barbell_bench_press / STRENGTH` | 5 | 0 | 5 | 2 |
| `barbell_back_squat / STRENGTH` | 6 | 3 | 3 | 3 |
| `barbell_bench_press / HYPERTROPHY` | 2 | 0 | 2 | 1 |
| `cable_rear_delt_fly / HYPERTROPHY` | 3 | 2 | 1 | 0 |
| `ex_314df428 / POWER` | 4 | 0 | 4 | 0 |

Power's repeated model-unavailable rows remain outside the active B8 production scope. They are not candidates for Power routing or scope expansion in C13/C14.

## Reference routes

- `reviewed_strength_isolated` remains the positive reference: B4 Strength RESTORE `[0,0,6]`; exact B5 back-squat identity; exact existing-compatible B6; full `3/3` materialization; final 3 units/week within B4; B7 eligible; B8 `STRENGTH_V1` authorized; B9 selects `B8_STRENGTH_V1`.
- `reviewed_hypertrophy_isolated` remains CONTROL: exact canonical fly B5 owner, executable existing-compatible B6, full `3/3` materialization, and H target outcome `UNCHANGED` at 3 units/week within `[0,6,6]`; B7 still rejects on `CHANGE_PROVENANCE_UNCLOSED`, so B8 requires CONTROL. The H fly target pass does not close the independent owner/provenance and placement changes.

## Zero-baseline and direction-only investigations

- Nine RESTORE Strength targets show a real canonical B2 median of `0` from `NORMAL_COMPLETED_WEEKS`, with 7 eligible weeks. Their B4 units band is `[0,0,6]`. The 0 is observed historical direct exposure, not a missing median or a value manufactured from CONTROL. A zero preferred baseline does not demand six sets; the maximum is an envelope. This is expected baseline semantics, not a C13 bug candidate.
- The eight Strength `INTRODUCE_DIRECT_STIMULUS` cases are direction-only. There is no numeric minimum/shortfall. Their final direct presence is 0, so the target audit is correct. No evidence supports authorizing a numeric B6 change or treating presence of an exercise with an incompatible rep class as direct Strength.
- The target auditor correctly treats direction-only as direct-presence and numeric RESTORE as both unit and session band checks. No current evidence identifies a target-accounting false negative.

## Top implementation candidates

These are investigation candidates for a later phase; C13 changes none of them.

| Candidate | Root cause, incidence, and location | Possible semantic change | Risk, tests, and route boundary |
|---|---|---|---|
| 1 — same-owner Strength prescription authority | Five failed owner-target rows across five primary B6 cases carry incompatible `8–12 × 2` provisional prescriptions and no exact Strength-compatible B6 authority. Inspect `StimulusPrescriptionRealization.kt` and `StimulusPlannedPrescriptionResolver.kt`. | Only accept a target-compatible same-owner recorded prescription when exact effort/load/rep authority exists; retain fail-closed behavior when it does not. | High safety sensitivity: never transfer load from another owner or invent a rep/load prescription. Test exact same-owner histories, incompatible rep classes, missing reference and RPE/load authority. A route can change only if exact B4/B5/B6, materialization, target outcome, B7 and B8 all pass; otherwise route remains CONTROL. |
| 2 — whole-program Hypertrophy target overage | Two target dossiers exceed B4 maxima: `persona1_mixed` by 3 weekly direct units, and `persona1_reviewed` by 9 weekly units plus 1 session. Inspect how target-level funding and whole-program units meet in `StimulusTargetPlan.kt` / the canonical materialization path; C13 does not identify a causal builder mutation for the excess. | Diagnose and, only with exact owner authority, keep total direct work within the B4 unit and session envelope. Do not call final above-band work a successful replacement. | Risk is collateral owner removal and unsupported funding attribution. Add exact multi-owner/week tests and preserve C10/C11 evidence. No B7/B8 relaxation; no route change without full target and collateral proof. |
| 3 — Power prescription model unavailability | `ex_314df428/POWER` is selected in four cases but has no B6 model authority in all four. | No implementation is approved by C13; record this as a future model-capability investigation only. | Keep all four outside Strength B8 scope. No Power/RFD/SSC routing or scope expansion. |

## Parity, build accounting, and recommendation

- Routes before and after C13: CONTROL 21, Strength 1, Hypertrophy 0, Combined 0. Gate-relaxation candidates: 0.
- B1–B6 and EXPERIMENTAL semantics are unchanged. The standard coverage checksum is unchanged. Ordering remains `B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9`.
- Normal generated cases remain `CONTROL=1 / EXPERIMENTAL=1 / TOTAL=2 / THIRD=0`; preflight rejection remains `0/0/0/0`. No third build was added.
- No C13 production code changed. C13 does not change B5 ranking, B6 prescription policy, B7/B8 gates, or routes.
- The five preflight rejections remain the unchanged no-history boundary and are recorded separately in the raw census.
- Raw machine-readable report: `c13-canonical-quality-gap-census.json` in the `Stimulus-production-coverage` Hosted CI artifact. Implementation/final CI run IDs, artifact IDs and digests, downloaded ZIP hashes, and APK bytes/hash are added after both CI runs complete.

There are zero potential false-negative gate cases. C14 should investigate the repeated same-owner Strength prescription-evidence gap first, without changing B4 numeric authority or B5 ranking. Only exact compatible owner-local B6 evidence can advance a case; the direction-only cases remain blocked until canonical evidence authorizes direct-compatible work. Keep the Hypertrophy over-band cases, all provenance gaps, and unsupported Power rows on CONTROL unless a later phase supplies the complete authority and safety chain.

## Local verification

- Environment check: process `JAVA_TOOL_OPTIONS` was initially blank; the User value is `-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`, and `C:\GradleIpc` exists. Gradle 9.3.0 reported launcher JDK 17 and daemon JBR 21. The process-only IPC option was used for Gradle; no machine-specific path was added to the repository.
- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` passed. The focused regression set passed 167 tests with 0 failures, 0 errors, and 0 skips, including C10–C13 provenance/classification and the requested selector, materialization, readiness, cutover, coverage, and failure-boundary suites.
- Full `:app:testDebugUnitTest --no-daemon` was attempted before the final test-only funnel refinement. It ran 1,452 tests with 0 assertion failures, 0 errors, and 3 skips before the Windows JBR 21 `EXCEPTION_ACCESS_VIOLATION` in `robolectric-nativeruntime.dll`; Gradle then reported the resulting loopback IPC reset. This is recorded as the Robolectric native SQLite crash, not an application assertion failure. The generated crash log was removed.

## Hosted CI and artifacts

- Implementation/test commit: `853eb8fb1518fa62ec815c86fe95b9175dc2bbf0`.
- PR #5 implementation Hosted run [37046551592](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37046551592) passed protocol validation, Community/Cloud contracts, whitespace, full tests, coverage upload, `assembleDebug`, signer validation, and APK upload. XML reports total 2,199 tests, 0 failures, 0 errors, and 4 skips.
- Coverage artifact `Stimulus-production-coverage`, ID `11244658704`: GitHub artifact digest `sha256:b57225460dbb0bade10e3c29fc9e74151f09dd3519d69831180cebb1c0013e52`; downloaded ZIP SHA-256 `B57225460DBB0BADE10E3C29FC9E74151F09DD3519D69831180CEBB1C0013E52`. Its census JSON is 4,986,105 bytes with SHA-256 `C5C2E3CBE31EF5FC8C32DC592B9F1CCC909AF6F0FFC46B00DE329E98D10CD9CD`, matching the local census.
- APK artifact `WhatYouGottaDo-debug-apk`, ID `11244678961`: GitHub artifact digest `sha256:f4b4cf2c52ae2aa0afdf7496908b97caac80e3a149ff3ab35fd400527fa462d4`; downloaded ZIP SHA-256 `F4B4CF2C52AE2AA0AFDF7496908B97CAAC80E3A149FF3AB35FD400527FA462D4`. The extracted APK is 68,625,075 bytes with SHA-256 `3412421EFAB6FDAED8B7605679C93644879326C1F0FC7B81F744414EFC4D658F`.
- The final documentation HEAD receives a separate Hosted run; its full CI and artifact values are added after completion. GitHub artifact digests, downloaded archive hashes, and extracted APK hashes remain separate fields even where the first two values happen to match.
