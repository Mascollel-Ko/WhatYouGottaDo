#!/usr/bin/env python3
"""Apply the approved exercise-canonicalization workbook to the seed catalog."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

from openpyxl import load_workbook


EQUIPMENT = {
    "바벨": "BARBELL",
    "덤벨": "DUMBBELL",
    "케틀벨": "KETTLEBELL",
    "콘/마커": "CONE_MARKER",
    "맨몸": "BODYWEIGHT",
    "벤치": "BENCH",
    "머신": "MACHINE",
    "바이퍼": "VIPR",
    "메디신볼": "MEDICINE_BALL",
    "랜드마인": "LANDMINE",
    "케이블": "CABLE",
    "레그익스텐션 머신": "LEG_EXTENSION_MACHINE",
    "레그컬 머신": "LEG_CURL_MACHINE",
    "박스/스텝박스": "BOX",
}

EXTRA_COLUMNS = [
    "training_role",
    "sport_transfer_direct",
    "sport_transfer_supportive",
    "load_profile",
    "metadata_confidence",
    "tissue_profile_action",
    "metadata_basis",
]

SPLIT_TARGETS = {
    "ex_8380d7fe": "half_kneeling_single_arm_dumbbell_press",
    "ex_8e1b313e": "half_kneeling_single_arm_dumbbell_press",
    "ex_66e8c8c2": "half_kneeling_single_arm_dumbbell_press",
    "ex_d79824d2": "half_kneeling_single_arm_kettlebell_press",
    "ex_e0759156": "half_kneeling_single_arm_kettlebell_press",
    "ex_d2bb7946": "barbell_romanian_deadlift",
    "ex_9523db82": "dumbbell_romanian_deadlift",
}

BASE_KEYS = {
    "half_kneeling_single_arm_dumbbell_press": "ex_8380d7fe",
    "half_kneeling_single_arm_kettlebell_press": "ex_d79824d2",
    "barbell_romanian_deadlift": "ex_d2bb7946",
    "dumbbell_romanian_deadlift": "ex_9523db82",
    "lateral_bound_continuous": "ex_34e7d21",
    "single_leg_hip_bridge": "ex_5715d6ca",
}

RUNTIME_SUBTYPES = {
    "half_kneeling_single_arm_dumbbell_press": "HALF_KNEELING_SINGLE_ARM_DUMBBELL_PRESS",
    "half_kneeling_single_arm_kettlebell_press": "HALF_KNEELING_SINGLE_ARM_KETTLEBELL_PRESS",
    "barbell_romanian_deadlift": "BARBELL_ROMANIAN_DEADLIFT",
    "dumbbell_romanian_deadlift": "DUMBBELL_ROMANIAN_DEADLIFT",
    "lateral_bound_continuous": "LATERAL_BOUND_CONTINUOUS",
    "ex_34e7d21": "LATERAL_BOUND_TO_STICK",
    "ex_eb636bac": "OVERHEAD_CABLE_SINGLE_ARM_TRICEPS_EXTENSION",
    "ex_5322f2d1": "CABLE_SINGLE_ARM_TRICEPS_PUSHDOWN",
}


def rows_from_sheet(workbook, name: str) -> list[dict[str, str]]:
    values = list(workbook[name].values)
    header = [str(value or "").strip() for value in values[0]]
    return [
        {
            header[index]: "" if value is None else str(value).strip()
            for index, value in enumerate(row)
            if index < len(header) and header[index]
        }
        for row in values[1:]
        if any(value is not None and str(value).strip() for value in row)
    ]


def tokens(value: str) -> list[str]:
    return [token.strip() for token in value.split("|") if token and token.strip()]


def target_map(decisions: list[dict[str, str]]) -> tuple[dict[str, str | None], set[str]]:
    mapping: dict[str, str | None] = {}
    source_keys: set[str] = set()
    for decision in decisions:
        sources = tokens(decision["원본 stableKey"])
        targets = tokens(decision["최종 stableKey"])
        source_keys.update(sources)
        for source in sources:
            if source in SPLIT_TARGETS:
                mapping[source] = SPLIT_TARGETS[source]
            elif len(targets) == 1:
                mapping[source] = targets[0]
            elif not targets:
                mapping[source] = None
            else:
                raise ValueError(f"Unresolved split mapping: {decision['ID']} / {source}")
    return mapping, source_keys


def equipment_tokens(value: str) -> str:
    mapped = []
    for token in tokens(value):
        if token not in EQUIPMENT:
            raise ValueError(f"Unsupported workbook equipment: {token}")
        if EQUIPMENT[token] not in mapped:
            mapped.append(EQUIPMENT[token])
    return "|".join(mapped)


def canonical_exercise(
    metadata: dict[str, str],
    old_by_key: dict[str, dict[str, str]],
    source_keys: set[str],
) -> tuple[int, dict[str, str]]:
    stable_key = metadata["stableKey"]
    base_key = stable_key if stable_key in old_by_key else BASE_KEYS.get(stable_key, stable_key)
    base = old_by_key.get(base_key)
    if base is None:
        candidates = [
            old_by_key[key]
            for key in source_keys
            if key in old_by_key and old_by_key[key]["exercise_name"] in tokens(metadata["sourceNames"])
        ]
        if not candidates:
            raise ValueError(f"No seed base row for {stable_key}")
        base = candidates[0]

    row = dict(base)
    row.update(
        {
            "schema_version": "4",
            "exercise_name": metadata["canonicalName"],
            "description": metadata["description"],
            "default_rest_seconds": metadata["defaultRestSeconds"],
            "stable_key": stable_key,
            "source": "BUILT_IN",
            "movement_pattern": metadata["movementPattern"],
            "movement_category": metadata["movementCategory"],
            "primary_muscles": metadata["primaryMuscles"],
            "secondary_muscles": metadata["secondaryMuscles"],
            "equipment_tags": equipment_tokens(metadata["equipment"]),
            "force_type": metadata["forceType"],
            "body_region": metadata["bodyRegion"],
            "laterality": metadata["laterality"],
            "plane": metadata["plane"],
            "is_unilateral": "1" if metadata["laterality"].startswith("UNILATERAL") else "0",
            "training_role": metadata["trainingRole"],
            "sport_transfer_direct": metadata["sportTransferDirect"],
            "sport_transfer_supportive": metadata["sportTransferSupportive"],
            "load_profile": metadata["loadProfile"],
            "metadata_confidence": metadata["metadataConfidence"],
            "tissue_profile_action": metadata["tissueProfileAction"],
            "metadata_basis": metadata["metadataBasis"],
        }
    )
    source_positions = [
        int(old_by_key[key]["__position"])
        for key in source_keys
        if key in old_by_key and (key == base_key or old_by_key[key]["exercise_name"] in tokens(metadata["sourceNames"]))
    ]
    return (min(source_positions) if source_positions else int(base["__position"]), row)


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        return list(reader.fieldnames or []), list(reader)


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def text_sha256(path: Path) -> str:
    normalized = path.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def update_runtime_manifest(path: Path, asset: Path, workbook: Path) -> None:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    _, rows = read_csv(asset)
    manifest.update(
        {
            "rowCount": len(rows),
            "sourceArtifact": workbook.name,
            "sourceSha256": sha256(workbook),
            "assetSha256": text_sha256(asset),
            "transferCounts": dict(sorted(
                (value, sum(row["badmintonTransferLevel"] == value for row in rows))
                for value in {row["badmintonTransferLevel"] for row in rows}
            )),
            "stressMagnitudeCounts": dict(sorted(
                (value, sum(row["stressMagnitudeHint"] == value for row in rows))
                for value in {row["stressMagnitudeHint"] for row in rows}
            )),
            "appCueProfileCounts": dict(sorted(
                (value, sum(row["appCueProfile"] == value for row in rows))
                for value in {row["appCueProfile"] for row in rows}
            )),
        }
    )
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def target_sources(
    decisions: list[dict[str, str]],
) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for decision in decisions:
        sources = tokens(decision["원본 stableKey"])
        targets = tokens(decision["최종 stableKey"])
        for target in targets:
            result[target] = sources
    return result


def canonicalize_runtime_metadata(
    path: Path,
    canonical_exercises: list[dict[str, str]],
    metadata_by_key: dict[str, dict[str, str]],
    sources_by_target: dict[str, list[str]],
) -> None:
    fieldnames, rows = read_csv(path)
    old_by_key = {row["stableKey"]: row for row in rows}
    output: list[dict[str, str]] = []
    for exercise in canonical_exercises:
        key = exercise["stable_key"]
        metadata = metadata_by_key.get(key)
        base_key = key if key in old_by_key else BASE_KEYS.get(key, key)
        base = old_by_key.get(base_key)
        if base is None and metadata:
            base = next(
                (old_by_key[source] for source in sources_by_target.get(key, []) if source in old_by_key),
                None,
            )
        if base is None:
            raise ValueError(f"No runtime metadata base row for {key}")
        row = dict(base)
        row["stableKey"] = key
        row["exerciseName"] = exercise["exercise_name"]
        if key in RUNTIME_SUBTYPES:
            row["movementSubtype"] = RUNTIME_SUBTYPES[key]
        if key == "lateral_bound_continuous":
            row.update(
                {
                    "movementFamily": "LATERAL_BOUND_CONTINUOUS_VARIANTS",
                    "programSlot": "PLYOMETRIC_POWER",
                    "redundancyGroup": "LATERAL_CONTINUOUS_SSC",
                    "secondaryStressTags": "FRONTAL_PLANE_LOAD|REACTIVE_SSC_LOAD|SINGLE_LEG_STABILITY_LOAD|CALF_ANKLE_SSC_LOAD",
                    "jointImpactStressTags": "JUMP_LANDING_IMPACT_STRESS",
                    "badmintonTransferType": "LOWER_BODY_SUPPORTIVE",
                    "badmintonSkillTargets": "FIRST_STEP|LATERAL_MOVEMENT",
                }
            )
        output.append(row)
    write_csv(path, fieldnames, output)


def tissue_base_rows(
    rows: list[dict[str, str]],
    target_key: str,
    sources_by_target: dict[str, list[str]],
) -> list[dict[str, str]]:
    available_keys = {row["exerciseStableKey"] for row in rows}
    base_key = target_key if target_key in available_keys else BASE_KEYS.get(target_key, target_key)
    selected = [row for row in rows if row["exerciseStableKey"] == base_key]
    if selected:
        return selected
    for source in sources_by_target.get(target_key, []):
        selected = [row for row in rows if row["exerciseStableKey"] == source]
        if selected:
            return selected
    return []


def canonicalize_tissue_asset(
    path: Path,
    canonical_exercises: list[dict[str, str]],
    sources_by_target: dict[str, list[str]],
) -> None:
    fieldnames, rows = read_csv(path)
    output: list[dict[str, str]] = []
    korean_name_column = next(
        (column for column in fieldnames if column == "운동명"),
        None,
    )
    for exercise in canonical_exercises:
        key = exercise["stable_key"]
        for source in tissue_base_rows(rows, key, sources_by_target):
            row = dict(source)
            row["exerciseStableKey"] = key
            if korean_name_column:
                row[korean_name_column] = exercise["exercise_name"]
            for source_column, target_column in (
                ("body_region", "bodyRegion"),
                ("force_type", "forceType"),
                ("movement_pattern", "movementPattern"),
                ("movement_category", "movementCategory"),
                ("laterality", "laterality"),
                ("plane", "plane"),
                ("equipment_tags", "equipmentTags"),
                ("primary_muscles", "primaryMuscles"),
                ("secondary_muscles", "secondaryMuscles"),
                ("description", "description"),
            ):
                if target_column in row:
                    row[target_column] = exercise.get(source_column, row[target_column])
            if "executionLaterality(D_CONTEXT_ONLY)" in row:
                row["executionLaterality(D_CONTEXT_ONLY)"] = exercise["laterality"]
            if key == "lateral_bound_continuous":
                if "movementPattern" in row:
                    row["movementPattern"] = "LATERAL_POWER|PLYOMETRIC_SSC|CONTINUOUS_REBOUND"
                if "ContextFlags" in row:
                    row["ContextFlags"] = "ALTERNATING|FRONTAL|PLYOMETRIC_SSC|CONTINUOUS_REBOUND"
                if "runtimeFlags" in row:
                    row["runtimeFlags"] = "CONTACTS_REQUIRED|CONTINUOUS_REBOUND"
            tool_context = {
                "half_kneeling_single_arm_dumbbell_press": "DUMBBELL",
                "half_kneeling_single_arm_kettlebell_press": "KETTLEBELL",
                "barbell_romanian_deadlift": "BARBELL",
                "dumbbell_romanian_deadlift": "DUMBBELL",
            }.get(key)
            if tool_context:
                if "ContextFlags" in row:
                    flags = tokens(row["ContextFlags"])
                    flags = [flag for flag in flags if flag not in {"BARBELL", "DUMBBELL", "KETTLEBELL"}]
                    row["ContextFlags"] = "|".join([tool_context, *flags])
                if "runtimeFlags" in row:
                    flags = tokens(row["runtimeFlags"])
                    row["runtimeFlags"] = "|".join([tool_context, *[flag for flag in flags if flag != tool_context]])
            output.append(row)
    write_csv(path, fieldnames, output)


def canonicalize_keyed_tissue_asset(
    path: Path,
    canonical_exercises: list[dict[str, str]],
    sources_by_target: dict[str, list[str]],
    key_column: str = "stableKey",
) -> None:
    fieldnames, rows = read_csv(path)
    rows_by_key: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        rows_by_key.setdefault(row[key_column], []).append(row)

    output: list[dict[str, str]] = []
    seen: set[tuple[str, ...]] = set()
    for exercise in canonical_exercises:
        key = exercise["stable_key"]
        base_key = key if key in rows_by_key else BASE_KEYS.get(key, key)
        base_rows = rows_by_key.get(base_key, [])
        if not base_rows:
            base_rows = next(
                (rows_by_key[source] for source in sources_by_target.get(key, []) if source in rows_by_key),
                [],
            )
        for source in base_rows:
            old_key = source[key_column]
            row = {
                column: value.replace(old_key, key) if old_key else value
                for column, value in source.items()
            }
            row[key_column] = key
            identity = tuple(row[column] for column in fieldnames)
            if identity not in seen:
                seen.add(identity)
                output.append(row)
    write_csv(path, fieldnames, output)


def canonicalize_tissue_context_names(
    path: Path,
    canonical_by_key: dict[str, dict[str, str]],
    source_to_target: dict[str, str | None],
) -> None:
    fieldnames, rows = read_csv(path)
    for row in rows:
        target = source_to_target.get(row["exerciseStableKey"], row["exerciseStableKey"])
        if not target or target not in canonical_by_key:
            raise ValueError(f"Unresolved COD context exercise: {row['exerciseStableKey']}")
        row["exerciseStableKey"] = target
        row["displayNameKo"] = canonical_by_key[target]["exercise_name"]
    if len(rows) != len({row["exerciseStableKey"] for row in rows}):
        raise ValueError("Duplicate COD context exercise after canonicalization")
    write_csv(path, fieldnames, rows)


def update_tissue_manifest(directory: Path, asset_names: tuple[str, ...]) -> None:
    path = directory / "tissue_rcv_asset_manifest_v1.csv"
    fieldnames, rows = read_csv(path)
    for row in rows:
        if row["assetName"] not in asset_names:
            continue
        asset = directory / row["assetName"]
        _, asset_rows = read_csv(asset)
        row["rowCount"] = str(len(asset_rows))
        row["assetSha256"] = text_sha256(asset)
    write_csv(path, fieldnames, rows)


def write_legacy_import_map(path: Path, rows: list[dict[str, str]]) -> None:
    fieldnames = [
        "old_stable_key",
        "old_name",
        "canonical_stable_key",
        "canonical_name",
        "import_rule",
    ]
    overrides = {
        "ex_d2bb7946": (
            "barbell_romanian_deadlift",
            "루마니안 바벨 데드리프트",
            "DIRECT",
        ),
        "ex_8380d7fe": (
            "half_kneeling_single_arm_dumbbell_press",
            "하프 닐링 원암 덤벨 프레스",
            "DIRECT",
        ),
        "ex_8e1b313e": (
            "half_kneeling_single_arm_dumbbell_press",
            "하프 닐링 원암 덤벨 프레스",
            "DIRECT",
        ),
        "ex_66e8c8c2": (
            "half_kneeling_single_arm_dumbbell_press",
            "하프 닐링 원암 덤벨 프레스",
            "DIRECT",
        ),
        "ex_e3487166": ("", "", "DROP_DELETED_EXERCISE_WITH_WARNING"),
    }
    output = []
    for row in rows:
        canonical_key, canonical_name, import_rule = overrides.get(
            row["oldStableKey"],
            (row["canonicalStableKey"], row["canonicalName"], row["importRule"]),
        )
        output.append(
            {
                "old_stable_key": row["oldStableKey"],
                "old_name": row["oldName"],
                "canonical_stable_key": canonical_key,
                "canonical_name": canonical_name,
                "import_rule": import_rule,
            }
        )
    output.append(
        {
            "old_stable_key": "imported_배드민턴",
            "old_name": "배드민턴",
            "canonical_stable_key": "ex_ae9ecdbc",
            "canonical_name": "배드민턴 경기 기록",
            "import_rule": "DIRECT",
        }
    )
    write_csv(path, fieldnames, output)


def canonicalize_strength_assets(directory: Path) -> None:
    assignment_path = directory / "repetition_curve_assignments_v1.csv"
    assignment_fields, assignments = read_csv(assignment_path)
    generic_assignment = next(
        row
        for row in assignments
        if row["exerciseStableKey"] in {"ex_d2bb7946", "barbell_romanian_deadlift"}
    )
    assignments = [
        row
        for row in assignments
        if row["exerciseStableKey"]
        not in {
            "ex_d2bb7946",
            "ex_9523db82",
            "barbell_romanian_deadlift",
            "dumbbell_romanian_deadlift",
        }
    ] + [
        dict(generic_assignment, exerciseStableKey="barbell_romanian_deadlift"),
        dict(generic_assignment, exerciseStableKey="dumbbell_romanian_deadlift"),
    ]
    write_csv(
        assignment_path,
        assignment_fields,
        sorted(assignments, key=lambda row: row["exerciseStableKey"]),
    )

    proxy_path = directory / "strength_proxy_loadings_v1.csv"
    proxy_fields, proxy_rows = read_csv(proxy_path)
    for row in proxy_rows:
        row["exerciseStableKey"] = {
            "ex_d2bb7946": "barbell_romanian_deadlift",
            "ex_9523db82": "dumbbell_romanian_deadlift",
        }.get(row["exerciseStableKey"], row["exerciseStableKey"])
    write_csv(proxy_path, proxy_fields, proxy_rows)

    target_path = directory / "strength_target_registry_v1.csv"
    target_fields, target_rows = read_csv(target_path)
    for row in target_rows:
        movements = tokens(row["closeVariationStableKeys"])
        row["closeVariationStableKeys"] = "|".join(
            {
                "ex_d2bb7946": "barbell_romanian_deadlift",
                "ex_9523db82": "dumbbell_romanian_deadlift",
            }.get(key, key)
            for key in movements
        )
    write_csv(target_path, target_fields, target_rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", required=True, type=Path)
    parser.add_argument("--seed", required=True, type=Path)
    parser.add_argument("--runtime-metadata", type=Path)
    parser.add_argument("--tissue-dir", type=Path)
    parser.add_argument("--legacy-map", type=Path)
    parser.add_argument("--strength-dir", type=Path)
    args = parser.parse_args()

    workbook = load_workbook(args.workbook, read_only=True, data_only=True)
    decisions = rows_from_sheet(workbook, "통합_결정")
    final_metadata = rows_from_sheet(workbook, "최종_메타데이터")
    legacy_mapping = rows_from_sheet(workbook, "백업_호환_매핑")
    source_to_target, source_keys = target_map(decisions)

    with args.seed.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        fieldnames = list(reader.fieldnames or [])
        rows = list(reader)
    for column in EXTRA_COLUMNS:
        if column not in fieldnames:
            fieldnames.append(column)

    exercises = [row for row in rows if row["row_type"] == "exercise"]
    for position, row in enumerate(exercises):
        row["__position"] = str(position)
    old_by_key = {row["stable_key"]: row for row in exercises}
    old_key_by_name = {row["exercise_name"]: row["stable_key"] for row in exercises}

    canonical_rows = [
        canonical_exercise(metadata, old_by_key, source_keys)
        for metadata in final_metadata
    ]
    final_target_keys = {metadata["stableKey"] for metadata in final_metadata}
    retained = [
        (int(row["__position"]), row)
        for row in exercises
        if row["stable_key"] not in source_keys and row["stable_key"] not in final_target_keys
    ]
    final_exercises = [
        row
        for _, row in sorted(retained + canonical_rows, key=lambda item: (item[0], item[1]["stable_key"]))
    ]
    canonical_by_key = {row["stable_key"]: row for row in final_exercises}
    metadata_by_key = {row["stableKey"]: row for row in final_metadata}
    sources_by_target = target_sources(decisions)

    for row in rows:
        row.pop("__position", None)
        row["schema_version"] = "4"
        if row["row_type"] != "program_item":
            continue
        old_key = row["stable_key"].strip() or old_key_by_name.get(row["exercise_name"], "")
        if not old_key:
            raise ValueError(f"Program item has no exact seed exercise: {row['exercise_name']}")
        target = source_to_target.get(old_key, old_key)
        if not target or target not in canonical_by_key:
            raise ValueError(f"Program item resolves to deleted or missing exercise: {row['exercise_name']} / {old_key}")
        row["stable_key"] = target
        row["exercise_name"] = canonical_by_key[target]["exercise_name"]

    names = [row["exercise_name"] for row in final_exercises]
    keys = [row["stable_key"] for row in final_exercises]
    forbidden = ("싱글 레그", "싱글레그", "원 레그", "싱글 암", "싱글암", "원 암")
    if len(names) != len(set(names)):
        raise ValueError("Duplicate canonical exercise names remain")
    if len(keys) != len(set(keys)) or any(not key for key in keys):
        raise ValueError("Duplicate or blank canonical stableKey remains")
    if any(term in name for name in names for term in forbidden):
        raise ValueError("Forbidden unilateral spelling remains")
    if any("CSV 복원" in name for name in names):
        raise ValueError("Legacy CSV placeholder remains active")

    output_rows = final_exercises + [row for row in rows if row["row_type"] != "exercise"]
    for row in output_rows:
        row.pop("__position", None)
    with args.seed.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(output_rows)

    program_items = [row for row in output_rows if row["row_type"] == "program_item"]
    assert all(row["stable_key"] in canonical_by_key for row in program_items)
    print(f"canonical exercises: {len(final_exercises)}")
    print(f"program items with explicit stableKey: {len(program_items)}")
    if args.runtime_metadata:
        canonicalize_runtime_metadata(
            args.runtime_metadata,
            final_exercises,
            metadata_by_key,
            sources_by_target,
        )
        manifest = args.runtime_metadata.with_name("canonical_exercise_metadata_manifest.json")
        if manifest.exists():
            update_runtime_manifest(manifest, args.runtime_metadata, args.workbook)
        print(f"runtime metadata rows: {len(final_exercises)}")
    if args.tissue_dir:
        tissue_assets = (
            "tissue_rcv_exercise_index_v1.csv",
            "tissue_rcv_exercise_protocols_v1.csv",
            "tissue_rcv_exercise_load_unit_authority_v1.csv",
        )
        for filename in tissue_assets:
            canonicalize_tissue_asset(
                args.tissue_dir / filename,
                final_exercises,
                sources_by_target,
            )
        context_asset = args.tissue_dir / "cod_context_exercise_tiers_v1.csv"
        canonicalize_tissue_context_names(context_asset, canonical_by_key, source_to_target)
        for filename in (
            "exercise_tissue_scope_manifest_v1.csv",
            "tissue_mtc_exercise_movement_family_mapping_v1.csv",
            "tissue_mtc_exercise_complex_applicability_v1.csv",
            "tissue_mtc_fallback_resolution_trace_v1.csv",
        ):
            canonicalize_keyed_tissue_asset(
                args.tissue_dir / filename,
                final_exercises,
                sources_by_target,
            )
        update_tissue_manifest(args.tissue_dir, tissue_assets)
        print(f"connective-tissue exercise identities: {len(final_exercises)}")
    if args.legacy_map:
        write_legacy_import_map(args.legacy_map, legacy_mapping)
        print(f"legacy import mappings: {len(legacy_mapping) + 1}")
    if args.strength_dir:
        canonicalize_strength_assets(args.strength_dir)
        print("strength-performance identities canonicalized")


if __name__ == "__main__":
    main()
