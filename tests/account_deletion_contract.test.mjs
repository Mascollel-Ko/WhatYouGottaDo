import { readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";

const read = (path) => readFileSync(path, "utf8");
test("account deletion is privileged, interactive and fails closed", () => {
  const edge = read("supabase/functions/account-delete/index.ts");
  assert.match(edge, /authenticateRequest\(request\)/);
  assert.match(edge, /verifyFreshGoogleProof\(userId, body\.google_id_token, body\.nonce\)/);
  assert.match(edge, /grant_type=id_token/);
  assert.match(edge, /body\.confirmation !== CONFIRMATION/);
  assert.match(edge, /account_deletion_contract_ready/);
  assert.match(edge, /UPLOAD_URLS_STILL_ACTIVE/);
  assert.match(edge, /removeR2PrivateObjects/);
  assert.match(edge, /auth\.admin\.deleteUser\(userId, false\)/);
  assert.ok(edge.indexOf("auth.admin.deleteUser") < edge.lastIndexOf("removeR2PrivateObjects"));
  assert.match(edge, /account_deletion_cleanup_jobs/);
  const gc = read("supabase/functions/cloud-backup-gc/index.ts");
  assert.match(gc, /getUserById/);
  assert.match(gc, /account_deletion_cleanup_jobs/);
  assert.match(gc, /removeR2PrivateObjects/);
  assert.match(edge, /backupCleanupPending/);
});
test("published programs are detached, all private user records cascade", () => {
  const migration = read("supabase/migrations/20261106000000_account_deletion_preserve_public_programs.sql");
  assert.match(migration, /alter column owner_user_id drop not null/);
  assert.match(migration, /on delete set null/);
  assert.match(migration, /account_deletion_requests enable row level security/);
  const community = read("supabase/functions/community-api/index.ts");
  assert.match(community, /탈퇴한 사용자/);
});
test("issued cloud backups cannot be created or finalized while deleting", () => {
  for (const route of ["cloud-backup-upload-url", "cloud-backup-finalize"]) {
    assert.match(read(`supabase/functions/${route}/index.ts`), /ACCOUNT_DELETION_PENDING/);
  }
  assert.match(read("supabase/config.toml"), /\[functions.account-delete\][\s\S]*verify_jwt = true/);
});
