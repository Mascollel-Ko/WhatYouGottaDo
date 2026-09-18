import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const endpoint = await readFile(new URL("../supabase/functions/community-api/index.ts", import.meta.url), "utf8");
const migration = await readFile(new URL("../supabase/migrations/20260918000000_community_sharing.sql", import.meta.url), "utf8");
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
