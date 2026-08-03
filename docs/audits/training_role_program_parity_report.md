# Legacy training-role program parity report

- Baseline: `40615fab9c7ff892b0e48dd5a244eeb77e7cf2ee`
- Change under review: restore the 26 approved exact-stableKey legacy `trainingRole` rows and remove manufactured defaults.

## Classification

| Area | Result | Classification |
|---|---|---|
| Program block and slot construction | Existing generators compile and focused builder tests pass | `EXPECTED_TRAINING_ROLE_POLICY_RESTORATION` |
| Candidate stableKeys and main/accessory choice | Explicit legacy roles are available only for the approved 26 keys | `EXPECTED_TRAINING_ROLE_POLICY_RESTORATION` |
| Replacement, duplicate suppression, required/excluded exercise rules | No policy code changed | `PREEXISTING_BEHAVIOR` |
| 3-through-7-day schedule and other-week reuse | Existing timing parity suite passes | `PREEXISTING_BEHAVIOR` |
| Session timing, `defaultRestSeconds`, set reduction, and shortage handling | No timing or prescription code changed; existing parity suite passes | `PREEXISTING_BEHAVIOR` |

No `UNRELATED_REGRESSION` was observed. This restoration does not promote legacy roles into final `ProgramRoleRef`; that mapping remains Phase 2B work.
