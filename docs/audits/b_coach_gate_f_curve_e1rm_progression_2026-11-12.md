# B-Coach Gate F — 사용자를 위한 수식 설명

**요점:** 무게를 올렸다는 사실에는 보상을 주지 않는다. 실제로 완료한 세트와 RPE가 충분할 때에만 중량·반복·세트 증가를 비교한다. 이 계산은 APK의 **Shadow 비교 화면**에만 적용하며, 저장된 프로그램은 바꾸지 않는다.

### 기존 기록과 새로운 후보를 같은 e1RM 곡선으로 비교

앱에 이미 저장된 비선형 반복 곡선을 `q(n)`이라고 한다. 최근 실제 세트에서 중량 `L0`, 반복 `r0`, RPE에서 추정한 남은 반복 `s`를 얻었다면 `기존 e1RM= L0/q(r0+s)`이다.

다음 후보 `c`의 무게와 반복을 각각 `Lc, rc`로 놓고, 목표 RIR을 고정해 `필요 e1RM = Lc/q(rc+목표RIR)`을 구한다. 이렇게 해야 중량을 올린 후보와 반복을 늘린 후보를 같은 기준으로 비교할 수 있다. **후보마다 추정 RIR을 다시 끼워 넣으면 모든 후보의 e1RM이 같아지는 계산상의 함정**이 있어, 목표 RIR은 고정한다.

`e1RM 차이 = abs(ln(필요 e1RM / 기존 e1RM))`이다. 이 값이 작은 후보일수록 과거 실제 수행능력과 가깝다. 단, e1RM 차이만으로 선택하지 않고 **목표 RIR 이탈과 필요한 세트 수 부족**도 함께 계산한다.

### 1순위를 정하는 규칙

1. 장비 간격이 맞지 않거나, 실제 완료 세트의 RPE가 불충분하면 해당 증가 후보를 제외한다.
2. 후보마다 `목표 RIR 이탈² + e1RM 로그 차이 + 세트 부족 비율`을 계산한다.
3. 과거 RPE에서 가능한 여러 RIR 시나리오를 적용하고, 각 시나리오에서 최선의 후보와의 점수 차이(후회)를 구한다.
4. **가장 불리한 시나리오의 후회가 최소인 후보**를 우선한다. 동점이면 확률가중 평균점수 → 명목 훈련량 증가폭 → 변경 개수 순서로 판정한다.
5. 세트 증가에는 더 많은 세트가 필요하다는 근거가 있어야 한다. 같은 중량과 반복의 세트 수만 늘리면 첫 세트의 e1RM은 변하지 않기 때문이다.

**중요한 범위:** 이번 APK는 스쿼트에 대해 하나의 잠정 순위를 보여준다. 1주차는 과거 실제 기록을 사용하고, 이후 다시 계획을 생성할 때에는 그 시점까지 완료한 최신 실제 기록을 사용한다. **운동 직후 자동으로 2~4주차를 고쳐 쓰는 기능은 이번 Gate F에 포함되지 않았다.** 다른 운동에 수식을 무검증 복사하지 않는다.

---

# B-Coach Gate F — Confirmed RPE + canonical curve e1RM progression ranking

**Date:** 2026-11-12  
**Scope:** back squat experimental, read-only comparison in the APK.  
**Status:** SHADOW ONLY / NOT APPROVED FOR WRITING WORKOUT PRESCRIPTIONS.  
**Based on:** Gate A–E branches, B-Coach v1.2 design and CSV audit; no published adaptation constants are inferred.

## User-requested behavior

The user does **not** have to choose LOAD_FIRST / REPS_FIRST / SETS_FIRST in AUTO. One deterministic ranking rule compares candidate changes of load, repetitions, and set count. **Merely increasing load receives zero positive credit.** Progression candidates enter the comparison only after comparable confirmed work and usable RPE evidence; a high RPE or inconsistent working sets closes that path conservatively.

Week 1 uses confirmed historical records available **as of** generation. On later generation, the most recent **actually confirmed** record at that time can become the new anchor (e.g., a completed week-1 session). Gate F does **not** automatically rewrite week 2–4 on completion, nor independently prove that a historical session was linked to a prescribed week; that requires a later program-link/replanning gate.

## Existing canonical nonlinear curve — do not replace

Let `q_theta(n)` be the existing canonical nonlinear, monotone PCHIP relative-load curve as resolved with the stored personal theta. Among the most heavily loaded confirmed sets in the latest real session, the **highest reported RPE** (with later set index resolving ties) supplies the conservative reference; this avoids using an optimistic earlier work set while still **not** predicting next-session set fatigue. For anchor performed load `L0`, performed repetitions `r0`, and discrete anchor RIR scenario `s` from the existing RPE/RIR policy:

```
E_observed(s) = L0 / q_theta(r0 + s)
```

This is the **anchor-observed curve-implied e1RM**, *not* the latest Bayesian posterior median and *not* a probability of future success. Do not substitute Epley, a linear rep equation or an unreviewed coefficient.

For candidate `c=(L_c,r_c,S_c)` and a single declared target effort `r*= (targetRirMin + targetRirMax)/2`:

```
E_required(c) = L_c / q_theta(r_c + r*)
D_e1RM(c,s) = abs(ln(E_required(c) / E_observed(s)))
```

This compares candidate load **and candidate repetitions on the same curve against the observed historic capability**. Importantly, *using each candidate's projected RIR in the required-e1RM denominator instead* would trivially reproduce the same anchor e1RM for every candidate (circular, unable to rank). Holding target RIR fixed avoids that error.

For each anchor RIR scenario `s`, the existing Gate B curve projects:

```
q_theta(r_c + RIR_projected(c,s))
    = q_theta(r0 + s) * (L_c / L0)
```

Do not extrapolate outside the supported repetitions/relative-load interval; unsupported scenarios are not assigned zero cost or fictitious successful reps.

## One fixed comparison rule (no arbitrary load reward)

```
width = max(1, targetRirMax - targetRirMin)
RIR_loss(c,s) = (distance(RIR_projected(c,s), [targetRirMin,targetRirMax]) / width)^2

dose_loss(c) =
  if an explicit structural targetSets exists:
      max(0, targetSets - S_c) / targetSets
  else: 0 (and SETS_UP_ONE is ineligible)

C(c,s) = RIR_loss(c,s) + D_e1RM(c,s) + dose_loss(c)

best(s) = min over eligible candidates a of C(a,s)
J(c) = max over anchor RIR scenarios s [ C(c,s) - best(s) ]

WINNER = eligible candidate with minimum:
  1. J(c)                                     (minimax regret)
  2. sum_s probability(s) * C(c,s)             (tie-break)
  3. max(0, nominalVolumeLoadDelta(c))         (tie-break)
  4. changedLeverCount(c), then stable action ID (deterministic)
```

All three terms in `C` are explicitly defined dimensionless costs with coefficient **1** (a versioned **engineering assumption**, not a clinically validated ratio of benefits or fatigue). The positive volume increase is a *tie-breaker*, never a reward for adding weight. Candidate ranking remains a diagnostic experiment until prospective validation.

### Candidates in this reproducible first slice

| Action | Load | Reps | Sets |
| --- | --- | --- | --- |
| HOLD | L0 | r0 | S0 |
| LOAD_UP_ONE_STEP | L0 + 2 * plateOneSideKg | r0 | S0 |
| REPS_UP_ONE | L0 | r0 + 1 | S0 |
| SETS_UP_ONE | L0 | r0 | S0 + 1 |

Additional load/down/composite options stay in Gate B exploratory data but are **not** part of this four-action fixed selector. The original Gate C three experimental coefficient policies are retained as a separate **legacy sensitivity comparison** in the screen; Gate F is the new primary *single-policy* ranking.

### Evidence / hard gating

- At least one real confirmed anchor set must be present and all RIR scenarios used must be canonical-curve-supported; no RPE means no invented winner.
- For **advancement**, the latest observed confirmed working sets must have the anchor's same load and repetitions, non-null valid RPE in the existing reader, and `observedRpe <= 10 - targetRirMin`. This is an explicit **experimental evidence gate**, not physiological proof that a next session will succeed.
- A detected mismatch between recent weight gaps and configured one-plate-each-side barbell steps makes `LOAD_UP_ONE_STEP` ineligible pending user review. It does **not** silently rewrite equipment settings.
- `SETS_UP_ONE` is eligible only when the B program's **structural first occurrence** specifies more sets than actually observed. This is **not** an independently validated muscle-dose need. All later sets remain unmodeled for fatigue and completion.
- On uncertain/incomplete inputs, HOLD is the only provisional choice when an observed reference is evaluable, otherwise no choice is emitted.
- This is **not** automatic program mutation, not training-effect prediction, not a future-day readiness model and not a substitute for the v1.2 full-posterior eight-axis planner.

## Worked interpretation for the athlete's logged squat

Observed: 140 kg × 5 × 3, reported set RPE 7.5 / 8 / 8. One-side plate setting 1.5 kg implies 3 kg on the bar. The recent recording history includes 2.5 kg gaps, so the load-step option must be marked for review and cannot be an automatic change.

- 140×5×3: baseline; curve anchor.
- 143×5×3: examine fixed-target RIR required e1RM but **exclude** from eligible ranking when configured grid conflicts with actual observations.
- 140×6×3: compare canonical curve-implied e1RM requirement against actual history and the projected RIR target fit.
- 140×5×4: same first-set e1RM requirement as HOLD; only a credible **set-dose shortfall** could justify an extra set, not e1RM itself.

This model computes scores on-device, **not** an approved final kg recommendation. The actual winner must be read from that APK's computed results, not assumed in this document.

## Testing and release conditions

Focused Robolectric tests verify four candidates, canonical nonlinear e1RM matching, no reward for unsupported set increases, equipment-grid conflict, missing RPE, excessively high later-set RPE, mixed work-set evidence, time-as-of identity, reproducibility, and persistence purity. The CI workflow compiles, runs focused tests, makes existing-certificate signed debug and optimized performance APK artifacts, and verifies signature/package. Full 4-week load/reps/sets auto-replanning and different exercise-specific curves remain **not implemented in Gate F**.
