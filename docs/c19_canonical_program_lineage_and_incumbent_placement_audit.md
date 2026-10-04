# Phase C19 — canonical program lineage and incumbent placement input

## Audit identity

- C18 merge commit / C19 start HEAD: 45163045bad0fa5e1927f32ae17ec900e0cb85ab
- C18 PR #10 pre-merge HEAD: 7700278b872bcd60f26c5171d70710fe940fd70d
- C18 ready-for-review: 2026-10-04 03:16:25 UTC
- C18 merged: 2026-10-04 03:16:49 UTC
- C18 merged-main Hosted CI: run 37173616256, SUCCESS; 2,258 tests, 0 failures, 0 errors, 4 skips
- C19 implementation/test commit / `lastAuditedCommit`: `fa9c88d294bed3275cd7c58e392034e641ac3912`
- C19 final source/test HEAD: `fa9c88d294bed3275cd7c58e392034e641ac3912`; final documentation HEAD is reported by the closeout commit and separately CI-verified.

## Persistence and identity audit

The existing TrainingProgram fields have these semantics:

| Field | Use in C19 |
|---|---|
| id | Physical Room row ID. Used to retrieve the exact program selected for regeneration. It is not the logical lineage. |
| stableKey | Existing unique, persisted program identity. Reused as CanonicalProgramLineageId because normal regeneration retains it, independent new programs receive a new user key, and backup restore preserves it. |
| name | Editable display name. Never used for lineage. |
| createdAt / updatedAt | Record timestamps. Neither determines lineage. C19 does not add a separate generation revision. |
| calendar start date | Not a TrainingProgram placement identity. Program placement remains week/day/order relative. |
| canonicalBuilderProtocolVersion / canonicalPlannerRuntimeVersion | Nullable provenance identifying the canonical source contract. Missing or incompatible versions reject incumbent extraction. |
| existingProgramId | Physical row ID passed from PlanScreen through TrainingViewModel and TrainingRepository before planning. The repository loads the program and its items once and passes an immutable index into generation. |

There is no dedicated in-app “copy program” action in the inspected plan flow. Community import creates a new local program key, so an imported template is an independent program and does not inherit the publisher’s lineage. Backup restore preserves the backed-up program stableKey and C19 metadata. New-program save creates a new stableKey; regeneration updates the existing row and preserves its stableKey. A canceled preview only reads the existing program and does not write or advance lineage.

Program rows already store weekNumber, dayOfWeek, and orderIndex. Date shifting applies those relative positions to workout dates without changing the saved program coordinates. The editor can move persisted rows, but the application does not distinguish manually moved placements from generated placements. C19 therefore treats the currently persisted accepted position as the incumbent regardless of how it was chosen.

## C19A model and compatibility

C19 adds an immutable CanonicalIncumbentPlacementIndex built at the repository/service boundary. Its exact match key is stableKey + selectionRole + week; its placement is day + order. Each source carries the physical program ID, logical lineage ID, source type, builder protocol, and planner runtime. The production source type is CURRENT_PERSISTED_PROGRAM.

The extractor rejects a missing requested program, a missing/mismatched program row, unknown or incompatible canonical source versions, malformed positions, duplicate exact owner-week rows, and legacy rows without an exact selectionRole. It does not infer a role from exercise name, category, training slot, prescription, or stableKey. Same exercise key with a different role cannot inherit the anchor. Rows absent from the newly generated skeleton are not reintroduced.

The lineage source is the existing program stableKey, so no second lineage table or duplicate placement store was added. Room migration 36→37 adds nullable selectionRole and canonical source-version columns. Existing rows migrate with null values: this preserves their contents but does not invent exact canonical roles or historical source versions. The Room schema export is 37.

The program backup snapshot schema advances from 2 to 3 and includes selectionRole plus source versions; backup format remains 14 and restore schema remains 13. Older backups without the new columns restore null roles/source versions and cannot anchor regeneration. New backup/restore round trips retain the role and source contract. Community imports intentionally create a fresh local lineage and omit private canonical lineage metadata.

Generation takes its snapshot before invoking the planner. No DAO or mutable Room entity is passed into placement code, and there is no mid-run database reread. No same-screen concurrent plan editor was found; C19 does not add a revision token or stale-write compare. The current snapshot is immutable for its run. If C20 makes the shadow preference affect saved output, it should first define whether cross-device updates require checking the persisted program’s updatedAt/fingerprint before save.

The current source-version check is exact: only a program created by the active builder/runtime contract supplies an incumbent. Legacy, manual, Community-imported, or older-version programs fail closed until they have been accepted and saved by the current canonical generation. This intentionally limits first-run continuity when old persisted rows lack exact role/source provenance.

## C19B shadow and dataflow

PlanScreen supplies the selected program ID when a personalized generation is requested. TrainingRepository loads that program and its items, builds the immutable index, and passes only that index to PersonalizedProgramPlanningService. The index is consumed after EXPERIMENTAL materialization and before late CONTROL generation. CONTROL is not an incumbent source and is not passed to B1–B6 or canonical placement.

The shadow evaluator joins only exact current skeleton owner-role-week rows. It reports PRESERVE_INCUMBENT only when explicit hard-feasibility evidence is supplied; it reports INCUMBENT_REJECTED_HARD_CONSTRAINT only for an explicit hard-invalid result; absent proof remains NO_DECISION_UNRESOLVED. The live planner path supplies no new feasibility override in C19, so it does not claim live feasibility or modify any generated placement. C19 adds no displacement authority.

The 32-row regression census uses a test-only persisted-program fixture built from the current C18 census output. The C18 output is rendered in the same production-coverage audit test from the C17 full-placement counterfactual, including its capacity, OFI, tissue, spacing, frequency, and program-projection checks. These explicit test fixtures verify that the C19 exact index and shadow retain C18’s established feasibility classifications; they are not a production CONTROL input.

| Case | Rows | Preserve incumbent | Hard-invalid incumbent | Unresolved |
|---|---:|---:|---:|---:|
| persona0_mixed | 6 | 4 | 0 | 2 |
| persona0_reviewed | 4 | 0 | 0 | 4 |
| persona3_reviewed | 8 | 2 | 2 | 4 |
| persona4_mixed | 14 | 6 | 0 | 8 |
| Total | 32 | 12 | 2 | 18 |

No row has proven actual displacement authority. In particular, the two persona3 RDL rows reject their old day-2 positions as hard-invalid; this does not authorize the current EXP day-1 destination. The 18 unresolved rows remain unresolved. Unsupported Power rows are outside this census and remain blocked.

The positive reference persona2_reviewed has 14 shared owner-week rows with both day and order preserved, plus its two authorized calibration rows; it remains B8_STRENGTH_CALIBRATION_V1. C19 does not move those rows or use the comparator as a placement source.

The deterministic machine-readable census is [c19-program-lineage-incumbent-placement-census.json](c19-program-lineage-incumbent-placement-census.json). Its C18 fixture source is marked test-only and states that CONTROL was not used for production extraction.

## Parity and validation

| Check | C18 baseline | C19 result |
|---|---:|---:|
| CONTROL | 20 | 20 |
| Strength V1 | 1 | 1 |
| Strength Calibration | 1 | 1 |
| Hypertrophy | 0 | 0 |
| Combined | 0 | 0 |
| B7 provenance-unclosed | 11 | 11 |
| B7 target-unmet | 9 | 9 |
| B7 target-regressed | 1 | 1 |
| Standard coverage SHA-256 | 55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB | 55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB |

Protocol is 3.54.0, planner runtime is RECORD_BASED_PLANNER_0.14.6_KOTLIN_1, and app version remains 0.5.1.5. The version bump records the new persisted generation-input contract; it does not signal a placement behavior or route change.

The B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9 order remains intact. Normal generation remains CONTROL 1 / EXPERIMENTAL 1 / total 2 / third 0. Preflight rejection remains 0 / 0 / 0 / 0. B7 and B8 predicates, Power, Combined scope, and production placement ranking are unchanged.

Tests cover exact identity and deterministic order, same-key/different-role separation, legacy missing roles, duplicate owner-week ambiguity, stale source contract, removed owners, save/regenerate/new-program lineage semantics, Room migration 36→37, restart persistence, backup/restore compatibility, C18 regression census parity, and CONTROL perturbation invariance. The CONTROL perturbation test injects late comparison fixtures and asserts one EXP build with no additional full CONTROL build; the normal corpus audit separately verifies the standard two-build accounting.

Local Gradle environment: process JAVA_TOOL_OPTIONS was set to -Djdk.net.unixdomain.tmpdir=C:\GradleIpc -Djava.net.preferIPv4Stack=true; the user-level value was -Djdk.net.unixdomain.tmpdir=C:\GradleIpc; C:\GradleIpc existed; Gradle 9.3.0 ran with the repository’s configured JetBrains Java 21 daemon. The first full local attempt stopped at 1,501 completed tests after one stale test assertion pinned Room 36 and a Robolectric native SQLite access violation during WorkManager enqueue. After updating the schema assertion to 37, a full run using a temporary external Gradle init script that restarted test workers every 75 tests passed: 2,268 tests, 0 failures, 0 errors, 4 skips. The temporary init script is not in the repository. The explicit compile command also passed.

Protocol validation passed for 9 families / 36 protocols. Community/Cloud contracts passed 9/9 and `git diff --check` passed. Full local `:app:testDebugUnitTest` passed 2,268 tests, 0 failures, 0 errors, and 4 skips using the temporary worker-restart init script; the explicit compile tasks passed in that run. The latest read uses one Room transaction for the program and item snapshot.

Implementation/test Hosted CI run [37180702110](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/37180702110) passed all steps on `fa9c88d294bed3275cd7c58e392034e641ac3912`: protocol validation, Community/Cloud contracts, whitespace, 2,268 tests (0 failures, 0 errors, 4 skips), coverage upload, APK assembly, signer validation, and APK upload. The standard coverage artifact SHA-256 is `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB`. The C19 census artifact is 21,393 bytes with SHA-256 `4D0B07BDBEB808613F37C798CD1DCF58B24C816C1BB36D9B3FD9D5C904A3A537`. The APK is 68,708,551 bytes with SHA-256 `C3FCDF3B109C11C07CEAA472F275C383BB86D338DEEE7AFB76901A20FCF949DF`.

The final documentation HEAD is verified by PR #11's documentation-closeout Hosted CI check. `lastAuditedCommit` remains the implementation/test SHA above; the audit text and protocol registry are documentation metadata rather than implementation evidence.

## C20 prerequisites

Production now has an exact persisted lineage, explicit canonical role, program-relative placement, and CONTROL-independent incumbent input available before placement. C19 shadow results remain diagnostic and fail closed without feasibility proof. C20 may evaluate a stability repair only after it defines how to provide current hard-feasibility results and how to detect a stale incumbent snapshot before saving. C19 does not preserve incumbents in the real planner, authorize a move, or relax B8.
