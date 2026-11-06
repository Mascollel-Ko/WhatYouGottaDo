import { readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";
test("storage guard reads actual R2 object sizes and fails closed", () => {
  const r2 = readFileSync("supabase/functions/_shared/r2.mjs","utf8");
  const api = readFileSync("supabase/functions/cloud-backup-upload-url/index.ts","utf8");
  assert.match(r2, /ListObjectsV2Command/);
  assert.match(r2, /listing\.Contents/);
  assert.match(r2, /listing\.NextContinuationToken/);
  assert.match(api, /STORAGE_CACHE_TTL_MILLIS = 10 \* 60 \* 1000/);
  assert.match(api, /measureR2BucketBytes/);
  assert.match(api, /STORAGE_USAGE_UNAVAILABLE/);
  assert.match(api, /cloud_storage_state/);
  assert.match(api, /measuredBytes >= GLOBAL_STORAGE_GUARD_BYTES/);
  assert.doesNotMatch(api, /storageState\?\.cached_total_bytes \?\? 0/);
});
