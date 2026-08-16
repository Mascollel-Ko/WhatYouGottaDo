# 배드민턴 연습 훈련량 계산

| Field | Value |
|---|---|
| Protocol ID | BADMINTON-VOLUME |
| Protocol version | 1.3.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | UNKNOWN_PENDING_AUDIT; practice/objective boundary clarified in v0.5.0.33; exact practice contract governed in v0.5.0.37 |
| Last audited commit | 2adc231 |
| Evidence profile | PRODUCT_POLICY, ENGINEERING_HEURISTIC |
| Supersedes | — |

## 1. 일반 사용자용 요약

현재 배드민턴 연습 훈련량은 `배드민턴` 또는 `배드민턴 레슨`으로
기록한 확인 완료 시간에 RPE 보정값을 곱해 계산합니다. 반복 수, 중량,
점프 수, 런지 수 또는 방향전환 횟수를 추정해 더하지 않습니다.

배드민턴 연습 훈련량은 다음과 별도인 숫자입니다.

- 아홉 가지 배드민턴 지원훈련 목적 자극
- 다음날 회복을 해석하기 위한 코트 노출 시간
- 과거의 풋워크/반응 및 보조운동 composite

이 숫자들을 `연습 + 목적 자극 = 총 배드민턴 부하`처럼 합산하지
않습니다.

## 2. 목적

`BADMINTON-VOLUME`이 소유하는 현재 배드민턴 **연습** 훈련량의
입장 identity, confirmed 입력, duration/RPE 산술, 날짜 및 주간 집계
경계를 재현 가능한 canonical 계약으로 고정합니다.

## 3. 적용 범위

현재 canonical identity 중 아래 두 stableKey만 연습 후보입니다.

| stableKey | Canonical display name | Required resolved activity kind |
|---|---|---|
| `ex_ae9ecdbc` | 배드민턴 | `SPORT_SESSION` |
| `ex_badminton_lesson` | 배드민턴 레슨 | `SPORT_SESSION` |

표시 이름은 검토용이며 계산 identity가 아닙니다. runtime 입장은 exact
stableKey와 resolved activity kind로 판단합니다. 이름 검색이나
`SPORT_SESSION + DIRECT` 같은 broad inference는 허용하지 않습니다.

## 4. 비적용 범위

이 protocol은 다음 의미를 소유하지 않습니다.

- `BadmintonObjectiveStimulusCalculator`의 아홉 objective와 280개 relation
- `CourtDurationRecoveryAnalyzer`의 next-day recovery court exposure
- `footworkReactiveRaw`, `supportRaw`, standardized composite index
- OFI/readiness의 broad legacy `BADMINTON_COURT` category
- 이벤트 수 추정, 센서 정밀도, 경기력 예측, 의학적 진단 또는 부상 확률

## 5. 용어

- **Practice record**: 3절의 exact identity와 activity kind를 충족하는 기록
- **Confirmed set**: `WorkoutSet.confirmed == true`
- **Practice raw**: 이 문서의 duration/RPE 식으로 계산한 비표준화 값
- **Court exposure**: 코트 시간과 다음날 회복 입력을 연결하는 별도 해석 입력
- **Objective stimulus**: explicit canonical relation으로 계산하는 아홉 목적의 별도 자극

공통 용어는 [`docs/protocols/common/TERMINOLOGY.md`](../common/TERMINOLOGY.md)를
따릅니다.

## 6. 입력 데이터

연습 계산에 필요한 최소 입력은 다음뿐입니다.

- exercise stableKey
- resolved activity kind
- ISO local date
- confirmed set seconds
- confirmed set RPE
- confirmed set RPE가 하나도 없을 때 사용할 entry RPE

미확인 set은 duration과 set RPE 양쪽에서 제외합니다.

## 7. 계산 또는 분류 계약

기록별 raw 값은 다음과 같습니다.

```text
confirmedMinutes = sum(confirmedSet.seconds) / 60
effectiveRpe = average(nonNull confirmedSet.rpe)
               or entry.rpe when no confirmed set RPE exists
practiceRaw = confirmedMinutes * badmintonIntensityFactor(effectiveRpe)
```

RPE modifier는 현재 제품 정책입니다.

| effective RPE | Modifier |
|---|---:|
| `null` | 1.00 |
| `<= 6.0` | 0.90 |
| `> 6.0 && < 8.0` | 1.00 |
| `>= 8.0 && < 9.0` | 1.05 |
| `>= 9.0 && < 10.0` | 1.10 |
| `>= 10.0` | 1.15 |

여러 confirmed set의 seconds는 먼저 합산하고, non-null set RPE는 산술
평균합니다. set RPE가 하나라도 있으면 entry RPE를 섞지 않습니다.
Practice raw에는 중량, 반복 수, event count 또는 support/base-dose fallback이
없습니다.

## 8. 집계 방식

- 같은 parseable ISO 날짜의 여러 practice record raw를 합산합니다.
- 서로 다른 날짜는 별도 daily point로 유지합니다.
- 주간 raw는 upstream이 제공한 `WeeklyTrainingData` bucket 안의 record raw를
  합산합니다. 현재 calculator는 record 날짜를 다시 bucket하지 않습니다.
- 공통 차트 주간 경계는 Monday-Sunday이며, 월 소유 및 표시 순서는
  `AnalysisChartTemporalPolicy`를 따릅니다.
- Practice raw와 objective stimulus를 하나의 total로 더하지 않습니다.

## 9. 출력과 UI 해석

Practice raw는 기록한 연습 시간과 자각 강도를 설명하는 제품 지표입니다.
진단, 조직 손상량 또는 정확한 외부 부하로 해석하지 않습니다.

현재 Analysis detail과 Lab에는 legacy `BADMINTON_TRAINING`,
`COURT_VOLUME`, `FOOTWORK_REACTIVE`, `BADMINTON_SUPPORT` consumer가 남아
있습니다. 그 composite 표시가 이 protocol의 practice 의미를 넓히지는
않습니다. 새 practice provider와 selector compatibility가 모두 연결되기
전에는 기존 metric ID를 relabel하거나 삭제하지 않습니다.

## 10. 예외 및 fallback

- confirmed seconds 합이 0이면 practice raw는 0입니다.
- confirmed set RPE와 entry RPE가 모두 없으면 modifier 1.00을 사용합니다.
- unparseable date를 daily path가 조용히 제외하는 동작은 현재 구현 세부사항이며
  canonical product 의미가 아닙니다.
- 음수 seconds가 nonpositive raw로 collapse되는 동작도 invalid-input 처리의
  현재 구현 세부사항이며 미래 authority가 아닙니다.
- 연습 시간이 없을 때 repetition, weight 또는 임의 event count를 만들지 않습니다.

## 11. 개인화 또는 보정

Practice raw 자체에는 개인 baseline이나 posterior 보정이 없습니다. 현재
주간 `courtVolumeIndex`의 historical standardization은 live legacy consumer
호환 경계이며 이 practice raw 계약의 일부가 아닙니다.

## 12. 연구 근거

이 계약의 identity, modifier, fallback은 `PRODUCT_POLICY` 및
`ENGINEERING_HEURISTIC`입니다. 논문 효과크기, 임상 검증 또는 손상 위험
추정으로 표현하지 않습니다.

## 13. 제품 정책 및 휴리스틱

현재 canonical catalog에서는 practice set과 recovery court-exposure set이
우연히 같은 두 stableKey입니다. 두 개념은 동일하지 않습니다.

- Practice는 RPE-adjusted training dose입니다.
- Court exposure는 unadjusted minutes를 다음날 회복 입력과 연결합니다.
- Court exposure rule은 `MATCH_RECORD`를 허용할 수 있지만 practice allowlist는
  exact 두 identity만 허용합니다.

아홉 objective는 `ACCELERATION`, `DECELERATION`, `FOOTWORK`,
`JUMP_LANDING`, `LUNGE_REACH`, `REACTION`, `CONDITIONING`,
`ROTATION_GENERATION`, `ANTI_ROTATION`이며 이 문서의 practice raw와
독립입니다.

## 14. 알려진 한계

- 이 protocol의 정확한 최초 app version은 추가 Git history 감사가 필요합니다.
- exact practice stableKey allowlist는 아직 legacy calculator 내부에 있습니다.
  다음 단계에서 작은 typed catalog로 이동해야 합니다.
- legacy footwork/support/composite와 그 UI/Lab consumers는 replacement-first
  migration이 완료될 때까지 남아 있습니다.
- self-entered duration과 RPE 품질에 의존합니다.

## 15. 현재 구현 상태

- Practice specification: `ACTIVE / IMPLEMENTED`
- Practice replacement readiness: `READY_FOR_REPLACEMENT_IMPLEMENTATION`
- Legacy calculator deletion: `NOT_READY`
- Runtime behavior change in this audit: none

## 16. 구현 위치

- [`BadmintonTrainingLoadIndexCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt): current exact practice path plus legacy components
- [`PerformanceTrendConstants.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/trends/PerformanceTrendConstants.kt): current RPE modifier boundaries
- [`ExerciseAnalysisMapper.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/features/ExerciseAnalysisMapper.kt): confirmed-set and RPE fallback projection
- [`CourtDurationRecoveryAnalyzer.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/coach/CourtDurationRecoveryAnalyzer.kt): separate recovery court-exposure boundary
- [`BadmintonObjectiveStimulusCalculator.kt`](../../../app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculator.kt): separate nine-objective boundary

## 17. 검증 테스트

- [`BadmintonPracticeLoadCharacterizationTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/trends/BadmintonPracticeLoadCharacterizationTest.kt)
- [`CourtDurationRecoveryAnalyzerTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/coach/CourtDurationRecoveryAnalyzerTest.kt)
- [`BadmintonObjectiveStimulusCalculatorTest.kt`](../../../app/src/test/java/com/training/trackplanner/analysis/badminton/BadmintonObjectiveStimulusCalculatorTest.kt)
- [`CanonicalAnalysisAuthorityTest.kt`](../../../app/src/test/java/com/training/trackplanner/data/CanonicalAnalysisAuthorityTest.kt)

## 18. 권위 자산

- [`exercise_bootstrap.csv`](../../../app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv)
- [`runtime_metadata.csv`](../../../app/src/main/assets/metadata/canonical_v1/runtime_metadata.csv)
- [`badminton_objective_relations.csv`](../../../app/src/main/assets/metadata/canonical_v1/badminton_objective_relations.csv): separate frozen objective authority
- [`badminton_practice_admission_set_matrix.csv`](../../audits/badminton_practice_admission_set_matrix.csv): 257-identity audit materialization, not runtime authority

## 19. 관련 문서

- [`BADMINTON_TRAINING_TAXONOMY.md`](BADMINTON_TRAINING_TAXONOMY.md)
- [`BADMINTON_TRANSFER_CATEGORIES.md`](BADMINTON_TRANSFER_CATEGORIES.md)
- [`replacement_first_analysis_migration_audit.md`](../../audits/replacement_first_analysis_migration_audit.md)
- [`docs/protocols/README.md`](../README.md)

## 20. 변경 이력

- `1.3.0` (2026-08-16): exact two-key practice admission, confirmed duration,
  RPE source/boundaries, date/week aggregation을 govern하고 legacy
  footwork/support/composite를 current practice contract에서 분리했습니다.
- `1.2.0` (2026-08-15): badminton practice volume을 유지하면서 retired
  seven-axis runtime 앵커를 제거했습니다.
- `1.0.1` (2026-07-19): 공통 temporal chart 표시 계약을 추가했습니다.
- `1.0.0` (2026-07-17): 당시 runtime을 첫 managed contract로 등록했습니다.
