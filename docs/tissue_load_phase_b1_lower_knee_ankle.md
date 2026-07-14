# Phase B1 Lower-Limb Tissue Rubric Draft

Status: `RUBRIC_DRAFT_COMPLETE_PENDING_BLIND_REVIEW`

Batch: `TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE`

## Cause

The deterministic tissue foundation had no scientifically reviewed load rubrics or anchor evidence. Its Achilles preflight source also paired the correct PMID and title with an incorrect DOI. Phase B1 repairs that source identity, records reproducible lower-limb research decisions, and creates only condition-bounded draft anchors.

## Research Scope

- Canonical exercise targets reviewed: 15.
- Tissue/dimension questions decided: 31 (14 Tier 1 and 17 Tier 2).
- Officially identity-verified registry sources: 10.
- Sources included in at least one research decision: 5.
- Target-level included-source references: 7.
- Target-level excluded-source references with explicit reasons: 29 across 26 decisions.
- Draft claims: 12.
- Draft rubric rows: 5, covering two tissue/dimension questions.
- Decisions: 2 `DRAFT_RUBRIC_CREATED`, 14 `EVIDENCE_FOUND_BUT_NOT_COMPARABLE`, 14 `BLOCKED_INSUFFICIENT_EVIDENCE`, 1 `OUT_OF_SCOPE_AFTER_AUDIT`, 0 conflicting, 0 missing.

The only calibrated draft questions are `ACHILLES_TENDON:PEAK_TENSILE_LOAD` and `KNEE_PATELLOFEMORAL:COMPRESSION`. Force, strain, translation, joint contact, impulse, stability, loading-rate, and energy outcomes were not silently pooled across incompatible methods.

## Source Correction

- Immutable source ID before and after correction: `PREFLIGHT_32658037`.
- PMID: `32658037`.
- Resolved title: `Exercise Progression to Incrementally Load the Achilles Tendon.`
- Incorrect committed DOI: `10.2519/jospt.2020.9406`.
- Officially resolved DOI: `10.1249/MSS.0000000000002459`.
- NCBI: PMID, title, authors, journal, year, publication type, and DOI parsed from official metadata.
- Crossref: DOI, normalized title, first author, journal, and year matched.
- Result: `PMID_AND_DOI_VERIFIED` / `MATCHED`; publication integrity remains `STATUS_UNKNOWN`.

The old DOI was a bibliographic-identity mismatch, not a formatting defect. The earlier verifier failed closed, so the source stayed unverified and no production profile or rubric inherited the mismatch. The source ID was preserved because it is an immutable provenance key; no duplicate `SRC_PMID_32658037` row exists.

Network preflight found official NCBI and Crossref identity services available. PubMed Central was available for PMC-hosted papers; publisher/full-text availability remained source-dependent. Committed source verification is deterministic and ordinary CI performs offline validation.

## Draft Rubrics

| Tissue / dimension | Band | Anchor basis | Boundary policy |
| --- | --- | --- | --- |
| Achilles tendon / peak tensile load | LOW | Tested seated heel raise | Within-study modeled peak-force order; no global numeric threshold |
| Achilles tendon / peak tensile load | MODERATE | Tested single-leg heel raise | Within-study modeled peak-force order; no global numeric threshold |
| Achilles tendon / peak tensile load | VERY_HIGH | Close single-leg hopping variant | Close-variant transfer with very-low confidence |
| Patellofemoral joint / compression | LOW | Source-defined loading index tier 1 | Source index upper bound 0.333 |
| Patellofemoral joint / compression | MODERATE | Source-defined loading index tier 2 | Source index 0.333 to 0.667 |

Missing bands are intentional. No equal quartiles, cross-tissue threshold, default `NONE`, or invented interpolation is used.

## Target Exercise Outcomes

| Stable key | Canonical name | Research-use status | Supported tissue/dimension | Sources | Draft claims | Draft rubrics | Transfer/non-use note |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `barbell_back_squat` | 스쿼트 | `USED_AS_TRANSFER_REFERENCE` | PFJ compression; tibiofemoral AP shear | `SRC_PMID_11949662`, `SRC_PMID_8947402` | - | - | Loaded protocols and models differ from the canonical app prescription. |
| `ex_cb3c4dc2` | 맨몸 스쿼트 | `USED_AS_DIRECT_ANCHOR` | Patellar peak tensile load; PFJ compression | `SRC_PMID_37847102`, `SRC_PMID_37272685` | `DCLM_PAT_PEAK_FULL_SQUAT`, `DCLM_PFJ_COMP_60_SQUAT`, `DCLM_PFJ_COMP_FULL_SQUAT` | `RUBRIC_PFJ_COMP_MODERATE` | Direct source conditions remain condition-bounded. |
| `ex_c5043892` | 프론트 스쿼트 | `USED_AS_TRANSFER_REFERENCE` | Tibiofemoral AP shear | `SRC_PMID_8947402` | - | - | Study fixed a 50-pound load; app prescriptions can differ. |
| `ex_b78a8f95` | 레그 익스텐션 | `NO_COMPARABLE_SOURCE_FOUND` | - | - | - | - | `NO_COMPARABLE_SOURCE`. |
| `ex_d60745b4` | 싱글 레그 레그 익스텐션 | `NO_COMPARABLE_SOURCE_FOUND` | - | - | - | - | `NO_COMPARABLE_SOURCE`. |
| `ex_64644b5e` | 런지 | `USED_AS_DIRECT_ANCHOR` | PFJ compression; tibiofemoral AP shear | `SRC_PMID_37272685`, `SRC_PMID_8947402` | `DCLM_PFJ_COMP_LUNGE` | `RUBRIC_PFJ_COMP_MODERATE` | Direct rubric use is limited to PFJ compression. |
| `ex_bb728af2` | 리어풋 엘리베이티드 스플릿 스쿼트 | `USED_AS_DIRECT_ANCHOR` | Patellar peak tensile load; PFJ compression | `SRC_PMID_37847102`, `SRC_PMID_37272685` | `DCLM_PAT_PEAK_BULGARIAN`, `DCLM_PFJ_COMP_BULGARIAN` | `RUBRIC_PFJ_COMP_MODERATE` | Direct rubric use is limited to PFJ compression. |
| `ex_bd072cd` | 스탠딩 카프 레이즈 | `USED_AS_TRANSFER_REFERENCE` | Achilles peak tensile load | `PREFLIGHT_32658037`, `SRC_PMID_28145739` | - | - | App load and bilateral/unilateral execution are not fixed. |
| `ex_5c8751d2` | 시티드 카프 레이즈 | `USED_AS_DIRECT_ANCHOR` | Achilles peak tensile load | `PREFLIGHT_32658037`, `SRC_PMID_28145739` | `DCLM_ACH_PEAK_SEATED_CALF` | `RUBRIC_ACH_PEAK_LOW` | Tested condition only. |
| `ex_5ca7133f` | 싱글 레그 카프 레이즈 | `USED_AS_DIRECT_ANCHOR` | Achilles peak tensile load; loading rate | `PREFLIGHT_32658037`, `SRC_PMID_28145739` | `DCLM_ACH_PEAK_SINGLE_CALF`, `DCLM_ACH_RATE_SINGLE_CALF` | `RUBRIC_ACH_PEAK_MODERATE` | Loading-rate claim is not a calibrated loading-rate rubric. |
| `ex_e465d1e9` | 포고 점프 | `USED_AS_TRANSFER_REFERENCE` | Achilles peak tensile load | `PREFLIGHT_32658037` | `DCLM_ACH_PEAK_SINGLE_HOP` | - | Repeated unilateral directional hops differ from bilateral pogo execution. |
| `ex_a3ddd8ac` | 라인 홉 | `USED_AS_TRANSFER_REFERENCE` | Achilles peak tensile load; PFJ compression | `PREFLIGHT_32658037`, `SRC_PMID_37272685` | `DCLM_ACH_PEAK_SINGLE_HOP` | - | Direction, leg count, and cadence differ. |
| `ex_314df428` | 싱글 레그 홉 앤 스틱 | `USED_AS_TRANSFER_REFERENCE` | Achilles peak tensile load; PFJ compression | `PREFLIGHT_32658037`, `SRC_PMID_37272685` | `DCLM_ACH_PEAK_SINGLE_HOP`, `DCLM_PFJ_COMP_SINGLE_HOP` | - | Sources used repeated or maximum forward hops; the app target adds a stick landing. |
| `ex_d6726746` | 드롭 점프 | `USED_AS_DIRECT_ANCHOR` | PFJ compression | `SRC_PMID_37272685` | `DCLM_PFJ_COMP_DROP_JUMP` | `RUBRIC_PFJ_COMP_MODERATE` | Tested double-leg drop-jump condition only. |
| `ex_377448a9` | 스플릿 스텝 후 좌우 런지 | `USED_AS_TRANSFER_REFERENCE` | PFJ compression; ACL deceleration stabilization | `SRC_PMID_37272685`, `SRC_PMID_31593498` | `DCLM_PFJ_COMP_LUNGE` | - | The composite app task was not directly studied; component evidence remains separate. |

Summary: 15 reviewed, 6 direct anchors, 7 transfer references, 0 reviewed-not-used, 0 blocked exercises, and 2 with no comparable source.

## Evidence Limitations

- Most samples are healthy adults and do not justify population-wide clinical use.
- Many outcomes are modeled internal loads or inverse-dynamics proxies rather than direct tissue measurements.
- Exercise load, ROM, cadence, bilateral status, landing strategy, and laboratory surface limit transfer to app prescriptions.
- Joint contact force is not interchangeable with external force or intersegmental resultant force.
- Tendon force is not interchangeable with muscle activation, loading rate, cyclic exposure, or energy storage.
- Ligament strain or cadaveric stability cannot be relabeled as translation, valgus, rotational shear, or exercise stability demand.
- `KNEE_ACL:VALGUS` is out of scope under the current catalog and was not used to expand the schema in this phase.

## Non-Production Gates

- Draft only.
- Pending independent blind review.
- Pending final claim creation.
- Pending human approval.
- Not production eligible.
- Production profile rows: 0.
- Blind-review rows: 0.
- Final-claim rows: 0.
- Human approvals: 0.

## File Responsibility Map

- `tissue_load_evidence_registry_v1.csv`: 10 verified, non-production source identities.
- `tissue_source_verification_v1.csv`: deterministic official identity-verification artifact.
- `tissue_evidence_claims_draft_v1.csv`: 12 condition-bounded AI-prepared claims.
- `tissue_rubric_research_log_v1.csv`: one explicit decision for each of 31 tissue/dimension questions.
- `tissue_rubric_target_exercise_review_v1.csv`: exactly one research-use outcome for each target exercise.
- `tissue_load_band_rubric_v1.csv`: five draft-only anchor rows for two questions.
- `tissue_metadata_audit_manifest_v1.csv`: preserved foundation row plus Phase B1 evidence-batch snapshot.
- `TissueMetadataModels.kt`, `TissueMetadataParser.kt`, `TissueEvidenceValidator.kt`: typed rubric contracts, parsing, and Phase B1 gates.
- `generate_tissue_phase_b1_draft_assets.ps1`: deterministic draft-data and audit generation.
- `export_tissue_blind_review_package.ps1`: explicit-path, redacted Phase C package export.
- `TissuePhaseB1ResearchTest.kt`: completeness, references, non-production state, and approval-contamination tests.

## Phase C Handoff

Phase C same-session evidence re-audit has now produced 12 technical claim candidates and re-audited all five rubric rows. This did not satisfy the independent blind-review gate: the reviewer had access to the Phase B1 drafts, and the formal blind-review, final-claim, approval, and production-profile ledgers remain empty. Current details and hashes are in `docs/tissue_load_phase_c_same_session_reaudit.md`.

For a future independent review, generate a blind package outside tracked assets and existing `outputs/*`:

```powershell
powershell.exe -ExecutionPolicy Bypass -File tools/export_tissue_blind_review_package.ps1 -OutputPath <temporary-path.csv>
```

The reviewer receives source identity, exercise/tissue/dimension, condition, evidence locator, and access instructions. The package excludes proposed bands, numeric claim values, claim direction, confidence, assignment method, draft rationale, rubric rationale, and research-use conclusions. Phase C must independently record blind review before any final claim or human approval can exist.
