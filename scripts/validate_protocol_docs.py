#!/usr/bin/env python3
"""Validate the canonical protocol registry and Markdown contracts."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROTOCOL_ROOT = ROOT / "docs" / "protocols"
REGISTRY_PATH = PROTOCOL_ROOT / "protocol_registry.json"

FAMILY_FIELDS = {"familyId", "titleKo", "descriptionKo", "indexDocument"}
PROTOCOL_FIELDS = {
    "protocolId",
    "familyId",
    "titleKo",
    "titleEn",
    "currentVersion",
    "status",
    "implementationStatus",
    "evidenceProfile",
    "canonicalDocument",
    "implementedFromAppVersion",
    "lastAuditedCommit",
    "sourceAnchors",
    "testAnchors",
    "authorityAssets",
    "supportingDocuments",
    "knownGaps",
    "supersedes",
    "publicSummaryAvailable",
}
STATUSES = {"ACTIVE", "EXPERIMENTAL", "DRAFT", "SUPERSEDED", "RETIRED"}
IMPLEMENTATION_STATUSES = {
    "IMPLEMENTED",
    "PARTIALLY_IMPLEMENTED",
    "SPECIFICATION_ONLY",
    "NOT_APPLICABLE",
    "UNKNOWN_PENDING_AUDIT",
}
EVIDENCE_LABELS = {
    "DIRECT_RESEARCH_SUPPORT",
    "RESEARCH_TRANSFER",
    "MECHANISTIC_SUPPORT",
    "PRODUCT_POLICY",
    "ENGINEERING_HEURISTIC",
    "USER_APPROVED_POLICY",
    "LOW_CONFIDENCE_PROXY",
    "MIXED",
}
HEADINGS = [
    "일반 사용자용 요약",
    "목적",
    "적용 범위",
    "비적용 범위",
    "용어",
    "입력 데이터",
    "계산 또는 분류 계약",
    "집계 방식",
    "출력과 UI 해석",
    "예외 및 fallback",
    "개인화 또는 보정",
    "연구 근거",
    "제품 정책 및 휴리스틱",
    "알려진 한계",
    "현재 구현 상태",
    "구현 위치",
    "검증 테스트",
    "권위 자산",
    "관련 문서",
    "변경 이력",
]
CANONICAL_FAMILY_DIRS = {
    "ofi",
    "connective_tissue",
    "badminton",
    "strength",
    "program_builder",
}
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
PLACEHOLDER = re.compile(r"\b(?:TODO|TBD)\b|작성 예정", re.IGNORECASE)


class Errors:
    def __init__(self) -> None:
        self.items: list[str] = []

    def add(self, protocol_id: str, file: str, field: str, action: str) -> None:
        self.items.append(
            f"[{protocol_id}] file={file} field={field}: {action}"
        )


def load_registry(errors: Errors) -> dict | None:
    try:
        return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError:
        errors.add("REGISTRY", str(REGISTRY_PATH), "file", "Create protocol_registry.json.")
    except json.JSONDecodeError as exc:
        errors.add(
            "REGISTRY",
            str(REGISTRY_PATH),
            f"JSON line {exc.lineno} column {exc.colno}",
            f"Fix invalid JSON: {exc.msg}.",
        )
    return None


def duplicate_values(items: list[dict], field: str) -> set[str]:
    seen: set[str] = set()
    duplicates: set[str] = set()
    for item in items:
        value = item.get(field)
        if isinstance(value, str):
            if value in seen:
                duplicates.add(value)
            seen.add(value)
    return duplicates


def metadata_value(text: str, field: str) -> str | None:
    match = re.search(
        rf"^\|\s*{re.escape(field)}\s*\|\s*([^|]+?)\s*\|$",
        text,
        re.MULTILINE,
    )
    return match.group(1).strip() if match else None


def without_known_gaps(text: str) -> str:
    start = text.find("## 14. 알려진 한계")
    end = text.find("## 15. 현재 구현 상태")
    if start >= 0 and end > start:
        return text[:start] + text[end:]
    return text


def validate_links(protocol_id: str, path: Path, text: str, errors: Errors) -> None:
    for raw_target in MARKDOWN_LINK.findall(text):
        target = raw_target.strip().split("#", 1)[0].replace("%20", " ")
        if not target or re.match(r"^(?:https?|mailto):", target):
            continue
        resolved = (
            ROOT / target.lstrip("/")
            if target.startswith("/")
            else path.parent / target
        ).resolve()
        if not resolved.exists():
            errors.add(
                protocol_id,
                str(path.relative_to(ROOT)),
                "Markdown link",
                f"Fix or remove broken local link `{raw_target}`.",
            )


def validate_protocol(item: dict, family_ids: set[str], errors: Errors) -> None:
    protocol_id = str(item.get("protocolId", "UNKNOWN"))
    document = str(item.get("canonicalDocument", ""))
    missing = sorted(PROTOCOL_FIELDS - item.keys())
    if missing:
        errors.add(
            protocol_id,
            document or str(REGISTRY_PATH.relative_to(ROOT)),
            "registry fields",
            f"Add required fields: {', '.join(missing)}.",
        )
        return

    if item["familyId"] not in family_ids:
        errors.add(protocol_id, document, "familyId", "Reference a registered family.")
    if item["status"] not in STATUSES:
        errors.add(protocol_id, document, "status", f"Use one of {sorted(STATUSES)}.")
    if item["implementationStatus"] not in IMPLEMENTATION_STATUSES:
        errors.add(
            protocol_id,
            document,
            "implementationStatus",
            f"Use one of {sorted(IMPLEMENTATION_STATUSES)}.",
        )
    if not isinstance(item["currentVersion"], str) or not SEMVER.fullmatch(item["currentVersion"]):
        errors.add(protocol_id, document, "currentVersion", "Use semantic version X.Y.Z.")
    if not isinstance(item["evidenceProfile"], list) or not item["evidenceProfile"]:
        errors.add(protocol_id, document, "evidenceProfile", "Register at least one evidence label.")
    else:
        unknown = sorted(set(item["evidenceProfile"]) - EVIDENCE_LABELS)
        if unknown:
            errors.add(protocol_id, document, "evidenceProfile", f"Remove unknown labels: {unknown}.")

    implementation = item["implementationStatus"]
    if implementation in {"IMPLEMENTED", "PARTIALLY_IMPLEMENTED"} and not item["sourceAnchors"]:
        errors.add(protocol_id, document, "sourceAnchors", "Add current runtime source anchors.")
    if implementation == "IMPLEMENTED" and not item["testAnchors"]:
        errors.add(
            protocol_id,
            document,
            "testAnchors",
            "Add current tests or change implementation status with a documented reason.",
        )
    if implementation == "PARTIALLY_IMPLEMENTED" and not item["knownGaps"]:
        errors.add(protocol_id, document, "knownGaps", "Describe the missing runtime behavior.")
    if implementation == "SPECIFICATION_ONLY" and item["implementedFromAppVersion"] is not None:
        errors.add(
            protocol_id,
            document,
            "implementedFromAppVersion",
            "Use null for a specification-only protocol.",
        )

    for field in ("sourceAnchors", "testAnchors", "authorityAssets", "supportingDocuments"):
        if not isinstance(item[field], list):
            errors.add(protocol_id, document, field, "Use a JSON array.")
            continue
        for target in item[field]:
            if not isinstance(target, str) or not (ROOT / target).exists():
                errors.add(protocol_id, document, field, f"Fix nonexistent repository path `{target}`.")

    path = PROTOCOL_ROOT / document
    if not path.is_file():
        errors.add(protocol_id, document, "canonicalDocument", "Create the registered Markdown file.")
        return
    text = path.read_text(encoding="utf-8")
    for number, heading in enumerate(HEADINGS, start=1):
        expected = f"## {number}. {heading}"
        if expected not in text:
            errors.add(protocol_id, document, f"heading {number}", f"Add exact heading `{expected}`.")

    for field in (
        "Protocol ID",
        "Protocol version",
        "Status",
        "Implementation status",
        "Implemented from app version",
        "Last audited commit",
        "Evidence profile",
        "Supersedes",
    ):
        if metadata_value(text, field) is None:
            errors.add(protocol_id, document, "metadata table", f"Add metadata row `{field}`.")
    expected_metadata = {
        "Protocol ID": item["protocolId"],
        "Protocol version": item["currentVersion"],
        "Status": item["status"],
        "Implementation status": item["implementationStatus"],
        "Last audited commit": item["lastAuditedCommit"],
    }
    for field, expected in expected_metadata.items():
        actual = metadata_value(text, field)
        if actual is not None and actual != expected:
            errors.add(protocol_id, document, field, f"Match registry value `{expected}`.")

    outside_gaps = without_known_gaps(text)
    if item["status"] == "ACTIVE" and PLACEHOLDER.search(outside_gaps):
        errors.add(
            protocol_id,
            document,
            "placeholder",
            "Resolve placeholder text or move an explicit unresolved fact into 알려진 한계.",
        )
    if implementation == "IMPLEMENTED":
        absent_term = re.search(
            r"(?:required runtime|필수 runtime).{0,80}(?:neutral|absent|disabled|미구현|없음)",
            outside_gaps,
            re.IGNORECASE | re.DOTALL,
        )
        if absent_term or "Implementation status | SPECIFICATION_ONLY" in text:
            errors.add(
                protocol_id,
                document,
                "implementation claim",
                "Do not mark IMPLEMENTED while required runtime terms are absent or neutral.",
            )
    validate_links(protocol_id, path, text, errors)


def validate() -> Errors:
    errors = Errors()
    registry = load_registry(errors)
    if registry is None:
        return errors
    families = registry.get("families")
    protocols = registry.get("protocols")
    if not isinstance(families, list) or not isinstance(protocols, list):
        errors.add("REGISTRY", str(REGISTRY_PATH.relative_to(ROOT)), "top-level", "Use family and protocol arrays.")
        return errors

    for duplicate in sorted(duplicate_values(families, "familyId")):
        errors.add(duplicate, str(REGISTRY_PATH.relative_to(ROOT)), "familyId", "Remove duplicate family ID.")
    for duplicate in sorted(duplicate_values(protocols, "protocolId")):
        errors.add(duplicate, str(REGISTRY_PATH.relative_to(ROOT)), "protocolId", "Remove duplicate protocol ID.")

    family_ids: set[str] = set()
    for family in families:
        if not isinstance(family, dict):
            errors.add("REGISTRY", str(REGISTRY_PATH.relative_to(ROOT)), "families", "Use JSON objects.")
            continue
        missing = sorted(FAMILY_FIELDS - family.keys())
        family_id = str(family.get("familyId", "UNKNOWN"))
        if missing:
            errors.add(family_id, str(REGISTRY_PATH.relative_to(ROOT)), "family fields", f"Add: {missing}.")
        else:
            family_ids.add(family_id)
            index_path = PROTOCOL_ROOT / family["indexDocument"]
            if not index_path.is_file():
                errors.add(family_id, str(index_path.relative_to(ROOT)), "indexDocument", "Create the family index.")

    for item in protocols:
        if not isinstance(item, dict):
            errors.add("REGISTRY", str(REGISTRY_PATH.relative_to(ROOT)), "protocols", "Use JSON objects.")
            continue
        validate_protocol(item, family_ids, errors)

    registered = {
        (PROTOCOL_ROOT / item["canonicalDocument"]).resolve()
        for item in protocols
        if isinstance(item, dict) and isinstance(item.get("canonicalDocument"), str)
    }
    on_disk = {
        path.resolve()
        for directory in CANONICAL_FAMILY_DIRS
        for path in (PROTOCOL_ROOT / directory).rglob("*.md")
    }
    for path in sorted(on_disk - registered):
        errors.add(
            "UNREGISTERED",
            str(path.relative_to(ROOT)),
            "canonicalDocument",
            "Register this canonical file or move it outside canonical family directories.",
        )
    return errors


def self_test() -> None:
    assert SEMVER.fullmatch("1.2.3")
    assert not SEMVER.fullmatch("v1.2.3")
    fixture = "| Protocol ID | TEST-ONE |\n## 14. 알려진 한계\n- TODO\n## 15. 현재 구현 상태\n"
    assert metadata_value(fixture, "Protocol ID") == "TEST-ONE"
    assert not PLACEHOLDER.search(without_known_gaps(fixture))


def main() -> int:
    self_test()
    errors = validate()
    if errors.items:
        print(f"Protocol documentation validation failed with {len(errors.items)} error(s):")
        for item in errors.items:
            print(f"- {item}")
        return 1
    registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    print(
        "Protocol documentation validation passed: "
        f"{len(registry['families'])} families, {len(registry['protocols'])} protocols."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
