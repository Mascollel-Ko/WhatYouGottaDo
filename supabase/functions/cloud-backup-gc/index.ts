import { emptyResponse, isUuid, jsonResponse } from "../_shared/cloud_backup.mjs";
import { cleanupUserBackups } from "../_shared/backup_cleanup.mjs";
import { createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client, measureR2BucketBytes } from "../_shared/r2.mjs";
import { removeR2PrivateObjects } from "../_shared/r2_user_cleanup.mjs";
import { reconcileR2Three } from "../_shared/r2_three_retention.mjs";

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
      !isUuid(body.ticket) ||
      (Object.keys(body).length !== 1 &&
        !(Object.keys(body).length === 2 &&
          (body.audit_usage === true || body.audit_three === true ||
           body.prune_to_three === true)))) {
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

  // A valid one-time ticket can request read-only R2 inventory for operational
  // verification. No user files are downloaded or modified by this route.
  if (body.audit_usage === true) {
    try {
      const r2 = requiredR2Config();
      const bucketBytes = await measureR2BucketBytes(createR2Client(r2), r2.bucket);
      return jsonResponse({ ok: true, bucketBytes });
    } catch {
      return jsonResponse({ error: "R2_LIST_UNAVAILABLE" }, 503);
    }
  }

  // Ticket-only administrative R2 inventory and one-shot precise retention.
  // audit_three verifies all three stored payloads without mutating data.
  // prune_to_three is deliberately never available to signed-in app users.
  if (body.audit_three === true || body.prune_to_three === true) {
    try {
      const { data, error } = await service.from("cloud_backups")
        .select("user_id").in("status", ["CURRENT", "RETAINED"])
        .order("created_at", { ascending: false }).limit(500);
      if (error) throw new Error("THREE_RETENTION_USER_DISCOVERY_FAILED");
      const users = [...new Set((data ?? []).map((row) => row.user_id))].filter(isUuid).slice(0, 8);
      if (users.length === 0) throw new Error("THREE_RETENTION_NO_ACCOUNT");
      const results = [];
      for (const userId of users) {
        try {
          results.push(await reconcileR2Three(service, userId, {
            dryRun: body.audit_three === true,
          }));
        } catch (failure) {
          results.push({ error: failure instanceof Error ? failure.message : "THREE_RETENTION_FAILED" });
        }
      }
      return jsonResponse({
        ok: results.every((x) => !x.error && (body.audit_three === true || x.exactThree)),
        accounts: results.length,
        results,
      });
    } catch {
      return jsonResponse({ error: "THREE_RETENTION_UNAVAILABLE" }, 503);
    }
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
    // Delete orphaned backup objects for accounts already removed from Auth.
    // A job can be queued before Auth deletion, so verify nonexistence first.
    const jobs = await service.from("account_deletion_cleanup_jobs")
      .select("user_id,attempts")
      .order("created_at", { ascending: true }).limit(10);
    if (jobs.error) throw new Error("ACCOUNT_CLEANUP_DISCOVERY_FAILED");
    let accountObjectsRemoved = 0, accountCleanupDeferred = 0;
    for (const job of jobs.data ?? []) {
      if (!isUuid(job.user_id)) continue;
      try {
        const lookup = await service.auth.admin.getUserById(job.user_id);
        if (lookup.data?.user || lookup.error?.status !== 404) {
          accountCleanupDeferred++;
          continue;
        }
        const r2 = requiredR2Config();
        await removeR2PrivateObjects(createR2Client(r2), r2.bucket, job.user_id);
        const removed = await service.from("account_deletion_cleanup_jobs")
          .delete().eq("user_id", job.user_id);
        if (removed.error) throw new Error("ACCOUNT_CLEANUP_ACK_FAILED");
        accountObjectsRemoved++;
      } catch {
        accountCleanupDeferred++;
        await service.from("account_deletion_cleanup_jobs")
          .update({ attempts: Number(job.attempts ?? 0) + 1, last_attempt_at: new Date().toISOString() })
          .eq("user_id", job.user_id);
      }
    }
    return jsonResponse({ ok: true, ...summary, accountObjectsRemoved, accountCleanupDeferred });
  } catch {
    return jsonResponse({ error: "CLEANUP_TEMPORARILY_UNAVAILABLE" }, 503);
  }
}

Deno.serve(handler);
