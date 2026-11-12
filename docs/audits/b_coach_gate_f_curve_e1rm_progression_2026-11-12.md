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
