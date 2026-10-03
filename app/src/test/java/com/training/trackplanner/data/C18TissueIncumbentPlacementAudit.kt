package com.training.trackplanner.data

import com.training.trackplanner.analysis.tissue.TissueRcvCatalog
import org.json.JSONArray
import org.json.JSONObject

/** Audit-only classifications; none of these values are consumed by production routing. */
internal enum class C18PriorPlacementState {
    PRIOR_PLACEMENT_VALID,
    PRIOR_PLACEMENT_HARD_INVALID,
    STILL_UNRESOLVED
}

internal enum class C18IncumbentSourceType {
    CURRENT_PERSISTED_PROGRAM,
    PREVIOUS_ACCEPTED_CANONICAL_PLAN
}

internal data class C18CanonicalOwnerIdentity(val stableKey: String, val selectionRole: String)

internal data class C18IncumbentProgramSource(
    val sourceType: C18IncumbentSourceType,
    val programId: Long?,
    val programStableKey: String?,
    val protocolVersion: String?,
    val runtimeVersion: String?
)

internal data class C18PersistedPlacementRow(
    val stableKey: String,
    val selectionRole: String?,
    val week: Int,
    val day: Int,
    val order: Int
)

internal data class C18IncumbentAnchor(
    val owner: C18CanonicalOwnerIdentity,
    val week: Int,
    val day: Int,
    val order: Int,
    val sourceType: C18IncumbentSourceType,
    val programId: Long,
    val programStableKey: String,
    val protocolVersion: String,
    val runtimeVersion: String
)

internal enum class C18IncumbentIndexStatus {
    AVAILABLE,
    NO_SOURCE,
    SOURCE_IDENTITY_INCOMPLETE,
    SOURCE_VERSION_INCOMPATIBLE
}

internal data class C18IncumbentIndexResult(
    val status: C18IncumbentIndexStatus,
    val anchors: List<C18IncumbentAnchor>,
    val ambiguousOwnerWeeks: Set<Pair<C18CanonicalOwnerIdentity, Int>>,
    val omittedRowsWithoutExactRole: Int
)

internal enum class C18IncumbentRecommendation {
    KEEP_INCUMBENT,
    MOVE_INCUMBENT_HARD_INVALID,
    NO_ELIGIBLE_INCUMBENT
}

/**
 * Shadow-only input/index. It accepts only persisted program identity plus an exact owner role and
 * compatible source versions. CONTROL/comparison types are intentionally absent from this API.
 */
internal object C18CanonicalIncumbentPlacementShadow {
    fun index(
        source: C18IncumbentProgramSource?,
        rows: List<C18PersistedPlacementRow>,
        currentlySelectedOwners: Set<C18CanonicalOwnerIdentity>,
        removedOwners: Set<C18CanonicalOwnerIdentity> = emptySet(),
        roleReplacements: Set<Pair<C18CanonicalOwnerIdentity, C18CanonicalOwnerIdentity>> = emptySet(),
        expectedProtocolVersion: String,
        expectedRuntimeVersion: String
    ): C18IncumbentIndexResult {
        if (source == null) return C18IncumbentIndexResult(C18IncumbentIndexStatus.NO_SOURCE, emptyList(), emptySet(), 0)
        if (source.programId == null || source.programId <= 0 || source.programStableKey.isNullOrBlank()) {
            return C18IncumbentIndexResult(C18IncumbentIndexStatus.SOURCE_IDENTITY_INCOMPLETE, emptyList(), emptySet(), 0)
        }
        if (source.protocolVersion != expectedProtocolVersion || source.runtimeVersion != expectedRuntimeVersion) {
            return C18IncumbentIndexResult(C18IncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE, emptyList(), emptySet(), 0)
        }

        val candidates = rows.mapNotNull { row ->
            val role = row.selectionRole?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val owner = C18CanonicalOwnerIdentity(row.stableKey, role)
            if (owner !in currentlySelectedOwners || owner in removedOwners ||
                roleReplacements.any { (old, replacement) -> old == owner && replacement != owner }
            ) return@mapNotNull null
            C18IncumbentAnchor(owner, row.week, row.day, row.order, source.sourceType,
                source.programId, source.programStableKey, source.protocolVersion, source.runtimeVersion)
        }
        val ambiguous = candidates.groupBy { it.owner to it.week }.filterValues { it.size > 1 }.keys
        val anchors = candidates.filter { (it.owner to it.week) !in ambiguous }
            .sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }, { it.day }, { it.order }))
        return C18IncumbentIndexResult(
            status = C18IncumbentIndexStatus.AVAILABLE,
            anchors = anchors,
            ambiguousOwnerWeeks = ambiguous,
            omittedRowsWithoutExactRole = rows.count { it.selectionRole.isNullOrBlank() }
        )
    }

    /** Feasibility is supplied by existing hard-constraint projections; continuity never overrides it. */
    fun recommend(anchor: C18IncumbentAnchor?, priorPlacementHardFeasible: Boolean?): C18IncumbentRecommendation =
        when {
            anchor == null || priorPlacementHardFeasible == null -> C18IncumbentRecommendation.NO_ELIGIBLE_INCUMBENT
            priorPlacementHardFeasible -> C18IncumbentRecommendation.KEEP_INCUMBENT
            else -> C18IncumbentRecommendation.MOVE_INCUMBENT_HARD_INVALID
        }
}

/** Builds a deterministic diagnostic artifact from the real C17 counterfactual and exact assets. */
internal object C18TissueIncumbentPlacementCensus {
    private val tissueKeys = listOf(
        "ex_28347c1f",
        "barbell_romanian_deadlift",
        "dumbbell_chest_supported_row",
        "barbell_reverse_curl",
        "dumbbell_lying_triceps_extension"
    )
    private val negativeControls = listOf("barbell_back_squat", "cable_rear_delt_fly", "ex_5ca7133f")

    fun render(
        c17Census: String,
        canonical: CanonicalExerciseMetadataRepository,
        c17MergeSha: String,
        c18StartSha: String
    ): String {
        val input = JSONObject(c17Census)
        val runtime = canonical.runtimeMetadataCatalog()
        val tissue = canonical.tissueRepository().catalog
        val casesInput = input.getJSONArray("cases").toJsonObjects()
        val rootDeltas = (if (input.has("deltas")) input.getJSONArray("deltas").toJsonObjects() else
            casesInput.flatMap { case -> case.getJSONArray("deltas").toJsonObjects().map { delta ->
                normalizeDelta(delta, case.getString("case"))
            } })
        val tissueAudit = JSONArray(tissueKeys.map { key -> tissueKeyAudit(key, canonical, runtime, tissue, rootDeltas) })
        // These are explicit test fixtures only, constructed from the audit's pre-delta placement.
        // Each case is a separate program; the production generator never reads comparator rows.
        val anchorsByCaseOwnerWeek = rootDeltas.groupBy { it.getString("case") }.entries
            .sortedBy { it.key }.flatMap { (caseId, caseDeltas) ->
                val shadowSource = C18IncumbentProgramSource(
                    C18IncumbentSourceType.CURRENT_PERSISTED_PROGRAM,
                    caseDeltas.first().getString("case").hashCode().toLong().and(0x7fffffff).coerceAtLeast(1L),
                    "c18-explicit-test-incumbent:$caseId",
                    "3.52.0", "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1"
                )
                val shadowRows = caseDeltas.map { delta ->
                    val owner = delta.getJSONObject("owner")
                    val from = delta.getJSONObject("from")
                    C18PersistedPlacementRow(owner.getString("stableKey"), owner.getString("selectionRole"),
                        delta.getInt("week"), from.getInt("day"), from.getInt("order"))
                }
                val shadowOwners = shadowRows.mapTo(linkedSetOf()) {
                    C18CanonicalOwnerIdentity(it.stableKey, requireNotNull(it.selectionRole))
                }
                val anchors = C18CanonicalIncumbentPlacementShadow.index(
                    shadowSource, shadowRows, shadowOwners,
                    expectedProtocolVersion = "3.52.0",
                    expectedRuntimeVersion = "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1"
                ).anchors
                anchors.map { anchor ->
                    "${caseId}|${anchor.owner.stableKey}|${anchor.owner.selectionRole}|${anchor.week}" to anchor
                }
            }.toMap()
        val rows = JSONArray(rootDeltas.sortedWith(compareBy<JSONObject>(
            { it.getString("case") }, { it.getInt("week") }, { it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") }
        )).map { delta ->
            val owner = delta.getJSONObject("owner")
            val identity = C18CanonicalOwnerIdentity(owner.getString("stableKey"), owner.getString("selectionRole"))
            val anchorKey = "${delta.getString("case")}|${identity.stableKey}|${identity.selectionRole}|${delta.getInt("week")}"
            placementRow(delta, anchorsByCaseOwnerWeek[anchorKey])
        })
        val cases = casesInput.map { case -> caseSummary(case, rootDeltas) }
        val classifications = rows.toJsonObjects().groupingBy { it.getString("priorPlacementClassification") }.eachCount()
        val originCounts = rootDeltas.groupingBy { it.getJSONObject("origin").getString("stage") }.eachCount().toSortedMap()
        val unresolvedCount = tissueAudit.toJsonObjects().count { it.getString("projectionAfter") == "UNRESOLVED_LOAD_INPUT" }
        val output = JSONObject()
            .put("schema", "c18-tissue-incumbent-placement-census-v1")
            .put("c17MergeSha", c17MergeSha)
            .put("c18StartSha", c18StartSha)
            .put("tissueKeys", tissueAudit)
            .put("negativeControlKeys", JSONArray(negativeControls.map { key ->
                val identity = canonical.identity(key)
                val protocol = tissue.protocols[key]
                val authorities = tissue.authorityRows.filter { it.exerciseStableKey == key }
                JSONObject().put("stableKey", key)
                    .put("canonicalName", identity?.exerciseName)
                    .put("metadataRow", identity != null)
                    .put("runtimeRow", runtime.resolveByStableKey(key) != null)
                    .put("tissueProtocol", protocol?.mappingStatus)
                    .put("authorityRows", authorities.size)
                    .put("exactLoadUnitsResolved", authorities.all { it.loadUnitStableKey in tissue.loadUnits })
            }))
            .put("incumbentSourceAudit", incumbentSourceAudit())
            .put("placementRows", rows)
            .put("caseSummaries", JSONArray(cases))
            .put("summary", JSONObject()
                .put("placementDeltas", rows.length())
                .put("uniqueCaseOwnerRolePairs", rootDeltas.map { delta ->
                    "${delta.getString("case")}|${delta.getJSONObject("owner").getString("stableKey")}|${delta.getJSONObject("owner").getString("selectionRole")}"
                }.distinct().size)
                .put("c17UnnecessaryDrift", rootDeltas.count { it.getString("classification") == "UNNECESSARY_PLACEMENT_DRIFT" })
                .put("c17Unresolved", rootDeltas.count { it.getString("classification") == "UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT" })
                .put("priorPlacementValid", classifications[C18PriorPlacementState.PRIOR_PLACEMENT_VALID.name] ?: 0)
                .put("priorPlacementHardInvalid", classifications[C18PriorPlacementState.PRIOR_PLACEMENT_HARD_INVALID.name] ?: 0)
                .put("stillUnresolved", classifications[C18PriorPlacementState.STILL_UNRESOLVED.name] ?: 0)
                .put("actualDisplacementAuthorityProven", rows.toJsonObjects().count { it.getBoolean("actualDisplacementAuthorityProven") })
                .put("syntheticIncumbentShadowKeep", rows.toJsonObjects().count { it.getString("incumbentShadowRecommendation") == C18IncumbentRecommendation.KEEP_INCUMBENT.name })
                .put("syntheticIncumbentShadowMove", rows.toJsonObjects().count { it.getString("incumbentShadowRecommendation") == C18IncumbentRecommendation.MOVE_INCUMBENT_HARD_INVALID.name })
                .put("syntheticIncumbentShadowNoDecision", rows.toJsonObjects().count { it.getString("incumbentShadowRecommendation") == C18IncumbentRecommendation.NO_ELIGIBLE_INCUMBENT.name })
                .put("unresolvedTissueKeys", unresolvedCount)
                .put("originStageCounts", JSONObject(originCounts)))
        return output.toString(2)
    }

    private fun tissueKeyAudit(
        key: String,
        canonical: CanonicalExerciseMetadataRepository,
        runtime: RuntimeExerciseMetadataCatalog,
        tissue: TissueRcvCatalog,
        deltas: List<JSONObject>
    ): JSONObject {
        val identity = canonical.identity(key)
        val planning = runtime.resolveByStableKey(key)
        val protocol = tissue.protocols[key]
        val authority = tissue.authorityRows.filter { it.exerciseStableKey == key }
        val indexPresent = key in tissue.exerciseStableKeys
        val relationJoin = protocol != null && authority.isNotEmpty() && authority.all { row ->
            row.loadUnitStableKey in tissue.loadUnits &&
                tissue.loadUnits[row.loadUnitStableKey]?.jointComplexStableKey in tissue.jointComplexes &&
                tissue.loadUnits[row.loadUnitStableKey]?.recoveryClass in tissue.routing
        }
        val observed = deltas.filter { delta ->
            delta.getJSONObject("owner").getString("stableKey") == key &&
                delta.getJSONObject("individualCounterfactual").getJSONArray("violations").toJsonStrings()
                    .any { it.contains("unresolved=[$key]") }
        }
        val first = observed.firstOrNull()
        val before = first?.getJSONObject("before")
        val basis = authority.map { it.doseBasis }.distinct().singleOrNull()
        val bodyweight = basis == "BODYWEIGHT_REPETITION"
        val rootCause = if (bodyweight) "CANONICAL_BODYWEIGHT_COEFFICIENT_EXISTS_BUT_RUNTIME_DOSE_JOIN_OMITS_IT; C17_CORPUS_ALSO_HAS_NO_BODYWEIGHT"
            else "WEIGHTED_DOSE_HAS_ONLY_PROVISIONAL_NO_INVENTED_LOAD_ZERO"
        return JSONObject()
            .put("stableKey", key)
            .put("canonicalName", identity?.exerciseName)
            .put("canonicalMetadataRowExists", identity != null)
            .put("planningMetadataExists", planning != null)
            .put("planningEligibility", planning?.planningEligibility)
            .put("tissueIndexExists", indexPresent)
            .put("tissueProtocolExists", protocol != null)
            .put("protocolMappingStatus", protocol?.mappingStatus)
            .put("tissueAuthorityRowCount", authority.size)
            .put("tissueDoseBasis", basis)
            .put("canonicalBodyWeightCoefficients", JSONArray(authority.mapNotNull { it.bodyWeightCoefficient }.distinct().sorted()))
            .put("exactDoseProfileExists", key in tissue.exerciseDoseProfiles)
            .put("runtimeJoinExists", relationJoin)
            .put("exactLoadUnits", JSONArray(authority.map { it.loadUnitStableKey }.distinct().sorted()))
            .put("relationSources", JSONArray(authority.flatMap { it.sourceRefs }.distinct().sorted()))
            .put("projectionBefore", if (observed.isNotEmpty()) "UNRESOLVED_NO_POSITIVE_EXPOSURE" else "NO_C17_UNRESOLVED_ROW")
            .put("rootCause", rootCause)
            .put("projectedWeightKg", before?.optDouble("weightKg") ?: JSONObject.NULL)
            .put("projectedLoadSource", before?.optString("loadSource").orEmpty())
            .put("c17ProjectionOccurrences", observed.size)
            .put("repairApplied", false)
            .put("projectionAfter", if (observed.isNotEmpty()) "UNRESOLVED_LOAD_INPUT" else "NOT_APPLICABLE")
            .put("safeToTreatAsZeroLoad", false)
    }

    private fun placementRow(delta: JSONObject, anchor: C18IncumbentAnchor?): JSONObject {
        val individual = delta.getJSONObject("individualCounterfactual")
        val violations = individual.getJSONArray("violations").toJsonStrings()
        val hasKnownHardViolation = violations.any { it.startsWith("NEW_DAY_OFI_OR_AXIS_GATE:") ||
            it.startsWith("SESSION_TIME_CAP:") || it.startsWith("DAY_NOT_IN_USER_SCHEDULE:") ||
            it.startsWith("PROGRAM_PROJECTION:") || it == "PRIMARY_ANCHOR_CALENDAR_SPACING" }
        val hasUnresolvedTissue = violations.any { it.startsWith("NEW_WEEK_TISSUE_GATE:") && it.contains(":unresolved=[") }
        val priorState = when {
            hasKnownHardViolation -> C18PriorPlacementState.PRIOR_PLACEMENT_HARD_INVALID
            hasUnresolvedTissue -> C18PriorPlacementState.STILL_UNRESOLVED
            violations.isEmpty() -> C18PriorPlacementState.PRIOR_PLACEMENT_VALID
            else -> C18PriorPlacementState.STILL_UNRESOLVED
        }
        val owner = delta.getJSONObject("owner")
        val from = delta.getJSONObject("from")
        val to = delta.getJSONObject("to")
        val powerSensitivity = delta.getJSONArray("triggerSensitivity").toJsonObjects().firstOrNull {
            it.getJSONObject("owner").getString("stableKey") == "ex_314df428" &&
                it.getJSONObject("owner").getString("selectionRole").contains("POWER")
        }
        val directEdges = delta.getJSONArray("directCausalDisplacementEdges")
        val recommendation = C18CanonicalIncumbentPlacementShadow.recommend(anchor, when {
            priorState == C18PriorPlacementState.PRIOR_PLACEMENT_VALID -> true
            priorState == C18PriorPlacementState.PRIOR_PLACEMENT_HARD_INVALID -> false
            else -> null
        })
        return JSONObject()
            .put("case", delta.getString("case"))
            .put("week", delta.getInt("week"))
            .put("owner", owner)
            .put("from", JSONObject().put("day", from.getInt("day")).put("order", from.getInt("order")))
            .put("to", JSONObject().put("day", to.getInt("day")).put("order", to.getInt("order")))
            .put("materialParity", delta.getBoolean("materialParity"))
            .put("originStage", delta.getJSONObject("origin").getString("stage"))
            .put("originAction", delta.getJSONObject("origin").getString("action"))
            .put("originCause", delta.getJSONObject("origin").getString("cause"))
            .put("originTriggerOwner", delta.getJSONObject("origin").opt("triggerOwner"))
            .put("originTriggerRole", delta.getJSONObject("origin").opt("triggerSelectionRole"))
            .put("originTargetIds", delta.getJSONObject("origin").optJSONArray("triggerTargetIds") ?: JSONArray())
            .put("originAuthorizedDemandIds", delta.getJSONObject("origin").optJSONArray("authorizedDemandIds") ?: JSONArray())
            .put("priorPlacementClassification", priorState.name)
            .put("incumbentAnchorAvailable", anchor != null)
            .put("incumbentShadowRecommendation", recommendation.name)
            .put("individualCounterfactualValid", individual.getBoolean("valid"))
            .put("counterfactualViolations", JSONArray(violations))
            .put("counterfactualDetails", individual.optJSONObject("details") ?: JSONObject.NULL)
            .put("actualDisplacementEdges", directEdges)
            .put("actualDisplacementAuthorityProven", delta.getString("classification") == "NECESSARY_AUTHORIZED_DISPLACEMENT" && directEdges.length() > 0)
            .put("unsupportedPowerSensitivity", powerSensitivity ?: JSONObject.NULL)
            .put("c17Classification", delta.getString("classification"))
    }

    private fun caseSummary(case: JSONObject, deltas: List<JSONObject>): JSONObject {
        val id = case.getString("case")
        val caseDeltas = deltas.filter { it.getString("case") == id }
        val powerRows = caseDeltas.filter { it.getJSONObject("owner").getString("stableKey") == "ex_314df428" }
        val rdlRows = caseDeltas.filter { it.getJSONObject("owner").getString("stableKey") == "barbell_romanian_deadlift" }
        val rdlPowerSensitivity = rdlRows.filter { delta ->
            delta.getJSONArray("triggerSensitivity").toJsonObjects().any { trigger ->
                trigger.getJSONObject("owner").getString("stableKey") == "ex_314df428"
            }
        }.map { delta ->
            val sensitivity = delta.getJSONArray("triggerSensitivity").toJsonObjects().first {
                it.getJSONObject("owner").getString("stableKey") == "ex_314df428"
            }
            val actualViolations = delta.getJSONObject("individualCounterfactual").getJSONArray("violations")
            val withoutPowerViolations = sensitivity.getJSONArray("violationsWithoutTrigger")
            JSONObject().put("week", delta.getInt("week"))
                .put("actualRestorationViolations", actualViolations)
                .put("withoutPowerSensitivityViolations", withoutPowerViolations)
                .put("powerOmissionClearsOfi", actualViolations.toJsonStrings().any { it.startsWith("NEW_DAY_OFI_OR_AXIS_GATE:") } &&
                    withoutPowerViolations.toJsonStrings().none { it.startsWith("NEW_DAY_OFI_OR_AXIS_GATE:") })
                .put("originCausalEdgeProven", false)
        }
        return JSONObject()
            .put("case", id)
            .put("route", case.getString("route"))
            .put("placementDeltaCount", case.getInt("placementDeltaCount"))
            .put("fullSharedOwnerCounterfactualValid", case.getBoolean("fullSharedOwnerCounterfactualValid"))
            .put("fullSharedOwnerCounterfactual", case.getJSONObject("fullSharedOwnerCounterfactual"))
            .put("sharedOwnerPlacementMetrics", case.getJSONObject("sharedOwnerPlacementMetrics"))
            .put("priorPlacementValidRows", caseDeltas.count { row ->
                row.placementState() == C18PriorPlacementState.PRIOR_PLACEMENT_VALID.name
            })
            .put("priorPlacementHardInvalidRows", caseDeltas.count { row ->
                row.placementState() == C18PriorPlacementState.PRIOR_PLACEMENT_HARD_INVALID.name
            })
            .put("stillUnresolvedRows", caseDeltas.count { row ->
                row.placementState() == C18PriorPlacementState.STILL_UNRESOLVED.name
            })
            .put("powerAdditionRows", powerRows.size)
            .put("rdlPowerSensitivityRows", JSONArray(rdlPowerSensitivity))
    }

    private fun JSONObject.placementState(): String {
        val violations = getJSONObject("individualCounterfactual").getJSONArray("violations").toJsonStrings()
        return when {
            violations.any { it.startsWith("NEW_DAY_OFI_OR_AXIS_GATE:") || it.startsWith("SESSION_TIME_CAP:") ||
                it.startsWith("DAY_NOT_IN_USER_SCHEDULE:") || it.startsWith("PROGRAM_PROJECTION:") ||
                it == "PRIMARY_ANCHOR_CALENDAR_SPACING" } -> C18PriorPlacementState.PRIOR_PLACEMENT_HARD_INVALID.name
            violations.any { it.startsWith("NEW_WEEK_TISSUE_GATE:") && it.contains(":unresolved=[") } -> C18PriorPlacementState.STILL_UNRESOLVED.name
            violations.isEmpty() -> C18PriorPlacementState.PRIOR_PLACEMENT_VALID.name
            else -> C18PriorPlacementState.STILL_UNRESOLVED.name
        }
    }

    private fun incumbentSourceAudit() = JSONObject()
        .put("persistedProgramIdentity", JSONArray(listOf("TrainingProgram.id", "TrainingProgram.stableKey")))
        .put("persistedPlacementFields", JSONArray(listOf("TrainingProgramItem.weekNumber", "dayOfWeek", "orderIndex")))
        .put("persistedExerciseIdentity", "TrainingProgramItem.exerciseStableKey")
        .put("selectionRolePersistedExplicitly", false)
        .put("overloadedTrainingSlotMayContainRole", true)
        .put("trainingSlotIsReliableExactRoleContract", false)
        .put("programProtocolOrRuntimePersisted", false)
        .put("generationReceivesExistingProgramBeforeBuild", false)
        .put("existingProgramIdUsedDuringSaveAfterBuild", true)
        .put("manualPlacementDistinguishedFromGenerated", false)
        .put("dateShiftAffectsProgramRelativeWeekDayOrder", false)
        .put("newPersistenceRequiredForShadow", false)
        .put("exactIncumbentAvailableFromCurrentProductionPath", false)
        .put("reason", "Current storage has program-relative placement and stableKey, but no explicit role/source lineage; generation does not receive the existing program before building. C18 constructs separate typed synthetic incumbent inputs from the audited pre-delta placement rows for shadow testing only; production reads no CONTROL/comparison source.")

    /** Adapts C17's in-test form and the checked-in human census form into one exact C18 ledger row. */
    private fun normalizeDelta(row: JSONObject, caseId: String): JSONObject {
        if (row.has("individualCounterfactual")) return row
        val firstEvent = row.optJSONObject("firstDivergenceEvent") ?: JSONObject()
        val individual = JSONObject()
            .put("valid", row.optBoolean("individualCounterfactualValid"))
            .put("violations", row.optJSONArray("individualCounterfactualViolations") ?: JSONArray())
            .put("details", row.optJSONObject("counterfactualConstraintState") ?: JSONObject.NULL)
        val origin = JSONObject()
            .put("stage", row.optString("originStage", "UNPROVEN"))
            .put("action", row.optString("originAction", "UNPROVEN"))
            .put("cause", row.optString("originCause", "UNPROVEN"))
            .put("triggerOwner", firstEvent.opt("triggerOwner"))
            .put("triggerSelectionRole", firstEvent.opt("triggerSelectionRole"))
            .put("triggerTargetIds", row.optJSONArray("originTargetIds") ?: JSONArray())
            .put("authorizedDemandIds", row.optJSONArray("originAuthorizedDemandIds") ?: JSONArray())
        return JSONObject()
            .put("case", caseId)
            .put("week", row.getInt("week"))
            .put("owner", row.getJSONObject("owner"))
            .put("from", JSONObject().put("day", row.getInt("fromDay")).put("order", row.getInt("fromOrder")))
            .put("to", JSONObject().put("day", row.getInt("toDay")).put("order", row.getInt("toOrder")))
            .put("before", row.optJSONObject("beforeRow") ?: JSONObject())
            .put("after", row.optJSONObject("afterRow") ?: JSONObject())
            .put("individualCounterfactual", individual)
            .put("origin", origin)
            .put("directCausalDisplacementEdges", row.optJSONArray("directCausalDisplacementEdges") ?: JSONArray())
            .put("triggerSensitivity", row.optJSONArray("candidateCanonicalTriggerSensitivity") ?: JSONArray())
            .put("materialParity", row.optBoolean("materialParity"))
            .put("classification", row.optString("classification", "UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT"))
    }

    private fun JSONArray.toJsonObjects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun JSONArray.toJsonStrings(): List<String> = (0 until length()).map(::getString)
}
