import {
  CloudRequestError,
  MAX_COMPRESSED_BYTES,
  MAX_UNCOMPRESSED_VERIFY_BYTES,
  emptyResponse,
  jsonResponse,
  parseJsonRequest,
  validateFinalizeRequest,
  buildObjectKey,
} from "../_shared/cloud_backup.mjs";
import { authenticateRequest, createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client, getObject, headObject, readBodyBytes } from "../_shared/r2.mjs";

function failurePayload(code, technicalDetail = null) {
  return {
    failure_stage: "FINALIZE_VERIFICATION",
    failure_code: code,
    retryable: code === "R2_UNAVAILABLE" || code === "INTERNAL_ERROR",
    technical_detail: technicalDetail,
  };
}

async function markFailed(service, userId, backup, code, detail = null) {
  const update = await service.from("cloud_backups").update({ status: "FAILED" })
    .eq("backup_id", backup.backup_id).eq("user_id", userId).in("status", ["UPLOADING", "VERIFIED"]);
  if (update.error) throw new Error("failed to mark backup FAILED");
  const failure = await service.from("cloud_backup_failures").insert({
    user_id: userId,
    backup_id: backup.backup_id,
    install_id: backup.install_id,
    occurred_at: new Date().toISOString(),
    ...failurePayload(code, detail),
  });
  if (failure.error) throw new Error("failed to record backup failure");
}

async function bestEffortDelete(client, bucket, key) {
  try {
    // Dynamic import keeps the helper's public surface small and is supported by Deno.
    const { DeleteObjectCommand } = await import("npm:@aws-sdk/client-s3@3.842.0");
    await client.send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
  } catch {
    // Orphan cleanup is deliberately best effort; the metadata remains FAILED.
  }
}

function hex(bytes) {
  return [...new Uint8Array(bytes)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

async function verifyObject({ client, bucket, backup }) {
  if (!Number.isSafeInteger(backup.compressed_size_bytes) || backup.compressed_size_bytes < 0 ||
      backup.compressed_size_bytes > MAX_COMPRESSED_BYTES || backup.uncompressed_size_bytes == null ||
      backup.uncompressed_size_bytes < 0 || backup.uncompressed_size_bytes > MAX_UNCOMPRESSED_VERIFY_BYTES ||
      typeof backup.checksum !== "string" || backup.checksum_algorithm !== "SHA-256") {
    throw new CloudRequestError("INVALID_VERIFICATION_METADATA", "Backup verification metadata is incomplete.");
  }

  let head;
  try {
    head = await headObject(client, bucket, backup.object_key);
  } catch {
    throw new CloudRequestError("R2_OBJECT_MISSING", "The uploaded object is not available.", 409);
  }
  if (head.ContentLength !== backup.compressed_size_bytes) {
    throw new CloudRequestError("COMPRESSED_SIZE_MISMATCH", "The uploaded object size does not match metadata.");
  }

  let object;
  try {
    object = await getObject(client, bucket, backup.object_key);
    const compressed = await readBodyBytes(object.Body, backup.compressed_size_bytes);
    const actualChecksum = hex(await crypto.subtle.digest("SHA-256", compressed));
    if (actualChecksum !== backup.checksum.toLowerCase()) {
      throw new CloudRequestError("CHECKSUM_MISMATCH", "The uploaded object checksum does not match metadata.");
    }

    const decompressedStream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream("gzip"));
    const decompressed = await readBodyBytes(decompressedStream, backup.uncompressed_size_bytes + 1);
    if (decompressed.byteLength !== backup.uncompressed_size_bytes) {
      throw new CloudRequestError("UNCOMPRESSED_SIZE_MISMATCH", "The decompressed payload size does not match metadata.");
    }
  } catch (error) {
    if (error instanceof CloudRequestError) throw error;
    throw new CloudRequestError("INVALID_GZIP", "The uploaded object is not a valid bounded GZIP payload.");
  }
}

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);
  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;

  let service;
  let backup;
  let r2;
  let client;
  try {
    const { backup_id: backupId } = validateFinalizeRequest(await parseJsonRequest(request));
    service = createServiceClient();
    const userId = auth.user.id;
    const lookup = await service.from("cloud_backups")
      .select("backup_id,user_id,parent_backup_id,install_id,object_key,status,compressed_size_bytes,uncompressed_size_bytes,checksum,checksum_algorithm,backup_format_version,schema_version")
      .eq("backup_id", backupId).eq("user_id", userId).maybeSingle();
    if (lookup.error) throw new Error("backup lookup failed");
    backup = lookup.data;
    if (!backup) throw new CloudRequestError("BACKUP_NOT_FOUND", "Backup does not exist.", 404);
    if (["CURRENT", "RETAINED"].includes(backup.status)) return jsonResponse({ backup_id: backupId, status: backup.status });
    if (backup.status !== "UPLOADING") throw new CloudRequestError("BACKUP_NOT_FINALIZABLE", "Backup is not awaiting finalization.", 409);

    r2 = requiredR2Config();
    const expectedKey = buildObjectKey(userId, backupId);
    if (backup.object_key !== expectedKey) throw new Error("stored object key failed canonical check");
    client = createR2Client(r2);
    await verifyObject({ client, bucket: r2.bucket, backup });

    const verified = await service.from("cloud_backups").update({
      status: "VERIFIED", uploaded_at: new Date().toISOString(), verified_at: new Date().toISOString(),
    }).eq("backup_id", backupId).eq("user_id", userId).eq("status", "UPLOADING");
    if (verified.error) throw new Error("failed to mark backup VERIFIED");

    const promotion = await service.rpc("cloud_promote_backup", { p_backup_id: backupId, p_user_id: userId });
    if (promotion.error) throw new Error("atomic promotion failed");
    const result = Array.isArray(promotion.data) ? promotion.data[0] : promotion.data;
    if (!result || result.status === "LINEAGE_CONFLICT") {
      await markFailed(service, userId, backup, "LINEAGE_CONFLICT");
      await bestEffortDelete(client, r2.bucket, backup.object_key);
      return jsonResponse({ error: "LINEAGE_CONFLICT" }, 409);
    }
    return jsonResponse({ backup_id: backupId, status: "CURRENT" });
  } catch (error) {
    if (backup && service && backup.status === "UPLOADING" && !(error instanceof CloudRequestError && error.code === "BACKUP_NOT_FOUND")) {
      try {
        if (client && r2) await bestEffortDelete(client, r2.bucket, backup.object_key);
        await markFailed(service, auth.user.id, backup, error instanceof CloudRequestError ? error.code : "INTERNAL_ERROR");
      } catch {
        return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
      }
    }
    if (error instanceof CloudRequestError) return jsonResponse({ error: error.code }, error.status);
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
  }
}

Deno.serve(handler);
