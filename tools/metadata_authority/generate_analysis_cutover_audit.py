from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from analysis_cutover_authority import (
    BADMINTON_OBJECTIVE_HEADERS,
    CORE_HEADERS,
    ROTATION_AUDIT_HEADERS,
    build_analysis_assets,
)
from authority_common import write_csv


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/metadata/canonical_v1"
AUDIT = ROOT / "docs/audits/core_badminton_rotation_objective_audit.csv"


def render(output: Path) -> tuple[Path, Path, Path]:
    core, objectives, audit = build_analysis_assets()
    core_path = output / "core_relations.csv"
    objective_path = output / "badminton_objective_relations.csv"
    audit_path = output / "core_badminton_rotation_objective_audit.csv"
    write_csv(core_path, CORE_HEADERS, core)
    write_csv(objective_path, BADMINTON_OBJECTIVE_HEADERS, objectives)
    write_csv(audit_path, ROTATION_AUDIT_HEADERS, audit)
    return core_path, objective_path, audit_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as directory:
        generated = render(Path(directory))
        expected = (
            ASSETS / "core_relations.csv",
            ASSETS / "badminton_objective_relations.csv",
            AUDIT,
        )
        if args.check:
            stale = [str(target) for source, target in zip(generated, expected) if not target.exists() or source.read_bytes() != target.read_bytes()]
            if stale:
                raise ValueError(f"Analysis cutover authority is stale: {stale}")
            print("Analysis cutover authority and rotation audit are current.")
        else:
            for source, target in zip(generated, expected):
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
            print("Generated analysis cutover authority and rotation audit.")


if __name__ == "__main__":
    main()
