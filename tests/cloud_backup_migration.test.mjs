import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migration = await readFile(
  new URL("../supabase/migrations/20260917000000_cloud_backup_transport.sql", import.meta.url),
  "utf8",
);
const finalizeMigration = await readFile(
  new URL("../supabase/migrations/20260917010000_cloud_backup_finalize.sql", import.meta.url),
  "utf8",
);

test("transport migration defines the protocol metadata tables and current contract", () => {
  for (const table of [
    "profiles",
    "cloud_backups",
    "cloud_upload_authorizations",
    "cloud_backup_failures",
    "cloud_storage_state",
  ]) {
    assert.match(migration, new RegExp(`create table if not exists public\\.${table}\\s*\\(`));
  }
  assert.match(migration, /backup_format_version integer not null/);
  assert.match(migration, /schema_version integer not null/);
  assert.match(migration, /local_revision bigint not null/);
  assert.match(migration, /backup_format_version = 14 and schema_version = 13/);
  assert.match(migration, /compression text not null default 'GZIP'/);
  assert.match(migration, /between 0 and 20971520/);
  assert.match(migration, /checksum_algorithm = 'SHA-256'/);
  assert.match(migration, /status in \('UPLOADING', 'VERIFIED', 'CURRENT', 'RETAINED', 'CONFLICT_RECOVERY', 'FAILED'\)/);
});
test("transport migration enforces owner isolation and server-only lifecycle writes", () => {
  assert.match(migration, /foreign key \(parent_backup_id, user_id\)/);
  assert.match(migration, /references public\.cloud_backups \(backup_id, user_id\)/);
  assert.match(migration, /create unique index if not exists cloud_backups_one_current_per_user_idx/);
  assert.match(migration, /where status = 'CURRENT'/);
  assert.match(migration, /create policy profiles_select_own/);
  assert.match(migration, /using \(user_id = auth\.uid\(\)\)/);
  assert.match(migration, /create policy cloud_backups_select_own/);
  assert.match(migration, /revoke all on table public\.cloud_upload_authorizations from anon, authenticated/);
  assert.match(migration, /revoke all on table public\.cloud_backup_failures from anon, authenticated/);
  assert.match(migration, /revoke all on table public\.cloud_storage_state from anon, authenticated/);
  assert.doesNotMatch(migration, /R2_SECRET_ACCESS_KEY\s*=/i);
});

test("finalize migration provides a locked server-only atomic promotion", () => {
  assert.match(finalizeMigration, /cloud_promote_backup\(p_backup_id uuid, p_user_id uuid\)/);
  assert.match(finalizeMigration, /for update/gi);
  assert.match(finalizeMigration, /LINEAGE_CONFLICT/);
  assert.match(finalizeMigration, /status = 'RETAINED'/);
  assert.match(finalizeMigration, /status = 'CURRENT'/);
  assert.match(finalizeMigration, /revoke all on function/);
  assert.match(finalizeMigration, /grant execute on function .*service_role/);
});
