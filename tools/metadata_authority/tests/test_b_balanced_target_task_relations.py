"""B-balanced additive target-task overlay contract.

This file is staged for the future canonical B allocator; it is NOT currently
loaded by the V2.2 Python v1.0 engine or a live Kotlin prescription service.
"""
import csv
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CANONICAL = ROOT / "app/src/main/assets/metadata/canonical_v1"
OVERLAY = ROOT / "app/src/main/assets/metadata/b_balanced_v1/target_task_relations.csv"


def rows(path):
    with path.open(encoding="utf-8", newline="") as reader:
        return list(csv.DictReader(reader))


class BalancedTargetTaskRelationsTest(unittest.TestCase):
    def test_overlay_is_bounded_non_prescriptive_and_references_selectable_keys(self):
        overlay = rows(OVERLAY)
        identities = {r["stableKey"]: r for r in rows(CANONICAL / "identity_master.csv")}
        self.assertEqual(9, len(overlay))
        self.assertEqual(9, len({r["relationId"] for r in overlay}))
        self.assertEqual(9, len({(r["exerciseStableKey"], r["targetDomain"], r["targetTask"]) for r in overlay}))
        for r in overlay:
            self.assertIn(r["exerciseStableKey"], identities)
            self.assertEqual("YES", identities[r["exerciseStableKey"]]["selectable"])
            self.assertIn(r["targetDomain"], ("MOVEMENT", "CORE"))
            self.assertEqual("REVIEWED_B_OVERLAY_V1", r["reviewStatus"])
            self.assertEqual("NONE", r["numericDoseAuthority"])

    def test_hip_adduction_and_abduction_are_distinct_task_targets(self):
        overlay = rows(OVERLAY)
        by_key = {}
        for r in overlay:
            by_key.setdefault(r["exerciseStableKey"], set()).add(r["targetTask"])
        self.assertEqual({"HIP_ADDUCTION"}, by_key["cable_hip_adduction"])
        self.assertEqual({"HIP_ADDUCTION"}, by_key["hip_adduction_machine"])
        self.assertEqual({"HIP_ABDUCTION"}, by_key["ex_728da646"])

        identities = {r["stableKey"]: r for r in rows(CANONICAL / "identity_master.csv")}
        bootstrap = {r["stableKey"]: r for r in rows(CANONICAL / "exercise_bootstrap.csv")}
        for key in ("cable_hip_adduction", "hip_adduction_machine", "ex_728da646"):
            self.assertIn("HIP_ADDUCTION", identities[key]["jointAction"])
            self.assertIn("HIP_ABDUCTION", identities[key]["jointAction"])
        self.assertIn("안쪽으로", bootstrap["cable_hip_adduction"]["description"])
        self.assertIn("안쪽으로", bootstrap["hip_adduction_machine"]["description"])
        self.assertIn("바깥쪽으로", bootstrap["ex_728da646"]["description"])

    def test_core_roles_refine_but_do_not_replace_bracing_or_grant_sets(self):
        overlay = [r for r in rows(OVERLAY) if r["targetDomain"] == "CORE"]
        core = rows(CANONICAL / "core_relations.csv")
        movement = rows(CANONICAL / "movement_relations.csv")
        core_by_key = {}
        for r in core:
            core_by_key.setdefault(r["exerciseStableKey"], {})[r["relationType"]] = r["relationValue"]
        movement_by_key = {}
        for r in movement:
            if r["relationType"] == "MOVEMENT_PATTERN":
                movement_by_key.setdefault(r["exerciseStableKey"], set()).add(r["relationValue"])
        for r in overlay:
            key, task = r["exerciseStableKey"], r["targetTask"]
            self.assertEqual("DIRECT", core_by_key[key]["CORE_CLASS"])
            self.assertEqual("BRACING", core_by_key[key]["DIRECT_TARGET"])
            self.assertIn(task, movement_by_key.get(key, set()))
            self.assertEqual("NONE", r["numericDoseAuthority"])


if __name__ == "__main__":
    unittest.main()
