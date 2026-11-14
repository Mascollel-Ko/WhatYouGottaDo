package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.AuthorizedAtomOrigin
import com.training.trackplanner.data.v22.BBalanced12WeekEngine
import com.training.trackplanner.data.v22.BVolumePattern
import com.training.trackplanner.data.v22.reassessBBalancedAfterEdit
import java.util.UUID
import org.json.JSONObject

internal enum class ProgramEditScope { ALL_WEEKS, INDIVIDUAL_WEEK }

internal const val PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE = "전체 주에 똑같이 들어가 있는 운동이 아닙니다"

/** Shared validation and mutation boundary for all-weeks program edits. */
internal object ProgramScopedEditor {
    private data class Identity(
        val exerciseStableKey: String,
        val stableKey: String,
        val logicalExerciseId: String,
        val variant: String,
        val placementDay: Int
    )

    private data class SetDose(
        val setIndex: Int,
        val reps: Int,
        val weightKg: Double,
        val seconds: Int,
        val targetRpeMin: Double?,
        val loadState: ProgramLoadState
    )

    private data class Prescription(
        val restSeconds: Int,
        val prescription: String,
        val setCount: Int,
        val reps: Int,
        val weightKg: Double,
        val seconds: Int,
        val taskProtocolSemanticsJson: String?,
        val sets: List<SetDose>
    )

    /** Non-steady B blocks never expose all-weeks editing. Legacy plans keep fail-closed item checks. */
    fun supportsAll(plan: GeneratedProgramSkeleton): Boolean {
        val rawMetadata = plan.planningMetadataJson ?: return true
        val metadata = runCatching { JSONObject(rawMetadata) }.getOrNull()
            ?: return !rawMetadata.contains(BBalanced12WeekEngine.BLOCK_SCHEMA)
        if (metadata.optString("schema") != BBalanced12WeekEngine.BLOCK_SCHEMA) return true
        val specs = metadata.optJSONArray("blockSpecs") ?: return false
        if (specs.length() == 0) return false
        return (0 until specs.length()).all { index ->
            runCatching {
                BVolumePattern.valueOf(specs.getJSONObject(index).getString("pattern")) == BVolumePattern.STEADY
            }.getOrDefault(false)
        }
    }

    fun canEditAll(plan: GeneratedProgramSkeleton, id: String): Boolean =
        allWeekTargets(plan, id) != null

    /**
     * Explicit user action, NOT a side effect of selecting ALL_WEEKS edit scope:
     * replicate one chosen B steady-week's full schedule and prescription to every week.
     * The user must confirm this destructive replacement in the UI.
     *
     * Each copied occurrence has a fresh local/logical identity, while progression
     * track membership intentionally follows the selected week's exercise. A manual
     * OFF remains OFF. Reconciliation preserves linked sessions without guessing dates.
     */
    fun copyBWeekToAll(plan: GeneratedProgramSkeleton, sourceWeek: Int): GeneratedProgramSkeleton {
        require(supportsAll(plan)) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
        require(plan.planningMetadataJson?.contains("B_BALANCED") == true) {
            "전체 주 구성 복사는 B 프로그램에서만 지원됩니다."
        }
        val weeks = (1..plan.request.durationWeeks).toList()
        require(sourceWeek in weeks)
        val source = plan.items.filter { it.weekNumber == sourceWeek }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
        require(source.isNotEmpty()) { "복사할 주차에 운동이 없습니다." }
        val days = plan.resolvedWeekDaySchedule()[sourceWeek]
            ?: source.map { it.dayOfWeek }.toSet()
        val cloned = weeks.flatMap { week ->
            source.map { item ->
                if (week == sourceWeek) item else item.copy(
                    localId = UUID.randomUUID().toString(),
                    weekNumber = week,
                    progressionBinding = item.progressionBinding?.copy(
                        logicalItemId = UUID.randomUUID().toString(),
                        persisted = false
                    )
                )
            }
        }
        val draft = plan.copy(
            items = cloned,
            weekDaySchedule = weeks.associateWith { days }
        )
        val eligible = cloned.filter { it.progressionBinding != null }
            .mapTo(mutableSetOf()) { it.exerciseStableKey }
        return draft.reconcileProgression(eligible).reassessBBalancedAfterEdit()
    }

    private fun identity(plan: GeneratedProgramSkeleton, row: ProgramSkeletonItem): Identity {
        val origin = plan.personalizedDecision?.authorizedScheduling?.localOrigins?.get(row.localId)
        val stableKey = row.stableKey.ifBlank { row.exerciseStableKey }
        val logicalId = origin?.logicalId() ?: buildString {
            append("stable:").append(stableKey)
            append("|slot:").append(row.requestedTemplateSlot)
            append("|role:").append(row.selectionRole)
        }
        return Identity(row.exerciseStableKey, stableKey, logicalId, row.progressionVariant, row.dayOfWeek)
    }

    private fun AuthorizedAtomOrigin.logicalId(): String =
        "authority:$authorizedDemandId|split:$splitGroupId|chunk:${splitChunkIndex ?: -1}"

    private fun prescription(row: ProgramSkeletonItem): Prescription = Prescription(
        restSeconds = row.restSeconds,
        prescription = row.prescription,
        setCount = row.setCount,
        reps = row.reps,
        weightKg = row.weightKg,
        seconds = row.seconds,
        taskProtocolSemanticsJson = row.taskProtocolSemanticsJson,
        sets = ProgramSetPrescriptionResolver.resolve(row).map {
            SetDose(it.setIndex, it.reps, it.weightKg, it.seconds, it.targetRpeMin, it.loadState)
        }
    )

    private fun allWeekTargets(plan: GeneratedProgramSkeleton, id: String): List<ProgramSkeletonItem>? {
        if (!supportsAll(plan)) return null
        val selected = plan.items.singleOrNull { it.localId == id } ?: return null
        val selectedIdentity = identity(plan, selected)
        val selectedPrescription = prescription(selected)
        val weeks = 1..plan.request.durationWeeks.coerceAtLeast(1)
        return weeks.map { week ->
            val matches = plan.items.filter { it.weekNumber == week && identity(plan, it) == selectedIdentity }
            matches.singleOrNull()?.takeIf { prescription(it) == selectedPrescription } ?: return null
        }
    }

    private fun targets(plan: GeneratedProgramSkeleton, id: String, scope: ProgramEditScope): Set<String> {
        if (scope == ProgramEditScope.INDIVIDUAL_WEEK) {
            require(plan.items.any { it.localId == id }) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
            return setOf(id)
        }
        return requireNotNull(allWeekTargets(plan, id)) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
            .mapTo(linkedSetOf(), ProgramSkeletonItem::localId)
    }

    private fun requireTargetsUnambiguous(
        plan: GeneratedProgramSkeleton,
        targetIds: Set<String>
    ): GeneratedProgramSkeleton {
        val ambiguous = plan.items.filter { it.localId in targetIds }.any { target ->
            plan.items.count { it.weekNumber == target.weekNumber && identity(plan, it) == identity(plan, target) } != 1
        }
        require(!ambiguous) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
        return plan
    }

    private fun requireSameDayStructure(
        plan: GeneratedProgramSkeleton,
        weeks: Collection<Int>,
        day: Int
    ) {
        val structures = weeks.map { week ->
            plan.items.filter { it.weekNumber == week && it.dayOfWeek == day }
                .sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
                .map { identity(plan, it) }
        }
        require(structures.distinct().size == 1) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
    }

    fun update(
        plan: GeneratedProgramSkeleton,
        id: String,
        updated: ProgramSkeletonItem,
        scope: ProgramEditScope
    ): GeneratedProgramSkeleton {
        require(updated.localId == id)
        val ids = targets(plan, id, scope)
        if (scope == ProgramEditScope.INDIVIDUAL_WEEK) {
            return plan.upsertDraftItem(updated).reassessBBalancedAfterEdit()
        }
        val next = plan.copy(items = plan.items.map { row ->
            if (row.localId !in ids) row else row.copy(
                restSeconds = updated.restSeconds,
                prescription = updated.prescription,
                setCount = updated.setCount,
                reps = updated.reps,
                weightKg = updated.weightKg,
                seconds = updated.seconds,
                weightSource = updated.weightSource,
                setPrescriptions = updated.setPrescriptions,
                taskProtocolSemanticsJson = updated.taskProtocolSemanticsJson
            )
        })
        return requireTargetsUnambiguous(next, ids).reassessBBalancedAfterEdit()
    }

    fun delete(plan: GeneratedProgramSkeleton, id: String, scope: ProgramEditScope): GeneratedProgramSkeleton {
        val ids = targets(plan, id, scope)
        return plan.copy(items = plan.items.filterNot { it.localId in ids }).reassessBBalancedAfterEdit()
    }

    fun move(plan: GeneratedProgramSkeleton, id: String, day: Int, scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(day in 1..7)
        if (plan.items.first { it.localId == id }.dayOfWeek == day) return plan
        val ids = targets(plan, id, scope)
        val targetRows = plan.items.filter { it.localId in ids }
        require(targetRows.all { day in plan.weekDaySchedule[it.weekNumber].orEmpty() })
        if (scope == ProgramEditScope.ALL_WEEKS) {
            requireSameDayStructure(plan, targetRows.map(ProgramSkeletonItem::weekNumber), day)
        }
        val next = plan.copy(items = plan.items.map { row ->
            if (row.localId !in ids) row else row.copy(
                dayOfWeek = day,
                orderIndex = (plan.items.filter { it.weekNumber == row.weekNumber && it.dayOfWeek == day }
                    .maxOfOrNull { it.orderIndex } ?: 0) + 1
            )
        })
        return requireTargetsUnambiguous(next, ids).reassessBBalancedAfterEdit()
    }

    fun reorder(plan: GeneratedProgramSkeleton, id: String, offset: Int, scope: ProgramEditScope): GeneratedProgramSkeleton {
        val ids = targets(plan, id, scope)
        val changes = mutableMapOf<String, Int>()
        val neighborIdentities = mutableSetOf<Identity>()
        for (row in plan.items.filter { it.localId in ids }) {
            val peers = plan.items.filter { it.weekNumber == row.weekNumber && it.dayOfWeek == row.dayOfWeek }
                .sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
            val index = peers.indexOfFirst { it.localId == row.localId }
            val other = peers.getOrNull(index + offset) ?: continue
            if (scope == ProgramEditScope.ALL_WEEKS) {
                require(canEditAll(plan, other.localId)) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
                neighborIdentities += identity(plan, other)
            }
            changes[row.localId] = other.orderIndex
            changes[other.localId] = row.orderIndex
        }
        if (scope == ProgramEditScope.ALL_WEEKS) {
            require(changes.size == ids.size * 2 && neighborIdentities.size == 1) {
                PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE
            }
        }
        return plan.copy(items = plan.items.map { row ->
            changes[row.localId]?.let { row.copy(orderIndex = it) } ?: row
        }).reassessBBalancedAfterEdit()
    }

    fun days(plan: GeneratedProgramSkeleton, week: Int, days: Set<Int>, scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(days.all { it in 1..7 })
        if (scope == ProgramEditScope.ALL_WEEKS) {
            require(supportsAll(plan)) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
            val schedules = (1..plan.request.durationWeeks).map { plan.weekDaySchedule[it].orEmpty() }
            require(schedules.distinct().size == 1) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
            val removed = plan.items.filter { it.weekNumber == week && it.dayOfWeek !in days }
            require(removed.all { canEditAll(plan, it.localId) }) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
        }
        val weeks = if (scope == ProgramEditScope.ALL_WEEKS)
            (1..plan.request.durationWeeks).toSet() else setOf(week)
        return plan.copy(
            items = plan.items.filterNot { it.weekNumber in weeks && it.dayOfWeek !in days },
            weekDaySchedule = plan.weekDaySchedule.mapValues { (key, old) -> if (key in weeks) days else old }
        ).reassessBBalancedAfterEdit()
    }

    fun add(plan: GeneratedProgramSkeleton, item: ProgramSkeletonItem, scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(plan.items.none { it.localId == item.localId })
        if (scope == ProgramEditScope.ALL_WEEKS) require(supportsAll(plan)) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
        val weeks = if (scope == ProgramEditScope.ALL_WEEKS)
            (1..plan.request.durationWeeks).toList() else listOf(item.weekNumber)
        if (scope == ProgramEditScope.ALL_WEEKS) {
            val schedules = weeks.map { plan.weekDaySchedule[it].orEmpty() }
            require(schedules.distinct().size == 1) { PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE }
            requireSameDayStructure(plan, weeks, item.dayOfWeek)
        }
        val added = weeks.map { week ->
            item.copy(
                localId = if (week == item.weekNumber) item.localId else UUID.randomUUID().toString(),
                weekNumber = week,
                orderIndex = (plan.items.filter { it.weekNumber == week && it.dayOfWeek == item.dayOfWeek }
                    .maxOfOrNull { it.orderIndex } ?: 0) + 1,
                progressionBinding = null
            )
        }
        val next = plan.copy(
            items = plan.items + added,
            weekDaySchedule = plan.weekDaySchedule.mapValues { (week, days) ->
                if (week in weeks) days + item.dayOfWeek else days
            }
        )
        return requireTargetsUnambiguous(next, added.mapTo(linkedSetOf(), ProgramSkeletonItem::localId))
            .reassessBBalancedAfterEdit()
    }
}
