# 자동 프로그램 생성 개요

| Field | Value |
|---|---|
| Protocol ID | PROGRAM-BUILDER-OVERVIEW |
| Protocol version | 3.5.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.4.2.0; independent record-based builder from v0.5.1.4; execution layer v0.14.0 from 2026-09-06 |
| Last audited commit | 3d3c01605a775217996c8a8695afa6aad7025f6b |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

`1.0.0`은 현재 동작을 처음으로 관리되는 문서 계약으로 고정한다는 뜻입니다. 과학적 완전성, 임상 타당성 또는 예측 정확도를 뜻하지 않습니다.

## 1. 일반 사용자용 요약

### Post-generation residual completion (2026-09-07, Commit 1)

Legacy Auto는 이 경계 밖에 있습니다. 기존 Record-Based 분석, AdaptationGap, 용량 배정,
정확한 처방, 초기 시간 배치, horizon 생성, repair 및 validation은 변경하지 않습니다.
그 결과를 InitialSkeleton으로 고정하며 originalGenerationFingerprint는 계속 이 초기 결과를 가리킵니다.
현재 작업의 baseline은 위 audit SHA이며 아래 구현은 별도 로컬 커밋으로 검증합니다.

- Q = authorized post-capacity/pre-placement demand: FiniteExecutionAllocator 이후의
  continuity + gapItems + optional만 사용합니다. 배치 이전 raw gap 후보는 Q가 아닙니다.
- 저항 Q/C의 단위는 실제 처방 working sets, objective Q/C는 sets × canonical coefficient의
  RPE-neutral planning exposure입니다. C는 완성된 초기 주의 전체 항목에서 합산합니다.
  R = max(0, Q-C), objective EPSILON = 1e-9. 수요 선택은 기존 priority, R/Q, 기존 순서입니다.
  종목 간 단위 합산 점수나 생리적 충분성 판정은 만들지 않습니다.
- 기존 DIRECT-drop에만 DIRECT-specific sets 잔여량을 둡니다. SUPPORTIVE는 실제 계수로
  weighted objective 잔여량에 기여하지만 DIRECT에는 0입니다. continuity는 exact stableKey/style variant입니다.
- 실제 과거 RPE는 기존 역사 분석에만 사용합니다. 미래 residual에는 계획 RPE를 발명하거나
  처방 문자열에서 읽지 않습니다. ProgramSetPrescription에 performed RPE를 추가하지 않습니다.
- week 1의 명시적 logical slot identity, stableKey, 전체 처방과 순서를 포함해 각 주의 동형성을
  검증합니다. 불일치는 POST_PROCESS_SKIPPED_NON_ISOMORPHIC_WEEKS로 초기 결과를 그대로 반환합니다.
  보충은 대표 주에서 한 번만 수행하고 모든 동형 주에 동일하게 반영합니다.
- 관측 세션 >=4이면 ExecutionCapacityPlanner의 excluded-week 정책이 이미 적용된 unit/time
  median을 사용합니다. 아니면 비어 있지 않은 초기 일들의 median입니다. 시간 기준은 세션 상한 이하입니다.
  units/reference <0.50 AND seconds/reference <0.50인 날만 보충하며 시간비, unit비, 일 순서로 고릅니다.
  시간은 sum(seconds>0 ? seconds :45) + rest × max(0, sets-1)입니다. setup 시간은 발명하지 않습니다.
- 매 항목 추가 후 전체 주의 C/R과 sparse 지표를 다시 계산합니다. 기존 후보·제한·장비·처방 권한을
  사용하고, 의미 있는 최소값까지 기존 flexible 저항 처방만 축소합니다. reviewed indivisible 처방은 분할하지 않습니다.
  전체 추가량도 authorized exact units 및 기존 capacity를 넘지 않습니다. 빈 시간은 허용하며 filler는 없습니다.
- StandaloneDayOFI는 동일한 cutoff+1 날짜에 실제 cutoff까지의 confirmed history와 평가할 하루만
  넣어 DailyFatigueCalculator로 계산합니다. synthetic row의 rpe는 null이고 canonical missing-RPE 정책이
  처리합니다. OFI >=87 및 기존 축 caution(100 포함)을 거부합니다. 실제 미래 생리적 OFI 예측이 아닌
  동일 baseline의 계획 비교 투영입니다. canonical projector가 없는 호출은 초기 결과를 보존합니다.
- 조직에는 안전한 canonical synthetic projection 진입점이 없으므로 기존 tissueRestrictedStableKeys,
  current global hard-state만 사용합니다. 별도 tissue 엔진/점수/임계값은 만들지 않습니다.
- 기존 모든 날에 hard-feasibility상 들어갈 수 없는 동일한 양의 잔여 수요가 새 날에는 들어가고,
  사용자가 주간 일수를 고정하지 않았으며 기존 최대 5일 이내일 때에만 최대 +1일을 허용합니다.
  날짜는 RecordBasedReviewedPolicy.defaultSchedule을 사용합니다. 기존 2일 요청의 3-slot schedule처럼
  canonical schedule이 실제 새 slot을 제공하지 않으면 날짜를 발명하지 않습니다.
- residualCompletion provenance는 Q/초기 C/최종 C/R, 각 추가 항목 및 재계산 잔여량, 기준값,
  projection date, 초기/완성 fingerprint를 기존 decision JSON에 저장합니다. 자동 보충 자체를 사용자 편집으로
  오인하지 않도록 편집 비교만 completedFingerprint를 사용합니다. schema/backup 계약은 바꾸지 않습니다.
  planningBudget의 표시용 planned counts는 완성 결과로 갱신하고 기존 execution trace는 초기 배정 감사 기록으로 보존합니다.
- 보충 오류는 유효한 InitialSkeleton을 보존합니다. 이 단계에는 최종 균등화·이동·swap이 없습니다.

### Legacy Auto Skeleton V1 isolation (2026-09-06)

Legacy Auto Skeleton V1의 동결 source/behavior 기준은
`f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9`입니다. 시작 HEAD는
`a53f419ed723945d30016419453afb292ac3fe44`이며 전체 앱을 과거로 되돌리지 않습니다.
Legacy와 Record-Based는 서로 독립된 제품입니다. No-History Planner V2는 별도 미래 작업이며 구현하지 않습니다.
Ponytail 원칙은 폐기·금지하며, 유사도 추정이나 일반화된 fallback으로 명시 규칙을 대체하지 않습니다.

- `generateLegacyAutoSkeleton → LegacyAutoGenerationService → LegacyAutoProgramBuilder`는
  `data/program/legacy/`의 동결 규칙/후보/요일/배치/강도와 전용
  `LegacyAutoRequest / LegacyAutoSkeleton / LegacyAutoSkeletonItem`만 사용합니다.
- `legacyAutoDraft / LegacyAutoSkeletonPreview`는 Record-Based의
  `personalizedDraft / ProgramSkeletonPreview`와 별도 상태·타입·편집 helper를 사용합니다.
  Legacy 결과에는 개인화 결정, 훈련 상태/갭/배정 trace, progression style/variant/anchor/binding/session 필드가 없습니다.
- Legacy builder 반환이 frozen-output finalization 경계입니다. 360-case historical parity는 이 반환값을 검사합니다.
  반환 **후, 저장 전** `LegacyProgressionDraft`가 localId별 바인딩과 공유 세션 설정을 별도 소유합니다.
  `ProgramExecutionDraft`는 요청/스케줄/개인화 결정을 갖지 않는 편집·실행 projection이며 기존
  reconcile/configure 로직을 재사용합니다. 동결 모델에 진행 필드를 추가하거나 builder로 되돌리지 않습니다.
  Legacy 미리보기는 기존 `ProgressionDraftControl / ProgressionSettingsSheet`로 AUTO, 같은 운동의 기존 세션,
  별도 세션, OFF를 저장 전에 선택할 수 있습니다. 세션의 역할/모드/custom rule도 기존 권위를 따릅니다.
  `LegacyAutoPersistenceAdapter → saveLegacyAutoProgram`은 기존 exact item/set rows를 그대로 저장하고,
  세션/논리 ID/연결 모드를 restored 바인딩으로 전달한 뒤 `progression.author(programId, generated, restored)`를 실행합니다.
  명시 선택은 자동 authoring보다 우선합니다. 기존 저장 프로그램 편집·재저장·적용·기록의 의미는 유지합니다.
- Record-Based의 현재 요일 규칙과 reviewed 배드민턴 16-key 관계/처방은
  `personalized/RecordBasedReviewedPolicy`가 독립 소유합니다. 기존 값·순서·범위 clamp를 그대로 옮겼으며
  다른 planner의 후보표, intensity resolver 또는 day selector를 호출하지 않습니다.
  `GeneratedProgramSkeleton`은 이제 Record-Based 및 저장/수동 편집 초안의 기존 계약이며 Legacy 결과가 아닙니다.
- 공유 가능한 것은 exercise identity/DAO, 단순 immutable set row와 결과 알림, UI의 typed temporal 렌더러,
  동결 출력 확정 이후의 편집/실행 세션 인프라입니다. 공유 planner helper/flags 기반 planning pipeline은 없습니다.

동결 커밋의 실제 코드로 별도 worktree에서 만든 golden은 3..8주 × 3..7일 × 30/45/60분 ×
0/.30/.50/.70의 **360개 전체 조합**을 보호합니다. 정규화 request와 모든 result/item/week-plan 필드를
fingerprint하며 자동 생성의 `activeDays == materializedDays`를 매 주 검사합니다.
수동 편집으로 남긴 빈 요일에는 이 자동 생성 invariant를 적용하거나 자동 수리하지 않습니다.
실패 시 golden 자동 갱신은 금지합니다. 상세 provenance/재현 절차는
[`tools/legacy_auto/README.md`](../../../tools/legacy_auto/README.md)에 있습니다.

시작 커밋의 Record-Based 29-persona 전체 결과/결정 트리 fingerprint도 보존합니다
(random decision UUID와 생성 wall-clock만 정규화). `22_sparse_two_week_horizon`은 기존에
2주 결과에 빈 3주차 일정 `3:1|3:2|3:4|3:6`이 남습니다. 이 알고리듬 문제는 발견·고정만 하며 이번 작업에서 수정하지 않습니다.

주차/요일 표시는 historical generic 문자열 번역으로 복구하지 않습니다. typed temporal presentation은 필수입니다.
`DayOfWeek.of(day)`와 전용 Program 주차/요일 resource, 동일 너비의 한 줄 Row 선택기를 사용합니다.
제목·접근성 요일은 `localizedWeekday`를 유지하며 선택기만 W1/Mo 등 compact 표기를 사용합니다.
KO/EN × 320/360/411dp × font 1.0/1.3 × 3..8주 × 3..7일 × Legacy/Record-Based/manual-empty의
전체 preview matrix가 실제 selector glyph bounds·단일 행·탭 크기를 검증합니다.
7/8주 및 6/7일 Record-Based UI fixture는 editor 수용 범위 검사이며 알고리듬의 생성 범위를 확장하지 않습니다.


### v0.14.1 수동 진행 세션 교정 (2026-09-06)

`RECORD_BASED_PLANNER_0.14.1_KOTLIN_1`은 작성·재편집의 진행 세션 상태 전달을 교정합니다.
`DraftProgressionSession` 컬렉션이 공유 역할/override/모드/규칙을 소유하며 항목의
`DraftProgressionBinding`은 세션 UUID, 논리 항목 ID, 연결 모드와 typed signature를 참조합니다.
권위는 USER_EXPLICIT > PLANNER_EXPLICIT > AUTO_INFERRED입니다. 같은 exact exerciseStableKey만
수동 연결할 수 있으며, 한 멤버 삭제나 명시 연결 멤버의 처방 변경이 세션을 해체하지 않습니다.
수동 명시 세션의 처방 불일치는 연결 해제 대신 REVIEW입니다. 생성 style의 drift 규칙은 유지합니다.

기존 `ProgramProgressionTrack`/`ProgramProgressionItem`을 그대로 사용하며 Room 31 및 백업
format 13/schema 12는 바꾸지 않습니다. 편집 시작은 항목/세트/연결/세션을 단일 DB transaction으로
읽고 `skeletonFromProgram`에서 실제 상태와 planner style/variant/anchor/role을 복원합니다.
저장은 화면에 있는 세션/논리 ID를 materialize하며 숨겨진 `oldBindings` 복구는 없습니다.

canonical activity, progress behavior, training-role authority가 적격성을 정하므로 적격 저항운동은
0kg에서도 진행 세션을 선택할 수 있습니다. 이름·카테고리·장비·양수 중량으로 적격성을 추정하지 않습니다.
초안과 상세 화면은 같은 세션 선택 sheet를 사용하며 처방 요약과 중복 제거한 요일을 표시합니다.
역할은 세션 단위이므로 어느 멤버에서 변경해도 공유합니다. 새 세션은 별도 UUID입니다.
사용자 custom 규칙 및 APP/CUSTOM/DIRECT/OFF는 재편집과 다음 적용까지 보존합니다.

자동 sameTrack 4%/세트 수 비교, predecessor blocking, 기본 RPE/step fallback, OFI/색상,
개인화 선택과 legacy builder는 변경하지 않습니다. Move/순수 미확정 Push의 연결·순번·처방 출처는
유지하고 일반 copy는 detached입니다. 부분 수행 push 정책은 기존 v0.14.0 그대로입니다.

### v0.14.0 프로그램 실행·진행 제안 (2026-09-06)

생성·선택 알고리듬 뒤에 별도 실행 계층을 추가했습니다. runtime 표지는
`RECORD_BASED_PLANNER_0.14.0_KOTLIN_1`이며 앱 버전은 `0.5.1.4` 그대로입니다.
Room 30→31은 기존 기록을 변경하지 않고 여섯 관계형 테이블을 추가합니다.
상세 설계와 구현 결정은 [프로그램 실행·진행 명세](../../program_execution_progression_spec_ko.md)를 참조합니다.

- 프로그램 저장 시 `ProgramProgressionTrack`과 `ProgramProgressionItem`을 작성합니다.
  같은 exact exerciseStableKey만 자동 연결 후보이며, 명시 HLM/DUP style/variant와
  planner role을 먼저 사용합니다. 수동 처방은 세트 수, 반복 패턴, 구조, 중량,
  기존 confirmed-record e1RM projection의 저장 당시 snapshot, typed slot/intensity를 비교합니다.
  RPE나 이름으로 author-time 의도를 추론하지 않습니다. e1RM이 없으면 null이며 이후 재계산으로 채우지 않습니다.
- 역할과 규칙은 트랙별입니다. 사용자 override가 우선하며, 연결·역할이 모호하면 검토 상태를 유지합니다.
  초안의 진행 행과 저장된 프로그램의 진행 행에서 Auto/기존 트랙/별도/끄기 및 앱/내 기준/직접 판단/끄기를 설정합니다.
- 적용마다 별도 `ProgramApplication`을 만들고, `ProgramWorkoutLink`가 이름·주차·요일·논리 항목·트랙·순번·역할·규칙을 snapshot합니다.
  원본 프로그램 삭제 FK는 없으며 템플릿 수정은 적용 이력을 바꾸지 않습니다.
- `ProgramPrescriptionSet`은 원본과 현재 계획을 분리합니다. `WorkoutSet.confirmed=true`만 수행값입니다.
  첫 확인 전 세트 편집은 현재 계획 수정이며 원본을 바꾸지 않습니다. 첫 확인 후에는 목표를 동결합니다.
  추가된 세트는 `originalExists=false`, 삭제된 계획 세트는 `plannedSetIndex=null`로 원본과 구분합니다.
- 같은 application/track의 완료된 직전 순번만 비교합니다. 부분 수행, 임의 mixed-load 구조,
  수행 도중 구조 변경은 자동 성공으로 만들지 않습니다. Uniform은 작업 중량, 명시 anchor는 해당 세트 중량/RPE를 사용합니다.
- 기본 규칙은 목표 완료 + 실제 RPE 상한 이내일 때 증량 후보, 높은 노력/누락 RPE/첫 실패는 유지,
  연속 두 실패는 5% 감량 후보입니다. Main 상한 8, Assistance 9는 편집 가능한 engineering defaults입니다.
  사용자 kg → 두 번 이상 관측된 같은 체인 증량 → 2%/0.5kg 산술 grid → 검토 순서입니다.
  grid는 실제 장비 단위의 추론이나 실행 가능성 보증이 아닙니다.
- `140 actual / 145 plan / 142.5 suggestion`은 `INCREASE +2.5`입니다.
  관련 국소 조직 제한만 해당 운동의 증량을 보류하며 OFI 하나를 전체 감량 스위치로 사용하지 않습니다.
- PENDING은 닫아도 유지됩니다. 수락/현재 계획 유지/직접 입력은 영구 resolution으로 남고,
  수락은 다음 한 세션만 수정합니다. 수행하지 않은 미래 세션으로 연쇄 합성하지 않습니다.
  근거 변경은 미해결 제안을 STALE로 만들고 재계산하며 이미 명시한 결정은 자동으로 덮지 않습니다.
- 이동·미루기는 연결과 처방·제안 참조를 remap합니다. 일반 복사는 detached입니다.
  부분 수행의 나머지만 미루면 새 부분은 detached이고 기존 트랙은 검토 대상으로 표시합니다.
- plyometric/skill/court 세션에는 자동 중량 진행을 부여하지 않습니다.
  기존 planner selection, legacy ProgramAutoBuilder, OFI/조직/strength posterior/Objective 산식은 변경하지 않습니다.

구현: `ProgramProgressionModels`, `ProgramProgressionEngine`, `ProgramProgressionService`,
`ProgramProgressionBackup`, `ProgramProgressionWireCodec`, `ProgramProgressionUi`.
검증: `ProgramProgressionEngineTest`, `ProgramProgressionPersistenceTest`,
`ProgramProgressionLayoutTest`, `ProgramProgressionDeviceLayoutTest`, `ProgramProgressionMigrationTest`.

프로그램 만들기에는 서로 독립적인 두 결정론적 경로가 있습니다. 기존 자동 골자 생성은 기존 입력과 rule table을 그대로 사용합니다. 기록 기반 경로는 완료 기록, 명시 답변, canonical metadata를 사용해 설명 가능한 다주 계획을 만듭니다.

## 2. 목적

현재 제품의 입력, 계산·분류, 집계, 표시와 fallback을 재현할 수 있는 하나의 canonical 계약을 제공합니다.

## 3. 적용 범위

이 문서는 `PROGRAM-BUILDER-OVERVIEW`가 소유한 현재 runtime 동작과 직접 연결된 source, tests, authority assets에 적용됩니다.

## 4. 비적용 범위

의학적 진단, 부상 확률, 치료 권고, 미구현 센서 정밀도, 미래 설계와 다른 protocol family의 계산은 포함하지 않습니다.

## 5. 용어

용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를 따릅니다. code identifier, enum, stable key와 식은 runtime 표기를 유지합니다.

## 6. 입력 데이터

기존 자동 경로는 프로그램명, 기간, 주당 운동일, 하루 시간, 배드민턴 비율과 active exercise catalogue를 사용합니다. 기록 기반 경로는 생성 cutoff 이하의 `confirmed=true` set, canonical exercise `stableKey`, resolved runtime metadata, 초기 profile, 저장된 사용자 의도와 이번 실행의 핵심 선호·조건부 중단 맥락 답변을 사용합니다. 미래 기록과 미확정 set은 입력에서 제외합니다.

## 7. 계산 또는 분류 계약

공개 경로는 `LegacyAutoGenerationService → LegacyAutoProgramBuilder`입니다. 현재 UI 입력은 이름, 기간, 주당 운동일, 하루 시간, 배드민턴 비율이며 builder는 goal, equipment, 제외어, sport-strength, periodization과 preferred/excluded stable key를 현재 기본값으로 정규화합니다.

기록 기반 경로는 `TrainingViewModel → TrainingRepository → PersonalizedProgramPlanningService`이며 기존 `LegacyAutoProgramBuilder`를 호출하거나 수정하지 않습니다. `PlanningHistorySnapshotBuilder`가 시점 고정 snapshot을 만들고, `PlannerActivityDomainResolver`가 typed role/capability, canonical activity kind, progress metric과 runtime metadata로 저항운동·구조화 배드민턴 드릴·athletic-performance drill·일반 코트 세션을 분리합니다. `MovementExposureRepresentationAnalyzer`와 `BadmintonObjectiveRepresentationAnalyzer`가 관찰된 분포를 계산하고 `AdaptationGapAnalyzer`는 그 상태를 기존 우선순위 사다리로 변환합니다. 운동 선택은 별도의 reviewed stableKey authority에서 수행합니다. `PersonalizedProgramBuilder`는 주간 구조와 set별 처방을 materialize하고 기존 editor/save/apply 형식으로 변환합니다. projection repair가 필요하면 기존 선택 priority로 보존 항목을 결정한 뒤, 반환할 최종 item으로 실제 세트 수·drill bout·fingerprint를 다시 확정합니다.

핵심 선호 세 질문은 매번 먼저 표시합니다. 용량 해석에 영향을 줄 수 있는 미확인 저훈련 주를 최근순으로 최대 3개 골라 날짜가 있는 원인 질문을 추가합니다. 빈도는 향후 일정 견고성만 소유하며 90일 경과 또는 최근 8주 확정 외부 중단 2회와 NEVER/RARE 답변의 모순 때 다시 묻습니다. `preparePersonalizedProgram`이 질문을 한 번에 반환하고, 사용자가 모두 답한 뒤 `generatePreparedPersonalizedProgram`이 고정된 cutoff와 명시 조건으로 중단 없이 생성합니다. 기억나지 않음/다른 이유/의도적 디로드는 유효한 주별 답변이며 피로 실패로 바꾸지 않습니다. 답변은 관찰 사실로 취급하지 않고 명시 사용자 맥락으로 저장합니다.

Program candidate admission is exact stableKey authority. The typed
`LegacyAutoCandidateAuthority` view is derived directly from `LegacyAutoRuleTables`;
names, metadata labels, core tokens, or similarity cannot add an exercise.
The approved set remains 59 keys. The disconnected advanced builder is guarded
against reintroduction and was removed after confirming zero production
consumers.

저장 프로그램 적용은 생성이나 재평가가 아니라 exact materialization입니다.
`TrainingViewModel → TrainingRepository → ProgramPlanService` 적용 경로는
fatigue/readiness gate를 입력받지 않습니다. 모든 item은
`ProgramSetPrescriptionResolver`로 해석하고 저장된 운동과 set 처방을
그대로 unconfirmed workout plan으로 만듭니다.

## 8. 집계 방식

기존 자동 경로는 기간을 3~8주, 주당 일수를 3~7일, 시간을 30/45/60분으로 정규화합니다. 기록 기반 경로의 기간과 주당 일수는 별도 control에서 기본 `AUTO`로 보이며 각각 2~6주와 2~5일에서 결정됩니다. 사용자는 명시 override 뒤 다시 AUTO로 돌아갈 수 있습니다. 기존 자동 경로의 더 넓은 범위와 배드민턴:근력 비율은 유지되지만 그 비율은 기록 기반 계산에 쓰지 않습니다. 모든 week/day/set은 명시적으로 생성하며, 운동 anchor는 cutoff까지의 완료 기록과 실제 progression만 사용합니다.

현재 블록 판단의 per-anchor strength style, style feature 및 canonical strength posterior 변화는 cutoff를 끝으로 하는 최근 56일만 사용합니다. 노출 표현 비교는 현재 `cutoff-27..cutoff`와 직전 `cutoff-55..cutoff-28`의 인접 28일 창을 사용하고, confidence는 각 창을 cutoff에 고정한 네 개의 7일 bin으로 계산합니다. 노출 representation에는 ISO 주차를 사용하지 않습니다. 별도의 지속 용량·중단 맥락은 완결된 ISO 월~일 주간을 사용합니다. 일반 코트 부하는 원시량과 주간 환산량을 보존하지만 코트 부하가 S&C 주당 일수를 직접 제한하지 않습니다.

움직임 표현 단위는 확인된 저항운동 working set 1개이며 생리적 dose가 아닙니다. 현재·직전 share는 활성 required movement 집합 안에서 정규화합니다. 개인 비교는 `currentShare / priorShare`이고, peer 비교는 target을 제외한 같은 base-priority의 positive exposure가 둘 이상일 때 그 median을 사용합니다. `0.25`와 `0.50`은 큰 분포 차이를 찾는 engineering outlier rule일 뿐 충분량 또는 최적량 임계값이 아닙니다. 상태는 `ABSENT`, `STRONG_UNDERREPRESENTATION_SIGNAL`, `UNDERREPRESENTATION_SIGNAL`, `NO_CLEAR_DEFICIT_SIGNAL`, `UNKNOWN`이며 `NO_CLEAR_DEFICIT_SIGNAL`은 충분하다는 뜻이 아닙니다. 현재 저항운동이 전혀 없으면 개별 부재 provenance는 보존하되 하나의 `RESISTANCE_FOUNDATIONAL_ONRAMP`만 생성합니다.

Objective V2는 기존 transfer coefficient와 RPE modifier를 유지하되 weighted exposure와 DIRECT-only exposure를 별도로 저장합니다. share의 개인 retention을 주된 비교로 사용하고 세 개 이상의 positive peer objective median은 약한 보조 신호로만 사용합니다. peer-only 신호는 HIGH gap이 될 수 없습니다. `DIRECT_DROP`은 직전 DIRECT가 최근 창에서 사라진 사실이고, `NEVER_DIRECT_OBSERVED`는 자동 gap이 아닌 발달 관찰입니다. 후자는 강한 gap 뒤 spare capacity에서 block당 최대 한 후보만 고려하며 PRESERVE anchor를 밀어내지 않습니다. 아홉 objective를 같은 목표량으로 가정하지 않습니다.

anchor 전환의 `gapPressure`는 `contributesTransitionPressure=true`인 gap만 사용합니다. 선택적 `BADMINTON_DEVELOP_*` 관찰은 후보·설명·저장 provenance에는 남지만 단독으로 rotation, structure 또는 dose 전환을 바꾸지 않습니다. 회복 입력에서 production tissue `MODERATE`는 명시적으로 `.30`에 매핑하며 과거 `ELEVATED` 토큰도 같은 값으로 호환합니다. 그 밖의 readiness, OFI와 systemic recovery 식은 유지합니다.

수동 프로그램은 자동 생성 범위와 별개입니다. 기록 달력에서 선택한
inclusive 날짜 범위는 첫 날짜를 1주차 월요일로 매핑하고 날짜 간 빈칸을
그대로 둡니다. 같은 날짜의 동일 운동 기록도 합치지 않으며 각 기록과
set 순서를 별도 program item과 set prescription으로 보존합니다.

## 9. 출력과 UI 해석

표시는 계산 결과를 설명하는 제품 계약이며 진단, 손상량 또는 치료 권고로 해석하지 않습니다.

v0.5.0.14부터 optimization action과 `PROGRAM_...` warning은 내부 trace와
diagnostic으로만 유지합니다. 정상 완료 화면은
`ProgramUserNoticeCode`와 정수 인자만 전달받고 Android presentation
boundary에서 현재 locale의 문장으로 변환합니다. 따라서 domain/data
계층은 Android `Context`에 의존하지 않으며, 정상 사용자 화면은 action
code나 enum 이름을 직접 표시하지 않습니다.

저장된 프로그램 상세는 실제 운동이 있는 날짜만 표시하고, 각 운동을
read-only card로 보여 줍니다. 운동 identity를 현재 catalogue에서 해석할 수
있으면 기존 `ExerciseInfoDialog`를 열며, 그렇지 않아도 저장된 이름과
처방 snapshot은 계속 표시합니다.

표시된 처방과 적용 결과는 같은 canonical resolver를 사용합니다. 피로도,
readiness, OFI와 연결조직 분석은 정보와 권고이며 저장 프로그램을 자동으로
삭제, 축소, 교체하거나 변경하지 않습니다.

## 10. 예외 및 fallback

기존 경로는 candidate가 부족하면 기존 rule table의 deterministic fallback order를 사용합니다. 기록 기반 경로도 무작위 운동이나 이름 유사도 fallback을 쓰지 않습니다. reviewed authority에서 유효한 stableKey를 찾지 못하거나 projection validation을 통과하지 못하면 실패를 표시하며 기존 editor 내용을 덮어쓰지 않습니다. 재시도는 같은 기록 기반 경로만 다시 실행합니다.

## 11. 개인화 또는 보정

기록 기반 경로는 반복된 실제 운동의 연속성을 우선하되 관찰 style을 미래 처방으로 직접 복사하지 않습니다. 각 anchor에 대해 관찰 style과 다차원 feature, 적응 상태를 계산하고 `StructureTreatment`와 `DoseTreatment`를 별도로 결정합니다. 근력 반응은 최근 56일 canonical posterior의 `tanh(changePercent / 5)`와 인접 28+28일의 matched raw 수행을 .65/.35로 결합합니다. 관찰이 2개 미만인 posterior는 unavailable이며 0% 변화 자체는 유효한 안정 근거입니다. 전체/국소 회복 조정은 아래 v0.13 계약을 따르고, gap은 저항·구조화 배드민턴·athletic/보조운동이 공유하는 유한 실행 용량 안에서 재배분합니다. 저항 working set, 구조화 배드민턴 bout, athletic-performance bout는 서로 다른 단위로 관리하면서 같은 세션 시간 한도를 공유합니다.

multi-day style variant는 anchor별로 서로 다른 생성일에 배치합니다. 2일 계획은 기존 처리 의미에 따라 HLM/Madcow를 `HEAVY+LIGHT` 또는 heavy 완화 시 `LIGHT+MEDIUM`, DUP를 `STRENGTH+VOLUME` 또는 strength 완화 시 `VOLUME+MODERATE`로 제한합니다. gap truncation은 기존 HIGH, MEDIUM/MODERATE, LOW 순서를 사용하고 같은 priority에서는 원래 domain 순서를 보존합니다.

주간 저항 목표에는 고정 4세트 하한이 없습니다. 기록/회복 목표 안에서 `PRESERVE`와 가능한 `PRESERVE_CORE_REBALANCE` 연속성을 먼저 유지하고, `PARTIAL_CONTINUITY`와 `ROTATE_EMPHASIS`는 유한 예산에서 0이 될 수 있습니다. 모든 anchor를 유지하려고 용량을 늘리지는 않습니다. 단, 낮은 기록 용량·양호한 회복·선택된 HIGH 저항 gap이 동시에 있고 최소 2세트를 다른 방식으로 배정할 수 없으면 `MINIMAL_CAPACITY_EXPANSION`으로 제한적 확장을 기록하고 preview에 표시합니다.

직접 노출은 exact objective의 canonical DIRECT 관계로만 인정합니다. 사용자 요청에 따라 SUPPORTIVE 관계도 필요한 보조운동 후보로 사용하되 직접 노출이나 목표 충족으로 승격하지 않습니다. GENERAL/LOW는 후보 관계를 대신하지 않습니다. 안정성 typed authority와 명시적 SUPPORTIVE 관계가 함께 있는 운동은 athletic/보조 실행으로 배정할 수 있습니다. 처방은 최근 56일 실제 완료 구조 → exact stableKey 다주 canonical seed의 실행 가능한 무부하 처방 → 기존 reviewed rule 순서로 해석합니다. 휴식 시간만 있는 timing row로 반복·라운드·운동시간을 발명하지 않습니다.

`TOP_SET_BACKOFF`, `STRAIGHT_5X5`, `MADCOW_LIKE_HLM_RAMPING`, `DUP_LIKE_UNDULATING`, `HEAVY_LIGHT_MEDIUM` 등은 각 anchor의 실제 feature와 할당 budget이 허용할 때만 알아볼 수 있는 형태로 이어집니다. multi-day style의 중량 기준은 마지막 세션이 아니라 최근 관찰 주의 가장 강한 정당한 노출입니다. 생성된 미래 주차는 새 완료 근거가 없으므로 자동 증량하지 않고 같은 현재 microcycle을 반복할 수 있습니다. 일반 배드민턴 세션은 실제 회복 비용으로 계속 반영되지만 구조화된 배드민턴 목표 자극으로 계산하지 않습니다.

### v0.12 실행 용량과 사전 확인

실행 순서는 representation/gap → 처방 authority → 유한 cross-domain 배분 → 실제 시간 배치 → residual repair → 반환 item 기준 count/fingerprint입니다. 보조 및 경기력 훈련을 저항 예산 밖에 덧붙이지 않습니다. 의미 있는 최소 처방을 먼저 배정하고 discretionary 연속성 용량을 양보할 수 있습니다. 동일 exact objective를 공유하는 항목은 중복 투입하지 않으며 typed family/redundancy로 서로 다른 훈련 질을 구분합니다.

용량 envelope는 명시 주당 일수·세션 분·가용 초, 최근 session median/p75 단위·median 시간, 관찰 controllable workload, court context, useful demand, 시간상 bound와 최종 실제 단위를 보존합니다. v0.13에서는 전신 dose factor를 demand와 density 양쪽에 중복 적용하지 않고 가용 용량·유효 demand 경계가 정해진 뒤 한 번 적용합니다. 지속적으로 소화한 용량의 강한 근거, productive/tolerated 상태, 충분한 명시 가용 시간과 demand가 함께 있고 global hard restriction이 없을 때 그 관찰 용량 근처까지 허용하되 자동 초과하지 않습니다. 실제 처방 시간 검사는 유지합니다.

고정 4/5개 item 제한과 high-court 별도 item cap은 제거했습니다. 코트 부하는 기존 recovery/자동 빈도/lower-anchor interference 문맥만 유지하며 objective 자극을 만들지 않습니다. 실제 처방과 세트 간 휴식이 시간 한도를 결정하고 typed 하체/impact 항목을 분산합니다. 시간이 늘어도 정당한 demand가 소진됐으면 filler를 만들지 않습니다.

매 preflight에서 근력 목표·배드민턴 포함·프리웨이트 수용의 세 질문을 먼저 합니다. 저장 선호나 관찰 기록이 질문을 생략하지 않으며 모든 유효 답변을 선택해야 생성할 수 있습니다. 취소는 저장하지 않고 UNRESOLVED로 임시 단기 계획을 만들지 않습니다. 정상 AUTO badminton-support horizon은 adaptation minimum 4주를 지키며 실제 회복 제한/희소 이력의 bridge만 짧아질 수 있습니다. 명시 기간은 그대로 우선합니다.

portable app_meta의 planningBudget.execution은 최종 direct representation과 supportive allocation을 분리합니다. SUPPORTIVE_ONLY_DIRECT_EXPOSURE_NOT_REPLACED는 보조운동은 실제 배정됐지만 직접 노출은 아직 없다는 뜻입니다. Room schema 및 기존 적용 confirmed=false 계약은 유지합니다.

### v0.13 장기 훈련상태와 중단 맥락

`TrainingStateAssessment`는 기존 canonical OFI 56일 series를 읽으며 새 OFI engine이 아닙니다. strain은 최근 7일·직전 7일과 최근 7일을 제외한 개인 baseline `-55..-7`의 median/MAD를 사용합니다. 적응은 기존 posterior와 인접 28+28일의 load/reps/RPE 처방 matching을 결합하며, 결측은 0점이 아닌 unavailable입니다. 같은 완료 기록을 재사용하는 posterior/raw confidence는 합산하지 않고 큰 값으로 제한합니다. 운동별 반응을 여섯 주요 movement의 confidence-weighted median으로 먼저 집계한 뒤 positive/negative breadth를 계산합니다. accessory 하나의 PR은 광범위 저하를 상쇄하지 않습니다.

전신 soft factor는 `clip(1 - .15*S*(1-P) - .10*M, .80, 1)`로 한 번만 적용합니다. 낮은 근거는 HOLD입니다. LIMITED 또는 국소 contributor가 없는 VERY_HIGH/BLOCKED만 전신 hard gate이며, 국소 stableKey·profile 운동 제한은 전신 상태를 HARD_RESTRICTION으로 바꾸지 않습니다. 정확한 tissue contributor key가 있으면 그 국소 범위를 보존하고, 국소 키 없는 심한 tissue 제한/LIMITED는 전신 cap을 적용합니다. 높은 soft OFI에서 생성된 사람이 읽는 readiness 권고 문장을 독립 hard restriction으로 재사용하지 않습니다. 국소 anchor 조정은 해당 운동의 반복 저하·tissue 제한·기존 lower-anchor court interference만 소유합니다.

최근 최대 12주의 용량 이력은 adaptation/OFI 창을 대체하지 않습니다. 각 주의 controllable 단위·시간·일수·canonical court load·수행·RPE를 보존하고 주변 non-low 주 중앙값의 `.625` 미만을 후보 저훈련 주로 표시합니다. 원인 상태는 NORMAL/EXTERNAL_INTERRUPTION_LIKELY/EVENT_OR_TAPER_LIKELY/RECOVERY_REDUCTION_LIKELY/UNEXPLAINED_LOW_WEEK입니다. 정확한 주의 USER_CONFIRMED EXTERNAL/EVENT_OR_TAPER만 workload/day/density tolerance의 분자·분모에서 제외합니다. 추론의 source는 INFERRED/HIGH_CONFIDENCE_INFERRED, 미확인은 UNRESOLVED로 보존하고 개인 사건으로 확정하지 않습니다. 실제 load/reps/RPE·OFI·court·tissue 기록은 어떤 맥락에서도 지우지 않습니다. 모르는 주는 소화 실패라고 단정하지 않습니다.

`SustainableWorkloadEvidence`는 최소 2주 연속 사용 가능한 안정 run의 중앙 단위·시간·일수, run 길이·최근성, 수행과 RPE 근거를 보존합니다. 가중치는 `durationWeeks² * .92^ageWeeks`이며 1주 폭증은 run이 아닙니다. 이는 반복 소화한 관찰 용량이지 MRV·최대 안전량·최적량이 아닙니다. 외부 일정이 잦으면 CORE_MUST_DO/IMPORTANT/OPTIONAL_CAPACITY 순서로 핵심을 앞쪽 논리 세션에 보호하고 선택 항목을 뒤로 배치합니다. 요일을 하드코딩하거나 빈도 답변만으로 용량을 줄이지 않습니다.

주별 답변은 `personalized_planning_week_context_v1`의 ISO Monday 키에 cause/source/answeredAtEpochMillis로 저장·백업·복원합니다. 옛 global interruptionCause는 읽기 호환만 유지하며 어떤 주에도 자동 적용하지 않습니다. 빈도와 answeredAtEpochMillis는 기존 preferences에 별도 저장합니다. 기존 결정은 새 필드 없이 읽을 수 있고 Room·Android version·release tag는 변경하지 않습니다. 세부 식·공학적 임계값·검증은 기존 implementation note에 있습니다.

### v0.13.1 빈도·제한·구간 근거 교정

AUTO 빈도는 HIGH-qualified sustainable days → NORMAL 주 중앙 일수 → 최근 실제 일수 순으로 참조해 2~5일로 반올림합니다. 임시 배치 보조값 ceil(itemCount/4)을 함께 쓰되 생리적 권위로 해석하지 않습니다. 전신 hard=2, MALADAPTATION=3, ACCUMULATING_STRAIN=4, 나머지=5의 state ceiling만 적용합니다. genericCourtLoad 180/305 등에서 직접 3일 cap을 부과하지 않습니다. 명시 일수·기간 override는 유지됩니다.

각 run의 관찰 성공 주 수와 달력 span을 구분합니다. HIGH는 해당 run 자체의 성공 주 ≥4, 수행 비교 주 ≥2, 수행 coverage ≥.50, 넓은 저하·큰 RPE 악화 없음이 필요합니다. MODERATE는 ≥3주와 해당 run의 수행/RPE 근거, 그 외 LOW입니다. HIGH 전체 용량은 HIGH-qualified run만의 중앙 단위·시간·일수를 duration²/recency 가중합니다. 다른 run의 수행을 빌려 긴 run을 검증하지 않습니다.

확정 외부/event 주 1개는 직전 안정 NORMAL 2주와 다음 주 .85~1.40 복귀 및 광범위 저하 없음 아래 연결될 수 있습니다. 중단 주는 성공 주·수행 근거·가짜 훈련량으로 세지 않습니다. 추론/미확인/FATIGUE/실제 악화 주는 연결하지 않습니다. 국소 제한이 있어도 productive/tolerated 상태와 HIGH 근거는 제한 밖 작업의 sustainable release를 허용하며, 해당 운동의 admission/처방은 제한을 계속 지킵니다.

## 12. 연구 근거

Evidence profile은 `PRODUCT_POLICY, ENGINEERING_HEURISTIC`입니다. 이는 source와 repository 안의 supporting evidence를 구분해 기록한 것으로, implementation status나 임상 검증을 대신하지 않습니다.

## 13. 제품 정책 및 휴리스틱

계수, 임계값, taxonomy, fallback과 표시 문구 중 연구의 직접 효과크기가 아닌 값은 제품 정책 또는 engineering heuristic으로 취급합니다. 이를 논문 효과크기로 표현하지 않습니다.

## 14. 알려진 한계

- 기존 자동 생성기는 history, today, resolved metadata catalogue와 fatigue 입력을 사용하지 않습니다. 이는 기록 기반 경로와 의도적으로 분리된 기존 계약입니다.
- self-entered 기록과 metadata 품질에 의존하며 결과는 진단 또는 조직 손상량이 아닙니다.
- 기록 기반 결과는 의료·부상 판단이나 최적성 보장이 아니며, 선택된 horizon 끝에서 재평가하는 제품 휴리스틱입니다.
- 이 모델은 관찰된 상대 분포와 직접 노출 소실만 판단합니다. 절대 주간 세트 목표, 생리적 충분성, 아홉 badminton objective의 동일 목표 비중은 정의하지 않습니다.

## 15. 현재 구현 상태

- Specification status: `ACTIVE`
- Runtime implementation status: `IMPLEMENTED`
- v0.13.1 record-based boundary: 기존 v0.12 실행 배분 앞에 longitudinal training-state assessment와 interruption-aware sustainable workload를 연결했습니다. core 세 질문, 조건부 중단 질문, 명시 AUTO override, 유한 cross-domain 배분, 처방 시간 검사, final-item provenance와 기존 editor/save/apply를 유지합니다.
- 개인화 선호와 최근 decision provenance는 portable `app_meta`로 백업·복원되며 로컬 seed/rebuild/lineage metadata는 이식하지 않습니다.
- v0.5.0.6 identity boundary: built-in program seed 753개 item은 모두
  explicit canonical stableKey를 사용하며 display name lookup으로 identity를 만들지 않습니다.
- v0.5.0.12 manual program boundary: `training_program_item_sets`가 있으면
  set별 reps/weight/seconds가 authoritative하며, 자식 row가 없는 기존
  program item은 scalar setCount/reps/weight/seconds를 반복하는 legacy
  fallback으로 해석합니다.
- v0.5.0.13 application boundary: scalar/child storage 형식, 적용 날짜와
  현재 fatigue/readiness 상태에 관계없이 저장된 모든 운동과 set을
  unconfirmed plan으로 정확히 적용합니다.
- v0.5.0.14 presentation boundary: optimization trace는 안정적인 내부
  action code를 유지하고, 완료 화면은 typed notice를 한국어/영어
  resource로 변환해 별도 항목과 severity로 표시합니다.
- Audit result: 현재 local main의 source, tests, authority assets를 감사한 계약입니다.
- 문서와 runtime이 다르면 이 문서의 known gap에 남기며 문서만으로 runtime을 완료 상태로 바꾸지 않습니다.

## 16. 구현 위치

- [TrainingStateAssessment.kt](../../../app/src/main/java/com/training/trackplanner/data/personalized/TrainingStateAssessment.kt)
- [WeeklyWorkloadContext.kt](../../../app/src/main/java/com/training/trackplanner/data/personalized/WeeklyWorkloadContext.kt)
- [WeeklyContextAnnotationJson.kt](../../../app/src/main/java/com/training/trackplanner/data/personalized/WeeklyContextAnnotationJson.kt)
- [TrainingStateRouting.kt](../../../app/src/main/java/com/training/trackplanner/data/personalized/TrainingStateRouting.kt)
- [TrainingStateJson.kt](../../../app/src/main/java/com/training/trackplanner/data/personalized/TrainingStateJson.kt)

- [`app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoGenerationService.kt`](../../../app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoGenerationService.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramSkeletonGenerator.kt)
- [`app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoProgramBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoProgramBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt`](../../../app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt)
- [`app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt`](../../../app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedPlanningModels.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedPlanningModels.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PlanningHistorySnapshotBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PlanningHistorySnapshotBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/AthletePlanningStateBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/AthletePlanningStateBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/ExposureRepresentation.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/ExposureRepresentation.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedDecisionComponents.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedDecisionComponents.kt)
- [`app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt)
- [`app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoRuleTables.kt`](../../../app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoRuleTables.kt)
- [`app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoCandidateAuthority.kt`](../../../app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoCandidateAuthority.kt)
- [`app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoExerciseSpec.kt`](../../../app/src/main/java/com/training/trackplanner/data/program/legacy/LegacyAutoExerciseSpec.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramOptimizationTrace.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramPlanService.kt)
- [`app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt`](../../../app/src/main/java/com/training/trackplanner/data/ProgramSetPrescription.kt)
- [`app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt`](../../../app/src/main/java/com/training/trackplanner/ProgramUserNoticePresentation.kt)
- [`app/src/main/java/com/training/trackplanner/TrainingViewModel.kt`](../../../app/src/main/java/com/training/trackplanner/TrainingViewModel.kt)
- [`app/src/main/java/com/training/trackplanner/PlanScreen.kt`](../../../app/src/main/java/com/training/trackplanner/PlanScreen.kt)
- [`app/src/main/java/com/training/trackplanner/PlanGeneratedPreview.kt`](../../../app/src/main/java/com/training/trackplanner/PlanGeneratedPreview.kt)
- [`app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt`](../../../app/src/main/java/com/training/trackplanner/RecordCalendarScreen.kt)
- [`app/src/main/java/com/training/trackplanner/PlanProgramSections.kt`](../../../app/src/main/java/com/training/trackplanner/PlanProgramSections.kt)

- [`ExecutionAllocationPlanner.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/ExecutionAllocationPlanner.kt)
- [`PerformancePrescriptionResolver.kt`](../../../app/src/main/java/com/training/trackplanner/data/personalized/PerformancePrescriptionResolver.kt)

## 17. 검증 테스트

- [TrainingStateParityTest.kt](../../../app/src/test/java/com/training/trackplanner/data/personalized/TrainingStateParityTest.kt)
- [TrainingStateCorrectionTest.kt](../../../app/src/test/java/com/training/trackplanner/data/personalized/TrainingStateCorrectionTest.kt)
- [TrainingStateRealBackupComparisonTest.kt](../../../app/src/test/java/com/training/trackplanner/data/TrainingStateRealBackupComparisonTest.kt)

- [`app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramRuleTablesTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramCandidateAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramCandidateAuthorityTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderParityMatrixTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/ProgramAutoBuilderParityMatrixTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerParityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerParityTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerV010Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/PersonalizedPlannerV010Test.kt)
- [`app/src/test/java/com/training/trackplanner/data/personalized/ExposureRepresentationV011Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/ExposureRepresentationV011Test.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramUserNoticePresentationTest.kt)
- [`app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/MetadataPresentationUiTest.kt)
- [`app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/RecordRangeProgramServiceTest.kt)
- [`app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt`](../../../app/src/test/java/com/training/trackplanner/ProgramRecordUiContractTest.kt)

- [`ExecutionAllocationV012Test.kt`](../../../app/src/test/java/com/training/trackplanner/data/personalized/ExecutionAllocationV012Test.kt)
- [`PersonalizedPlanningQuestionUiTest.kt`](../../../app/src/test/java/com/training/trackplanner/PersonalizedPlanningQuestionUiTest.kt)
- [`RealBackupPersonalizedPlannerE2eTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/RealBackupPersonalizedPlannerE2eTest.kt)

## 18. 권위 자산

- [v013_training_state_reference.py](../../../tools/planner_reference/v013_training_state_reference.py)
- [v013_training_state_cases.py](../../../tools/planner_reference/v013_training_state_cases.py)
- [v013_training_state_golden.json](../../../tools/planner_reference/fixtures/v013_training_state_golden.json)

- [`app/src/main/assets/training_settings_seed.csv`](../../../app/src/main/assets/training_settings_seed.csv)
- [`tools/planner_reference/v011_exposure_representation_reference.py`](../../../tools/planner_reference/v011_exposure_representation_reference.py)
- [`tools/planner_reference/fixtures/v011_exposure_representation_golden.json`](../../../tools/planner_reference/fixtures/v011_exposure_representation_golden.json)

- [`v012_execution_allocation_reference.py`](../../../tools/planner_reference/v012_execution_allocation_reference.py)
- [`v012_execution_allocation_golden.json`](../../../tools/planner_reference/fixtures/v012_execution_allocation_golden.json)

## 19. 관련 문서

- [`docs/v0.4.2.0_release_notes.md`](../../v0.4.2.0_release_notes.md)
- [`docs/v0.5.0.13_release_notes.md`](../../v0.5.0.13_release_notes.md)
- [`docs/v0.5.0.14_release_notes.md`](../../v0.5.0.14_release_notes.md)
- [`docs/v0.3.5.3_program_builder_architecture.md`](../../v0.3.5.3_program_builder_architecture.md)
- [`docs/protocols/data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md`](../data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md): slot relation shadow와 공개 generator golden을 정의하며 production generator는 변경하지 않습니다.
- [`docs/protocols/README.md`](../README.md)

- [기록 기반 planner 구현 노트](../../record_based_planner_implementation_note.md)
- [기록 기반 planner 릴리스 노트](../../v0.5.1.4_record_based_planner_release_notes.md)

## 20. 변경 이력

- `3.5.0` (2026-09-07): 초기 skeleton 이후 funded Q/C/R 기반 잔여 보충, 대표 주 mirror,
  동일 기준일 canonical OFI 비교와 기존 tissue 제한을 추가했습니다. 초기 분석·용량·처방·Legacy는 동결합니다.

- `3.4.1` (2026-09-06): 수동 진행 세션의 독립 초안 identity, 공유 역할과 lossless 편집/저장 경계 및 0kg 적격성 UI를 교정했습니다. 영속 schema와 자동 비교/제안 산식은 유지합니다.

- `3.3.1` (2026-09-05): 코트 기반 3일 AUTO cap을 제거하고 주별 중단 답변·확정 중단 bridge·run-local HIGH 근거·전신/국소 제한 분리를 적용했습니다. physiological engines/finite allocator/legacy 경로는 유지합니다.
- `3.3.0` (2026-09-05): 장기 strain/adaptation/tolerance와 중단 인지 지속 용량을 추가하고 전신 soft dose 중복을 제거했습니다. SUPPORTIVE가 후속 DIRECT 후보를 지우지 않도록 수정했습니다. representation·Objective V2·OFI·strength posterior·tissue engine·legacy builder의 수치는 변경하지 않았습니다.

- `3.2.0` (2026-09-05): 유한 cross-domain 재배분, 처방 기반 시간 용량, canonical seed 처방, 명시적 SUPPORTIVE 보조운동과 proactive 세 질문을 연결했습니다. representation 임계값·Objective V2 계수·strength posterior·tissue 회복식·legacy builder는 변경하지 않았습니다.

- `3.1.1` (2026-09-04): 비압력 발달 gap이 실제 anchor 전환 압력에 기여하지 않는 실행 계약을 고정하고, production tissue `MODERATE`를 `.30`으로 명시 매핑했으며, Python/Kotlin badminton parity가 raw 기록에서 실제 production analyzer를 통과하도록 강화했습니다.
- `3.1.0` (2026-09-04): binary presence gap을 보수적 exposure representation 신호로 교체하고, 동일 priority movement peer median, normalized personal retention, Objective V2 weighted/DIRECT 분리, 최대 한 optional developmental candidate, typed athletic domain과 별도 bout budget, reviewed prescription gate를 계약화했습니다. 충분성·절대 주간 최소량·아홉 objective 동일 목표는 도입하지 않았습니다.
- `3.0.1` (2026-09-04): per-anchor multi-day 배치, repair 이후 budget/fingerprint 확정, 연속 posterior 입력, Persona 28 비교 계약, gap priority, 설명 가능한 최소 용량 확장, optional-anchor 유한 배정과 명시적 AUTO override를 보정했습니다. global gap pressure, active-week baseline, 2-bout drill 기본값, 보수적 진행과 기존 임계값은 유지했습니다.
- `3.0.0` (2026-09-04): 기록 기반 planner를 v0.10으로 올려 56일 current-block window, per-anchor style feature와 structure/dose 전환, 주간 저항·drill 분리 budget, 일괄 preflight, AUTO 조건, 최근 주 strongest load 기준과 미래 자동 증량 금지를 계약화했습니다.
- `2.0.0` (2026-09-03): 기존 자동 builder를 유지한 채 완료 기록 기반의 독립 builder, 조건부 의도 질문, 동적 2~6주 horizon, strength-style 보존, provenance 및 portable backup 계약을 추가했습니다.
- `1.5.0` (2026-08-15): deleted the zero-consumer advanced ProgramBuilder reservoir/beam/evaluation stack, retained the public deterministic pipeline and 59-key authority, and verified the public golden matrix unchanged.
- `1.4.0` (2026-08-15): ProgramRuleTables의 59 exact stableKey를 sole candidate authority로 고정하고 192-scenario public output parity를 추가했습니다.
- `1.3.0` (2026-07-30): 내부 optimization action과 사용자 완료 문구를
  typed notice 및 locale resource 경계로 분리했습니다.
- `1.2.0` (2026-07-30): 저장 프로그램 적용을 fatigue/readiness와 분리한
  exact materialization 계약을 추가했습니다.
- `1.1.0` (2026-07-30): 기록 범위의 exact manual program 변환, set별
  authoritative prescription, legacy scalar fallback과 저장 프로그램 card
  표시 계약을 추가했습니다.
- `1.0.1` (2026-07-28): program seed, generation spec와 future entry의
  exercise reference를 canonical stableKey-only로 고정했습니다.
- `1.0.0` (2026-07-17): 현재 local `main` runtime을 감사해 첫 governed contract로 등록했습니다.
