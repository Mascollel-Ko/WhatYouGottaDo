import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const read = path => fs.readFileSync(path, "utf8");

test("P1 uses one normal launcher and no debug watch launcher", () => {
  const manifest = read("app/src/main/AndroidManifest.xml");
  assert.match(manifest, /\.MainActivity/);
  assert.doesNotMatch(manifest, /WatchDiagnosticsActivity/);
  assert.equal(fs.existsSync("app/src/debug/AndroidManifest.xml"), false);
});

test("P1 declares only required exercise and sleep read permissions", () => {
  const manifest = read("app/src/main/AndroidManifest.xml");
  assert.match(manifest, /android.permission.health.READ_EXERCISE/);
  assert.match(manifest, /android.permission.health.READ_SLEEP/);
  assert.doesNotMatch(manifest, /android.permission.health.WRITE_/);
  assert.doesNotMatch(manifest, /android.permission.health.READ_HEART_RATE/);
});

test("Production uses Health Connect and records no raw samples", () => {
  const gradle = read("app/build.gradle.kts");
  const gateway = read("app/src/main/java/com/training/trackplanner/health/WatchHealthData.kt");
  assert.match(gradle, /implementation\("androidx.health.connect:connect-client:1\.1\.0"\)/);
  assert.match(gateway, /readRecords/);
  assert.doesNotMatch(gateway, /insertRecords|updateRecords|deleteRecords|HeartRateRecord|Log\./);
});

test("Sleep writes use empty-only guard on latest check-in", () => {
  const source = read("app/src/main/java/com/training/trackplanner/data/DailyStatusService.kt");
  assert.match(source, /fillSleepHoursIfEmpty/);
  assert.match(source, /latest\?\.sleepHours != null/);
});

test("P1 auto sport prompts exclude walking and never touch confirmed", () => {
  const dialog = read("app/src/main/java/com/training/trackplanner/health/WatchWorkoutImportDialog.kt");
  assert.match(dialog, /EXERCISE_TYPE_WALKING|session.walking/);
  assert.doesNotMatch(dialog, /confirmed\s*=\s*true/);
  assert.match(dialog, /matches\(target.entry/);
});

test("Sleep permission grant and auto-fill remain separate actions", () => {
  const screen = read("app/src/main/java/com/training/trackplanner/health/WatchSleepConditionEntry.kt");
  assert.match(screen, /워치 수면 연동/);
  assert.match(screen, /수면 자동 채우기 켜기/);
  assert.match(screen, /fillSleepHoursIfEmpty/);
});

test("Watch sleep import offers explicit feedback and selection without duplicate picker", () => {
  const screen = read("app/src/main/java/com/training/trackplanner/health/WatchSleepConditionEntry.kt");
  assert.match(screen, /if \(lookupDialog\)/);
  assert.match(screen, /수면 기록이 없습니다\./);
  assert.match(screen, /sessions\.size == 1/);
  assert.match(screen, /sessions\.forEach/);
  assert.match(screen, /WatchSleepAggregate\.combine\(chosen\)/);
  assert.match(screen, /replacementMinutes = minutes/);
  assert.match(screen, /launcher\.launch\(setOf\(WatchHealthData\.SLEEP_PERMISSION\)\)/);
  assert.doesNotMatch(screen, /pickerOpen/);
});

test("Daily sleep candidate uses previous 21:00 through current 12:00 only", () => {
  const gateway = read("app/src/main/java/com/training/trackplanner/health/WatchHealthData.kt");
  assert.match(gateway, /date\.minusDays\(1\)\.atTime\(21, 0\)/);
  assert.match(gateway, /date\.atTime\(12, 0\)/);
  assert.match(gateway, /maxOf\(record\.startTime, from\)/);
  assert.match(gateway, /minOf\(record\.endTime, to\)/);
});

test("Explicit replacement updates only daily sleepHours", () => {
  const service = read("app/src/main/java/com/training/trackplanner/data/DailyStatusService.kt");
  assert.match(service, /replaceSleepHoursFromWatch/);
  assert.match(service, /\(latest \?: DailyCheckIn\(date = date\)\)\.copy\(sleepHours = hours\)/);
});
