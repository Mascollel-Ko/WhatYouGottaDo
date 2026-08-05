from __future__ import annotations

import hashlib
import csv
import json
import tempfile
import unittest
from pathlib import Path
import sys


TOOLS = Path(__file__).resolve().parents[1]
REPO = TOOLS.parents[1]
sys.path.insert(0, str(TOOLS))

from authority_common import load_workbook, sheet_rows  # noqa: E402
from export_canonical_metadata import export  # noqa: E402
from validate_authority_workbook import validate  # noqa: E402


WORKBOOK = REPO / "docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx"
ASSETS = REPO / "app/src/main/assets/metadata/canonical_v1"


def csv_by_key(path: Path, key: str) -> dict[str, dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return {row[key]: row for row in csv.DictReader(source)}


class MetadataAuthorityTest(unittest.TestCase):
    def test_workbook_encodes_approved_identity_and_relation_decisions(self):
        counts = validate(WORKBOOK)
        self.assertEqual(257, counts["identityRows"])
        self.assertEqual(241, counts["selectableIdentityRows"])
        self.assertEqual(16, counts["historyOnlyIdentityRows"])
        self.assertEqual(241, counts["timingRows"])
        self.assertEqual(257, counts["bootstrapRows"])

    def test_export_is_byte_deterministic_and_excludes_research_workbook_layers(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            first_path = Path(first)
            second_path = Path(second)
            export(WORKBOOK, first_path)
            export(WORKBOOK, second_path)
            first_files = sorted(path.relative_to(first_path) for path in first_path.rglob("*") if path.is_file())
            second_files = sorted(path.relative_to(second_path) for path in second_path.rglob("*") if path.is_file())
            self.assertEqual(first_files, second_files)
            self.assertNotIn(Path("tissue_load_relations.csv"), first_files)
            self.assertIn(Path("exercise_bootstrap.csv"), first_files)
            self.assertIn(Path("movement_relations.csv"), first_files)
            for relative in first_files:
                self.assertEqual(
                    hashlib.sha256((first_path / relative).read_bytes()).digest(),
                    hashlib.sha256((second_path / relative).read_bytes()).digest(),
                    relative,
                )

    def test_history_only_rows_are_not_runtime_program_selectable(self):
        workbook = load_workbook(WORKBOOK, read_only=True)
        identities = {row["stableKey"]: row for row in sheet_rows(workbook, "05_IDENTITY_MASTER")}
        history = {key for key, row in identities.items() if row["identityStatus"] == "HISTORY_ONLY_GENERIC"}
        self.assertEqual(16, len(history))
        self.assertTrue(all(identities[key]["selectable"] == "NO" for key in history))
        with tempfile.TemporaryDirectory() as directory:
            export(WORKBOOK, Path(directory))
            runtime = (Path(directory) / "runtime_metadata.csv").read_text(encoding="utf-8")
            for key in history:
                line = next(line for line in runtime.splitlines() if line.startswith(key + ","))
                self.assertIn(",HISTORY_ONLY,", line)

    def test_runtime_metadata_matches_previous_asset_except_history_gate(self):
        legacy = csv_by_key(
            REPO / "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv",
            "stableKey",
        )
        canonical = csv_by_key(ASSETS / "runtime_metadata.csv", "stableKey")
        history = set(csv_by_key(ASSETS / "history_identity.csv", "stableKey"))
        self.assertEqual(224, len(legacy))
        self.assertEqual(set(legacy), set(canonical).intersection(legacy))
        for stable_key, old in legacy.items():
            current = canonical[stable_key]
            for field, old_value in old.items():
                if field == "exerciseName":
                    continue
                if field == "currentPlanningEligibility" and stable_key in history:
                    self.assertEqual("HISTORY_ONLY", current[field])
                else:
                    self.assertEqual(old_value, current[field], f"{stable_key}.{field}")

    def test_manifest_hashes_and_counts_match_every_generated_asset(self):
        manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual("1.0.0", manifest["generatorVersion"])
        for entry in manifest["files"]:
            path = ASSETS / entry["path"]
            self.assertEqual(entry["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            with path.open(encoding="utf-8-sig", newline="") as source:
                self.assertEqual(entry["rowCount"], sum(1 for _ in csv.DictReader(source)))


if __name__ == "__main__":
    unittest.main()
