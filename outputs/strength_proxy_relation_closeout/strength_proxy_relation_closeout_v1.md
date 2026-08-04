# Strength proxy relation closeout v1

## Scope

This closeout fixes only the **exercise-to-strength-target relationship set**.

It does not set or change:

- population alpha priors;
- transfer-slope values;
- residual variance;
- shared-factor loading magnitudes;
- personalization thresholds;
- weekly alpha-learning algorithms;
- production posterior behavior.

## Final active relation set

- Direct anchors: **5**
- Shared-factor proxies: **12**
- Total active relations: **17**
- Explicit exclusions from the previous 24-row provisional set: **7**

### BENCH_PRESS

Direct anchor:
- `barbell_bench_press` — 벤치프레스

Shared-factor proxies:
- `ex_27b3deb5` — 클로즈그립 벤치프레스
- `ex_3a7d3eda` — 덤벨 벤치프레스
- `ex_a61f1e96` — 인클라인 덤벨 프레스
- `ex_1dbee10e` — 머신 체스트프레스

### BACK_SQUAT

Direct anchor:
- `barbell_back_squat` — 스쿼트

Shared-factor proxies:
- `ex_c5043892` — 프론트 스쿼트
- `ex_fa3416f6` — 스미스 머신 스쿼트

### DEADLIFT

Direct anchor:
- `barbell_deadlift` — 데드리프트

Shared-factor proxies:
- `barbell_romanian_deadlift` — 루마니안 바벨 데드리프트
- `dumbbell_romanian_deadlift` — 루마니안 덤벨 데드리프트

### WEIGHTED_PULL_UP

Direct anchor:
- `ex_e41f4c2b` — 중량 풀업

Shared-factor proxies:
- `pull_up` — 풀업
- `ex_e41e8dcf` — 중량 친업
- `ex_6466fe77` — 친업
- `ex_dc9e5953` — 랫풀다운

### MILITARY_PRESS

Direct anchor:
- `ex_32219f7a` — 오버헤드 프레스  
  Strict standing barbell overhead press without intentional knee or hip drive.

Shared-factor proxies:
- none in v1

## Relation semantics

Direct anchors use:

`TARGET_SPECIFIC_ALLOWED`

Non-direct relations use:

`SHARED_FACTOR_ONLY`

No absent relation is treated as incomplete. Absence means the exercise is not currently authorized to route evidence into that strength target.

## Deferred alpha-learning contract

Population priors and personal alpha learning are intentionally deferred.

Future design must retain enough data and metadata to construct an exercise-specific weekly latent-strength series. A week without a session must not disappear and must not be filled with zero or a copied observation. It must be represented by an interpolated latent state with increased uncertainty.

Required future provenance states include at least:

- `OBSERVED`
- `INTERPOLATED`
- `EXTRAPOLATED`

Interpolated rows may align proxy and target time series, but must not receive the same likelihood weight as direct observations.

## Validation

| checkId                          | status   |   observed |   expected | detail                                                                                        |
|:---------------------------------|:---------|-----------:|-----------:|:----------------------------------------------------------------------------------------------|
| RELATION_COUNT                   | PASS     |         17 |         17 | Five direct anchors plus twelve shared-factor proxies.                                        |
| UNIQUE_RELATION_ID               | PASS     |         17 |         17 | No duplicate relation IDs.                                                                    |
| UNIQUE_TARGET_EXERCISE_PAIR      | PASS     |         17 |         17 | No duplicate target/exercise pairs.                                                           |
| ONE_DIRECT_ANCHOR_PER_TARGET     | PASS     |          5 |          5 | Each target has exactly one direct anchor.                                                    |
| PROXY_SHARED_ONLY                | PASS     |         12 |         12 | All non-direct relations are shared-factor only.                                              |
| NO_NUMERIC_PRIORS                | PASS     |          0 |          0 | Registry intentionally contains no alpha or population-prior numeric parameters.              |
| FUTURE_INTERPOLATION_REQUIREMENT | PASS     |         17 |         17 | Future alpha-learning weekly series must interpolate no-session weeks and retain uncertainty. |
