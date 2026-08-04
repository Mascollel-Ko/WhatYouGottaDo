# Badminton transfer relation normalization v1

Status: ARTIFACT_ONLY
Canonical inventory: `outputs/final_closeout/canonical_exercise_inventory_final.csv`

## Layer boundary

1. **Source exercise metadata** stores transfer level, transfer type codes, skill target codes, physical quality codes, court movement codes, confidence, and provenance by canonical `exerciseStableKey`.
2. **Runtime mapper** (`BadmintonTransferMetadataMapper`) combines effective runtime metadata and existing compatibility fields. This task does not change it.
3. **Derived analysis/display axes** are `DECELERATION_LANDING`, `UNILATERAL_STABILITY`, `LATERAL_MOVEMENT`, `ROTATION_CONTROL`, `RACKET_SUPPORT`, `AEROBIC_FOOTWORK`, and `LOW_FATIGUE_CONTROL`. They are outputs, not source taxonomy replacements.

Canonical codes remain analysis keys. Korean and English display names are separate localization fields; display text must never become a join key.

## Identity normalization

- 208 retained canonical identities preserve populated source metadata.
- 33 equipment-specific identities inherit the reviewed broad transfer meaning with explicit source lineage.
- 21 legacy alias routes exist only in the migration artifact.
- 16 historical generic source identities remain non-selectable and are not duplicated into the authority registry; 4 older aliases that targeted those generics remain lineage-only rows.

## Normalization-ready conceptual tables

Reference tables: `BadmintonTransferLevelRef`, `BadmintonTransferTypeRef`, `BadmintonSkillTargetRef`, `BadmintonPhysicalQualityRef`, and `CourtMovementTypeRef`.

Exercise relation tables: `ExerciseBadmintonTransferLevelRelation`, `ExerciseBadmintonTransferTypeRelation`, `ExerciseBadmintonSkillTargetRelation`, `ExerciseBadmintonPhysicalQualityRelation`, and `ExerciseCourtMovementTypeRelation` keyed by canonical stableKey and code with provenance/review status.

This is a review design only. No Room entity, production registry, seed asset, calculation, UI, or migration is implemented here.
