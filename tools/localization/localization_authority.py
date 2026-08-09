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

OUTPUTS = {
    "ui_base": ROOT / "app/src/main/res/values/localization_generated.xml",
    "ui_en": ROOT / "app/src/main/res/values-en/localization_generated.xml",
    "strings_en": ROOT / "app/src/main/res/values-en/strings.xml",
    "exercise_base": ROOT / "app/src/main/res/values/exercise_names.xml",
    "exercise_en": ROOT / "app/src/main/res/values-en/exercise_names.xml",
    "tissue_base": ROOT / "app/src/main/res/values/tissue_education.xml",
    "tissue_en": ROOT / "app/src/main/res/values-en/tissue_education.xml",
    "kotlin": (
        ROOT
        / "app/src/main/java/com/training/trackplanner/localization/GeneratedLocalizationCatalogue.kt"
    ),
    "manifest": ROOT / "docs/generated/localization_authority_manifest.json",
}

ANDROID_PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")
KOTLIN_PLACEHOLDER = re.compile(r"\$\{[^}]+}|\$[A-Za-z_][A-Za-z0-9_]*")
CELL_REF = re.compile(r"([A-Z]+)(\d+)")


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


def _android_text(value: str) -> str:
    pieces: list[str] = []
    cursor = 0
    for match in ANDROID_PLACEHOLDER.finditer(value):
        prefix = value[cursor : match.start()].replace("%", "%%")
        pieces.append(prefix)
        pieces.append(match.group(0))
        cursor = match.end()
    pieces.append(value[cursor:].replace("%", "%%"))
    normalized = "".join(pieces).replace("\\", "\\\\").replace("'", "\\'")
    return escape(normalized, {"\"": "&quot;"})


def _xml_strings(entries: list[tuple[str, str]]) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key, value in sorted(entries):
        lines.append(f'    <string name="{key}">{_android_text(value)}</string>')
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def _kotlin_string(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    )


def _ui_assets(rows: list[dict[str, object]]) -> tuple[str, str, str, dict[str, str]]:
    android_rows = [row for row in rows if _text(row.get("sourceType")) == "ANDROID_RESOURCE"]
    generated: dict[tuple[str, str], str] = {}
    for row in rows:
        if _text(row.get("sourceType")) == "ANDROID_RESOURCE":
            continue
        korean = _text(row.get("koreanSource"))
        english = _text(row.get("englishTarget"))
        if korean and english:
            generated.setdefault(
                (korean, english),
                _resource_key("loc_ui", korean, english),
            )

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
    exercise_keys: dict[str, str],
    tissue_keys: dict[str, tuple[str, str, str, str]],
) -> str:
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
        "internal object GeneratedLocalizationCatalogue {",
        "    val exactUiTextIds: Map<String, Int> = mapOf(",
    ]
    lines.extend(
        f'        "{_kotlin_string(source)}" to R.string.{resource},'
        for source, resource in sorted(exact_ui.items())
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
    lines.extend(["    )", "}", ""])
    return "\n".join(lines)


def _artifacts() -> dict[Path, str]:
    ui = _xlsx_rows(AUTHORITY, "02_UI_APPROVED_FULL")
    exercises = _xlsx_rows(AUTHORITY, "03_EXERCISE_NAMES")
    metadata = _xlsx_rows(AUTHORITY, "04_METADATA_DICTIONARY")
    tissues = _xlsx_rows(AUTHORITY, "05_TISSUE_EDU_EN")
    baseline = _xlsx_rows(AUTHORITY, "10_CURRENT_BASELINE_APPROVAL")
    if len(ui) != 612 or len(exercises) != 257 or len(metadata) != 1834 or len(tissues) != 92:
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

    ui_base, ui_en, strings_en, exact_ui = _ui_assets(ui)
    exercise_base, exercise_en, exercise_keys = _exercise_assets(exercises)
    tissue_base, tissue_en, tissue_keys = _tissue_assets(tissues)
    manifest = {
        "authority": AUTHORITY.name,
        "authoritySha256": hashlib.sha256(AUTHORITY.read_bytes()).hexdigest().upper(),
        "pinnedBaseline": "5eecdac1d3dc87fe5a8982221b891345a8794710",
        "currentBaselineCheckRequired": 0,
        "uiApprovedRows": len(ui),
        "exerciseApprovedRows": len(exercises),
        "metadataAuthoritativeRows": len(metadata),
        "tissueApprovedRows": len(tissues),
        "exactUiRuntimeEntries": len(exact_ui),
        "provenance": "LOC-AUTH-2026-08-09-v2-FULL-APPROVED",
    }
    return {
        OUTPUTS["ui_base"]: ui_base,
        OUTPUTS["ui_en"]: ui_en,
        OUTPUTS["strings_en"]: strings_en,
        OUTPUTS["exercise_base"]: exercise_base,
        OUTPUTS["exercise_en"]: exercise_en,
        OUTPUTS["tissue_base"]: tissue_base,
        OUTPUTS["tissue_en"]: tissue_en,
        OUTPUTS["kotlin"]: _generated_kotlin(exact_ui, exercise_keys, tissue_keys),
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
