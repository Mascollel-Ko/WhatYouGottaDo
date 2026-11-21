# B-Coach inherited core → badminton task transfer audit (2026-11-21)

## Problem and correction

The original `ex_d5bdffe1` (데드버그) was registered as a trunk anti-extension exercise in canonical movement/core relations but also inherited objective-level `DECELERATION/SUPPORTIVE` and `FOOTWORK/SUPPORTIVE`. The inference was based on inherited skill tag `CHANGE_OF_DIRECTION` despite `COURT_MOVEMENT=NONE`. It also had the incorrect exercise bootstrap family `deadlift` (now corrected to `데드버그`).

Corrections are limited and traceable:
- `BADMINTON_OBJECTIVE_0201/0202`: `NONE`, retaining the objective rows and canonical relation counts.
- Dead-bug runtime transfer and legacy skill tags: no specific badminton transfer; `CORE_BRACING` and `HIP_CONTROL` remain core/stability context.
- Canonical, v2 authority and integrity-manifest hashes updated.
- The canonical direct-core evidence remains `CoreClass.DIRECT`, target `BRACING`. The existing approved anti-extension and dynamic-trunk-stabilization movement relations remain untouched.
- Ordinary recorded badminton sessions (`SPORT_SESSION`, `FATIGUE_ONLY`) remain valid history/context but cannot be automatically selected as program exercise prescriptions.

## Related inherited links requiring semantic review

This is a bounded candidate audit, **not** a claim that all 16 relations are disproven. Their transferred objective specificity exceeds what the family names alone establish. Until exercise-specific evidence/protocol validation, do not present these as demonstrated gains in actual sport technique.

| Exercise | stableKey | Current inherited task relations | Notes |
| --- | --- | --- | --- |
| 밴드 팔로프 프레스 | `band_pallof_press` | DECELERATION + FOOTWORK, SUPPORTIVE | Anti-rotation ability is not a measured braking/footwork outcome |
| 케이블 팔로프 프레스 | `cable_pallof_press` | DECELERATION + FOOTWORK, SUPPORTIVE | Same issue; prior separate approval requires review |
| 버드독 | `ex_28347c1f` | DECELERATION + FOOTWORK, SUPPORTIVE | Contralateral control, not directional braking |
| 할로우 바디 홀드 | `ex_2a826c82` | DECELERATION + FOOTWORK, SUPPORTIVE | Static trunk control, not movement skill |
| 터키시 겟업 | `ex_91f6958d` | DECELERATION + FOOTWORK, SUPPORTIVE | Multiplanar stability; transfer is not established by label |
| 플랭크 | `ex_a44ae2ca` | DECELERATION + FOOTWORK, SUPPORTIVE | Anti-extension, not specific deceleration |
| 코펜하겐 플랭크 | `ex_a8385c4a` | DECELERATION + FOOTWORK, SUPPORTIVE | Adductor/hip support may be relevant; objective-specific effect unverified |
| 사이드 플랭크 | `ex_f6d43398` | DECELERATION + FOOTWORK, SUPPORTIVE | Lateral trunk stability, not direct footwork |

These are the **16 remaining** positive objective-level relations detected in the reviewed anti-rotation/core/stability-family query; dead-bug's two positive relations were removed separately. A future audit should adjudicate each relation with approved evidence and distinguish general supporting capacity from directly practiced badminton objectives. Do not silently rewrite the 16 rows or invent causal sport-performance effect sizes.

## Validation requirements

1. Canonical manifest hash validation, source authority synchronization and count parity.
2. Exact stableKey regression for dead-bug movement, core, and no scored footwork/deceleration dose.
3. Confirmed recreational badminton history still influences recovery/context and never appears as `reps × sets` in new B-Coach workout suggestions.
4. Candidate A/B/C must differ by user-visible exercise/weekly schedule/set prescription; no cosmetic duplicates.
5. Added same-exercise 2+2 split candidate must preserve four weekly sets and pass all time/recovery/locked-item constraints.
6. Android small-display 3-button and gesture-navigation insets tested on-device; Compose-only display checks are insufficient.
