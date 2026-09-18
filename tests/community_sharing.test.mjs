import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const endpoint = await readFile(new URL("../supabase/functions/community-api/index.ts", import.meta.url), "utf8");
const migration = await readFile(new URL("../supabase/migrations/20260918000000_community_sharing.sql", import.meta.url), "utf8");
const labelMigration = await readFile(new URL("../supabase/migrations/20260919000000_community_program_label_arrays.sql", import.meta.url), "utf8");
const securityMigration = await readFile(new URL("../supabase/migrations/20260920000000_community_security_hardening.sql", import.meta.url), "utf8");
const config = await readFile(new URL("../supabase/config.toml", import.meta.url), "utf8");

test("Community API is authenticated and keeps the private boundary", () => {
  assert.match(endpoint, /authenticateRequest\(request\)/);
  assert.match(endpoint, /const userId = auth\.user\.id/);
  assert.match(config, /\[functions\.community-api\]\s+verify_jwt = true/);
  assert.doesNotMatch(endpoint, /R2_|service_role|access_token|refresh_token/i);
  for (const operation of ["profile_get", "program_publish", "program_feed", "program_like", "weekly_publish", "friend_lookup", "friend_request_send", "friend_respond", "activity_update"]) {
    assert.match(endpoint, new RegExp(`case "${operation}"`));
  }
});

test("Community migration protects rows and server-authoritative aggregates", () => {
  for (const table of [
    "community_profiles", "community_programs", "community_program_exercises",
    "community_program_likes", "community_weekly_summaries", "community_friend_requests",
    "community_friendships", "community_blocks", "community_friend_activity",
    "community_friend_code_lookup_attempts",
  ]) {
    assert.match(migration, new RegExp(`create table if not exists public\\.${table}\\s*\\(`));
    assert.match(migration, new RegExp(`alter table public\\.${table} enable row level security`));
    assert.match(migration, new RegExp(`revoke all on table public\\.${table} from anon, authenticated`));
  }
  assert.match(migration, /community_profiles_nickname_unique/);
  assert.match(migration, /community_consume_friend_code_lookup/);
  assert.match(migration, /community_toggle_program_like/);
  assert.match(migration, /received_program_likes/);
});

test("Community DTO code does not expose account UUIDs or private backup fields", async () => {
  const models = await readFile(new URL("../app/src/main/java/com/training/trackplanner/data/CommunityModels.kt", import.meta.url), "utf8");
  for (const forbidden of ["userId", "ownerUserId", "email", "backupSourceId", "sessionStableKey", "objectKey"]) assert.doesNotMatch(models, new RegExp(forbidden));
});

test("Community publication and structured filters remain server validated", () => {
  assert.match(endpoint, /labelArray\(input\.strengthRegions/);
  assert.match(endpoint, /labelArray\(input\.strengthGoals/);
  assert.match(endpoint, /labelArray\(input\.functionalGoals/);
  assert.match(endpoint, /labelArray\(input\.badmintonGoals/);
  assert.match(endpoint, /overlaps\(column/);
  assert.match(endpoint, /NOT_INCLUDED/);
  assert.match(endpoint, /if \(notIncluded && include\.length === 0\)/);
  assert.match(endpoint, /\.eq\.\{\}/);
  assert.match(labelMigration, /strength_regions text\[\]/);
  assert.match(labelMigration, /functional_goals text\[\]/);
  assert.match(labelMigration, /community_programs_strength_regions_array/);
  assert.match(labelMigration, /community_program_label_arrays_sync/);
  assert.match(labelMigration, /set strength_regions = array\[strength_region\]/);
  assert.match(labelMigration, /set functional_goals = case/);
  assert.match(endpoint, /authorComment/);
  assert.match(endpoint, /cautionText/);
});

test("Community imports use installation-local provenance without backup coupling", async () => {
  const codec = await readFile(new URL("../app/src/main/java/com/training/trackplanner/data/CommunityProgramSnapshotCodec.kt", import.meta.url), "utf8");
  const entity = await readFile(new URL("../app/src/main/java/com/training/trackplanner/data/Entities.kt", import.meta.url), "utf8");
  assert.match(codec, /CommunityProgramImport/);
  assert.match(entity, /community_program_imports/);
  assert.doesNotMatch(codec, /backupSourceId|sessionStableKey/);
});

test("Community SECURITY DEFINER functions are closed to direct RPC", () => {
  for (const signature of [
    "community_ensure_profile(uuid)",
    "community_consume_friend_code_lookup(uuid)",
    "community_toggle_program_like(uuid, uuid, boolean)",
    "community_recalculate_like_totals()",
    "community_recalculate_program_author_total()",
    "community_touch_updated_at()",
    "community_sync_program_label_arrays()",
    "community_label_array_has_no_duplicates(text[])",
  ]) {
    assert.ok(securityMigration.includes(`revoke execute on function public.${signature}`));
  }
  assert.match(securityMigration, /from public, anon, authenticated/);
  assert.match(securityMigration, /set search_path = pg_catalog/);
  for (const rpc of ["community_ensure_profile", "community_consume_friend_code_lookup", "community_toggle_program_like"]) {
    assert.match(securityMigration, new RegExp(`grant execute on function public\\.${rpc}`));
  }
  assert.match(securityMigration, /from public, anon, authenticated, service_role/);
});
