import assert from "node:assert/strict";
import test from "node:test";
import {
  CloudRequestError,
  PRESIGN_EXPIRY_SECONDS,
  buildGetSigningRequest,
  buildObjectKey,
  buildPutSigningRequest,
  assertParentOwned,
  emptyResponse,
  isDownloadableStatus,
  issuePresignedUrl,
  validateDownloadRequest,
  validateUploadRequest,
} from "../supabase/functions/_shared/cloud_backup.mjs";

const USER_A = "11111111-1111-4111-8111-111111111111";
const USER_B = "22222222-2222-4222-8222-222222222222";
const INSTALL_A = "33333333-3333-4333-8333-333333333333";
const PARENT_A = "44444444-4444-4444-8444-444444444444";

function validUpload(overrides = {}) {
  return {
    install_id: INSTALL_A,
    parent_backup_id: PARENT_A,
    compressed_size_bytes: 1024,
    uncompressed_size_bytes: 4096,
    checksum: "a".repeat(64),
    checksum_algorithm: "SHA-256",
    backup_format_version: 14,
    schema_version: 13,
    app_version: "test-build",
    local_revision: 3,
    compression: "GZIP",
    ...overrides,
  };
}

function assertRequestError(fn, code) {
  assert.throws(fn, (error) => error instanceof CloudRequestError && error.code === code);
}

test("upload validation rejects malformed checksum", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ checksum: "bad" })), "INVALID_CHECKSUM");
});

test("upload validation rejects unsupported format and schema", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ backup_format_version: 13 })), "UNSUPPORTED_FORMAT");
  assertRequestError(() => validateUploadRequest(validUpload({ schema_version: 12 })), "UNSUPPORTED_SCHEMA");
});

test("upload validation rejects invalid parent UUID", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ parent_backup_id: "not-a-uuid" })), "INVALID_UUID");
});

test("client cannot supply server authority fields", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ user_id: USER_A })), "CLIENT_AUTHORITY_FIELD");
  assertRequestError(() => validateUploadRequest(validUpload({ object_key: "users/arbitrary" })), "CLIENT_AUTHORITY_FIELD");
  assertRequestError(() => validateUploadRequest(validUpload({ backup_id: PARENT_A })), "CLIENT_AUTHORITY_FIELD");
  assertRequestError(() => validateDownloadRequest({ backup_id: PARENT_A, user_id: USER_A }), "CLIENT_AUTHORITY_FIELD");
  assertRequestError(() => validateDownloadRequest({ backup_id: PARENT_A, object_key: "users/arbitrary" }), "CLIENT_AUTHORITY_FIELD");
});

test("size validation rejects negative and over-ceiling uploads", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ compressed_size_bytes: -1 })), "INVALID_SIZE");
  assertRequestError(() => validateUploadRequest(validUpload({ compressed_size_bytes: 20 * 1024 * 1024 + 1 })), "BACKUP_TOO_LARGE");
});

test("source revision validation rejects negative revisions", () => {
  assertRequestError(() => validateUploadRequest(validUpload({ local_revision: -1 })), "INVALID_LOCAL_REVISION");
});

test("server object key is owned by the verified user", () => {
  const key = buildObjectKey(USER_A, PARENT_A);
  assert.equal(key, `users/${USER_A}/backups/${PARENT_A}.csv.gz`);
  assert.notEqual(key, buildObjectKey(USER_B, PARENT_A));
  assert.doesNotThrow(() => assertParentOwned(USER_A, USER_A));
  assertRequestError(() => assertParentOwned(USER_A, USER_B), "PARENT_NOT_OWNED");
});

test("download authorization accepts only an owned backup id and downloadable status", () => {
  assert.deepEqual(validateDownloadRequest({ backup_id: PARENT_A }), { backup_id: PARENT_A });
  assert.equal(isDownloadableStatus("CURRENT"), true);
  assert.equal(isDownloadableStatus("UPLOADING"), false);
  assert.equal(isDownloadableStatus("FAILED"), false);
});

test("CORS preflight response is bodyless and successful", () => {
  const response = emptyResponse();
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-methods"), "POST, OPTIONS");
});

test("injected signing helper signs exact operation and key with bounded expiry", async () => {
  const putRequest = buildPutSigningRequest("whatyougottado-backups", buildObjectKey(USER_A, PARENT_A), 1024);
  const getRequest = buildGetSigningRequest(putRequest.bucket, putRequest.objectKey);
  const calls = [];
  const signer = async (request, expiresInSeconds) => {
    calls.push({ request, expiresInSeconds });
    return `https://signed.invalid/${request.operation}/${request.objectKey}?expires=${expiresInSeconds}`;
  };

  const putUrl = await issuePresignedUrl({ signer, request: putRequest });
  const getUrl = await issuePresignedUrl({ signer, request: getRequest, expiresInSeconds: 60 });
  assert.equal(calls[0].request.operation, "PUT");
  assert.equal(calls[0].request.objectKey, putRequest.objectKey);
  assert.equal(calls[0].request.contentType, "application/gzip");
  assert.equal(calls[0].expiresInSeconds, PRESIGN_EXPIRY_SECONDS);
  assert.equal(calls[1].request.operation, "GET");
  assert.equal(calls[1].request.objectKey, getRequest.objectKey);
  assert.equal(calls[1].expiresInSeconds, 60);
  assert.match(putUrl, /PUT/);
  assert.match(getUrl, /GET/);
  assert.doesNotMatch(putUrl + getUrl, /SECRET|ACCESS_KEY|JWT/i);
});
