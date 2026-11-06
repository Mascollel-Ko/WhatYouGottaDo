import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

test("hourly backup cleanup is ticket-gated, not public or app-token gated", () => {
  const sql = readFileSync("supabase/migrations/20261106010000_cloud_backup_cleanup_lifecycle.sql","utf8");
  const edge = readFileSync("supabase/functions/cloud-backup-gc/index.ts","utf8");
  const config = readFileSync("supabase/config.toml","utf8");
  assert.match(sql, /cloud_cleanup_tickets enable row level security/);
  assert.match(sql, /revoke all on public\.cloud_cleanup_tickets from public, anon, authenticated/);
  assert.match(sql, /consumed_at is null/);
  assert.match(sql, /issued_at >= now\(\) - interval '5 minutes'/);
  assert.match(sql, /cron\.schedule\(/);
  assert.match(sql, /'0 \* \* \* \*'/);
  assert.match(sql, /net\.http_post\(/);
  assert.match(edge, /cloud_consume_cleanup_ticket/);
  assert.match(edge, /verified\.data !== true/);
  assert.match(edge, /cleanupUserBackups\(service, userId, 50\)/);
  assert.match(config, /\[functions.cloud-backup-gc\]\nverify_jwt = false/);
});
