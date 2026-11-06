import { authenticateRequest, createServiceClient, requiredR2Config } from "../_shared/supabase_auth.mjs";
import { createR2Client } from "../_shared/r2.mjs";
import { removeR2PrivateObjects } from "../_shared/r2_user_cleanup.mjs";
import { emptyResponse, jsonResponse, isUuid } from "../_shared/cloud_backup.mjs";

const MAX_REAUTH_SECONDS = 300;
const CONFIRMATION = "DELETE_ACCOUNT";

// A freshly issued Supabase JWT is not sufficient proof of Google reauthentication.
// Validate the Google ID token and the nonce with Supabase Auth itself.
async function verifyFreshGoogleProof(userId, token, nonce) {
  if (typeof token !== "string" || token.length < 100 || token.length > 10000 ||
      typeof nonce !== "string" || nonce.length < 24 || nonce.length > 256) return false;
  const url = Deno.env.get("SUPABASE_URL");
  const apiKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !apiKey) throw new Error("SUPABASE_AUTH_CONFIG_MISSING");
  const response = await fetch(`${url}/auth/v1/token?grant_type=id_token`, {
    method: "POST",
    headers: { apikey: apiKey, "content-type": "application/json" },
    body: JSON.stringify({ provider: "google", id_token: token, nonce }),
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) return false;
  const validated = await response.json();
  if (validated?.user?.id !== userId) return false;
  // Supabase Auth verified signature/audience/issuer/nonce. Bound the proof
  // to a recently issued Google credential, not a reusable old JWT.
  try {
    const segment = token.split(".")[1];
    const raw = segment.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(raw + "=".repeat((4 - raw.length % 4) % 4)));
    const age = Date.now() / 1000 - claims.iat;
    return Number.isFinite(age) && age >= -15 && age <= MAX_REAUTH_SECONDS;
  } catch { return false; }
}

async function handler(request) {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST") return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);
  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;
  const userId = auth.user.id;
  let body;
  try { body = await request.json(); } catch { return jsonResponse({ error: "INVALID_JSON" }, 400); }
  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body).sort().join(",") !== "confirmation,google_id_token,nonce" ||
      body.confirmation !== CONFIRMATION) {
    return jsonResponse({ error: "CONFIRMATION_REQUIRED" }, 400);
  }

  try {
    if (!(await verifyFreshGoogleProof(userId, body.google_id_token, body.nonce))) {
      return jsonResponse({ error: "RECENT_GOOGLE_LOGIN_REQUIRED" }, 403);
    }
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

    // Persist the retry ticket before removing the authentication account.
    // Garbage collection verifies that the account no longer exists.
    const ticket = await service.from("account_deletion_cleanup_jobs")
      .upsert({ user_id: userId }, { onConflict: "user_id" });
    if (ticket.error) throw new Error("ACCOUNT_CLEANUP_QUEUE_FAILED");

    const deleted = await service.auth.admin.deleteUser(userId, false);
    if (deleted.error) throw new Error("SUPABASE_AUTH_DELETE_FAILED");

    let backupCleanupPending = false;
    try {
      const config = requiredR2Config();
      await removeR2PrivateObjects(createR2Client(config), config.bucket, userId);
      const cleared = await service.from("account_deletion_cleanup_jobs")
        .delete().eq("user_id", userId);
      if (cleared.error) backupCleanupPending = true;
    } catch {
      backupCleanupPending = true;
    }
    return jsonResponse({
      ok: true, deleted: true, sharedProgramsPreserved: true,
      backupCleanupPending,
    });
  } catch (error) {
    console.error("account-delete failed", error instanceof Error ? error.message : "unknown");
    return jsonResponse({ error: "ACCOUNT_DELETION_FAILED" }, 500);
  }
}

Deno.serve(handler);
