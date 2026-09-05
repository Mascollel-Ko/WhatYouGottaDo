"""Synthetic raw observations only; never reads the user's private backup."""
import argparse
import copy
import json
from datetime import date, timedelta
from pathlib import Path
from v013_training_state_reference import assess, AXES

GOLDEN = Path(__file__).with_name('fixtures') / 'v013_training_state_golden.json'


def source(units=None, responses=None, baseline=68, current=90, decline=False, missing_rpe=False):
    cutoff = date(2026, 8, 30)
    units = units or [42]*12
    keys = ['knee', 'hinge', 'push', 'pull']
    groups = ['LOWER_KNEE', 'POSTERIOR_CHAIN', 'HORIZONTAL_PUSH', 'VERTICAL_PULL']
    rows = []
    for week, count in enumerate(units):
        start = cutoff-timedelta(days=(len(units)-week)*7-1)
        for index in range(count):
            key = keys[index % 4]
            # Four distinct training dates, comparable prescriptions, chronological improvement.
            rows.append(dict(date=str(start+timedelta(days=(index//4) % 4)), stableKey=key,
                exerciseName=key, category='STRENGTH', setIndex=index+1, reps=32-week*2 if decline else 8,
                weightKg=100.0, seconds=0,
                rpe=None if missing_rpe else (7+week*.18 if decline else 8-week*.08)))
    return dict(cutoff=str(cutoff), records=rows,
        domains=dict.fromkeys(keys, 'RESISTANCE'), coverage=dict(zip(keys, groups)),
        metadata={k:dict(progressMetricType='LOAD_REPS') for k in keys},
        signals={k:dict(posteriorChangePercent=p,observationCount=6,source='SYNTHETIC_CANONICAL_POSTERIOR')
                 for k,p in zip(keys, responses or [8]*4)},
        daily=[dict(date=str(cutoff-timedelta(days=i)), overallFatigueIndex=current if i<7 else baseline,
                    confirmedTrainingLoad=1, recoveryPressureScore=current if i<7 else baseline,
                    **{a: current if i<7 else baseline for a in AXES}) for i in reversed(range(56))],
        recovery=dict(readinessStatus='FATIGUED', tissueStatus='HIGH', tissueRestrictedStableKeys=[]),
        weeklyCourtLoad=[dict(end=str(cutoff-timedelta(days=i*7)),load=100) for i in range(len(units))])


def annotate(value, indices, cause):
    count = len(value["weeklyCourtLoad"])
    cutoff = date.fromisoformat(value["cutoff"])
    for i in indices:
        start = cutoff-timedelta(days=(count-i)*7-1)
        value.setdefault("weekAnnotations", {})[str(start)] = dict(cause=cause, source="USER_CONFIRMED", answeredAtEpochMillis=1)
    return value


def cases():
    values = {}
    values['chronic_high_improving'] = source(current=72)
    values['same_72_declining'] = source([42]*8+[30,24,18,12],[-8]*4,40,72,True)
    # Sub-percent posterior drift is stable, below the positive-breadth threshold.
    values['high_stable'] = source(responses=[.5]*4)
    for r in values['high_stable']['records']: r['rpe']=8.0
    values['one_pr_broad_decline'] = source(responses=[15,-10,-10,-10],decline=True)
    values['blocked_overrides_performance'] = source()
    values['blocked_overrides_performance']['recovery']['tissueStatus']='BLOCKED'
    values['missing_rpe'] = source(missing_rpe=True)
    values['sparse'] = source([4])
    values['sparse']['daily']=values['sparse']['daily'][-7:]
    values['sparse']['signals']={}
    values['volume_increase_stable_performance'] = source([30]*8+[60]*4,responses=[0]*4)
    for r in values['volume_increase_stable_performance']['records']: r['rpe']=8.0
    values['isolated_interruption'] = source([42]*5+[18]+[42]*6)
    values['low_weeks_deterioration'] = source([42,42,42,18,42,18,42,18,42,18,42,18],[-8]*4,40,72,True)
    values['confirmed_external'] = source([40,42,18,41,43,20,42])
    annotate(values['confirmed_external'], [2,5], 'EXTERNAL')
    values['confirmed_event'] = copy.deepcopy(values['confirmed_external'])
    annotate(values['confirmed_event'], [2,5], 'EVENT_OR_TAPER')
    values['confirmed_event']['weeklyCourtLoad'][1]['load']=600
    values['long_successful_run'] = source([54]*12)
    values['one_extreme_week'] = source([30]*6+[200]+[30]*5)
    values['frequent_interruption'] = copy.deepcopy(values['confirmed_external'])
    values['frequent_interruption']['interruptionFrequency']='FREQUENT'
    values['older_successful_run'] = source([54]*8+[12,14,15,18])
    annotate(values['older_successful_run'], [8,9,10,11], 'EXTERNAL')
    values['local_press_only'] = source()
    values['local_press_only']['recovery']['tissueRestrictedStableKeys']=['push']
    values['local_press_only']['recovery']['tissueStatus']='VERY_HIGH'
    values['overhead_mode_only'] = source()
    values['overhead_mode_only']['hardRestrictedModes']=['OVERHEAD_PRESS']
    values['limited_global'] = source()
    values['limited_global']['recovery']['readinessStatus']='LIMITED'
    values['legacy_global_external_ignored'] = source([40,42,18,41,43,20,42])
    values['legacy_global_external_ignored']['interruptionCause']='EXTERNAL'
    values['different_causes'] = copy.deepcopy(values['confirmed_external'])
    annotate(values['different_causes'],[5],'FATIGUE')
    values['external_with_rpe_decline'] = source([42]*8+[18,42,18,42], [-8]*4,40,72,True)
    for row in values['external_with_rpe_decline']['records']: row['reps']=8
    annotate(values['external_with_rpe_decline'],[8,10],'EXTERNAL')
    values['confirmed_bridge'] = source([42,41,18,43,40])
    annotate(values['confirmed_bridge'],[2],'EXTERNAL')
    values['unknown_answer'] = copy.deepcopy(values['confirmed_bridge'])
    annotate(values['unknown_answer'],[2],'UNKNOWN')
    return values


def verify(results):
    assert results['chronic_high_improving']['state'] in ('PRODUCTIVE_HIGH_LOAD','PRODUCTIVE_NORMAL')
    assert results['chronic_high_improving']['globalDoseFactor']>.95
    assert results['same_72_declining']['state'] in ('ACCUMULATING_STRAIN','MALADAPTATION_PATTERN')
    assert results['same_72_declining']['globalDoseFactor']<.9
    assert results['high_stable']['state']=='TOLERATED_HIGH_LOAD'
    assert not results['one_pr_broad_decline']['state'].startswith('PRODUCTIVE')
    assert results['blocked_overrides_performance']['state']=='HARD_RESTRICTION'
    assert results['blocked_overrides_performance']['globalDoseFactor']<=.75
    assert results['missing_rpe']['maladaptationEvidence']==0
    assert results['sparse']['state']=='UNCERTAIN'
    assert not results['volume_increase_stable_performance']['state'].startswith('PRODUCTIVE')
    assert results['isolated_interruption']['sustainable']['sustainableWeeklyControllableUnits']==42
    assert any(w['context']=='RECOVERY_REDUCTION_LIKELY' for w in results['low_weeks_deterioration']['weeklyContext'])
    assert all(w['excludedFromTolerance'] for w in results['confirmed_external']['weeklyContext'] if w['low'])
    assert any(w['courtLoad']==600 for w in results['confirmed_event']['weeklyContext'])
    assert results['long_successful_run']['sustainable']['confidence']=='HIGH'
    assert results['one_extreme_week']['sustainable']['sustainableWeeklyControllableUnits']==30
    assert results['frequent_interruption']['sustainable']['robustSchedule']
    assert results['frequent_interruption']['globalDoseFactor']==results['confirmed_external']['globalDoseFactor']
    assert results['older_successful_run']['sustainable']['sustainableWeeklyControllableUnits']==54
    assert results['older_successful_run']['sustainable']['confidence']=='HIGH'
    assert results['local_press_only']['state']!='HARD_RESTRICTION'
    assert not results['overhead_mode_only']['globalHardRestriction']
    assert results['limited_global']['globalHardRestriction']
    assert not any(w['excludedFromTolerance'] for w in results['legacy_global_external_ignored']['weeklyContext'])
    low=[w for w in results['different_causes']['weeklyContext'] if w['low']]
    assert low[0]['excludedFromTolerance'] and not low[1]['excludedFromTolerance']
    assert results['external_with_rpe_decline']['adaptation']['rpeDrift']>0
    assert any(w['bridgesStableRun'] for w in results['confirmed_bridge']['weeklyContext'])
    assert results['confirmed_bridge']['sustainable']['runs'][0]['observedSuccessfulWeeks']==4
    assert results['confirmed_bridge']['sustainable']['runs'][0]['calendarSpanWeeks']==5
    assert not any(w['bridgesStableRun'] for w in results['unknown_answer']['weeklyContext'])


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--write',action='store_true')
    args=parser.parse_args()
    inputs=cases()
    results={key:assess(value) for key,value in inputs.items()}
    verify(results)
    # Determinism plus golden check; generated by this independent Python reference.
    rendered=json.dumps([dict(name=k,input=inputs[k],expected=results[k]) for k in sorted(inputs)],
                        ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False)+'\n'
    if args.write: GOLDEN.write_text(rendered,encoding='utf-8')
    else: assert GOLDEN.read_text(encoding='utf-8')==rendered
    for k,v in results.items(): print(k,v['state'],round(v['globalDoseFactor'],6),v['sustainable']['confidence'])
    print(f'PASS: {len(inputs)} independent v0.13.1 raw-input cases')
