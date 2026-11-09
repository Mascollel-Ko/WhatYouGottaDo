/**
 * Administrative one-shot R2 reconciliation. Callable ONLY behind the consumed
 * server-minted one-time GC ticket; never from a client-authenticated endpoint.
 *
 * Exactly the CURRENT and two latest VERIFIED RETAINED snapshots are protected.
 * Verify all three R2 payload checksums before deleting a single object.
 * Preserve DB ancestry via PRUNED tombstones, even when an R2 object is gone.
 */
import { DeleteObjectCommand, ListObjectsV2Command } from "npm:@aws-sdk/client-s3@3.842.0";
import { buildObjectKey, isUuid } from "./cloud_backup.mjs";
import { createR2Client, getObject, readBodyBytes, measureR2BucketBytes } from "./r2.mjs";
import { requiredR2Config } from "./supabase_auth.mjs";

const PAGE_SIZE = 500;
const MAX_ROWS = 25000;
const MAX_OBJECTS = 25000;
const PROTECTION_COUNT = 3;
const MAX_VERIFICATION_BYTES = 20 * 1024 * 1024;
const PENDING_GRACE_MS = 24 * 60 * 60 * 1000;
const ORPHAN_GRACE_MS = 60 * 60 * 1000;

async function rowsForUser(service, userId) {
  const rows = [];
  for (let start = 0; start < MAX_ROWS; start += PAGE_SIZE) {
    const response = await service.from("cloud_backups")
      .select("backup_id,user_id,parent_backup_id,object_key,status,created_at,verified_at,recovery_expires_at,checksum,compressed_size_bytes,cleanup_requested_at")
      .eq("user_id", userId).order("created_at", { ascending: false })
      .range(start, start + PAGE_SIZE - 1);
    if (response.error) throw new Error("THREE_RETENTION_METADATA_UNAVAILABLE");
    const page = response.data ?? [];
    rows.push(...page);
    if (page.length < PAGE_SIZE) return rows;
  }
  throw new Error("THREE_RETENTION_METADATA_LIMIT");
}

async function objectsForUser(client, bucket, userId) {
  const prefix = "users/" + userId + "/backups/";
  const objects = [];
  let cursor;
  for (let page = 0; page < 30; page++) {
    const result = await client.send(new ListObjectsV2Command({
      Bucket: bucket, Prefix: prefix, MaxKeys: 1000,
      ...(cursor ? { ContinuationToken: cursor } : {}),
    }));
    for (const object of result.Contents ?? []) {
      if (typeof object.Key !== "string" || !object.Key.startsWith(prefix) ||
          !/^users\/[0-9a-f-]{36}\/backups\/[0-9a-f-]{36}\.csv\.gz$/.test(object.Key) ||
          !Number.isSafeInteger(object.Size) || object.Size < 0) {
        throw new Error("THREE_RETENTION_UNEXPECTED_R2_OBJECT");
      }
      const backupId = object.Key.slice(prefix.length, -".csv.gz".length);
      if (!isUuid(backupId) || object.Key !== buildObjectKey(userId, backupId)) {
        throw new Error("THREE_RETENTION_NONCANONICAL_R2_OBJECT");
      }
      objects.push(object);
      if (objects.length > MAX_OBJECTS) throw new Error("THREE_RETENTION_OBJECT_LIMIT");
    }
    if (!result.IsTruncated) return objects;
    if (!result.NextContinuationToken || result.NextContinuationToken === cursor) {
      throw new Error("THREE_RETENTION_PAGINATION_INVALID");
    }
    cursor = result.NextContinuationToken;
  }
  throw new Error("THREE_RETENTION_PAGE_LIMIT");
}

async function verifyProtectedBytes(client, bucket, row, userId, actualObject) {
  const expectedKey = buildObjectKey(userId, row.backup_id);
  if (row.object_key !== expectedKey || !actualObject ||
      !Number.isSafeInteger(row.compressed_size_bytes) ||
      row.compressed_size_bytes < 1 || row.compressed_size_bytes > MAX_VERIFICATION_BYTES ||
      actualObject.Size !== row.compressed_size_bytes ||
      !/^[0-9a-f]{64}$/i.test(row.checksum ?? "")) {
    throw new Error("THREE_RETENTION_PROTECTED_METADATA_INVALID");
  }
  const result = await getObject(client, bucket, expectedKey);
  const bytes = await readBodyBytes(result.Body, MAX_VERIFICATION_BYTES);
  if (bytes.length !== row.compressed_size_bytes) {
    throw new Error("THREE_RETENTION_PROTECTED_LENGTH_MISMATCH");
  }
  const digest = [...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))]
    .map((value) => value.toString(16).padStart(2, "0")).join("");
  if (digest !== row.checksum.toLowerCase()) {
    throw new Error("THREE_RETENTION_PROTECTED_CHECKSUM_MISMATCH");
  }
}

function safeToRemove(row, object, now) {
  const created = row ? Date.parse(row.created_at) : Date.parse(object.LastModified);
  if (!Number.isFinite(created) || created > now) return false;
  if (!row) return now - created >= ORPHAN_GRACE_MS;
  if (row.status === "CURRENT") return false;
  if (row.status === "VERIFIED") return false; // An ongoing promotion must not be interrupted.
  if (row.status === "UPLOADING" && now - created < PENDING_GRACE_MS) return false;
  if (row.status === "CONFLICT_RECOVERY" &&
      Date.parse(row.recovery_expires_at) > now) return false;
  if (row.status === "CLEANUP_PENDING" &&
      now - Date.parse(row.cleanup_requested_at) < 15 * 60 * 1000) return false;
  return true;
}

async function claim(service, row, nowIso) {
  if (row.status === "PRUNED") return true;
  const update = await service.from("cloud_backups").update({
    status: "CLEANUP_PENDING",
    cleanup_requested_at: nowIso,
    recovery_expires_at: null,
  }).eq("user_id", row.user_id).eq("backup_id", row.backup_id)
    .eq("status", row.status).select("backup_id").maybeSingle();
  if (update.error) throw new Error("THREE_RETENTION_CLAIM_FAILED");
  return !!update.data;
}

async function markPruned(service, row, nowIso) {
  if (row.status === "PRUNED") return;
  const result = await service.from("cloud_backups").update({
    status: "PRUNED", cleanup_completed_at: nowIso,
  }).eq("user_id", row.user_id).eq("backup_id", row.backup_id)
    .eq("status", "CLEANUP_PENDING").select("backup_id").maybeSingle();
  if (result.error || !result.data) throw new Error("THREE_RETENTION_PRUNE_ACK_FAILED");
}

export async function reconcileR2Three(service, userId, { dryRun = true } = {}) {
  if (!isUuid(userId)) throw new Error("THREE_RETENTION_INVALID_USER");
  const rows = await rowsForUser(service, userId);
  const current = rows.filter((row) => row.status === "CURRENT");
  const retained = rows.filter((row) => row.status === "RETAINED" &&
    row.verified_at != null).slice(0, 2);
  if (current.length !== 1 || !current[0].verified_at || retained.length !== 2) {
    throw new Error("THREE_RETENTION_REQUIRES_THREE_VERIFIED_BACKUPS");
  }
  const protectedRows = [current[0], ...retained];
  const keys = new Set(protectedRows.map((row) => buildObjectKey(userId, row.backup_id)));
  const byObjectKey = new Map(rows.map((row) => [row.object_key, row]));
  if (byObjectKey.size !== rows.length || keys.size !== PROTECTION_COUNT) {
    throw new Error("THREE_RETENTION_DUPLICATE_METADATA");
  }
  const config = requiredR2Config();
  const r2 = createR2Client(config);
  const objects = await objectsForUser(r2, config.bucket, userId);
  const byKey = new Map(objects.map((object) => [object.Key, object]));
  for (const row of protectedRows) {
    await verifyProtectedBytes(r2, config.bucket, row, userId, byKey.get(row.object_key));
  }

  const now = Date.now();
  const eligible = objects.filter((object) =>
    !keys.has(object.Key) && safeToRemove(byObjectKey.get(object.Key), object, now));
  const blocked = objects.filter((object) =>
    !keys.has(object.Key) && !safeToRemove(byObjectKey.get(object.Key), object, now));
  if (dryRun) return {
    dryRun: true, verifiedProtected: PROTECTION_COUNT,
    totalObjects: objects.length, eligible: eligible.length, blocked: blocked.length,
  };

  let deleted = 0;
  let failures = 0;
  for (const object of eligible) {
    const row = byObjectKey.get(object.Key);
    try {
      if (row && !await claim(service, row, new Date().toISOString())) {
        failures++;
        continue;
      }
      await r2.send(new DeleteObjectCommand({ Bucket: config.bucket, Key: object.Key }));
      if (row) await markPruned(service, row, new Date().toISOString());
      deleted++;
    } catch {
      // A CLEANUP_PENDING row is replayable: the next administrative run
      // can retry the object without ever treating deletion as successful.
      failures++;
    }
  }
  const remaining = await objectsForUser(r2, config.bucket, userId);
  const bucketBytes = await measureR2BucketBytes(r2, config.bucket);
  const saved = await service.from("cloud_storage_state").upsert({
    state_id: true, cached_total_bytes: bucketBytes, measured_at: new Date().toISOString(),
  }, { onConflict: "state_id" });
  if (saved.error) throw new Error("THREE_RETENTION_USAGE_CACHE_FAILED");
  const currentAfter = await service.from("cloud_backups").select("backup_id")
    .eq("user_id", userId).eq("status", "CURRENT").maybeSingle();
  if (currentAfter.error) throw new Error("THREE_RETENTION_CURRENT_RECHECK_FAILED");
  const currentChanged = currentAfter.data?.backup_id !== current[0].backup_id;
  return {
    dryRun: false, verifiedProtected: PROTECTION_COUNT,
    beforeObjects: objects.length, deleted, failures,
    remainingObjects: remaining.length,
    blocked: blocked.length, currentChanged, bucketBytes,
    exactThree: remaining.length === PROTECTION_COUNT && !currentChanged && failures === 0,
  };
}
