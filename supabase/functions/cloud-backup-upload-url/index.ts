import {
  CloudRequestError,
  GLOBAL_STORAGE_GUARD_BYTES,
  PRESIGN_EXPIRY_SECONDS,
  UPLOAD_AUTHORIZATION_LIMIT,
  UPLOAD_AUTHORIZATION_WINDOW_MS,
  emptyResponse,
  jsonResponse,
  parseJsonRequest,
  validateUploadRequest,
  buildObjectKey,
  isDownloadableStatus,
} from "../_shared/cloud_backup.mjs";
import { authenticateRequest, createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client, presignPut } from "../_shared/r2.mjs";

function failurePayload(error) {
  return {
    failure_stage: "UPLOAD_AUTHORIZATION",
    failure_code: error instanceof CloudRequestError ? error.code : "INTERNAL_ERROR",
    retryable: !(error instanceof CloudRequestError) || error.status >= 500,
    technical_detail: error instanceof CloudRequestError ? null : "presign operation failed",
  };
}

async function recordFailure(service, userId, backupId, installId, error) {
  const { error: insertError } = await service.from("cloud_backup_failures").insert({
    user_id: userId,
    backup_id: backupId,
    install_id: installId,
    occurred_at: new Date().toISOString(),
    ...failurePayload(error),
  });
  if (insertError) throw new Error("failure record insert failed");
}

async function failBackup(service, userId, backupId, installId, error) {
  const { error: updateError } = await service.from("cloud_backups").update({ status: "FAILED" })
    .eq("backup_id", backupId).eq("user_id", userId);
  if (updateError) throw new Error("failed to mark backup FAILED");
  await recordFailure(service, userId, backupId, installId, error);
}

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);

  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;

  try {
    const requestBody = validateUploadRequest(await parseJsonRequest(request));
    const service = createServiceClient();
    const userId = auth.user.id;

    if (requestBody.parent_backup_id !== null) {
      const { data: parent, error: parentError } = await service
        .from("cloud_backups")
        .select("backup_id,user_id,status")
        .eq("backup_id", requestBody.parent_backup_id)
        .eq("user_id", userId)
        .maybeSingle();
      if (parentError) throw new Error("parent lookup failed");
      if (!parent || !isDownloadableStatus(parent.status)) {
        throw new CloudRequestError("PARENT_NOT_OWNED", "parent_backup_id is not a valid owned backup.", 403);
      }
    }

    const cutoff = new Date(Date.now() - UPLOAD_AUTHORIZATION_WINDOW_MS).toISOString();
    const { count, error: quotaError } = await service
      .from("cloud_upload_authorizations")
      .select("authorization_id", { count: "exact", head: true })
      .eq("user_id", userId)
      .gte("issued_at", cutoff);
    if (quotaError) throw new Error("quota lookup failed");
    if ((count ?? 0) >= UPLOAD_AUTHORIZATION_LIMIT) {
      throw new CloudRequestError("UPLOAD_QUOTA_EXCEEDED", "Upload authorization quota exceeded.", 429);
    }

    const { data: storageState, error: storageError } = await service
      .from("cloud_storage_state")
      .select("cached_total_bytes")
      .eq("state_id", true)
      .maybeSingle();
    if (storageError) throw new Error("storage guard lookup failed");
    if ((storageState?.cached_total_bytes ?? 0) >= GLOBAL_STORAGE_GUARD_BYTES) {
      throw new CloudRequestError("GLOBAL_STORAGE_GUARD", "New uploads are temporarily paused.", 429);
    }

    const backupId = crypto.randomUUID();
    const objectKey = buildObjectKey(userId, backupId);
    const now = new Date();
    const expiresAt = new Date(now.getTime() + PRESIGN_EXPIRY_SECONDS * 1000);
    const backupRow = {
      backup_id: backupId,
      user_id: userId,
      parent_backup_id: requestBody.parent_backup_id,
      install_id: requestBody.install_id,
      created_at: now.toISOString(),
      object_key: objectKey,
      compressed_size_bytes: requestBody.compressed_size_bytes,
      uncompressed_size_bytes: requestBody.uncompressed_size_bytes,
      compression: "GZIP",
      checksum: requestBody.checksum,
      checksum_algorithm: requestBody.checksum_algorithm,
      backup_format_version: requestBody.backup_format_version,
      schema_version: requestBody.schema_version,
      app_version: requestBody.app_version,
      local_revision: requestBody.local_revision,
      status: "UPLOADING",
    };
    const { error: insertError } = await service.from("cloud_backups").insert(backupRow);
    if (insertError) throw new Error("backup metadata insert failed");

    try {
      const r2 = requiredR2Config();
      const uploadUrl = await presignPut(
        createR2Client(r2),
        r2.bucket,
        objectKey,
        requestBody.compressed_size_bytes,
      );
      const { error: authorizationError } = await service.from("cloud_upload_authorizations").insert({
        user_id: userId,
        backup_id: backupId,
        issued_at: now.toISOString(),
        expires_at: expiresAt.toISOString(),
        object_key: objectKey,
      });
      if (authorizationError) throw new Error("authorization metadata insert failed");

      return jsonResponse({
        backup_id: backupId,
        object_key: objectKey,
        upload_url: uploadUrl,
        expires_at: expiresAt.toISOString(),
        content_type: "application/gzip",
        status: "UPLOADING",
        local_revision: requestBody.local_revision,
      });
    } catch (error) {
      await failBackup(service, userId, backupId, requestBody.install_id, error);
      throw error;
    }
  } catch (error) {
    if (error instanceof CloudRequestError) return jsonResponse({ error: error.code }, error.status);
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
  }
}

Deno.serve(handler);
