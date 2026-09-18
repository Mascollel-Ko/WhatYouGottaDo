-- Community base tables are not a direct PostgREST API.  All reads and
-- mutations go through the authenticated community-api service role.

-- The original migrations granted the required RPCs to service_role, but
-- PostgreSQL PUBLIC retains default EXECUTE unless it is explicitly revoked.
revoke execute on function public.community_ensure_profile(uuid)
  from public, anon, authenticated;
revoke execute on function public.community_consume_friend_code_lookup(uuid)
  from public, anon, authenticated;
revoke execute on function public.community_toggle_program_like(uuid, uuid, boolean)
  from public, anon, authenticated;

-- Trigger-only functions and the label CHECK helper are never externally
-- callable, including by service_role.
revoke execute on function public.community_touch_updated_at()
  from public, anon, authenticated, service_role;
revoke execute on function public.community_recalculate_like_totals()
  from public, anon, authenticated, service_role;
revoke execute on function public.community_recalculate_program_author_total()
  from public, anon, authenticated, service_role;
revoke execute on function public.community_sync_program_label_arrays()
  from public, anon, authenticated, service_role;
revoke execute on function public.community_label_array_has_no_duplicates(text[])
  from public, anon, authenticated, service_role;

-- Only the three RPCs called by community-api retain service_role execution.
grant execute on function public.community_ensure_profile(uuid) to service_role;
grant execute on function public.community_consume_friend_code_lookup(uuid) to service_role;
grant execute on function public.community_toggle_program_like(uuid, uuid, boolean) to service_role;

-- This helper only uses built-in array functions, so pg_catalog is the
-- narrowest safe search path and removes mutable search_path resolution.
alter function public.community_label_array_has_no_duplicates(text[])
  set search_path = pg_catalog;
