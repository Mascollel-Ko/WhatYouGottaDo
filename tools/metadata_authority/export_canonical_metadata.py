from __future__ import annotations

import argparse
import filecmp
import shutil
import tempfile
from pathlib import Path

from authority_common import (
    BOOTSTRAP_SHEET,
    HISTORY_COMPATIBILITY_ONLY,
    HISTORY_ONLY_STATUS,
    IDENTITY_SHEET,
    PROGRAM_SLOT_SHEET,
    PRODUCTION_ACTIVE,
    RUNTIME_SHEET,
    SCALAR_SHEET,
    TIMING_SHEET,
    TRAINING_ROLE_SHEET,
    index_by,
    load_workbook,
    sha256,
    sheet_rows,
    write_csv,
    write_json,
)
from validate_authority_workbook import validate


RUNTIME_HEADERS = [
    "stableKey", "exerciseName", "currentActivityKind", "currentPlanningEligibility",
    "movementFamily", "movementSubtype", "programSlot", "redundancyGroup",
    "progressMetricType", "strengthProgressionGroup", "analysisEligibility",
    "primaryStressProfile", "secondaryStressTags", "tendonStressTags",
    "ligamentJointStabilityStressTags", "jointImpactStressTags", "cognitiveStressTags",
    "sportContextTags", "recoveryDecayProfile", "stressMagnitudeHint",
    "badmintonTransferLevel", "badmintonTransferType", "badmintonSkillTargets",
    "badmintonPhysicalQualities", "transferConfidence", "sourceConfidenceLevel",
    "finalSourceStatus", "neuromuscularStressLevel", "systemicMuscularStressLevel",
    "localMuscularStressLevel", "jointTendonImpactStressLevel",
    "movementFocusDemandLevel", "recoveryDurationClass", "appCueProfile",
]


SCALAR_TO_RUNTIME = {
    "activityKind": "currentActivityKind",
    "planningEligibility": "currentPlanningEligibility",
    "movementFamily": "movementFamily",
    "movementSubtype": "movementSubtype",
    "progressMetricType": "progressMetricType",
    "strengthProgressionGroup": "strengthProgressionGroup",
    "analysisEligibility": "analysisEligibility",
    "primaryStressProfile": "primaryStressProfile",
    "secondaryStressTags": "secondaryStressTags",
    "tendonStressTags": "tendonStressTags",
    "ligamentJointStabilityStressTags": "ligamentJointStabilityStressTags",
    "jointImpactStressTags": "jointImpactStressTags",
    "cognitiveStressTags": "cognitiveStressTags",
    "sportContextTags": "sportContextTags",
    "recoveryDecayProfile": "recoveryDecayProfile",
    "stressMagnitudeHint": "stressMagnitudeHint",
    "badmintonTransferLevel": "badmintonTransferLevel",
    "badmintonTransferType": "badmintonTransferType",
    "badmintonSkillTargets": "badmintonSkillTargets",
    "badmintonPhysicalQualities": "badmintonPhysicalQualities",
    "transferConfidence": "transferConfidence",
    "sourceConfidenceLevel": "sourceConfidenceLevel",
    "finalSourceStatus": "finalSourceStatus",
    "recoveryDurationClass": "recoveryDurationClass",
    "appCueProfile": "appCueProfile",
}


RELATION_EXPORTS = {
    "09_MOVEMENT_REL": ("movement_relations.csv", ["relationId"]),
    "10_MUSCLE_REL": ("muscle_relations.csv", ["relationKey"]),
    "12_OFI_REL": ("ofi_relations.csv", ["relationKey"]),
    "13_RECOVERY_REL": ("recovery_relations.csv", ["exerciseStableKey"]),
    "14_BADMINTON_REL": ("badminton_relations.csv", ["relationKey"]),
    "15_PROGRESSION": ("progression_relations.csv", ["relationKey"]),
    "17_STRENGTH_PROXY": ("strength_proxy_relations.csv", ["relationId"]),
    "20_EQUIPMENT_REL": ("equipment_relations.csv", ["exerciseStableKey", "groupId", "memberOrder"]),
}


def runtime_rows(workbook) -> list[dict[str, str]]:
    identities = index_by(sheet_rows(workbook, IDENTITY_SHEET), "stableKey")
    scalars = index_by(sheet_rows(workbook, SCALAR_SHEET), "stableKey")
    snapshots = index_by(sheet_rows(workbook, RUNTIME_SHEET), "stableKey")
    rows: list[dict[str, str]] = []
    for stable_key, identity in sorted(identities.items()):
        scalar = scalars[stable_key]
        source_key = scalar["metadataSourceStableKey"] or identity["sourceStableKey"] or stable_key
        source = snapshots.get(stable_key) or snapshots.get(source_key)
        if source is None:
            raise ValueError(f"No runtime snapshot source for {stable_key} ({source_key})")
        row = {header: source.get(header, "") for header in RUNTIME_HEADERS}
        row["stableKey"] = stable_key
        row["exerciseName"] = identity["exerciseName"]
        for scalar_field, runtime_field in SCALAR_TO_RUNTIME.items():
            row[runtime_field] = scalar.get(scalar_field, "")
        if identity["identityStatus"] == HISTORY_ONLY_STATUS:
            row["currentPlanningEligibility"] = "HISTORY_ONLY"
        rows.append(row)
    return rows


def export(workbook_path: Path, output: Path) -> dict:
    counts = validate(workbook_path)
    workbook = load_workbook(workbook_path, read_only=True)
    identities = sheet_rows(workbook, IDENTITY_SHEET)
    active_keys = {row["stableKey"] for row in identities if row["selectable"] == "YES"}
    history_keys = {row["stableKey"] for row in identities if row["identityStatus"] == HISTORY_ONLY_STATUS}
    output.mkdir(parents=True, exist_ok=True)

    files: list[dict] = []

    def emit(name: str, headers: list[str], rows: list[dict[str, str]], primary_key: list[str], scope: str) -> None:
        path = output / name
        count = write_csv(path, headers, rows)
        files.append({
            "path": name,
            "rowCount": count,
            "sha256": sha256(path),
            "primaryKey": primary_key,
            "scope": scope,
        })

    identity_headers = list(identities[0])
    emit("identity_master.csv", identity_headers, sorted(identities, key=lambda row: row["stableKey"]), ["stableKey"], "CANONICAL_AND_HISTORY_COMPATIBILITY")
    emit("history_identity.csv", identity_headers, sorted((row for row in identities if row["stableKey"] in history_keys), key=lambda row: row["stableKey"]), ["stableKey"], HISTORY_COMPATIBILITY_ONLY)
    emit("runtime_metadata.csv", RUNTIME_HEADERS, runtime_rows(workbook), ["stableKey"], "CANONICAL_AND_HISTORY_COMPATIBILITY")

    bootstrap = sheet_rows(workbook, BOOTSTRAP_SHEET)
    emit("exercise_bootstrap.csv", list(bootstrap[0]), sorted(bootstrap, key=lambda row: row["stableKey"]), ["stableKey"], "CANONICAL_AND_HISTORY_COMPATIBILITY")

    timing = sheet_rows(workbook, TIMING_SHEET)
    emit("program_timing.csv", list(timing[0]), sorted(timing, key=lambda row: row["stableKey"]), ["stableKey"], PRODUCTION_ACTIVE)

    training = sheet_rows(workbook, TRAINING_ROLE_SHEET)
    active_training = [row for row in training if row["relationScope"] == PRODUCTION_ACTIVE]
    history_training = [row for row in training if row["relationScope"] == HISTORY_COMPATIBILITY_ONLY]
    emit("training_roles.csv", list(training[0]), sorted(active_training, key=lambda row: (row["exerciseStableKey"], row["trainingRoleCode"])), ["exerciseStableKey", "trainingRoleCode"], PRODUCTION_ACTIVE)
    emit("history_training_roles.csv", list(training[0]), sorted(history_training, key=lambda row: (row["exerciseStableKey"], row["trainingRoleCode"])), ["exerciseStableKey", "trainingRoleCode"], HISTORY_COMPATIBILITY_ONLY)

    slots = sheet_rows(workbook, PROGRAM_SLOT_SHEET)
    active_slots = [row for row in slots if row["relationScope"] == PRODUCTION_ACTIVE]
    history_slots = [row for row in slots if row["relationScope"] == HISTORY_COMPATIBILITY_ONLY]
    emit("program_slot_capabilities.csv", list(slots[0]), sorted(active_slots, key=lambda row: (row["exerciseStableKey"], row["capabilityCode"])), ["exerciseStableKey", "capabilityCode"], PRODUCTION_ACTIVE)
    emit("history_program_slot_capabilities.csv", list(slots[0]), sorted(history_slots, key=lambda row: (row["exerciseStableKey"], row["capabilityCode"])), ["exerciseStableKey", "capabilityCode"], HISTORY_COMPATIBILITY_ONLY)

    for sheet_name, (file_name, primary_key) in RELATION_EXPORTS.items():
        rows = [row for row in sheet_rows(workbook, sheet_name) if row.get("exerciseStableKey", row.get("targetAnchorStableKey", "")) in active_keys or sheet_name == "17_STRENGTH_PROXY"]
        headers = list(rows[0]) if rows else [str(cell.value or "") for cell in workbook[sheet_name][1]]
        emit(file_name, headers, sorted(rows, key=lambda row: tuple(row.get(key, "") for key in primary_key)), primary_key, PRODUCTION_ACTIVE)

    manifest = {
        "schemaVersion": 1,
        "generatorVersion": "1.0.0",
        "authorityWorkbookSha256": sha256(workbook_path),
        "counts": counts,
        "files": sorted(files, key=lambda item: item["path"]),
        "researchScope": "NOT_EXPORTED_TO_RUNTIME_ASSETS",
        "relationshipAdjudication": "NOT_ADJUDICATED",
    }
    write_json(output / "manifest.json", manifest)
    return manifest


def compare_directories(expected: Path, actual: Path) -> None:
    comparison = filecmp.dircmp(expected, actual)
    mismatches = comparison.left_only + comparison.right_only + comparison.diff_files + comparison.funny_files
    if mismatches:
        raise ValueError(f"Canonical metadata export is stale: {sorted(mismatches)}")
    for child in comparison.common_dirs:
        compare_directories(expected / child, actual / child)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "canonical_v1"
            export(args.workbook, generated)
            compare_directories(generated, args.output)
        print("Canonical metadata assets are deterministic and current.")
    else:
        if args.output.exists():
            shutil.rmtree(args.output)
        manifest = export(args.workbook, args.output)
        print(f"Exported {len(manifest['files'])} canonical metadata files to {args.output}")


if __name__ == "__main__":
    main()
