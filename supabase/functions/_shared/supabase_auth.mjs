import { createClient } from "npm:@supabase/supabase-js@2.49.8";
import { jsonResponse } from "./cloud_backup.mjs";

const EXPECTED_R2_BUCKET = "whatyougottado-backups";

function requiredEnv(name) {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing required server configuration: ${name}`);
  return value;
}

export function createServiceClient() {
  return createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export async function authenticateRequest(request) {
  const authorization = request.headers.get("authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return { error: jsonResponse({ error: "AUTH_REQUIRED" }, 401) };
  }

  const supabase = createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_ANON_KEY"), {
    global: { headers: { Authorization: `Bearer ${match[1]}` } },
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data, error } = await supabase.auth.getUser();
  if (error || !data.user) {
    return { error: jsonResponse({ error: "AUTH_INVALID" }, 401) };
  }
  return { user: data.user };
}

export function requiredR2Config() {
  const bucket = requiredEnv("R2_BUCKET");
  if (bucket !== EXPECTED_R2_BUCKET) {
    throw new Error("R2_BUCKET does not match the Cloud Backup bucket");
  }
  return {
    accountId: requiredEnv("R2_ACCOUNT_ID"),
    accessKeyId: requiredEnv("R2_ACCESS_KEY_ID"),
    secretAccessKey: requiredEnv("R2_SECRET_ACCESS_KEY"),
    bucket,
  };
}
