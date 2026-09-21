# Physical-quality coverage audit

Generated from canonical runtime metadata, `physical_quality_relations.csv`, and the orthogonal relation-layer review queue. The current production asset contains 279 reviewed relation rows. This is an audit artifact; it is not a planner or Needs Engine decision input.

## Multi-layer membership coverage

`PROGRAM_SELECTABLE` total: **234**

| Relation layer | Membership count |
|---|---:|
| General quality | 180 |
| Core | 206 |
| Sport task | 100 |
| Recovery/Prehab | 6 |

Membership is orthogonal. `primarySemanticLayer` is display/audit context only and does not suppress another layer.

| Overlap | Count |
|---|---:|
| General + Core | 154 |
| General + Sport task | 57 |
| Core + Sport task | 97 |
| General + Core + Sport task | 54 |
| No recognized semantic relation | 0 |

## Production relation counts

| Quality | Relations |
|---|---:|
| `STRENGTH` | 86 |
| `HYPERTROPHY` | 139 |
| `POWER` | 16 |
| `RAPID_FORCE_PRODUCTION` | 13 |
| `REACTIVE_STRENGTH_SSC` | 8 |
| `MUSCULAR_ENDURANCE` | 7 |
| `CARDIORESPIRATORY_FITNESS` | 8 |
| `MOBILITY_ROM` | 2 |

All production relation rows have `reviewStatus=PASS` and `prescriptionDependent=YES`. Qualifiers use the typed atomic region/mode vocabulary.

## Reviewed examples

- `kettlebell_goblet_squat` retains Core membership and now has general `STRENGTH` (supportive) and `HYPERTROPHY` (direct) capability relations.
- `ex_6232f4bc` (box step-up) retains Core membership and has a supportive unilateral strength relation.
- `ex_f332aeab` (wall drive) retains Core membership and has supportive RFD/SSC capability only; no direct SSC relation is inferred.
- `ex_708e64ce` (dumbbell pullover) is promoted to a reviewed hypertrophy relation from explicit runtime volume evidence.
- The eight lunge/split-squat variants now carry reviewed direct lower-squat strength capability from their unilateral lower strength progression family.
- `ex_69a56484` (lateral step-down) and `ex_704cbf1a` (dumbbell step-up) carry direct unilateral-lower strength capability while retaining Core ownership of Core-layer semantics.
- `ex_ab468462` and `ex_a091b9fe` (leg press variants) carry direct lower-squat strength and hypertrophy capability; their intrinsic bootstrap laterality remains bilateral and unilateral respectively.
- `kettlebell_halo` remains intentionally unresolved for general quality; its low-load control evidence does not establish direct mobility ROM.

Assessment-only exercises remain excluded from production physical-quality relations. The queue records membership flags and a non-authoritative primary semantic layer for review traceability.
