package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer

/** C36 evidence joins actual B4/B5/B6, allocation, scheduling, materialization and B7 objects. */
internal object C36ExecutionProvenanceCensus {
    private const val UNEXPLAINED_REMOVAL = "UNEXPLAINED_REMOVED_IDENTITY"

    /** Writes the report without allocating a second, report-sized String. */
    fun writeCompactJson(value: JSONObject, writer: Writer) {
        writeJsonValue(value, writer)
    }

    private fun writeJsonValue(value: Any?, writer: Writer) {
        when {
            value == null || value === JSONObject.NULL -> writer.write("null")
            value is JSONObject -> {
                writer.write('{'.code)
                val keys = value.keys()
                var first = true
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!first) writer.write(','.code)
                    first = false
                    writeJsonString(key, writer)
                    writer.write(':'.code)
                    writeJsonValue(value.get(key), writer)
                }
                writer.write('}'.code)
            }
            value is JSONArray -> {
                writer.write('['.code)
                for (index in 0 until value.length()) {
                    if (index > 0) writer.write(','.code)
                    writeJsonValue(value.get(index), writer)
                }
                writer.write(']'.code)
            }
            value is String -> writeJsonString(value, writer)
            value is Number -> writer.write(jsonNumber(value))
            value is Boolean -> writer.write(value.toString())
            else -> writeJsonString(value.toString(), writer)
        }
    }

    private fun writeJsonString(value: String, writer: Writer) {
        writer.write('"'.code)
        value.forEach { char ->
            when (char) {
                '"' -> writer.write("\\\"")
                '\\' -> writer.write("\\\\")
                '\b' -> writer.write("\\b")
                '\u000C' -> writer.write("\\f")
                '\n' -> writer.write("\\n")
                '\r' -> writer.write("\\r")
                '\t' -> writer.write("\\t")
                else -> if (char.code < 0x20) {
                    writer.write("\\u")
                    writer.write(HEX[(char.code ushr 12) and 0xF].code)
                    writer.write(HEX[(char.code ushr 8) and 0xF].code)
                    writer.write(HEX[(char.code ushr 4) and 0xF].code)
                    writer.write(HEX[char.code and 0xF].code)
                } else writer.write(char.code)
            }
        }
        writer.write('"'.code)
    }

    private fun jsonNumber(value: Number): String {
        val rendered = value.toString()
        require(rendered != "NaN" && rendered != "Infinity" && rendered != "-Infinity") {
            "JSON census cannot contain a non-finite number"
        }
        if ('.' !in rendered || 'e' in rendered.lowercase()) return rendered
        return rendered.trimEnd('0').trimEnd('.')
    }

    private const val HEX = "0123456789abcdef"

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        contexts: Map<String, PreparedCanonicalGenerationContext>,
        generationMillisByCase: Map<String, Long>,
        c35Summary: JSONObject,
        startSha: String,
        implementationSha: String
    ): JSONObject {
        val cases = JSONArray()
        val regionalRows = mutableListOf<JSONObject>()
        val removalRows = mutableListOf<JSONObject>()
        val prescriptionChanges = mutableListOf<JSONObject>()
        val b7Counts = sortedMapOf<String, Int>()
        val b8Counts = sortedMapOf<String, Int>()
        val routes = sortedMapOf<String, Int>()
        var materializedOverrun = 0
        var duplicatePhysicalRows = 0
        var unauthorizedMaterialUnits = 0
        var regionalRawResidual = 0.0
        var regionalFiniteDiagnosticFunded = 0
        var regionalFinalAuthorized = 0
        var regionalMaterialized = 0
        var regionalWeekOneMaterialized = 0
        var regionalRetained = 0
        var regionalNewMaterial = 0
        var regionalAllocationShortfall = 0
        var regionalPlacementShortfall = 0
        var regionalB4ToSchedulingShortfall = 0
        var regionalFrequencyFunded = 0
        var diagnosticFundedButCapacityRejectedRows = 0
        var diagnosticFundedButCapacityRejectedUnits = 0
        var diagnosticZeroButActuallyScheduledRows = 0
        var diagnosticZeroButActuallyScheduledUnits = 0

        records.sortedBy { it.first.label }.forEach { (spec, result) ->
            val generated = result ?: return@forEach
            val comparison = generated.comparison ?: run {
                cases.put(JSONObject().put("case", spec.label).put("generated", false)
                    .put("route", generated.routeDecision.selectedSource.name)
                    .put("generationTimeMs", generationMillisByCase[spec.label] ?: JSONObject.NULL))
                return@forEach
            }
            val experimental = comparison.experimental
            val decision = experimental.personalizedDecision
            val execution = decision?.planningBudget?.execution
            val bounded = decision?.frequencyDemand?.boundedMaterialAllocation
            val authorizationTrace = decision?.authorizedScheduling
            val b7 = comparison.experimentalReadinessAudit
            val b8 = comparison.productionCutoverAuthority
            val ownerIdentity = { row: ProgramSkeletonItem ->
                StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
            }
            duplicatePhysicalRows += experimental.items.groupingBy {
                listOf(it.exerciseStableKey, it.selectionRole, it.weekNumber, it.dayOfWeek, it.orderIndex,
                    it.setPrescriptions.map { set -> set.copy(setIndex = 0) })
            }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }

            comparison.targetPlan.movementTargets.sortedBy { it.targetId }.forEach { movement ->
                movement.regionalDoseTargets.filter {
                    it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET
                }.forEach { dose ->
                    val selected = comparison.selectionPlan.selectedCandidates.filter {
                        movement.targetId == it.primaryTargetId
                    }
                    val ownerRows = JSONArray()
                    selected.forEach { candidate ->
                        val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                        val planned = comparison.selectionPlan.materialDemand.candidates.singleOrNull {
                            it.stableKey == candidate.stableKey && it.role == candidate.selectionRole
                        }
                        val boundedOwner = bounded?.owners.orEmpty().singleOrNull {
                            it.owner.stableKey == identity.stableKey && it.owner.selectionRole == identity.selectionRole &&
                                (planned == null || it.owner.variant == planned.styleVariant)
                        }
                        val legacyFinite = execution?.finiteAllocationPriorityOrder.orEmpty().singleOrNull {
                            it.owner == identity
                        }
                        val frequencyCandidateProof = decision?.frequencyDemand?.candidates.orEmpty().filter {
                            it.item.stableKey == identity.stableKey && it.item.role == identity.selectionRole &&
                                (planned == null || it.item.styleVariant == planned.styleVariant)
                        }
                        val b6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().singleOrNull {
                            it.targetId == movement.targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                                it.owner?.stableKey == identity.stableKey && it.owner.selectionRole == identity.selectionRole
                        }
                        val movementB6 = comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty()
                            .singleOrNull { it.targetId == movement.targetId }
                        val audit = comparison.prescriptionMaterializationAudits.singleOrNull {
                            it.targetId == movement.targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                                it.owner?.stableKey == identity.stableKey && it.owner.selectionRole == identity.selectionRole
                        }
                        val authorizedDemand = authorizationTrace?.authorized.orEmpty().filter {
                            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == identity && it.item.material
                        }
                        val weekRows = JSONArray()
                        val weeklyMaterialized = (1..(experimental.personalizedDecision?.planningHorizonWeeks ?: 1)).sumOf { week ->
                            val finalRows = experimental.items.filter {
                                it.weekNumber == week && ownerIdentity(it) == identity
                            }
                            val units = finalRows.sumOf { it.setPrescriptions.size }
                            val unchanged = unchangedPrescriptionUnits(
                                comparison.control.items.filter { it.weekNumber == week && ownerIdentity(it) == identity }, finalRows
                            )
                            regionalMaterialized += units
                            if (week == 1) regionalWeekOneMaterialized += units
                            regionalRetained += unchanged
                            regionalNewMaterial += (units - unchanged).coerceAtLeast(0)
                            val demand = authorizedDemand.firstOrNull()
                            val authorizedUnits = demand?.prescription?.sets?.size ?: 0
                            regionalFinalAuthorized += authorizedUnits
                            regionalPlacementShortfall += (authorizedUnits - units).coerceAtLeast(0)
                            weekRows.put(JSONObject()
                                .put("week", week)
                                .put("authorizedSchedulingDemandId", demand?.id ?: JSONObject.NULL)
                                .put("authorizedFundingSource", demand?.fundingSource?.name ?: JSONObject.NULL)
                                .put("authorizedSourceReason", demand?.sourceReason?.name ?: JSONObject.NULL)
                                .put("authorizedOriginalRank", demand?.originalRank ?: JSONObject.NULL)
                                .put("authorizedUnits", authorizedUnits)
                                .put("materializedUnits", units)
                                .put("retainedExactPrescriptionUnits", unchanged)
                                .put("newOrChangedUnits", (units - unchanged).coerceAtLeast(0))
                                .put("controlOwnerUnits", comparison.control.items.filter {
                                    it.weekNumber == week && ownerIdentity(it) == identity
                                }.sumOf { it.setPrescriptions.size })
                                .put("placementEvents", JSONArray(execution?.ownerAllocationProvenance.orEmpty().filter { event ->
                                    event.owner == identity && listOfNotNull(event.before?.week, event.after?.week).contains(week)
                                }.map { it.toJson() })))
                            units
                        }
                        if (audit != null) materializedOverrun += audit.maximumWeeklyOverrun
                        if (weeklyMaterialized > 0 && b6?.authorizedPrescription == null && movementB6?.status !in setOf(
                                StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6,
                                StimulusMovementB6Status.COVERED_BY_APPROVED_TASK_B6,
                                StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6
                            )) unauthorizedMaterialUnits += weeklyMaterialized

                        ownerRows.put(JSONObject()
                            .put("stableKey", identity.stableKey)
                            .put("selectionRole", identity.selectionRole)
                            .put("styleVariant", planned?.styleVariant ?: "")
                            .put("b5Selected", true)
                            .put("b5Reasons", JSONArray(candidate.selectionReasons))
                            .put("b6Status", b6?.status?.name ?: movementB6?.status?.name ?: "NO_AUTHORITY")
                            .put("b6Authorized", b6?.authorizedPrescription != null || movementB6?.status in setOf(
                                StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6,
                                StimulusMovementB6Status.COVERED_BY_APPROVED_TASK_B6,
                                StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6
                            ))
                            .put("b6Prescription", b6?.authorizedPrescription?.let(::prescriptionJson) ?: JSONObject.NULL)
                            .put("legacyFiniteObservation", legacyFinite?.toJson() ?: JSONObject.NULL)
                            .put("boundedMaterialDemand", boundedOwner?.toJson() ?: JSONObject.NULL)
                            .put("finalAuthorizedWeeklyDemandUnits", authorizedDemand.firstOrNull()?.prescription?.sets?.size ?: 0)
                            .put("finalAuthorizedDemandIds", JSONArray(authorizedDemand.map { it.id }.sorted()))
                            .put("materializationAudit", audit?.let(::materializationJson) ?: JSONObject.NULL)
                            .put("frequencyCandidateProof", JSONArray(frequencyCandidateProof.map { proof -> JSONObject()
                                .put("originalRank", proof.originalRank)
                                .put("requestedUnits", proof.requestedUnits)
                                .put("fundedBaseUnits", proof.fundedBaseUnits)
                                .put("remainingUnits", proof.remainingUnits)
                                .put("rejectionReason", proof.rejectionReason.name)
                                .put("continuity", proof.continuity)
                                .put("prescriptionAuthority", proof.prescriptionAuthority.name)
                                .put("styleVariant", proof.item.styleVariant)
                            }))
                            .put("ownerWeek", weekRows))
                    }
                    val ownerFunded = selected.sumOf { candidate ->
                        bounded?.owners.orEmpty().singleOrNull {
                            it.owner.stableKey == candidate.stableKey && it.owner.selectionRole == candidate.selectionRole
                        }?.allocatedUnits ?: 0
                    }
                    val row = JSONObject()
                        .put("case", spec.label)
                        .put("weekScope", "PER_WEEK_REPEATED_OVER_HORIZON")
                        .put("targetId", movement.targetId)
                        .put("movement", movement.movementCoverage.name)
                        .put("b4WeeklyTargetEquivalent", dose.weeklyTarget ?: JSONObject.NULL)
                        .put("existingCompatibleEquivalent", dose.existingEquivalentExposure ?: JSONObject.NULL)
                        .put("rawResidualEquivalent", dose.residualEquivalentExposure ?: JSONObject.NULL)
                        .put("authorizedWholeSetUnits", dose.authorizedWholeSetUnits ?: 0)
                        .put("selectedOwnerCount", selected.size)
                        .put("selectedOwners", ownerRows)
                        .put("boundedAllocationTracePresent", bounded != null)
                        .put("boundedAllocatedUnits", if (bounded != null) ownerFunded else JSONObject.NULL)
                        .put("boundedCapacityShortfall", if (bounded != null)
                            ((dose.authorizedWholeSetUnits ?: 0) - ownerFunded).coerceAtLeast(0) else JSONObject.NULL)
                        .put("b6AuthorizedWeeklyUnits", selected.sumOf { candidate ->
                            authorizationTrace?.authorized.orEmpty().firstOrNull {
                                it.item.stableKey == candidate.stableKey && it.item.role == candidate.selectionRole && it.item.material
                            }?.prescription?.sets?.size ?: 0
                        })
                        .put("finalMaterializedUnitsAcrossHorizon", selected.sumOf { candidate ->
                            experimental.items.filter { it.exerciseStableKey == candidate.stableKey && it.selectionRole == candidate.selectionRole }
                                .sumOf { it.setPrescriptions.size }
                        })
                    val finiteDiagnosticFunded = selected.sumOf { candidate ->
                        execution?.finiteAllocationPriorityOrder.orEmpty().singleOrNull {
                            it.owner == StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                        }?.fundedUnits ?: 0
                    }
                    val scheduledUnitsPerWeek = selected.sumOf { candidate ->
                        authorizationTrace?.authorized.orEmpty().filter { demand ->
                            demand.item.stableKey == candidate.stableKey && demand.item.role == candidate.selectionRole && demand.item.material
                        }.sumOf { it.prescription.sets.size }
                    }
                    val frequencyFundedUnits = selected.sumOf { candidate ->
                        val planned = comparison.selectionPlan.materialDemand.candidates.singleOrNull {
                            it.stableKey == candidate.stableKey && it.role == candidate.selectionRole
                        }
                        decision?.frequencyDemand?.candidates.orEmpty().filter {
                            it.item.stableKey == candidate.stableKey && it.item.role == candidate.selectionRole &&
                                (planned == null || it.item.styleVariant == planned.styleVariant)
                        }.sumOf { it.fundedBaseUnits }
                    }
                    regionalFiniteDiagnosticFunded += finiteDiagnosticFunded
                    regionalFrequencyFunded += frequencyFundedUnits
                    regionalAllocationShortfall += ((dose.authorizedWholeSetUnits ?: 0) - finiteDiagnosticFunded).coerceAtLeast(0)
                    regionalB4ToSchedulingShortfall += ((dose.authorizedWholeSetUnits ?: 0) - scheduledUnitsPerWeek).coerceAtLeast(0) *
                        (experimental.personalizedDecision?.planningHorizonWeeks ?: 1)
                    when {
                        finiteDiagnosticFunded > 0 && scheduledUnitsPerWeek == 0 -> {
                            diagnosticFundedButCapacityRejectedRows++
                            diagnosticFundedButCapacityRejectedUnits += finiteDiagnosticFunded
                        }
                        finiteDiagnosticFunded == 0 && scheduledUnitsPerWeek > 0 -> {
                            diagnosticZeroButActuallyScheduledRows++
                            diagnosticZeroButActuallyScheduledUnits += scheduledUnitsPerWeek
                        }
                    }
                    row.put("finiteAllocationDiagnosticFundedUnits", finiteDiagnosticFunded)
                        .put("exactScheduledUnitsPerWeek", scheduledUnitsPerWeek)
                        .put("frequencyFundedBaseUnits", frequencyFundedUnits)
                        .put("diagnosticVsFinalFunding", when {
                            finiteDiagnosticFunded > 0 && scheduledUnitsPerWeek == 0 -> "DIAGNOSTIC_ONLY; FINAL_FREQUENCY_ALLOCATION_CAPACITY_REJECTED"
                            finiteDiagnosticFunded == 0 && scheduledUnitsPerWeek > 0 -> "DIAGNOSTIC_ZERO; BASE_AUTHORIZED_SCHEDULED"
                            finiteDiagnosticFunded == scheduledUnitsPerWeek -> "DIAGNOSTIC_EQUALS_FINAL_WEEKLY_SCHEDULING"
                            else -> "DIFFERENT_STAGE_OR_ALLOCATION_SNAPSHOT"
                        })
                    regionalRawResidual += dose.residualEquivalentExposure ?: 0.0
                    regionalRows += row
                }
            }

            val removed = comparison.removedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            val attributionByOwner = b7?.changeAttributions.orEmpty().mapNotNull { attribution ->
                val key = attribution.stableKey ?: return@mapNotNull null
                val role = attribution.selectionRole ?: return@mapNotNull null
                StimulusPrescriptionOwnerIdentity(key, role) to attribution
            }.toMap()
            val nonSelectionByOwner = comparison.nonSelectionProvenance.associateBy { it.omittedControlOwner }
            removed.forEach { owner ->
                val beforeByWeek = comparison.control.items.filter { ownerIdentity(it) == owner }.groupBy(ProgramSkeletonItem::weekNumber)
                val afterByWeek = comparison.experimental.items.filter { ownerIdentity(it) == owner }.groupBy(ProgramSkeletonItem::weekNumber)
                val attribution = attributionByOwner[owner]
                val nonSelection = nonSelectionByOwner[owner]
                beforeByWeek.toSortedMap().forEach { (week, beforeRows) ->
                    val afterRows = afterByWeek[week].orEmpty()
                    val b11 = nonSelection?.targetEvidence.orEmpty().map { evidence ->
                        JSONObject().put("targetId", evidence.targetId)
                            .put("classification", evidence.classification.name)
                            .put("dispositionStatus", evidence.disposition.status.name)
                            .put("selectedInstead", evidence.disposition.selectedInstead?.let(::ownerJson) ?: JSONObject.NULL)
                            .put("directCandidate", evidence.disposition.directTargetCandidate)
                            .put("selectionRequired", evidence.disposition.selectionRequired)
                            .put("targetCoveredBySelectedOwner", evidence.disposition.targetCoveredBySelectedOwner)
                            .put("reasons", JSONArray(evidence.disposition.reasons.map { it.name }.sorted()))
                            .put("firstDifferingField", evidence.disposition.firstDifferingField?.name ?: JSONObject.NULL)
                            .put("canonicalRole", evidence.disposition.canonicalSelectionRole)
                    }
                    val exactReplacementOwners = nonSelection?.targetEvidence.orEmpty().mapNotNull { evidence ->
                        val disposition = evidence.disposition
                        val role = disposition.canonicalSelectionRole?.takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                        if (evidence.classification != StimulusNonSelectionClassification.CANONICAL_REPLACEMENT ||
                            disposition.status != StimulusCandidateDispositionStatus.SELECTED ||
                            !disposition.directTargetCandidate || disposition.stableKey != owner.stableKey) return@mapNotNull null
                        StimulusPrescriptionOwnerIdentity(disposition.stableKey, role)
                    }.toSet()
                    val selectedAlternatives = comparison.selectionPlan.selectedCandidates.filter { candidate ->
                        StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole) in exactReplacementOwners
                    }
                    val alternativeEvidence = selectedAlternatives.map { candidate ->
                        val replacementOwner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                        val exactB6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { auth ->
                            auth.owner?.let { it.stableKey == replacementOwner.stableKey && it.selectionRole == replacementOwner.selectionRole } == true &&
                                auth.targetId in candidate.coveredTargetIds && auth.authorizedPrescription != null &&
                                nonSelection?.targetEvidence.orEmpty().any { evidence ->
                                    val disposition = evidence.disposition
                                    evidence.targetId == auth.targetId &&
                                        evidence.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT &&
                                        disposition.status == StimulusCandidateDispositionStatus.SELECTED &&
                                        disposition.directTargetCandidate && disposition.stableKey == replacementOwner.stableKey &&
                                        disposition.canonicalSelectionRole == replacementOwner.selectionRole
                                }
                        }
                        val exactScheduled = authorizationTrace?.authorized.orEmpty().filter {
                            it.item.stableKey == replacementOwner.stableKey && it.item.role == replacementOwner.selectionRole && it.item.material
                        }
                        val exactTargetsFromB11 = nonSelection?.targetEvidence.orEmpty().filter { evidence ->
                            evidence.disposition.stableKey == replacementOwner.stableKey &&
                                evidence.disposition.canonicalSelectionRole == replacementOwner.selectionRole
                        }.map { it.targetId }.toSet()
                        val exactB4TargetsPresent = exactTargetsFromB11.filter { targetId ->
                            when {
                                targetId.startsWith("QUALITY:") -> comparison.targetPlan.qualityTargets.any {
                                    "QUALITY:${it.quality.name}" == targetId
                                }
                                targetId.startsWith("MOVEMENT:") -> comparison.targetPlan.movementTargets.any { it.targetId == targetId }
                                targetId.startsWith("TASK:") -> comparison.targetPlan.taskTargets.any { "TASK:${it.task}" == targetId }
                                else -> false
                            }
                        }.sorted()
                        JSONObject().put("owner", ownerJson(replacementOwner))
                            .put("coveredTargets", JSONArray(candidate.coveredTargetIds.sorted()))
                            .put("b5Reasons", JSONArray(candidate.selectionReasons))
                            .put("exactB4Targets", JSONArray(nonSelection?.targetEvidence.orEmpty().filter { evidence ->
                                evidence.disposition.canonicalSelectionRole == replacementOwner.selectionRole &&
                                    evidence.disposition.stableKey == replacementOwner.stableKey
                            }.map { it.targetId }.distinct().sorted()))
                            .put("exactB4TargetsPresent", JSONArray(exactB4TargetsPresent))
                            .put("b6", JSONArray(exactB6.map { auth -> JSONObject()
                                .put("targetId", auth.targetId).put("status", auth.status.name)
                                .put("source", auth.source?.name ?: JSONObject.NULL)
                                .put("authorizedUnits", auth.authorizedPrescription?.sets?.size ?: 0)
                                .put("b4TargetPresent", auth.targetId in exactB4TargetsPresent)
                                .put("reasonCodes", JSONArray(auth.reasonCodes.sorted()))
                            }))
                            .put("scheduledDemand", JSONArray(exactScheduled.map { demand -> JSONObject()
                                .put("id", demand.id).put("fundingSource", demand.fundingSource.name)
                                .put("sourceReason", demand.sourceReason?.name ?: JSONObject.NULL)
                                .put("authorizedUnits", demand.prescription.sets.size)
                                .put("prescriptionMatchesExactB6", exactB6.any { it.authorizedPrescription == demand.prescription })
                            }))
                            .put("materializedSameWeekUnits", comparison.experimental.items.filter {
                                it.weekNumber == week && it.exerciseStableKey == replacementOwner.stableKey &&
                                    it.selectionRole == replacementOwner.selectionRole
                            }.sumOf { it.setPrescriptions.size })
                    }
                    val events = execution?.ownerAllocationProvenance.orEmpty().filter { it.owner == owner &&
                        listOfNotNull(it.before?.week, it.after?.week).contains(week)
                    }
                    val selectionTraces = comparison.selectionPlan.traces.filter { trace ->
                        (trace.selectedStableKey == owner.stableKey && trace.selectedSelectionRole == owner.selectionRole) ||
                            (trace.coveredByPreviouslySelectedStableKey == owner.stableKey &&
                                trace.coveredByPreviouslySelectedSelectionRole == owner.selectionRole) ||
                            (trace.candidateRejectionReasons.containsKey(owner.stableKey) &&
                                trace.candidateSelectionRoles[owner.stableKey] == owner.selectionRole)
                    }
                    val materializationTraces = comparison.materializationTraces.filter {
                        it.selectedStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                    }
                    val edges = execution?.ownerDisplacementEdges.orEmpty().filter { it.displacedOwner == owner &&
                        (it.week == null || it.week == week)
                    }
                    val completeCanonicalReplacement = b11.any { it.optString("classification") ==
                        StimulusNonSelectionClassification.CANONICAL_REPLACEMENT.name } && alternativeEvidence.any { alternative ->
                        val b6Rows = alternative.getJSONArray("b6")
                        val scheduledRows = alternative.getJSONArray("scheduledDemand")
                        val exactTargetB6 = (0 until b6Rows.length()).any { i ->
                            val auth = b6Rows.getJSONObject(i)
                            auth.optBoolean("b4TargetPresent") && auth.optInt("authorizedUnits") > 0 &&
                                (0 until scheduledRows.length()).any { j ->
                                    val scheduled = scheduledRows.getJSONObject(j)
                                    scheduled.optBoolean("prescriptionMatchesExactB6") &&
                                        scheduled.optInt("authorizedUnits") == auth.optInt("authorizedUnits")
                                } && alternative.optInt("materializedSameWeekUnits") == auth.optInt("authorizedUnits")
                        }
                        exactTargetB6
                    } && afterRows.isEmpty()
                    val exactDisplacement = edges.any { edge ->
                        edge.displacedUnits > 0 && edge.stage in events.map(OwnerAllocationProvenance::stage) &&
                            edge.causeOwner in comparison.addedOwnerIdentities && edge.targetIds.isNotEmpty() &&
                            edge.authorizedDemandIds.isNotEmpty()
                    }
                    val currentB7Unexplained = UNEXPLAINED_REMOVAL in attribution?.reasonCodes.orEmpty()
                    val classification = when {
                        currentB7Unexplained -> "UNEXPLAINED_REMOVAL"
                        completeCanonicalReplacement -> "B7_CLOSED_EXACT_CANONICAL_REPLACEMENT"
                        exactDisplacement -> "EXACT_ALLOCATOR_DISPLACEMENT_EVIDENCE"
                        else -> "B7_CLOSED_OTHER_OR_REQUIRES_REVIEW"
                    }
                    val row = JSONObject()
                        .put("case", spec.label)
                        .put("week", week)
                        .put("removedOwner", ownerJson(owner))
                        .put("controlRows", JSONArray(beforeRows.map(::itemJson)))
                        .put("experimentalSameOwnerRows", JSONArray(afterRows.map(::itemJson)))
                        .put("sameStableKeyOtherRoleRows", JSONArray(comparison.experimental.items.filter {
                            it.weekNumber == week && it.exerciseStableKey == owner.stableKey && it.selectionRole != owner.selectionRole
                        }.map(::itemJson)))
                        .put("b11TargetEvidence", JSONArray(b11))
                        .put("b5SelectionTrace", JSONArray(selectionTraces.map { trace -> JSONObject()
                            .put("targetId", trace.targetId).put("priority", trace.priority.name)
                            .put("selectedStableKey", trace.selectedStableKey ?: JSONObject.NULL)
                            .put("selectedSelectionRole", trace.selectedSelectionRole ?: JSONObject.NULL)
                            .put("coveredByStableKey", trace.coveredByPreviouslySelectedStableKey ?: JSONObject.NULL)
                            .put("coveredBySelectionRole", trace.coveredByPreviouslySelectedSelectionRole ?: JSONObject.NULL)
                            .put("candidateRejectionReason", trace.candidateRejectionReasons[owner.stableKey] ?: JSONObject.NULL)
                            .put("candidateRole", trace.candidateSelectionRoles[owner.stableKey] ?: JSONObject.NULL)
                            .put("reasonCodes", JSONArray(trace.reasonCodes.sorted()))
                        }))
                        .put("b5AlternativesWithExactEvidence", JSONArray(alternativeEvidence))
                        .put("materializationTrace", JSONArray(materializationTraces.map { trace -> JSONObject()
                            .put("targetId", trace.targetId).put("selectedAtB5", trace.selectedAtB5)
                            .put("directIdentityVerifiedAtSelection", trace.directIdentityVerifiedAtSelection ?: JSONObject.NULL)
                            .put("presentInFinalExperimentalSkeleton", trace.presentInFinalExperimentalSkeleton)
                            .put("finalWeeklyOccurrences", trace.finalWeeklyOccurrences).put("finalTotalSetUnits", trace.finalTotalSetUnits)
                            .put("directIdentityStillValid", trace.directIdentityStillValid)
                            .put("realizedTargetStatus", trace.realizedTargetStatus ?: JSONObject.NULL)
                            .put("reasonCodes", JSONArray(trace.reasonCodes.sorted()))
                        }))
                        .put("allocationEvents", JSONArray(events.map { it.toJson() }))
                        .put("displacementEdges", JSONArray(edges.map { it.toJson() }))
                        .put("b7Attribution", attribution?.let(::attributionJson) ?: JSONObject.NULL)
                        .put("proposedClassification", classification)
                    removalRows += row
                }
            }

            val changedShared = comparison.sharedOwnerIdentities.flatMap { owner ->
                val before = comparison.control.items.filter {
                    ownerIdentity(it) == owner
                }.groupBy(ProgramSkeletonItem::weekNumber)
                val after = comparison.experimental.items.filter { ownerIdentity(it) == owner }
                    .groupBy(ProgramSkeletonItem::weekNumber)
                (before.keys + after.keys).distinct().sorted().mapNotNull { week ->
                    val oldRows = before[week].orEmpty()
                    val newRows = after[week].orEmpty()
                    if (oldRows.map(::prescriptionFingerprint) == newRows.map(::prescriptionFingerprint)) null
                    else JSONObject().put("case", spec.label).put("week", week).put("owner", ownerJson(owner))
                        .put("before", JSONArray(oldRows.map(::itemJson)))
                        .put("after", JSONArray(newRows.map(::itemJson)))
                        .put("b5SelectedExactOwner", comparison.selectionPlan.selectedCandidates.any {
                            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                        })
                        .put("b6ExactOwnerAuthority", JSONArray(comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                            .filter { it.owner?.stableKey == owner.stableKey && it.owner.selectionRole == owner.selectionRole }
                            .map(::authorizationJson)))
                        .put("b7Attribution", attributionByOwner[owner]?.let(::attributionJson) ?: JSONObject.NULL)
                        .put("allocationEvents", JSONArray(execution?.ownerAllocationProvenance.orEmpty().filter { it.owner == owner &&
                            listOfNotNull(it.before?.week, it.after?.week).contains(week)
                        }.map { it.toJson() }))
                }
            }
            prescriptionChanges += changedShared

            val squatOwner = StimulusPrescriptionOwnerIdentity("barbell_back_squat", "STYLE_HEAVY_LOWER_KNEE")
            val squatChanges = changedShared.filter { it.optJSONObject("owner")?.optString("stableKey") == squatOwner.stableKey &&
                it.optJSONObject("owner")?.optString("selectionRole") == squatOwner.selectionRole
            }
            val squatAnchor = contexts[spec.label]?.state?.anchors.orEmpty().filter { it.stableKey == squatOwner.stableKey }
            val squatScheduling = authorizationTrace?.authorized.orEmpty().filter {
                it.item.stableKey == squatOwner.stableKey && it.item.role == squatOwner.selectionRole
            }
            val case = JSONObject()
                .put("case", spec.label)
                .put("generated", true)
                .put("route", generated.routeDecision.selectedSource.name)
                .put("generationTimeMs", generationMillisByCase[spec.label] ?: JSONObject.NULL)
                .put("executionAllocationAccounting", execution?.let { trace -> JSONObject()
                    .put("finalControllableCapacityUnits", trace.capacity.finalControllableUnits)
                    .put("scheduleFeasibleUnits", trace.capacity.scheduleFeasibleUnits)
                    .put("usefulDemandUnits", trace.capacity.usefulDemandUnits)
                    .put("continuityRequestedUnits", trace.continuityRequestedUnits)
                    .put("continuityAllocatedUnits", trace.continuityAllocatedUnits)
                    .put("materialGapRequestedUnits", trace.materialGapRequestedUnits)
                    .put("materialGapAllocatedUnits", trace.materialGapAllocatedUnits)
                } ?: JSONObject.NULL)
                .put("removedOwnerCount", removed.size)
                .put("unexplainedRemovedOwnerCount", attributionByOwner.values.count { UNEXPLAINED_REMOVAL in it.reasonCodes })
                .put("changedSharedPrescriptionOwnerWeeks", JSONArray(changedShared))
                .put("persona4RecentSquatDossier", if (spec.label == "persona4_recent") JSONObject()
                    .put("owner", ownerJson(squatOwner))
                    .put("controlRows", JSONArray(comparison.control.items.filter { ownerIdentity(it) == squatOwner }.map(::itemJson)))
                    .put("experimentalRows", JSONArray(comparison.experimental.items.filter { ownerIdentity(it) == squatOwner }.map(::itemJson)))
                    .put("exactB5Selected", comparison.selectionPlan.selectedCandidates.any {
                        it.stableKey == squatOwner.stableKey && it.selectionRole == squatOwner.selectionRole
                    })
                    .put("exactB6Authorities", JSONArray(comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter {
                        it.owner?.stableKey == squatOwner.stableKey && it.owner.selectionRole == squatOwner.selectionRole
                    }.map(::authorizationJson)))
                    .put("preparedUserAnchors", JSONArray(squatAnchor.map { JSONObject()
                        .put("stableKey", it.stableKey).put("sets", it.sets).put("sessions", it.sessions)
                        .put("style", it.style.name).put("canonicalPerformanceSource", it.canonicalPerformanceSource)
                        .put("posteriorObservationCount", it.posteriorObservationCount)
                    }))
                    .put("authorizedScheduling", JSONArray(squatScheduling.map { demand -> JSONObject()
                        .put("id", demand.id).put("continuity", demand.continuity)
                        .put("fundingSource", demand.fundingSource.name).put("sourceReason", demand.sourceReason?.name ?: JSONObject.NULL)
                        .put("sets", demand.prescription.sets.size).put("prescription", prescriptionJson(demand.prescription))
                    }))
                    .put("executionAllocationAccounting", execution?.let { trace -> JSONObject()
                        .put("finalControllableCapacityUnits", trace.capacity.finalControllableUnits)
                        .put("scheduleFeasibleUnits", trace.capacity.scheduleFeasibleUnits)
                        .put("usefulDemandUnits", trace.capacity.usefulDemandUnits)
                        .put("continuityRequestedUnits", trace.continuityRequestedUnits)
                        .put("continuityAllocatedUnits", trace.continuityAllocatedUnits)
                        .put("materialGapRequestedUnits", trace.materialGapRequestedUnits)
                        .put("materialGapAllocatedUnits", trace.materialGapAllocatedUnits)
                        .put("finiteMaterialCandidateRows", JSONArray(trace.finiteAllocationPriorityOrder.map { it.toJson() }))
                        .put("finiteSquatOwnerRow", trace.finiteAllocationPriorityOrder.singleOrNull {
                            it.owner.stableKey == squatOwner.stableKey && it.owner.selectionRole == squatOwner.selectionRole
                        }?.toJson() ?: JSONObject.NULL)
                    } ?: JSONObject.NULL)
                    .put("frequencyCapacityCandidates", JSONArray(decision?.frequencyDemand?.candidates.orEmpty().map {
                        it.toJson().put("selectionRole", it.item.role)
                    }))
                    .put("frequencyComputedCapacityUnits", decision?.frequencyDemand?.computedCapacity?.finalControllableUnits ?: JSONObject.NULL)
                    .put("baseAuthorizedSquatDemands", JSONArray(decision?.frequencyDemand?.baseAuthorized.orEmpty().filter {
                        it.item.stableKey == squatOwner.stableKey && it.item.role == squatOwner.selectionRole
                    }.map { demand -> JSONObject().put("id", demand.id).put("continuity", demand.continuity)
                        .put("units", demand.prescription.sets.size).put("prescription", prescriptionJson(demand.prescription))
                    }))
                    .put("ownerEvents", JSONArray(execution?.ownerAllocationProvenance.orEmpty().filter { it.owner == squatOwner }
                        .map { it.toJson() }))
                    .put("b7Attribution", attributionByOwner[squatOwner]?.let(::attributionJson) ?: JSONObject.NULL)
                    .put("diagnosis", when {
                        squatChanges.isEmpty() -> "NO_SHARED_OWNER_PRESCRIPTION_CHANGE"
                        squatB6Authorized(comparison, squatOwner) -> "B6_AUTHORIZED_CHANGE_REQUIRES_FIELD_LEVEL_REVIEW"
                        squatScheduling.any { it.continuity } && squatAnchor.any { it.sets == 4 } ->
                            "FOUR_SET_DEMAND_PRESENT_BEFORE_PLACEMENT; PERSONAL_ANCHOR_MATCHES_BUT_NO_EXACT_B5_B6_AUTHORITY"
                        else -> "FOUR_SET_DEMAND_PRESENT_BEFORE_PLACEMENT; CAUSAL_AUTHORITY_NOT_PROVEN"
                    }) else JSONObject.NULL)
                .put("b7", JSONObject()
                    .put("status", b7?.status?.name ?: "NOT_EVALUATED")
                    .put("reasons", JSONArray(b7?.reasonCodes.orEmpty().sorted()))
                    .put("changeProvenanceClosed", b7?.changeProvenanceClosed ?: JSONObject.NULL)
                    .put("collateralRegressionFree", b7?.collateralRegressionFree ?: JSONObject.NULL)
                    .put("targetOutcomes", JSONArray(b7?.targetOutcomes.orEmpty().map { outcome -> JSONObject()
                        .put("targetId", outcome.targetId).put("status", outcome.status.name)
                        .put("directlyAffected", outcome.directlyAffected).put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
                        .put("beforeDistance", outcome.controlWeeklyUnitsDistance ?: JSONObject.NULL)
                        .put("afterDistance", outcome.experimentalWeeklyUnitsDistance ?: JSONObject.NULL)
                    })))
                .put("b8", JSONObject().put("status", b8?.status?.name ?: "NOT_EVALUATED")
                    .put("scope", b8?.scope?.name ?: JSONObject.NULL).put("reasonCodes", JSONArray(b8?.reasonCodes.orEmpty().sorted())))
                .put("buildAccounting", JSONObject().put("control", generated.buildCounts.controlBuilds)
                    .put("experimental", generated.buildCounts.experimentalBuilds).put("total", generated.buildCounts.totalBuildInvocations)
                    .put("third", generated.buildCounts.thirdBuilds))
            cases.put(case)
            routes[generated.routeDecision.selectedSource.name] = (routes[generated.routeDecision.selectedSource.name] ?: 0) + 1
            b7Counts[b7?.status?.name ?: "NOT_EVALUATED"] = (b7Counts[b7?.status?.name ?: "NOT_EVALUATED"] ?: 0) + 1
            b7?.reasonCodes.orEmpty().forEach { reason -> b7Counts[reason] = (b7Counts[reason] ?: 0) + 1 }
            b8Counts[b8?.status?.name ?: "NOT_EVALUATED"] = (b8Counts[b8?.status?.name ?: "NOT_EVALUATED"] ?: 0) + 1
        }

        val unexplained = removalRows.filter { it.optString("proposedClassification") == "UNEXPLAINED_REMOVAL" }
        val uniqueRemovedOwners = removalRows.map { row ->
            listOf(row.optString("case"), row.optJSONObject("removedOwner")?.optString("stableKey"),
                row.optJSONObject("removedOwner")?.optString("selectionRole")).joinToString("|")
        }.distinct()
        val uniqueIdentities = removalRows.map { row ->
            listOf(row.optJSONObject("removedOwner")?.optString("stableKey"),
                row.optJSONObject("removedOwner")?.optString("selectionRole")).joinToString("|")
        }.distinct()
        val exactReplacementPairs = removalRows.mapNotNull { row ->
            val old = row.optJSONObject("removedOwner") ?: return@mapNotNull null
            val b11 = row.optJSONArray("b11TargetEvidence") ?: return@mapNotNull null
            val replacement = row.optJSONArray("b5AlternativesWithExactEvidence") ?: return@mapNotNull null
            if (b11.length() == 0 || replacement.length() == 0) null else
                "${row.optString("case")}|${old.optString("stableKey")}#${old.optString("selectionRole")}|" +
                    (0 until replacement.length()).mapNotNull { i -> replacement.optJSONObject(i)?.optJSONObject("owner") }
                        .joinToString(",") { "${it.optString("stableKey")}#${it.optString("selectionRole")}" }
        }.distinct()
        val unexplainedUnique = unexplained.map { row ->
            listOf(row.optString("case"), row.optJSONObject("removedOwner")?.optString("stableKey"),
                row.optJSONObject("removedOwner")?.optString("selectionRole")).joinToString("|")
        }.distinct()
        val unexplainedOwnerCaseRows = unexplained.groupBy { row ->
            listOf(row.optString("case"), row.optJSONObject("removedOwner")?.optString("stableKey"),
                row.optJSONObject("removedOwner")?.optString("selectionRole")).joinToString("|")
        }
        val canonicalCandidateOwnerCases = unexplainedOwnerCaseRows.filterValues { rows -> rows.any { row ->
            row.getJSONArray("b11TargetEvidence").let { evidence ->
                (0 until evidence.length()).any { evidence.getJSONObject(it).optString("classification") ==
                    StimulusNonSelectionClassification.CANONICAL_REPLACEMENT.name }
            }
        } }
        fun ownerCaseHasSelectedExactReplacement(rows: List<JSONObject>): Boolean = rows.any { row ->
            row.getJSONArray("b5AlternativesWithExactEvidence").length() > 0
        }
        fun ownerCaseHasExactB6(rows: List<JSONObject>): Boolean = rows.any { row ->
            val alts = row.getJSONArray("b5AlternativesWithExactEvidence")
            (0 until alts.length()).any { i ->
                val b6 = alts.getJSONObject(i).getJSONArray("b6")
                (0 until b6.length()).any { j -> b6.getJSONObject(j).optBoolean("b4TargetPresent") &&
                    b6.getJSONObject(j).optInt("authorizedUnits") > 0 }
            }
        }
        fun ownerCaseHasExactScheduledMaterialization(rows: List<JSONObject>): Boolean = rows.any { row ->
            val alts = row.getJSONArray("b5AlternativesWithExactEvidence")
            (0 until alts.length()).any { i ->
                val alt = alts.getJSONObject(i)
                val b6 = alt.getJSONArray("b6")
                val schedule = alt.getJSONArray("scheduledDemand")
                (0 until b6.length()).any { j ->
                    val auth = b6.getJSONObject(j)
                    auth.optBoolean("b4TargetPresent") && auth.optInt("authorizedUnits") > 0 &&
                        (0 until schedule.length()).any { k ->
                            val demand = schedule.getJSONObject(k)
                            demand.optBoolean("prescriptionMatchesExactB6") &&
                                demand.optInt("authorizedUnits") == auth.optInt("authorizedUnits") &&
                                alt.optInt("materializedSameWeekUnits") == auth.optInt("authorizedUnits")
                        }
                }
            }
        }
        return JSONObject()
            .put("phase", "C36")
            .put("title", "Execution accounting and change provenance closure")
            .put("startSha", startSha)
            .put("implementationSha", implementationSha)
            .put("accountingSemantics", JSONObject()
                .put("legacyFiniteAllocationPriorityOrder", "PRE-REGIONAL_OR_GENERIC_BUILDER_CANDIDATE_DIAGNOSTIC; NOT FINAL B4 REGIONAL FUNDING")
                .put("boundedMaterialDemand.allocatedUnits", "B4 REGIONAL DEMAND UNITS ACCEPTED BY FINITE CAPACITY")
                .put("authorizedScheduling", "WEEKLY EXACT AUTHORIZED INPUT TO PLACEMENT")
                .put("materializedUnits", "FINAL EXP PROGRAM SETS BY OWNER AND WEEK")
                .put("retainedUnits", "EXACT PRESCRIPTION SETS ALSO PRESENT UNDER SAME OWNER AND WEEK IN CONTROL; COMPARATOR ONLY"))
            .put("beforeAfter", JSONObject()
                .put("c35ReportedAccounting", JSONObject()
                    .put("rawResidualPerWeekUnits", c35Summary.getDouble("regionalRawResidualWeeklyUnits"))
                    .put("finiteFundedPerWeekUnits", c35Summary.getInt("regionalFundedUnits"))
                    .put("reportedUnfundedPerWeekUnits", c35Summary.getInt("regionalUnfundedUnits"))
                    .put("compatibleMaterializedPerWeekUnits", c35Summary.getInt("regionalMaterializedCompatibleUnits"))
                    .put("reportedUnmaterializedShortfallUnits", c35Summary.getInt("regionalUnmaterializedShortfallUnits")))
                .put("c36StageAlignedAccounting", JSONObject()
                    .put("legacyDiagnosticFundedPerWeekUnits", regionalFiniteDiagnosticFunded)
                    .put("exactFrequencyFundedPerWeekUnits", regionalFrequencyFunded)
                    .put("finalAuthorizedAndMaterializedPerWeekUnits", regionalWeekOneMaterialized)
                    .put("b4ToFinalScheduleShortfallPerWeekUnits", regionalRawResidual.toInt() - regionalFrequencyFunded)
                    .put("authorizationToPlacementShortfallAcrossHorizonUnits", regionalPlacementShortfall)
                    .put("diagnosticFundedButCapacityRejectedUnits", diagnosticFundedButCapacityRejectedUnits)
                    .put("diagnosticZeroButActuallyScheduledUnits", diagnosticZeroButActuallyScheduledUnits))
                .put("c34ToC36RemovalAccounting", JSONObject()
                    .put("c34UnexplainedCaseOwnerPairs", 47)
                    .put("c36UnexplainedOwnerWeekOccurrences", unexplained.size)
                    .put("c36UnexplainedCaseOwnerPairs", unexplainedUnique.size)))
            .put("cases", cases)
            .put("regionalOwnerWeeks", JSONArray(regionalRows))
            .put("removedOwnerWeeks", JSONArray(removalRows))
            .put("prescriptionChangeOwnerWeeks", JSONArray(prescriptionChanges))
            .put("summary", JSONObject()
                .put("generatedCases", cases.length())
                .put("regionalTargetRows", regionalRows.size)
                .put("regionalRawResidualPerTargetWeekUnits", regionalRawResidual)
                .put("regionalFiniteAllocationDiagnosticFundedPerTargetWeekUnits", regionalFiniteDiagnosticFunded)
                .put("regionalFrequencyFundedBaseUnitsPerTargetWeek", regionalFrequencyFunded)
                .put("regionalFinalAuthorizedWeeklyUnitsRepeatedAcrossHorizon", regionalFinalAuthorized)
                .put("regionalMaterializedUnitsAcrossHorizon", regionalMaterialized)
                .put("regionalFirstWeekMaterializedUnits", regionalWeekOneMaterialized)
                .put("regionalRetainedSameOwnerPrescriptionUnits", regionalRetained)
                .put("regionalNewOrChangedMaterialUnits", regionalNewMaterial)
                .put("regionalLegacyDiagnosticUnfundedUnits", regionalAllocationShortfall)
                .put("legacyDiagnosticFundedButCapacityRejectedRows", diagnosticFundedButCapacityRejectedRows)
                .put("legacyDiagnosticFundedButCapacityRejectedUnits", diagnosticFundedButCapacityRejectedUnits)
                .put("legacyDiagnosticZeroButActuallyScheduledRows", diagnosticZeroButActuallyScheduledRows)
                .put("legacyDiagnosticZeroButActuallyScheduledUnits", diagnosticZeroButActuallyScheduledUnits)
                .put("regionalB4ToAuthorizedSchedulingShortfallAcrossHorizon", regionalB4ToSchedulingShortfall)
                .put("regionalAuthorizationToPlacementShortfallUnits", regionalPlacementShortfall)
                .put("regionalOverrunUnits", materializedOverrun)
                .put("duplicatePhysicalRows", duplicatePhysicalRows)
                .put("unauthorizedMaterialUnits", unauthorizedMaterialUnits)
                .put("removedOwnerWeekOccurrences", removalRows.size)
                .put("uniqueRemovedCaseOwnerPairs", uniqueRemovedOwners.size)
                .put("uniqueRemovedOwnerIdentitiesAcrossCorpus", uniqueIdentities.size)
                .put("unexplainedRemovedOwnerWeekOccurrences", unexplained.size)
                .put("uniqueUnexplainedCaseOwnerPairs", unexplainedUnique.size)
                .put("uniqueUnexplainedOwnerCasesWithB11CanonicalReplacement", canonicalCandidateOwnerCases.size)
                .put("unexplainedOwnerCasesWithExactB5ReplacementSelected", canonicalCandidateOwnerCases.values.count(::ownerCaseHasSelectedExactReplacement))
                .put("unexplainedOwnerCasesWithExactB4B6Authority", canonicalCandidateOwnerCases.values.count(::ownerCaseHasExactB6))
                .put("unexplainedOwnerCasesWithExactB4B5B6ScheduleMaterialization", canonicalCandidateOwnerCases.values.count(::ownerCaseHasExactScheduledMaterialization))
                .put("exactDisplacementOwnerWeekOccurrences", removalRows.count { it.getJSONArray("displacementEdges").length() > 0 })
                .put("candidateReplacementPairsWithB11AndSelectedAlternative", exactReplacementPairs.size)
                .put("prescriptionChangeOwnerWeeks", prescriptionChanges.size)
                .put("unexplainedPrescriptionChangeOwnerWeeks", prescriptionChanges.count { row ->
                    row.optJSONObject("b7Attribution")?.optString("source") == StimulusExperimentalChangeAttributionSource.UNEXPLAINED.name
                })
                .put("finiteCapacityAttributedPrescriptionChangeOwnerWeeks", prescriptionChanges.count { row ->
                    row.optJSONObject("b7Attribution")?.optString("source") == StimulusExperimentalChangeAttributionSource.FINITE_CAPACITY_CONTINUITY_ALLOCATION.name
                })
                .put("routes", JSONObject(routes))
                .put("b7StatusesAndReasons", JSONObject(b7Counts))
                .put("b8Statuses", JSONObject(b8Counts))
            .put("buildAccounting", JSONObject()
                .put("control", records.mapNotNull { it.second?.buildCounts?.controlBuilds }.sum())
                .put("experimental", records.mapNotNull { it.second?.buildCounts?.experimentalBuilds }.sum())
                .put("total", records.mapNotNull { it.second?.buildCounts?.totalBuildInvocations }.sum())
                .put("third", records.mapNotNull { it.second?.buildCounts?.thirdBuilds }.sum())))
    }

    private fun squatB6Authorized(
        comparison: StimulusSelectionProgramComparison,
        owner: StimulusPrescriptionOwnerIdentity
    ): Boolean = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any {
        it.owner?.let { authOwner -> authOwner.stableKey == owner.stableKey && authOwner.selectionRole == owner.selectionRole } == true &&
            it.authorizedPrescription != null
    }

    private fun unchangedPrescriptionUnits(before: List<ProgramSkeletonItem>, after: List<ProgramSkeletonItem>): Int {
        val old = before.flatMap { it.setPrescriptions }.groupingBy(::setFingerprint).eachCount().toMutableMap()
        var matched = 0
        after.flatMap { it.setPrescriptions }.forEach { set ->
            val key = setFingerprint(set)
            val count = old[key] ?: 0
            if (count > 0) { matched++; old[key] = count - 1 }
        }
        return matched
    }

    private fun setFingerprint(set: ProgramSetPrescription) = listOf(
        set.reps, set.seconds, set.weightKg, set.loadState.name, set.targetRpeMin
    ).joinToString("|")

    private fun prescriptionFingerprint(item: ProgramSkeletonItem) = listOf(
        item.setPrescriptions.map(::setFingerprint), item.restSeconds, item.weightSource, item.prescription
    ).joinToString("#")

    private fun itemJson(item: ProgramSkeletonItem) = JSONObject()
        .put("stableKey", item.exerciseStableKey).put("selectionRole", item.selectionRole)
        .put("week", item.weekNumber).put("day", item.dayOfWeek).put("order", item.orderIndex)
        .put("sets", item.setCount).put("reps", item.reps).put("seconds", item.seconds)
        .put("loadKg", item.weightKg).put("loadAuthority", item.setPrescriptions.firstOrNull()?.loadState?.name)
        .put("targetRpeMin", item.setPrescriptions.firstOrNull()?.targetRpeMin ?: JSONObject.NULL)
        .put("restSeconds", item.restSeconds).put("durationSeconds", item.estimatedDurationSeconds)
        .put("weightSource", item.weightSource).put("prescription", item.prescription)
        .put("setPrescriptions", JSONArray(item.setPrescriptions.map { set -> JSONObject()
            .put("index", set.setIndex).put("reps", set.reps).put("seconds", set.seconds)
            .put("loadKg", set.weightKg).put("loadState", set.loadState.name)
            .put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
        }))

    private fun prescriptionJson(prescription: PlannedPrescription) = JSONObject()
        .put("sets", JSONArray(prescription.sets.map { set -> JSONObject()
            .put("index", set.setIndex).put("reps", set.reps).put("seconds", set.seconds)
            .put("loadKg", set.weightKg).put("loadState", set.loadState.name)
            .put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
        }))
        .put("restSeconds", prescription.restSeconds).put("weightSource", prescription.weightSource)
        .put("text", prescription.text)

    private fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
        .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)

    private fun authorizationJson(auth: StimulusPrescriptionAuthorization) = JSONObject()
        .put("targetId", auth.targetId).put("quality", auth.quality?.name)
        .put("owner", auth.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) })
        .put("status", auth.status.name).put("source", auth.source?.name)
        .put("authorizedPrescription", auth.authorizedPrescription?.let(::prescriptionJson) ?: JSONObject.NULL)
        .put("reasons", JSONArray(auth.reasonCodes.sorted()))

    private fun materializationJson(audit: StimulusPrescriptionMaterializationAudit) = JSONObject()
        .put("state", audit.state.name).put("executionAuthority", audit.executionAuthority.name)
        .put("authorizedWeeklyUnits", audit.authorizedWeeklySetUnits)
        .put("materializedWeeklyUnits", audit.materializedWeeklySetUnits)
        .put("compatibleWeeklyUnits", audit.targetCompatibleMaterializedUnits)
        .put("shortfall", audit.shortfall).put("overrun", audit.overrun)
        .put("maxWeeklyShortfall", audit.maximumWeeklyShortfall).put("maxWeeklyOverrun", audit.maximumWeeklyOverrun)
        .put("weeks", JSONArray(audit.weeklyAudits.map { week -> JSONObject()
            .put("week", week.weekNumber).put("authorized", week.authorizedSetUnits)
            .put("materialized", week.materializedSetUnits).put("compatible", week.targetCompatibleMaterializedUnits)
            .put("shortfall", week.shortfall).put("overrun", week.overrun).put("reasons", JSONArray(week.reasonCodes.sorted()))
        }))

    private fun attributionJson(attribution: StimulusExperimentalChangeAttribution) = JSONObject()
        .put("source", attribution.source.name).put("reasonCodes", JSONArray(attribution.reasonCodes.sorted()))
        .put("targetIds", JSONArray(attribution.targetIds.sorted()))
        .put("evidenceSources", JSONArray(attribution.evidenceSources.sorted()))
}
