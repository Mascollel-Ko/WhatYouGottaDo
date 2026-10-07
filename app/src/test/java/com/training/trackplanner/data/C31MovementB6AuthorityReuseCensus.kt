package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Audit-only identity overlay for the 22 C30 sparse movement targets. */
internal object C31MovementB6AuthorityReuseCensus {
    private val executableQualityStatuses = setOf(
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
    )

    fun render(
        c29Census: String,
        canonicalPlanningByCase: Map<String, CanonicalStimulusPlanningResult>,
        preparedContextByCase: Map<String, PreparedCanonicalGenerationContext>,
        selectionPlanByCase: Map<String, StimulusCandidateSelectionPlan>,
        authorizationPlanByCase: Map<String, StimulusPrescriptionAuthorizationPlan>,
        experimentalByCase: Map<String, GeneratedProgramSkeleton>,
        stage: String,
        startSha: String
    ): String {
        val needs = JSONObject(c29Census).getJSONArray("needs").objects().sortedWith(compareBy(
            { it.getString("case") }, { it.getString("stableKey") }, { it.getString("selectionRole") }
        ))
        val rows = needs.map { need ->
            val caseName = need.getString("case")
            val planning = requireNotNull(canonicalPlanningByCase[caseName])
            val context = requireNotNull(preparedContextByCase[caseName])
            val selection = requireNotNull(selectionPlanByCase[caseName])
            val authorization = requireNotNull(authorizationPlanByCase[caseName])
            val movementCoverage = need.getJSONArray("representedGapCodes").getString(0)
                .let(MovementCoverage::valueOf)
            val target = planning.targetPlan.movementTargets.single { it.movementCoverage == movementCoverage }
            val targetId = target.targetId
            val trace = selection.traces.single { it.targetId == targetId }
            val selected = selection.selectedCandidates.singleOrNull { targetId in it.coveredTargetIds }
            val selectedOwner = selected?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            val selectedB6 = selectedOwner?.let { owner ->
                authorization.authorizations.filter { row ->
                    row.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == owner &&
                        row.status in executableQualityStatuses && row.authorizedPrescription != null
                }
            }.orEmpty()
            val taskB6 = TaskProtocolB6AuthorizationEngine.build(
                planning.targetPlan, selection, context.snapshot
            )
            val experiment = experimentalByCase[caseName]
            val directQualityRows = authorization.authorizations.filter { row ->
                row.owner != null && row.status in executableQualityStatuses && row.authorizedPrescription != null
            }.flatMap { row ->
                val owner = requireNotNull(row.owner)
                experiment?.items.orEmpty().filter { item ->
                    item.exerciseStableKey == owner.stableKey && item.selectionRole == owner.selectionRole &&
                        context.snapshot.activityKind(item.exerciseStableKey) == PlannedActivityKind.RESISTANCE &&
                        context.snapshot.movementCoverage(item.exerciseStableKey).directlyRepresents(movementCoverage)
                }.map { item -> JSONObject()
                    .put("targetId", row.targetId)
                    .put("quality", row.quality?.name ?: JSONObject.NULL)
                    .put("stableKey", owner.stableKey)
                    .put("selectionRole", owner.selectionRole)
                    .put("status", row.status.name)
                    .put("source", row.source?.name ?: JSONObject.NULL)
                    .put("prescription", prescriptionJson(row.authorizedPrescription))
                    .put("materializedRows", experiment?.items.orEmpty().count {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                    })
                }
            }
            val exactTaskRows = taskB6.authorizedByOwner.mapNotNull { (owner, taskAuthority) ->
                val matching = experiment?.items.orEmpty().filter { item ->
                    item.exerciseStableKey == owner.stableKey && item.selectionRole == owner.selectionRole &&
                        item.taskProtocolSemanticsJson != null
                }
                matching.takeIf { it.isNotEmpty() }?.map { item -> JSONObject()
                    .put("stableKey", owner.stableKey)
                    .put("selectionRole", owner.selectionRole)
                    .put("protocolId", taskAuthority.definition.protocolId)
                    .put("primaryTask", taskAuthority.definition.primaryTask.name)
                    .put("authorizedTasks", JSONArray(taskAuthority.attributedTasks.map { it.name }.sorted()))
                    .put("directMovementRelation", context.snapshot.activityKind(owner.stableKey) == PlannedActivityKind.RESISTANCE &&
                        context.snapshot.movementCoverage(owner.stableKey).directlyRepresents(movementCoverage))
                    .put("materializedProtocolRows", matching.size)
                }
            }.flatten()
            val candidateOwnerIdentities = trace.candidatePool.map { stableKey ->
                StimulusPrescriptionOwnerIdentity(
                    stableKey,
                    trace.candidateSelectionRoles[stableKey]
                        ?: "CANONICAL_STIMULUS_MOVEMENT_${movementCoverage.name}"
                )
            }.toSet()
            val taskAuthoritiesInCandidatePool = taskB6.authorizedByOwner.filterKeys { it in candidateOwnerIdentities }
            val qualityTargetAudit = planning.targetPlan.qualityTargets.map { b4 ->
                val targetId = "QUALITY:${b4.quality.name}"
                val traceForTarget = selection.traces.singleOrNull { it.targetId == targetId }
                JSONObject()
                    .put("targetId", targetId)
                    .put("strategy", b4.strategy.name)
                    .put("priority", b4.priority.name)
                    .put("numericAuthority", b4.numericAuthority.name)
                    .put("selectionRequired", traceForTarget?.selectionRequired ?: false)
                    .put("selectedOwner", traceForTarget?.selectedStableKey ?: JSONObject.NULL)
                    .put("selectedRole", traceForTarget?.selectedSelectionRole ?: JSONObject.NULL)
            }
            val taskTargetAudit = planning.targetPlan.taskTargets.map { b4 ->
                val targetId = "TASK:${b4.task}"
                val traceForTarget = selection.traces.singleOrNull { it.targetId == targetId }
                JSONObject()
                    .put("targetId", targetId)
                    .put("strategy", b4.strategy.name)
                    .put("priority", b4.priority.name)
                    .put("numericAuthority", b4.numericAuthority.name)
                    .put("selectionRequired", traceForTarget?.selectionRequired ?: false)
                    .put("selectedOwner", traceForTarget?.selectedStableKey ?: JSONObject.NULL)
                    .put("selectedRole", traceForTarget?.selectedSelectionRole ?: JSONObject.NULL)
            }
            val taskB6OwnerAudit = taskB6.authorizedByOwner.map { (owner, authority) ->
                JSONObject()
                    .put("stableKey", owner.stableKey)
                    .put("selectionRole", owner.selectionRole)
                    .put("protocolId", authority.definition.protocolId)
                    .put("primaryTask", authority.definition.primaryTask.name)
                    .put("authorizedTasks", JSONArray(authority.attributedTasks.map { it.name }.sorted()))
                    .put("selectedByCurrentB5", selection.selectedCandidates.any {
                        it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                    })
                    .put("directMovementRelation", context.snapshot.activityKind(owner.stableKey) == PlannedActivityKind.RESISTANCE &&
                        context.snapshot.movementCoverage(owner.stableKey).directlyRepresents(movementCoverage))
                    .put("materializedProtocolRows", experiment?.items.orEmpty().count {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole &&
                            it.taskProtocolSemanticsJson != null
                    })
            }
            val otherCanonicalB5Owners = selection.selectedCandidates
                .filter { selectedCandidate -> selectedCandidate.coveredTargetIds.any { !it.startsWith("MOVEMENT:") } }
                .distinctBy { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                .map { selectedCandidate ->
                    val identity = StimulusPrescriptionOwnerIdentity(selectedCandidate.stableKey, selectedCandidate.selectionRole)
                    val qualityRows = authorization.authorizations.filter { auth ->
                        auth.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == identity
                    }
                    val existingMaterialRows = experiment?.items.orEmpty().count {
                        it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    }
                    JSONObject()
                        .put("stableKey", identity.stableKey)
                        .put("selectionRole", identity.selectionRole)
                        .put("coveredNonMovementTargets", JSONArray(selectedCandidate.coveredTargetIds.filter { !it.startsWith("MOVEMENT:") }.sorted()))
                        .put("activityKind", context.snapshot.activityKind(identity.stableKey).name)
                        .put("canonicalMovementCoverage", context.snapshot.movementCoverage(identity.stableKey).name)
                        .put("directMovementRelation", context.snapshot.activityKind(identity.stableKey) == PlannedActivityKind.RESISTANCE &&
                            context.snapshot.movementCoverage(identity.stableKey).directlyRepresents(movementCoverage))
                        .put("qualityB6Rows", JSONArray(qualityRows.map(::authorizationJson)))
                        .put("hasExactExecutableQualityB6", qualityRows.any { row ->
                            row.status in executableQualityStatuses && row.authorizedPrescription != null
                        })
                        .put("materializedPhysicalRows", existingMaterialRows)
                }
            val approvedTaskProtocolMovementOverlay = ApprovedBadmintonTaskProtocols.definitions.map { definition ->
                val activity = context.snapshot.activityKind(definition.stableKey)
                val coverage = context.snapshot.movementCoverage(definition.stableKey)
                JSONObject()
                    .put("protocolId", definition.protocolId)
                    .put("stableKey", definition.stableKey)
                    .put("selectionRole", definition.selectionRole)
                    .put("authorizedPrimaryTask", definition.primaryTask.name)
                    .put("activityKind", activity.name)
                    .put("canonicalMovementCoverage", coverage.name)
                    .put("directMovementRelation", activity == PlannedActivityKind.RESISTANCE &&
                        coverage.directlyRepresents(movementCoverage))
                    .put("stableKeyInMovementCandidatePool", definition.stableKey in trace.candidatePool)
            }
            val qualityAuthoritiesByCandidate = trace.candidatePool.mapIndexed { index, stableKey ->
                val candidateRole = trace.candidateSelectionRoles[stableKey]
                    ?: "CANONICAL_STIMULUS_MOVEMENT_${movementCoverage.name}"
                val authorities = authorization.authorizations.filter { row ->
                    row.owner?.stableKey == stableKey && row.status in executableQualityStatuses && row.authorizedPrescription != null
                }
                JSONObject()
                    .put("rank", index + 1)
                    .put("stableKey", stableKey)
                    .put("selectionRole", candidateRole)
                    .put("directMovementCandidate", true)
                    .put("existingQualityAuthorities", JSONArray(authorities.map { row -> JSONObject()
                        .put("targetId", row.targetId)
                        .put("quality", row.quality?.name ?: JSONObject.NULL)
                        .put("selectionRole", row.owner?.selectionRole ?: JSONObject.NULL)
                        .put("status", row.status.name)
                        .put("source", row.source?.name ?: JSONObject.NULL)
                        .put("prescription", prescriptionJson(row.authorizedPrescription))
                    }))
                    .put("exactSameMovementOwnerAuthority", authorities.any { row ->
                        row.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } ==
                            StimulusPrescriptionOwnerIdentity(stableKey, candidateRole)
                    })
            }
            val sameStableKeyAuthorities = authorization.authorizations.filter { row ->
                val owner = row.owner
                val selected = selectedOwner
                owner != null && selected != null && owner.stableKey == selected.stableKey &&
                    row.status in executableQualityStatuses && row.authorizedPrescription != null &&
                    owner.selectionRole != selected.selectionRole
            }
            val selectedTaskAuthorization = selectedOwner?.let(taskB6.authorizedByOwner::get)
            val exactSameOwner = selectedB6.isNotEmpty() || selectedTaskAuthorization != null
            val qualitySatisfaction = directQualityRows.isNotEmpty()
            val taskSatisfaction = exactTaskRows.any { it.optBoolean("directMovementRelation") }
            val selectedColdStart = selectedOwner?.let { owner -> authorization.authorizations.any { row ->
                row.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == owner &&
                    row.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
            } } == true
            val exactCandidateKeys = trace.candidatePool.toSet()
            val authorizedAlternatives = authorization.authorizations.filter { row ->
                row.owner != null && row.owner.stableKey in exactCandidateKeys &&
                    row.status in executableQualityStatuses && row.authorizedPrescription != null &&
                    row.owner.selectionRole == (trace.candidateSelectionRoles[row.owner.stableKey]
                        ?: "CANONICAL_STIMULUS_MOVEMENT_${movementCoverage.name}") &&
                    row.owner.stableKey != selectedOwner?.stableKey
            }
            val multiQualityConflicts = authorization.multiQualityResolutions.values.filter { resolution ->
                resolution.owner.stableKey in exactCandidateKeys &&
                    resolution.status == StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
            }
            val historyEvidence = (authorization.canonicalPrescriptions.keys intersect authorization.historyBackedOwners)
                .filter { it.stableKey in exactCandidateKeys }
            val primaryClass = when {
                exactSameOwner -> "EXACT_SAME_OWNER_AUTHORITY_EXISTS"
                qualitySatisfaction -> "EXISTING_QUALITY_AUTHORITY_CAN_SATISFY_MOVEMENT_NON_ADDITIVELY"
                taskSatisfaction -> "EXISTING_TASK_AUTHORITY_CAN_SATISFY_MOVEMENT_NON_ADDITIVELY"
                authorizedAlternatives.isNotEmpty() -> "AUTHORIZED_ALTERNATIVE_WAS_AVAILABLE"
                selectedColdStart -> "COLD_START_USER_CALIBRATION_CANDIDATE"
                sameStableKeyAuthorities.isNotEmpty() -> "SAME_STABLEKEY_EXISTING_AUTHORITY_DIFFERENT_ROLE"
                multiQualityConflicts.isNotEmpty() -> "MULTI_AUTHORITY_CONFLICT_OR_AMBIGUITY"
                historyEvidence.isNotEmpty() -> "HISTORY_BACKED_AUTHORITY_CANDIDATE"
                else -> "GENUINE_NO_B6_POLICY"
            }
            JSONObject()
                .put("case", caseName)
                .put("movementTargetId", targetId)
                .put("movementCoverage", movementCoverage.name)
                .put("b3Disposition", planning.decisionPortfolio.movementDecisions.single {
                    it.movementCoverage == movementCoverage
                }.disposition.name)
                .put("b4NumericAuthority", target.numericAuthority.name)
                .put("sameCaseB4QualityTargets", JSONArray(qualityTargetAudit))
                .put("sameCaseB4TaskTargets", JSONArray(taskTargetAudit))
                .put("sameCaseAuthorizedTaskB6Owners", JSONArray(taskB6OwnerAudit))
                .put("sameCaseOtherCanonicalB5Owners", JSONArray(otherCanonicalB5Owners))
                .put("selectedStableKey", selectedOwner?.stableKey ?: JSONObject.NULL)
                .put("selectedMovementRole", selectedOwner?.selectionRole ?: JSONObject.NULL)
                .put("selectedCandidateRank", trace.selectedStableKey?.let { key -> trace.candidatePool.indexOf(key) + 1 } ?: JSONObject.NULL)
                .put("candidatePoolSize", trace.candidatePool.size)
                .put("candidatePool", JSONArray(qualityAuthoritiesByCandidate))
                .put("selectedOwnerQualityB6", JSONArray(selectedB6.map(::authorizationJson)))
                .put("sameStableKeyDifferentRoleAuthorities", JSONArray(sameStableKeyAuthorities.map(::authorizationJson)))
                .put("selectedOwnerTaskB6", selectedTaskAuthorization?.let(::taskAuthorizationJson) ?: JSONObject.NULL)
                .put("taskAuthoritiesInMovementCandidatePool", JSONArray(taskAuthoritiesInCandidatePool.map { (owner, authority) ->
                    JSONObject()
                        .put("stableKey", owner.stableKey)
                        .put("selectionRole", owner.selectionRole)
                        .put("protocolId", authority.definition.protocolId)
                        .put("primaryTask", authority.definition.primaryTask.name)
                        .put("authorizedTasks", JSONArray(authority.attributedTasks.map { it.name }.sorted()))
                }))
                .put("approvedTaskProtocolMovementOverlay", JSONArray(approvedTaskProtocolMovementOverlay))
                .put("directMovementQualityRows", JSONArray(directQualityRows))
                .put("directMovementTaskRows", JSONArray(exactTaskRows))
                .put("historyBackedCanonicalPrescriptionsAreExecutionAuthority", false)
                .put("historyBackedCandidateEvidence", JSONArray(historyEvidence.map { JSONObject()
                    .put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                }))
                .put("coldStartCalibrationCandidate", selectedColdStart)
                .put("authorizedAlternativeCandidates", JSONArray(authorizedAlternatives.map(::authorizationJson)))
                .put("multiQualityConflicts", JSONArray(multiQualityConflicts.map { resolution -> JSONObject()
                    .put("stableKey", resolution.owner.stableKey)
                    .put("selectionRole", resolution.owner.selectionRole)
                    .put("reasonCodes", JSONArray(resolution.reasonCodes))
                }))
                .put("primaryReuseClassification", primaryClass)
                .put("genuineNoB6Policy", primaryClass == "GENUINE_NO_B6_POLICY")
                .put("movementMaterializedByExistingAuthorizedRow", qualitySatisfaction || taskSatisfaction)
                .put("experimentalBuildPresent", experiment != null)
                .put("controlRowsAreAuthority", false)
        }
        val poolCounts = rows.map { it.getInt("candidatePoolSize") }
        val classes = rows.groupingBy { it.getString("primaryReuseClassification") }.eachCount().toSortedMap()
        val qualityCandidateRows = rows.sumOf { it.getJSONArray("candidatePool").length() }
        val qualityAuthorityCandidates = rows.sumOf { row ->
            val pool = row.getJSONArray("candidatePool")
            (0 until pool.length()).count { pool.getJSONObject(it).getJSONArray("existingQualityAuthorities").length() > 0 }
        }
        return JSONObject()
            .put("phase", "C31")
            .put("title", "Movement B6 authority reuse audit")
            .put("stage", stage)
            .put("startSha", startSha)
            .put("productionBehaviorChanged", false)
            .put("summary", JSONObject()
                .put("movementTargets", rows.size)
                .put("candidatePoolRows", qualityCandidateRows)
                .put("movementCandidatesWithExistingQualityB6", qualityAuthorityCandidates)
                .put("exactSameOwnerAuthority", classes["EXACT_SAME_OWNER_AUTHORITY_EXISTS"] ?: 0)
                .put("qualityNonAdditiveReuse", classes["EXISTING_QUALITY_AUTHORITY_CAN_SATISFY_MOVEMENT_NON_ADDITIVELY"] ?: 0)
                .put("taskNonAdditiveReuse", classes["EXISTING_TASK_AUTHORITY_CAN_SATISFY_MOVEMENT_NON_ADDITIVELY"] ?: 0)
                .put("sameStableKeyDifferentRole", classes["SAME_STABLEKEY_EXISTING_AUTHORITY_DIFFERENT_ROLE"] ?: 0)
                .put("historyBackedCandidate", classes["HISTORY_BACKED_AUTHORITY_CANDIDATE"] ?: 0)
                .put("userCalibrationCandidate", classes["COLD_START_USER_CALIBRATION_CANDIDATE"] ?: 0)
                .put("multiAuthorityConflict", classes["MULTI_AUTHORITY_CONFLICT_OR_AMBIGUITY"] ?: 0)
                .put("authorizedAlternative", classes["AUTHORIZED_ALTERNATIVE_WAS_AVAILABLE"] ?: 0)
                .put("genuineNoB6Policy", classes["GENUINE_NO_B6_POLICY"] ?: 0)
                .put("candidatePoolMin", poolCounts.minOrNull() ?: 0)
                .put("candidatePoolMax", poolCounts.maxOrNull() ?: 0)
                .put("primaryClassificationCounts", JSONObject().apply { classes.forEach { (key, value) -> put(key, value) } }))
            .put("targets", JSONArray(rows))
            .toString(2)
    }

    private fun authorizationJson(row: StimulusPrescriptionAuthorization): JSONObject = JSONObject()
        .put("targetId", row.targetId)
        .put("quality", row.quality?.name ?: JSONObject.NULL)
        .put("stableKey", row.owner?.stableKey ?: JSONObject.NULL)
        .put("selectionRole", row.owner?.selectionRole ?: JSONObject.NULL)
        .put("source", row.source?.name ?: JSONObject.NULL)
        .put("status", row.status.name)
        .put("reasonCodes", JSONArray(row.reasonCodes))
        .put("executionAuthority", row.executionAuthority.name)
        .put("plannedCompatibility", row.plannedCompatibility?.let { compatibility -> JSONObject()
            .put("status", compatibility.status.name)
            .put("reasonCodes", JSONArray(compatibility.reasonCodes))
            .put("reference1RmKg", compatibility.reference1RmKg ?: JSONObject.NULL)
            .put("relativeIntensity", compatibility.relativeIntensity ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        .put("inputPrescription", prescriptionJson(row.inputPrescription))
        .put("prescription", prescriptionJson(row.authorizedPrescription))
        .put("coldStartCalibration", row.coldStartCalibration?.let { proposal -> JSONObject()
            .put("stableKey", proposal.owner.stableKey)
            .put("selectionRole", proposal.owner.selectionRole)
            .put("quality", proposal.quality.name)
            .put("setCount", proposal.setCount)
            .put("repetitions", proposal.repetitions)
            .put("targetRpe", proposal.targetRpe)
            .put("loadState", proposal.loadState.name)
            .put("authoritySource", proposal.authoritySource.name)
            .put("reasonCodes", JSONArray(proposal.reasonCodes))
        } ?: JSONObject.NULL)

    private fun taskAuthorizationJson(row: TaskProtocolB6Authorization): JSONObject = JSONObject()
        .put("status", row.status.name)
        .put("protocolId", row.definition.protocolId)
        .put("stableKey", row.definition.stableKey)
        .put("selectionRole", row.definition.selectionRole)
        .put("primaryTask", row.definition.primaryTask.name)
        .put("authorizedTasks", JSONArray(row.attributedTasks.map { it.name }.sorted()))
        .put("loadMode", row.definition.shape.loadMode?.name ?: JSONObject.NULL)
        .put("targetRpe", row.definition.shape.targetRpe ?: JSONObject.NULL)

    private fun prescriptionJson(value: PlannedPrescription?): JSONObject? = value?.let { prescription -> JSONObject()
        .put("text", prescription.text)
        .put("sets", JSONArray(prescription.sets.map { set -> JSONObject()
            .put("setIndex", set.setIndex)
            .put("reps", set.reps)
            .put("weightKg", set.weightKg)
            .put("seconds", set.seconds)
            .put("loadState", set.loadState.name)
            .put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
        }))
        .put("restSeconds", prescription.restSeconds)
        .put("weightSource", prescription.weightSource)
    }

    private fun MovementCoverage.directlyRepresents(target: MovementCoverage): Boolean = when (target) {
        MovementCoverage.UPPER_PULL -> this in setOf(
            MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL, MovementCoverage.UPPER_PULL
        )
        else -> this == target
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
