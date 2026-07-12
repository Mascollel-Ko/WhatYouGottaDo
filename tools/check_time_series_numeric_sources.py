from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app" / "src" / "main" / "java" / "com" / "training" / "trackplanner" / "analysis" / "lab"
STRICT_PIPELINE = TARGET / "pipeline"
STRICT_INGESTION = STRICT_PIPELINE / "StrictTimeSeriesIngestion.kt"
STRICT_CONTEXT = STRICT_PIPELINE / "PreparedAnalysisContext.kt"
STRICT_DIAGNOSTICS = STRICT_PIPELINE / "StrictTimeSeriesDiagnostics.kt"
STRICT_REPRESENTATION = STRICT_PIPELINE / "StrictTimeSeriesRepresentation.kt"
STRICT_ROW_SCALING = STRICT_PIPELINE / "PreparedRowAndScalingPlans.kt"
STRICT_VIEWS = STRICT_PIPELINE / "PreparedEstimatorViews.kt"
STRICT_FUTURE = STRICT_PIPELINE / "FutureEstimatorBoundaries.kt"
WRAPPER = TARGET / "StableLinearAlgebra.kt"
MODELS = TARGET / "BayesianTimeSeriesModels.kt"
SELECTOR = TARGET / "EndogenousVariableSelector.kt"
SUPPORT = TARGET / "BayesianTimeSeriesSupport.kt"
ANALYZER = TARGET / "BayesianTimeSeriesAnalyzer.kt"
LP = TARGET / "BayesianLocalProjectionEstimator.kt"
DYNAMIC = TARGET / "BayesianDynamicEstimators.kt"

FORBIDDEN = [
    re.compile(r"MatrixUtils\.inverse"),
    re.compile(r"\.getInverse\s*\("),
    re.compile(r"\bgaussJordan\b", re.IGNORECASE),
    re.compile(r"\bpowerIteration\b", re.IGNORECASE),
    re.compile(r"\b(solveLower|solveUpper|forwardSubstitution|backSubstitution|forwardSolve|backwardSolve)\b"),
    re.compile(r"\blargestEigenvalue\b"),
    re.compile(r"\bdominantEigenvector\b"),
    re.compile(r"\bfun\s+(invert|inverse|determinant)\s*\("),
    re.compile(r"\bfun\s+.*cholesky\s*\([^)]*\)\s*:\s*Array<DoubleArray>", re.IGNORECASE),
    re.compile(r"\bjitter\s*=\s*jitter\s*\*"),
    re.compile(r"/\s*(lower|upper)\[[^\]]+\]\[[^\]]+\]"),
    re.compile(r"\bposteriorProbabilityRankPositive\b"),
    re.compile(r"\bjohansenTraceStatistic\b"),
    re.compile(r"\bLEGACY_HEURISTIC\b"),
    re.compile(r"\bCONFIRMED_I[01]\b"),
    re.compile(r"\bREQUIRE_EXPLICIT_ROBUSTNESS_PLAN\b"),
    re.compile(r"coerceAtLeast\s*\(\s*MIN_SCALE\s*\)"),
    re.compile(r"supportedForModelRouting\s*=\s*true"),
    re.compile(r"\.missingRates\b"),
    re.compile(r"weeks\.drop\s*\(\s*1\s*\)"),
    re.compile(r"Bayesian rank posterior"),
]

WRAPPER_ONLY = [
    "CholeskyDecomposition",
    "EigenDecomposition",
    "RRQRDecomposition",
    "SingularValueDecomposition",
    "QRDecomposition",
]


def main() -> int:
    violations: list[str] = []
    wrapper_text = WRAPPER.read_text(encoding="utf-8")
    strict_start = wrapper_text.find("fun strictCholesky")
    regularized_start = wrapper_text.find("fun regularizedCholesky")
    strict_body = wrapper_text[strict_start:regularized_start] if strict_start >= 0 and regularized_start > strict_start else ""
    if "rank != prepared.size" not in strict_body:
        violations.append("StableLinearAlgebra.kt: strictCholesky lacks numerical-rank success gate")
    if "MAX_CONDITION_NUMBER" not in strict_body:
        violations.append("StableLinearAlgebra.kt: strictCholesky lacks condition-number success gate")
    if "isFinite()" not in strict_body:
        violations.append("StableLinearAlgebra.kt: strictCholesky lacks finite diagnostic gate")

    selector_text = SELECTOR.read_text(encoding="utf-8")
    if "TrendDataPoint" in selector_text:
        violations.append("EndogenousVariableSelector.kt: selector production path must not depend on raw TrendDataPoint")
    if re.search(r"fun\s+select\s*\([^)]*Map\s*<\s*TrendMetricId\s*,\s*List\s*<\s*TrendDataPoint", selector_text, re.DOTALL):
        violations.append("EndogenousVariableSelector.kt: select must not accept raw TrendDataPoint maps")
    if re.search(r"candidateRollingPredictiveGain\s*\([^)]*TrendDataPoint", selector_text, re.DOTALL):
        violations.append("EndogenousVariableSelector.kt: candidate ranking must not accept raw TrendDataPoint maps")
    if "alignmentService.align(" in selector_text:
        violations.append("EndogenousVariableSelector.kt: selector ranking must consume prepared systems, not realign raw series")
    if re.search(r"horizon\s*=\s*1\b", selector_text):
        violations.append("EndogenousVariableSelector.kt: candidate ranking must use the requested horizon, not hard-coded horizon 1")
    if "IntegrationOrderAnalyzer" in selector_text or ".diagnose(" in selector_text:
        violations.append("EndogenousVariableSelector.kt: selector must not recompute transformation diagnostics")

    support_text = SUPPORT.read_text(encoding="utf-8")
    if "items.indexOf" in support_text:
        violations.append("BayesianTimeSeriesSupport.kt: conflict resolution must not use input order as a revision tie-break")
    if re.search(r"source\s*\.\s*(compareTo|lowercase|uppercase)|sortedBy\s*\{\s*it\.source", support_text):
        violations.append("BayesianTimeSeriesSupport.kt: conflict resolution must not use source-string ordering")
    if "RevisionOrderKey" in support_text:
        violations.append("BayesianTimeSeriesSupport.kt: heterogeneous revision fields must not be collapsed into one tuple")

    analyzer_text = ANALYZER.read_text(encoding="utf-8")
    selector_call = analyzer_text.find("endogenousVariableSelector.select")
    plan_call = analyzer_text.find("transformationPlan(")
    catalog_call = analyzer_text.find("preparedCandidateCatalog(")
    if min(selector_call, plan_call, catalog_call) < 0 or not (plan_call < catalog_call < selector_call):
        violations.append("BayesianTimeSeriesAnalyzer.kt: transformation plan and prepared catalog must be created before selector invocation")

    lp_text = LP.read_text(encoding="utf-8")
    if re.search(r"alignment\.copy\s*\(", lp_text):
        violations.append("BayesianLocalProjectionEstimator.kt: slicing must not fall back to shallow alignment.copy")

    dynamic_text = DYNAMIC.read_text(encoding="utf-8")
    if "raw.map(::standardize)" in dynamic_text:
        violations.append("BayesianDynamicEstimators.kt: compatibility estimators must not standardize full raw series before allowed-row restriction")

    legacy_symbols = (
        "LegacyTimeSeriesAnalyzer",
        "BayesianLocalProjectionEstimator",
        "BayesianVarEstimator",
        "BayesianVecmEstimator",
        "CointegrationAnalyzer",
        "EndogenousVariableSelector",
    )
    strict_sources = {path: path.read_text(encoding="utf-8") for path in STRICT_PIPELINE.rglob("*.kt")}
    diagnostics_text = strict_sources.get(STRICT_DIAGNOSTICS, "")
    if not re.search(r"MIN_INTEGRATION_SEGMENT_WEEKS\s*=\s*32\b", diagnostics_text):
        violations.append("StrictTimeSeriesDiagnostics.kt: integration diagnostics must use a 32-week contiguous segment minimum")
    if "statsmodels-adfuller-kpss-c" not in diagnostics_text:
        violations.append("StrictTimeSeriesDiagnostics.kt: diagnostic method must name the statsmodels ADF/KPSS constant-only contract")
    if not {"SUPPORTED_I0", "SUPPORTED_I1", "INSUFFICIENT_CONTIGUOUS_SAMPLE"}.issubset(set(re.findall(r"\b[A-Z_0-9]+\b", diagnostics_text))):
        violations.append("StrictTimeSeriesDiagnostics.kt: supported/inconclusive status vocabulary is incomplete")

    representation_text = strict_sources.get(STRICT_REPRESENTATION, "")
    if "TRANSFORMATION_ASSESSMENT_CONFLICT" not in representation_text:
        violations.append("StrictTimeSeriesRepresentation.kt: explicit transformation mismatch must fail with TRANSFORMATION_ASSESSMENT_CONFLICT")

    stages_text = strict_sources.get(STRICT_PIPELINE / "StrictTimeSeriesStages.kt", "")
    if not re.search(r"STRICT_HORIZON_RANGE\s*:\s*IntRange\s*=\s*1\.\.8\b", stages_text):
        violations.append("StrictTimeSeriesStages.kt: strict horizon range must be exactly 1..8")

    row_scaling_text = strict_sources.get(STRICT_ROW_SCALING, "")
    if "HorizonPolicy.NOT_APPLICABLE" not in row_scaling_text or "planWithoutHorizon" not in row_scaling_text:
        violations.append("PreparedRowAndScalingPlans.kt: non-horizon row plans must use explicit NOT_APPLICABLE policy")
    for token in ("TOO_FEW_TRAINING_VALUES", "NEAR_CONSTANT_TRAINING_SERIES", "NON_FINITE_TRAINING_SERIES"):
        if token not in row_scaling_text:
            violations.append(f"PreparedRowAndScalingPlans.kt: missing scaling failure code {token}")

    for path in STRICT_PIPELINE.rglob("*.kt"):
        text = strict_sources[path]
        if path != STRICT_INGESTION and "TrendDataPoint" in text:
            violations.append(f"{path.relative_to(ROOT)}: raw TrendDataPoint is allowed only at strict ingestion")
        for symbol in legacy_symbols:
            if symbol in text:
                violations.append(f"{path.relative_to(ROOT)}: strict pipeline references legacy symbol {symbol}")
        legacy_import = re.search(r"^import\s+com\.training\.trackplanner\.analysis\.lab\.(?!pipeline\.|StableLinearAlgebra\b)", text, re.MULTILINE)
        if legacy_import and path != STRICT_INGESTION:
            violations.append(f"{path.relative_to(ROOT)}: strict pipeline imports the legacy lab package")
        if re.search(r"\b(USE_DOCUMENTED_FALLBACK|ADF_REJECT|KPSS_RETAIN)\b", text):
            violations.append(f"{path.relative_to(ROOT)}: strict pipeline must not use legacy fixed diagnostic thresholds or fallback policy")
        if re.search(r"data\s+class\s+\w+\s*\([^)]*fingerprint\s*:", text, re.DOTALL):
            violations.append(f"{path.relative_to(ROOT)}: identity-bearing strict type must not expose data-class copy semantics")
        if re.search(r"filter\s*\(\s*Double::isFinite\s*\)|filterNotNull\s*\(\s*\)", text):
            violations.append(f"{path.relative_to(ROOT)}: strict path must not compress calendar gaps through finite/null filtering")
        if re.search(r"\bhorizon\s*=\s*1\b", text):
            violations.append(f"{path.relative_to(ROOT)}: strict preparation must not hard-code horizon 1")
        if "exactDifference(" in text:
            violations.append(f"{path.relative_to(ROOT)}: strict downstream code must not perform implicit differencing")

    all_strict_text = "\n".join(strict_sources.values())
    required_strict_contracts = (
        "StrictTimeSeriesPreparationPipeline",
        "PreparedAnalysisContext",
        "PreparedEstimatorView",
        "RowPlanner",
        "ScalingPlanner",
        "IdentifiedShockPosterior",
        "FutureBvarInput",
        "FutureBlpInput",
        "FutureJohansenInput",
        "FutureVecmInput",
    )
    for contract in required_strict_contracts:
        if contract not in all_strict_text:
            violations.append(f"strict pipeline: missing required contract {contract}")

    for path, text in strict_sources.items():
        if path not in {STRICT_REPRESENTATION, STRICT_CONTEXT} and "CanonicalTransformationAuthority.createPlan" in text:
            violations.append(f"{path.relative_to(ROOT)}: transformation decisions must route through PreparedAnalysisContext")
        if path != STRICT_ROW_SCALING and re.search(r"object\s+RowPlanner|object\s+ScalingPlanner", text):
            violations.append(f"{path.relative_to(ROOT)}: duplicate row or scaling authority")
        if path not in {STRICT_DIAGNOSTICS, STRICT_ROW_SCALING} and re.search(r"\.average\s*\(\s*\)", text):
            violations.append(f"{path.relative_to(ROOT)}: estimator-local unrestricted scaling/statistics are forbidden")

    future_text = strict_sources.get(STRICT_FUTURE, "")
    representation_text = strict_sources.get(STRICT_REPRESENTATION, "")
    ingestion_text = strict_sources.get(STRICT_INGESTION, "")
    if "IdentifiedShockPosterior" not in future_text or "DRAW_BY_DRAW_WITHOUT_MEAN_SHOCK_COLLAPSE" not in future_text:
        violations.append("FutureEstimatorBoundaries.kt: strict BLP must require draw-specific identified shock posterior propagation")
    if "BvarPosteriorSourceIdentity" not in future_text:
        violations.append("FutureEstimatorBoundaries.kt: BVAR posterior identity must bind source metric, view, row, scaling, prior, and posterior fingerprints")
    for token in ("SHOCK_SOURCE", "eligibleSourceWeeks", "responseScalePlansByMetric", "horizonPolicy != HorizonPolicy.NOT_APPLICABLE"):
        if token not in future_text:
            violations.append(f"FutureEstimatorBoundaries.kt: missing BLP posterior-boundary guard {token}")
    if re.search(r"shock\s*\?:|exactDifference|List\s*<\s*Double\s*>\s*\?", future_text):
        violations.append("FutureEstimatorBoundaries.kt: strict BLP exposes a raw or fallback shock path")
    if re.search(r"\bCanonicalCalendar\b|calendar\.weeks|values\.size\s*==\s*calendar\.weeks\.size", representation_text[representation_text.find("internal class IdentifiedShockPosterior"):]):
        violations.append("StrictTimeSeriesRepresentation.kt: shock posterior must use prepared eligible source weeks, not full-calendar shock vectors")
    if "sourceIdentity: BvarPosteriorSourceIdentity" not in representation_text or "eligibleSourceWeeks" not in representation_text:
        violations.append("StrictTimeSeriesRepresentation.kt: shock posterior must carry BVAR posterior source identity and eligible source weeks")
    if "eligibleWeeks == rowPlanWeeks" not in future_text:
        violations.append("FutureEstimatorBoundaries.kt: BVAR shock weeks must exactly match row-plan source weeks")
    if "canonicalDrawIds" not in representation_text:
        violations.append("StrictTimeSeriesRepresentation.kt: shock posterior fingerprints must use canonical draw ordering")
    if "ids.none { it in rejectedIds }" not in representation_text:
        violations.append("StrictTimeSeriesRepresentation.kt: accepted and rejected shock draw IDs must be disjoint")
    if "TimeSeriesAlignmentService().alignObservations" not in ingestion_text:
        violations.append("StrictTimeSeriesIngestion.kt: strict ingestion must consume the existing authoritative resolver output")
    if "strict ingestion requires one authoritative resolved observation per metric/week" not in ingestion_text:
        violations.append("StrictTimeSeriesIngestion.kt: unresolved duplicate raw observations must be rejected at the strict boundary")
    for token in ("resolveObservationConflict", "maxRevision", "revisionNumber", "versionSequence", "authoritativeRevisionTime"):
        if token in ingestion_text:
            violations.append(f"StrictTimeSeriesIngestion.kt: strict ingestion must not implement revision precedence ({token})")

    views_text = strict_sources.get(STRICT_VIEWS, "")
    johansen_block = views_text[views_text.find("internal class JohansenPreparedView"):views_text.find("internal class VecmPreparedView")]
    if "emptyMap()" not in johansen_block or "VALIDATED_LEVEL" not in johansen_block:
        violations.append("PreparedEstimatorViews.kt: Johansen view must expose validated levels without transformed substitution")

    if "internal class LegacyTimeSeriesAnalyzer" not in analyzer_text:
        violations.append("BayesianTimeSeriesAnalyzer.kt: app-visible compatibility analyzer must be explicitly legacy")
    if "Legacy compatibility analysis" not in analyzer_text:
        violations.append("BayesianTimeSeriesAnalyzer.kt: legacy result must be labeled as compatibility output")

    for path in TARGET.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for pattern in FORBIDDEN:
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                violations.append(f"{path.relative_to(ROOT)}:{line}: {pattern.pattern}")
        if path != WRAPPER:
            for token in WRAPPER_ONLY:
                if token in text:
                    line = text[: text.index(token)].count("\n") + 1
                    violations.append(f"{path.relative_to(ROOT)}:{line}: direct {token}; use StableLinearAlgebra")
        if path != MODELS:
            if "MetricDataQualitySummary(" in text:
                line = text[: text.index("MetricDataQualitySummary(")].count("\n") + 1
                violations.append(f"{path.relative_to(ROOT)}:{line}: construct MetricDataQualitySummary through fromCells")
            if "PreparedMetricSeries(" in text:
                line = text[: text.index("PreparedMetricSeries(")].count("\n") + 1
                violations.append(f"{path.relative_to(ROOT)}:{line}: construct PreparedMetricSeries through createValidated")
        if path == SUPPORT:
            for match in re.finditer(r"MetricLifecycleMetadata\(\)", text):
                line = text.count("\n", 0, match.start()) + 1
                prefix = text[max(0, match.start() - 120):match.start()]
                if "requestedMetadata" not in prefix and "lifecycleMetadata[it]" not in prefix:
                    violations.append(f"{path.relative_to(ROOT)}:{line}: do not replace existing lifecycle metadata with defaults")
    if violations:
        print("\n".join(violations))
        return 1
    print("time-series numeric source scan passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
