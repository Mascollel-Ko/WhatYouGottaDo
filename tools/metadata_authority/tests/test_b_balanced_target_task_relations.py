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
        self.assertEqual(38, len(overlay))
        self.assertEqual(38, len({r["relationId"] for r in overlay}))
        self.assertEqual(38, len({(r["exerciseStableKey"], r["targetDomain"], r["targetTask"]) for r in overlay}))
        for r in overlay:
            self.assertIn(r["exerciseStableKey"], identities)
            self.assertEqual("YES", identities[r["exerciseStableKey"]]["selectable"])
            self.assertIn(r["targetDomain"], ("MOVEMENT", "CORE"))
            self.assertIn(r["reviewStatus"], ("REVIEWED_B_OVERLAY_V2", "REVIEW_REQUIRED"))
            self.assertEqual("NONE", r["numericDoseAuthority"])
            self.assertTrue(r["recordingUnit"])

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
        self.assertEqual("HIP_ADDUCTION", identities["cable_hip_adduction"]["jointAction"])
        self.assertEqual("HIP_ADDUCTION", identities["hip_adduction_machine"]["jointAction"])
        self.assertEqual("HIP_ABDUCTION", identities["ex_728da646"]["jointAction"])
        for key in ("cable_hip_adduction", "hip_adduction_machine", "ex_728da646"):
            self.assertEqual("ISOLATION", bootstrap[key]["movementPattern"])
        self.assertIn("안쪽으로", bootstrap["cable_hip_adduction"]["description"])
        self.assertIn("안쪽으로", bootstrap["hip_adduction_machine"]["description"])
        self.assertIn("바깥쪽으로", bootstrap["ex_728da646"]["description"])

    def test_all_direct_core_rows_have_reviewed_details_without_granting_sets(self):
        overlay = [r for r in rows(OVERLAY) if r["targetDomain"] == "CORE"]
        core = rows(CANONICAL / "core_relations.csv")
        core_by_key = {}
        for r in core:
            core_by_key.setdefault(r["exerciseStableKey"], {})[r["relationType"]] = r["relationValue"]
        direct_keys = {key for key, relations in core_by_key.items() if relations.get("CORE_CLASS") == "DIRECT"}
        reviewed = [r for r in overlay if r["reviewStatus"] == "REVIEWED_B_OVERLAY_V2"]
        self.assertEqual(31, len(direct_keys))
        self.assertEqual(direct_keys, {r["exerciseStableKey"] for r in reviewed})
        allowed = {
            "ANTI_EXTENSION", "ANTI_ROTATION", "ANTI_LATERAL_FLEXION",
            "AXIAL_BRACING", "DYNAMIC_TRUNK_STABILIZATION",
            "ROTATION_GENERATION", "TRUNK_FLEXION", "TRUNK_EXTENSION",
        }
        for r in overlay:
            key, task = r["exerciseStableKey"], r["targetTask"]
            self.assertIn(task, allowed)
            if r["reviewStatus"] == "REVIEWED_B_OVERLAY_V2":
                self.assertEqual("DIRECT", core_by_key[key]["CORE_CLASS"])
            self.assertEqual("NONE", r["numericDoseAuthority"])

    def test_copenhagen_stays_variant_dependent_and_not_direct(self):
        overlay = rows(OVERLAY)
        copenhagen = [r for r in overlay if r["exerciseStableKey"] == "ex_a8385c4a"]
        self.assertEqual(1, len(copenhagen))
        self.assertEqual("ANTI_LATERAL_FLEXION", copenhagen[0]["targetTask"])
        self.assertEqual("REVIEW_REQUIRED", copenhagen[0]["reviewStatus"])
        core = rows(CANONICAL / "core_relations.csv")
        relations = {r["relationType"]: r["relationValue"] for r in core if r["exerciseStableKey"] == "ex_a8385c4a"}
        self.assertEqual("HIDDEN_HIGH", relations["CORE_CLASS"])


if __name__ == "__main__":
    unittest.main()
