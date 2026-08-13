from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

from authority_common import sha256, write_csv


ROOT = Path(__file__).resolve().parents[2]
CORE_APPROVED_SOURCE = (
    ROOT / "docs/metadata_authority/core_training_classification_review_2026-08-13.csv"
)
CORE_APPROVED_SHA256 = "3c819568012cd17726486e7f3e21cac972c95eec1736e8ab038e9edc1c3fa954"

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

# Every token below is an explicit fact in badminton_relations.csv. Broad
# anatomy, plane, laterality, generic bracing, and core authority are
# intentionally absent.
EXPLICIT_OBJECTIVES = {
    "TRANSFER_TYPE": {
        "FOOTWORK_DIRECT": ("FOOTWORK",),
        "REACTION_DECISION_DIRECT": ("REACTION",),
        "CHANGE_OF_DIRECTION_DIRECT": ("DECELERATION", "FOOTWORK"),
        "LUNGE_REACH_DIRECT": ("LUNGE_REACH",),
        "GENERAL_CONDITIONING_SUPPORTIVE": ("CONDITIONING",),
        "RALLY_CONDITIONING_DIRECT": ("CONDITIONING",),
        "ROTATION_POWER_SUPPORTIVE": ("ROTATION_GENERATION",),
        "ANTI_ROTATION_STABILITY_SUPPORTIVE": ("ANTI_ROTATION",),
    },
    "SKILL_TARGET": {
        "FIRST_STEP": ("ACCELERATION",),
        "CHANGE_OF_DIRECTION": ("DECELERATION", "FOOTWORK"),
        "SPLIT_STEP": ("FOOTWORK",),
        "LATERAL_MOVEMENT": ("FOOTWORK",),
        "FRONT_COURT_LUNGE": ("LUNGE_REACH",),
        "LATERAL_LUNGE": ("LUNGE_REACH",),
        "RALLY_TOLERANCE": ("CONDITIONING",),
        "MULTI_SHUTTLE_ENDURANCE": ("CONDITIONING",),
        "ROTATION_SEQUENCING": ("ROTATION_GENERATION",),
        "ANTI_ROTATION_STABILITY": ("ANTI_ROTATION",),
    },
    "PHYSICAL_QUALITY": {
        "ACCELERATION": ("ACCELERATION",),
        "DECELERATION": ("DECELERATION",),
        "REACTIVE_AGILITY": ("REACTION",),
        "ANAEROBIC_REPEATABILITY": ("CONDITIONING",),
        "AEROBIC_BASE": ("CONDITIONING",),
        "ROTATIONAL_POWER": ("ROTATION_GENERATION",),
        "ROTATIONAL_STRENGTH": ("ROTATION_GENERATION",),
        "ANTI_ROTATION_STABILITY": ("ANTI_ROTATION",),
    },
    "COURT_MOVEMENT": {
        "FIRST_STEP": ("ACCELERATION",),
        "DECELERATION": ("DECELERATION",),
        "RECOVERY_STEP": ("FOOTWORK",),
        "LATERAL_MOVE": ("FOOTWORK",),
        "MULTI_DIRECTION": ("FOOTWORK",),
        "JUMP_LANDING": ("JUMP_LANDING",),
        "REACTION_RANDOM": ("REACTION",),
    },
}


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


def build_badminton_objective_relations(
    badminton_rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    by_key: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in badminton_rows:
        by_key[row["exerciseStableKey"]].append(row)

    generated: list[dict[str, str]] = []
    for stable_key, rows in sorted(by_key.items()):
        level_rows = [row for row in rows if row["relationType"] == "TRANSFER_LEVEL"]
        if len(level_rows) != 1:
            raise ValueError(f"Expected one badminton transfer level for {stable_key}")
        level = level_rows[0]["relationValue"]
        if level == "NONE":
            continue
        evidence_by_objective: dict[str, set[str]] = defaultdict(set)
        for row in rows:
            objectives = EXPLICIT_OBJECTIVES.get(row["relationType"], {}).get(
                row["relationValue"], ()
            )
            for objective in objectives:
                evidence_by_objective[objective].add(row["relationKey"])
        for objective in CANONICAL_OBJECTIVES:
            evidence = sorted(evidence_by_objective.get(objective, ()))
            if not evidence:
                continue
            generated.append({
                "relationId": f"BADMINTON_OBJECTIVE_{len(generated) + 1:04d}",
                "exerciseStableKey": stable_key,
                "objectiveId": objective,
                "transferLevel": level,
                "provenance": "INHERITED_FROM_EXPLICIT_BADMINTON_RELATION_V1",
                "evidenceRelationKeys": "|".join(evidence),
                "reviewStatus": "PASS",
                "reviewReason": (
                    "Objective is supported by explicit canonical badminton relations; "
                    "the prior exercise-wide transfer level is inherited for this initial "
                    "objective-specific cutover."
                ),
            })
    return generated


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
        audit.append({
            "exerciseStableKey": core["stableKey"],
            "coreDirectTarget": core["directTarget"],
            "decision": "CREATE_EXPLICIT_OBJECTIVE" if relation else "NO_OBJECTIVE_RELATION",
            "objectiveId": core["directTarget"] if relation else "",
            "transferLevel": relation["transferLevel"] if relation else "NONE",
            "evidenceRelationKeys": relation["evidenceRelationKeys"] if relation else "",
            "reason": (
                "Existing explicit canonical badminton evidence supports this objective."
                if relation else
                "Core classification alone is not badminton-transfer evidence; no explicit "
                "canonical badminton relation supports this objective."
            ),
        })
    return audit


def build_analysis_assets(
    badminton_rows: list[dict[str, str]],
) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    if sha256(CORE_APPROVED_SOURCE) != CORE_APPROVED_SHA256:
        raise ValueError("Approved core authority SHA-256 mismatch")
    core_rows = read_csv(CORE_APPROVED_SOURCE)
    core_relations = build_core_relations(core_rows)
    objective_relations = build_badminton_objective_relations(badminton_rows)
    rotation_audit = build_rotation_audit(core_rows, objective_relations)
    return core_relations, objective_relations, rotation_audit


def write_rotation_audit(path: Path, audit_rows: list[dict[str, str]]) -> None:
    write_csv(path, ROTATION_AUDIT_HEADERS, audit_rows)
