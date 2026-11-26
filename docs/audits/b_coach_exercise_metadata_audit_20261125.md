# B-Coach 운동 메타데이터 감사 · 2026-11-25

감사 시작 SHA **f52608c4007dda372cf0546106ccbdaf3cb91163**, 작업 브랜치 **audit/b-coach-exercise-metadata-20261125**. 직전 로컬 작업 트리는 깨끗한 main이었고 로컬 HEAD와 origin/main은 모두 9fc0cced8c759a37b8b7a056566a0aaed81105cf였다. 관련 브랜치의 원격 최신 커밋 f52608c4는 main을 포함하고 3커밋 앞서 있었다. 해당 커밋으로 별도 worktree를 만들었다. [원본 PR #93](https://github.com/Mascollel-Ko/WhatYouGottaDo/pull/93) · [기준 SHA CI](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/45559810371) · [당시 main CI](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/45402165834)

## 주요 결론

- 선택 가능한 번들 243개와 이력 전용 11개를 안정 식별자(stableKey)로 대조했다. 필수 구조 공백은 0건이다. 운동역학 검토에서 수평 당기기, 견갑·외회전, 편측 안정과 같은 과제에 개선 후보가 집중됐다. 현재 코드의 큰 근육 묶음은 주간 분배에 쓸 수 있으나 일별 국소 부담에는 해상도가 부족하다.
- 결합 운동의 원본에는 케이블과 덤벨이 각각 등록돼 있다. 초기 후보 선별기는 구분자 | 를 처리하지 않고, 최종 선별기는 처리한다. 일부 운동은 장비 부가 태그까지 필수로 처리한다. 복합 진행 지표는 실제 중량이 기록될 수 있는 운동에서 자동 중량 처방을 건너뛸 수 있다.
- 교환 후보는 생성된다. 주동근 겹침 감소량은 최종 회복 점수에 직접 들어가지 않는다. 다른 조건이 동일한 교환에 원안 변경 비용만 남을 수 있다. 사용자가 본 목·토 일정은 실제 저장 키와 처방을 얻어야 결론을 낼 수 있다.

## 범위와 완료율

| 항목 | 수치와 분모 |
|---|---|
| 정본 식별자 | 254개 |
| 선택 가능한 번들 / 초기 활성 | 243/254개 |
| 프로그램 후보 자격 라벨 | 235/243개 PROGRAM_SELECTABLE, 2 ANALYSIS_ONLY, 6 FATIGUE_ONLY. 개인 설정과 기록에 따라 실제 후보 수는 변한다. |
| 이력 전용 / 초기 비활성 | 11/254개. 전부 HISTORY_ONLY_GENERIC이다. |
| 정본 밖의 legacy | 폐기 일반 키 5개는 별도 저장·이전·OFI 호환 경로에서 확인했다. import map의 고유 원본 키 37개에는 이들 일부가 포함돼 254개에 더하지 않았다. |
| 자동 데이터 검사 | 254/254개 키를 일대일 조인하고 선택 가능 243/243개에 필수 항목 검사를 실행했다. |
| 기본 해부학·역학 검토 | 243/243개에 현재 장비·관절동작·설명·역할과 계열별 역학/처방 판단을 연결했다. 관계에 따라 키별 보류·수정 제안을 붙였다. 이력 11/11개는 역사 보존 경계를 검토했다. |
| 미검토 | 번들 기본 검토 0/254개. 원본에 REVIEW_REQUIRED 6개 운동·7관계가 남아 있다. 실제 사용자 DB의 활성/보관/사용자 운동, 이름만 제공된 3개 운동 및 개인의 효과는 분모 밖이다. |
| PRIMARY·SECONDARY·STABILIZER | 각각 188/243, 90/243, 164/243개. 서로 겹치는 집합이다. PROGRAM_SELECTABLE 235개에서도 각 분자는 같다. |
| 기타 역할 | GENERAL_PARTICIPATION 54/243, NOT_SUPPORTED 25/243, REVIEW_REQUIRED 6/243개. PROGRAM_SELECTABLE의 PRIMARY 없음 47/235개. |
| 필수 프로그램 값 공백 | 0건. 선택 가능 운동의 17개 핵심 런타임 필드와 휴식, 장비·근육·동작·코어 관계 존재 기준. 명시적인 NONE, NOT_APPLICABLE은 값으로 인정했다. |
| 보조 표 공백 | 진행 관계 48개, 회복 관계 1개. 진행 47개는 strengthProgressionGroup=NOT_APPLICABLE이다. 인버티드 로우 ex_e159d15a에는 두 보조 표가 비어 있지만 런타임 진행과 회복 값은 존재한다. |
| 운동별 수정·재판정 권장 | 87/243개. 운동당 최고 중요도 P1 36, P2 51이다. 검색 정책의 영향 운동은 별도 검색 표에 있다. |
| 원인별 중요도 | P0 0개 / P1 7개 / P2 6개 / P3 1개. 같은 원인이 영향을 주는 운동 수는 CSV에서 별도로 센다. |

[운동별 전수 CSV](b_coach_exercise_metadata_audit_20261125.csv)에는 1키 1행으로 실제 값, 사용 경로, 근거와 중요도를 기록했다. [기초 역학 검토표](b_coach_exercise_metadata_review_20261125.tsv)는 65개 family의 검토 기준이다. 각 운동의 관절동작, 기구, 편측, 설명, 현재 관계와 개별 수정 근거는 CSV에서 확인할 수 있다. 이는 번들 설명과 역할의 일차 검토이며 운동마다 개별 실험으로 근육 자극 계수와 회복 일수를 확정하지 않았다.

10월의 762관계 감사에는 기존 역할을 일괄 정리하는 잠정 제안이 포함돼 있었다. 이번 기준 SHA에는 이후 주동근 누락 보완, UI/분석 정합 보완, 11월 결합 로우 추가가 반영돼 있고 근육 관계는 790개다. 예를 들어 오버헤드 프레스에서 광배 훈련량을 적립하던 과거 오류는 현행 근육 역할 감사에서 이미 제거된 상태다. 11월 21일 감사에서 확인된 코어 운동과 풋워크·감속의 잘못된 전이는 11월 22일 통합 권위에서 NONE으로 교정됐고, 현재 CSV에서 데드버그·팔로프·겟업의 관계를 재확인했다. [10월 감사](canonical_muscle_762_workbook_review_20261019.md) · [11월 전이 감사](b_coach_inherited_core_sport_transfer_review_2026-11-21.md) · [통합 변경](b_coach_integrated_planning.md)

## 원본과 런타임 우선순위

1. **작성과 배포.** 권위 XLSX의 10_MUSCLE_REL, 09_MOVEMENT_REL, 13_RECOVERY_REL, 15_PROGRESSION, 20_EQUIPMENT_REL 등에서 exporter가 canonical_v1 CSV와 manifest를 만든다. Physical quality는 검토된 별도 도메인 원본을 해시와 함께 재배포한다. 배드민턴 목표는 별도 v2 권위와 검토 결정, B 코어 과제는 b_balanced_v1/target_task_relations.csv를 사용한다. 이름·라벨의 현지화 원본은 별도다. [원본 관리](../metadata_authority/README.md) · [exporter](../../tools/metadata_authority/export_canonical_metadata.py)
2. **로딩과 사용자 수정.** CanonicalExerciseMetadataRepository가 manifest, 키, 스코프, 중복을 확인한다. exercise_bootstrap.csv가 SeedData·Room Exercise의 초기 권위이며 runtime_metadata.csv는 별도의 exact-key 카탈로그다. ExerciseMetadataReconciliationService가 canonical 값과 명시된 사용자 override를 합친다. 명시적 빈 값도 사용자 의도다. V22는 UserMuscleRoleCodec.effectiveRelations에서 exact-key typed 사용자 근육 관계를 적용한다. GlobalSelector.prepareLayout은 family, redundancy, primary stress 세 필드를 exact-key runtime 카탈로그로 덮고, 카탈로그에 없으면 기존 skeleton 필드를 둔다. 장비나 RIR이 이 덮어쓰기의 일부는 아니다.
3. **이력·커스텀·이름.** 11개 HISTORY_ONLY_GENERIC이 비활성 seed로 남는다. 근육 resolver는 선택 가능한 canonical, 명시된 custom typed 관계, 미매핑을 구분한다. 코어/배드민턴의 호환 계승은 승인된 변형이 도메인별 동일 서명일 때만 성립한다. 과거 pull_up 등 폐기 5개에는 복원·프로그램 이전·이력 OFI 별칭이 있고 동작 이름 유사도에서 근육을 빌리지 않는다. 신규 사용자 운동의 UUID 계열 stableKey는 편집·rename에서 보존된다. 빈 키가 있는 과거 복원은 해당 백업 안에서 임시 그룹화 후 독립 custom key를 부여한다. [식별과 호환 계약](../protocols/data_portability/EXERCISE_IDENTITY_AND_CANONICALIZATION.md)
4. **레거시 소비.** 과거 seed pass3와 legacy 근육 문자열은 정본 관계의 자동 근거가 아니다. B의 이력 기반 시작안은 V22PythonParityEngine.build를 호출한다. classify는 데드버그·팔로프·컬·풀업·로우 등의 이름 및 일부 키를 문자열 분류한다. 기록은 키로 그룹화하지만 이후 최신 기록 이름이 바뀌면 초기 역할이 바뀔 수 있다. B 블록형은 초기안과 joint 안을 다시 평가하고, 제안이 없을 때 새 메타데이터 로스터를 시도한다. 구형 B12 호출은 최종 교환 탐색에 진입하지 않는다. [호환 소비자 126행](metadata_legacy_compatibility_consumers.md)

## 필드별 소비 경로

V22ProgramPlanningService.generate → BBalanced12WeekEngine.build → BBalancedDayPlacementPlanner 및 블록 처방·공동 최적화 → BCoachMultiobjectiveProduction.prepare → BCoachV2GlobalSelector.select.

| 단계 / 원본 필드 | 함수와 실제 계산·누락 처리 | 적용과 사용자 영향 |
|---|---|---|
| 1 후보 · Room의 activityKind, planningEligibility, confidence | BBalanced12WeekEngine.build/coldStartBaseline, BCoachJointBlockOptimizer.profile, BJointExerciseSetOptimizer.facets, GlobalSelector.rosterNeighbors. 초기 active, non-sport, 자격 조건. 후단은 review/custom 조건 추가. BCoachV2Policy.isTestOnlyExercise는 태그와 이름 접미사 참조. legacy 시작안은 명시적으로 SPORT_SESSION을 제외하고 빈 날이면 새 시드로 전환. | 후보 필터. 같은 기록과 설정에서도 시작안과 새 후보의 적격 조건이 다르다. |
| 2 장비 · equipment_relations 및 Exercise.equipment | coldStartBaseline.permitted, BCoachJointBlockOptimizer.permitted, rosterNeighbors.permitted. 초기 두 파서는 파이프 미분리. 최종은 분리한 모든 토큰 또는 exact compound. 빈 장비와 맨몸 허용. typed NONE/SINGLE/ALL_REQUIRED 직접 미사용. | 후보 제외. 실제 필요한 장비를 갖춰도 부가 태그 때문에 빠질 수 있다. |
| 3 메인·보조 · program_slot_capabilities, training_roles, programSlot, selectionRole | V22는 MAIN_STRENGTH_SLOT 집합을 전달. BCoachJointBlockOptimizer.selectedMainKeys와 BJointExerciseSetOptimizer는 명시 MAIN·선택된 앵커를 분리한다. CanonicalStrengthExposureCapability와 StrengthPrimaryMainPolicy는 별도 권위. GlobalSelector는 전달된 자격 집합도 메인처럼 보호한다. training role 및 나머지 capability는 editor·reconciliation·backup에 연결되고 V2에서 고정 점수로 쓰이지 않는다. | 후보, 순서, 진행, 교환 보호. 스쿼트·벤치에 이 capability 행이 없어도 승인 앵커로 처리할 수 있고, 실제 보조로 놓인 자격 운동의 이동은 제한될 수 있다. |
| 4 일 배치 · bootstrap movementPattern, 휴식, heavy | BBalancedDayPlacementPlanner.estimateMinutes/search/lowerBody. 시간 초과 제곱×3, 최대 시간×0.14, 인접 같은 키 20·heavy 24·하체 2, 2일 거리 heavy 2, 이동 0.12 등. BCoachJointBlockOptimizer.score의 인접 주동근 세트 겹침은 근육별 양일 최솟값 합×0.0025. | 초기 배치와 점수. 초기 순환 요일 거리와 최종 실제 달력 회복 점수를 구분한다. |
| 5 동작 중복 · family, group, subtype | GlobalSelector.prepareLayout → BCoachSessionComposition.families/neighbors → DoseEvaluator.evaluate/hardConstraints. family와 group이 모두 같을 때 묶음. 미정은 제외. 뒤 운동 세트 / 전체가 overlap이며 근비대량 ×0.8. 서로 다른 변형 2개 초과 실패. subtype은 여기서 직접 비교하지 않는다. | 탐색·점수·검토 가능 한도. 같은 세션에서 유사 운동 두 개가 허용될 수 있다. |
| 6 근육 자극량 · muscle_relations + typed override | contributionMap, BJointExerciseSetOptimizer.facets, BCoachJointBlockOptimizer.score, DoseEvaluator.muscleCredits/structuralCredit/setDose. 초기·BJoint는 PRIMARY 중심. Joint 1/0.35, V2 1/0.5×시나리오 1.15/1/0.85. 나머지 0. V2는 구조 credit×반복×노력×강도×부하×단위, 반복 5–30은 근비대 보정 1, 미교정 저항량은 .75. 각 숫자식에서 원본 coefficient를 직접 읽지 않는다. | 후보·점수 및 BJoint 커버리지 보존. 같은 근육 원본 관계도 후보 단계별 평가량이 다르다. |
| 7 코어 · canonical core, B target tasks | contributionMap, BJointExerciseSetOptimizer.facets, DoseEvaluator.tasks/structuralCredit. DIRECT 1, HIDDEN/없음 0. BJoint는 승인 세부 과제 또는 DIRECT_CORE로 커버리지를 본다. V2 최종량은 직접 코어 총량이다. | 후보·커버리지·점수. 코어 세부 과제가 여러 행이어도 직접 세트를 배수로 적립하지 않는다. |
| 8 피로 · runtime primaryStressProfile, Bootstrap 여섯 수치 | BCoachSessionComposition.concentration과 ScheduleLoad.evaluate/setAdjustment. 부위별 일 최대 세트 초과분 max(0, peak−max(4, ceil(weekSets/2))) / 주 전체. 여섯 축은 전신·고힘 신경·고속·전체 국소 근육·감속·탄성. localMuscular는 .55×반복 보정+.45×노력 보정. 누락 강도/RIR factor 1, Exercise 없음 0과 unmapped 신호. | 점수. 특정 근육별 피로량이 아니며 secondaryStressTags, localMuscularStressLevel 문자를 직접 합하지 않는다. |
| 9 회복 · 회복 분류, 관절/건, 집중 요구 | BCoachV2ScheduleLoad.evaluate. 0–3일 축별 커널, max(1, observed Q75, baseline Q75) 정규화 후 r/(1+r), .4 평균+.6 최대의 축 평균. DoseEvaluator의 RECOVERY_LOAD에 .15×유사 세트와 .10×부위 집중 추가. recoveryProfiles의 운동별 등급, 관절/건/집중 태그를 직접 읽는 B hard gate를 확인하지 못했다. 정식 OFI/RCV 분석은 별도 경로다. | 점수·일정/시간 한도. 묶여 있는 전체 국소 근육 및 관절·건의 실제 회복 기간을 보여 주는 값으로 해석하기 어렵다. |
| 10 재배치 · PRIMARY·요일·잠금 | BCoachSessionComposition.primaryMuscles/primarySwapNeighbors → GlobalSelector.searchNeighbors/register. 같은 주·블록, 두 날의 PRIMARY 겹침 변화. 누락은 빈 집합, 패턴 8개로 제한. | 생성·탐색. 후보별 같은 평가 경로는 아래에 검증했다. |
| 11 진행·처방 · progressMetricType, strengthProgressionGroup, rest·세트 | BBlockPurposePrescription.prepare/accessoryPrescription, LoadCandidateResolver.resolve/nonLoadMetric, DoseEvaluator.progressionFit. 역할/블록 범위, 고정 부하 우선, 비부하/시간 분기, 같은 키 단위의 모델·기록·사용자 교정. 목표 RIR은 기존 target RPE 우선, 약한 증거 4, 비대/peak 2.5, 일반·근력·스포츠 3. progression_relations는 이 경로의 직접 입력이 아니다. | 후보 처방·점수. 무게나 RIR이 필요 없는 과제는 별도 분기 가능한 반면 복합 지표가 중량을 가릴 수 있다. |
| 12 목적과 전이 · badminton objective, physical quality, typed movement | rosterNeighbors, 두 공동 optimizer, DoseEvaluator.tasks/structuralCredit. V2 목표별 max DIRECT 1 / SUPPORTIVE .6 / GENERAL .25 / LOW .1 / 없음 0. Joint는 1/.55/.2/.07. Physical quality는 stimulus ledger 및 분석 경로에서 소비되고 V2 점수 직접 항이 아니다. | 후보·목표 점수. 세션형 경기와 시간/반복형 드릴을 분리한다. 세부 전이 데이터의 DIRECT가 실제 경기 개선을 검증했다는 뜻은 아니다. |
| 13 최종 · 목표·제약 | GlobalSelector.searchComparator/select, BCoachConstraintReviewPolicy. 정책 가중치 부족 .40, 초과 .09, 진행 .16, 회복 .14, 변화량 .07, 실행 .06, 원안 변경 .04, 불확실 .04. 빔은 차단/사용자 확인 건수, 최악 손실, base loss, 서명으로 비교. 최종은 제약 우선·시나리오 regret 등. | 점수와 차단/검토. 시간, 범위, 세트, 중복, main 순서, 요일, 새 운동, 사용자 lock을 검사. 근육 겹침 감소의 독립 점수나 hard gate는 없다. |

[앱 호출](../../app/src/main/java/com/training/trackplanner/data/v22/V22ProgramPlanningService.kt) · [초기 생성](../../app/src/main/java/com/training/trackplanner/data/v22/BBalanced12WeekEngine.kt) · [요일 배치](../../app/src/main/java/com/training/trackplanner/data/v22/BBalancedDayPlacementPlanner.kt) · [공동 최적화](../../app/src/main/java/com/training/trackplanner/data/v22/BCoachJointBlockOptimizer.kt) · [세트 최적화](../../app/src/main/java/com/training/trackplanner/data/v22/BJointExerciseSetOptimizer.kt) · [블록 처방](../../app/src/main/java/com/training/trackplanner/data/v22/BBlockPurposePrescription.kt) · [최종 탐색](../../app/src/main/java/com/training/trackplanner/data/v22/BCoachV2GlobalSelector.kt) · [목표량 평가](../../app/src/main/java/com/training/trackplanner/data/v22/BCoachV2DoseEvaluator.kt) · [일정 피로](../../app/src/main/java/com/training/trackplanner/data/v22/BCoachV2ScheduleLoad.kt)

## 중복 세 종류와 근육 코드

**동작 대체 가능성**은 움직임·그립·장비·저항 방향을 비교한다. **국소 피로**는 다른 운동에서도 같은 근육의 실제 세트·역할·강도·간격에 의해 발생한다. **훈련 목적**은 항신전·항회전·신전·파워처럼 직접 수행하는 과제다. B는 첫 항목을 family/group으로 직접 평가하고, 국소 부담은 부위와 6축 점수 및 새 탐색 신호로 부분 반영하며, 목적은 BJoint 과제·전이 벡터와 최종 직접 코어 합계를 통해 일부 반영한다. 특정 근육에 자극을 집중하려는 의도나 다른 속도·강도로 주는 부하는 동일 날 편성의 타당한 이유가 될 수 있다.

관측된 16개 코드 중 CHEST, BACK_LATS, SHOULDERS, BICEPS, TRICEPS, FOREARM_GRIP, ANTERIOR_CORE, LATERAL_CORE, POSTERIOR_CHAIN_ERECTORS, GLUTES, GLUTE_MEDIUS, QUADS, HAMSTRINGS, HIP_ADDUCTOR, CALVES와 ADDUCTOR_ABDUCTOR가 있다. 마지막 코드는 런지 5개에 남은 보류 관계다. 역할은 PRIMARY, SECONDARY, STABILIZER, GENERAL_PARTICIPATION, NOT_SUPPORTED, REVIEW_REQUIRED다. NOT_SUPPORTED 28행은 광배 등 특정 근육을 훈련량으로 인정하지 않는 명시적 0이다. GENERAL_PARTICIPATION은 주행·코트 동작·접촉 드릴 등의 참여를 표현한다. 코어의 항신전/항측굴, 캐리의 그립 등척성은 직접 목표라면 PRIMARY로 적절하다.

광배와 견갑 후인근, 삼각근 전/측/후 및 회전근개·전거근, 팔꿈치 굴곡과 손목/그립을 구별하기 어렵다. 두 코어 코드로는 복직근·복사근·몸통 과제의 차이를 한꺼번에 드러내기 힘들다. 종아리는 무릎 굴곡에 따른 비복근·가자미근 기여가 달라진다. 이미 jointAction, subtype, 코어 과제, physical quality 및 조직 RCV가 일부 차이를 표현한다. 조직 부하와 근육 자극에는 별도 근거가 필요하다. 코드 세분화 이전에 기존 동작·과제 분류로 오탐이 얼마나 줄어드는지 측정하면 이력·사용자 override 호환성과 유지비용을 관리하기 쉽다.

## 요청 사례

정본 CSV, 원본과 현지화 XLSX, 리소스, legacy import, 버전 관리 텍스트를 확인했다. **하이 풀업, AB 슬라이드, 머신 컬의 stableKey는 확인되지 않았다.** AB wheel은 과거 설계 문서의 검토 예시에 나온다. 실제 사용자 DB나 단말의 최근 프로그램 스냅샷, 세트별 처방·잠금에는 접근하지 않았다. 사용자 정의, 미배포 운동, 다른 이름 가운데 어느 경우인지 확정할 수 없다.

| 사례 · 현재 값 | 실제 과제, 현재 B 해석과 권고 |
|---|---|
| 중량 풀업 ex_e41f4c2b, 과거 일반 풀업 pull_up, 하이 풀업 미식별 | 중량 풀업은 LAT 주·이두/어깨 보조, 수직 당기기/e1RM. 일반 풀업 키는 기존 복원·seed 이전·이력 OFI 승인 경로가 있다. 고속으로 높게 당기는 하이 풀업이라면 피로가 일부 겹치지만 가동 범위·속도 때문에 근력·파워 목적이 달라질 수 있다. 수행과 키부터 확인한다. [Youdas 등](https://pubmed.ncbi.nlm.nih.gov/21068680/) |
| 풀업·랫풀다운 ex_dc9e5953·바벨 로우 ex_de46b7f6 | 풀업과 풀다운은 광배와 팔꿈치 굴곡을 공유하며 개방/폐쇄 사슬로 차이가 난다. 무지지 로우는 별도 family이나 BACK_LATS PRIMARY가 겹친다. 견갑 후인과 척추 고정, 팔꿈치 경로를 함께 검토한다. 일부 피로 중복의 근거는 있다. [Lehman 등](https://pubmed.ncbi.nlm.nih.gov/15228624/) |
| 덤벨 컬 ex_281347da·해머 컬 ex_2892da5a·머신 컬 미식별 | 앞 두 운동 모두 BICEPS PRIMARY지만 중립과 회외 자세의 상완근·상완요골근 역할이 다르다. 머신 컬이라면 대체 가능성이 있으나 지지 위치·저항 곡선·단위와 키를 확보해야 한다. 보조목적의 공통 부분과 국소 강도의 차이를 기록한다. [Coratella 등](https://pubmed.ncbi.nlm.nih.gov/36976950/) |
| 데드버그 ex_d5bdffe1·케이블 팔로프 cable_pallof_press·AB 슬라이드 미식별 | 데드버그 ANTERIOR_CORE PRIMARY / DIRECT 항신전·동적 안정. 팔로프는 ANTERIOR/LATERAL PRIMARY / DIRECT 항회전. 롤아웃형 AB 슬라이드라면 데드버그와 항신전이 겹치고 지렛대·어깨 요구는 더 달라진다. [Escamilla 등](https://pubmed.ncbi.nlm.nih.gov/16494072/) |
| 백스쿼트 barbell_back_squat·레그 익스텐션 ex_b78a8f95·박스 스텝업 ex_6232f4bc | 모두 QUADS PRIMARY, 스쿼트·스텝업은 GLUTES도 PRIMARY. 가족은 세 개로 다르다. 스쿼트는 근력 앵커, 익스텐션은 무릎 신전 보조, 스텝업은 편측과 균형 보조. 대퇴사두 집중 처방이면 동시 배치가 합리적일 수 있다. [Kassiano 등](https://pubmed.ncbi.nlm.nih.gov/41379528/) |
| 컨벤셔널 barbell_deadlift·바벨 RDL barbell_romanian_deadlift | 같은 힌지 가족/e1RM, GLUTES/HAMSTRINGS 주, 기립근 안정. 바닥에서 무릎을 펴는 구간과 편심 힌지의 부담이 다르다. 처방에 따라 메인 또는 보조가 된다. 그립과 무릎 신전 참여를 따로 검토한다. [Lee 등](https://pubmed.ncbi.nlm.nih.gov/30662500/) |
| 벤치 barbell_bench_press·인클라인 덤벨 ex_a61f1e96·OHP ex_32219f7a | 벤치와 인클라인은 가슴 주동근으로 같은 family이나 각도에 따라 전면 어깨가 달라진다. 수직 밀기 OHP는 SHOULDERS PRIMARY이며 삼두 보조가 겹친다. 하중·기구·각도와 역할을 보존한다. [Rodríguez-Ridao 등](https://pubmed.ncbi.nlm.nih.gov/33049982/) |
| 페이스풀 face_pull·리버스 플라이 dumbbell_rear_delt_fly 등·밴드 외회전 ex_e8ff8cfa | 견갑 후인/외회전, 상완 수평외전, 회전근개 외회전이 각각 중심이다. SHOULDERS PRIMARY로 통합되어 대체/피로를 오인할 수 있다. 페이스풀 BACK_LATS 보조 행을 재판정하고 케이블 부하와 동작 질을 별도로 확인한다. |
| 원레그 카프 ex_5ca7133f·일반 카프 | 저측굴곡 CALVES PRIMARY의 국소 겹침은 타당하다. 일반 스탠딩 ex_bd072cd는 이력 전용이고 덤벨/맨몸/머신 정본이 있다. 좌식 ex_5c8751d2의 무릎 각도는 비복근 자극을 바꾼다. 편측의 균형과 기록 단위를 유지한다. [Kinoshita 등](https://pubmed.ncbi.nlm.nih.gov/38156065/) |
| 스플릿 스탠스 결합 로우 + 덤벨 OHP | split_stance_contralateral_cable_row_dumbbell_press. BACK_LATS/SHOULDERS/LATERAL_CORE 주, BICEPS/TRICEPS/CHEST 보조, DIRECT 항회전, STABILITY_SLOT, QUALITY_BASED, typed 케이블+덤벨. B 과제의 기록 단위는 LOAD_REPETITIONS_PER_SIDE. 당기기와 항회전 중복을 분리하고, 대흉근 보조 분류와 양쪽 하중 기록, 장비 소비를 검증한다. 전이는 사용자 승인 훈련 우선순위이며 성능 실증은 없다. |

EMG는 관여와 과제 차이의 단서다. 근활성 수치를 앱의 근비대/피로 계수로 그대로 환산할 수 없다. 종아리와 스쿼트/익스텐션은 비교 훈련 연구에서 근육 크기 결과도 확인했다. 그 연구의 대상·기구·관절각·수행 범위를 벗어난 판단은 역학상 추론으로 표시한다. [EMG 해석 한계](https://pmc.ncbi.nlm.nih.gov/articles/PMC5758546/) · [원본 근거와 검토 이력](../metadata_evidence_sources_v0.3.5.0.md)

## SWAP_PRIMARY 집중 검증

같은 주·같은 블록 두 날에서 각각의 이동 가능 항목이 해당 날짜의 다른 운동과 PRIMARY를 공유해야 한다. 관계 없는 키와 같은 키 쌍은 0이다. 양쪽 날짜 점수가 모두 악화되지 않아야 하고 합이 낮아져야 한다. 서로 다른 family/group이어도 가능하다. 교환 후 같은 키를 한 날에 중복 배치하지 않는다. 같은 블록·요일·키 패턴으로 반복 가능한 주를 묶는다. recovery slot은 calendar에서 null이면 제외한다. 명시·정책 메인, 전달된 메인 자격 집합, 템플릿 앵커, 연결 진행/anchor set, 사용자 운동/부하 고정, 스포츠·재활·peak 행은 보호한다. [교환 구현](../../app/src/main/java/com/training/trackplanner/data/v22/BCoachSessionComposition.kt)

원시 교환에서 날짜와 순서만 바뀐다. 세트·반복·주차·localId는 운동과 함께 이동하며 각 날의 세트 수와 시간은 달라질 수 있다. 이후 후보 준비에서 메인 순서, 반복 범위, 후보별 RIR와 하중을 다시 계산한다. 탐색 순위에서 1세트 저강도와 5세트 고강도는 주동근 코드 교집합이 같으면 같은 수로 기여한다.

상위 8패턴, 빔 4개, 5라운드, 등록 상한 256개 고유 상태, 최종안 12개다. 시간 제약 재검토가 있으면 먼저 제안하고, 주동근 이웃은 일반 로스터·세트 변형보다 앞서 제안한다. 등록 상한 이후에는 평가 호출도 없으며 동등 fingerprint는 평가 호출 후 등록에서 제거된다. 원안·joint와 같은 evaluator에 들어간 후보도 시간·family 최대 2·요일 등 제약과 종합 점수에서 걸러질 수 있다.

목요일의 확인된 운동만으로도 중량 풀업과 결합 로우의 BACK_LATS, 익스텐션과 스텝업의 QUADS, 팔로프와 결합 로우의 LATERAL_CORE가 겹친다. 토요일에서 확인된 데드버그·페이스풀·카프·덤벨 컬의 PRIMARY는 각각 다르다. 확인된 정보만 사용하면 토요일 PRIMARY 겹침이 0이다. 실제 하이 풀업과 AB 슬라이드의 저장 키·typed 역할과 처방을 확인해야 한다. AB 롤아웃이 복부 PRIMARY이면 데드버그와 토요일에 겹칠 수 있지만, 목요일로 옮겨도 팔로프 ANTERIOR_CORE와 새 겹침이 생길 수 있다. 이는 가정에 따른 비교이며 저장 키나 근육을 AB 슬라이드에 부여하지 않았다.

[감사용 Kotlin fixture](BCoachPrimarySwapAudit20261125.kt)는 동작 가족이 서로 다른 네 가상 항목의 스트레스·세트와 시간 입력을 통제한다. 양일 겹침 1→0이어도 공식 evaluator의 회복/목표 부족/초과 점수에 감소량이 들어가지 않는다. 기록이나 연결 원본이 있는 경우 이동 2개는 추가 2와 제거 2로 집계되어 원안 변경 점수가 0.5, 가중치 .04에서 base loss +.02가 된다. 완전한 신규 생성에서는 원안 변경 비용이 0이다. 두 번째 fixture는 5세트/2세트 교환에서 일별 7/4→4/7, 주간 원시 처방 보존을 검사한다. 이는 평가식 비교이며 실제 사용자의 탐색 방문 로그나 최종 선택을 재현한 결과는 아니다.

## 문제 목록과 수정 후 검증

P0의 운동 ID 연결 손실이나 저장 손상 사례는 관찰되지 않았다.

| ID | 유형과 근거 | 원인·영향 및 수정 후 확인 |
|---|---|---|
| P1-01 | 장비 소비 · 코드 입증 | CABLE/DUMBBELL 입력을 동일하게 줘도 초기와 후단 결과가 다르다. 화면 설정에서 케이블은 한국어 토큰으로만 전달돼 CABLE과 직접 일치하지 않는다. ex_728da646의 단일 머신과 ex_704cbf1a의 박스+덤벨에 부가 태그가 강제될 수 있다. typed 모델과 요청 장비 매핑을 한 곳에서 소비하고 NONE/SINGLE/ALL_REQUIRED, 누락·부분·완전 보유를 단계별로 시험한다. |
| P1-02 | 국소 피로 소비 · 평가 비교 | 교환 탐색의 개선이 최종 회복 점수에서 소실된다. 후보 방문·제약·회복·원안 변경을 기록하고, 세트, 강도, 근비대 집중 의도를 고정한 교환/무교환에서 실제 이득을 확인한다. |
| P1-03 | 중량/단위 소비 · 코드 입증 | TIME/QUALITY 문자열이 중량 모델 분기 앞에서 처리된다. LOAD_REPS_OR_TIME 5종, QUALITY_LOAD_REPS 겟업, QUALITY_BASED 페이스풀·팔로프·결합 운동 등은 기술과 중량 기록의 결합을 확인한다. 같은 키의 관측 무게/고정 부하/무게 없음, 양쪽 하중과 기술 범위를 독립 검증한다. |
| P1-04 | legacy 시드 · 코드 경로, 실사용 미확정 | history seed의 이름 분류와 새 자동 추가 키에 적용되는 review gate가 공존한다. 새 후보와 달리 시드의 보관·이력 전용 운동 적격성이 전 구간에서 보호되는지 확인 필요. rename과 보관 전후 동일 키의 생성, 유지·삭제 의도를 테스트한다. |
| P1-05 | 근육 원본 · 운동역학 판단 | 미니밴드 사이드워크 GLUTES, 벤치 딥스 CHEST 주·TRICEPS 보조, 페이스풀 BACK_LATS 보조, 무지지 바벨 로우 척추 안정 등. 작성된 설명·기구에서 각 역할을 재승인하고 입력만 바꾼 계획·근육량·회복 비교를 한다. |
| P1-06 | 메인 자격 소비 · 코드 입증 | 초기 optimizer가 자격과 실제 메인을 분리해도 V2는 전달된 전체 자격 집합을 메인 신호로 사용한다. 동일 키를 실제 메인과 보조로 처방해 메인 순서, 교환 보호, 진행·불확실성과 연결/고정을 비교한다. |
| P1-07 | 최종 근비대 목표 소비 · 코드 입증 | V2는 대퇴사두·둔근·햄스트링·종아리·가슴·광배·어깨·이두·삼두 9개만 비대 task로 생성한다. 올바른 HIP_ADDUCTOR, GLUTE_MEDIUS, FOREARM_GRIP PRIMARY도 해당 근육의 목표량으로 보이지 않는다. 별도 코어·전이·회복 또는 다른 근육의 점수는 여전히 영향을 줄 수 있다. 사용자가 해당 부위 훈련을 원할 때 원본에 있는 직접 자극을 드러내는지 검증한다. |
| P2-01 | 근육 및 동작 해상도 | 견갑 후인·전인, 회전근개, 삼각근 세 부분, 팔 굴곡·그립, 종아리 무릎 자세가 뭉친다. 스캡 풀업의 수직 당기기 대체, 리어델트·페이스풀·외회전의 교차 관계를 우선 검토한다. |
| P2-02 | 단계별 자극 소비 | 초기·joint·V2·분석의 주/보조 계수, 근거 및 예외가 다르다. BJoint는 PROVISIONAL PRIMARY 5행(하프 닐링 2종, 결합 로우 1종)을 제외하고 V2 교환과 dose는 포함한다. 역할별 출처와 효과를 기록하고 직접 코어·hidden, 중복 할인 후량을 대조한다. |
| P2-03 | 탐색 · 구조 | PRIMARY-only, 양일 겹침 선결, 한쪽 악화 금지, 상위 8 및 공유 예산 때문에 유효할 수 있는 조합이 빠진다. 고강도/저강도, 한쪽 과밀, 5/2세트 조건의 생성/탈락/선정 로그를 비교한다. |
| P2-04 | 원본 공백/근거 | 인버티드 로우 보조 진행·회복 관계 공백, 런지·겟업 보류, 지지 머신에서 코어 안정 과다 지정 가능성이 남는다. 관련 운동의 실제 자세를 확인하고 이력 자동 승계 없이 원본에서 보강한다. |
| P2-05 | 목적 소비 | BJoint에 있는 상세 코어 목적이 최종 총량에서 뭉친다. 항신전·항회전·회전과 의도적 같은 과제를 비교하고 hidden을 direct로 환산하지 않도록 확인한다. |
| P2-06 | 코어 검색·분류 · 런타임 재현 | 직접 코어 32개와 별개로 운동 목록의 '코어' 검색은 201개 비직접 운동을 추가로 반환한다. 검색 목적, 코어 분류와 일반 근육 참여 일치를 구별한다. 한·영 검색, 컬, 보조 자세 유지, 직접 신전·회전, custom 미분류를 시험한다. |
| P3-01 | 문서/관리 | runtime 회복 등급·typed 회복·OFI/조직·B 일정 수치의 용어가 비슷하다. 원본에서 실제 선택·부하·화면 소비까지 연결하는 호환 inventory를 유지한다. |

수정은 장비와 복합 처방 경로의 재현·회귀부터 시작한다. 역학이 명확한 행만 권위 XLSX에서 정정한 후 공식 exporter로 재생성한다. 같은 입력의 실제 생성 프로그램을 비교해 국소 회복·교환 평가를 검증한다. stableKey·사용자 override·모델 계산·연결 진행·캘린더 적용의 호환을 함께 확인한다. 실제 사용자에서의 자극량과 회복은 실측으로 검증해야 한다.

## 코어 검색 재감사

**왜 컬이 나오나.** 덤벨 컬 ex_281347da, 해머 컬 ex_2892da5a와 원암 케이블 컬 ex_3d5719de는 모두 이두가 PRIMARY, 전면 코어가 STABILIZER이며 coreClass는 HIDDEN_LOW다. 공용 검색 matchesMetadataQuery는 주동근·보조근·안정근·일반 참여를 검색 대상으로 허용한다. '전면 코어' 안에 '코어'가 있어 컬이 일치한다. 운동 목록에는 이름·detail1·detail2 텍스트 검색도 OR로 적용돼, detail2에 '전완근/코어'가 있는 원암 케이블 컬은 그 경로로도 들어온다. 프로그램 운동 선택 팝업에서도 공용 검색의 안정근 일치로 세 컬이 나온다. B-Coach의 직접 코어량은 coreClass DIRECT만 사용하므로 이 검색 노출이 컬을 직접 코어 세트로 집계한다는 증거는 없다.

**코어 소분류도 별도 경로다.** STRENGTH_CORE는 효과적인 PRIMARY와 함께 Exercise.movementPattern 등의 TRUNK/ANTI_ROTATION 등 부분 문자열을 허용한다. 원암 케이블 컬은 bootstrap movementPattern이 ANTI_ROTATION이어서 코어 소분류에도 들어간다. 덤벨 컬과 해머 컬은 소분류 검사에서는 빠졌다. 기능성 '코어 조절'은 movement purpose에 더해 일부 경기/보조 태그도 사용한다. 상세 근육 필터에서는 안정근을 의도적으로 고를 수 있으며 역할과 근육을 같이 고르면 둘은 같은 관계 행에 있어야 한다.

| 번들 243개, 기본 필터 | 검색 또는 소분류 결과 | DIRECT | HIDDEN | NONE |
|---|---:|---:|---:|---:|
| 운동 목록 '코어' | 233 | 32 | 176 | 25 |
| 공용 운동 선택 팝업 '코어' | 226 | 32 | 170 | 24 |
| 운동 목록 'core' | 226 | 32 | 170 | 24 |
| 운동 목록 '복근' | 4 | 0 | 4 | 0 |
| 두 코어 소분류의 합집합 | 66 | 31 | 31 | 4 |

운동 목록 검색 결과 중 이름에 '컬'이 들어간 행은 22개이며 팔 컬 외에도 레그 컬과 노르딕이 들어간다. 직접 몸통 신전인 슈퍼맨 ex_284ecca6은 두 코어 소분류 모두에서 빠졌다. [검색 전수 CSV](b_coach_core_search_audit_20261125.csv)는 두 화면의 검색 조건과 두 소분류, detailText 일치, 현재 코어 근육 역할을 243행에 기록한다. 이는 번들 원본과 실제 Kotlin predicate를 사용한 Robolectric 결과다. 사용자 DB override, 현재 UI에서 선택한 필터, 화면 렌더링은 이 결과에 포함하지 않았다. [검색 검증 fixture](BCoachCoreSearchAudit20261125.kt) · [운동 목록](../../app/src/main/java/com/training/trackplanner/ExerciseScreen.kt) · [공용 검색](../../app/src/main/java/com/training/trackplanner/CommonUi.kt) · [근육 검색 권위](../../app/src/main/java/com/training/trackplanner/CanonicalMusclePresentation.kt) · [소분류](../../app/src/main/java/com/training/trackplanner/ExerciseSubcategoryMapper.kt)

'코어'와 'core'가 독립된 운동 목적 검색일 때 두 화면 모두 canonical DIRECT 운동을 기본 결과로 보여 주는 것이 합리적이다. 안정근·간접 코어 운동은 해당 역할이나 보조 필터를 선택해 찾을 수 있게 한다. 소분류도 bootstrap 문자열 대신 canonical 코어 목적을 확인하고, 정본 코어 관계가 없는 사용자 운동의 일반 이름 검색은 유지한다. '복근'과 '복직근'은 몸통 전체와 다른 검색 요청일 수 있으므로 동의어와 정확한 근육 검색의 기대를 별도로 시험한다. 역학상 맞는 안정근 관계는 검색 노출 문제 때문에 삭제할 이유가 없다.

## 검증

- **원본과 배포** · validate_authority_workbook.py, exporter --check, 메타데이터 Python 17/17, 프로토콜 9개군·36개, audit CSV의 manifest SHA-256/관계 키/일대일 조인/CSV round-trip을 통과했다.
- **로컬 핵심 JVM** · Windows JBR 21에서 테스트 클래스 격리 옵션으로 10개 클래스, 167건 중 156 통과·11 건너뜀·0 실패. 기존 교환 회귀 2개를 포함한 BCoachMultiobjectiveProductionTest 101개와 canonical resolver 및 optimizer 테스트가 통과했다. BBalanced12WeekEngineTest 11건은 비공개 이력 fixture 요구 조건으로 건너뛰었다.
- **추가 감사 fixture** · 기준 SHA의 Gradle 산출물에 대한 Kotlin/JUnit 교환 2/2와 Robolectric 코어 검색 및 기존 검색 8/8. 연결 원본이 있는 교환 비교에서 원안 0.2636995291, 교환 0.2836995291, 두 회복 점수 0.1437507575였다. test source를 임시로 실행하는 경우 감사 Kotlin 파일을 해당 package 경로에 두고 테스트 후 제거한다.
- **로컬 전체 Gradle / APK** · 전체 JVM은 robolectric-nativeruntime.dll의 EXCEPTION_ACCESS_VIOLATION으로 중단됐다. assembleDebug 및 APK v2 서명 확인은 통과했다. Debug APK 72,687,490바이트, SHA-256 1A3FDF51C54844A743BF983F80948B9A5DA2678189E1E31CBD36ACEAB04D8394. 이는 work/WhatYouGottaDo/app/build/outputs/apk/debug/app-debug.apk의 로컬 산출물이다.
- **GitHub** · 기준 SHA와 main의 CI는 완료·성공으로 확인했다. 기준 SHA의 debug unit test, Debug APK, 최적화 perf APK 단계가 성공했다. 감사 브랜치에서의 검증은 GitHub Actions의 해당 브랜치 기록에서 확인할 수 있다. [기준 CI](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions/runs/45559810371) · [감사 브랜치 CI](https://github.com/Mascollel-Ko/WhatYouGottaDo/actions?query=branch%3Aaudit%2Fb-coach-exercise-metadata-20261125)
- **변경 검증** · 감사 문서와 표, 생성·재현 도구만 docs/audits에 커밋했다. 시작 SHA 대비 오퍼레이션 변경 수 0이다.
