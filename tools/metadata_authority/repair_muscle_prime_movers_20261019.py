"""Precisely repair omitted primary movers in the in-review muscle authority workbook.

This is a targeted additive repair to the existing PR #28 proposal. It does not
rerun or replace the original 531 row-level reclassification proposals.
Expect exactly sixteen new muscle relations, two reviewed updates and seven
bootstrap corrections. Only source-authored workbook rows are modified.
"""
from __future__ import annotations

import csv
from pathlib import Path

from authority_common import BOOTSTRAP_SHEET, IDENTITY_SHEET, load_workbook, sheet_rows, text

ROOT = Path(__file__).resolve().parents[2]
WORKBOOK = ROOT / "docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx"
PATCH = ROOT / "docs/audits/muscle_role_targeted_repairs_20261019.csv"
ROLE_WEIGHT = {"PRIMARY": "1", "SECONDARY": "0.5"}

# Update only the seven demonstrably erroneous muscle-list display records.
BOOTSTRAP_FIXES = {
    "dumbbell_goblet_squat": ("QUADRICEPS,GLUTE", "CORE,DEEP_CORE"),
    "kettlebell_goblet_squat": ("QUADRICEPS,GLUTE", "CORE,DEEP_CORE"),
    "ex_85f12271": ("QUADRICEPS,GLUTE", "CORE,DEEP_CORE"),
    "ex_d67e5761": ("QUADRICEPS,GLUTE", "CALF,CORE"),
    "ex_a611010d": ("HIP_ADDUCTOR,QUADRICEPS", "GLUTE,CORE"),
    "ex_69a56484": ("QUADRICEPS", "GLUTE,CORE"),
    "ex_7404067c": ("GLUTE,HAMSTRING", "CORE,DEEP_CORE"),
}
EXPECTED_BOOTSTRAP = {
    "dumbbell_goblet_squat": ("CORE,DEEP_CORE", "DEEP_CORE"),
    "kettlebell_goblet_squat": ("CORE,DEEP_CORE", "DEEP_CORE"),
    "ex_85f12271": ("CORE", "DEEP_CORE"),
    "ex_d67e5761": ("CALF", "CORE"),
    "ex_a611010d": ("HIP_ADDUCTOR", "CORE"),
    "ex_69a56484": ("GLUTE", "CORE"),
    "ex_7404067c": ("CORE,DEEP_CORE", "DEEP_CORE"),
}

def apply(*, dry_run: bool = True) -> dict:
    wb = load_workbook(WORKBOOK)
    identities = {row["stableKey"]: row for row in sheet_rows(wb, IDENTITY_SHEET)}
    ws = wb["10_MUSCLE_REL"]
    headers = [text(c.value) for c in ws[1]]
    ix = {h: i for i, h in enumerate(headers)}
    existing = {}
    keys = set()
    for row in ws.iter_rows(min_row=2):
        rid = text(row[ix["relationKey"]].value)
        if not rid: continue
        if rid in keys: raise ValueError(f"Duplicate original relation key: {rid}")
        keys.add(rid)
        pair = (text(row[ix["exerciseStableKey"]].value), text(row[ix["muscleCode"]].value))
        if pair in existing: raise ValueError(f"Duplicate original exercise/muscle: {pair}")
        existing[pair] = row
    if len(existing) != 762: raise ValueError(f"Expected 762 PR #28 proposal rows; found {len(existing)}")
    with PATCH.open(encoding="utf-8-sig", newline="") as f:
        repairs = list(csv.DictReader(f))
    if len(repairs) != 18 or sum(x["action"] == "ADD" for x in repairs) != 16:
        raise ValueError("Unexpected targeted repair contract")
    if len({(r["exerciseStableKey"], r["muscleCode"]) for r in repairs}) != 18:
        raise ValueError("Duplicate targeted repair")
    appended = 0
    updated = 0
    for index, repair in enumerate(repairs, start=1):
        key = repair["exerciseStableKey"]
        old = repair["muscleCode"]
        new = repair["targetMuscleCode"]
        role = repair["targetRole"]
        coefficient = repair["targetCoefficient"]
        if key not in identities or identities[key]["selectable"] != "YES":
            raise ValueError(f"Repair targets non-selectable identity: {key}")
        if role not in ROLE_WEIGHT or ROLE_WEIGHT[role] != coefficient:
            raise ValueError(f"Invalid reviewed role weight: {key}/{new}")
        if not repair["rationale"].strip():
            raise ValueError(f"Missing biomechanical rationale: {key}/{new}")
        old_row = existing.get((key, old))
        target_row = existing.get((key, new))
        if repair["action"] == "UPDATE":
            if old_row is None: raise ValueError(f"Missing update source: {key}/{old}")
            if target_row is not None and target_row is not old_row:
                raise ValueError(f"Duplicate new role target: {key}/{new}")
            if text(old_row[ix["muscleRelationType"]].value) != repair["expectedRole"]:
                raise ValueError(f"Source role drift: {key}/{old}")
            updated += 1
            if not dry_run:
                old_row[ix["muscleCode"]].value = new
                old_row[ix["muscleRelationType"]].value = role
                old_row[ix["coefficient"]].value = coefficient
                old_row[ix["status"]].value = "TARGETED_PRIME_MOVER_FIX_20261019"
                old_row[ix["provenance"]].value = (
                    text(old_row[ix["provenance"]].value) + "; " + repair["rationale"]
                )
        elif repair["action"] == "ADD":
            if old != new or old_row is not None or repair["expectedRole"] != "":
                raise ValueError(f"Not a missing muscle relation: {key}/{old}")
            rid = f"MUSCLE_PR28_REPAIR_{appended + 1:03d}"
            if rid in keys: raise ValueError(f"Relation id collision: {rid}")
            keys.add(rid)
            appended += 1
            if not dry_run:
                values = dict.fromkeys(headers, "")
                values.update({
                    "relationKey": rid,
                    "exerciseStableKey": key,
                    "exerciseName": identities[key]["exerciseName"],
                    "muscleCode": new,
                    "muscleRelationType": role,
                    "qualifier": "",
                    "coefficient": coefficient,
                    "confidence": "1",
                    "sourceStatus": "TARGETED_PRIME_MOVER_FIX",
                    "status": "TARGETED_PRIME_MOVER_FIX_20261019",
                    "sourceStableKey": key,
                    "provenance": repair["rationale"] + "; reviewed mechanical action, PR28 repair",
                })
                ws.append([values[h] for h in headers])
        else:
            raise ValueError(f"Unknown repair action: {repair['action']}")
    if appended != 16 or updated != 2:
        raise ValueError(f"Unexpected write counts: {appended} new, {updated} updated")
    bootstrap = wb[BOOTSTRAP_SHEET]
    bh = {text(cell.value): cell.column for cell in bootstrap[1]}
    repaired_bootstrap = set()
    for row in bootstrap.iter_rows(min_row=2):
        key = text(row[bh["stableKey"] - 1].value)
        if key not in BOOTSTRAP_FIXES: continue
        current = tuple(text(row[bh[name]-1].value) for name in ("primaryMuscles","secondaryMuscles"))
        if current != EXPECTED_BOOTSTRAP[key]:
            raise ValueError(f"Bootstrap data drift: {key}, {current}")
        repaired_bootstrap.add(key)
        if not dry_run:
            for name, target in zip(("primaryMuscles", "secondaryMuscles"), BOOTSTRAP_FIXES[key]):
                row[bh[name]-1].value = target
    if repaired_bootstrap != set(BOOTSTRAP_FIXES):
        raise ValueError(f"Missing intended bootstrap entries: {set(BOOTSTRAP_FIXES) - repaired_bootstrap}")
    if not dry_run:
        wb.save(WORKBOOK)
    return {
        "original_rows_retained": 762,
        "appended_missing_relations": appended,
        "updated_wrong_roles": updated,
        "corrected_bootstrap_identities": len(repaired_bootstrap),
        "resulting_relation_rows": 762 + appended,
        "dry_run": dry_run,
    }

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    print(apply(dry_run=not args.apply))
