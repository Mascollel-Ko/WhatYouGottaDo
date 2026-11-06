-- Pending cloud object cleanup survives removal of a deleted auth account.
create table if not exists public.account_deletion_cleanup_jobs (
  user_id uuid primary key,
  created_at timestamptz not null default now(),
  last_attempt_at timestamptz,
  attempts integer not null default 0 check (attempts >= 0)
);
alter table public.account_deletion_cleanup_jobs enable row level security;
revoke all on public.account_deletion_cleanup_jobs from public, anon, authenticated;
grant select, insert, update, delete on public.account_deletion_cleanup_jobs to service_role;
