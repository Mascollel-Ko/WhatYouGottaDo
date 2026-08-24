from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import zipfile
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[2]
AUTHORITY = (
    ROOT
    / "docs/metadata_authority/WhatYouGottaDo_KO_EN_Localization_Authority_v2_FULL_APPROVED_2026-08-09.xlsx"
)
TISSUE_KO = (
    ROOT
    / "app/src/main/assets/metadata/tissue_load_v1/tissue_rcv_educational_info_v1.csv"
)
BASELINE_GENERATED_EN = ROOT / "tools/localization/current_baseline_generated_en.csv"
DYNAMIC_BASELINE_EN = ROOT / "tools/localization/current_baseline_dynamic_en.csv"
EXERCISE_DESCRIPTION_EN = ROOT / "tools/localization/exercise_description_generated_en.csv"
PROGRAM_NAME_EN = ROOT / "tools/localization/program_name_generated_en.csv"
EXERCISE_BOOTSTRAP = ROOT / "app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv"
TRAINING_SETTINGS_SEED = ROOT / "app/src/main/assets/training_settings_seed.csv"
STRENGTH_TARGET_REGISTRY = (
    ROOT / "app/src/main/assets/strength_performance/strength_target_registry_v1.csv"
)

OUTPUTS = {
    "ui_base": ROOT / "app/src/main/res/values/localization_generated.xml",
    "ui_en": ROOT / "app/src/main/res/values-en/localization_generated.xml",
    "strings_en": ROOT / "app/src/main/res/values-en/strings.xml",
    "exercise_base": ROOT / "app/src/main/res/values/exercise_names.xml",
    "exercise_en": ROOT / "app/src/main/res/values-en/exercise_names.xml",
    "exercise_description_base": ROOT / "app/src/main/res/values/exercise_descriptions.xml",
    "exercise_description_en": ROOT / "app/src/main/res/values-en/exercise_descriptions.xml",
    "program_name_base": ROOT / "app/src/main/res/values/program_names.xml",
    "program_name_en": ROOT / "app/src/main/res/values-en/program_names.xml",
    "tissue_base": ROOT / "app/src/main/res/values/tissue_education.xml",
    "tissue_en": ROOT / "app/src/main/res/values-en/tissue_education.xml",
    "kotlin": (
        ROOT
        / "app/src/main/java/com/training/trackplanner/localization/GeneratedLocalizationCatalogue.kt"
    ),
    "manifest": ROOT / "docs/generated/localization_authority_manifest.json",
}

ANDROID_PLACEHOLDER = re.compile(
    r"%(?:(?:\d+\$)[-#+ 0,(<]*\d*(?:\.\d+)?|[-#+0,(<]*\d*(?:\.\d+)?)[bBhHsScCdoxXeEfgGaAtT]"
)
KOTLIN_PLACEHOLDER = re.compile(r"\$\{[^}]+}|\$[A-Za-z_][A-Za-z0-9_]*")
CELL_REF = re.compile(r"([A-Z]+)(\d+)")
CONTEXT_ONLY_APPROVAL_KEYS = {"approval_1359", "approval_1447"}
RETIRED_UI_TEXTS = {
    "배드민턴 훈련량은 셔틀 플레이 시간, 풋워크/반응, 보조훈련량을 합친 흐름입니다.",
    "전이 점검, 배드민턴 관련 훈련량의 일별·주별 흐름",
}


def _xlsx_rows(path: Path, sheet_name: str) -> list[dict[str, object]]:
    with zipfile.ZipFile(path) as archive:
        shared = _shared_strings(archive)
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        relations = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        relation_targets = {
            relation.attrib["Id"]: relation.attrib["Target"] for relation in relations
        }
        namespace = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        relationship_namespace = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
        sheet = next(
            node
            for node in workbook.find("m:sheets", namespace)
            if node.attrib["name"] == sheet_name
        )
        target = relation_targets[sheet.attrib[relationship_namespace]].lstrip("/")
        if not target.startswith("xl/"):
            target = f"xl/{target}"
        xml = ET.fromstring(archive.read(target))
        rows = [_xlsx_row(row, shared) for row in xml.findall(".//m:sheetData/m:row", namespace)]
    if not rows:
        return []
    headers = rows[0]
    return [
        {
            str(header): row[index] if index < len(row) else None
            for index, header in enumerate(headers)
            if header not in (None, "")
        }
        for row in rows[1:]
        if any(value not in (None, "") for value in row)
    ]


def _shared_strings(archive: zipfile.ZipFile) -> list[str]:
    path = "xl/sharedStrings.xml"
    if path not in archive.namelist():
        return []
    root = ET.fromstring(archive.read(path))
    namespace = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    return ["".join(node.text or "" for node in item.findall(".//m:t", namespace)) for item in root]


def _xlsx_row(row: ET.Element, shared: list[str]) -> list[object]:
    values: dict[int, object] = {}
    for cell in row:
        if not cell.tag.endswith("}c"):
            continue
        match = CELL_REF.match(cell.attrib.get("r", ""))
        if not match:
            continue
        column = _column_index(match.group(1))
        kind = cell.attrib.get("t")
        if kind == "inlineStr":
            value: object = "".join(node.text or "" for node in cell.iter() if node.tag.endswith("}t"))
        else:
            value_node = next((node for node in cell if node.tag.endswith("}v")), None)
            raw = value_node.text if value_node is not None else ""
            if kind == "s" and raw:
                value = shared[int(raw)]
            elif kind == "b":
                value = raw == "1"
            elif kind in {"str", "e"}:
                value = raw
            elif raw == "":
                value = None
            else:
                try:
                    number = float(raw)
                    value = int(number) if number.is_integer() else number
                except ValueError:
                    value = raw
        values[column] = value
    return [values.get(index) for index in range(max(values, default=-1) + 1)]


def _column_index(letters: str) -> int:
    value = 0
    for letter in letters:
        value = value * 26 + ord(letter) - ord("A") + 1
    return value - 1


def _text(value: object) -> str:
    return "" if value is None else str(value).strip()


def _resource_key(prefix: str, *parts: str) -> str:
    digest = hashlib.sha1("\u241f".join(parts).encode("utf-8")).hexdigest()[:12]
    return f"{prefix}_{digest}"


def _android_text(value: str, escape_literal_percent: bool = True) -> str:
    pieces: list[str] = []
    cursor = 0
    for match in ANDROID_PLACEHOLDER.finditer(value):
        prefix = value[cursor : match.start()]
        if escape_literal_percent:
            prefix = prefix.replace("%", "%%")
        pieces.append(prefix)
        pieces.append(match.group(0))
        cursor = match.end()
    suffix = value[cursor:]
    if escape_literal_percent:
        suffix = suffix.replace("%", "%%")
    pieces.append(suffix)
    normalized = "".join(pieces).replace("\\", "\\\\").replace("'", "\\'")
    return escape(normalized, {"\"": "&quot;"})


def _xml_strings(entries: list[tuple[str, str]]) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key, value in sorted(entries):
        has_format_argument = ANDROID_PLACEHOLDER.search(value) is not None
        formatted = ' formatted="false"' if "%" in value and not has_format_argument else ""
        lines.append(
            f'    <string name="{key}"{formatted}>'
            f'{_android_text(value, escape_literal_percent=has_format_argument)}</string>'
        )
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def _csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return [
            {
                key: value.replace("\r\n", "\n").replace("\r", "\n")
                for key, value in row.items()
            }
            for row in csv.DictReader(handle)
        ]


def _source_template(value: str) -> str:
    return re.sub(
        rf"{KOTLIN_PLACEHOLDER.pattern}|{ANDROID_PLACEHOLDER.pattern}",
        "${}",
        value,
    )


def _resource_template(value: str) -> str:
    count = 0

    def replace(_: re.Match[str]) -> str:
        nonlocal count
        count += 1
        return f"%{count}$s"

    return re.sub(r"\$\{\}|" + ANDROID_PLACEHOLDER.pattern, replace, value)


def _english_resource_template(value: str) -> str:
    sequential = 0

    def replace(match: re.Match[str]) -> str:
        nonlocal sequential
        token = match.group(0)
        indexed = re.match(r"%(\d+)\$", token)
        if indexed:
            return f"%{indexed.group(1)}$s"
        sequential += 1
        return f"%{sequential}$s"

    return re.sub(
        rf"{KOTLIN_PLACEHOLDER.pattern}|{ANDROID_PLACEHOLDER.pattern}",
        replace,
        value,
    )


def _template_regex(source: str) -> str:
    return "^" + "(.*?)".join(re.escape(part) for part in source.split("${}")) + "$"


def _kotlin_string(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    )


def _ui_assets(
    rows: list[dict[str, object]],
    metadata_rows: list[dict[str, object]],
    baseline_rows: list[dict[str, str]],
) -> tuple[str, str, str, dict[str, str], list[tuple[str, str]], dict[str, int]]:
    android_rows = [row for row in rows if _text(row.get("sourceType")) == "ANDROID_RESOURCE"]
    generated: dict[tuple[str, str], str] = {}
    for row in rows:
        if _text(row.get("sourceType")) == "ANDROID_RESOURCE":
            continue
        if _text(row.get("sourceKeyOrContext")) in CONTEXT_ONLY_APPROVAL_KEYS:
            continue
        korean = _text(row.get("koreanSource"))
        english = _text(row.get("englishTarget"))
        if korean in RETIRED_UI_TEXTS:
            continue
        if korean and english:
            generated.setdefault(
                (korean, english),
                _resource_key("loc_ui", korean, english),
            )

    workbook_korean = {
        _text(row.get("koreanSource"))
        for row in rows
        if _text(row.get("koreanSource"))
    }
    metadata_by_korean: dict[str, set[str]] = defaultdict(set)
    for row in metadata_rows:
        korean = _text(row.get("koreanDisplay"))
        english = _text(row.get("englishDisplay"))
        if korean and english:
            metadata_by_korean[korean].add(english)
    metadata_exact = {
        korean: next(iter(english_values))
        for korean, english_values in metadata_by_korean.items()
        if korean not in workbook_korean and len(english_values) == 1
    }
    for korean, english in metadata_exact.items():
        generated.setdefault(
            (korean, english),
            _resource_key("loc_metadata", korean, english),
        )

    baseline_exact: dict[str, str] = {}
    for row in baseline_rows:
        korean = row["korean"].strip()
        english = row["english"].strip()
        if row["provenance"] != "CODEX_GENERATED_ENGLISH":
            raise ValueError(f"Invalid generated-English provenance: {row['provenance']}")
        if not korean or not english:
            raise ValueError("Generated-English rows must not be blank")
        approved_targets = {
            approved_english
            for approved_korean, approved_english in generated
            if approved_korean == korean
        }
        if len(approved_targets) > 1 and english in approved_targets:
            generated = {
                pair: key
                for pair, key in generated.items()
                if pair[0] != korean or pair[1] == english
            }
        elif approved_targets or korean in metadata_exact:
            continue
        previous = baseline_exact.setdefault(korean, english)
        if previous != english:
            raise ValueError(f"Conflicting generated English for: {korean}")
        generated.setdefault(
            (korean, english),
            _resource_key("loc_baseline", korean, english),
        )

    pattern_candidates: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        if _text(row.get("sourceType")) == "ANDROID_RESOURCE":
            continue
        if _text(row.get("sourceKeyOrContext")) in CONTEXT_ONLY_APPROVAL_KEYS:
            continue
        korean = _text(row.get("koreanSource"))
        english = _text(row.get("englishTarget"))
        if korean in RETIRED_UI_TEXTS:
            continue
        source_template = _source_template(korean)
        if korean and english and "${}" in source_template:
            pattern_candidates[source_template].add(_english_resource_template(english))

    baseline_patterns = _csv_rows(DYNAMIC_BASELINE_EN)
    for row in baseline_patterns:
        source_template = row["koreanTemplate"].strip()
        english_template = row["englishTemplate"].strip()
        if row["provenance"] != "CODEX_GENERATED_ENGLISH":
            raise ValueError(f"Invalid dynamic-English provenance: {row['provenance']}")
        if not source_template or not english_template or "${}" not in source_template:
            raise ValueError("Dynamic-English rows require a source template and placeholders")
        if source_template not in pattern_candidates:
            pattern_candidates[source_template].add(english_template)

    patterns: list[tuple[str, str]] = []
    for source_template, english_templates in pattern_candidates.items():
        if len(english_templates) != 1:
            continue
        english_template = next(iter(english_templates))
        resource_key = generated.setdefault(
            (_resource_template(source_template), english_template),
            _resource_key("loc_ui_pattern", source_template, english_template),
        )
        patterns.append((_template_regex(source_template), resource_key))

    english_by_korean: dict[str, set[str]] = defaultdict(set)
    for korean, english in generated:
        english_by_korean[korean].add(english)
    exact = {
        korean: key
        for (korean, english), key in generated.items()
        if len(english_by_korean[korean]) == 1
        and not ANDROID_PLACEHOLDER.search(korean)
        and not KOTLIN_PLACEHOLDER.search(korean)
    }
    metadata_exact_count = sum(korean in exact for korean in metadata_exact)
    baseline_exact_count = sum(korean in exact for korean in baseline_exact)
    counts = {
        "workbookExact": len(exact) - metadata_exact_count - baseline_exact_count,
        "metadataExact": metadata_exact_count,
        "codexGeneratedExact": baseline_exact_count,
    }
    patterns.sort(key=lambda entry: (-len(entry[0]), entry[0]))
    return (
        _xml_strings([(key, pair[0]) for pair, key in generated.items()]),
        _xml_strings([(key, pair[1]) for pair, key in generated.items()]),
        _xml_strings(
            [
                (_text(row.get("sourceKeyOrContext")), _text(row.get("englishTarget")))
                for row in android_rows
            ]
        ),
        exact,
        patterns,
        counts,
    )


def _exercise_assets(rows: list[dict[str, object]]) -> tuple[str, str, dict[str, str]]:
    names: dict[str, tuple[str, str]] = {}
    for row in rows:
        stable_key = _text(row.get("stableKey"))
        korean = _text(row.get("koreanName"))
        english = _text(row.get("englishName"))
        if stable_key and korean and english:
            names[stable_key] = (korean, english)
    keys = {stable_key: f"exercise_name_{stable_key}" for stable_key in names}
    return (
        _xml_strings([(keys[key], value[0]) for key, value in names.items()]),
        _xml_strings([(keys[key], value[1]) for key, value in names.items()]),
        keys,
    )


def _identity_text_assets(
    translations: list[dict[str, str]],
    canonical: dict[str, str],
    resource_prefix: str,
) -> tuple[str, str, dict[str, str]]:
    translated: dict[str, tuple[str, str]] = {}
    for row in translations:
        stable_key = row["stableKey"].strip()
        korean = row["korean"].strip()
        english = row["english"].strip()
        if row["provenance"].strip() != "CODEX_GENERATED_ENGLISH":
            raise ValueError(f"Invalid generated-English provenance: {stable_key}")
        if stable_key not in canonical or canonical[stable_key] != korean:
            raise ValueError(f"Generated localization source mismatch: {stable_key}")
        if not english:
            raise ValueError(f"Blank generated English: {stable_key}")
        translated[stable_key] = (korean, english)
    if translated.keys() != canonical.keys():
        missing = sorted(canonical.keys() - translated.keys())
        extra = sorted(translated.keys() - canonical.keys())
        raise ValueError(f"Generated localization coverage mismatch: missing={missing}, extra={extra}")
    keys = {stable_key: f"{resource_prefix}_{stable_key}" for stable_key in translated}
    return (
        _xml_strings([(keys[key], value[0]) for key, value in translated.items()]),
        _xml_strings([(keys[key], value[1]) for key, value in translated.items()]),
        keys,
    )


def _exercise_description_assets() -> tuple[str, str, dict[str, str]]:
    canonical = {
        row["stableKey"].strip(): row["description"].strip()
        for row in _csv_rows(EXERCISE_BOOTSTRAP)
    }
    return _identity_text_assets(
        _csv_rows(EXERCISE_DESCRIPTION_EN),
        canonical,
        "exercise_description",
    )


def _program_name_assets() -> tuple[str, str, dict[str, str]]:
    canonical = {
        row["program_key"].strip(): row["program_name"].strip()
        for row in _csv_rows(TRAINING_SETTINGS_SEED)
        if row["row_type"].strip() == "program"
    }
    return _identity_text_assets(_csv_rows(PROGRAM_NAME_EN), canonical, "program_name")


def _strength_target_name_keys(exact_ui: dict[str, str]) -> dict[str, str]:
    keys: dict[str, str] = {}
    for row in _csv_rows(STRENGTH_TARGET_REGISTRY):
        if row["enabled"].strip().lower() != "true":
            continue
        target_key = row["targetKey"].strip()
        display_name = row["displayNameKo"].strip()
        resource = exact_ui.get(display_name)
        if not target_key or resource is None:
            raise ValueError(f"Missing approved strength target localization: {target_key}")
        keys[target_key] = resource
    return keys


def _tissue_assets(rows: list[dict[str, object]]) -> tuple[str, str, dict[str, tuple[str, str, str, str]]]:
    with TISSUE_KO.open(encoding="utf-8", newline="") as handle:
        korean_rows = {row["stableKey"]: row for row in csv.DictReader(handle)}
    resources: dict[str, tuple[str, str, str, str]] = {}
    base_entries: list[tuple[str, str]] = []
    english_entries: list[tuple[str, str]] = []
    field_pairs = (
        ("name", "displayNameKo", "englishName"),
        ("location", "anatomicalLocationKo", "englishLocation"),
        ("functions", "primaryFunctionsKo", "englishPrimaryFunctions"),
        ("contexts", "commonLoadContextsKo", "englishCommonLoadContexts"),
    )
    for row in rows:
        stable_key = _text(row.get("stableKey"))
        source = korean_rows.get(stable_key)
        if source is None:
            raise ValueError(f"Missing Korean tissue education row: {stable_key}")
        field_keys: list[str] = []
        for suffix, korean_field, english_field in field_pairs:
            key = f"tissue_education_{stable_key}_{suffix}"
            field_keys.append(key)
            base_entries.append((key, source[korean_field]))
            english_entries.append((key, _text(row.get(english_field))))
        resources[stable_key] = tuple(field_keys)  # type: ignore[assignment]
    if set(korean_rows) != set(resources):
        missing = sorted(set(korean_rows) - set(resources))
        raise ValueError(f"Unapproved tissue education rows: {missing}")
    return _xml_strings(base_entries), _xml_strings(english_entries), resources


def _generated_kotlin(
    exact_ui: dict[str, str],
    ui_patterns: list[tuple[str, str]],
    strength_target_keys: dict[str, str],
    exercise_keys: dict[str, str],
    exercise_description_keys: dict[str, str],
    program_name_keys: dict[str, str],
    tissue_keys: dict[str, tuple[str, str, str, str]],
) -> str:
    exact_ui_entries = sorted(exact_ui.items())
    exact_ui_chunks = [exact_ui_entries[index:index + 400] for index in range(0, len(exact_ui_entries), 400)]
    lines = [
        "package com.training.trackplanner.localization",
        "",
        "import androidx.annotation.StringRes",
        "import com.training.trackplanner.R",
        "",
        "internal data class TissueEducationResourceIds(",
        "    @StringRes val name: Int,",
        "    @StringRes val location: Int,",
        "    @StringRes val functions: Int,",
        "    @StringRes val contexts: Int",
        ")",
        "",
        "internal data class UiTextPattern(",
        "    val regex: Regex,",
        "    @StringRes val text: Int",
        ")",
        "",
        "internal object GeneratedLocalizationCatalogue {",
        "    val exactUiTextIds: Map<String, Int> = buildMap {",
    ]
    lines.extend(f"        putAll(exactUiTextIdsChunk{index}())" for index in range(len(exact_ui_chunks)))
    lines.extend(["    }", ""])
    for index, chunk in enumerate(exact_ui_chunks):
        lines.append(f"    private fun exactUiTextIdsChunk{index}(): Map<String, Int> = mapOf(")
        lines.extend(
            f'        "{_kotlin_string(source)}" to R.string.{resource},'
            for source, resource in chunk
        )
        lines.extend(["    )", ""])
    lines.append("    val uiTextPatterns: List<UiTextPattern> = listOf(")
    lines.extend(
        f'        UiTextPattern(Regex("{_kotlin_string(regex)}"), R.string.{resource}),'
        for regex, resource in ui_patterns
    )
    lines.extend(["    )", "", "    val strengthTargetNameIds: Map<String, Int> = mapOf("])
    lines.extend(
        f'        "{_kotlin_string(target_key)}" to R.string.{resource},'
        for target_key, resource in sorted(strength_target_keys.items())
    )
    lines.extend(["    )", "", "    val exerciseNameIds: Map<String, Int> = mapOf("])
    lines.extend(
        f'        "{_kotlin_string(stable_key)}" to R.string.{resource},'
        for stable_key, resource in sorted(exercise_keys.items())
    )
    lines.extend(["    )", "", "    val tissueEducationIds: Map<String, TissueEducationResourceIds> = mapOf("])
    for stable_key, resources in sorted(tissue_keys.items()):
        lines.append(
            f'        "{_kotlin_string(stable_key)}" to TissueEducationResourceIds('
            f"R.string.{resources[0]}, R.string.{resources[1]}, "
            f"R.string.{resources[2]}, R.string.{resources[3]}),"
        )
    lines.extend(["    )", "", "    val exerciseDescriptionIds: Map<String, Int> = mapOf("])
    lines.extend(
        f'        "{_kotlin_string(stable_key)}" to R.string.{resource},'
        for stable_key, resource in sorted(exercise_description_keys.items())
    )
    lines.extend(["    )", "", "    val programNameIds: Map<String, Int> = mapOf("])
    lines.extend(
        f'        "{_kotlin_string(stable_key)}" to R.string.{resource},'
        for stable_key, resource in sorted(program_name_keys.items())
    )
    lines.extend(["    )", "}", ""])
    return "\n".join(lines)


def _artifacts() -> dict[Path, str]:
    ui = _xlsx_rows(AUTHORITY, "02_UI_APPROVED_FULL")
    exercises = _xlsx_rows(AUTHORITY, "03_EXERCISE_NAMES")
    metadata = _xlsx_rows(AUTHORITY, "04_METADATA_DICTIONARY")
    tissues = _xlsx_rows(AUTHORITY, "05_TISSUE_EDU_EN")
    baseline = _xlsx_rows(AUTHORITY, "10_CURRENT_BASELINE_APPROVAL")
    baseline_generated = _csv_rows(BASELINE_GENERATED_EN)
    if len(ui) != 612 or len(exercises) != 253 or len(metadata) != 1834 or len(tissues) != 92:
        raise ValueError("Localization authority row counts do not match the approved baseline")
    if any(_text(row.get("authorityStatus")) != "APPROVED" for row in ui + exercises + tissues):
        raise ValueError("Localization authority contains an unapproved runtime row")
    check_required = next(
        _text(row.get("Value"))
        for row in baseline
        if _text(row.get("Rule")) == "Current-baseline CHECK_REQUIRED"
    )
    if check_required != "0":
        raise ValueError("Current-baseline CHECK_REQUIRED is not zero")

    ui_base, ui_en, strings_en, exact_ui, ui_patterns, exact_counts = _ui_assets(
        ui,
        metadata,
        baseline_generated,
    )
    strength_target_keys = _strength_target_name_keys(exact_ui)
    exercise_base, exercise_en, exercise_keys = _exercise_assets(exercises)
    exercise_description_base, exercise_description_en, exercise_description_keys = (
        _exercise_description_assets()
    )
    program_name_base, program_name_en, program_name_keys = _program_name_assets()
    tissue_base, tissue_en, tissue_keys = _tissue_assets(tissues)
    manifest = {
        "authority": AUTHORITY.name,
        "authoritySha256": hashlib.sha256(AUTHORITY.read_bytes()).hexdigest().upper(),
        "pinnedBaseline": "5eecdac1d3dc87fe5a8982221b891345a8794710",
        "currentBaselineCheckRequired": 0,
        "uiApprovedRows": len(ui),
        "exerciseApprovedRows": len(exercises),
        "exerciseDescriptionLocalizedRows": len(exercise_description_keys),
        "seedProgramLocalizedRows": len(program_name_keys),
        "strengthTargetLocalizedRows": len(strength_target_keys),
        "metadataAuthoritativeRows": len(metadata),
        "tissueApprovedRows": len(tissues),
        "exactUiRuntimeEntries": len(exact_ui),
        "workbookExactUiRuntimeEntries": exact_counts["workbookExact"],
        "metadataExactUiRuntimeEntries": exact_counts["metadataExact"],
        "codexGeneratedEnglishEntries": exact_counts["codexGeneratedExact"],
        "dynamicUiRuntimeEntries": len(ui_patterns),
        "provenance": "LOC-AUTH-2026-08-09-v2-FULL-APPROVED",
    }
    return {
        OUTPUTS["ui_base"]: ui_base,
        OUTPUTS["ui_en"]: ui_en,
        OUTPUTS["strings_en"]: strings_en,
        OUTPUTS["exercise_base"]: exercise_base,
        OUTPUTS["exercise_en"]: exercise_en,
        OUTPUTS["exercise_description_base"]: exercise_description_base,
        OUTPUTS["exercise_description_en"]: exercise_description_en,
        OUTPUTS["program_name_base"]: program_name_base,
        OUTPUTS["program_name_en"]: program_name_en,
        OUTPUTS["tissue_base"]: tissue_base,
        OUTPUTS["tissue_en"]: tissue_en,
        OUTPUTS["kotlin"]: _generated_kotlin(
            exact_ui,
            ui_patterns,
            strength_target_keys,
            exercise_keys,
            exercise_description_keys,
            program_name_keys,
            tissue_keys,
        ),
        OUTPUTS["manifest"]: json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    for path, content in _artifacts().items():
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8", newline="\n")
        elif not path.exists() or path.read_text(encoding="utf-8") != content:
            raise ValueError(f"Stale localization authority output: {path.relative_to(ROOT)}")
        print(f"{path.relative_to(ROOT).as_posix()} sha256={hashlib.sha256(content.encode()).hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
