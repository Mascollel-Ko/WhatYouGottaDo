-- Community v1 label arrays.  The original scalar columns remain as a legacy
-- compatibility surface; these arrays are authoritative for new API clients.

alter table public.community_programs
  add column if not exists strength_regions text[] not null default '{}'::text[],
  add column if not exists strength_goals text[] not null default '{}'::text[],
  add column if not exists functional_goals text[] not null default '{}'::text[],
  add column if not exists badminton_goals text[] not null default '{}'::text[];

update public.community_programs
set strength_regions = array[strength_region]
where cardinality(strength_regions) = 0;

update public.community_programs
set strength_goals = array[strength_goal]
where cardinality(strength_goals) = 0;

update public.community_programs
set functional_goals = case
  when includes_functional and functional_primary_goal is not null
    then array[functional_primary_goal]
  else '{}'::text[]
end
where cardinality(functional_goals) = 0;

update public.community_programs
set badminton_goals = case
  when includes_badminton and badminton_primary_goal is not null
    then array[badminton_primary_goal]
  else '{}'::text[]
end
where cardinality(badminton_goals) = 0;

create or replace function public.community_label_array_has_no_duplicates(label_values text[])
returns boolean
language sql
immutable
strict
as $$
  select cardinality(label_values) = cardinality(array(select distinct unnest(label_values)));
$$;

create or replace function public.community_sync_program_label_arrays()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if cardinality(new.strength_regions) = 0 then
    new.strength_regions := array[new.strength_region];
  end if;
  if cardinality(new.strength_goals) = 0 then
    new.strength_goals := array[new.strength_goal];
  end if;
  if cardinality(new.functional_goals) = 0 and new.includes_functional and new.functional_primary_goal is not null then
    new.functional_goals := array[new.functional_primary_goal];
  end if;
  if cardinality(new.badminton_goals) = 0 and new.includes_badminton and new.badminton_primary_goal is not null then
    new.badminton_goals := array[new.badminton_primary_goal];
  end if;
  new.strength_region := new.strength_regions[1];
  new.strength_goal := new.strength_goals[1];
  new.includes_functional := cardinality(new.functional_goals) > 0;
  new.functional_primary_goal := new.functional_goals[1];
  new.includes_badminton := cardinality(new.badminton_goals) > 0;
  new.badminton_primary_goal := new.badminton_goals[1];
  return new;
end;
$$;

drop trigger if exists community_program_label_arrays_sync on public.community_programs;
create trigger community_program_label_arrays_sync
before insert or update on public.community_programs
for each row execute function public.community_sync_program_label_arrays();

alter table public.community_programs
  add constraint community_programs_strength_regions_array check (
    cardinality(strength_regions) > 0
    and strength_regions <@ array['UPPER_BODY', 'LOWER_BODY', 'ALL_LIMBS']::text[]
    and public.community_label_array_has_no_duplicates(strength_regions)
  ),
  add constraint community_programs_strength_goals_array check (
    cardinality(strength_goals) > 0
    and strength_goals <@ array['HYPERTROPHY', 'STRENGTH']::text[]
    and public.community_label_array_has_no_duplicates(strength_goals)
  ),
  add constraint community_programs_functional_goals_array check (
    functional_goals <@ array['EXPLOSIVE_ACCELERATION', 'ELASTIC_GROUND_REACTION', 'BODY_COORDINATION']::text[]
    and public.community_label_array_has_no_duplicates(functional_goals)
  ),
  add constraint community_programs_badminton_goals_array check (
    badminton_goals <@ array['SWING_POWER', 'LANDING_DECELERATION_STABILITY', 'FOOTWORK']::text[]
    and public.community_label_array_has_no_duplicates(badminton_goals)
  );

create index if not exists community_programs_strength_regions_gin_idx
  on public.community_programs using gin (strength_regions);
create index if not exists community_programs_strength_goals_gin_idx
  on public.community_programs using gin (strength_goals);
create index if not exists community_programs_functional_goals_gin_idx
  on public.community_programs using gin (functional_goals);
create index if not exists community_programs_badminton_goals_gin_idx
  on public.community_programs using gin (badminton_goals);

revoke all on function public.community_label_array_has_no_duplicates(text[]) from public, anon, authenticated;
revoke all on function public.community_sync_program_label_arrays() from public, anon, authenticated;
grant execute on function public.community_sync_program_label_arrays() to service_role;

comment on column public.community_programs.strength_regions is 'Canonical multi-valued strength regions; strength_region is legacy.';
comment on column public.community_programs.strength_goals is 'Canonical multi-valued strength goals; strength_goal is legacy.';
comment on column public.community_programs.functional_goals is 'Canonical optional functional goals; empty means not included.';
comment on column public.community_programs.badminton_goals is 'Canonical optional badminton goals; empty means not included.';
