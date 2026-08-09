import sys
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from localization_audit import collect, artifacts, kotlin_string_templates  # noqa: E402


class LocalizationAuditTest(unittest.TestCase):
    def test_kotlin_template_scanner_preserves_nested_fallbacks(self):
        source = 'Text("활성 revision ${summary.key ?: "없음"} · ${count}개")'

        self.assertEqual(
            [("활성 revision ${} · ${}개", 5)],
            kotlin_string_templates(source),
        )

    def test_current_baseline_has_locale_aware_routes(self):
        _, _, summary = collect()
        metrics = {row["metric"]: int(row["value"]) for row in summary}
        self.assertEqual(0, metrics["currentBaselineCheckRequired"])
        self.assertEqual(0, metrics["unexplainedEnglishModeKoreanLeakCount"])
        self.assertEqual(0, metrics["unexplainedProductionLeakCount"])
        self.assertEqual(257, metrics["localizedExerciseNameCount"])
        self.assertEqual(257, metrics["localizedExerciseDescriptionCount"])
        self.assertEqual(12, metrics["localizedSeedProgramNameCount"])
        self.assertEqual(16, metrics["historyOnlyExerciseNameCount"])
        self.assertEqual(0, metrics["invalidEnglishPlaceholderCount"])
        self.assertGreater(metrics["approvedLocalizedPresentationCount"], 0)
        self.assertGreater(metrics["codexGeneratedEnglishCount"], 0)
        self.assertEqual(1, metrics["userContentPassthroughCount"])

    def test_artifacts_are_deterministic(self):
        first = artifacts()
        second = artifacts()
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
