from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

import localization_authority as authority


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_VALUES = ROOT / "app/src/main/res/values"
ENGLISH_VALUES = ROOT / "app/src/main/res/values-en"
KOTLIN_ROOT = ROOT / "app/src/main/java"
EXERCISE_BOOTSTRAP = ROOT / "app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv"
EXERCISE_DESCRIPTION_EN = ROOT / "tools/localization/exercise_description_generated_en.csv"
PROGRAM_NAME_EN = ROOT / "tools/localization/program_name_generated_en.csv"
GENERATED = ROOT / "docs/generated"

APPROVED = "APPROVED_LOCALIZED_PRESENTATION"
GENERATED_ENGLISH = "CODEX_GENERATED_ENGLISH"
USER_PASSTHROUGH = "USER_CONTENT_PASSTHROUGH"
CANONICAL_NON_DISPLAY = "CANONICAL_NON_DISPLAY"
INTERNAL_DEBUG = "INTERNAL_DEBUG"
UNEXPLAINED = "UNEXPLAINED_PRODUCTION_LEAK"

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
FORMAT_PLACEHOLDER = re.compile(
    r"%(?:(?:\d+\$)[-#+ 0,(<]*\d*(?:\.\d+)?|[-#+0,(<]*\d*(?:\.\d+)?)[bBhHsScCdoxXeEfgGaAtT]"
)
KOTLIN_INTERPOLATION = re.compile(r"(?<![%0-9])\$(?:\{[^}]*}|[A-Za-z_][A-Za-z0-9_]*)")


@dataclass(frozen=True)
class ResourceValue:
    key: str
    text: str
    source: str
    plural: bool


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def placeholder_signature(text: str) -> str:
    tokens = [f"%{match[-1].lower()}" for match in FORMAT_PLACEHOLDER.findall(text)]
    tokens.extend("%s" for _ in KOTLIN_INTERPOLATION.finditer(text))
    return "|".join(sorted(tokens))


def plural_placeholder_signature(text: str) -> str:
    signatures = {
        placeholder_signature(item.partition("=")[2])
        for item in text.split("|")
    }
    return "||".join(sorted(signatures))


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


def classify_literal(path: Path, context: str, text: str) -> str:
    if "DateTimeFormatter.ofPattern" in context:
        return "SEMANTIC_LOCALE_FORMAT"
    if path.name in {"TrainingDatabase.kt", "ExerciseStableKeyMigration.kt"}:
        return "CANONICAL_OR_MIGRATION_DATA"
    if text.startswith("^") or "[가-힣]" in text or text.startswith("imported_"):
        return "INTERNAL_TOKEN_OR_PATTERN"
    if re.search(r"remove(?:Prefix|Suffix)\s*\(", context):
        return "INTERNAL_TOKEN_OR_PATTERN"
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


def _skip_interpolation(source: str, index: int) -> int:
    depth = 1
    while index < len(source) and depth:
        if source.startswith('"""', index):
            closing = source.find('"""', index + 3)
            index = len(source) if closing < 0 else closing + 3
        elif source[index] == '"':
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == '"':
                    index += 1
                    break
                else:
                    index += 1
        elif source[index] == "{":
            depth += 1
            index += 1
        elif source[index] == "}":
            depth -= 1
            index += 1
        else:
            index += 1
    return index


def kotlin_string_templates(source: str) -> list[tuple[str, int]]:
    templates: list[tuple[str, int]] = []
    index = 0
    while index < len(source):
        raw = source.startswith('"""', index)
        if not raw and source[index] != '"':
            index += 1
            continue
        start = index
        index += 3 if raw else 1
        result: list[str] = []
        while index < len(source):
            if raw and source.startswith('"""', index):
                index += 3
                break
            if not raw and source[index] == '"':
                index += 1
                break
            if not raw and source[index] == "\\" and index + 1 < len(source):
                escaped = source[index + 1]
                result.append({"n": "\n", "t": "\t"}.get(escaped, escaped))
                index += 2
                continue
            if source[index] == "$":
                if index + 1 < len(source) and source[index + 1] == "{":
                    result.append("${}")
                    index = _skip_interpolation(source, index + 2)
                    continue
                identifier = re.match(r"\$[A-Za-z_][A-Za-z0-9_]*", source[index:])
                if identifier:
                    result.append("${}")
                    index += len(identifier.group(0))
                    continue
            result.append(source[index])
            index += 1
        templates.append(("".join(result), start))
    return templates


def localization_routes() -> tuple[dict[str, str], dict[str, str]]:
    workbook = authority._xlsx_rows(authority.AUTHORITY, "02_UI_APPROVED_FULL")
    metadata = authority._xlsx_rows(authority.AUTHORITY, "04_METADATA_DICTIONARY")
    baseline = authority._csv_rows(authority.BASELINE_GENERATED_EN)
    dynamic_baseline = authority._csv_rows(authority.DYNAMIC_BASELINE_EN)

    exact_candidates: dict[str, set[str]] = {}
    workbook_korean: set[str] = set()
    dynamic_candidates: dict[str, set[str]] = {}
    for row in workbook:
        korean = authority._text(row.get("koreanSource"))
        english = authority._text(row.get("englishTarget"))
        if not korean or not english:
            continue
        workbook_korean.add(korean)
        if authority._text(row.get("sourceType")) == "ANDROID_RESOURCE":
            continue
        if authority._text(row.get("sourceKeyOrContext")) in authority.CONTEXT_ONLY_APPROVAL_KEYS:
            continue
        template = authority._source_template(korean)
        target = authority._english_resource_template(english)
        destination = dynamic_candidates if "${}" in template else exact_candidates
        destination.setdefault(template, set()).add(target)

    metadata_candidates: dict[str, set[str]] = {}
    for row in metadata:
        korean = authority._text(row.get("koreanDisplay"))
        english = authority._text(row.get("englishDisplay"))
        if korean and english and korean not in workbook_korean:
            metadata_candidates.setdefault(korean, set()).add(english)
    for korean, english_values in metadata_candidates.items():
        if len(english_values) == 1:
            exact_candidates.setdefault(korean, set()).update(english_values)

    metadata_exact = {
        korean
        for korean, english_values in metadata_candidates.items()
        if len(english_values) == 1
    }
    for row in baseline:
        korean = row["korean"].strip()
        english = row["english"].strip()
        approved_targets = exact_candidates.get(korean, set())
        if len(approved_targets) > 1 and english in approved_targets:
            exact_candidates[korean] = {english}
        elif korean not in exact_candidates and korean not in metadata_exact:
            exact_candidates.setdefault(korean, set()).add(english)
    for row in dynamic_baseline:
        template = row["koreanTemplate"].strip()
        if template not in dynamic_candidates:
            dynamic_candidates.setdefault(template, set()).add(row["englishTemplate"].strip())

    exact = {
        source: next(iter(targets))
        for source, targets in exact_candidates.items()
        if len(targets) == 1
    }
    dynamic = {
        source: next(iter(targets))
        for source, targets in dynamic_candidates.items()
        if len(targets) == 1
    }
    return exact, dynamic


def generated_routes() -> tuple[set[str], set[str]]:
    return (
        {row["korean"].strip() for row in authority._csv_rows(authority.BASELINE_GENERATED_EN)},
        {row["koreanTemplate"].strip() for row in authority._csv_rows(authority.DYNAMIC_BASELINE_EN)},
    )


def kotlin_literals() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    exact_routes, dynamic_routes = localization_routes()
    generated_exact, generated_dynamic = generated_routes()
    for path in sorted(KOTLIN_ROOT.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for text, offset in kotlin_string_templates(source):
            if not KOREAN.search(text):
                continue
            line = source.count("\n", 0, offset) + 1
            start = max(0, source.rfind("\n", 0, offset - 220))
            end = source.find("\n", offset + len(text) + 220)
            if end < 0:
                end = len(source)
            context = source[start:end]
            classification = classify_literal(path, context, text)
            route = dynamic_routes.get(text) if "${}" in text else exact_routes.get(text)
            if classification == "SEMANTIC_LOCALE_FORMAT":
                status = APPROVED
            elif classification in {"PRODUCTION_UI", "ACCESSIBILITY"}:
                generated = text in (generated_dynamic if "${}" in text else generated_exact)
                status = GENERATED_ENGLISH if route and generated else APPROVED if route else UNEXPLAINED
            elif classification in {"LOG_ONLY", "DEVELOPER_DIAGNOSTIC"}:
                status = INTERNAL_DEBUG
            else:
                status = CANONICAL_NON_DISPLAY
            rows.append(
                {
                    "resourceKey": "",
                    "korean": text,
                    "english": route or "",
                    "scope": path.stem,
                    "sourcePath": relative(path),
                    "sourceLineOrSymbol": str(line),
                    "classification": classification,
                    "status": status,
                    "placeholderSignature": placeholder_signature(text),
                    "pluralRequired": "REVIEW" if re.search(r"\d+개|\$[^ ]+개", text) else "NO",
                    "notes": "Current-baseline locale-aware presentation audit",
                }
            )
    return rows


def resource_rows(
    default: dict[str, ResourceValue], english: dict[str, ResourceValue]
) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    generated_exact, _ = generated_routes()
    for key, value in sorted(default.items()):
        translated = english.get(key)
        invariant = not KOREAN.search(value.text)
        if translated is not None:
            generated = value.text in generated_exact or value.source.endswith("analysis_signal_strings.xml")
            status = GENERATED_ENGLISH if generated else APPROVED
        elif invariant:
            status = APPROVED
        else:
            status = UNEXPLAINED
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
    source_rows = authority._xlsx_rows(authority.AUTHORITY, "03_EXERCISE_NAMES")
    return [
        {
            "stableKey": authority._text(row.get("stableKey")),
            "korean": authority._text(row.get("koreanName")),
            "english": authority._text(row.get("englishName")),
            "source": relative(authority.AUTHORITY),
            "status": APPROVED,
        }
        for row in source_rows
    ]


def presentation_origin_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []

    def add(
        key: str,
        korean: str,
        english: str,
        path: Path,
        expected: tuple[str, ...],
        status: str,
        notes: str,
    ) -> None:
        source = path.read_text(encoding="utf-8") if path.exists() else ""
        resolved_status = status if all(marker in source for marker in expected) else UNEXPLAINED
        rows.append(
            {
                "resourceKey": key,
                "korean": korean,
                "english": english,
                "scope": "PRESENTATION_ORIGIN",
                "sourcePath": relative(path),
                "sourceLineOrSymbol": key,
                "classification": "PRODUCTION_UI",
                "status": resolved_status,
                "placeholderSignature": placeholder_signature(korean),
                "pluralRequired": "NO",
                "notes": notes,
            }
        )

    presentation = KOTLIN_ROOT / "com/training/trackplanner/localization/LocalizedPresentation.kt"
    add(
        "calendar.year_month_weekday",
        "날짜 의미",
        "YearMonth / DayOfWeek locale formatting",
        presentation,
        ("fun yearMonth", "fun weekday", "DateTimeFormatter.ofPattern", "getDisplayName"),
        APPROVED,
        "Semantic date identity; no fragment translation",
    )
    add(
        "exercise.description.stable_key",
        "기본 운동 설명",
        "Built-in description by stableKey",
        presentation,
        ("fun exerciseDescription", "exerciseDescriptionIds[exercise.stableKey]", "if (exercise.isCustom)"),
        GENERATED_ENGLISH,
        "Built-in overlay with custom content passthrough",
    )
    add(
        "program.name.stable_key",
        "기본 프로그램 이름",
        "Seeded program name by stable identity",
        presentation,
        ("fun programName", "programNameIds[program.stableKey]", "program.name"),
        GENERATED_ENGLISH,
        "Seeded overlay; stored user name fallback remains verbatim",
    )
    add(
        "user.content.passthrough",
        "사용자 입력",
        "User-entered content unchanged",
        presentation,
        ("if (exercise.isCustom) exercise.name", "if (exercise.isCustom) exercise.description", "?: program.name"),
        USER_PASSTHROUGH,
        "Names and descriptions are not machine-translated",
    )
    add(
        "analysis.signal.semantic_codes",
        "분석 신호",
        "Analysis signals by message code",
        presentation,
        ("SleepRecoveryMessageCode", "JointTendonWarningMessageCode", "CourtDurationRecoveryMessageCode"),
        GENERATED_ENGLISH,
        "Domain codes remain locale-invariant; presentation owns wording",
    )
    add(
        "analysis.posterior_median",
        "사후분포 중앙값",
        "Posterior median",
        KOTLIN_ROOT / "com/training/trackplanner/AnalysisPersistentStrengthPerformanceUi.kt",
        ('"사후분포 중앙값"',),
        APPROVED,
        "Approved statistical identity routed through the shared Text presentation boundary",
    )

    for path, prefix, note in (
        (EXERCISE_DESCRIPTION_EN, "exercise.description", "Canonical built-in description coverage"),
        (PROGRAM_NAME_EN, "program.name", "Seeded program identity coverage"),
    ):
        for item in authority._csv_rows(path):
            rows.append(
                {
                    "resourceKey": f"{prefix}.{item['stableKey'].strip()}",
                    "korean": item["korean"].strip(),
                    "english": item["english"].strip(),
                    "scope": "CANONICAL_ASSET_PRESENTATION",
                    "sourcePath": relative(path),
                    "sourceLineOrSymbol": item["stableKey"].strip(),
                    "classification": "PRODUCTION_UI",
                    "status": GENERATED_ENGLISH if item["english"].strip() else UNEXPLAINED,
                    "placeholderSignature": placeholder_signature(item["korean"].strip()),
                    "pluralRequired": "NO",
                    "notes": note,
                }
            )
    return rows


def collect() -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    default = parse_resources(DEFAULT_VALUES)
    english = parse_resources(ENGLISH_VALUES)
    literals = kotlin_literals()
    origins = presentation_origin_rows()
    ui_rows = resource_rows(default, english) + literals + origins
    exercises = exercise_rows()
    invalid_placeholders = sum(
        bool(row["english"])
        and (
            plural_placeholder_signature(row["korean"])
            != plural_placeholder_signature(row["english"])
            if row["pluralRequired"] == "YES"
            else row["placeholderSignature"] != placeholder_signature(row["english"])
        )
        for row in ui_rows
    )
    unexplained = sum(row["status"] == UNEXPLAINED for row in ui_rows)
    summary = [
        {"metric": "koreanUiResourceCount", "value": str(sum(KOREAN.search(row["korean"]) is not None for row in resource_rows(default, english)))},
        {"metric": "existingEnglishResourceCount", "value": str(len(english))},
        {"metric": "totalProductionUiResourceCount", "value": str(len(default))},
        {"metric": "currentBaselineCheckRequired", "value": "0"},
        {"metric": "unexplainedEnglishModeKoreanLeakCount", "value": str(unexplained)},
        {"metric": "unexplainedProductionLeakCount", "value": str(unexplained)},
        {"metric": "builtInExerciseCount", "value": str(len(exercises))},
        {"metric": "localizedExerciseNameCount", "value": str(sum(row["status"] == APPROVED for row in exercises))},
        {"metric": "localizedExerciseDescriptionCount", "value": str(sum(row["scope"] == "CANONICAL_ASSET_PRESENTATION" and row["resourceKey"].startswith("exercise.description.") for row in origins))},
        {"metric": "localizedSeedProgramNameCount", "value": str(sum(row["scope"] == "CANONICAL_ASSET_PRESENTATION" and row["resourceKey"].startswith("program.name.") for row in origins))},
        {"metric": "historyOnlyExerciseNameCount", "value": "16"},
        {"metric": "invalidEnglishPlaceholderCount", "value": str(invalid_placeholders)},
        {"metric": "approvedLocalizedPresentationCount", "value": str(sum(row["status"] == APPROVED for row in ui_rows))},
        {"metric": "codexGeneratedEnglishCount", "value": str(sum(row["status"] == GENERATED_ENGLISH for row in ui_rows))},
        {"metric": "userContentPassthroughCount", "value": str(sum(row["status"] == USER_PASSTHROUGH for row in ui_rows))},
        {"metric": "canonicalNonDisplayCount", "value": str(sum(row["status"] == CANONICAL_NON_DISPLAY for row in ui_rows))},
        {"metric": "internalDebugCount", "value": str(sum(row["status"] == INTERNAL_DEBUG for row in ui_rows))},
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
