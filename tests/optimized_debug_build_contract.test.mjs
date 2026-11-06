import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const gradle = fs.readFileSync("app/build.gradle.kts", "utf8");
const workflow = fs.readFileSync(".github/workflows/android-debug-build.yml", "utf8");

test("Optimized Debug retains debugging while enabling R8 optimization", () => {
  const start = gradle.indexOf('create("optimizedDebug") {');
  const end = gradle.indexOf('create("perf") {', start);
  assert.ok(start >= 0 && end > start, "standalone optimizedDebug build type");
  const variant = gradle.slice(start, end);
  assert.match(variant, /initWith\(getByName\("debug"\)\)/);
  assert.match(variant, /isDebuggable = true/);
  assert.match(variant, /isMinifyEnabled = true/);
  assert.match(variant, /getDefaultProguardFile\("proguard-android-optimize\.txt"\)/);
  assert.match(variant, /signingConfigs\.getByName\("ciDebug"\)/);
  assert.doesNotMatch(variant, /applicationIdSuffix|versionNameSuffix|isDebuggable = false/);
});

test("CI builds and verifies a third installable APK without replacing the other two", () => {
  assert.match(workflow, /:app:assembleOptimizedDebug/);
  assert.match(workflow, /WhatYouGottaDo-optimized-debug-apk/);
  assert.match(workflow, /outputs\/mapping\/optimizedDebug\/mapping\.txt/);
  assert.match(workflow, /application-debuggable/);
  assert.match(workflow, /Optimized Debug signer does not match regular Debug signer/);
  assert.match(workflow, /WhatYouGottaDo-performance-apk/);
  assert.match(workflow, /WhatYouGottaDo-debug-apk/);
});
