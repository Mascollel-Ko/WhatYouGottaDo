# P0: Galaxy Watch 7 Health Connect read-only diagnostic implementation

Date: 2026-10-27
Status: **Code proposed; CI/build and physical-device verification pending**
Design authority: [health_connect_watch_time_capture_design_2026-10-26.md](health_connect_watch_time_capture_design_2026-10-26.md)

## Exact scope

P0 debug build only. No production watch time matching, suggestions, confirmed-set changes, RPE edits, OFI changes, badminton workload changes, Room migration, CSV format changes, automatic background reads, or new cloud uploads.

The debug APK contains a separate launcher named **워치 기록 진단 (DEBUG)**. Enter explicitly; the normal app launcher and Record/Plan screens are unchanged. The diagnostics use only the official Health Connect API.

- READ_EXERCISE request and READ_HEART_RATE request are separate opt-in actions.
- Health Connect availability check precedes client creation. Unsupported installations keep the main app functional.
- Exercise sessions are read for the local selected day, including sessions starting before the day and crossing midnight. Data source, type code, start/end instants and elapsed seconds are shown.
- Heart-rate source count, first/last timestamp, minimum/maximum/arithmetic mean and gaps of 10 or more minutes are displayed. Same-timestamp duplicates remain visible; missing samples are never filled with invented values.
- Paginated reads use bounded 250-record pages with an explicit truncated result after 60 pages.
- Query cancellation on changed date, explicit refresh, partial permission, lack of data and query errors are distinguished.
- Raw heart-rate samples are temporary in-memory values only; no storage, log, backup, network request or database writes.
- Permission rationale activity and Android 14 permission-usage alias are debug-only, as are the sensitive permissions.
- READ_HEART_RATE is not required to inspect exercise sessions, and vice versa.
- Release manifest has no Health Connect read permission, SDK in debugImplementation only.

## Versions and build effect

- `androidx.health.connect:connect-client:1.1.0` (stable) for debug variant.
- Official Google documentation requires `compileSdk = 36` and AGP `8.9.1` or later with SDK 1.1.0.
- Therefore Gradle plugin is changed from 8.7.3 to 8.9.1; compileSdk from 35 to 36; targetSdk 35, minSdk 26 and app version remain unchanged.
- Main/release source code does not import Health Connect. Toolchain changes also affect release compilation and require CI verification.

## Added/modified code

- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/training/trackplanner/health/WatchDiagnosticsActivity.kt`
- `app/src/debug/java/com/training/trackplanner/health/WatchHealthConnectGateway.kt`
- `app/src/debug/java/com/training/trackplanner/health/WatchDiagnosticMath.kt`
- `app/src/test/java/com/training/trackplanner/health/WatchDiagnosticMathTest.kt`
- `tests/watch_p0_release_isolation.test.mjs`
- `build.gradle.kts`, `app/build.gradle.kts`
- `.github/workflows/android-debug-build.yml` adds a debug/release isolation check to existing Node tests.

## Verification targets

- `node --test tests/watch_p0_release_isolation.test.mjs`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew compileReleaseKotlin`
- GitHub Actions: Android Debug Build — test and build.

The owner may use the GitHub Actions debug APK artifact, install it on the Samsung phone, grant Health Connect permissions, then inspect an exercise-mode badminton session and a non-exercise-mode day.

## Physical Watch 7 test still required

1. Samsung Health sync after watch exercise.
2. Confirm Health Connect permission status and source package.
3. Confirm exerciseType raw code: dedicated BADMINTON or other.
4. Check start/end and time crossing midnight when relevant.
5. Run with heart-rate permission separately; compare available sample coverage while exercising with/without starting exercise mode.
6. Record sync lag and any missing/capped results.
7. Health Connect may not contain records even while Samsung Health itself displays them. This must not be interpreted as no exercise.

**No claim of successful watch read or exercise inference is made until this device test is observed.**
