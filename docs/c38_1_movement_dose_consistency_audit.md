# C38.1 movement/region dose consistency audit

> **C38.2 정정:** 아래의 C38.1 `persona0_sparse` rear-delt fly → `UPPER_PULL` 선택 및 8세트 materialization 주장은 잘못된 legacy slot 매핑을 물려받은 결과라 철회한다. C38.2의 canonical movement 교차검증에서는 리어델트 플라이를 직접 pull movement owner로 인정하지 않는다. 이전 11개 pull 제거 관계와 EXP 집계도 올바른 운동 분류로 다시 평가해야 하며, 아래의 당시 수치는 C38.1 실행 기록일 뿐 유효한 근거가 아니다. 최신 정정 결과는 `docs/c38_2_movement_dose_consistency_audit.md`에 기록한다.

## 범위와 기준

C38.1은 movement coverage, regional dose evidence, 최종 세트 projection에서 서로 달랐던 movement 관계와 주간 집계 의미를 통일한다. 시작 코드 기준은 `c7e9c3eff8553cf80109261bd578142fee18d19a` (C38.0, Protocol 3.75.0 / Runtime 0.15.17)이며, 이 변경은 새로운 세트 수·운동 자격·중량·노력 정책을 추가하지 않는다. App 0.5.1.5, Room 38 및 저장·백업 계약은 유지한다.

## 수정한 프로덕션 경로

- `MovementCoverage.directlyRepresents`, `regionalEvidenceTargets`, `canonicalRepresentationDomain`으로 기존 coverage 관계를 한곳에 명시했다. `HORIZONTAL_PULL`/`VERTICAL_PULL`/`UPPER_PULL`은 통합 pull evidence에서 함께 관찰되지만, 수평·수직 pull은 서로를 직접 충족하지 않는다. 수평/수직 push, 무릎 중심/후면사슬, `CORE_DIRECT`는 서로 합치지 않는다.
- B1 표현 분석, regional history index, B4 regional dose, B5 candidate eligibility와 final projection/materialization audit에 같은 관계·credit resolver를 연결했다. direct movement credit은 해당 movement 관계와 canonical qualifier가 모두 맞을 때만 인정한다. 의미 있는 canonical supportive hypertrophy relation은 기존 0.5 regional credit을 유지하며 movement coverage 자체를 대신하지 않는다. 한 실제 세트는 target region/quality 장부마다 한 번만 credit된다.
- UPPER_PULL을 기존 regional Hypertrophy evidence의 통합 항목으로 B4/B5/B6에 연결했다. 기존 regional target이 수평 또는 수직 pull dose를 소유하면 UPPER_PULL은 그 수치를 다시 발행하지 않고 direction-only로 공유한다. 둘 중 별도 regional numeric target이 활성화되지 않은 경우에만 기존 regional B4 개인 기준선/cold-start 권한을 사용할 수 있다. 독립적인 UPPER_PULL quota는 추가하지 않았다.
- 최근 및 이전 28일 주간 총량 중앙값은 적격·관측·완료·비제외 주에서 해당 region의 0세트도 포함한다. 운동한 주만의 중앙값은 실제 positive exposure 주로 계산한다. 제외·미관측·미완료 주는 0으로 만들지 않으며, 비어 있는 기간은 null/미관측으로 남긴다.
- B5의 선택과 `candidateItems`는 `(stableKey, selectionRole)` identity를 사용한다. 동일 physical stableKey가 다른 역할에 덮이지 않으면서 실제 선택·materialization은 같은 운동 세트를 중복 발행하지 않는다.

## A–E 결정적 검증

- **A — 통합 pull:** B1 표현 분석과 regional B2 근거는 수평·수직 방향 노출을 UPPER_PULL 통합 항목에서 주별로 합산한다. 같은 주 수평 3세트와 수직 4세트는 UPPER_PULL 7세트다. 3세트와 4세트가 다른 주라면 각 주 값으로 통계를 내며 중앙값을 더하지 않는다. 기존 방향별 B4 target이 수치 권한을 갖고 있으면 aggregate가 같은 quota를 다시 만들지 않는다.
- **B — 0주:** 28일 전체 주간 용량 중앙값에는 관측되고 완료된 적격 주의 실제 0을 포함한다. 운동한 주 중앙값은 양의 세트가 있었던 주만 계산한다. 제외·미관측 구간을 0으로 꾸미지 않으며, 빈 구간은 `null`로 남긴다.
- **C — movement/quality 분리:** 승인된 품질 세트의 최종 투영은 부위 qualifier와 운동의 canonical movement 관계를 모두 확인한다. 수직 프레스의 어깨 자극은 기존 지역 근비대 장부에 기여할 수 있지만 HORIZONTAL_PUSH movement를 충족하지 않는다. LOWER_KNEE도 POSTERIOR_CHAIN movement를 충족하지 않는다. 기존 canonical supportive 근비대 0.5 credit은 유지한다.
- **D — 동일 관계의 전 구간 적용:** B1, B4 근거·residual, B5 후보, 예정/최종 projection, B7 materialization audit에 공통 movement 관계와 regional credit resolver를 사용한다. 수평/수직 밀기와 당기기, LOWER_KNEE/POSTERIOR_CHAIN, CORE_DIRECT의 허용·차단 경계를 테스트로 고정했다.
- **E — B5 owner 식별:** B5 선택 및 후보 표는 `(stableKey, selectionRole)`을 식별자로 사용하고, B6 권한은 target/owner/week의 정확한 연결을 유지한다. 이미 선택한 실제 운동을 다른 role로 다시 발행해 두 번째 물리 행을 만들지 않는다. 테스트는 정확한 role 일치만 권한 재사용으로 인정하고, stableKey만 같은 다른 role의 B6 근거를 전용하지 않음을 검증한다.

## 회귀 증거

Focused tests는 같은 주 수평 pull 3세트와 수직 pull 4세트를 UPPER_PULL 7세트로 합치며 주별 통계를 먼저 계산한다. 서로 다른 주의 3/4세트는 주간 값 3과 4에서 통계가 나오고 중앙값을 더하지 않는다. 최근 주 `[8,0,0,0]`의 전체 주간 중앙값은 0이고 노출 주 중앙값은 8이며, `[8,0,8,0]`도 0주를 포함한다. 제외·미관측 기간은 분모에 들어가지 않는다.

음성 회귀는 수직 밀기→수평 밀기, 수평 pull→독립 수직 pull, 무릎 중심→후면사슬 movement 크레딧 전이를 막는다. 동일 역할/다른 역할의 후보 충돌은 덮어쓰지 않고, 이미 선택한 physical owner를 다른 demand의 두 번째 material row로 복제하지 않는다.

## 22-case 결과

C38.0 기준은 B7 eligible 4 / ineligible 18, B8 authorized 1 / control 21, B9 EXP 1 / CONTROL 21이었다. C38.1 production corpus에서는 B7 eligible 7 / ineligible 15, B8 authorized 2 / control 20, B9 `B8_POWER_JUMP_V1` 2 / CONTROL 20이다. 추가 통과는 `persona3_sparse`이며, 이전에는 B7 `CHANGE_PROVENANCE_UNCLOSED` 때문에 CONTROL이었고 현재는 UPPER_PULL의 exact B4 regional residual, B5 pull owner, B6 user-calibrated hypertrophy shape, 주차별 materialization을 확인한 뒤 기존 Power B8 범위로 라우팅된다. Power/JUMP_LANDING 정책이나 B8/B9 범위는 넓히지 않았다.

기존 32 unique `(case, stableKey, selectionRole)` 제거 관계 중 11개의 UPPER_PULL 관계는 exact B4/B5/B6/materialization 증거가 생겨 B7 canonical replacement로 닫혔다(22 owner-week occurrence). 나머지 원래 21쌍은 계속 unresolved이며, 새 타깃 선택 결과로 추가된 4쌍도 B7의 `UNEXPLAINED_REMOVED_IDENTITY`로 남겼다. 따라서 현재는 25 unique pairs / 50 owner-week occurrence다. 분포는 exact B5 대안 없음 9, B5는 있으나 exact B6 없음 7, B4/B6 흔적은 있으나 정확한 주간 배정·materialization이 없는 9다. 새 4쌍은 `persona0_reviewed`와 `persona3_reviewed`의 CORE_DIRECT, `persona4_recent`의 ARMS_BICEPS, `persona4_reviewed`의 CALVES owner이며, census에는 선택 trace만으로 B6 또는 allocator 증거가 있다고 간주하지 않는다.

지역 target census는 29 movement target / 58 owner-week row, 후보 245, 선택 27, 정상 탈락 218이다. B4 authorized 412 set units 중 실제 materialization은 352이며, 4 target은 finite capacity 제한으로 남는다. overfill 0, duplicate credit 0, unauthorized material 0, 제3 build 0이다. broader regional demand census에서는 한 주 raw residual 808, 사전 priority diagnostic funding 688, 최종 frequency funding 616, 최종 horizon materialization 1,232(616 × 2주)다. 688은 최종 funding이 아니며, diagnostic에서 fund됐지만 실제 capacity에서 거절된 9 rows / 72 units를 별도로 유지한다. 최종 승인량과 배치 후 생성량의 차이는 0이다.

`persona0_sparse`는 `cable_rear_delt_fly`를 `CANONICAL_STIMULUS_MOVEMENT_UPPER_PULL`로 선택하여 B4 residual 8, B6 8×8회·RPE 최소 7·중량 보정 필요 상태를 승인하고 주 8세트를 생성한다. `persona2_sparse`에서는 같은 정확한 B4/B5/B6 수치가 있어도 finite allocator가 두 주 모두 0세트를 funding해 8세트 shortfall을 보존한다. 0 kg는 실제 중량으로 취급하지 않는다.

## 남은 차단과 안전성

기존 18개 B6 부재 관계 중 UPPER_PULL 11개 외 7개는 동일 운동 근거 또는 권한이 없다: 스쿼트 2건의 canonical posterior reference, 벤치 1건의 exact planned resistance load, hypertrophy 2건의 regional numeric authority, safe load/effort 2건의 exact 근거가 확인되지 않았다. 기존 5개 0-funded 사례는 배정되지 않은 새 대체 운동이 원래 CONTROL owner 제거를 정당화하지 않으며, 해당 부족량은 removal provenance로 빌려 쓰지 않는다. 추가 4개 pair도 exact B6/allocation chain이 없어 계속 fail-closed다.

`UPPER_PULL`만을 위한 주간 수치 목표, cross-quality 환산계수, B6 중량 또는 RPE, Strength/Power/Task authority를 만들지 않았다. C36의 exact B7/B8 검증, CONTROL object, C37.4/C37.5의 저장 시 재검증·선택 흐름은 유지한다. 세부 machine-readable census는 테스트 실행 시 `app/build/reports/c36-execution-provenance-census.json`, `c33-regional-b4-residual-production-census.json`, `c26-current-next-phase-corpus.json`에 재생성된다. 이 build 산출물을 저장소에 복제하지 않는다.

## 검증 및 버전

- 시작 SHA `c7e9c3eff8553cf80109261bd578142fee18d19a`; implementation SHA `b36f4ad2bb40c9ff4e2d57bfe8f3793a6434bbd5`.
- Protocol `3.76.0`, Runtime `RECORD_BASED_PLANNER_0.15.18_KOTLIN_1`; App `0.5.1.5`, Room `38`.
- Focused regional 테스트 73/73 통과(RegionalAuthority 22, RegionalBottleneckDiagnosis 12, target selector 39); production coverage 6/6, service integration 2/2, post-generation authority freeze 1/1 통과. 전체 JVM suite는 364 suites, 2,441 tests, 실패 0, 오류 0, 기존 skip 4였다.
- `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `assembleDebug`, protocol validator (9 families / 36 protocols), `git diff --check`가 통과했다. 로컬 debug APK는 72,729,550 bytes, SHA-256 `3C8070534E3BE3252C3DDDF89DDFBA71DF9A4EB972230B03B60684054665E720`이다.
- 최초 JBR 21 테스트 실행에서 알려진 Robolectric native SQLite access violation이 관찰됐다. 외부 JDK 17/fork worker 방식으로 전체 suite를 다시 실행해 native crash 및 assertion 실패 없이 통과했다. `gradle/gradle-daemon-jvm.properties`는 원래대로 복구했고 crash log는 미추적 상태라 commit에 포함하지 않았다.
- PR #24는 OPEN/Draft이며 main에는 병합하지 않았다. Hosted CI와 artifact 결과는 코드 및 문서 후속 commit의 최종 CI 확인 뒤 별도로 기록한다.
