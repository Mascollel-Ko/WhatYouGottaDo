#!/usr/bin/env python3
"""Read-only source parity check. Requires the frozen Git object; never regenerates a golden."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REF = "f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9"
BASE = "app/src/main/java/com/training/trackplanner/data/"
FILES = ["ProgramAutoBuilder", "ProgramRuleTables", "ProgramSlotAllocator", "ProgramDaySelector",
         "ProgramDraftModel", "ProgramIntensityResolver", "ProgramExerciseSpec",
         "ProgramCandidateAuthority", "ProgramGenerationService", "ProgramSkeletonGenerator"]

def source(name):
    return subprocess.check_output(["git", "show", f"{REF}:{BASE}{name}.kt"], cwd=ROOT).decode("utf-8").replace("\r\n", "\n")

sources = {name: source(name) for name in FILES}
names = {"GeneratedProgramSkeleton": "LegacyAutoSkeleton", "ProgramSkeletonItem": "LegacyAutoSkeletonItem",
         "ProgramSkeletonRequest": "LegacyAutoRequest", "ProgramAutoBuilder": "LegacyAutoProgramBuilder",
         "ProgramAutoUsage": "LegacyAutoUsage", "ResolvedProgramExercise": "ResolvedLegacyAutoExercise"}
for code in sources.values():
    for name in re.findall(r"(?:class|object)\s+(\w+)", code):
        if name.startswith("Program") and name not in names:
            names[name] = "LegacyAuto" + name[7:]
names["ProgramSkeletonGenerator"] = "LegacyAutoSkeletonGenerator"

def body(code):
    return "\n".join(line for line in code.splitlines()
                     if not line.startswith(("package ", "import ", "// Mechanically isolated from ",
                                             "// Frozen product rules:"))).strip()

for old_name, code in sources.items():
    output_name = names.get(old_name, "LegacyAutoDraftModel")
    if old_name == "ProgramSkeletonGenerator":
        output_name = "LegacyAutoModels"
        code = code.split("/** Compatibility entry point")[0]
    if old_name == "ProgramGenerationService":
        code = re.sub(r"return ProgramSkeletonGenerator\(\)\.generate\([\s\S]*?\n        \)",
                      "return ProgramAutoBuilder().build(request = request, exercises = exercises)", code)
    expected = re.sub(r"\b\w+\b", lambda match: names.get(match[0], match[0]), code)
    actual = (ROOT / BASE / "program/legacy" / f"{output_name}.kt").read_text(encoding="utf-8")
    assert body(expected) == body(actual), f"Frozen source drift: {old_name} -> {output_name}"
    print(f"PASS {old_name} -> {output_name}")
print("10/10 frozen files: only package/type/import changes and the documented DAO entry adaptation.")
