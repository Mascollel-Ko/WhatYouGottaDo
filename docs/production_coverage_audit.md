# Post-B15 production coverage and fallback audit

Baseline: `ff2148d381b22ae107957e790373ac5c79e988fa`. Protocol remains `3.50.0`.
This is an internal diagnostic and test audit. Scope selection, B6 prescriptions,
B7/B8 gates, B9 routing, build invocation boundaries and persistence are unchanged.

## Coverage by stage, from code

`R` means prescription-classified evidence, `P` means canonical capability-proxy
evidence, `D` means a diagnostic decision/target row (including NONE/UNRESOLVED),
`C` means conditional on target strategy and an eligible catalog candidate, and
`X` means executable authority with all existing gates satisfied. Presence in a
row is not evidence that the quality can cross the production gate.

| Stage | Strength | Hypertrophy | Power | RFD | SSC | Muscular endurance | Cardio | Mobility |
|---|---|---|---|---|---|---|---|---|
| B1 Need | R; response model | R; no response model | P | P | P | P | P | P |
| B2 History | R | R | P | P | P | P | P | P |
| B3 Decision | D | D | D | D | D | D | D | D |
| B4 Target | D | D | D | D | D | D | D | D |
| B5 Selection | C | C | C | C | C | C | C | C |
| B6 Executable | X | X | No | No | No | No | No | No |
| B7 Attribution | B5/B6 | B5/B6 | B5 only | B5 only | B5 only | B5 only | B5 only | B5 only |
| B8 Authority | X, alone or S+H | X, alone or S+H | No | No | No | No | No | No |
| B9 Production | X, permitted mode | X, permitted mode | No | No | No | No | No | No |

Code anchors (paths relative to repository root):

- `app/src/main/java/com/training/trackplanner/data/ExercisePhysicalQuality.kt`,
  `TrainableQuality`: exactly the eight qualities above.
- `data/personalized/AthleteStimulusNeed.kt`, `AthleteStimulusNeedEngine.analyze`:
  maps all enum entries; only Strength has a response calculation. Non-Strength
  response stays `INSUFFICIENT_EVIDENCE`. `qualityRelevance` handles all eight.
- `data/personalized/StimulusEvidenceSemantics.kt`, `evidenceBasisForQuality` and
  `qualityObservationDisposition`: S/H require reviewed prescription realization;
  the remaining qualities use canonical capability evidence, not executable dose.
- `data/personalized/LedgerBackedQualityDoseHistory.kt`, `analyze`: all eight
  receive weekly evidence, bands and observability. Generic court observations
  remain context and do not become quality dose. Unclassified sources cannot
  establish an exact numeric baseline.
- `data/personalized/StimulusTrainingDecisionPortfolio.kt`, `build`, and
  `StimulusTargetPlan.kt`, `build`: preserve every incoming quality. B4 numeric
  authority depends on personal baseline observability; a target row may have no
  minimum target or unresolved authority.
- `data/personalized/StimulusCandidateSelection.kt`, `build`, `selectionAllowed`,
  `eligibleCandidates`: accepts all quality targets and task targets, constrained
  by strategy, direct catalog capability, equipment, exclusions and recovery.
  Only S/H have prescription-compatible history ranking. A candidate identity
  does not grant prescription authority.
- `data/personalized/StimulusPrescriptionRealization.kt`, `resolveTarget`, and
  `StimulusPlannedPrescriptionResolver.kt`: explicitly bound to S/H. Other
  qualities produce `REALIZATION_MODEL_UNAVAILABLE`; task identity selection is
  also not task prescription authority.
- `data/personalized/StimulusExperimentalReadiness.kt`, `attributeChanges`:
  B5 additions inherit covered target IDs without a quality whitelist; B6 shared
  prescription changes inherit only executable authorization target IDs. Reuse,
  removal and unexplained changes remain separately attributed.
- `data/personalized/StimulusProductionCutoverAuthority.kt`, scope enum and audit,
  and `StimulusProductionRouter.kt`, resolver and router: only S, H and S+H;
  provenance, materialization and B9 identity gates remain required.

The abbreviated `data/` paths above have prefix
`app/src/main/java/com/training/trackplanner/`.

## Scope and fallback diagnostics

`resolve` retains its exact nullable production contract. `resolveDetailed`
observes the same comparison and carries that scope unchanged, along with an
exclusive status, all material quality IDs, owner identities, missing owner
provenance, unknown target IDs and sorted secondary reasons. An unsupported
quality can also have an unknown target ID; both reasons survive. S+H plus
another quality adds `THIRD_QUALITY_PRESENT`. Removal-only changes are reported
as ambiguous scope rather than no material change.

The service still uses `scope ?: STRENGTH_V1` for the existing B8 audit. The
generation result's internal `diagnostics.scopeResolution` is independent of
that B8 scope, so a failed resolution is not reported as successful Strength
resolution. Known upstream failures retain the outer reason plus the typed
canonical reason and detail. Cancellation/unexpected failures still propagate.

For CONTROL, primary-stage precedence is: explicit CONTROL policy, upstream
failure, B6 materialization integrity failure, no material, failed scope
resolution, failed authorization for a material owner, B7 readiness, B8 authority,
then B9 contract. Exactly one primary is counted. This is a reproducible earliest
observed blocking boundary, not a claim that later failures have no other causes.
All B7/B8/B9 reasons remain available. Diagnostics never select a program.

## Corpus method and limits

`StimulusProductionCoverageAuditTest` calls real Room-backed repository preflight
and, when preparation succeeds, `generatePreparedPersonalizedProgramEvaluation`. Each case has a fresh seeded
database and fixed cutoff `2026-09-20`; results are not copied, repaired or supplied
with fabricated B8 authority. The corpus combines five intent/context settings
(Strength, Hypertrophy, mixed, badminton performance, low/non-badminton) with five
history settings (none, one session, recent 2–3 weeks, reviewed history across an
eight-week window, mixed repetition/quality history). Weekly days span 2–5,
session time spans 30/60/90 minutes, and equipment spans free weights,
machine/cable and unrestricted mixed equipment.

These 25 cases sample the axes rather than exhaust their Cartesian product. Two
isolated-owner reviewed-history cases retain the existing B14 fixture method as
reference cases and are labeled separately. Seeded posterior history is a test
fixture, not a population sample or empirical clinical outcome.

The older B15 combined success fixture composes separate service comparisons and
supplies B8 authority. It remains a routing contract test, but its constructed
successes are excluded from this real-service coverage aggregate.

The initial corpus run exposed the existing no-history precondition in
`PlanningHistorySnapshotBuilder.build`: it requires at least one confirmed set.
The audit records that precise preflight rejection separately, with no program,
no B6/B7/B8/B9 result and no builds. It does not seed substitute history to force
a CONTROL result. This is an input boundary, not a routing defect; production
code and the precondition remain unchanged. Unexpected exceptions fail the test.

Every record includes targets (strategy and numeric authority), material
qualities/owners, resolution, B6 resolutions/authorizations/conflicts, B7/B8/B9,
selected program, primary/secondary reasons and actual build counts. Records and
summaries are sorted; reversed input order must render identically. The report
is written to `app/build/reports/stimulus-production-coverage.txt`.

The executed corpus produced 27 total cases: 22 generated cases and 5
`PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY` cases. Among generated cases, B9
selected Strength 3 times, Hypertrophy once, combined zero times and CONTROL
18 times. The exclusive primary fallback counts were `NO_MATERIAL_CHANGE 14`,
`SCOPE_RESOLUTION 3` and `B6_EXECUTION_AUTHORITY 1`; no upstream, B7, B8 or B9
primary failure occurred in this corpus. The remaining four generated cases
were successful production routes. Every generated case recorded
`CONTROL=1`, `EXPERIMENTAL=1`, `TOTAL=2`, `THIRD=0`.

Unsupported material blockers were observed for POWER (2) and
`REACTIVE_STRENGTH_SSC` (2). RFD, muscular endurance, cardio and mobility had
zero material blockers. These counts come from governed material provenance;
the capability target rows for unsupported qualities were not counted as
blockers. The report contains the complete per-case B1–B9 record.

An unsupported blocker requires a CONTROL result and a governed material scope
that fails specifically for unsupported quality. A need or target row alone is
never a blocker. Per-quality blocker counts may overlap in a multi-quality case;
exclusive primary-stage counts never overlap.

## Legacy dependency audit

| Dependency | Classification | Code evidence and implication |
|---|---|---|
| CONTROL is built first | INPUT_SEED_DEPENDENCY | `generatePreparedProduction` builds CONTROL before experimental evaluation. Recognized failure can stop before the second invocation; successful comparison paths build 1+1. |
| Canonical target plan available only through CONTROL | REMOVED_CANONICAL_DATA_HOST_DEPENDENCY | `CanonicalStimulusPlanningResult` owns the B1/B2/B3/B4 result and is passed explicitly to B5/B6. CONTROL still receives `stimulusTargetPlanShadow` as a compatibility mirror, but canonical evaluation does not read that mirror. |
| CONTROL canonical compatibility mirror | CANONICAL_DATA_MIRROR_DEPENDENCY | Existing diagnostics, persistence and UI-facing decision payloads may continue to expose `control.personalizedDecision.athleteStimulusNeedProfile`; it is a compatibility representation and is not the B5/B6 source of truth. |
| CONTROL request mirror | SAFETY_COMPARATOR / PERSISTENCE_MIRROR | `GeneratedProgramSkeleton.request` remains for compatibility, persistence and parity diagnostics; canonical B5/B6/EXPERIMENTAL planning does not read it. |
| B5/B6/EXPERIMENTAL request | REMOVED_CONTROL_REQUEST_DEPENDENCY | `resolvePreparedProgramRequest(...)` resolves the complete request and frequency provenance from preflight request/constraints plus prepared state, gaps, block intent, frequency evidence and horizon. The prepared orchestration value passes it explicitly to B5 and B6; normal CONTROL generation must match it or enter the typed fail-closed boundary. |
| B5 identity selection | INPUT_SEED_DEPENDENCY | Selector consumes CONTROL stable keys/direct-capability identities to avoid additions and to form reuse/reference traces. It is not merely a post-generation comparison. |
| B6 current prescriptions | PRESCRIPTION_BASELINE_DEPENDENCY | `control.items` creates the exact owner/role prescription table passed into authorization and conflict localization before EXPERIMENTAL build. |
| B7 collateral and B8/B9 safety | SAFETY_COMPARATOR_ONLY | B7 compares prescriptions, removed owners, target distances and fingerprints; B8 checks schedule/owner parity; B9 selects original CONTROL or EXPERIMENTAL. |

These anchors are in `PersonalizedProgramPlanningService.kt`,
`StimulusCandidateSelection.kt`, `StimulusPrescriptionMaterialization.kt`,
`StimulusExperimentalReadiness.kt` and `StimulusProductionCutoverAuthority.kt`.
The B5 seed and B6 baseline remain intentional dependencies; no dependency is labeled
`TEMPORARY_MIGRATION_DEPENDENCY` merely on that assumption. CONTROL remains an
essential seed/baseline input and a safety comparator/rollback program, while its request field
is a compatibility mirror and diagnostic/comparator input only. Its compatibility mirror is also
no longer the canonical B1-B4 source of truth.

## Phase C1 — canonical planning ownership and data-flow independence

The C1 seam keeps protocol `3.50.0` and production behavior unchanged. Before C1,
`generatePrepared()` built CONTROL first, calculated B1/B2/B3/B4, mirrored the result into
`control.personalizedDecision.athleteStimulusNeedProfile`, and B5/B6 recovered the target
plan from `control.personalizedDecision.athleteStimulusNeedProfile.stimulusTargetPlanShadow`.

The canonical path now calculates the following typed result once from the shared
prepared snapshot and planning state. The pure computation takes no CONTROL argument.
Production invokes it after the first CONTROL build so an expected canonical failure
can return the already-built CONTROL through the existing evaluation boundary:

```text
PlanningHistorySnapshot + AthletePlanningState
  -> AthleteStimulusNeedEngine (B1 needs)
  -> LedgerBackedQualityDoseHistoryAnalyzer (B2 history; shared legacy dose for comparison)
B1 + B2 -> StimulusTrainingDecisionPortfolioEngine (B3)
B3 + B2 -> StimulusTargetPlanEngine (B4)
  -> CanonicalStimulusPlanningResult
       - athleteStimulusNeedProfile
       - qualityDoseHistory
       - decisionPortfolio
       - targetPlan
```

CONTROL is still built and remains the source for the B5 identity seed and B6
current-prescription baseline. After CONTROL materialization,
its final audit is attached to the typed result as comparator diagnostics, and the existing
`personalizedDecision` shadow fields are populated as a persistence-compatible mirror.
B5 and B6 consume the explicit `CanonicalStimulusPlanningResult.targetPlan`; they do not
read the CONTROL mirror. B7/B8/B9 continue to consume the existing comparison and CONTROL
rollback/comparator fields. No CONTROL, EXPERIMENTAL or third-build boundary changed.

Ownership and use, traced in `PersonalizedProgramPlanningService`:

| Value | Producer / owner | Downstream role |
|---|---|---|
| B1 need profile | `buildCanonicalStimulusPlanningResult` calls `AthleteStimulusNeedEngine.analyze(snapshot, state)`; independent result owns it | Input to B3; mirrored for diagnostics/persistence |
| B2 dose history | Same seam calls `LedgerBackedQualityDoseHistoryAnalyzer.analyze(snapshot, state, legacyDoseHistory)` | Input to B3/B4; includes dose evidence and baselines |
| B3 portfolio | Same seam calls `StimulusTrainingDecisionPortfolioEngine.build(B1, B2)` | Input to B4; legacy comparison is mirror-only diagnostics |
| B4 target plan | Same seam calls `StimulusTargetPlanEngine.build(B3, B2)` | Explicit actual B5 selection and B6 authorization/realization input |
| `FinalStimulusNeedAudit` | Audits materialized CONTROL using its items and snapshot | Diagnostics/comparator input, never upstream input to B1–B4 |
| `controlProgramAudit` | B4 plus final materialized audit and horizon | Read-only comparator diagnostics on the independent result, also mirrored |
| `stimulusTargetPlanShadow` | `generatePreparedWithCanonicalPlanning` attaches the same result plus legacy/control comparisons | Compatibility mirror only; B5/B6 do not recover canonical inputs from it |

Before C1, `generatePrepared` owned the local B1–B4 values only until it returned
CONTROL. Downstream consumers then recovered B4 from the nested mirror. Now
`CanonicalPreparedProgram` pairs CONTROL with a separately owned canonical outcome;
the canonical aggregate itself has no skeleton, program key, routing decision or
mutable authority. B6/B8 require both the explicit result and existing CONTROL;
there is no optional mirror fallback or hidden extra CONTROL build.

Expected `StimulusCanonicalEvaluationFailure` during independent computation is
retained as a typed outcome until production can rethrow it inside the existing
canonical failure boundary. It returns the built CONTROL and completes progress once.
That failed computation has no canonical mirror to attach. Standalone generation
rethrows; cancellation and unexpected exceptions propagate directly in all paths.
There is no broad production catch and no retry or additional program build.

B1–B4 are computed once per production generation. The legacy dose-history analysis
is shared between B2 comparison and legacy diagnostics, avoiding a second analysis.
The existing experimental path still rereads preferences/rebuilds snapshot, state,
gaps, intent and frequency, and performs its own final materialization audit. Those
residual computations predate C1; C2 changes only the request source while retaining
the seed and baseline dependencies.

`CanonicalStimulusPlanningIndependenceTest` compares complete B1–B4 data classes
against the pre-C1 engine chain on Strength, H, S+H, badminton/performance, sparse,
and reviewed eight-week inputs. It also compares the independently computed result
against all compatibility mirror fields, normalizing only attached diagnostics.
Equality includes evidence, reasons, unresolved markers, target quality, strategy,
priority, numeric authority and weekly ranges. This is a refactor parity oracle,
not a claim that engine policy is permanently frozen.

Separate regressions remove the entire decision, need profile, or B4 mirror and
compare B5 selections, B6 authorizations and final program fingerprints. A deliberately
Strength-only mirror with explicit H-only targets verifies source of truth. Other
tests cover a single production computation, real 1/1/2/0 build accounting,
monotonic progress with one completion, typed computation failure, cancellation,
and unexpected argument/state exceptions. The unmodified 27-case routing corpus
is rerun separately and compared with the pre-C1 hosted artifact.

## Phase C2 — canonical request independence

C2 keeps protocol `3.50.0`. Before C2, production B5 selection and the B6/EXPERIMENTAL
builder read `control.request`, so CONTROL acted as the request source as well as the
intentional identity seed and current-prescription baseline. C2 resolves one complete
`ProgramSkeletonRequest` plus `PlanningFrequencyProvenance` from the frozen preflight request
and constraints, prepared state, gaps, block intent, frequency evidence and selected horizon.
That request bundle belongs to `CanonicalPreparedProgram` orchestration, not to
`CanonicalStimulusPlanningResult`.

```text
preflight.request + constraints + prepared state/gaps/intent/frequency/horizon
  -> resolvePersonalizedRequest(...)
  -> ResolvedPreparedProgramRequest(request, frequencyProvenance)
       -> unchanged CONTROL builder arguments
       -> B5 candidate selector
       -> B6 selector and EXPERIMENTAL builder
       -> B6 horizon audit
```

Normal CONTROL and EXPERIMENTAL requests are compared as complete data classes. A mismatch
between the built CONTROL request and the prepared resolved request is a typed
`RESOLVED_REQUEST_PARITY` failure and uses the existing CONTROL fallback boundary. B5 and B6
have no nullable request fallback and never recover request shape from CONTROL. The persisted
`GeneratedProgramSkeleton.request` remains intact; remaining `control.request` reads are confined
to B7/production diagnostics and safety/comparator fingerprints. CONTROL stable keys/direct
identities still seed B5, `control.items` still supply the B6 prescription baseline, and B7/B8/B9
continue to use the same comparison, rollback object and routing boundary.

Request regressions assert whole-request parity over the existing 27-case Strength, H, mixed
and badminton corpus (including weekly days 2–5 and session lengths 30/60/90), plus explicit and
inferred weekly-day/duration combinations. A conflicting CONTROL request intentionally changes
goal, days, duration, minutes, equipment and exclusions while B5 is forced to search for its
isolated H owner; assertions verify B5 selects from the upstream request and B6 uses its exact
days, duration, session length, frequency provenance and audit horizon. Corpus report aggregates,
program routing decisions and build counts remain byte-for-byte comparable to the pre-C2 baseline.

Hosted Android CI run [`36544683613`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36544683613)
passed with 337 JUnit XML files, 2,138 tests, 0 failures, 0 errors and 4 skips. Both new request
regressions passed. The 27-case report is byte-identical to the pre-C2 `main` artifact from run
`36539189749` (SHA-256 `67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`):
22 generated, 5 preflight rejects; CONTROL 18, Strength 3, Hypertrophy 1, combined 0; primary
fallback no material 14, scope 3, B6 execution 1; routes and 1/1/2/0 build counts unchanged.
Protocol documentation validation, Community/Cloud contracts, APK assembly and CI signer
verification also passed. The generated APK is SHA-256
`B377A50A6E11EBFA7ED6A5CD5E0741A3FE0BC9BFE5924ACF1502BD97FD6F5BE7`.

## Phase C3 — typed B5 incumbent identity seed

C3 keeps protocol `3.50.0`. Before C3, `StimulusTargetCandidateSelector` received the
complete CONTROL `GeneratedProgramSkeleton` and read only its incumbent stable-key set.
After C3, `StimulusIncumbentIdentitySeed.fromControl(control)` performs the explicit
one-way projection `CONTROL → typed seed → B5`. The selector receives neither the
generated program nor a `ProgramSkeletonItem`.

The seed holds owner identities `(stableKey, selectionRole)`. Repeated weekly rows for
one exact pair are deduplicated, while distinct roles sharing a stable key remain
separate. This retains B6/B7 provenance without passing prescription data to B5. The
selector derives the same stable-key set as before, so `controlDirectCapabilityIdentities`
remains a sorted list of unique stable keys. Canonical snapshot/catalog relations still
decide direct quality capability; request and eligibility gates remain separate inputs.

This is a representation boundary only. The CONTROL-derived incumbent seed remains the
B5 source, so B5 seed source independence is **NO**. B6 still builds
`currentPrescriptions` from the CONTROL items and its prescriptions. B7/B8/B9 retain
the same CONTROL comparator, fingerprints and rollback object. B1–B4 continue to use
`CanonicalStimulusPlanningResult`, while B5/B6 requests continue to use
`ResolvedPreparedProgramRequest`.

The C3 regressions compare the complete selection plan and trace against the prior
CONTROL stable-key projection, run B5 after discarding CONTROL, reject a blank stable key
and duplicate owner pair, and preserve a shared stable key with distinct roles. The
identity type has no additional metadata fields, so conflicting duplicate metadata
cannot be represented. Existing empty `selectionRole` values remain valid under the
current model.

The local real Room/service coverage report remains byte-identical to the C2 baseline:
SHA-256 `67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`. It retains
27 cases (22 generated, 5 preflight rejects), routing totals CONTROL 18, Strength 3,
Hypertrophy 1, combined 0; primary fallback counts no material 14, scope 3 and B6
execution 1; each generated case records `CONTROL=1`, `EXPERIMENTAL=1`, `TOTAL=2`,
`THIRD=0`. Production fingerprints and routing are unchanged.

The next smallest independence seam is the B5 seed source: B5 now consumes an identity-only
contract, while B6 still needs the richer exact CONTROL prescription baseline. C3 does not
implement that next seam or change the B6 baseline.

### C3 hosted verification

Hosted Android Debug Build run [`36560234974`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36560234974)
passed on implementation/test commit `58bda835ee8fbba4cda9e533ed3544d5ec5f4def`. The full
unit-test task passed with 337 JUnit XML files, 2,143 tests, 0 failures, 0 errors and 4 skips;
protocol validation, Community/Cloud contracts, whitespace checks, coverage upload, APK assembly
and CI signer verification also passed. Its coverage report is byte-identical to the C2 baseline
(SHA-256 `67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`). The coverage
artifact is ID `11028614936` (285,505 bytes; ZIP digest
`sha256:782baee2a6256aac3b61db1c9353b2e8b62c0de975a392703bffdee3a15c87f9`). The APK artifact
is ID `11030400264` (64,939,706 bytes; ZIP digest
`sha256:5862667e46c62f0184e4fa99d1368969c69e4c836d4dbc89c14ffa10715d3b2c`); the downloaded
`app-debug.apk` SHA-256 is `6E821134E31D516F33C07B046088E17F96C55655759AD3AED861064579FD6456`.

## Phase C4 — finalized builder owner state for B5 seed

C4 started from verified `main` `e5efeaef3b10d060d8d58772874f4824a9f00b32` and its implementation/test
commit is `b2543ed0f1ab830273d099e3badf84676256421e`. It retains protocol `3.50.0` and changes only the source boundary for B5's typed
incumbent seed. `PersonalizedProgramBuilder.buildWithArtifacts()` produces the
final CONTROL program and seed together from the same finalized `(stableKey,
selectionRole)` owner state, after completion, placement/rebalancing, frequency
expansion, `PostSplitWeeklyReflow` and authorization validation. The compatibility
`build()` method delegates to this seam once and returns its program.

The regular prepared-production path stores the builder seed in
`CanonicalPreparedProgram` and explicitly passes it through B5, B6, B8 and B9.
There are zero `fromControl()` projections in normal production. The one remaining
service projection is confined to the named injected-CONTROL test adapter. The B6
prescription baseline still reads `control.items`; B7/B8/B9 comparisons,
fingerprints, rollback and routing remain unchanged. C1 canonical planning, C2
request independence and C3's seed-only selector contract remain covered.

Parity tests compare the builder artifact to the legacy final-CONTROL projection
across multiple personas, a real prepared-service fixture and a frequency-expanded
builder case. The frequency test verifies expansion has completed before the exact
owner set comparison. The source-boundary test verifies capture follows the
post-split reflow call. Owner identity remains the exact pair: repeated rows of one
pair collapse, while two roles under one stable key remain separate.

Local verification on 2026-09-29 used process/user
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`; the path exists.
The Gradle launcher was Temurin 17.0.20.1 and the actual Gradle/test JVM was
JetBrains JBR 21.0.11+1-1163.116-jcef. Initial compile smoke, post-change main and
unit-test compilation, and the focused C4/C1/C2/C3/B5-B9/service/coverage suite
passed. The focused suite contained 129 tests with 0 failures, 0 errors and 0 skips.
The local 27-case coverage report is byte-identical to the C2 baseline at SHA-256
`67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`.

The full local `:app:testDebugUnitTest --no-daemon` run is incomplete, not a pass.
It exited after 4m33s when the Windows JBR test worker hit `EXCEPTION_ACCESS_VIOLATION`
in `robolectric-nativeruntime.dll+0x5c22`; Gradle then reported the worker connection
reset. The partial XML corpus had 259 suites, 1,430 completed tests, 0 assertion
failures, 0 errors and 3 skips. This is a Robolectric native crash, not an
application assertion failure. Hosted Linux CI is authoritative for the complete
suite.

### C4 hosted verification

Hosted Android Debug Build run [`36576447742`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36576447742)
on implementation/test commit `b2543ed0f1ab830273d099e3badf84676256421e` passed in
13m18s. Protocol validation (9 families, 36 protocols), Community/Cloud contracts
(9/9), whitespace checks, all debug unit tests, production coverage upload, APK
assembly, signer verification and APK upload passed. Downloaded JUnit results
contain 338 XML files, 2,146 tests, 0 failures, 0 errors and 4 skips.

The hosted production report is byte-identical to the C2 baseline at SHA-256
`67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`. It retains 27
cases (22 generated, 5 preflight rejects), source totals CONTROL 18, Strength 3,
Hypertrophy 1, combined 0; primary fallback counts no material 14, scope 3 and B6
execution authority 1. Every generated case retains `CONTROL=1`, `EXPERIMENTAL=1`,
`TOTAL=2`, `THIRD=0`; routing and fingerprints are unchanged.

Coverage artifact `Stimulus-production-coverage` is ID `11036944398`, 286,579 bytes,
archive SHA-256 `ef6064eb9cd49b259c9f46e1f42b296b7223160e0371db7c5013de682ffc88be`.
APK artifact `WhatYouGottaDo-debug-apk` is ID `11037894237`, 64,939,134 bytes,
archive SHA-256 `a62b72e0bedc723bfacabaca511555c5ef85fde285fa0f33c2f69f2afc9c9e9f`.
Downloaded `app-debug.apk` SHA-256 is
`5B7B65B9C98E98ED58654F37091F2239D056794B3DE24C0A493BE3D1C03407BD`.

## Phase C5 — finalized builder prescription source for B6

C5 started from verified `main` `15da3b79bc33d912c1c6ca14a2293020e5a32c24` and was implemented in
`8d51e65bdc68960b86c46fa0b3f7829963085cfa`. Protocol remains `3.50.0`. The build artifact now
contains a typed `StimulusIncumbentPrescriptionBaseline` alongside the program and B5 identity
seed. Both canonical values project from the builder's finalized prescription rows after the
post-split reflow, authorization checks, and bounded-demand observation. Normal B6 receives the
required baseline explicitly; it does not read `control.items` to create that baseline or fall
back to CONTROL. The one `fromControl()` baseline compatibility projection exists only in the
named injected-CONTROL test adapter.

The baseline preserves the old `distinct().toList().toMap()` semantics exactly, including
insertion order, removal of exact duplicate pairs, separate `(stableKey, selectionRole)` entries,
and last-distinct-value wins for conflicting prescriptions under one owner. `PlannedPrescription`
equality preserves text, ordered set fields (including target effort), rest seconds, and weight
source. Authorization, realization, conflict localization, and `PRESERVE_CONTROL_OWNER` tests
compare typed and legacy projections. The full 29-persona builder parity matrix and a real
frequency-expansion case verify artifact equality and owner-role consistency. Normal production
build accounting remains CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0. C1-C4 boundaries,
comparator/rollback behavior, and routing remain unchanged.

Local verification on 2026-09-30 used user
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`; the path exists. Gradle 9.3.0
used Temurin 17.0.20.1 as launcher and JetBrains JBR 21.0.11+1-1163.116-jcef for the Gradle/test
JVM. Initial compile smoke, post-change `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin
--no-daemon`, and the focused C5/C4/B6/service/29-persona/coverage suite passed. The focused run
contained 68 tests, 0 failures, 0 errors, and 0 skips. Protocol validation passed (9 families,
36 protocols), Community/Cloud contracts passed 9/9, and `git diff --check` passed. The local
27-case coverage file has SHA-256
`67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`.

The full local `:app:testDebugUnitTest --no-daemon` attempt is incomplete, not a pass. It exited
after 4m51s when the Windows JBR test worker hit `EXCEPTION_ACCESS_VIOLATION` in
`robolectric-nativeruntime.dll+0x5c22`; Gradle then reported the worker connection reset. Partial
JUnit output contained 259 XML suites and 1,430 completed tests, 0 assertion failures, 0 errors,
and 3 skips. This matches the previously observed Robolectric native crash; hosted Linux CI is
the full-suite result.

### C5 hosted verification

Hosted Android Debug Build run
[`36621916521`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36621916521) passed
on implementation/test commit `8d51e65bdc68960b86c46fa0b3f7829963085cfa` in 12m14s. Protocol
validation (9 families, 36 protocols), Community/Cloud contracts (9/9), whitespace checks,
all debug unit tests, coverage upload, APK assembly, signer verification and APK upload passed.
Downloaded JUnit results contain 339 XML files, 2,148 tests, 0 failures, 0 errors and 4 skips.

The hosted 27-case report matches the C2 baseline byte-for-byte at SHA-256
`67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`: 27 total, 22 generated,
5 preflight rejects; source totals CONTROL 18, Strength 3, Hypertrophy 1, combined 0; primary
fallback counts no material 14, scope 3 and B6 execution 1. Generated cases retain
`CONTROL=1`, `EXPERIMENTAL=1`, `TOTAL=2`, `THIRD=0`; routing and fingerprints are unchanged.

Coverage artifact `Stimulus-production-coverage` is ID `11059107245`, 288,443 bytes, archive
SHA-256 `4d43bd4c9eef70b527b51e4a8d02d4d52cf79256528d67a07d2f74aa88c8773d`. APK artifact
`WhatYouGottaDo-debug-apk` is ID `11059002504`, 64,938,484 bytes, archive SHA-256
`83e2db8560d99e36c7af9213e77ee2f582500bfebd98b280ac4682fa24cb2401`; downloaded `app-debug.apk`
is 68,526,771 bytes with SHA-256
`D6E15A0AE44DAB25ED84F3417A2DB0621147977C4A989D31B0C4D560D9D1044A`.

## Phase C6 — B6 post-build realization source independence

C6 started from verified `main` `ed56b611ff939ad722d7a82b093ef977d1e32ac5`; its implementation/test
commit is `783b94e628bcf1da5e949f3c8d8c3a0a55a000c0`. Protocol remains `3.50.0`. After the
EXPERIMENTAL builder returns, normal B6 realization consumes the B5 selection plan, C4 incumbent
identity seed, C5 prescription baseline, and EXPERIMENTAL rows. It no longer reads
`control.items`, `control.request`, or `control.personalizedDecision` to assemble realization
inputs. Existing CONTROL comparison, fingerprint, safety, routing, and rollback uses remain.

`StimulusRealizationPrescriptionInputs` restores the exact legacy owner set and prescription map.
Direct stable-key traces resolve through `StimulusIncumbentIdentitySeed` to full
`(stableKey, selectionRole)` identities; finalized baseline-key order retains the legacy direct
owner insertion order. Experimental materializations are inserted first, then the C5 incumbent
map is applied last, so the incumbent still overrides a colliding experimental prescription.
New experimental-only owners survive, and map key order matches the original combined-row
`associateBy()` projection.

The new parity test compares owner-set order, merged map keys/order/values, incumbent-overrides-
experimental behavior, new-owner retention, role separation under one stable key, and complete
realization-plan equality for Strength, Hypertrophy, combined, and direct-incumbent cases. The
source-boundary test verifies normal B6 passes the typed seed, baseline, and EXPERIMENTAL rows and
does not read `control.items` or `control.request` in the realization path. Authorization and
build accounting are unchanged at CONTROL 1 / EXPERIMENTAL 1 / TOTAL 2 / THIRD 0.

Local verification on 2026-09-30 used the process setting
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\GradleIpc`; the directory exists. Gradle 9.3.0
used Temurin 17.0.20.1 as launcher and JetBrains JBR 21.0.11+1-1163.116-jcef for its daemon and
test worker. Pre-change compile smoke and post-change `:app:compileDebugKotlin
:app:compileDebugUnitTestKotlin --no-daemon` passed. The focused C1-C6, B6-B9, service, and
coverage suite passed 117 tests with 0 failures, 0 errors, and 0 skips. Protocol documentation
validation passed (9 families, 36 protocols), Community/Cloud contract tests passed 9/9, and
`git diff --check` passed. The 27-case coverage report is byte-identical to the baseline at
SHA-256 `67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`.

The full local `:app:testDebugUnitTest --no-daemon` attempt is incomplete, not a pass. It exited
after 4m31s when the Windows JBR 21 worker hit `EXCEPTION_ACCESS_VIOLATION` in
`robolectric-nativeruntime.dll+0x5c22`, followed by a worker connection reset. No test assertion
failure was reported before the native crash; hosted Linux CI is the full-suite result.

### C6 hosted verification

Hosted Android Debug Build run
[`36632454346`](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/36632454346) passed
on implementation/test commit `783b94e628bcf1da5e949f3c8d8c3a0a55a000c0` in 13m10s. Protocol
validation (9 families, 36 protocols), Community/Cloud contracts (9/9), whitespace checks, the
full debug unit suite, production coverage upload, APK assembly, signer verification, and APK
upload all passed. Downloaded JUnit results contain 340 XML files, 2,150 tests, 0 failures,
0 errors, and 4 skips.

The hosted 27-case report matches the expected baseline SHA-256
`67f8feb8ca6f0a74b060ef234f0c710ac89550cced647bb5b4e0357bc2d61d63`: 27 total, 22 generated,
5 preflight rejects; source totals CONTROL 18, Strength 3, Hypertrophy 1, combined 0; primary
fallback counts no material 14, scope 3, and B6 execution authority 1. All generated cases
retain `CONTROL=1`, `EXPERIMENTAL=1`, `TOTAL=2`, `THIRD=0`; routing changed: NO.

Coverage artifact `Stimulus-production-coverage` is ID `11062768027`, 289,134 bytes, archive
SHA-256 `61e73da9a13abcc1949217d12528c320c9fe41faa78d5351bc23eafb88dd9bc9`. APK artifact
`WhatYouGottaDo-debug-apk` is ID `11063267784`, 64,938,691 bytes, archive SHA-256
`09e995a10d627d9b9a736d2958d18b52c549521397cdb897533fb68be069f623`; downloaded `app-debug.apk`
is 68,526,771 bytes with SHA-256
`EF71EC80FC7CB18D84B909F41F405F39EF9F05CDF579DD999A0F0E05720870F0`.
