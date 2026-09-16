# September 2026 backup compatibility fixture

Scope: the September pre-Phase-1-A **format 13 / restore schema 12** contract and
its upgrade to current **format 14 / restore schema 13**, with Room 32 unchanged.
No older-format fixtures are added by this follow-up.

## Historical provenance

Historical writer: `app/src/main/java/com/training/trackplanner/data/RecordCsvBackupRestore.kt`
at `d7ee94a5e35fbd76a4d756245aa48f62dd29ccfd` (2026-09-06, app 0.5.1.4).
It declares format 13, restore schema 12 and program schema 2. Compare with Phase 1-A
baseline `b742693b8e4dc0ea2207b0913e26f27f0db56d2e` using `git show` / `git diff`.

`september-format-13.csv` is frozen synthetic test data, not a real-user file.
Construction used the existing full program/progression test scenario and projected
its values onto the exact 149-column historical writer header. This removes the
session column entirely, rather than renaming it or leaving a current-only column.
Mapped rows carry schema 12; profile/check-in/smash/runtime rows retain their historical
row-specific schema values. The historical manifest framing, sorted entity counts,
capability markers and normalized-body SHA-256 are preserved.

Source comparison confirms `buildRestoreCsv` changed only by adding the session value
between this historical writer and Phase 1-A (with the associated header and version
constants outside that function). The fixture therefore uses the historical column
and capability contract, not an arbitrary current file with only version numbers
changed. It was not produced by executing an old APK. Tests load the frozen resource;
no fixture generation or second canonical serializer runs in the app or test suite.

## Included authority

- Six workout entries on three September dates, two entries per date, twelve sets;
  existing entry source identities, stored completion/performance timestamps and
  exercise stable identity; no session keys. Completed entries carry timestamps as
  ordinary application edits would, avoiding the synthetic scenario's direct-DAO
  omission that triggers existing restore timestamp backfill.
- Program schema 2 program/items/exact sets and a program tombstone.
- All six execution/progression row types: application, item, link, prescription,
  suggestion and track.
- Exercise/runtime metadata, typed snapshots and explicit override, portable app
  metadata, profile, daily/check-in, and smash-speed parent source reference.
- Historical empty derived-strength manifest; current restore still rebuilds derived
  strength state under the existing policy.

The new test checks source IDs/workout values before and after normalization, valid
same-date/different-date session grouping, program/exercise/progression authority,
metadata, current format/schema, absence of Cloud lineage, and exact session/source
identity through a clean second restore. Current export/restore/export comparisons
cover all currently authoritative sections. Existing Phase 1-A tests (including
manual export using the common canonical builder) are retained unchanged.

## Audit note outside the expanded test scope

The earlier history audit found that `v0.5.0.6`
(`c825a4b941032b62c81b0860780fb5e6f0968efa`) uses **format 8 / restore schema 7**,
not 8/8. No new formats 8–12 compatibility fixtures or claims are included here;
existing parser support and tests remain unchanged.

## Frozen file integrity

SHA-256 of the whole fixture: `41200745cfc6c46a50227eb353d4a3e23f9dae1f5305844910c9b20b47aa23af`.
The CSV manifest separately checks the normalized uncompressed body. This inner
manifest hash is distinct from the future Cloud SHA-256 over actual gzip bytes.

## Validation

Focused Gradle run: `WorkoutSessionIdentityTest`, `*Backup*Test`, `*Progression*Test`:
156 tests, 153 passed, 3 optional real-data tests skipped, zero failures/errors.
All 12 session tests passed, including the new frozen September upgrade test and
existing current-format/manual canonical export comparisons. Protocol validation:
8 families / 35 protocols passed. Production sources, persistence schemas, parser
support and historical Phase 1-A release notes are unchanged.
