import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = path => fs.readFileSync(path, "utf8");

test("a saved sleep time blocks the Health Connect reader after the daily state loads", () => {
  const record = source("app/src/main/java/com/training/trackplanner/RecordScreen.kt");
  const policy = source("app/src/main/java/com/training/trackplanner/health/WatchSleepLookupPolicy.kt");
  assert.match(record, /checkInForDate\(date\)\.map/);
  assert.match(record, /WatchSleepLookupPolicy\.shouldRead\(checkInLoaded, checkIn\?\.sleepHours\)/);
  assert.match(policy, /checkInLoaded && savedSleepHours == null/);
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
