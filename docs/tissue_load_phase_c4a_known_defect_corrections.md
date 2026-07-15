# Phase C4A Known-Defect Corrections

Status: `C4A_METADATA_FOUNDATION_AND_RESEARCH_CATALOG_PARTIAL`

## Superseded C3.1 request

- Request: `TISSUE_APPROVAL_REQUEST_C3_1_A00141AC34448C59`
- Scope: `a00141ac34448c5904db5aff2a514c599b8d47582e0d5bbac6bb752a85bb3b06`
- Resolution: `TISSUE_APPROVAL_RESOLUTION_C4A_A00141AC`
- The original request remains byte-for-byte unchanged after normalized line-ending comparison.
- No human approval, final claim, blind review, or production profile is created.

## Defects requiring versioned C4A replacements

1. `C3_ACH_MAX_HOP_LANDING_STRAIN` copied the weighted heel-rise protocol's added-load, total-mass, and vest-placement fields into a bodyweight hop-landing condition.
2. The reported `8.8 +/- 1.6 percent` Achilles strain for maximal hop landing is an event average, not a peak.
3. Candidate rows do not carry an explicit source-condition identity, so parity is not mechanically enforceable.
4. `C3METRIC_10656979_PCL_FORCE` reports modeled ligament force and therefore requires `TENSION`, not posterior translation as the measured mechanical mode.
5. `C3METRIC_8947402_TFJ_SHEAR` reports an intersegmental joint-force resultant, not internal compartment contact force.
6. Missing load values require explicit `not reported` or `not applicable` rendering rather than malformed numeric labels.

Historical C3.1 rows are retained unchanged. Corrected C4A rows and strict parity validation are added as new versioned artifacts in the research-catalog commit.
