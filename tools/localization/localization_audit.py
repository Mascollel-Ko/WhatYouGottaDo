from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_VALUES = ROOT / "app/src/main/res/values"
ENGLISH_VALUES = ROOT / "app/src/main/res/values-en"
KOTLIN_ROOT = ROOT / "app/src/main/java"
EXERCISE_BOOTSTRAP = ROOT / "app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv"
GENERATED = ROOT / "docs/generated"

UI_COLUMNS = (
    "resourceKey",
    "korean",
    "english",
    "scope",
    "sourcePath",
    "sourceLineOrSymbol",
    "classification",
    "status",
    "placeholderSignature",
    "pluralRequired",
    "notes",
)
EXERCISE_COLUMNS = (
    "stableKey",
    "korean",
    "english",
    "source",
    "status",
)
SUMMARY_COLUMNS = ("metric", "value")

KOREAN = re.compile(r"[가-힣]")
FORMAT_PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")
KOTLIN_INTERPOLATION = re.compile(r"(?<![%0-9])\$(?:\{[^}]+}|[A-Za-z_][A-Za-z0-9_]*)")
STRING_LITERAL = re.compile(r'"""(?P<raw>.*?)"""|"(?P<escaped>(?:\\.|[^"\\])*)"', re.DOTALL)


@dataclass(frozen=True)
class ResourceValue:
    key: str
    text: str
    source: str
    plural: bool


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def placeholder_signature(text: str) -> str:
    tokens = set(FORMAT_PLACEHOLDER.findall(text))
    if KOTLIN_INTERPOLATION.search(text):
        tokens.add("${}")
    return "|".join(sorted(tokens))


def parse_resources(directory: Path) -> dict[str, ResourceValue]:
    values: dict[str, ResourceValue] = {}
    if not directory.exists():
        return values
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        for node in root:
            if node.tag == "string" and node.get("translatable", "true") != "false":
                key = node.attrib["name"]
                text = "".join(node.itertext()).strip()
                values[key] = ResourceValue(key, text, relative(path), False)
            elif node.tag == "plurals" and node.get("translatable", "true") != "false":
                key = node.attrib["name"]
                items = [f"{item.attrib['quantity']}={''.join(item.itertext()).strip()}" for item in node]
                values[key] = ResourceValue(key, "|".join(items), relative(path), True)
    return values


def classify_literal(path: Path, context: str) -> str:
    if re.search(r"contentDescription|\.semantics\s*\{", context):
        return "ACCESSIBILITY"
    if re.search(r"\b(?:Log\.[a-zA-Z]+|println|printStackTrace)\s*\(", context):
        return "LOG_ONLY"
    if re.search(r"\b(?:error|require|check)\s*\(|Exception\s*\(", context):
        return "DEVELOPER_DIAGNOSTIC"
    if re.search(r"stableKey|canonicalCode|diagnosticCode|warningCode|protocolId", context):
        return "NON_DISPLAY_IDENTIFIER"
    if re.search(r"SeedData|CanonicalExercise|canonical_v1", path.name):
        return "CANONICAL_DATA"
    return "PRODUCTION_UI"


def literal_text(match: re.Match[str]) -> str:
    if match.group("raw") is not None:
        return match.group("raw")
    return (
        match.group("escaped")
        .replace(r"\n", "\n")
        .replace(r"\t", "\t")
        .replace(r'\"', '"')
        .replace(r"\\", "\\")
    )


def kotlin_literals() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for path in sorted(KOTLIN_ROOT.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for match in STRING_LITERAL.finditer(source):
            text = literal_text(match)
            if not KOREAN.search(text):
                continue
            line = source.count("\n", 0, match.start()) + 1
            start = max(0, source.rfind("\n", 0, match.start() - 220))
            end = source.find("\n", match.end() + 220)
            if end < 0:
                end = len(source)
            context = source[start:end]
            classification = classify_literal(path, context)
            rows.append(
                {
                    "resourceKey": "",
                    "korean": text,
                    "english": "",
                    "scope": path.stem,
                    "sourcePath": relative(path),
                    "sourceLineOrSymbol": str(line),
                    "classification": classification,
                    "status": (
                        "DIRECT_LITERAL_REQUIRES_RESOURCE"
                        if classification in {"PRODUCTION_UI", "ACCESSIBILITY"}
                        else classification
                    ),
                    "placeholderSignature": placeholder_signature(text),
                    "pluralRequired": "REVIEW" if re.search(r"\d+개|\$[^ ]+개", text) else "NO",
                    "notes": "Baseline Kotlin literal inventory",
                }
            )
    return rows


def resource_rows(
    default: dict[str, ResourceValue], english: dict[str, ResourceValue]
) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for key, value in sorted(default.items()):
        translated = english.get(key)
        invariant = not KOREAN.search(value.text)
        if translated is not None:
            status = "APPROVED_ENGLISH"
        elif invariant:
            status = "APPROVED_LOCALE_INVARIANT"
        else:
            status = "MISSING_APPROVED_ENGLISH"
        rows.append(
            {
                "resourceKey": key,
                "korean": value.text,
                "english": translated.text if translated else "",
                "scope": "ANDROID_RESOURCE",
                "sourcePath": value.source,
                "sourceLineOrSymbol": key,
                "classification": "PRODUCTION_UI",
                "status": status,
                "placeholderSignature": placeholder_signature(value.text),
                "pluralRequired": "YES" if value.plural else "NO",
                "notes": "Existing reviewed English reused" if translated else "",
            }
        )
    return rows


def exercise_rows() -> list[dict[str, str]]:
    with EXERCISE_BOOTSTRAP.open(encoding="utf-8", newline="") as handle:
        source_rows = list(csv.DictReader(handle))
    return [
        {
            "stableKey": row["stableKey"],
            "korean": row["name"],
            "english": "",
            "source": relative(EXERCISE_BOOTSTRAP),
            "status": "MISSING_APPROVED_ENGLISH_NAME",
        }
        for row in source_rows
        if row["isActive"] == "YES" and row["isCustom"] == "NO"
    ]


def collect() -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    default = parse_resources(DEFAULT_VALUES)
    english = parse_resources(ENGLISH_VALUES)
    literals = kotlin_literals()
    ui_rows = resource_rows(default, english) + literals
    exercises = exercise_rows()
    invalid_placeholders = sum(
        bool(row["english"])
        and row["placeholderSignature"] != placeholder_signature(row["english"])
        for row in ui_rows
    )
    direct = sum(row["status"] == "DIRECT_LITERAL_REQUIRES_RESOURCE" for row in ui_rows)
    summary = [
        {"metric": "koreanUiResourceCount", "value": str(sum(KOREAN.search(row["korean"]) is not None for row in resource_rows(default, english)))},
        {"metric": "existingEnglishResourceCount", "value": str(len(english))},
        {"metric": "totalProductionUiResourceCount", "value": str(len(default))},
        {"metric": "missingApprovedEnglishUiStringCount", "value": str(sum(row["status"] in {"MISSING_APPROVED_ENGLISH", "DIRECT_LITERAL_REQUIRES_RESOURCE"} for row in ui_rows))},
        {"metric": "builtInExerciseCount", "value": str(len(exercises))},
        {"metric": "missingApprovedEnglishExerciseNameCount", "value": str(sum(row["status"] == "MISSING_APPROVED_ENGLISH_NAME" for row in exercises))},
        {"metric": "invalidEnglishPlaceholderCount", "value": str(invalid_placeholders)},
        {"metric": "directProductionUiLiteralCount", "value": str(direct)},
    ]
    return ui_rows, exercises, summary


def render(rows: list[dict[str, str]], columns: tuple[str, ...]) -> str:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=columns, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def artifacts() -> dict[Path, str]:
    ui, exercises, summary = collect()
    return {
        GENERATED / "ui_localization_inventory.csv": render(ui, UI_COLUMNS),
        GENERATED / "exercise_name_localization_inventory.csv": render(exercises, EXERCISE_COLUMNS),
        GENERATED / "localization_coverage_summary.csv": render(summary, SUMMARY_COLUMNS),
    }


def validate_or_write(write: bool) -> None:
    for path, content in artifacts().items():
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8", newline="")
        elif not path.exists() or path.read_text(encoding="utf-8") != content:
            raise ValueError(f"Stale localization artifact: {relative(path)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    validate_or_write(args.write)
    for path in artifacts():
        digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else "pending"
        print(f"{relative(path)} sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
