# Metadata Gap For v0.3.5 Analysis

## Ready Enough
- Structured movement, muscle, fatigue, badminton transfer, and progression fields exist.
- Image presence does not affect analysis and is safely optional.

## Gaps To Review
- 34 exercises still have no safe image mapping. This is UI review, not analysis blocking.
- StableKey normalization remains mixed for existing DB rows because this patch preserves existing row identity.
- Pain/condition-specific fields are still limited to existing app inputs; no new diagnosis-oriented fields were added.

## Do Not Add Yet
- New injury prediction labels.
- New chart-only tags.
- New analysis categories without reviewed formulas.