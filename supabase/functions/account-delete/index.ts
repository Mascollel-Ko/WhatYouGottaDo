import {
  DeleteObjectsCommand,
  ListObjectsV2Command,
} from "npm:@aws-sdk/client-s3@3.842.0";
import { authenticateRequest, createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client } from "../_shared/r2.mjs";
import { emptyResponse, jsonResponse, isUuid } from "../_shared/cloud_backup.mjs";

const MAX_REAUTH_SECONDS = 180;
const CONFIRMATION = "DELETE_ACCOUNT";

function freshAccessToken(request, userId) {
  const bearer = (request.headers.get("authorization") ?? "").match(/^Bearer\\s+(.+)$/i)?.[1];
  if (!bearer) return false;
  try {
    const encoded = bearer.split(".")[1];
    const padded = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(padded + "=".repeat((4 - padded.length % 4) % 4)));
    const now = Math.floor(Date.now() / 1000);
    return claims.sub === userId && claims.role === "authenticated" &&
      Number.isFinite(claims.iat) && claims.iat <= now + 15 &&
      now - claims.iat <= MAX_REAUTH_SECONDS;
  } catch {
    return false;
  }
}

/** Delete only objects under the JWT user's exact immutable UUID prefix. */
async function removeR2PrivateObjects(client, bucket, userId) {
  if (!isUuid(userId)) throw new Error("Invalid user identifier");
  const prefix = `users/${userId}/`;
  // Always start at page one: deleting while paginating by continuation token
  // can skip keys depending on the backend's listing semantics.
  for (let pass = 0; pass < 1000; pass++) {
    const listing = await client.send(new ListObjectsV2Command({
      Bucket: bucket, Prefix: prefix, MaxKeys: 1000,
    }));
    const items = (listing.Contents ?? []).filter((x) =>
      typeof x.Key === "string" && x.Key.startsWith(prefix));
    if (items.length === 0) return;
    const deleted = await client.send(new DeleteObjectsCommand({
      Bucket: bucket,
      Delete: { Objects: items.map((x) => ({ Key: x.Key })), Quiet: true },
    }));
    if ((deleted.Errors ?? []).length !== 0) throw new Error("R2_DELETE_FAILED");
  }
  throw new Error("R2_DELETE_INCOMPLETE");
}

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);
  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;
  const userId = auth.user.id;
  if (!freshAccessToken(request, userId)) {
    return jsonResponse({ error: "RECENT_GOOGLE_LOGIN_REQUIRED" }, 403);
  }
  let body;
  try { body = await request.json(); } catch { return jsonResponse({ error: "INVALID_JSON" }, 400); }
  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body).length !== 1 || body.confirmation !== CONFIRMATION) {
    return jsonResponse({ error: "CONFIRMATION_REQUIRED" }, 400);
  }

  try {
    const service = createServiceClient();
    const contract = await service.rpc("account_deletion_contract_ready");
    if (contract.error || contract.data !== true) {
      return jsonResponse({ error: "ACCOUNT_DELETE_NOT_READY" }, 503);
    }

    // Set a durable lock before inspecting outstanding presigned URLs.
    const lock = await service.from("account_deletion_requests")
      .upsert({ user_id: userId }, { onConflict: "user_id" });
    if (lock.error) throw new Error("ACCOUNT_DELETE_LOCK_FAILED");

    // A previously issued PUT URL remains usable independently of JWT until
    // expiry; refuse deletion until all such URLs have expired.
    const active = await service.from("cloud_upload_authorizations")
      .select("expires_at")
      .eq("user_id", userId)
      .gt("expires_at", new Date().toISOString())
      .order("expires_at", { ascending: false })
      .limit(1);
    if (active.error) throw new Error("ACTIVE_UPLOAD_LOOKUP_FAILED");
    if (active.data?.length) {
      return jsonResponse({ error: "UPLOAD_URLS_STILL_ACTIVE",
        retryAfter: active.data[0].expires_at }, 409);
    }

    const config = requiredR2Config();
    await removeR2PrivateObjects(createR2Client(config), config.bucket, userId);

    // FK SET NULL leaves only user-published public snapshots; all private
    // profile, friend, weekly summary, backup metadata and likes rows cascade.
    const deleted = await service.auth.admin.deleteUser(userId, false);
    if (deleted.error) throw new Error("SUPABASE_AUTH_DELETE_FAILED");
    return jsonResponse({ ok: true, deleted: true, sharedProgramsPreserved: true });
  } catch (error) {
    console.error("account-delete failed", error instanceof Error ? error.message : "unknown");
    return jsonResponse({ error: "ACCOUNT_DELETION_FAILED" }, 500);
  }
}

Deno.serve(handler);
