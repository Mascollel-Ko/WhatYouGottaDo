# Record-based planner implementation note

Baseline: `0efe36bcbb7b49f2a3142fc253fbfbfc83514a04`.

The legacy automatic path remains `ProgramGenerationService -> ProgramSkeletonGenerator -> ProgramAutoBuilder`. Personalized generation is a separate path that produces the same editable `GeneratedProgramSkeleton` format and uses the existing save/apply flow.

## Authority and behavior

- Only confirmed sets on or before the explicit cutoff enter planning history.
- Exercises and candidates are resolved through canonical `stableKey` and typed runtime metadata. Movement coverage no longer uses display-name or substring inference.
- `SPORT_SESSION` badminton records are generic court-load context only. Canonical, directly transferred `BADMINTON_FOOTWORK` exercises are structured drills. General strength-and-conditioning exercises with supportive Objective V2 relations remain resistance training.
- All nine canonical badminton objectives are represented. A previously observed axis that disappears is a drop gap; an axis never observed is a lower-priority developmental gap.
- Strength response uses the exercise-local canonical posterior when available. Missing posterior evidence remains unknown; it does not fall back to a generic Epley estimate.
- Hypertrophy exposure uses eligibility, effort, and movement contribution rather than counting every 6-20-repetition set equally.
- OFI, Today Readiness, tissue RCV, generic court load, and exercise-local strength posterior are cutoff-aware planner inputs. They constrain horizon, frequency, density, progression, and validation.
- Progression is explicit: `ADVANCE`, `HOLD`, `REDUCE`, or `REVIEW`. Repetitions are never raised at the last load merely because the generated week number increased. A new exercise never receives an invented starting load.
- Strength style is inferred per anchor. A multi-day style is expanded only for the anchor that evidenced it.
- UI questions are returned and answered one at a time. Dismissing a question stores no answer and does not continue generation.
- The submitted personalized request is authoritative for API callers. The legacy editor's default goal/day/week controls are passed as unspecified personalized constraints, while its session-time limit remains a real availability constraint.
- Saved provenance includes the stable program key, original generation fingerprint, final saved fingerprint, and `userEditedAfterGeneration`. A later item edit updates that provenance.

## Capability-consumer matrix

| Capability | Canonical producer | Planner consumers | Observable effect |
|---|---|---|---|
| Confirmed history and cutoff | Room workout records | snapshot, every analyzer | future/unconfirmed records excluded |
| Movement coverage | runtime metadata `programSlot` | anchors, gaps, candidates, structure | typed coverage and rebalance |
| Badminton objectives | Objective Stimulus V2 catalog/calculator | state, gaps, candidates, decision trace | nine-axis drop/development gaps |
| Generic court load | `BadmintonPracticeLoadCalculator` | dose, block constraints, recovery | lower density/frequency pressure; never objective stimulus |
| Strength performance | exercise-local strength posterior history | anchor response, progression | posterior-based advance/hold/reduce/review |
| Hypertrophy stimulus | runtime eligibility + confirmed set effort | behavior, skew gaps | effective stimulus instead of raw set count |
| OFI | `DailyFatigueCalculator` | recovery constraints, decision trace | conservative planning at high fatigue |
| Readiness | `TodayReadinessEngine` | dose, horizon, prescription | frequency ceiling and load reduction/review |
| Connective tissue | tissue RCV service | prescription, constraints | blocks increases for high-contribution exercises |
| User intent | one-at-a-time questions/preferences | state, candidates, block intent | modality and goal choices alter candidate eligibility |
| Session availability | personalized constraints | weekly structure, validator/repair | 2-5 days and time-budget enforcement |
| Save identity | program stable key + SHA-256 fingerprints | backup/restore and editor mutation | durable generation/edit provenance |

## Verification

`PersonalizedPlannerParityTest` runs all 29 named v0.8 personas from raw confirmed workout history through snapshot, athlete state, gaps, block intent, candidate selection, structure, prescription, validation, and final skeleton. It compares deterministic final fingerprints; it does not treat a list of persona names as parity evidence.

`RealBackupPersonalizedPlannerE2eTest` is opt-in because the user's backup must never enter source control. Set `WGTD_REAL_BACKUP_PATH` to the external format-12 CSV. The test uses the production parse/preflight/restore route, plans at latest/4-week/8-week cutoffs, checks future-row non-leakage, perturbs generic badminton load without changing Objective V2 exposure, saves a five-week/four-day bodybuilding request, records an edit, applies it as unconfirmed planned sets, exports, and restores into a second database.

Reference oracle: offline `wgtd_planner_reference_v0.8` (`REFERENCE_PLANNER_0.8.0`). The Python files are behavioral evidence, not runtime code and not production semantic authority.
