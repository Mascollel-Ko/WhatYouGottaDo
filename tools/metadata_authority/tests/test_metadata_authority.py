from __future__ import annotations

import hashlib
import csv
import json
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import sys


TOOLS = Path(__file__).resolve().parents[1]
REPO = TOOLS.parents[1]
sys.path.insert(0, str(TOOLS))

from authority_common import DISPLAY_HEADERS, DISPLAY_SHEET, load_workbook, sheet_rows  # noqa: E402
from export_canonical_metadata import export  # noqa: E402
from validate_authority_workbook import validate  # noqa: E402
from metadata_display_routing_audit import collect as collect_display_routing  # noqa: E402
from metadata_display_routing_audit import render as render_display_routing  # noqa: E402
from metadata_display_routing_audit import validate as validate_display_routing  # noqa: E402
from analysis_cutover_authority import (  # noqa: E402
    CORE_APPROVED_SHA256,
    CORE_APPROVED_SOURCE,
    build_analysis_assets,
)


WORKBOOK = REPO / "docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx"
ASSETS = REPO / "app/src/main/assets/metadata/canonical_v1"
RESOURCES = REPO / "app/src/main/res"


def csv_by_key(path: Path, key: str) -> dict[str, dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return {row[key]: row for row in csv.DictReader(source)}


def export_to(directory: Path):
    return export(
        WORKBOOK,
        directory / "assets",
        directory / "res",
        directory / "display_manifest.json",
    )


class MetadataAuthorityTest(unittest.TestCase):
    def test_workbook_encodes_approved_identity_and_relation_decisions(self):
        counts = validate(WORKBOOK)
        self.assertEqual(257, counts["identityRows"])
        self.assertEqual(241, counts["selectableIdentityRows"])
        self.assertEqual(16, counts["historyOnlyIdentityRows"])
        self.assertEqual(241, counts["timingRows"])
        self.assertEqual(257, counts["bootstrapRows"])
        self.assertEqual(1823, counts["displayRows"])
        self.assertEqual(1687, counts["productionDisplayRows"])
        workbook = load_workbook(WORKBOOK, read_only=True)
        self.assertEqual(DISPLAY_HEADERS, [str(cell.value or "").strip() for cell in workbook[DISPLAY_SHEET][1]])

    def test_export_is_byte_deterministic_and_excludes_research_workbook_layers(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            first_path = Path(first)
            second_path = Path(second)
            export_to(first_path)
            export_to(second_path)
            first_files = sorted(path.relative_to(first_path) for path in first_path.rglob("*") if path.is_file())
            second_files = sorted(path.relative_to(second_path) for path in second_path.rglob("*") if path.is_file())
            self.assertEqual(first_files, second_files)
            self.assertNotIn(Path("assets/tissue_load_relations.csv"), first_files)
            self.assertIn(Path("assets/exercise_bootstrap.csv"), first_files)
            self.assertIn(Path("assets/metadata_display_labels_ko.csv"), first_files)
            self.assertIn(Path("res/values/metadata_display_catalog.xml"), first_files)
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
            output = Path(directory)
            export_to(output)
            runtime = (output / "assets/runtime_metadata.csv").read_text(encoding="utf-8")
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

    def test_approved_core_and_badminton_objective_cutover_assets_are_exact(self):
        self.assertEqual(CORE_APPROVED_SHA256, hashlib.sha256(CORE_APPROVED_SOURCE.read_bytes()).hexdigest())
        with (ASSETS / "badminton_relations.csv").open(encoding="utf-8-sig", newline="") as source:
            badminton_rows = list(csv.DictReader(source))
        core, objectives, rotation_audit = build_analysis_assets(badminton_rows)
        self.assertEqual(272, len(core))
        self.assertEqual(278, len(objectives))
        self.assertEqual(19, len(rotation_audit))
        self.assertEqual(2, sum(row["coreDirectTarget"] == "ANTI_ROTATION" and row["decision"] == "CREATE_EXPLICIT_OBJECTIVE" for row in rotation_audit))
        self.assertEqual(15, sum(row["coreDirectTarget"] == "ROTATION_GENERATION" and row["decision"] == "CREATE_EXPLICIT_OBJECTIVE" for row in rotation_audit))
        self.assertNotIn("ROTATION_POWER", {row["objectiveId"] for row in objectives})

    def test_display_resources_parse_and_have_consistent_locale_keys(self):
        def resource_keys(path: Path) -> set[str]:
            root = ET.parse(path).getroot()
            array = next(child for child in root if child.attrib.get("name") == "metadata_display_entries")
            return {"|".join((item.text or "").split("|")[:2]) for item in array}

        korean = resource_keys(RESOURCES / "values/metadata_display_catalog.xml")
        english = resource_keys(RESOURCES / "values-en/metadata_display_catalog.xml")
        self.assertEqual(korean, english)
        self.assertEqual(1823, len(korean))

    def test_display_routing_audit_is_current_and_complete(self):
        metrics = collect_display_routing(REPO)
        validate_display_routing(metrics)
        self.assertEqual(1687, metrics["reachableProductionPairCount"])
        self.assertEqual(1687, metrics["translatedReachableProductionPairCount"])
        self.assertEqual(136, metrics["expectedCompatibilityOnlyPairCount"])
        self.assertEqual(70, metrics["preRefactorRawCodeProneUiPathCount"])
        self.assertEqual(0, metrics["postRefactorRawCodeProneUiPathCount"])
        self.assertEqual(
            render_display_routing(metrics),
            (REPO / "docs/generated/metadata_display_coverage_audit.csv").read_text(
                encoding="utf-8"
            ),
        )

    def test_display_routing_validation_rejects_invalid_domain_and_missing_translation(self):
        valid = collect_display_routing(REPO)
        for field in (
            "invalidDisplayDomainCount",
            "missingReachableProductionPairCount",
        ):
            broken = dict(valid)
            broken[field] = 1
            with self.assertRaisesRegex(ValueError, field):
                validate_display_routing(broken)


if __name__ == "__main__":
    unittest.main()
