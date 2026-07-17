package com.training.trackplanner.analysis.tissue

data class TissueRcvAuthorityRow(
    val exerciseStableKey: String,
    val exerciseName: String,
    val bodyRegion: String,
    val loadUnitStableKey: String,
    val loadUnitCode: String,
    val loadUnitName: String,
    val tissueClass: String,
    val jointComplexStableKey: String,
    val jointComplexName: String,
    val mappingRoles: Set<String>,
    val magnitudeM: Double,
    val rapidityS: Double,
    val contextC: Double,
    val loadProfileP: String,
    val doseBasis: String,
    val doseUnit: String,
    val doseRule: String,
    val bodyWeightCoefficient: Double?,
    val contextFlags: Set<String>,
    val scoreVersion: String,
    val mappingConfidence: String,
    val familyImportance: String,
    val scoreStatus: String,
    val sourceRefs: List<String>
)

data class TissueRcvExerciseProtocol(
    val exerciseStableKey: String,
    val exerciseName: String,
    val defaultProtocolClass: String,
    val diProfileId: String,
    val functionalCurveId: String,
    val jointProtectionCurveId: String,
    val fastMechanicalCurveId: String,
    val biologicalCurveRouting: String,
    val runtimeFlags: Set<String>,
    val mappingStatus: String
)

data class TissueRcvProtocolClass(
    val protocolClass: String,
    val diProfileId: String,
    val functionalCurveId: String,
    val jointProtectionCurveId: String,
    val fastMechanicalCurveId: String,
    val runtimeFlags: Set<String>,
    val exerciseCount: Int
)

data class TissueRcvDiProfile(
    val id: String,
    val doseBasis: String,
    val requiredInputs: List<String>,
    val optionalInputs: List<String>,
    val doseFormula: String,
    val effortPriority: List<String>,
    val effortRule: String,
    val loadProfileRole: String
)

data class TissueRecoveryKnot(
    val elapsedHours: Double,
    val value: Double
)

data class TissueRecoveryCurve(
    val id: String,
    val displayName: String,
    val outcome: String,
    val interpolation: String,
    val evidenceGrade: String,
    val evidenceNote: String,
    val knots: List<TissueRecoveryKnot>
)

data class TissueRecoveryRouting(
    val recoveryClass: String,
    val channels: Set<String>,
    val fastCurveId: String?,
    val biologicalCurveId: String?
)

data class TissueRcvJointComplex(
    val stableKey: String,
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val bodyRegion: String,
    val complexType: String
)

data class TissueRcvLoadUnit(
    val stableKey: String,
    val code: String,
    val nameKo: String,
    val nameEn: String,
    val tissueClass: String,
    val jointComplexStableKey: String,
    val analysisResolution: String,
    val primaryLoadModes: Set<String>,
    val recoveryClass: String,
    val memberTissueCount: Int
)

data class TissueRcvCatalog(
    val authorityRows: List<TissueRcvAuthorityRow>,
    val exerciseStableKeys: Set<String>,
    val protocols: Map<String, TissueRcvExerciseProtocol>,
    val protocolClasses: Map<String, TissueRcvProtocolClass>,
    val diProfiles: Map<String, TissueRcvDiProfile>,
    val curves: Map<String, TissueRecoveryCurve>,
    val routing: Map<String, TissueRecoveryRouting>,
    val jointComplexes: Map<String, TissueRcvJointComplex>,
    val loadUnits: Map<String, TissueRcvLoadUnit>
) {
    val unresolvedExerciseCount: Int
        get() = protocols.values.count { it.mappingStatus == "UNRESOLVED_GENERIC" }
}
