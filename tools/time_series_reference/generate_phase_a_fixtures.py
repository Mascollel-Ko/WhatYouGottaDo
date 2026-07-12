from __future__ import annotations

import json
import platform
import subprocess
from pathlib import Path

import numpy as np
import scipy
import scipy.linalg
import scipy.special
import statsmodels
from statsmodels.tsa.stattools import adfuller, kpss


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
        "generator_script_version": "phase-a-6",
        "python_version": platform.python_version(),
        "numpy_version": np.__version__,
        "scipy_version": scipy.__version__,
        "statsmodels_version": statsmodels.__version__,
        "adf_parameters": {"regression": "c", "autolag": "AIC"},
        "kpss_parameters": {"regression": "c", "nlags": "auto"},
        "rng_seed": SEED,
        "source_commit": source_commit(),
        "purpose": purpose,
        "tolerance": 1e-7,
    }


def ar1(rng: np.random.Generator, phi: float, n: int, sigma: float = 1.0) -> np.ndarray:
    values = np.zeros(n)
    for index in range(1, n):
        values[index] = phi * values[index - 1] + rng.normal(scale=sigma)
    return values


def walk(rng: np.random.Generator, n: int, drift: float = 0.0) -> np.ndarray:
    return np.cumsum(rng.normal(size=n) + drift)


def diagnostic_result(values: np.ndarray) -> dict:
    result: dict[str, object] = {"values": as_list(values)}
    try:
        adf_stat, adf_p, adf_lag, adf_nobs, adf_crit, adf_aic = adfuller(values, regression="c", autolag="AIC")
        result["adf"] = {
            "status": "OK",
            "statistic": float(adf_stat),
            "p_value": float(adf_p),
            "selected_lag": int(adf_lag),
            "effective_nobs": int(adf_nobs),
            "critical_values": {key: float(value) for key, value in adf_crit.items()},
            "best_aic": float(adf_aic),
        }
    except Exception as error:
        result["adf"] = {"status": "INCONCLUSIVE", "reason": str(error)}
    try:
        kpss_stat, kpss_p, kpss_lag, kpss_crit = kpss(values, regression="c", nlags="auto")
        result["kpss"] = {
            "status": "OK",
            "statistic": float(kpss_stat),
            "p_value": float(kpss_p),
            "selected_lag": int(kpss_lag),
            "critical_values": {key: float(value) for key, value in kpss_crit.items()},
            "p_value_boundary": "table-boundary" if kpss_p in (0.01, 0.1) else None,
        }
    except Exception as error:
        result["kpss"] = {"status": "INCONCLUSIVE", "reason": str(error)}
    return result


def integration_reference_fixture() -> dict:
    rng = np.random.default_rng(SEED)
    n = 80
    random_walk = walk(np.random.default_rng(2), n, drift=0.05)
    fixtures = {
        "stationary_ar_03": ar1(rng, 0.3, n),
        "stationary_ar_08": ar1(rng, 0.8, n),
        "near_unit_root_ar_095": ar1(rng, 0.95, n),
        "random_walk": random_walk,
        "random_walk_with_drift": walk(np.random.default_rng(3), n, drift=0.2),
        "stationary_first_difference_of_random_walk": np.diff(random_walk),
        "constant_series": np.ones(n) * 3.0,
        "near_constant_series": np.ones(n) + np.arange(n) * 1e-13,
        "trend_stationary_constant_only": np.arange(n) * 0.05 + rng.normal(scale=0.2, size=n),
        "insufficient_31_week_segment": ar1(rng, 0.3, 31),
        "exactly_32_week_segment": ar1(rng, 0.3, 32),
        "two_segments_i0_left": ar1(rng, 0.3, 40),
        "two_segments_i0_right": ar1(rng, 0.4, 40),
        "two_segments_i1_left": walk(np.random.default_rng(2), 40, drift=0.05),
        "two_segments_i1_right": walk(np.random.default_rng(4), 40, drift=0.05),
        "two_segments_disagree_left": ar1(rng, 0.3, 40),
        "two_segments_disagree_right": walk(rng, 40),
        "singular_adf_regression": np.array(([1.0, 2.0] * 40), dtype=float),
        "lifecycle_separated_left": ar1(rng, 0.3, 40),
        "lifecycle_separated_right": ar1(rng, 0.3, 40),
    }
    return {
        "provenance": provenance("Phase A statsmodels ADF/KPSS integration fixture"),
        "segment_policy": {
            "minimum_integration_segment_weeks": 32,
            "segment_aggregation": "all eligible segments must agree",
        },
        "fixtures": {name: diagnostic_result(values) for name, values in fixtures.items()},
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
    exact_singular_psd = np.array([[1.0, 1.0], [1.0, 1.0]])
    high_condition_spd = np.diag([1.0, 1e-13])
    numerically_rank_deficient = np.diag([1.0, 1e-16])
    nearly_singular = np.diag([1.0, 1e-9])
    regularized_b = np.diag([1.0, -1e-9])
    regularized_b_effective = regularized_b + np.eye(2) * (1e-9 + 1e-11)
    regularized_values, regularized_vectors = scipy.linalg.eigh(gen_a, regularized_b_effective)

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
        "high_condition_spd": {
            "a": as_list(high_condition_spd),
            "condition_number": float(np.linalg.cond(high_condition_spd, 2)),
        },
        "numerically_rank_deficient": {
            "a": as_list(numerically_rank_deficient),
            "rank": int(np.linalg.matrix_rank(numerically_rank_deficient, tol=1e-14)),
        },
        "exact_singular_psd": {
            "a": as_list(exact_singular_psd),
            "rank": int(np.linalg.matrix_rank(exact_singular_psd)),
        },
        "regularizable_numerical_noise": {
            "a": [[1.0, 0.0], [0.0, -1e-9]],
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
        "regularized_generalized_symmetric_eigen": {
            "a": as_list(gen_a),
            "b": as_list(regularized_b),
            "effective_b": as_list(regularized_b_effective),
            "values_desc": as_list(regularized_values[::-1]),
            "vectors_desc": as_list(regularized_vectors[:, ::-1].T),
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
        "johansen_rank_deficient_s00": {
            "s00": as_list(rank_deficient),
            "s01": as_list(s01),
            "s10": as_list(s10),
            "s11": as_list(s11),
        },
        "johansen_rank_deficient_s11": {
            "s00": as_list(s00),
            "s01": as_list(s01),
            "s10": as_list(s10),
            "s11": as_list(rank_deficient),
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
            "final_week_horizon_is_excluded": True,
        },
        "mixed_weekday_dates": {
            "raw_tuesday": "2026-01-06",
            "canonical_monday": "2026-01-05",
        },
        "metric_bounds": {
            "unselected_metric_week": "2024-01-01",
            "selected_start_week": "2025-12-22",
            "selected_end_week": "2026-01-12",
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
        "conflicting_observations": [
            {"week": "2026-01-05", "value": 10.0},
            {"week": "2026-01-05", "value": 20.0}
        ],
        "state_value_invalid_cases": [
            {"state": "OBSERVED_VALUE", "value": None},
            {"state": "OBSERVED_VALUE", "value": "NaN"},
            {"state": "STRUCTURAL_ZERO", "value": 3.0},
            {"state": "VERSION_DISCONTINUITY", "value": 10.0}
        ],
        "activation_policy_cases": {
            "non_structural_before_first_observation": "MISSING",
            "first_observation_policy_before_activation": "PRE_METRIC_CREATION",
            "explicit_metadata_before_activation": "PRE_METRIC_CREATION",
        },
        "quality_summary_denominators": {
            "raw_missing_rate": "missing / applicable_active_weeks",
            "unusable_rate": "(missing + version_discontinuity + conflict + transformation_failure) / model_eligible_weeks",
            "coverage_rate": "usable / model_eligible_weeks",
            "exclude_from_denominator": ["PRE_METRIC_CREATION", "NOT_APPLICABLE"],
        },
        "transformation_calendar_contract": {
            "all_i0_preserves_first_week": True,
            "mixed_transformations_preserve_full_week_vector": True,
            "first_difference_first_week_state": "MISSING",
        },
        "prepared_series_contract": {
            "weeks_are_iso_mondays": True,
            "weeks_are_continuous": True,
            "candidate_screening_uses_prepared_series": True,
        },
    }


def prepared_pipeline_contract_fixture() -> dict:
    return {
        "provenance": provenance("Phase A prepared-data pipeline contract fixture"),
        "quality_summary_overlap": {
            "eligible_cells": [
                {"week": "2026-01-05", "state": "MISSING", "transformation_failure": True},
                {"week": "2026-01-12", "state": "VERSION_DISCONTINUITY", "transformation_failure": True},
                {"week": "2026-01-19", "state": "CONFLICT", "transformation_failure": True},
                {"week": "2026-01-26", "state": "OBSERVED_VALUE", "transformation_failure": False},
            ],
            "excluded_from_denominator": [
                {"week": "2026-02-02", "state": "PRE_METRIC_CREATION"},
                {"week": "2026-02-09", "state": "NOT_APPLICABLE"},
            ],
            "expected": {
                "model_eligible_week_count": 4,
                "usable_count": 1,
                "unusable_count": 3,
                "transformation_failure_count": 3,
                "unusable_rate": 0.75,
                "coverage_rate": 0.25,
            },
        },
        "lifecycle_fingerprint_contract": {
            "preserve_under": ["restriction", "stationarization", "candidate_screening", "prepared_system_creation"],
            "fingerprint_fields": [
                "available_from_week",
                "available_until_week",
                "structural_zero_allowed",
                "activation_policy",
                "not_applicable_weeks",
                "version_discontinuity_weeks",
                "version_discontinuity_ranges",
                "source",
                "source_version",
                "registry_version",
                "derived_from_metric",
                "inference_policy",
                "metadata_version",
            ],
        },
        "revision_resolution": {
            "rules": [
                "identical candidates merge regardless of input order",
                "heterogeneous authoritative revision schemes produce CONFLICT",
                "same highest revision with different values produces CONFLICT",
                "same highest revision with identical values merges",
                "ordinary observation time is not revision order",
                "version strings are not lexically ordered",
            ],
            "permutation_invariant": True,
        },
        "row_identity": {
            "policy": "COMMON_USABLE_ROWS",
            "fingerprint_inputs": [
                "canonical_source_week",
                "target_week",
                "lag_weeks",
                "ordered_metric_ids",
                "metric_roles",
                "transformations",
                "lag",
                "requested_horizon_set",
                "horizon_policy",
                "preparation_version",
                "row_policy",
            ],
            "candidate_and_baseline_scores_use_same_source_weeks": True,
            "role_requirements": {
                "SHOCK_SOURCE": ["source", "shock_lags"],
                "ENDOGENOUS_STATE": ["source", "var_lags"],
                "RESPONSE": ["actual_requested_horizon_target"],
                "CONTEMPORANEOUS_CONTROL": ["source_only"],
                "LAGGED_CONTROL": ["source", "declared_lags"],
            },
        },
        "transformation_plan_contract": {
            "created_before_candidate_selection": True,
            "optional_inconclusive_policy": "EXCLUDE_FROM_ELIGIBLE_CANDIDATES",
            "required_inconclusive_policy": "FAIL_STRICT_PREPARATION",
            "candidate_scoring_uses_transformed_prepared_series": True,
            "level_fallback_after_failed_transformation": False,
            "fingerprint_inputs": ["metric_id", "integration_order", "transformation", "decision_reason", "plan_version"],
        },
        "scaling_sample_contract": {
            "standardization_sample": "validated_training_rows_only",
            "excluded_weeks_affect_scaling": False,
            "test_rows_affect_training_scaling": False,
            "candidate_and_baseline_share_common_source_weeks": True,
        },
        "restricted_view_identity": {
            "source_preparation_fingerprint_preserved": True,
            "lifecycle_fingerprint_preserved": True,
            "quality_summary_recomputed_for_view": True,
            "shallow_alignment_copy_allowed": False,
        },
        "lifecycle_semantic_validation": {
            "structural_zero_requires_policy": True,
            "observed_value_rejected_in_not_applicable_range": True,
            "observed_value_rejected_in_version_discontinuity_range": True,
            "duplicate_ranges_normalize_in_fingerprint": True,
            "overlapping_version_discontinuity_ranges_rejected": True,
        },
        "strict_single_authority_contract": {
            "stage_order": [
                "RawTimeSeriesInput",
                "CanonicalCalendar",
                "LifecycleValidatedLevelSeries",
                "IntegrationOrderAssessment",
                "CanonicalTransformationPlan",
                "EstimatorRepresentationPlan",
                "TransformedPreparedSeries",
                "PreparedAnalysisContext",
                "PreparedEstimatorView",
                "PreparedRowPlan",
                "PreparedScalingPlan",
            ],
            "segment_boundaries": [
                "MISSING",
                "PRE_METRIC_CREATION",
                "NOT_APPLICABLE",
                "VERSION_DISCONTINUITY",
                "CONFLICT",
                "ACTIVATION_BOUNDARY",
                "AVAILABILITY_END",
            ],
            "segment_aggregation": "ALL_ELIGIBLE_SEGMENTS_MUST_AGREE",
            "estimator_representations": {
                "BVAR": "CANONICAL_STATIONARY",
                "BLP": "CANONICAL_RESPONSE_PLUS_RESPONSE_SCALE_PLAN",
                "JOHANSEN": "VALIDATED_LEVEL",
                "VECM": "VALIDATED_LEVEL_AND_ALIGNED_FIRST_DIFFERENCE",
            },
            "response_uncertainty_policy": "TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS",
            "shock_posterior": {
                "minimum_accepted_draws": 2,
                "draw_ids_preserved": True,
                "draw_weights_validated": True,
                "single_mean_shock_allowed": False,
            },
            "row_authority": "RowPlanner",
            "scaling_authority": "ScalingPlanner",
            "optional_statistical_ranking_phase": "PHASE_E",
        },
    }


def phase_b_design(values: np.ndarray, p: int, p_max: int) -> tuple[np.ndarray, np.ndarray]:
    y = values[p_max:, :]
    rows = []
    for index in range(p_max, values.shape[0]):
        row = [1.0]
        for lag in range(1, p + 1):
            row.extend(values[index - lag, :].tolist())
        rows.append(row)
    return y, np.array(rows, dtype=float)


def phase_b_prior(k: int, p: int, lam: float, intercept_variance: float = 100.0, decay: float = 2.0):
    q = 1 + k * p
    b0 = np.zeros((q, k), dtype=float)
    diagonal = np.empty(q, dtype=float)
    diagonal[0] = intercept_variance
    for index in range(1, q):
        lag = ((index - 1) // k) + 1
        diagonal[index] = lam * lam / (lag ** (2.0 * decay))
    v0 = np.diag(diagonal)
    v0_precision = np.diag(1.0 / diagonal)
    nu0 = k + 2.0
    s0 = np.eye(k)
    return b0, v0, v0_precision, nu0, s0


def phase_b_posterior(values: np.ndarray, p: int, p_max: int, lam: float) -> dict:
    y, x = phase_b_design(values, p, p_max)
    n, k = y.shape
    b0, v0, v0_precision, nu0, s0 = phase_b_prior(k, p, lam)
    precision = v0_precision + x.T @ x
    vn = scipy.linalg.solve(precision, np.eye(precision.shape[0]), assume_a="pos")
    bn = scipy.linalg.solve(precision, v0_precision @ b0 + x.T @ y, assume_a="pos")
    residual = y - x @ bn
    delta = bn - b0
    sn = s0 + residual.T @ residual + delta.T @ v0_precision @ delta
    sn = (sn + sn.T) / 2.0
    nu_n = nu0 + n
    log_det_v0 = np.linalg.slogdet(v0)[1]
    log_det_vn = np.linalg.slogdet(vn)[1]
    log_det_s0 = np.linalg.slogdet(s0)[1]
    log_det_sn = np.linalg.slogdet(sn)[1]
    log_ml = (
        -0.5 * n * k * np.log(np.pi)
        + 0.5 * k * (log_det_vn - log_det_v0)
        + 0.5 * nu0 * log_det_s0
        - 0.5 * nu_n * log_det_sn
        + scipy.special.multigammaln(nu_n / 2.0, k)
        - scipy.special.multigammaln(nu0 / 2.0, k)
    )
    return {
        "p": p,
        "lambda": lam,
        "y": as_list(y),
        "x": as_list(x),
        "b0": as_list(b0),
        "v0": as_list(v0),
        "v0_precision": as_list(v0_precision),
        "vn": as_list(vn),
        "bn": as_list(bn),
        "sn": as_list(sn),
        "nu0": float(nu0),
        "nuN": float(nu_n),
        "log_det_v0": float(log_det_v0),
        "log_det_vn": float(log_det_vn),
        "log_det_s0": float(log_det_s0),
        "log_det_sn": float(log_det_sn),
        "multigammaln_nuN_half": float(scipy.special.multigammaln(nu_n / 2.0, k)),
        "multigammaln_nu0_half": float(scipy.special.multigammaln(nu0 / 2.0, k)),
        "log_marginal_likelihood": float(log_ml),
    }


def phase_b_fixture() -> dict:
    rng = np.random.default_rng(SEED + 200)
    n = 64
    k2 = np.zeros((n, 2), dtype=float)
    eps = rng.multivariate_normal([0.0, 0.0], [[1.0, 0.35], [0.35, 0.8]], size=n)
    for index in range(1, n):
        k2[index] = np.array([[0.45, 0.10], [-0.15, 0.35]]) @ k2[index - 1] + eps[index]
    k3 = np.zeros((n, 3), dtype=float)
    eps3 = rng.multivariate_normal([0.0, 0.0, 0.0], [[1.0, 0.2, 0.1], [0.2, 0.9, 0.25], [0.1, 0.25, 0.7]], size=n)
    for index in range(2, n):
        k3[index] = (
            np.array([[0.40, 0.05, 0.0], [-0.10, 0.30, 0.08], [0.05, -0.08, 0.25]]) @ k3[index - 1]
            + np.array([[0.10, 0.02, 0.0], [0.0, 0.08, 0.02], [0.03, 0.0, 0.06]]) @ k3[index - 2]
            + eps3[index]
        )
    p_max = 2
    training = k2[p_max:, :]
    mean = training.mean(axis=0)
    scale = training.std(axis=0, ddof=1)
    standardized_k2 = (k2 - mean) / scale
    models = [phase_b_posterior(standardized_k2, p, p_max, lam) for p in [1, 2] for lam in [0.1, 0.2]]
    log_weights = np.array([model["log_marginal_likelihood"] for model in models], dtype=float)
    probabilities = np.exp(log_weights - scipy.special.logsumexp(log_weights))
    for model, probability in zip(models, probabilities):
        model["posterior_probability"] = float(probability)
    fixed_b = np.array([[0.05, -0.02], [0.30, 0.10], [-0.15, 0.25]])
    fixed_sigma = np.array([[1.2, 0.4], [0.4, 0.9]])
    fixed_y, fixed_x = phase_b_design(standardized_k2, 1, p_max)
    residuals = fixed_y - fixed_x @ fixed_b
    chol = scipy.linalg.cholesky(fixed_sigma, lower=True)
    shocks = scipy.linalg.solve_triangular(chol, residuals.T, lower=True).T
    reversed_values = standardized_k2[:, [1, 0]]
    reversed_y, reversed_x = phase_b_design(reversed_values, 1, p_max)
    reversed_residuals = reversed_y - reversed_x @ fixed_b[:, [1, 0]]
    reversed_chol = scipy.linalg.cholesky(fixed_sigma[[1, 0]][:, [1, 0]], lower=True)
    reversed_shocks = scipy.linalg.solve_triangular(reversed_chol, reversed_residuals.T, lower=True).T
    return {
        "provenance": provenance("Phase B conjugate BVAR posterior fixture") | {
            "generator_script_version": "phase-b-1",
            "matrix_orientation": "Y=n_by_K; X=n_by_(1+Kp); B=q_by_K; lag-major coefficient columns",
            "inverse_wishart_parameterization": "Sigma ~ IW(nu, S); E[Sigma]=S/(nu-K-1)",
            "model_prior": "uniform over canonical (p, lambda)",
            "lambda_grid": [0.1, 0.2],
            "lag_grid": [1, 2],
            "tolerance": 1e-8,
        },
        "raw_k2": as_list(k2),
        "standardization": {"mean": as_list(mean), "scale": as_list(scale)},
        "design_k2_p1": {"y": as_list(phase_b_design(standardized_k2, 1, p_max)[0]), "x": as_list(phase_b_design(standardized_k2, 1, p_max)[1])},
        "design_k3_p2": {"y": as_list(phase_b_design(k3, 2, 2)[0]), "x": as_list(phase_b_design(k3, 2, 2)[1])},
        "posterior_models": models,
        "marginal_lag": {
            "1": float(sum(model["posterior_probability"] for model in models if model["p"] == 1)),
            "2": float(sum(model["posterior_probability"] for model in models if model["p"] == 2)),
        },
        "marginal_lambda": {
            "0.1": float(sum(model["posterior_probability"] for model in models if model["lambda"] == 0.1)),
            "0.2": float(sum(model["posterior_probability"] for model in models if model["lambda"] == 0.2)),
        },
        "fixed_structural_shock": {
            "b": as_list(fixed_b),
            "sigma": as_list(fixed_sigma),
            "cholesky": as_list(chol),
            "residuals": as_list(residuals),
            "shocks": as_list(shocks),
            "reconstruction": as_list((chol @ shocks.T).T),
        },
        "variable_order_sensitivity": {
            "baseline_first_shock": as_list(shocks[:, 0]),
            "reversed_first_shock": as_list(reversed_shocks[:, 0]),
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
    (FIXTURES / "phase_a_prepared_pipeline_contract.json").write_text(
        json.dumps(prepared_pipeline_contract_fixture(), indent=2, sort_keys=True),
        encoding="utf-8",
    )
    (FIXTURES / "phase_a_integration_reference.json").write_text(
        json.dumps(integration_reference_fixture(), indent=2, sort_keys=True),
        encoding="utf-8",
    )
    (FIXTURES / "phase_b_bvar_reference.json").write_text(
        json.dumps(phase_b_fixture(), indent=2, sort_keys=True),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
