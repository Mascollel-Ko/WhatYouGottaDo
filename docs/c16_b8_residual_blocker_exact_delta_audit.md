# C16 — Exact B8 residual blocker audit

## Audit identity and result

- C15 merge SHA / C16 start SHA: `caa3e8d07f52462ec009e31aad406fa0c7b18aea`
- C16 audit implementation/test SHA (`lastAuditedCommit`): `87770f482267bc82d39c62afe7a64e1c11c0b8e3` (Hosted CI run `37115376964`, green).
- Final repository HEAD: recorded in the completion report; the audit document is finalized in a subsequent docs-only commit.
- C15 mainline CI: run `37111700913`, green; 2,225 tests, 0 failures, 0 errors, 4 skips.
- Protocol/runtime/app: `3.52.0` / `RECORD_BASED_PLANNER_0.14.4_KOTLIN_1` / `0.5.1.5`.
- Before and after C16A routes: CONTROL 20, Strength V1 1, Strength Calibration 1, Hypertrophy 0, Combined 0.
- Before and after C16A aggregate B7 counts: `CHANGE_PROVENANCE_UNCLOSED=11`, `AFFECTED_TARGET_REMAINS_UNMET=9`, `TARGET_REGRESSED=1`.
- Standard coverage SHA-256 remains `55CD3C4E9E58B700ED4577A6C0CE0A99FD847F552A334B45FD0815E6FC8825AB` (the established pre-C16 coverage baseline is `818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9`).
- C16 made no production source, B5, B6, C14/C15, B7/B8, or routing changes. The test-side census and classifier do not participate in production decisions.

The four remaining cold-start candidates are not B8 false negatives. Each has the exact authorized C15 calibration prescription and at least one independent residual change. Three cases have shared-owner placement changes without exact B5/B6 material authority; `persona3_reviewed` also adds an out-of-scope Power owner whose B4 target is direction-only and whose owner has no B6 authority. The positive `persona2_reviewed` case has only the authorized calibration addition and passes `B8_STRENGTH_CALIBRATION_V1`.

## What is counted as an expected calibration delta

Every calibration owner is exact `(stableKey, selectionRole)`, selected by B5 for `QUALITY:STRENGTH`, authorized by C15 B6 as `AUTHORIZED_COLD_START_USER_CALIBRATION`, and fully materialized for both expected weeks. Its rows are `2 sets × 6 reps`, target RPE `6.5`, rest `90` seconds, and `USER_CALIBRATION_REQUIRED` load state. The four blocked cases place the new rows as follows:

| Case | Exact calibration owner | Week 1 / week 2 placement | Authorized rows |
|---|---|---|---:|
| `persona0_mixed` | `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH` | day 3 / order 1 | 2 |
| `persona0_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | day 4 / order 1 | 2 |
| `persona3_reviewed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | day 1 / order 2 | 2 |
| `persona4_mixed` | `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` | day 5 / order 1 | 2 |

In the three bench cases, CONTROL has `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` (`2 × 8`, provisional `0.0 kg`) and EXPERIMENTAL replaces that *different role* with the canonical Strength-role owner. The exact C11 non-selection record classifies the old role as `CANONICAL_REPLACEMENT` for `QUALITY:STRENGTH`; the new exact B5 owner has exact C15 B6 authority and full materialization; B7 carries `B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE`. C16 therefore tags those two removals per case as `EXPECTED_C15_CALIBRATION_DELTA`. This classification does not equate stableKey-only identity; it records a verified same-key, different-role canonical replacement.

| Case | Authorized calibration delta rows (including exact role replacement) | Residual material deltas |
|---|---:|---:|
| `persona0_mixed` | 2 | 6 |
| `persona0_reviewed` | 4 | 4 |
| `persona3_reviewed` | 4 | 10 |
| `persona4_mixed` | 4 | 14 |
| `persona2_reviewed` positive reference | 2 | 0 |

The machine-readable census stores every CONTROL and EXPERIMENTAL skeleton row, exact owner identity, set prescription, load state, target RPE, rest, B4/B5/B6 evidence, materialization, C10 origin events and displacement edges, B7 attribution, scope resolution, B8 reasons, and the paired delta ledger. It is emitted by `StimulusProductionCoverageAuditTest` as `c16-b8-residual-blocker-census.json` and uploaded with the Hosted CI coverage/report artifact.

## B8 scope and reason interpretation

The B7 status is `ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW` for all five named cases, with no B7 reason codes and `changeProvenanceClosed=true`. That global B7 status does not mean every changed owner has material B5/B6 target authority. The exact changed shared placement owners below have no B5 selected identity, no exact B6 authority, and no B7 material attribution. Their C10 events show EXP placement assignment (and, in two `persona4_mixed` owners, accepted rebalancer movement), but do not attach a governed B4 target or a causal displacement edge that authorizes those material changes.

For the four blocked cases, `StimulusProductionMaterialScopeResolver` returns `scope=null`, `PARTIAL_PROVENANCE`, with `MATERIAL_PROVENANCE_PARTIAL`; `persona0_reviewed`, `persona3_reviewed`, and `persona4_mixed` also report `REMOVED_OWNER_CHANGE_PRESENT` for the exact bench role replacement, and `persona3_reviewed` additionally reports `UNSUPPORTED_QUALITY_POWER`. The service then calls B8 with the documented fallback `STRENGTH_V1` when scope is unresolved. Consequently, the C15 calibration owner is checked by the ordinary numeric Strength V1 validator instead of the calibration validator. This explains the V1 prescription/materialization reason codes attached to the calibration owner, but it does not create a false negative: each of the four cases independently retains unauthorized material placement changes, and persona3 additionally retains Power material outside Strength Calibration scope.

Exact B8 reason occurrences across the four blocked cases:

| B8 reason | Cases | Exact material basis and interpretation |
|---|---:|---|
| `B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY` | 4 | No material owner passes the fallback Strength V1 checks. The calibration owner has user-input load authority, not the fully encoded load required by Strength V1; the moved legacy owners have no exact B6 authority. Derivative summary reason. |
| `B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY` | 4 | For the calibration owner, this is a V1-vs-calibration validator mismatch caused by unresolved scope. For each moved shared owner, no exact B6 prescription authority exists. The latter is an independent blocker. |
| `B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION` | 4 | The calibration owner is fully materialized under `REQUIRES_USER_LOAD_INPUT`, which ordinary Strength V1 does not accept; changed legacy owners have no matching exact B6 materialization. The family is partly a scope-fallback manifestation and partly an owner-authority gap. |
| `B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED` | 4 | B8's per-owner check finds no B6 material attribution for the changed shared owners. This coexists with B7's global `changeProvenanceClosed=true`; it is a narrower material-authority/target attribution check. |
| `B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION` | 4 | Exact shared owners have different day/order signatures between CONTROL and EXPERIMENTAL and are not authorized in the current cutover scope. This is an independent parity/safety blocker. |
| `B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY` | 4 | The changed legacy rows have no exact material target attribution, so the fallback Strength validator receives no target ID for those owner mutations. Derivative of the ungoverned placement rows. |
| `B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY` | 1 | `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER` is B5-selected for Power, not for the fallback Strength target. The reason is scope-specific; it does not mean that B5 omitted the Power candidate. |
| `B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE` | 1 | The exact added Power rows are attributed to `QUALITY:POWER`, outside the Strength Calibration route. This is a correct rejection. |

The authorized canonical bench-role removal does not produce `CONTROL_OWNER_REMOVAL_NOT_ALLOWED`: B8 recognizes the exact C11 canonical role-replacement attribution. `EMPTY_MATERIAL_AUTHORITY`, prescription/materialization failures, and upstream inconsistency are not separate reasons to relax B8 while the independent placement/Power changes remain.

## Four blocked case dossiers

### `persona0_mixed`

- Route: CONTROL. B4 Strength is `RESTORE_PERSONAL_BASELINE`, numeric authority `PERSONAL_RESTORE_BASELINE`. B5 selects `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`; exact C15 B6 authorizes and fully materializes the two weekly `2 × 6 @ RPE 6.5`, user-selected-load rows. B7 is eligible; target is not regressed.
- Authorized calibration deltas: 2. No exact CONTROL role replacement applies.
- Residual six placement deltas, with all prescription fields unchanged and each occurring in weeks 1 and 2:
  - `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: day 3/order 1 → day 1/order 2; `6 × 8`, 40 kg, `CANONICAL_POSTERIOR_HOLD`.
  - `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL`: day 1/order 2 → day 3/order 2; `3 × 10`, 12.5 kg, `CANONICAL_POSTERIOR_HOLD`.
  - `ex_28347c1f#COVERAGE_CORE_DIRECT`: day 1/order 3 → day 3/order 3; `2 × 8`, provisional zero-load state unchanged.
- Each moved owner is absent from B5's selected identities, has no exact B6 authority, and has no B7 material attribution. C10 emits `INITIAL_WEEKLY_PLACEMENT / PLACEMENT_ASSIGNED / INITIAL_PLACEMENT_POLICY` events for the EXP placements with demand IDs but no target IDs or displacement edges. Those events explain the EXP placement assignment; they do not authorize the CONTROL-vs-EXP change.
- Primary root blocker: `UNAUTHORIZED_PLACEMENT_CHANGE` on those three exact owner-role pairs. Secondary: partial material scope because the moved owners have no target attribution. B8's unrelated-mutation and per-owner material reasons are correct. Disposition: `TRUE_SAFETY_BLOCK`; potential B8 false negative: no.

### `persona0_reviewed`

- Route: CONTROL. B4 Strength is numeric `RESTORE_PERSONAL_BASELINE`. B5 selects exact `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`; C15 B6 gives full `2 × 6 @ RPE 6.5` user-calibration authority for weeks 1 and 2. B7 is eligible; target is not regressed.
- Authorized calibration deltas: 4 total: two new canonical bench rows plus two CONTROL rows of `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` removed through exact C11 canonical replacement. The legacy rows were at day 1/order 1, `2 × 8`, provisional `0.0 kg`.
- Residual four placement deltas, weeks 1 and 2, prescriptions unchanged:
  - `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`: day 2/order 1 → day 1/order 1; `2 × 8`.
  - `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL`: day 4/order 1 → day 2/order 1; `2 × 8`.
- Neither moved owner is selected by B5 or has exact B6/B7 material authority. C10 events record initial EXP placement assignments but no governed target edge. The exact bench replacement is not the blocker.
- Primary root blocker: `UNAUTHORIZED_PLACEMENT_CHANGE` on the RDL and chest-supported row. Secondary: `PARTIAL_PROVENANCE` for those changed shared owners. Disposition: `TRUE_SAFETY_BLOCK`; potential B8 false negative: no.

### `persona3_reviewed`

- Route: CONTROL. B4 has numeric Strength `RESTORE_PERSONAL_BASELINE` and also Power `DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY / DIRECTION_ONLY`. B5 selects `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH` for Strength and `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER` for Power. Strength C15 B6 is exact and fully materialized; the Power target has no numeric B4 dose and no exact executable Power B6 authority. B7 is eligible; Strength target is not regressed.
- Authorized calibration deltas: 4: two bench calibration rows plus the two exact C11-authorized removals of `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` (day 1/order 2, `2 × 8`, provisional zero-load).
- Residual ten deltas:
  - Eight placement changes, all in weeks 1 and 2 with set/reps/load unchanged: `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE` day 6/order 1 → day 4/order 1 (`6 × 8`, 40 kg); `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN` day 2/order 1 → day 1/order 1 (`2 × 8`); `dumbbell_chest_supported_row#COVERAGE_UPPER_PULL` day 4/order 1 → day 2/order 1 (`2 × 8`); `ex_28347c1f#COVERAGE_CORE_DIRECT` day 1/order 1 → day 6/order 1 (`2 × 8`). These four exact roles are not B5-selected and have no exact B6/B7 material authority.
  - Two added rows for `ex_314df428#CANONICAL_STIMULUS_QUALITY_POWER`, weeks 1 and 2 at day 2/order 2, `3 × 5`, from `REVIEWED_BADMINTON_RULE_DECELERATION`. B5 selected this exact owner for Power, but B4 is direction-only and B6 reports the Power model unavailable; there is no exact owner-specific Power authority/materialization. C10 records EXP placement assignment with a demand ID but no target ID. The rows are real EXP material changes and cannot be admitted by Strength Calibration.
- Primary blocker: `UNAUTHORIZED_NON_STRENGTH_CHANGE` / current Strength-only scope cannot admit the Power rows without numeric Power dose and B6 authority. B8 correctly rejects the mixed Power material. Secondary: the four unselected shared-owner placement changes leave material scope provenance partial. Disposition: `CURRENT_SCOPE_LIMITATION`; potential B8 false negative: no. This is not a valid authorized multi-quality cutover, and C16 does not remove the Power rows or expand scope.

### `persona4_mixed`

- Route: CONTROL. B4 Strength is numeric `RESTORE_PERSONAL_BASELINE`. B5 selects exact `barbell_bench_press#CANONICAL_STIMULUS_QUALITY_STRENGTH`; C15 B6 fully materializes the two `2 × 6 @ RPE 6.5` user-calibration rows. B7 is eligible; target is not regressed.
- Authorized calibration deltas: 4: two new canonical bench rows plus two CONTROL `barbell_bench_press#COVERAGE_HORIZONTAL_PUSH` rows removed through exact C11 canonical replacement (day 1/order 1, `2 × 8`, provisional zero-load).
- Residual fourteen placement deltas, all in weeks 1 and 2, with prescriptions unchanged:
  - `barbell_back_squat#STYLE_HEAVY_LOWER_KNEE`: day 1/order 2 → day 3/order 1 (`6 × 8`, 40 kg).
  - `barbell_reverse_curl#COVERAGE_ARMS_BICEPS`: day 3/order 3 → day 1/order 3 (`2 × 8`).
  - `barbell_romanian_deadlift#COVERAGE_POSTERIOR_CHAIN`: day 3/order 1 → day 1/order 1 (`2 × 8`).
  - `cable_rear_delt_fly#STYLE_HEAVY_HORIZONTAL_PULL`: day 3/order 2 → day 1/order 2 (`3 × 10`, 12.5 kg).
  - `dumbbell_lying_triceps_extension#COVERAGE_ARMS_TRICEPS`: day 3/order 4 → day 1/order 4 (`2 × 8`).
  - `ex_28347c1f#COVERAGE_CORE_DIRECT`: day 1/order 3 → day 3/order 2 (`2 × 8`).
  - `ex_5ca7133f#COVERAGE_CALVES`: day 5/order 1 → day 5/order 2 (`2 × 8`).
- All seven exact owner-role pairs are not selected by B5 for these rows, have no exact B6 authorization, and have no B7 material target attribution. C10 records initial weekly placement for each. For core and calves it also records accepted `BOUNDED_DAY_REBALANCER / PLACEMENT_MOVED / REBALANCE_OBJECTIVE` moves (core: initial day 1/order 5 → day 3/order 2; calves: initial day 1/order 6 → day 5/order 2). These accepted move traces identify their stage/cause, but not a B5/B6-governed target or a causal edge that authorizes the final parity change.
- Primary root blocker: `UNAUTHORIZED_PLACEMENT_CHANGE` across seven exact roles. Secondary: partial material scope attribution. Disposition: `TRUE_SAFETY_BLOCK`; potential B8 false negative: no.

## Positive and retained references

- `persona2_reviewed`: exact same cold-start Strength model, `ex_32eb8457#CANONICAL_STIMULUS_QUALITY_STRENGTH`, two fully materialized rows, no canonical-role removal and no residual material delta. Scope resolves to `STRENGTH_CALIBRATION_V1`; B7 is eligible and B8 returns `AUTHORIZED_FOR_BOUNDED_CUTOVER`. This is the positive C15 calibration reference.
- `reviewed_strength_isolated`: retained exact-load reference for `B8_STRENGTH_V1`; C16 did not change its numeric-load requirements.
- `reviewed_hypertrophy_isolated`: remains CONTROL under its separate Hypertrophy blockers; C16 does not change Hypertrophy scope or semantics.

## Aggregate classification and decision

Among the four blocked cases, authorized calibration material changes total 14 rows; residual material changes total 34 rows (32 placement deltas and two Power addition rows). Across unique case-owner-role pairs, 16 placement roles are involved (3 + 2 + 4 + 7), plus one Power owner in `persona3_reviewed`. The three exact bench legacy-role pairs are approved canonical replacements and are not counted as residual. The positive reference has two authorized rows and zero residuals.

| Primary disposition | Cases |
|---|---:|
| True safety block from unselected owner placement changes | 3 |
| Current Strength-only scope limitation (Power has direction-only B4 and no B6 authority) | 1 |
| True program defect established by this audit | 0 |
| Provenance-only case with no residual material change | 0 |
| Comparison-only artifact | 0 |
| Potential B8 false negative | 0 |

The C16B repair was not warranted. No case meets the strict false-negative predicate: the four blocked cases retain exact residual material deltas, and `persona3_reviewed` also includes Power material outside Strength Calibration with no executable Power authority. B8 remains unchanged. No route is opened by C16.

The next phase should investigate the shared-owner placement changes at their originating builder stages and establish whether each accepted placement is governed by a canonical target and exact authority or must preserve the CONTROL placement. It should separately resolve the Power `DIRECTION_ONLY`/no-B6 materialization gap before any multi-quality cutover design. Do not relax Strength Calibration based on the now-explained B8 reason families, and do not delete valid independent target work to fit the current scope.

## Validation

- C16 test-only artifact: `c16-b8-residual-blocker-census.json`, uploaded with Hosted CI coverage/test reports.
- C16A Hosted CI: protocol validator, Community/Cloud contracts, whitespace, full unit tests (2,232 tests, 0 failures, 0 errors, 4 skips), coverage artifact, `assembleDebug`, APK signer validation, and APK upload all passed on run `37115376964`.
- Hosted APK artifact: `app-debug.apk`, 68,675,787 bytes, SHA-256 `5DF67FA797509CBE0F4C94FA9F4DF216B77B4B2847805AEE8FBBFCE529B46366`.
- Local Windows Gradle compile is successful after setting `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\GradleIpc'`; the full local unit-suite result is recorded after it completes. An earlier attempt before setting the process variable failed at daemon startup with `Unable to establish loopback connection`; that was environment setup, not an application failure.
- Production semantics, B1–B6, CONTROL independence, B8 normal Strength V1 policy, and route count remain unchanged. Build accounting remains normal `1 CONTROL / 1 EXPERIMENTAL / 2 total / 0 third builds`; canonical expected failure `1 / 0 / 1 / 0`; experimental expected failure `1 / 1 / 2 / 0`; preflight rejection `0 / 0 / 0 / 0`. Ordering remains `B1–B6 → EXPERIMENTAL → CONTROL → comparison → B7 → B8 → B9`.
- Added C16 assertions cover exact authorized/residual counts and primary dispositions in the real 27-case Room corpus; `C16B8BlockerClassificationTest` covers synthetic calibration-only, unauthorized owner, valid non-Strength scope, provenance-only, comparison-only, and strict false-negative predicates.
- Isolated local `C16B8BlockerClassificationTest`: 7 tests, 0 failures/errors/skips.
- Local full-suite attempt: 1,465 tests completed, 0 failures, 0 errors, 3 skips before the Windows JBR 21 worker crashed in native `robolectric-nativeruntime.dll` while preparing SQLite (`SQLiteConnectionNatives.nativePrepareStatement`, `EXCEPTION_ACCESS_VIOLATION`). The remaining suite did not complete; Hosted Linux CI completed all 2,232 tests green.
