# TrainingRole / ProgramSlotCapability split closeout v1

## Scope

This closeout separates intrinsic exercise training meaning from ProgramBuilder placement capability. It migrates the approved 26 exact stableKeys mechanically and does not broaden ProgramBuilder eligibility or redesign selection policy.

## Result

- Legacy baseline: 26 unique stableKeys.
- ProgramSlotCapability: 26 rows, no missing, additional, changed, or duplicate mappings.
- TrainingRole: 19 rows: STRENGTH 5, POWER 6, PLYOMETRIC 4, STABILITY 4.
- REVIEW_REQUIRED: 7 intrinsic meanings (ACCESSORY 5, SPEED_REACTIVE 2) remain absent rather than inferred.
- Room: schema 26 to 27, transactional normalized relation tables, no destructive migration.
- Backup: format 10 / restore schema 9; new normalized columns; old `training_role` import remains compatibility-only.
- ProgramBuilder: consumes ProgramSlotCapability; TrainingRole does not grant main, secondary, or accessory placement.
- Analysis: does not consume the new ProgramSlotCapability relation.

## Validation

- PASS: 50
- REVIEW_REQUIRED: 1
- FAIL: 0
- Focused relation and backup tests: PASS.
- Focused ProgramBuilder and directly affected analysis tests: PASS.
- Frozen analysis contract parity: PASS; baseline and production contract bytes unchanged.
- Full unit tests: PASS (1,136 tests, 0 failures).
- `compileDebugKotlin`: PASS.
- `compileDebugAndroidTestKotlin`: PASS; on-device migration execution remains CI/device territory.
- Protocol validation: PASS (8 families, 33 protocols).

## Deferred

- Broad TrainingRole review across the full exercise catalogue.
- Full ProgramSlotCapability taxonomy and assignments beyond the approved 26.
- Full ProgramBuilder metadata and policy redesign.
