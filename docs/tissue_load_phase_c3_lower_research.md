# Phase C3 Lower-Limb Multidimensional Tissue-Load Research

Status: MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL

This batch re-extracts source measurements into separate mechanical-load, temporal, measurement, and normalization identities. It does not approve claims, create production profiles, or activate runtime tissue-load calculations.

## Search and source boundary

- Search date: 2026-07-14.
- Databases: PubMed, PubMed Central, Crossref, and official publisher metadata.
- Queries were separated by tissue, load mode, temporal metric, exercise, external load, ROM, and measurement method.
- Primary studies were preferred. Search snippets, rehabilitation websites, blogs, vendor pages, and unsourced tables were not used as evidence.
- The ten existing verified sources were re-extracted. Five additional primary studies were PMID/DOI verified and publication-integrity checked.

| Source | Included use | Exclusion or comparison boundary |
|---|---|---|
| PMID 35142563 / DOI 10.1152/japplphysiol.00662.2021 | Direct Achilles strain for locomotor, heel-rise, hop, and landing conditions | Strain is not force or energy storage; no generic band |
| PMID 31193251 / DOI 10.1016/j.jshs.2016.12.005 | Patellar tendon stress, stress impulse, and stress rate metric separation | Numeric tables were not transcribed without direct table extraction; no threshold |
| PMID 21092960 / DOI 10.1016/j.jbiomech.2010.10.028 | Protocol-specific ACL strain during dynamic jump landing | Small sample and close-variant mapping; no landing-wide band |
| PMID 25059338 / DOI 10.1007/s00167-014-3190-3 | Passive inversion boundary evidence | Not an exercise or landing protocol; excluded from app claims and rubrics |
| PMID 40705768 / DOI 10.1371/journal.pone.0327973 | High-load squat PFJ, TFJ, and ankle modeled peak contact force | Elite cohort and model cannot be pooled with bodyweight-task models |

## Metric availability

- Metric extraction rows: 49.
- New multidimensional claim candidates: 30.
- Source-specific composite rows: 2; these remain non-rubric and non-profile.
- PFJ peak, impulse per event, and loading rate are independent rows. Opposite peak/impulse rankings are not treated as contradictions.
- Achilles and patellar tendon force, stress, strain, impulse, and loading rate remain distinct measurement families.
- Cumulative session exposure is only defined as a future contract: compatible per-event impulse times a valid event count. Missing count remains missing.

## Research decision matrix

| Tissue | Mechanical mode x temporal metric | Measurement | Included sources | Excluded sources | Decision | Remaining blocker |
|---|---|---|---|---|---|---|
| ACHILLES_TENDON | ENERGY_STORAGE_RELEASE x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | - | BLOCKED_MISSING_DOSE_INPUT | Energy per event and valid event count required. |
| ACHILLES_TENDON | ENERGY_STORAGE_RELEASE x PEAK | MEASURED_TENDON_ENERGY_STORAGE | - | SRC_PMID_35142563 | BLOCKED_INSUFFICIENT_EVIDENCE | Direct force-elongation energy measurement required. |
| ACHILLES_TENDON | TENSION x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | PREFLIGHT_32658037 | BLOCKED_MISSING_DOSE_INPUT | Valid event count, laterality, and no-double-counting contract required. |
| ACHILLES_TENDON | TENSION x IMPULSE_PER_EVENT | TENDON_FORCE_TIME_INTEGRAL | PREFLIGHT_32658037 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | Condition-specific impulse rubric remains missing. |
| ACHILLES_TENDON | TENSION x LOADING_RATE | MODELED_TENDON_FORCE_LOADING_RATE | PREFLIGHT_32658037 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | No loading-rate rubric. |
| ACHILLES_TENDON | TENSION x PEAK | MEASURED_TENDON_STRAIN | SRC_PMID_35142563 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| ACHILLES_TENDON | TENSION x PEAK | MODELED_TENDON_FORCE | PREFLIGHT_32658037<br>SRC_PMID_28145739 | - | DRAFT_RUBRIC_CREATED | - |
| ANKLE_DELTOID_LIGAMENT | EVERSION_STRESS x PEAK | MODELED_LIGAMENT_FORCE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated eversion-load research required. |
| ANKLE_LATERAL_LIGAMENT_COMPLEX | IMPACT_STABILIZATION x LOADING_RATE | GROUND_REACTION_FORCE_LOADING_RATE | - | - | BLOCKED_NO_VALIDATED_PROXY | Validated mapping required. |
| ANKLE_LATERAL_LIGAMENT_COMPLEX | INVERSION_STRESS x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | - | BLOCKED_MISSING_DOSE_INPUT | Per-event tissue metric and count required. |
| ANKLE_LATERAL_LIGAMENT_COMPLEX | INVERSION_STRESS x PEAK | MODELED_LIGAMENT_FORCE | SRC_PMID_25059338<br>SRC_PMID_30923576 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| ANKLE_SUBTALAR | INVERSION_STRESS x PEAK | SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY | SRC_PMID_30923576 | - | BLOCKED_NO_VALIDATED_PROXY | Validated internal-load mapping required. |
| ANKLE_SUBTALAR | ROTATIONAL_STRESS x PEAK | SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY | SRC_PMID_30923576 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| ANKLE_TALOCRURAL | COMPRESSION x PEAK | MODELED_JOINT_CONTACT_FORCE | SRC_PMID_40705768 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| ANKLE_TALOCRURAL | IMPACT_STABILIZATION x LOADING_RATE | GROUND_REACTION_FORCE_LOADING_RATE | - | - | BLOCKED_NO_VALIDATED_PROXY | Validated mapping required. |
| KNEE_ACL | ANTERIOR_TRANSLATION_STRESS x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | - | BLOCKED_MISSING_DOSE_INPUT | Event count and tissue-specific per-event metric required. |
| KNEE_ACL | ANTERIOR_TRANSLATION_STRESS x PEAK | MODELED_LIGAMENT_FORCE | SRC_PMID_10656979 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| KNEE_ACL | IMPACT_STABILIZATION x LOADING_RATE | GROUND_REACTION_FORCE_LOADING_RATE | - | SRC_PMID_21092960 | BLOCKED_NO_VALIDATED_PROXY | Validated internal mapping required. |
| KNEE_ACL | IMPACT_STABILIZATION x PEAK | MEASURED_LIGAMENT_STRAIN | SRC_PMID_21092960<br>SRC_PMID_31593498 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| KNEE_ACL | INTERNAL_ROTATION_STRESS x PEAK | EXTERNAL_JOINT_MOMENT | - | - | BLOCKED_NO_VALIDATED_PROXY | Validated tissue-specific mapping required. |
| KNEE_ACL | VALGUS_STRESS x PEAK | EXTERNAL_JOINT_MOMENT | - | - | BLOCKED_NO_VALIDATED_PROXY | Validated tissue-specific mapping required. |
| KNEE_MCL | VALGUS_STRESS x PEAK | MODELED_LIGAMENT_FORCE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Primary exercise mechanics required. |
| KNEE_PATELLOFEMORAL | COMPRESSION x CUMULATIVE_SESSION_IMPULSE | JOINT_CONTACT_FORCE_TIME_INTEGRAL | SRC_PMID_37272685 | - | BLOCKED_MISSING_DOSE_INPUT | Valid repetitions or event counts and laterality required. |
| KNEE_PATELLOFEMORAL | COMPRESSION x IMPULSE_PER_EVENT | JOINT_CONTACT_FORCE_TIME_INTEGRAL | SRC_PMID_37272685 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| KNEE_PATELLOFEMORAL | COMPRESSION x LOADING_RATE | MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | SRC_PMID_37272685 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| KNEE_PATELLOFEMORAL | COMPRESSION x PEAK | MODELED_JOINT_CONTACT_FORCE | SRC_PMID_37272685<br>SRC_PMID_11949662<br>SRC_PMID_18632195<br>SRC_PMID_40705768 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| KNEE_PCL | POSTERIOR_TRANSLATION_STRESS x PEAK | MODELED_LIGAMENT_FORCE | SRC_PMID_10656979 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| KNEE_TIBIOFEMORAL | ANTERIOR_POSTERIOR_SHEAR x IMPULSE_PER_EVENT | JOINT_CONTACT_FORCE_TIME_INTEGRAL | - | SRC_PMID_8947402 | BLOCKED_INSUFFICIENT_EVIDENCE | Force-time integral required. |
| KNEE_TIBIOFEMORAL | ANTERIOR_POSTERIOR_SHEAR x PEAK | MODELED_JOINT_CONTACT_FORCE | SRC_PMID_8947402 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| KNEE_TIBIOFEMORAL | COMPRESSION x IMPULSE_PER_EVENT | JOINT_CONTACT_FORCE_TIME_INTEGRAL | - | SRC_PMID_8947402 | BLOCKED_INSUFFICIENT_EVIDENCE | Direct force-time integral required. |
| KNEE_TIBIOFEMORAL | COMPRESSION x PEAK | MODELED_JOINT_CONTACT_FORCE | SRC_PMID_8947402<br>SRC_PMID_40705768 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| KNEE_TIBIOFEMORAL | ROTATIONAL_STRESS x PEAK | EXTERNAL_JOINT_MOMENT | - | - | BLOCKED_NO_VALIDATED_PROXY | Validated proxy mapping required. |
| PATELLAR_TENDON | TENSION x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | SRC_PMID_37847102 | BLOCKED_MISSING_DOSE_INPUT | Valid event count required. |
| PATELLAR_TENDON | TENSION x ECCENTRIC_PHASE_PEAK | MODELED_TENDON_FORCE | - | SRC_PMID_37847102 | BLOCKED_INSUFFICIENT_EVIDENCE | Phase-resolved source values required. |
| PATELLAR_TENDON | TENSION x IMPULSE_PER_EVENT | MODELED_TENDON_STRESS_TIME_INTEGRAL | SRC_PMID_31193251 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | No compatible rubric. |
| PATELLAR_TENDON | TENSION x IMPULSE_PER_EVENT | TENDON_FORCE_TIME_INTEGRAL | SRC_PMID_37847102 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| PATELLAR_TENDON | TENSION x LOADING_RATE | MODELED_TENDON_FORCE_LOADING_RATE | SRC_PMID_37847102 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| PATELLAR_TENDON | TENSION x LOADING_RATE | MODELED_TENDON_STRESS_LOADING_RATE | SRC_PMID_31193251 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | - |
| PATELLAR_TENDON | TENSION x PEAK | MEASURED_TENDON_STRAIN | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dynamic exercise-specific direct strain evidence required. |
| PATELLAR_TENDON | TENSION x PEAK | MODELED_TENDON_FORCE | SRC_PMID_37847102 | - | SOURCE_CLAIMS_CREATED_NO_RUBRIC | - |
| PATELLAR_TENDON | TENSION x PEAK | MODELED_TENDON_STRESS | SRC_PMID_31193251 | - | EVIDENCE_FOUND_BUT_NOT_COMPARABLE | Exact numeric table extraction and cross-model comparison remain blocked. |
| QUADRICEPS_TENDON | TENSION x CYCLIC_EXPOSURE | SOURCE_DEFINED_CYCLIC_EXPOSURE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x ECCENTRIC_PHASE_IMPULSE | TENDON_FORCE_TIME_INTEGRAL | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x ECCENTRIC_PHASE_PEAK | MODELED_TENDON_FORCE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x IMPULSE_PER_EVENT | TENDON_FORCE_TIME_INTEGRAL | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x LOADING_RATE | MODELED_TENDON_FORCE_LOADING_RATE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x PEAK | MEASURED_TENDON_STRAIN | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |
| QUADRICEPS_TENDON | TENSION x PEAK | MODELED_TENDON_FORCE | - | - | BLOCKED_INSUFFICIENT_EVIDENCE | Dedicated quadriceps-tendon research required. |

The tissue_research_decision_c3_v1.csv file is the authoritative row-level record and also preserves each exact search query, search date, reason, and provenance.

## C3.1 evidence and load-condition correction

C3.1 re-read six affected sources while retaining the verified 15-source registry. The ACL observations are represented as
`TENSION x PEAK x MEASURED_LIGAMENT_STRAIN`, with landing as event context and pre-contact as movement phase; no valgus,
rotation, or translation cause is inferred. The weighted heel-rise protocol used a torso vest with additional mass equal to
20% bodyweight, represented separately as 0.20 BW added load and 1.20 BW total system mass.

All 49 extraction rows, 30 candidates, 42 prior dimensions, 17 prior modes, 48 decisions, and 2 prior point rows have an
explicit row in `tissue_c3_1_correction_disposition_v1.csv`. The replacement package retains 30 corrected candidates, creates
2 exact condition anchors, creates no interval or ordering rubric, and leaves 27 material research targets blocked. The full
old/new interpretation map is rendered in `tissue_load_phase_c3_1_approval_request.md`.

## Existing 24-candidate migration

| Old candidate | Old dimension | New identity | Disposition | Preserved measurement | Removed interpretation |
|---|---|---|---|---|---|
| R1C_01_ACH_SEATED_PEAK | PEAK_TENSILE_LOAD | TENSION x PEAK / MODELED_TENDON_FORCE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_02_ACH_SINGLE_CALF_PEAK | PEAK_TENSILE_LOAD | TENSION x PEAK / MODELED_TENDON_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | No interpretation beyond the retained condition-specific partial Achilles rubric. |
| R1C_03_ACH_REPEATED_HOP_PEAK | PEAK_TENSILE_LOAD | TENSION x PEAK / MODELED_TENDON_FORCE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | No interpretation beyond the retained condition-specific partial Achilles rubric. |
| R1C_04_ACH_SINGLE_CALF_RATE | LOADING_RATE | TENSION x LOADING_RATE / MODELED_TENDON_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_05_PAT_BW_SQUAT_PEAK | PEAK_TENSILE_LOAD | TENSION x PEAK / MODELED_TENDON_FORCE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_06_PAT_BULGARIAN_PEAK | PEAK_TENSILE_LOAD | TENSION x PEAK / MODELED_TENDON_FORCE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_07A_PFJ_60_SQUAT_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_07B_PFJ_60_SQUAT_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_07C_PFJ_60_SQUAT_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RETAIN_WITH_NARROWER_CONDITION | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_08A_PFJ_FULL_SQUAT_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_08B_PFJ_FULL_SQUAT_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_08C_PFJ_FULL_SQUAT_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_09A_PFJ_BULGARIAN_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_09B_PFJ_BULGARIAN_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_09C_PFJ_BULGARIAN_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_10A_PFJ_LUNGE_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_10B_PFJ_LUNGE_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_10C_PFJ_LUNGE_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_11A_PFJ_DROP_JUMP_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_11B_PFJ_DROP_JUMP_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_11C_PFJ_DROP_JUMP_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_12A_PFJ_MAX_HOP_PEAK | PEAK_COMPRESSION | COMPRESSION x PEAK / MODELED_JOINT_CONTACT_FORCE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_12B_PFJ_MAX_HOP_IMPULSE | COMPRESSION_IMPULSE | COMPRESSION x IMPULSE_PER_EVENT / JOINT_CONTACT_FORCE_TIME_INTEGRAL | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |
| R1C_12C_PFJ_MAX_HOP_RATE | COMPRESSION_LOADING_RATE | COMPRESSION x LOADING_RATE / MODELED_JOINT_CONTACT_FORCE_LOADING_RATE | RECLASSIFY_EXACTLY | Source value, unit, tested exercise, method, and exact source conditions are preserved. | Generic band and cross-condition interpretation remain absent. |

No previous candidate disappears. The seated-calf generic LOW interpretation remains removed; Achilles peak and loading rate remain separate; the 60-degree squat remains condition-specific; Bulgarian split squat, RFESS, and split squat remain distinct canonical variants.

## Remaining blockers

- No approved external-load dose-response model exists for weighted calf transfer.
- Quadriceps-tendon exercise mechanics remain materially under-researched in this batch.
- Patellar tendon dynamic strain and phase-specific eccentric metrics remain blocked.
- PFJ and TFJ cross-model pooling remains prohibited.
- ACL/MCL/ankle external proxies remain blocked without validated tissue-specific mappings.
- Cumulative and cyclic exposure remain blocked when event count, laterality, or per-event exposure is missing.
- Upper-limb tissues remain a separate future batch.
