# C27 — Sparse Added-Identity Provenance and Materialization Audit

## 결론

남은 sparse-case 추가 owner 22건은 비실행 진단값이 아니었다. 모두 EXPERIMENTAL skeleton에 실제 일정 row로 materialize되어 비교와 배치 안전 평가에 참여했지만, exact B5 owner도 exact B6 prescription authorization도 없었다. `MaterialDemandResolver`가 coverage gap 후보를 고르고, exact authority가 없을 때 `PersonalizedProgramBuilder`가 일반 no-history provisional prescriber로 되돌아가는 우회가 근본 원인이다. 이 audit에서는 그 동작을 수정하지 않았다.

22건은 5개 case에서 22개의 `(stableKey, selectionRole)` attribution이며, 각각 week 1과 2에 배치되어 실제 실행형 EXP owner-week row는 44건이다. 다섯 case 모두 최종 선택 route는 CONTROL이어서 이 EXP row가 현재 저장되는 production skeleton은 아니지만, 비교 입력인 EXP skeleton 자체에는 실행 material로 존재한다.

## C26 merge 및 기준선

PR #19는 head `f4ce3174941e23d6af55f9eda00f24d6ce142386`에서 base `6a3f38320d2ded8c7220a9ea72ec613594017868`로 병합됐다. Merge SHA와 merged main은 `2d002b39d2ae105fbf4d831c8421ac736a8e27e4`, 병합 시각은 `2026-10-06T15:31:58Z`다. PR Hosted CI run `37456080857`와 merged-main Hosted CI run `37488311594`가 성공했다. merged-main 실행에서 protocol validation, Community/Cloud contracts, whitespace, unit tests, coverage, APK assembly, signer validation, artifact upload가 모두 성공했다.

C27 시작점은 `2d002b39d2ae105fbf4d831c8421ac736a8e27e4`다. Audit/test commit은 `9d2200cbaaa7dc28fcd539e993e66d1e160c13b3`다. 기준선은 Protocol `3.61.0`, Runtime `RECORD_BASED_PLANNER_0.15.3_KOTLIN_1`, App `0.5.1.5`, Room `38`이다. routes는 CONTROL 19, Strength V1 1, Strength Calibration 2, Hypertrophy 0, Combined 0이다. B7은 provenance-unclosed 11 cases, unmet 0, regression 1, collateral regression 0이다. unexplained added/removed/prescription-change attributions는 각각 22/7/1이다.

C26에서 거부 후 제거한 Quality material은 executable row 0, material delta 0이다. 그 C26의 22 owner-week keys와 이 sparse audit의 44 owner-week keys의 exact 교집합은 0이다. 두 집합은 숫자만 같을 뿐 서로 다르다.

## 분류 결과

| Primary root cause | Attributions | Cases | 의미 |
|---|---:|---:|---|
| `NO_B6_BUT_MATERIALIZED` | 22 | 5 | exact B6가 없는 coverage owner가 generic provisional prescription으로 실제 EXP row가 됨 |
| `AUTHORIZED_B6_BUT_PROVENANCE_LINK_MISSING` | 0 | 0 | exact executable authority는 확인되지 않음 |
| `NON_EXECUTABLE_DIAGNOSTIC_MISCLASSIFIED` | 0 | 0 | 모든 identity에 실제 EXP item이 있음 |
| `SUPPORTING_OR_COVERAGE_STRUCTURAL_CHANGE` (primary) | 0 | 0 | coverage는 producer 경로지만 독립 prescription authority가 아님 |
| `PRESERVED_OR_RELOCATED_IDENTITY_MISREAD_AS_ADDED` | 0 | 0 | CONTROL에는 없고 EXP에는 있어 실제 신규 identity임 |
| `OTHER` | 0 | 0 | 해당 없음 |

Coverage/material-demand 구조는 22건 전부의 secondary producer-path 사실이다. 이를 primary authorization으로 취급하지 않았다. exact authority가 없는 상태에서 실행 prescription이 생긴 점이 더 upstream의 원인이다.

## 22개 exact identity

각 아래 owner는 CONTROL에 없고 EXP에 있으며, 모든 경우 week 1과 2 모두에 같은 exact owner가 들어 있다. 각 identity별 전체 2주 row, prescription fields, placement, B5/B6 lookup, B7 reasons 및 producer 진단은 [machine census](c27-sparse-added-identity-census.json)에 기록했다.

| Case | stableKey | selectionRole | Exercise | Material-demand gap |
|---|---|---|---|---|
| `persona0_sparse` | `ex_1dbee10e` | `COVERAGE_HORIZONTAL_PUSH` | 머신 체스트프레스 | `HORIZONTAL_PUSH` |
| `persona0_sparse` | `ex_28347c1f` | `COVERAGE_CORE_DIRECT` | 버드독 | `CORE_DIRECT` |
| `persona1_sparse` | `barbell_reverse_curl` | `COVERAGE_ARMS_BICEPS` | 바벨 리버스 컬 | `ARMS_BICEPS` |
| `persona1_sparse` | `barbell_romanian_deadlift` | `COVERAGE_POSTERIOR_CHAIN` | 루마니안 바벨 데드리프트 | `POSTERIOR_CHAIN` |
| `persona1_sparse` | `dumbbell_goblet_squat` | `COVERAGE_LOWER_KNEE` | 덤벨 고블릿 스쿼트 | `LOWER_KNEE` |
| `persona1_sparse` | `dumbbell_lying_triceps_extension` | `COVERAGE_ARMS_TRICEPS` | 덤벨 라잉 트라이셉스 익스텐션 | `ARMS_TRICEPS` |
| `persona1_sparse` | `ex_28347c1f` | `COVERAGE_CORE_DIRECT` | 버드독 | `CORE_DIRECT` |
| `persona1_sparse` | `ex_5ca7133f` | `COVERAGE_CALVES` | 원레그 카프 레이즈 | `CALVES` |
| `persona2_sparse` | `barbell_deadlift` | `COVERAGE_POSTERIOR_CHAIN` | 데드리프트 | `POSTERIOR_CHAIN` |
| `persona2_sparse` | `barbell_reverse_curl` | `COVERAGE_ARMS_BICEPS` | 바벨 리버스 컬 | `ARMS_BICEPS` |
| `persona2_sparse` | `cable_overhead_triceps_extension` | `COVERAGE_ARMS_TRICEPS` | 케이블 오버헤드 트라이셉스 익스텐션 | `ARMS_TRICEPS` |
| `persona2_sparse` | `cable_rear_delt_fly` | `COVERAGE_UPPER_PULL` | 케이블 리어델트 플라이 | `UPPER_PULL` |
| `persona2_sparse` | `ex_28347c1f` | `COVERAGE_CORE_DIRECT` | 버드독 | `CORE_DIRECT` |
| `persona2_sparse` | `ex_5c8751d2` | `COVERAGE_CALVES` | 시티드 카프 레이즈 | `CALVES` |
| `persona3_sparse` | `ex_1dbee10e` | `COVERAGE_HORIZONTAL_PUSH` | 머신 체스트프레스 | `HORIZONTAL_PUSH` |
| `persona3_sparse` | `ex_28347c1f` | `COVERAGE_CORE_DIRECT` | 버드독 | `CORE_DIRECT` |
| `persona4_sparse` | `barbell_reverse_curl` | `COVERAGE_ARMS_BICEPS` | 바벨 리버스 컬 | `ARMS_BICEPS` |
| `persona4_sparse` | `barbell_romanian_deadlift` | `COVERAGE_POSTERIOR_CHAIN` | 루마니안 바벨 데드리프트 | `POSTERIOR_CHAIN` |
| `persona4_sparse` | `dumbbell_chest_supported_row` | `COVERAGE_UPPER_PULL` | 덤벨 체스트 서포티드 로우 | `UPPER_PULL` |
| `persona4_sparse` | `dumbbell_lying_triceps_extension` | `COVERAGE_ARMS_TRICEPS` | 덤벨 라잉 트라이셉스 익스텐션 | `ARMS_TRICEPS` |
| `persona4_sparse` | `ex_28347c1f` | `COVERAGE_CORE_DIRECT` | 버드독 | `CORE_DIRECT` |
| `persona4_sparse` | `ex_5ca7133f` | `COVERAGE_CALVES` | 원레그 카프 레이즈 | `CALVES` |

Case counts are 2, 6, 6, 2, and 6 attributions (4, 12, 12, 4, and 12 owner-week rows) for `persona0_sparse` through `persona4_sparse` respectively.

## Authority and materialization path

1. `MaterialDemandResolver.resolve` obtains gap alternatives through `GapCandidateSelector`, checks equipment/restrictions and a minimum timing estimate, then selects a candidate for an uncovered `AdaptationGap`. In the real corpus, its candidate audit says `MATERIALIZED_GAP` and the exact represented gap codes match the coverage roles above.
2. Those rows are not canonical B5 selections. The exact B5 selected owner set contains other owner-role pairs; none of the 22 added pairs is selected. Consequently there is no exact B6 authorization record for those owner-week materials. Exact provider lookup returns `NoExactAuthority` for each identity.
3. `PersonalizedProgramBuilder` handles that result by calling `generationPrescriptions.prescribe(...)` for the selected demand item. For an exercise with no same-key confirmed set history, `PersonalizedPrescriptionPlanner.prescribeUncached` returns the provisional default: 2 sets × 8 reps in this corpus, rest 90 seconds, weight field 0 kg, source `PROVISIONAL_RPE_NO_INVENTED_LOAD`. The persisted set state is `EXPLICIT_LOAD`; RPE target fields are null even though the display text mentions “RPE 6–8”. This is not exact B6 authority and the weight/load encoding does not create one.
4. The generated `ProgramSkeletonItem` is placed by `INITIAL_WEEKLY_PLACEMENT` with cause `INITIAL_PLACEMENT_POLICY`. It is not merely a rejected diagnostic candidate: it enters the EXP item list and comparison, and is counted by safety projections. The presence of scheduler `authorizedDemandIds` such as `authorized_1` is an allocation bookkeeping ID, not B6 permission.
5. In the canonical material-demand merge, `mergeCanonicalMaterialDemand` combines candidates, deferred reasons and audit values but omits `ownerAllocationProvenance`. Thus the `MATERIAL_DEMAND/ADDED` origin event created by the resolver is lost. The first retained event is placement assignment, which says where the row was put, not why its prescription was allowed.
6. B7 sees an EXP-only exact identity, but the added-owner attribution consumer has no B5/B6 target link for this `COVERAGE_*` identity. It emits `UNEXPLAINED_ADDED_IDENTITY` with no target IDs.

Useful implementation references are `ExecutionAllocationPlanner.kt` (`MaterialDemand` and `MaterialDemandResolver`, around lines 197–271), `PersonalizedProgramBuilder.kt` (provisional no-history prescription around line 252, demand merge around 558–567, exact-owner fallback around 799–807, merge omission around 1113–1119), and `ExactPrescriptionAuthorization.kt` (the exact-owner lookup returns a typed `NoExactAuthority`). These references describe the audited code; C27 did not change them.

### Supporting / coverage authority answers

- The candidates are selected to address uncovered material-demand gap codes, not because “coverage” itself authorizes a dose.
- B4 supplies case-level targets/needs, while these pairs are not exact B4 target owners or B5 selections. Their relation to B4 is via adaptation/material-demand gaps, not an exact owner-target grant.
- The separate producer is `MaterialDemandResolver`, not `ResidualCompletion`, `BoundedDayRebalancer`, `PostSplitWeeklyReflow`, or C20 incumbent preservation. Those later systems can schedule or move a row; they do not provide prescription authority.
- Dose comes from a generic no-history provisional fallback, not CONTROL row copying, personal exact-owner history, reviewed protocol, or exact B6.
- There is no owner-specific weekly frequency authority. A row appears in each program week from the generation horizon; availability/placement does not prove an independently authorized weekly frequency.
- Stable keys resolve to canonical exercise records, and the `COVERAGE_*` role is an explicit machine role. That pair still is not an exact selected B5 owner nor an executable B6 authority.
- B7 has no typed link that joins the material-demand gap selection, provisional prescription source, and owner identity. The merge also discards the source allocation event.

This is a second B6 bypass distinct from the C26 one. C26 filtered exact B5-selected Quality owners lacking B6. These 22 supporting candidates are outside that B5-owned set, so they travel through a separate material-demand lane and reach the `NoExactAuthority` generic fallback.

## Other C26 checks

- C26 denied Quality owner-week rows: 22 before the fix, 0 executable after, 0 material deltas after.
- Exact intersection of those old rows with this sparse corpus: 0.
- C20 incumbent result remains 12 `HARD_VALID`, 0 `HARD_INVALID`, 20 `UNRESOLVED`; unresolved/invalid rows are not force-preserved.
- Power authority/B6/material rows remain 0/0/0. JUMP_LANDING approved protocol/material remain 0/0.
- Build accounting remains CONTROL 1, EXPERIMENTAL 1, TOTAL 2, THIRD 0.
- The 7 Quality replacement removals remain unclosed because a `CANONICAL_REPLACEMENT` label does not supply exact executable replacement B6.

## The separate prescription change

`reviewed_hypertrophy_isolated`, `ex_284ecca6#COVERAGE_POSTERIOR_CHAIN`, has 3 sets in CONTROL and 2 in EXP for each of weeks 1 and 2. It has no exact B6 authorization. EXP's first retained state at `INITIAL_WEEKLY_PLACEMENT` already has 2 sets; no later rebalancer/reflow event changes the count. Therefore the observed 3→2 difference was present in the provisional EXP prescription before placement, rather than caused by placement repair. The generic no-history prescription path is the same authority family as the sparse additions, but this is a shared-identity prescription change, not an added identity. B7 correctly leaves it `UNEXPLAINED_PRESCRIPTION_CHANGE`; C27 makes no change to it.

## Per-case view

- `persona0_sparse`: 2 owners / 4 owner-weeks: horizontal push machine press and direct-core bird dog. Both came from selected gap coverage; no B5/B6 authority.
- `persona1_sparse`: 6 owners / 12 owner-weeks: biceps curl, Romanian deadlift, goblet squat, triceps extension, bird dog, one-leg calf raise. All are gap candidates; none was exact B5/B6.
- `persona2_sparse`: 6 owners / 12 owner-weeks: deadlift, reverse curl, overhead triceps extension, rear-delt fly, bird dog, seated calf raise. All are material-demand rows with provisional prescription; none was exact B5/B6.
- `persona3_sparse`: 2 owners / 4 owner-weeks: machine chest press and bird dog. Both were materialized despite the case having selected Power/Strength B5 owners that do not match these coverage roles.
- `persona4_sparse`: 6 owners / 12 owner-weeks: reverse curl, Romanian deadlift, chest-supported row, lying triceps extension, bird dog, one-leg calf raise. All are material-demand rows with provisional prescription; none was exact B5/B6.

## Answers and next implementation boundary

A. Exact executable B6 authority among the 22 added identities: **0**.

B. Actual executable EXP rows without exact authority: **44 owner-weeks**, across **22 identities**. None is currently selected as the final production skeleton because these cases route CONTROL.

C. Diagnostic-only misclassifications: **0**.

D. Supporting/coverage completion exposes a separate execution-authority gap: **yes**. Gap completion can create a candidate, but must not grant or imply permission to create a new prescription.

E. The most upstream root cause is that the material-demand candidate path is treated as executable scheduling demand before an exact prescription authority is required. The generic `NoExactAuthority` fallback then manufactures a provisional prescription; lost origin provenance is a secondary observability failure.

F. The next bounded implementation should close the **material-demand → executable scheduling demand boundary in `PersonalizedProgramBuilder`**, requiring an exact authority for any newly added executable material while preserving already-compatible incumbent rows. It should also retain the `MaterialDemandResolver` owner-origin event through `mergeCanonicalMaterialDemand` so B7 can explain a candidate without treating it as authority. The implementation should first decide explicitly whether unsupported gap candidates are deferred or remain diagnostic-only; it must not silently delete incumbents.

G. Do not add generic coverage/Quality B6, invent sets/reps/load/frequency, reuse CONTROL prescriptions, infer authority from exercise/category/display text, loosen B7/B8, or add mixed Strength+Task, Power, or JUMP_LANDING authority.

C27 is audit-only. Protocol/runtime/app/Room/backup schemas are unchanged; no production source file or routing behavior was modified.
