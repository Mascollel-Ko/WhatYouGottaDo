# B-Coach 생성 품질 감사 — 2026-11-15

## 범위와 판정 기준

- 기준 브랜치: `feat/b-coach-v2-global-optimizer`
- 시작 SHA: `61cfc29a23a9e2be09bc96021e8606fc6d0b391e`
- 입력 기준일: `2026-11-15`
- 프로필 근거: 스쿼트 PR 170 kg, 데드리프트 PR 210 kg, posterior 없음
- 생성 경로: 실제 `TrainingRepository.generateV22Program()` → B 블록 생성 → joint optimizer → global selector → 후보 승인
- 저장 경로: 선택 후보 승인 → Room 저장 → DB 재열기 → 캘린더 적용

이 문서는 결정론적 Robolectric/Room fixture 결과다. Need, 회복, RIR 보정, 상대 강도 및 다목적 점수는 제품 정책 휴리스틱이며 임상 예측이나 보편적 과학 사실이 아니다. 전체 자동 원본은 로컬 `app/build/reports/b-coach/b_coach_generation_fixtures.md`에 생성된다.

> **2026-11-16 후속 변경 주의:** 아래 F1–F6의 프로필 PR 기반 추정 중량은
> 커밋 `8952ccae` 당시의 **90% 능력 하한 상한 정책**을 사용한 과거 fixture 결과다.
> 이후 `PROFILE_PR_PRIOR`에 한해 해당 하한을 실제 처방 중량 결정에서 제외한다.
> 기존 3% 보수 계수 및 세트별 후속 감량은 유지하며, 90% 시나리오는 위험 평가에 남긴다.
> 따라서 표의 중량과 예상 RIR은 후속 코드 실행 시 변경될 수 있고 새 실행 결과로 대체해서 해석해야 한다.
> 회귀 테스트: `P01` (프로필 PR의 반올림/비반올림 경로), `P02` (유효 posterior의 기존 보수 경로).

## A1–A9 결과

| 감사 항목 | 이전 원인 | 구현 결과 | 자동 검증 |
|---|---|---|---|
| A1 | 계산 전 0 kg baseline이 취소 후 편집 초안으로 남음 | fallback을 중량 해결된 baseline으로 통일하고 취소는 생성 전 초안으로 복귀 | 생성/취소 상태 및 후보 승인 회귀 PASS |
| A2 | 호환 posterior가 없으면 프로필 PR도 버림 | 유효 posterior가 우선하고, 없을 때 exact squat/deadlift에 `PROFILE_PR_PRIOR` 사용 | 프로필 170/210 kg 통합 fixture PASS |
| A3 | `estimatedLoadKg`가 있어도 UI는 공백 | `약 n kg · 장비 단위 확인 필요` 노출, 입력 확정 시 `USER_FIXED` 전환 | 미리보기/후보 UI 회귀 PASS |
| A4 | 후보에 들어온 운동으로 SPORT task universe를 생성 | 검증된 전체 배드민턴 카탈로그로 후보 독립 task universe 생성 | 중요도 1/5 대 5/5 비교 PASS |
| A5 | 기존 후보 장비를 사용자 장비로 오인하고 스포츠 모집 상한에 묻힘 | 명시 장비로 fail-closed, 스포츠 별도 모집, 모집→shortlist→채점→최종 진단 | 추적 stableKey 5개 진단 PASS |
| A6 | 3일 이상 연속과 일반 1세트 후보 허용 | 가능한 경우 3일 연속을 hard fail, 메인 3+/보조 2+, 드릴·회복·테스트 예외 | 6 fixture 최장 연속 1–2일, 1세트 0% |
| A7 | 360 dp 팝업과 내부 summary 문자열 6개만 표시 | 전체 화면 구조화 후보 UI, 전체 주/요일/세트, 고정 하단 승인/취소 | 320 dp, fontScale 1.3, 12주 탐색 PASS |
| A8 | 세트별 중량이 달라도 운동 대표값 0 kg로 오해 | 세트 배열을 우선 표시하여 `124.6→122.7 kg`처럼 각 세트 노출 | 세트별 상이 중량 UI/저장 PASS |
| A9 | 자동 progression UUID/요일 정규화가 사용자 편집으로 해시됨 | 의미 지문 v3, 파생 AUTO 상태 제외, 명시 progression 의도 포함, v2 replay 호환 | 추천·대안 승인, reconcile 후 저장 PASS |

## 실제 fixture 요약

| Fixture | 입력 | 선택 요일 | 최장 연속 | 1세트 항목 | SPORT 최종 선정 | 후보 수 |
|---|---|---|---:|---:|---|---:|
| F1 | 4주, 4일, 60분, SPORT 1 | 1·3·4·6 | 2 | 0/28 | 팔로프, 파머스 캐리 | 3 |
| F2 | 4주, 4일, 60분, SPORT 5 | 1·3·4·6 | 2 | 0/32 | 팔로프, 파머스 캐리, 가벼운 콕줍기 | 3 |
| F3 | 12주, 3일, 60분, SPORT 5 | 1·3·4 | 2 | 0/96 | 팔로프, 파머스 캐리, 가벼운 콕줍기 | 3 |
| F4 | 4주, 고정 1·3·5요일, 60분 | 1·3·5 | 1 | 0/32 | 팔로프, 파머스 캐리, 가벼운 콕줍기 | 3 |
| F5 | 4주, 3일, 30분, 맨몸만 | 1·2·4 | 2 | 0/16 | 버드독, 원레그 홉, 사이드 플랭크 | 3 |
| F6 | 4주, 3일, 90분 | 1·3·4 | 2 | 0/32 | 팔로프, 파머스 캐리, 가벼운 콕줍기 | 3 |

4주 fixture는 정확히 W1–W4만, 12주 fixture는 W1–W12를 포함한다. 비고정 fixture는 가능한 요일 조합이 있는 경우 주 경계(일→월 포함)에서도 3일 이상 연속을 허용하지 않는다.

## 대표 실제 프로그램 표

아래는 선택 후보의 1주차다. `~kg`는 프로필 PR prior에서 계산된 추정값이고, `보정 필요`는 exact-stableKey 능력 근거가 없어 임의 중량을 만들지 않은 상태다.

### F1 — SPORT 1/5, 4일·60분

| 요일 | 운동 | stableKey | 세트×반복 | 세트별 중량 | 목표/예상 RIR | 휴식 |
|---:|---|---|---|---|---|---:|
| 1 | 스쿼트 | `barbell_back_squat` | 3×6 | ~124.6 / 122.7 / 122.7 kg | 3.0 / 7.2·7.4·7.0 | 120초 |
| 1 | 밴드 팔로프 프레스 | `band_pallof_press` | 2×6 | 비중량 지표 | 미확정 | 45초 |
| 3 | 데드리프트 | `barbell_deadlift` | 5×8 | ~146.8 / 144.6 / 144.6 / 142.4 / 142.4 kg | 3.0 / 6.9·7.0·6.7·6.9·6.6 | 150초 |
| 4 | 중량 친업 | `ex_e41e8dcf` | 3×6 | 보정 필요 | 4.0 / 미확정 | 120초 |
| 4 | 덤벨 파머스 캐리 | `dumbbell_farmer_carry` | 2×6 | 비중량 지표 | 미확정 | 60초 |
| 6 | 벤치프레스 | `barbell_bench_press` | 5×5 | 보정 필요 | 4.0 / 미확정 | 120초 |
| 6 | 오버헤드 프레스 | `ex_32219f7a` | 4×6 | 보정 필요 | 4.0 / 미확정 | 120초 |

### F2 — SPORT 5/5, 4일·60분

| 요일 | 운동 | stableKey | 세트×반복 | 세트별 중량 | 판정 |
|---:|---|---|---|---|---|
| 1 | 스쿼트 | `barbell_back_squat` | 3×6 | ~124.6 / 122.7 / 122.7 kg | 메인 3세트 |
| 1 | 밴드 팔로프 프레스 | `band_pallof_press` | 4×5 | 비중량 지표 | SPORT 보조 증가 |
| 3 | 데드리프트 | `barbell_deadlift` | 5×9 | ~143.2 / 141.0 / 141.0 / 138.9 / 138.9 kg | 메인 5세트 |
| 3 | 가벼운 콕줍기 | `ex_216351a1` | 5×6 | 비중량 지표 | SPORT 5에서 추가 선정 |
| 4 | 중량 친업 | `ex_e41e8dcf` | 3×6 | 보정 필요 | 임의 중량 없음 |
| 4 | 덤벨 파머스 캐리 | `dumbbell_farmer_carry` | 2×7 | 비중량 지표 | 일반 보조 2세트 |
| 6 | 벤치프레스 | `barbell_bench_press` | 5×5 | 보정 필요 | 임의 중량 없음 |
| 6 | 오버헤드 프레스 | `ex_32219f7a` | 3×6 | 보정 필요 | 임의 중량 없음 |

### F4 — 사용자가 1·3·5요일 고정

| 요일 | 운동 | stableKey | 세트×반복 | 세트별 중량 |
|---:|---|---|---|---|
| 1 | 스쿼트 | `barbell_back_squat` | 3×6 | ~124.6 / 122.7 / 122.7 kg |
| 1 | 벤치프레스 | `barbell_bench_press` | 3×6 | 보정 필요 |
| 1 | 밴드 팔로프 프레스 | `band_pallof_press` | 4×5 | 비중량 지표 |
| 3 | 데드리프트 | `barbell_deadlift` | 3×8 | ~146.8 / 144.6 / 144.6 kg |
| 3 | 오버헤드 프레스 | `ex_32219f7a` | 3×6 | 보정 필요 |
| 3 | 가벼운 콕줍기 | `ex_216351a1` | 3×5 | 비중량 지표 |
| 5 | 중량 친업 | `ex_e41e8dcf` | 4×6 | 보정 필요 |
| 5 | 덤벨 파머스 캐리 | `dumbbell_farmer_carry` | 2×5 | 비중량 지표 |

### F5 — 맨몸만, 3일·30분

| 요일 | 운동 | stableKey | 세트×반복 | 장비 판정 |
|---:|---|---|---|---|
| 1 | 버드독 | `ex_28347c1f` | 2×6 | 맨몸 적격 |
| 2 | 원레그 홉 투 스틱 | `ex_314df428` | 5×6 | 맨몸 적격 |
| 4 | 월싯 | `ex_64661229` | 2×5 | 보정 필요 |
| 4 | 사이드 플랭크 | `ex_f6d43398` | 2×5 | 맨몸 적격 |

## SPORT 중요도와 추적 stableKey

SPORT 1/5의 F1은 28개 항목에서 팔로프·파머스 캐리를 선택했다. 같은 시간/요일 조건의 SPORT 5/5 F2는 32개 항목으로 늘고 `ex_216351a1` 가벼운 콕줍기를 추가 선택했다. 중요도가 결과를 바꾸되 특정 운동을 강제 삽입하지 않는다.

| stableKey | F2 전체 장비 | F2 결과 | F5 맨몸 장비 결과 |
|---|---|---|---|
| `lateral_bound_continuous` | 적격 | 모집, 최종 shortlist 밖 | 적격, 최종 shortlist 밖 |
| `ex_34e7d21` | 적격 | 모집, 최종 shortlist 밖 | shortlist·채점 후 미선정 |
| `ex_4255e429` | 적격 | shortlist·채점 후 미선정 | `CONE_MARKER` 장비 불가 |
| `ex_421ba24b` | 적격 | shortlist·채점 후 미선정 | `CONE_MARKER` 장비 불가 |
| `ex_bc84eb7f` | 적격 | shortlist·채점 후 미선정 | `CONE_MARKER` 장비 불가 |

각 행은 메타데이터 존재, DB 활성, `PROGRAM_SELECTABLE`, 장비, 모집, shortlist, 채점, 최종 결과를 별도 필드로 기록한다. F2에서 랜덤 콕줍기 3종이 탈락한 것은 메타데이터 누락이 아니라 다른 후보의 점수/시간/회복 비교 결과다.

## 승인·저장 E2E

1. 생성된 모든 fixture에서 추천안과 표시된 대안 각각을 동일 baseline 지문으로 승인했다.
2. 승인 대기 전후 자동 progression reconcile은 AUTO UUID/연결만 바꿀 때 stale로 처리하지 않았다.
3. 사용자가 명시 progression 연결/모드/규칙을 바꾸면 지문이 달라졌다.
4. 첫 fixture의 승인 후보를 Room에 저장하고 DB snapshot으로 다시 열어 item 수와 세트 구조를 비교했다.
5. `2026-11-16`부터 캘린더에 적용하여 실제 workout/set 행이 생성되는지 확인했다.

## 로컬 검증 결과

- 핵심 B-Coach/v2.2.1 묶음: 100 tests, failures 0, skipped 2.
- 전체 `:app:testDebugUnitTest`: 433 suites, 2,865 tests, failures 0, errors 0, skipped 25, 15분 59초.
- 정상 worker 첫 전체 실행은 새 `progressMetricType` 소비자 두 경로가 감사 인벤토리에 빠진 것을 검출했고, 인벤토리를 갱신한 뒤 해당 게이트가 통과했다. 같은 실행 후반에는 알려진 Windows `robolectric-nativeruntime.dll` `EXCEPTION_ACCESS_VIOLATION`이 JVM 밖에서 발생했다.
- 2-worker 격리 실행은 2,865개 테스트를 모두 수행했지만 다른 프로세스와의 `.robolectric-download-lock` 생성 충돌 1건이 있었다. 해당 클래스는 단독 6/6 통과했고, 최종 전체 성공 실행은 임시 `forkEvery=1`, `maxParallelForks=1`로 완전 직렬화했다. 두 설정과 crash log는 검증 직후 제거되어 전달 diff에 포함되지 않는다.
- `:app:lintDebug`, `:app:assembleDebug`, `:app:compileReleaseKotlin`, `:app:compileDebugAndroidTestKotlin`: 모두 PASS, 최종 증분 실행 4분 33초.
- lint: errors 0, warnings 209 (전체 issue 231; 나머지는 informational).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 78,634,831 bytes, SHA-256 `69D5D9A2F6A1CA3CADFC258637F1E46B9A15FE01828063356F9FA8CB8C291C24`.
- `adb devices -l`: 연결 기기 없음. 따라서 `connectedDebugAndroidTest`와 실제 기기 터치/렌더링은 미실행이다.

## 남은 한계

- 이 결과는 고정 seed와 in-memory Room의 결정론적 fixture이며 실제 사용자 DB·기기 렌더링을 대신하지 않는다.
- 프로필 PR prior는 실제 완료 기록/유효 posterior보다 낮은 근거다. posterior가 유효하면 posterior가 우선한다.
- exact-stableKey 능력 근거가 없는 벤치프레스·오버헤드 프레스·중량 친업은 보정 필요 상태를 유지한다.
- SPORT 5는 관련 운동의 채택 가능성을 높이지만 모든 DIRECT 운동 선정을 보장하지 않는다.
- 예상 세션 시간, 회복, Need 및 minimax regret은 제품 정책 계산이며 부상 확률이나 임상 처방이 아니다.
- 실제 Android 기기 상호작용은 연결 기기가 있을 때만 별도로 검증할 수 있다.
