# Optimized Debug: debugging enabled, R8 enabled

Status: experimental third APK build type (not Play distribution).

## Why

On the Galaxy S25, the user's previously tested `perf` APK felt almost entirely smooth during fast Record/Plan flings while the ordinary `debug` APK stuttered. This experiment tries to preserve JDWP/debug inspection while applying R8 optimization.

## Three build types built from the same commit

| Build type | Android debuggable | R8 minification | Intended use |
| --- | --- | --- | --- |
| `debug` | true | false | Full debugging / development |
| `optimizedDebug` | **true** | **true** | Experimental compromise: debugging plus R8 |
| `perf` | false | true | Non-debuggable performance baseline |

`optimizedDebug` starts from the debug build, uses debug dependency fallbacks, the default optimized Android ProGuard config, no resource shrinking, and the same CI test signing configuration as both existing APKs.

For comparison, all three preserve the same `applicationId = com.whatyougottado.app`, `versionCode`, database, settings and backups. They **cannot coexist**; install one at a time.

## Safety / testing

1. First verify an independent CSV backup. Do not uninstall the app or clear its storage to resolve installation errors.
2. Go to GitHub Actions for the matching PR or a manually dispatched workflow. Download `WhatYouGottaDo-debug-apk`, `WhatYouGottaDo-optimized-debug-apk`, and `WhatYouGottaDo-performance-apk` from the **same run**.
3. Install by `adb install -r <apk>` or Android's package installer if it accepts the update. CI checks that the optimized-debug APK has the same package and certificate as the debug APK. Existing installed APK might still have a different certificate; in that case stop rather than uninstalling.
4. With the same Record day, Plan program, screen refresh setting and warmup, compare 10–15 fast up/down flings for each variant. The `tools/perf/measure_scroll_frames.ps1` utility records p50/p90/p95/p99 frame times without touching app data.
5. Verify workout sets, RPE, existing confirmed status, plan links, sleep read permission, saved sleep hours and backup screen in the optimized-debug build.
6. Debugger is enabled, but R8 can inline, merge or remove code. This can change breakpoint locations and inspected local variables. The ordinary debug build remains necessary for precise source-level debugging.
7. If `optimizedDebug` stutters while `perf` is smooth, debuggable-runtime overhead (including different runtime optimizations) is likely significant; R8 alone is not sufficient. If both are smooth, consider retaining optimized-debug for routine tests.

CI validates: `assembleOptimizedDebug`, a nonempty R8 mapping, the `application-debuggable` manifest flag, package identity and matching debug certificate. No record logic, health logic, or backup scheduling is changed.
