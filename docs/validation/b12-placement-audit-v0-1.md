# B12 placement and deletion audit v0.1 — 2026-10-22

## Scope and authority

This change modifies only the live B12 route. A/C and the frozen Python B comparator remain unchanged.

- The generator no longer truncates B12 sets to satisfy the old time approximation.
- A B request for three days may propose four days **only if** the user permits it using "B 최대 운동일수" and the deterministic placement heuristic finds a meaningful reduction in estimated peak session time.
- With no explicit maximum-training-days setting (e.g. old callers/tests), the original requested weekday identities stay unchanged.
- Moving a whole exercise item preserves its stableKey, original set records, physical set count and source provenance.
- Same stableKey on the same day is rejected for four-day expansion; heavy barbell exercises on the same day receive a strong penalty.
- B12 records whether days changed and the planning assumptions in planningMetadataJson.placementAudit.
- deletionAudit is **review-only**. It computes the counterfactual effect of removing all weekly sessions of each exercise; **no exercise is actually deleted**.
- Main strength exposures, custom/unresolved metadata and tasks with unresolved authority are protected or marked unresolved. No named-exercise synonym guesses or fabricated personal-response values.
- User edits after generation continue to re-evaluate only; they do not invoke redistribution or automatic replacement.

## Limits of the pilot

The estimate includes a generic warmup, explicit anchor setup/ramp allowances, per-exercise setup, observed-or-default set durations, rest between sets and an uncertainty buffer. Constants are engineering assumptions, **not measured personal durations or a statistically validated bound**. A plan with estimated peak under 60 minutes is **not certified** to finish within 60 minutes.

This version does not call the canonical PlanDayProjection or tissue recovery evaluator. Relative placement penalties for heavy lifts, adjacent lower-body sessions and repeated same-key items are only a screening heuristic, **not exercise science authority** or a claim that the new schedule is safe. Those integrations remain a separate, explicit review step.

deletionAudit.importanceWeightedLoss is a **relative review ranking**, not a physiological dose-response prediction. If a direct primary muscle or reviewed core task would vanish, a large protective review cost is added. There is no auto-removal, no automatic repayment of dropped sets in subsequent programs, and no inference that missing metadata indicates zero value.

## Review checklist

1. With 3 requested days and 4 allowed, compare the B12 output's effective day count, session-level physical sets and total weekly workload.
2. With 3 requested days and no expansion authorization, verify no automatic fourth day appears.
3. Verify that every current set is retained, that duplicated same-key days are rejected for expanded schedules, and that historical kilograms remain USER_CALIBRATION_REQUIRED.
4. Inspect every deletionAudit status and require explicit review before enabling any removal action.
5. Inspect placementAudit's time warning, candidate evaluation count and lack of canonical OFI/tissue clearance.
6. Run JVM/unit tests and APK/Actions; compare a representative real-history fixture on an Android device before merging.

## B-only new-program UI cleanup — 2026-10-23

- Only B balanced (12-week) is selectable on the new-program screen. A/C mode, C focus-key, and duration dropdowns have been removed from that route.
- The existing weekly training-day selector (1–7), optional 3-to-4-day expansion, session duration and four goal importance controls are unchanged.
- The new UI constructs its request via `newBBalancedGenerationRequest`, which explicitly fixes mode to B and weeks to 12.
- Historical A/C programs remain available for load, preview, edit and persistence; the legacy parity engine and V22Mode enum are deliberately kept. No database migration or silent history rewrite.
- Existing A/C parity and integration tests must continue to pass.
