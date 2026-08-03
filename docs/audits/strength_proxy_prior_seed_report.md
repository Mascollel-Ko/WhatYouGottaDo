# Strength proxy prior seed report

- Baseline: `40615fab9c7ff892b0e48dd5a244eeb77e7cf2ee`
- Config version: `strength-proxy-prior-1.0.0`
- Runtime status: isolated prior-only; no production strength output consumer

## Target registry

| Target | Direct anchor | Load semantics | Authority |
|---|---|---|---|
| `BENCH_PRESS` | `barbell_bench_press` | `EXTERNAL_LOAD` | legacy explicit target |
| `BACK_SQUAT` | `barbell_back_squat` | `EXTERNAL_LOAD` | legacy explicit target |
| `DEADLIFT` | `barbell_deadlift` | `EXTERNAL_LOAD` | legacy explicit target; generic target identity |
| `WEIGHTED_PULL_UP` | `ex_e41f4c2b` | `BODYWEIGHT_PLUS_ADDED_LOAD` | legacy explicit target |
| `MILITARY_PRESS` | `ex_32219f7a` | `EXTERNAL_LOAD` | product-owner semantic decision |

## MILITARY_PRESS product decision

`ex_32219f7a` currently displays as `오버헤드 프레스`. For this registry it means a standing strict barbell overhead press without intentional knee or hip drive. Existing historical records under the key are treated as this canonical movement. Push press, push jerk, and split jerk are not direct anchors and require separate stableKeys. This is explicit product policy; it does not claim the earlier metadata already documented the technique.

## Seed lineage and uncertainty

- 24 exact stableKey/target relations are seeded: five direct anchors, existing explicit legacy proxy rows whose lineage could be established, and two press bridge relations.
- Legacy non-direct coefficients are reused only as provisional specificity seeds. They are not interpreted as transfer slopes or fixed kilogram conversion factors.
- Every non-direct row is `PROVISIONAL_PRODUCT_PRIOR / TEMPORARY_APPROVED`, requires post-metadata research review, and uses broad uncertainty (`specificity concentration 2`, transfer slope SD `0.75`, residual log-SD uncertainty `0.85`).
- A proxy may link to multiple targets. `ex_32219f7a` links directly to `MILITARY_PRESS` and provisionally to `BENCH_PRESS`; this does not authorize double-counting one observation.
- Future Bayesian analysis must update a shared factor once and let target posteriors respond to that state. User-specific state belongs in a separate `UserStrengthProxyPosterior`, not exercise metadata.

## Frozen and production boundaries

The existing production target registry and proxy loading assets remain byte-for-byte unchanged:

- `strength_target_registry_v1.csv`: `8C8B02DFD7E7BB8160CCEA588B943E148A032236CF0BA11E1E7D5F05CF7FC31B`
- `strength_proxy_loadings_v1.csv`: `6236AB5BBC338A71E0411A45306C0088FB6E63040356F90B0040327ECE360196`

The four Phase 0/1 production contract Kotlin files also remain frozen. No personalised update, production estimate change, Room migration, backup change, or ProgramBuilder change is part of this seed.
