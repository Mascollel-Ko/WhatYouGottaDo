import {
  createServiceClient,
  authenticateRequest,
} from "../_shared/supabase_auth.mjs";
import { emptyResponse, jsonResponse } from "../_shared/cloud_backup.mjs";

const MAX_PAGE_SIZE = 20;
const ACTIVITY_TTL_MS = 90 * 60 * 1000;
const LABELS = {
  strengthRegions: ["UPPER_BODY", "LOWER_BODY", "ALL_LIMBS"],
  strengthGoals: ["HYPERTROPHY", "STRENGTH"],
  functionalGoals: [
    "EXPLOSIVE_ACCELERATION",
    "ELASTIC_GROUND_REACTION",
    "BODY_COORDINATION",
  ],
  badmintonGoals: [
    "SWING_POWER",
    "LANDING_DECELERATION_STABILITY",
    "FOOTWORK",
  ],
};

class CommunityError extends Error {
  status: number;
  code: string;
  constructor(code: string, status = 400) {
    super(code);
    this.code = code;
    this.status = status;
  }
}

function bodyObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new CommunityError("INVALID_JSON");
  return value as Record<string, unknown>;
}

function text(value: unknown, field: string, max: number): string {
  if (typeof value !== "string")
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  const result = value.trim();
  if (result.length > max || /[\u0000-\u001f\u007f\r\n]/u.test(result))
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  return result;
}

function optionalText(value: unknown, field: string, max: number): string {
  if (value == null) return "";
  return text(value, field, max);
}

function bool(value: unknown, field: string, fallback = false): boolean {
  if (value == null) return fallback;
  if (typeof value !== "boolean")
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  return value;
}

function labelArray(value: unknown, field: string, allowed: string[], required: boolean, allowNotIncluded = false): string[] {
  if (!Array.isArray(value)) throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  const normalized = value.map((item) => text(item, field, 60).toUpperCase());
  const permitted = allowNotIncluded ? [...allowed, "NOT_INCLUDED"] : allowed;
  if (normalized.length > permitted.length || new Set(normalized).size !== normalized.length || normalized.some((item) => !permitted.includes(item)))
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  const result = allowed.filter((item) => normalized.includes(item));
  if (normalized.includes("NOT_INCLUDED")) result.push("NOT_INCLUDED");
  if (required && result.length === 0) throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  return result;
}

function searchLabelArray(value: unknown, field: string, allowed: string[], allowNotIncluded = false): string[] {
  if (value == null) return [];
  return labelArray(value, field, allowed, false, allowNotIncluded);
}

function applyLabelFilter(query: any, column: string, values: string[], allowNotIncluded = false): any {
  if (!values.length) return query;
  const include = values.filter((value) => value !== "NOT_INCLUDED");
  const notIncluded = allowNotIncluded && values.includes("NOT_INCLUDED");
  if (notIncluded && include.length === 0) return query.eq(column, "{}");
  if (!notIncluded) return query.overlaps(column, include);
  const literal = `{${include.join(",")}}`;
  return query.or(`${column}.eq.{},${column}.ov.${literal}`);
}

function uuid(value: unknown, field: string): string {
  if (
    typeof value !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    )
  ) {
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  }
  return value.toLowerCase();
}

function nickname(value: unknown): string {
  if (typeof value !== "string") throw new CommunityError("INVALID_NICKNAME");
  const result = value.trim();
  const codePointLength = [...result].length;
  if (
    codePointLength < 2 ||
    codePointLength > 24 ||
    /[\u0000-\u001f\u007f\r\n]/u.test(result)
  ) {
    throw new CommunityError("INVALID_NICKNAME");
  }
  return result;
}

function date(value: unknown, field: string): string {
  const result = text(value, field, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(result))
    throw new CommunityError(`INVALID_${field.toUpperCase()}`);
  return result;
}

function pageSize(value: unknown): number {
  if (value == null) return MAX_PAGE_SIZE;
  if (!Number.isInteger(value)) throw new CommunityError("INVALID_PAGE_SIZE");
  return Math.min(MAX_PAGE_SIZE, Math.max(1, value as number));
}

function decodeCursor(value: unknown): Record<string, unknown> | null {
  if (value == null || value === "") return null;
  if (typeof value !== "string" || value.length > 512)
    throw new CommunityError("INVALID_CURSOR");
  try {
    const decoded = JSON.parse(atob(value));
    if (!decoded || typeof decoded !== "object" || Array.isArray(decoded))
      throw new Error();
    return decoded as Record<string, unknown>;
  } catch {
    throw new CommunityError("INVALID_CURSOR");
  }
}

function profileDto(profile: any) {
  return {
    nickname: profile.nickname ?? null,
    receivedLikeCount: Number(profile.received_program_likes ?? 0),
    friendCode: profile.friend_code,
    shareLastWorkoutTime: !!profile.share_last_workout_time,
    shareCurrentTrainingStatus: !!profile.share_current_training_status,
    shareCurrentExerciseName: !!profile.share_current_exercise_name,
  };
}

async function ensureProfile(service: any, userId: string): Promise<any> {
  const { data, error } = await service.rpc("community_ensure_profile", {
    p_user_id: userId,
  });
  if (error || !data) throw new CommunityError("PROFILE_FAILED", 500);
  return Array.isArray(data) ? data[0] : data;
}

function normalizeSnapshot(value: unknown): Record<string, unknown> {
  const input = bodyObject(value);
  const sourceProgram = bodyObject(input.program);
  const allowedProgram: Record<string, unknown> = {};
  for (const key of [
    "name",
    "durationDays",
    "goal",
    "weeklyTrainingDays",
    "sessionMinutes",
    "availableEquipment",
    "periodizationType",
  ]) {
    if (sourceProgram[key] !== undefined)
      allowedProgram[key] = sourceProgram[key];
  }
  const rawItems = Array.isArray(input.items) ? input.items : [];
  if (rawItems.length > 1000)
    throw new CommunityError("SNAPSHOT_TOO_LARGE", 413);
  const items = rawItems.map((raw) => {
    const row = bodyObject(raw);
    const result: Record<string, unknown> = {};
    for (const key of [
      "weekNumber",
      "dayOfWeek",
      "orderIndex",
      "exerciseStableKey",
      "exerciseName",
      "category",
      "restSeconds",
      "prescription",
      "setCount",
      "reps",
      "weightKg",
      "seconds",
      "trainingSlot",
      "dayIntensity",
      "weightSource",
    ]) {
      if (row[key] !== undefined) result[key] = row[key];
    }
    const sets = Array.isArray(row.sets) ? row.sets : [];
    if (sets.length > 100) throw new CommunityError("SNAPSHOT_TOO_LARGE", 413);
    result.sets = sets.map((rawSet) => {
      const set = bodyObject(rawSet);
      const normalized: Record<string, unknown> = {};
      for (const key of ["setIndex", "reps", "weightKg", "seconds"])
        if (set[key] !== undefined) normalized[key] = set[key];
      return normalized;
    });
    return result;
  });
  const rawProgression =
    input.progression && typeof input.progression === "object"
      ? bodyObject(input.progression)
      : {};
  const tracks = Array.isArray(rawProgression.tracks)
    ? rawProgression.tracks
    : [];
  const bindings = Array.isArray(rawProgression.bindings)
    ? rawProgression.bindings
    : [];
  const progression = {
    tracks: tracks.slice(0, 1000).map((raw) => {
      const row = bodyObject(raw);
      const result: Record<string, unknown> = {};
      for (const key of [
        "exerciseStableKey",
        "label",
        "basePolicy",
        "anchorSetIndex",
        "role",
        "roleOverride",
        "mode",
        "rule",
        "needsReview",
      ])
        if (row[key] !== undefined) result[key] = row[key];
      return result;
    }),
    bindings: bindings.slice(0, 2000).map((raw) => {
      const row = bodyObject(raw);
      const result: Record<string, unknown> = {};
      for (const key of [
        "itemIndex",
        "logicalItemId",
        "linkMode",
        "signature",
        "trackIndex",
      ])
        if (row[key] !== undefined) result[key] = row[key];
      return result;
    }),
  };
  return { schemaVersion: 1, program: allowedProgram, items, progression };
}

async function sha256(value: unknown): Promise<string> {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((part) => part.toString(16).padStart(2, "0"))
    .join("");
}

function programDto(
  row: any,
  profile: any,
  liked: Set<string>,
  includeSnapshot: boolean,
  userId?: string,
) {
  const isMine = userId != null && row.owner_user_id === userId;
  return {
    publicProgramId: row.public_program_id,
    ...(isMine ? { sourceProgramStableKey: row.source_program_stable_key } : {}),
    nickname: profile?.nickname ?? "",
    authorReceivedLikeCount: Number(profile?.received_program_likes ?? 0),
    publishedAt: row.published_at,
    updatedAt: row.updated_at,
    programName: row.program_name,
    labels: {
      strengthRegions: row.strength_regions ?? (row.strength_region ? [row.strength_region] : []),
      strengthGoals: row.strength_goals ?? (row.strength_goal ? [row.strength_goal] : []),
      functionalGoals: row.functional_goals ?? (row.includes_functional && row.functional_primary_goal ? [row.functional_primary_goal] : []),
      badmintonGoals: row.badminton_goals ?? (row.includes_badminton && row.badminton_primary_goal ? [row.badminton_primary_goal] : []),
    },
    authorComment: row.author_comment,
    cautionText: row.caution_text,
    representativeExercises:
      row.community_program_exercises
        ?.slice(0, 8)
        .map((item: any) => item.exercise_name) ?? [],
    likeCount: Number(row.like_count ?? 0),
    likedByMe: liked.has(row.public_program_id),
    isMine,
    ...(includeSnapshot ? { snapshot: row.program_snapshot } : {}),
  };
}

async function authorProfiles(
  service: any,
  rows: any[],
): Promise<Map<string, any>> {
  const ids = [
    ...new Set(rows.map((row) => row.owner_user_id).filter(Boolean)),
  ];
  if (!ids.length) return new Map();
  const { data, error } = await service
    .from("community_profiles")
    .select("user_id,nickname,received_program_likes")
    .in("user_id", ids);
  if (error) throw new CommunityError("PROFILE_LOOKUP_FAILED", 500);
  return new Map(
    (data ?? []).map((profile: any) => [profile.user_id, profile]),
  );
}

async function listPrograms(
  service: any,
  userId: string,
  input: Record<string, unknown>,
) {
  const size = pageSize(input.pageSize);
  const cursor = decodeCursor(input.cursor);
  const q = input.q == null ? "" : text(input.q, "query", 80);
  let query = service
    .from("community_programs")
    .select(
      "public_program_id,owner_user_id,published_at,updated_at,program_name,strength_regions,strength_goals,functional_goals,badminton_goals,strength_region,strength_goal,includes_functional,functional_primary_goal,includes_badminton,badminton_primary_goal,author_comment,caution_text,like_count,program_snapshot,community_program_exercises(item_index,exercise_name)",
    )
    .not("published_at", "is", null)
    .limit(size);
  query = applyLabelFilter(query, "strength_regions", searchLabelArray(input.strengthRegions, "strengthRegions", LABELS.strengthRegions));
  query = applyLabelFilter(query, "strength_goals", searchLabelArray(input.strengthGoals, "strengthGoals", LABELS.strengthGoals));
  query = applyLabelFilter(query, "functional_goals", searchLabelArray(input.functionalGoals, "functionalGoals", LABELS.functionalGoals, true), true);
  query = applyLabelFilter(query, "badminton_goals", searchLabelArray(input.badmintonGoals, "badmintonGoals", LABELS.badmintonGoals, true), true);
  const sort = input.sort === "POPULAR" ? "POPULAR" : "LATEST";
  if (sort === "POPULAR") {
    query = query
      .order("like_count", { ascending: false })
      .order("published_at", { ascending: false })
      .order("public_program_id", { ascending: false });
    if (cursor) {
      const likeCount = Number(cursor.likeCount);
      const publishedAt =
        typeof cursor.publishedAt === "string" ? cursor.publishedAt : "";
      const id = typeof cursor.id === "string" ? cursor.id : "";
      if (
        !Number.isSafeInteger(likeCount) ||
        !/^\d{4}-\d\d-\d\dT/.test(publishedAt) ||
        !/^[0-9a-f-]{36}$/i.test(id)
      )
        throw new CommunityError("INVALID_CURSOR");
      query = query.or(
        `like_count.lt.${likeCount},and(like_count.eq.${likeCount},published_at.lt.${publishedAt}),and(like_count.eq.${likeCount},published_at.eq.${publishedAt},public_program_id.lt.${id})`,
      );
    }
  } else {
    query = query
      .order("published_at", { ascending: false })
      .order("public_program_id", { ascending: false });
    if (cursor) {
      const publishedAt =
        typeof cursor.publishedAt === "string" ? cursor.publishedAt : "";
      const id = typeof cursor.id === "string" ? cursor.id : "";
      if (
        !/^\d{4}-\d\d-\d\dT/.test(publishedAt) ||
        !/^[0-9a-f-]{36}$/i.test(id)
      )
        throw new CommunityError("INVALID_CURSOR");
      query = query.or(
        `published_at.lt.${publishedAt},and(published_at.eq.${publishedAt},public_program_id.lt.${id})`,
      );
    }
  }
  if (q) {
    const [byName, byComment, byCaution, byAuthor, byExercise] =
      await Promise.all([
        service
          .from("community_programs")
          .select("public_program_id")
          .not("published_at", "is", null)
          .ilike("program_name", `%${q}%`)
          .limit(100),
        service
          .from("community_programs")
          .select("public_program_id")
          .not("published_at", "is", null)
          .ilike("author_comment", `%${q}%`)
          .limit(100),
        service
          .from("community_programs")
          .select("public_program_id")
          .not("published_at", "is", null)
          .ilike("caution_text", `%${q}%`)
          .limit(100),
        service
          .from("community_profiles")
          .select("user_id")
          .ilike("nickname", `%${q}%`)
          .limit(100),
        service
          .from("community_program_exercises")
          .select("public_program_id")
          .ilike("search_text", `%${q}%`)
          .limit(100),
      ]);
    if (
      byName.error ||
      byComment.error ||
      byCaution.error ||
      byAuthor.error ||
      byExercise.error
    )
      throw new CommunityError("SEARCH_FAILED", 500);
    const authorIds = new Set(
      (byAuthor.data ?? []).map((row: any) => row.user_id),
    );
    const authorPrograms = authorIds.size
      ? await service
          .from("community_programs")
          .select("public_program_id")
          .in("owner_user_id", [...authorIds])
          .not("published_at", "is", null)
          .limit(100)
      : { data: [], error: null };
    if (authorPrograms.error) throw new CommunityError("SEARCH_FAILED", 500);
    const ids = [
      ...new Set(
        [
          ...(byName.data ?? []),
          ...(byComment.data ?? []),
          ...(byCaution.data ?? []),
          ...(authorPrograms.data ?? []),
          ...(byExercise.data ?? []),
        ].map((row: any) => row.public_program_id),
      ),
    ];
    if (!ids.length) return { programs: [], nextCursor: null };
    query = query.in("public_program_id", ids);
  }
  const { data: rows, error } = await query;
  if (error) throw new CommunityError("PROGRAM_FEED_FAILED", 500);
  const programs = rows ?? [];
  const profiles = await authorProfiles(service, programs);
  const ids = programs.map((row: any) => row.public_program_id);
  const { data: likes, error: likeError } = ids.length
    ? await service
        .from("community_program_likes")
        .select("public_program_id")
        .eq("user_id", userId)
        .in("public_program_id", ids)
    : { data: [], error: null };
  if (likeError) throw new CommunityError("LIKE_LOOKUP_FAILED", 500);
  const liked = new Set((likes ?? []).map((row: any) => row.public_program_id));
  const output = programs.map((row: any) =>
    programDto(row, profiles.get(row.owner_user_id), liked, false, userId),
  );
  const last = programs.at(-1);
  const nextCursor =
    programs.length === size && last
      ? btoa(
          JSON.stringify({
            likeCount: Number(last.like_count ?? 0),
            publishedAt: last.published_at,
            id: last.public_program_id,
          }),
        )
      : null;
  return { programs: output, nextCursor, sort };
}

async function friends(service: any, userId: string) {
  const [incoming, outgoing, active] = await Promise.all([
    service
      .from("community_friend_requests")
      .select(
        "request_id,requester_user_id,recipient_user_id,status,created_at",
      )
      .eq("recipient_user_id", userId)
      .eq("status", "PENDING")
      .order("created_at", { ascending: false }),
    service
      .from("community_friend_requests")
      .select(
        "request_id,requester_user_id,recipient_user_id,status,created_at",
      )
      .eq("requester_user_id", userId)
      .eq("status", "PENDING")
      .order("created_at", { ascending: false }),
    service
      .from("community_friendships")
      .select("friendship_id,user_a,user_b,created_at")
      .or(`user_a.eq.${userId},user_b.eq.${userId}`)
      .order("created_at", { ascending: false }),
  ]);
  if (incoming.error || outgoing.error || active.error)
    throw new CommunityError("FRIENDS_FAILED", 500);
  const ids = [
    ...new Set(
      [
        ...(incoming.data ?? []).flatMap((row: any) => [
          row.requester_user_id,
          row.recipient_user_id,
        ]),
        ...(outgoing.data ?? []).flatMap((row: any) => [
          row.requester_user_id,
          row.recipient_user_id,
        ]),
        ...(active.data ?? []).flatMap((row: any) => [row.user_a, row.user_b]),
      ].filter((id) => id !== userId),
    ),
  ];
  const profiles = ids.length
    ? await service
        .from("community_profiles")
        .select(
          "user_id,nickname,friend_code,received_program_likes,share_last_workout_time,share_current_training_status,share_current_exercise_name",
        )
        .in("user_id", ids)
    : { data: [], error: null };
  if (profiles.error) throw new CommunityError("PROFILE_LOOKUP_FAILED", 500);
  const profileById = new Map(
    (profiles.data ?? []).map((row: any) => [row.user_id, row]),
  );
  const safe = (row: any, otherId: string) => ({
    requestId: row.request_id,
    friendshipId: row.friendship_id,
    nickname: profileById.get(otherId)?.nickname ?? "",
    receivedLikeCount: Number(
      profileById.get(otherId)?.received_program_likes ?? 0,
    ),
    friendCode: profileById.get(otherId)?.friend_code ?? "",
    status: row.status,
    createdAt: row.created_at,
  });
  const activity = await friendActivity(
    service,
    userId,
    (active.data ?? []).map((row: any) =>
      row.user_a === userId ? row.user_b : row.user_a,
    ),
    profileById,
  );
  return {
    incoming: (incoming.data ?? []).map((row: any) =>
      safe(row, row.requester_user_id),
    ),
    outgoing: (outgoing.data ?? []).map((row: any) =>
      safe(row, row.recipient_user_id),
    ),
    friends: (active.data ?? []).map((row: any) =>
      safe(row, row.user_a === userId ? row.user_b : row.user_a),
    ),
    activity,
  };
}

async function friendActivity(
  service: any,
  userId: string,
  friendIds: string[],
  profileById?: Map<string, any>,
) {
  if (!friendIds.length) return [];
  const { data, error } = await service
    .from("community_friend_activity")
    .select(
      "user_id,last_workout_at,workout_started_at,current_exercise_name,last_activity_at",
    )
    .in("user_id", friendIds);
  if (error) throw new CommunityError("ACTIVITY_FAILED", 500);
  const profiles =
    profileById ??
    (await service
      .from("community_profiles")
      .select(
        "user_id,nickname,received_program_likes,share_last_workout_time,share_current_training_status,share_current_exercise_name",
      )
      .in("user_id", friendIds)
      .then((result: any) => {
        if (result.error)
          throw new CommunityError("PROFILE_LOOKUP_FAILED", 500);
        return new Map(
          (result.data ?? []).map((row: any) => [row.user_id, row]),
        );
      }));
  const cutoff = Date.now() - ACTIVITY_TTL_MS;
  return (data ?? []).map((row: any) => {
    const profile = profiles.get(row.user_id);
    const fresh =
      row.last_activity_at && Date.parse(row.last_activity_at) >= cutoff;
    return {
      nickname: profile?.nickname ?? "",
      receivedLikeCount: Number(profile?.received_program_likes ?? 0),
      lastWorkoutAt: profile?.share_last_workout_time
        ? row.last_workout_at
        : null,
      isTraining: profile?.share_current_training_status ? !!fresh : null,
      workoutStartedAt: profile?.share_current_training_status
        ? row.workout_started_at
        : null,
      currentExerciseName:
        profile?.share_current_exercise_name && fresh
          ? row.current_exercise_name
          : null,
    };
  });
}

async function handler(request: Request): Promise<Response> {
  if (request.method === "OPTIONS") return emptyResponse();
  if (request.method !== "POST")
    return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405);
  const auth = await authenticateRequest(request);
  if (auth.error) return auth.error;
  try {
    const input = bodyObject(await request.json());
    const operation = text(input.op, "operation", 48);
    const userId = auth.user.id;
    const service = createServiceClient();
    switch (operation) {
      case "profile_get": {
        return jsonResponse({
          profile: profileDto(await ensureProfile(service, userId)),
        });
      }
      case "profile_set": {
        const profile = await ensureProfile(service, userId);
        const updates: Record<string, unknown> = {};
        if (input.nickname !== undefined) {
          const value = nickname(input.nickname);
          updates.nickname = value;
          updates.nickname_normalized = value.toLowerCase();
        }
        if (Object.keys(updates).length) {
          const { data, error } = await service
            .from("community_profiles")
            .update(updates)
            .eq("user_id", userId)
            .select()
            .single();
          if (error)
            throw new CommunityError(
              error.code === "23505"
                ? "NICKNAME_TAKEN"
                : "PROFILE_UPDATE_FAILED",
              error.code === "23505" ? 409 : 500,
            );
          return jsonResponse({ profile: profileDto(data) });
        }
        return jsonResponse({ profile: profileDto(profile) });
      }
      case "privacy_set": {
        await ensureProfile(service, userId);
        const { data, error } = await service
          .from("community_profiles")
          .update({
            share_last_workout_time: bool(
              input.shareLastWorkoutTime,
              "shareLastWorkoutTime",
            ),
            share_current_training_status: bool(
              input.shareCurrentTrainingStatus,
              "shareCurrentTrainingStatus",
            ),
            share_current_exercise_name: bool(
              input.shareCurrentExerciseName,
              "shareCurrentExerciseName",
            ),
          })
          .eq("user_id", userId)
          .select()
          .single();
        if (error) throw new CommunityError("PRIVACY_UPDATE_FAILED", 500);
        return jsonResponse({ profile: profileDto(data) });
      }
      case "program_publish": {
        const profile = await ensureProfile(service, userId);
        if (!profile.nickname)
          throw new CommunityError("NICKNAME_REQUIRED", 409);
        const sourceKey = text(
          input.sourceProgramStableKey,
          "sourceProgramStableKey",
          200,
        );
        const snapshot = normalizeSnapshot(input.snapshot);
        const programName = text(
          input.programName ?? (snapshot.program as any).name,
          "programName",
          160,
        );
        const strengthRegions = labelArray(input.strengthRegions, "strengthRegions", LABELS.strengthRegions, true);
        const strengthGoals = labelArray(input.strengthGoals, "strengthGoals", LABELS.strengthGoals, true);
        const functionalGoals = labelArray(input.functionalGoals ?? [], "functionalGoals", LABELS.functionalGoals, false);
        const badmintonGoals = labelArray(input.badmintonGoals ?? [], "badmintonGoals", LABELS.badmintonGoals, false);
        const authorComment = optionalText(
          input.authorComment,
          "authorComment",
          100,
        );
        const cautionText = optionalText(input.cautionText, "cautionText", 200);
        const sha = await sha256(snapshot);
        const existing = await service
          .from("community_programs")
          .select("public_program_id,published_at")
          .eq("owner_user_id", userId)
          .eq("source_program_stable_key", sourceKey)
          .maybeSingle();
        if (existing.error)
          throw new CommunityError("PROGRAM_LOOKUP_FAILED", 500);
        const payload = {
          owner_user_id: userId,
          source_program_stable_key: sourceKey,
          snapshot_schema_version: 1,
          program_snapshot: snapshot,
          snapshot_sha256: sha,
          source_updated_at: Number(input.sourceUpdatedAt ?? Date.now()),
          program_name: programName,
          strength_regions: strengthRegions,
          strength_goals: strengthGoals,
          functional_goals: functionalGoals,
          badminton_goals: badmintonGoals,
          // Legacy scalar columns remain populated for old clients and rows.
          strength_region: strengthRegions[0],
          strength_goal: strengthGoals[0],
          includes_functional: functionalGoals.length > 0,
          functional_primary_goal: functionalGoals[0] ?? null,
          includes_badminton: badmintonGoals.length > 0,
          badminton_primary_goal: badmintonGoals[0] ?? null,
          author_comment: authorComment,
          caution_text: cautionText,
          published_at: existing.data?.published_at ?? new Date().toISOString(),
        };
        const result = existing.data
          ? await service
              .from("community_programs")
              .update(payload)
              .eq("public_program_id", existing.data.public_program_id)
              .select()
              .single()
          : await service
              .from("community_programs")
              .insert(payload)
              .select()
              .single();
        if (result.error)
          throw new CommunityError("PROGRAM_PUBLISH_FAILED", 500);
        const row = result.data;
        await service
          .from("community_program_exercises")
          .delete()
          .eq("public_program_id", row.public_program_id);
        const exercises = (snapshot.items as any[]).map((item, index) => ({
          public_program_id: row.public_program_id,
          item_index: index,
          exercise_stable_key: text(
            item.exerciseStableKey ?? "unknown",
            "exerciseStableKey",
            200,
          ),
          exercise_name: text(item.exerciseName ?? "", "exerciseName", 160),
          search_text:
            `${item.exerciseName ?? ""} ${item.exerciseStableKey ?? ""}`.toLocaleLowerCase(),
        }));
        if (exercises.length) {
          const inserted = await service
            .from("community_program_exercises")
            .insert(exercises);
          if (inserted.error)
            throw new CommunityError("PROGRAM_PUBLISH_FAILED", 500);
        }
        return jsonResponse({
          program: programDto(row, profile, new Set(), true, userId),
        });
      }
      case "program_unpublish": {
        const id = uuid(input.publicProgramId, "publicProgramId");
        const { error } = await service
          .from("community_programs")
          .update({ published_at: null })
          .eq("public_program_id", id)
          .eq("owner_user_id", userId);
        if (error) throw new CommunityError("PROGRAM_UNPUBLISH_FAILED", 500);
        return jsonResponse({ ok: true });
      }
      case "program_feed": {
        return jsonResponse(await listPrograms(service, userId, input));
      }
      case "program_detail": {
        const id = uuid(input.publicProgramId, "publicProgramId");
        const { data: row, error } = await service
          .from("community_programs")
          .select("*,community_program_exercises(item_index,exercise_name)")
          .eq("public_program_id", id)
          .not("published_at", "is", null)
          .maybeSingle();
        if (error || !row) throw new CommunityError("PROGRAM_NOT_FOUND", 404);
        const profiles = await authorProfiles(service, [row]);
        const like = await service
          .from("community_program_likes")
          .select("public_program_id")
          .eq("user_id", userId)
          .eq("public_program_id", id);
        if (like.error) throw new CommunityError("LIKE_LOOKUP_FAILED", 500);
        return jsonResponse({
          program: programDto(
            row,
            profiles.get(row.owner_user_id),
            new Set(
              (like.data ?? []).map((value: any) => value.public_program_id),
            ),
            true,
            userId,
          ),
        });
      }
      case "program_like": {
        const id = uuid(input.publicProgramId, "publicProgramId");
        const liked = bool(input.liked, "liked");
        const { data, error } = await service.rpc(
          "community_toggle_program_like",
          { p_public_program_id: id, p_user_id: userId, p_liked: liked },
        );
        if (error || !data) throw new CommunityError("LIKE_FAILED", 500);
        return jsonResponse({
          liked: !!data.liked,
          likeCount: Number(data.likeCount ?? 0),
        });
      }
      case "weekly_publish": {
        const profile = await ensureProfile(service, userId);
        if (!profile.nickname)
          throw new CommunityError("NICKNAME_REQUIRED", 409);
        const weekStart = date(input.weekStart, "weekStart");
        const payload = bodyObject(input.payload);
        const allowed = [
          "weekStart",
          "weekEnd",
          "trainingDays",
          "strengthSessionCount",
          "confirmedStrengthSetCount",
          "badmintonSessionCount",
          "badmintonMinutes",
        ];
        for (const key of Object.keys(payload))
          if (!allowed.includes(key))
            throw new CommunityError("PRIVATE_WEEKLY_FIELD");
        for (const key of ["weekStart", "weekEnd"])
          if (payload[key] !== undefined) date(payload[key], key);
        for (const key of [
          "trainingDays",
          "strengthSessionCount",
          "confirmedStrengthSetCount",
          "badmintonSessionCount",
          "badmintonMinutes",
        ]) {
          if (
            payload[key] !== undefined &&
            (!Number.isSafeInteger(payload[key]) || Number(payload[key]) < 0)
          )
            throw new CommunityError("INVALID_WEEKLY_VALUE");
        }
        const result = await service
          .from("community_weekly_summaries")
          .upsert(
            {
              owner_user_id: userId,
              week_start: weekStart,
              summary_schema_version: 1,
              summary_payload: payload,
            },
            { onConflict: "owner_user_id,week_start" },
          )
          .select()
          .single();
        if (result.error)
          throw new CommunityError("WEEKLY_PUBLISH_FAILED", 500);
        return jsonResponse({ ok: true, weekStart });
      }
      case "weekly_unpublish": {
        const weekStart = date(input.weekStart, "weekStart");
        const result = await service
          .from("community_weekly_summaries")
          .delete()
          .eq("owner_user_id", userId)
          .eq("week_start", weekStart);
        if (result.error)
          throw new CommunityError("WEEKLY_UNPUBLISH_FAILED", 500);
        return jsonResponse({ ok: true });
      }
      case "weekly_feed": {
        const result = await service
          .from("community_weekly_summaries")
          .select(
            "summary_id,owner_user_id,week_start,summary_payload,published_at,updated_at",
          )
          .order("published_at", { ascending: false })
          .limit(pageSize(input.pageSize));
        if (result.error) throw new CommunityError("WEEKLY_FEED_FAILED", 500);
        const profiles = await authorProfiles(service, result.data ?? []);
        return jsonResponse({
          summaries: (result.data ?? []).map((row: any) => ({
            summaryId: row.summary_id,
            nickname: profiles.get(row.owner_user_id)?.nickname ?? "",
            authorReceivedLikeCount: Number(
              profiles.get(row.owner_user_id)?.received_program_likes ?? 0,
            ),
            weekStart: row.week_start,
            payload: row.summary_payload,
            publishedAt: row.published_at,
          })),
        });
      }
      case "friend_lookup": {
        const code = text(input.friendCode, "friendCode", 9);
        if (!/^\d{4}-\d{4}$/.test(code))
          throw new CommunityError("INVALID_FRIEND_CODE");
        const rate = await service.rpc("community_consume_friend_code_lookup", {
          p_user_id: userId,
        });
        if (rate.error || rate.data !== true)
          throw new CommunityError("LOOKUP_RATE_LIMITED", 429);
        const result = await service
          .from("community_profiles")
          .select("nickname,friend_code,received_program_likes,user_id")
          .eq("friend_code", code)
          .maybeSingle();
        if (result.error) throw new CommunityError("LOOKUP_FAILED", 500);
        if (!result.data) throw new CommunityError("FRIEND_NOT_FOUND", 404);
        return jsonResponse({
          preview: {
            nickname: result.data.nickname ?? "",
            friendCode: result.data.friend_code,
            receivedLikeCount: Number(result.data.received_program_likes ?? 0),
          },
        });
      }
      case "friend_request_send": {
        const profile = await ensureProfile(service, userId);
        if (!profile.nickname)
          throw new CommunityError("NICKNAME_REQUIRED", 409);
        const code = text(input.friendCode, "friendCode", 9);
        const target = await service
          .from("community_profiles")
          .select("user_id")
          .eq("friend_code", code)
          .maybeSingle();
        if (target.error || !target.data)
          throw new CommunityError("FRIEND_NOT_FOUND", 404);
        const targetId = target.data.user_id;
        if (targetId === userId) throw new CommunityError("FRIEND_SELF", 400);
        const blocked = await service
          .from("community_blocks")
          .select("blocker_user_id")
          .or(
            `and(blocker_user_id.eq.${userId},blocked_user_id.eq.${targetId}),and(blocker_user_id.eq.${targetId},blocked_user_id.eq.${userId})`,
          )
          .limit(1);
        if (blocked.error)
          throw new CommunityError("FRIEND_REQUEST_FAILED", 500);
        if (blocked.data?.length)
          throw new CommunityError("FRIEND_BLOCKED", 403);
        const order =
          userId < targetId
            ? { user_a: userId, user_b: targetId }
            : { user_a: targetId, user_b: userId };
        const existingFriend = await service
          .from("community_friendships")
          .select("friendship_id")
          .match(order)
          .maybeSingle();
        if (existingFriend.data)
          throw new CommunityError("ALREADY_FRIENDS", 409);
        const reverse = await service
          .from("community_friend_requests")
          .select("request_id")
          .eq("requester_user_id", targetId)
          .eq("recipient_user_id", userId)
          .eq("status", "PENDING")
          .maybeSingle();
        if (reverse.error)
          throw new CommunityError("FRIEND_REQUEST_FAILED", 500);
        if (reverse.data) throw new CommunityError("REQUEST_EXISTS", 409);
        const result = await service
          .from("community_friend_requests")
          .insert({ requester_user_id: userId, recipient_user_id: targetId })
          .select("request_id,created_at")
          .single();
        if (result.error)
          throw new CommunityError(
            result.error.code === "23505"
              ? "REQUEST_EXISTS"
              : "FRIEND_REQUEST_FAILED",
            result.error.code === "23505" ? 409 : 500,
          );
        return jsonResponse({
          requestId: result.data.request_id,
          createdAt: result.data.created_at,
        });
      }
      case "friends":
        return jsonResponse(await friends(service, userId));
      case "friend_respond": {
        const requestId = uuid(input.requestId, "requestId");
        const accept = bool(input.accept, "accept");
        const requestRow = await service
          .from("community_friend_requests")
          .select("request_id,requester_user_id,recipient_user_id,status")
          .eq("request_id", requestId)
          .eq("recipient_user_id", userId)
          .maybeSingle();
        if (
          requestRow.error ||
          !requestRow.data ||
          requestRow.data.status !== "PENDING"
        )
          throw new CommunityError("REQUEST_NOT_FOUND", 404);
        const update = await service
          .from("community_friend_requests")
          .update({
            status: accept ? "ACCEPTED" : "DECLINED",
            responded_at: new Date().toISOString(),
          })
          .eq("request_id", requestId);
        if (update.error)
          throw new CommunityError("FRIEND_RESPONSE_FAILED", 500);
        if (accept) {
          const a =
            requestRow.data.requester_user_id < userId
              ? requestRow.data.requester_user_id
              : userId;
          const b =
            requestRow.data.requester_user_id < userId
              ? userId
              : requestRow.data.requester_user_id;
          const inserted = await service
            .from("community_friendships")
            .upsert({ user_a: a, user_b: b }, { onConflict: "user_a,user_b" });
          if (inserted.error)
            throw new CommunityError("FRIEND_RESPONSE_FAILED", 500);
        }
        return jsonResponse({ ok: true, accepted: accept });
      }
      case "friend_remove": {
        const friendshipId = uuid(input.friendshipId, "friendshipId");
        const row = await service
          .from("community_friendships")
          .select("user_a,user_b")
          .eq("friendship_id", friendshipId)
          .maybeSingle();
        if (
          row.error ||
          !row.data ||
          ![row.data.user_a, row.data.user_b].includes(userId)
        )
          throw new CommunityError("FRIENDSHIP_NOT_FOUND", 404);
        const deleted = await service
          .from("community_friendships")
          .delete()
          .eq("friendship_id", friendshipId);
        if (deleted.error)
          throw new CommunityError("FRIEND_REMOVE_FAILED", 500);
        return jsonResponse({ ok: true });
      }
      case "friend_block": {
        const code = text(input.friendCode, "friendCode", 9);
        const target = await service
          .from("community_profiles")
          .select("user_id")
          .eq("friend_code", code)
          .maybeSingle();
        if (target.error || !target.data || target.data.user_id === userId)
          throw new CommunityError("FRIEND_NOT_FOUND", 404);
        const blocked = await service
          .from("community_blocks")
          .upsert(
            { blocker_user_id: userId, blocked_user_id: target.data.user_id },
            { onConflict: "blocker_user_id,blocked_user_id" },
          );
        if (blocked.error) throw new CommunityError("FRIEND_BLOCK_FAILED", 500);
        const a = userId < target.data.user_id ? userId : target.data.user_id;
        const b = userId < target.data.user_id ? target.data.user_id : userId;
        const removed = await service
          .from("community_friendships")
          .delete()
          .match({ user_a: a, user_b: b });
        if (removed.error) throw new CommunityError("FRIEND_BLOCK_FAILED", 500);
        return jsonResponse({ ok: true });
      }
      case "privacy_get": {
        return jsonResponse({
          profile: profileDto(await ensureProfile(service, userId)),
        });
      }
      case "activity_update": {
        const performed = bool(input.performed, "performed");
        if (!performed) return jsonResponse({ ok: true });
        await service.from("community_friend_activity").upsert(
          {
            user_id: userId,
            last_workout_at: new Date().toISOString(),
            workout_started_at: input.workoutStartedAt
              ? new Date(Number(input.workoutStartedAt)).toISOString()
              : new Date().toISOString(),
            current_exercise_stable_key:
              optionalText(
                input.currentExerciseStableKey,
                "exerciseStableKey",
                200,
              ) || null,
            current_exercise_name:
              optionalText(input.currentExerciseName, "exerciseName", 160) ||
              null,
            last_activity_at: new Date().toISOString(),
          },
          { onConflict: "user_id" },
        );
        return jsonResponse({ ok: true });
      }
      case "activity": {
        const activeFriendRows = await service
          .from("community_friendships")
          .select("user_a,user_b")
          .or(`user_a.eq.${userId},user_b.eq.${userId}`);
        if (activeFriendRows.error)
          throw new CommunityError("ACTIVITY_FAILED", 500);
        const ids = (activeFriendRows.data ?? []).map((row: any) =>
          row.user_a === userId ? row.user_b : row.user_a,
        );
        return jsonResponse({
          activity: await friendActivity(service, userId, ids),
        });
      }
      default:
        throw new CommunityError("UNKNOWN_OPERATION", 400);
    }
  } catch (error) {
    if (error instanceof CommunityError)
      return jsonResponse({ error: error.code }, error.status);
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500);
  }
}

Deno.serve(handler);
