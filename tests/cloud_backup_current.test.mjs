import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { CloudRequestError, validateCurrentRequest } from "../supabase/functions/_shared/cloud_backup.mjs";

const endpoint = await readFile(
  new URL("../supabase/functions/cloud-backup-current/index.ts", import.meta.url),
  "utf8",
);
const config = await readFile(
  new URL("../supabase/config.toml", import.meta.url),
  "utf8",
);

function assertRequestError(value, code) {
  assert.throws(() => validateCurrentRequest(value), (error) =>
    error instanceof CloudRequestError && error.code === code);
}

test("CURRENT discovery validates an empty request and rejects client authority", () => {
  assert.deepEqual(validateCurrentRequest({}), {});
  assertRequestError({ user_id: "client-supplied" }, "CLIENT_AUTHORITY_FIELD");
  assertRequestError({ backup_id: "client-supplied" }, "CLIENT_AUTHORITY_FIELD");
  assertRequestError({ unexpected: true }, "UNKNOWN_FIELD");
});

test("CURRENT discovery is JWT-authenticated and scoped to the verified user", () => {
  assert.match(endpoint, /authenticateRequest\(request\)/);
  assert.match(endpoint, /auth\.user\.id/);
  assert.match(endpoint, /\.eq\("user_id", auth\.user\.id\)/);
  assert.match(endpoint, /\.eq\("status", "CURRENT"\)/);
  assert.match(endpoint, /current:\s*null/);
  assert.doesNotMatch(endpoint, /download_url|object_key|presign/i);
});

test("CURRENT discovery keeps gateway JWT verification enabled", () => {
  assert.match(config, /\[functions\.cloud-backup-current\]\s+verify_jwt = true/);
});
