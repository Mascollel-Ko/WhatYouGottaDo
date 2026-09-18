import csv
import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "app/src/main/assets/metadata/canonical_v1"
QUEUE = ROOT / "docs/audits/physical_quality_review_queue.csv"


def read_csv(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


class PhysicalQualityRelationsTest(unittest.TestCase):
    def test_production_asset_is_manifested_and_uses_closed_vocabulary(self):
        relations = read_csv(ASSETS / "physical_quality_relations.csv")
        manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
        entry = next(row for row in manifest["files"] if row["path"] == "physical_quality_relations.csv")
        self.assertEqual(259, len(relations))
        self.assertEqual(259, entry["rowCount"])
        self.assertEqual(entry["sha256"], hashlib.sha256((ASSETS / entry["path"]).read_bytes()).hexdigest())
        regions = {
            "SYSTEMIC", "LOWER", "POSTERIOR_CHAIN", "QUADS_GLUTE", "HAMSTRING", "ANKLE",
            "UPPER_PUSH", "UPPER_PULL", "ROTATIONAL", "FOREARM_GRIP", "JOINT_ROM",
            "UNILATERAL_LOWER", "CHEST", "SHOULDERS", "ARMS", "OTHER",
        }
        modes = {
            "GENERAL", "BILATERAL", "UNILATERAL", "HINGE", "SQUAT", "HORIZONTAL_PRESS",
            "VERTICAL_PRESS", "PULL", "ECCENTRIC", "ISOMETRIC", "BALLISTIC", "PLYOMETRIC",
            "SSC", "LANDING", "ROTATIONAL", "WRIST_FLEXION", "WRIST_EXTENSION",
            "PRONATION_SUPINATION", "CONDITIONING", "MOBILITY_CONTROL", "OTHER",
        }
        self.assertTrue(all(row["reviewStatus"] == "PASS" for row in relations))
        self.assertTrue(all(row["prescriptionDependent"] == "YES" for row in relations))
        self.assertTrue(all(row["regionQualifier"] in regions for row in relations))
        self.assertTrue(all(row["modeQualifier"] in modes for row in relations))
        self.assertFalse(any("_SSC" in row["regionQualifier"] or "_SSC" in row["modeQualifier"] for row in relations))

    def test_program_selectable_ownership_is_complete_and_exclusions_hold(self):
        runtime = {row["stableKey"]: row for row in read_csv(ASSETS / "runtime_metadata.csv")}
        identities = {row["stableKey"]: row for row in read_csv(ASSETS / "identity_master.csv")}
        queue = read_csv(QUEUE)
        selectable = {
            key for key, row in runtime.items()
            if identities[key]["selectable"] == "YES" and row["currentPlanningEligibility"] == "PROGRAM_SELECTABLE"
        }
        self.assertEqual(234, len(selectable))
        self.assertEqual(selectable, {row["exerciseStableKey"] for row in queue})
        self.assertTrue(all(row["ownerLayer"] for row in queue))
        self.assertEqual({"INTENTIONALLY_UNRESOLVED"}, {
            row["ownerLayer"] for row in queue if row["exerciseStableKey"] in {"ex_708e64ce", "kettlebell_halo"}
        })

        relations = read_csv(ASSETS / "physical_quality_relations.csv")
        by_exercise = {}
        for row in relations:
            by_exercise.setdefault(row["exerciseStableKey"], []).append(row)
        for key in {"ex_91d8430b", "ex_c7977dfd", "band_pallof_press", "cable_pallof_press", "ex_1c7f2342", "ex_33841b88"}:
            self.assertNotIn(key, by_exercise)
        for key in {"cable_hip_adduction", "hip_adduction_machine", "ex_728da646", "ex_8824026f", "ex_1cf51b6b"}:
            self.assertNotIn("REACTIVE_STRENGTH_SSC", {row["qualityId"] for row in by_exercise.get(key, [])})


if __name__ == "__main__":
    unittest.main()
