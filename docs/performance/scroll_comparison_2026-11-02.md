# Scroll-jank comparison (Record / Plan)

Status: **measurement procedure** — no S25 device traces captured yet.

## Why

The only APK previously uploaded by CI was `assembleDebug`. Android's Compose team recommends measuring `LazyColumn` performance in non-debuggable release mode with R8 rather than assuming debug builds represent production scroll speed. See: https://developer.android.com/develop/ui/compose/lists

The `perf` build type is a **test-only** non-debuggable, R8-minified variant:
- Exactly the existing `com.whatyougottado.app` application ID and `versionCode`.
- CI signs it with the same CI debug certificate as `WhatYouGottaDo-debug-apk`, so upgrading between the two CI builds retains on-device data when an existing CI build is installed.
- It is **not** an official production release signature, not a Play-distribution artifact, and is **not** covered by automatic instrumentation tests on the actual Galaxy S25.
- If Android refuses the install due to signature mismatch, **do not uninstall or clear app data**. Stop and use the signed build that matches the installed application.
- It does not modify session records, auto-backup logic, or Health Connect queries.

## Manual comparison on the same S25

1. Preserve/verify the app's current manual CSV backup before changing APKs. Never uninstall or clear app storage during performance testing.
2. On the same GitHub Actions run, download `WhatYouGottaDo-debug-apk` and `WhatYouGottaDo-performance-apk`.
3. Install debug via `adb install -r path-to-debug.apk`. If it fails for any reason, stop; do not use `-d`, `-t`, or uninstall.
4. Leave the device display refresh settings unchanged; select a Record day with many workout sets and a Plan program with many exercises.
5. With Android SDK platform-tools installed, from repo root on Windows:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools/perf/measure_scroll_frames.ps1 -Label record-debug
   powershell -ExecutionPolicy Bypass -File tools/perf/measure_scroll_frames.ps1 -Label plan-debug
   ```
   The script resets gfxinfo counters for the specified package, then asks you to fling up/down 10–15 times. It saves the raw report and prints total frames, p50/p90/p95/p99 times and >8.33 ms / >16.67 ms counts.
6. Install the performance APK by `adb install -r path-to-performance.apk` (same CI certificate) and repeat the same Record and Plan scenarios with `record-perf` and `plan-perf` labels. Give the app comparable warmup time and screen content.
7. Compare the **p95 frame time** and proportion of frames exceeding the target for the configured display rate. One run is noisy: perform 3+ trials each, with the same dataset and screen.
8. If performance APK is smooth but debug APK isn't, do not prematurely modify training data or disable backup — treat debug overhead as primary and consider a proper release distribution path.
9. If both stutter, capture a Perfetto trace / Android Studio System Trace on the test device and inspect Compose composition/layout, binder IPC, Room, GC, graphics work and any background workers. Avoid personal health records in trace exports.

Notes:
- A 120 Hz frame period is approximately 8.33 ms; 60 Hz is 16.67 ms. `dumpsys gfxinfo framestats` reports timing observations, not necessarily display-presented FPS. Device refresh throttling or adaptive refresh affects interpretation.
- Record currently renders all sets **inside one workout-card list item**. That can be a structural bottleneck for dates containing very large set counts, even after the Plan day-card split in PR #40.
- A saved sleepHours value now prevents creation of the watch sleep reader (PR #40); do not assume Health Connect causes stutters when using filled condition dates.
- Performance APK still needs functional smoke testing for sign-in, watch permissions, recording and backups because R8 changes code generation.
