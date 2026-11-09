import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  canCleanObject, STALE_UPLOAD_MS, FAILED_OBJECT_GRACE_MS,
  RETIRED_BACKUP_GRACE_MS, PENDING_RETRY_MS, FAILURE_AUDIT_MS,
} from "../supabase/functions/_shared/backup_cleanup_policy.mjs";

const nowMs = Date.parse("2026-11-06T09:00:00Z");
const ago = (ms) => new Date(nowMs-ms).toISOString();
const base = { backup_id: "old", created_at: ago(2 * STALE_UPLOAD_MS) };
const flags = {
  nowMs, currentId: "current",
  protectedBackupIds: ["current", "retained1", "retained2"], retentionVerified: true,
};
test("only RETAINED outside the verified latest three is eligible", () => {
  assert.equal(canCleanObject({ ...base, status: "CURRENT" }, flags), false);
  assert.equal(canCleanObject({ ...base, backup_id: "retained1", status: "RETAINED" }, flags), false);
  assert.equal(canCleanObject({ ...base, backup_id: "retained2", status: "RETAINED" }, flags), false);
  assert.equal(canCleanObject({ ...base, status: "RETAINED" }, flags), true);
  assert.equal(canCleanObject({ ...base, status: "RETAINED" }, { ...flags, currentId: null }), false);
  assert.equal(canCleanObject({ ...base, status: "RETAINED" }, { ...flags, retentionVerified: false }), false);
  assert.equal(canCleanObject({ ...base, status: "RETAINED" }, {
    ...flags, protectedBackupIds: ["current", "retained1"],
  }), false);
  assert.equal(canCleanObject({ ...base, status: "RETAINED", created_at: ago(RETIRED_BACKUP_GRACE_MS-1) }, flags), false);
});
test("recent failed / active uploading backups cannot be swept", () => {
  for (const status of ["UPLOADING","FAILED"]) {
    assert.equal(canCleanObject({ ...base, status, created_at: ago(1_000) }, flags), false);
    assert.equal(canCleanObject({ ...base, status }, flags), true);
  }
  assert.equal(STALE_UPLOAD_MS, 24*60*60*1000);
  assert.equal(FAILED_OBJECT_GRACE_MS, 24*60*60*1000);
});
test("conflict backups expire only after recovery window", () => {
  assert.equal(canCleanObject({ ...base, status:"CONFLICT_RECOVERY", recovery_expires_at: ago(-1_000) }, flags), false);
  assert.equal(canCleanObject({ ...base, status:"CONFLICT_RECOVERY", recovery_expires_at: ago(1_000) }, flags), true);
});
test("pending deletion retries after fifteen minutes", () => {
  assert.equal(canCleanObject({ ...base, status:"CLEANUP_PENDING", cleanup_requested_at: ago(PENDING_RETRY_MS-1) }, flags), false);
  assert.equal(canCleanObject({ ...base, status:"CLEANUP_PENDING", cleanup_requested_at: ago(PENDING_RETRY_MS+1) }, flags), true);
  assert.equal(FAILURE_AUDIT_MS, 30*24*60*60*1000);
});
test("cleanup physically deletes from canonical UUID-scoped R2 path and keeps lineage tombstones", () => {
  const source = readFileSync("supabase/functions/_shared/backup_cleanup.mjs", "utf8");
  const upload = readFileSync("supabase/functions/cloud-backup-upload-url/index.ts", "utf8");
  const finalize = readFileSync("supabase/functions/cloud-backup-finalize/index.ts", "utf8");
  assert.match(source, /buildObjectKey\(userId, row\.backup_id\)/);
  assert.match(source, /expiredAuthorization\(service, row\.backup_id, nowIso\)/);
  assert.match(source, /DeleteObjectCommand/);
  assert.match(source, /status: "PRUNED"/);
  assert.match(source, /status: "CLEANUP_PENDING"/);
  assert.match(source, /protectedFilesPresent/);
  assert.match(source, /latestUnresolved/);
  assert.match(source, /preserveLatestUnresolved/);
  assert.match(upload, /cleanupUserBackups\(service, userId, 10\)/);
  assert.match(finalize, /cleanupUserBackups\(service, userId, 10\)/);
});

test("physical pruning remains emergency-pausable while protected-three checks are mandatory", () => {
  const source = readFileSync("supabase/functions/_shared/backup_cleanup.mjs", "utf8");
  assert.match(source, /CLOUD_BACKUP_PRUNING_ENABLED/);
  assert.match(source, /paused: true/);
  assert.match(source, /protectedRows\.retained\.length !== 2/);
  assert.match(source, /protectedBackupIds/);
});
