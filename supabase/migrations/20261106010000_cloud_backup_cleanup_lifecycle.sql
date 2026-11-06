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
