# Phase C15 — Cold-Start Strength Calibration Audit

## Audit identity

- C14 merged main / C15 start: `b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec`
- C14 PR #6 merge SHA: `b1c8aa06dcae0c25aa4f38a1d3a92298fcba23ec`
- C15 implementation/test SHA and `lastAuditedCommit`: `4d589cb3a371c2e8359332e320d375d7e0ad9a92`
- Final reviewed code SHA (the following closeout commit is documentation-only): `4d589cb3a371c2e8359332e320d375d7e0ad9a92`
- C14 main CI: run `37096232001`; 2,212 tests, 0 failures, 0 errors, 4 skips; all protocol, contracts, whitespace, coverage, APK, signer, and upload jobs passed.

C15 addresses the cold-start case where canonical Strength owner selection and numeric B4 set authority exist, but no exact same-owner load signal exists. It adds a user-calibration prescription state and a separately gated `B8_STRENGTH_CALIBRATION_V1` route. It does not infer a kilogram value.

## Evidence and policy

The repository already has a canonical same-owner capacity path in `StrengthTrainingLoadAuthorityResolver`, using the existing strength performance resolver, reviewed repetition-curve assignment, posterior/history, realized stimulus classification, load-semantics resolution, and C14 recency and evidence checks. C15 reuses the C14 eligibility result to identify only the specific absence condition `EXACT_OWNER_STRENGTH_SIGNAL_MISSING`. It adds no second 1RM oracle and does not borrow load across exercises.

External primary evidence was reviewed:

- Nuzzo et al.'s meta-regression included 952 repetitions-to-failure tests from 7,289 people and modeled both mean repetitions and between-person variation. Bench press and leg press differed from the general relationship. This supports treating population rep/load relationships as uncertain context rather than an exact individual load source. [PubMed Central, PMID 37792272](https://pmc.ncbi.nlm.nih.gov/articles/PMC10933212/)
- Lovegrove et al. found repeatable 1-RIR load prescriptions in a small novice-trained male sample, with different loads across 3-, 5-, and 8-repetition schemes (deadlift 88.2%, 84.3%, 79.2%; bench 93.0%, 87.3%, 79.6% of 1RM). These are study observations, not a universal app conversion table. [PubMed, PMID 36135029](https://pubmed.ncbi.nlm.nih.gov/36135029/)
- Zourdos et al. studied the RIR-anchored resistance-training RPE scale in 29 squatters. The scale can communicate effort and regulate training, while its study design and sample do not make a reported RPE an exact measurement for every user. [PubMed, PMID 26049792](https://pubmed.ncbi.nlm.nih.gov/26049792/)
- Mansfield et al. found RIR estimates in trained men could differ from actual reserve and improved across repeated sets as participants got closer to failure. This supports retaining uncertainty and using completed observations as calibration evidence. [PubMed, PMID 32881842](https://pubmed.ncbi.nlm.nih.gov/32881842/)
- The requested Hughes et al. four-exercise study (PMID 33337690) is marked as retracted in PubMed (retraction PMID 33470601). It was reviewed but excluded from the supporting evidence. A non-retracted 2024 bench-press study found useful RIR accuracy in its trained sample at 75% 1RM; this is also not treated as a universal guarantee. [PubMed retraction record](https://pubmed.ncbi.nlm.nih.gov/33337690/), [PubMed, PMID 37967832](https://pubmed.ncbi.nlm.nih.gov/37967832/)

The product policy remains distinct from those empirical estimates: if no personal 1–6 repetition pattern exists, the calibration prescription uses 6 repetitions and target RPE 6.5. Six is the selected product bridge within the existing Strength class, not a claim of universally optimal training. B4/material demand alone supplies the set count.

## Load representation and persistence

The core numeric load fields remain non-nullable for compatibility. `ProgramLoadState` now distinguishes `EXPLICIT_LOAD`, `USER_CALIBRATION_REQUIRED`, `REAL_ZERO_LOAD`, and `NOT_APPLICABLE`. `USER_CALIBRATION_REQUIRED` may coexist with the legacy numeric sentinel `0.0`, but semantic code and UI must use the typed state; the sentinel is not an actual prescription or exposure. Legacy database rows migrate to `EXPLICIT_LOAD` so prior meaning is preserved.

Room schema version advances from 35 to 36 with additive state/effort columns for recorded sets, program item sets, and progression prescription sets. The migration is additive and defaults existing stored rows conservatively to explicit load. No global nullable-load conversion was made. Load state and target effort also survive plan save/reload, session linking, progression backup/wire serialization, CSV backup/restore, and Community payload encoding. Tests cover database migration, disk-backed plan reload, CSV, Community, and Cloud compatibility. The existing app version remains `0.5.1.5`.

The four load meanings are separate in the domain enum. C15 currently emits only `USER_CALIBRATION_REQUIRED`; real zero and not-applicable behavior elsewhere is unchanged. A newly generated calibration set is saveable and editable while blank, but it cannot be confirmed for an exercise with required external/mechanical load until the user enters a valid positive load. Canonical bodyweight, assistance, timed, and non-resistance semantics are not globally forced to positive kilograms.

## Prescription and authority chain

`ColdStartStrengthCalibrationResolver` emits an exact-owner proposal only when all of the following hold: the target is Strength; B4 contains a numeric authorized demand; B5 selected the exact canonical Strength owner for the direct target; exact-owner C14 evidence is absent specifically for `EXACT_OWNER_STRENGTH_SIGNAL_MISSING`; load semantics resolve to the supported mechanical load path; and no conflicting owner-local signal is present. It copies B4's authorized set count, uses six reps when the personal Strength-rep pattern is absent, encodes target RPE 6.5 and rest, and leaves load state explicitly unresolved.

The distinct B6 authority is `AUTHORIZED_COLD_START_USER_CALIBRATION` with execution authority `REQUIRES_USER_LOAD_INPUT`. It grants shape authority (owner, numeric set count, reps, effort, rest) and user-calibration authority, but no exact-kilogram authority. Materialization preserves that shape and unresolved state. Normal `B8_STRENGTH_V1` still requires its existing exact numeric load authority. The separate `B8_STRENGTH_CALIBRATION_V1` route requires exact B4/B5 identity and dose, the typed unresolved-load state, supported load semantics and UI/confirmation behavior, full materialization, B7 eligibility, provenance, accepted target outcome, collateral safety, and Strength-only scope.

Before load entry, the unresolved set is not an analysis-eligible completed set and does not add training exposure, volume, fatigue, posterior, or history evidence. Confirmation writes the actual user-entered load and observed RPE through the existing `WorkoutSet` and same-owner history path. Later C14 generation takes priority when a usable exact-owner reference exists; C15 is only the bootstrap path. A performed set is an observation, not a literal rep-max label.

## Five C13 REP_RANGE_INCOMPATIBLE dossiers

All five exact rows have numeric B4 restore-baseline authority for two sets, exact B5 canonical Strength selection, known external/mechanical load semantics, and zero usable same-owner observations/reference under C14. C15 produces two sets × six reps at target RPE 6.5, with `USER_CALIBRATION_REQUIRED`; all five B6 authorities are valid and all five materialize fully. Their target outcomes are unchanged. B7 is eligible in all five; B8 decides the route independently.

| Case | Exact selected owner | Load semantics | C15/B6/materialization | B7 | B8 and final route |
|---|---|---|---|---|---|
| `persona0_mixed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `MACHINE_STACK_LOAD` | Available; 2×6 @ RPE 6.5; full | Eligible | Existing Strength V1 comparison still has empty material authority, prescription/provenance, unrelated-mutation, and upstream-consistency blockers; CONTROL |
| `persona0_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | Available; 2×6 @ RPE 6.5; full | Eligible | Same existing Strength V1 comparison blockers; CONTROL |
| `persona2_reviewed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `MACHINE_STACK_LOAD` | Available; 2×6 @ RPE 6.5; full | Eligible | `B8_STRENGTH_CALIBRATION_V1` authorized; routed to calibration Strength |
| `persona3_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | Available; 2×6 @ RPE 6.5; full | Eligible | Exact B5/materialization and ordinary Strength V1 comparison still report unrelated added-owner/non-Strength/provenance/upstream blockers; CONTROL |
| `persona4_mixed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | `EXTERNAL_LOAD` | Available; 2×6 @ RPE 6.5; full | Eligible | Existing Strength V1 comparison blockers remain; CONTROL |

The other available C15 proposal in the 22-case corpus is `persona3_mixed`; it fully materializes but does not pass the separate B8 comparison. No case was made eligible through CONTROL load or prescription data.

## Direction-only cases

Eight Strength cases remain `INTRODUCE_DIRECT_STIMULUS` with B4 `DIRECTION_ONLY`: `persona0_recent`, `persona0_sparse`, `persona2_recent`, `persona2_sparse`, `persona3_recent`, `persona3_sparse`, `persona4_recent`, and `persona4_sparse`. C15 returns `DIRECTION_ONLY_DOES_NOT_AUTHORIZE_C15_SET_COUNT` for each. No numeric sets, repetitions, or calibration authority are invented for these rows.

## Corpus result and route delta

The deterministic census covers 27 input cases: 22 generated and 5 preflight rejected. Before C15, the C14 baseline was CONTROL 21, Strength V1 1, Hypertrophy 0, Combined 0. After C15 it is CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0. Only `persona2_reviewed` advanced to the separately gated calibration route. `reviewed_strength_isolated` remains `B8_STRENGTH_V1`; `reviewed_hypertrophy_isolated` remains CONTROL. No Power, Hypertrophy, RFD, SSC, or task authority was added.

B7 counts moved from provenance-unclosed 14, affected-target-unmet 9, and target-regressed 1 to 11, 9, and 1. Three exact provenance closures become usable in the updated materialization chain; the target-unmet and regression counts do not move. B7/B8 gates were not globally relaxed. B8 authorization occurs once for each separately gated Strength route (the existing Strength V1 positive and the one cold-start route); other B8 reasons remain in the census artifact.

Program Builder Protocol changes `3.51.0 → 3.52.0` and planner runtime changes `0.14.3 → 0.14.4` because generated production programs can now carry a new unresolved-load prescription state and B9 can route the new bounded status. App version stays `0.5.1.5`. Phase order remains `B1-B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9`; normal build accounting remains CONTROL 1 / EXPERIMENTAL 1 / total 2 / third 0, with preflight accounting 0 / 0 / 0 / 0.

The standard coverage output hash changes from C14's `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9` to C15's `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`, matching the intentional one-case route change. The report is generated deterministically at `app/build/reports/c15-cold-start-strength-calibration-census.json` and uploaded by Hosted CI as `c15-cold-start-strength-calibration-census.json`.

## User flow and safety checks

- Plan preview renders `중량 직접 선택` / `Choose weight`, not `0 kg`, and provides the localized compact RPE guidance.
- A loaded exercise opens with a blank editable weight field and prescribed reps/target effort. Entering a valid weight changes the state to explicit load; confirmation remains disabled/rejected while a required load is missing.
- The state survives program save, app-level database reload, date/session linking, and backup/restore paths.
- Same-exercise set copy may reuse a load the user entered earlier in that session. No weight is copied from a different exercise to satisfy calibration.
- The first confirmed user load becomes ordinary same-owner recorded evidence. Subsequent proposal resolution prefers the C14 exact-owner path. No separate permanent calibration history is introduced.
- An unresolved planned set is excluded from completed-work analysis and is not treated as a zero-kilogram Strength observation.

## Verification

- Protocol/runtime: `3.52.0` / `RECORD_BASED_PLANNER_0.14.4_KOTLIN_1`; app `0.5.1.5`.
- B1-B6/selected owners/materialized experimental rows and build ordering are checked in the C15 coverage census; B5 selection itself is unchanged.
- C15 positive/negative tests cover no-invented-load, exact-owner absence, numeric B4 dose, direction-only rejection, Strength-only scope, resolved load semantics, plan save/reload, user entry/confirmation, valid observation storage, and no cross-exercise transfer. Additional checks cover migration, CSV, Community/Cloud compatibility, analysis exclusion, B7/B8 separation, and the existing Strength V1 path.
- Local Gradle environment: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`; `C:\GradleIpc` exists; Gradle 9.3.0 / JDK 21.
- Compile: `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` passed.
- Focused C15 suites passed after fixes. The first full local run exposed two test expectations that needed updating for the additive migration column and schema version 36; both fixes passed focused reruns. The post-fix full run recorded 1,457 completed tests, 0 failures, 0 errors, and 3 skips before Windows JBR `robolectric-nativeruntime.dll` raised an access violation in native SQLite (`SQLiteConnectionNatives.nativePrepareStatement`) and reset Gradle's loopback worker IPC. The full suite is incomplete and is not counted as a pass.
- The closeout regression run on the final implementation code passed `ProgramEffortContractTest`, `WorkoutSessionIdentityTest.frozenSeptember13BackupPreservesIdentityThroughCurrentExportAndCleanRestore`, and `PostGenerationAuthorityFreezeTest`. Legacy progression rows omit the new default load-state keys, while a non-default calibration state still round-trips.
- Hosted implementation CI: [run 37108587556](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37108587556), green on `4d589cb3a371c2e8359332e320d375d7e0ad9a92`. The workflow passed protocol validation, Community/Cloud contracts, whitespace, full unit tests, coverage/report upload, APK assembly, signer validation, and APK upload. Unit results: 2,225 tests, 0 failures, 0 errors, 4 skips.
- Hosted implementation artifacts: `Stimulus-production-coverage` (481,565-byte archive, SHA-256 `c3f2e4ee5a45f38f61077ebc116f6b207f150be21168a994af7446f914a0bc95`); `WhatYouGottaDo-debug-apk` artifact ID `11269051622`, containing `app-debug.apk` at 68,675,787 bytes with SHA-256 `CBEE0B4AB629F47DCEA41D12B548FF426335646F7EBE5C4CA7BCB30251454B96`. Signer validation passed. The uploaded C15 census JSON SHA-256 is `7CB554FFA683FD8FAE4FAFC46B11C8AF410FBFB754829C7AD21AAD4906A523B0`.
- The final documentation-only HEAD is checked by a separate Hosted workflow; its run link and result are recorded in the task closeout alongside the final commit SHA.

## Remaining limits

C15 does not identify a kilogram value when exact owner-local evidence is absent. The user supplies a sensible load for the first session, targeting a comfortable RPE around 6–7 rather than testing a repetition maximum. Cases without numeric B4 set authority remain blocked. Cases that do not pass the independent B7/B8 chain stay on CONTROL even when the C15 shape proposal is valid.
