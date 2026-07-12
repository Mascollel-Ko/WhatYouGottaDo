from __future__ import annotations

import json
import platform
import subprocess
from pathlib import Path

import numpy as np
import scipy
import scipy.linalg


SEED = 20260712
ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "tools" / "time_series_reference" / "fixtures"


def source_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except Exception:
        return "unknown"


def as_list(value):
    return np.asarray(value, dtype=float).tolist()


def provenance(purpose: str) -> dict:
    return {
        "schema_version": 1,
        "generator_script_version": "phase-a-2",
        "python_version": platform.python_version(),
        "numpy_version": np.__version__,
        "scipy_version": scipy.__version__,
        "rng_seed": SEED,
        "source_commit": source_commit(),
        "purpose": purpose,
        "tolerance": 1e-7,
    }


def spd(rng: np.random.Generator, n: int, jitter: float = 0.8) -> np.ndarray:
    m = rng.normal(size=(n, n))
    return m.T @ m + np.eye(n) * jitter


def linear_algebra_fixture() -> dict:
    rng = np.random.default_rng(SEED)
    a_spd = spd(rng, 3)
    b_vec = rng.normal(size=3)
    b_mat = rng.normal(size=(3, 2))
    ls_a = np.array(
        [
            [1.0, 0.0, 1.0],
            [0.0, 1.0, 1.0],
            [1.0, 1.0, 2.0],
            [2.0, 1.0, 3.0],
        ]
    )
    ls_b = np.array([1.0, 2.0, 3.0, 4.0])
    svd_a = np.array(
        [
            [1.0, 0.0, 0.2],
            [0.0, 1.0, 0.3],
            [1.0, 1.0, 2.0],
            [2.0, 1.0, 3.5],
        ]
    )
    sym = np.array([[2.0, 0.5, 0.0], [0.5, 3.0, 0.4], [0.0, 0.4, 4.0]])
    gen_a = np.array([[2.0, 0.4], [0.4, 1.0]])
    gen_b = np.array([[1.5, 0.2], [0.2, 1.2]])
    s00 = spd(rng, 2)
    s01 = rng.normal(size=(2, 2))
    s10 = s01.T
    s11 = spd(rng, 2)
    johansen_a = s10 @ scipy.linalg.solve(s00, s01, assume_a="pos")

    sym_values, sym_vectors = scipy.linalg.eigh(sym)
    gen_values, gen_vectors = scipy.linalg.eigh(gen_a, gen_b)
    johansen_values, johansen_vectors = scipy.linalg.eigh(johansen_a, s11)
    singular = scipy.linalg.svdvals(svd_a)
    rank_deficient = np.array([[1.0, 2.0], [2.0, 4.0]])
    nearly_singular = np.diag([1.0, 1e-9])

    return {
        "provenance": provenance("Phase A linear algebra golden fixture"),
        "spd_solve": {
            "a": as_list(a_spd),
            "b": as_list(b_vec),
            "matrix_b": as_list(b_mat),
            "x": as_list(scipy.linalg.solve(a_spd, b_vec, assume_a="pos")),
            "matrix_x": as_list(scipy.linalg.solve(a_spd, b_mat, assume_a="pos")),
            "log_det": float(np.linalg.slogdet(a_spd)[1]),
        },
        "near_singular_spd": {
            "a": as_list(nearly_singular),
            "condition_number": float(np.linalg.cond(nearly_singular, 2)),
            "should_fail_spd": False,
        },
        "rank_deficient_condition_number": {
            "a": as_list(rank_deficient),
            "rank": int(np.linalg.matrix_rank(rank_deficient)),
            "condition_number_is_infinite": True,
        },
        "rank_deficient_least_squares": {
            "a": as_list(ls_a),
            "b": as_list(ls_b),
            "x": as_list(scipy.linalg.lstsq(ls_a, ls_b)[0]),
            "rank": int(np.linalg.matrix_rank(ls_a)),
        },
        "svd": {
            "a": as_list(svd_a),
            "singular_values": as_list(singular),
            "condition_number": float(np.linalg.cond(svd_a, 2)),
            "rank": int(np.linalg.matrix_rank(svd_a)),
        },
        "strict_spd": {
            "a": [[2.0, 0.1], [0.1, 1.0]],
        },
        "regularizable_numerical_noise": {
            "a": [[1.0, 0.0], [0.0, -1e-10]],
        },
        "materially_indefinite": {
            "a": [[-1.0, 0.0], [0.0, 1.0]],
        },
        "symmetric_eigen": {
            "a": as_list(sym),
            "values_desc": as_list(sym_values[::-1]),
            "vectors_desc": as_list(sym_vectors[:, ::-1].T),
        },
        "generalized_symmetric_eigen": {
            "a": as_list(gen_a),
            "b": as_list(gen_b),
            "values_desc": as_list(gen_values[::-1]),
            "vectors_desc": as_list(gen_vectors[:, ::-1].T),
        },
        "johansen_form": {
            "s00": as_list(s00),
            "s01": as_list(s01),
            "s10": as_list(s10),
            "s11": as_list(s11),
            "a": as_list(johansen_a),
            "values_desc": as_list(johansen_values[::-1]),
            "vectors_desc": as_list(johansen_vectors[:, ::-1].T),
        },
        "asymmetric_failure": {
            "a": [[1.0, 0.2], [0.4, 1.0]],
        },
        "non_pd_b_failure": {
            "a": [[1.0, 0.0], [0.0, 2.0]],
            "b": [[1.0, 0.0], [0.0, -1.0]],
        },
    }


def calendar_fixture() -> dict:
    return {
        "provenance": provenance("Phase A calendar grid golden fixture"),
        "weeks": ["2025-12-22", "2025-12-29", "2026-01-05", "2026-01-12"],
        "series": {
            "BADMINTON_TRAINING": [
                {"week": "2025-12-22", "value": 1.0},
                {"week": "2025-12-29", "value": 2.0},
                {"week": "2026-01-12", "value": 4.0},
            ],
            "FATIGUE_COMPOSITE": [
                {"week": "2025-12-22", "value": 0.0},
                {"week": "2026-01-05", "value": 3.0},
                {"week": "2026-01-12", "value": 5.0},
            ],
        },
        "structural_zero_metric": "BADMINTON_TRAINING",
        "expected_states": {
            "BADMINTON_TRAINING": ["OBSERVED_VALUE", "OBSERVED_VALUE", "STRUCTURAL_ZERO", "OBSERVED_VALUE"],
            "FATIGUE_COMPOSITE": ["OBSERVED_VALUE", "MISSING", "OBSERVED_VALUE", "OBSERVED_VALUE"],
        },
        "lag_checks": {
            "w04_is_not_w02_lag1": True,
            "w04_uses_w03_lag1_when_w03_structural_zero": True,
        },
        "difference_checks": {
            "missing_week_difference_rejected": True,
        },
        "horizon_checks": {
            "h2_from_w01_targets_w03": True,
        },
        "lifecycle_observations": {
            "BADMINTON_TRAINING": [
                {"week": "2025-12-22", "value": 1.0},
                {"week": "2025-12-29", "value": None, "state": "MISSING", "missing_reason": "user skipped entry"},
                {"week": "2026-01-12", "value": 4.0},
            ],
            "FATIGUE_COMPOSITE": [
                {"week": "2025-12-22", "value": 0.0},
                {"week": "2026-01-05", "value": None, "state": "VERSION_DISCONTINUITY"},
                {"week": "2026-01-12", "value": 5.0},
            ],
        },
        "lifecycle_metadata": {
            "BADMINTON_TRAINING": {
                "available_from_week": "2025-12-29",
                "structural_zero_allowed": True,
                "not_applicable_weeks": ["2026-01-19"],
            },
            "FATIGUE_COMPOSITE": {
                "available_from_week": "2025-12-22",
                "version_discontinuity_weeks": ["2026-01-05"],
            },
        },
    }


def main() -> None:
    FIXTURES.mkdir(parents=True, exist_ok=True)
    (FIXTURES / "phase_a_linear_algebra.json").write_text(
        json.dumps(linear_algebra_fixture(), indent=2, sort_keys=True),
        encoding="utf-8",
    )
    (FIXTURES / "phase_a_calendar_grid.json").write_text(
        json.dumps(calendar_fixture(), indent=2, sort_keys=True),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
