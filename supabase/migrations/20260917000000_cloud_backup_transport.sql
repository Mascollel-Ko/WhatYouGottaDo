-- Cloud Backup transport metadata.
-- The Android client never writes these lifecycle rows directly. Edge Functions
-- use the service role only after verifying the caller's Supabase Auth JWT.

create table if not exists public.profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  nickname text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint profiles_nickname_length check (nickname is null or char_length(nickname) <= 120)
);

create table if not exists public.cloud_backups (
  backup_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  parent_backup_id uuid,
  install_id uuid not null,

  created_at timestamptz not null default timezone('utc', now()),
  uploaded_at timestamptz,
  verified_at timestamptz,

  object_key text not null unique,

  compressed_size_bytes bigint,
  uncompressed_size_bytes bigint,

  compression text not null default 'GZIP',
  checksum text,
  checksum_algorithm text,

  backup_format_version integer not null,
  schema_version integer not null,
  app_version text not null,
  local_revision bigint not null,

  status text not null default 'UPLOADING',

  conflict_id uuid,
  recovery_expires_at timestamptz,

  -- This second key lets every parent reference prove same-user ownership.
  constraint cloud_backups_backup_owner_unique unique (backup_id, user_id),
  constraint cloud_backups_backup_object_unique unique (backup_id, object_key),
  constraint cloud_backups_parent_same_user_fk
    foreign key (parent_backup_id, user_id)
    references public.cloud_backups (backup_id, user_id),
  constraint cloud_backups_object_key_format check (
    object_key ~ '^users/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/backups/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.csv\.gz$'
  ),
  constraint cloud_backups_compressed_size check (
    compressed_size_bytes is null or compressed_size_bytes between 0 and 20971520
  ),
  constraint cloud_backups_uncompressed_size check (
    uncompressed_size_bytes is null or uncompressed_size_bytes >= 0
  ),
  constraint cloud_backups_compression check (compression = 'GZIP'),
  constraint cloud_backups_checksum check (
    checksum is null or checksum ~ '^[0-9a-f]{64}$'
  ),
  constraint cloud_backups_checksum_algorithm check (
    (checksum is null and checksum_algorithm is null)
    or (checksum is not null and checksum_algorithm = 'SHA-256')
  ),
  constraint cloud_backups_current_contract check (
    backup_format_version = 14 and schema_version = 13
  ),
  constraint cloud_backups_local_revision check (local_revision >= 0),
  constraint cloud_backups_app_version check (char_length(trim(app_version)) between 1 and 160),
  constraint cloud_backups_status check (
    status in ('UPLOADING', 'VERIFIED', 'CURRENT', 'RETAINED', 'CONFLICT_RECOVERY', 'FAILED')
  ),
  constraint cloud_backups_recovery_expiry check (
    (status = 'CONFLICT_RECOVERY' and recovery_expires_at is not null)
    or (status <> 'CONFLICT_RECOVERY' and recovery_expires_at is null)
  )
);

create table if not exists public.cloud_upload_authorizations (
  authorization_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  backup_id uuid not null,
  issued_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz not null,
  object_key text not null,
  constraint cloud_upload_authorizations_backup_owner_fk
    foreign key (backup_id, user_id)
    references public.cloud_backups (backup_id, user_id)
    on delete cascade,
  constraint cloud_upload_authorizations_backup_object_fk
    foreign key (backup_id, object_key)
    references public.cloud_backups (backup_id, object_key)
    on delete cascade,
  constraint cloud_upload_authorizations_expiry check (expires_at > issued_at)
);

create table if not exists public.cloud_backup_failures (
  failure_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  backup_id uuid,
  install_id uuid not null,
  failure_stage text not null,
  failure_code text not null,
  occurred_at timestamptz not null default timezone('utc', now()),
  retryable boolean not null,
  technical_detail text,
  constraint cloud_backup_failures_backup_owner_fk
    foreign key (backup_id, user_id)
    references public.cloud_backups (backup_id, user_id)
    on delete cascade,
  constraint cloud_backup_failures_stage check (char_length(trim(failure_stage)) between 1 and 80),
  constraint cloud_backup_failures_code check (char_length(trim(failure_code)) between 1 and 120)
);

create table if not exists public.cloud_storage_state (
  state_id boolean primary key default true,
  cached_total_bytes bigint not null default 0,
  measured_at timestamptz not null default timezone('utc', now()),
  constraint cloud_storage_state_singleton check (state_id),
  constraint cloud_storage_state_bytes check (cached_total_bytes >= 0)
);

create index if not exists cloud_backups_user_status_idx
  on public.cloud_backups (user_id, status);
create index if not exists cloud_backups_user_created_idx
  on public.cloud_backups (user_id, created_at desc);
create index if not exists cloud_backups_parent_idx
  on public.cloud_backups (parent_backup_id);
create index if not exists cloud_backups_conflict_idx
  on public.cloud_backups (conflict_id);
create index if not exists cloud_upload_authorizations_user_issued_idx
  on public.cloud_upload_authorizations (user_id, issued_at desc);
create index if not exists cloud_backup_failures_user_occurred_idx
  on public.cloud_backup_failures (user_id, occurred_at desc);

create unique index if not exists cloud_backups_one_current_per_user_idx
  on public.cloud_backups (user_id)
  where status = 'CURRENT';

alter table public.profiles enable row level security;
alter table public.cloud_backups enable row level security;
alter table public.cloud_upload_authorizations enable row level security;
alter table public.cloud_backup_failures enable row level security;
alter table public.cloud_storage_state enable row level security;

revoke all on table public.profiles from anon, authenticated;
revoke all on table public.cloud_backups from anon, authenticated;
revoke all on table public.cloud_upload_authorizations from anon, authenticated;
revoke all on table public.cloud_backup_failures from anon, authenticated;
revoke all on table public.cloud_storage_state from anon, authenticated;

grant select on table public.profiles to authenticated;
grant update (nickname) on table public.profiles to authenticated;
grant select on table public.cloud_backups to authenticated;

create policy profiles_select_own
  on public.profiles for select to authenticated
  using (user_id = auth.uid());

create policy profiles_update_own_nickname
  on public.profiles for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy cloud_backups_select_own
  on public.cloud_backups for select to authenticated
  using (user_id = auth.uid());

-- No INSERT/UPDATE/DELETE policies are intentional on lifecycle/internal tables.
-- service_role access is used only after Edge Function JWT verification.
grant all on table public.profiles to service_role;
grant all on table public.cloud_backups to service_role;
grant all on table public.cloud_upload_authorizations to service_role;
grant all on table public.cloud_backup_failures to service_role;
grant all on table public.cloud_storage_state to service_role;
