-- Explicit user-approved LOCAL_WINS override only. No background worker may call
-- this RPC, and the server requires the CURRENT observed before the approval.
-- Preserve all prior R2 objects and metadata for recovery/audit.
create or replace function public.cloud_promote_explicit_local_wins(
    p_backup_id uuid, p_user_id uuid, p_expected_current_backup_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    candidate public.cloud_backups%rowtype;
    current_id uuid;
begin
    if p_expected_current_backup_id is null then
        return jsonb_build_object('status', 'CURRENT_CHANGED');
    end if;

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

    select backup_id into current_id
    from public.cloud_backups
    where user_id = p_user_id and status = 'CURRENT'
    for update;
    if current_id is distinct from p_expected_current_backup_id then
        return jsonb_build_object('status', 'CURRENT_CHANGED');
    end if;

    -- Explicit replacement is the only operation that may rebase a stale
    -- local snapshot. Connect the replacement to the real CURRENT on server.
    update public.cloud_backups
    set status = 'RETAINED'
    where user_id = p_user_id and status = 'CURRENT';

    update public.cloud_backups
    set parent_backup_id = current_id, status = 'CURRENT'
    where backup_id = p_backup_id and user_id = p_user_id and status = 'VERIFIED';

    return jsonb_build_object('status', 'CURRENT', 'backup_id', p_backup_id);
end;
$$;

revoke all on function public.cloud_promote_explicit_local_wins(uuid, uuid, uuid)
    from public, anon, authenticated;
grant execute on function public.cloud_promote_explicit_local_wins(uuid, uuid, uuid)
    to service_role;
