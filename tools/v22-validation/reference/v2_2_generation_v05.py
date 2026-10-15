#!/usr/bin/env python3
"""V2-2 v0.4 history-driven exploratory planner; NOT Kotlin/canonical-equivalent.
No hard-coded exercise roster, fixed exercise slots, or fixed weekly set total.
"""
import csv, collections, datetime, json, pathlib, sys, math
csv.field_size_limit(100_000_000)
ASOF=datetime.date(2026,10,11)
CATEGORIES={'근력운동','기능성운동'}

def numeric(v):
    try:
        n=float(v)
        return n if math.isfinite(n) else None
    except (TypeError,ValueError):return None

def classify(r):
    name=r['exercise_name'].lower();key=r['stable_key'].lower()
    if '데드버그' in name or 'dead_bug' in key:return 'CORE_ANTI_EXTENSION'
    if '팔로프' in name or 'pallof' in key:return 'CORE_ANTI_ROTATION'
    if '컬' in name and '레그' not in name:return 'ARMS_BICEPS_OR_FOREARM'
    if '트라이셉' in name or '삼두' in name:return 'ARMS_TRICEPS'
    if '카프' in name:return 'CALVES'
    if '페이스풀' in name:return 'UPPER_BACK_ACCESSORY'
    if '데드리프트' in name:return 'HIP_HINGE'
    if '스쿼트' in name:return 'SQUAT_PATTERN'
    if '풀업' in name:return 'VERTICAL_PULL'
    if '로우' in name:return 'HORIZONTAL_PULL'
    if '프레스' in name:return 'PRESS_OTHER'
    return 'OTHER_'+r['category']

def load(path,asof=ASOF):
    ex=collections.defaultdict(list); dates=collections.defaultdict(list)
    with open(path,encoding='utf-8-sig',newline='') as f:
        next(f)
        for r in csv.DictReader(f):
            if r['row_type']!='set' or r['confirmed']!='1' or r['set_confirmed']!='1' or r['category'] not in CATEGORIES:continue
            try:d=datetime.date.fromisoformat(r['date'])
            except (TypeError,ValueError):continue
            if not asof-datetime.timedelta(days=55)<=d<=asof:continue
            key=r['stable_key'] or r['exercise_name'];r['_day']=d
            ex[key].append(r);dates[d].append(r)
    return ex,dates

def build_profiles(ex,asof=ASOF):
    result=[];cut28=asof-datetime.timedelta(days=27)
    for key,rows in ex.items():
        rows.sort(key=lambda r:(r['_day'],int(r['set_index'] or 0)))
        recent=[r for r in rows if r['_day']>=cut28]
        ds28=len({r['_day'] for r in recent});ds56=len({r['_day'] for r in rows})
        # 28-day rate dominates; 56-day rate is fallback for historically relevant but sparse exercises.
        weekly28=len(recent)/4;weekly56=len(rows)/8
        score=0.75*weekly28+0.25*weekly56
        last=rows[-1];lastday=last['_day']; age=(asof-lastday).days
        sessions=collections.defaultdict(list)
        for r in rows:sessions[r['_day']].append(r)
        recent_shape=sorted(sessions[lastday],key=lambda r:int(r['set_index'] or 0))
        result.append({'key':key,'name':last['exercise_name'],'category':last['category'],'role':classify(last),
            'sets28':len(recent),'sets56':len(rows),'days28':ds28,'days56':ds56,'lastDate':str(lastday),
            'ageDays':age,'weekly28':weekly28,'weekly56':weekly56,'priority':round(score,3),
            'recentShape':recent_shape})
    return sorted(result,key=lambda p:(-p['priority'],-p['days28'],p['name']))

def round_sets(x):return max(1,int(math.floor(x+0.5)))

def desired_profiles(profiles,mode):
    # Preserve recurring 28-day activities; use 56-day fallback for meaningful established activities.
    roster=[p for p in profiles if p['sets28']>=2 or (p['days56']>=2 and p['ageDays']<=35) or (p['key']=='barbell_deadlift' and p['sets56']>=2 and p['ageDays']<=42)]
    # A uses observed 28-day weekly frequency. No preselected roster or arbitrary slot count.
    allocation={p['key']:round_sets(max(p['weekly28'],p['weekly56']*.65)) for p in roster}
    reasons=[]
    if mode=='B':
        # No canonical deficits available: DO NOT invent them. Keep allocation and mark B as provisional.
        reasons.append('B_NOT_CANONICALLY_OPTIMIZED: no validated regional deficit ledger; preserves A allocation instead of guessing deficits')
    elif mode=='C':
        # Demonstration focus chosen from actual frequent squat-pattern history, not inferred user goal.
        sq=[p for p in roster if p['role']=='SQUAT_PATTERN' and '스쿼트' in p['name'] and '스플릿' not in p['name']]
        if sq:
            chosen=max(sq,key=lambda p:p['priority']); old=allocation[chosen['key']]
            allocation[chosen['key']]=old+1
            reasons.append(f'C_DEMO_ONLY: +1 nominal set for {chosen["name"]}; not a verified canonical exposure gain')
        else:reasons.append('C_NO_SUPPORTED_SQUAT_FOCUS: no focus adjustment')
    return roster,allocation,reasons

def high_stress(p,shape):
    # Conservative explicit high-load heuristic; not canonical strength certification.
    if p['role'] not in ('SQUAT_PATTERN','HIP_HINGE'):return False
    if not ('스쿼트' in p['name'] or '데드리프트' in p['name']):return False
    good=[r for r in shape if numeric(r.get('reps')) and numeric(r.get('weight_kg')) and numeric(r.get('rpe'))]
    return bool(good) and any(1<=numeric(r['reps'])<=5 and numeric(r['rpe'])>=7 and numeric(r['weight_kg'])>0 for r in good)

def shape(p,n):
    rows=p['recentShape'];sets=[]
    for i in range(n):
        r=rows[i%len(rows)];w=numeric(r.get('weight_kg'));rep=numeric(r.get('reps'));secs=numeric(r.get('seconds'))
        sets.append({'weightKg':w if w and w>0 else None,'reps':int(rep) if rep and rep>0 and rep.is_integer() else None,
            'seconds':int(secs) if secs and secs>0 else None,'observedRpe':numeric(r.get('rpe')),
            'sourceDate':p['lastDate'],'loadStatus':'HISTORICAL_LOAD_UNVERIFIED' if w and w>0 else 'NO_LOAD_OR_CALIBRATION_REQUIRED'})
    return {'exercise':p['name'],'stableKey':p['key'],'category':p['category'],'role':p['role'],
            'setCount':n,'sets':sets,'historicalSets28d':p['sets28'],'historicalSets56d':p['sets56'],
            'source':'LATEST_COMPLETED_SESSION_REUSE_NOT_NEW_PRESCRIPTION'}

def distribute(roster,allocation,days):
    # Build each exercise's weekly occurrences from observed 28d frequency (no fixed exercises/day).
    tasks=[]
    for p in roster:
        n=allocation[p['key']]
        frequency=min(days,max(1,round(p['days28']/4) if p['days28'] else round(p['days56']/8)))
        frequency=min(frequency,n)
        counts=[n//frequency+(1 if i<n%frequency else 0) for i in range(frequency)]
        for idx,c in enumerate(counts):tasks.append((p,c,idx,frequency))
    # Distribute highest burden first. Never put squat and deadlift high-load sets on same day.
    sessions=[[] for _ in range(days)]; totals=[0]*days; high=[set() for _ in range(days)]; used=collections.defaultdict(set)
    tasks.sort(key=lambda t:(-int(high_stress(t[0],t[0]['recentShape'])),-t[1],-t[0]['priority'],t[0]['name']))
    for p,count,idx,freq in tasks:
        ishigh=high_stress(p,p['recentShape'])
        family='SQUAT' if p['role']=='SQUAT_PATTERN' else 'HINGE' if p['role']=='HIP_HINGE' else None
        candidates=[]
        for i in range(days):
            if i in used[p['key']]:continue
            conflict=ishigh and family in ('SQUAT','HINGE') and ({'SQUAT','HINGE'}- {family}).intersection(high[i])
            if conflict:continue
            candidates.append(i)
        if not candidates:
            # No unsafe silent placement; report unscheduled items instead.
            return None,{'exercise':p['name'],'reason':'HIGH_STRESS_OR_FREQUENCY_PLACEMENT_IMPOSSIBLE','remainingSets':count}
        # Prioritize even weekly distribution, then preferred spaced slots for recurring exercise.
        target=(idx*days/freq) if freq>1 else None
        choice=min(candidates,key=lambda i:(totals[i],min(abs(i-target),days-abs(i-target)) if target is not None else 0,i))
        sessions[choice].append(shape(p,count));totals[choice]+=count;used[p['key']].add(choice)
        if ishigh and family:high[choice].add(family)
    return [{'session':i+1,'items':s,'nominalSets':sum(x['setCount'] for x in s)} for i,s in enumerate(sessions)],None

def infer_training_days(dates, asof=ASOF, weeks=8, trim_fraction=0.30):
    """Use complete rolling seven-day windows, including zero-activity weeks.

    Remove floor(weeks * 30%) least-active weeks, then round the mean half up.
    A workout day is a unique day with confirmed strength OR functional sets.
    """
    counts=[]
    for i in range(weeks):
        end=asof-datetime.timedelta(days=7*i)
        start=end-datetime.timedelta(days=6)
        count=sum(1 for d,rows in dates.items() if start<=d<=end and any(r['category'] in CATEGORIES for r in rows))
        counts.append({'start':str(start),'end':str(end),'trainingDays':count})
    drop=int(math.floor(weeks*trim_fraction))
    ranked=sorted(range(weeks),key=lambda i:(counts[i]['trainingDays'],i))
    dropped=set(ranked[:drop])
    kept=[x['trainingDays'] for i,x in enumerate(counts) if i not in dropped]
    mean=sum(kept)/len(kept)
    inferred=max(1,min(7,int(math.floor(mean+0.5))))
    return inferred,{'window':'8_COMPLETE_ROLLING_7_DAY_WEEKS','categories':sorted(CATEGORIES),
        'weeks':[{**w,'excludedBottom30Percent':i in dropped} for i,w in enumerate(counts)],
        'excludedWeekCount':drop,'rounding':'HALF_UP','retainedMean':round(mean,4)}

def generate(path,asof=ASOF,training_days=None):
    ex,dates=load(path,asof);profiles=build_profiles(ex,asof)
    inferred,weekly_evidence=infer_training_days(dates,asof)
    days=training_days if training_days is not None else inferred
    if not 1<=days<=7:raise ValueError('training_days must be 1..7')
    out={'schema':'v2.2.v0.5.history_driven_exploratory','source':pathlib.Path(path).name,'asOf':str(asof),
         'assumptions':{'trainingDays':days,'trainingDaysSource':'USER_INPUT' if training_days is not None else '8W_BOTTOM_30_PERCENT_TRIMMED_MEAN',
             'trainingDayInference':weekly_evidence,'sessionMinutes':'NOT_PROVIDED','explicitGoal':'NOT_PROVIDED','Cfocus':'SQUAT_DEMONSTRATION_NOT_USER_GOAL'},
         'history':{'allExerciseCount':len(profiles),'functionalExercises':sum(p['category']=='기능성운동' for p in profiles),
             'exerciseProfiles':[{k:v for k,v in p.items() if k!='recentShape'} for p in profiles]},'programs':{},
         'limitations':['NOT Kotlin V1/V2-1 canonical exposure, deficits, recovery, or strength eligibility','B is not genuinely deficit optimized without canonical stimulus ledger','C focus is a demonstration, not chosen from an explicit goal','No time limit supplied: 120% ceiling cannot be checked','No specific weekdays supplied: inter-session recovery spacing unverified','Weights, repetitions and RPE are historical observations, not prescribed progressions','Heuristic high-stress detection cannot substitute for metadata-based joint/tissue load analysis','No invented load for exercises with bodyweight, duration, or missing load']}
    for mode in 'ABC':
        roster,alloc,reasons=desired_profiles(profiles,mode)
        sessions,error=distribute(roster,alloc,days)
        if error:
            out['programs'][mode]={'status':'PLACEMENT_FAILED','error':error,'reasons':reasons};continue
        retained={x['stableKey'] for s in sessions for x in s['items']}
        missing=[p['name'] for p in roster if p['key'] not in retained]
        out['programs'][mode]={'status':'EXPLORATORY','reasons':reasons,'sessions':sessions,
            'weeklyNominalSets':sum(s['nominalSets'] for s in sessions),'exerciseCount':len(retained),
            'retainedHistoryExerciseCount':len(retained),'omittedEligibleExercises':missing,
            'roleSets':dict(collections.Counter({role:sum(x['setCount'] for s in sessions for x in s['items'] if x['role']==role) for role in {p['role'] for p in roster}}))}
    return out

def markdown(o):
    lines=['# V2-2 v0.5 기록 기반 자동 구성 (탐색적)','',f"백업: `{o['source']}` / 기준일: {o['asOf']} / 주 {o['assumptions']['trainingDays']}일 ({o['assumptions']['trainingDaysSource']})",'',
        '**주의:** 모든 중량·반복은 과거 기록 재사용이며 새로운 처방이 아닙니다. V1/V2-1 canonical 분석과 회복·시간 제한은 아직 연결되지 않았습니다.','']
    for mode,p in o['programs'].items():
        lines.extend([f'## {mode}',f"상태: {p['status']}"])
        if p['status']!='EXPLORATORY':lines.append(str(p['error']));continue
        lines.extend([f"주 {p['weeklyNominalSets']}세트 / {p['exerciseCount']}종목",''])
        for s in p['sessions']:
            lines.extend([f"### {s['session']}일차 ({s['nominalSets']}세트)",''])
            for x in s['items']:
                spec='/'.join((f"{v['weightKg']:g}kg" if v['weightKg'] is not None else '중량 없음')+(f"×{v['reps']}회" if v['reps'] is not None else f"·{v['seconds']}초" if v['seconds'] else '·반복/시간 미기록') for v in x['sets'])
                lines.append(f"- {x['exercise']} [{x['role']}] {x['setCount']}세트 — {spec}")
            lines.append('')
        lines.extend(['보존된 훈련 역할: '+', '.join(f'{k} {v}세트' for k,v in sorted(p['roleSets'].items())),''])
        lines.extend(['- '+r for r in p['reasons']]);lines.append('')
    lines.extend(['## 한계']+['- '+v for v in o['limitations']])
    return '\n'.join(lines)+'\n'

if __name__=='__main__':
    path=sys.argv[1] if len(sys.argv)>1 else '/mnt/data/whatyougottado_backup_2026-10-11.csv'
    days=int(sys.argv[2]) if len(sys.argv)>2 else None
    out=generate(path,training_days=days)
    root=pathlib.Path('/mnt/data/v2_2_v05_2026-10-11')
    root.with_suffix('.json').write_text(json.dumps(out,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
    root.with_suffix('.md').write_text(markdown(out),encoding='utf-8')
    for k,p in out['programs'].items():print(k,p['status'],p.get('weeklyNominalSets'),p.get('exerciseCount'),[s['nominalSets'] for s in p.get('sessions',[])])
