# Tissue Load Foundation v1

## Status

The tissue-load system is a non-production shadow foundation. It does not replace the six fatigue axes, OFI, readiness, warnings, or ProgramBuilder behavior. Missing metadata remains missing rather than numeric zero.

Current implementation status after foundation Commit 1: `FOUNDATION_PARTIAL`.

## Repository Audit

- Baseline: `25cb01448ea5926841a4b89d9738da135704059a` on latest `origin/main`.
- Canonical exercise metadata: `canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`.
- Canonical exercise count: 239.
- Canonical identity: `stableKey`; runtime lookup is stable-key first. The tissue repository accepts exact stable keys only and does not use the legacy display-name fallback.
- Existing tissue-related metadata columns: `tendonStressTags`, `ligamentJointStabilityStressTags`, `jointImpactStressTags`, and scalar `jointTendonImpactStressLevel`.
- Legacy tissue-token count: 45 distinct non-`NONE` tokens (15 tendon, 20 ligament/joint stability, 9 impact, plus the scalar level is not a migration token).
- Record dose fields currently available: entry date/exercise/RPE and confirmed set reps, weight, seconds, set RPE, and rest metadata.
- Directly usable dose bases: external-load repetitions, effective-bodyweight repetitions, and duration holds. Session duration/RPE is derivable only when a record is explicitly a duration session.
- Unavailable event inputs: landing, direction-change, jump, throw, stroke, and sport-event counts; movement velocity; ROM; surface; footwear; anticipated condition; landing technique.
- Performed side is not stored. Lead/trail and dominant/non-dominant context are not stored.
- Existing bodyweight authority: `BodyweightEffectiveLoadCalculator`.
- Existing plank/side-plank duration authority: `DurationHoldLoadCalculator`.
- Existing record RPE resolution: confirmed set RPE, falling back to entry RPE.
- Existing fatigue decay ownership: `FatigueDecayModel` and readiness `ResidualFatigueCalculator`. Tissue v1 does not reuse those curves as tissue recovery evidence.
- Existing legacy metadata fallback: runtime metadata may use an exact unique display-name fallback for old database rows. Tissue v1 intentionally does not.
- Backup/import/export currently persists workout entry/set fields and runtime exercise metadata, but has no tissue-specific record inputs.

## Metadata Architecture

- `canonical_tissue_catalog_v1.csv`: 61 distinct structures: 20 joints/functional complexes, 23 tendons, 16 ligaments/capsuloligamentous structures, and 2 fascia structures.
- `exercise_tissue_scope_manifest_v1.csv`: 14,579 stableKey/tissue rows. Every row is `NOT_YET_EVALUATED`, non-production, and has no fabricated human approval.
- Four long-form profile files: one row per stableKey/tissue/dimension/reference condition when future evidence review supports a row. They are intentionally empty in the foundation.
- `tissue_load_band_rubric_v1.csv`: schema only. No global thresholds, automatic quartiles, or invented anchors are present.
- `tissue_metadata_audit_manifest_v1.csv`: deterministic hashes, counts, validation status, and decision. It cannot grant human approval or production eligibility.

Catalog anatomy identity and exercise-specific load claims use separate evidence burdens. Catalog rows are currently `UNVERIFIED` and contain no exercise-load claims. Any future load, force, strain, stress, injury, or risk assertion must use the exercise-load evidence and claim pipeline.

## Invariants

- Joint, tendon, ligament, and fascia identities remain separate.
- Patellofemoral and tibiofemoral structures remain separate.
- ACL, PCL, MCL, and LCL remain separate.
- Plantar fascia is fascia, not tendon.
- The scapulothoracic complex is marked as a functional articulation, not a true synovial joint.
- No sum-preservation relationship exists with `jointTendonImpactFatigue`.
- Legacy tags can only seed future review candidates; they cannot assign bands.
- `NOT_YET_EVALUATED`, blocked, conflicting, missing-input, and unresolved-side states cannot become zero.
- AI preparation cannot fill or imply human approval.

## Network Capability

On 2026-07-13, network permission was explicitly requested and approved. The elevated NCBI preflight parsed PMID 32658037 and title `Exercise Progression to Incrementally Load the Achilles Tendon.` Crossref returned HTTP 404 for DOI `10.2519/jospt.2020.9406` on both encoded and literal bounded attempts.

Capability status: `PARTIAL_SOURCE_VERIFICATION_AVAILABLE`.

No real source is promoted by this result. Actual source rows remain `UNVERIFIED` until the complete source gate passes.

## File Responsibility Map

- `TissueMetadataModels.kt`: tissue classes, dimensions, evaluation states, long-form profile/scope/rubric/audit models.
- `TissueMetadataParser.kt`: deterministic RFC-style CSV parsing and typed metadata parsing.
- `TissueMetadataValidator.kt`: anatomy, scope, profile, approval, and semantic-hash invariants.
- `TissueLoadProfileRepository.kt`: exact stable-key profile and rubric lookup.
- `generate_tissue_foundation_assets.ps1`: deterministic catalog, dense scope, empty profile/rubric schema, and audit snapshot generation.

## Limitations

- No exercise-specific tissue profile is scientifically reviewed or production eligible.
- No rubric anchor research, independent blind review, full canonical backfill, or human batch approval is complete.
- Current records cannot resolve performed side or sport event counts.
- No tissue decay curve is approved; future shadow output must use declared calendar windows.
- No user-facing tissue feature is activated.
- Bayesian/time-series code is outside this foundation and unchanged.
