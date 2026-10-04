package com.training.trackplanner.data

import org.json.JSONArray
import org.json.JSONObject

/** Deterministic, test-only projection of the C18 historical-placement regression rows. */
internal object C19CanonicalProgramLineageCensus {
    fun render(
        c18Json: String,
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, com.training.trackplanner.data.personalized.StimulusProductionGenerationResult?>>
    ): String {
        val source = JSONObject(c18Json)
        val rows = source.getJSONArray("placementRows").objects()
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        val grouped = rows.groupBy { it.getString("case") }.toSortedMap()
        val outputRows = mutableListOf<JSONObject>()
        val sourceRows = mutableListOf<JSONObject>()

        grouped.entries.forEachIndexed { caseIndex, (caseId, caseRows) ->
            val programId = 1900L + caseIndex
            val program = TrainingProgram(
                id = programId,
                stableKey = "c19-fixture-lineage:$caseId",
                name = "C18 explicit incumbent regression fixture",
                durationDays = 28,
                canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
                canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
            )
            val persistedRows = caseRows.mapIndexed { rowIndex, row ->
                val owner = row.getJSONObject("owner")
                val from = row.getJSONObject("from")
                TrainingProgramItem(
                    id = rowIndex + 1L,
                    programId = programId,
                    weekNumber = row.getInt("week"),
                    dayOfWeek = from.getInt("day"),
                    orderIndex = from.getInt("order"),
                    exerciseStableKey = owner.getString("stableKey"),
                    exerciseName = owner.getString("stableKey"),
                    category = "REGRESSION_FIXTURE",
                    selectionRole = owner.getString("selectionRole")
                )
            }
            val index = CanonicalIncumbentPlacementIndex.fromPersistedProgram(program, programId, persistedRows)
            sourceRows += JSONObject()
                .put("programId", programId)
                .put("lineageId", program.stableKey)
                .put("sourceType", "EXPLICIT_C18_TEST_FIXTURE")
                .put("builderProtocolVersion", program.canonicalBuilderProtocolVersion)
                .put("plannerRuntimeVersion", program.canonicalPlannerRuntimeVersion)
                .put("hasExactRoles", persistedRows.all { !it.selectionRole.isNullOrBlank() })
                .put("eligibleAsIncumbentSource", index.status == CanonicalIncumbentIndexStatus.AVAILABLE)
                .put("rejectionReason", JSONObject.NULL)

            val currentRows = caseRows.map { row ->
                val owner = row.getJSONObject("owner")
                val to = row.getJSONObject("to")
                ProgramSkeletonItem(
                    localId = "${caseId}-${owner.getString("stableKey")}-${owner.getString("selectionRole")}-${row.getInt("week")}",
                    weekNumber = row.getInt("week"),
                    dayOfWeek = to.getInt("day"),
                    orderIndex = to.getInt("order"),
                    exerciseStableKey = owner.getString("stableKey"),
                    exerciseName = owner.getString("stableKey"),
                    category = "REGRESSION_FIXTURE",
                    restSeconds = 0,
                    prescription = "unchanged",
                    setCount = 1,
                    reps = 1,
                    weightKg = 0.0,
                    seconds = 0,
                    selectionReason = "fixture",
                    weightSource = "fixture",
                    selectionRole = owner.getString("selectionRole")
                )
            }
            val feasibility = caseRows.associate { row ->
                val owner = row.getJSONObject("owner")
                val key = CanonicalIncumbentOwnerWeek(
                    CanonicalOwnerIdentity(owner.getString("stableKey"), owner.getString("selectionRole")),
                    row.getInt("week")
                )
                key to when (row.getString("priorPlacementClassification")) {
                    "PRIOR_PLACEMENT_VALID" -> CanonicalIncumbentFeasibility.HARD_VALID
                    "PRIOR_PLACEMENT_HARD_INVALID" -> CanonicalIncumbentFeasibility.HARD_INVALID
                    else -> CanonicalIncumbentFeasibility.UNRESOLVED
                }
            }
            val shadow = CanonicalIncumbentPlacementShadowEvaluator.evaluate(index, currentRows, feasibility)
            val shadowByKey = shadow.rows.associateBy { it.owner.stableKey to (it.owner.selectionRole to it.week) }
            caseRows.forEach { row ->
                val owner = row.getJSONObject("owner")
                val key = owner.getString("stableKey") to (owner.getString("selectionRole") to row.getInt("week"))
                val evaluated = shadowByKey[key]
                val from = row.getJSONObject("from")
                val to = row.getJSONObject("to")
                outputRows += JSONObject()
                    .put("case", caseId)
                    .put("week", row.getInt("week"))
                    .put("owner", JSONObject().put("stableKey", owner.getString("stableKey"))
                        .put("selectionRole", owner.getString("selectionRole")))
                    .put("C18Classification", row.getString("priorPlacementClassification"))
                    .put("lineageId", index.source?.lineageId?.value)
                    .put("incumbentAvailable", index.placement(
                        CanonicalOwnerIdentity(owner.getString("stableKey"), owner.getString("selectionRole")), row.getInt("week")
                    ) != null)
                    .put("shadowRecommendation", evaluated?.recommendation?.name ?: "NO_EXACT_CURRENT_OWNER")
                    .put("hardFeasibility", evaluated?.feasibility?.name ?: "UNRESOLVED")
                    .put("productionPlacement", JSONObject().put("day", to.getInt("day")).put("order", to.getInt("order")))
                    .put("shadowIncumbentPlacement", if (evaluated?.recommendation == CanonicalIncumbentRecommendation.PRESERVE_INCUMBENT)
                        JSONObject().put("day", from.getInt("day")).put("order", from.getInt("order")) else JSONObject.NULL)
                    .put("productionChanged", false)
            }
        }
        val sortedRows = outputRows.sortedWith(compareBy<JSONObject>(
            { it.getString("case") }, { it.getInt("week") },
            { it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") }
        ))
        val counts = sortedRows.groupingBy { it.getString("shadowRecommendation") }.eachCount()
        val routes = generated.groupingBy { it.second.routeDecision.selectedSource }.eachCount()
        val b7Codes = generated.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
            .groupingBy { it }.eachCount()
        return JSONObject()
            .put("schema", "c19-program-lineage-incumbent-placement-census-v1")
            .put("c18SourceFixture", JSONObject()
                .put("schema", source.getString("schema"))
                .put("sourceRows", rows.size)
                .put("testOnly", true)
                .put("controlUsedForExtraction", false))
            .put("lineageModel", JSONObject()
                .put("databaseRowIdRole", "PHYSICAL_ROW_ID_ONLY")
                .put("lineageIdSource", "TRAINING_PROGRAM_STABLE_KEY")
                .put("displayNameUsed", false)
                .put("calendarStartDateUsed", false)
                .put("selectionRoleInIdentity", true))
            .put("persistedProgramAudit", JSONArray(sourceRows))
            .put("incumbentExtraction", JSONObject()
                .put("exactIdentity", "stableKey+selectionRole+week")
                .put("placementCoordinates", "weekNumber+dayOfWeek+orderIndex")
                .put("ambiguousDuplicateOwnerWeeksOmitted", true)
                .put("legacyMissingRoleOmitted", true)
                .put("controlComparatorInput", false))
            .put("realCaseShadow", JSONArray(sortedRows))
            .put("routeSnapshot", JSONObject()
                .put("CONTROL", routes[com.training.trackplanner.data.personalized.StimulusProductionProgramSource.CONTROL] ?: 0)
                .put("STRENGTH_V1", routes[com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_STRENGTH_V1] ?: 0)
                .put("STRENGTH_CALIBRATION_V1", routes[com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1] ?: 0)
                .put("HYPERTROPHY", routes[com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_HYPERTROPHY_V1] ?: 0)
                .put("COMBINED", routes[com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1] ?: 0)
                .put("b7ProvenanceUnclosed", b7Codes["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                .put("b7TargetUnmet", b7Codes["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                .put("b7TargetRegressed", b7Codes["TARGET_REGRESSED"] ?: 0))
            .put("summary", JSONObject()
                .put("placementRows", sortedRows.size)
                .put("PRESERVE_INCUMBENT", counts[CanonicalIncumbentRecommendation.PRESERVE_INCUMBENT.name] ?: 0)
                .put("INCUMBENT_REJECTED_HARD_CONSTRAINT", counts[CanonicalIncumbentRecommendation.INCUMBENT_REJECTED_HARD_CONSTRAINT.name] ?: 0)
                .put("NO_DECISION_UNRESOLVED", counts[CanonicalIncumbentRecommendation.NO_DECISION_UNRESOLVED.name] ?: 0)
                .put("actualProductionPlacementChanged", false))
            .toString(2)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
