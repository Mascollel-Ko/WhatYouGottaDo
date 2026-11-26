"""Read the pinned repository assets and generate the audit CSV. No runtime writes."""

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "app/src/main/assets/metadata"
CANONICAL = ASSETS / "canonical_v1"
MISSING = {"", "UNKNOWN", "UNCLASSIFIED"}
REQUIRED = (
    "currentActivityKind", "currentPlanningEligibility", "movementFamily", "movementSubtype",
    "redundancyGroup", "programSlot", "progressMetricType", "strengthProgressionGroup",
    "analysisEligibility", "primaryStressProfile", "recoveryDecayProfile", "recoveryDurationClass",
    "neuromuscularStressLevel", "systemicMuscularStressLevel", "localMuscularStressLevel",
    "jointTendonImpactStressLevel", "movementFocusDemandLevel",
)
SOURCES = {
    "PULL_UP_CHIN_UP_LAT_PULLDOWN_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/21068680/",
    "ROW_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/15228624/",
    "ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/36976950/",
    "CALF_RAISE_ANKLE_STIFFNESS_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/38156065/",
    "DEADLIFT_HINGE_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/30662500/",
    "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS": "https://pubmed.ncbi.nlm.nih.gov/33049982/",
    "KNEE_EXTENSION_QUAD_ISOLATION": "https://pubmed.ncbi.nlm.nih.gov/41379528/",
}
# Row-specific judgments. The priority marks a review recommendation, not a change approval.
NOTES = {
    "ex_16b298db": ("P1", "주동근 세분 분류 불일치", "muscleRelations",
                    "밴드 측방 이동의 외전 토크를 GLUTES에 묶었다. 중둔근 역할을 머신 외전과 같은 기준으로 재판정하고 밴드 위치와 고관절 각도에 따른 대둔근 기여를 남긴다."),
    "ex_728da646": ("P1", "머신 외전의 장비 태그가 강제 요구로 소비됨", "equipment 소비",
                     "GLUTE_MEDIUS PRIMARY는 합리적이다. 단일 HIP_ABDUCTION_MACHINE 요구를 MACHINE·BAND까지 모두 요구하는 문자열과 대조한다."),
    "hip_adduction_machine": ("", "", "", "앉은 지지와 고정 저항에서 HIP_ADDUCTOR PRIMARY가 직접 내전 과제에 맞는다."),
    "cable_hip_adduction": ("", "", "", "운동측 내전과 지지측 골반 제어를 구별한다. 주동근 HIP_ADDUCTOR를 유지할 근거가 있다."),
    "ex_a8385c4a": ("P2", "직접 측면 코어 목적의 변형 의존", "targetTaskRelations",
                    "상부 다리 내전과 측면 몸통 힘은 공존한다. 지지 길이·큐 및 처방 목표 없이 HIDDEN_HIGH를 DIRECT로 승격하지 않는다."),
    "ex_d5bdffe1": ("", "", "", "대측 사지에서 신전 모멘트가 생기는 동안 전면 복부의 항신전과 동적 안정이 직접 과제다. 복부가 등척성으로 작동해도 PRIMARY가 타당하다."),
    "ex_28347c1f": ("P2", "보조근·안정근 경계", "muscleRelations",
                    "대측 팔·다리의 무게로 생기는 회전과 신전을 제어한다. 척추 주변의 고정 역할과 둔근 신전의 분류를 수행 지침에 맞춰 검토한다."),
    "ex_f6d43398": ("P2", "골반 안정근 해상도", "muscleRelations",
                    "외측 몸통의 항측굴은 직접 과제다. GLUTES 안정 행이 지지측 중둔근 외전 요구를 구분할 수 있는지 확인한다."),
    "ex_a44ae2ca": ("", "", "", "앞 플랭크는 엎드린 자세의 항신전 유지다. 전면 복부 PRIMARY와 시간 기반 사용이 적절하다."),
    "ex_2a826c82": ("", "", "", "팔·다리 지렛대를 늘리며 후방 골반경사와 항신전을 유지한다. 같은 시간이라도 플랭크와 부하는 다르다."),
    "ex_a345e30b": ("P2", "수행 조건에 따른 주동근 과대 귀속 가능", "muscleRelations|jointAction",
                    "전완 지지로 그립 요구가 줄어든다. 캡틴체어에서 골반을 말아 올리는 범위가 직접 복부 자극의 전제다."),
    "ex_b3f447be": ("P2", "수행 조건에 따른 주동근 과대 귀속 가능", "muscleRelations|jointAction",
                    "현수 그립과 어깨의 안정 행은 타당하다. 고관절 굴곡과 골반 말아 올리기를 구분하고 고관절 굴곡근 해상도를 검토한다."),
    "ex_644e0f9d": ("P2", "고관절 굴곡근 해상도 부족", "muscleRelations",
                    "몸통 굴곡과 다리 들기가 동시 발생한다. 복부 PRIMARY가 적합하고 고관절 굴곡근의 동적 역할을 표현할 수 있는지 확인한다."),
    "barbell_deadlift": ("P1", "보조근과 그립 안정근 누락 후보", "muscleRelations",
                         "바닥에서 무릎을 펴는 구간의 대퇴사두와 바를 유지하는 그립을 재검토한다. 힙 힌지와 바 지지의 주 역할은 유지한다."),
    "barbell_romanian_deadlift": ("P2", "그립 안정근 누락 후보", "muscleRelations",
                                  "무릎 각도가 비교적 일정한 바벨 힌지다. 둔근·햄스트링과 척추 안정은 타당하다. 스트랩 사용과 그립의 역할을 구별한다."),
    "ex_e0b1e364": ("P2", "명칭·편측 동작 범위 확인", "laterality|redundancyGroup",
                     "한 팔에 드는 방식만으로 한 다리 수행을 뜻하지 않는다. 실제 지지 다리와 하중의 측면을 설명 및 이미지와 대조한다."),
    "ex_deca2b61": ("P1", "주동근·보조근 역전 후보", "muscleRelations",
                     "삼두 슬롯인데 CHEST만 PRIMARY다. 벤치 아래로 내려가는 손 지지 조건에서 팔꿈치 신전의 삼두를 우선 검토한다."),
    "ex_64661229": ("P2", "주동근 과다 지정 후보", "muscleRelations",
                     "무릎 각도 유지의 대퇴사두가 중심이다. 둔근 PRIMARY는 발 위치·깊이별 근거가 필요하다."),
    "ex_b4b198de": ("P2", "내전근 역할 누락 후보", "muscleRelations",
                     "전두면 편향이 있어 내전근이 하강·복귀에 참여한다. 지지측과 뻗은 다리의 역할을 따로 확인한다."),
    "ex_c4535de3": ("P1", "직접 전거근 역할이 SHOULDERS로 흡수", "muscleRelations",
                     "견갑 전인과 흉곽 지지가 핵심이다. 삼각근 주도 운동과 같은 피로로 계산하지 않도록 과제를 표시한다."),
    "ex_f97a05ab": ("P2", "견갑 동작의 직접 목표 표현 부족", "physicalQualityRelations|muscleRelations",
                     "전거근·승모근의 상방회전 제어가 목표일 수 있다. 고정적인 근비대 세트로 환산하지 않고 품질 과제로 검증한다."),
    "ex_e1894690": ("P1", "서로 다른 동작을 대체군에 묶음", "redundancyGroup|programSlot|muscleRelations",
                     "팔꿈치가 거의 고정된 견갑 동작이다. 풀업·랫풀다운과의 대체 가능성 및 견갑 하강·고정 역할을 분리한다."),
    "face_pull": ("P1", "상부 등·회전근개 역할 및 하중 채널", "muscleRelations|progressMetricType",
                  "광배 SECONDARY는 설명된 견갑 후인/외회전의 대표 근육으로 부적절하다. 상부 등과 회전근개를 구별하고 케이블 부하 실측을 보존한다."),
    "ex_e8ff8cfa": ("P2", "회전근개와 삼각근 혼합", "muscleRelations",
                     "극하근·소원근을 SHOULDERS로만 나타내면 오버헤드 프레스와 동일 주동근으로 교환 신호를 낸다. 저항 방향과 제어 과제를 우선 사용한다."),
    "ex_b78a8f95": ("P2", "지지 기구의 안정근 과다 지정 후보", "muscleRelations",
                     "등받이가 있는 기구에서 전면 복부를 항상 유의미한 운동 안정근으로 기록할 근거가 약하다. QUADS는 유지한다."),
    "ex_d60745b4": ("P2", "지지 기구의 안정근 과다 지정 후보", "muscleRelations",
                     "편측이어도 시트와 허벅지 고정 방식에 따라 복부 안정 요구가 낮다. 무릎 신전 주동근과 좌우 기록을 보존한다."),
    "ex_6232f4bc": ("", "", "", "한쪽 발로 올라가는 무릎·고관절 신전이다. QUADS·GLUTES 주동근이 합리적이고 스쿼트와 국소 피로를 공유한다."),
    "ex_704cbf1a": ("P1", "스텝업에 부가 장비 태그를 강제할 수 있음", "equipment 소비",
                     "박스 높이·편측 자세가 핵심이다. 장비 요구표의 박스와 덤벨만으로 통과하는지 확인한다."),
    "ex_7ce96a7a": ("P1", "스플릿 스쿼트에 부가 장비 태그를 강제할 수 있음", "equipment 소비",
                     "앞발을 높여 무릎과 고관절 가동 범위를 바꾼다. 권위의 박스와 덤벨에 TRX 링을 항상 추가 요구하지 않도록 검증한다."),
    "ex_69a56484": ("P2", "스텝업과 하강 제어의 대체성 과장", "redundancyGroup|movementSubtype",
                     "측면 스텝다운의 편심 하강·정렬 제어와 상승 스텝업의 근력 처방을 독립적으로 비교한다."),
    "split_stance_contralateral_cable_row_dumbbell_press": (
        "P1", "직접 상지·코어 과제와 처방 채널의 충돌", "muscleRelations|progressMetricType|equipment 소비",
        "반대 방향의 케이블 당기기·덤벨 상방 밀기는 별개의 부하이고 복사근은 회전을 억제한다. CHEST 보조의 근거가 약하다. 기술 우선은 유지하며 양쪽 부하·반복 기록을 검증한다."),
    "ex_91f6958d": ("P2", "역할 미해결 및 시간·하중 구분", "muscleRelations|primaryStressProfile|progressMetricType",
                    "REVIEW_REQUIRED는 움직임 단계별 역할이 다름을 반영한다. 캐리 피로 유형을 그대로 공유할 적절성과 무게가 품질 기록에서 빠지는지 확인한다."),
    "ex_4aa3725a": ("P2", "몸통 안정 역할 과다 지정 후보", "muscleRelations",
                     "등받이와 궤도 제약이 허리를 지지하는 기구에서는 전면 코어 기여를 비지지 프레스와 구별한다."),
    "ex_1dbee10e": ("P2", "몸통 안정 역할 과다 지정 후보", "muscleRelations",
                     "머신과 패드 지지를 확인하고 프리웨이트 또는 원암 프레스와 같은 복부 안정 행을 관행적으로 유지하지 않는다."),
    "machine_chest_fly": ("P2", "몸통 안정 역할 과다 지정 후보", "muscleRelations",
                           "좌석과 패드가 지지하는 수평 모음에서 전면 복부는 강도를 결정하기 어렵다."),
    "ex_5c8751d2": ("P2", "무릎 굴곡과 종아리 근육 해상도", "muscleRelations|redundancyGroup",
                     "굽힌 무릎은 비복근 길이를 줄인다. 앉은 저측굴곡을 선 자세와 완전 중복으로 평가할 근거는 약하다."),
    "ex_5ca7133f": ("", "", "", "저측굴곡의 CALVES는 타당하다. 단측 구간의 균형, 상대 하중과 좌우 처방을 보존한다."),
    "ex_281347da": ("", "", "", "팔꿈치 굴곡의 BICEPS와 전완 관여는 합리적이다. 머신이 관절을 지지하더라도 같은 계열로 볼 수 있는지는 기구를 확인한다."),
    "ex_e159d15a": ("P2", "진행·회복 보조표에 행 없음", "progressionRelations|recoveryRelations",
                     "런타임 진행·회복 필드와 core 권위는 존재한다. 일반 인버티드 로우의 승인된 보조 관계를 독립적으로 조사하고 다른 장비 변형에서 추론하지 않는다."),
    "ex_40fa57be": ("P2", "높은 팔꿈치 경로의 근육·보조근 해상도", "muscleRelations",
                     "케이블의 높이와 상완 벌림이 클수록 후면 어깨·견갑 후인이 중요해질 수 있다. 팔꿈치 굴곡 보조를 누락했는지 점검한다."),
    "ex_de46b7f6": ("P1", "무지지 힌지의 척추 안정근 누락 후보", "muscleRelations",
                     "바벨 로우에서 굽힌 몸통의 신전 모멘트가 등받이 지지 로우와 다르다. 척추기립근의 등척성 지지와 견갑 후인을 따로 기록한다."),
    "ex_8e4bf08e": ("P2", "척추 안정근 누락 후보", "muscleRelations",
                     "더 세운 Yates 자세는 일반 굽힌 로우와 모멘트가 다르다. 실제 상체 각도와 그립에 맞춰 척추 지지 및 팔 굴곡을 재검토한다."),
    "ex_708e64ce": ("P2", "대흉근·광배 역할의 수행 의존", "muscleRelations",
                     "덤벨의 중력 방향 및 벤치 지지를 기준으로 두 근육의 주·보조 분류를 재검토한다."),
    "ex_8a3800f1": ("P2", "기술 지표와 저항 기록의 결합", "progressMetricType",
                     "편심 범위·속도·보조량을 기록한다. 외부 하중이 긴 지렛대의 질을 대체하지 않지만 실측 기록은 유효하다."),
    "plate_rotational_press_out": ("P2", "실제 회전과 회전 저항 경계", "movementSubtype|coreRelations",
                                   "몸통이 실제 회전하는지 하중의 회전 모멘트를 버티는지 수행 설명과 영상으로 확인한다."),
    "ex_3f184ef9": ("", "", "", "푸시업/점프 생략 여부에 따라 기계적 역할이 바뀐다. GENERAL_PARTICIPATION과 반복/시간 단위는 합리적이다."),
}


def read(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def grouped(rows, key):
    result = defaultdict(list)
    for row in rows:
        result[row[key]].append(row)
    return result


def show(rows, *fields):
    return " | ".join(":".join(row[field] for field in fields) for row in rows)


def index(rows, key):
    result = {row[key]: row for row in rows}
    assert len(rows) == len(result), key
    return result


def review(row, exercise, identity, roles, missing):
    key, family = row["stableKey"], row["movementFamily"]
    findings, directions, fields, priorities = [], [], [], []

    def flag(priority, finding, field, direction):
        if priority:
            findings.append(finding)
            fields.extend(field.split("|"))
            priorities.append(priority)
        directions.append(direction)

    if key in NOTES:
        flag(*NOTES[key])
    if "|" in exercise["equipment"]:
        flag("P1", "초기 장비 문자열 분리 누락", "equipment 소비",
             "결합 장비를 모두 보유하는 입력을 초기·공동·최종 후보 단계에서 동일하게 검증한다.")
    if identity["equipmentRequirementModel"] == "NONE" and "," in exercise["equipment"]:
        flag("P1", "장비 없음 권위와 부가 태그 소비가 다름", "equipment 소비",
             "보조 장비 없이 가능한 수행을 검증하고 요구 장비와 검색 태그의 역할을 구분한다.")
    if row["progressMetricType"] in {"LOAD_REPS_OR_TIME", "QUALITY_LOAD_REPS"}:
        flag("P1", "복합 진행 지표에서 혼합 하중 채널이 사라질 수 있음", "progressMetricType 소비",
             "사용자 고정 외의 확인된 무게가 있을 때도 중량×반복 채널을 보존하는지 검증한다.")
    if any(item["muscleCode"] in {"HIP_ADDUCTOR", "GLUTE_MEDIUS", "FOREARM_GRIP"}
           for item in roles.get("PRIMARY", [])):
        flag("P1", "일부 직접 근육이 최종 비대 목표에 없음", "hypertrophy 목표 소비",
             "해당 근육의 직접 기여를 기록하고 B의 고정 비대 목표에서 제외할 정책인지 검증한다.")
    if family == "ROW_VARIANTS":
        flag("P2", "광배와 견갑 후인근 해상도 부족", "muscleRelations",
             "상완 신전과 견갑 후인 노출을 지지·팔꿈치 경로에 따라 검토한다.")
    if family == "REAR_DELT_FLY_HORIZONTAL_ABDUCTION_VARIANTS":
        flag("P2", "후면 삼각근과 기타 어깨 해상도 부족", "muscleRelations",
             "후면 삼각근과 기구 지지에 따른 견갑 후인 역할을 구분한다.")
    if family == "ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS":
        flag("P2", "상완근·상완요골근과 그립 해상도 부족", "muscleRelations",
             "중립·회내 시 팔꿈치 굴곡과 손목/그립 부하의 역할을 별도로 비교한다.")
    if roles.get("REVIEW_REQUIRED"):
        flag("P2", "권위 원본의 관계 검토 보류", "muscleRelations",
             "표시된 관절 과제와 변형을 확인하고 검토 보류를 유지한다.")
    if any(item["status"].startswith("PROVISIONAL") for item in roles.get("PRIMARY", [])):
        flag("P2", "잠정 주동근을 단계별로 다르게 소비", "muscleRelations 소비",
             "BJoint의 보류와 V2의 포함이 목적에 맞는지 같은 로스터에서 확인한다.")
    if missing:
        flag("P1", "필수 메타데이터 값 불명", "|".join(missing),
             "원본과 런타임 필드를 대조해 근거 있는 값을 확인한다.")
    return (" | ".join(dict.fromkeys(findings)), " | ".join(dict.fromkeys(directions)),
            " | ".join(dict.fromkeys(fields)), min(priorities, default=""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((CANONICAL / "manifest.json").read_text(encoding="utf-8"))
    for entry in manifest["files"]:
        assert hashlib.sha256((CANONICAL / entry["path"]).read_bytes()).hexdigest() == entry["sha256"], entry

    identities = index(read(CANONICAL / "identity_master.csv"), "stableKey")
    bootstrap = index(read(CANONICAL / "exercise_bootstrap.csv"), "stableKey")
    runtime = index(read(CANONICAL / "runtime_metadata.csv"), "stableKey")
    assert identities.keys() == runtime.keys() == bootstrap.keys()
    assert NOTES.keys() <= identities.keys()
    with (HERE / "b_coach_exercise_metadata_review_20261125.tsv").open(encoding="utf-8", newline="") as stream:
        family_notes = index(list(csv.DictReader(stream, delimiter="\t")), "movementFamily")
    assert set(family_notes) == {row["movementFamily"] for row in runtime.values()}

    files = ("muscle_relations", "movement_relations", "core_relations", "training_roles",
             "history_training_roles", "program_slot_capabilities", "history_program_slot_capabilities",
             "physical_quality_relations", "progression_relations", "recovery_relations",
             "badminton_objective_relations", "badminton_relations", "equipment_relations", "ofi_relations")
    tables = {}
    for name in files:
        rows = read(CANONICAL / f"{name}.csv")
        assert all(row["exerciseStableKey"] in identities for row in rows), name
        tables[name] = grouped(rows, "exerciseStableKey")
    b_tasks = grouped(read(ASSETS / "b_balanced_v1/target_task_relations.csv"), "exerciseStableKey")
    counts = Counter()
    audit = []
    for key, identity in identities.items():
        row, exercise = runtime[key], bootstrap[key]
        family = row["movementFamily"]
        family_note = family_notes[family]
        def related(table):
            return tables[table].get(key, [])

        muscle = related("muscle_relations")
        assert all(item["exerciseName"] == identity["exerciseName"] for item in muscle)
        roles = grouped(muscle, "muscleRelationType")
        selectable = identity["selectable"] == "YES"
        planning = row["currentPlanningEligibility"] == "PROGRAM_SELECTABLE"
        missing = [field for field in REQUIRED if row[field] in MISSING] if selectable else []
        if selectable:
            missing += [name for name in ("core_relations", "movement_relations", "muscle_relations",
                                         "equipment_relations") if not related(name)]
        optional_gaps = [name for name in ("progression_relations", "recovery_relations")
                         if selectable and not related(name)]
        if selectable and not int(exercise["defaultRestSeconds"]) > 0:
            missing.append("defaultRestSeconds")
        found, direction, fields, priority = review(row, exercise, identity, roles, missing) if selectable else ("", "", "", "")
        if selectable and roles.get("REVIEW_REQUIRED"):
            verdict = "기초 역학 검토 완료. 보류 근육 관계의 확정 필요"
        elif selectable and not roles.get("PRIMARY") and roles.get("GENERAL_PARTICIPATION"):
            verdict = "기계적 참여를 인정하되 현재 고정 근육 자극·교환 점수는 0"
        elif not selectable:
            verdict = "이력 전용. 동일명 또는 유사한 변형으로 역할 승계 금지"
        elif priority:
            verdict = "기초 역학 검토 완료. 표시된 항목의 재판정 권고"
        else:
            verdict = "검토한 현행 분류 범위에서 명백한 불일치 없음"
        all_roles = {role: show(roles.get(role, []), "muscleCode", "coefficient")
                     for role in ("PRIMARY", "SECONDARY", "STABILIZER", "GENERAL_PARTICIPATION",
                                  "NOT_SUPPORTED", "REVIEW_REQUIRED")}
        counts["all"] += 1
        counts["selectable" if selectable else "history_only"] += 1
        counts["program_selectable"] += int(planning)
        counts["required_missing_cells"] += len(missing)
        counts["optional_gaps"] += len(optional_gaps)
        counts["recommendation"] += int(bool(priority))
        counts["priority_" + priority] += int(bool(priority))
        counts["with_review_required_relation"] += int(bool(roles.get("REVIEW_REQUIRED")))
        for role in all_roles:
            counts[role] += int(bool(roles.get(role)))
            counts["plan_" + role] += int(planning and bool(roles.get(role)))
        capabilities = related("program_slot_capabilities")
        core = [item["relationValue"] for item in related("core_relations") if item["relationType"] == "CORE_CLASS"]
        use = ["선택/대체/배치에서 사용 가능한 번들 필드", "OFI bootstrap 수치: 일정 회복",
               "진행 지표: load/RIR 분기"] if planning else [row["currentPlanningEligibility"]]
        if planning and roles.get("PRIMARY"):
            use.append("주동근: 교환 탐색 및 일부 근육의 근비대량")
        if planning and roles.get("SECONDARY"):
            use.append("보조근: 근비대량")
        if core == ["DIRECT"]:
            use.append("직접 코어량")
        if related("badminton_objective_relations"):
            use.append("전이 목표별 점수")
        if any(item["capabilityCode"] == "MAIN_STRENGTH_SLOT" for item in capabilities):
            use.append("MAIN 자격(선정과 구별)")
        if not selectable:
            use = ["이력 표시. 근육 승계 없음. 도메인별 호환 관계는 별도"]
        mechanics = family_note["역학 검토"] if selectable else "일반명 이력 보존. 수행 변형과 장비를 확정할 수 없다."
        program = family_note["프로그램 역할 검토"] if selectable else "신규 선택 제외가 원본 계약이다. 기존 기록의 재사용 경로는 문서 참고."
        evidence = "authority workbook, identity/jointAction + bootstrap description + typed relations"
        if selectable and family in SOURCES:
            evidence += " | " + SOURCES[family] + " (연구 대상과 본 행의 차이는 보고서 참조)"
        audit.append({
            "stableKey": key, "exerciseName": identity["exerciseName"],
            "group": identity["identityStatus"], "selectable": identity["selectable"],
            "bootstrapActive": exercise["isActive"], "lineageType": identity["lineageType"],
            "sourceStableKey": identity["sourceStableKey"], "historyTreatment": identity["historyTreatment"],
            **all_roles,
            "muscleEvidenceState": show(muscle, "muscleCode", "muscleRelationType", "confidence",
                                        "sourceStatus", "status", "provenance"),
            **{field: value for field, value in row.items() if field not in {"stableKey", "exerciseName"}},
            "trainingRoles": show(related("training_roles") + related("history_training_roles"), "trainingRoleCode"),
            "slotCapabilities": show(capabilities + related("history_program_slot_capabilities"), "capabilityCode"),
            "jointAction": identity["jointAction"], "laterality": exercise["laterality"],
            "equipmentRequirementModel": identity["equipmentRequirementModel"],
            "equipmentRequirements": show(related("equipment_relations"), "equipmentCode", "simultaneousRequired"),
            "equipmentRuntime": exercise["equipment"], "defaultRestSeconds": exercise["defaultRestSeconds"],
            "loadMode": exercise["mode"], "movementRelations": show(related("movement_relations"), "relationType", "relationValue"),
            "coreRelations": show(related("core_relations"), "relationType", "relationValue", "reviewStatus"),
            "bTargetTasks": show(b_tasks.get(key, []), "targetTask", "reviewStatus", "numericDoseAuthority", "recordingUnit"),
            "qualityRelations": show(related("physical_quality_relations"), "qualityId", "relationLevel",
                                     "regionQualifier", "modeQualifier", "prescriptionDependent", "reviewStatus"),
            "progressionRelations": show(related("progression_relations"), "progressionGroup", "progressMetricType",
                                          "strengthProgressionGroup"),
            "recoveryRelations": show(related("recovery_relations"), "recoveryDecayProfile",
                                       "recoveryDurationClass", "stressMagnitudeHint"),
            "badmintonObjectives": show(related("badminton_objective_relations"), "objectiveId", "transferLevel", "reviewStatus"),
            "badmintonRelations": show(related("badminton_relations"), "relationType", "relationValue", "transferConfidence"),
            "ofiRelations": show(related("ofi_relations"), "relationId", "role", "coefficient"),
            "scheduleWeights": " | ".join(f"{field}={exercise[field]}" for field in
                                          ("systemicLoadWeight", "neuralHeavyWeight", "neuralSpeedWeight",
                                           "localLoadWeight", "decelerationWeight", "elasticSscWeight")),
            "volumeLoadEligible": exercise["volumeLoadEligible"],
            "estimated1RmEligible": exercise["estimated1RmEligible"],
            "description": exercise["description"], "mechanicalReview": mechanics,
            "programRoleReview": program, "runtimeUse": " | ".join(use),
            "finding": found, "recommendation": direction or ("이력 원본 유지" if not selectable else "검토 범위에서 현재 역할 유지"),
            "fieldsToReview": fields,
            "source": evidence,
            "evidenceConfidence": ("이력상 식별 한계" if not selectable else
                                   "추가 검토 필요" if roles.get("REVIEW_REQUIRED") else
                                   "운동역학 판단과 유사 과제 실증" if family in SOURCES else
                                   "운동역학 추론. 정량 효과 근거 제한"),
            "priority": priority, "reviewStatus": verdict,
            "requiredMissing": " | ".join(missing),
            "optionalRelationGaps": " | ".join(optional_gaps),
        })
    assert sum(row["selectable"] == "YES" for row in audit) == manifest["counts"]["selectableIdentityRows"]
    assert len(audit) == manifest["counts"]["identityRows"]
    with args.output.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(audit[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(audit)
    assert read(args.output) == audit
    print(json.dumps(dict(counts), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
