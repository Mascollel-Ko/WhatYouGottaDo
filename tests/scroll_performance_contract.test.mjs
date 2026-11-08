import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = path => fs.readFileSync(path, "utf8");

test("saved sleep defers Health Connect reads until explicit user action", () => {
  const record = source("app/src/main/java/com/training/trackplanner/RecordScreen.kt");
  assert.match(record, /checkInForDate\(date\)\.map/);
  assert.match(record, /if \(checkInLoaded\)/);
  assert.match(record, /savedHours = checkIn\?\.sleepHours/);
  const health = source("app/src/main/java/com/training/trackplanner/health/WatchSleepConditionEntry.kt");
  assert.match(health, /savedHours != null && !manualLookup/);
  assert.match(health, /manualLookup = true/);
  assert.match(health, /return@LaunchedEffect/);
  assert.doesNotMatch(record, /WatchSleepLookupPolicy[.]shouldRead/);
});

test("plan detail lazily composes each exercise separately from its day heading", () => {
  const screen = source("app/src/main/java/com/training/trackplanner/PlanScreen.kt");
  const detail = screen.slice(screen.indexOf("private fun ProgramDetailScreen("), screen.indexOf("private fun ProgramApplyCard("));
  assert.match(detail, /groupedKeys\.forEach/);
  assert.match(detail, /ProgramDaySummaryHeader\(/);
  assert.match(detail, /contentType = \{ "program-exercise" \}/);
  assert.match(detail, /ProgramDaySummaryExerciseCard\(/);
  assert.doesNotMatch(detail, /ProgramDaySummarySection\(/);
});

test("record reorder gesture is not restarted when set values change", () => {
  const record = source("app/src/main/java/com/training/trackplanner/RecordScreen.kt");
  assert.match(record, /pointerInput\(entryId, selectedDate, sortedEntryIds\)/);
  assert.doesNotMatch(record, /pointerInput\(entryId, selectedDate, sortedEntries\)/);
});

test("existing sport, sleep calculation and backup rules remain unchanged", () => {
  const plan = source("app/src/main/java/com/training/trackplanner/PlanProgramSections.kt");
  assert.match(plan, /ProgramSetPrescriptionResolver\.resolve\(item, storedSets\)/);
  const health = source("app/src/main/java/com/training/trackplanner/health/WatchSleepConditionEntry.kt");
  assert.match(health, /fillSleepHoursIfEmpty\(/);
});
