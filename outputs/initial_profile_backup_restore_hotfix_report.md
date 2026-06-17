# Initial Profile Backup / Restore Hotfix Report

## v0.3.4.4.3 Changes

- Backup still exports legacy profile keys for compatibility.
- Backup exports structured keys such as `sex`, `birthYear`, `strengthTrainingYears`, `badmintonTrainingYears`, `painAreaTags`, `avoidMovementTags`, and `primaryGoal`.
- Restore prefers structured keys.
- Restore sanitizes enum keys and numeric ranges before saving.

## Import Safety Rules

| Input | Handling |
| --- | --- |
| invalid `sex` | `UNSPECIFIED` |
| invalid `birthYear` | `null` |
| RPE outside 1-10 | `null` |
| 1-5 recovery scale outside range | `null` |
| unknown `trainingBreakCategory` | fallback from legacy `breakWeeks`, otherwise `NONE` |
| unknown `trainingBreakReason` | fallback from legacy `breakDueToPain`, otherwise `NONE` |
| unknown pain/avoid tag | dropped; legacy free text maps to `OTHER` |
| unknown `primaryGoal` | fallback from legacy `goals`, otherwise `MIXED` |

## Legacy Compatibility

- v0.3.4.4 and partial v0.3.4.4.1 backups can import without crashing.
- Free-text legacy values are not used as core fatigue inputs.
- If legacy text cannot be safely mapped, the app stores `null`, `NONE`, `OTHER`, `UNSPECIFIED`, or `MIXED`.
