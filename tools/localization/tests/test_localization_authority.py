import hashlib
import json
import sys
import tempfile
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
        self.assertEqual(253, manifest["exerciseApprovedRows"])
        self.assertEqual(253, manifest["exerciseDescriptionLocalizedRows"])
        self.assertEqual(12, manifest["seedProgramLocalizedRows"])
        self.assertEqual(4, manifest["strengthTargetLocalizedRows"])
        self.assertEqual(1834, manifest["metadataAuthoritativeRows"])
        self.assertEqual(92, manifest["tissueApprovedRows"])
        self.assertEqual(0, manifest["currentBaselineCheckRequired"])
        self.assertGreater(manifest["codexGeneratedEnglishEntries"], 1_000)
        self.assertGreater(manifest["dynamicUiRuntimeEntries"], 200)
        self.assertEqual(
            "782B41556DE341BD1208F13A189C99387967F811435CEA2821B6B1D87CB040B8",
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

    def test_strength_targets_use_approved_identity_resources(self):
        kotlin = authority._artifacts()[authority.OUTPUTS["kotlin"]]

        self.assertIn("val strengthTargetNameIds: Map<String, Int> = mapOf(", kotlin)
        for target_key in (
            "strength.bench_press",
            "strength.back_squat",
            "strength.conventional_deadlift",
            "strength.weighted_pull_up",
        ):
            self.assertIn(f'"{target_key}" to R.string.', kotlin)

    def test_retired_badminton_composite_wording_is_not_generated(self):
        artifacts = authority._artifacts()
        runtime_text = "\n".join(
            (
                artifacts[authority.OUTPUTS["ui_base"]],
                artifacts[authority.OUTPUTS["ui_en"]],
                artifacts[authority.OUTPUTS["kotlin"]],
            )
        )

        self.assertIn("Badminton practice load", runtime_text)
        for retired in authority.RETIRED_UI_TEXTS:
            self.assertNotIn(retired, runtime_text)

    def test_static_percent_is_literal_while_formatted_percent_is_escaped(self):
        xml = authority._xml_strings(
            [("static", "80% range"), ("dynamic", "80% %1$s")]
        )

        self.assertIn('<string name="static" formatted="false">80% range</string>', xml)
        self.assertIn('<string name="dynamic">80%% %1$s</string>', xml)

    def test_csv_rows_normalizes_embedded_line_endings(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.csv"
            path.write_bytes(b'korean,english\r\n"first\r\nsecond","one\r\ntwo"\r\n')

            self.assertEqual(
                [{"korean": "first\nsecond", "english": "one\ntwo"}],
                authority._csv_rows(path),
            )


if __name__ == "__main__":
    unittest.main()
