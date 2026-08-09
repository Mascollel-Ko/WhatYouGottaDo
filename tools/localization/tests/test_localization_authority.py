import hashlib
import json
import sys
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import localization_authority as authority  # noqa: E402


class LocalizationAuthorityTest(unittest.TestCase):
    def test_approved_workbook_generates_complete_runtime_assets(self):
        artifacts = authority._artifacts()
        manifest = json.loads(artifacts[authority.OUTPUTS["manifest"]])

        self.assertEqual(612, manifest["uiApprovedRows"])
        self.assertEqual(257, manifest["exerciseApprovedRows"])
        self.assertEqual(1834, manifest["metadataAuthoritativeRows"])
        self.assertEqual(92, manifest["tissueApprovedRows"])
        self.assertEqual(0, manifest["currentBaselineCheckRequired"])
        self.assertEqual(
            "0CA2D8D01B603499D8509CC6E6E00BA027818B1F693D4B2978BF642C0F7DFE3A",
            manifest["authoritySha256"],
        )

    def test_generation_is_deterministic_and_preserves_approved_terms(self):
        first = authority._artifacts()
        second = authority._artifacts()

        self.assertEqual(first, second)
        english_exercises = first[authority.OUTPUTS["exercise_en"]]
        self.assertIn("Barbell Deadlift", english_exercises)
        self.assertNotIn("Conventional Deadlift", english_exercises)
        self.assertEqual(
            hashlib.sha256(first[authority.OUTPUTS["kotlin"]].encode()).hexdigest(),
            hashlib.sha256(second[authority.OUTPUTS["kotlin"]].encode()).hexdigest(),
        )


if __name__ == "__main__":
    unittest.main()
