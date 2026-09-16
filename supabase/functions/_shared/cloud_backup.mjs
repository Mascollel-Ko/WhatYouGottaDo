export const CURRENT_BACKUP_FORMAT = 14;
export const CURRENT_SCHEMA_VERSION = 13;
export const MAX_COMPRESSED_BYTES = 20 * 1024 * 1024;
export const UPLOAD_AUTHORIZATION_LIMIT = 30;
export const UPLOAD_AUTHORIZATION_WINDOW_MS = 24 * 60 * 60 * 1000;
export const PRESIGN_EXPIRY_SECONDS = 5 * 60;
export const GLOBAL_STORAGE_GUARD_BYTES = 8 * 1024 * 1024 * 1024;
export const R2_CONTENT_TYPE = "application/gzip";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CHECKSUM_PATTERN = /^[0-9a-f]{64}$/i;
const ALLOWED_UPLOAD_FIELDS = new Set([
  "parent_backup_id",
  "install_id",
  "compressed_size_bytes",
  "uncompressed_size_bytes",
  "checksum",
  "checksum_algorithm",
  "backup_format_version",
  "schema_version",
  "app_version",
  "local_revision",
  "compression",
]);

export class CloudRequestError extends Error {
  constructor(code, message, status = 400) {
    super(message);
    this.name = "CloudRequestError";
    this.code = code;
    this.status = status;
  }
}

export function isUuid(value) {
  return typeof value === "string" && UUID_PATTERN.test(value);
}

function assertObject(value) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new CloudRequestError("INVALID_JSON", "Request body must be a JSON object.");
  }
}

function rejectUnknownOrClientAuthorityFields(value, allowedFields) {
  for (const key of Object.keys(value)) {
    if (key === "user_id" || key === "object_key" || key === "backup_id") {
      throw new CloudRequestError(
        "CLIENT_AUTHORITY_FIELD",
        `${key} is server-controlled and must not be supplied.`,
      );
    }
    if (!allowedFields.has(key)) {
      throw new CloudRequestError("UNKNOWN_FIELD", `Unsupported request field: ${key}.`);
    }
  }
}

function requireUuid(value, field) {
  if (!isUuid(value)) {
    throw new CloudRequestError("INVALID_UUID", `${field} must be a UUID.`);
  }
  return value.toLowerCase();
}

function optionalNonNegativeInteger(value, field) {
  if (value === undefined || value === null) return null;
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new CloudRequestError("INVALID_SIZE", `${field} must be a non-negative integer.`);
  }
  return value;
}

export function validateUploadRequest(value) {
  assertObject(value);
  rejectUnknownOrClientAuthorityFields(value, ALLOWED_UPLOAD_FIELDS);

  const installId = requireUuid(value.install_id, "install_id");
  const parentBackupId = value.parent_backup_id == null
    ? null
    : requireUuid(value.parent_backup_id, "parent_backup_id");

  if (!Number.isSafeInteger(value.compressed_size_bytes) || value.compressed_size_bytes < 0) {
    throw new CloudRequestError(
      "INVALID_SIZE",
      "compressed_size_bytes must be a non-negative integer.",
    );
  }
  if (value.compressed_size_bytes > MAX_COMPRESSED_BYTES) {
    throw new CloudRequestError(
      "BACKUP_TOO_LARGE",
      `compressed_size_bytes exceeds the ${MAX_COMPRESSED_BYTES}-byte limit.`,
      413,
    );
  }
  const uncompressedSize = optionalNonNegativeInteger(
    value.uncompressed_size_bytes,
    "uncompressed_size_bytes",
  );

  if (value.backup_format_version !== CURRENT_BACKUP_FORMAT) {
    throw new CloudRequestError("UNSUPPORTED_FORMAT", "Only backup format 14 is supported.");
  }
  if (value.schema_version !== CURRENT_SCHEMA_VERSION) {
    throw new CloudRequestError("UNSUPPORTED_SCHEMA", "Only restore schema 13 is supported.");
  }
  if (!Number.isSafeInteger(value.local_revision) || value.local_revision < 0) {
    throw new CloudRequestError("INVALID_LOCAL_REVISION", "local_revision must be a non-negative integer.");
  }

  const compression = value.compression ?? "GZIP";
  if (compression !== "GZIP") {
    throw new CloudRequestError("UNSUPPORTED_COMPRESSION", "Only GZIP compression is supported.");
  }

  const checksum = value.checksum == null ? null : value.checksum;
  if (checksum !== null && (typeof checksum !== "string" || !CHECKSUM_PATTERN.test(checksum))) {
    throw new CloudRequestError("INVALID_CHECKSUM", "checksum must be 64 hexadecimal characters.");
  }
  const checksumAlgorithm = value.checksum_algorithm == null ? null : value.checksum_algorithm;
  if (checksum !== null && checksumAlgorithm !== "SHA-256") {
    throw new CloudRequestError("INVALID_CHECKSUM_ALGORITHM", "checksum_algorithm must be SHA-256.");
  }
  if (checksum === null && checksumAlgorithm !== null) {
    throw new CloudRequestError(
      "INVALID_CHECKSUM_ALGORITHM",
      "checksum_algorithm requires a checksum.",
    );
  }

  if (typeof value.app_version !== "string" || value.app_version.trim().length < 1 || value.app_version.length > 160) {
    throw new CloudRequestError("INVALID_APP_VERSION", "app_version must be 1–160 characters.");
  }

  return {
    parent_backup_id: parentBackupId,
    install_id: installId,
    compressed_size_bytes: value.compressed_size_bytes,
    uncompressed_size_bytes: uncompressedSize,
    checksum: checksum === null ? null : checksum.toLowerCase(),
    checksum_algorithm: checksum === null ? null : checksumAlgorithm,
    backup_format_version: CURRENT_BACKUP_FORMAT,
    schema_version: CURRENT_SCHEMA_VERSION,
    app_version: value.app_version.trim(),
    local_revision: value.local_revision,
    compression: "GZIP",
  };
}

export function validateDownloadRequest(value) {
  assertObject(value);
  for (const key of Object.keys(value)) {
    if (key === "user_id" || key === "object_key") {
      throw new CloudRequestError(
        "CLIENT_AUTHORITY_FIELD",
        `${key} is server-controlled and must not be supplied.`,
      );
    }
    if (key !== "backup_id") {
      throw new CloudRequestError("UNKNOWN_FIELD", `Unsupported request field: ${key}.`);
    }
  }
  return { backup_id: requireUuid(value.backup_id, "backup_id") };
}

export function buildObjectKey(userId, backupId) {
  return `users/${requireUuid(userId, "user_id")}/backups/${requireUuid(backupId, "backup_id")}.csv.gz`;
}

export function assertParentOwned(parentUserId, requesterUserId) {
  if (!parentUserId || parentUserId.toLowerCase() !== requesterUserId.toLowerCase()) {
    throw new CloudRequestError("PARENT_NOT_OWNED", "parent_backup_id is not owned by this user.", 403);
  }
}

export function isDownloadableStatus(status) {
  return status === "VERIFIED" || status === "CURRENT" || status === "RETAINED" ||
    status === "CONFLICT_RECOVERY";
}

export function buildPutSigningRequest(bucket, objectKey, compressedSizeBytes) {
  return {
    operation: "PUT",
    bucket,
    objectKey,
    contentType: R2_CONTENT_TYPE,
    contentLength: compressedSizeBytes,
  };
}

export function buildGetSigningRequest(bucket, objectKey) {
  return {
    operation: "GET",
    bucket,
    objectKey,
  };
}

export async function issuePresignedUrl({ signer, request, expiresInSeconds = PRESIGN_EXPIRY_SECONDS }) {
  if (typeof signer !== "function") throw new TypeError("signer must be a function");
  if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 1 || expiresInSeconds > PRESIGN_EXPIRY_SECONDS) {
    throw new CloudRequestError("INVALID_EXPIRY", "Presign expiry must be between 1 and 300 seconds.");
  }
  return signer(request, expiresInSeconds);
}

export function jsonResponse(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type, apikey, x-client-info",
      "access-control-allow-methods": "POST, OPTIONS",
      ...extraHeaders,
    },
  });
}

export function emptyResponse(status = 204, extraHeaders = {}) {
  return new Response(null, {
    status,
    headers: {
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type, apikey, x-client-info",
      "access-control-allow-methods": "POST, OPTIONS",
      ...extraHeaders,
    },
  });
}

export async function parseJsonRequest(request) {
  try {
    return await request.json();
  } catch {
    throw new CloudRequestError("INVALID_JSON", "Request body must be valid JSON.");
  }
}
