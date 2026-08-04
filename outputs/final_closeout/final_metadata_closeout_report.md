# Final Exercise Metadata Closeout

## Scope

- Baseline: `9c1c83c957593931dfe86f7ad01267006501c589` (verified `origin/main`)
- Artifact-only: no production Kotlin, Room, seed, UI, analysis, backup, or user-data edits
- Prior 224-row workbook preserved unchanged
- New output directory: `outputs/final_closeout/`

## Final Identity Result

- Source canonical identities: 224
- Retained selectable identities: 208
- Retained canonical generic history identities: 16 (non-selectable)
- New equipment-specific identities: 33
- Final selectable canonical identities: 241
- Legacy generic alias identities: 1
- Deprecated legacy aliases: 24
- DROP candidates: 5
- Backup-only custom identities: 4

The 16 ambiguous current stableKeys remain history-only generic identities. All 33 concrete equipment variants receive new stableKeys. No ambiguous history is reassigned. `ex_f892893e` remains a legacy generic calf alias and does not select a concrete calf variant.

Retained canonical generic stableKeys:

- `ex_bd072cd`
- `ex_8e51640a`
- `ex_eaea872c`
- `ex_d9084b5e`
- `ex_e159d15a`
- `ex_516f4456`
- `ex_7176cbee`
- `ex_8e18b02a`
- `ex_99728d25`
- `ex_a1fc4533`
- `ex_a9e8859c`
- `ex_ac7df636`
- `ex_d20b7487`
- `ex_dd2f732e`
- `ex_e994008a`
- `single_leg_rdl`

Deprecated legacy alias stableKeys (24):

- `ex_201f6426` -> `single_leg_rdl`
- `ex_26ac0c19` -> `med_ball_overhead_slam`
- `ex_5715d6ca` -> `single_leg_hip_bridge`
- `ex_66e8c8c2` -> `half_kneeling_single_arm_dumbbell_press`
- `ex_8354acd` -> `vipr_chop`
- `ex_8380d7fe` -> `half_kneeling_single_arm_dumbbell_press`
- `ex_885b629` -> `single_leg_rdl`
- `ex_8e1b313e` -> `half_kneeling_single_arm_dumbbell_press`
- `ex_9523db82` -> `dumbbell_romanian_deadlift`
- `ex_bb728af2` -> `ex_e2efd0fe`
- `ex_c821775c` -> `ex_a12de111`
- `ex_d2bb7946` -> `barbell_romanian_deadlift`
- `ex_d634055c` -> `single_leg_hip_bridge`
- `ex_d79824d2` -> `half_kneeling_single_arm_kettlebell_press`
- `ex_e0759156` -> `half_kneeling_single_arm_kettlebell_press`
- `ex_f892893e` -> `ex_bd072cd`
- `imported_6코너_섀도우_풋워크` -> `ex_33841b88`
- `imported_래터럴_바운드` -> `lateral_bound_continuous`
- `imported_싱글_레그_홉_앤_스틱` -> `ex_314df428`
- `imported_싱글_레그_rdl` -> `single_leg_rdl`
- `landmine_rainbow` -> `landmine_rotation`
- `med_ball_side_throw` -> `medicine_ball_rotational_throw`
- `medicine_ball_side_slam` -> `med_ball_rotational_slam`
- `vipr_shovel_scoop` -> `vipr_rotational_lift`

## Canonical Taxonomy Decisions

- MovementPattern authoritative terms restored: `SQUAT`, `HINGE`, `LUNGE`, `GAIT`, `TRUNK_ROTATION`.
- MovementEvent authoritative term restored: `REBOUND`.
- Repository-era terms remain deprecated aliases only: `KNEE_DOMINANT_SQUAT`, `HIP_HINGE`, `SPLIT_STANCE_LUNGE`, `LOCOMOTION`, `ROTATION`, `REACTIVE_BOUNCE`.
- JointAction remains joint-qualified and atomic. JointComplex is derived only through `JointActionRef.jointComplexCode`; no compound `HIP_FLEXION_EXTENSION` code exists.
- KineticChain Option B: OPEN 94, CLOSED 71, authoritatively omitted 76. No MIXED_CHAIN or role field is introduced.
- Stability/Mobility levels are NONE/LOW/MODERATE/HIGH. NONE is explicit reviewed absence; blank has the separate unreviewed/not-applicable/unavailable meaning. DROP placeholders receive neither value.

## Equipment Identity Closeout

The approved Interpretation B is applied. Equipment that changes mechanics, posture, resistance, or recording identity creates a separate canonical exercise. `ALL_REQUIRED` is used only for simultaneous multi-item requirements; `ANY_OF` is absent after identity splitting. Bodyweight is represented by no EquipmentRef.

New stableKeys (33):

- `standing_bodyweight_calf_raise`
- `standing_dumbbell_calf_raise`
- `standing_calf_raise_machine`
- `dumbbell_chest_supported_row`
- `chest_supported_row_machine`
- `cable_hip_adduction`
- `hip_adduction_machine`
- `one_arm_suspension_trainer_row`
- `one_arm_gymnastic_ring_row`
- `suspension_trainer_inverted_row`
- `gymnastic_ring_inverted_row`
- `dumbbell_spider_curl`
- `ez_bar_spider_curl`
- `cable_rear_delt_fly`
- `dumbbell_rear_delt_fly`
- `barbell_reverse_wrist_curl`
- `dumbbell_reverse_wrist_curl`
- `cable_pallof_press`
- `band_pallof_press`
- `dumbbell_farmer_carry`
- `kettlebell_farmer_carry`
- `cable_overhead_triceps_extension`
- `dumbbell_overhead_triceps_extension`
- `dumbbell_goblet_squat`
- `kettlebell_goblet_squat`
- `dumbbell_lying_triceps_extension`
- `ez_bar_lying_triceps_extension`
- `barbell_reverse_curl`
- `ez_bar_reverse_curl`
- `dumbbell_preacher_curl`
- `ez_bar_preacher_curl`
- `dumbbell_single_leg_rdl`
- `kettlebell_single_leg_rdl`

## Analysis Preservation

- Muscle identity rows: 241; inherited/preserved relation count: 797.
- Connective-tissue identity rows: 241; inherited/preserved load-unit count: 3434. The source authority SHA-256 remains `a700c438045868ca2029f452d731934f3266223759552eef4caa64553c158294`.
- OFI/fatigue identity rows: 241; inherited/preserved relation count: 3913.
- Strength targets: 5; explicit sparse proxy relations: 24. Absence remains authoritative.
- Directed progression relations: 0. The 177 descriptive progression-group tags were not promoted into edges.

## Tissue / Movement Review Flags

230 final identities have non-blocking joint-complex review flags. They are reported without automatic correction. Stabilizing/load-transfer-only tissue complexes (cervical spine, pelvic ring, foot, hand/grip) are distinguished from probable movement-anatomy omissions and movement-only complexes.

Flag instances: 204 stabilizing/load-transfer exceptions, 592 probable movement-anatomy omissions, and 19 movement-only/no-tissue-load flags. One identity may carry more than one flag.

## Validation

- PASS: 33
- REVIEW_REQUIRED: 1
- FAIL: 0
- Workbook sheets: 19
- Rendered sheet previews: 19
- CSV shape failures: 0

Excel limits worksheet names to 31 characters. The requested logical sheet `connective_tissue_joint_consistency` is therefore stored physically as `connective_tissue_joint_consist`; the full logical name remains in the title row and this report.

## Unresolved Items

No unresolved equipment, stableKey, taxonomy, or identity decision remains. Tissue/movement disagreements stay explicit independent-review flags and do not mutate approved tissue loads or movement mappings.

## Commands Executed

- `git fetch --all --tags --prune`
- source workbook import and 12-sheet render through `@oai/artifact-tool`
- full CSV/source hashing and reconciliation script
- 19-sheet workbook generation, render, XLSX re-import, formula/error scan, and CSV shape validation
- path-scoped Git production-diff checks

## Authoritative Inputs Inspected

- `outputs/WhatYouGottaDo_운동별_메타데이터_정본조정안.xlsx` (SHA-256 `4d73cee66a2f853f3dc68a11d2dd20fdfe8ff06cc860c7540986ab696f551dda`)
- `outputs/canonical_exercise_inventory_reconciliation.csv`
- `outputs/legacy_alias_to_canonical_mapping.csv`
- `outputs/drop_candidates_review.csv`
- `outputs/backup_only_custom_exercises.csv`
- `outputs/equipment_source_conflict_migration.csv` and `outputs/equipment_requirement_schema.md`
- `outputs/kinetic_chain_schema_decision.md`
- `outputs/metadata_registry_delta.csv` and `outputs/metadata_registry_delta.md`
- `outputs/metadata_mapping_reconciliation_row_audit.csv` and `outputs/metadata_mapping_reconciliation_validation.csv`
- `app/src/main/assets/training_settings_seed.csv` and `app/src/main/assets/exercise_legacy_import_map.csv`
- `app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`
- `app/src/main/assets/metadata/analysis_contract_baseline_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_index_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_exercise_load_unit_authority_v1.csv`
- `app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_joint_complexes_v1.csv`
- `app/src/main/assets/metadata/strength_proxy_prior_v1/strength_target_refs_v1.csv`
- `app/src/main/assets/metadata/strength_proxy_prior_v1/strength_proxy_relations_v1.csv`
- Runtime contract and registry implementations under `app/src/main/java/com/training/trackplanner/data` and `analysis` were read for consistency only.

## Files

- `WhatYouGottaDo_운동별_메타데이터_최종정본.xlsx`
- `canonical_exercise_inventory_final.csv`
- `equipment_variant_split_plan.csv`
- `equipment_requirement_groups_final.csv`
- `movement_registry_final.csv`
- `muscle_identity_consistency.csv`
- `connective_tissue_joint_consistency.csv`
- `ofi_identity_consistency.csv`
- `strength_proxy_sparse_subset.csv`
- `progression_sparse_subset.csv`
- `final_metadata_validation.csv`
- `final_metadata_closeout_report.md`
