# Cloud Backup

| 항목 | 값 |
|---|---|
| Protocol ID | `DATA-CLOUD-BACKUP` |
| Protocol version | `1.1.0` |
| Status | `DRAFT` |
| Implementation status | `PARTIALLY_IMPLEMENTED` |
| Implemented from app version | `UNRELEASED_PHASE_2_SUPABASE_TRANSPORT` |
| Last audited commit | `c6e001ee3271f9086e679beb18199e171242ac67` |
| Evidence profile | `PRODUCT_POLICY, ENGINEERING_HEURISTIC` |
| Supersedes | 없음 |

`1.0.0`은 WhatYouGottaDo의 Cloud Backup, 계정별 데이터 격리, backup lineage,
semantic merge, conflict recovery, Supabase/R2 권한 경계를 처음 governed contract로
고정한 버전입니다. 전체 Cloud runtime 구현을 주장하지 않으며 local foundation 구현 범위는 19절에 명시합니다.

---

## 1. 일반 사용자용 요약

WhatYouGottaDo의 운동 기록은 **항상 로컬 Room DB에 먼저 저장**합니다.
Cloud Backup은 로컬 저장을 대신하지 않고, 로그인한 사용자의 로컬 데이터를
추가로 보존하는 기능입니다.

정상 사용에서는 사용자가 백업을 의식하지 않아도 됩니다.

- 운동 기록·프로그램 수정은 즉시 로컬에 저장됩니다.
- Cloud Backup은 수정 후 안정화, 세션 종료, 앱 background 진입 등의 시점에
  자동으로 시도합니다.
- 인터넷이 없거나 일시적으로 서버에 연결할 수 없어도 로컬 저장은 정상적으로
  계속됩니다.
- Cloud 실패는 로컬 저장을 rollback하지 않습니다.
- 로그인한 사용자는 Cloud Backup이 기본 ON이며, 별도로 OFF할 수 있습니다.
- OFF는 계보 삭제가 아니라 업로드 일시 중지입니다.
- 다른 기기와 변경이 갈라졌을 때는 이름이나 날짜만 보고 덮어쓰지 않습니다.
  stable identity와 backup lineage를 비교합니다.
- 서로 독립적인 변경은 가능한 범위에서 함께 보존합니다.
- 같은 논리 객체를 양쪽에서 다르게 바꿨을 때만 사용자 선택을 요청합니다.
- 사용자는 자동 병합 후보도 선택적으로 제외할 수 있습니다.
- 사용자가 확정한 최종 결과는 새 Cloud `CURRENT`가 되어 다음 변경의 기준점이 됩니다.
- 계정을 삭제해도 현재 기기의 운동 기록·프로그램은 삭제하지 않습니다.
  서버 계정·Cloud 데이터와 서버 계보 정보만 제거하고 로컬 데이터는 Guest 데이터로 남깁니다.

Cloud Backup은 의료 데이터 동기화, 실시간 공동 편집, CRDT 또는 모든 필드의 자동
충돌 해결을 목표로 하지 않습니다.

---

## 2. 목적

이 프로토콜의 목적은 다음 제품 문제를 해결하는 것입니다.

1. 기기 분실·교체 시 운동 기록과 프로그램을 복원할 수 있게 합니다.
2. 여러 기기 또는 Guest/로그인 전환에서 한쪽 데이터를 조용히 덮어쓰지 않습니다.
3. 기존 로컬 백업·복원 계약을 재사용하면서 Cloud 전용 계보와 충돌 처리를 분리합니다.
4. 사용자 데이터와 계정 간 소유권을 분리하여 다른 Google 계정의 데이터를 섞지 않습니다.
5. 서버 장애·네트워크 실패·앱 종료가 로컬 기록 손실로 이어지지 않게 합니다.
6. Cloud 저장공간과 업로드 빈도를 제한하여 운영비와 오용을 제어합니다.
7. 서버와 클라이언트의 권한 경계를 명확히 하여 R2 관리자 자격증명과 Cloud 상태 변경 권한이
   Android 앱에 노출되지 않게 합니다.

---

## 3. 적용 범위

이 프로토콜은 다음에 적용됩니다.

- Supabase Google Authentication
- 사용자 profile의 최소 식별 metadata
- Cloud Backup metadata
- Cloudflare R2에 저장하는 압축 backup object
- Android WorkManager 기반 Cloud Backup 실행
- backup lineage
- Local/Cloud 변경 비교
- semantic merge
- conflict resolution
- conflict recovery
- Local Recovery Snapshot
- account archive
- Guest ↔ Cloud 비교
- 계정 전환
- 계정 삭제 후 Guest 전환
- upload authorization quota
- global R2 storage guard
- retention과 orphan cleanup
- Cloud Backup 설정/UI 상태
- 기존 canonical backup/restore preflight의 재사용
- 새 `sessionStableKey`의 도입과 legacy migration

---

## 4. 비적용 범위

v1은 다음을 제공하지 않습니다.

- 실시간 record sync
- CRDT 또는 operational transform
- 여러 사용자의 공동 편집
- 프로그램 내부 `TrainingProgramItem` 단위 Cloud identity
- `WorkoutSet` 단위 stable identity 또는 set-level merge
- `TrainingProgramItemSet` 단위 Cloud identity
- 이름·날짜·순서·내용 유사도를 이용한 heuristic identity merge
- 다른 계정 간 자동 semantic merge
- Cloud Backup payload의 추가 client-side end-to-end encryption
- 소셜 공개 프로그램/피드의 실제 schema와 공개 정책
- 의료적 진단·회복 진단·생체 신호 해석

수동 export/import의 canonical 계약은
`docs/protocols/data_portability/BACKUP_AND_RESTORE.md`가 계속 소유합니다.
Cloud 기능은 그 logical snapshot과 restore preflight를 재사용하며, 수동 backup 계약을
이 문서가 대체하지 않습니다.

---

## 5. 용어

### 5.1 Cloud Backup

로컬 Room DB의 canonical user-data snapshot을 서버 metadata와 연결하여
Cloudflare R2에 보존하는 기능입니다.

### 5.2 CURRENT

해당 사용자에게 현재 Cloud 기준점으로 인정되는 정상 backup입니다.
사용자당 최대 하나만 존재할 수 있습니다.

### 5.3 RETAINED

직전 정상 `CURRENT`를 복구 여유를 위해 보존한 backup입니다.
정상 상태에서는 사용자당 최대 하나를 유지합니다.

### 5.4 backup lineage

`backup_id`와 `parent_backup_id`로 표현하는 single-parent backup 계보입니다.

### 5.5 localBaseBackupId

현재 활성 로컬 데이터가 어느 Cloud backup을 기준으로 파생되었는지 나타내는
로컬 상태값입니다.

### 5.6 localRevision

현재 Cloud 기준점 이후 아직 Cloud CURRENT에 포함되지 않은 **논리적 사용자 변경 횟수**입니다.
DB row 수가 아닙니다.

예: 프로그램 하나 삭제가 20개 child row를 cascade하더라도 `+1`입니다.

### 5.7 cloudBackupPending

현재 로컬 데이터에 아직 최신 Cloud CURRENT에 포함되지 않은 변경이 있음을 뜻합니다.

### 5.8 semantic identity

기기·Room local ID·이름·날짜·순서가 바뀌어도 같은 논리 객체임을 식별하는 영구 identity입니다.

### 5.9 semantic merge

stable identity와 공통 parent를 이용하여 서로 독립적인 변경을 함께 보존하고,
실제 의미 충돌만 사용자에게 선택시키는 병합입니다.

### 5.10 Local Recovery Snapshot

현재 기기에서 restore/import/bulk mutation 사고를 되돌리기 위한 단일 로컬 복구본입니다.
Cloud Backup 및 account archive와 별개입니다.

### 5.11 account archive

계정 전환 중 비활성 계정의 최신 로컬 상태를 잃지 않기 위한 계정별 로컬 snapshot입니다.
계정당 최신 1개만 유지합니다.

### 5.12 pending resolution

사용자가 conflict 선택을 완료하여 로컬 결과는 확정했지만,
그 결과가 아직 Cloud CURRENT로 승격되지 않은 상태입니다.

---

## 6. 입력 데이터와 권위 identity

### 6.1 Program

Cloud semantic identity는 기존 `TrainingProgram.stableKey`입니다.

- Room `TrainingProgram.id: Long`은 로컬 FK용이며 portable identity가 아닙니다.
- 이름 변경은 identity를 바꾸지 않습니다.
- 같은 이름의 서로 다른 stableKey는 서로 다른 프로그램입니다.
- Cloud merge를 위해 별도 `program_id`를 추가하지 않습니다.
- `TrainingProgramItem`에는 Cloud용 stable ID를 추가하지 않습니다.
- 프로그램 내부 항목은 Cloud merge에서 개별 객체로 병합하지 않고
  **프로그램 전체를 하나의 semantic unit**으로 취급합니다.
- `ProgramProgressionItem.logicalItemId`는 progression 전용 identity로 유지하며
  Cloud Program Item identity로 재사용하지 않습니다.

### 6.2 Exercise

`Exercise.stableKey`가 Cloud semantic identity입니다.

- 같은 stableKey는 같은 운동 정의입니다.
- 이름 유사성은 identity가 아닙니다.
- 공통 parent가 있을 때 서로 다른 필드 변경은 3-way merge할 수 있습니다.
- 같은 필드를 양쪽에서 다른 값으로 바꾸면 해당 Exercise 단위 사용자 선택이 필요합니다.
- metadata child마다 별도 Cloud identity를 추가하지 않습니다.

### 6.3 Workout Session

새 `sessionStableKey`를 도입합니다.

역할:

- 하나의 운동 세션을 날짜와 독립적으로 식별
- 날짜 `밀기` 또는 이동 후에도 같은 세션 identity 유지
- 세션 복사 시 새 identity 생성
- Cloud 비교 화면에서 관련 WorkoutEntry를 같은 세션으로 묶어 표현

기존 데이터 migration:

- 기존 record에는 session identity가 없으므로 migration 시 **같은 날짜의 WorkoutEntry를
  하나의 legacy session으로 묶고 한 번만 새 UUID를 부여**합니다.
- 시간 간격을 추론하여 여러 세션으로 나누지 않습니다.
- migration 이후 날짜가 이동해도 `sessionStableKey`는 유지합니다.

### 6.4 Workout Entry

기존 `WorkoutEntry.backupSourceId`가 개별 운동기록 semantic identity입니다.

현재 코드의 `WorkoutSourceIdentityProvider`는 새 entry identity에 database lineage와 UUID를 사용하고,
기존 row에도 source identity를 backfill합니다.

날짜 이동은 같은 `backupSourceId`를 유지하고,
복사는 새 `backupSourceId`를 받아야 합니다.

### 6.5 Workout Set

별도 stable identity를 추가하지 않습니다.

`WorkoutSet`은 `WorkoutEntry` 내부 데이터입니다.

따라서:

- set insertion/deletion/reorder를 별도 Cloud identity로 추적하지 않습니다.
- 같은 WorkoutEntry가 양쪽에서 달라진 경우 set 내용 전체를 해당 WorkoutEntry의 변경으로 비교합니다.
- 사용자 선택 단위도 WorkoutEntry까지이며 WorkoutSet 하나씩 선택하지 않습니다.

---

## 7. Cloud Backup 계약

### 7.1 전체 구조

Cloud v1 구조는 다음과 같습니다.

```text
Android / Room
    │
    ├─ Supabase Auth (Google)
    ├─ Supabase metadata / RLS
    └─ Supabase Edge Function
             │
             └─ short-lived presigned URL
                       │
                       └─ Cloudflare R2
```

원칙:

- 앱은 local-first입니다.
- backup bytes는 Supabase DB에 저장하지 않습니다.
- 앱은 R2 administrator key를 보유하지 않습니다.
- R2 key/secret은 server-side Supabase Secrets에만 둡니다.
- Edge Function은 client가 보낸 `user_id`를 신뢰하지 않고 verified auth에서 UUID를 얻습니다.
- presigned URL은 bearer capability이므로 전체 URL을 로그에 남기지 않습니다.
- JWT, R2 secret, service-role key를 앱/로그/Git에 남기지 않습니다.
- R2 credential은 backup bucket에 필요한 최소 Object Read/Write 권한만 가집니다.

### 7.2 계정과 로그인

첫 사용 화면:

- `Google로 로그인`
- `로그인 없이 계속`

Google login:

- Supabase UUID가 영구 내부 account identity입니다.
- nickname은 별도 표시값이며 중복을 허용합니다.
- 최초 profile에 nickname이 없으면 한 번 입력을 요청할 수 있습니다.
- Cloud Backup은 로그인 시 기본 ON입니다.
- 사용자는 로그인 상태를 유지한 채 Cloud Backup만 OFF할 수 있습니다.

Logout:

- 현재 활성 로컬 운동 데이터는 유지합니다.
- 서버 Cloud Backup도 유지합니다.
- Cloud/social access만 비활성화합니다.

Cloud Backup OFF:

- 업로드와 자동 restore를 중지합니다.
- `localBaseBackupId`를 지우지 않습니다.
- 로컬 변경은 `localRevision`과 pending 상태에 계속 누적할 수 있습니다.
- 다시 ON 하면 기존 lineage로 비교를 재개합니다.

### 7.3 계정 전환

다른 Google 계정으로 전환할 때 기존 활성 계정의 데이터를 새 계정에 자동 귀속하지 않습니다.

전환 순서:

1. 현재 계정의 활성 로컬 DB를 account archive로 생성
2. archive checksum/preflight 검증
3. pending resolution이 있으면 해당 계정 archive에 함께 보존
4. 검증 성공 후 활성 DB의 이전 계정 user-data를 비움
5. 새 계정의 local archive/Cloud 상태를 확인
6. 새 계정 데이터를 활성화

account archive:

- 계정당 최신 1개
- 임시 파일 생성 → SHA-256/preflight 검증 → atomic replace
- 비활성 계정에만 유지
- 해당 계정이 다시 활성화되고 Cloud 비교/복원/병합 및 필요한 동기화가 정상 완료되면 기존 archive를 소비·삭제
- 다음 전환 시 최신 상태로 새 archive 생성

### 7.4 Guest → 로그인

Guest 데이터가 있다고 해서 폐기하지 않습니다.

Cloud가 없는 새 계정:

- 현재 Guest 로컬 데이터를 해당 계정에 연결
- 최초 Cloud Backup을 생성

Cloud가 이미 있는 계정:

1. Guest 상태를 로컬 archive로 안전하게 보존
2. Cloud CURRENT와 Guest 데이터를 비교
3. 변경점 요약과 semantic comparison 제공
4. 사용자가 선택적으로 병합
5. 최종 결과를 새 CURRENT로 저장

Guest에는 신뢰 가능한 common parent가 없으므로 `UNKNOWN` 또는 `UNRELATED` lineage로 처리합니다.

### 7.5 계정 삭제

계정 삭제는 로컬 운동일지 삭제가 아닙니다.

순서:

1. 해당 사용자의 Cloud Backup objects 삭제
2. Cloud metadata/conflict/profile 등 서버 data 삭제
3. Supabase user account 삭제
4. 로컬의 인증 token과 서버 연결 정보 제거
5. `localBaseBackupId`, Cloud backup IDs, conflict IDs 등 삭제된 서버 계보 정보 초기화
6. 활성 Room DB의 운동기록·프로그램·운동 정의는 그대로 유지
7. 앱을 Guest/local-only 상태로 전환

pending resolution이 있으면:

- 그 결과 데이터 자체는 로컬에 남김
- 삭제된 account에 upload해야 한다는 pending 의무와 server lineage만 제거
- 이후 다른 계정 로그인 시 Guest ↔ Cloud 규칙 적용

### 7.6 Local Cloud state

로컬에는 개념적으로 다음 단일 상태를 유지합니다.

```text
cloud_backup_state

account_user_id
install_id
cloud_backup_enabled

local_base_backup_id
local_revision
cloud_backup_pending

last_local_change_at
last_successful_backup_id
last_successful_backup_at

retry_attempt
next_retry_at
last_failure_code
```

`install_id`는 hardware ID가 아니라 설치 시 생성하는 random UUID입니다.
재설치하면 새 install ID가 됩니다.

실제 user-data mutation과 다음 값은 같은 Room transaction에서 처리해야 합니다.

- `localRevision += 1`
- `cloudBackupPending = true`
- `lastLocalChangeAt = now`

transaction이 rollback되면 Cloud state 변경도 rollback되어야 합니다.

### 7.7 logical local revision

Cloud에 보존해야 하는 실제 user-data 변경만 revision을 증가시킵니다.

포함 예:

- 프로그램 생성/수정/삭제
- Exercise 생성/수정/삭제
- 운동 세션/WorkoutEntry 생성/수정/삭제
- 계획과 Cloud restore에 의미가 있는 durable user state

제외 예:

- 선택한 탭
- scroll 위치
- 단순 cache
- 화면 임시 상태
- theme 등 restore 대상이 아닌 UI state

판정 질문은 다음과 같습니다.

> 이 변경이 다른 기기에서 복원될 때 보존되어야 하는가?

### 7.8 Snapshot boundary

Cloud snapshot 시작 시 같은 consistent DB boundary에서 다음을 함께 고정합니다.

```text
snapshotRevision
snapshotCreatedAt
snapshotBaseBackupId
canonical user-data payload
```

업로드 중 발생한 이후 수정은 현재 snapshot에 섞지 않습니다.

예:

```text
snapshot 시작 localRevision = 3
업로드 중 새 변경 2개 → 현재 localRevision = 5
snapshot revision 3이 CURRENT 승격 성공
→ localBaseBackupId = 새 backup_id
→ localRevision = 2
→ cloudBackupPending = true
```

성공 시 무조건 revision을 0으로 만드는 구현은 금지합니다.

### 7.9 Backup trigger

자동 Cloud Backup 시도 trigger:

1. 마지막 local edit 이후 5분 동안 추가 변경이 없음
2. 운동 세션 종료
3. 앱 background 진입

세션 종료와 background 진입은 5분 debounce를 기다리지 않습니다.

중복 방지:

- pending이 없으면 upload하지 않음
- 이미 Cloud Backup worker가 실행 중이면 duplicate concurrent worker를 만들지 않음
- 마지막 성공 이후 실제 변경이 없으면 추가 backup을 만들지 않음

### 7.10 Android WorkManager

실제 Cloud 작업은 WorkManager 기반 durable work로 실행합니다.

- network connection 필요
- Cloud Backup worker는 동시에 하나만 실행
- app process 종료에 의존하지 않음
- worker 실행 중 새 edit는 다음 snapshot으로 남김
- restore/import/conflict apply/account switch와 backup mutation은 mutex/serialization

서로 겹치면 안 되는 작업:

- Cloud Backup finalize
- Cloud Restore
- Manual Import
- Conflict Recovery Restore
- account switch

일반 user edit는 Cloud upload와 동시에 가능하되 snapshot boundary를 침범하지 않습니다.

### 7.11 Offline

offline은 user-data failure로 취급하지 않습니다.

- Room 저장 정상
- pending 유지
- Local Recovery 정상
- 연결 복구 후 적절한 다음 trigger에서 backup 시도
- foreground 복귀 시 짧은 지연 후 재시도 가능
- background에서는 정상 WorkManager 조건을 기다림
- 무조건적인 duplicate immediate upload는 하지 않음

---

## 8. Cloud object, quota, retention

### 8.1 R2 object key

서버가 다음 key를 계산합니다.

```text
users/{verified_supabase_user_uuid}/backups/{backup_id}.csv.gz
```

규칙:

- `user_id`는 verified auth에서 얻음
- client는 임의 object path를 지정할 수 없음
- nickname/email/install_id를 path에 사용하지 않음
- `object_key`는 server-generated
- Cloud transport는 existing canonical logical backup snapshot과 restore preflight 의미를 보존해야 함
- manual CSV export format은 이 프로토콜이 변경하지 않음

### 8.2 Compression과 size

- canonical payload: `BackupExportService.buildCanonicalBackup()`이 생성한
  `CanonicalBackupContent.utf8Bytes()`의 UTF-8 CSV bytes
- storage pipeline: **canonical CSV bytes → gzip**; JSON wrapping이나 별도 serializer 금지
- manual export와 Cloud/local snapshot은 동일 canonical serialization/preflight 경로를 재사용
- R2, pending resolution, Local Recovery, Account Archive의 canonical snapshot 모두 `.csv.gz` 사용
- Account Archive 예: `archives/{account_id}/account_archive.csv.gz` (lineage는 별도 sidecar)
- compression: `GZIP`
- 최대 object size: **gzip 이후 실제 R2 object 기준 20 MB**
- server는 client가 보낸 size를 신뢰하지 않고 실제 object를 검증
- compression 실패 시 upload하지 않음
- 기존 CURRENT/RETAINED는 변경하지 않음

### 8.3 Integrity

- algorithm: `SHA-256`
- checksum 대상: 실제 저장/전송하는 gzip 압축 후 `.csv.gz` bytes (R2 object 및 local snapshot); 압축 전 CSV 또는 JSON이 아님

restore 검증 순서:

1. compressed size
2. SHA-256
3. gunzip
4. canonical restore preflight
5. transactional restore

checksum은 integrity 검증이며 encryption이 아닙니다.

### 8.4 Presigned URL

- PUT: 5분
- GET: 5분
- exact object + exact operation에만 유효
- 만료 시 새 URL 필요
- full URL을 log하지 않음
- v1에서 one-time URL을 전제로 하지 않음

Client upload authorization request는 `backup_id`, lineage/snapshot metadata 등 필요한
값만 보냅니다. `user_id`와 arbitrary `object_key`를 client 입력으로 받지 않습니다.

Download authorization은 `backup_id`를 받고 server가 owner와 object key를 해석합니다.

### 8.5 사용자 upload quota

사용자별 rolling 24시간 PUT authorization 최대 30회.

카운트 시점:

- **presigned PUT URL 발급 성공 시 +1**

카운트하지 않음:

- auth 실패
- quota rejection
- URL 발급 자체 실패

URL 발급 뒤 실제 upload/verify가 실패해도 이미 1회로 셉니다.
retry가 새 PUT URL을 요구하면 다시 quota에 포함됩니다.

### 8.6 Global R2 guard

운영 hard stop:

```text
cached actual R2 usage >= 8 GB
→ 신규 PUT authorization 중단
```

- restore/download는 계속 허용
- 기존 backup 삭제하지 않음
- usage가 8 GB 미만으로 돌아오면 신규 upload 재개
- usage cache TTL: 10분
- 별도 reservation system은 두지 않음
- 실제 10 GB 범위보다 2 GB 여유를 둔 제품 정책이므로 10분 cache 사이의 소폭 초과는 허용

### 8.7 Normal retention

정상 backup은 사용자당:

- `CURRENT` 1개
- `RETAINED` 1개

새 backup `N`이 VERIFIED이고 기존 `CURRENT=B`, `RETAINED=A`이면:

transaction 내부:

```text
B: CURRENT → RETAINED
N: VERIFIED → CURRENT
```

transaction commit 후:

```text
A object/metadata cleanup
```

삭제 실패:

- N CURRENT 유지
- B RETAINED 유지
- A는 일시적인 excess retained 상태로 남을 수 있음
- `RETENTION` failure 기록
- 서버 cleanup에서 재시도

정상 CURRENT 승격 전에 기존 A를 먼저 삭제하지 않습니다.

### 8.8 Conflict recovery retention

사용자 판단이 필요한 conflict를 해결할 때 Cloud CURRENT는 정상 retention으로 이전
RETAINED가 되고, Cloud에 없던 Local pre-resolution 상태는 필요 시
`CONFLICT_RECOVERY`로 7일 보존합니다.

- normal 2-backup limit에 포함하지 않음
- 자동 CURRENT 승격 금지
- `conflict_id` 연결
- `recovery_expires_at` 필수
- 7일 후 cleanup
- recovery row 자체를 CURRENT로 변경하지 않음
- recovery를 다시 채택하면 그 content로 **새 backup_id**를 생성하여 정상
  `UPLOADING → VERIFIED → CURRENT` 경로를 탑니다

자동 semantic merge만 일어나 사용자 conflict가 없었던 경우에는
`CONFLICT_RECOVERY`를 만들지 않습니다.

---

## 9. Lineage, 비교, semantic merge

### 9.1 Backup lineage

각 Cloud backup:

```text
backup_id: UUID
parent_backup_id: UUID | null
```

첫 backup은 parent가 null입니다.

같은 parent A에서 B와 C가 각각 생기면 branch입니다.

merge 결과도 multi-parent graph를 만들지 않고 **single-parent**로 유지합니다.

예:

```text
A → B (Cloud CURRENT)
A → Local branch

merge result M
→ M.parent_backup_id = B
```

Local branch가 A에서 갈라졌다는 사실과 common parent는 conflict/resolution history에 기록합니다.

### 9.2 Ancestry enum

```text
SAME
LOCAL_DESCENDS_FROM_CLOUD
CLOUD_DESCENDS_FROM_LOCAL
DIVERGED
UNRELATED
UNKNOWN
```

`created_at`만으로 ancestry를 판정하지 않습니다.
사용자 화면의 recency는 lineage와 별도로 표시합니다.

### 9.3 기본 판정

1. Local empty / Cloud exists  
   → latest valid Cloud 자동 restore

2. Local exists / Cloud absent  
   → Local을 첫 Cloud Backup으로 생성

3. same baseline, one side only changed  
   → changed side가 authoritative change

4. common parent에서 양쪽 모두 변경  
   → 3-way semantic merge

5. lineage unknown/unrelated  
   → parent 기반 3-way merge 금지, conservative comparison

6. conflict 여부를 단순 timestamp 최신성으로 결정하지 않음

built-in/default seed data만 존재하는 fresh install은 meaningful local user-data가 없는 것으로 볼 수 있습니다.

### 9.4 ADD

Identity는 stable key가 유일한 기준입니다.

- parent에 없고 한쪽에 새 stable ID → ADD
- 양쪽에서 서로 다른 stable ID ADD → 이름/내용이 같아도 둘 다 보존
- parent에 없는데 양쪽에 같은 stable ID가 나타남:
  - semantic content 동일 → 하나의 logical object로 자동 통합
  - content 다름 → 안전한 범위에서 비교하고 실제 conflict만 사용자 선택

이름 유사성으로 다른 stable identity를 자동 통합하지 않습니다.

### 9.5 Known-parent 3-way merge

같은 identity에 대해 common parent가 있으면:

- Local만 field/object 변경 → Local
- Cloud만 변경 → Cloud
- 양쪽이 같은 값으로 변경 → 자동 수용
- 양쪽이 서로 다른 독립 field/child를 변경 → 안전한 범위에서 모두 반영
- 같은 semantic field/object를 다르게 변경 → 사용자 conflict

### 9.6 DELETE

삭제 자체를 위험한 동작으로 취급하지 않습니다.
**삭제의 의미가 merge에서 모호할 때만 질문합니다.**

known common parent:

- 한쪽 delete + 다른 쪽 unchanged → delete 자동 적용
- 양쪽 delete → delete 자동 적용
- 한쪽 delete + 다른 쪽 modify → 사용자 선택

linear lineage에서 Local이 Cloud CURRENT를 base로 사용하고 Cloud에 별도 변경이 없는데
Local에서 삭제했다면 정상 사용자 변경이므로 다음 backup에 자동 포함합니다.

unknown/unrelated lineage:

- common parent가 없으므로 한쪽에만 존재하는 객체를 임의로 "삭제된 것"이라고 추정하지 않음
- side-only object는 보존 후보
- same identity가 양쪽에 있고 내용이 다르면 사용자 선택

### 9.7 Program merge unit

`TrainingProgram.stableKey`가 identity입니다.

- 프로그램 내부 item/set에는 Cloud merge stable ID를 추가하지 않음
- 같은 program stableKey가 양쪽에서 달라졌으면 **프로그램 전체를 semantic comparison unit**으로 사용
- 프로그램 내부 item별 자동 3-way merge는 v1에서 하지 않음
- `ProgramProgressionItem.logicalItemId`는 progression 목적 그대로 유지

### 9.8 Exercise merge unit

`Exercise.stableKey`가 identity입니다.

known parent:

- 서로 다른 field 변경 → 자동 merge 가능
- 같은 field가 다른 값 → Exercise 단위 conflict

child metadata row마다 stable ID를 추가하지 않습니다.

### 9.9 Session / WorkoutEntry merge unit

계층:

```text
WorkoutSession(sessionStableKey)
└─ WorkoutEntry(backupSourceId)
   └─ WorkoutSet(no Cloud stable ID)
```

Session은 container입니다.
실제 merge 선택 단위는 `WorkoutEntry`입니다.

예:

```text
Parent S
- Bench A
- Pullup B

Local: A 수정
Cloud: B 수정
→ 둘 다 자동 반영

Local: A 수정
Cloud: A를 다른 값으로 수정
→ A만 conflict
```

Set 내용은 WorkoutEntry 내부 변경으로 비교합니다.

### 9.10 UNKNOWN / UNRELATED

Guest/import 등 common parent를 신뢰할 수 없는 경우:

- 서로 다른 stable identity → 둘 다 보존
- 같은 identity + semantic content 동일 → 자동 통합
- 같은 identity + content 다름 → 사용자 선택
- 3-way change attribution은 하지 않음

---

## 10. Conflict와 resolution

### 10.1 Conflict 생성 조건

모든 자동 merge를 conflict로 기록하지 않습니다.

```text
모든 차이를 안전하게 자동 처리 가능
→ conflict row 없음

사용자 선택이 필요한 semantic conflict가 하나 이상
→ cloud_conflicts row 생성
```

### 10.2 conflict status

```text
USER_DECISION_REQUIRED
RESOLUTION_PENDING_UPLOAD
RESOLVED_LOCAL
RESOLVED_CLOUD
RESOLVED_MERGED
```

`RESOLUTION_PENDING_UPLOAD`은 사용자가 선택을 완료하고 로컬 결과까지 적용했지만,
그 결과가 아직 Cloud CURRENT로 승격되지 않은 상태입니다.

### 10.3 선택적 업데이트

Conflict/compare UI는 자동 병합 가능한 항목도 사용자에게 보여줄 수 있으며
사용자는 필요한 경우 자동 선택을 해제할 수 있습니다.

선택 단위:

- Program
- Exercise
- WorkoutEntry
- Session은 관련 entry를 묶어 보여주는 container
- WorkoutSet 개별 선택 없음
- TrainingProgramItem 개별 선택 없음

최종 버튼 예:

`선택한 변경사항 적용`

사용자가 체크한 결과 전체를 **하나의 Room transaction**으로 적용합니다.

### 10.4 Apply atomicity

순서:

1. Local Recovery Snapshot 생성
2. final merge result preflight
3. Room transaction 시작
4. 프로그램/Exercise/session/WorkoutEntry 변경 일괄 적용
5. Cloud local state 갱신
6. COMMIT

실패 시:

- 전체 rollback
- 기존 Local 유지
- 기존 Cloud CURRENT 유지
- conflict는 미해결 상태 유지

### 10.5 최종 결과가 새 정본

사용자 선택 결과 `M`은 먼저 로컬에 적용됩니다.

이후:

1. M의 exact snapshot 생성
2. upload
3. verify
4. CURRENT promotion
5. 성공하면 `localBaseBackupId = M.backup_id`
6. snapshot 이후 남은 local revision을 계산
7. 남은 revision이 0이면 pending false

Cloud update를 선택하지 않은 항목도 최종 Local state의 일부이므로 M에 포함됩니다.

### 10.6 Pending upload

사용자 선택 후 M upload가 실패해도 Local M은 rollback하지 않습니다.

- 기존 Cloud CURRENT는 유지
- Local M 사용 계속
- `cloudBackupPending = true`
- WorkManager가 retry
- Cloud 승격 전 conflict status는 `RESOLUTION_PENDING_UPLOAD`

CLOUD 전체 사용:

- 이미 Cloud CURRENT가 정본
- local restore가 성공하면 `RESOLVED_CLOUD`
- 새 CURRENT upload가 필요하지 않음

LOCAL 전체 사용 또는 MERGED:

- 새 Cloud CURRENT가 필요
- CURRENT 승격 전까지 pending status 유지

### 10.7 Pending resolution durable state

로컬에 최소:

```text
pending_resolution

conflict_id
resolution_mode
result_local_revision
base_cloud_backup_id
created_at
```

사용자가 이미 선택한 항목별 결과는 최종 Local DB에 반영되므로 선택 목록 전체를
다시 저장할 필요는 없습니다.

앱 재시작 시:

- pending resolution 존재
- cloud backup pending
→ conflict UI를 다시 묻지 않고 확정 snapshot upload부터 재개

### 10.8 Resolution snapshot boundary

사용자 conflict 해결 직후의 결과 M을 정확히 보존하기 위해 별도 임시 snapshot을 고정합니다.

```text
pending_resolution/
  result.csv.gz
  result.meta
```

그 뒤 사용자가 추가 기록 X를 만들어도:

```text
먼저 M → CURRENT
그 다음 X → 다음 normal backup
```

으로 처리합니다.

따라서 conflict resolution history의 `result_backup_id`는 사용자가 실제로 확인한
정확한 M을 가리킵니다.

동시에 pending resolution은 하나만 허용합니다.

CURRENT 승격 성공 후 해당 temporary snapshot을 삭제합니다.

### 10.9 계정 전환 중 pending resolution

A 계정의 pending M이 upload 전인데 B 계정으로 전환하면:

- A account archive에 active data + pending result + metadata를 함께 보존
- B와 섞지 않음
- A로 다시 로그인하면 M upload부터 재개
- 같은 conflict 선택을 다시 묻지 않음

---

## 11. Restore와 Local Recovery

### 11.1 Cloud Restore

1. client가 `backup_id`로 GET authorization 요청
2. server가 user ownership 확인
3. 5분 GET URL 발급
4. `.csv.gz` download
5. compressed size 검증
6. SHA-256 검증
7. gunzip
8. canonical restore preflight
9. restore 직전 Local Recovery Snapshot 생성
10. Room transaction으로 restore
11. 성공 시 local Cloud state 갱신

정상 CURRENT restore 완료 시 기본적으로:

```text
localBaseBackupId = restored backup_id
localRevision = 0
cloudBackupPending = false
```

restore 중 하나라도 실패하면 현재 Local DB를 변경하지 않습니다.

### 11.2 Local Recovery Snapshot

용도:

- accidental restore
- manual import
- conflict operation
- bulk deletion/change
- 기타 destructive mutation의 local rollback

저장:

```text
recovery/{generation_uuid}/local_recovery_previous.csv.gz
recovery/{generation_uuid}/metadata.json
```

정확히 1개의 valid previous recovery를 유지합니다.

생성 trigger:

- 일반 변경 후 5분 안정화
- BEFORE_CLOUD_RESTORE
- BEFORE_MANUAL_IMPORT
- BEFORE_CONFLICT_CLOUD_RESTORE
- BEFORE_BULK_CHANGE

Atomic replace:

1. immutable UUID generation directory에 CSV gzip과 sidecar를 fsync
2. compressed-byte checksum, gzip, canonical parser/canonicalizer/restore planner preflight
3. Room의 local recovery generation pointer를 transaction으로 promote
4. valid일 때만 이전 generation을 보수적으로 정리

현재 Phase 1-B2 구현은 이 pointer를 Room `app_meta`의 local infrastructure key로
기록합니다. 따라서 payload와 sidecar가 완성·검증되기 전에는 pointer가 바뀌지 않으며,
trusted restore에서는 복원 데이터·lineage·pointer가 같은 Room transaction에서
commit/rollback됩니다.

### 11.3 Recovery safe swap

현재 B, recovery A에서 A를 복원하면:

1. A 검증
2. 현재 B를 임시 보호
3. A restore
4. 성공 → current=A, Recovery=B
5. 실패 → current와 Recovery 모두 기존 상태 유지

### 11.4 Sidecar metadata

Canonical user-data payload에 Cloud lineage를 강제로 섞지 않습니다.
그러나 Local Recovery와 account archive는 별도 sidecar metadata에 lineage를 보존합니다.

공통 개념:

```text
snapshot_id
snapshot_type            // LOCAL_RECOVERY | ACCOUNT_ARCHIVE
created_at
account_user_id          // guest null
local_base_backup_id     // unknown null
local_revision
cloud_backup_pending
last_local_change_at
checksum
checksum_algorithm       // SHA-256
compressed_size_bytes
backup_format_version
schema_version
app_version
```

`cloud_backup_state`는 canonical CSV에 포함되지 않습니다. restore 시 현재 installation
`installId`와 enablement를 유지하고 retry, next retry, failure, last successful Cloud
acknowledgement는 복원하지 않습니다.

Account archive 추가:

```text
archived_reason
- ACCOUNT_SWITCH
- GUEST_LOGIN_MERGE
```

내부 snapshot restore 시 sidecar의 account/base/revision/pending/last-local-change
lineage를 복원합니다. 현재 설치의 `installId`와 Cloud enablement는 유지합니다.

### 11.5 Manual Import

일반 수동 Import는 source lineage를 신뢰하지 않습니다.

순서:

1. BEFORE_MANUAL_IMPORT Local Recovery 생성
2. import preflight/transaction
3. 성공 후:
   - `localBaseBackupId = null`
   - import를 하나의 logical mutation으로 취급
   - `localRevision`을 새 unknown branch에 맞게 설정
   - `cloudBackupPending = true`
4. Cloud가 있으면 `UNKNOWN` comparison

예외:

- app이 자체 생성하고 검증한 account archive / Local Recovery는 trusted sidecar lineage를 복원할 수 있음

---

## 12. Retry와 failure

### 12.1 Client retry

retryable Cloud user-data operation:

```text
1차 실패 → 1분
2차 실패 → 5분
3차 실패 → 15분
그 후 자동 retry 종료
```

로컬 durable state:

```text
retry_attempt
next_retry_at
last_failure_code
```

새 PUT URL이 필요한 retry는 30/24h authorization quota에 다시 포함됩니다.

### 12.2 Server cleanup retry

`RETENTION`과 `CLEANUP` 같은 서버 housekeeping은 Android가 재시도하지 않습니다.

- server cleanup 다음 run에서 재시도
- user popup 불필요
- failure log 기록

### 12.3 Failure stage

```text
AUTH
QUOTA
COMPRESSION
UPLOAD
VERIFY
FINALIZE
RETENTION
CLEANUP
DOWNLOAD
DECOMPRESSION
RESTORE
```

### 12.4 Failure code

AUTH:

```text
AUTH_REQUIRED
AUTH_INVALID
ACCESS_DENIED
```

QUOTA:

```text
DAILY_UPLOAD_LIMIT
GLOBAL_STORAGE_LIMIT
```

COMPRESSION:

```text
COMPRESSION_FAILED
COMPRESSED_SIZE_TOO_LARGE
```

UPLOAD:

```text
PRESIGNED_URL_FAILED
UPLOAD_FAILED
UPLOAD_TIMEOUT
```

VERIFY:

```text
OBJECT_NOT_FOUND
OBJECT_SIZE_INVALID
CHECKSUM_MISMATCH
BACKUP_FORMAT_INVALID
UNSUPPORTED_BACKUP_VERSION
```

FINALIZE:

```text
METADATA_WRITE_FAILED
CURRENT_PROMOTION_FAILED
PREVIOUS_CURRENT_DEMOTION_FAILED
CONCURRENT_PROMOTION_CONFLICT
```

RETENTION:

```text
OLD_BACKUP_DELETE_FAILED
OLD_METADATA_DELETE_FAILED
```

CLEANUP:

```text
FAILED_OBJECT_DELETE_FAILED
ORPHAN_OBJECT_DELETE_FAILED
```

DOWNLOAD:

```text
PRESIGNED_URL_FAILED
DOWNLOAD_FAILED
DOWNLOAD_TIMEOUT
```

DECOMPRESSION:

```text
DECOMPRESSION_FAILED
```

RESTORE:

```text
CHECKSUM_MISMATCH
UNSUPPORTED_BACKUP_VERSION
RESTORE_PREFLIGHT_FAILED
RESTORE_TRANSACTION_FAILED
```

### 12.5 Retryability

Non-retryable 기본:

```text
AUTH_REQUIRED
AUTH_INVALID
ACCESS_DENIED
DAILY_UPLOAD_LIMIT
GLOBAL_STORAGE_LIMIT
COMPRESSION_FAILED
COMPRESSED_SIZE_TOO_LARGE
OBJECT_SIZE_INVALID
CHECKSUM_MISMATCH
BACKUP_FORMAT_INVALID
UNSUPPORTED_BACKUP_VERSION
DECOMPRESSION_FAILED
RESTORE_PREFLIGHT_FAILED
```

Retryable 기본:

```text
PRESIGNED_URL_FAILED
UPLOAD_FAILED
UPLOAD_TIMEOUT
OBJECT_NOT_FOUND
METADATA_WRITE_FAILED
CURRENT_PROMOTION_FAILED
PREVIOUS_CURRENT_DEMOTION_FAILED
CONCURRENT_PROMOTION_CONFLICT
OLD_BACKUP_DELETE_FAILED
OLD_METADATA_DELETE_FAILED
FAILED_OBJECT_DELETE_FAILED
ORPHAN_OBJECT_DELETE_FAILED
DOWNLOAD_FAILED
DOWNLOAD_TIMEOUT
RESTORE_TRANSACTION_FAILED
```

### 12.6 User-visible failure

자동 회복 가능한 오류는 기본적으로 popup을 띄우지 않습니다.

UI 상태 예:

- 정상
- 백업 대기 중
- 백업 중
- 확인 필요
- 꺼짐

사용자 행동이 필요한 경우만 명확히 표시합니다.

예:

- login 필요
- 하루 authorization limit
- global storage stop
- backup size 초과
- unsupported future backup
- corrupt backup
- semantic conflict

내부 error code를 그대로 사용자에게 노출하지 않습니다.

---

## 13. Server data model과 제품 정책

### 13.1 profiles

```text
profiles

user_id UUID PRIMARY KEY
nickname TEXT
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

- `user_id` = Supabase auth user UUID
- nickname 중복 허용
- email/Google display name을 별도 profile column에 불필요하게 복제하지 않음
- v1에서 다른 사용자의 profile 직접 조회는 제공하지 않음

### 13.2 cloud_backups

```text
backup_id UUID PRIMARY KEY
user_id UUID NOT NULL
parent_backup_id UUID NULL
install_id UUID NOT NULL

created_at TIMESTAMPTZ NOT NULL
uploaded_at TIMESTAMPTZ NULL
verified_at TIMESTAMPTZ NULL

object_key TEXT NOT NULL UNIQUE

compressed_size_bytes BIGINT NULL
uncompressed_size_bytes BIGINT NULL

compression TEXT NOT NULL
checksum TEXT NULL
checksum_algorithm TEXT NULL

backup_format_version INTEGER NOT NULL
schema_version INTEGER NOT NULL
app_version TEXT NOT NULL

status TEXT NOT NULL

conflict_id UUID NULL
recovery_expires_at TIMESTAMPTZ NULL
```

status:

```text
UPLOADING
VERIFIED
CURRENT
RETAINED
CONFLICT_RECOVERY
FAILED
```

DB constraints:

- `backup_id` PK
- `object_key` UNIQUE
- `parent_backup_id` self FK
- 사용자당 CURRENT 최대 1개 partial unique index
- `compression = GZIP`
- `checksum_algorithm = SHA-256` when checksum present
- `CONFLICT_RECOVERY`이면 `recovery_expires_at` 필수
- 다른 normal status에서는 recovery expiry 금지

권장 indexes:

```text
(user_id, status)
(user_id, created_at DESC)
(parent_backup_id)
(conflict_id)
```

### 13.3 cloud_upload_authorizations

```text
authorization_id UUID PRIMARY KEY
user_id UUID NOT NULL
backup_id UUID NOT NULL
issued_at TIMESTAMPTZ NOT NULL
expires_at TIMESTAMPTZ NOT NULL
object_key TEXT NOT NULL
```

rolling 24h quota의 authority입니다.

### 13.4 cloud_backup_failures

```text
failure_id UUID PRIMARY KEY
user_id UUID NOT NULL
backup_id UUID NULL
install_id UUID NOT NULL

failure_stage TEXT NOT NULL
failure_code TEXT NOT NULL

occurred_at TIMESTAMPTZ NOT NULL
retryable BOOLEAN NOT NULL
technical_detail TEXT NULL
```

`technical_detail`에 secret/JWT/full presigned URL을 넣지 않습니다.

### 13.5 cloud_conflicts

```text
conflict_id UUID PRIMARY KEY
user_id UUID NOT NULL

local_install_id UUID NOT NULL
common_parent_backup_id UUID NULL
cloud_backup_id UUID NOT NULL
ancestry_status TEXT NOT NULL

detected_at TIMESTAMPTZ NOT NULL
resolved_at TIMESTAMPTZ NULL

conflict_status TEXT NOT NULL
resolution_backup_id UUID NULL

difference_report JSONB NOT NULL
```

`common_parent_backup_id`는 Guest/UNKNOWN case에서 null일 수 있습니다.

`difference_report`는 full dataset duplicate가 아니라 summary입니다.

예:

```json
{
  "programs": {
    "local_only": 1,
    "cloud_only": 2,
    "different": 1
  },
  "workout_entries": {
    "local_only": 4,
    "cloud_only": 7,
    "different": 2
  },
  "exercises": {
    "local_only": 0,
    "cloud_only": 1,
    "different": 0
  }
}
```

### 13.6 cloud_conflict_resolutions

```text
resolution_id UUID PRIMARY KEY
conflict_id UUID NOT NULL

resolution_mode TEXT NOT NULL
  LOCAL
  CLOUD
  MERGED
  RECOVERY

source_backup_id UUID NULL
result_backup_id UUID NOT NULL

resolved_at TIMESTAMPTZ NOT NULL
decision_summary JSONB NULL
```

MERGED의 decision summary는 실제 data copy가 아니라 결정 count/summary입니다.

예:

```json
{
  "programs": {
    "auto_merged": 2,
    "local_selected": 1,
    "cloud_selected": 0
  },
  "workout_entries": {
    "auto_merged": 5,
    "local_selected": 0,
    "cloud_selected": 1
  }
}
```

### 13.7 cloud_storage_state

전역 R2 usage 10분 cache를 위한 server state를 둡니다.

```text
cached_total_bytes
measured_at
```

정확한 persistence 방식은 구현 시 결정할 수 있으나, 8 GB guard 판정은 이 contract를 따라야 합니다.

---

## 14. Server authority, RLS, state transition

### 14.1 Client read/write boundary

Android client:

- publishable client key + user JWT 사용
- 본인 profile / 허용된 본인 Cloud 상태만 RLS 아래 read
- backup lifecycle 핵심 row를 직접 INSERT/UPDATE/DELETE하지 않음

Server/Edge Function:

- verified user identity 사용
- Cloud backup metadata mutation
- upload authorization
- verification
- CURRENT promotion
- conflict lifecycle
- retention/cleanup

### 14.2 RLS

`profiles`:

- 본인 SELECT
- 본인 nickname UPDATE
- user_id/created_at 임의 수정 금지
- initial row는 server/trigger 생성

`cloud_backups`:

- 본인 SELECT 가능
- client INSERT/UPDATE/DELETE 금지

`cloud_conflicts` / `cloud_conflict_resolutions`:

- 본인 SELECT 가능
- client mutation 금지

`cloud_upload_authorizations`:

- client 직접 접근 금지

`cloud_backup_failures`:

- client 직접 접근은 필수가 아니며 v1에서는 server/internal authority로 둘 수 있음

RLS와 별개로 DB grant도 최소화합니다.

### 14.3 Backup state transition

Normal upload:

```text
UPLOADING → VERIFIED → CURRENT
```

Failure:

```text
UPLOADING → FAILED
```

Transient finalize failure:

```text
VERIFIED 유지 → retry
```

기존 current:

```text
CURRENT → RETAINED
```

Conflict recovery object:

```text
UPLOADING → VERIFIED → CONFLICT_RECOVERY
```

금지:

```text
FAILED → CURRENT
RETAINED → CURRENT
UPLOADING → CURRENT
CONFLICT_RECOVERY → CURRENT
```

CURRENT가 되는 유일한 정상 경로는 `VERIFIED → CURRENT`입니다.

### 14.4 Idempotency

`backup_id`를 upload/verify/finalize idempotency key로 사용합니다.

같은 backup_id 요청:

- 이미 완료 → 기존 상태 반환
- 진행 중 → 현재 상태 반환
- duplicate row 생성 금지

### 14.5 CURRENT promotion transaction

server는 per-user serialization/row lock과 DB unique constraint를 함께 사용합니다.

transaction:

1. 현재 CURRENT 다시 조회
2. lineage/conflict 재검사
3. 기존 CURRENT → RETAINED
4. new VERIFIED → CURRENT
5. COMMIT

실패:

- rollback
- 기존 CURRENT 유지
- 새 backup은 VERIFIED 유지 가능

R2 old-object cleanup은 transaction 밖에서 수행합니다.

### 14.6 Concurrent device finalize

A가 CURRENT인 상태에서 기기 1과 기기 2가 모두 A를 parent로 B/C를 업로드할 수 있습니다.

- B가 먼저 commit → B CURRENT, A RETAINED
- C finalize 시 CURRENT를 다시 읽음
- C parent=A, current=B이면 branch로 판정
- C를 조용히 CURRENT로 승격하지 않음
- semantic comparison/merge로 이동

"먼저 성공한 기기"는 일시적인 CURRENT일 뿐 다른 branch를 폐기하는 권한이 아닙니다.

---

## 15. Upload verify / finalize

### 15.1 Upload authorization

server가 먼저 검사:

1. authenticated user
2. backup_id idempotency
3. rolling 30/24h authorization
4. global storage guard
5. lineage metadata validity
6. server-generated object key

성공하면 5분 PUT URL을 발급하고 authorization row를 기록합니다.

### 15.2 Upload completion

client가 R2 upload 후 server에 completion/finalize 요청합니다.

server는 client success claim만 믿지 않습니다.

검사:

1. R2 object 존재
2. actual compressed size
3. max 20 MB
4. SHA-256
5. supported backup format/schema
6. 필요한 transport/preflight validation
7. lineage를 다시 검사

URL 발급 시점과 finalize 사이에 다른 기기가 CURRENT를 바꿀 수 있으므로
lineage 재검사는 필수입니다.

### 15.3 Promotion

문제가 없으면:

```text
UPLOADING → VERIFIED
```

그 뒤 atomic CURRENT promotion을 시도합니다.

branch가 발견되면 무조건 promotion하지 않고 semantic merge/conflict workflow로 이동합니다.

---

## 16. Cleanup

Cleanup 실행 주체:

```text
Supabase Cron
→ scheduled cleanup Edge Function
→ Supabase metadata + Cloudflare R2
```

빈도:

- 하루 1회

처리:

- 24시간 이상 `UPLOADING` → orphan candidate
- FAILED object leftovers 삭제 재시도
- 7일 지난 `CONFLICT_RECOVERY` 삭제
- retention excess object cleanup
- cleanup failure log

보호:

- CURRENT/RETAINED 정상 object는 orphan cleanup이 임의 삭제하지 않음

Cleanup 실패:

- 사용자 popup 없음
- `CLEANUP`/`RETENTION` failure log
- 다음 scheduled run에서 재시도

---

## 17. 출력과 UI 해석

### 17.1 설정 화면

사용자-facing 상태는 내부 status를 단순화합니다.

```text
정상
백업 대기 중
백업 중
확인 필요
꺼짐
```

표시 예:

```text
클라우드 백업
상태: 정상
마지막 백업: 2026. 9. 16. 13:42
```

내부 `VERIFIED`, `CHECKSUM_MISMATCH`, retry counter 등은 기본 UI에 직접 노출하지 않습니다.

### 17.2 Conflict compare screen

권장 순서:

1. 한 줄 요약
2. lineage 상태 설명
3. Local 마지막 변경 / Cloud 마지막 backup 시각
4. Program / Exercise / Workout record 차이 summary
5. changed-only 상세 비교
6. 사용자 선택이 필요한 conflict
7. 자동 병합 후보와 선택 상태
8. 최종 적용 summary
9. `선택한 변경사항 적용`

`이 기기 전체 사용` / `클라우드 전체 사용`은 초기의 큰 기본 버튼이 아니라
고급 전체 override 옵션으로 둡니다.

기본 UX:

```text
차이를 이해
→ 필요한 변경만 선택
→ 최종 결과 확인
→ 원자적으로 적용
→ 새 Cloud CURRENT 생성
```

---

## 18. 예외 및 fallback

### 18.1 Future backup version

현재 앱이 이해하지 못하는 더 새로운 backup format/schema:

- restore 금지
- current Local 유지
- Cloud backup 자체를 FAILED/삭제하지 않음
- 앱 update를 요구

오래된 supported backup:

- 기존 `BACKUP_AND_RESTORE` migration/preflight 계약 사용

### 18.2 Corrupt object

size/checksum/gunzip/preflight 실패:

- Local DB 변경 금지
- CURRENT 자동 교체 금지
- 적절한 failure code 기록

### 18.3 Fresh install

meaningful user-data가 없고 로그인된 계정에 Cloud CURRENT가 있으면
latest valid Cloud backup을 자동 restore할 수 있습니다.

built-in/default seed만 있는 상태는 meaningful user-data가 없는 fresh state로 볼 수 있습니다.

### 18.4 Unknown lineage

일반 Manual Import, Guest 또는 lineage metadata를 잃은 로컬 데이터는
`localBaseBackupId = null`로 취급하고 UNKNOWN comparison을 사용합니다.

### 18.5 Cloud Backup OFF

OFF 중 로컬 변경을 허용합니다.
OFF 자체가 lineage reset은 아닙니다.

다시 ON:

- Cloud CURRENT가 base와 같으면 Local changes를 정상 upload
- Cloud CURRENT가 달라졌으면 preserved base를 이용해 비교/merge

---

## 19. 현재 구현 상태, 구현 위치, 검증

### 19.1 현재 구현 상태

이 문서는 `PARTIALLY_IMPLEMENTED`입니다. Phase 1-A의 session identity와
Phase 1-B1의 local state/revision infrastructure와 Phase 1-B2의 Local Recovery
subset이 구현되었습니다. Local Recovery는 canonical payload를 보존하고 보호된
import/bulk mutation 경계에서 reversible full replacement를 제공합니다.
Room 32 → 33은 installation-local `cloud_backup_state`만 추가합니다.
현재 canonical backup은 format 14 / restore schema 13이며 Cloud state를 포함하지 않습니다.

Phase 1-B1 source/test audit와 mutation classification:
[local revision audit](../../cloud_backup_phase_1b1_audit.md).
새 설치 상태는 revision 0 / pending false이며, 기존 DB migration은 Cloud 승인 이력이
없으므로 base/account/success ID 없이 revision 1 / pending true로 보수적으로 시작합니다.
일반 user operation은 동일 Room transaction에서 실제 domain 변경을 비교하여 한 번만
revision을 증가시킵니다. nested operation은 한 번으로 합쳐지며 no-op와 infrastructure는 제외됩니다.
수동 import는 Local Recovery 생성 뒤 import transaction과
`startExternalCloudBranchInTransaction` branch reset을 한 Room transaction으로
수행합니다. 이는 Cloud가 활성화된 end-to-end backup 동작을 의미하지 않습니다.

Phase 2 server transport foundation is now implemented in the repository:
`supabase/migrations/20260917000000_cloud_backup_transport.sql` adds the
protocol metadata tables, owner-only RLS/grants, current format/schema checks,
parent ownership constraints, source `local_revision`, and quota/storage indexes. The authenticated
`cloud-backup-upload-url` and `cloud-backup-download-url` Edge Functions verify
Supabase Auth identity, generate backup IDs/object keys, enforce request limits,
and issue five-minute R2 presigned PUT/GET URLs. They use only these server-side
secret names: `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`,
`R2_BUCKET`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`.
The R2 credential is a dedicated S3 API credential with Object Read & Write
limited to the `whatyougottado-backups` bucket. Configure these names through
Supabase Edge Function Secrets or the local ignored `.env`; never put values in
Git or logs. Remote migration and function deployment were not performed in
this task.

The upload authorization creates an `UPLOADING` metadata row. A future
completion/verification step must promote it through `VERIFIED` before
`CURRENT`; issuing a URL alone never acknowledges a valid backup.
Client upload/download workers, object verification/finalize, CURRENT promotion,
lineage comparison, semantic merge, conflict UI, account archive, retention,
and cleanup remain future work. Local Recovery's local-only WorkManager
stabilization job remains implemented.
기존 코드에서 확인한 재사용 authority:

- `TrainingProgram.stableKey`
- `Exercise.stableKey`
- `WorkoutEntry.backupSourceId`
- `WorkoutSourceIdentityProvider`
- move 시 `backupSourceId` 보존 / copy 시 새 identity 생성
- canonical local backup/restore preflight
- `ProgramProgressionItem.logicalItemId`의 progression-only 의미

아직 필요한 대표 구현:

- server-side object verification/finalize
- CURRENT promotion and retention
- semantic comparison engine
- conflict UI
- account archive / pending resolution
- cleanup Cron

### 19.2 Existing source anchors

현재 audit 기준 기존 파일:

```text
app/src/main/java/com/training/trackplanner/data/Entities.kt
app/src/main/java/com/training/trackplanner/data/WorkoutSourceIdentityProvider.kt
app/src/main/java/com/training/trackplanner/data/CalendarRecordService.kt
app/src/main/java/com/training/trackplanner/data/SafeBackupRestore.kt
app/src/main/java/com/training/trackplanner/data/BackupRestoreImportService.kt
app/src/main/java/com/training/trackplanner/data/ProgramProgressionModels.kt
app/src/main/java/com/training/trackplanner/data/ProgramProgressionBackup.kt
docs/protocols/data_portability/BACKUP_AND_RESTORE.md
```

Phase 1-B local implementation anchors:

```text
app/src/main/java/com/training/trackplanner/data/CloudBackupState.kt
app/src/main/java/com/training/trackplanner/data/CloudRevisionTracking.kt
app/src/main/java/com/training/trackplanner/data/TrainingRepository.kt
app/src/main/java/com/training/trackplanner/data/RecordMutationService.kt
app/src/main/java/com/training/trackplanner/data/DailyStatusService.kt
app/src/main/java/com/training/trackplanner/data/LocalRecoveryGate.kt
app/src/main/java/com/training/trackplanner/data/LocalRecoveryReplacement.kt
app/src/main/java/com/training/trackplanner/data/LocalRecoveryStore.kt
app/src/main/java/com/training/trackplanner/data/LocalRecoveryService.kt
app/src/main/java/com/training/trackplanner/data/LocalRecoveryScheduler.kt
app/schemas/com.training.trackplanner.data.TrainingDatabase/33.json
app/src/test/java/com/training/trackplanner/data/CloudBackupStateTest.kt
app/src/test/java/com/training/trackplanner/data/WorkoutSessionIdentityTest.kt
app/src/test/java/com/training/trackplanner/data/LocalRecoveryServiceTest.kt
```

Cloud transport foundation is implemented by the Supabase migration and the two authenticated presign functions above. Android Cloud upload/download, object verification/finalize, account archive, pending resolution, and semantic conflict handling remain unimplemented.

### 19.3 Required verification

구현 Definition of Done에는 최소 다음 범주의 자동 검증이 필요합니다.

Identity/migration:

- legacy same-date entries → 동일 sessionStableKey
- move → sessionStableKey/backupSourceId 유지
- copy → 새 semantic identity
- Program stableKey/Exercise stableKey portable identity 회귀

Snapshot/local revision:

- mutation + revision same transaction
- upload 중 추가 mutation 보존
- snapshot revision 일부 성공 시 remaining revision 계산
- process restart 후 pending/retry state 유지

Upload:

- size/checksum 검증
- 20 MB compressed limit
- 5분 URL expiry handling
- idempotent same backup_id
- 30/24h authorization
- 8 GB global stop

Finalize:

- CURRENT uniqueness
- concurrent device branch detection
- promotion rollback
- retention cleanup failure가 CURRENT를 손상하지 않음

Merge:

- independent ADD 보존
- same identity same content collapse
- known-parent independent change auto merge
- same semantic object conflicting change → user decision
- delete vs unchanged → delete
- delete vs modify → conflict
- UNKNOWN lineage에서 3-way 추론 금지
- Program whole-unit merge
- WorkoutEntry unit merge
- no WorkoutSet identity assumption

Conflict:

- automatic merge only → no conflict row
- pending upload survives process restart
- exact resolution snapshot boundary
- new edits after resolution snapshot go to next backup
- LOCAL/CLOUD/MERGED whole and selective choices
- conflict recovery 7-day behavior

Account:

- Guest + existing Cloud comparison
- account switch never cross-uploads previous account data
- account archive atomic replace
- account delete preserves active workout data and clears server lineage

Restore:

- corrupt/future backup fail-closed
- Local Recovery before destructive restore/import
- restore transaction rollback
- recovery safe swap

Security:

- client cannot mutate lifecycle tables
- RLS owner isolation
- server derives user UUID
- no secret/JWT/full presigned URL in logs

### 19.4 Implementation sequence

권장 순서:

**Phase 1 — Local foundation**

- sessionStableKey + Room migration
- canonical backup/restore schema extension
- cloud_backup_state
- Local Recovery
- account archive
- pending resolution snapshot/state

**Phase 2 — Supabase foundation**

- profile, cloud_backups, upload authorizations, and failure log metadata
- indexes/check constraints and owner-only RLS/grants
- authenticated upload/download presign Edge Functions
- conflict/resolution tables remain future work

**Phase 3 — R2 backup runtime**

- gzip object generation
- presigned PUT/GET authorization (implemented)
- verify/finalize
- WorkManager
- retention
- Cron cleanup
- quota/storage authorization guards (implemented; actual accounting remains future work)

**Phase 4 — Semantic merge/UI**

- lineage comparison
- semantic difference report
- Program/Exercise/Session/WorkoutEntry merge rules
- selective update UI
- conflict recovery/history
- resolution → new CURRENT cycle

각 phase는 관련 protocol/documentation와 tests를 같은 변경에서 갱신해야 합니다.

---

## 20. 권위 자산, 관련 문서, 변경 이력

### 20.1 권위 자산

이 프로토콜이 구현되면 다음이 authority가 됩니다.

- local Room durable user-data
- local Cloud state
- `BACKUP_AND_RESTORE` canonical logical snapshot/preflight
- Supabase auth UUID
- Supabase Cloud metadata
- Cloudflare R2 verified object
- `cloud_backups`의 단일 CURRENT pointer
- stable semantic identities defined in this document

R2 object만 존재하고 server verification/CURRENT promotion이 완료되지 않은 object는
정본이 아닙니다.

### 20.2 관련 문서

- `docs/protocols/data_portability/BACKUP_AND_RESTORE.md`
  - 기존 local/manual backup·restore canonical contract
  - Cloud sync/merge authority가 아님
- `docs/protocols/PROTOCOL_DOCUMENT_TEMPLATE.md`
  - governed protocol document template
- `docs/protocols/PROTOCOL_CHANGE_POLICY.md`
  - protocol 변경 절차
- `docs/protocols/CONTRIBUTING_PROTOCOLS.md`
  - protocol 문서 기여 규칙
- `docs/protocols/protocol_registry.json`
  - protocol registry

구현 시 `BACKUP_AND_RESTORE.md`에는 Cloud 기능이 별도
`DATA-CLOUD-BACKUP` protocol에 의해 관리된다는 cross-reference를 추가하고,
registry/index도 같은 task에서 갱신합니다.

### 20.3 변경 이력

- `1.1.0`
  - Local Recovery generation pairs use canonical CSV → gzip plus trusted sidecar metadata.
  - SHA-256, gzip, parser/preflight validation and Room-backed atomic generation pointer are required.
  - Protected manual imports and audited bulk mutations fail closed until a valid previous snapshot exists.
  - Trusted Local Recovery restore performs a reversible full replacement while preserving install identity.
  - Cloud transport, auth, Cloud CURRENT/RETAINED objects and network workers remain outside this release.

- `1.0.1`
  - canonical UTF-8 CSV → gzip representation 및 `.csv.gz` 경로 명료화
  - 공통 canonical builder 재사용과 실제 압축 bytes의 SHA-256 검증 명시
  - Cloud runtime 변화 없음 (SPECIFICATION_ONLY 유지)

- `1.0.0`
  - 최초 Cloud Backup specification
  - local-first + Supabase/R2 architecture
  - CURRENT/RETAINED/CONFLICT_RECOVERY lifecycle
  - stable semantic identity
  - `sessionStableKey`
  - semantic merge와 selective update
  - Guest/account switch/account deletion
  - Local Recovery/account archive/pending resolution
  - quota/storage guard/retry/cleanup
  - RLS/server authority
  - WorkManager execution contract
