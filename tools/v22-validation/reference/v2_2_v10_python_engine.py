#!/usr/bin/env python3
"""Executable deterministic multi-block planning prototype.
Not a Kotlin canonical stimulus oracle; never invents new load prescriptions.
"""
import argparse, collections, copy, datetime, json, math, pathlib
from v2_2_generation_v05 import generate, build_profiles, load, numeric

SCHEMA='V2_2_PYTHON_V10_BLOCK_PLANNING'
CORE={'CORE_ANTI_EXTENSION','CORE_ANTI_ROTATION'}
ARMS={'ARMS_BICEPS_OR_FOREARM','ARMS_TRICEPS'}

def split_blocks(n):
    if not 1<=n<=52:raise ValueError('weeks must be 1..52')
    if n<=4:return [n]
    k=max(2,round(n/3));base=n//k;extra=n%k
    return [base+(1 if i<extra else 0) for i in range(k)]

def role_targets(base,profiles):
    # Personal historical exposure *proxy*; no physiological deficiency conclusion.
    observed=collections.defaultdict(float)
    for p in profiles:
        observed[p['role']]+=0.75*p['weekly28']+0.25*p['weekly56']
    allocated=collections.Counter(x['role'] for s in base for x in s['items'] for _ in range(x['setCount']))
    return {r:{'historyWeeklyNominal':round(v,3),'baselinePlanned':allocated[r],
               'gap':round(max(0,v-allocated[r]),3)} for r,v in sorted(observed.items())}

def session_guard(sessions):
    # Only protects same-day conservative high-load squat/hinge conflicts.
    for s in sessions:
        heavy=[]
        for x in s['items']:
            if x['role'] not in ('SQUAT_PATTERN','HIP_HINGE'):continue
            if any((v.get('reps') or 100)<=5 and (v.get('observedRpe') or 0)>=7 and (v.get('weightKg') or 0)>0 for v in x['sets']):
                heavy.append(x['role'])
        if 'SQUAT_PATTERN' in heavy and 'HIP_HINGE' in heavy:return False
    return True

def plan_adjust(sessions,role_from,role_to,receiver_key=None):
    # Adjust only existing exercises; keep >=1 set and never manufacture kg.
    donors=[(s,x) for s in sessions for x in s['items'] if x['role']==role_from and x['setCount']>=2]
    receivers=[(s,x) for s in sessions for x in s['items'] if x['role']==role_to and (receiver_key is None or x['stableKey']==receiver_key)]
    if not donors or not receivers:return None
    sd,xd=sorted(donors,key=lambda z:(-z[1]['setCount'],z[0]['session'],z[1]['stableKey']))[0]
    sr,xr=sorted(receivers,key=lambda z:(z[1]['setCount'],z[0]['session'],z[1]['stableKey']))[0]
    xd['setCount']-=1;xd['sets'].pop();sd['nominalSets']-=1
    xr['setCount']+=1;xr['sets'].append(copy.deepcopy(xr['sets'][-1]));sr['nominalSets']+=1
    return {'from':xd['stableKey'],'to':xr['stableKey'],'donorRole':role_from,'receiverRole':role_to,
            'amountNominalSets':1,'reason':'PLANNED_PERSONAL_HISTORY_GAP_PROXY_NOT_CANONICAL_STIMULUS'}

def build(path,weeks=8,days=4,mode='A',asof=datetime.date(2026,10,11),focus=None,minutes=None):
    if mode not in 'ABC':raise ValueError('mode')
    if not 1<=days<=7:raise ValueError('days')
    if minutes is not None and minutes<=0:raise ValueError('minutes')
    g=generate(path,asof,training_days=days)
    if g['programs']['A']['status']!='EXPLORATORY':raise ValueError('baseline placement failed')
    profiles=build_profiles(load(path,asof)[0],asof)
    base=copy.deepcopy(g['programs']['A']['sessions'])
    evidence=role_targets(base,profiles)
    baseline_sets=sum(s['nominalSets'] for s in base)
    lengths=split_blocks(weeks)
    blocks=[];all_weeks=[];cursor=1
    # B uses only positive historical-versus-plan proxy gaps, not guessed canonical deficits.
    gaps={r:v['gap'] for r,v in evidence.items()}
    roles=sorted({x['role'] for s in base for x in s['items']})
    focus_roles=[x['role'] for s in base for x in s['items'] if x['stableKey']==focus] if focus else []
    if mode=='C' and not focus_roles:raise ValueError('C requires an existing explicit focus stableKey')
    for bi,length in enumerate(lengths):
        block_sessions=copy.deepcopy(base)
        moves=[];status='BASELINE' if bi==0 else 'PLANNED_DEVELOPMENT'
        if bi>0:
            # Donors: lower priority roles with >=2 nominal sets; no protected core/arms/primary patterns.
            current=collections.Counter({r:sum(x['setCount'] for s in block_sessions for x in s['items'] if x['role']==r) for r in roles})
            protected=CORE|ARMS|{'SQUAT_PATTERN','HIP_HINGE','VERTICAL_PULL'}
            donors=[r for r in roles if r not in protected and current[r]>=3]
            donors.sort(key=lambda r:(-current[r],r))
            if mode=='B':
                receivers=sorted((r for r in roles if gaps.get(r,0)>0),key=lambda r:(-gaps[r],r))
            elif mode=='C':receivers=focus_roles
            else:
                # A preserves baseline allocation unless history provides a material role gap.
                receivers=sorted((r for r in roles if gaps.get(r,0)>=1),key=lambda r:(-gaps[r],r))
            # One reallocation max per block per week; no compensation for missed sessions.
            for receiver in receivers:
                donor=next((d for d in donors if d!=receiver),None)
                if donor:
                    m=plan_adjust(block_sessions,donor,receiver,focus if mode=='C' else None)
                    if m:moves.append(m)
                    break
        assert session_guard(block_sessions)
        assert sum(s['nominalSets'] for s in block_sessions)==baseline_sets
        b={'block':bi+1,'startWeek':cursor,'endWeek':cursor+length-1,'phase':status,
           'plannedReallocations':moves,'strengthPolicy':'KEEP_APPROVED_EXACT_HISTORY_REFERENCE_NO_AUTO_KG_INCREASE',
           'hypertrophyPolicy':'REPETITION_RANGE_PROGRESS_REVIEW_ONLY_NO_FABRICATED_LOAD',
           'recoveryPolicy':'NO_FIXED_DELOAD; NEEDS_REAL_RECOVERY_AUTHORITY',
           'plannedRoleSets':dict(sorted(collections.Counter({r:sum(x['setCount'] for s in block_sessions for x in s['items'] if x['role']==r) for r in roles}).items()))}
        blocks.append(b)
        for w in range(cursor,cursor+length):
            sessions=copy.deepcopy(block_sessions)
            for s in sessions:
                for x in s['items']:
                    x['prescriptionAuthority']='HISTORICAL_REFERENCE_ONLY'
                    x['plannedProgression']='REVIEW_REQUIRED_NOT_AUTO_PRESCRIBED'
                    x['block']=bi+1
            all_weeks.append({'week':w,'block':bi+1,'sessions':sessions,'weeklyNominalSets':sum(s['nominalSets'] for s in sessions)})
        cursor+=length
    return {'schema':SCHEMA,'input':{'backup':pathlib.Path(path).name,'asOf':str(asof),'weeks':weeks,'days':days,'mode':mode,'focusStableKey':focus,'sessionMinutes':minutes},
            'historyEvidence':evidence,'blocks':blocks,'weeks':all_weeks,'baselineWeeklyNominalSets':baseline_sets,
            'authority':{'stimulus':'NAME_BASED_ROLE_PROXY_NOT_CANONICAL','prescription':'HISTORICAL_OBSERVATION_ONLY',
              'executionMissCarryover':False,'structuralPlanningOnly':True},
            'limitations':['Not a validated Kotlin V1/V2-1 stimulus ledger','No fabricated kilogram progression',
              'No actual session time estimates; duration cap unverified','No weekday schedule or tissue-level recovery verification',
              'No validated canonical deficit ranking; B is a proxy experiment','C requires explicit focus stableKey',
              'Program intensity progression cannot be finalized without exercise-local load authority']}

def report(p):
    inp=p['input'];lines=[f'# V2-2 Python v1.0 — {inp["mode"]} / {inp["weeks"]}주 / 주 {inp["days"]}일','',
    '**상태: 실행 가능한 블록 계획 시제품. Kotlin 정식 자극 계산·중량 처방과 동일하다고 주장하지 않음.**','',
    '| 블록 | 주차 | 주당 세트 | 구조적 재배분 |','|---|---|---:|---|']
    for b in p['blocks']:
        moves=', '.join(f'{m["donorRole"]} → {m["receiverRole"]} 1세트' for m in b['plannedReallocations']) or '없음'
        lines.append(f'| {b["block"]} | {b["startWeek"]}–{b["endWeek"]} | {p["baselineWeeklyNominalSets"]} | {moves} |')
    lines+=['','## 운동 구성 (블록별 대표 주)']
    for b in p['blocks']:
        w=p['weeks'][b['startWeek']-1];lines+=['',f'### 블록 {b["block"]} ({b["startWeek"]}–{b["endWeek"]}주)']
        for s in w['sessions']:
            lines.append(f'**{s["session"]}일차 — {s["nominalSets"]}세트**')
            for x in s['items']:
                sets=' / '.join(f'{v["weightKg"]:g}kg×{v["reps"]}' if v.get('weightKg') is not None and v.get('reps') is not None else f'{v.get("reps") or "?"}회(중량 미확인)' for v in x['sets'])
                lines.append(f'- {x["exercise"]}: {x["setCount"]}세트 ({sets})')
    lines+=['','## 제한','']+['- '+z for z in p['limitations']]
    return '\n'.join(lines)+'\n'

def validate(p):
    assert len(p['weeks'])==p['input']['weeks']
    assert [w['week'] for w in p['weeks']]==list(range(1,p['input']['weeks']+1))
    assert sum(b['endWeek']-b['startWeek']+1 for b in p['blocks'])==p['input']['weeks']
    for w in p['weeks']:
        assert len(w['sessions'])==p['input']['days']
        assert w['weeklyNominalSets']==sum(s['nominalSets'] for s in w['sessions'])
        assert all(s['nominalSets']==sum(x['setCount'] for x in s['items']) for s in w['sessions'])
        assert all(x['setCount']==len(x['sets']) and x['setCount']>0 for s in w['sessions'] for x in s['items'])
        assert session_guard(w['sessions'])
    return True

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--backup',default='/mnt/data/whatyougottado_backup_2026-10-11.csv');ap.add_argument('--weeks',type=int,default=8);ap.add_argument('--days',type=int,default=4);ap.add_argument('--mode',choices=list('ABC'),default='A');ap.add_argument('--focus');ap.add_argument('--minutes',type=int);ap.add_argument('--out',default='/mnt/data/v2_2_v10');a=ap.parse_args()
    p=build(a.backup,a.weeks,a.days,a.mode,focus=a.focus,minutes=a.minutes);validate(p)
    pathlib.Path(a.out+'.json').write_text(json.dumps(p,ensure_ascii=False,indent=2),encoding='utf8')
    pathlib.Path(a.out+'.md').write_text(report(p),encoding='utf8')
    print('VALIDATED',a.mode,[(b['startWeek'],b['endWeek'],b['plannedReallocations']) for b in p['blocks']])
