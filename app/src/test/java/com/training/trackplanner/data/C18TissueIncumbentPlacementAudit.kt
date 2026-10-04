package com.training.trackplanner.data

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

internal data class C18TissueAuthoritySnapshot(
    val stableKey: String,
    val canonicalName: String?,
    val canonicalMetadataRowExists: Boolean,
    val planningMetadataExists: Boolean,
    val planningEligibility: String?,
    val tissueIndexExists: Boolean,
    val tissueProtocolExists: Boolean,
    val protocolMappingStatus: String?,
    val tissueAuthorityRowCount: Int,
    val tissueDoseBasis: String?,
    val canonicalBodyWeightCoefficients: List<Double>,
    val exactDoseProfileExists: Boolean,
    val runtimeJoinExists: Boolean,
    val exactLoadUnits: List<String>,
    val relationSources: List<String>
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
    private data class IncumbentFixtureRow(
        val caseId: String,
        val stableKey: String,
        val selectionRole: String,
        val week: Int,
        val day: Int,
        val order: Int
    )

    private val tissueKeys = listOf(
        "ex_28347c1f",
        "barbell_romanian_deadlift",
        "dumbbell_chest_supported_row",
        "barbell_reverse_curl",
        "dumbbell_lying_triceps_extension"
    )
    private val negativeControls = listOf("barbell_back_squat", "cable_rear_delt_fly", "ex_5ca7133f")

    /** Capture only small exact-key facts before the corpus test builds its large audit graph. */
    fun captureTissueAuthoritySnapshot(canonical: CanonicalExerciseMetadataRepository): List<C18TissueAuthoritySnapshot> {
        val runtime = canonical.runtimeMetadataCatalog()
        val tissue = canonical.tissueRepository().catalog
        return (tissueKeys + negativeControls).distinct().sorted().map { key ->
            val identity = canonical.identity(key)
            val planning = runtime.resolveByStableKey(key)
            val protocol = tissue.protocols[key]
            val authority = tissue.authorityRows.filter { it.exerciseStableKey == key }
            val relationJoin = protocol != null && authority.isNotEmpty() && authority.all { row ->
                row.loadUnitStableKey in tissue.loadUnits &&
                    tissue.loadUnits[row.loadUnitStableKey]?.jointComplexStableKey in tissue.jointComplexes &&
                    tissue.loadUnits[row.loadUnitStableKey]?.recoveryClass in tissue.routing
            }
            C18TissueAuthoritySnapshot(
                stableKey = key,
                canonicalName = identity?.exerciseName,
                canonicalMetadataRowExists = identity != null,
                planningMetadataExists = planning != null,
                planningEligibility = planning?.planningEligibility,
                tissueIndexExists = key in tissue.exerciseStableKeys,
                tissueProtocolExists = protocol != null,
                protocolMappingStatus = protocol?.mappingStatus,
                tissueAuthorityRowCount = authority.size,
                tissueDoseBasis = authority.map { it.doseBasis }.distinct().singleOrNull(),
                canonicalBodyWeightCoefficients = authority.mapNotNull { it.bodyWeightCoefficient }.distinct().sorted(),
                exactDoseProfileExists = key in tissue.exerciseDoseProfiles,
                runtimeJoinExists = relationJoin,
                exactLoadUnits = authority.map { it.loadUnitStableKey }.distinct().sorted(),
                relationSources = authority.flatMap { it.sourceRefs }.distinct().sorted()
            )
        }
    }

    fun render(
        c17Census: String,
        tissueAuthoritySnapshot: List<C18TissueAuthoritySnapshot>,
        c17MergeSha: String,
        c18StartSha: String
    ): String {
        val input = JSONObject(c17Census)
        val tissueByKey = tissueAuthoritySnapshot.associateBy(C18TissueAuthoritySnapshot::stableKey)
        val casesInput = input.getJSONArray("cases").toJsonObjects()
        val rootDeltas = (if (input.has("deltas")) input.getJSONArray("deltas").toJsonObjects() else
            casesInput.flatMap { case -> case.getJSONArray("deltas").toJsonObjects().map { delta ->
                normalizeDelta(delta, case.getString("case"))
            } })
        val tissueAudit = JSONArray(tissueKeys.map { key ->
            tissueKeyAudit(key, requireNotNull(tissueByKey[key]), rootDeltas)
        })
        // These are fixed test fixtures representing historical persisted canonical plans.
        // They are deliberately independent of the CONTROL/EXPERIMENTAL comparison rows.
        val anchorsByCaseOwnerWeek = explicitIncumbentFixture().groupBy(IncumbentFixtureRow::caseId).entries
            .sortedBy { it.key }.flatMap { (caseId, fixtureRows) ->
                val shadowSource = C18IncumbentProgramSource(
                    C18IncumbentSourceType.CURRENT_PERSISTED_PROGRAM,
                    1800L + listOf("persona0_mixed", "persona0_reviewed", "persona3_reviewed", "persona4_mixed").indexOf(caseId),
                    "c18-explicit-test-incumbent:$caseId",
                    "3.52.0", "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1"
                )
                val shadowRows = fixtureRows.map { row ->
                    C18PersistedPlacementRow(row.stableKey, row.selectionRole, row.week, row.day, row.order)
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
        rootDeltas.forEach { delta ->
            val owner = delta.getJSONObject("owner")
            val from = delta.getJSONObject("from")
            val key = "${delta.getString("case")}|${owner.getString("stableKey")}|${owner.getString("selectionRole")}|${delta.getInt("week")}"
            val fixture = anchorsByCaseOwnerWeek[key]
            require(fixture != null && fixture.day == from.getInt("day") && fixture.order == from.getInt("order")) {
                "C18 explicit incumbent fixture does not match the audited prior placement for $key"
            }
        }
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
        val tissueRows = tissueAudit.toJsonObjects()
        val unresolvedCount = tissueRows.count { it.getString("projectionAfter").startsWith("UNRESOLVED_REQUIRED_") }
        val output = JSONObject()
            .put("schema", "c18-tissue-incumbent-placement-census-v1")
            .put("c17MergeSha", c17MergeSha)
            .put("c18StartSha", c18StartSha)
            .put("tissueKeys", tissueAudit)
            .put("negativeControlKeys", JSONArray(negativeControls.map { key ->
                val snapshot = requireNotNull(tissueByKey[key])
                JSONObject().put("stableKey", key)
                    .put("canonicalName", snapshot.canonicalName)
                    .put("metadataRow", snapshot.canonicalMetadataRowExists)
                    .put("runtimeRow", snapshot.planningMetadataExists)
                    .put("tissueProtocol", snapshot.protocolMappingStatus)
                    .put("authorityRows", snapshot.tissueAuthorityRowCount)
                    .put("exactLoadUnitsResolved", snapshot.runtimeJoinExists)
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
                .put("currentCorpusInputUnresolvedTissueKeys", unresolvedCount)
                .put("resolvedWithRequiredInput", tissueRows.count { it.getString("projectionWithRequiredInput").startsWith("RESOLVED_") })
                .put("exactBodyweightAdapterRepairs", tissueRows.count { it.getBoolean("repairApplied") })
                .put("originStageCounts", JSONObject(originCounts)))
        return output.toString(2)
    }

    /** Frozen test-only canonical incumbent inputs; no CONTROL object is read to construct them. */
    private fun explicitIncumbentFixture(): List<IncumbentFixtureRow> {
        data class PairPlacement(val caseId: String, val stableKey: String, val role: String, val day: Int, val order: Int)
        val owners = listOf(
            PairPlacement("persona0_mixed", "barbell_back_squat", "STYLE_HEAVY_LOWER_KNEE", 3, 1),
            PairPlacement("persona0_mixed", "cable_rear_delt_fly", "STYLE_HEAVY_HORIZONTAL_PULL", 1, 2),
            PairPlacement("persona0_mixed", "ex_28347c1f", "COVERAGE_CORE_DIRECT", 1, 3),
            PairPlacement("persona0_reviewed", "barbell_romanian_deadlift", "COVERAGE_POSTERIOR_CHAIN", 2, 1),
            PairPlacement("persona0_reviewed", "dumbbell_chest_supported_row", "COVERAGE_UPPER_PULL", 4, 1),
            PairPlacement("persona3_reviewed", "barbell_back_squat", "STYLE_HEAVY_LOWER_KNEE", 6, 1),
            PairPlacement("persona3_reviewed", "barbell_romanian_deadlift", "COVERAGE_POSTERIOR_CHAIN", 2, 1),
            PairPlacement("persona3_reviewed", "dumbbell_chest_supported_row", "COVERAGE_UPPER_PULL", 4, 1),
            PairPlacement("persona3_reviewed", "ex_28347c1f", "COVERAGE_CORE_DIRECT", 1, 1),
            PairPlacement("persona4_mixed", "barbell_back_squat", "STYLE_HEAVY_LOWER_KNEE", 1, 2),
            PairPlacement("persona4_mixed", "barbell_reverse_curl", "COVERAGE_ARMS_BICEPS", 3, 3),
            PairPlacement("persona4_mixed", "barbell_romanian_deadlift", "COVERAGE_POSTERIOR_CHAIN", 3, 1),
            PairPlacement("persona4_mixed", "cable_rear_delt_fly", "STYLE_HEAVY_HORIZONTAL_PULL", 3, 2),
            PairPlacement("persona4_mixed", "dumbbell_lying_triceps_extension", "COVERAGE_ARMS_TRICEPS", 3, 4),
            PairPlacement("persona4_mixed", "ex_28347c1f", "COVERAGE_CORE_DIRECT", 1, 3),
            PairPlacement("persona4_mixed", "ex_5ca7133f", "COVERAGE_CALVES", 5, 1)
        )
        return owners.flatMap { owner -> (1..2).map { week ->
            IncumbentFixtureRow(owner.caseId, owner.stableKey, owner.role, week, owner.day, owner.order)
        } }.sortedWith(compareBy(
            IncumbentFixtureRow::caseId, IncumbentFixtureRow::stableKey,
            IncumbentFixtureRow::selectionRole, IncumbentFixtureRow::week
        ))
    }

    private fun tissueKeyAudit(
        key: String,
        snapshot: C18TissueAuthoritySnapshot,
        deltas: List<JSONObject>
    ): JSONObject {
        val observed = deltas.filter { delta ->
            delta.getJSONObject("owner").getString("stableKey") == key &&
                delta.getJSONObject("individualCounterfactual").getJSONArray("violations").toJsonStrings()
                    .any { it.contains("unresolved=[$key]") }
        }
        val first = observed.firstOrNull()
        val before = first?.getJSONObject("before")
        val basis = snapshot.tissueDoseBasis
        val bodyweight = basis == "BODYWEIGHT_REPETITION"
        val rootCause = if (bodyweight) "PROJECTION_ADAPTER_OMISSION_FIXED; C17_CORPUS_ALSO_HAS_NO_BODYWEIGHT"
            else "REQUIRED_RECORDED_LOAD_INPUT_ABSENT"
        val positiveInputProjection = when {
            bodyweight -> "RESOLVED_WITH_BODYWEIGHT_AND_ZERO_ADDED_LOAD_AFTER_EXACT_COEFFICIENT_REPAIR"
            snapshot.tissueDoseBasis == "WEIGHTED_REPETITION" -> "RESOLVED_WITH_VALID_RECORDED_WEIGHTED_LOAD"
            else -> "NOT_VALIDATED"
        }
        val currentInputResult = when {
            observed.isEmpty() -> "NO_C17_UNRESOLVED_ROW"
            bodyweight -> "UNRESOLVED_REQUIRED_BODYWEIGHT_ABSENT"
            else -> "UNRESOLVED_REQUIRED_WEIGHTED_LOAD_ABSENT"
        }
        return JSONObject()
            .put("stableKey", key)
            .put("canonicalName", snapshot.canonicalName)
            .put("canonicalMetadataRowExists", snapshot.canonicalMetadataRowExists)
            .put("planningMetadataExists", snapshot.planningMetadataExists)
            .put("planningEligibility", snapshot.planningEligibility)
            .put("tissueIndexExists", snapshot.tissueIndexExists)
            .put("tissueProtocolExists", snapshot.tissueProtocolExists)
            .put("protocolMappingStatus", snapshot.protocolMappingStatus)
            .put("tissueAuthorityRowCount", snapshot.tissueAuthorityRowCount)
            .put("tissueDoseBasis", basis)
            .put("canonicalBodyWeightCoefficients", JSONArray(snapshot.canonicalBodyWeightCoefficients))
            .put("exactDoseProfileExists", snapshot.exactDoseProfileExists)
            .put("runtimeJoinExists", snapshot.runtimeJoinExists)
            .put("exactLoadUnits", JSONArray(snapshot.exactLoadUnits))
            .put("relationSources", JSONArray(snapshot.relationSources))
            .put("projectionBefore", if (observed.isNotEmpty()) "UNRESOLVED_NO_POSITIVE_EXPOSURE" else "NO_C17_UNRESOLVED_ROW")
            .put("rootCause", rootCause)
            .put("repairResult", if (bodyweight) "EXACT_CANONICAL_COEFFICIENT_CONSUMED_BY_RUNTIME" else "NO_METADATA_REPAIR_REQUIRED")
            .put("projectedWeightKg", before?.optDouble("weightKg") ?: JSONObject.NULL)
            .put("projectedLoadSource", before?.optString("loadSource").orEmpty())
            .put("c17ProjectionOccurrences", observed.size)
            .put("repairApplied", bodyweight)
            .put("projectionAfter", currentInputResult)
            .put("projectionWithRequiredInput", positiveInputProjection)
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
            .put("counterfactualDetails", individual.optJSONObject("details")?.let(::compactCounterfactualDetails) ?: JSONObject.NULL)
            .put("actualDisplacementEdges", directEdges)
            .put("actualDisplacementAuthorityProven", delta.getString("classification") == "NECESSARY_AUTHORIZED_DISPLACEMENT" && directEdges.length() > 0)
            .put("unsupportedPowerSensitivity", powerSensitivity ?: JSONObject.NULL)
            .put("c17Classification", delta.getString("classification"))
    }

    /** Retains exact gate inputs/results while excluding the repeated full tissue-unit snapshots. */
    private fun compactCounterfactualDetails(details: JSONObject): JSONObject {
        fun compactOfi(rows: JSONArray?): JSONArray = JSONArray(
            rows?.toJsonObjects()?.map { row ->
                JSONObject()
                    .put("day", row.optInt("day"))
                    .put("ofi", row.optInt("ofi"))
                    .put("axisScores", row.optJSONArray("axisScores") ?: JSONArray())
                    .put("cautionReasons", row.optJSONArray("cautionReasons") ?: JSONArray())
                    .put("feasible", row.optBoolean("feasible"))
            }.orEmpty()
        )
        fun compactTissue(value: JSONObject?): Any {
            if (value == null) return JSONObject.NULL
            val days = value.optJSONArray("days")?.toJsonObjects().orEmpty().map { day ->
                JSONObject()
                    .put("day", day.optInt("day"))
                    .put("date", day.optString("date"))
                    .put("feasible", day.optBoolean("feasible"))
                    .put("blockedUnits", day.optJSONArray("blockedUnits") ?: JSONArray())
                    .put("unresolvedKeys", day.optJSONArray("unresolvedKeys") ?: JSONArray())
            }
            return JSONObject()
                .put("diagnostic", value.optString("diagnostic"))
                .put("feasible", value.optBoolean("feasible"))
                .put("days", JSONArray(days))
        }
        val weeks = details.optJSONArray("weekConstraintStates")?.toJsonObjects().orEmpty().map { week ->
            JSONObject()
                .put("week", week.optInt("week"))
                .put("dayOfi", compactOfi(week.optJSONArray("dayOfi")))
                .put("baselineDayOfi", compactOfi(week.optJSONArray("baselineDayOfi")))
                .put("weekTissue", compactTissue(week.optJSONObject("weekTissue")))
                .put("baselineWeekTissue", compactTissue(week.optJSONObject("baselineWeekTissue")))
        }
        return JSONObject()
            .put("restoredOwnerRows", details.optJSONArray("restoredOwnerRows") ?: JSONArray())
            .put("omittedOwnerRowsForSensitivity", details.optJSONArray("omittedOwnerRowsForSensitivity") ?: JSONArray())
            .put("sessionSecondsByWeek", details.optJSONObject("sessionSecondsByWeek") ?: JSONObject())
            .put("sessionCapacitySeconds", details.optInt("sessionCapacitySeconds"))
            .put("weekConstraintStates", JSONArray(weeks))
            .put("primarySpacingKeys", details.optJSONArray("primarySpacingKeys") ?: JSONArray())
            .put("programProjectionErrors", details.optJSONArray("programProjectionErrors") ?: JSONArray())
            .put("violations", details.optJSONArray("violations") ?: JSONArray())
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
            .put("fullSharedOwnerCounterfactual", compactCounterfactualDetails(case.getJSONObject("fullSharedOwnerCounterfactual")))
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
        .put("reason", "Current storage has program-relative placement and stableKey, but no explicit role/source lineage; generation does not receive the existing program before building. C18 uses fixed typed historical-placement fixtures for shadow testing only; production reads no CONTROL/comparison source.")

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
