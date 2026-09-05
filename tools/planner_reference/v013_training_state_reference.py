"""Independent planner-only training-state reference. No exercise selection or OFI engine.

All thresholds are engineering policies, not physiological/diagnostic safe ranges.
Private inputs may be supplied with --input; no private data belongs in the golden.
"""
import argparse
import json
import math
from datetime import date, timedelta
from pathlib import Path
from statistics import median

AXES = ("highForceNeuralScore", "systemicMuscularScore", "localMuscularScore",
        "highSpeedScore", "reactiveScore")
CONTROL = {"RESISTANCE", "STRUCTURED_BADMINTON_DRILL", "ATHLETIC_PERFORMANCE_DRILL"}
MAJOR = {"LOWER_KNEE", "POSTERIOR_CHAIN", "HORIZONTAL_PUSH", "HORIZONTAL_PULL",
         "VERTICAL_PUSH", "VERTICAL_PULL"}
LOW_WEEK_RATIO = .625
BASELINE_FLOOR = 8.0
GOLDEN = Path(__file__).with_name("fixtures") / "v013_training_state_golden.json"


def clip(x, low=0.0, high=1.0):
    return max(low, min(high, x))


def med(values):
    values = [x for x in values if x is not None and math.isfinite(x)]
    return median(values) if values else None


def mean_available(parts):
    valid = [(v, w) for v, w in parts if v is not None]
    return sum(v*w for v, w in valid)/sum(w for _, w in valid) if valid else None


def weighted_median(parts):
    parts = sorted((v, w) for v, w in parts if v is not None and w > 0)
    half = sum(w for _, w in parts)/2
    seen = 0.0
    for value, weight in parts:
        seen += weight
        if seen >= half:
            return value
    return None


def within(rows, start, end):
    return [r for r in rows if start <= date.fromisoformat(r["date"]) <= end]


def relative(current, baseline):
    center = med(baseline)
    mad = med([abs(x-center) for x in baseline]) if center is not None else None
    scale = max(BASELINE_FLOOR, 1.4826*mad) if mad is not None else None
    z = (current-center)/scale if current is not None and scale else None
    return center, mad, scale, z


def matched(current, prior, load_supported):
    """Nearest prescription matching; each prior set used at most once per component.
    Confidence counts distinct current dates, never multiple near-identical working sets.
    """
    values = {"load": [], "reps": [], "rpe": []}
    dates = set()
    with_rpe = set()
    for component in values:
        unused = list(sorted(prior, key=lambda r: (r["date"], r["setIndex"])))
        for row in sorted(current, key=lambda r: (r["date"], r["setIndex"])):
            possible = []
            for i, old in enumerate(unused):
                both_rpe = row.get("rpe") is not None and old.get("rpe") is not None
                same_weight = (abs(row["weightKg"]-old["weightKg"]) <= .02*old["weightKg"]
                               if old["weightKg"] > 0 else row["weightKg"] == 0)
                rep_difference = abs(row["reps"]-old["reps"])
                if component == "load":
                    valid = load_supported and old["weightKg"] > 0 and row["weightKg"] > 0 and row["reps"] > 0
                    valid = valid and rep_difference <= (1 if both_rpe else 0)
                    valid = valid and (not both_rpe or row["rpe"] <= old["rpe"]+.5)
                elif component == "reps":
                    valid = same_weight and row["reps"] > 0 and old["reps"] > 0
                else:
                    valid = same_weight and rep_difference <= 1 and row["reps"] > 0 and both_rpe
                if valid:
                    possible.append((rep_difference, abs(row["weightKg"]-old["weightKg"]), old["date"], i))
            if not possible:
                continue
            old = unused.pop(min(possible)[-1])
            value = (row["weightKg"]/old["weightKg"]-1 if component == "load" else
                     row["reps"]-old["reps"] if component == "reps" else old["rpe"]-row["rpe"])
            values[component].append(value)
            dates.add(row["date"])
            if row.get("rpe") is not None and old.get("rpe") is not None:
                with_rpe.add(row["date"])
    load = med(values["load"])
    reps = med(values["reps"])
    rpe = med(values["rpe"])
    components = [(clip(load/.05, -1, 1) if load is not None else None, .40),
                  (clip(reps/3, -1, 1) if reps is not None else None, .35),
                  (clip(rpe/1.5, -1, 1) if rpe is not None else None, .25)]
    return dict(matchedLoadChange=load, matchedRepChange=reps, matchedRpeEfficiency=rpe,
                rawPerformanceResponse=mean_available(components), validRawComparisonCount=len(dates),
                rawConfidence=clip(len(dates)/6)*(1.0 if with_rpe else .6))


def adaptation(source, current, prior, posterior=True):
    responses = []
    keys = sorted({r["stableKey"] for r in current+prior if source["domains"].get(r["stableKey"]) == "RESISTANCE"})
    for key in keys:
        metric = source.get("metadata", {}).get(key, {}).get("progressMetricType", "")
        raw = matched([r for r in current if r["stableKey"] == key],
                      [r for r in prior if r["stableKey"] == key],
                      metric in {"LOAD_REPS", "VOLUME_LOAD", "ESTIMATED_1RM"})
        signal = source.get("signals", {}).get(key, {}) if posterior else {}
        change = signal.get("posteriorChangePercent")
        count = signal.get("observationCount", 0)
        p = math.tanh(change/5) if change is not None and count >= 2 else None
        pc = clip(count/6) if p is not None else 0.0
        response = mean_available([(p, .65), (raw["rawPerformanceResponse"], .35)])
        # Posterior and raw performance reuse workout observations: cap correlated confidence.
        confidence = max(pc, raw["rawConfidence"])
        responses.append(dict(stableKey=key, movement=source["coverage"].get(key, "OTHER"),
                              posteriorChangePercent=change, posteriorResponse=p, posteriorConfidence=pc,
                              responseScore=response, confidence=confidence, **raw))
    groups = {}
    for key in sorted(MAJOR):
        members = [r for r in responses if r["movement"] == key and r["responseScore"] is not None and r["confidence"] > 0]
        if members:
            groups[key] = dict(response=weighted_median([(r["responseScore"], r["confidence"]) for r in members]),
                               confidence=max(r["confidence"] for r in members))
    total = sum(v["confidence"] for v in groups.values())
    global_response = sum(v["response"]*v["confidence"] for v in groups.values())/total if total else None
    positive = sum(v["confidence"] for v in groups.values() if v["response"] >= .20)/total if total else None
    negative = sum(v["confidence"] for v in groups.values() if v["response"] <= -.20)/total if total else None
    drift = med([-r["matchedRpeEfficiency"] for r in responses if r["matchedRpeEfficiency"] is not None])
    return dict(exercises=responses, movements=groups, globalAdaptationResponse=global_response,
                positiveBreadth=positive, negativeBreadth=negative, rpeDrift=drift)


def weekly_context(source, cutoff):
    rows = [r for r in source["records"] if source["domains"].get(r["stableKey"]) in CONTROL]
    court = {r["end"]: r["load"] for r in source.get("weeklyCourtLoad", [])}
    weeks = []
    first = min((date.fromisoformat(r["date"]) for r in source["records"]), default=cutoff)
    for offset in reversed(range(12)):
        end = complete_week_end(cutoff)-timedelta(days=offset*7)
        start = end-timedelta(days=6)
        if start < first:
            continue  # incomplete leading bins cannot prove sustainable weekly workload
        group = within(rows, start, end)
        sessions = {}
        for row in group:
            sessions.setdefault((row["date"], row["stableKey"]), []).append(row)
        seconds = sum(sum(r["seconds"] if r["seconds"] > 0 else 45 for r in batch) +
                      (len(batch)-1)*source.get("restSeconds", {}).get(key[1], 60)
                      for key, batch in sessions.items())
        performance = adaptation(source, group, within(rows, start-timedelta(days=7), start-timedelta(days=1)), False)
        weeks.append(dict(start=str(start), end=str(end), units=len(group), minutes=seconds/60,
                          days=len({r["date"] for r in group}), courtLoad=court.get(str(end), 0.0),
                          medianRpe=med([r.get("rpe") for r in group]),
                          performanceResponse=performance["globalAdaptationResponse"],
                          negativeBreadth=performance["negativeBreadth"], rpeDrift=performance["rpeDrift"]))
    return classify_weeks(weeks, source, cutoff)


def complete_week_end(cutoff):
    return cutoff-timedelta(days=(cutoff.weekday()+1) % 7)


def deteriorating(week):
    return ((week["negativeBreadth"] or 0) >= .5 and (week["performanceResponse"] or 0) <= -.2
            or (week["rpeDrift"] or 0) > 1)


def usable(week):
    return (week["context"] == "NORMAL" and week["units"] > 0 and
            (week["negativeBreadth"] or 0) < .5 and (week["rpeDrift"] or 0) <= 1 and
            (week["performanceResponse"] is None or week["performanceResponse"] >= -.10))


def classify_weeks(weeks, source, cutoff):
    # Context never removes performance or RPE. Old global interruptionCause is not consulted.
    for i, week in enumerate(weeks):
        neighbors = weeks[max(0, i-3):i]+weeks[i+1:i+4]
        initial = med([w["units"] for w in neighbors if w["units"] > 0])
        typical = med([w["units"] for w in neighbors if initial is not None and w["units"] >= LOW_WEEK_RATIO*initial])
        low = typical is not None and week["units"] < LOW_WEEK_RATIO*typical
        annotation = source.get("weekAnnotations", {}).get(week["start"])
        if annotation and annotation.get("source") != "USER_CONFIRMED":
            annotation = None
        context, cause, provenance, reasons = "NORMAL", "UNKNOWN", "UNRESOLVED", []
        if low:
            returned = any(w["units"] >= .85*typical for w in weeks[i+1:i+3])
            preceding = weeks[max(0, i-2):i]
            no_prior_decline = bool(preceding) and not any(deteriorating(w) for w in preceding)
            stable_before = len(preceding) == 2 and no_prior_decline and all(w["units"] >= .85*typical for w in preceding)
            court_typical = med([w["courtLoad"] for w in neighbors if w["courtLoad"] > 0])
            event = court_typical is not None and week["courtLoad"] >= 1.5*court_typical
            if annotation:
                cause, provenance = annotation["cause"], "USER_CONFIRMED"
                context = {"EXTERNAL": "EXTERNAL_INTERRUPTION_LIKELY",
                           "EVENT_OR_TAPER": "EVENT_OR_TAPER_LIKELY",
                           "FATIGUE": "RECOVERY_REDUCTION_LIKELY"}.get(cause, "UNEXPLAINED_LOW_WEEK")
                reasons = ["EXACT_WEEK_USER_ANSWER", "CAUSE_"+cause]
            elif deteriorating(week):
                context, provenance, reasons = "RECOVERY_REDUCTION_LIKELY", "INFERRED", ["PERFORMANCE_OR_RPE_DETERIORATION"]
            elif returned and event and no_prior_decline and not deteriorating(week):
                context, provenance, reasons = "EVENT_OR_TAPER_LIKELY", "HIGH_CONFIDENCE_INFERRED", ["COURT_RISE_AND_SC_RETURN_NOT_USER_CONFIRMED"]
            elif returned and stable_before and not deteriorating(week):
                context, provenance, reasons = "EXTERNAL_INTERRUPTION_LIKELY", "HIGH_CONFIDENCE_INFERRED", ["ISOLATED_DROP_AND_RETURN_NOT_USER_CONFIRMED"]
            else:
                context, reasons = "UNEXPLAINED_LOW_WEEK", ["CAUSE_UNKNOWN"]
        week.update(context=context, low=low, localTypicalUnits=typical, reasonCodes=reasons,
                    cause=cause, source=provenance, bridgesStableRun=False,
                    excludedFromTolerance=low and provenance == "USER_CONFIRMED" and cause in {"EXTERNAL", "EVENT_OR_TAPER"})
    runs, current, bridged = [], [], set()
    for i, week in enumerate(weeks):
        following = weeks[i+1] if i+1 < len(weeks) else None
        before_typical = med([w["units"] for w in current[-2:]])
        bridge = (week["excludedFromTolerance"] and not deteriorating(week) and len(current) >= 2 and
                  current[-1]["end"] == str(date.fromisoformat(week["start"])-timedelta(days=1)) and
                  current[-2]["end"] == str(date.fromisoformat(week["start"])-timedelta(days=8)) and
                  following is not None and usable(following) and before_typical is not None and
                  .85*before_typical <= following["units"] <= 1.40*before_typical and
                  all(w["units"] >= .85*before_typical for w in current[-2:]))
        if bridge:
            bridged.add(week["start"])
            continue
        stable = not current or .70 <= week["units"]/max(1, current[-1]["units"]) <= 1.40
        if not usable(week) or not stable:
            if len(current) >= 2:
                runs.append(current)
            current = []
        if usable(week):
            current.append(week)
    if len(current) >= 2:
        runs.append(current)
    summaries = []
    for run in runs:
        n = len(run)
        performance = sum(w["performanceResponse"] is not None for w in run)
        coverage = performance/n
        confidence = ("HIGH" if n >= 4 and performance >= 2 and coverage >= .50 else
                      "MODERATE" if n >= 3 and (performance > 0 or any(w["rpeDrift"] is not None for w in run)) else "LOW")
        summaries.append(dict(start=run[0]["start"], end=run[-1]["end"], durationWeeks=n,
            units=med([w["units"] for w in run]), minutes=med([w["minutes"] for w in run]), days=med([w["days"] for w in run]),
            response=med([w["performanceResponse"] for w in run]), rpeDrift=med([w["rpeDrift"] for w in run]),
            weight=n**2*.92**((cutoff-date.fromisoformat(run[-1]["end"])).days/7),
            observedSuccessfulWeeks=n,
            calendarSpanWeeks=((date.fromisoformat(run[-1]["end"])-date.fromisoformat(run[0]["start"])).days+1)//7,
            performanceEvidenceWeeks=performance, performanceEvidenceCoverage=coverage,
            negativeBreadth=med([w["negativeBreadth"] for w in run]), confidence=confidence,
            qualifiedForCapacityRelease=confidence == "HIGH"))
    high = [r for r in summaries if r["qualifiedForCapacityRelease"]]
    confidence = "HIGH" if high else "MODERATE" if any(r["confidence"] == "MODERATE" for r in summaries) else "LOW"
    capacity_runs = high or summaries
    for week in weeks:
        week["bridgesStableRun"] = week["start"] in bridged and any(r["start"] <= week["start"] <= r["end"] for r in summaries)
    sustainable = dict(sustainableWeeklyControllableUnits=weighted_median([(r["units"], r["weight"]) for r in capacity_runs]),
        sustainableWeeklyMinutes=weighted_median([(r["minutes"], r["weight"]) for r in capacity_runs]),
        sustainableDaysPerWeek=weighted_median([(r["days"], r["weight"]) for r in capacity_runs]),
        longestStableRunWeeks=max((r["observedSuccessfulWeeks"] for r in summaries), default=0),
        successfulStableRunCount=len(runs), observationWeeks=len(weeks), confidence=confidence, runs=summaries,
        rawWeeklyMean=sum(w["units"] for w in weeks)/len(weeks) if weeks else None,
        normalWeekMedian=med([w["units"] for w in weeks if w["context"] == "NORMAL"]),
        questionRequired=any(w["low"] and w["source"] != "USER_CONFIRMED" for w in weeks),
        robustSchedule=source.get("interruptionFrequency") in {"MONTHLY", "FREQUENT", "VERY_FREQUENT"},
        reasonCodes=["DEMONSTRATED_NOT_MAXIMAL_OR_SAFE_CAPACITY", "RUN_LOCAL_PERFORMANCE_AUTHORITY", "COMPLETE_ISO_WEEKS"]+
            ([] if summaries else ["NO_MULTI_WEEK_STABLE_RUN"]))
    return weeks, sustainable


def auto_frequency(assessment, recent_days, item_count):
    sustainable = assessment["sustainable"]
    qualified_days = sustainable["sustainableDaysPerWeek"] if sustainable["confidence"] == "HIGH" else None
    normal = med([w["days"] for w in assessment["weeklyContext"] if w["context"] == "NORMAL"])
    reference = qualified_days if qualified_days is not None else normal if normal is not None else recent_days
    demonstrated = int(clip(math.floor(reference+.5), 2, 5))
    floor = int(clip((item_count+3)//4, 2, 5))
    ceiling = 2 if assessment["globalHardRestriction"] else {"MALADAPTATION_PATTERN": 3, "ACCUMULATING_STRAIN": 4}.get(assessment["state"], 5)
    return min(max(demonstrated, floor), ceiling)


def permits_release(assessment):
    return (not assessment["globalHardRestriction"] and assessment["state"] in
            {"PRODUCTIVE_HIGH_LOAD", "PRODUCTIVE_NORMAL", "TOLERATED_HIGH_LOAD"} and
            assessment["sustainable"]["confidence"] == "HIGH")


def assess(source):
    cutoff = date.fromisoformat(source["cutoff"])
    daily = [r for r in source["daily"] if date.fromisoformat(r["date"]) <= cutoff]
    recent = within(daily, cutoff-timedelta(days=6), cutoff)
    previous = within(daily, cutoff-timedelta(days=13), cutoff-timedelta(days=7))
    baseline = within(daily, cutoff-timedelta(days=55), cutoff-timedelta(days=7))
    ofi7 = med([r["overallFatigueIndex"] for r in recent])
    base_values = [r["overallFatigueIndex"] for r in baseline]
    center, mad, scale, z = relative(ofi7, base_values)
    absolute = clip((ofi7-55)/35) if ofi7 is not None else None
    rel = clip(z/2) if z is not None else None
    q80 = sorted(base_values)[round((len(base_values)-1)*.8)] if base_values else None
    persistence = sum(r["overallFatigueIndex"] > q80 for r in recent)/len(recent) if recent and q80 is not None else None
    previous_median = med([r["overallFatigueIndex"] for r in previous])
    trend = clip((ofi7-previous_median)/15, -1, 1) if ofi7 is not None and previous_median is not None else None
    axis_z = [relative(med([r[a] for r in recent]), [r[a] for r in baseline])[3] for a in AXES]
    axis = max((clip(v/2) for v in axis_z if v is not None), default=None)
    recovery = source["recovery"]
    readiness = {"READY": 0, "NORMAL": 0, "GOOD": 0, "CAUTION": .30, "FATIGUED": .60, "UNKNOWN": .15}.get(recovery["readinessStatus"])
    tissue = {"NORMAL": 0, "LOW": .1, "MODERATE": .3, "ELEVATED": .3, "HIGH": .6, "UNKNOWN": .1}.get(recovery["tissueStatus"])
    soft = max((v for v in [readiness, tissue] if v is not None), default=None)
    S = mean_available([(absolute, .30), (rel, .25), (persistence, .15),
                        (max(0, trend) if trend is not None else None, .10), (axis, .10), (soft, .10)])
    strain = dict(ofi7=ofi7, baselineMedian=center, baselineMad=mad, robustScale=scale,
                  personalZ=z, absoluteStrain=absolute, relativeStrain=rel, persistence=persistence,
                  ofiTrend=trend, maxAxisElevation=axis, elevatedAxisCount=sum(v is not None and v >= 1 for v in axis_z),
                  baselineDays=len(baseline), recentDays=len(recent), strainScore=S)
    current = within(source["records"], cutoff-timedelta(days=27), cutoff)
    prior = within(source["records"], cutoff-timedelta(days=55), cutoff-timedelta(days=28))
    adapt = adaptation(source, current, prior)
    weeks, sustainable = weekly_context(source, cutoff)
    def tolerance_window(rows, start, end):
        relevant = [w for w in weeks if start <= date.fromisoformat(w["end"]) <= end]
        excluded = {w["end"] for w in relevant if w["excludedFromTolerance"]}
        usable = [r for r in rows if source["domains"].get(r["stableKey"]) in CONTROL and
                  any(w["start"] <= r["date"] <= w["end"] for w in relevant) and
                  not any(w["end"] in excluded and w["start"] <= r["date"] <= w["end"] for w in relevant)]
        denomin = len(relevant)-len(excluded)
        days = {r["date"] for r in usable}
        density = med([sum(r["date"] == d for r in usable) for d in days])
        return dict(units=len(usable)/denomin if denomin else None,
                    days=len(days)/denomin if denomin else None, density=density)
    week_end = complete_week_end(cutoff)
    now = tolerance_window(within(source["records"], week_end-timedelta(days=27), week_end), week_end-timedelta(days=27), week_end)
    then = tolerance_window(within(source["records"], week_end-timedelta(days=55), week_end-timedelta(days=28)), week_end-timedelta(days=55), week_end-timedelta(days=28))
    def stability(key):
        return clip((now[key]/then[key]-.60)/.30) if now[key] is not None and then[key] is not None and then[key] > 0 else None
    work, days, density = [stability(k) for k in ("units", "days", "density")]
    drift = adapt["rpeDrift"]
    rpe = clip(1-max(0, drift)/1.5) if drift is not None else None
    T = mean_available([(work, .35), (days, .25), (density, .20), (rpe, .20)])
    tolerance = dict(current=now, prior=then, workloadStability=work, dayStability=days,
                     densityStability=density, rpeStability=rpe, score=T)
    A, positive, negative = adapt["globalAdaptationResponse"], adapt["positiveBreadth"], adapt["negativeBreadth"]
    P = mean_available([(clip((A+.20)/.80) if A is not None else None, .50), (T, .30), (positive, .20)])
    unresolved = any(w["low"] and w["source"] != "USER_CONFIRMED" and w["context"] != "RECOVERY_REDUCTION_LIKELY" or w["context"] == "UNEXPLAINED_LOW_WEEK" for w in weeks[-8:])
    M = mean_available([(clip((-A-.10)/.50) if A is not None else None, .40), (negative, .25),
                        (1-T if T is not None and not unresolved else None, .20),
                        (clip(max(0, drift)/1.5) if drift is not None else None, .15)])
    active = sum(bool(within([r for r in current if source["domains"].get(r["stableKey"]) in CONTROL],
                            cutoff-timedelta(days=6+i*7), cutoff-timedelta(days=i*7))) for i in range(4))
    families = len(adapt["movements"])
    confidence = ("HIGH" if len(baseline) >= 28 and families >= 3 and active >= 3 else
                  "MODERATE" if len(baseline) >= 14 and families >= 2 and active >= 2 else "LOW")
    hard = []
    local = recovery.get("tissueRestrictedStableKeys", [])
    if recovery["readinessStatus"] == "LIMITED":
        hard.append("READINESS_LIMITED")
    if recovery["tissueStatus"] in {"VERY_HIGH", "BLOCKED"} and not local:
        hard.append("TISSUE_"+recovery["tissueStatus"])
    global_codes = hard.copy()
    local_codes = ["LOCAL_TISSUE:"+key for key in sorted(local)]
    # Advisory readiness text generated from soft pressure is not an independent hard gate.
    local_codes += ["EXPLICIT_MODE:"+mode for mode in sorted(source.get("hardRestrictedModes", []))]
    hard = global_codes+local_codes
    global_hard = recovery["readinessStatus"] == "LIMITED" or recovery["tissueStatus"] in {"VERY_HIGH", "BLOCKED"} and not local
    state = ("HARD_RESTRICTION" if global_hard else "UNCERTAIN" if confidence == "LOW" else
             "MALADAPTATION_PATTERN" if M is not None and M >= .65 and negative is not None and negative >= .50 else
             "PRODUCTIVE_HIGH_LOAD" if S is not None and S >= .60 and P is not None and P >= .65 and M is not None and M < .30 else
             "TOLERATED_HIGH_LOAD" if S is not None and S >= .60 and P is not None and P >= .45 and M is not None and M < .45 else
             "ACCUMULATING_STRAIN" if S is not None and S >= .60 and M is not None and M >= .40 else
             "PRODUCTIVE_NORMAL" if S is not None and S < .60 and P is not None and P >= .65 else "STABLE")
    factor = clip(1-.15*(S or 0)*(1-(P or 0))-.10*(M or 0), .80, 1.0)
    if confidence == "LOW":
        factor = 1.0  # missing evidence is HOLD, not proof of failure
    if global_hard:
        factor = min(factor, .75 if recovery["readinessStatus"] == "LIMITED" or recovery["tissueStatus"] == "BLOCKED" else .80)
    return dict(strain=strain, adaptation=adapt, tolerance=tolerance, weeklyContext=weeks,
                sustainable=sustainable, strainScore=S, productiveEvidence=P, maladaptationEvidence=M,
                positiveBreadth=positive, negativeBreadth=negative, state=state, confidence=confidence,
                globalDoseFactor=factor, globalHardRestriction=global_hard, hardRestrictionCodes=hard,
                globalHardRestrictionCodes=global_codes, localRestrictionCodes=local_codes,
                localRestrictedStableKeys=sorted(local), restrictedModes=sorted(source.get("hardRestrictedModes", [])),
                reasonCodes=["SINGLE_GLOBAL_SOFT_DOSE", "CORRELATED_CONFIDENCE_CAPPED"]+
                            (["UNEXPLAINED_LOW_WEEK_NOT_ASSUMED_FAILURE"] if unresolved else []))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = assess(json.loads(args.input.read_text(encoding="utf-8")))
    rendered = json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)+"\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
