# Community Sharing v1

| 항목 | 값 |
|---|---|
| Protocol ID | `COMMUNITY-SHARING` |
| Protocol version | `1.0.0` |
| Status | `ACTIVE` |
| Implementation status | `IMPLEMENTED` |
| Implemented from app version | `UNRELEASED_COMMUNITY_V1` |
| Last audited commit | `77569334` |
| Evidence profile | `PRODUCT_POLICY, ENGINEERING_HEURISTIC` |
| Supersedes | 없음 |

## 1. 일반 사용자용 요약

커뮤니티는 Google 로그인 후 사용하는 공개 프로그램·주간 요약·친구 활동 표면입니다.
홈의 `커뮤니티 접속` 카드에서 들어가며 친구, 프로그램, 주간 요약을 한 화면에서
살펴볼 수 있습니다. 프로그램을 검색하고 좋아요를 누르거나 내 프로그램으로 가져올
수 있고, 친구 코드로 요청을 보내 서로 허용한 작은 운동 활동만 볼 수 있습니다.

## 2. 목적

개인 운동 데이터와 분리된 공개 공유 경험을 제공하면서 닉네임, 프로그램 스냅샷,
안전한 주간 집계, 친구 관계를 서버 권위로 관리합니다. 공개 계정 UUID나 private
Cloud Backup lineage를 사용자에게 노출하지 않는 것이 목적입니다.

## 3. 적용 범위

인증된 Community API, `community_*` Supabase 테이블, Android Community 화면,
공개 프로그램 검색·필터·좋아요·가져오기, 명시적 주간 요약 공유, 친구 요청과
90분 TTL 활동 투영에 적용됩니다. Community 데이터는 Supabase/Postgres에만 둡니다.

## 4. 비적용 범위

댓글, DM, 팔로워, 순위표, 협업 편집, 자동 게시, 위치 공유, 건강·회복 지표 공유,
raw workout/set 공개, R2 기반 Community 저장은 포함하지 않습니다. private
Cloud Backup은 이 계약의 공개 데이터가 아닙니다.

## 5. 용어

- **Community profile**: 공개 닉네임, 친구 코드, 받은 프로그램 좋아요 합계, 친구 활동 privacy를 보유합니다.
- **CommunityProgramSnapshotV1**: Room 행이 아닌 버전 있는 공개 프로그램 사본입니다.
- **friend code**: 서버가 만든 8자리 숫자(`NNNN-NNNN`)이며 auth UUID가 아닙니다.
- **activity projection**: 확인된 운동 뒤 best-effort로 갱신되는 소셜 표시용 상태입니다.

## 6. 입력 데이터

서버는 검증된 JWT의 사용자, 닉네임·친구 코드·레이블·plain text, 프로그램 의미와
progression 스냅샷, 안전한 주간 aggregate만 받습니다. 프로그램 스냅샷에는 program
이름/기간/목표, item 처방과 set, exercise stableKey/name, progression track/binding을
포함할 수 있습니다. Room numeric ID, workout history, private notes, health/check-in,
`backupSourceId`, `sessionStableKey`, Cloud Backup ID, 계정 UUID는 입력·출력 경계를 넘지 않습니다.

## 7. 계산 또는 분류 계약

닉네임은 trim 후 2–24 Unicode code point의 plain text이고 control/newline을 거부하며,
소문자 normalized unique index로 case-insensitive uniqueness를 강제합니다. 프로그램
레이블은 `strengthRegions`(1개 이상), `strengthGoals`(1개 이상), 선택적
`functionalGoals`, `badmintonGoals` 배열로 검증합니다. 배열은 허용 vocabulary만
받고 중복을 제거하며 canonical 순서를 사용합니다. feed 검색은 프로그램명·코멘트·
주의사항·exercise text를 대상으로 하고, 같은 차원 안에서는 OR/array overlap,
차원 간에는 AND로 결합합니다. 기능성·배드민턴 검색에는 `NOT_INCLUDED`를 사용할 수
있고 빈 선택은 제약이 없습니다. 게시 전에는 strength 두 배열을 선택해야 하며 선택적
기능성·배드민턴 배열이 비어 있으면 미포함입니다. 게시본 업데이트는 같은 author/source stable key publication을
갱신하면서 public id, likes, 최초 게시 시각을 유지합니다. `LATEST`와 `POPULAR`는 결정적 keyset cursor 순서를
사용합니다. 좋아요와 저자 받은 좋아요 합계는 service-role RPC/trigger가 계산합니다.

## 8. 집계 방식

주간 공유는 명시적 publish/update만 수행하며 `(owner, weekStart)`가 유일합니다.
허용 aggregate는 week 범위, training days, strength session/set count, badminton
session/minutes입니다. 친구 activity는 `last_activity_at`가 90분 이내일 때만 현재
운동으로 간주하며 오래된 exercise name은 현재 값으로 반환하지 않습니다.

## 9. 출력과 UI 해석

공개 DTO는 `publicProgramId`, `requestId`, `friendshipId`, nickname, received like
count, labels, safe aggregate와 privacy 허용 activity만 반환합니다. auth UUID, email,
access/refresh token, object URL/key, R2 credential, Room ID와 raw record는 반환하지
않습니다. Home 순서는 Today Summary → Community Entry → Today Condition입니다.

## 10. 예외 및 fallback

Guest와 잘못된 JWT는 Community 작업을 수행할 수 없습니다. 닉네임 없는 계정은 게시와
friend identity 작업에서 안내됩니다. 네트워크 실패는 로컬 기록·프로그램 저장을
rollback하지 않습니다. malformed/unsupported snapshot은 Room transaction을 시작하지
않거나 transaction 안에서 전부 rollback합니다. 설치 로컬 `community_program_imports`가
source public id와 새 local stable key를 기록하고, 기존 import가 남아 있으면 사용자 확인
없이 복제하지 않습니다. 확인한 복제본은 새 stable key와 Room id를 갖는 독립 프로그램이며
이 provenance는 canonical Cloud Backup CSV에 포함되지 않습니다.

## 11. 개인화 또는 보정

개인화 계산이나 health 보정은 공유 payload에 들어가지 않습니다. 친구 activity privacy는
처음에는 세 항목 모두 false이고, 사용자가 확인한 뒤 서버에 저장됩니다. 서버는 Android가
숨긴 것과 관계없이 이 설정으로 각 field를 redaction합니다.

## 12. 연구 근거

Community는 연구 기반 부하·회복 모델을 정의하지 않습니다. 숫자와 표시 경계는 제품
정책 및 engineering heuristic이며, 원래 운동 계산기의 canonical 결과를 안전한 aggregate로
요약할 때만 사용합니다.

## 13. 제품 정책 및 휴리스틱

공개 author total은 현재 published program에 받은 like의 합입니다. weekly summary에는
좋아요가 없습니다. friend code lookup은 계정별 데이터베이스 counter로 15분당 30회로
제한합니다. accepted friendship과 양방향 block 검사를 서버가 수행합니다.

## 14. 알려진 한계

Community 활동 갱신은 확인된 운동 뒤 best-effort network projection이며 workout source
of truth가 아닙니다. hosted Edge Function deployment와 실제 기기에서의 모든 narrow-width
시각 검증은 배포 환경에서 재확인해야 합니다. 현재 v1은 댓글·DM·협업·health sharing을
지원하지 않습니다.

## 15. 현재 구현 상태

Android Community 화면, Home 진입, profile/nickname/privacy, array-based publication form,
multi-select program feed/search/filter,
like, snapshot import, weekly publish/unshare, friend request/accept/decline/remove/block,
TTL/privacy activity API와 migration이 구현되어 있습니다. Edge Function은 `verify_jwt = true`
로 등록되며 service role은 함수 내부에서만 사용합니다. Private Cloud Backup tables와
R2 object path는 Community 경로에서 읽지 않습니다.

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/CommunityScreen.kt`
- `app/src/main/java/com/training/trackplanner/CommunityViewModel.kt`
- `app/src/main/java/com/training/trackplanner/data/CommunityClient.kt`
- `app/src/main/java/com/training/trackplanner/data/CommunityProgramSnapshotCodec.kt`
- `supabase/functions/community-api/index.ts`
- `supabase/migrations/20260918000000_community_sharing.sql`
- `supabase/migrations/20260919000000_community_program_label_arrays.sql`
- `supabase/config.toml`

## 17. 검증 테스트

- `app/src/test/java/com/training/trackplanner/data/CommunityProgramSnapshotCodecTest.kt`
- `app/src/test/java/com/training/trackplanner/CommunityProgramLabelCatalogTest.kt`
- `app/src/test/java/com/training/trackplanner/CommunityUiStructuralTest.kt`
- `tests/community_sharing.test.mjs`
- `supabase/tests/community_sharing.sql`

주간 summary는 canonical sessionStableKey grouping과 canonical badminton practice volume
calculator를 재사용해 여러 exercise/row를 한 session으로 집계합니다. 테스트는 Room ID 제거와
fresh stableKey import/provenance, progression binding 재구성, malformed snapshot atomicity,
publication/filter validation, confirmed-only activity, JWT/RLS/grant/aggregate 계약을 확인합니다.

## 18. 권위 자산

Room schema 34와 `MIGRATION_33_34`가 설치 로컬 import provenance의 저장 경계를
정의합니다. 공개 snapshot의 의미는 현재 Room `TrainingProgram`/item/set/progression
모델에서 생성하며 server schema와 validation이 공개 경계를 결정합니다. provenance
테이블은 canonical Cloud Backup payload에서 제외됩니다.

## 19. 관련 문서

- [Cloud Backup](../data_portability/CLOUD_BACKUP.md): Community publication은 Cloud Backup payload가 아님을 명시합니다.
- [Protocol library](../README.md): registry와 family index입니다.

## 20. 변경 이력

- `1.0.0`: 첫 Community sharing contract와 Android/Supabase 구현을 등록했습니다.
