package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSkeletonItem
import org.json.JSONArray
import org.json.JSONObject

/** Provenance distinguishes a bounded product decision from a universal training claim. */
enum class TaskProtocolPolicyProvenance { USER_APPROVED_PROJECT_POLICY }

enum class TaskProtocolB6Status { AUTHORIZED_APPROVED_TASK_PROTOCOL }
enum class TaskProtocolFrequencyStatus { SATISFIED, SHORTFALL, AMBIGUOUS_MATERIALIZATION }

enum class TaskCreditSemantics { NON_ADDITIVE }
enum class TaskProtocolActivitySemantics { DRILL }

data class TaskProtocolWeeklyFrequencyOutcome(
    val protocolId: String,
    val stableKey: String,
    val selectionRole: String,
    val week: Int,
    val requestedExposures: Int,
    val placedExposures: Int,
    val shortfall: Int,
    val status: TaskProtocolFrequencyStatus,
    val authority: TaskProtocolPolicyProvenance = TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY,
    val reasonCode: String
)

data class ApprovedTaskProtocolDefinition(
    val protocolId: String,
    val stableKey: String,
    val primaryTask: CanonicalTaskTarget,
    val selectionRole: String,
    val authorizedTasks: Set<CanonicalTaskTarget>,
    val shape: TaskPrescriptionShape,
    val activitySemantics: TaskProtocolActivitySemantics = TaskProtocolActivitySemantics.DRILL,
    val weeklyExposures: Int = 2,
    val creditSemantics: TaskCreditSemantics = TaskCreditSemantics.NON_ADDITIVE,
    val provenance: TaskProtocolPolicyProvenance = TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY,
    val policyVersion: String = "C24_USER_APPROVED_BADMINTON_TASK_POLICY_V1"
) {
    init {
        require(protocolId.isNotBlank() && stableKey.isNotBlank() && selectionRole.isNotBlank())
        require(primaryTask in authorizedTasks && authorizedTasks.isNotEmpty())
        require(weeklyExposures == 2)
        require(shape.targetRpe == null)
        require(shape.loadMode == TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD)
        require(activitySemantics == TaskProtocolActivitySemantics.DRILL)
    }
}

/** Single source of truth for the three exact, user-approved C24 protocols. */
internal object ApprovedBadmintonTaskProtocols {
    private const val SIX_CORNER = "BADMINTON_SIX_CORNER_FOOTWORK_V1"
    private const val LATERAL_SHUTTLE = "BADMINTON_LATERAL_SHUTTLE_LUNGE_V1"
    private const val SPLIT_STEP = "BADMINTON_SPLIT_STEP_REACTION_V1"

    val definitions: List<ApprovedTaskProtocolDefinition> = listOf(
        ApprovedTaskProtocolDefinition(
            protocolId = SIX_CORNER,
            stableKey = "ex_33841b88",
            primaryTask = CanonicalTaskTarget.ACCELERATION,
            selectionRole = "CANONICAL_STIMULUS_TASK_ACCELERATION",
            authorizedTasks = setOf(CanonicalTaskTarget.ACCELERATION, CanonicalTaskTarget.DECELERATION,
                CanonicalTaskTarget.FOOTWORK, CanonicalTaskTarget.REACTION),
            shape = TaskPrescriptionShape(
                mode = TaskPrescriptionMode.DURATION_RANGE_SECONDS, setCount = 3,
                minSeconds = 10, maxSeconds = 20, restSeconds = 60,
                loadMode = TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD,
                activityKind = null
            )
        ),
        ApprovedTaskProtocolDefinition(
            protocolId = LATERAL_SHUTTLE,
            stableKey = "ex_421ba24b",
            primaryTask = CanonicalTaskTarget.LUNGE_REACH,
            selectionRole = "CANONICAL_STIMULUS_TASK_LUNGE_REACH",
            authorizedTasks = setOf(CanonicalTaskTarget.LUNGE_REACH, CanonicalTaskTarget.DECELERATION),
            shape = TaskPrescriptionShape(
                mode = TaskPrescriptionMode.REPETITIONS_PER_SIDE, setCount = 3, reps = 5,
                laterality = TaskLateralitySemantics.PER_SIDE, restSeconds = 75,
                loadMode = TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD,
                activityKind = null
            )
        ),
        ApprovedTaskProtocolDefinition(
            protocolId = SPLIT_STEP,
            stableKey = "ex_8e69fc74",
            primaryTask = CanonicalTaskTarget.REACTION,
            selectionRole = "CANONICAL_STIMULUS_TASK_REACTION",
            authorizedTasks = setOf(CanonicalTaskTarget.REACTION),
            shape = TaskPrescriptionShape(
                mode = TaskPrescriptionMode.DURATION_RANGE_SECONDS, setCount = 3,
                minSeconds = 10, maxSeconds = 20, restSeconds = 60,
                loadMode = TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD,
                activityKind = null
            )
        )
    )

    fun exact(stableKey: String, selectionRole: String, primaryTask: CanonicalTaskTarget): ApprovedTaskProtocolDefinition? =
        definitions.singleOrNull { it.stableKey == stableKey && it.selectionRole == selectionRole && it.primaryTask == primaryTask }
}

/** The downstream exact grant: B4 remains direction-only; this does not rewrite B4 history. */
data class TaskProtocolB6Authorization(
    val status: TaskProtocolB6Status,
    val definition: ApprovedTaskProtocolDefinition,
    /** Exact current canonical activity domain, persisted independently of display text. */
    val materializationActivityKind: PlannedActivityKind,
    val attributedTasks: Set<CanonicalTaskTarget>,
    val transferEvidence: Map<CanonicalTaskTarget, BadmintonObjectiveTransferLevel>,
    val reasonCode: String = "USER_APPROVED_EXACT_TASK_PROTOCOL_AUTHORIZED"
) {
    init {
        require(attributedTasks.isNotEmpty() && attributedTasks.all { it in definition.authorizedTasks })
        require(attributedTasks.all { transferEvidence[it] == BadmintonObjectiveTransferLevel.DIRECT })
        require(materializationActivityKind in setOf(
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        ))
    }
}

/** Persisted per physical protocol occurrence; one item is one exposure and task credit is capped. */
internal data class TaskProtocolExposureMetadata(
    val authorization: TaskProtocolB6Authorization,
    val exposureIndex: Int
) {
    init { require(exposureIndex in 1..authorization.definition.weeklyExposures) }

    fun toJsonString(): String = toJson().toString()

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("status", authorization.status.name)
        .put("activitySemantics", authorization.definition.activitySemantics.name)
        .put("materializationActivityKind", authorization.materializationActivityKind.name)
        .put("protocolId", authorization.definition.protocolId)
        .put("policyVersion", authorization.definition.policyVersion)
        .put("policyProvenance", authorization.definition.provenance.name)
        .put("stableKey", authorization.definition.stableKey)
        .put("selectionRole", authorization.definition.selectionRole)
        .put("primaryTask", authorization.definition.primaryTask.name)
        .put("authorizedTasks", JSONArray(authorization.attributedTasks.map { it.name }.sorted()))
        .put("transferEvidence", JSONObject().apply {
            authorization.transferEvidence.toSortedMap(compareBy { it.name }).forEach { (task, level) ->
                put(task.name, level.name)
            }
        })
        .put("weeklyExposures", authorization.definition.weeklyExposures)
        .put("creditSemantics", authorization.definition.creditSemantics.name)
        .put("exposureIndex", exposureIndex)
        .put("shape", authorization.definition.shape.toJson())

    companion object {
        fun fromJsonString(value: String): TaskProtocolExposureMetadata {
            val json = JSONObject(value)
            require(json.getInt("schemaVersion") == 1)
            val protocolId = json.getString("protocolId")
            val definition = ApprovedBadmintonTaskProtocols.definitions.singleOrNull { it.protocolId == protocolId }
                ?: error("UNKNOWN_APPROVED_TASK_PROTOCOL")
            require(json.getString("stableKey") == definition.stableKey)
            require(json.getString("selectionRole") == definition.selectionRole)
            require(json.getString("policyVersion") == definition.policyVersion)
            require(json.getString("policyProvenance") == definition.provenance.name)
            require(json.getString("primaryTask") == definition.primaryTask.name)
            require(json.getString("activitySemantics") == definition.activitySemantics.name)
            require(json.getInt("weeklyExposures") == definition.weeklyExposures)
            require(json.getString("creditSemantics") == definition.creditSemantics.name)
            require(TaskPrescriptionShape.fromJson(json.getJSONObject("shape")) == definition.shape)
            val tasks = json.getJSONArray("authorizedTasks").let { array ->
                (0 until array.length()).map { CanonicalTaskTarget.valueOf(array.getString(it)) }.toSet()
            }
            require(tasks.isNotEmpty() && tasks.all { it in definition.authorizedTasks })
            val transferEvidence = json.getJSONObject("transferEvidence").let { evidence ->
                tasks.associateWith { task ->
                    BadmintonObjectiveTransferLevel.valueOf(evidence.getString(task.name))
                }
            }
            require(transferEvidence.values.all { it == BadmintonObjectiveTransferLevel.DIRECT })
            val authorization = TaskProtocolB6Authorization(
                status = TaskProtocolB6Status.valueOf(json.getString("status")),
                definition = definition,
                materializationActivityKind = PlannedActivityKind.valueOf(json.getString("materializationActivityKind")),
                attributedTasks = tasks,
                transferEvidence = transferEvidence
            )
            return TaskProtocolExposureMetadata(authorization, json.getInt("exposureIndex"))
        }
    }
}

internal data class TaskProtocolAuthorizationPlan(
    val authorizedByOwner: Map<StimulusPrescriptionOwnerIdentity, TaskProtocolB6Authorization>,
    val deferredByOwner: Map<StimulusPrescriptionOwnerIdentity, String>,
    val selectedTaskOwners: Set<StimulusPrescriptionOwnerIdentity>
)

/** Shared fail-closed check used by final target audit and B7 over the persisted typed contract. */
internal fun TaskProtocolExposureMetadata.matchesMaterializedItem(
    item: ProgramSkeletonItem,
    actualActivityKind: PlannedActivityKind,
    currentB4Tasks: Set<CanonicalTaskTarget>,
    exactDirectTasks: Set<CanonicalTaskTarget>? = null,
    selectedPrimaryTargetId: String? = null
): Boolean {
    val authorization = this.authorization
    val definition = authorization.definition
    val shape = definition.shape
    val expectedSeconds = when (shape.mode) {
        TaskPrescriptionMode.DURATION_SECONDS -> requireNotNull(shape.seconds)
        TaskPrescriptionMode.DURATION_RANGE_SECONDS -> requireNotNull(shape.maxSeconds)
        else -> 0
    }
    val expectedReps = shape.reps ?: 0
    return item.exerciseStableKey == definition.stableKey && item.stableKey == definition.stableKey &&
        item.selectionRole == definition.selectionRole &&
        (selectedPrimaryTargetId == null || selectedPrimaryTargetId == "TASK:${definition.primaryTask.name}") &&
        definition.primaryTask in currentB4Tasks && authorization.attributedTasks.all { task ->
            task in currentB4Tasks && authorization.transferEvidence[task] == BadmintonObjectiveTransferLevel.DIRECT &&
                (exactDirectTasks == null || task in exactDirectTasks)
        } && authorization.materializationActivityKind == actualActivityKind &&
        actualActivityKind in setOf(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL) &&
        shape.targetRpe == null && shape.loadMode == TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD &&
        item.prescription == shape.format() && item.restSeconds == shape.restSeconds &&
        item.setCount == shape.setCount && item.setPrescriptions.size == shape.setCount &&
        item.reps == expectedReps && item.seconds == expectedSeconds && item.weightKg == 0.0 &&
        item.setPrescriptions.all { set ->
            set.reps == expectedReps && set.seconds == expectedSeconds && set.weightKg == 0.0 &&
                set.loadState == ProgramLoadState.NOT_APPLICABLE && set.targetRpeMin == null
        }
}

/** Exact B4+B5+approved-policy resolver; relations are checked against live canonical metadata. */
internal object TaskProtocolB6AuthorizationEngine {
    fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot
    ): TaskProtocolAuthorizationPlan {
        val selectedTaskOwners = selectionPlan.selectedCandidates
            .filter { candidate -> candidate.coveredTargetIds.any { it.startsWith("TASK:") } }
            .mapTo(linkedSetOf()) { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val taskTargets = targetPlan.taskTargets.associateBy { it.task }
        val grants = linkedMapOf<StimulusPrescriptionOwnerIdentity, TaskProtocolB6Authorization>()
        val deferred = linkedMapOf<StimulusPrescriptionOwnerIdentity, String>()

        selectionPlan.selectedCandidates
            .filter { candidate -> candidate.coveredTargetIds.any { it.startsWith("TASK:") } }
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            .forEach { candidate ->
                val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                val primaryTask = candidate.primaryTargetId.removePrefix("TASK:")
                    .takeIf { candidate.primaryTargetId.startsWith("TASK:") }
                    ?.let { runCatching { CanonicalTaskTarget.valueOf(it) }.getOrNull() }
                val definition = primaryTask?.let {
                    ApprovedBadmintonTaskProtocols.exact(candidate.stableKey, candidate.selectionRole, it)
                }
                if (definition == null) {
                    deferred[owner] = "NO_EXACT_USER_APPROVED_TASK_PROTOCOL"
                    return@forEach
                }
                val primaryB4 = taskTargets[definition.primaryTask.name]
                if (primaryB4 == null) {
                    deferred[owner] = "EXACT_B4_PRIMARY_TASK_TARGET_MISSING"
                    return@forEach
                }
                // C24 is the exact downstream bridge for a direction-only B4 task target.
                // It must not replace a future personal numeric task baseline or turn a
                // no-need/unresolved B4 state into a starter protocol.
                if (primaryB4.numericAuthority != StimulusTargetNumericAuthority.DIRECTION_ONLY) {
                    deferred[owner] = "EXACT_B4_PRIMARY_TASK_TARGET_NOT_DIRECTION_ONLY"
                    return@forEach
                }
                val direct = snapshot.badmintonDirectObjectives[candidate.stableKey].orEmpty()
                if (!definition.authorizedTasks.all { it.name in direct }) {
                    deferred[owner] = "APPROVED_PROTOCOL_CANONICAL_DIRECT_RELATION_MISSING"
                    return@forEach
                }
                if (snapshot.activityKind(candidate.stableKey) !in setOf(
                        PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
                        PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
                    )) {
                    deferred[owner] = "APPROVED_PROTOCOL_OWNER_ACTIVITY_KIND_NOT_DRILL"
                    return@forEach
                }
                // The exact user-approved protocol explicitly governs these targets. Reused B5
                // coverage is not the authority source; every task still needs a current B4
                // target and exact canonical DIRECT relation.
                val coveredTasks = definition.authorizedTasks
                    .filter { it.name in taskTargets && it.name in direct }
                    .toSet()
                if (primaryTask !in coveredTasks) {
                    deferred[owner] = "EXACT_B5_PRIMARY_TARGET_NOT_COVERED"
                    return@forEach
                }
                grants[owner] = TaskProtocolB6Authorization(
                    status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
                    definition = definition,
                    materializationActivityKind = snapshot.activityKind(candidate.stableKey),
                    attributedTasks = coveredTasks,
                    transferEvidence = coveredTasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
                )
            }
        return TaskProtocolAuthorizationPlan(grants, deferred, selectedTaskOwners)
    }
}

/** Adds typed placement shortfall diagnostics without changing the generated placement. */
internal fun com.training.trackplanner.data.GeneratedProgramSkeleton.withTaskProtocolFrequencyOutcomes(
    plan: TaskProtocolAuthorizationPlan
): com.training.trackplanner.data.GeneratedProgramSkeleton {
    val removeLocalIds = linkedSetOf<String>()
    val duplicateDayShortfalls = linkedSetOf<Pair<StimulusPrescriptionOwnerIdentity, Int>>()
    val ambiguousMaterializations = linkedSetOf<Pair<StimulusPrescriptionOwnerIdentity, Int>>()
    plan.authorizedByOwner.toSortedMap(compareBy({ it.stableKey }, { it.selectionRole })).forEach { (owner, authorization) ->
        (1..request.durationWeeks).forEach { week ->
            val rows = items.filter { item ->
                item.weekNumber == week && item.exerciseStableKey == owner.stableKey && item.selectionRole == owner.selectionRole
            }
            val decoded = rows.mapNotNull { row -> row.taskProtocolSemanticsJson?.let { json ->
                runCatching { TaskProtocolExposureMetadata.fromJsonString(json) }.getOrNull()?.let { row to it }
            } }
            val valid = decoded.size == rows.size && decoded.all { (row, metadata) ->
                metadata.authorization.definition.protocolId == authorization.definition.protocolId &&
                    metadata.matchesMaterializedItem(
                        item = row,
                        actualActivityKind = authorization.materializationActivityKind,
                        currentB4Tasks = authorization.attributedTasks,
                        exactDirectTasks = authorization.attributedTasks,
                        selectedPrimaryTargetId = "TASK:${authorization.definition.primaryTask.name}"
                    )
            }
            if (!valid) return@forEach
            if (decoded.map { it.second.exposureIndex }.distinct().size != decoded.size) {
                ambiguousMaterializations += owner to week
                return@forEach
            }
            val sameDayGroups = decoded.groupBy { it.first.dayOfWeek }.values
            sameDayGroups.filter { it.size > 1 }.forEach { group ->
                // Keep the lowest stable exposure index; a second same-protocol exposure on
                // one day is never used to satisfy a two-exposure weekly policy.
                group.sortedWith(compareBy({ it.second.exposureIndex }, { it.first.orderIndex }, { it.first.localId }))
                    .drop(1).forEach { (row, _) -> removeLocalIds += row.localId }
                duplicateDayShortfalls += owner to week
            }
        }
    }
    val normalizedItems = items.filterNot { it.localId in removeLocalIds }
    val outcomes = buildList {
        plan.authorizedByOwner.toSortedMap(compareBy({ it.stableKey }, { it.selectionRole })).forEach { (owner, authorization) ->
            val definition = authorization.definition
            (1..request.durationWeeks).forEach { week ->
                val rows = normalizedItems.filter { item ->
                    item.weekNumber == week && item.exerciseStableKey == owner.stableKey && item.selectionRole == owner.selectionRole
                }
                val decoded = rows.mapNotNull { row -> row.taskProtocolSemanticsJson?.let { json ->
                    runCatching { TaskProtocolExposureMetadata.fromJsonString(json) }.getOrNull()?.let { row to it }
                } }
                val valid = decoded.filter { (row, metadata) ->
                    metadata.authorization.definition.protocolId == definition.protocolId &&
                        metadata.matchesMaterializedItem(
                            item = row,
                            actualActivityKind = authorization.materializationActivityKind,
                            currentB4Tasks = authorization.attributedTasks,
                            exactDirectTasks = authorization.attributedTasks
                        )
                }
            val duplicateIdentity = (owner to week) in ambiguousMaterializations ||
                    valid.map { it.second.exposureIndex }.distinct().size != valid.size || decoded.size != rows.size
                val placed = if (duplicateIdentity) 0 else valid.size
                val status = when {
                    duplicateIdentity -> TaskProtocolFrequencyStatus.AMBIGUOUS_MATERIALIZATION
                    placed >= definition.weeklyExposures -> TaskProtocolFrequencyStatus.SATISFIED
                    else -> TaskProtocolFrequencyStatus.SHORTFALL
                }
                add(TaskProtocolWeeklyFrequencyOutcome(
                    protocolId = definition.protocolId,
                    stableKey = owner.stableKey,
                    selectionRole = owner.selectionRole,
                    week = week,
                    requestedExposures = definition.weeklyExposures,
                    placedExposures = placed.coerceAtMost(definition.weeklyExposures),
                    shortfall = (definition.weeklyExposures - placed).coerceAtLeast(0),
                    status = status,
                    reasonCode = when (status) {
                        TaskProtocolFrequencyStatus.SATISFIED -> "APPROVED_PROTOCOL_FREQUENCY_PLACED"
                        TaskProtocolFrequencyStatus.SHORTFALL -> if ((owner to week) in duplicateDayShortfalls)
                            "SAME_PROTOCOL_DUPLICATE_DAY_REMOVED_FREQUENCY_SHORTFALL" else "SAFE_PLACEMENT_FREQUENCY_SHORTFALL"
                        TaskProtocolFrequencyStatus.AMBIGUOUS_MATERIALIZATION -> "TASK_PROTOCOL_EXPOSURE_IDENTITY_INVALID"
                    }
                ))
            }
        }
    }
    return copy(items = normalizedItems, taskProtocolFrequencyOutcomes = outcomes)
}
