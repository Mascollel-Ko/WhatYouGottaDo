import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const root = process.cwd();
const read = path => fs.readFileSync(root + "/" + path, "utf8");

test("P0 health read permissions are absent from production source manifest", () => {
  const manifest = read("app/src/main/AndroidManifest.xml");
  assert.doesNotMatch(manifest, /android\.permission\.health\.READ_(EXERCISE|HEART_RATE)/);
  assert.doesNotMatch(manifest, /WatchDiagnosticsActivity/);
});

test("P0 health read permissions are declared only for debug", () => {
  const manifest = read("app/src/debug/AndroidManifest.xml");
  assert.match(manifest, /android\.permission\.health\.READ_EXERCISE/);
  assert.match(manifest, /android\.permission\.health\.READ_HEART_RATE/);
  assert.match(manifest, /androidx\.health\.ACTION_SHOW_PERMISSIONS_RATIONALE/);
  assert.match(manifest, /VIEW_PERMISSION_USAGE/);
});

test("Health Connect library exists only in debug dependency configuration", () => {
  const gradle = read("app/build.gradle.kts");
  assert.match(gradle, /debugImplementation\("androidx\.health\.connect:connect-client:1\.1\.0"\)/);
  assert.doesNotMatch(gradle, /^\s*implementation\("androidx\.health\.connect/m);
});

test("P0 implementation does not write health records or change workouts", () => {
  const gateway = read("app/src/debug/java/com/training/trackplanner/health/WatchHealthConnectGateway.kt");
  assert.match(gateway, /readRecords\(/);
  assert.doesNotMatch(gateway, /insertRecords|updateRecords|deleteRecords|WorkoutSet|WorkoutEntry|Log\./);
});

test("P0 normal Record screen remains untouched by debug-only entry point", () => {
  const manifest = read("app/src/debug/AndroidManifest.xml");
  assert.match(manifest, /android\.intent\.action\.MAIN/);
  assert.match(manifest, /android\.intent\.category\.LAUNCHER/);
});
