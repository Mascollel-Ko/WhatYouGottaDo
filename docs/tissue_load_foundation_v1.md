# Tissue Load Foundation v1

## Status

The tissue-load system is a non-production shadow foundation. It does not replace the six fatigue axes, OFI, readiness, warnings, or ProgramBuilder behavior. Missing metadata remains missing rather than numeric zero.

Current implementation status after foundation Commit 4: `FOUNDATION_COMPLETE` for the deterministic, non-production shadow foundation.

This does not mean `RUBRIC_COMPLETE`, `FULL_BACKFILL_COMPLETE`, or `PRODUCTION_ELIGIBLE`.

## Repository Audit

- Baseline: Phase A final commit `c5aaaa0a01b289d50e5d277ef4d0fcb7a0ea6a1f`, fast-forwarded to `origin/main` before the tissue commits were reapplied.
- Canonical exercise metadata: `canonical_exercise_metadata_v0_3_5_0_pass3_1.csv`.
- Canonical exercise count: 239.
- Canonical identity: `stableKey`; runtime lookup is stable-key first. The tissue repository accepts exact stable keys only and does not use the legacy display-name fallback.
- Existing tissue-related metadata columns: `tendonStressTags`, `ligamentJointStabilityStressTags`, `jointImpactStressTags`, and scalar `jointTendonImpactStressLevel`.
- Legacy tissue-token count: 42 distinct non-`NONE` field/token identities (15 tendon, 19 ligament/joint stability, and 8 impact). The scalar level is not a migration token.
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
- `tissue_load_band_rubric_v1.csv`: five Phase B1 draft-only rows for Achilles peak tensile load and patellofemoral compression. No global thresholds, automatic quartiles, or invented completion bands are present.
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
- `NOT_YET_EVALUATED`, blocked, conflicting, and missing-input states cannot become zero.
- Tissue-state identity is unsided. Execution laterality may inform dose or context, but it never creates separate state keys, status rows, recovery clocks, rankings, or UI labels.
- AI preparation cannot fill or imply human approval.

## Network Capability

On 2026-07-14, official NCBI metadata confirmed that PMID `32658037` is `Exercise Progression to Incrementally Load the Achilles Tendon.` and supplies DOI `10.1249/MSS.0000000000002459`. Parsed Crossref metadata matched the DOI, normalized title, first author, print year, and journal. Source identity is therefore `PMID_AND_DOI_VERIFIED` / `MATCHED`; publication integrity, claims, blind review, human approval, and production eligibility remain pending.

The previous committed DOI `10.2519/jospt.2020.9406` was a bibliographic-identity mismatch, not a formatting defect. The original verifier failed closed, so the source stayed unverified and no production profile or rubric inherited the bad citation. The immutable provenance ID `PREFLIGHT_32658037` is preserved and no duplicate `SRC_PMID_32658037` row exists.

Capability status: `LIVE_SOURCE_VERIFICATION_AVAILABLE` for source-identity verification. Ordinary Gradle CI remains offline.

## Evidence Provenance

- `tissue_load_evidence_registry_v1.csv` keeps the immutable preflight source ID with corrected, officially verified bibliographic identity and non-production status.
- Draft claims, same-session re-audits, claim candidates, blinded reviews, and final claims are separate ledgers. Phase B1 added 12 condition-bounded drafts; Phase C added 12 technically re-audited non-production candidates. Blind review and final-claim ledgers remain empty.
- `tissue_source_verification_v1.csv` records parsed NCBI/Crossref identity verification without upgrading claim or publication-integrity status.
- `tissue_review_batch_approval_v1.csv` is empty. Automated audit output cannot substitute for a human approval row.
- `verify_tissue_sources.ps1` is the network-enabled refresh command. Offline CI validates committed artifacts and never depends on NCBI or Crossref uptime.
- Production `STUDY_BACKED` rows require verified identifiers, matched bibliography, supported claim text, acceptable publication integrity, a separate blind review, and human or valid batch approval.

## Record And Migration Contracts

- `legacy_tissue_tag_migration_v1.csv` contains one review decision for each of the 42 real legacy field/token identities. Every row is `LEGACY_SEEDED_NOT_EVALUATED` with `automaticBandAllowed=false`; broad cuff, stability, joint, and impact tags remain ambiguous candidate sets and are never equally split or promoted to bands.
- `dose_input_capability_v1.csv` audits all 12 supported dose-basis tokens. External-load repetitions, effective-bodyweight repetitions, holds, and explicitly duration-recorded activities are derivable from current records. Distance and landing, direction-change, jump, throw, and stroke counts are explicitly unavailable and have no fallback.
- Current bodyweight and hold calculations continue to name `BodyweightEffectiveLoadCalculator` and `DurationHoldLoadCalculator` as their authorities.
- Performed side is not recorded. Tissue state remains unsided regardless; no 50:50 split or lead/trail-to-left/right conversion is allowed. Execution laterality is retained only as optional dose/context metadata and never blocks an otherwise calculable exposure.
- `exercise_tissue_modifier_rules_v1.csv` is an empty production schema. The contract fixes reference conditions, specificity, exclusive/interaction groups, replacement-before-multiplication order, required inputs, bounds, evidence state, and human approval without inventing a modifier.

## Shadow Exposure Pipeline

- `TissueDoseResolver` reads confirmed set inputs only. It delegates effective-bodyweight dose to `BodyweightEffectiveLoadCalculator` and plank/side-plank holds to `DurationHoldLoadCalculator`. Unrecorded distance or event counts return `MISSING_RECORD_INPUT`; duration never implies an event count.
- `TissueModifierResolver` applies the most-specific rule in each exclusive group, replacement before multiplication, explicit bounds, and deterministic rule ordering. Missing inputs, blocked combinations, and undeclared interactions fail closed. The committed production modifier file remains empty.
- `TissueExposureCalculator` requires an exact profile stable key, reference condition, dose basis, and finite profile-specific dimension weight. Side policy is interpreted only as execution context. Each unsided tissue/dimension is calculated independently; there is no legacy-total allocation or sum-preservation rule.
- Non-production numeric fixtures require an explicit opt-in flag and an `explicitlyNonProductionFixture` weight. Default calls reject them as `EVIDENCE_NOT_APPROVED`.
- Missing performed side preserves the unsided numeric total when the remaining inputs are calculable. It never creates a blocker, left/right warning, separate state, or 50:50 split.
- RPE can only enter once. Existing hold/session dose marks RPE as already applied; otherwise the exposure layer accepts only an explicitly supplied approved response multiplier.
- `TissueWindowedExposureCalculator` provides calendar-day 24-hour, 72-hour, and 7-day exposure windows. Future records are excluded, contributor ordering is deterministic, and `residualExposure` remains null.
- `tissue_recovery_profiles_v1.csv` is schema-only. No decay kernel or tissue-class default was copied from the legacy fatigue system.
- `TissueExposureShadowPipeline` is a standalone API and has no production caller. Existing six-axis fatigue, OFI, readiness, wording, ProgramBuilder, and Bayesian/time-series code remain numerically disconnected.

## File Responsibility Map

- `TissueMetadataModels.kt`: tissue classes, dimensions, evaluation states, long-form profile/scope/rubric/audit models.
- `TissueMetadataParser.kt`: deterministic RFC-style CSV parsing and typed metadata parsing.
- `TissueMetadataValidator.kt`: anatomy, scope, profile, approval, and semantic-hash invariants.
- `TissueLoadProfileRepository.kt`: exact stable-key profile and rubric lookup.
- `generate_tissue_foundation_assets.ps1`: deterministic catalog, dense scope, empty profile/rubric schema, and audit snapshot generation.
- `TissueEvidenceModels.kt`: source, draft, blind-review, final-claim, and batch-approval contracts.
- `TissueEvidenceParser.kt`: typed evidence-ledger parsing.
- `TissueEvidenceValidator.kt`: source, claim, actor separation, production gate, and batch-audit reference validation.
- `verify_tissue_sources.ps1`: bounded network preflight and fail-closed verification-artifact refresh.
- `generate_tissue_phase_b1_draft_assets.ps1`: deterministic lower-limb source, draft-claim, research-decision, target-review, draft-rubric, and evidence-batch audit generation.
- `generate_tissue_phase_c_reaudit_assets.ps1`: deterministic same-session re-audits, claim candidates, two explicit interpretation adjudications, re-audited rubric candidates, and historical Phase C audit generation.
- `export_tissue_blind_review_package.ps1`: explicit-path redacted handoff for a separate Phase C reviewer.
- `tissue_load_phase_b1_lower_knee_ankle.md`: Phase B1 source correction, decisions, target outcomes, limitations, and handoff record.
- `tissue_load_phase_c_same_session_reaudit.md`: source-by-source and claim-by-claim technical re-audit, adjudication scope, rubric decisions, hashes, and remaining approval record.
- `TissueRecordContracts.kt`: migration, dose, calculation-state, execution-laterality context, and modifier enums/models. Side resolution is diagnostic context only.
- `TissueRecordContractParser.kt`: typed parsing for migration, dose-capability, and modifier artifacts.
- `TissueRecordContractValidator.kt`: exact legacy coverage, no-band migration, dose fallback, tissue/dimension, and modifier evidence checks.
- `generate_tissue_record_contract_assets.ps1`: deterministic 42-token migration, 12-basis capability, empty modifier schema, and stage-3 audit generation.
- `TissueExposureModels.kt`: record input, dose, tissue-key, exposure, recovery-window, contribution, gap, and hierarchical snapshot models.
- `TissueDoseResolver.kt`: confirmed-set dose resolution through existing bodyweight/hold authorities and explicit missing states.
- `TissueModifierResolver.kt`: deterministic exclusive-group selection, operation order, interaction blocking, and clamping diagnostics.
- `TissueExposureCalculator.kt`: exact-key, evidence-gated, independent tissue-dimension exposure calculation.
- `TissueWindowedExposureCalculator.kt`: calendar-bound 24-hour, 72-hour, and 7-day summaries with deterministic contributors.
- `TissueExposureShadowPipeline.kt`: standalone non-production orchestration; it is intentionally not injected into the existing fatigue path.
- `TissueExposureShadowPipelineTest.kt`: dose authority, modifier ordering/conflicts, independent exposure, missingness, laterality, windows, and unknown-key coverage.
- `generate_tissue_record_contract_assets.ps1`: also emits the empty recovery schema and stage-4 deterministic audit snapshot.

## Limitations

- No exercise-specific tissue profile is independently reviewed or production eligible.
- Phase C same-session technical re-audit is complete for the 12 Phase B1 claims and five rubric rows. It was not independent; blind review, final claims, full canonical backfill, and human batch approval remain incomplete.
- Current records cannot resolve performed side or sport event counts.
- Legacy candidate mappings are review seeds only; none is an exercise-specific tissue profile.
- Modifier rules remain empty because no reviewed interaction or factor is available.
- No tissue decay curve is approved; future shadow output must use declared calendar windows.
- No user-facing tissue feature is activated.
- Bayesian/time-series code is outside this foundation and unchanged.
- No canonical exercise has a reviewed dimension weight, so committed application assets produce metadata gaps rather than numeric tissue exposure.
- Shadow fixture weights are test-only and cannot pass the default evidence gate.

## Phase C3.1 corrected approval boundary

The unapproved C3 request `TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B` remains immutable and is superseded by
`TISSUE_APPROVAL_RESOLUTION_C3_1_48F86FEE`. C3.1 limits mechanical mode to physical loading, stores event/phase/position/
functional/response context separately, permits ligament `TENSION`, and separates added external load from total system mass.

The two Achilles points are `CONDITION_ANCHOR` rows, not intervals: 3.0 BW applies only to its exact single-calf condition and
7.3 BW only to its exact repeated-hop condition. They do not classify 3.1 BW, intermediate values, neighboring exercises, or
D-stage profiles. LOW and HIGH remain intentionally missing. The replacement request is pending, non-production, and creates
no approval, final claim, blind review, profile, runtime behavior, version, or tag.

## Phase C2A Approval Boundary

Phase C2A adds explicit blind and same-session final-claim paths, exact-scope human approval requests, non-overlapping decimal rubric boundaries, and publication-integrity verification. The deterministic request is `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196` with scope hash `9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36`.

That historical package reached `APPROVAL_PACKAGE_READY` but was never approved and is now superseded. The approval, formal final-claim, blind-review, and all four production-profile ledgers remain empty. See `tissue_load_phase_c2a_approval_path.md` for the promotion contract and `tissue_load_phase_c2a_approval_request.md` for the preserved historical package.

## Phase C2A-R1 revision boundary

The C2A request above is now an immutable superseded artifact and must not be ingested. C2A-R1 adds explicit PFJ
`PEAK_COMPRESSION`, `COMPRESSION_IMPULSE`, and `COMPRESSION_LOADING_RATE` dimensions and keeps patellar tendon
`TENDON_STRAIN` distinct from force. Historical `COMPRESSION` rows remain readable, but the old PFJ composite rubrics
cannot define generic app load bands. The current lower-limb revision remains non-production; no existing fatigue,
readiness, program, persistence, backup, or time-series path reads these new artifacts.

The revised request is `TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD` at scope hash
`74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f`. Its status is
`REVISED_APPROVAL_PACKAGE_PARTIAL` because six material research targets remain explicitly blocked. The request is still
non-production and is not a human decision. See `tissue_load_phase_c2a_r1_revised_approval_request.md`.

## Phase C3 multidimensional boundary

The revised C2A-R1 request above remains byte-for-byte historical and was superseded before approval by
`TISSUE_APPROVAL_RESOLUTION_C3_MD_R1_74ECC664`. Phase C3 replaces the ambiguous single-dimension approval scope with
separate mechanical-load mode, temporal metric, measurement metric, normalization, and exact-condition identities.

The pending replacement request is `TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B` at scope hash
`48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8`. Completion is
`MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL`: 27 material targets remain blocked, only two narrow Achilles peak rubric
anchors exist, and no universal burden score or fixed cross-dimensional weights are defined. Human approvals, formal
final claims, blind reviews, and production profiles remain zero. No runtime fatigue, OFI, readiness, ProgramBuilder,
persistence, backup, time-series, UI, or version path reads the C3 package.
