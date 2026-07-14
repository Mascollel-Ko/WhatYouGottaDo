# Tissue Load Phase C2A: Approval Path and Publication Gate

## Completion Boundary

Phase C2A ends at `APPROVAL_PACKAGE_READY`.

The approval architecture is ready, but no human batch approval has been recorded, no formal final claim has been promoted, and no production tissue profile has been created. The two Phase C user adjudications remain interpretation decisions only; they are not batch approval.

## Cause

- Formal final-claim validation previously assumed an independent blind-review path.
- The accepted same-session re-audit had no valid, explicit promotion contract.
- The PFJ LOW and MODERATE intervals overlapped at exactly `0.333` without inclusivity fields.
- All source publication-integrity states were unknown.
- Human approval was not bound to an immutable scientific payload and exact snapshot hashes.

## Changes

### Review paths

Formal final claims now declare exactly one `TissueFinalClaimReviewPath`:

- `INDEPENDENT_BLIND_REVIEW` keeps the original strict independent-review requirements.
- `SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL` requires the exact re-audit, candidate, linked rubrics, active human batch approval, audit identity, scope hash, source-verification snapshot, and publication-integrity snapshot.

The paths are mutually exclusive. A missing path, fabricated blind review, missing same-session link, changed candidate payload, stale snapshot, rejected/revoked approval, or AI self-approval fails validation. A revoked approval also makes linked production profiles ineligible during validation.

### Approval contract

The formal-claim and batch-approval ledgers now carry explicit review, request, scope, audit, source, publication, actor, and decision fields. Existing empty CSVs remain parseable; a legacy populated final claim without an explicit review path is invalid until migrated.

Future ledger ingestion accepts a full approval only when the user's statement exactly matches the pending request statement. Partial approval must explicitly partition every requested candidate and rubric ID and state exclusion reasons. Casual instructions such as `continue` are not approval.

### Rubric boundaries

Rubrics now declare decimal-safe endpoint inclusivity and a boundary semantics version. `BigDecimal` comparisons implement:

- `RUBRIC_PFJ_COMP_LOW`: `(-infinity, 0.333]`
- `RUBRIC_PFJ_COMP_MODERATE`: `(0.333, 0.667]`

Therefore exactly `0.333` matches LOW only, and exactly `0.667` matches MODERATE only. Global validation rejects overlapping, inverted, duplicate, or ambiguous intervals while allowing scientifically intentional gaps and missing bands.

### Publication integrity

`tissue_publication_integrity_verification_v1.csv` records authoritative PubMed comments/corrections, Crossref relation/update metadata, and publisher notice checks where accessible. All 10 current sources are `NO_ADVERSE_NOTICE_FOUND`: no adverse notice was found in the checked authoritative metadata as of the recorded time. This is not a claim of exhaustive future certainty.

Future promotion accepts only `NO_ADVERSE_NOTICE_FOUND` or a separately reviewed acceptable correction. Unknown, retracted, expression-of-concern, stale, or unverified states fail closed.

## Approval Request

- Approval request ID: `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196`
- Approval scope hash: `9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36`
- Review path: `SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL`
- Claim candidates: 12
- Rubrics: 5
- Sources: 10
- User adjudications: 2
- Excluded candidates: 0
- Excluded rubrics: 0
- Audit manifest: `tissue_approval_c2a_cbc8f749c37d`
- Audit input hash: `cbc8f749c37d16e535496f256d723d0815dc2bcb29d4539cf7c541f141e25783`
- Source-verification snapshot: `03216998a8d5dd728ae538fdeb771431d0af69d710d321ed267e5b1cd82b37e8`
- Publication-integrity snapshot: `bde38622731a2919141bbed967cffe7121d89cfac30ae87f8834badef006825f`

Exact required user statement:

```text
I approve approvalRequestId=TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196,
approvalScopeHash=9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36,
reviewPath=SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
covering exactly the listed 12 claim candidates and 5 rubric rows.
I understand this was a same-session, non-independent technical re-audit.
```

## Result

- Approval request rows: 1 pending human decision
- Human batch approval rows: 0
- Formal final-claim rows: 0
- Independent blind-review rows: 0
- Joint, tendon, ligament, and fascia production profile rows: 0
- Completion status: `APPROVAL_PACKAGE_READY`

## File and Feature Map

### Review and approval contracts

- `TissueEvidenceModels.kt`: explicit final-claim paths, expanded final/approval records, approval-request status and model, publication-integrity types.
- `TissueEvidenceParser.kt`: typed parsing for expanded final claims, approvals, approval requests, and publication-integrity rows.
- `TissueEvidenceValidator.kt`: common/path-specific promotion gates, exact approval ingestion, current snapshot checks, revocation propagation, and approval-request validation.
- `TissueApprovalScopeHasher.kt`: order-independent SHA-256 over the complete scientific approval scope; excludes timestamps and harmless notes.
- `tissue_evidence_claims_v1.csv`: expanded empty formal-claim schema.
- `tissue_review_batch_approval_v1.csv`: expanded empty human-approval ledger schema.
- `tissue_review_batch_approval_request_v1.csv`: one pending exact-scope request; it is not approval.
- `TissueFinalClaimReviewPathTest.kt`: strict blind and same-session path, mutual exclusion, stale snapshot, payload identity, and revoked approval tests.
- `TissueApprovalRequestTest.kt`: exact scope, deterministic hash, invalid-row rejection, statement, ingestion, and no-promotion tests.
- `TissueEvidenceValidatorTest.kt`: empty-ledger compatibility and revoked linked-profile regression coverage.

### Rubric and publication gates

- `TissueMetadataModels.kt`, `TissueMetadataParser.kt`, `TissueMetadataValidator.kt`: explicit decimal rubric endpoints and global interval validation.
- `tissue_load_band_rubric_v1.csv`: non-overlapping PFJ inclusivity semantics.
- `TissueRubricBoundaryAndIntegrityTest.kt`: decimal boundary and offline publication-gate coverage.
- `tissue_load_evidence_registry_v1.csv`, `tissue_source_verification_v1.csv`: mapped checked integrity state.
- `tissue_publication_integrity_verification_v1.csv`: 10 authoritative-metadata verification rows.
- `verify_tissue_publication_integrity.ps1`: bounded live PubMed/Crossref/publisher verifier.
- `verify_tissue_sources.ps1`: source verifier aware of the checked integrity state.
- `generate_tissue_foundation_assets.ps1`, `generate_tissue_phase_b1_draft_assets.ps1`, `generate_tissue_phase_c_reaudit_assets.ps1`: preserve the extended rubric and audit schemas.
- `TissuePhaseCReauditTest.kt`: historical Phase C audit remains immutable while current source/rubric snapshots advance.

### Approval package and documentation

- `generate_tissue_batch_approval_request.ps1`: validates governed inputs and deterministically emits the request, audit row, report, hashes, and exact statement without populating approval.
- `tissue_metadata_audit_manifest_v1.csv`: appends the C2A `APPROVAL_PACKAGE_READY` historical audit row.
- `tissue_load_phase_c2a_approval_request.md`: human-readable candidate, rubric, source, adjudication, limitation, hash, and statement package.
- `tissue_load_foundation_v1.md`, `tissue_load_phase_c_same_session_reaudit.md`, `codex_worklog.md`: handoff and non-production boundary updates.

## Remaining Work

1. Receive an explicit user decision tied to the request ID and scope hash.
2. Ingest an approved, partially approved, rejected, or revoked historical ledger row without altering the request.
3. Promote only approved candidates and rubrics to formal final claims.
4. Perform Phase D1 Achilles/PFJ exercise-profile backfill only after valid approval.
5. Validate real shadow exposure without changing six-axis fatigue, OFI, readiness, ProgramBuilder, Room/backup, or Bayesian/time-series behavior.

## C2A-R1 Supersession Notice

The request `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196` and scope hash
`9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36` are no longer eligible for approval ingestion.
Human review superseded the request before approval because the combined package overgeneralized exact study conditions,
confused bodyweight-normalized internal force with exercise resistance, omitted external-load evidence, merged distinct
split-squat variants, used a 60-degree squat as a generic app anchor, and collapsed PFJ peak, impulse, loading rate, and a
study-specific composite into one generic compression interpretation.

The original request file is preserved byte-for-byte as historical evidence. Resolution
`TISSUE_APPROVAL_RESOLUTION_C2A_R1_9D916660` records `SUPERSEDED_BEFORE_APPROVAL`; no approval row was created.
The 12 human research directives are in `tissue_human_research_directive_v1.csv`. See
`tissue_load_phase_c2a_r1_lower_revision.md` for the revised research boundary.

## C2A-R1 Revised Request

The replacement package is a separate immutable request, not a mutation of the old request:

- Request: `TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD`
- Scope hash: `74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f`
- Audit: `tissue_research_c2a_r1_4b74d89f1410`
- Status: `REVISED_APPROVAL_PACKAGE_PARTIAL`
- Scope: 24 revised candidates, 2 revised rubrics, 12 candidate dispositions, 5 rubric dispositions, 12 directives,
  13 research decisions, 49 mappings, and existing source/integrity snapshots.

Six material research targets remain blocked, so no production promotion is permitted. A later decision must reproduce
the exact statement in `tissue_load_phase_c2a_r1_revised_approval_request.md`; casual continuation text is not approval.

## Verification

- Live publication-integrity verification: 10 sources, 10 no-adverse-notice results, 0 blockers; output SHA-256 matched the committed artifact.
- Approval request generator: repeated request, audit, and report outputs were byte-identical.
- Focused approval, path, rubric, integrity, historical Phase C, and profile-revocation tests passed.
- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest --rerun-tasks`: 696 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug`: passed.
