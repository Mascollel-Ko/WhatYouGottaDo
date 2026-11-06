-- Backups that were once downloadable must preserve lineage metadata
-- even after their R2 objects have been physically removed. Never touch CURRENT.
alter table public.cloud_backups
  add column if not exists cleanup_requested_at timestamptz,
  add column if not exists cleanup_completed_at timestamptz;
alter table public.cloud_backups
  drop constraint if exists cloud_backups_status;
alter table public.cloud_backups
  add constraint cloud_backups_status check (status in (
    'UPLOADING', 'VERIFIED', 'CURRENT', 'RETAINED',
    'CONFLICT_RECOVERY', 'FAILED', 'CLEANUP_PENDING', 'PRUNED'
  ));
create index if not exists cloud_backups_cleanup_idx
  on public.cloud_backups (user_id, status, created_at)
  where status in ('UPLOADING','FAILED','RETAINED','CONFLICT_RECOVERY','CLEANUP_PENDING','PRUNED');
-- A 30-day metadata audit tail prevents abrupt disappearance of error history.
-- Rows that are ancestors of any other backups remain as PRUNED tombstones.

-- One-time database-generated cleanup tickets eliminate the need to store an
-- admin/service JWT in a cron job, app, or publicly reachable endpoint.
create table if not exists public.cloud_cleanup_tickets (
  ticket uuid primary key default gen_random_uuid(),
  issued_at timestamptz not null default now(),
  consumed_at timestamptz
);
alter table public.cloud_cleanup_tickets enable row level security;
revoke all on public.cloud_cleanup_tickets from public, anon, authenticated;
grant select, insert, update, delete on public.cloud_cleanup_tickets to service_role;

create or replace function public.cloud_consume_cleanup_ticket(p_ticket uuid)
returns boolean
language plpgsql security definer set search_path = ''
as $$
declare
  accepted uuid;
begin
  update public.cloud_cleanup_tickets
     set consumed_at = now()
   where ticket = p_ticket
     and consumed_at is null
     and issued_at >= now() - interval '5 minutes'
     and issued_at <= now() + interval '30 seconds'
   returning ticket into accepted;
  return accepted is not null;
end;
$$;
revoke all on function public.cloud_consume_cleanup_ticket(uuid) from public, anon, authenticated;
grant execute on function public.cloud_consume_cleanup_ticket(uuid) to service_role;

-- Supported Supabase mechanism for unattended Edge Function invocations.
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;

-- UTC schedule: once per hour. Tickets are generated and consumed exactly
-- once; the Edge Function cannot be triggered with a client JWT.
select cron.schedule(
  'wgtd-cloud-backup-gc-hourly',
  '0 * * * *',
  $cron$
    with expired_tickets as (
      delete from public.cloud_cleanup_tickets
      where issued_at < now() - interval '1 day'
    ),
    ticket as (
      insert into public.cloud_cleanup_tickets default values
      returning ticket
    )
    select net.http_post(
      url := 'https://yiruprqpkptjuhtknvur.supabase.co/functions/v1/cloud-backup-gc',
      headers := jsonb_build_object('Content-Type', 'application/json'),
      body := jsonb_build_object('ticket', ticket.ticket),
      timeout_milliseconds := 20000
    ) from ticket;
  $cron$
);
