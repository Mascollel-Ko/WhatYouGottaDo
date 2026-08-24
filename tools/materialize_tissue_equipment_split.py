#!/usr/bin/env python3
"""Materialize approved equipment-split tissue profiles by exact stable key."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app/src/main/assets/metadata/tissue_load_v1"
SPLIT_PLAN = ROOT / "outputs/final_closeout/equipment_variant_split_plan.csv"
BASELINE_COMMIT = "d3be2a9af81bc42b8733fd953cc2cdc770be186b"
PROVENANCE = "MATERIALIZED_FROM_APPROVED_EQUIPMENT_SPLIT"

AUTHORITY = ASSET_DIR / "tissue_rcv_exercise_load_unit_authority_v1.csv"
INDEX = ASSET_DIR / "tissue_rcv_exercise_index_v1.csv"
PROTOCOLS = ASSET_DIR / "tissue_rcv_exercise_protocols_v1.csv"
PROTOCOL_CLASSES = ASSET_DIR / "tissue_rcv_protocol_classes_v1.csv"
DI_PROFILES = ASSET_DIR / "tissue_rcv_di_profiles_v1.csv"
DOSE_PROFILES = ASSET_DIR / "tissue_rcv_exercise_dose_profiles_v1.csv"
MANIFEST = ASSET_DIR / "tissue_rcv_asset_manifest_v1.csv"

INVERTED_ROW_FACTORS = {
    "suspension_trainer_inverted_row": (0.60, 1.0),
    "gymnastic_ring_inverted_row": (0.60, 1.0),
    "one_arm_suspension_trainer_row": (0.60, 1.0),
    "one_arm_gymnastic_ring_row": (0.60, 1.0),
}
BODYWEIGHT_FACTORS = {
    "standing_bodyweight_calf_raise": (1.0, 0.0),
    **INVERTED_ROW_FACTORS,
}
CARRY_KEYS = {"dumbbell_farmer_carry", "kettlebell_farmer_carry"}


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def csv_text(fieldnames: list[str], rows: list[dict[str, str]], quoting: int = csv.QUOTE_MINIMAL) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n", quoting=quoting)
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def write_rows(path: Path, rows: list[dict[str, str]], quoting: int = csv.QUOTE_MINIMAL) -> None:
    if not rows:
        raise ValueError(f"Refusing to write an empty table: {path}")
    path.write_text(csv_text(list(rows[0]), rows, quoting), encoding="utf-8", newline="")


def target_dose(stable_key: str) -> tuple[str, str, str, str, str, str]:
    if stable_key in BODYWEIGHT_FACTORS:
        bodyweight, added = BODYWEIGHT_FACTORS[stable_key]
        return (
            "BODYWEIGHT_REPETITION",
            "DI_BODYWEIGHT_REP",
            "effective bodyweight kg-repetitions",
            "(bodyWeightKg * bodyweightFactor + recordedWeightKg * addedLoadFactor) * reps",
            f"{bodyweight:.2f}",
            f"{added:.2f}",
        )
    if stable_key in CARRY_KEYS:
        return (
            "LOAD_TIME",
            "DI_LOAD_TIME",
            "kg-seconds",
            "recordedWeightKg * confirmedSeconds; no distance or per-hand multiplier",
            "",
            "",
        )
    return (
        "WEIGHTED_REPETITION",
        "DI_WEIGHTED_REP",
        "external load kg-repetitions",
        "recordedWeightKg * reps",
        "",
        "",
    )


def materialize() -> dict[Path, str]:
    plan = read_rows(SPLIT_PLAN)
    if len(plan) != 34:
        raise ValueError(f"Expected 34 approved split targets, found {len(plan)}")
    for row in plan:
        if row["analysisMetadataInheritance"] != "MUSCLE_TISSUE_OFI_INHERITED_UNCHANGED_FROM_GENERIC_SOURCE":
            raise ValueError(f"Unapproved inheritance for {row['variantStableKey']}")
        if row["decisionStatus"] != "USER_APPROVED_APPLIED" or row["selectable"] != "YES":
            raise ValueError(f"Split target is not approved/selectable: {row['variantStableKey']}")

    target_keys = {row["variantStableKey"] for row in plan}
    if len(target_keys) != 34:
        raise ValueError("Approved split plan contains duplicate target stable keys")
    if len(BODYWEIGHT_FACTORS) != 5 or len(CARRY_KEYS) != 2:
        raise AssertionError("Exact dose classification constants changed unexpectedly")
    weighted_keys = target_keys - BODYWEIGHT_FACTORS.keys() - CARRY_KEYS
    if len(weighted_keys) != 27:
        raise ValueError(f"Expected 27 weighted targets, found {len(weighted_keys)}")

    authority_base = [row for row in read_rows(AUTHORITY) if row["exerciseStableKey"] not in target_keys]
    index_base = [row for row in read_rows(INDEX) if row["exerciseStableKey"] not in target_keys]
    protocol_base = [row for row in read_rows(PROTOCOLS) if row["exerciseStableKey"] not in target_keys]
    authority_by_source: dict[str, list[dict[str, str]]] = {}
    for row in authority_base:
        authority_by_source.setdefault(row["exerciseStableKey"], []).append(row)
    index_by_source = {row["exerciseStableKey"]: row for row in index_base}
    protocol_by_source = {row["exerciseStableKey"]: row for row in protocol_base}

    next_number = max(int(float(row["번호"])) for row in index_base) + 1
    new_authority: list[dict[str, str]] = []
    new_index: list[dict[str, str]] = []
    new_protocols: list[dict[str, str]] = []
    new_dose_profiles: list[dict[str, str]] = []
    for offset, split in enumerate(sorted(plan, key=lambda row: row["variantStableKey"])):
        source_key = split["sourceGenericStableKey"]
        target_key = split["variantStableKey"]
        target_name = split["variantExerciseName"]
        source_authority = authority_by_source.get(source_key)
        source_index = index_by_source.get(source_key)
        source_protocol = protocol_by_source.get(source_key)
        if not source_authority or source_index is None or source_protocol is None:
            raise ValueError(f"Missing exact source tissue authority: {source_key}")

        dose_basis, di_profile, dose_unit, dose_rule, bodyweight, added = target_dose(target_key)
        number = str(next_number + offset)
        for source_row in source_authority:
            row = dict(source_row)
            row.update(
                {
                    "번호": number,
                    "운동명": target_name,
                    "exerciseStableKey": target_key,
                    "DoseBasis": dose_basis,
                    "DoseUnit": dose_unit,
                    "DoseRule": dose_rule,
                    "bodyWeightCoefficient": bodyweight,
                    "transferSourceExerciseKey": source_key,
                    "transferSourceExerciseName": split["sourceExerciseName"],
                    "stableKeyMappingStatus": PROVENANCE,
                }
            )
            new_authority.append(row)

        index_row = dict(source_index)
        index_row.update(
            {
                "번호": number,
                "exerciseStableKey": target_key,
                "운동명": target_name,
                "equipmentTags": split["atomicEquipmentCodes"],
                "scoreRowCount": str(len(source_authority)),
                "DoseBasis": dose_basis,
                "DoseUnit": dose_unit,
                "DoseRule": dose_rule,
            }
        )
        new_index.append(index_row)

        protocol_row = dict(source_protocol)
        protocol_row.update(
            {
                "exerciseStableKey": target_key,
                "운동명": target_name,
                "diProfileId": di_profile,
                "scoreAuthority": f"{PROVENANCE}:{source_key}",
            }
        )
        new_protocols.append(protocol_row)

        new_dose_profiles.append(
            {
                "exerciseStableKey": target_key,
                "doseKind": dose_basis,
                "bodyweightFactor": bodyweight,
                "addedLoadFactor": added,
                "loadSemantics": dose_rule,
                "compatibilityMode": "EXACT_STABLE_KEY_ONLY",
                "sourceStableKey": source_key,
                "provenance": PROVENANCE,
            }
        )

    authority_rows = authority_base + new_authority
    index_rows = index_base + new_index
    protocol_rows = protocol_base + new_protocols
    if len(new_authority) != 427:
        raise ValueError(f"Expected 427 materialized authority rows, found {len(new_authority)}")

    di_rows = [row for row in read_rows(DI_PROFILES) if row["diProfileId"] != "DI_LOAD_TIME"]
    di_rows.append(
        {
            "diProfileId": "DI_LOAD_TIME",
            "Dose basis": "LOAD_TIME",
            "필수 입력": "loadKg|activeSeconds",
            "선택 입력": "setRPE",
            "D_raw 공식": "recorded loadKg * confirmed activeSeconds",
            "I 우선순위": "setRPE|sessionRPE",
            "I 규칙": "Use recorded effort once; never infer distance or double a per-hand load.",
            "P 역할": "HOLD_CYCLE",
        }
    )

    class_rows = read_rows(PROTOCOL_CLASSES)
    class_counts: dict[str, int] = {}
    for row in protocol_rows:
        class_counts[row["defaultProtocolClass"]] = class_counts.get(row["defaultProtocolClass"], 0) + 1
    for row in class_rows:
        row["운동 수"] = str(class_counts.get(row["protocolClass"], 0))

    generated = {
        AUTHORITY: csv_text(list(authority_rows[0]), authority_rows),
        INDEX: csv_text(list(index_rows[0]), index_rows),
        PROTOCOLS: csv_text(list(protocol_rows[0]), protocol_rows),
        PROTOCOL_CLASSES: csv_text(list(class_rows[0]), class_rows, csv.QUOTE_ALL),
        DI_PROFILES: csv_text(list(di_rows[0]), di_rows, csv.QUOTE_ALL),
        DOSE_PROFILES: csv_text(list(new_dose_profiles[0]), new_dose_profiles),
    }

    manifest_rows = [row for row in read_rows(MANIFEST) if row["assetName"] != DOSE_PROFILES.name]
    manifest_by_name = {row["assetName"]: row for row in manifest_rows}
    for path, content in generated.items():
        digest = hashlib.sha256(content.replace("\r\n", "\n").replace("\r", "\n").encode()).hexdigest()
        existing = manifest_by_name.get(path.name)
        if existing is None:
            existing = {
                "assetName": path.name,
                "sourceSheet": "APPROVED_EQUIPMENT_SPLIT",
                "sourceVersion": "FINAL-CLOSEOUT-2026-08-04",
                "sourceWorkbookSha256": hashlib.sha256(SPLIT_PLAN.read_bytes()).hexdigest(),
                "rowCount": "",
                "assetSha256": "",
                "baselineCommit": BASELINE_COMMIT,
                "stateIdentity": "loadUnitStableKey|loadDimension|UNSIDED",
            }
            manifest_rows.append(existing)
            manifest_by_name[path.name] = existing
        existing["rowCount"] = str(content.count("\n") - 1)
        existing["assetSha256"] = digest
    generated[MANIFEST] = csv_text(list(manifest_rows[0]), manifest_rows)
    return generated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated = materialize()
    if args.check:
        mismatches = [
            str(path.relative_to(ROOT))
            for path, content in generated.items()
            if not path.exists() or path.read_bytes().decode("utf-8") != content
        ]
        if mismatches:
            raise SystemExit("Generated tissue assets are stale: " + ", ".join(mismatches))
        return
    for path, content in generated.items():
        path.write_text(content, encoding="utf-8", newline="")


if __name__ == "__main__":
    main()
