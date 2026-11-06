import { DeleteObjectsCommand, ListObjectsV2Command } from "npm:@aws-sdk/client-s3@3.842.0";
import { isUuid } from "./cloud_backup.mjs";

/** Delete only objects under the JWT user's exact immutable UUID prefix. */
export async function removeR2PrivateObjects(client, bucket, userId) {
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

