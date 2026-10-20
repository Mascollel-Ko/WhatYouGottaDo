"""Guarded, additive correction of confirmed muscle relations and legacy UI/analysis tokens.

Do not run against a different workbook revision: every touched cell is asserted.
Only two missing relations are added. Already valid PR #28 rows are preserved.
"""
from __future__ import annotations
import argparse
from pathlib import Path
from authority_common import load_workbook, BOOTSTRAP_SHEET, text

ROOT = Path(__file__).resolve().parents[2]
WORKBOOK = ROOT / "docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx"
ADDS = {
    "ex_ee378da7": ("스케이터 스쿼트", "QUADS", "Single-leg knee extension in skater squat"),
    "ex_6232f4bc": ("박스 스텝업", "QUADS", "Loaded knee extension during step-up"),
}
# expected(old) -> reviewed(new). The two UI muscle fields also feed legacy analysis.
PATCH = {
    "ex_ee378da7": {"primaryMuscles": ("GLUTE", "QUADRICEPS,GLUTE")},
    "ex_6232f4bc": {"primaryMuscles": ("GLUTE,DEEP_CORE", "QUADRICEPS,GLUTE")},
    "ex_eb636bac": {
        "primaryMuscles": ("BACK,TRICEPS,FOREARM,CORE", "TRICEPS"),
        "secondaryMuscles": ("FOREARM,CORE", ""),
        "hypertrophyVolumeGroup": ("BACK", "ARMS"),
    },
    "ex_bb4b4276": {
        "primaryMuscles": ("BACK,ANTERIOR_DELTOID,LATERAL_DELTOID,SHOULDER,SCAPULAR_STABILIZERS,TRICEPS,CORE", "SHOULDER"),
        "secondaryMuscles": ("DEEP_CORE", "TRICEPS"),
        "hypertrophyVolumeGroup": ("BACK", "SHOULDERS"),
    },
    "ex_1dbee10e": {
        "primaryMuscles": ("CHEST,BACK,ANTERIOR_DELTOID,SCAPULAR_STABILIZERS,TRICEPS", "CHEST"),
        "secondaryMuscles": ("CORE", "SHOULDER,TRICEPS"),
    },
    "ex_4aa3725a": {
        "primaryMuscles": ("BACK,ANTERIOR_DELTOID,LATERAL_DELTOID,SHOULDER,SCAPULAR_STABILIZERS,TRICEPS,CORE,DEEP_CORE", "SHOULDER"),
        "secondaryMuscles": ("DEEP_CORE", "TRICEPS"),
        "hypertrophyVolumeGroup": ("BACK", "SHOULDERS"),
    },
    "ex_c5043892": {
        "primaryMuscles": ("BACK,SHOULDER,QUADRICEPS,GLUTE,CORE", "QUADRICEPS,GLUTE"),
        "secondaryMuscles": ("DEEP_CORE", ""),
        "hypertrophyVolumeGroup": ("BACK", "QUADS"),
    },
    "ex_da56a4fe": {
        "primaryMuscles": ("BACK,REAR_DELT,TRICEPS,CORE", "TRICEPS"),
        "secondaryMuscles": ("DEEP_CORE", ""),
        "hypertrophyVolumeGroup": ("BACK", "ARMS"),
    },
    "dumbbell_goblet_squat": {"hypertrophyVolumeGroup": ("CORE", "QUADS")},
    "kettlebell_goblet_squat": {"hypertrophyVolumeGroup": ("CORE", "QUADS")},
    "ex_85f12271": {"hypertrophyVolumeGroup": ("CORE", "QUADS")},
    "ex_d67e5761": {"hypertrophyVolumeGroup": ("CALVES", "QUADS")},
    "ex_b69ab702": {"hypertrophyVolumeGroup": ("CHEST", "ARMS")},
    "ex_e159d15a": {"hypertrophyVolumeGroup": ("CHEST", "BACK")},
    "chest_supported_row_machine": {"hypertrophyVolumeGroup": ("CHEST", "BACK")},
    "dumbbell_chest_supported_row": {"hypertrophyVolumeGroup": ("CHEST", "BACK")},
    "ex_6a966452": {"hypertrophyVolumeGroup": ("CHEST", "BACK")},
}

def apply(dry_run=True):
    wb = load_workbook(WORKBOOK)
    ws = wb["10_MUSCLE_REL"]
    cols = {text(c.value): c.column - 1 for c in ws[1]}
    seen = {}
    keys = set()
    for row in ws.iter_rows(min_row=2):
        rid = text(row[cols["relationKey"]].value)
        if not rid: continue
        pair = (text(row[cols["exerciseStableKey"]].value), text(row[cols["muscleCode"]].value))
        if rid in keys or pair in seen: raise ValueError(f"Duplicate muscle identity {rid}: {pair}")
        keys.add(rid)
        seen[pair] = row
    if len(seen) != 778: raise ValueError(f"Expected 778 prior relations, got {len(seen)}")
    if set(ADDS) & {key for key,code in seen if (key, "QUADS") in seen}:
        raise ValueError("Missing-quads repair already present or source drifted")
    for index, (key, (name, muscle, rationale)) in enumerate(ADDS.items(), 1):
        if (key, muscle) in seen: raise ValueError(f"Muscle already registered: {key}/{muscle}")
        if text(seen[(key,"GLUTES")][cols["muscleRelationType"]].value) != "PRIMARY":
            raise ValueError(f"Expected existing glute PRIMARY: {key}")
        rid = f"MUSCLE_20261020_REPAIR_{index:03}"
        if rid in keys: raise ValueError(f"Relation ID already used {rid}")
        values = dict.fromkeys(cols, "")
        values.update(relationKey=rid,exerciseStableKey=key,exerciseName=name,
            muscleCode=muscle,muscleRelationType="PRIMARY",qualifier="",coefficient="1",
            confidence="1",sourceStatus="TARGETED_PRIME_MOVER_FIX",status="TARGETED_PRIME_MOVER_FIX_20261020",
            sourceStableKey=key,provenance=rationale + "; separately audited targeted addition")
        if not dry_run: ws.append([values[k] for k in cols])
    boot = wb[BOOTSTRAP_SHEET]
    bc = {text(c.value): c.column-1 for c in boot[1]}
    visited=set()
    for row in boot.iter_rows(min_row=2):
        key=text(row[bc["stableKey"]].value)
        if key not in PATCH: continue
        visited.add(key)
        for field,(expected,replacement) in PATCH[key].items():
            current = text(row[bc[field]].value)
            if current != expected:
                raise ValueError(f"Unexpected bootstrap value {key}.{field}: {current!r} expected {expected!r}")
            if not dry_run: row[bc[field]].value = replacement
    if visited != set(PATCH): raise ValueError(f"Missing bootstrap identities: {set(PATCH)-visited}")
    if not dry_run: wb.save(WORKBOOK)
    return {"original_relations":778,"added_relations":len(ADDS),"bootstrap_rows_touched":len(PATCH),"dry_run":dry_run}

if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args=parser.parse_args()
    print(apply(dry_run=not args.apply))
