from __future__ import annotations

import csv
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CANONICAL = ROOT / "app/src/main/assets/metadata/canonical_v1"
TISSUE = ROOT / "app/src/main/assets/metadata/tissue_load_v1"

RUNTIME_FIELDS = {
    "currentActivityKind": "ACTIVITY_KIND",
    "currentPlanningEligibility": "PLANNING_ELIGIBILITY",
    "movementFamily": "MOVEMENT_FAMILY",
    "movementSubtype": "MOVEMENT_SUBTYPE",
    "programSlot": "PROGRAM_SLOT",
    "redundancyGroup": "REDUNDANCY_GROUP",
    "progressMetricType": "PROGRESS_METRIC",
    "strengthProgressionGroup": "STRENGTH_PROGRESSION_GROUP",
    "analysisEligibility": "ANALYSIS_ELIGIBILITY",
    "primaryStressProfile": "PRIMARY_STRESS_PROFILE",
    "secondaryStressTags": "SECONDARY_STRESS",
    "tendonStressTags": "TENDON_STRESS",
    "ligamentJointStabilityStressTags": "LIGAMENT_JOINT_STABILITY",
    "jointImpactStressTags": "JOINT_IMPACT",
    "cognitiveStressTags": "COGNITIVE_STRESS",
    "sportContextTags": "SPORT_CONTEXT",
    "recoveryDecayProfile": "RECOVERY_DECAY",
    "stressMagnitudeHint": "STRESS_LEVEL",
    "badmintonTransferLevel": "BADMINTON_TRANSFER_LEVEL",
    "badmintonTransferType": "BADMINTON_TRANSFER_TYPE",
    "badmintonSkillTargets": "BADMINTON_SKILL_TARGET",
    "badmintonPhysicalQualities": "BADMINTON_PHYSICAL_QUALITY",
    "transferConfidence": "TRANSFER_CONFIDENCE",
    "sourceConfidenceLevel": "SOURCE_CONFIDENCE",
    "finalSourceStatus": "FINAL_SOURCE_STATUS",
    "neuromuscularStressLevel": "NEUROMUSCULAR_STRESS",
    "systemicMuscularStressLevel": "SYSTEMIC_MUSCULAR_STRESS",
    "localMuscularStressLevel": "LOCAL_MUSCULAR_STRESS",
    "jointTendonImpactStressLevel": "JOINT_TENDON_IMPACT_STRESS",
    "movementFocusDemandLevel": "MOVEMENT_FOCUS_DEMAND",
    "recoveryDurationClass": "RECOVERY_DURATION",
}

BOOTSTRAP_FIELDS = {
    "category": "EXERCISE_CATEGORY",
    "mode": "EXERCISE_MODE",
    "detail1": "EXERCISE_DETAIL",
    "detail2": "EXERCISE_DETAIL",
    "movementPattern": "MOVEMENT_PATTERN",
    "movementCategory": "MOVEMENT_CATEGORY",
    "primaryMuscles": "MUSCLE",
    "secondaryMuscles": "MUSCLE",
    "equipment": "EQUIPMENT",
    "equipmentTags": "EQUIPMENT",
    "compoundType": "COMPOUND_TYPE",
    "forceType": "FORCE_TYPE",
    "bodyRegion": "BODY_REGION",
    "plane": "PLANE",
    "laterality": "LATERALITY",
    "axialLoadLevel": "AXIAL_LOAD",
    "stabilityRoles": "STABILITY_ROLE",
    "loadProfile": "LOAD_PROFILE",
    "recoveryDecayProfile": "RECOVERY_PROFILE",
    "stabilityDemandLevel": "STABILITY_DEMAND",
    "mobilityDemandLevel": "MOBILITY_DEMAND",
    "progressMetricType": "PROGRESS_METRIC",
    "strengthProgressionGroup": "PROGRESSION_GROUP",
    "sportTransferDirect": "DIRECT_TRANSFER",
    "sportTransferSupportive": "SUPPORTIVE_TRANSFER",
    "badmintonTransferRoles": "BADMINTON_TRANSFER_TYPE",
}

ENUM_FIELDS = {
    "MovementPattern": "MOVEMENT_PATTERN",
    "MovementCategory": "MOVEMENT_CATEGORY",
    "FatigueForceType": "FORCE_TYPE",
    "TrainingRole": "TRAINING_ROLE_RELATION",
    "ProgramSlotCapability": "PROGRAM_SLOT_CAPABILITY",
    "AxialLoadLevel": "AXIAL_LOAD",
    "FatigueLaterality": "LATERALITY",
    "MetadataConfidence": "METADATA_CONFIDENCE",
    "BadmintonTransferRole": "DIRECT_TRANSFER",
}

DEFAULTS = {
    "ACTIVITY_KIND": {"EXERCISE", "SPORT_SESSION"},
    "PLANNING_ELIGIBILITY": {"PROGRAM_SELECTABLE", "FATIGUE_ONLY", "ANALYSIS_ONLY", "HIDDEN"},
    "MOVEMENT_FAMILY": {"NOT_APPLICABLE"},
    "MOVEMENT_SUBTYPE": {"NOT_APPLICABLE"},
    "TRANSFER_CONFIDENCE": {"NONE", "LOW", "MEDIUM", "HIGH"},
    "SOURCE_CONFIDENCE": {"HEURISTIC_ACCEPTED", "ANATOMY_SUPPORTED", "SOURCE_WEAK_BUT_ACCEPTABLE", "VERIFIED_FAMILY", "VERIFIED_EXACT"},
    "FINAL_SOURCE_STATUS": {"SOURCE_ACCEPTED", "SOURCE_ACCEPTED_WITH_LIMITATION"},
    "METADATA_CONFIDENCE": {"HIGH", "MEDIUM", "LOW", "NEEDS_REVIEW", "UNKNOWN"},
}

RAW_UI_EXPOSURES = {
    "EXERCISE_CATEGORY": "CommonUi.kt|ExerciseScreen.kt|RuntimeMetadataExerciseEditorDialog.kt",
    "EXERCISE_MODE": "CommonUi.kt",
    "EXERCISE_DETAIL": "CommonUi.kt",
    "MUSCLE": "CommonUi.kt|RuntimeMetadataExerciseEditorDialog.kt",
    "EQUIPMENT": "CommonUi.kt|RuntimeMetadataExerciseEditorDialog.kt",
    "BODY_REGION": "RuntimeMetadataExerciseEditorDialog.kt",
}


@dataclass
class InventoryItem:
    display_field: str
    canonical_code: str
    source_files: set[str] = field(default_factory=set)
    production_usage_count: int = 0


def _rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def _tokens(value: str) -> tuple[str, ...]:
    return tuple(token.strip() for token in re.split(r"[|,]", value or "") if token.strip())


def _enum_values(source: str, enum_name: str) -> set[str]:
    match = re.search(rf"enum class {enum_name}\s*\{{(.*?)\n\}}", source, re.DOTALL)
    if not match:
        return set()
    body = re.sub(r"//.*", "", match.group(1))
    return set(re.findall(r"(?m)^\s*([A-Z][A-Z0-9_]*)\s*(?:\([^\n]*\))?\s*[,;]?\s*$", body))


def collect_inventory() -> dict[tuple[str, str], InventoryItem]:
    items: dict[tuple[str, str], InventoryItem] = {}

    def add(display_field: str, canonical_code: str, source: Path, count: int = 1) -> None:
        code = canonical_code.strip()
        if not code or code == "NONE":
            return
        key = (display_field, code)
        item = items.setdefault(key, InventoryItem(display_field, code))
        item.source_files.add(source.relative_to(ROOT).as_posix())
        item.production_usage_count += count

    runtime_path = CANONICAL / "runtime_metadata.csv"
    for row in _rows(runtime_path):
        for column, display_field in RUNTIME_FIELDS.items():
            for code in _tokens(row.get(column, "")):
                add(display_field, code, runtime_path)

    bootstrap_path = CANONICAL / "exercise_bootstrap.csv"
    for row in _rows(bootstrap_path):
        for column, display_field in BOOTSTRAP_FIELDS.items():
            for code in _tokens(row.get(column, "")):
                add(display_field, code, bootstrap_path)

    taxonomy_paths = [
        ROOT / "app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt",
        ROOT / "app/src/main/java/com/training/trackplanner/data/ExerciseRoleRelations.kt",
    ]
    taxonomy = "\n".join(path.read_text(encoding="utf-8") for path in taxonomy_paths)
    for enum_name, display_field in ENUM_FIELDS.items():
        for code in _enum_values(taxonomy, enum_name):
            add(display_field, code, taxonomy_paths[0])

    slot_path = ROOT / "app/src/main/java/com/training/trackplanner/data/ProgramSlotDefinition.kt"
    slots = _enum_values(slot_path.read_text(encoding="utf-8"), "ProgramSlotId")
    for code in slots:
        for display_field in ("PROGRAM_SLOT", "MOVEMENT_FAMILY", "REDUNDANCY_GROUP", "STRENGTH_PROGRESSION_GROUP"):
            add(display_field, code, slot_path)

    for display_field, codes in DEFAULTS.items():
        for code in codes:
            add(display_field, code, runtime_path)
    for display_field in ("STRESS_LEVEL", "NEUROMUSCULAR_STRESS", "SYSTEMIC_MUSCULAR_STRESS", "LOCAL_MUSCULAR_STRESS", "JOINT_TENDON_IMPACT_STRESS", "MOVEMENT_FOCUS_DEMAND"):
        for code in ("LOW", "MODERATE", "HIGH", "VERY_HIGH"):
            add(display_field, code, runtime_path)
    for display_field in ("RECOVERY_DECAY", "RECOVERY_DURATION"):
        for code in ("SHORT", "MEDIUM", "LONG", "VERY_LONG"):
            add(display_field, code, runtime_path)

    relation_specs = [
        ("muscle_relations.csv", "muscleCode", "MUSCLE_GROUP"),
        ("equipment_relations.csv", "equipmentCode", "EQUIPMENT"),
        ("equipment_relations.csv", "requirementModel", "EQUIPMENT_REQUIREMENT_MODEL"),
        ("training_roles.csv", "trainingRoleCode", "TRAINING_ROLE_RELATION"),
        ("program_slot_capabilities.csv", "capabilityCode", "PROGRAM_SLOT_CAPABILITY"),
        ("progression_relations.csv", "progressionGroup", "PROGRESSION_GROUP"),
        ("recovery_relations.csv", "recoveryDecayProfile", "RECOVERY_PROFILE"),
        ("strength_proxy_relations.csv", "relationRole", "STRENGTH_PROXY_ROLE"),
    ]
    for file_name, column, display_field in relation_specs:
        path = CANONICAL / file_name
        for row in _rows(path):
            add(display_field, row[column], path)

    movement_path = CANONICAL / "movement_relations.csv"
    movement_fields = {
        "MOVEMENT_PATTERN": "MOVEMENT_PATTERN",
        "JOINT_ACTION": "JOINT_ACTION",
        "MOVEMENT_EVENT": "MOVEMENT_EVENT",
        "KINETIC_CHAIN": "KINETIC_CHAIN",
        "STABILITY_DEMAND": "STABILITY_DEMAND",
        "MOBILITY_DEMAND": "MOBILITY_DEMAND",
        "MOVEMENT_FAMILY": "MOVEMENT_FAMILY",
        "MOVEMENT_SUBTYPE": "MOVEMENT_SUBTYPE",
    }
    for row in _rows(movement_path):
        display_field = movement_fields.get(row["relationType"])
        if display_field:
            add(display_field, row["relationValue"], movement_path)

    ofi_path = CANONICAL / "ofi_relations.csv"
    for row in _rows(ofi_path):
        if row["relationType"] == "OFI_AXIS":
            add("OFI_AXIS", row["relationId"], ofi_path)

    for file_name, display_field in (("tissue_rcv_tissues_v1.csv", "TISSUE"), ("tissue_rcv_joint_complexes_v1.csv", "JOINT_COMPLEX")):
        path = TISSUE / file_name
        for row in _rows(path):
            add(display_field, row["canonicalCode"], path)

    return dict(sorted(items.items()))


def inventory_headers() -> list[str]:
    return [
        "displayField", "canonicalCode", "sourceFiles", "productionUsageCount",
        "currentKoreanLabel", "currentEnglishLabel", "coverageStatus", "rawUiExposureLocations",
    ]
