import {
  GetObjectCommand,
  HeadObjectCommand,
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
