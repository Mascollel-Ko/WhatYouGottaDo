"""Apply a reviewed 762-row muscle-role proposal to the XLSX authoring source.

Deliberately NOT part of production builds: it produces a review branch only.
Source input is a row-keyed audit, not inferred from a generated CSV modification.
Use --apply only when the branch is intended for inspection; a PR review must
separately authorize merging semantic changes.
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path

from authority_common import load_workbook, text

REPO = Path(__file__).resolve().parents[2]
DEFAULT_WORKBOOK = REPO / "docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx"
AUDIT = REPO / "docs/audits/canonical_muscle_role_762_review_20261019.csv"
ROLE_COEFFICIENT = {
    "PRIMARY": "1",
    "SECONDARY": "0.5",
    "STABILIZER": "0",
    "NOT_SUPPORTED": "0",
    "GENERAL_PARTICIPATION": "0",
    "REVIEW_REQUIRED": "0",
}

def apply(workbook_path: Path, audit_path: Path, dry_run: bool = False) -> dict:
    with audit_path.open(encoding="utf-8-sig", newline="") as stream:
        proposals = list(csv.DictReader(stream))
    if len(proposals) != 762:
        raise ValueError(f"Audit contract expected 762 reviewed source rows; got {len(proposals)}")
    by_id = {r["relationKey"]: r for r in proposals}
    if len(by_id) != len(proposals):
        raise ValueError("Duplicate audit IDs")
    wb = load_workbook(workbook_path)
    ws = wb["10_MUSCLE_REL"]
    headers = {text(c.value): c.column for c in ws[1] if c.value is not None}
    required = ("relationKey", "exerciseStableKey", "muscleCode",
        "muscleRelationType", "coefficient", "status", "provenance")
    if not set(required) <= set(headers):
        raise ValueError("Muscle authoring sheet schema changed")
    existing = set()
    changed = 0
    status = {"PRIMARY": 0, "SECONDARY": 0, "STABILIZER": 0,
              "NOT_SUPPORTED": 0, "GENERAL_PARTICIPATION": 0, "REVIEW_REQUIRED": 0}
    for row in ws.iter_rows(min_row=2):
        def cell(key):
            return row[headers[key] - 1]
        key = text(cell("relationKey").value)
        if not key:
            continue
        if key not in by_id or key in existing:
            raise ValueError(f"Missing/duplicate relation ID: {key}")
        existing.add(key)
        proposal = by_id[key]
        if text(cell("exerciseStableKey").value) != proposal["exerciseStableKey"]:
            raise ValueError(f"Identity drift for {key}")
        if text(cell("muscleCode").value) != proposal["currentMuscleCode"]:
            raise ValueError(f"Muscle drift for {key}")
        if text(cell("muscleRelationType").value) != proposal["currentRole"]:
            raise ValueError(f"Role drift for {key}")
        if text(cell("coefficient").value) != proposal["currentCoefficient"]:
            raise ValueError(f"Coefficient drift for {key}")
        proposed_role = proposal["proposedRole"]
        if proposed_role not in ROLE_COEFFICIENT:
            raise ValueError(f"Unknown proposed role for {key}")
        if ROLE_COEFFICIENT[proposed_role] != proposal["proposedCoefficient"]:
            raise ValueError(f"Incorrect target coefficient for {key}")
        if proposal["decisionStatus"] not in ("MECHANICS_RULE_PROPOSAL", "NEEDS_HUMAN_REVIEW"):
            raise ValueError(f"Unreviewed decision status for {key}")
        status[proposed_role] += 1
        if (proposal["currentRole"],proposal["currentCoefficient"]) != (
            proposed_role,proposal["proposedCoefficient"]
        ):
            changed += 1
            if not dry_run:
                cell("muscleRelationType").value = proposed_role
                cell("coefficient").value = proposal["proposedCoefficient"]
                cell("status").value = (
                    "REVIEW_REQUIRED" if proposed_role == "REVIEW_REQUIRED" else
                    "PROVISIONAL_MECHANICS_V1"
                )
                cell("provenance").value = (
                    text(cell("provenance").value) +
                    "; ANATOMICAL_ROLE_REVIEW_PROPOSAL_2026-10-19"
                )
    if existing != set(by_id) or changed != 531:
        raise ValueError(f"Audit coverage or semantic count mismatch: {len(existing)}, {changed}")
    if not dry_run:
        wb.save(workbook_path)
    return {"source_rows":len(existing),"changed":changed,"target_roles":status,
            "dry_run":dry_run,"warning":"PROVISIONAL; not a standalone physiological dose authority"}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook",type=Path,default=DEFAULT_WORKBOOK)
    parser.add_argument("--audit",type=Path,default=AUDIT)
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    print(apply(args.workbook,args.audit,dry_run=not args.apply))
