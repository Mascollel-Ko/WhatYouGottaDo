# Galaxy Watch 7 / Health Connect 기반 운동시간 제안·기록 설계

- 문서 상태: **DESIGN / PROPOSED / NOT IMPLEMENTED**
- 작성일: 2026-10-26 (Asia/Seoul)
- 대상 저장소: Mascollel-Ko/WhatYouGottaDo
- 조사 기준: main @ 9c35ebcca02dfa1479b208ba532aad877bba7f2a, 앱 0.5.1.5, Room schema 39
- 주요 대상: 갤럭시 워치7 → 삼성 헬스 → Health Connect → WhatYouGottaDo
- 범위: **설계 문서만**. 기능 구현, DB 변경, 권한 요청, 빌드 결과를 주장하지 않는다.
- 본 문서는 기존 ACTIVE protocol을 대체하지 않는다. 실제 구현은 별도 변경·회귀 검증을 거쳐야 한다.

## 0. 한 문장 제품 정의

사용자가 특정 날짜의 배드민턴 계획 또는 기록을 열거나 배드민턴 종목을 추가하면, 앱이 **이미 저장된 워치 운동 세션/심박수**를 찾아 **시간 후보**를 제안한다. 사용자는 후보를 확인·수정·거절하거나 **워치에서 가져오기**를 직접 눌러 선택할 수 있다. **명시적으로 승인한 시간만 기존 WorkoutSet.seconds에 기록**하며 완료 여부·RPE·기존 계산식은 임의 변경하지 않는다.

우선 사용자 가치: “운동시간을 직접 기억해서 타이핑하지 않아도 된다.” 자동으로 배드민턴을 100% 식별하거나 정확한 활동 초수를 판정하는 것이 목표가 아니다.

## 1. 조사로 확인한 현재 코드 구조와 절대 보존사항

1. 운동 기록 UI: RecordScreen.kt → RecordEntryCard.kt → RecordSetRow.kt. 스포츠 시간은 시간/분 입력을 seconds로 바꿔 저장한다(SportDurationInput.kt).
2. 실제 분석 입력: WorkoutSet.confirmed == true인 기록. 따라서 **시간만 적용하고 자동으로 confirmed=true로 만들지 않는다**. 기존 확인 버튼은 계속 사용한다.
3. 저장: WorkoutEntry(id, date, exerciseStableKey, sessionStableKey, backupSourceId 등), WorkoutSet(id, entryId, setIndex, seconds, confirmed, rpe 등), RecordMutationService.updateSet → Room transaction/분석 갱신. 현재 WorkoutEntry에는 실측 시작·종료 시각 필드가 없다. createdAt, performedAt, firstConfirmedAt, completedAt은 운동 시작·종료 시각 대용이 아니다.
4. WorkoutEntry.sessionStableKey는 같은 날짜의 여러 엔트리가 공유할 수 있으므로 **워치 연결의 유일키로 사용 금지**. backupSourceId는 로컬 ID를 넘는 이식성에 도움이 되지만 세트별 불변 식별자가 따로 없으므로 연결 테이블/백업 매핑 설계 필요.
5. 날짜 복사·이동·밀기: CalendarRecordService에는 새 로컬 entry ID 생성, 계획/완료 세트 분리, source identity 재사용/교체, progression 링크 재배치가 존재한다. 이 이벤트를 구분해 처리하지 않으면 워치 시간이 계획 복사본에 부당하게 복제될 수 있다.
6. 배드민턴 연습 입장: BadmintonPracticeCatalog의 ex_ae9ecdbc(배드민턴)와 ex_badminton_lesson(배드민턴 레슨), resolved kind SPORT_SESSION만 현재 BADMINTON_VOLUME 계산 대상으로 취급한다. 임의 명칭 또는 전체 SPORT_SESSION으로 확장하지 않는다.
7. 배드민턴 practice raw = confirmedMinutes × RPE modifier. confirmed 세트의 seconds와 RPE 우선순위·주간 집계는 BADMINTON_VOLUME_CALCULATION.md의 기존 계약대로 유지한다. 워치 칼로리·평균 심박수는 이 값에 더하지 않는다.
8. OFI 다섯 축, 연결조직, 회복 지표, ProgramBuilder, 운동 순서, 세트 confirmed·처방·세션 연결 로직의 계산은 모두 동결.
9. 현재 앱 minSdk 26, targetSdk 35, Room 39, CSV backup format 14 / restore schema 13. 새 라이브러리·Room schema·CSV format은 구현할 때만 바꾼다.
10. QUIET_UI_PRESENTATION 원칙 준수: 중립적인 카드·행·적은 강조, 한 화면 안 과도한 중첩 카드 금지, 좁은 화면/큰 글꼴에서 문자 세로 쪼개짐과 불필요한 버튼 줄바꿈 금지, 한국어/영어 현행 로컬라이제이션과 접근성 유지.

참고 코드:
- app/src/main/java/com/training/trackplanner/{RecordScreen.kt,RecordEntryCard.kt,RecordSetRow.kt,SportDurationInput.kt}
- app/src/main/java/com/training/trackplanner/data/{Entities.kt,RecordMutationService.kt,CalendarRecordService.kt,TrainingDatabase.kt,RecordCsvBackupRestore.kt}
- docs/protocols/{badminton/BADMINTON_VOLUME_CALCULATION.md,ofi/OFI_CORE.md,presentation/QUIET_UI_PRESENTATION.md,data_portability/BACKUP_AND_RESTORE.md,data_portability/CLOUD_BACKUP.md}

## 2. 제품 범위와 비범위

### 범위에 포함

- 연결 상태·데이터 출처·권한 상태 표시.
- 기록 화면의 “워치에서 가져오기”, 명시적 사용자 동의 후 제한적 자동 제안.
- 기존/새로 추가하는 배드민턴 운동 항목에 같은 날짜의 워치 시간 후보 매칭.
- 실제 운동 세션(강한 출처)과 심박수 기반 활동 추정(약한 출처) 구별.
- 한 날짜에 아침·저녁 두 번 운동, 운동 중 쉬는 시간, 운동 중단·재시작, 여러 후보, 타 종목과 겹침, 날짜 변경, 여러 기기 출처 처리.
- 세트별 시간 적용 전 미리보기, 사용자 수정, 승인, 해제, 거절/다시 찾기, 재동기화.
- 기존 기록/확인/RPE/계획/백업/분석 회귀와 소스 연결의 데이터 무결성.
- 프라이버시와 네트워크 없는 환경, 권한 회수, 데이터 소실/수정 대응.
- 검증용 데이터 품질 지표와 단계별 배포 게이트.

### 이번 1차 릴리스에서 하지 않을 것

- 워치용 별도 Wear OS 앱, 상시 실시간 센서 스트리밍, GPS 위치 수집.
- 자동 운동 완료 처리, 자동 RPE 작성, 자동 OFI/관절부하 증감, 시간표 기반 운동 자동 생성.
- 배드민턴 특유의 풋워크/스매시/점프/게임 플레이 여부를 심박수만으로 판별.
- 근력운동의 운동 종목·세트·반복·중량 자동 추정.
- 삼성 자체 에너지 점수/활동 요약 직접 SDK 접근, 삼성 파트너 등록.
- Health Connect에 쓰기(읽기 전용으로 시작).
- 칼로리/수면/걸음수 권한을 1차에서 일괄 요청; 향후 별도 설계로 추가.
- 심박수 임계치 숫자를 의료·생리학적 근거가 있는 값처럼 홍보하는 것.

## 3. 플랫폼 사실과 구현 전 확인 사항

- 삼성 공식 문서: 워치 건강 데이터는 휴대폰 Samsung Health를 거쳐 Health Connect로 동기화할 수 있다. ExerciseSessionRecord, HeartRateRecord 등 지원. 버전·개별 설정에 따라 데이터 공유 범위가 다르며 삼성 헬스의 daily activity tracker 요약은 그대로 동기화되지 않는다.
- Health Connect에는 EXERCISE_TYPE_BADMINTON 상수가 존재한다. 그러나 **해당 워치/삼성 헬스가 실제로 어떤 exerciseType으로 내보내는지는 기기 테스트 전 미확정**.
- Samsung Health↔Health Connect 전송 지연은 고정 시간이 아니다. 휴대폰 앱이 열려 있어도 워치에서 아직 동기화되지 않았을 수 있다.
- 앱은 minSdk 26이므로 일부 지원 OS에서는 Health Connect 자체가 이용 불가능하다. Android 9(API 28)+, Google Play 서비스 등 가용성 확인이 필요하다. 지원 불가 기기는 기존 수동 입력만 노출한다.
- Health Connect의 다른 앱 데이터 과거 조회는 기본적으로 최초 권한 승인 전 최대 30일. 그 이전은 별도 역사 읽기 권한이 필요한 경우가 있다. 1차에서 장기 과거 권한을 요청하지 않는다.
- 1차는 **사용자가 앱을 보고 있을 때만 조회**. 향후 백그라운드 자동동기화는 플랫폼 feature availability 및 별도 권한 검증 후 추진.
- 읽기 동작에서 Health Connect 데이터의 실제 source와 metadata를 확인한다. “워치에서 직접 측정” 또는 “삼성 헬스에서 입력된 것”을 확인 불가능한 경우 단정하지 않는다.
- Health Connect 데이터에 대한 배포 시 Google Play Health Apps declaration, 데이터 안전성/개인정보 처리방침, 최소 권한 설명이 필요하다.

공식 참조:
1. https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
2. https://developer.samsung.com/health/health-connect-faq.html
3. https://developer.android.com/health-and-fitness/health-connect/experiences/workouts
4. https://developer.android.com/reference/androidx/health/connect/client/records/ExerciseSessionRecord
5. https://developer.android.com/health-and-fitness/health-connect/availability
6. https://developer.android.com/health-and-fitness/health-connect/read-data
7. https://developer.android.com/health-and-fitness/health-connect/publish
8. https://developer.samsung.com/health/data/overview.html (2차 후보일 뿐)

## 4. 사용자 설정·권한 동선

### 최초 연결

설정 > 운동 데이터 연동 > “워치 기록 연결(Health Connect)”을 사용자가 누른 뒤:
1. 무엇을 읽는가(운동 종류/시간, 선택 시 심박수), 무엇을 하는가(시간 후보 제안), 무엇을 하지 않는가(자동 완료·의학 진단·무단 업로드)를 한 화면에 설명.
2. Health Connect 사용 가능 여부 확인. 미지원/미설치/서비스 중단/업데이트 필요/권한 거절을 각각 안내.
3. **READ_EXERCISE 먼저** 요청. 실제 운동 세션 시간만 원하면 심박수 권한 없이 쓸 수 있어야 한다.
4. 심박수 기반 추정 사용을 선택한 경우에만 **READ_HEART_RATE** 추가 요청. 거절하면 명시적 운동 세션 기반 기능은 지속.
5. “기록 화면에서 운동시간 후보 제안” 토글은 연결 성공 후에만 제공. 선택형/기본 권장 ON, 알림은 전송하지 않음. 처음에는 언제든 OFF 가능.
6. 연결 상태: 미연결 / 연결됨(운동만) / 연결됨(운동+심박수) / 권한 변경됨 / 지원 불가 / 동기화 데이터 없음 / 읽기 오류. **미연결과 기록 없음은 다른 상태**.
7. “최근 워치 기록 확인”, “사용 중지”, “저장한 워치 출처 정보 지우기”, “데이터 사용 안내” 제공.

### 권한 최소화

- 앱 설치/시작 시 권한 팝업을 자동으로 띄우지 않는다.
- READ_EXERCISE를 거절해도 수동 시간 입력 유지. READ_HEART_RATE만 거절하면 HR 추정 기능만 비활성.
- READ_SLEEP, READ_STEPS, READ_TOTAL_CALORIES_BURNED 및 백그라운드/히스토리 권한은 이번 범위 제외.
- 권한이 제거되면 향후 읽기를 즉시 중단하고 UI에 표시. **기존 사용자가 승인한 운동시간은 그대로 유지**.

## 5. 정확한 사용자 흐름: 진입점별 동작

### A. 이미 계획된 날짜의 배드민턴 기록 열기

1. Record 탭에서 오늘 또는 과거 날짜 선택.
2. 해당 날짜에 canonical 배드민턴/레슨 항목이 있고, 아직 사용할 수 있는 시간 후보가 있으며, 사용자에게 보여줄 필요가 있으면 항목 내부에 작은 비차단 제안 행: “워치에서 운동시간 후보 발견 · 확인하기”.
3. 탭하면 아래 후보 목록/미리보기로 진입. 자동 팝업 반복 금지. 이미 시간 기록이 있고 확인된 항목은 **자동 제안으로 덮어쓰지 않고** 사용자가 직접 워치 버튼을 눌러야 함.
4. 거절/닫기 후에는 동일 후보·동일 항목에 재제안하지 않는다. “다시 찾기”는 가능.

### B. 과거/오늘 날짜에 새 배드민턴 종목 추가

1. 기존 ExercisePickerDialog에서 종목 선택 → 기존의 workout entry 및 기본 세트 생성까지 먼저 정상적으로 완료.
2. 추가한 항목의 시간 입력 영역에 후보가 있을 때만 “워치 시간 사용” 표시. 직접 입력은 바로 가능.
3. 후보 적용 전에 대상 세트를 확인. 한 세트이고 아직 미확인·사용자 수정 이력이 없다면 해당 세트를 기본 제안. 여러 세트·일부 확인 완료라면 적용할 세트/행을 명시적으로 선택.
4. 승인 직후 저장된 시간은 새 기록에 보이되 confirmed는 변경하지 않는다. 사용자가 기존 확인 버튼으로 최종 수행 승인.

### C. 사용자가 시간 입력 옆 “워치에서 가져오기” 선택

1. 해당 운동 기록·날짜에 대해 Health Connect 읽기 요청. 조회 중에는 버튼 로딩 표시/중복 호출 방지.
2. 실제 운동 세션과 HR 후보를 우선순위대로 후보 목록에 표시.
3. 선택 후 **시작/종료 수정** 또는 최종 분 단위 시간 직접 수정 가능.
4. 선택 없이 취소하면 데이터·기존 기록 불변.
5. 연결 불가/권한 없음/자료 없음이면 실패 원인과 “직접 입력”을 제공. 존재하지 않는 워치 기록을 생성하지 않는다.

### D. 미래 날짜의 계획 생성/복사/프로그램 적용

- 미래 기록이 없으므로 제안하지 않는다. 현재·과거에 실측 데이터가 있는 경우에만 조회.
- Plan 화면의 프로그램 생성·분배·처방에는 어떤 watch query도 자동 개입하지 않는다.
- “계획에 워치 데이터를 미리 적용” 같은 미래 예측 시간 자동 주입 금지.

### E. 같은 날 아침·저녁 배드민턴

- 07:05~08:15, 19:00~20:40 두 세션은 분리된 후보로 보여준다.
- 어떤 WorkoutEntry/세트에 연결할지 사용자가 선택. 날짜만 같다는 이유로 두 세션 합산 또는 하나로 덮어쓰지 않는다.
- 이미 다른 세트와 연결된 원본 후보는 “이미 사용됨” 표시. 사용자가 연결을 옮길 경우 **기존 연결 해제 확인**이 선행되어야 한다.
- 한 번 측정된 90분을 배드민턴 기록 두 개에 각각 90분 반영하는 중복 계산 금지.

### F. UI 표준 문구 예시

- “워치에 배드민턴 운동 기록이 있어요: 19:04–20:37 (93분)”
- “심박수 변화가 큰 시간대가 있어요. 배드민턴 시간이 맞나요?”
- “운동 기록은 찾았지만 시작·종료가 실제 운동시간과 다를 수 있어요.”
- “이 기록은 이미 다른 운동에 연결되어 있어요.”
- “삼성 헬스에서 아직 동기화되지 않았을 수 있어요. 나중에 다시 확인하세요.”
- “적용하면 현재 입력한 시간만 변경됩니다. 확인 여부와 RPE는 유지됩니다.”
- 모든 제안에 “추정/운동 세션/사용자 수정”이라는 출처 설명 제공. 근거 없는 정확도 퍼센트 표시 금지.

## 6. 후보 데이터 계층 및 신뢰도

### Source A: 명시적 운동 세션(우선)

- Health Connect ExerciseSessionRecord에서 startTime, endTime, exerciseType, metadata/dataOrigin 읽기.
- EXERCISE_TYPE_BADMINTON → “배드민턴 운동 세션” 후보. 실제 워치가 발행했다는 주장은 source가 확인된 경우에만.
- EXERCISE_TYPE_OTHER_WORKOUT/기타 동적 운동 → “운동 종류 확인 필요” 후보. 사용자 동의 없이 배드민턴으로 자동 분류 금지.
- RUNNING/WALKING 등 뚜렷하게 다른 종목은 배드민턴 자동 제안에서 제외(수동 탐색에는 “다른 운동” 별도 표시 가능).
- 기록된 start/end는 **세션 지속 시간**이다. 실제 랠리/활동 시간과 구별하고 세션의 pause/lap/segment가 없는 경우 활동 분수 추정 금지.
- 소스가 허용하면 휴식/일시정지 정보를 별도 표시하되 원본 세션 시간을 조용히 깎지 않는다.

### Source B: 심박수 기반 활동 시간대(보조)

- HeartRateRecord의 sample.time + beatsPerMinute. 운동 종류는 유도할 수 없으므로 항상 “활동 추정”으로 표기.
- READ_HEART_RATE 권한 필수. 측정 빈도가 낮거나 자료가 부족하면 “추정 불가/기록 부족”; 무리하게 구간을 만들지 않는다.
- 개인별 baseline이 충분하지 않거나, 여러 운동이 시간적으로 겹치거나, 센서 신호가 불연속인 경우 자동 제안 제외(수동 탐색의 그래프/시점 확인은 가능).
- 아침/저녁의 과거 습관과 해당 날짜의 배드민턴 계획은 **후보 순위 참고**에만 사용. 존재하지 않는 운동을 확정하는 근거가 아니다.
- 심박수만으로 운동 종류, 통증, 손상, 젖산, 활동 강도 또는 회복을 정확히 추정한다고 주장하지 않는다.

### Source C: 직접 입력(항상 제공)

- 모든 경우 직접 입력이 독립적으로 가능하다.
- 수동 입력·사용자 수정의 의미가 워치 자동 후보보다 우선이다.
- 워치 후보 생성 실패가 기존 WorkoutSet.seconds 값에 영향을 주면 안 된다.

## 7. HR 추정 알고리즘 초안(연구용 후보, 정식 승인 전 비활성)

이 절의 수치는 **시험을 시작하기 위한 엔지니어링 가설**이지 임상·운동과학적으로 검증된 경계값이 아니다. 파일럿 데이터 수집 → 오차·오탐 측정 → 사용자 검토 후 활성화한다.

1. 당일 로컬 날짜와 자정을 가로지르는 최대 1일 연장 범위에서 HeartRateRecord 조회; UTC Instant를 유지하고 조회 경계만 기기/기록의 ZoneId에 따라 변환.
2. 샘플 중복 정리(동일 시각, 서로 다른 출처 포함), 생리학적/형식상 불가능한 값 제외, 시간 정렬, 누락 구간 표시. 서로 다른 데이터 출처를 불명확하게 평균해 “워치 심박수”로 표기 금지.
3. 분 단위 구간의 median으로 시계열을 만든다. 무자료 분은 0이나 낮은 심박으로 보간하지 않는다.
4. 개인 기준선: 과거 여러 날의 충분한 안정 후보가 있을 때만 산출. 기본값을 나이 기반 최대심박 또는 모든 사람에게 동일한 BPM로 강제하지 않는다. 초기 가설: 3일 이상 유효 자료가 있고 여러 날 합산 수십 분 이상 안정 구간이 있을 때 검토. 부족하면 자동 제안 보류.
5. 높은 구간 가설: 기준선 대비 일정 폭/비율로 상승한 상태가 연속 관측되고, 일정 시간 내 반복되면 후보. 파라미터의 예시 탐색 범위는 개인 기준선 대비 +25~40 bpm, 연속 관측 3~8분, 쉬는 간격 묶기 5~12분. **값 확정 전 샘플 테스트 필수**.
6. 10분 이상 센서 기록 공백처럼 시작/끝을 보장할 수 없는 간격은 시각화에 “데이터 없음”으로 표시. 공백 전체를 운동으로 채우지 않는다.
7. 긴 한 세션 내부의 심박수 하강(경기 대기)을 무조건 종료로 보지 않는다. 쉬는 구간을 포함한 “코트 전체 시간”과 신호가 활발한 “관측된 활동 구간”을 별도 값으로 다룬다. 표본 부족이면 활동 구간 수치 미표시.
8. 비정상적으로 긴 후보(예: 수 시간 이상), 서로 다른 활동이 겹친 후보, 하루 여러 독립 구간은 각각 분리하거나 자동 제안 중지하고 수동 선택으로 전달.
9. 모든 HR 후보에는 근거 요약(관측 시작/마지막, 기록 범위, 누락 정도), 출처, uncertainty label (예: “시각 확인 필요”)을 표시. 근거 없는 확률값, 의료 등급, “정확함” 표시 금지.
10. 실제 배드민턴 여부와 시작·끝 ground truth가 없는 알고리즘 출력을 사용자의 **확인 없이** WorkoutSet에 반영하지 않는다.

향후 파일럿은 기능 플래그 WATCH_HR_CANDIDATE_PROTOTYPE 아래에서만 실험하고, 가설 파라미터와 샘플 데이터셋/검증 보고서를 별도 문서화한다.

## 8. 시간 정의·편집·적용

- **원본 운동세션 시간**: 소스의 endInstant - startInstant.
- **활동 관측 시간**: 실제 HR 관측으로 활동 가능성이 높은 간격의 합. 미측정 휴식·심박 지연 등을 감안해야 하므로 1차 분석에는 넣지 않는다.
- **승인된 기록 시간**: 사용자가 선택/수정한 최종 운동 지속시간. 기존 WorkoutSet.seconds와 정확히 같은 의미/단위.
- 1차 UI의 기본 입력은 “전체 세션 시간”. 사용자가 운동 시작/종료를 수정하거나 시간을 직접 입력할 수 있다.
- 소스는 초 단위 Instant 그대로 저장 가능하나 기존 시간 입력 UI는 시간/분 기준이다. 승인 시 **분 단위 반올림 후 seconds = minutes × 60** 사용, 원본의 초 단위 수치는 provenance 필드에 별도 보존. 반올림 규칙은 양수 기준 가장 가까운 분(30초 올림); 0분은 확인 불가. 날짜/시간이 역전되면 저장 금지.
- 추정 후보 구간의 경계 오차를 사용자에게 알리고, 수정 전/후 값을 보여준다.
- 사용자가 수동으로 WorkoutSet.seconds를 변경하면 소스 연결 상태를 “사용자 수정됨”으로 기록. 이후 재동기화가 수동 값을 복원하거나 덮어쓰면 안 된다.
- “워치 연결 해제”를 선택하면 소스 연결만 지우고 이미 입력·확인한 시간은 유지(명시적으로 “시간도 지우기” 선택 시에만 바꾸며, 확인 상태 자동 해제는 금지).
- **완료 세트에 대한 시간 덮어쓰기**는 비교 화면에서 기존 값과 새 값을 보여주고 재확인을 거친 후 기존 RecordSetEdit(DURATION) 경로로 저장. 확정된 RPE, 순서, rest, 퍼포먼스 기록, 프로그램 링크는 보존.
- 여러 스포츠 시간 세트가 있다면 한 워치 세션을 기본적으로 한 대상 세트에만 연결. 분할 배분은 1차 자동화 불허. 사용자가 복수 배분하려면 별도 분할 도구/총합 상한 검사에 대한 추가 설계 필요.

## 9. 제안 노출 정책: 귀찮거나 틀리게 묻지 않기

- 제안 대상: 위의 exact badminton practice stableKey, 해당 기록의 선택 날짜가 오늘 또는 과거, 읽기 권한 보유, 미사용 후보 있음.
- 최초 자동 동작: 사용자가 Record를 실제로 열 때 비차단 조회(또는 신선한 캐시 사용); Plan 화면에서 단순 스크롤/프로그램 생성할 때 자동 조회하지 않는다.
- 운동 추가 완료 후 한 번 확인하되, 이미 직접 입력한 시간이 있거나 세트 confirmed이면 자동 행 노출은 억제. 수동 워치 버튼은 항상 가능.
- 최초 제안이 보인 후보의 생성 규칙/출처 버전이 같으면 “닫기/이번에는 안 함” 이후 반복 노출하지 않는다. 앱 재시작·탭 왕복·날짜 왕복 시에도 동일.
- 새 기기 데이터가 늦게 도착하면 새 source revision에 대해 새 제안 자격을 판단. 이미 사용자가 수정한 시간에는 덮어쓰기 제안도 자동으로 하지 않는다.
- 하루에 여러 번 방문하더라도 모달 자동 팝업 없음. 화면 내 1개의 작은 제안만. 노이즈 많은 HR 후보는 수동 탐색 우선.
- 배드민턴 계획이 전혀 없는 날 심박수만 높았다는 이유로 새 운동 기록을 조용히 만들거나 푸시 알림을 보내지 않는다.
- 사용자가 “시간 제안 안 받기”를 끄면 후보 데이터 수집/자동 조회도 필요한 만큼 중지.
- 읽기 실패와 “후보가 없음”을 구분. 후보 없음은 정상이며 경고로 강조하지 않는다.

## 10. 후보 선택 우선순위와 충돌 방지

정렬:
1. 직접 기록된 BADMINTON exercise session (date overlap + source 확인)
2. 사용자가 동작을 확인해야 하는 OTHER_WORKOUT session
3. 검증 후 활성화할 HR 기반 추정 후보
4. 사용자가 직접 입력한 시간은 자동 후보보다 항상 우선(자동 덮어쓰기 금지)

후보 비교:
- 후보가 대상 날짜의 00:00–24:00와 교차하면 검색 결과 가능; 자정을 넘으면 시작 Instant·끝 Instant, 기록 기준 로컬 날짜를 함께 보여준다. 앱 WorkoutEntry.date는 사용자 선택 날짜를 유지하며 원본 날짜로 자동 변경 금지.
- 같은 실운동의 Samsung Health/타사 중복 동기화: source의 record ID + origin으로 1차 식별; 시간 중첩·종목·기기/출처 메타데이터로 중복 의심 표시. **다른 source의 유사 시각만으로 자동 삭제·병합 금지**.
- 여러 실제 세션이 1개 계획에 겹치면 후보 목록을 보여주고 선택받는다. 1차에서 자동 합산하지 않는다.
- 동일 source candidate가 이미 다른 세트에 승인 연결돼 있으면 다른 세트에 자동 적용 불가; 사용자가 이동 승인하면 한 트랜잭션으로 기존 링크 제거·새 링크 지정.
- 소스가 수정/삭제되어도 사용자가 승인한 시간은 바꾸지 않는다. 소스 변경됨/찾을 수 없음 상태만 표시하고 “다시 검토” 가능.
- 소스가 한 번 중복 수신돼도 새 후보 또는 새 WorkoutEntry를 반복 생성하지 않는다.

## 11. 연결의 상태 모델과 사용자 의사 보존

### Candidate state

- DISCOVERED: 원본 운동/심박 데이터 읽음
- REVIEWABLE: 날짜·운동 적합성/품질 검사를 통과, 사용자에게 제시 가능
- LOW_QUALITY: 불충분/모호, 자동 제안 불가(수동 조회에서 제한적으로 표시)
- DISMISSED: 해당 후보/타깃에 대해 사용자가 거절
- LINKED: 사용자가 해당 세트에 연결 승인
- SOURCE_CHANGED: 원본이 수정됨, 재확인 가능
- SOURCE_MISSING: 원본 삭제/권한 회수/기기 변경 등으로 접근 불가(원인 단정 금지)

### Attribution state

- WATCH_SESSION_ACCEPTED: 운동 세션 시간 승인
- HR_CANDIDATE_ACCEPTED: 심박 기반 시간 후보를 사용자 승인
- USER_EDITED_AFTER_ACCEPT: 연결 뒤 시간/경계 수동 수정
- DETACHED: 연결 해제, 저장된 duration 유지

불변식:
- 조회/미리보기/취소/제안 닫기/권한 팝업 거절은 WorkoutEntry, WorkoutSet, confirmed를 0건 변경.
- LINKED는 사용자 승인 직후 트랜잭션 성공한 경우만. 실패 시 이전 값 유지.
- 세트 1개 ↔ 활동 소스 1개. 같은 원본 전체 시간을 여러 세트에 복수 적용하는 중복 없음.
- 실제 사용자 승인 이전에 프로그램 완료·운동 수행·부하 계산에 기여하지 않음.
- 소스 revision 변경은 별도 경고 상태로 이동; 저장 데이터 덮어쓰기 금지.

## 12. 권장 저장 구조(제안, 아직 스키마 아님)

### 12-A. 로컬 원본/후보 캐시

별도 watch_candidate_cache 테이블(개념):
- localCandidateId(UUID), provider(HEALTH_CONNECT), originPackageName, sourceRecordId(nullable), sourceRecordType, sourceLastModifiedAt(nullable), sourceFingerprint/version
- startInstantUtc, endInstantUtc, associatedZoneId, localRecordDate, exerciseType(nullable)
- candidateKind(SESSION_BADMINTON / SESSION_OTHER / HR_HYPOTHESIS), qualityStatus, qualityReason
- observedCoverage/metricSummary(있을 경우), fetchedAt, expiresAt, sourceChangedFlag
- 민감한 원시 HeartRate sample 전체를 MVP에서 영구 저장하지 않는다. 시계열은 조회한 순간 메모리에서 분석하고 필요한 최소한의 품질 요약만 로컬에 저장.
- 데이터 출처 ID는 단말 로컬 비이식 식별자. 다른 폰에서 동일 ID를 기대하지 않는다.
- 캐시 30일 이내 제한·수동 초기화; 캐시가 사라져도 승인된 WorkoutSet 시간은 보존.

### 12-B. 운동 기록 귀속/감사 메타데이터

별도 workout_time_attribution 테이블(개념):
- attributionId(UUID), targetWorkoutEntryId(Long FK), targetWorkoutSetId(Long FK unique), targetWorkoutBackupSourceId(nullable)
- sourceKind(SESSION / HR_HYPOTHESIS / MANUAL), sourceProvider, originPackageName(nullable), sourceRecordId(nullable)
- sourceStartInstantUtc, sourceEndInstantUtc, userApprovedStart/End(nullable), approvedDurationSeconds
- status, acceptedAt, lastUserEditAt, sourceModifiedAt(nullable), revision
- UNIQUE(targetWorkoutSetId), source 소비 유일성 정책(중복 원본/분할 논리 고려), FK ON DELETE CASCADE(부모 제거 시 attribution 삭제).
- 한 사용자의 WorkoutEntry.sessionStableKey는 공유될 수 있으므로 이 테이블 PK/FK가 될 수 없다.
- 새로 들어온 워치 후보를 링크만 했다고 confirmed=true로 만들지 않는다.
- DB migration은 현재 schema 39를 기준으로 additive 변경 + 마이그레이션/역마이그레이션 안전성 확인. 정확한 다음 schema 버전은 구현 직전 최신 main 재확인 후 결정.
- WorkoutSet 필드를 직접 늘리는 방식과 별도 attribution 테이블 방식은 코드 설계에서 비교 가능하나, source 이력·비이식 캐시를 분리하는 후자를 우선 추천.

### 12-C. 영속성과 식별자

- 사용자의 운동 기록 측정치(승인된 seconds)는 기존 WorkoutSet가 진실의 원천(source of truth).
- 기록이 복사/이동/다른 날짜에 재생성될 때 attribution을 기계적으로 copy()하지 않는다. CalendarRecordService 경로에 안전한 명시적 정책을 추가.
- 내보내기/복원에서 로컬 set ID가 바뀔 수 있으므로 attribution backup 매핑은 단순 local ID가 아니라 기존 immutable workout backupSourceId + 해당 백업 내 고유 set locator(현행 setIndex 중복/재정렬 가능성 검증 필요)를 사용; 실현 어려우면 1차는 승인한 seconds만 기존 CSV에 남기고 attribution은 기기 로컬에서만 유지.
- Device 변경/복원 후 Health Connect 원본을 무단 재매칭하지 않음. 사용자 승인을 받은 시간만 정상 표시. 원본 근거가 로컬에 없으면 “이전 기기에서 승인한 시간” 또는 일반 수동 시간으로 fallback.
- cloud-backup 대량화 방지: 원시 HR 데이터/후보 캐시를 기존 자동 백업으로 보낼 이유가 없음. 구분된 provenance snapshot 추가는 제품 정책/개인정보 안내/백업 스키마 업그레이드 승인 이후만.

## 13. 기존 기록 조작과의 상호작용 매트릭스

| 사용자 행동 | 워치 연결 정책 | 기존 기록 정책 |
|---|---|---|
| 당일 종목 추가 | 후보 조회만, 수동 승인 | 기본 세트 정상 생성 |
| 운동시간 수동 변경 | 귀속 USER_EDITED_AFTER_ACCEPT | seconds만 변경 |
| 세트 확인/취소 | 링크 보존 | 기존 confirmed 경로 유지 |
| 세트 삭제 | 해당 귀속 제거 | 기존 삭제 흐름 |
| 운동 항목 삭제 | 해당 귀속·제안 상태 정리 | 기존 삭제 흐름 |
| 하루 전체 삭제 | 해당 날짜의 타깃 귀속 정리 | 기존 삭제 흐름 |
| 날짜 복사(계획) | 워치 원본 링크 복제 금지 | 현행 복사 정책 유지 |
| 날짜 복사(확인 기록 포함) | 워치 출처 링크는 복제하지 않음 | 현행 confirmed copy는 임의 변경 금지 |
| 날짜 이동 | 현지 날짜와 source 시간 불일치 검출; 원본 연결 자동 재해석 금지, 필요 시 재선택 | 기존 운동/세션/프로그램 링크 이동 유지 |
| “밀기” | 미래 미확인 계획 귀속 금지/해제; 원본 워치 source를 미래로 이동시키지 않음 | confirmed 기록과 미확인 계획 분리 유지 |
| 프로그램 재적용·덮어쓰기 | 삭제 타깃 링크 정리; 잔존 확정 링크에만 존속 | 기존 protected confirmed 로직 유지 |
| 백업·복원 | seconds 영속; 비이식 source 원본 의존 금지 | 기존 CSV/클라우드 규칙 유지 |
| 운동 종목 변경 | 새 종목이 불일치면 attribution “검토 필요” | 기존 편집은 계속 가능 |
| Samsung Health 소스 삭제/수정 | 미승인 후보 폐기/갱신; 승인 시간 자동 변경 금지 | confirmed 상태 보존 |

**특별 주의**: CalendarRecordService.moveDate는 기존 entry 행을 삭제하고 새 ID로 복사한다. 단순 targetEntryId FK만으로는 연결 보존/해제 결정을 표현할 수 없다. 새 migration/서비스에는 “move vs copy vs split/push”를 구분하는 작업 단위 이벤트/매핑이 필요하다. 초기에 문서에서 모두 해결한 것으로 표시하지 말고 구현 테스트로 확인한다.

## 14. 권장 컴포넌트와 호출 책임

앱 코드 구현 시의 **제안 구조**:

- health/HealthConnectAvailability.kt — OS/SDK/프로바이더/기능 가용성
- health/HealthPermissionCoordinator.kt — 최소 읽기권한과 회수 상태
- health/HealthConnectReadGateway.kt — 날짜 범위별 read-only exercise/heart-rate query, 페이지 처리, source origin
- health/WatchCandidateNormalizer.kt — 시간대/종목/원본 source 통일, 중복 가능성 식별
- health/WatchHrIntervalEstimator.kt — opt-in 테스트용 보수적 구간 후보, 자료 품질
- health/WatchWorkoutMatcher.kt — exact badminton stableKey와 시간 후보 매칭, 자동 제안 자격
- health/WatchTimeAttributionService.kt — 미리보기와 원자적 user accept/detach/reassign, current seconds 보호
- health/WatchDataRepository.kt — Room local candidate/attribution, TTL, source revision, permission status
- UI: RecordEntryCard/RecordSetRow에서 시간 입력 옆 action; RecordScreen의 대상 날짜/추가한 항목 연결; 별도 chooser dialog/bottom sheet; 설정 화면.
- Health Connect 패키지 레벨/매니페스트/권한·Play 정보 추가는 빌드 가능한 첫 구현 단계에서만.

호출 원칙:
- Compose 렌더링·상태 recomposition마다 조회 금지; 화면 진입 명시 이벤트/버튼 트리거 + debounce/in-flight dedupe.
- 1회 조회는 실제 필요한 날짜와 record types만; 화면 종료/날짜 변경 시 오래된 비동기 응답이 다른 날짜의 UI에 적용되지 않도록 request key/cancellation 적용.
- Health Connect의 제한/오류는 typed outcome으로 UI에 전달(NO_PERMISSION, UNAVAILABLE, NOT_SYNCED_OR_EMPTY, RATE_LIMITED, TRANSIENT_ERROR, SUCCESS).
- paging·변경내역/업데이트·삭제 처리는 API 버전 가용성에 맞춰 구현. 필요 시 change logs를 이용하고 token 만료 시 제한된 범위 재조회 및 중복 방지를 수행한다.
- 앱 성능/배터리: 초단위 폴링 금지. foreground on-demand와 제한된 캐시부터 시작. 주기적 background 읽기는 추후 별도 승인.

## 15. UI 상세 기준

### 기록 카드 축약 상태

- 기존 “시간/분 + RPE + 확인” 흐름 유지.
- 운동시간 입력 옆 폭을 과점하지 않는 텍스트/아이콘 행동 “워치에서 가져오기”를 별도 줄 또는 컴팩트 한 행에 배치.
- 표시 상태: 시계 아이콘 + “워치 적용: 93분”; HR 추정인 경우 반드시 “추정”.
- 시간 변경의 근거를 작은 보조 문구로 표시하고 클릭 시 상세.
- 확인 Checkbox와 워치 적용 버튼의 터치 영역을 분리해 잘못 누르는 일 방지.

### 후보 선택 화면

- 제목 “워치에서 운동시간 가져오기”.
- “날짜: 2026-10-26 · 배드민턴” + data last synced/fetched timestamp + 출처/권한 상태.
- 후보 카드당 시작~종료, 총 시간, 출처(배드민턴 세션 / 기타 운동 / 심박수 추정), 중복/이미 연결 상태.
- 후보 선택 → 시작/끝 시각 수정, 최종 분 확인 → “운동시간에 적용”. 시간 편집자는 자정 교차와 시간 역전 표시.
- 기존 값이 있으면 old → new 비교. confirmed이면 확인 한 번 더.
- 취소/다른 후보/직접 입력/동기화 재조회 선택 가능.
- 데이터 없음: “이 날짜에 공유된 운동 기록이 없습니다”와 삼성 헬스/Health Connect 상태 점검 안내. 조회 실패 시 “기록이 없습니다”로 허위 표시하지 않음.

### 접근성/적응형

- 320dp 안팎 폭, 태블릿 가로, 폰트 1.3~2.0배, 한국어/영어 긴 레이블 테스트.
- 시간 범위와 버튼이 좁은 칸에서 한 글자씩 세로 배치되지 않도록 wrap은 control 단위로.
- 버튼 contentDescription, TalkBack 읽기 순서, 48dp 권장 터치 타깃, 다크 모드/대비, 뒤로가기 시 취소/중복 저장 없음.
- 제안/상태를 색상으로만 구별하지 않고 텍스트로 구별. 불필요한 모달 반복 및 불필요한 높은 심박수 경고 금지.

## 16. 데이터 보호 및 오프라인

1. 원시 HR 개별 sample은 가능한 한 **조회 시 계산만 하고 영구 보관하지 않는다**. 최초 기능은 기록된 운동시간과 최소 출처 상태만 저장.
2. 동기화/수집은 사용자에게 보이는 기능에 필요한 최소 기간/유형. 개인 운동 패턴을 타사 서버로 보내지 않고 단말 우선.
3. 클라우드 백업은 기존 구조를 사용하되 원시 HR, source record ID, source 후보 캐시는 기본 제외. 나중에 민감 provenance까지 전송할 경우 별도 고지와 정책 검토.
4. 읽기 권한 동의와 제안 옵트인은 구분하고 언제든 중단할 수 있게 한다. 중단 시 저장된 사용자 승인 운동시간은 유지할지 명확하게 안내.
5. 로컬 캐시 삭제 버튼: 원본 후보/파생 추정 데이터 삭제, 사용자가 확인한 기존 WorkoutSet.seconds와 RPE/confirmed 보존.
6. 개인정보 처리방침에 읽는 데이터 종류·목적·저장 위치·보존 기간·삭제 방식·백업 포함 여부 명시. 콘솔 Health Apps declaration과 Data Safety 최신 정책 확인.
7. 로그/크래시 리포트/분석 이벤트에 원시 HR 타임라인, 개인 운동 시각, Samsung source ID를 무분별하게 남기지 않는다. 성능·오류 telemetry는 집계/비식별 위주.
8. 오프라인: 이미 확정한 WorkoutSet 시간 유지, 기존 수동 입력 완전 정상. Health Connect는 로컬 저장일 수 있으므로 네트워크 부재 자체를 무조건 조회 실패로 간주하지 않는다.

## 17. 실사용에서 빈틈 막기: 실패·경계 상황

- 워치 미착용 / 운동 측정 안 함: 후보 없음, 수동 입력.
- 운동 모드를 시작했으나 종료하지 않음 / 중간에 일시정지: 신뢰할 수 없는 전체 시간이면 수정 요구, 무한 세션 방지.
- 삼성 헬스에 보이는데 Health Connect 없음: 출처 동기화 불일치 안내, 임의 기록 생성 금지.
- 동일 날짜 두 운동 또는 운동+퇴근 계단: 후보 분리, HR만으로 배드민턴 확정 금지.
- 워치 저전력/센서 밀착 불량/샘플 간격 불규칙: HR 자동 제안 중지, 단순 범위 표시.
- 여러 원본이 동일 시간 중복: source/원본 중복 상태 표시, 자동 합산 금지.
- 과거/미래 일정, 날짜 선택 바뀜, 시간대 이동, 자정 넘김: UTC Instant 기준 저장, local selected date는 명시적으로 분리.
- 사용자가 기존 시간을 90분 입력한 후 워치가 92분 제안: 조용한 덮어쓰기 금지.
- confirmed 스포츠 set 수정: 반드시 diff/승인, 기존 분석 재계산 경로 정상 호출, 성능 posterior/프로그램 진행이 부수적으로 변하지 않았는지 검증.
- 다른 입력창이 포커스 중이고 워치 값이 늦게 도착: 포커스 중에는 화면값 변경 금지, 별도 적용 버튼으로만.
- 중복 탭/프로세스 종료/DB 실패/앱 재시작: transactional idempotency, retry 시 중복 귀속 없음.
- Health Connect 권한 회수/OS 업데이트/SDK 교체/계정 교체: read 중지 + 타입별 기능 fallback, 이미 확인한 시간 보호.
- 캐시 만료/원본 수정/원본 삭제: 사용자의 승인 기록 변동 없음; 오직 provenance 상태와 “다시 확인”만 표시.
- 백업 복원/신규 설치: 확정 seconds는 계속 보존; 소스 존재하지 않을 수 있음을 알리고 재승인 없이 연동하지 않음.
- Record 복사·이동·밀기·전체 삭제·프로그램 재적용: origin source가 잘못 계승/중복되지 않도록 전용 통합 테스트 요구.
- 다중 세트/레슨+게임 혼합: 하나의 워치 소스가 여러 세트에 중복 들어가지 않도록 사용자의 분리 승인 전 단일 타깃 원칙.
- 앱이 백그라운드/화면 회전/중지 상태로 가도 UI 갱신/잘못된 날짜 commit 경합 방지.

## 18. 테스트 계획과 중단 기준

### 단위 테스트

- ExerciseSessionRecord 시간 계산, 운동 종류 우선순위, badge 출처, 기기 출처 중복 판단.
- 추정 시간 빈 구간/불연속·경계·여러 번 고심박·측정없음 등 30개 이상 고정 fixture.
- 후보/타깃 상태 전이, once-per-source prompt suppression, source 수정·삭제, 사용자가 이미 수정한 시간 보호.
- 분 단위 반올림, 음수/0/역전/오버플로/자정·DST 시간대 케이스.
- source uniqueness 및 다중 세트 이중 계상 차단.
- 승인 실패/트랜잭션 롤백/중복 터치 idempotency.
- no-permission / partial permission / unavailable / old history / slow sync / offline / paging/limit handling.
- watch OFF/연결 없음에서 기존 Record 화면 구조·값/confirmation·추가/수정/삭제 동작 완전 동일.

### Room·CSV·클라우드 회귀

- DB v39→새 버전 이관, 오래된 DB 설치/재설치/기존 백업 import.
- workout_seconds와 confirmed, sessionStableKey, backupSourceId 유지.
- calendar copy, move, pushFuturePlan, split confirmed+unconfirmed, delete, program apply/overwrite, backup/restore 시 attribution 귀속 검사.
- 기존 CSV v14와 restore v13 읽기 유지; 신규 provenance export를 추가할 경우 format/schema 증가와 정확한 importer 대응 후에만.
- 기존 클라우드 백업 용량/스케줄 보장, 캐시·HR 원시 데이터 무분별한 포함 금지.
- OFI/BadmintonPracticeLoadCalculator 결과: 동일한 입력 seconds/RPE이면 연동 전후 **정확히 동일**. 바뀐 seconds만 의도대로 기존 분석에 반영.

### 장치 검증

- 갤럭시 워치7 + 삼성 헬스: 배드민턴 운동 모드 실행 1회, 운동 모드 없이 운동 1회, 1일 2세션 1회.
- 실측 타이머(운동 시작/종료)와 삼성 헬스 화면/Health Connect ExerciseSessionRecord 실제 data type, 출처, 샘플 밀도 비교.
- Wear OS 워치→삼성 헬스→Health Connect 지연 시간 분포 측정(보장 시간 주장 금지).
- READ_EXERCISE만 승인 / HR만 거절 / 전부 승인 / 중간 회수 각각 확인.
- Android 8/9(미지원 fallback), 13(provider), 14+ (framework) 앱 무중단; OS/SDK별 장치 가용성 검증.
- 일반 폰 320dp, 갤럭시 폴드/태블릿, 시스템 큰 글씨, TalkBack, 다크 테마, rotate/back button.

### HR 파일럿 출시 게이트(설계 제안)

- 실제 시작·종료를 사람이 기록한 최소 20~30개 배드민턴 세션 + 걷기/달리기/일상 활동 등 20개 이상 음성(비배드민턴) 구간.
- 적절히 측정된 세션에서 시작/끝 경계 오차 분포(중앙값·90백분위), 제안 누락률, **오탐 제안률**, 데이터 부족율을 함께 보고.
- 승인 전 워치 시간이 기존 기록에 들어간 케이스 0, 중복 제안/중복 계상 0, 과거 기록 손실 0이어야 함.
- 오탐/시간 오차 기준은 사용자 검토로 최종 정한다. 기준을 통과하지 못하면 HR 자동 제안 비활성, 운동 세션 읽기만 출시.
- 테스트가 아직 없는 알고리즘을 “실측 활동시간 자동 인식”으로 홍보하지 않는다.

## 19. 단계별 구현 순서(각 단계 별 독립 검증)

**P0 — 데이터 접근 가능성 검증 / debug-only**
- Health Connect read permissions 실험 경로와 단말 진단(실제 source, time range, exerciseType, HR density, sync latency)을 기존 사용자 데이터 변경 없이 구현.
- 삼성 헬스→Health Connect에서 배드민턴 유형/시간이 실제로 전송되는지 확인. 미지원이면 문서에 명시.

**P1 — 명시적 운동 세션 가져오기 / 가장 먼저 사용자 가치 제공**
- READ_EXERCISE, 날짜별 세션 목록, Record 시간 UI의 직접 선택, 미리보기/편집/승인.
- 운동 시간 WorkoutSet.seconds 반영, confirmed·RPE 보존, 충돌/중복 방지, 기본 로컬 attribution.
- Room/백업 경계 및 copy/move/push 회귀. watch OFF 기능 동등성 보장.

**P2 — 계획/기록 날짜에 조용한 자동 제안**
- canonical 배드민턴 entry의 조회 트리거 및 추천 우선순위, dismissed 상태, 늦은 동기화 처리.
- 화면당 한 제안, 알림 없이 사용자 최종 승인만. 한 날 여러 세션 및 UI 안정성 검증.

**P3 — HR 기반 시간대 후보 / 실험→검증→조건부 공개**
- READ_HEART_RATE를 별도 선택 허용, 데이터 밀도 조사, 프로토타입 후보 생성, 실측 ground truth 비교.
- 기준 만족 시에만 opt-in HR 제안 켜기. 그 전에는 debug/실험 기능으로만 유지.

**P4 — 향후 확장(별도 승인)**
- 심박수 그래프, 칼로리 등 보조 측정값을 기록에 연결, 일상 활동·수면 분석 검토.
- 삼성 Health Data SDK의 활동 요약/에너지 점수는 파트너 배포 요건·데이터 안전정책 확인 후 따로 결정.
- 워치 자체 앱/실시간 센서 스트리밍 필요성은 실제 P3 검증 실패 또는 추가 사용자 가치가 있을 때 평가.

## 20. 코드 작업 지시 및 완료보고 형식

- 위 단계마다 별도 PR/커밋과 테스트, debug APK/CI 결과를 보고하되 **설계 문서 추가 자체를 기능 완료라고 보고하지 않는다**.
- 각 단계 시작 시 최신 main 및 active protocol/version/schema를 다시 읽고 충돌 확인.
- 로컬 Gradle 실행 가능한 환경이면 실제 JVM/Android 빌드 테스트 수행, 불가능하면 이유·명령·출력 구별 기록.
- 테스트 결과: 통과/실패/스킵, coverage가 아닌 실제 행동별 검증표, migration/restore 증거, 변경 파일, 마지막 SHA, GitHub Actions URL.
- 기존 프로토콜을 바꿔야 한다면 소유 프로토콜 별 revision/evidence를 함께 갱신. 사용 승인 없이 OFI 및 badminton volume 공식을 변경하지 않는다.
- new enum, table, migration, backup schema, permissions, UI 문자열의 이름/버전은 구현 시 현재 코드와 대조해 확정한다.

## 21. 설계 결론과 남아 있는 사실 검증

### 이번에 확정할 제품 정책

1. 단말 우선, 수동 입력 보존, 사용자 승인 필수.
2. 명시적 워치 운동 세션 우선 → HR 활동 추정은 별도 실험.
3. 기존 seconds/confirmed/RPE 분석 경계 유지, 중복 계상 금지.
4. 새 운동 계획/세트를 워치가 임의 생성·완료하지 않음.
5. 계획 복사/밀기 시 원본 시간 자동 복제 금지.
6. 초기 권한은 READ_EXERCISE, READ_HEART_RATE는 추가 동의, 그 외는 추후.
7. 원시 HR의 무제한 영구 저장/클라우드 업로드 금지.
8. 측정 가능한 오류율을 확보하기 전 자동 운동 판정 기능 출시 금지.

### 실제 단말 시험 전에는 확정할 수 없는 사실

- 워치7/삼성 헬스 현재 버전이 배드민턴 기록을 Health Connect에 BADMINTON으로 발행하는지.
- 운동 모드를 켜지 않은 날에 시간대 재구성에 충분한 HR sample이 쌓이는지.
- 운동 중단·대기·휴식에서 세션 지속시간과 실제 활동시간이 얼마나 달라지는지.
- 최초 동기화의 실제 지연 시간 분포.
- HR 후보 알고리즘의 시간 오차와 오탐률.
- 연결 테이블의 백업 포맷 도입 여부와 정확한 다음 Room/CSV 버전(구현 직전 최신 main에 종속).

**Next action:** P0 진단용 읽기만 구현하여 갤럭시 워치7의 실제 Health Connect data origin과 운동 세션/심박수 기록 밀도를 확인한다. P0 결과 없이 HR 시간 추정 정확도를 보장하거나 production에 자동 적용하지 않는다.
