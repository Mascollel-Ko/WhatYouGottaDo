from __future__ import annotations

import argparse
import re
from pathlib import Path

from authority_common import (
    BOOTSTRAP_SHEET,
    CONFLICT_SHEET,
    DECISION_TOKENS,
    HISTORY_COMPATIBILITY_ONLY,
    HISTORY_ONLY_STATUS,
    IDENTITY_SHEET,
    DISPLAY_HEADERS,
    DISPLAY_SHEET,
    PROGRAM_SLOT_SHEET,
    PRODUCTION_ACTIVE,
    TIMING_SHEET,
    TRAINING_ROLE_SHEET,
    index_by,
    load_workbook,
    sheet_rows,
)
from display_inventory import collect_inventory


REQUIRED_SHEETS = {
    "00_README", "01_VALIDATION_SUMMARY", "02_SOURCE_MANIFEST", "03_AUTHORITY_MATRIX",
    "04_STATUS_LEGEND", IDENTITY_SHEET, "06_ALIAS_LINEAGE", "07_RUNTIME_SNAPSHOT",
    "08_SCALAR_METADATA", "09_MOVEMENT_REL", "10_MUSCLE_REL", "11_TISSUE_LOAD_REL",
    "12_OFI_REL", "13_RECOVERY_REL", "14_BADMINTON_REL", "15_PROGRESSION",
    "16_DIRECTED_EDGES", "17_STRENGTH_PROXY", TRAINING_ROLE_SHEET, PROGRAM_SLOT_SHEET,
    "20_EQUIPMENT_REL", CONFLICT_SHEET, "22_DEFERRED_REVIEW", "23_PATCH_LOG",
    "24_INFERENCE_AUDIT", "25_FIELD_DICTIONARY", "26_EXPORT_CONTRACT",
    "27_MIGRATION_NOTES", TIMING_SHEET, BOOTSTRAP_SHEET, DISPLAY_SHEET,
}

TRAINING_ROLES = {
    "STRENGTH", "HYPERTROPHY", "POWER", "PLYOMETRIC", "STABILITY", "MOBILITY",
    "PREHAB", "SKILL_DRILL", "CONDITIONING", "TEST", "RECOVERY",
}
PROGRAM_SLOTS = {
    "MAIN_STRENGTH_SLOT", "SECONDARY_STRENGTH_SLOT", "ACCESSORY_SLOT", "POWER_SLOT",
    "PLYOMETRIC_SLOT", "SPEED_REACTIVE_SLOT", "STABILITY_SLOT",
}
TERM_CATEGORIES = {"ANATOMY", "BIOMECHANICS", "TRAINING", "EQUIPMENT", "BADMINTON", "RECOVERY", "ANALYSIS", "STATUS", "ABBREVIATION", "PRODUCT_TERM", "OTHER"}
SELECTION_POLICIES = {"OFFICIAL_STANDARD", "COMMON_PROFESSIONAL_USAGE", "OFFICIAL_WITH_COMMON_ALIAS", "ESTABLISHED_LOANWORD", "ABBREVIATION_PRESERVED", "PRODUCT_DEFINED", "CONTEXTUAL_PARAPHRASE"}
SOURCE_TIERS = {"TIER_1_OFFICIAL", "TIER_2_SPORTS_SCIENCE", "TIER_3_PROFESSIONAL_USAGE", "PRODUCT_OWNER_DECISION", "EXISTING_APPROVED_LABEL"}
DISPLAY_SCOPES = {"PRODUCTION", "EDITOR_ONLY", "SEARCH_ONLY", "INTERNAL_ONLY", "INTENTIONALLY_HIDDEN"}
REVIEW_STATUSES = {"APPROVED_EXISTING", "APPROVED_RESEARCHED", "PRODUCT_OWNER_APPROVED", "REVIEW_REQUIRED", "INTENTIONALLY_HIDDEN"}
LATIN_TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9]*")
RAW_CANONICAL = re.compile(r"^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$")


def require_unique(rows: list[dict[str, str]], fields: tuple[str, ...], label: str) -> None:
    keys = [tuple(row.get(field, "") for field in fields) for row in rows]
    require(all(key[0] for key in keys), f"Blank primary key in {label}")
    require(len(keys) == len(set(keys)), f"Duplicate primary key in {label}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate(path: Path) -> dict[str, int]:
    workbook = load_workbook(path, read_only=True)
    missing = REQUIRED_SHEETS - set(workbook.sheetnames)
    require(not missing, f"Missing sheets: {sorted(missing)}")

    identities = sheet_rows(workbook, IDENTITY_SHEET)
    identity_by_key = index_by(identities, "stableKey")
    require(len(identities) == 257, f"Expected 257 identities, found {len(identities)}")
    history = [row for row in identities if row["identityStatus"] == HISTORY_ONLY_STATUS]
    selectable = [row for row in identities if row["selectable"] == "YES"]
    require(len(history) == 16, f"Expected 16 history-only identities, found {len(history)}")
    require(len(selectable) == 241, f"Expected 241 selectable identities, found {len(selectable)}")
    require(all(row["selectable"] == "NO" for row in history), "History-only identities must be non-selectable")
    require(all("PRESERVE" in row["historyTreatment"] for row in history), "History-only treatment must preserve history")
    require(not any(row["mappingConfidence"] in DECISION_TOKENS for row in identities), "Decision token found in mappingConfidence")
    require(identity_by_key["ex_bd072cd"]["identityDecisionStatus"] == "KEEP_CANONICAL", "Missing ex_bd072cd decision")
    require(identity_by_key["single_leg_rdl"]["identityDecisionStatus"] == "PROPOSED_USER_APPROVED", "Missing single_leg_rdl decision")
    require(not identity_by_key["ex_bd072cd"]["mappingConfidence"], "ex_bd072cd confidence must remain blank")
    require(not identity_by_key["single_leg_rdl"]["mappingConfidence"], "single_leg_rdl confidence must remain blank")

    bootstrap = sheet_rows(workbook, BOOTSTRAP_SHEET)
    bootstrap_by_key = index_by(bootstrap, "stableKey")
    require(set(bootstrap_by_key) == set(identity_by_key), "Bootstrap keys must exactly match identities")
    for stable_key, row in bootstrap_by_key.items():
        require(row["name"] == identity_by_key[stable_key]["exerciseName"], f"Bootstrap name mismatch: {stable_key}")
        require(row["isCustom"] == "NO", f"Bundled bootstrap row cannot be custom: {stable_key}")
        require(row["isActive"] in {"YES", "NO"}, f"Invalid active flag: {stable_key}")
        require(row["needsReview"] in {"YES", "NO"}, f"Invalid review flag: {stable_key}")
        require(row["estimated1RmEligible"] in {"YES", "NO"}, f"Invalid e1RM flag: {stable_key}")
        require(row["volumeLoadEligible"] in {"YES", "NO"}, f"Invalid volume flag: {stable_key}")
        require(0 <= int(row["defaultRestSeconds"]) <= 3600, f"Bootstrap rest out of range: {stable_key}")
        for field in (
            "familyE1rmMultiplier", "systemicLoadWeight", "neuralHeavyWeight", "neuralSpeedWeight",
            "localLoadWeight", "decelerationWeight", "elasticSscWeight", "rotationPowerWeight",
            "antiRotationWeight", "overheadSwingWeight", "gripLoadWeight",
        ):
            float(row[field])
        if identity_by_key[stable_key]["identityStatus"] == HISTORY_ONLY_STATUS:
            require(row["planningEligibility"] == "HISTORY_ONLY", f"History-only bootstrap row is selectable: {stable_key}")
            require(row["isActive"] == "NO", f"History-only bootstrap row is active: {stable_key}")

    training = sheet_rows(workbook, TRAINING_ROLE_SHEET)
    slots = sheet_rows(workbook, PROGRAM_SLOT_SHEET)
    require_unique(training, ("exerciseStableKey", "trainingRoleCode", "relationScope"), TRAINING_ROLE_SHEET)
    require_unique(slots, ("exerciseStableKey", "capabilityCode", "relationScope"), PROGRAM_SLOT_SHEET)
    require(all(row["trainingRoleCode"] in TRAINING_ROLES for row in training), "Invalid TrainingRole")
    require(all(row["capabilityCode"] in PROGRAM_SLOTS for row in slots), "Invalid ProgramSlotCapability")
    training_keys = {(row["exerciseStableKey"], row["trainingRoleCode"], row["relationScope"]) for row in training}
    slot_keys = {(row["exerciseStableKey"], row["capabilityCode"], row["relationScope"]) for row in slots}
    require(("single_leg_rdl", "STRENGTH", HISTORY_COMPATIBILITY_ONLY) in training_keys, "single_leg_rdl history role missing")
    require(("single_leg_rdl", "MAIN_STRENGTH_SLOT", HISTORY_COMPATIBILITY_ONLY) in slot_keys, "single_leg_rdl history slot missing")
    require(("ex_bd072cd", "ACCESSORY_SLOT", HISTORY_COMPATIBILITY_ONLY) in slot_keys, "ex_bd072cd history slot missing")
    for stable_key in ("dumbbell_single_leg_rdl", "kettlebell_single_leg_rdl"):
        require((stable_key, "MAIN_STRENGTH_SLOT", PRODUCTION_ACTIVE) in slot_keys, f"Active hinge slot missing: {stable_key}")
    for stable_key in ("standing_bodyweight_calf_raise", "standing_calf_raise_machine", "standing_dumbbell_calf_raise"):
        require((stable_key, "ACCESSORY_SLOT", PRODUCTION_ACTIVE) in slot_keys, f"Active calf slot missing: {stable_key}")
    require(("ex_8824026f", "STRENGTH", PRODUCTION_ACTIVE) in training_keys, "One-leg leg curl strength role missing")
    require(("ex_8824026f", "ACCESSORY_SLOT", PRODUCTION_ACTIVE) in slot_keys, "One-leg leg curl accessory slot missing")
    require(not any(key == "ex_8824026f" and value == "PLYOMETRIC" for key, value, _ in training_keys), "One-leg leg curl retains plyometric role")
    require(not any(key == "ex_8824026f" and value == "PLYOMETRIC_SLOT" for key, value, _ in slot_keys), "One-leg leg curl retains plyometric slot")
    require(all(row["exerciseStableKey"] in identity_by_key for row in training + slots), "Orphan role or slot relation")
    require(all(row["relationScope"] in {PRODUCTION_ACTIVE, HISTORY_COMPATIBILITY_ONLY} for row in training + slots), "Invalid relation scope")

    timing = sheet_rows(workbook, TIMING_SHEET)
    timing_by_key = index_by(timing, "stableKey")
    require(len(timing) == len(selectable), f"Expected {len(selectable)} timing rows, found {len(timing)}")
    require(set(timing_by_key) == {row["stableKey"] for row in selectable}, "Timing keys must exactly match selectable identities")
    require(not set(timing_by_key).intersection(row["stableKey"] for row in history), "History-only timing profile found")
    for identity in selectable:
        stable_key = identity["stableKey"]
        require(int(timing_by_key[stable_key]["defaultRestSeconds"]) == int(bootstrap_by_key[stable_key]["defaultRestSeconds"]), f"Rest parity mismatch: {stable_key}")
        require(0 <= int(timing_by_key[stable_key]["defaultRestSeconds"]) <= 3600, f"Rest out of range: {stable_key}")

    display_sheet = workbook[DISPLAY_SHEET]
    display_headers = [str(cell.value or "").strip() for cell in display_sheet[1]]
    require(display_headers == DISPLAY_HEADERS, f"Invalid {DISPLAY_SHEET} headers")
    display_rows = sheet_rows(workbook, DISPLAY_SHEET)
    require_unique(display_rows, ("displayField", "canonicalCode"), DISPLAY_SHEET)
    require(all(row["canonicalCode"] for row in display_rows), f"Blank canonicalCode in {DISPLAY_SHEET}")
    expected_production = set(collect_inventory())
    actual_production = {
        (row["displayField"], row["canonicalCode"])
        for row in display_rows
        if row["displayScope"] == "PRODUCTION"
    }
    require(actual_production == expected_production, f"Display coverage mismatch: missing={sorted(expected_production - actual_production)[:10]}, orphan={sorted(actual_production - expected_production)[:10]}")
    for row in display_rows:
        key = f"{row['displayField']}|{row['canonicalCode']}"
        require(row["termCategory"] in TERM_CATEGORIES, f"Invalid termCategory: {key}")
        require(row["selectionPolicy"] in SELECTION_POLICIES, f"Invalid selectionPolicy: {key}")
        require(row["sourceTier"] in SOURCE_TIERS, f"Invalid sourceTier: {key}")
        require(row["displayScope"] in DISPLAY_SCOPES, f"Invalid displayScope: {key}")
        require(row["reviewStatus"] in REVIEW_STATUSES, f"Invalid reviewStatus: {key}")
        if row["displayScope"] == "PRODUCTION":
            require(bool(row["koreanLabel"]), f"Blank Korean production label: {key}")
            require(bool(row["englishLabel"]), f"Blank English production label: {key}")
            require(bool(row["sourceReferences"]), f"Blank source reference: {key}")
            require(bool(row["selectionRationale"]), f"Blank selection rationale: {key}")
            require(not RAW_CANONICAL.fullmatch(row["koreanLabel"]), f"Raw canonical Korean label: {key}")
        latin = set(LATIN_TOKEN.findall(row["koreanLabel"]))
        allowed = {token for token in row["allowedLatinTokens"].split("|") if token}
        require(latin == allowed, f"Latin token allowlist mismatch: {key}, label={sorted(latin)}, allowed={sorted(allowed)}")
    e1rm = next((row for row in display_rows if row["displayField"] == "PROGRESS_METRIC" and row["canonicalCode"] == "ESTIMATED_1RM"), None)
    require(e1rm is not None, "Missing e1RM display row")
    require(e1rm["koreanLabel"] == "e1RM", "e1RM Korean label must remain exact")
    require(e1rm["selectionPolicy"] == "ABBREVIATION_PRESERVED", "e1RM selection policy mismatch")
    require(e1rm["sourceTier"] == "PRODUCT_OWNER_DECISION", "e1RM source tier mismatch")
    require(e1rm["allowedLatinTokens"] == "e1RM", "e1RM Latin allowlist mismatch")
    require(e1rm["reviewStatus"] == "PRODUCT_OWNER_APPROVED", "e1RM review status mismatch")

    relation_primary_keys = {
        "09_MOVEMENT_REL": ("relationId",),
        "10_MUSCLE_REL": ("relationKey",),
        "11_TISSUE_LOAD_REL": ("relationKey",),
        "12_OFI_REL": ("relationKey",),
        "13_RECOVERY_REL": ("exerciseStableKey",),
        "14_BADMINTON_REL": ("relationKey",),
        "15_PROGRESSION": ("relationKey",),
        "17_STRENGTH_PROXY": ("relationId",),
        "20_EQUIPMENT_REL": ("exerciseStableKey", "groupId", "memberOrder"),
    }
    for sheet_name, primary_key in relation_primary_keys.items():
        rows = sheet_rows(workbook, sheet_name)
        require_unique(rows, primary_key, sheet_name)
        if "exerciseStableKey" in rows[0]:
            require(all(row["exerciseStableKey"] in identity_by_key for row in rows), f"Orphan relation in {sheet_name}")

    conflicts = sheet_rows(workbook, CONFLICT_SHEET)
    scope = [row for row in conflicts if row["recordType"] == "SCOPE_STATUS"]
    require(len(scope) == 1, "Exactly one conflict scope row is required")
    require(scope[0]["adjudicationStatus"] == "NOT_ADJUDICATED", "Conflict scope must remain NOT_ADJUDICATED")
    require("not adjudicated" in scope[0]["notes"].lower(), "Conflict scope note must avoid pass claims")

    for sheet in workbook.worksheets:
        for row in sheet.iter_rows():
            for cell in row:
                if isinstance(cell.value, str) and cell.value.startswith("#") and cell.value in {"#REF!", "#DIV/0!", "#VALUE!", "#NAME?", "#N/A"}:
                    raise ValueError(f"Formula error in {sheet.title}!{cell.coordinate}: {cell.value}")

    return {
        "identityRows": len(identities),
        "selectableIdentityRows": len(selectable),
        "historyOnlyIdentityRows": len(history),
        "trainingRoleRows": len(training),
        "programSlotRows": len(slots),
        "timingRows": len(timing),
        "bootstrapRows": len(bootstrap),
        "displayRows": len(display_rows),
        "productionDisplayRows": len(actual_production),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", type=Path, required=True)
    args = parser.parse_args()
    counts = validate(args.workbook)
    print("Authority workbook valid: " + ", ".join(f"{key}={value}" for key, value in counts.items()))


if __name__ == "__main__":
    main()
