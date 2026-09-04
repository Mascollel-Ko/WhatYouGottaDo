#!/usr/bin/env python3
"""Deterministic v0.11 exposure-representation reference (test-only)."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from statistics import median

SEVERE_RATIO = 0.25
CLEAR_RATIO = 0.50
OBJECTIVES = [
    "ACCELERATION", "DECELERATION", "FOOTWORK", "JUMP_LANDING", "LUNGE_REACH",
    "REACTION", "CONDITIONING", "ROTATION_GENERATION", "ANTI_ROTATION",
]


def comparison_window(days_before_cutoff: int) -> str | None:
    if 0 <= days_before_cutoff <= 27:
        return "CURRENT"
    if 28 <= days_before_cutoff <= 55:
        return "PRIOR"
    return None


def anchored_seven_day_bin(days_before_cutoff: int) -> int | None:
    window = comparison_window(days_before_cutoff)
    if window == "CURRENT":
        return days_before_cutoff // 7
    if window == "PRIOR":
        return (days_before_cutoff - 28) // 7
    return None


def active_anchored_bins(days_before_cutoff: list[int], window: str = "CURRENT") -> int:
    return len({anchored_seven_day_bin(day) for day in days_before_cutoff if comparison_window(day) == window})


def confidence(active_bins: int) -> str:
    return "HIGH" if active_bins == 4 else "MODERATE" if active_bins in (2, 3) else "LOW"


def safe_ratio(numerator: float | None, denominator: float | None) -> float | None:
    return numerator / denominator if numerator is not None and denominator is not None and denominator > 0 else None


def movement_state(current: float, evidence: str, peer: float | None, personal: float | None) -> str:
    if current == 0:
        return "ABSENT"
    if evidence == "LOW":
        return "UNKNOWN"
    if (peer is not None and peer <= SEVERE_RATIO) or (personal is not None and personal <= SEVERE_RATIO):
        return "STRONG_UNDERREPRESENTATION_SIGNAL"
    if peer is not None and personal is not None and peer <= CLEAR_RATIO and personal <= CLEAR_RATIO:
        return "STRONG_UNDERREPRESENTATION_SIGNAL"
    if (peer is not None and peer <= CLEAR_RATIO) or (personal is not None and personal <= CLEAR_RATIO):
        return "UNDERREPRESENTATION_SIGNAL"
    return "NO_CLEAR_DEFICIT_SIGNAL" if peer is not None or personal is not None else "UNKNOWN"


def movement_gap(priority: str, state: str, evidence: str) -> str | None:
    if priority == "HIGH":
        if state == "ABSENT":
            return "MODERATE" if evidence == "LOW" else "HIGH"
        if state == "STRONG_UNDERREPRESENTATION_SIGNAL":
            return "HIGH" if evidence == "HIGH" else "MODERATE"
        if state == "UNDERREPRESENTATION_SIGNAL":
            return "MODERATE"
    else:
        if state in ("ABSENT", "STRONG_UNDERREPRESENTATION_SIGNAL"):
            return "MODERATE"
        if state == "UNDERREPRESENTATION_SIGNAL":
            return "LOW"
    return None


def assess_movement(case: dict) -> dict:
    current, prior, priorities = case["current"], case["prior"], case["priorities"]
    current_total, prior_total = sum(current.values()), sum(prior.values())
    evidence = confidence(case["activeBins"])
    result = {}
    for target, priority in priorities.items():
        cur, old = current.get(target, 0.0), prior.get(target, 0.0)
        current_share = cur / current_total if current_total > 0 else None
        prior_share = old / prior_total if prior_total > 0 else None
        personal = safe_ratio(current_share, prior_share)
        peers = [current.get(key, 0.0) for key, peer_priority in priorities.items() if key != target and peer_priority == priority and current.get(key, 0.0) > 0]
        reference = median(peers) if len(peers) >= 2 else None
        peer = safe_ratio(cur, reference)
        state = movement_state(cur, evidence, peer, personal)
        result[target] = {
            "currentShare": current_share,
            "priorShare": prior_share,
            "peerReference": reference,
            "peerRepresentationRatio": peer,
            "personalRetentionRatio": personal,
            "state": state,
            "confidence": evidence,
            "gapPriority": movement_gap(priority, state, evidence),
        }
    return {"foundationalOnramp": current_total == 0, "representations": result}


def badminton_state(current: float, evidence: str, peer: float | None, personal: float | None) -> str:
    if current == 0:
        return "ABSENT"
    if evidence == "LOW":
        return "UNKNOWN"
    if personal is not None and personal <= SEVERE_RATIO:
        return "STRONG_UNDERREPRESENTATION_SIGNAL"
    if personal is not None and personal <= CLEAR_RATIO and peer is not None and peer <= SEVERE_RATIO:
        return "STRONG_UNDERREPRESENTATION_SIGNAL"
    if personal is not None and personal <= CLEAR_RATIO:
        return "UNDERREPRESENTATION_SIGNAL"
    if personal is None and evidence == "HIGH" and peer is not None and peer <= SEVERE_RATIO:
        return "UNDERREPRESENTATION_SIGNAL"
    return "NO_CLEAR_DEFICIT_SIGNAL" if personal is not None or peer is not None else "UNKNOWN"


def assess_badminton(case: dict) -> dict:
    current = {key: float(case["currentWeighted"].get(key, 0.0)) for key in OBJECTIVES}
    prior = {key: float(case["priorWeighted"].get(key, 0.0)) for key in OBJECTIVES}
    current_direct = {key: float(case["currentDirect"].get(key, 0.0)) for key in OBJECTIVES}
    prior_direct = {key: float(case["priorDirect"].get(key, 0.0)) for key in OBJECTIVES}
    current_total, prior_total = sum(current.values()), sum(prior.values())
    evidence = confidence(case["activeBins"])
    result = {}
    for target in OBJECTIVES:
        cur, old = current[target], prior[target]
        current_share = cur / current_total if current_total > 0 else None
        prior_share = old / prior_total if prior_total > 0 else None
        personal = safe_ratio(current_share, prior_share)
        peers = [value for key, value in current.items() if key != target and value > 0]
        reference = median(peers) if len(peers) >= 3 else None
        peer = safe_ratio(cur, reference)
        state = badminton_state(cur, evidence, peer, personal)
        direct_drop = prior_direct[target] > 0 and current_direct[target] == 0
        never_direct = prior_direct[target] == 0 and current_direct[target] == 0
        peer_only = personal is None and peer is not None and peer <= SEVERE_RATIO
        gap_priority = None
        if direct_drop:
            gap_priority = "HIGH"
        elif state in ("STRONG_UNDERREPRESENTATION_SIGNAL", "UNDERREPRESENTATION_SIGNAL"):
            gap_priority = "HIGH" if state == "STRONG_UNDERREPRESENTATION_SIGNAL" and evidence == "HIGH" and not peer_only else "MODERATE"
        result[target] = {
            "currentShare": current_share,
            "priorShare": prior_share,
            "peerMedianCurrent": reference,
            "peerRepresentationRatio": peer,
            "personalRetentionRatio": personal,
            "state": state,
            "confidence": evidence,
            "directDrop": direct_drop,
            "neverDirectObserved": never_direct,
            "gapPriority": gap_priority,
        }
    developmental = sorted(key for key, value in result.items() if value["neverDirectObserved"])
    return {"representations": result, "optionalDevelopmentalCandidate": developmental[0] if developmental else None}


HIGH = {key: "HIGH" for key in ("LOWER_KNEE", "POSTERIOR_CHAIN", "HORIZONTAL_PUSH", "UPPER_PULL")}
WITH_CORE = {**HIGH, "CORE_DIRECT": "MODERATE"}

MOVEMENT_CASES = [
    {"id": "pull_absent", "current": {"LOWER_KNEE": 30, "POSTERIOR_CHAIN": 20, "HORIZONTAL_PUSH": 25, "UPPER_PULL": 0}, "prior": {}, "priorities": HIGH, "activeBins": 4},
    {"id": "pull_one_vs_peers", "current": {"LOWER_KNEE": 30, "POSTERIOR_CHAIN": 20, "HORIZONTAL_PUSH": 25, "UPPER_PULL": 1}, "prior": {}, "priorities": HIGH, "activeBins": 4},
    {"id": "balanced_ten", "current": {key: 10 for key in HIGH}, "prior": {}, "priorities": HIGH, "activeBins": 4},
    {"id": "balanced_one", "current": {key: 1 for key in HIGH}, "prior": {}, "priorities": HIGH, "activeBins": 4},
    {"id": "personal_pull_share_collapse", "current": {"LOWER_KNEE": 30, "POSTERIOR_CHAIN": 30, "HORIZONTAL_PUSH": 32, "UPPER_PULL": 8}, "prior": {key: 25 for key in HIGH}, "priorities": HIGH, "activeBins": 4},
    {"id": "all_halved_proportionally", "current": {key: 10 for key in HIGH}, "prior": {key: 20 for key in HIGH}, "priorities": HIGH, "activeBins": 4},
    {"id": "one_active_bin", "current": {"LOWER_KNEE": 30, "POSTERIOR_CHAIN": 20, "HORIZONTAL_PUSH": 25, "UPPER_PULL": 1}, "prior": {}, "priorities": HIGH, "activeBins": 1},
    {"id": "no_resistance", "current": {key: 0 for key in WITH_CORE}, "prior": {}, "priorities": WITH_CORE, "activeBins": 0},
    {"id": "athletic_excluded", "current": {key: 10 for key in HIGH}, "prior": {}, "priorities": HIGH, "activeBins": 4, "excludedAthleticBouts": 50},
    {"id": "moderate_priority_ceiling", "current": {**{key: 10 for key in HIGH}, "CORE_DIRECT": 0}, "prior": {}, "priorities": WITH_CORE, "activeBins": 4},
]


def nine(**values: float) -> dict:
    return values


BADMINTON_CASES = [
    {"id": "direct_deceleration_drop", "currentWeighted": nine(FOOTWORK=8, REACTION=8, ACCELERATION=8), "priorWeighted": nine(DECELERATION=8, FOOTWORK=8, REACTION=8, ACCELERATION=8), "currentDirect": {}, "priorDirect": nine(DECELERATION=8), "activeBins": 4},
    {"id": "nonzero_personal_deceleration_collapse", "currentWeighted": nine(DECELERATION=1, FOOTWORK=11, REACTION=10, ACCELERATION=9), "priorWeighted": nine(DECELERATION=10, FOOTWORK=10, REACTION=10, ACCELERATION=10), "currentDirect": nine(DECELERATION=1), "priorDirect": nine(DECELERATION=10), "activeBins": 4},
    {"id": "peer_only_deceleration", "currentWeighted": nine(DECELERATION=1, FOOTWORK=11, REACTION=10, ACCELERATION=9), "priorWeighted": {}, "currentDirect": nine(DECELERATION=1), "priorDirect": {}, "activeBins": 4},
    {"id": "peer_imbalance_sparse", "currentWeighted": nine(DECELERATION=1, FOOTWORK=11, REACTION=10, ACCELERATION=9), "priorWeighted": {}, "currentDirect": nine(DECELERATION=1), "priorDirect": {}, "activeBins": 1},
    {"id": "supportive_jump_without_direct", "currentWeighted": nine(JUMP_LANDING=.6, FOOTWORK=4, REACTION=4, ACCELERATION=4), "priorWeighted": {}, "currentDirect": {}, "priorDirect": {}, "activeBins": 4},
    {"id": "generic_court_zero_objective", "currentWeighted": {}, "priorWeighted": {}, "currentDirect": {}, "priorDirect": {}, "activeBins": 0, "genericCourtMinutes": 240},
    {"id": "never_direct_not_drop", "currentWeighted": nine(FOOTWORK=4), "priorWeighted": nine(FOOTWORK=4), "currentDirect": {}, "priorDirect": {}, "activeBins": 4},
    {"id": "never_direct_no_normal_pressure", "currentWeighted": nine(FOOTWORK=4, REACTION=4, ACCELERATION=4), "priorWeighted": nine(FOOTWORK=4, REACTION=4, ACCELERATION=4), "currentDirect": {}, "priorDirect": {}, "activeBins": 4},
    {"id": "all_objectives_halved", "currentWeighted": {key: 5 for key in OBJECTIVES}, "priorWeighted": {key: 10 for key in OBJECTIVES}, "currentDirect": {key: 5 for key in OBJECTIVES}, "priorDirect": {key: 10 for key in OBJECTIVES}, "activeBins": 4},
    {"id": "one_objective_share_drop", "currentWeighted": nine(DECELERATION=2, FOOTWORK=10, REACTION=10, ACCELERATION=10), "priorWeighted": nine(DECELERATION=10, FOOTWORK=10, REACTION=10, ACCELERATION=10), "currentDirect": nine(DECELERATION=2), "priorDirect": nine(DECELERATION=10), "activeBins": 4},
    {"id": "balanced_current", "currentWeighted": {key: 10 for key in OBJECTIVES}, "priorWeighted": {}, "currentDirect": {key: 10 for key in OBJECTIVES}, "priorDirect": {}, "activeBins": 4},
    {"id": "direct_athletic_plyometric", "currentWeighted": nine(JUMP_LANDING=4), "priorWeighted": {}, "currentDirect": nine(JUMP_LANDING=4), "priorDirect": {}, "activeBins": 4, "expectedActivityDomain": "ATHLETIC_PERFORMANCE_DRILL"},
]


def build_fixture() -> dict:
    return {
        "version": "RECORD_BASED_PLANNER_0.11.0_REFERENCE_1",
        "epsilon": 1e-9,
        "windowContract": [
            {"daysBeforeCutoff": day, "window": comparison_window(day), "bin": anchored_seven_day_bin(day)}
            for day in (0, 6, 7, 13, 14, 20, 21, 27, 28, 34, 35, 41, 42, 48, 49, 55, 56)
        ],
        "activeBinExamples": {
            "currentFour": active_anchored_bins([0, 7, 14, 21]),
            "currentOne": active_anchored_bins([0, 1, 6]),
            "priorFour": active_anchored_bins([28, 35, 42, 49], "PRIOR"),
        },
        "movementCases": [{**case, "expected": assess_movement(case)} for case in MOVEMENT_CASES],
        "badmintonCases": [{**case, "expected": assess_badminton(case)} for case in BADMINTON_CASES],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    path = Path(__file__).with_name("fixtures") / "v011_exposure_representation_golden.json"
    rendered = json.dumps(build_fixture(), ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.write:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(rendered, encoding="utf-8")
    if args.check:
        if not path.exists() or path.read_text(encoding="utf-8") != rendered:
            raise SystemExit("v0.11 golden fixture is stale; run with --write")
    if not args.write and not args.check:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
