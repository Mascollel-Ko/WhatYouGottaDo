import sys
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from localization_audit import collect, artifacts  # noqa: E402


class LocalizationAuditTest(unittest.TestCase):
    def test_translation_gate_reports_real_unapproved_gaps(self):
        _, _, summary = collect()
        metrics = {row["metric"]: int(row["value"]) for row in summary}
        self.assertGreater(metrics["missingApprovedEnglishUiStringCount"], 0)
        self.assertGreater(metrics["missingApprovedEnglishExerciseNameCount"], 0)
        self.assertEqual(0, metrics["invalidEnglishPlaceholderCount"])

    def test_artifacts_are_deterministic(self):
        first = artifacts()
        second = artifacts()
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
