# Korean Metadata Display Terminology Closeout

## Scope

This closeout covers presentation and search terminology only. Canonical
codes, scientific relations, calculations, persistence, backup/import values,
user data, and Room schema remain unchanged.

## Authority and sources

- Human-editable authority: workbook sheet `30_METADATA_DISPLAY_LABELS`
- Official anatomy/medical reference tier: National Institute of Korean
  Language terminology resources and established Korean clinical anatomy usage
- Sports-science tier: Korea Institute of Sport Science and Korean coaching
  terminology
- Professional tier: established Korean gym and coaching usage
- Formal/common conflicts: common clear label for display, official term in
  `koreanFormalLabel`, both forms in `koreanSearchAliases`
- Latin policy: row-level explicit allowlist only
- Product decision: `ESTIMATED_1RM` displays exactly `e1RM`

Source entry points used by registry rows:

- https://www.korean.go.kr/nkview/nklife/2007_1/2007_0102.pdf
- https://www.sports.re.kr/
- https://css.kspo.or.kr/front/center/intro.do

## Final counts

| Metric | Count |
|---|---:|
| totalDistinctProductionCodes | 1,683 |
| existingKoreanLabelsRetained | 748 |
| labelsRevised | 229 |
| newLabelsAdded | 706 |
| compatibilitySearchOnlyRows | 136 |
| officialAnatomySelections | 174 |
| commonProfessionalSelections | 1,454 |
| establishedLoanwordSelections | 54 |
| abbreviationPreservedSelections | 1 |
| reviewRequiredCount | 0 |
| unresolvedCount | 0 |
| rawUiExposureCountBefore | 376 |
| rawUiExposureCountAfter | 0 |

Source-tier totals across production rows are `TIER_1_OFFICIAL=174`,
`TIER_2_SPORTS_SCIENCE=1454`, `TIER_3_PROFESSIONAL_USAGE=54`, and
`PRODUCT_OWNER_DECISION=1`.

## Review-required rows

None. Every production row has a nonblank defensible Korean label. There are
no unresolved release fallbacks.

## Search and rendering result

Korean primary labels, Korean formal/common aliases, English labels/aliases,
and canonical codes resolve to one typed option. Search aliases do not create
duplicate options, and results render only the Korean primary label in Korean
locale. Free-form user text bypasses the catalogue.

The 376 inventory rows with prior direct UI exposure were routed through typed
catalogue lookups. Production rendering no longer uses a raw canonical token
fallback; unknown production codes fail coverage tests.
