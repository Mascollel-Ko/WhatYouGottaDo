# Physical-quality coverage audit

Generated from canonical runtime metadata and `physical_quality_relations.csv`. This is an audit artifact; it is not a planner or Needs Engine decision input.

## Ownership coverage

| Owner | Count |
|---|---:|
| `GENERAL_QUALITY_RELATION` | 172 |
| `CORE_LAYER` | 41 |
| `SPORT_TASK_LAYER` | 17 |
| `RECOVERY_PREHAB_LAYER` | 2 |
| `INTENTIONALLY_UNRESOLVED` | 2 |

PROGRAM_SELECTABLE total: **234**

## Production relation counts

| Quality | Relations |
|---|---:|
| `STRENGTH` | 72 |
| `HYPERTROPHY` | 135 |
| `POWER` | 16 |
| `RAPID_FORCE_PRODUCTION` | 12 |
| `REACTIVE_STRENGTH_SSC` | 7 |
| `MUSCULAR_ENDURANCE` | 7 |
| `CARDIORESPIRATORY_FITNESS` | 8 |
| `MOBILITY_ROM` | 2 |

All production relation rows have `reviewStatus=PASS` and `prescriptionDependent=YES`. Qualifiers use the typed atomic region/mode vocabulary; unresolved candidates remain in the queue with explicit reasons.
