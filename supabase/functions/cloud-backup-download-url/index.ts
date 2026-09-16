import {
  CloudRequestError,
  PRESIGN_EXPIRY_SECONDS,
  buildObjectKey,
  emptyResponse,
  isDownloadableStatus,
  jsonResponse,
  parseJsonRequest,
  validateDownloadRequest,
} from "../_shared/cloud_backup.mjs";
import { authenticateRequest, createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client, presignGet } from "../_shared/r2.mjs";

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);

  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;

  try {
    const { backup_id: backupId } = validateDownloadRequest(await parseJsonRequest(request));
    const service = createServiceClient();
    const userId = auth.user.id;
    const { data: backup, error } = await service
      .from("cloud_backups")
      .select("backup_id,user_id,object_key,status,compression,backup_format_version,schema_version,compressed_size_bytes,uncompressed_size_bytes,checksum,checksum_algorithm,app_version,local_revision")
      .eq("backup_id", backupId)
      .eq("user_id", userId)
      .maybeSingle();
    if (error) throw new Error("backup lookup failed");
    if (!backup || !isDownloadableStatus(backup.status)) {
      throw new CloudRequestError("BACKUP_NOT_AVAILABLE", "Backup is not available for download.", 404);
    }

    const r2 = requiredR2Config();
    const expectedKey = buildObjectKey(userId, backupId);
    if (backup.object_key !== expectedKey) throw new Error("stored object key failed canonical check");
    const downloadUrl = await presignGet(createR2Client(r2), r2.bucket, expectedKey);

    return jsonResponse({
      backup_id: backupId,
      object_key: expectedKey,
      download_url: downloadUrl,
      expires_at: new Date(Date.now() + PRESIGN_EXPIRY_SECONDS * 1000).toISOString(),
      content_type: "application/gzip",
      compression: backup.compression,
      backup_format_version: backup.backup_format_version,
      schema_version: backup.schema_version,
      compressed_size_bytes: backup.compressed_size_bytes,
      uncompressed_size_bytes: backup.uncompressed_size_bytes,
      checksum: backup.checksum,
      checksum_algorithm: backup.checksum_algorithm,
      app_version: backup.app_version,
      local_revision: backup.local_revision,
      status: backup.status,
    });
  } catch (error) {
    if (error instanceof CloudRequestError) return jsonResponse({ error: error.code }, error.status);
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
  }
}

Deno.serve(handler);
