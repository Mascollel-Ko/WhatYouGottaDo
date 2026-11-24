# B-Coach weekday draft editing

Base: 4065327cb1fc50498f01d698ed623ea0a142f942

- Review weekday values are Monday-based slots; display real localized weekday names, and use a compact single-row selector.
- Generated candidate schedules are derived from actual items (manual empty-slot behavior is unchanged).
- When both early seeds violate the existing avoidable clustering constraint, evaluate non-clustered weekday permutations instead of searching only their existing days.
- On the accepted pre-save draft, long-press/drag to a vacant day, center swap, left/right insertion. Shift only the necessary chain, use the opposite side at a boundary, never wrap Sunday to Monday.
- All weeks receive the same bijective mapping; per-week dose and logical identity stay intact, and recovery-block boundaries are protected.
- Store the reversible user permutation separately from the original approval. Save validation reconstructs the original layout and retains the accepted structure and evidence checks. Candidate scores are marked stale after a manual change.
- Add model regressions for both requested examples, exhaustive slot permutations, all-week distinct prescriptions, and approval integrity; generated candidates must match their weekday schedule.

CI and real-device results must be checked against the actual branch commit; this document does not assert unexecuted tests passed.
