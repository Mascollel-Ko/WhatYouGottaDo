# P1 제품 설계 — 스포츠 운동시간 선택 가져오기 + 컨디션 수면시간 자동 표시

- 상태: **DESIGN / PROPOSED / NOT IMPLEMENTED**
- 기준일: 2026-11-01 (Asia/Seoul)
- 상위 설계: [Galaxy Watch 7 / Health Connect 기반 운동시간 제안·기록 설계](health_connect_watch_time_capture_design_2026-10-26.md)
- P0: [Health Connect P0 진단 구현 기록](watch_health_p0_diagnostics_implementation_2026-10-27.md)
- 현재 사용자 요구 추가: **기록 탭에 들어갔을 때 선택된 날짜의 워치 수면시간이 있으면 컨디션 수면시간에 즉시 표시**.
- 이 문서는 기존 P0 읽기 전용 진단을 P1 사용자 기능으로 이동하기 위한 *변경 설계*. 기존 코드가 이미 구현되었다는 뜻이 아니다.

## 1. 사용자 경험 — 기록 탭 진입

1. 사용자가 Record 화면에서 오늘 또는 과거 날짜 D를 연다.
2. 화면 본체를 즉시 렌더링하고, Health Connect에서 *해당 날짜 아침에 끝난 야간 수면*을 비차단으로 읽는다. 단, 처음 연결할 때는 사용자의 READ_SLEEP 동의를 요구하고, 거절해도 기존 앱은 정상 작동한다.
3. 그 날짜의 기존 DailyCheckIn.sleepHours가 있다면 해당 값을 최우선 표시한다. 워치 결과가 다르면 자동 덮어쓰기 대신 "워치 수면기록 확인"을 제공한다.
4. 기존 값이 없고, 사용자가 **수면 자동 채우기 기능을 활성화했고** 기록이 정상이며 중복·품질 문제가 없다면 해당 날짜의 수면시간을 기존 일일 컨디션의 sleepHours에 자동 입력한다. 다른 컨디션 필드는 보존한다.
5. 수면 자동 채우기 비활성 또는 데이터가 모호한 경우에는 저장하지 않고 "워치 수면 7시간 24분 · 적용" 같은 미리보기만 표시한다.
6. 사용자가 컨디션 편집창을 열 때는 저장/미리보기/권한 상태를 일관되게 표시한다. 즉시 나타나는 값은 실제 데이터만; 예시나 추정치를 실제 수면으로 표시하지 않는다.
7. 기록 조회/수면 불러오기는 운동 추가와 독립이다. 수면 권한이 없어도 배드민턴 운동 읽기와 기존 수동 기능을 사용할 수 있다.

## 2. 수면의 날짜 귀속

- **기본 귀속일은 잠에서 깬 날(수면 세션의 로컬 종료일)**. 예를 들어 10/30 23:30~10/31 07:00 수면은 10/31 Record 컨디션에 귀속.
- Android Health Connect SleepSessionRecord에는 startTime, endTime 및 선택적 stages가 포함. 삼성 헬스 동기화가 지연될 수 있다.
- D 조회 시 D-1부터 D+1 일부까지 조회하되, 읽은 모든 레코드를 D로 저장하지 말고 *종료일 D 및 야간 주요 세션 정책*으로 선택.
- 여러 야간 수면 기록, 수면 중 각성 후 별도 세션, 타 기기 소스의 동일한 수면, 해외 여행/시간대 변경은 중복 가능성이 있으므로 무작정 합산하지 않음.
- 낮잠은 기본 야간 수면시간에 자동 합산하지 않는다. 필요 시 "다른 수면 기록"에서 사용자 선택 허용.
- 당일 진행 중인 미완료 수면·현재 이후 시각·종료시각이 시작보다 빠른 데이터는 자동 기록 대상 아님.
- 사용자가 날짜 D를 바꿨는데 이전 비동기 조회가 늦게 돌아오면 절대로 새 날짜에 쓰지 않음. 날짜, 조회 generation, 최신 저장 값 검증.

## 3. 수면시간 계산 기준

- **실제 수면시간과 침대에 있었던 총 시간은 다르다.**
- 선택적 단계 정보가 신뢰 가능하게 제공된다면 `SLEEPING`, `LIGHT`, `DEEP`, `REM` 등의 실제 수면 단계 시간을 합산하고 `AWAKE`, `AWAKE_IN_BED`, `OUT_OF_BED`은 제외한다. 코드 상수/상세 타입은 구현 시 공식 API 재확인.
- stage 중복 구간은 union 처리해 이중 합산 금지. 알 수 없는 stage/누락 구간을 자동으로 수면으로 가정하지 않는다.
- stage 기록 범위/품질이 현저히 부족하면 "수면 시작~종료 구간(실제 수면시간 미확인)"으로만 보이고 **자동 sleepHours 저장하지 않는다**. 사용자가 직접 확인·선택하면 수동 시간으로 저장 가능.
- 데이터 공급자가 기록하는 수면 단계별 시간과 Samsung Health 표시 수면시간 사이의 차이가 발생할 수 있다. 단말 실측과 일치 비교 필요.
- 유효 시간은 0 < duration <= 24시간. 기존 DailyCheckIn.sleepHours(Double, 0..24)로 입력할 때 1분 단위 환산·표시는 "n시간 n분"; persisted double 규칙(분/60, 표기 반올림)은 별도 테스트로 고정.
- 의학적 수면 진단/수면 질 점수 계산은 P1 범위 밖.

## 4. 권한, 읽기, 동기화

- Health Connect `SleepSessionRecord` / `android.permission.health.READ_SLEEP`을 사용.
- P0는 debug-only 운동·심박수 읽기다. P1 개발 시 Health Connect read gateway를 안전하게 제품 코드로 옮기고, 출시용 manifest·rationale/privacy/Play health permissions 정책을 준수.
- 최초 접속 때 시스템 권한을 강제로 띄우지 않고 "워치 수면시간 연동"을 선택할 때 분명히 설명하고 요청. 동의 후 설정에서 "수면 자동 채우기" 선택 가능.
- 수면 권한만 허용/운동 권한만 허용/권한 전부 거절/철회 각각 독립 동작.
- 조회는 Record 탭 날짜 진입 및 사용자 수동 새로고침 시에만, Compose recomposition마다 반복 호출하지 않음.
- Health Connect에 기록 없음과 권한 없음, 동기화 미완료/조회 실패, 단계 정보 부족을 다르게 표시. 삼성 헬스 기록이 있어도 Health Connect 전송이 늦을 수 있음.
- 원시 수면 단계·심박수 시계열은 P1 로컬 캐시/백업/클라우드에 기본 영구 저장하지 않음. 승인된 sleepHours만 기존 앱의 저장 정책에 따라 백업.

## 5. 동시성·수동 입력 보호: 반드시 원자적 부분 갱신

현재 앱 소스 확인 결과:
- `HomeDailyCheckInCard.kt`의 `DailyConditionEditorDialog`에서 `sleepHours` 직접 입력 가능.
- `RecordScreen.kt`의 `RecordDailyConditionCard`가 선택 날짜 컨디션과 편집창을 보여 줌.
- `DailyStatusService.upsertInTransaction`은 `DailyCheckIn`을 저장하면서 `DailyMetric.sleepHours`까지 동기화.
- 따라서 워치 응답으로 오래된 `DailyCheckIn` 전체 행을 덮어쓸 경우 몸무게/피로도/관절 불편/집중 상태 값이 사라질 위험이 있음.

구현 정책:
1. 수면 값이 비어 있는 경우에만 적용 가능한 별도 `fillSleepHoursIfEmpty(date, validatedHours, expected...)` 형태의 *DB 트랜잭션 내부 재조회 + 특정 필드 갱신* 도입.
2. 최종 저장 시점에 최신 check-in을 재조회하고 `sleepHours != null`이면 적용 중지. 기존 각 조건, note, updatedAt/createdAt, 기존 daily metrics 동기화를 정확히 보존.
3. 사용자가 편집창에서 수면시간을 직접 수정하면 자동값보다 우선. 워치 값이 업데이트돼도 사용자 변경을 자동 덮어쓰지 않음.
4. 사용자가 워치 값을 거절하거나 삭제했다면 같은 데이터로 반복 자동 채우지 않도록 날짜/출처별 거절·수동 수정 상태 유지. 기존 값이 빈 경우라도 명시적 사용 중지 의사를 존중.
5. 기록된 수면값의 출처는 자동 채움/직접 입력/사용자 수정으로 구별하되, 출처 메타데이터는 개인정보·백업·복원 정책을 점검한 뒤 최소화. 자동채움 기능 OFF면 기존 저장 값은 지우지 않음.
6. 운동 기록·운동시간에 대한 confirmed, RPE, ProgramBuilder, OFI 계산식은 변경하지 않음. `sleepHours` 값의 기존 회복 분석 반영만 정상적으로 발생.

## 6. 운동시간 제안과 P1 결합

### 스포츠
- 배드민턴, 배드민턴 레슨, 기타 의도적인 스포츠/유산소를 선택할 때 사용자에게 적절한 워치 활동 기록을 선택하도록 한다.
- 전송된 운동 타입이 정확히 일치할 경우 우선 제안. 날짜에 걷기(type 79)만 있으면 배드민턴으로 자동 분류하지 않음; "워치에서 가져오기" 수동 탐색은 허용.
- **일상적 걷기/저강도 보행은 홈/기록에서 독립적인 운동 자동 제안·자동 저장을 하지 않는다.**
- 이미 계획·수행 기록이 있으면 새 기록을 중복 생성하지 않고 후보를 사용자 승인 후 기존 기록에 연결. 여러 활동 세션을 합산할 때는 총 구간의 합 vs 첫 시작~끝 경과시간을 모두 보여주고 사용자가 최종 시간을 결정.
- 운동 선택 팝업은 관련 source가 있을 때만, 사용자 취소 후 반복 호출/모달 스팸 금지.
- 시간 적용은 사용자 승인 필수, 기존 confirmed/RPE/세션 링크·프로그램 처방 보존.

### 진단용 아이콘 제거
- P0의 별도 Launcher 활동 `WatchDiagnosticsActivity`와 진단 UI 및 별도 아이콘은 P1 실제 기록 화면 도입 후 제거.
- 읽기/권한 처리의 제품용 코드와 테스트는 유지·이관. 디버그 진단 화면을 먼저 삭제해 실제 데이터 읽기를 망가뜨리지 않는다.
- 실제 P1 release 구현·테스트 후 배포용 Health Connect 사용 동의와 Play Console 선언 검토.

## 7. 검증 시나리오

1. 선택 날짜 D에 전날 밤 시작하고 D 아침 끝난 수면 세션 → D에만 표시.
2. D-1에 끝난 수면은 D에 들어가지 않음. D+1 새벽에 끝난 수면도 D에 들어가지 않음.
3. 정상 단계 데이터 7h24m → 기존 수면 입력이 비어 있고 자동 채우기 ON일 때만 저장.
4. 사용자 수면 입력 6h45m 존재 → 워치 7h24m 조회 후도 기존 값 유지, 선택적 교체 버튼.
5. 사용자가 수면시간을 직접 지우고 저장 → 다음 날짜 왕복/앱 재시작 시 같은 워치값 자동 재입력 금지.
6. 자동 채우기 권한만 있고 운동 권한 없음, 반대 상태, 모두 없음 → 각각 정상 fallback.
7. 수면 기록 없음, 미완료, stage 누락, 복수 겹치는 레코드, 낮잠, 자정 교차, 다중 origin, 시간대 전환, partial sync.
8. Record D 조회 도중 D+1로 날짜 변경/편집창에서 다른 값 저장 → 오래된 결과가 덮어쓰지 않음.
9. sleepHours 저장 후 DailyCheckIn과 DailyMetric 값 일치; bodyWeightKg/overallFatigue/lowerBodyFatigue/jointTendonDiscomfort/focusMotivation/note 등 보존.
10. 백업 CSV/클라우드에 자동 채운 sleepHours가 기존 값과 동일하게 round-trip; 원시 수면 단계 기록은 백업 제외.
11. 수면 자동 채우기 OFF, 권한 철회, 워치 미착용, 삼성 헬스 동기화 지연에도 기존 수동 기록 유지.
12. 앱 320dp/큰 글꼴/영문/다크 모드에서 긴 문자열과 버튼 세로 분절 없음.
13. 운동 기능 + 수면 기능이 동시에 켜진 상태에서 별도 권한·조회·UI 성능 회귀 없음.

## 8. 구현 순서와 출시 게이트

1. P1-A: 공식 SDK `SleepSessionRecord` 읽기 + READ_SLEEP 온보딩/권한 및 디버그 단말 실측으로 Samsung Health↔Health Connect 수면 시간 비교.
2. P1-B: Record 선택 날짜에 수면시간 즉시 미리보기와 비차단 조회, wake-date 귀속 및 수면 단계 계산/품질 게이트.
3. P1-C: 사용자 설정의 자동 채우기 ON 상태 + empty-only 원자적 부분 갱신 + 기존 DailyMetric 반영 및 개인정보/백업 정책.
4. P1-D: 스포츠 기록 워치 후보 선택 UI, 수동 확인/중복방지/확인상태 보존.
5. P1-E: 새 사용자 흐름으로 이관 성공 후 P0 진단 launcher 및 화면 제거, release UI·manifest·Play 선언 정리.
6. CI: JVM 단위/Room migration·integration/기존 OFI와 badminton 회귀, assembleDebug와 release Kotlin compile, signed debug APK, action 성공.
7. **실제 갤럭시 워치7의 수면 세션 read가 확인되기 전에는 “삼성 헬스 수면 자동 입력 완료”라고 주장하지 않는다.**

## 공식 출처

- Samsung Health ↔ Health Connect 데이터 유형 (sleep session/stages): https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
- Samsung 수면 데이터 동기화 지연과 전날 시작하는 수면 세션 조회: https://developer.samsung.com/health/blog/en/managing-sleep-data-with-samsung-health-and-health-connect
- Android Health Connect sleep sessions/permission: https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions
