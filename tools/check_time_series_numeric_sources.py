from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app" / "src" / "main" / "java" / "com" / "training" / "trackplanner" / "analysis" / "lab"
WRAPPER = TARGET / "StableLinearAlgebra.kt"
MODELS = TARGET / "BayesianTimeSeriesModels.kt"
SELECTOR = TARGET / "EndogenousVariableSelector.kt"
SUPPORT = TARGET / "BayesianTimeSeriesSupport.kt"

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

    support_text = SUPPORT.read_text(encoding="utf-8")
    if "items.indexOf" in support_text:
        violations.append("BayesianTimeSeriesSupport.kt: conflict resolution must not use input order as a revision tie-break")
    if re.search(r"source\s*\.\s*(compareTo|lowercase|uppercase)|sortedBy\s*\{\s*it\.source", support_text):
        violations.append("BayesianTimeSeriesSupport.kt: conflict resolution must not use source-string ordering")
    if "RevisionOrderKey" in support_text:
        violations.append("BayesianTimeSeriesSupport.kt: heterogeneous revision fields must not be collapsed into one tuple")

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
