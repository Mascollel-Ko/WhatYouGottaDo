import {
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from "npm:@aws-sdk/client-s3@3.842.0";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3.842.0";
import {
  PRESIGN_EXPIRY_SECONDS,
  buildGetSigningRequest,
  buildPutSigningRequest,
  issuePresignedUrl,
} from "./cloud_backup.mjs";

export function createR2Client(config) {
  return new S3Client({
    region: "auto",
    endpoint: `https://${config.accountId}.r2.cloudflarestorage.com`,
    credentials: {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
    },
  });
}

export function presignPut(client, bucket, objectKey, compressedSizeBytes) {
  const request = buildPutSigningRequest(bucket, objectKey, compressedSizeBytes);
  const command = new PutObjectCommand({
    Bucket: request.bucket,
    Key: request.objectKey,
    ContentType: request.contentType,
    ContentLength: request.contentLength,
  });
  return issuePresignedUrl({
    request,
    expiresInSeconds: PRESIGN_EXPIRY_SECONDS,
    signer: (_request, expiresIn) => getSignedUrl(client, command, { expiresIn }),
  });
}

export function presignGet(client, bucket, objectKey) {
  const request = buildGetSigningRequest(bucket, objectKey);
  const command = new GetObjectCommand({
    Bucket: request.bucket,
    Key: request.objectKey,
    ResponseContentType: "application/gzip",
  });
  return issuePresignedUrl({
    request,
    expiresInSeconds: PRESIGN_EXPIRY_SECONDS,
    signer: (_request, expiresIn) => getSignedUrl(client, command, { expiresIn }),
  });
}

export function headObject(client, bucket, objectKey) {
  return client.send(new HeadObjectCommand({ Bucket: bucket, Key: objectKey }));
}

export function getObject(client, bucket, objectKey) {
  return client.send(new GetObjectCommand({ Bucket: bucket, Key: objectKey }));
}

/** Read an SDK response body without allowing an object larger than maxBytes. */
export async function readBodyBytes(body, maxBytes) {
  if (!body || typeof body[Symbol.asyncIterator] !== "function") {
    throw new Error("R2 object body is not streamable");
  }
  const chunks = [];
  let length = 0;
  for await (const chunk of body) {
    const bytes = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk);
    length += bytes.byteLength;
    if (length > maxBytes) throw new Error("R2 object exceeded verification bound");
    chunks.push(bytes);
  }
  const output = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    output.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return output;
}

/** The R2 bucket is the source of truth for paid storage bytes. Do not derive
 * usage from backup metadata (FAILED and orphan objects may still exist).
 * Bounded pagination fails closed on malformed / unavailable listings.
 */
export async function measureR2BucketBytes(client, bucket) {
  let cursor;
  let total = 0;
  for (let page = 0; page < 10000; page++) {
    const listing = await client.send(new ListObjectsV2Command({
      Bucket: bucket,
      MaxKeys: 1000,
      ...(cursor ? { ContinuationToken: cursor } : {}),
    }));
    for (const object of listing.Contents ?? []) {
      if (!Number.isSafeInteger(object.Size) || object.Size < 0 ||
          total > Number.MAX_SAFE_INTEGER - object.Size) {
        throw new Error("R2_SIZE_INVALID");
      }
      total += object.Size;
    }
    if (!listing.IsTruncated) return total;
    if (!listing.NextContinuationToken ||
        listing.NextContinuationToken === cursor) throw new Error("R2_PAGINATION_INVALID");
    cursor = listing.NextContinuationToken;
  }
  throw new Error("R2_LISTING_TOO_LARGE");
}
