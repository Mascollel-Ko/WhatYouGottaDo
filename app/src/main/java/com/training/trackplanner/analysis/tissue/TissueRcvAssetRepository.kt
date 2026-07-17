package com.training.trackplanner.analysis.tissue

import android.content.Context

object TissueRcvAssetFiles {
    const val DIRECTORY = "metadata/tissue_load_v1"
    const val AUTHORITY = "tissue_rcv_exercise_load_unit_authority_v1.csv"
    const val EXERCISE_INDEX = "tissue_rcv_exercise_index_v1.csv"
    const val EXERCISE_PROTOCOLS = "tissue_rcv_exercise_protocols_v1.csv"
    const val PROTOCOL_CLASSES = "tissue_rcv_protocol_classes_v1.csv"
    const val DI_PROFILES = "tissue_rcv_di_profiles_v1.csv"
    const val CURVE_KNOTS = "tissue_rcv_recovery_curve_knots_v1.csv"
    const val ROUTING = "tissue_rcv_recovery_routing_v1.csv"
    const val JOINT_COMPLEXES = "tissue_rcv_joint_complexes_v1.csv"
    const val LOAD_UNITS = "tissue_rcv_load_units_v1.csv"
    const val EDUCATIONAL_INFO = "tissue_rcv_educational_info_v1.csv"
    const val COD_CONTEXT_EXERCISE_TIERS = "cod_context_exercise_tiers_v1.csv"
    const val COD_CONTEXT_LOAD_UNIT_ELIGIBILITY = "cod_context_load_unit_eligibility_v1.csv"
    const val COD_CONTEXT_MODIFIER_RULES = "cod_context_modifier_rules_v1.csv"

    val required = listOf(
        AUTHORITY,
        EXERCISE_INDEX,
        EXERCISE_PROTOCOLS,
        PROTOCOL_CLASSES,
        DI_PROFILES,
        CURVE_KNOTS,
        ROUTING,
        JOINT_COMPLEXES,
        LOAD_UNITS,
        EDUCATIONAL_INFO,
        COD_CONTEXT_EXERCISE_TIERS,
        COD_CONTEXT_LOAD_UNIT_ELIGIBILITY,
        COD_CONTEXT_MODIFIER_RULES
    )
}

class TissueRcvAssetRepository private constructor(
    val catalog: TissueRcvCatalog
) {
    fun authorityRowsFor(stableKey: String): List<TissueRcvAuthorityRow> =
        catalog.authorityRows.filter { it.exerciseStableKey == stableKey }

    companion object {
        fun fromAssets(context: Context): TissueRcvAssetRepository =
            fromCsv(TissueRcvAssetFiles.required.associateWith { fileName ->
                context.assets.open("${TissueRcvAssetFiles.DIRECTORY}/$fileName")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            })

        fun fromCsv(csvByFileName: Map<String, String>): TissueRcvAssetRepository {
            fun table(fileName: String): TissueCsvTable =
                TissueMetadataParser.table(requireNotNull(csvByFileName[fileName]) {
                    "Missing RCV asset: $fileName"
                })

            val authorityRows = table(TissueRcvAssetFiles.AUTHORITY).rows.map { row ->
                TissueRcvAuthorityRow(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    exerciseName = row.required("운동명"),
                    bodyRegion = row.required("bodyRegion"),
                    loadUnitStableKey = row.required("loadUnitStableKey"),
                    loadUnitCode = row.required("loadUnitCode"),
                    loadUnitName = row.required("분석단위명"),
                    tissueClass = row.required("조직유형"),
                    jointComplexStableKey = row.required("jointComplexStableKey"),
                    jointComplexName = row.required("관절복합체명"),
                    mappingRoles = row.tokens("mappingRoles").toSet(),
                    magnitudeM = row.double("M"),
                    rapidityS = row.double("S_rapidity"),
                    contextC = row.double("C"),
                    loadProfileP = row.required("P_load_profile"),
                    doseBasis = row.required("DoseBasis"),
                    doseUnit = row.required("DoseUnit"),
                    doseRule = row.required("DoseRule"),
                    bodyWeightCoefficient = row.optionalDouble("bodyWeightCoefficient"),
                    contextFlags = row.tokens("ContextFlags").toSet(),
                    scoreVersion = row.required("점수버전"),
                    mappingConfidence = row.value("mappingConfidence"),
                    familyImportance = row.value("familyImportance"),
                    scoreStatus = row.required("scoreStatus"),
                    sourceRefs = row.value("근거URL").lines().map(String::trim).filter(String::isNotBlank)
                )
            }
            val exerciseIndexRows = table(TissueRcvAssetFiles.EXERCISE_INDEX).rows
            val exerciseNamesByStableKey = exerciseIndexRows.associate { row ->
                row.required("exerciseStableKey") to row.required("운동명")
            }
            val exerciseStableKeys = exerciseNamesByStableKey.keys
            val protocols = table(TissueRcvAssetFiles.EXERCISE_PROTOCOLS).rows.map { row ->
                TissueRcvExerciseProtocol(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    exerciseName = row.required("운동명"),
                    defaultProtocolClass = row.required("defaultProtocolClass"),
                    diProfileId = row.required("diProfileId"),
                    functionalCurveId = row.required("functionalCurveId"),
                    jointProtectionCurveId = row.required("jointProtectionCurveId"),
                    fastMechanicalCurveId = row.required("fastMechanicalCurveId"),
                    biologicalCurveRouting = row.required("biologicalCurveRouting"),
                    runtimeFlags = row.tokens("runtimeFlags").toSet(),
                    mappingStatus = row.required("mappingStatus")
                )
            }.associateBy(TissueRcvExerciseProtocol::exerciseStableKey)
            val protocolClasses = table(TissueRcvAssetFiles.PROTOCOL_CLASSES).rows.map { row ->
                TissueRcvProtocolClass(
                    protocolClass = row.required("protocolClass"),
                    diProfileId = row.required("diProfile"),
                    functionalCurveId = row.required("기능곡선"),
                    jointProtectionCurveId = row.required("관절보호곡선"),
                    fastMechanicalCurveId = row.required("급성기계곡선"),
                    runtimeFlags = row.tokens("runtime flags").toSet(),
                    exerciseCount = row.int("운동 수")
                )
            }.associateBy(TissueRcvProtocolClass::protocolClass)
            val diProfiles = table(TissueRcvAssetFiles.DI_PROFILES).rows.map { row ->
                TissueRcvDiProfile(
                    id = row.required("diProfileId"),
                    doseBasis = row.required("Dose basis"),
                    requiredInputs = row.tokens("필수 입력"),
                    optionalInputs = row.tokens("선택 입력"),
                    doseFormula = row.required("D_raw 공식"),
                    effortPriority = row.tokens("I 우선순위"),
                    effortRule = row.required("I 규칙"),
                    loadProfileRole = row.required("P 역할")
                )
            }.associateBy(TissueRcvDiProfile::id)
            val curveRows = table(TissueRcvAssetFiles.CURVE_KNOTS).rows
            val curves = curveRows.groupBy { it.required("curveId") }.mapValues { (id, rows) ->
                val first = rows.first()
                TissueRecoveryCurve(
                    id = id,
                    displayName = first.required("설명"),
                    outcome = first.required("semantic"),
                    interpolation = first.required("interpolation"),
                    evidenceGrade = first.required("evidenceGrade"),
                    evidenceNote = first.value("basis"),
                    knots = rows.map { row ->
                        TissueRecoveryKnot(
                            elapsedHours = row.double("hours"),
                            value = row.double("value")
                        )
                    }.sortedBy(TissueRecoveryKnot::elapsedHours)
                )
            }
            val routing = table(TissueRcvAssetFiles.ROUTING).rows.map { row ->
                TissueRecoveryRouting(
                    recoveryClass = row.required("recoveryClass"),
                    channels = row.tokens("채널").toSet(),
                    fastCurveId = row.value("fastCurve").takeIf(String::isNotBlank),
                    biologicalCurveId = row.value("biologicalCurve").takeIf(String::isNotBlank)
                )
            }.associateBy(TissueRecoveryRouting::recoveryClass)
            val jointComplexes = table(TissueRcvAssetFiles.JOINT_COMPLEXES).rows.map { row ->
                TissueRcvJointComplex(
                    stableKey = row.required("jointComplexStableKey"),
                    code = row.required("canonicalCode"),
                    nameKo = row.required("canonicalNameKo"),
                    nameEn = row.required("canonicalNameEn"),
                    bodyRegion = row.required("bodyRegion"),
                    complexType = row.required("complexType")
                )
            }.associateBy(TissueRcvJointComplex::stableKey)
            val loadUnits = table(TissueRcvAssetFiles.LOAD_UNITS).rows.map { row ->
                TissueRcvLoadUnit(
                    stableKey = row.required("loadUnitStableKey"),
                    code = row.required("canonicalCode"),
                    nameKo = row.required("canonicalNameKo"),
                    nameEn = row.required("canonicalNameEn"),
                    tissueClass = row.required("tissueClass"),
                    jointComplexStableKey = row.required("primaryJointComplexStableKey"),
                    analysisResolution = row.required("analysisResolution"),
                    primaryLoadModes = row.tokens("primaryLoadModes").toSet(),
                    recoveryClass = row.required("recoveryClass"),
                    memberTissueCount = row.int("memberTissueCount")
                )
            }.associateBy(TissueRcvLoadUnit::stableKey)
            val educationalTable = table(TissueRcvAssetFiles.EDUCATIONAL_INFO)
            require(educationalTable.header.none { it.contains("side", ignoreCase = true) }) {
                "Educational metadata must not contain a side field."
            }
            val educationalRows = educationalTable.rows.map { row ->
                TissueEducationalInfo(
                    stableKey = row.required("stableKey"),
                    displayNameKo = row.required("displayNameKo"),
                    anatomicalLocationKo = row.required("anatomicalLocationKo"),
                    primaryFunctionsKo = row.tokens("primaryFunctionsKo"),
                    commonLoadContextsKo = row.tokens("commonLoadContextsKo"),
                    shortDescriptionKo = row.value("shortDescriptionKo").takeIf(String::isNotBlank),
                    scope = enumValueOf(row.required("scope"))
                )
            }
            require(educationalRows.map(TissueEducationalInfo::stableKey).distinct().size == educationalRows.size) {
                "Educational metadata contains a duplicate stable key."
            }
            val educationalInfo = educationalRows.associateBy(TissueEducationalInfo::stableKey)
            val codContextExerciseTiers = table(TissueRcvAssetFiles.COD_CONTEXT_EXERCISE_TIERS).rows
                .map { row ->
                    TissueCodContextExerciseTier(
                        exerciseStableKey = row.required("exerciseStableKey"),
                        displayNameKo = row.required("displayNameKo"),
                        factor = row.double("factor"),
                        policyVersion = row.required("policyVersion"),
                        rationale = row.required("rationale"),
                        evidenceStatus = row.required("evidenceStatus"),
                        reviewStatus = row.required("reviewStatus")
                    )
                }
            val codContextLoadUnitEligibility =
                table(TissueRcvAssetFiles.COD_CONTEXT_LOAD_UNIT_ELIGIBILITY).rows.map { row ->
                    TissueCodContextLoadUnitEligibility(
                        loadUnitStableKey = row.required("loadUnitStableKey"),
                        jointComplexStableKey = row.required("jointComplexStableKey"),
                        displayNameKo = row.required("displayNameKo"),
                        bodyRegion = row.required("bodyRegion"),
                        eligible = row.boolean("codContextEligible"),
                        eligibilityReason = row.value("eligibilityReason"),
                        exclusionReason = row.value("exclusionReason"),
                        policyVersion = row.required("policyVersion"),
                        reviewStatus = row.required("reviewStatus")
                    )
                }
            val codContextModifierRules = table(TissueRcvAssetFiles.COD_CONTEXT_MODIFIER_RULES).rows
                .map { row ->
                    TissueCodContextModifierRule(
                        modifierRuleId = row.required("modifierRuleId"),
                        exerciseStableKey = row.required("exerciseStableKey"),
                        loadUnitStableKey = row.required("loadUnitStableKey"),
                        contextType = row.required("contextType"),
                        factor = row.double("factor"),
                        policyVersion = row.required("policyVersion"),
                        rationale = row.required("rationale"),
                        evidenceStatus = row.required("evidenceStatus"),
                        reviewStatus = row.required("reviewStatus")
                    )
                }

            val catalog = TissueRcvCatalog(
                authorityRows = authorityRows,
                exerciseStableKeys = exerciseStableKeys,
                exerciseNamesByStableKey = exerciseNamesByStableKey,
                protocols = protocols,
                protocolClasses = protocolClasses,
                diProfiles = diProfiles,
                curves = curves,
                routing = routing,
                jointComplexes = jointComplexes,
                loadUnits = loadUnits,
                educationalInfo = educationalInfo,
                codContextExerciseTiers = codContextExerciseTiers,
                codContextLoadUnitEligibility = codContextLoadUnitEligibility,
                codContextModifierRules = codContextModifierRules
            )
            TissueRcvAssetValidator.requireValid(catalog)
            return TissueRcvAssetRepository(catalog)
        }
    }
}

object TissueRcvAssetValidator {
    fun requireValid(catalog: TissueRcvCatalog) {
        require(catalog.exerciseStableKeys.size == 239) { "Expected 239 exercise stable keys." }
        require(catalog.authorityRows.size == 3507) { "Expected 3,507 authority score rows." }
        require(catalog.authorityRows.map { it.exerciseStableKey }.toSet().size == 238) {
            "Exactly one generic exercise must remain without an authority score."
        }
        require(catalog.protocols.size == 239) { "Expected 239 exercise protocol mappings." }
        require(catalog.protocolClasses.size == 50) { "Expected 50 protocol classes." }
        require(catalog.diProfiles.size == 13) { "Expected 13 D/I profiles." }
        require(catalog.curves.size == 21 && catalog.curves.values.sumOf { it.knots.size } == 114) {
            "Expected 21 curves and 114 knots."
        }
        require(catalog.routing.size == 7) { "Expected seven recovery routing classes." }
        require(catalog.loadUnits.size == 77) { "Expected 77 load units." }
        require(catalog.educationalInfo.values.count {
            it.scope == TissueEducationalInfoScope.JOINT_COMPLEX
        } == 15) { "Expected educational metadata for 15 joint complexes." }
        require(catalog.educationalInfo.values.count {
            it.scope == TissueEducationalInfoScope.LOAD_UNIT
        } == 77) { "Expected educational metadata for 77 load units." }
        require(catalog.educationalInfo.keys == catalog.jointComplexes.keys + catalog.loadUnits.keys) {
            "Educational metadata must cover production keys exactly."
        }
        require(catalog.educationalInfo.values.all {
            it.anatomicalLocationKo.isNotBlank() &&
                it.primaryFunctionsKo.isNotEmpty() &&
                it.commonLoadContextsKo.isNotEmpty()
        }) { "Educational metadata contains an incomplete entry." }
        require(catalog.educationalInfo.keys.none {
            it.contains("LEFT", ignoreCase = true) || it.contains("RIGHT", ignoreCase = true)
        }) { "Educational metadata must remain unsided." }
        require(catalog.unresolvedExerciseCount == 1) { "Expected one unresolved generic exercise." }
        require(catalog.protocols.keys == catalog.exerciseStableKeys) {
            "Protocol mappings must cover the canonical exercise stable keys exactly."
        }
        require(catalog.authorityRows.all { it.exerciseStableKey in catalog.exerciseStableKeys }) {
            "Authority score contains an unknown exercise stable key."
        }
        require(catalog.authorityRows.all { it.loadUnitStableKey in catalog.loadUnits }) {
            "Authority score contains an unknown load unit."
        }
        require(catalog.loadUnits.values.all { it.jointComplexStableKey in catalog.jointComplexes }) {
            "Load unit contains an unknown joint complex."
        }
        catalog.protocols.values.forEach { protocol ->
            require(protocol.defaultProtocolClass in catalog.protocolClasses)
            require(protocol.diProfileId in catalog.diProfiles)
            require(protocol.functionalCurveId in catalog.curves)
            require(protocol.jointProtectionCurveId in catalog.curves)
            require(protocol.fastMechanicalCurveId in catalog.curves)
        }
        catalog.protocolClasses.values.forEach { protocol ->
            require(protocol.diProfileId in catalog.diProfiles)
            require(protocol.functionalCurveId in catalog.curves)
            require(protocol.jointProtectionCurveId in catalog.curves)
            require(protocol.fastMechanicalCurveId in catalog.curves)
        }
        catalog.routing.values.forEach { routing ->
            routing.fastCurveId?.takeUnless { it == "REGION_DEPENDENT_CARTILAGE" }?.let {
                require(it in catalog.curves)
            }
            routing.biologicalCurveId?.let { require(it in catalog.curves) }
        }
        catalog.curves.values.forEach { curve ->
            require(curve.knots.size >= 2)
            require(curve.knots.zipWithNext().all { (left, right) -> left.elapsedHours < right.elapsedHours })
            require(curve.knots.all { it.value in 0.0..1.25 })
        }
        TissueCodContextValidator.requireValid(catalog)
    }
}

private fun Map<String, String>.value(name: String): String = get(name).orEmpty().trim()
private fun Map<String, String>.required(name: String): String =
    value(name).also { require(it.isNotBlank()) { "Missing required RCV field: $name" } }
private fun Map<String, String>.tokens(name: String): List<String> =
    value(name).split('|').map(String::trim).filter(String::isNotBlank)
private fun Map<String, String>.double(name: String): Double =
    required(name).toDouble().also { require(it.isFinite()) }
private fun Map<String, String>.optionalDouble(name: String): Double? =
    value(name).takeIf(String::isNotBlank)?.toDouble()?.also { require(it.isFinite()) }
private fun Map<String, String>.int(name: String): Int =
    required(name).toDouble().toInt()
private fun Map<String, String>.boolean(name: String): Boolean = when (required(name).lowercase()) {
    "true" -> true
    "false" -> false
    else -> error("Invalid Boolean RCV field: $name")
}
