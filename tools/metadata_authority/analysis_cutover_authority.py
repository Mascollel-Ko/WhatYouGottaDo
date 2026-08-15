from __future__ import annotations

import csv
from pathlib import Path

from authority_common import sha256, write_csv


ROOT = Path(__file__).resolve().parents[2]
CORE_APPROVED_SOURCE = (
    ROOT / "docs/metadata_authority/core_training_classification_review_2026-08-13.csv"
)
CORE_APPROVED_SHA256 = "3c819568012cd17726486e7f3e21cac972c95eec1736e8ab038e9edc1c3fa954"
BADMINTON_OBJECTIVE_AUTHORITY_SOURCE = (
    ROOT / "docs/metadata_authority/badminton_objective_relations_v2_authority.csv"
)
BADMINTON_OBJECTIVE_AUTHORITY_SHA256 = "bbd4277111e52fc37a09840ebe41ef0dbe91347b9d17bbff6b4dac9a4cf47a56"
USER_APPROVED_BADMINTON_OBJECTIVE_PROVENANCE = (
    "USER_APPROVED_BADMINTON_OBJECTIVE_2026_08_14"
)

CORE_HEADERS = [
    "relationId",
    "exerciseStableKey",
    "relationType",
    "relationValue",
    "sourceRowOrder",
    "provenance",
    "reviewStatus",
]
BADMINTON_OBJECTIVE_HEADERS = [
    "relationId",
    "exerciseStableKey",
    "objectiveId",
    "transferLevel",
    "provenance",
    "evidenceRelationKeys",
    "reviewStatus",
    "reviewReason",
]
ROTATION_AUDIT_HEADERS = [
    "exerciseStableKey",
    "coreDirectTarget",
    "decision",
    "objectiveId",
    "transferLevel",
    "evidenceRelationKeys",
    "reason",
]

CANONICAL_OBJECTIVES = (
    "ACCELERATION",
    "DECELERATION",
    "FOOTWORK",
    "JUMP_LANDING",
    "LUNGE_REACH",
    "REACTION",
    "CONDITIONING",
    "ROTATION_GENERATION",
    "ANTI_ROTATION",
)

def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def build_core_relations(core_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    if len(core_rows) != 241:
        raise ValueError(f"Approved core authority must have 241 rows, found {len(core_rows)}")
    relations: list[dict[str, str]] = []
    for row in sorted(core_rows, key=lambda item: int(item["sourceRowOrder"])):
        stable_key = row["stableKey"]
        core_class = row["coreClass"]
        target = row["directTarget"]
        relations.append({
            "relationId": f"CORE_CLASS_{int(row['sourceRowOrder']):03d}",
            "exerciseStableKey": stable_key,
            "relationType": "CORE_CLASS",
            "relationValue": core_class,
            "sourceRowOrder": row["sourceRowOrder"],
            "provenance": "USER_APPROVED_CORE_REVIEW_2026_08_13",
            "reviewStatus": row["reviewStatus"],
        })
        if core_class == "DIRECT":
            if not target:
                raise ValueError(f"DIRECT core row lacks target: {stable_key}")
            relations.append({
                "relationId": f"CORE_TARGET_{int(row['sourceRowOrder']):03d}",
                "exerciseStableKey": stable_key,
                "relationType": "DIRECT_TARGET",
                "relationValue": target,
                "sourceRowOrder": row["sourceRowOrder"],
                "provenance": "USER_APPROVED_CORE_REVIEW_2026_08_13",
                "reviewStatus": row["reviewStatus"],
            })
        elif target:
            raise ValueError(f"Non-DIRECT core row has target: {stable_key}")
    if len(relations) != 272:
        raise ValueError(f"Canonical core relation count must be 272, found {len(relations)}")
    return relations


def build_badminton_objective_relations() -> list[dict[str, str]]:
    if sha256(BADMINTON_OBJECTIVE_AUTHORITY_SOURCE) != BADMINTON_OBJECTIVE_AUTHORITY_SHA256:
        raise ValueError("Canonical badminton objective authority SHA-256 mismatch")
    relations = read_csv(BADMINTON_OBJECTIVE_AUTHORITY_SOURCE)
    if len(relations) != 280:
        raise ValueError(f"Canonical badminton objective relation count must be 280, found {len(relations)}")
    if set(relations[0]) != set(BADMINTON_OBJECTIVE_HEADERS):
        raise ValueError("Canonical badminton objective authority has an invalid schema")
    if len({row["relationId"] for row in relations}) != len(relations):
        raise ValueError("Duplicate canonical badminton objective relation ID")
    pairs = {(row["exerciseStableKey"], row["objectiveId"]) for row in relations}
    if len(pairs) != len(relations):
        raise ValueError("Duplicate canonical badminton exercise/objective pair")
    if {row["objectiveId"] for row in relations} != set(CANONICAL_OBJECTIVES):
        raise ValueError("Canonical badminton objective set must contain exactly nine objectives")
    for row in relations:
        if row["transferLevel"] not in {"DIRECT", "SUPPORTIVE", "GENERAL", "LOW"}:
            raise ValueError(f"Invalid badminton objective transfer level: {row['relationId']}")
        if row["reviewStatus"] != "PASS" or not row["reviewReason"]:
            raise ValueError(f"Unreviewed badminton objective relation: {row['relationId']}")
        if row["provenance"] == USER_APPROVED_BADMINTON_OBJECTIVE_PROVENANCE:
            if row["evidenceRelationKeys"]:
                raise ValueError(f"Product decision unexpectedly contains inherited evidence: {row['relationId']}")
        elif row["provenance"] == "INHERITED_FROM_EXPLICIT_BADMINTON_RELATION_V1":
            if not row["evidenceRelationKeys"]:
                raise ValueError(f"Inherited objective lacks frozen evidence lineage: {row['relationId']}")
        else:
            raise ValueError(f"Unknown badminton objective provenance: {row['relationId']}")
    return relations


def build_rotation_audit(
    core_rows: list[dict[str, str]],
    objective_rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    objective_by_pair = {
        (row["exerciseStableKey"], row["objectiveId"]): row for row in objective_rows
    }
    candidates = [
        row for row in core_rows
        if row["coreClass"] == "DIRECT"
        and row["directTarget"] in {"ROTATION_GENERATION", "ANTI_ROTATION"}
    ]
    audit: list[dict[str, str]] = []
    for core in sorted(candidates, key=lambda row: (row["directTarget"], row["stableKey"])):
        pair = (core["stableKey"], core["directTarget"])
        relation = objective_by_pair.get(pair)
        user_approved = relation and relation["provenance"] == USER_APPROVED_BADMINTON_OBJECTIVE_PROVENANCE
        audit.append({
            "exerciseStableKey": core["stableKey"],
            "coreDirectTarget": core["directTarget"],
            "decision": "CREATE_EXPLICIT_OBJECTIVE" if relation else "NO_OBJECTIVE_RELATION",
            "objectiveId": core["directTarget"] if relation else "",
            "transferLevel": relation["transferLevel"] if relation else "NONE",
            "evidenceRelationKeys": relation["evidenceRelationKeys"] if relation else "",
            "reason": (
                "An explicit reviewed product-owner decision approves this badminton objective."
                if user_approved else
                "Existing explicit canonical badminton evidence supports this objective."
                if relation else
                "Core classification alone is not badminton-transfer evidence; no explicit "
                "canonical badminton relation supports this objective."
            ),
        })
    return audit


def build_analysis_assets() -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    if sha256(CORE_APPROVED_SOURCE) != CORE_APPROVED_SHA256:
        raise ValueError("Approved core authority SHA-256 mismatch")
    core_rows = read_csv(CORE_APPROVED_SOURCE)
    core_relations = build_core_relations(core_rows)
    objective_relations = build_badminton_objective_relations()
    rotation_audit = build_rotation_audit(core_rows, objective_relations)
    return core_relations, objective_relations, rotation_audit


def write_rotation_audit(path: Path, audit_rows: list[dict[str, str]]) -> None:
    write_csv(path, ROTATION_AUDIT_HEADERS, audit_rows)
