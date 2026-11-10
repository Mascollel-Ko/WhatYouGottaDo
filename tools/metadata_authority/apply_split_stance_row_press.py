"""One-shot, source-controlled addition of a reviewed asymmetric row/press.

Patch the existing authority OOXML directly to preserve the workbook's other
sheets and cell contents. Run from the repository root. No xlsx dependency.
"""
from __future__ import annotations

import csv
import hashlib
import io
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
WORKBOOK = ROOT / 'docs/metadata_authority/WhatYouGottaDo_metadata_authority_v1.xlsx'
KEY = 'split_stance_contralateral_cable_row_dumbbell_press'
NAME = '스플릿 스탠스 케이블 로우 + 덤벨 프레스'
DESC = ('스플릿 스탠스에서 케이블을 한 팔로 당기면서 반대 팔로 덤벨을 수평으로 밀어낸다. '
        '골반과 흉곽의 정렬을 유지하며 불필요한 몸통 회전을 제어한다. 좌우를 바꿔 실시한다.')
EVIDENCE = ('User-selected badminton training priority. Exercise mechanics support anti-rotation task; '
            'specific badminton performance improvements are not established by a direct study.')
N = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
P = 'http://schemas.openxmlformats.org/package/2006/relationships'
NS = {'x': N, 'r': R}
ET.register_namespace('', N)
ET.register_namespace('r', R)


def sname(col: int) -> str:
    out = ''
    while col:
        col, rem = divmod(col - 1, 26)
        out = chr(rem + 65) + out
    return out


def parse_text(cell, shared):
    if cell is None:
        return ''
    kind = cell.attrib.get('t')
    if kind == 'inlineStr':
        return ''.join((t.text or '') for t in cell.findall('.//x:is/x:t', NS))
    v = cell.find('x:v', NS)
    raw = '' if v is None or v.text is None else v.text
    return shared[int(raw)] if kind == 's' and raw else raw


def rows_of(tree, shared):
    data = tree.find('.//x:sheetData', NS)
    values = []
    for row in data.findall('x:row', NS):
        values.append({re.match(r'[A-Z]+', c.attrib.get('r', '')).group(0): parse_text(c, shared)
                       for c in row.findall('x:c', NS) if re.match(r'[A-Z]+', c.attrib.get('r', ''))})
    headers = values[0]
    columns = [(col, head) for col, head in headers.items() if head]
    return data, columns, [{head: values[i].get(col, '') for col, head in columns} for i in range(1, len(values))]


def append(tree, columns, entries):
    data = tree.find('.//x:sheetData', NS)
    maxrow = max((int(r.get('r', 0)) for r in data.findall('x:row', NS)), default=1)
    for entry in entries:
        maxrow += 1
        row = ET.SubElement(data, f'{{{N}}}row', {'r': str(maxrow)})
        for col, header in columns:
            value = str(entry.get(header, '') or '')
            if not value:
                continue
            c = ET.SubElement(row, f'{{{N}}}c', {'r': f'{col}{maxrow}', 't': 'inlineStr'})
            ins = ET.SubElement(c, f'{{{N}}}is')
            t = ET.SubElement(ins, f'{{{N}}}t')
            if value.strip() != value:
                t.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
            t.text = value
    dimension = tree.find('x:dimension', NS)
    if dimension is not None:
        end_col = columns[-1][0]
        dimension.set('ref', f'A1:{end_col}{maxrow}')


def copy(rows, key, **changes):
    source = next(row for row in rows if row.get('stableKey', row.get('exerciseStableKey')) == key)
    return {**source, **changes}


def series(rows, source, key_col, id_col, ids, **changes):
    out = []
    for i, base in enumerate(row for row in rows if row.get(key_col) == source):
        row = {**base, **changes}
        row[key_col] = KEY
        row[id_col] = f'{ids}_{i + 1:03d}'
        if 'sourceStableKey' in row:
            row['sourceStableKey'] = KEY
        if 'exerciseName' in row:
            row['exerciseName'] = NAME
        if 'provenance' in row:
            row['provenance'] = 'USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10'
        out.append(row)
    return out


def patch_workbook():
    with zipfile.ZipFile(WORKBOOK) as src:
        files = {it.filename: (it, src.read(it.filename)) for it in src.infolist()}
    wb = ET.fromstring(files['xl/workbook.xml'][1])
    rels = ET.fromstring(files['xl/_rels/workbook.xml.rels'][1])
    idpaths = {}
    for rel in rels.findall(f'{{{P}}}Relationship'):
        target = rel.get('Target', '').lstrip('/')
        if not target.startswith('xl/'):
            target = 'xl/' + target
        idpaths[rel.get('Id')] = target
    sheets = {s.get('name'): idpaths[s.get(f'{{{R}}}id')]
              for s in wb.findall('.//x:sheets/x:sheet', NS)}
    shared = []
    if 'xl/sharedStrings.xml' in files:
        root = ET.fromstring(files['xl/sharedStrings.xml'][1])
        shared = [''.join((v.text or '') for v in si.findall('.//x:t', NS))
                  for si in root.findall('x:si', NS)]
    changes = {}

    def update(sheet, builder):
        path = sheets[sheet]
        tree = ET.fromstring(files[path][1])
        _, cols, rows = rows_of(tree, shared)
        if any(row.get('stableKey', row.get('exerciseStableKey')) == KEY for row in rows):
            return
        additions = builder(rows)
        if additions:
            append(tree, cols, additions)
            changes[path] = ET.tostring(tree, encoding='utf-8', xml_declaration=True)

    update('05_IDENTITY_MASTER', lambda rs: [copy(rs, 'cable_pallof_press',
        stableKey=KEY, exerciseName=NAME, identityStatus='SELECTABLE_CANONICAL', sourceStableKey=KEY,
        lineageType='NEW_INDEPENDENT_EXERCISE', selectable='YES',
        historyTreatment='NEW_IDENTITY_NO_AUTOMATIC_HISTORY_MIGRATION', movementPattern='ANTI_ROTATION',
        jointAction='ELBOW_EXTENSION|ELBOW_FLEXION|SHOULDER_EXTENSION|SHOULDER_HORIZONTAL_ADDUCTION|SCAPULAR_RETRACTION',
        movementEvent='', kineticChain='OPEN_CHAIN', stabilityDemandLevel='HIGH', mobilityDemandLevel='LOW',
        equipmentRequirementModel='ALL_REQUIRED', equipmentCodes='CABLE|DUMBBELL',
        equipmentRequirementGroupId='eqreq_' + KEY, sourceOrigin='WORKBOOK_CANONICAL',
        mappingConfidence='MEDIUM', identityDecisionStatus='', reviewStatus='REVIEW_RECOMMENDED',
        analysisInheritance='EXPLICIT_NEW_EXERCISE_AUTHORITIES', evidence=EVIDENCE,
        provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10')])

    def runtime(rs):
        r = copy(rs, 'cable_pallof_press', stableKey=KEY, exerciseName=NAME,
            currentActivityKind='EXERCISE', currentPlanningEligibility='PROGRAM_SELECTABLE',
            movementFamily='ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS', movementSubtype='PALLOF_PRESS',
            programSlot='TRUNK_ANTI_ROTATION_STABILITY', redundancyGroup='TRUNK_ANTI_MOVEMENT',
            progressMetricType='QUALITY_BASED', strengthProgressionGroup='NONE',
            analysisEligibility='FATIGUE|BADMINTON_TRANSFER|BALANCE',
            primaryStressProfile='ANTI_ROTATION_CORE_STABILITY_STRESS',
            secondaryStressTags='CORE_BRACING_LOAD|ANTI_ROTATION_LOAD|STABILITY_COORDINATION_LOAD',
            tendonStressTags='ROTATOR_CUFF_TENDON_STRESS',
            ligamentJointStabilityStressTags='SHOULDER_SCAPULAR_STABILITY_STRESS|LUMBOPELVIC_CONTROL_STRESS',
            jointImpactStressTags='NONE', cognitiveStressTags='MOTOR_LEARNING_LOAD', sportContextTags='NONE',
            recoveryDecayProfile='SHORT', stressMagnitudeHint='LOW',
            badmintonTransferLevel='DIRECT', badmintonTransferType='ANTI_ROTATION_STABILITY|SHOULDER_CARE',
            badmintonSkillTargets='ANTI_ROTATION_STABILITY|SHOULDER_DURABILITY',
            badmintonPhysicalQualities='ANTI_ROTATION|SCAPULAR_STABILITY|UNILATERAL_UPPER',
            transferConfidence='MEDIUM', sourceConfidenceLevel='HEURISTIC_ACCEPTED',
            finalSourceStatus='SOURCE_ACCEPTED_WITH_LIMITATION', neuromuscularStressLevel='LOW',
            systemicMuscularStressLevel='LOW', localMuscularStressLevel='MODERATE',
            jointTendonImpactStressLevel='MODERATE', movementFocusDemandLevel='HIGH',
            recoveryDurationClass='SHORT', appCueProfile='NONE')
        return [r]
    update('07_RUNTIME_SNAPSHOT', runtime)
    update('08_SCALAR_METADATA', lambda rs: [dict(copy(rs, 'cable_pallof_press',
        stableKey=KEY, metadataSourceStableKey=KEY),
        **{k:v for k,v in runtime([])[0].items() if k not in ('stableKey', 'exerciseName')
           and k in rs[0]})])

    def movements(rs):
        vals = [('MOVEMENT_PATTERN', 'ANTI_ROTATION'), ('MOVEMENT_PATTERN', 'HORIZONTAL_PUSH'),
                ('MOVEMENT_PATTERN', 'HORIZONTAL_PULL'), ('JOINT_ACTION', 'ELBOW_EXTENSION'),
                ('JOINT_ACTION', 'ELBOW_FLEXION'), ('JOINT_ACTION', 'SHOULDER_EXTENSION'),
                ('JOINT_ACTION', 'SHOULDER_HORIZONTAL_ADDUCTION'), ('JOINT_ACTION', 'SCAPULAR_RETRACTION')]
        return [dict.fromkeys(rs[0], '') | dict(relationId=f'MOVEMENT_SPLIT_ROW_PRESS_{i:02d}',
                exerciseStableKey=KEY, exerciseName=NAME, relationType=t, relationValue=v,
                sourceStableKey=KEY, status='REVIEW_RECOMMENDED',
                provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10')
                for i,(t,v) in enumerate(vals,1)]
    update('09_MOVEMENT_REL', movements)

    def muscles(rs):
        roles = [ ('BACK_LATS','PRIMARY',1), ('CHEST','PRIMARY',1),
            ('LATERAL_CORE','PRIMARY',1), ('SHOULDERS','SECONDARY',0.5),
            ('BICEPS','SECONDARY',0.5), ('TRICEPS','SECONDARY',0.5),
            ('ANTERIOR_CORE','STABILIZER',0), ('POSTERIOR_CHAIN_ERECTORS','STABILIZER',0),
            ('GLUTES','STABILIZER',0), ('FOREARM_GRIP','STABILIZER',0) ]
        return [dict.fromkeys(rs[0], '') | dict(relationKey=f'MUSCLE_SPLIT_ROW_PRESS_{i:02d}',
            exerciseStableKey=KEY, exerciseName=NAME, muscleCode=m, muscleRelationType=role,
            qualifier='', coefficient=str(coef), confidence='0.65',
            sourceStatus='USER_APPROVED_MECHANICS_REVIEW', status='PROVISIONAL_MECHANICS_V1',
            sourceStableKey=KEY, provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10')
            for i,(m,role,coef) in enumerate(roles,1)]
    update('10_MUSCLE_REL', muscles)

    # Do not duplicate unsupported tissue-specific numerical doses from another exercise.
    update('12_OFI_REL', lambda rs: series(rs,'cable_pallof_press','exerciseStableKey','relationKey','OFI_SPLIT_ROW_PRESS'))
    update('13_RECOVERY_REL', lambda rs: [copy(rs,'cable_pallof_press',
         exerciseStableKey=KEY, exerciseName=NAME, recoveryDecayProfile='SHORT',
         recoveryDurationClass='SHORT',stressMagnitudeHint='LOW',sourceStableKey=KEY,
         provenanceStatus='DIRECT_SOURCE',sourceStatus='SOURCE_ACCEPTED_WITH_LIMITATION',
         provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10')])

    def badminton(rs):
        items=[('TRANSFER_LEVEL','DIRECT'), ('TRANSFER_TYPE','ANTI_ROTATION_STABILITY'),
            ('TRANSFER_TYPE','SHOULDER_CARE'), ('SKILL_TARGET','ANTI_ROTATION_STABILITY'),
            ('SKILL_TARGET','SHOULDER_DURABILITY'), ('PHYSICAL_QUALITY','ANTI_ROTATION'),
            ('PHYSICAL_QUALITY','SCAPULAR_STABILITY'), ('PHYSICAL_QUALITY','UNILATERAL_UPPER')]
        return [dict.fromkeys(rs[0], '') | dict(relationKey=f'BADMINTON_SPLIT_ROW_PRESS_{i:02d}',
            exerciseStableKey=KEY, exerciseName=NAME, relationType=t, relationValue=v,
            transferConfidence='MEDIUM', identityStatus='SELECTABLE_CANONICAL',
            sourceStableKey=KEY, inheritanceSourceStableKey='', reviewStatus='PASS',
            evidenceSource='HEURISTIC_ACCEPTED|SOURCE_ACCEPTED_WITH_LIMITATION',
            provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10',
            notes=EVIDENCE) for i,(t,v) in enumerate(items,1)]
    update('14_BADMINTON_REL', badminton)
    update('15_PROGRESSION', lambda rs: [dict(copy(rs,'cable_pallof_press',
         exerciseStableKey=KEY, exerciseName=NAME, progressionGroup='TRUNK_ANTI_MOVEMENT',
         progressMetricType='QUALITY_BASED', strengthProgressionGroup='NONE',
         sourceStableKey=KEY, sourceStatus='USER_APPROVED_MECHANICS_REVIEW',
         provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10'),
         relationKey='PROGRESSION_SPLIT_ROW_PRESS_001')])
    update('18_TRAINING_ROLE', lambda rs: [dict(copy(rs,'half_kneeling_single_arm_dumbbell_press',
         exerciseStableKey=KEY, trainingRoleCode='STABILITY', relationScope='PRODUCTION_ACTIVE',
         provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10', reviewStatus='APPROVED'),
         notes='Main badminton anti-rotation and cross-body upper force-transfer stability training.')])
    update('19_PROGRAM_SLOT', lambda rs: [dict(copy(rs,'half_kneeling_single_arm_dumbbell_press',
         exerciseStableKey=KEY, capabilityCode='STABILITY_SLOT', relationScope='PRODUCTION_ACTIVE',
         provenance='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10', reviewStatus='APPROVED'),
         notes='Directly trains anti-rotation task, not overhead swing power.')])
    update('20_EQUIPMENT_REL', lambda rs: [dict.fromkeys(rs[0], '') | dict(
         exerciseStableKey=KEY,exerciseName=NAME,groupId='eqreq_'+KEY,
         requirementModel='ALL_REQUIRED',memberOrder=str(i),equipmentCode=eq,
         simultaneousRequired='YES',sourceStableKey=KEY,
         note='Both cable and dumbbell must be available in the same set.')
         for i,eq in enumerate(('CABLE','DUMBBELL'),1)])
    update('28_PROGRAM_TIMING', lambda rs: [dict(copy(rs,'cable_pallof_press',stableKey=KEY,
         defaultRestSeconds='75', provenanceId='TIMING-'+KEY, derivationMode='NEW_EXERCISE_REVIEWED',
         reviewStatus='APPROVED', sourceStableKey=KEY,
         sourceReference='USER_APPROVED_SPLIT_STANCE_ROW_PRESS_2026_11_10'))])
    def bootstrap(rs):
        row=copy(rs,'cable_pallof_press', stableKey=KEY, name=NAME, category='근력운동',
         detail1='광배근/대흉근/복사근',detail2='삼각근/이두근/삼두근/코어',
         mode='케이블+덤벨, 무게*횟수',description=DESC,defaultRestSeconds='75',
         familyId=KEY,familyName=NAME,familyRole='PRIMARY',familyE1rmMultiplier='1',
         movementPattern='ANTI_ROTATION',movementCategory='STABILITY',
         primaryMuscles='LAT,CHEST,OBLIQUE',secondaryMuscles='SHOULDER,BICEPS,TRICEPS',
         equipment='CABLE|DUMBBELL',equipmentTags='CABLE|DUMBBELL',
         compoundType='HYBRID',forceType='BRACE',bodyRegion='WHOLE_BODY',plane='MULTI_PLANAR',
         laterality='ASYMMETRIC',axialLoadLevel='LOW',
         stabilityRoles='CORE_STABILITY,ANTI_ROTATION,TRUNK_CONTROL,SHOULDER_STABILITY',
         sportTransferDirect='ANTI_ROTATION_STABILITY',
         sportTransferSupportive='SHOULDER_CARE',
         badmintonTransferRoles='ANTI_ROTATION_STABILITY,SHOULDER_CARE',
         fatigueCategories='LOCAL_MUSCLE,ANTI_ROTATION,GRIP_FOREARM',
         adaptiveBaselineGroups='ANTI_ROTATION,GRIP_FOREARM',
         accessoryRoles='CORE_STABILITY,SHOULDER_CARE,BADMINTON_SUPPORT',
         loadProfile='MODERATE_LOAD',recoveryDecayProfile='SHORT',
         systemicLoadWeight='0.25',neuralHeavyWeight='0.25',neuralSpeedWeight='0',
         localLoadWeight='0.5',decelerationWeight='0',elasticSscWeight='0',
         rotationPowerWeight='0',antiRotationWeight='0.75',overheadSwingWeight='0',
         gripLoadWeight='0.25',progressMetricType='QUALITY_BASED',
         strengthProgressionGroup='NONE',hypertrophyVolumeGroup='NONE',mainLiftGroup='NONE',
         accessoryContributionGroup='BADMINTON_SUPPORT',estimated1RmEligible='NO',
         volumeLoadEligible='NO',badmintonTransferStrength='DIRECT',courtMovementTypes='NONE',
         badmintonSkillTargets='ANTI_ROTATION_STABILITY|SHOULDER_DURABILITY',
         jointStressTags='SHOULDER,ELBOW,WRIST,HIP,LOW_BACK',stabilityDemandLevel='HIGH',
         mobilityDemandLevel='LOW',balanceContributionTags='ANTI_ROTATION,SHOULDER_CARE',
         analysisEligibility='FATIGUE|BADMINTON_TRANSFER|BALANCE',activityKind='EXERCISE',
         planningEligibility='PROGRAM_SELECTABLE',metadataConfidence='MEDIUM',imageAssetName='',
         isActive='YES',archivedAt='',isCustom='NO',needsReview='NO')
        return [row]
    update('29_EXERCISE_BOOTSTRAP', bootstrap)

    if changes:
        out = WORKBOOK.with_suffix('.xlsx.tmp')
        with zipfile.ZipFile(out,'w') as dst:
            for path,(item,old) in files.items():
                dst.writestr(item,changes.get(path,old))
        out.replace(WORKBOOK)
    return len(changes)


def patch_fixed_counts():
    paths={
       ROOT/'tools/metadata_authority/validate_authority_workbook.py':
           [('len(identities) == 253','len(identities) == 254'),
            ('Expected 253 identities','Expected 254 identities'),
            ('len(selectable) == 242','len(selectable) == 243'),
            ('Expected 242 selectable identities','Expected 243 selectable identities')],
       ROOT/'app/src/main/java/com/training/trackplanner/data/CanonicalExerciseMetadataRepository.kt':
           [('EXPECTED_IDENTITY_ROWS = 253','EXPECTED_IDENTITY_ROWS = 254'),
            ('EXPECTED_SELECTABLE_ROWS = 242','EXPECTED_SELECTABLE_ROWS = 243'),
            ('require(rows.size == 273)','require(rows.size == 275)')],
       ROOT/'tools/metadata_authority/analysis_cutover_authority.py':
           [('len(core_rows) != 242','len(core_rows) != 243'),
            ('must have 242 rows','must have 243 rows'),
            ('len(relations) != 273','len(relations) != 275'),
            ('count must be 273','count must be 275'),
            ('len(relations) != 280','len(relations) != 281'),
            ('relation count must be 280','relation count must be 281')],
    }
    for path,replacements in paths.items():
        text=path.read_text(encoding='utf-8')
        for old,new in replacements:
            text=text.replace(old,new)
        path.write_text(text,encoding='utf-8',newline='\n')


def add_approved_csv(path, new):
    with path.open(encoding='utf-8-sig',newline='') as source:
        rows=list(csv.DictReader(source))
    if not any(row.get('stableKey',row.get('exerciseStableKey'))==KEY for row in rows):
        rows.append(new)
        with path.open('w',encoding='utf-8-sig',newline='') as output:
            writer=csv.DictWriter(output,fieldnames=list(rows[0]),lineterminator='\n')
            writer.writeheader()
            writer.writerows(rows)


def patch_sources():
    core=ROOT/'docs/metadata_authority/core_training_classification_review_2026-08-13.csv'
    with core.open(encoding='utf-8-sig',newline='') as f:
        rows=list(csv.DictReader(f))
    if not any(r['stableKey']==KEY for r in rows):
        row={k:'' for k in rows[0]}
        row.update(stableKey=KEY,exerciseName=NAME,sourceRowOrder='243',classOrder='1',
            coreClass='DIRECT',directTarget='ANTI_ROTATION',
            inventorySnapshotMovementPattern='ANTI_ROTATION',inventorySnapshotStabilityDemand='HIGH',
            sourceEquipment='CABLE|DUMBBELL',sourceCommit='USER_APPROVED_2026_11_10',
            classificationReason='Direct task is active anti-rotation under opposite-arm pull and push.',
            evidenceGroup='DIRECT_CORE',evidenceApplicability='BIOMECHANICAL_REASONING',
            evidenceUrl='',confidence='MEDIUM',reviewStatus='REVIEW_RECOMMENDED')
        add_approved_csv(core,row)
    obj=ROOT/'docs/metadata_authority/badminton_objective_relations_v2_authority.csv'
    with obj.open(encoding='utf-8-sig',newline='') as f: rows=list(csv.DictReader(f))
    if not any(r['exerciseStableKey']==KEY for r in rows):
        new={k:'' for k in rows[0]}
        new.update(relationId='BADMINTON_OBJECTIVE_SPLIT_ROW_PRESS',exerciseStableKey=KEY,
            objectiveId='ANTI_ROTATION',transferLevel='DIRECT',
            provenance='USER_APPROVED_BADMINTON_OBJECTIVE_2026_08_14',
            evidenceRelationKeys='',reviewStatus='PASS',reviewReason=EVIDENCE)
        add_approved_csv(obj,new)
    f=ROOT/'tools/metadata_authority/analysis_cutover_authority.py'
    content=f.read_text(encoding='utf-8')
    for constant,source in [('CORE_APPROVED_SHA256',core),('BADMINTON_OBJECTIVE_AUTHORITY_SHA256',obj)]:
        digest=hashlib.sha256(source.read_bytes()).hexdigest()
        content=re.sub(r'('+constant+r' = ")[0-9a-f]+("\n)',lambda m:m.group(1)+digest+m.group(2),content)
    f.write_text(content,encoding='utf-8',newline='\n')


def main():
    if not WORKBOOK.exists():
        raise SystemExit('Missing checked-in authority workbook')
    updated=patch_workbook()
    patch_sources()
    patch_fixed_counts()
    print(f'Updated {updated} authority workbook sheets; key={KEY}')

if __name__=='__main__':
    main()