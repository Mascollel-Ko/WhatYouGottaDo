"""Independent run-local evidence boundary goldens (synthetic weekly observations only)."""
import argparse
import copy
import json
from datetime import date, timedelta
from pathlib import Path
from v013_training_state_reference import classify_weeks, auto_frequency, permits_release
from v013_training_state_cases import source

GOLDEN=Path(__file__).with_name("fixtures")/"v0131_run_golden.json"

def make(units, performance, gap_cause=None):
    end=date(2026,8,30)
    weeks=[]
    annotations={}
    for i,n in enumerate(units):
        start=end-timedelta(days=(len(units)-i)*7-1)
        response=performance[i]
        weeks.append(dict(start=str(start),end=str(start+timedelta(days=6)),units=n,minutes=n*1.5,
            days=4 if n>20 else 1,courtLoad=100,medianRpe=7,
            performanceResponse=response,negativeBreadth=0 if response is not None else None,
            rpeDrift=0 if response is not None else None))
        if n<20 and gap_cause:
            annotations[str(start)]=dict(cause=gap_cause,source="USER_CONFIRMED")
    return dict(cutoff=str(end),weekAnnotations=annotations),weeks

def cases():
    return {
        "no_cross_run_borrowing":make([42]*6+[10]+[30]*2,[None]*7+[.2]*2,"FATIGUE"),
        "qualified_five_week_run":make([40,41,43,40,42],[None,.2,.1,None,.2]),
        "only_qualified_workload_released":make([60]*6+[10]+[40]*5,[None]*7+[.2]*5,"FATIGUE"),
        "confirmed_neutral_bridge":make([42,41,18,43,40],[.2]*5,"EXTERNAL"),
        "fatigue_never_bridges":make([42,41,18,43,40],[.2]*5,"FATIGUE"),
        "unknown_never_bridges":make([42,41,18,43,40],[.2]*5),
        "inference_cannot_release_high":make([42,41,18,43,40],[.2]*5),
    }

def render():
    result=[]
    for name,(inputs,weeks) in cases().items():
        actual,capacity=classify_weeks(copy.deepcopy(weeks),inputs,date.fromisoformat(inputs["cutoff"]))
        result.append(dict(name=name,input=inputs,weeks=weeks,expectedWeeks=actual,expectedSustainable=capacity))
    values={r["name"]:r["expectedSustainable"] for r in result}
    assert values["no_cross_run_borrowing"]["confidence"]!="HIGH"
    assert values["qualified_five_week_run"]["confidence"]=="HIGH"
    assert values["only_qualified_workload_released"]["sustainableWeeklyControllableUnits"]==40
    bridge=values["confirmed_neutral_bridge"]["runs"][0]
    assert bridge["observedSuccessfulWeeks"]==4 and bridge["calendarSpanWeeks"]==5
    for key in ("fatigue_never_bridges","unknown_never_bridges","inference_cannot_release_high"):
        assert values[key]["confidence"]!="HIGH"
    # Court load is deliberately absent from AUTO frequency authority.
    from v013_training_state_reference import assess
    a=assess(source())
    assert auto_frequency(a,3,8)>=4 and permits_release(a)
    a["state"]="MALADAPTATION_PATTERN"
    assert auto_frequency(a,3,8)==3
    return json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False)+"\n"

if __name__=="__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("--write",action="store_true"); args=parser.parse_args()
    rendered=render()
    if args.write: GOLDEN.write_text(rendered,encoding="utf-8")
    else: assert GOLDEN.read_text(encoding="utf-8")==rendered
    print("PASS: 7 v0.13.1 run-local cases and frequency/release assertions")
