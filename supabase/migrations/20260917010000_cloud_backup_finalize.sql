-- Server-only lifecycle transition for verified Cloud Backup objects.
-- Locking the current row and the candidate in one transaction makes the
-- parent/current check and the one-CURRENT invariant atomic.

create or replace function public.cloud_promote_backup(p_backup_id uuid, p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  candidate public.cloud_backups%rowtype;
  current_id uuid;
begin
  select * into candidate
    from public.cloud_backups
    where backup_id = p_backup_id and user_id = p_user_id
    for update;
  if not found then
    return jsonb_build_object('status', 'NOT_FOUND');
  end if;
  if candidate.status <> 'VERIFIED' then
    return jsonb_build_object('status', 'NOT_VERIFIED');
  end if;

  -- Lock the existing CURRENT row before comparing lineage. If another
  -- finalization raced us, its committed row is observed here.
  select backup_id into current_id
    from public.cloud_backups
    where user_id = p_user_id and status = 'CURRENT'
    for update;
  if (current_id is null and candidate.parent_backup_id is not null)
     or (current_id is not null and candidate.parent_backup_id is distinct from current_id) then
    return jsonb_build_object('status', 'LINEAGE_CONFLICT', 'current_backup_id', current_id);
  end if;

  update public.cloud_backups
    set status = 'RETAINED'
    where user_id = p_user_id and status = 'CURRENT';
  update public.cloud_backups
    set status = 'CURRENT'
    where backup_id = p_backup_id and user_id = p_user_id and status = 'VERIFIED';
  return jsonb_build_object('status', 'CURRENT', 'backup_id', p_backup_id);
end;
$$;

revoke all on function public.cloud_promote_backup(uuid, uuid) from public, anon, authenticated;
grant execute on function public.cloud_promote_backup(uuid, uuid) to service_role;
