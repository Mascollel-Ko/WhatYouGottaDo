# Metadata Gap For v0.3.6 Program Generation

## Current Inputs
Program generation can use activityKind, planningEligibility, equipment, movement pattern, category, transfer roles, fatigue cost, and metadata confidence.

## v0.3.4.3 Change
Hidden exercises are excluded from generation through `Exercise.isProgramSelectableExercise()`.

## Candidate Future Fields
Keep as report-only for now:
- difficultyLevel
- sessionPlacement
- substitutionGroup
- programRole
- progressionPriority
- contraindicationTags

## Reason Not Added Now
These fields need program design rules. Adding schema now would create unused metadata debt.