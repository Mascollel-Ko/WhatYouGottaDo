from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app" / "src" / "main" / "java" / "com" / "training" / "trackplanner" / "analysis" / "lab"
WRAPPER = TARGET / "StableLinearAlgebra.kt"

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
    if violations:
        print("\n".join(violations))
        return 1
    print("time-series numeric source scan passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
