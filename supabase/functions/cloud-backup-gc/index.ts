import { emptyResponse, isUuid, jsonResponse } from "../_shared/cloud_backup.mjs";
import { cleanupUserBackups } from "../_shared/backup_cleanup.mjs";
import { createServiceClient } from "../_shared/supabase_auth.mjs";

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: "INVALID_JSON" }, 400);
  }
  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body).length !== 1 || !isUuid(body.ticket)) {
    return jsonResponse({ error: "INVALID_CLEANUP_TICKET" }, 403);
  }

  const service = createServiceClient();
  // Tickets are unguessable, server-minted, expire after 5m and are consumed
  // atomically. No user or publishable token can invoke privileged cleanup.
  const verified = await service.rpc("cloud_consume_cleanup_ticket", {
    p_ticket: body.ticket,
  });
  if (verified.error || verified.data !== true) {
    return jsonResponse({ error: "CLEANUP_NOT_AUTHORIZED" }, 403);
  }

  try {
    const { data, error } = await service.from("cloud_backups")
      .select("user_id")
      .in("status", [
        "UPLOADING", "FAILED", "RETAINED",
        "CONFLICT_RECOVERY", "CLEANUP_PENDING", "PRUNED",
      ])
      .order("created_at", { ascending: true }).limit(500);
    if (error) throw new Error("CLEANUP_USER_DISCOVERY_FAILED");
    const users = [...new Set((data ?? []).map((row) => row.user_id))]
      .filter(isUuid).slice(0, 8);
    const summary = { users: users.length, filesPruned: 0, failures: 0 };
    for (const userId of users) {
      try {
        const result = await cleanupUserBackups(service, userId, 50);
        summary.filesPruned += result.pruned;
        summary.failures += result.failures;
      } catch {
        summary.failures++;
      }
    }
    return jsonResponse({ ok: true, ...summary });
  } catch {
    return jsonResponse({ error: "CLEANUP_TEMPORARILY_UNAVAILABLE" }, 503);
  }
}

Deno.serve(handler);
