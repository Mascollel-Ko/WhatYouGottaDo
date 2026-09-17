import {
  CloudRequestError,
  emptyResponse,
  jsonResponse,
  parseJsonRequest,
  validateCurrentRequest,
} from "../_shared/cloud_backup.mjs";
import { authenticateRequest, createServiceClient } from "../_shared/supabase_auth.mjs";

/** Return only the authenticated user's CURRENT metadata. Object capabilities stay private. */
async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);

  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;

  try {
    validateCurrentRequest(await parseJsonRequest(request));
    const service = createServiceClient();
    const { data: current, error } = await service
      .from("cloud_backups")
      .select("backup_id,created_at,local_revision,backup_format_version,schema_version,status")
      .eq("user_id", auth.user.id)
      .eq("status", "CURRENT")
      .maybeSingle();
    if (error) throw new Error("current backup lookup failed");
    if (!current) return jsonResponse({ current: null });

    return jsonResponse({
      current: {
        backup_id: current.backup_id,
        created_at: current.created_at,
        local_revision: current.local_revision,
        backup_format_version: current.backup_format_version,
        schema_version: current.schema_version,
        status: "CURRENT",
      },
    });
  } catch (error) {
    if (error instanceof CloudRequestError) return jsonResponse({ error: error.code }, error.status);
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
  }
}

Deno.serve(handler);
