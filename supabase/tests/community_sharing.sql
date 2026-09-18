-- Community v1 catalog/security assertions.  These checks intentionally do
-- not create users or inspect private values.
do $$
declare
  table_name text;
  rls_enabled boolean;
begin
  foreach table_name in array array[
    'community_profiles',
    'community_programs',
    'community_program_exercises',
    'community_program_likes',
    'community_weekly_summaries',
    'community_friend_requests',
    'community_friendships',
    'community_blocks',
    'community_friend_activity',
    'community_friend_code_lookup_attempts'
  ] loop
    if not exists (
      select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = table_name and c.relkind = 'r'
    ) then raise exception 'missing public.% table', table_name; end if;
    select c.relrowsecurity into rls_enabled
      from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = table_name;
    if not rls_enabled then raise exception 'RLS is disabled for public.%', table_name; end if;
    if has_table_privilege('authenticated', 'public.' || table_name, 'SELECT')
       or has_table_privilege('authenticated', 'public.' || table_name, 'INSERT')
       or has_table_privilege('authenticated', 'public.' || table_name, 'UPDATE')
       or has_table_privilege('authenticated', 'public.' || table_name, 'DELETE') then
      raise exception 'Community table % is directly accessible to authenticated clients', table_name;
    end if;
  end loop;

  if not exists (select 1 from pg_proc where proname = 'community_ensure_profile') then
    raise exception 'community profile initializer is missing';
  end if;
  if not exists (select 1 from pg_proc where proname = 'community_toggle_program_like') then
    raise exception 'server-authoritative like function is missing';
  end if;
  if not exists (select 1 from pg_indexes where indexname = 'community_profiles_nickname_unique') then
    raise exception 'case-insensitive nickname uniqueness index is missing';
  end if;
  if not exists (select 1 from pg_indexes where indexname = 'community_friend_requests_pending_unique') then
    raise exception 'pending friend request uniqueness is missing';
  end if;
  if not exists (select 1 from pg_indexes where indexname = 'community_programs_popular_idx') then
    raise exception 'program popular feed index is missing';
  end if;
end;
$$;
