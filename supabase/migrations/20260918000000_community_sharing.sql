-- Community v1 is a separate server-side social layer.  It never reads or
-- exposes private Cloud Backup objects, lineage, or Room identities.
create extension if not exists pgcrypto;

create table if not exists public.community_profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  nickname text,
  nickname_normalized text,
  friend_code text not null unique,
  received_program_likes bigint not null default 0,
  share_last_workout_time boolean not null default false,
  share_current_training_status boolean not null default false,
  share_current_exercise_name boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint community_profiles_nickname_shape check (
    nickname is null or (
      char_length(nickname) between 2 and 24
      and nickname !~ '[[:cntrl:]\r\n]'
    )
  ),
  constraint community_profiles_friend_code_shape check (friend_code ~ '^[0-9]{4}-[0-9]{4}$'),
  constraint community_profiles_received_likes_nonnegative check (received_program_likes >= 0)
);

create unique index if not exists community_profiles_nickname_unique
  on public.community_profiles (nickname_normalized)
  where nickname_normalized is not null;

create table if not exists public.community_programs (
  public_program_id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users (id) on delete cascade,
  source_program_stable_key text not null,
  snapshot_schema_version integer not null default 1,
  program_snapshot jsonb not null,
  snapshot_sha256 text not null,
  source_updated_at bigint,
  program_name text not null,
  strength_region text not null,
  strength_goal text not null,
  includes_functional boolean not null default false,
  functional_primary_goal text,
  includes_badminton boolean not null default false,
  badminton_primary_goal text,
  author_comment text not null default '',
  caution_text text not null default '',
  like_count bigint not null default 0,
  published_at timestamptz,
  updated_at timestamptz not null default timezone('utc', now()),
  constraint community_programs_source_key_shape check (char_length(trim(source_program_stable_key)) between 1 and 200),
  constraint community_programs_snapshot_version check (snapshot_schema_version = 1),
  constraint community_programs_sha_shape check (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
  constraint community_programs_name_shape check (char_length(trim(program_name)) between 1 and 160),
  constraint community_programs_region check (strength_region in ('UPPER_BODY', 'LOWER_BODY', 'ALL_LIMBS')),
  constraint community_programs_goal check (strength_goal in ('HYPERTROPHY', 'STRENGTH')),
  constraint community_programs_functional_goal check (
    (includes_functional = false and functional_primary_goal is null)
    or (includes_functional = true and functional_primary_goal in ('EXPLOSIVE_ACCELERATION', 'ELASTIC_GROUND_REACTION', 'BODY_COORDINATION'))
  ),
  constraint community_programs_badminton_goal check (
    (includes_badminton = false and badminton_primary_goal is null)
    or (includes_badminton = true and badminton_primary_goal in ('SWING_POWER', 'LANDING_DECELERATION_STABILITY', 'FOOTWORK'))
  ),
  constraint community_programs_comment_shape check (
    char_length(author_comment) <= 100 and author_comment !~ '[[:cntrl:]\r\n]'
  ),
  constraint community_programs_caution_shape check (
    char_length(caution_text) <= 200 and caution_text !~ '[[:cntrl:]\r\n]'
  ),
  constraint community_programs_like_count_nonnegative check (like_count >= 0),
  constraint community_programs_source_unique unique (owner_user_id, source_program_stable_key)
);

create table if not exists public.community_program_exercises (
  public_program_id uuid not null references public.community_programs (public_program_id) on delete cascade,
  item_index integer not null,
  exercise_stable_key text not null,
  exercise_name text not null,
  search_text text not null default '',
  primary key (public_program_id, item_index)
);

create table if not exists public.community_program_likes (
  public_program_id uuid not null references public.community_programs (public_program_id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (public_program_id, user_id)
);

create table if not exists public.community_weekly_summaries (
  summary_id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users (id) on delete cascade,
  week_start date not null,
  summary_schema_version integer not null default 1,
  summary_payload jsonb not null,
  published_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint community_weekly_schema_version check (summary_schema_version = 1),
  constraint community_weekly_unique_week unique (owner_user_id, week_start)
);

create table if not exists public.community_friend_requests (
  request_id uuid primary key default gen_random_uuid(),
  requester_user_id uuid not null references auth.users (id) on delete cascade,
  recipient_user_id uuid not null references auth.users (id) on delete cascade,
  status text not null default 'PENDING',
  created_at timestamptz not null default timezone('utc', now()),
  responded_at timestamptz,
  constraint community_friend_request_not_self check (requester_user_id <> recipient_user_id),
  constraint community_friend_request_status check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

create unique index if not exists community_friend_requests_pending_unique
  on public.community_friend_requests (requester_user_id, recipient_user_id)
  where status = 'PENDING';
create index if not exists community_friend_requests_recipient_idx
  on public.community_friend_requests (recipient_user_id, status, created_at desc);
create index if not exists community_friend_requests_requester_idx
  on public.community_friend_requests (requester_user_id, status, created_at desc);

create table if not exists public.community_friendships (
  friendship_id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users (id) on delete cascade,
  user_b uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  constraint community_friendship_order check (user_a < user_b),
  constraint community_friendship_unique_pair unique (user_a, user_b)
);

create table if not exists public.community_blocks (
  blocker_user_id uuid not null references auth.users (id) on delete cascade,
  blocked_user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (blocker_user_id, blocked_user_id),
  constraint community_block_not_self check (blocker_user_id <> blocked_user_id)
);

create table if not exists public.community_friend_activity (
  user_id uuid primary key references auth.users (id) on delete cascade,
  last_workout_at timestamptz,
  workout_started_at timestamptz,
  current_exercise_stable_key text,
  current_exercise_name text,
  last_activity_at timestamptz,
  updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.community_friend_code_lookup_attempts (
  user_id uuid primary key references auth.users (id) on delete cascade,
  window_started_at timestamptz not null default timezone('utc', now()),
  attempt_count integer not null default 0,
  constraint community_lookup_attempts_nonnegative check (attempt_count >= 0)
);

create index if not exists community_programs_feed_idx
  on public.community_programs (published_at desc, public_program_id);
create index if not exists community_programs_popular_idx
  on public.community_programs (like_count desc, published_at desc, public_program_id);
create index if not exists community_programs_labels_idx
  on public.community_programs (strength_region, strength_goal, includes_functional, includes_badminton);
create index if not exists community_program_exercises_search_idx
  on public.community_program_exercises using gin (to_tsvector('simple', search_text));
create index if not exists community_weekly_feed_idx
  on public.community_weekly_summaries (published_at desc, summary_id);
create index if not exists community_activity_updated_idx
  on public.community_friend_activity (last_activity_at desc);

create or replace function public.community_touch_updated_at()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  new.updated_at = timezone('utc', now());
  return new;
end;
$$;

drop trigger if exists community_profiles_updated_at on public.community_profiles;
create trigger community_profiles_updated_at before update on public.community_profiles
for each row execute function public.community_touch_updated_at();
drop trigger if exists community_programs_updated_at on public.community_programs;
create trigger community_programs_updated_at before update on public.community_programs
for each row execute function public.community_touch_updated_at();
drop trigger if exists community_weekly_updated_at on public.community_weekly_summaries;
create trigger community_weekly_updated_at before update on public.community_weekly_summaries
for each row execute function public.community_touch_updated_at();
drop trigger if exists community_activity_updated_at on public.community_friend_activity;
create trigger community_activity_updated_at before update on public.community_friend_activity
for each row execute function public.community_touch_updated_at();

create or replace function public.community_recalculate_like_totals()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  affected_program uuid;
  affected_owner uuid;
begin
  affected_program := coalesce(new.public_program_id, old.public_program_id);
  affected_owner := coalesce(new.user_id, old.user_id);
  update public.community_programs p
     set like_count = (select count(*) from public.community_program_likes l where l.public_program_id = p.public_program_id),
         updated_at = timezone('utc', now())
   where p.public_program_id = affected_program;
  update public.community_profiles cp
     set received_program_likes = coalesce((
       select sum(p.like_count) from public.community_programs p
       where p.owner_user_id = cp.user_id and p.published_at is not null
     ), 0),
         updated_at = timezone('utc', now())
   where cp.user_id in (
     select p.owner_user_id from public.community_programs p where p.public_program_id = affected_program
     union
     select affected_owner
   );
  return coalesce(new, old);
end;
$$;

drop trigger if exists community_program_likes_totals on public.community_program_likes;
create trigger community_program_likes_totals after insert or delete on public.community_program_likes
for each row execute function public.community_recalculate_like_totals();

create or replace function public.community_recalculate_program_author_total()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_id uuid;
begin
  owner_id := coalesce(new.owner_user_id, old.owner_user_id);
  update public.community_profiles cp
     set received_program_likes = coalesce((
       select sum(p.like_count) from public.community_programs p
       where p.owner_user_id = cp.user_id and p.published_at is not null
     ), 0),
         updated_at = timezone('utc', now())
   where cp.user_id = owner_id;
  return coalesce(new, old);
end;
$$;

drop trigger if exists community_program_author_totals on public.community_programs;
create trigger community_program_author_totals after insert or update or delete on public.community_programs
for each row execute function public.community_recalculate_program_author_total();

create or replace function public.community_ensure_profile(p_user_id uuid)
returns public.community_profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  candidate text;
  result public.community_profiles;
begin
  if p_user_id is null then raise exception 'USER_REQUIRED'; end if;
  loop
    candidate := lpad(floor(random() * 100000000)::bigint::text, 8, '0');
    candidate := substr(candidate, 1, 4) || '-' || substr(candidate, 5, 4);
    begin
      insert into public.community_profiles(user_id, friend_code)
      values (p_user_id, candidate)
      on conflict (user_id) do nothing;
      exit;
    exception when unique_violation then
      -- Retry a colliding random friend code.
    end;
  end loop;
  select * into result from public.community_profiles where user_id = p_user_id;
  return result;
end;
$$;

create or replace function public.community_consume_friend_code_lookup(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  current_window timestamptz := timezone('utc', now());
  result public.community_friend_code_lookup_attempts;
begin
  insert into public.community_friend_code_lookup_attempts(user_id, window_started_at, attempt_count)
  values (p_user_id, current_window, 1)
  on conflict (user_id) do update
    set window_started_at = case
      when public.community_friend_code_lookup_attempts.window_started_at <= current_window - interval '15 minutes'
      then excluded.window_started_at else public.community_friend_code_lookup_attempts.window_started_at end,
        attempt_count = case
      when public.community_friend_code_lookup_attempts.window_started_at <= current_window - interval '15 minutes'
      then 1 else public.community_friend_code_lookup_attempts.attempt_count + 1 end
  returning * into result;
  return result.attempt_count <= 30;
end;
$$;

create or replace function public.community_toggle_program_like(
  p_public_program_id uuid,
  p_user_id uuid,
  p_liked boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  current_like boolean;
  count_value bigint;
begin
  if not exists (select 1 from public.community_programs where public_program_id = p_public_program_id and published_at is not null) then
    raise exception 'PROGRAM_NOT_FOUND';
  end if;
  if p_liked then
    insert into public.community_program_likes(public_program_id, user_id)
    values (p_public_program_id, p_user_id)
    on conflict do nothing;
  else
    delete from public.community_program_likes
    where public_program_id = p_public_program_id and user_id = p_user_id;
  end if;
  select exists(select 1 from public.community_program_likes where public_program_id = p_public_program_id and user_id = p_user_id),
         like_count into current_like, count_value
  from public.community_programs where public_program_id = p_public_program_id;
  return jsonb_build_object('liked', current_like, 'likeCount', count_value);
end;
$$;

alter table public.community_profiles enable row level security;
alter table public.community_programs enable row level security;
alter table public.community_program_exercises enable row level security;
alter table public.community_program_likes enable row level security;
alter table public.community_weekly_summaries enable row level security;
alter table public.community_friend_requests enable row level security;
alter table public.community_friendships enable row level security;
alter table public.community_blocks enable row level security;
alter table public.community_friend_activity enable row level security;
alter table public.community_friend_code_lookup_attempts enable row level security;

revoke all on table public.community_profiles from anon, authenticated;
revoke all on table public.community_programs from anon, authenticated;
revoke all on table public.community_program_exercises from anon, authenticated;
revoke all on table public.community_program_likes from anon, authenticated;
revoke all on table public.community_weekly_summaries from anon, authenticated;
revoke all on table public.community_friend_requests from anon, authenticated;
revoke all on table public.community_friendships from anon, authenticated;
revoke all on table public.community_blocks from anon, authenticated;
revoke all on table public.community_friend_activity from anon, authenticated;
revoke all on table public.community_friend_code_lookup_attempts from anon, authenticated;
grant all on table public.community_profiles to service_role;
grant all on table public.community_programs to service_role;
grant all on table public.community_program_exercises to service_role;
grant all on table public.community_program_likes to service_role;
grant all on table public.community_weekly_summaries to service_role;
grant all on table public.community_friend_requests to service_role;
grant all on table public.community_friendships to service_role;
grant all on table public.community_blocks to service_role;
grant all on table public.community_friend_activity to service_role;
grant all on table public.community_friend_code_lookup_attempts to service_role;
grant execute on function public.community_ensure_profile(uuid) to service_role;
grant execute on function public.community_consume_friend_code_lookup(uuid) to service_role;
grant execute on function public.community_toggle_program_like(uuid, uuid, boolean) to service_role;

comment on table public.community_profiles is 'Community identity and server-enforced privacy; never a public profiles view.';
comment on table public.community_programs is 'Versioned public program snapshots; separate from private Room and Cloud Backup.';
comment on table public.community_friend_activity is 'Small social projection only; never the workout record source of truth.';
