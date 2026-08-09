#!/usr/bin/env python3
"""Deterministically audit metadata display routing and Korean coverage."""

from __future__ import annotations

import argparse
import csv
import io
from pathlib import Path


CATALOGUE_MODES = {
    "METADATA_DISPLAY_CATALOGUE",
    "METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH",
}
USER_VISIBLE_DISPOSITIONS = {"PRODUCTION_UI", "ADVANCED_EDITOR"}


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def invalid_mode(row: dict[str, str]) -> bool:
    kind = row["valueKind"]
    mode = row["localizationMode"]
    if kind in {"INTEGER", "DECIMAL"}:
        return mode != "LOCALE_FORMATTER"
    if kind == "DURATION":
        return mode not in {"LOCALE_FORMATTER", "NEVER_DISPLAY"}
    if kind == "BOOLEAN":
        return mode != "ANDROID_STRING_RESOURCE"
    if kind == "FREE_TEXT":
        return mode not in {
            "USER_TEXT_PASSTHROUGH",
            "METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH",
        }
    if kind == "EXERCISE_REFERENCE":
        return mode != "EXERCISE_NAME_CATALOGUE"
    if kind == "INTERNAL_IDENTIFIER":
        return mode != "NEVER_DISPLAY"
    return mode not in {"METADATA_DISPLAY_CATALOGUE", "NEVER_DISPLAY"}


def collect(root: Path) -> dict[str, int]:
    contract = rows(root / "docs/generated/metadata_field_display_contract.csv")
    labels = rows(
        root / "app/src/main/assets/metadata/canonical_v1/metadata_display_labels_ko.csv"
    )
    inventory = rows(root / "docs/metadata_authority/metadata_display_inventory.csv")
    source_inventory = rows(
        root / "docs/generated/metadata_display_routing_inventory.csv"
    )

    domains = {row["displayField"] for row in labels}
    catalogue_fields = [
        row
        for row in contract
        if row["localizationMode"] in CATALOGUE_MODES
        and row["displayDisposition"] in USER_VISIBLE_DISPOSITIONS
    ]
    production = [row for row in labels if row["displayScope"] == "PRODUCTION"]
    compatibility = [row for row in labels if row["displayScope"] == "SEARCH_ONLY"]
    usage = {
        (row["displayField"], row["canonicalCode"]): int(row["productionUsageCount"])
        for row in inventory
    }

    return {
        "userVisibleCatalogueFieldCount": len(catalogue_fields),
        "hybridCatalogueUserTextFieldCount": sum(
            row["localizationMode"]
            == "METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH"
            for row in catalogue_fields
        ),
        "reachableProductionPairCount": len(production),
        "translatedReachableProductionPairCount": sum(
            bool(row["koreanLabel"].strip()) for row in production
        ),
        "missingReachableProductionPairCount": sum(
            not row["koreanLabel"].strip() for row in production
        ),
        "invalidDisplayDomainCount": sum(
            row["localizationMode"] in CATALOGUE_MODES
            and row["displayDomain"] not in domains
            for row in contract
        ),
        "invalidLocalizationModeForValueKindCount": sum(
            invalid_mode(row) for row in contract
        ),
        "expectedCompatibilityOnlyPairCount": len(compatibility),
        "unexpectedOrphanProductionPairCount": sum(
            usage.get((row["displayField"], row["canonicalCode"]), 0) == 0
            for row in production
        ),
        "preRefactorRawCodeProneUiPathCount": sum(
            int(row["baselineDirectDomainDecisionCount"]) for row in source_inventory
        ),
        "postRefactorRawCodeProneUiPathCount": sum(
            (root / row["path"])
            .read_text(encoding="utf-8")
            .count("MetadataDisplayField.")
            for row in source_inventory
        ),
    }


def render(metrics: dict[str, int]) -> str:
    output = io.StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(metrics.keys())
    writer.writerow(metrics.values())
    return output.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    destination = root / "docs/generated/metadata_display_coverage_audit.csv"
    content = render(collect(root))
    if args.write:
        destination.write_text(content, encoding="utf-8", newline="")
    elif destination.read_text(encoding="utf-8") != content:
        raise SystemExit(f"Stale metadata display coverage audit: {destination}")
    print(content, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
