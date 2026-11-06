-- Preserve user-published program snapshots while removing the account that
-- originally published them. Private and social user tables still cascade.
alter table public.community_programs
  drop constraint if exists community_programs_owner_user_id_fkey;
alter table public.community_programs
  alter column owner_user_id drop not null;
alter table public.community_programs
  add constraint community_programs_owner_user_id_fkey
  foreign key (owner_user_id) references auth.users(id) on delete set null;

-- A durable per-user deletion lock blocks newly issued backup upload URLs.
-- Only service_role may read/write; client roles have no policy.
create table if not exists public.account_deletion_requests (
  user_id uuid primary key references auth.users(id) on delete cascade,
  requested_at timestamptz not null default now()
);
alter table public.account_deletion_requests enable row level security;
revoke all on public.account_deletion_requests from public, anon, authenticated;
grant select, insert, update, delete on public.account_deletion_requests to service_role;

-- Fail closed if the shared-program preservation constraint is missing.
create or replace function public.account_deletion_contract_ready()
returns boolean language sql stable security invoker set search_path = ''
as $$
select exists (
  select 1 from pg_constraint c
  join pg_class t on t.oid = c.conrelid
  join pg_namespace n on n.oid = t.relnamespace
  where n.nspname = 'public' and t.relname = 'community_programs'
    and c.conname = 'community_programs_owner_user_id_fkey'
    and c.confrelid = 'auth.users'::regclass and c.confdeltype = 'n'
) and exists (
  select 1 from information_schema.columns
  where table_schema='public' and table_name='community_programs'
    and column_name='owner_user_id' and is_nullable='YES'
);
$$;
revoke all on function public.account_deletion_contract_ready() from public, anon, authenticated;
grant execute on function public.account_deletion_contract_ready() to service_role;
