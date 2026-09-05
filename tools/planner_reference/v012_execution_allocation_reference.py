"""Independent v0.12 finite execution reference, extending v0.10 reallocation.
The original v0.10 source is unchanged. All units here are scheduling units.
"""
import argparse
import json
from pathlib import Path

TARGET = Path(__file__).parent / "fixtures/v012_execution_allocation_golden.json"


def allocate(capacity, continuity, minimums, share, core, flexible):
    reserve = min(core, continuity, capacity)
    funded = [0] * len(minimums)
    for i, minimum in enumerate(minimums):
        if minimum > 0 and sum(funded) + minimum <= capacity - reserve:
            funded[i] = minimum
    target = min(capacity - reserve, max(sum(funded), int(capacity * share + .5)))
    expandable = [i for i in flexible if funded[i]]
    cursor = 0
    while sum(funded) < target and expandable:
        funded[expandable[cursor % len(expandable)]] += 1
        cursor += 1
    return dict(continuity=min(continuity, max(0, capacity - sum(funded))),
                material=funded, deferred=[i for i, n in enumerate(funded) if not n])


def golden():
    cases = [
        ("preserve", 5, 5, [], 0, 1, []),
        ("successful_5x5_high_resistance", 5, 5, [2], .30, 1, [0]),
        ("strength_high_badminton", 5, 5, [3], .30, 1, []),
        ("resistance_and_badminton", 8, 11, [2, 2], .45, 2, [0]),
        ("overlapping_direct_objectives_one_drill", 8, 8, [3], .35, 2, []),
        ("distinct_performance_qualities", 12, 12, [3, 3, 2], .48, 2, []),
        ("insufficient_capacity", 5, 5, [3, 2], .40, 1, []),
        ("developmental_not_material", 5, 5, [], 0, 1, []),
        ("no_safe_candidate", 5, 5, [], .30, 1, []),
        ("three_day_binding", 12, 15, [3, 3, 3, 3], .48, 2, []),
        ("five_day_available", 20, 15, [3, 3, 3, 3], .48, 2, []),
        ("45_minutes_binding", 8, 15, [3, 3, 3], .48, 2, []),
        ("90_minutes_available", 16, 15, [3, 3, 3], .48, 2, []),
        ("madcow_recovery_multigap", 8, 11, [2, 2], .48, 2, [1]),
    ]
    output = []
    for name, capacity, continuity, minimums, share, core, flexible in cases:
        expected = allocate(capacity, continuity, minimums, share, core, flexible)
        assert expected["continuity"] + sum(expected["material"]) <= capacity
        output.append(dict(name=name, capacity=capacity, continuity=continuity, minimums=minimums,
                           share=share, core=core, flexible=flexible, expected=expected))
    return dict(version="RECORD_BASED_PLANNER_0.12.0_REFERENCE_1", cases=output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = json.dumps(golden(), indent=2) + "\n"
    if args.write:
        TARGET.write_text(rendered, encoding="utf-8")
    else:
        assert TARGET.read_text(encoding="utf-8") == rendered, "Stale execution golden"
    print(f"Execution golden: {len(golden()['cases'])} cases passed")
