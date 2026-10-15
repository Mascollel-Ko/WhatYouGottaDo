#!/usr/bin/env python3
"""Run the V2-2 Python v1.0 real-history validation matrix.

The source backup is read-only. The committed fixture contains only the columns
consumed by V2-2 and only confirmed strength/functional sets.
"""

from __future__ import annotations

import argparse
import collections
import csv
import datetime as dt
import gzip
import hashlib
import io
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REFERENCE = HERE / "reference"
sys.path.insert(0, str(REFERENCE))

from v2_2_generation_v05 import (  # noqa: E402
    CATEGORIES,
    build_profiles,
    generate,
    infer_training_days,
    load,
    numeric,
)
from v2_2_v10_python_engine import build, validate  # noqa: E402

WEEKS = (2, 4, 8, 12)
DAY_VARIANTS = ("auto", "3", "4", "5")
MODES = ("A", "B", "C")
FIXTURE_FIELDS = (
    "row_type",
    "date",
    "exercise_name",
    "category",
    "confirmed",
    "set_index",
    "set_confirmed",
    "reps",
    "weight_kg",
    "seconds",
    "stable_key",
    "rpe",
)


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def confirmed_rows(path: pathlib.Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with path.open(encoding="utf-8-sig", newline="") as stream:
        next(stream)
        for row in csv.DictReader(stream):
            if (
                row["row_type"] == "set"
                and row["confirmed"] == "1"
                and row["set_confirmed"] == "1"
                and row["category"] in CATEGORIES
            ):
                try:
                    dt.date.fromisoformat(row["date"])
                except (TypeError, ValueError):
                    continue
                rows.append(row)
    return rows


def write_fixture(rows: list[dict[str, str]], target: pathlib.Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with io.TextIOWrapper(compressed, encoding="utf-8", newline="") as stream:
                stream.write("#WGTD_BACKUP_MANIFEST,V22_VALIDATION_FILTERED_CONFIRMED_SETS\n")
                writer = csv.DictWriter(stream, fieldnames=FIXTURE_FIELDS, lineterminator="\n")
                writer.writeheader()
                for row in rows:
                    writer.writerow({field: row.get(field, "") for field in FIXTURE_FIELDS})


def write_plain_fixture(rows: list[dict[str, str]], target: pathlib.Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="") as stream:
        stream.write("#WGTD_BACKUP_MANIFEST,V22_VALIDATION_FILTERED_CONFIRMED_SETS\n")
        writer = csv.DictWriter(stream, fieldnames=FIXTURE_FIELDS, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in FIXTURE_FIELDS})


def inference_json(value: dict) -> dict:
    return {
        "window": value["window"],
        "categories": value["categories"],
        "weeks": value["weeks"],
        "excludedWeekCount": value["excludedWeekCount"],
        "rounding": value["rounding"],
        "retainedMean": value["retainedMean"],
    }


def observed_set(value: dict) -> dict:
    return {
        "weightKg": value.get("weightKg"),
        "reps": value.get("reps"),
        "seconds": value.get("seconds"),
        "observedRpe": value.get("observedRpe"),
        "sourceDate": value["sourceDate"],
        "loadStatus": value["loadStatus"],
    }


def compact_plan(plan: dict) -> dict:
    blocks = []
    for block in plan["blocks"]:
        representative = plan["weeks"][block["startWeek"] - 1]
        blocks.append(
            {
                "block": block["block"],
                "startWeek": block["startWeek"],
                "endWeek": block["endWeek"],
                "phase": block["phase"],
                "plannedReallocations": block["plannedReallocations"],
                "plannedRoleSets": block["plannedRoleSets"],
                "sessions": [
                    {
                        "session": session["session"],
                        "nominalSets": session["nominalSets"],
                        "items": [
                            {
                                "exercise": item["exercise"],
                                "stableKey": item["stableKey"],
                                "category": item["category"],
                                "role": item["role"],
                                "setCount": item["setCount"],
                                "sets": [observed_set(value) for value in item["sets"]],
                                "historicalSets28d": item["historicalSets28d"],
                                "historicalSets56d": item["historicalSets56d"],
                            }
                            for item in session["items"]
                        ],
                    }
                    for session in representative["sessions"]
                ],
            }
        )
    return {
        "historyEvidence": plan["historyEvidence"],
        "baselineWeeklyNominalSets": plan["baselineWeeklyNominalSets"],
        "authority": plan["authority"],
        "limitations": plan["limitations"],
        "blocks": blocks,
    }


def candidate_focus(profiles: list[dict], baseline: dict) -> str | None:
    if baseline.get("status") != "EXPLORATORY":
        return None
    retained = {
        item["stableKey"]
        for session in baseline["sessions"]
        for item in session["items"]
    }
    squats = [
        profile
        for profile in profiles
        if profile["key"] in retained
        and profile["role"] == "SQUAT_PATTERN"
        and "스쿼트" in profile["name"]
        and "스플릿" not in profile["name"]
    ]
    return max(squats, key=lambda profile: profile["priority"])["key"] if squats else None


def case_row(case: dict) -> dict[str, object]:
    semantic = case.get("semantic") or {}
    blocks = semantic.get("blocks") or []
    first = blocks[0] if blocks else {}
    sessions = first.get("sessions") or []
    keys = [item["stableKey"] for session in sessions for item in session["items"]]
    set_counts = {
        item["stableKey"]: item["setCount"]
        for session in sessions
        for item in session["items"]
    }
    reallocations = [
        move
        for block in blocks
        for move in block.get("plannedReallocations", [])
    ]
    roles = first.get("plannedRoleSets") or {}
    return {
        "case_id": case["caseId"],
        "timeline": case["timeline"],
        "as_of": case["asOf"],
        "weeks": case["weeks"],
        "days_variant": case["daysVariant"],
        "days": case["days"],
        "inferred_days": case["inferredDays"],
        "mode": case["mode"],
        "focus_stable_key": case.get("focusStableKey") or "",
        "status": case["status"],
        "error": case.get("error") or "",
        "weekly_nominal_sets": semantic.get("baselineWeeklyNominalSets", ""),
        "exercise_count": len(set(keys)),
        "exercise_keys": "|".join(keys),
        "exercise_sets": json.dumps(set_counts, ensure_ascii=False, sort_keys=True),
        "session_layout": json.dumps(
            [[item["stableKey"] for item in session["items"]] for session in sessions],
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        "role_sets": json.dumps(roles, ensure_ascii=False, sort_keys=True),
        "reallocation_count": len(reallocations),
        "reallocations": json.dumps(reallocations, ensure_ascii=False, sort_keys=True),
        "has_direct_arms": any(role.startswith("ARMS_") for role in roles),
        "has_direct_core": any(role.startswith("CORE_") for role in roles),
        "duration_cap_verified": False,
        "execution_miss_carryover": semantic.get("authority", {}).get("executionMissCarryover", ""),
    }


def exercise_summary(rows: list[dict[str, str]], last_date: dt.date) -> list[dict[str, object]]:
    groups: dict[str, list[dict[str, str]]] = collections.defaultdict(list)
    for row in rows:
        groups[row["stable_key"] or row["exercise_name"]].append(row)
    result = []
    for key, values in groups.items():
        values.sort(key=lambda row: (row["date"], int(row["set_index"] or 0)))
        dates = [dt.date.fromisoformat(row["date"]) for row in values]
        result.append(
            {
                "stable_key": key,
                "exercise_name": values[-1]["exercise_name"],
                "category": values[-1]["category"],
                "first_date": min(dates).isoformat(),
                "last_date": max(dates).isoformat(),
                "training_days": len(set(dates)),
                "sets_total": len(values),
                "sets_7d": sum(last_date - dt.timedelta(days=6) <= day <= last_date for day in dates),
                "sets_28d": sum(last_date - dt.timedelta(days=27) <= day <= last_date for day in dates),
                "sets_56d": sum(last_date - dt.timedelta(days=55) <= day <= last_date for day in dates),
                "weight_positive": sum((numeric(row["weight_kg"]) or 0) > 0 for row in values),
                "reps_positive": sum((numeric(row["reps"]) or 0) > 0 for row in values),
                "rpe_present": sum(numeric(row["rpe"]) is not None for row in values),
                "seconds_positive": sum((numeric(row["seconds"]) or 0) > 0 for row in values),
            }
        )
    return sorted(result, key=lambda row: (-int(row["sets_total"]), str(row["stable_key"])))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup", type=pathlib.Path, required=True)
    parser.add_argument("--repo", type=pathlib.Path, default=HERE.parents[1])
    args = parser.parse_args()
    backup = args.backup.resolve()
    repo = args.repo.resolve()
    source_rows = confirmed_rows(backup)
    if not source_rows:
        raise SystemExit("No confirmed strength/functional sets found.")
    source_dates = [dt.date.fromisoformat(row["date"]) for row in source_rows]
    first_date, last_date = min(source_dates), max(source_dates)

    fixture_gz = repo / "app/src/test/resources/v22-real-history/whatyougottado_backup_2026-10-12-v22.csv.gz"
    write_fixture(source_rows, fixture_gz)
    work_dir = repo / "build/v22-real-history-validation"
    work_dir.mkdir(parents=True, exist_ok=True)
    fixture_csv = work_dir / "whatyougottado_backup_2026-10-12-v22.csv"
    write_plain_fixture(source_rows, fixture_csv)

    cases: list[dict] = []
    timelines: list[dict[str, object]] = []
    for timeline_index in range(11):
        as_of = last_date - dt.timedelta(days=3 * timeline_index)
        exercises, dates = load(fixture_csv, as_of)
        profiles = build_profiles(exercises, as_of)
        inferred, inference = infer_training_days(dates, as_of)
        auto = generate(fixture_csv, as_of, training_days=None)
        focus = candidate_focus(profiles, auto["programs"]["A"])
        visible = [row for row in source_rows if dt.date.fromisoformat(row["date"]) <= as_of]
        cut7, cut28, cut56 = (as_of - dt.timedelta(days=n) for n in (6, 27, 55))
        timelines.append(
            {
                "timeline": f"T{timeline_index}",
                "as_of": as_of.isoformat(),
                "visible_sets_all_history": len(visible),
                "visible_training_days_all_history": len({row["date"] for row in visible}),
                "sets_7d": sum(cut7 <= dt.date.fromisoformat(row["date"]) <= as_of for row in source_rows),
                "sets_28d": sum(cut28 <= dt.date.fromisoformat(row["date"]) <= as_of for row in source_rows),
                "sets_56d": sum(cut56 <= dt.date.fromisoformat(row["date"]) <= as_of for row in source_rows),
                "strength_sets_56d": sum(cut56 <= dt.date.fromisoformat(row["date"]) <= as_of and row["category"] == "근력운동" for row in source_rows),
                "functional_sets_56d": sum(cut56 <= dt.date.fromisoformat(row["date"]) <= as_of and row["category"] == "기능성운동" for row in source_rows),
                "inferred_days": inferred,
                "retained_mean": inference["retainedMean"],
                "rolling_week_days": "|".join(str(week["trainingDays"]) for week in inference["weeks"]),
                "focus_stable_key": focus or "",
                "profile_count_56d": len(profiles),
                "baseline_status_auto": auto["programs"]["A"]["status"],
                "baseline_sets_auto": auto["programs"]["A"].get("weeklyNominalSets", ""),
                "baseline_exercises_auto": auto["programs"]["A"].get("exerciseCount", ""),
            }
        )
        for weeks in WEEKS:
            for day_variant in DAY_VARIANTS:
                days_value = inferred if day_variant == "auto" else int(day_variant)
                for mode in MODES:
                    case_id = f"T{timeline_index}-{weeks}w-{day_variant}d-{mode}"
                    record = {
                        "caseId": case_id,
                        "timeline": f"T{timeline_index}",
                        "asOf": as_of.isoformat(),
                        "weeks": weeks,
                        "daysVariant": day_variant,
                        "days": days_value,
                        "inferredDays": inferred,
                        "trainingDayInference": inference_json(inference),
                        "mode": mode,
                        "focusStableKey": focus if mode == "C" else None,
                    }
                    if mode == "C" and focus is None:
                        record.update(status="INPUT_LIMITATION", error="NO_ELIGIBLE_FOCUS_STABLE_KEY")
                    else:
                        try:
                            plan = build(
                                fixture_csv,
                                weeks=weeks,
                                days=days_value,
                                mode=mode,
                                asof=as_of,
                                focus=focus if mode == "C" else None,
                            )
                            validate(plan)
                            record.update(status="SUCCESS", error=None, semantic=compact_plan(plan))
                        except Exception as error:  # preserve the reference behavior in the artifact
                            record.update(status="GENERATION_FAILED", error=str(error), semantic=None)
                    cases.append(record)

    expected = {
        "schema": "V22_REAL_HISTORY_MATRIX_V1",
        "sourceBackupName": backup.name,
        "sourceBackupSha256": sha256(backup),
        "filteredFixtureSha256": sha256(fixture_gz),
        "firstWorkoutDate": first_date.isoformat(),
        "lastWorkoutDate": last_date.isoformat(),
        "caseCount": len(cases),
        "cases": cases,
    }
    expected_path = repo / "app/src/test/resources/v22-real-history/python_expected_528.json.gz"
    expected_path.parent.mkdir(parents=True, exist_ok=True)
    with expected_path.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with io.TextIOWrapper(compressed, encoding="utf-8") as stream:
                json.dump(expected, stream, ensure_ascii=False, separators=(",", ":"), sort_keys=True)

    output = repo / "docs/validation/v22-real-history-2026-10-12"
    output.mkdir(parents=True, exist_ok=True)
    with (output / "matrix_results.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        rows = [case_row(case) for case in cases]
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    with (output / "timeline_summary.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(timelines[0]))
        writer.writeheader()
        writer.writerows(timelines)
    case_index = {case["caseId"]: case for case in cases}
    timeline_index = {row["timeline"]: row for row in timelines}
    changes = []
    for older_index in range(10, 0, -1):
        older_name, newer_name = f"T{older_index}", f"T{older_index - 1}"
        older_timeline, newer_timeline = timeline_index[older_name], timeline_index[newer_name]
        older_date = dt.date.fromisoformat(str(older_timeline["as_of"]))
        newer_date = dt.date.fromisoformat(str(newer_timeline["as_of"]))
        added = [row for row in source_rows if older_date < dt.date.fromisoformat(row["date"]) <= newer_date]
        added_counts = collections.Counter((row["date"], row["stable_key"] or row["exercise_name"]) for row in added)
        older_a = case_row(case_index[f"{older_name}-8w-autod-A"])
        newer_a = case_row(case_index[f"{newer_name}-8w-autod-A"])
        older_keys, newer_keys = set(str(older_a["exercise_keys"]).split("|")), set(str(newer_a["exercise_keys"]).split("|"))
        older_b = case_row(case_index[f"{older_name}-8w-autod-B"])
        newer_b = case_row(case_index[f"{newer_name}-8w-autod-B"])
        changes.append({
            "transition": f"{older_name}->{newer_name}",
            "older_as_of": older_date.isoformat(),
            "newer_as_of": newer_date.isoformat(),
            "added_confirmed_sets": len(added),
            "added_records": json.dumps([
                {"date": date, "stableKey": key, "sets": count}
                for (date, key), count in sorted(added_counts.items())
            ], ensure_ascii=False, separators=(",", ":")),
            "sets_56d_delta": int(newer_timeline["sets_56d"]) - int(older_timeline["sets_56d"]),
            "inferred_days_before": older_timeline["inferred_days"],
            "inferred_days_after": newer_timeline["inferred_days"],
            "weekly_sets_before": older_a["weekly_nominal_sets"],
            "weekly_sets_after": newer_a["weekly_nominal_sets"],
            "weekly_sets_delta": int(newer_a["weekly_nominal_sets"]) - int(older_a["weekly_nominal_sets"]),
            "exercise_jaccard": round(len(older_keys & newer_keys) / len(older_keys | newer_keys), 4),
            "selected_added": "|".join(sorted(newer_keys - older_keys)),
            "selected_removed": "|".join(sorted(older_keys - newer_keys)),
            "session_layout_changed": older_a["session_layout"] != newer_a["session_layout"],
            "b_reallocation_before": older_b["reallocations"],
            "b_reallocation_after": newer_b["reallocations"],
            "b_reallocation_changed": older_b["reallocations"] != newer_b["reallocations"],
        })
    with (output / "timeline_changes.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(changes[0]))
        writer.writeheader()
        writer.writerows(changes)
    summaries = exercise_summary(source_rows, last_date)
    with (output / "exercise_history_summary.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    representatives = {
        "T0_2w_3d_A": case_index["T0-2w-3d-A"],
        "T0_8w_auto_B": case_index["T0-8w-autod-B"],
        "T0_12w_5d_C": case_index["T0-12w-5d-C"],
    }
    (output / "representative_programs.json").write_text(
        json.dumps(representatives, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    manifest = {
        "sourceBackup": backup.name,
        "sourceBackupSha256": sha256(backup),
        "sourceBackupBytes": backup.stat().st_size,
        "sourceBackupCopied": False,
        "filteredFixture": str(fixture_gz.relative_to(repo)).replace("\\", "/"),
        "filteredFixtureSha256": sha256(fixture_gz),
        "confirmedV22SetRows": len(source_rows),
        "firstWorkoutDate": first_date.isoformat(),
        "lastWorkoutDate": last_date.isoformat(),
        "caseCount": len(cases),
        "successCount": sum(case["status"] == "SUCCESS" for case in cases),
        "failureCount": sum(case["status"] != "SUCCESS" for case in cases),
        "referencePython": "3.11.3",
    }
    (output / "validation_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
