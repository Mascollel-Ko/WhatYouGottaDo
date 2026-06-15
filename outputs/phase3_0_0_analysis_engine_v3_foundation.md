# Phase 3.0.0 Analysis Engine V3 Foundation

Date: 2026-06-15

## Scope

This patch prepares the V3 analysis engine without exposing new analysis methods in the user UI.

Implemented:

- Analysis date provider based on the device current date.
- Actual record and future plan input separation.
- Standard analysis windows.
- Common load, strength, taxonomy, and future plan projection metrics.
- Analysis method interface and result model.
- Sentence builder interface and sentence policy.
- V3 facade result structure.
- Disabled analysis method registry for future 3.1.0+ patches.
- Paper review templates for planned analysis methods.

Not implemented:

- Workload / Recovery judgment.
- Strength Load judgment.
- Badminton Transfer judgment.
- Balance / Safety judgment.
- Plan Adherence judgment.
- New user-facing analysis cards or sentences.
- New thresholds derived from papers.

## Date Rule

Analysis V3 uses `AnalysisDateProvider.today()`.

The default provider is `SystemAnalysisDateProvider`, which returns `LocalDate.now()`. Test and debug code can use `FixedAnalysisDateProvider`.

This prevents the app from treating the last workout date as today.

## Record / Plan Split

`AnalysisInputCollector` separates inputs as follows:

- `completedEntriesUntilToday`: entries with `date <= today`, containing only `confirmed=true` sets.
- `plannedEntriesFromTomorrow`: entries with `date > today`, containing only `confirmed=false` sets.
- Today planned but unconfirmed sets are not mixed into completed load.
- Future plans are not mixed into completed load.

## Common Metrics

The common metric layer computes neutral values only.

- `CommonLoadMetrics`: daily load candidate, weekly load, chronic load, recent/usual ratio, training days, rest days, monotony candidate, strain candidate.
- `CommonStrengthMetrics`: volume load, movement and muscle maps, hard set candidate count, unilateral count, balance ratios.
- `CommonTaxonomyMetrics`: taxonomy distributions and unknown buckets.
- `CommonPlanProjectionMetrics`: future plan session count, future training/rest days, future load candidates, future taxonomy distributions.

These classes do not create final judgments and do not render UI messages.

## Analyzer Extension

`AnalysisMethodRegistry` lists planned method ids but keeps all new methods disabled:

- `workload_recovery_v1`
- `strength_load_v1`
- `badminton_transfer_v1`
- `balance_safety_v1`
- `plan_adherence_v1`

Future patches should add one analyzer at a time and enable it only after method review.

## UI Relationship

The current analysis tab remains the existing simple confirmed-set summary.

V3 output is available for debug/log verification only. It is not shown to normal users in this patch.

## Data Safety

This patch only adds read-only DAO queries and analysis classes. It does not delete, rewrite, or migrate user records, plans, exercises, or metadata.
