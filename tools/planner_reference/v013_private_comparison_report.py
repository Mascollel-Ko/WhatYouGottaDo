"""Render local-only BEFORE/AFTER evidence. Never commit the generated private report.

Name matching below selects report rows for human inspection ONLY, never planner semantics.
"""
import argparse
import json
import re
from datetime import date,timedelta
from pathlib import Path
from statistics import median


def render(directory):
    def read(name): return json.loads((directory/name).read_text(encoding='utf-8'))
    before,after,source=read('v013_before.json'),read('v013_after.json'),read('v013_numerical_inputs.json')
    assert before['input']==after['input'], 'Original request/core answers/cutoff changed'
    assessment=after['trainingStateAssessment']
    capacity=assessment['sustainable']
    lines=['# Private real-backup v0.13 BEFORE / AFTER audit','',
        'Baseline: d299800438410f9022add04f3601d20a3ffd722e. Original request/core answers/cutoff are equal.',
        'Additional interruption context: '+json.dumps(after['additionalContextAnswers'],ensure_ascii=False),
        'Context provenance: '+after['additionalContextSource'],
        'No work trip, travel, competition or fatigue failure is asserted from missing records.',
        '', '## Same-input comparison','', '| Metric | BEFORE | AFTER |','|---|---:|---:|']
    bw=before['weeklyTotals']['1']; aw=after['weeklyTotals']['1']
    for label,old,new in [
        ('Horizon weeks',before['resolvedRequest']['durationWeeks'],after['resolvedRequest']['durationWeeks']),
        ('Training days',before['resolvedRequest']['weeklyTrainingDays'],after['resolvedRequest']['weeklyTrainingDays']),
        ('Resistance sets',bw['resistanceSets'],aw['resistanceSets']),('Structured bouts',bw['structuredBouts'],aw['structuredBouts']),
        ('Athletic bouts',bw['athleticBouts'],aw['athleticBouts']),('All controllable units',bw['totalUnits'],aw['totalUnits']),
        ('Weekly minutes',bw['minutes'],aw['minutes']),('Distinct exercises',bw['distinctExercises'],aw['distinctExercises']),
        ('High-force item exposures',bw['highForceItems'],aw['highForceItems']),
        ('Global factor',before['budget']['systemicDoseFactor'],after['budget']['systemicDoseFactor']),
        ('Cutoff OFI',before['recovery']['overallFatigueIndex'],after['recovery']['overallFatigueIndex']),
        ('Legacy isConstrained diagnostic',before['isConstrained'],after['isConstrained']),
        ('Readiness',before['recovery']['readinessStatus'],after['recovery']['readinessStatus']),
        ('Tissue',before['recovery']['tissueStatus'],after['recovery']['tissueStatus']),
        ('Raw 12-week mean (retrospective audit)',capacity['rawWeeklyMean'],capacity['rawWeeklyMean']),
        ('Interruption-adjusted normal median','n/a',capacity['normalWeekMedian']),
        ('Longest successful run','n/a',capacity['longestStableRunWeeks']),
        ('Sustainable units','n/a',capacity['sustainableWeeklyControllableUnits']),
        ('Sustainable minutes','n/a',capacity['sustainableWeeklyMinutes']),
        ('Sustainable days','n/a',capacity['sustainableDaysPerWeek']),
        ('Successful run count','n/a',capacity['successfulStableRunCount']),
        ('Sustainable confidence','n/a',capacity['confidence']),
        ('Low weeks assumed failure','No cause distinction in recent baseline',
         sum(w['low'] and w['context']=='RECOVERY_REDUCTION_LIKELY' for w in assessment['weeklyContext'])),
        ('Low weeks excluded external/event','0',sum(w['excludedFromTolerance'] for w in assessment['weeklyContext'])),
        ('Final capacity envelope',before['budget']['execution']['capacity']['finalControllableUnits'],after['budget']['execution']['capacity']['finalControllableUnits'])]:
        lines.append(f'| {label} | {old} | {new} |')
    lines += ['', '## Exact original input','', '```json',json.dumps(before['input'],ensure_ascii=False,indent=2),'```',
        '', '## Side-by-side weekly prescriptions','',
        'BEFORE repeats this microcycle for 3 weeks; AFTER repeats it for 4 weeks. All individual week/day rows follow below.',
        '']
    def first_week(report): return {r['item']['exerciseStableKey']:r for r in report['items'] if r['item']['weekNumber']==1}
    old_items,new_items=first_week(before),first_week(after)
    lines += ['Added keys: '+str(sorted(new_items.keys()-old_items.keys())),
              'Removed keys: '+str(sorted(old_items.keys()-new_items.keys())),
              '', '| Key / exercise | BEFORE day / sets / prescription | AFTER day / sets / prescription | Material change reason |','|---|---|---|---|']
    for key in sorted(old_items.keys()|new_items.keys()):
        def detail(row):
            if not row: return 'Absent'
            item=row['item']
            return f"D{item['dayOfWeek']} / {item['setCount']} / {item['prescription']}"
        old,new=old_items.get(key),new_items.get(key)
        change='Same weekly units' if old and new and old['item']['setCount']==new['item']['setCount'] else (
            'Single global dose replaces repeated soft penalties; existing finite proportional allocation and '+
            ('local adaptation/sport interference' if new and new['transition'] else 'existing performance continuity/material prescription')+' determine these units')
        lines.append(f"| {key} / {(new or old)['item']['exerciseName']} | {detail(old)} | {detail(new)} | {change} |")
    lines += ['', '## Full training-state evidence','', '```json',json.dumps(assessment,ensure_ascii=False,indent=2),'```',
        '', '## Capacity weeks','', '| Start | End | Units | Minutes | Days | Court | RPE | Context | Low | Excluded | Reasons |',
        '|---|---|---:|---:|---:|---:|---:|---|---|---|---|']
    for w in assessment['weeklyContext']:
        lines.append('| '+' | '.join(str(w[k]) for k in ('start','end','units','minutes','days','courtLoad','medianRpe','context','low','excludedFromTolerance','reasonCodes'))+' |')
    lines += ['', 'Question triggered: '+str(capacity['questionRequired']),
        'Sustainable release gate: '+str(not assessment['hardRestrictionCodes'] and assessment['state'] in
             ('PRODUCTIVE_HIGH_LOAD','TOLERATED_HIGH_LOAD','PRODUCTIVE_NORMAL') and capacity['confidence']=='HIGH'),
        'The actual hard/local tissue restriction is retained. Sustainable HIGH does not override that gate.',
        'The generated change therefore must not be described as unconditional release to sustainable volume.',
        '', '## Required exact-identity audit','',
        '| Key | Recorded name | Current/prior sessions | Current/prior sets | Matched load/reps/RPE | Posterior % | Response | Anchor score | BEFORE/AFTER units | Explanation |',
        '|---|---|---|---|---|---|---|---|---|---|']
    cutoff=date.fromisoformat(source['cutoff'])
    response={r['stableKey']:r for r in assessment['adaptation']['exercises']}
    names={r['stableKey']:r['exerciseName'] for r in source['records']}
    for key,name in sorted(names.items()):
        if not re.search(r'deadlift|rdl|squat|pull.?up|calf|press|데드리프트|스쿼트|풀업|카프|프레스',name,re.I): continue
        rows=[r for r in source['records'] if r['stableKey']==key and cutoff-timedelta(days=55)<=date.fromisoformat(r['date'])<=cutoff]
        if not rows: continue
        current=[r for r in rows if date.fromisoformat(r['date'])>=cutoff-timedelta(days=27)]
        prior=[r for r in rows if r not in current]
        r=response.get(key,{})
        change=source['signals'].get(key,{}).get('posteriorChangePercent')
        bonus=1 if change is None else 4 if change>=4 else 3 if change>=1.5 else -3 if change<=-2 else 1
        score=2*len({r['date'] for r in rows})+.15*len(rows)+bonus
        def units(report): return sum(x['item']['setCount'] for x in report['items'] if x['item']['weekNumber']==1 and x['item']['exerciseStableKey']==key)
        anchors={a['stableKey'] for a in after['anchors']}
        if key in anchors:
            transition=next(t for t in after['transitions'] if t['stableKey']==key)
            reason=f"Retained; {transition['structureTreatment']}; localDose={transition['localDoseFactor']}; sportInterference={transition['adaptation']['sportInterferencePressure']}"
        elif len({r['date'] for r in rows})<2: reason='Fewer than 2 completed sessions in 56 days'
        elif source['metadata'].get(key,{}).get('planningEligibility') not in ('PROGRAM_SELECTABLE','SELECTABLE'): reason='Not selectable authority'
        else: reason='Not selected by existing score -> max 2 per movement -> global top 9; no forced reinsertion'
        lines.append(f"| {key} | {name} | {len({r['date'] for r in current})}/{len({r['date'] for r in prior})} | {len(current)}/{len(prior)} | "+
            '/'.join(str(r.get(k)) for k in ('matchedLoadChange','matchedRepChange','matchedRpeEfficiency'))+
            f" | {change} | {r.get('responseScore')} | {score} | {units(before)}/{units(after)} | {reason} |")
    lines += ['', '## Accessory inflation / unchanged selection limitation','',
        'Calf/accessory volume follows the existing score and proportional-allocation machinery. Any unusually large calf allocation is flagged here, not silently capped or used to redefine global adaptation.',
        'Conventional deadlift omission does not prove a recovery veto: the exact anchor score and top-nine/per-movement boundary above must be checked separately.',
        '', '## Full programs (all weeks, no truncation)','',
        'Priority numbers below are reconstructed from unchanged planner priority policy (structure 100/95/75/60; performance continuity 85; gap HIGH100/MODERATE90/LOW70), not physiological scores.']
    for label,report in [('BEFORE',before),('AFTER',after)]:
        lines += ['', '### '+label,'', '| Week/day | Key | Name | Domain / coverage | Sets / prescriptions | Rest | Seconds | Numeric priority | Transition / gap reason |',
            '|---|---|---|---|---|---:|---:|---:|---|']
        for row in report['items']:
            item=row['item']; transition=row['transition']
            priority=({'PRESERVE':100,'PRESERVE_CORE_REBALANCE':95,'PARTIAL_CONTINUITY':75,'ROTATE_EMPHASIS':60}[transition['structureTreatment']]
                      if transition else 85 if item['trainingSlot']=='PERFORMANCE_CONTINUITY' else
                      max([{'HIGH':100,'MEDIUM':90,'MODERATE':90,'LOW':70}.get(g['priority'],70) for g in report['gaps'] if g['code'] in row['directGaps']+row['supportiveGaps']],default=90))
            lines.append(f"| {item['weekNumber']}/{item['dayOfWeek']} | {item['exerciseStableKey']} | {item['exerciseName']} | {row['domain']} / {row['coverage']} | "+
                json.dumps(item['setPrescriptions'],ensure_ascii=False)+' / '+item['prescription']+f" | {item['restSeconds']} | {item['estimatedDurationSeconds']} | {priority} | "+
                str(transition['reasons'] if transition else row['directGaps']+row['supportiveGaps'])+' |')
        lines+=['','Daily minutes: '+json.dumps(report['weeklyTotals'],ensure_ascii=False)]
        lower={'LOWER_KNEE','POSTERIOR_CHAIN','CALVES'}
        upper={'HORIZONTAL_PUSH','VERTICAL_PUSH','HORIZONTAL_PULL','VERTICAL_PULL','ARMS_BICEPS','ARMS_TRICEPS'}
        lines += ['First-week lower sets='+str(sum(x['item']['setCount'] for x in report['items'] if x['item']['weekNumber']==1 and x['coverage'] in lower)),
                  'First-week upper sets='+str(sum(x['item']['setCount'] for x in report['items'] if x['item']['weekNumber']==1 and x['coverage'] in upper))]
    lines += ['','Backup / restore: '+after['backupRestore'],'']
    return '\n'.join(lines)


if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('directory',type=Path); args=parser.parse_args()
    path=args.directory/'v013_private_before_after_report.md'
    path.write_text(render(args.directory),encoding='utf-8')
    print(path)
