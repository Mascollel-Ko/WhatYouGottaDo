#!/usr/bin/env python3
"""Generate localized, field-aware metadata labels from the canonical catalogue."""

from __future__ import annotations

import csv
import html
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = ROOT / "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
TAXONOMY_PATH = ROOT / "app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt"
SLOT_PATH = ROOT / "app/src/main/java/com/training/trackplanner/data/ProgramSlotDefinition.kt"
KO_OUTPUT = ROOT / "app/src/main/res/values/metadata_display_catalog.xml"
EN_OUTPUT = ROOT / "app/src/main/res/values-en/metadata_display_catalog.xml"

CSV_FIELDS = {
    "currentActivityKind": "ACTIVITY_KIND",
    "currentPlanningEligibility": "PLANNING_ELIGIBILITY",
    "movementFamily": "MOVEMENT_FAMILY",
    "movementSubtype": "MOVEMENT_SUBTYPE",
    "programSlot": "PROGRAM_SLOT",
    "redundancyGroup": "REDUNDANCY_GROUP",
    "progressMetricType": "PROGRESS_METRIC",
    "strengthProgressionGroup": "STRENGTH_PROGRESSION_GROUP",
    "analysisEligibility": "ANALYSIS_ELIGIBILITY",
    "primaryStressProfile": "PRIMARY_STRESS_PROFILE",
    "secondaryStressTags": "SECONDARY_STRESS",
    "tendonStressTags": "TENDON_STRESS",
    "ligamentJointStabilityStressTags": "LIGAMENT_JOINT_STABILITY",
    "jointImpactStressTags": "JOINT_IMPACT",
    "cognitiveStressTags": "COGNITIVE_STRESS",
    "sportContextTags": "SPORT_CONTEXT",
    "recoveryDecayProfile": "RECOVERY_DECAY",
    "stressMagnitudeHint": "STRESS_LEVEL",
    "badmintonTransferLevel": "BADMINTON_TRANSFER_LEVEL",
    "badmintonTransferType": "BADMINTON_TRANSFER_TYPE",
    "badmintonSkillTargets": "BADMINTON_SKILL_TARGET",
    "badmintonPhysicalQualities": "BADMINTON_PHYSICAL_QUALITY",
    "transferConfidence": "TRANSFER_CONFIDENCE",
    "sourceConfidenceLevel": "SOURCE_CONFIDENCE",
    "finalSourceStatus": "FINAL_SOURCE_STATUS",
    "neuromuscularStressLevel": "NEUROMUSCULAR_STRESS",
    "systemicMuscularStressLevel": "SYSTEMIC_MUSCULAR_STRESS",
    "localMuscularStressLevel": "LOCAL_MUSCULAR_STRESS",
    "jointTendonImpactStressLevel": "JOINT_TENDON_IMPACT_STRESS",
    "movementFocusDemandLevel": "MOVEMENT_FOCUS_DEMAND",
    "recoveryDurationClass": "RECOVERY_DURATION",
}

ENUM_FIELDS = {
    "MovementPattern": "MOVEMENT_PATTERN",
    "MovementCategory": "MOVEMENT_CATEGORY",
    "FatigueForceType": "FORCE_TYPE",
    "FatigueTrainingRole": "TRAINING_ROLE",
    "AxialLoadLevel": "AXIAL_LOAD",
    "FatigueLaterality": "LATERALITY",
    "MetadataConfidence": "METADATA_CONFIDENCE",
    "BadmintonTransferRole": "DIRECT_TRANSFER",
}

DEFAULTS = {
    "ACTIVITY_KIND": {"EXERCISE", "SPORT_SESSION"},
    "PLANNING_ELIGIBILITY": {"PROGRAM_SELECTABLE", "FATIGUE_ONLY", "ANALYSIS_ONLY", "HIDDEN"},
    "MOVEMENT_FAMILY": {"NOT_APPLICABLE"},
    "MOVEMENT_SUBTYPE": {"NOT_APPLICABLE"},
    "PROGRAM_SLOT": {
        "NOT_APPLICABLE",
        "MAIN_LOWER_STRENGTH",
        "MAIN_HINGE_STRENGTH",
        "HORIZONTAL_PULL_STRENGTH",
        "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY",
        "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY",
        "BADMINTON_FOOTWORK",
        "DECELERATION_LANDING",
        "ROTATIONAL_KINETIC_CHAIN",
        "SCAPULAR_SHOULDER_SUPPORT",
        "TRUNK_ANTI_ROTATION_STABILITY",
        "POWER_REACTIVE_LOW_VOLUME",
        "RECOVERY_PREHAB_LIGHT",
    },
    "PROGRESS_METRIC": {
        "NOT_APPLICABLE",
        "LOAD_REPS",
        "VOLUME_LOAD",
        "ESTIMATED_1RM",
        "REPS_OR_TIME",
        "SESSION_DURATION",
        "TIME_DISTANCE",
        "QUALITY_BASED",
        "COUNT_ONLY",
    },
    "TRANSFER_CONFIDENCE": {"NONE", "LOW", "MEDIUM", "HIGH"},
    "SOURCE_CONFIDENCE": {
        "HEURISTIC_ACCEPTED",
        "ANATOMY_SUPPORTED",
        "SOURCE_WEAK_BUT_ACCEPTABLE",
        "VERIFIED_FAMILY",
        "VERIFIED_EXACT",
    },
    "FINAL_SOURCE_STATUS": {"SOURCE_ACCEPTED", "SOURCE_ACCEPTED_WITH_LIMITATION"},
    "METADATA_CONFIDENCE": {"HIGH", "MEDIUM", "LOW", "NEEDS_REVIEW", "UNKNOWN"},
}

DIRECT_KO = {
    "EXERCISE": "운동",
    "SPORT_SESSION": "스포츠 세션",
    "PROGRAM_SELECTABLE": "프로그램에 사용 가능",
    "FATIGUE_ONLY": "피로도 분석에만 사용",
    "ANALYSIS_ONLY": "분석에만 사용",
    "HIDDEN": "숨김",
    "NOT_APPLICABLE": "해당 없음",
    "LOW": "낮음",
    "MODERATE": "보통",
    "MEDIUM": "보통",
    "HIGH": "높음",
    "VERY_HIGH": "매우 높음",
    "SHORT": "짧음",
    "LONG": "김",
    "VERY_LONG": "매우 김",
    "LOAD_REPS": "중량·반복수",
    "VOLUME_LOAD": "볼륨 부하",
    "ESTIMATED_1RM": "추정 1RM",
    "REPS_OR_TIME": "반복수 또는 시간",
    "SESSION_DURATION": "세션 시간",
    "TIME_DISTANCE": "시간·거리",
    "QUALITY_BASED": "수행 품질",
    "COUNT_ONLY": "횟수만 기록",
    "VERTICAL_PULL": "수직 당기기",
    "HORIZONTAL_PUSH": "수평 밀기",
    "HIP_HINGE": "힙 힌지",
    "ROTATIONAL_KINETIC_CHAIN": "회전 운동사슬",
    "RECOVERY_PREHAB_LIGHT": "회복·프리햅 저강도",
    "SOURCE_ACCEPTED": "근거 사용 가능",
    "SOURCE_ACCEPTED_WITH_LIMITATION": "제한 조건과 함께 근거 사용",
    "HEURISTIC_ACCEPTED": "휴리스틱 검토 완료",
    "ANATOMY_SUPPORTED": "해부학적 근거 지원",
    "SOURCE_WEAK_BUT_ACCEPTABLE": "근거가 제한적이나 사용 가능",
    "VERIFIED_FAMILY": "운동 계열 확인",
    "VERIFIED_EXACT": "운동 단위 확인",
    "NEEDS_REVIEW": "검토 필요",
    "UNKNOWN": "확인되지 않음",
    "DIRECT": "직접 전이",
    "GENERAL": "일반",
    "SUPPORTIVE": "보조 전이",
    "FATIGUE_LOW": "저피로도",
    "RECOVERY_CONTEXT": "회복 맥락",
    "RECOVERY_ONLY": "회복 분석만",
    "SESSION_LOAD": "세션 부하",
    "SPORT_SESSION_LOAD": "스포츠 세션 부하",
    "TEST_METRIC": "테스트 지표",
    "AEROBIC_BASE": "유산소 기초",
    "ANAEROBIC_REPEATABILITY": "무산소 반복 능력",
    "ANKLE_STIFFNESS": "발목 강성",
    "CALF_ELASTICITY": "종아리 탄성",
    "FRONTAL_PLANE_CONTROL": "전두면 제어",
    "LOWER_BODY_FORCE": "하체 힘",
    "ROTATOR_CUFF_CONTROL": "회전근개 제어",
    "SHOULDER_DURABILITY": "어깨 지구성",
    "SINGLE_LEG_STABILITY": "한발 안정성",
    "UPPER_BODY_EXPLOSIVE_POWER": "상체 폭발력",
    "CHANGE_OF_DIRECTION": "방향 전환",
    "DEFENSIVE_COVERAGE": "수비 범위",
    "FIRST_STEP": "첫 스텝",
    "FRONT_COURT_LUNGE": "전위 코트 런지",
    "MULTI_SHUTTLE_ENDURANCE": "멀티 셔틀 지구력",
    "NET_PLAY": "네트 플레이",
    "OVERHEAD_CLEAR": "오버헤드 클리어",
    "RALLY_TOLERANCE": "랠리 지속 능력",
    "ROTATION_SEQUENCING": "회전 연결",
    "SMASH": "스매시",
    "SPLIT_STEP": "스플릿 스텝",
    "CHANGE_OF_DIRECTION_DIRECT": "방향 전환 직접 전이",
    "FOOTWORK_DIRECT": "풋워크 직접 전이",
    "LUNGE_REACH_DIRECT": "런지·리치 직접 전이",
    "OVERHEAD_HITTING_DIRECT": "오버헤드 타구 직접 전이",
    "RALLY_CONDITIONING_DIRECT": "랠리 컨디셔닝 직접 전이",
    "REACTION_DECISION_DIRECT": "반응·판단 직접 전이",
    "COACHING_FEEDBACK_PROCESSING_LOAD": "코칭 피드백 처리 부하",
    "DECISION_MAKING_LOAD": "판단 부하",
    "MOTOR_LEARNING_LOAD": "운동 학습 부하",
    "PACING_LOAD": "페이스 조절 부하",
    "POSITIONAL_AWARENESS": "위치 인식",
    "SEQUENCE_CONTROL": "순서 제어",
    "TACTICAL_CONCENTRATION_LOAD": "전술 집중 부하",
    "TECHNICAL_CONCENTRATION_LOAD": "기술 집중 부하",
    "VISUAL_TRACKING_LOAD": "시각 추적 부하",
    "MAIN_LOWER_STRENGTH": "하체 메인 근력",
    "MAIN_HINGE_STRENGTH": "힌지 메인 근력",
    "HORIZONTAL_PULL_STRENGTH": "수평 당기기 근력",
    "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY": "수평 밀기 근력·보조",
    "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY": "머리 위 밀기 근력·보조",
    "BADMINTON_FOOTWORK": "배드민턴 풋워크",
    "DECELERATION_LANDING": "감속·착지",
    "SCAPULAR_SHOULDER_SUPPORT": "견갑·어깨 보조",
    "TRUNK_ANTI_ROTATION_STABILITY": "몸통 항회전 안정성",
    "POWER_REACTIVE_LOW_VOLUME": "파워·반응성 저볼륨",
    "NONE": "없음",
}

DIRECT_EN = {
    "EXERCISE": "Exercise",
    "SPORT_SESSION": "Sport session",
    "PROGRAM_SELECTABLE": "Available for programs",
    "FATIGUE_ONLY": "Fatigue analysis only",
    "ANALYSIS_ONLY": "Analysis only",
    "HIDDEN": "Hidden",
    "NOT_APPLICABLE": "Not applicable",
    "LOW": "Low",
    "MODERATE": "Moderate",
    "MEDIUM": "Medium",
    "HIGH": "High",
    "VERY_HIGH": "Very high",
    "SHORT": "Short",
    "LONG": "Long",
    "VERY_LONG": "Very long",
    "LOAD_REPS": "Load and repetitions",
    "VOLUME_LOAD": "Volume load",
    "ESTIMATED_1RM": "Estimated 1RM",
    "REPS_OR_TIME": "Repetitions or time",
    "SESSION_DURATION": "Session duration",
    "TIME_DISTANCE": "Time and distance",
    "QUALITY_BASED": "Performance quality",
    "COUNT_ONLY": "Count only",
    "SOURCE_ACCEPTED": "Source accepted",
    "SOURCE_ACCEPTED_WITH_LIMITATION": "Source accepted with limitations",
}

KO_TOKENS = {
    "ACCESSORY": "보조", "ACHILLES": "아킬레스", "ACCELERATION": "가속",
    "AGILITY": "민첩성", "ANAEROBIC": "무산소", "ANKLE": "발목", "ANTERIOR": "전면",
    "ANTI": "항", "ARM": "팔", "AXIAL": "축성", "BACK": "등", "BADMINTON": "배드민턴",
    "BALANCE": "균형", "BALLISTIC": "탄도성", "BAND": "밴드", "BARBELL": "바벨",
    "BASE": "기본", "BENCH": "벤치", "BICEPS": "이두근", "BODY": "신체",
    "BODYWEIGHT": "맨몸", "BRACING": "브레이싱", "CALF": "종아리", "CARDIO": "유산소",
    "CARE": "관리", "CARRY": "캐리", "CHAIN": "운동사슬", "CHEST": "가슴",
    "COGNITIVE": "인지", "COMPOUND": "복합", "CONDITIONING": "컨디셔닝",
    "CONTROL": "제어", "COORDINATION": "협응", "CORE": "코어", "COURT": "코트",
    "CUFF": "회전근개", "DECELERATION": "감속", "DECISION": "판단", "DYNAMIC": "동적",
    "ECCENTRIC": "신장성", "ELASTIC": "탄성", "ELBOW": "팔꿈치", "ENDURANCE": "지구력",
    "EXACT": "정확", "EXPLOSIVE": "폭발력", "EXTENSION": "신전", "FATIGUE": "피로도",
    "FIRST": "첫", "FLEXION": "굴곡", "FOOT": "발", "FOOTWORK": "풋워크",
    "FORCE": "힘", "FOREARM": "전완", "FRONTAL": "전두면", "GENERAL": "일반",
    "GLUTE": "둔근", "GRIP": "그립", "HAMSTRING": "햄스트링", "HEAVY": "고중량",
    "HINGE": "힌지", "HIP": "고관절", "HORIZONTAL": "수평", "HYPERTROPHY": "근비대",
    "IMPACT": "충격", "ISOLATION": "고립", "ISOMETRIC": "등척성", "JOINT": "관절",
    "JUMP": "점프", "KNEE": "무릎", "LANDING": "착지", "LATERAL": "측면",
    "LIGAMENT": "인대", "LIGHT": "저강도", "LOAD": "부하", "LOADED": "중량",
    "LOCAL": "국소", "LOWER": "하체", "LUMBOPELVIC": "요골반", "LUNGE": "런지",
    "MACHINE": "머신", "MAIN": "메인", "MOBILITY": "가동성", "MOTOR": "운동",
    "MOVEMENT": "동작", "MULTI": "다방향", "NEURAL": "신경계", "OVERHEAD": "머리 위",
    "PATELLAR": "슬개건", "PATTERN": "패턴", "PLYOMETRIC": "플라이오메트릭",
    "POSTERIOR": "후면", "POWER": "파워", "PREHAB": "프리햅", "PRESS": "프레스",
    "PROGRESS": "진행", "PULL": "당기기", "PUSH": "밀기", "QUAD": "대퇴사두근",
    "QUALITY": "품질", "REACTION": "반응", "REACTIVE": "반응성", "REAR": "후면",
    "RECOVERY": "회복", "REPETITION": "반복", "ROTATION": "회전",
    "ROTATIONAL": "회전", "ROTATOR": "회전근개", "SCAPULAR": "견갑",
    "SHOULDER": "어깨", "SINGLE": "한쪽", "SKILL": "기술", "SPEED": "속도",
    "SPORT": "스포츠", "SQUAT": "스쿼트", "STABILITY": "안정성",
    "STRENGTH": "근력", "STRESS": "스트레스", "SUPPORT": "보조",
    "SUPPORTED": "보조", "SUPPORTIVE": "보조", "SYSTEMIC": "전신",
    "TECHNICAL": "기술", "TENDON": "건", "TEST": "테스트", "TIME": "시간",
    "TRANSFER": "전이", "TRICEPS": "삼두근", "TRUNK": "몸통", "UNILATERAL": "편측",
    "UPPER": "상체", "VERTICAL": "수직", "VOLUME": "볼륨", "WRIST": "손목",
    "AEROBIC": "유산소", "CONCENTRATION": "집중", "CONTEXT": "맥락",
    "DIRECTION": "방향", "DIRECT": "직접", "DURABILITY": "지구성",
    "FEEDBACK": "피드백", "HITTING": "타구", "LEARNING": "학습", "LEG": "다리",
    "MAKING": "판단", "METRIC": "지표", "ONLY": "만", "PACING": "페이스 조절",
    "PLANE": "면", "PLAY": "플레이", "RALLY": "랠리", "REACH": "리치",
    "REPEATABILITY": "반복 능력", "SEQUENCE": "순서", "SEQUENCING": "연결",
    "SHUTTLE": "셔틀", "STIFFNESS": "강성", "TACTICAL": "전술",
    "TOLERANCE": "지속 능력", "TRACKING": "추적", "VERIFIED": "검증",
    "VISUAL": "시각",
}

FIELD_NONE_KO = {
    "BADMINTON_TRANSFER_LEVEL": "직접 전이 없음",
    "TRANSFER_CONFIDENCE": "평가 없음",
}
FIELD_NONE_EN = {
    "BADMINTON_TRANSFER_LEVEL": "No direct transfer",
    "TRANSFER_CONFIDENCE": "Not assessed",
}


def enum_values(source: str, enum_name: str) -> set[str]:
    match = re.search(rf"enum class {enum_name}\s*\{{(.*?)\n\}}", source, re.S)
    if not match:
        raise ValueError(f"Missing enum {enum_name}")
    return {
        token.strip()
        for token in match.group(1).split(",")
        if token.strip() and re.fullmatch(r"[A-Z][A-Z0-9_]*", token.strip())
    }


def english_label(code: str) -> str:
    if code in DIRECT_EN:
        return DIRECT_EN[code]
    acronyms = {"1RM", "COD", "RDL", "SSC", "EZ", "V", "VIPR"}
    words = [word if word in acronyms else word.lower() for word in code.split("_")]
    label = " ".join(words)
    return label[0].upper() + label[1:] if label else code


def korean_label(field: str, code: str, subtype_names: dict[str, str]) -> str:
    if code == "NONE" and field in FIELD_NONE_KO:
        return FIELD_NONE_KO[field]
    if field == "MOVEMENT_SUBTYPE" and code in subtype_names:
        return subtype_names[code]
    if code in DIRECT_KO:
        return DIRECT_KO[code]
    words = [KO_TOKENS.get(word, english_label(word)) for word in code.split("_")]
    return " ".join(words)


def english_field_label(field: str, code: str) -> str:
    if code == "NONE" and field in FIELD_NONE_EN:
        return FIELD_NONE_EN[field]
    return english_label(code)


def build_entries() -> tuple[dict[str, set[str]], dict[str, str]]:
    values: dict[str, set[str]] = defaultdict(set)
    subtype_names: dict[str, str] = {}
    with CSV_PATH.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            for column, field in CSV_FIELDS.items():
                for code in row[column].split("|"):
                    if code:
                        values[field].add(code)
            subtype = row["movementSubtype"]
            name = row["exerciseName"]
            if subtype and name:
                current = subtype_names.get(subtype)
                if current is None or len(name) < len(current):
                    subtype_names[subtype] = name

    taxonomy = TAXONOMY_PATH.read_text(encoding="utf-8")
    for enum_name, field in ENUM_FIELDS.items():
        values[field].update(enum_values(taxonomy, enum_name))
    movement_patterns = enum_values(taxonomy, "MovementPattern")
    program_slots = enum_values(SLOT_PATH.read_text(encoding="utf-8"), "ProgramSlotId")
    values["MOVEMENT_FAMILY"].update(movement_patterns | program_slots)
    values["MOVEMENT_SUBTYPE"].update(movement_patterns)
    values["REDUNDANCY_GROUP"].update(program_slots)
    values["STRENGTH_PROGRESSION_GROUP"].update(
        enum_values(taxonomy, "StrengthProgressionGroup") | program_slots
    )
    values["ANALYSIS_ELIGIBILITY"].update(enum_values(taxonomy, "AnalysisEligibility"))
    values["SECONDARY_STRESS"].update(enum_values(taxonomy, "FatigueCategory"))
    joint_stress = enum_values(taxonomy, "JointStressTag")
    values["TENDON_STRESS"].update(joint_stress)
    values["LIGAMENT_JOINT_STABILITY"].update(joint_stress)
    values["BADMINTON_TRANSFER_TYPE"].update(
        enum_values(taxonomy, "BadmintonTransferRole")
    )
    values["BADMINTON_SKILL_TARGET"].update(enum_values(taxonomy, "BadmintonSkillTarget"))
    values["BADMINTON_PHYSICAL_QUALITY"].update(
        enum_values(taxonomy, "CourtMovementType") |
        enum_values(taxonomy, "BalanceContributionTag")
    )
    values["SUPPORTIVE_TRANSFER"].update(enum_values(taxonomy, "BadmintonTransferRole"))
    values["PROGRAM_SLOT"].update(program_slots)
    levels = {"LOW", "MODERATE", "HIGH", "VERY_HIGH"}
    for field in (
        "STRESS_LEVEL",
        "NEUROMUSCULAR_STRESS",
        "SYSTEMIC_MUSCULAR_STRESS",
        "LOCAL_MUSCULAR_STRESS",
        "JOINT_TENDON_IMPACT_STRESS",
        "MOVEMENT_FOCUS_DEMAND",
    ):
        values[field].update(levels)
    durations = {"SHORT", "MEDIUM", "LONG", "VERY_LONG"}
    values["RECOVERY_DECAY"].update(durations)
    values["RECOVERY_DURATION"].update(durations)
    for field, defaults in DEFAULTS.items():
        values[field].update(defaults)
    return values, subtype_names


def write_resource(path: Path, entries: list[str]) -> None:
    body = "\n".join(f"        <item>{html.escape(entry)}</item>" for entry in entries)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<resources>\n"
        "    <string-array name=\"metadata_display_entries\">\n"
        f"{body}\n"
        "    </string-array>\n"
        "</resources>\n",
        encoding="utf-8",
        newline="\n",
    )


def main() -> None:
    values, subtype_names = build_entries()
    korean = []
    english = []
    for field in sorted(values):
        for code in sorted(values[field]):
            korean.append(f"{field}|{code}|{korean_label(field, code, subtype_names)}")
            english.append(f"{field}|{code}|{english_field_label(field, code)}")
    write_resource(KO_OUTPUT, korean)
    write_resource(EN_OUTPUT, english)
    print(f"Generated {len(korean)} field-aware labels per locale.")


if __name__ == "__main__":
    main()
