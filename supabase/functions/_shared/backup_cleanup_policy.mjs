/**
 * Safety policy for physical R2 backups. Database rows remain as lineage
 * tombstones until no child references them.
 */
export const STALE_UPLOAD_MS = 24 * 60 * 60 * 1000;
export const FAILED_OBJECT_GRACE_MS = 24 * 60 * 60 * 1000;
export const RETIRED_BACKUP_GRACE_MS = 24 * 60 * 60 * 1000;
export const PENDING_RETRY_MS = 15 * 60 * 1000;
export const FAILURE_AUDIT_MS = 30 * 24 * 60 * 60 * 1000;

export function canCleanObject(row, {
  nowMs,
  protectedBackupIds,
  currentId,
  retentionVerified,
}) {
  const age = nowMs - Date.parse(row.created_at);
  if (!Number.isFinite(age) || age < 0) return false;
  switch (row.status) {
    case "UPLOADING": return age >= STALE_UPLOAD_MS;
    case "FAILED": return age >= FAILED_OBJECT_GRACE_MS;
    case "CONFLICT_RECOVERY":
      return Number.isFinite(Date.parse(row.recovery_expires_at)) &&
        Date.parse(row.recovery_expires_at) <= nowMs;
    case "RETAINED":
      return !!currentId && retentionVerified &&
        Array.isArray(protectedBackupIds) &&
        protectedBackupIds.length === 3 &&
        protectedBackupIds.includes(currentId) &&
        !protectedBackupIds.includes(row.backup_id) &&
        age >= RETIRED_BACKUP_GRACE_MS;
    case "CLEANUP_PENDING":
      return Number.isFinite(Date.parse(row.cleanup_requested_at)) &&
        Date.parse(row.cleanup_requested_at) <= nowMs - PENDING_RETRY_MS;
    default: return false;
  }
}
