# Tissue Load Phase C: Same-Session Evidence Re-Audit

## Review Mode

This was a same-session evidence re-audit.

It was not an independent blind review. The reviewer had access to the Phase B1 draft claims and rubrics. Results from this phase are technical claim candidates pending explicit human batch approval.

```text
reviewMode = SAME_SESSION_EVIDENCE_REAUDIT
independenceStatus = NOT_INDEPENDENT
```

Phase C does not populate the blind-review ledger, formal final-claim ledger, batch-approval ledger, or production tissue profiles. Its maximum completion state is `EVIDENCE_REAUDIT_COMPLETE_PENDING_BATCH_APPROVAL`.

## Review Contract

- Every Phase B1 draft claim receives exactly one source-locator re-audit.
- Numeric candidates retain their unit, normalization, method, condition, and material limitations.
- Unsupported, contradicted, or unverifiable drafts remain visible in the re-audit ledger but do not become positive candidates.
- Same-session agreement is not represented as independent confirmation.
- User adjudications resolve only their stated interpretation questions and do not grant batch approval or production eligibility.

## Results

### Source Revalidation

Live NCBI and Crossref revalidation reproduced the committed verification artifact byte-for-byte (`SHA-256 D78D5E3CFBD26F5E153FDA06CD3CF6555FE9AF7E60EBCE7747EBC0A0AFD63C59`). No source ID, PMID, DOI, title, first author, year, or journal conflict was found. The verifier does not establish an affirmative publication-integrity clearance, so all rows honestly retain `STATUS_UNKNOWN`.

| Source ID | PMID | DOI | Identity | Bibliography | Integrity | Access used in Phase C |
|---|---:|---|---|---|---|---|
| `PREFLIGHT_32658037` | 32658037 | 10.1249/MSS.0000000000002459 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | TABLE / METHODS_AND_RESULTS |
| `SRC_PMID_10656979` | 10656979 | 10.1016/S0268-0033(99)00063-7 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_11949662` | 11949662 | 10.2519/jospt.2002.32.4.141 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_18632195` | 18632195 | 10.1016/j.clinbiomech.2008.05.002 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_28145739` | 28145739 | 10.4085/1062-6050-52.1.04 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_30923576` | 30923576 | 10.1186/s13047-019-0330-5 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_31593498` | 31593498 | 10.1177/0363546519876074 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |
| `SRC_PMID_37272685` | 37272685 | 10.1177/03635465231175160 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | TABLE / METHODS_AND_RESULTS |
| `SRC_PMID_37847102` | 37847102 | 10.1249/MSS.0000000000003323 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | TABLE / METHODS_AND_RESULTS |
| `SRC_PMID_8947402` | 8947402 | 10.1177/036354659602400615 | PMID_AND_DOI_VERIFIED | MATCHED | STATUS_UNKNOWN | ABSTRACT metadata revalidation |

`PREFLIGHT_32658037` remains the sole source ID for PMID 32658037. No `SRC_PMID_32658037` duplicate exists.

### Claim Re-Audit

| Draft claim | Source | Stable key | Tissue / dimension | Draft value / band | Re-audited value / maximum band | Support / action | Candidate | Main limitation |
|---|---|---|---|---|---|---|---|---|
| `DCLM_ACH_PEAK_SEATED_CALF` | `PREFLIGHT_32658037` | `ex_5c8751d2` | Achilles / peak tensile | 0.6 BW / LOW | 0.5–0.7 BW / LOW | PARTIALLY_SUPPORTED / correct value and condition | `CLAIM_C_58F3A7A0619274D3` | Range spans two reported group means; tested seated conditions used 15 kg thigh load. |
| `DCLM_ACH_PEAK_SINGLE_CALF` | `PREFLIGHT_32658037` | `ex_5ca7133f` | Achilles / peak tensile | 3.0 BW / MODERATE | 3.0 ± 0.3 BW / MODERATE | SUPPORTED_AS_DIRECT / retain with limitation | `CLAIM_C_44B1199853E615F2` | Eight healthy adults; modeled fixed 5 cm moment arm. |
| `DCLM_ACH_PEAK_SINGLE_HOP` | `PREFLIGHT_32658037` | `ex_314df428` | Achilles / peak tensile | 7.3 BW / VERY_HIGH | 7.3 BW / VERY_HIGH | SUPPORTED_AS_CLOSE_VARIANT / retain with limitation | `CLAIM_C_F4E8375FB29EC165` | Repeated hop evidence transfers to hop-and-stick only as `CLOSE_VARIANT`. |
| `DCLM_ACH_RATE_SINGLE_CALF` | `PREFLIGHT_32658037` | `ex_5ca7133f` | Achilles / loading rate | 13.1 BW/s / undefined | 13.1 ± 3.4 BW/s / undefined | SUPPORTED_AS_DIRECT / retain value, remove band | `CLAIM_C_6319786B969495EC` | Peak rate used a 5% moving window; no universal rate band is supported. |
| `DCLM_PAT_PEAK_FULL_SQUAT` | `SRC_PMID_37847102` | `ex_cb3c4dc2` | Patellar tendon / peak tensile | 3.4 BW / MODERATE | 3.4 ± 0.7 BW / MODERATE | SUPPORTED_AS_DIRECT / retain with limitation | `CLAIM_C_ABC6C4B0ECA22AC2` | Model omitted hamstring coactivation; band is within-study composite order. |
| `DCLM_PAT_PEAK_BULGARIAN` | `SRC_PMID_37847102` | `ex_bb728af2` | Patellar tendon / peak tensile | 3.0 BW / MODERATE | 3.0 ± 0.5 BW / MODERATE | SUPPORTED_AS_CLOSE_VARIANT / retain with limitation | `CLAIM_C_BD8915D6D39A0428` | Canonical rear-foot-elevated protocol is not fully specified by the source. |
| `DCLM_PFJ_COMP_60_SQUAT` | `SRC_PMID_37272685` | `ex_cb3c4dc2` | PFJ / compression | 2.5 BW / LOW | 2.5 ± 0.9 BW / LOW | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_8A2D37D94C1330F7` | 60-degree close variant; band uses composite index, not peak force alone. |
| `DCLM_PFJ_COMP_FULL_SQUAT` | `SRC_PMID_37272685` | `ex_cb3c4dc2` | PFJ / compression | 4.5 BW / MODERATE | 4.5 ± 0.8 BW / MODERATE | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_084C0B3DCC597268` | Composite index is an explicit compression proxy. |
| `DCLM_PFJ_COMP_BULGARIAN` | `SRC_PMID_37272685` | `ex_bb728af2` | PFJ / compression | 4.7 BW / MODERATE | 4.7 ± 0.7 BW / MODERATE | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_0777BA7A1FB88DF1` | Close variant; band uses composite peak-plus-impulse index. |
| `DCLM_PFJ_COMP_LUNGE` | `SRC_PMID_37272685` | `ex_64644b5e` | PFJ / compression | 5.1 BW / MODERATE | 5.1 ± 0.8 BW / MODERATE | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_E09E28E71A401E8D` | Study-defined lunge is a close variant; healthy model cohort. |
| `DCLM_PFJ_COMP_DROP_JUMP` | `SRC_PMID_37272685` | `ex_d6726746` | PFJ / compression | 6.8 BW / MODERATE | 6.8 ± 1.4 BW / MODERATE | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_E540DF218D72E603` | Drop height/prescription transfer is limited; composite band is not a peak-force threshold. |
| `DCLM_PFJ_COMP_SINGLE_HOP` | `SRC_PMID_37272685` | `ex_314df428` | PFJ / compression | 6.3 BW / MODERATE | 6.3 ± 1.2 BW / MODERATE | SUPPORTED_AS_EXPLICIT_PROXY / correct metric disclosure | `CLAIM_C_4D02CF35465C7511` | Maximum forward hop transfers to hop-and-stick only as a close variant. |

All 12 positive candidates remain `productionEligibility = false`, have blank human-approval fields, and retain `NOT_CROSS_STUDY_COMPARABLE`. Formal blind-review and final-claim ledgers remain empty.

### User Adjudications and Rubrics

The two explicit user instructions were recorded as interpretation adjudications, not approvals:

- `USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1` permits the `ex_314df428` Achilles peak-load transfer only as `CLOSE_VARIANT`, with `CLOSE_VARIANT_TRANSFER` and maximum candidate band `VERY_HIGH`. It does not represent the source protocol as an exact app match.
- `USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1` permits the source-defined 50% peak plus 50% impulse PFJ loading index as `DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY`. The metric remains visibly composite and is never described as pure peak compression force.

Both rows use `HUMAN_USER` / `EXPLICIT_USER_INSTRUCTION`, set `isBatchApproval = false`, and set `productionEligibilityEffect = NONE`.

| Rubric | Band | Re-audit action | Metric or anchor | Supporting candidates | Adjudication | Remaining limitation | Status |
|---|---|---|---|---|---|---|---|
| `RUBRIC_ACH_PEAK_LOW` | LOW | `CORRECT_ANCHOR` | Modeled peak Achilles force; 0.5-0.7 BW range across two seated conditions with 15 kg thigh load | `CLAIM_C_58F3A7A0619274D3` | none | Range is not one group mean; fixed-moment-arm model and small healthy sample | `REAUDITED_WITH_LIMITATIONS` |
| `RUBRIC_ACH_PEAK_MODERATE` | MODERATE | `RETAIN_WITH_LIMITATIONS` | Modeled peak Achilles force; standing single-leg heel raise, 3.0 +/- 0.3 BW | `CLAIM_C_44B1199853E615F2` | none | Fixed 5 cm moment arm, study cadence, healthy sample | `REAUDITED_WITH_LIMITATIONS` |
| `RUBRIC_ACH_PEAK_VERY_HIGH` | VERY_HIGH | `RETAIN_WITH_LIMITATIONS` | Modeled peak Achilles force; repeated directional single-leg hops, 7.3 BW | `CLAIM_C_F4E8375FB29EC165` | Achilles hop transfer | Close variant rather than exact hop-and-stick protocol | `REAUDITED_WITH_LIMITATIONS` |
| `RUBRIC_PFJ_COMP_LOW` | LOW | `CORRECT_METRIC` | Composite PFJ loading index; LOW is source tier below 0.333 | `CLAIM_C_8A2D37D94C1330F7` | PFJ composite proxy | 60-degree close variant; composite proxy rather than pure peak threshold | `REAUDITED_WITH_LIMITATIONS` |
| `RUBRIC_PFJ_COMP_MODERATE` | MODERATE | `CORRECT_METRIC` | Composite PFJ loading index; MODERATE is source tier 0.333-0.667 | `CLAIM_C_084C0B3DCC597268`, `CLAIM_C_0777BA7A1FB88DF1`, `CLAIM_C_E09E28E71A401E8D`, `CLAIM_C_E540DF218D72E603`, `CLAIM_C_4D02CF35465C7511` | PFJ composite proxy | Model assumptions and exercise-protocol transfers remain condition-specific | `REAUDITED_WITH_LIMITATIONS` |

Bands represented after re-audit are `LOW`, `MODERATE`, and `VERY_HIGH`. `HIGH` remains intentionally undefined; no equal-quartile or cross-study interpolation was introduced.

### Deterministic Integrity

Semantic CSV SHA-256 values ignore row ordering while preserving headers and field values:

| Artifact | SHA-256 |
|---|---|
| Evidence registry | `2e729407da8279c89f2289239285a9722d30305c64e282e8e8562d4e3acda04b` |
| Source verification | `32f57e1f2a4a7deb4a529258028bf43bacc2714c93a5e26d8057ad750eb35607` |
| Phase B1 draft claims | `397457e43c4f00bb19d74c9439290dac641fa21229ad6a4a1cf85ac3ffd345c7` |
| Phase C re-audits | `d76e40997928e92168a90a76e50437c76f1fecab1c9e5da47f88ce3cad46f26d` |
| Phase C claim candidates | `9288c959d0d0443a064975a2b1cc2ced510040e86a32ccc9e02668d854136658` |
| User adjudications | `ac3228b6ab5dc609c4f9a74412f62221ba75e18c7858d0201933a5aca1a56a9d` |
| Re-audited rubrics | `e7cb3e946118a7acd4c8aa75824a03048a5621ffd5e047efdef2fb5a56bf8d12` |
| Target-exercise review | `af2bd3e3f6d0abb32bb5759f532165d22a5184f96720018965ee37f6da43703f` |
| Audit manifest file bytes | `e4a9bd9bbe75e9f3e1317c418325b71bc43320a67eae1e82e9a293bbbd3b0757` |

The combined Phase C input hash is `94c15f4d43e843cd0238b1dd276d83e962bdf846a28a110330c5a970c0a64463`; audit row `tissue_reaudit_c_94c15f4d43e8` records 12 re-audits, 12 candidates, 2 adjudications, 4 retained claims, 8 corrected claims, 0 blocked claims, 2 retained rubrics, 3 corrected rubrics, and 0 blocked rubrics.

## Remaining Approval

Phase C status is `EVIDENCE_REAUDIT_COMPLETE_PENDING_BATCH_APPROVAL`. A human must still decide whether to:

- Approve all 12 claim candidates as a batch or review them individually.
- Approve the three corrected and two retained-with-limitations rubric rows.
- Promote approved candidates into the formal final-claim ledger.
- Begin Phase D1 Achilles/PFJ production-profile backfill.

Until then, blind-review rows, formal final claims, human batch approvals, and all four production profile ledgers remain empty. Existing six-axis fatigue, OFI, readiness, ProgramBuilder, Room/backup, and Bayesian/time-series behavior remain disconnected from these candidates.

## Phase C2A Handoff Update

Phase C2A supersedes the earlier unknown publication-integrity handoff without changing any Phase C scientific candidate value. Authoritative PubMed, Crossref, and available publisher metadata produced 10 `NO_ADVERSE_NOTICE_FOUND` rows and publication snapshot `bde38622731a2919141bbed967cffe7121d89cfac30ae87f8834badef006825f`.

The accepted same-session path now has an explicit formal promotion contract, but still requires an exact human batch decision. Pending request `TISSUE_APPROVAL_REQUEST_C2A_9D916660488C6196` covers all 12 candidates and five rubrics at scope hash `9d916660488c6196412cb956807bc2bf5adf8783957c6e646fa3eaca447b9b36`. No approval, final claim, blind review, or production profile has been created.
