-- Run against a migrated database with `supabase test db` or psql.
-- These catalog assertions keep the security boundary reviewable without
-- creating users or issuing real R2 credentials.
do $$
declare
  table_name text;
  rls_enabled boolean;
begin
  foreach table_name in array array[
    'profiles',
    'cloud_backups',
    'cloud_upload_authorizations',
    'cloud_backup_failures',
    'cloud_storage_state'
  ] loop
    if not exists (
      select 1 from pg_class c
      join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = table_name and c.relkind = 'r'
    ) then
      raise exception 'missing public.% table', table_name;
    end if;
    select c.relrowsecurity into rls_enabled
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = table_name;
    if not rls_enabled then
      raise exception 'RLS is disabled for public.%', table_name;
    end if;
  end loop;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
      and policyname = 'profiles_select_own'
      and cmd = 'SELECT' and roles = array['authenticated']::name[]
  ) then
    raise exception 'profiles owner SELECT policy is missing';
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
      and policyname = 'profiles_update_own_nickname'
      and cmd = 'UPDATE' and roles = array['authenticated']::name[]
  ) then
    raise exception 'profiles owner UPDATE policy is missing';
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'cloud_backups'
      and policyname = 'cloud_backups_select_own'
      and cmd = 'SELECT' and roles = array['authenticated']::name[]
  ) then
    raise exception 'cloud_backups owner SELECT policy is missing';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename in ('cloud_upload_authorizations', 'cloud_backup_failures', 'cloud_storage_state')
  ) then
    raise exception 'internal transport tables must not expose client policies';
  end if;

  if not has_table_privilege('authenticated', 'public.cloud_backups', 'SELECT')
     or has_table_privilege('authenticated', 'public.cloud_backups', 'INSERT')
     or has_table_privilege('authenticated', 'public.cloud_backups', 'UPDATE')
     or has_table_privilege('authenticated', 'public.cloud_backups', 'DELETE') then
    raise exception 'authenticated grants on cloud_backups are too broad';
  end if;

  if not exists (
    select 1 from pg_indexes
    where schemaname = 'public' and indexname = 'cloud_backups_one_current_per_user_idx'
      and indexdef like '%WHERE (status = ''CURRENT'')%'
  ) then
    raise exception 'per-user CURRENT uniqueness index is missing';
  end if;
end;
$$;
