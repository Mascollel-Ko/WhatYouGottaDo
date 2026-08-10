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
        self.assertEqual(257, manifest["exerciseDescriptionLocalizedRows"])
        self.assertEqual(12, manifest["seedProgramLocalizedRows"])
        self.assertEqual(1834, manifest["metadataAuthoritativeRows"])
        self.assertEqual(92, manifest["tissueApprovedRows"])
        self.assertEqual(0, manifest["currentBaselineCheckRequired"])
        self.assertGreater(manifest["codexGeneratedEnglishEntries"], 1_000)
        self.assertGreater(manifest["dynamicUiRuntimeEntries"], 200)
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
        self.assertIn(
            "Hold the EZ-bar with your elbows fixed and curl it upward.",
            first[authority.OUTPUTS["exercise_description_en"]],
        )
        self.assertIn(
            "Badminton Strength Support - 4 Weeks",
            first[authority.OUTPUTS["program_name_en"]],
        )
        english_ui = first[authority.OUTPUTS["ui_en"]]
        self.assertIn("It will be displayed as your exercise records accumulate.", english_ui)
        self.assertEqual(
            hashlib.sha256(first[authority.OUTPUTS["kotlin"]].encode()).hexdigest(),
            hashlib.sha256(second[authority.OUTPUTS["kotlin"]].encode()).hexdigest(),
        )

    def test_exact_runtime_catalogue_is_split_into_bounded_initializer_chunks(self):
        kotlin = authority._artifacts()[authority.OUTPUTS["kotlin"]]

        self.assertIn("val exactUiTextIds: Map<String, Int> = buildMap {", kotlin)
        self.assertGreater(kotlin.count("private fun exactUiTextIdsChunk"), 1)
        self.assertIn("putAll(exactUiTextIdsChunk0())", kotlin)


if __name__ == "__main__":
    unittest.main()
