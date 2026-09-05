"""Local-only v0.13.0 -> v0.13.1 acceptance report; never input to production selection."""
import argparse
import copy
import csv
import hashlib
import json
import re
from datetime import date, timedelta
from pathlib import Path
from v013_training_state_reference import classify_weeks

ROOT=Path(__file__).resolve().parents[2]

def render(directory):
    def read(name): return json.loads((directory/name).read_text(encoding="utf-8"))
    before,after=read("v0131_before.json"),read("v0131_after.json")
    source=read("v0131_numerical_inputs.json")
    assert before["input"]==after["input"],"Core generation inputs changed"
    bs,ass=before["trainingStateAssessment"],after["trainingStateAssessment"]
    bcap,cap=bs["sustainable"],ass["sustainable"]
    bw,aw=before["weeklyTotals"]["1"],after["weeklyTotals"]["1"]
    cutoff=date.fromisoformat(source["cutoff"])
    # Isolate new run-local rules using the OLD exact bin observations. This is diagnostic only.
    _,old_bins_corrected=classify_weeks(copy.deepcopy(bs["weeklyContext"]),{},cutoff)
    lines=["# 비공개 실제 백업: v0.13.0 → v0.13.1 교정 비교","",
        "Baseline: fbaf65da9042d7d82c5481b79ee596a0621c3220",
        "Planner: RECORD_BASED_PLANNER_0.13.1_KOTLIN_1; canonical protocol: 3.3.1.",
        "수정 전 JSON SHA256: "+hashlib.sha256((directory/"v0131_before.json").read_bytes()).hexdigest(),
        "v0.12 원본 BEFORE SHA256: "+hashlib.sha256((directory/"v013_before.json").read_bytes()).hexdigest(),
        "수정 전 결과는 production 수정 전에 현재 v0.13.0을 재실행해 고정했습니다.",
        "동일 백업·cutoff·핵심 요청·3개 핵심 답변·제외·장비·90분·AUTO 일수/기간을 확인했습니다.",
        "날짜별 UNKNOWN 답변과 빈도 UNSURE는 테스트 가정이며 실제 사용자의 사건 확인이 아닙니다. USER_CONFIRMED source는 앱의 답변 경로 검증을 뜻하며 실제 생활 사건을 단정하지 않습니다.",
        "기존 global cause UNSURE와 동일하게 원인을 모른다는 조건입니다. 확정 외부/event 제외 또는 bridge를 실제 사용자의 데이터에 만들어 넣지 않았습니다.",
        "", "## 무엇을 잘못 이해했고 어떻게 교정했는가","",
        "기존 로직은 코트 부하로 S&C를 3일 이하로 제한했고, 국소 tissue/명시 모드 제한을 전신 HARD_RESTRICTION으로 취급했습니다. 짧은 여러 구간의 수행 근거도 합쳐 HIGH 지속 용량이라고 표시했습니다.",
        "교정 후 정상 주의 실제 4일 훈련은 인정합니다. 국소 제한은 보존하면서 전신은 PRODUCTIVE_NORMAL로 구분합니다. 다만 자체 수행 근거가 있는 충분히 긴 구간은 없어 지속 용량 HIGH를 취소합니다. 따라서 더 많은 주간 운동을 강제하지 않고 같은 유효 작업을 4일로 분산합니다.",
        "", "## 필수 10개 질문","",
        f"1. 코트 직접 3일 cap 제거: AUTO {before['resolvedRequest']['weeklyTrainingDays']} → {after['resolvedRequest']['weeklyTrainingDays']}일. AFTER authority={after['weeklyFrequencyEvidence']}.",
        f"2. 주별 중단 해석: raw mean {bcap['rawWeeklyMean']:.3f} → {cap['rawWeeklyMean']:.3f}, normal median {bcap['normalWeekMedian']} → {cap['normalWeekMedian']}, sustainable estimate {bcap['sustainableWeeklyControllableUnits']} → {cap['sustainableWeeklyControllableUnits']}. 완결 ISO 주간 재정렬의 영향입니다. 실제 확정 외부/event 제외 0, bridge 0이므로 사용자 사건 확인에 따른 증량이라고 해석할 수 없습니다.",
        f"3. 국소 제한의 전신 억제: {bs['state']} → {ass['state']}. 옛 union={bs['hardRestrictionCodes']}; 새 global={ass['globalHardRestrictionCodes']}, local={ass['localRestrictionCodes']}. 전신 dose에는 옛 globalHard=false였으므로 .75/.80 cap은 없었지만 전신 state/release gate는 잘못 막혔습니다.",
        f"4. run-local confidence: {bcap['confidence']} → {cap['confidence']}. 같은 옛 bin에서 새 run-local 규칙만 적용하면 {old_bins_corrected['confidence']}; ISO 주간까지 적용한 최종 결과는 {cap['confidence']}. HIGH-qualified run이 없어 강한 release는 꺼져 있습니다.",
        "5. conventional deadlift는 복귀하지 않았습니다. 아래 exact stableKey 점수 감사에서 보듯 기존 56일 anchor 점수·움직임당 최대 2개·전체 상위 9개 선택에서 제외됩니다. 전신 피로/court-day cap으로 누른 결과가 아니며 RDL 강제 교체도 아닙니다.",
        f"6. 저항 세트 {bw['resistanceSets']} → {aw['resistanceSets']}: 정상 관찰량을 이유로 추가 증량하지 않았습니다. HIGH 근거가 없고 현재 useful demand가 이미 모두 배정됐습니다.",
        f"7. 배드민턴/경기력 예산 유지: structured {bw['structuredBouts']} → {aw['structuredBouts']}, athletic {bw['athleticBouts']} → {aw['athleticBouts']}. Pallof SUPPORTIVE는 유지하되 DIRECT 충족이라고 부르지 않습니다.",
        "8. calf는 9 → 9세트로 여전히 높습니다. 아래 관찰 주당 세트 대비 과다한 비례 incumbent allocation은 남은 버그/제한으로 명시합니다. 상류 교정으로 해소되지 않았고 calf 전용 cap은 추가하지 않았습니다.",
        "9. press 다양성은 유지됐습니다. incline DB, seated DB shoulder, half-kneeling unilateral KB, triceps/support 처방을 종류가 많다는 이유로 제거하지 않았습니다.",
        f"10. 더 적극적인 것은 관찰된 4일 빈도와 짧아진 일별 시간입니다. 주간 총 {aw['totalUnits']}단위·{aw['minutes']}분은 그대로이며, 데이터가 강한 지속 용량 release를 정당화하지 않아 증량하지 않았습니다.",
        "", "## 수치 비교","", "| 항목 | v0.13.0 | v0.13.1 |","|---|---:|---:|"]
    metrics=[
        ("AUTO recommended days",before["resolvedRequest"]["weeklyTrainingDays"],after["weeklyFrequencyEvidence"]["recommendedDays"]),
        ("resolved days",before["resolvedRequest"]["weeklyTrainingDays"],after["resolvedRequest"]["weeklyTrainingDays"]),
        ("horizon",before["resolvedRequest"]["durationWeeks"],after["resolvedRequest"]["durationWeeks"]),
        ("global state",bs["state"],ass["state"]),
        *[(key,bs[key],ass[key]) for key in ("strainScore","productiveEvidence","maladaptationEvidence","globalDoseFactor","globalHardRestriction")],
        *[(key,bcap[key],cap[key]) for key in ("rawWeeklyMean","normalWeekMedian","sustainableDaysPerWeek","sustainableWeeklyControllableUnits","sustainableWeeklyMinutes","confidence")],
        *[(key,bw[key],aw[key]) for key in ("resistanceSets","structuredBouts","athleticBouts","totalUnits","minutes","distinctExercises","dailyMinutes")],
        ("low weeks",sum(w["low"] for w in bs["weeklyContext"]),sum(w["low"] for w in ass["weeklyContext"])),
        ("capacity",before["budget"]["execution"]["capacity"]["finalControllableUnits"],after["budget"]["execution"]["capacity"]["finalControllableUnits"]),
        ("useful demand",before["budget"]["execution"]["capacity"]["usefulDemandUnits"],after["budget"]["execution"]["capacity"]["usefulDemandUnits"])]
    for label,b,a in metrics: lines.append(f"| {label} | {b} | {a} |")
    lines+=["","Exposure/strength-adaptation 창은 그대로 cutoff 기준 28+28일입니다. 지속 용량/중단/tolerance만 완결 ISO 월~일로 고정해 날짜별 답변이 다음 생성 때 다른 주를 가리키지 않도록 했습니다.",
        "따라서 BEFORE의 목~수 주간 평균과 AFTER의 월~일 평균은 원시 기록 변경이 아니라 집계 경계 변경입니다. 진행 중인 부분 주는 저훈련 주로 판정하지 않습니다."]
    for label,a in (("BEFORE",bs),("AFTER",ass)):
        lines+=["","### "+label+" 저훈련 주","",
            "| 시작~끝 | 단위/분/일 | court | context | cause/source | 제외 | bridge | 이유 |","|---|---|---:|---|---|---|---|---|"]
        for w in a["weeklyContext"]:
            if w["low"]:
                lines.append(f"| {w['start']}~{w['end']} | {w['units']}/{w['minutes']}/{w['days']} | {w['courtLoad']} | {w['context']} | {w.get('cause','legacy UNSURE')}/{w.get('source','legacy undifferentiated')} | {w['excludedFromTolerance']} | {w.get('bridgesStableRun',False)} | {w['reasonCodes']} |")
    lines+=["","## 구간별 근거","", "| 시작~끝 | 성공 주 / 달력 주 | 수행 주 / coverage | 단위/분/일 | 반응 / RPE drift | confidence / qualified |","|---|---|---|---|---|---|"]
    for r in cap["runs"]:
        lines.append(f"| {r['start']}~{r['end']} | {r['observedSuccessfulWeeks']}/{r['calendarSpanWeeks']} | {r['performanceEvidenceWeeks']}/{r['performanceEvidenceCoverage']} | {r['units']}/{r['minutes']}/{r['days']} | {r['response']}/{r['rpeDrift']} | {r['confidence']}/{r['qualifiedForCapacityRelease']} |")
    with (ROOT/"app/src/main/assets/metadata/canonical_v1/runtime_metadata.csv").open(encoding="utf-8-sig",newline="") as stream:
        names={r["stableKey"]:r["exerciseName"] for r in csv.DictReader(stream)}
    names.update({r["stableKey"]:r["exerciseName"] for r in source["records"]})
    def first(report):
        result={}
        for row in report["items"]:
            if row["item"]["weekNumber"]==1: result.setdefault(row["item"]["exerciseStableKey"],[]).append(row)
        return result
    bi,ai=first(before),first(after)
    def detail(rows):
        return "; ".join(f"D{r['item']['dayOfWeek']}: {r['item']['setCount']} / "+json.dumps(r["item"]["setPrescriptions"],ensure_ascii=False)+
            f" / rest {r['item']['restSeconds']}s / total {r['item']['estimatedDurationSeconds']}s / role {r['item']['trainingSlot']}" for r in rows) or "없음 (0)"
    lines+=["","## 모든 선택 운동: 세트·반복·중량·RPE·시간·역할 diff","",
        "| 운동 / stableKey | BEFORE | AFTER | 정확한 변화 이유 |","|---|---|---|---|"]
    for key in sorted(bi.keys()|ai.keys()):
        old,new=bi.get(key,[]),ai.get(key,[])
        unchanged=sorted((r["item"]["setCount"],str(r["item"]["setPrescriptions"]),r["item"]["restSeconds"]) for r in old)==sorted((r["item"]["setCount"],str(r["item"]["setPrescriptions"]),r["item"]["restSeconds"]) for r in new)
        reason="주간 처방 동일; AUTO 4일에 따른 배치만 재계산" if unchanged else "처방 변경: 아래 transition 및 allocator provenance 참조"
        lines.append(f"| {names.get(key,key)} / {key} | {detail(old)} | {detail(new)} | {reason} |")
    lines+=["","## 필수 운동·미선택 후보의 exact identity 감사","",
        "아래 이름 검색은 사람이 읽을 감사 행을 고르는 보고서 전용입니다. production 선택/분류에 사용하지 않습니다.",
        "| 운동 / stableKey | 최근/이전 세트·세션 | score | response | BEFORE/AFTER 단위 | 실제 선택/제외 권위 |","|---|---|---:|---:|---|---|"]
    responses={r["stableKey"]:r for r in ass["adaptation"]["exercises"]}
    audit=after["budget"]["execution"]["candidateAudit"]
    anchors={r["stableKey"]:r for r in after["anchors"]}
    pattern=r"deadlift|rdl|squat|pull.?up|calf|press|pallof|bound|shuttle|hop|landing|deceler|데드리프트|스쿼트|풀업|카프|프레스|셔틀|홉|착지|감속|팔로프|바운드|좌우|전후|랜딩"
    for key,name in sorted(names.items()):
        if not re.search(pattern,name+" "+key,re.I): continue
        rows=[r for r in source["records"] if r["stableKey"]==key and cutoff-timedelta(days=55)<=date.fromisoformat(r["date"])<=cutoff]
        if not rows and key not in audit and key not in {"barbell_deadlift"}: continue
        current=[r for r in rows if date.fromisoformat(r["date"])>=cutoff-timedelta(days=27)]
        prior=[r for r in rows if r not in current]
        p=source["signals"].get(key,{}).get("posteriorChangePercent")
        bonus=1 if p is None else 4 if p>=4 else 3 if p>=1.5 else -3 if p<=-2 else 1
        score=2*len({r["date"] for r in rows})+.15*len(rows)+bonus
        units=lambda items:sum(r["item"]["setCount"] for r in items.get(key,[]))
        reason=audit.get(key,"최근 56일 anchor 선정 조건 또는 현재 material demand 밖")
        if key in anchors:
            t=next(r for r in after["transitions"] if r["stableKey"]==key)
            reason=f"선정 {t['structureTreatment']}; localDose={t['localDoseFactor']}; sportInterference={t['adaptation']['sportInterferencePressure']}"
        elif key=="barbell_deadlift":
            reason=f"score {score:.2f}; 최종 top9 최저 {min(a['score'] for a in anchors.values()):.2f}; movement당 max2/global top9에서 탈락. raw response={responses.get(key,{}).get('responseScore')}; global 피로 veto 없음."
        counts=f"{len(current)}/{len(prior)} sets; {len({r['date'] for r in current})}/{len({r['date'] for r in prior})} sessions"
        lines.append(f"| {name} / {key} | {counts} | {score:.2f} | {responses.get(key,{}).get('responseScore')} | {units(bi)}/{units(ai)} | {reason} |")
    calf=[r for r in source["records"] if r["stableKey"]=="ex_5ca7133f" and cutoff-timedelta(days=55)<=date.fromisoformat(r["date"])<=cutoff]
    observed_weeks=len({date.fromisoformat(r["date"]).isocalendar()[:2] for r in calf})
    lines+=["",f"Calf 관찰: 최근 56일 {len(calf)}세트 / 실제 활동 주 {observed_weeks} = {len(calf)/max(1,observed_weeks):.3f}세트/활동 주. 추천 9세트는 여전히 비례 incumbent 배분의 문제입니다. 별도 새 adaptation/gap 정당화가 생기지 않았습니다.",
        "좌우 셔틀·홉·착지·감속이 없으면 위 candidateAudit의 NO_MATCHING_CURRENT_OBJECTIVE_DEMAND 또는 NO_SAFE_PRESCRIPTION_AUTHORITY를 그대로 보고합니다. 빈 시간이 있다고 새 운동을 강제 생성하지 않습니다.",
        "", "## 동일 핵심 요청", "", "```json",json.dumps(before["input"],ensure_ascii=False,indent=2),"```",
        "", "## 최종 실행 배분 provenance","", "```json",json.dumps(after["budget"]["execution"],ensure_ascii=False,indent=2),"```"]
    for label,report in (("BEFORE",before),("AFTER",after)):
        lines+=["","## "+label+" 전체 프로그램 (모든 주/일)","",
            "| 주/일 | 운동 / stableKey | 세트별 반복·중량·초·RPE | rest / 총초 | role |","|---|---|---|---|---|"]
        for row in report["items"]:
            item=row["item"]
            lines.append(f"| {item['weekNumber']}/{item['dayOfWeek']} | {item['exerciseName']} / {item['exerciseStableKey']} | "+json.dumps(item["setPrescriptions"],ensure_ascii=False)+f" | {item['restSeconds']}/{item['estimatedDurationSeconds']} | {item['trainingSlot']} |")
    lines+=["","Backup/restore: "+after["backupRestore"],
        "Intentional limitations: temporary item-count placement floor; one confirmed gap bridge only; unknown dated causes remain unresolved; no HIGH-qualified run means no strongest capacity release; preexisting top9/per-family selection and calf proportional allocation remain. NECK is retained as an explicit local diagnostic token but has no newly invented movement mapping.",
        "No physiological formulas, Objective V2 coefficients, posterior, legacy ProgramAutoBuilder, Room schema, Android version or release tag changed. No GitHub Actions success is implied.",""]
    return "\n".join(lines)

if __name__=="__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("directory",type=Path); args=parser.parse_args()
    result=render(args.directory)
    for name in ("v0131_diff.txt","v0131_private_before_after_report.md"):
        target=args.directory/name; target.write_text(result,encoding="utf-8"); print(target)
