import { DeleteObjectCommand } from "npm:@aws-sdk/client-s3@3.842.0";
import { createR2Client, headObject } from "./r2.mjs";
import { buildObjectKey, isUuid } from "./cloud_backup.mjs";
import { requiredR2Config } from "./supabase_auth.mjs";
import { canCleanObject, FAILURE_AUDIT_MS } from "./backup_cleanup_policy.mjs";

const LIMIT = 15;

async function readProtectedBackups(service, userId) {
  const [curr, retained] = await Promise.all([
    service.from("cloud_backups")
      .select("backup_id,object_key,compressed_size_bytes,created_at")
      .eq("user_id", userId).eq("status", "CURRENT").maybeSingle(),
    service.from("cloud_backups")
      .select("backup_id,object_key,compressed_size_bytes")
      .eq("user_id", userId).eq("status", "RETAINED")
      .order("created_at", { ascending: false }).limit(1).maybeSingle(),
  ]);
  if (curr.error || retained.error) throw new Error("PROTECTED_BACKUP_LOOKUP_FAILED");
  return { current: curr.data, retained: retained.data };
}

/** Verify both copies still exist before pruning older RETAINED objects. */
async function protectedFilesPresent(client, bucket, userId, protectedRows) {
  if (!protectedRows.current || !protectedRows.retained) return false;
  for (const row of [protectedRows.current, protectedRows.retained]) {
    if (row.object_key !== buildObjectKey(userId, row.backup_id)) return false;
    try {
      const head = await headObject(client, bucket, row.object_key);
      if (!Number.isSafeInteger(head.ContentLength) ||
          head.ContentLength !== row.compressed_size_bytes) return false;
    } catch {
      return false;
    }
  }
  return true;
}

async function expiredAuthorization(service, backupId, nowIso) {
  const result = await service.from("cloud_upload_authorizations")
    .select("authorization_id", { count: "exact", head: true })
    .eq("backup_id", backupId).gt("expires_at", nowIso);
  if (result.error) throw new Error("CLEANUP_AUTHORIZATION_LOOKUP_FAILED");
  return (result.count ?? 0) === 0;
}

async function claimCleanup(service, row, nowIso, retryCutoff) {
  let request = service.from("cloud_backups")
    .update({
      status: "CLEANUP_PENDING",
      cleanup_requested_at: nowIso,
      recovery_expires_at: null,
    })
    .eq("backup_id", row.backup_id).eq("user_id", row.user_id).eq("status", row.status)
    .select("backup_id");
  if (row.status === "CLEANUP_PENDING") {
    request = request.lt("cleanup_requested_at", retryCutoff);
  } else {
    request = request.is("cleanup_requested_at", null);
  }
  const result = await request.maybeSingle();
  if (result.error) throw new Error("CLEANUP_CLAIM_FAILED");
  return !!result.data;
}

/**
 * Runs with service_role, but only for the authenticated caller's own userId.
 * Idempotent, bounded, and never removes CURRENT or the newest RETAINED.
 * Delete the R2 object first, then mark its lineage row PRUNED.
 */
export async function cleanupUserBackups(service, userId, limit = LIMIT) {
  if (!isUuid(userId)) throw new Error("INVALID_CLEANUP_USER");
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 50) throw new Error("INVALID_CLEANUP_LIMIT");

  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();
  const cutoff = new Date(nowMs - FAILURE_AUDIT_MS).toISOString();
  const retryCutoff = new Date(nowMs - 15 * 60 * 1000).toISOString();

  const { data: candidates, error: listError } = await service.from("cloud_backups")
    .select("backup_id,user_id,object_key,status,created_at,recovery_expires_at,cleanup_requested_at")
    .eq("user_id", userId)
    .in("status", ["FAILED", "UPLOADING", "RETAINED", "CONFLICT_RECOVERY", "CLEANUP_PENDING"])
    .order("created_at", { ascending: true }).limit(250);
  if (listError) throw new Error("CLEANUP_CANDIDATE_LIST_FAILED");

  const protectedRows = await readProtectedBackups(service, userId);
  // The newest unresolved upload may contain the user's only recent changes
  // when CURRENT has been stale due to repeated lineage conflicts. Never
  // purge that fallback solely because 24h elapsed.
  const unresolved = await service.from("cloud_backups")
    .select("backup_id,created_at")
    .eq("user_id", userId)
    .in("status", ["FAILED", "UPLOADING", "VERIFIED"])
    .order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (unresolved.error) throw new Error("UNRESOLVED_BACKUP_LOOKUP_FAILED");
  const latestUnresolved = unresolved.data;
  const preserveLatestUnresolved = latestUnresolved &&
    (!protectedRows.current ||
      Date.parse(latestUnresolved.created_at) > Date.parse(protectedRows.current.created_at));

  let config, client, retentionVerified;
  let pruned = 0, failures = 0, skipped = 0;
  for (const row of candidates ?? []) {
    if (pruned + failures >= limit) break;
    if (preserveLatestUnresolved && row.backup_id === latestUnresolved.backup_id) {
      skipped++;
      continue;
    }
    if (row.status === "RETAINED" && retentionVerified === undefined) {
      try {
        config = requiredR2Config();
        client = createR2Client(config);
        retentionVerified = await protectedFilesPresent(client, config.bucket, userId, protectedRows);
      } catch {
        retentionVerified = false;
      }
    }
    if (!canCleanObject(row, {
      nowMs,
      currentId: protectedRows.current?.backup_id,
      newestRetainedId: protectedRows.retained?.backup_id,
      retentionVerified: retentionVerified === true,
    })) { skipped++; continue; }
    if (row.object_key !== buildObjectKey(userId, row.backup_id)) {
      failures++; continue;
    }
    if (!(await expiredAuthorization(service, row.backup_id, nowIso))) {
      skipped++; continue;
    }
    try {
      if (!config) config = requiredR2Config();
      if (!client) client = createR2Client(config);
      const claimed = await claimCleanup(service, row, nowIso, retryCutoff);
      if (!claimed) { skipped++; continue; }

      // S3 DeleteObject is idempotent, including when the file is absent.
      // A failed delete leaves CLEANUP_PENDING for a subsequent retry.
      await client.send(new DeleteObjectCommand({ Bucket: config.bucket, Key: row.object_key }));
      const completed = await service.from("cloud_backups")
        .update({ status: "PRUNED", cleanup_completed_at: new Date().toISOString() })
        .eq("backup_id", row.backup_id).eq("user_id", userId)
        .eq("status", "CLEANUP_PENDING").select("backup_id").maybeSingle();
      if (completed.error || !completed.data) throw new Error("CLEANUP_COMMIT_FAILED");
      pruned++;
    } catch {
      failures++;
      // Keep the claimed row for retry rather than claiming R2 deletion succeeded.
    }
  }

  // Keep failure/cleanup audit for 30 days. Preserve metadata referenced by
  // backup parent chains, even when their R2 objects have been removed.
  const old = await service.from("cloud_backups").select("backup_id")
    .eq("user_id", userId).eq("status", "PRUNED")
    .lt("cleanup_completed_at", cutoff)
    .order("cleanup_completed_at", { ascending: true }).limit(10);
  if (!old.error) {
    for (const row of old.data ?? []) {
      const dependent = await service.from("cloud_backups")
        .select("backup_id", { count: "exact", head: true }).eq("parent_backup_id", row.backup_id);
      if (dependent.error || (dependent.count ?? 0) !== 0) continue;
      await service.from("cloud_backups")
        .delete().eq("backup_id", row.backup_id).eq("user_id", userId).eq("status", "PRUNED");
    }
  }
  return { pruned, failures, skipped };
}
