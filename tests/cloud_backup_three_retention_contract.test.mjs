import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const gc = readFileSync("supabase/functions/cloud-backup-gc/index.ts", "utf8");
const retention = readFileSync("supabase/functions/_shared/r2_three_retention.mjs", "utf8");
const cleanup = readFileSync("supabase/functions/_shared/backup_cleanup.mjs", "utf8");
const policy = readFileSync("supabase/functions/_shared/backup_cleanup_policy.mjs", "utf8");

test("manual R2 prune requires server-minted ticket and auditable dry-run", () => {
  assert.match(gc, /cloud_consume_cleanup_ticket/);
  assert.match(gc, /body\.audit_three === true/);
  assert.match(gc, /body\.prune_to_three === true/);
  assert.match(gc, /reconcileR2Three/);
  assert.match(retention, /dryRun = true/);
});

test("three protected R2 payloads are checked before any destructive pass", () => {
  assert.match(retention, /PROTECTION_COUNT = 3/);
  assert.match(retention, /retained\.length !== 2/);
  assert.match(retention, /verifyProtectedBytes/);
  assert.match(retention, /crypto\.subtle\.digest\("SHA-256"/);
  assert.match(retention, /for \(const row of protectedRows\)/);
  assert.match(retention, /const eligible = objects\.filter/);
  assert.ok(retention.indexOf("for (const row of protectedRows)") <
    retention.indexOf("for (const object of eligible)"));
});

test("never remove current, unpromoted verified, or active recovery backups", () => {
  assert.match(retention, /row\.status === "CURRENT"/);
  assert.match(retention, /row\.status === "VERIFIED"/);
  assert.match(retention, /PENDING_GRACE_MS/);
  assert.match(retention, /row\.status === "CONFLICT_RECOVERY"/);
  assert.match(retention, /Date\.parse\(row\.recovery_expires_at\) > now/);
});

test("pruning scopes exact per-account R2 keys and preserves ancestry as PRUNED rows", () => {
  assert.match(retention, /buildObjectKey\(userId, row\.backup_id\)/);
  assert.match(retention, /DeleteObjectCommand/);
  assert.match(retention, /status: "CLEANUP_PENDING"/);
  assert.match(retention, /status: "PRUNED"/);
  assert.match(retention, /remaining\.length === PROTECTION_COUNT/);
  assert.match(cleanup, /protectedRows\.retained\.length !== 2/);
  assert.match(policy, /protectedBackupIds\.length === 3/);
});
