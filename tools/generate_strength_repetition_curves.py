#!/usr/bin/env python3
"""Generate reviewed monotone repetition-curve assets from published tables."""

from __future__ import annotations

import csv
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app/src/main/assets/strength_performance"
SOURCE = ASSET_DIR / "repetition_curve_source_v1.csv"
OUTPUT = ASSET_DIR / "repetition_curve_profiles_v1.csv"
MANIFEST = ASSET_DIR / "repetition_curve_manifest_v1.csv"
GENERATOR_VERSION = "strength-repetition-curve-generator-2.0.0"
ASSET_VERSION = "repetition-curve-assets-2.0.0"
SUPPORTED_REPS = range(1, 21)


def sha256(path: Path) -> str:
    canonical = path.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def pchip_slopes(xs: list[float], ys: list[float]) -> list[float]:
    """Fritsch-Carlson/PCHIP derivatives for strictly increasing x."""
    n = len(xs)
    h = [xs[i + 1] - xs[i] for i in range(n - 1)]
    delta = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
    if n == 2:
        return [delta[0], delta[0]]
    slopes = [0.0] * n
    for i in range(1, n - 1):
        if delta[i - 1] == 0.0 or delta[i] == 0.0 or delta[i - 1] * delta[i] < 0.0:
            slopes[i] = 0.0
        else:
            w1 = 2.0 * h[i] + h[i - 1]
            w2 = h[i] + 2.0 * h[i - 1]
            slopes[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])

    def endpoint(h0: float, h1: float, d0: float, d1: float) -> float:
        value = ((2.0 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        if value * d0 <= 0.0:
            return 0.0
        if d0 * d1 < 0.0 and abs(value) > abs(3.0 * d0):
            return 3.0 * d0
        return value

    slopes[0] = endpoint(h[0], h[1], delta[0], delta[1])
    slopes[-1] = endpoint(h[-1], h[-2], delta[-1], delta[-2])
    return slopes


def pchip(xs: list[float], ys: list[float], x: float) -> float:
    if not xs[0] <= x <= xs[-1]:
        raise ValueError(f"unsupported interpolation input: {x}")
    if x == xs[-1]:
        return ys[-1]
    slopes = pchip_slopes(xs, ys)
    index = next(i for i in range(len(xs) - 1) if xs[i] <= x <= xs[i + 1])
    h = xs[index + 1] - xs[index]
    t = (x - xs[index]) / h
    h00 = 2 * t**3 - 3 * t**2 + 1
    h10 = t**3 - 2 * t**2 + t
    h01 = -2 * t**3 + 3 * t**2
    h11 = t**3 - t**2
    return (
        h00 * ys[index]
        + h10 * h * slopes[index]
        + h01 * ys[index + 1]
        + h11 * h * slopes[index + 1]
    )


def load_source() -> dict[str, list[dict[str, str]]]:
    grouped: dict[str, list[dict[str, str]]] = {}
    with SOURCE.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            grouped.setdefault(row["curveProfileId"], []).append(row)
    return grouped


def generate() -> None:
    grouped = load_source()
    output_rows: list[tuple[str, int, float]] = []
    manifest_rows: list[dict[str, str]] = []
    for profile_id in sorted(grouped):
        rows = grouped[profile_id]
        # The physical identity q(1)=1 is exact; reviewed table rows follow it.
        points = [(1.0, 1.0)] + sorted(
            ((float(row["meanRepetitions"]), float(row["percent1Rm"]) / 100.0) for row in rows),
            key=lambda point: point[0],
        )
        xs = [point[0] for point in points]
        ys = [point[1] for point in points]
        previous = 1.0
        for repetitions in SUPPORTED_REPS:
            relative_load = 1.0 if repetitions == 1 else pchip(xs, ys, float(repetitions))
            if not 0.0 < relative_load <= previous <= 1.0:
                raise ValueError(f"non-monotone generated curve: {profile_id} at {repetitions}")
            output_rows.append((profile_id, repetitions, relative_load))
            previous = relative_load
        first = rows[0]
        manifest_rows.append(
            {
                "curveProfileId": profile_id,
                "curveVersion": ASSET_VERSION,
                "sourceCitation": "Nuzzo et al. Sports Medicine 2024; PMID 37792272; DOI 10.1007/s40279-023-01937-7",
                "sourceArtifactHash": first["sourceArtifactHash"],
                "sourceTableChecksum": sha256(SOURCE),
                "generatorVersion": GENERATOR_VERSION,
                "reviewedAt": "2026-07-26",
                "supportedRepRange": "1..20",
                "sourceExerciseScope": first["sourceExerciseScope"],
                "sourceModelDescription": "Published mean repetitions-to-failure table inverted with monotone PCHIP plus exact q(1)=1 identity anchor",
                "runtimeAssetChecksum": "PENDING",
            }
        )

    with OUTPUT.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(("curveProfileId", "repetitions", "relativeLoad"))
        for profile_id, repetitions, relative_load in output_rows:
            writer.writerow((profile_id, repetitions, f"{relative_load:.12f}"))
    output_hash = sha256(OUTPUT)
    for row in manifest_rows:
        row["runtimeAssetChecksum"] = output_hash
    with MANIFEST.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(manifest_rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(manifest_rows)
    validate()


def validate() -> None:
    with OUTPUT.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    by_profile: dict[str, list[float]] = {}
    for row in rows:
        by_profile.setdefault(row["curveProfileId"], []).append(float(row["relativeLoad"]))
    if set(by_profile) != {
        "reps_curve.bench_press.v1",
        "reps_curve.general_resistance.v1",
        "reps_curve.leg_press.v1",
    }:
        raise ValueError("canonical curve profile set mismatch")
    for profile_id, values in by_profile.items():
        if len(values) != len(SUPPORTED_REPS) or values[0] != 1.0:
            raise ValueError(f"invalid generated profile: {profile_id}")
        if any(not 0.0 < value <= 1.0 for value in values):
            raise ValueError(f"out-of-range generated profile: {profile_id}")
        if any(values[i + 1] > values[i] for i in range(len(values) - 1)):
            raise ValueError(f"non-monotone generated profile: {profile_id}")
    with MANIFEST.open(newline="", encoding="utf-8") as stream:
        manifest = list(csv.DictReader(stream))
    if any(row["runtimeAssetChecksum"] != sha256(OUTPUT) for row in manifest):
        raise ValueError("runtime curve checksum mismatch")
    print(f"validated {len(by_profile)} profiles; sha256={sha256(OUTPUT)}")


if __name__ == "__main__":
    generate()
