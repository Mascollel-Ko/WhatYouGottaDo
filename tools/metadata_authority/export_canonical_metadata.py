from __future__ import annotations

import argparse
import filecmp
import html
import shutil
import tempfile
from pathlib import Path

from authority_common import (
    BOOTSTRAP_SHEET,
    DISPLAY_SHEET,
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
from analysis_cutover_authority import (
    BADMINTON_OBJECTIVE_HEADERS,
    CORE_HEADERS,
    build_analysis_assets,
)


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

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RESOURCE_ROOT = ROOT / "app/src/main/res"
DEFAULT_DISPLAY_MANIFEST = ROOT / "docs/metadata_authority/metadata_display_resource_manifest.json"
DISPLAY_CSV_HEADERS = [
    "displayField", "canonicalCode", "koreanLabel", "koreanShortLabel",
    "koreanFormalLabel", "koreanDescription", "koreanSearchAliases",
    "englishLabel", "englishSearchAliases", "allowedLatinTokens",
    "displayScope", "reviewStatus",
]
EXTERNALLY_MANAGED_RUNTIME_ARTIFACTS = {
    "metadata_field_display_contract.json",
    "metadata_revision_manifest.json",
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


def write_display_resource(path: Path, rows: list[dict[str, str]], *, locale: str) -> None:
    label_field = "koreanLabel" if locale == "ko" else "englishLabel"
    alias_fields = (
        ("koreanLabel", "koreanShortLabel", "koreanFormalLabel", "koreanSearchAliases")
        if locale == "ko" else
        ("englishLabel", "englishSearchAliases")
    )
    visible = [row for row in rows if row["displayScope"] in {"PRODUCTION", "EDITOR_ONLY", "SEARCH_ONLY"}]
    labels = [f"{row['displayField']}|{row['canonicalCode']}|{row[label_field]}" for row in visible]
    aliases = []
    for row in visible:
        values: list[str] = []
        for field in alias_fields:
            values.extend(token.strip() for token in row[field].split("|") if token.strip())
        aliases.append(f"{row['displayField']}|{row['canonicalCode']}|{'|'.join(dict.fromkeys(values))}")

    def array(name: str, values: list[str]) -> str:
        body = "\n".join(f"        <item>{html.escape(value, quote=False)}</item>" for value in values)
        return f"    <string-array name=\"{name}\">\n{body}\n    </string-array>"

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
        + array("metadata_display_entries", labels) + "\n"
        + array("metadata_display_alias_entries", aliases) + "\n"
        + "</resources>\n",
        encoding="utf-8",
        newline="\n",
    )


def export(
    workbook_path: Path,
    output: Path,
    resource_root: Path = DEFAULT_RESOURCE_ROOT,
    display_manifest_path: Path = DEFAULT_DISPLAY_MANIFEST,
) -> dict:
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

    display = sorted(sheet_rows(workbook, DISPLAY_SHEET), key=lambda row: (row["displayField"], row["canonicalCode"]))
    emit("metadata_display_labels_ko.csv", DISPLAY_CSV_HEADERS, display, ["displayField", "canonicalCode"], "PRESENTATION_AND_SEARCH")
    ko_resource = resource_root / "values/metadata_display_catalog.xml"
    en_resource = resource_root / "values-en/metadata_display_catalog.xml"
    write_display_resource(ko_resource, display, locale="ko")
    write_display_resource(en_resource, display, locale="en")
    write_json(display_manifest_path, {
        "authorityWorkbookSha256": sha256(workbook_path),
        "files": [
            {"path": "app/src/main/res/values/metadata_display_catalog.xml", "rowCount": len(display), "sha256": sha256(ko_resource)},
            {"path": "app/src/main/res/values-en/metadata_display_catalog.xml", "rowCount": len(display), "sha256": sha256(en_resource)},
        ],
        "productionRowCount": sum(row["displayScope"] == "PRODUCTION" for row in display),
        "registryRowCount": len(display),
        "schemaVersion": 1,
    })

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

    core_relations, objective_relations, _ = build_analysis_assets()
    emit(
        "core_relations.csv",
        CORE_HEADERS,
        core_relations,
        ["relationId"],
        PRODUCTION_ACTIVE,
    )
    emit(
        "badminton_objective_relations.csv",
        BADMINTON_OBJECTIVE_HEADERS,
        objective_relations,
        ["relationId"],
        PRODUCTION_ACTIVE,
    )

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
            temporary = Path(directory)
            generated = temporary / "canonical_v1"
            generated_resources = temporary / "res"
            generated_manifest = temporary / "metadata_display_resource_manifest.json"
            export(args.workbook, generated, generated_resources, generated_manifest)
            for name in EXTERNALLY_MANAGED_RUNTIME_ARTIFACTS:
                source = args.output / name
                if source.exists():
                    shutil.copy2(source, generated / name)
            compare_directories(generated, args.output)
            for relative in ("values/metadata_display_catalog.xml", "values-en/metadata_display_catalog.xml"):
                if not filecmp.cmp(generated_resources / relative, DEFAULT_RESOURCE_ROOT / relative, shallow=False):
                    raise ValueError(f"Generated display resource is stale: {relative}")
            if not filecmp.cmp(generated_manifest, DEFAULT_DISPLAY_MANIFEST, shallow=False):
                raise ValueError("Generated display resource manifest is stale")
        print("Canonical metadata assets are deterministic and current.")
    else:
        preserved = {
            name: (args.output / name).read_bytes()
            for name in EXTERNALLY_MANAGED_RUNTIME_ARTIFACTS
            if (args.output / name).exists()
        }
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "canonical_v1"
            manifest = export(args.workbook, generated)
            if args.output.exists():
                shutil.rmtree(args.output)
            shutil.copytree(generated, args.output)
        for name, content in preserved.items():
            (args.output / name).write_bytes(content)
        print(f"Exported {len(manifest['files'])} canonical metadata files to {args.output}")


if __name__ == "__main__":
    main()
