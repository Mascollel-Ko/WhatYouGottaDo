#!/usr/bin/env python3
"""Prepare user-approved exercise images as deterministic stable-key assets."""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageOps


MAX_EDGE = 384
APPROVED_DUPLICATE = {
    "ex_f6703b06": "덤벨 프로네이션 수피네이션_ex_f6703b06.png",
}
FALLBACK_ASSETS = {
    "ex_d9084b5e": "exercise_images/local_downloads/one_arm_inverted_row.png",
    "ex_dd2f732e": "exercise_images/local_downloads/reverse_curl.png",
    "ex_e159d15a": "exercise_images/local_downloads/inverted_row.png",
    "ex_e994008a": "exercise_images/local_downloads/preacher_curl.png",
    "pull_up": "exercise_images/local_downloads/pull_up.png",
    "single_leg_rdl": "exercise_images/local_downloads/single_leg_rdl.png",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo = args.repo.resolve()
    source = args.source.resolve()
    identity_path = repo / "app/src/main/assets/metadata/canonical_v1/identity_master.csv"
    output_dir = repo / "app/src/main/assets/exercise_images/stable_key"
    mapping_path = repo / "app/src/main/assets/exercise_image_mapping.csv"

    with identity_path.open(encoding="utf-8-sig", newline="") as handle:
        identities = list(csv.DictReader(handle))
    names = {row["stableKey"]: row["exerciseName"] for row in identities}
    keys = tuple(names)

    candidates: dict[str, list[Path]] = defaultdict(list)
    for image_path in sorted(path for path in source.iterdir() if path.is_file()):
        matches = sorted(
            (key for key in keys if image_path.stem.endswith("_" + key)),
            key=lambda key: (len(key), key),
            reverse=True,
        )
        if not matches:
            raise ValueError(f"No canonical stableKey suffix: {image_path.name}")
        candidates[matches[0]].append(image_path)

    selected: dict[str, Path] = {}
    for stable_key, paths in candidates.items():
        if len(paths) == 1:
            selected[stable_key] = paths[0]
            continue
        approved_name = APPROVED_DUPLICATE.get(stable_key)
        approved = next((path for path in paths if path.name == approved_name), None)
        if approved is None:
            joined = ", ".join(path.name for path in paths)
            raise ValueError(f"Unapproved duplicate stableKey {stable_key}: {joined}")
        selected[stable_key] = approved

    overlap = selected.keys() & FALLBACK_ASSETS.keys()
    if overlap:
        raise ValueError(f"Fallback assets unexpectedly received replacements: {sorted(overlap)}")

    output_dir.mkdir(parents=True, exist_ok=True)
    for old_file in output_dir.glob("*.png"):
        old_file.unlink()
    for stable_key, source_path in sorted(selected.items()):
        with Image.open(source_path) as image:
            prepared = ImageOps.exif_transpose(image)
            prepared.thumbnail((MAX_EDGE, MAX_EDGE), Image.Resampling.LANCZOS)
            if prepared.mode not in {"RGB", "RGBA"}:
                prepared = prepared.convert("RGBA")
            prepared.save(output_dir / f"{stable_key}.png", format="PNG", optimize=True, compress_level=9)

    mapping_rows = [
        {
            "exercise_name": names[stable_key],
            "stable_key": stable_key,
            "image_asset_name": f"exercise_images/stable_key/{stable_key}.png",
            "mapping_confidence": "HIGH",
            "needs_review": "0",
            "reason": "user_approved_stable_key_asset",
        }
        for stable_key in sorted(selected)
    ]
    mapping_rows.extend(
        {
            "exercise_name": names[stable_key],
            "stable_key": stable_key,
            "image_asset_name": asset_path,
            "mapping_confidence": "HIGH",
            "needs_review": "0",
            "reason": "retained_existing_image_no_new_asset",
        }
        for stable_key, asset_path in sorted(FALLBACK_ASSETS.items())
    )
    mapping_rows.sort(key=lambda row: row["stable_key"])
    with mapping_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=mapping_rows[0].keys(), lineterminator="\n")
        writer.writeheader()
        writer.writerows(mapping_rows)

    print(f"prepared={len(selected)} fallback={len(FALLBACK_ASSETS)} mappings={len(mapping_rows)}")


if __name__ == "__main__":
    main()
