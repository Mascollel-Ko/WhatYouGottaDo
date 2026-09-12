package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.RepresentativeWeek
import java.util.UUID

internal enum class ProgramEditScope { ALL_WEEKS, INDIVIDUAL_WEEK }

/** User structural edits only. Never copy week one's existing prescription/binding to another week. */
internal object ProgramScopedEditor {
    fun supportsAll(plan: GeneratedProgramSkeleton)=RepresentativeWeek.deriveStructure(plan)!=null
    private fun targets(plan: GeneratedProgramSkeleton,id: String,scope: ProgramEditScope): Set<String> {
        if(scope==ProgramEditScope.INDIVIDUAL_WEEK) return setOf(id)
        val week=requireNotNull(RepresentativeWeek.deriveStructure(plan)) { "NON_ISOMORPHIC_EDITOR_DRAFT" }
        val atom=week.atomByLocalId.getValue(id)
        return week.originals.filterKeys { it.second==atom }.values.mapTo(mutableSetOf()) { it.localId }
    }
    fun delete(plan: GeneratedProgramSkeleton,id: String,scope: ProgramEditScope): GeneratedProgramSkeleton {
        val ids=targets(plan,id,scope)
        return plan.copy(items=plan.items.filterNot { it.localId in ids })
    }
    fun move(plan: GeneratedProgramSkeleton,id: String,day: Int,scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(day in 1..7)
        if(plan.items.first { it.localId==id }.dayOfWeek==day) return plan
        val ids=targets(plan,id,scope)
        require(plan.items.filter { it.localId in ids }.all { day in plan.weekDaySchedule[it.weekNumber].orEmpty() })
        return plan.copy(items=plan.items.map { row -> if(row.localId !in ids) row else row.copy(dayOfWeek=day,
            orderIndex=(plan.items.filter { it.weekNumber==row.weekNumber && it.dayOfWeek==day }.maxOfOrNull { it.orderIndex } ?: 0)+1) })
    }
    fun reorder(plan: GeneratedProgramSkeleton,id: String,offset: Int,scope: ProgramEditScope): GeneratedProgramSkeleton {
        val ids=targets(plan,id,scope)
        val changes=mutableMapOf<String,Int>()
        for(row in plan.items.filter { it.localId in ids }) {
            val peers=plan.items.filter { it.weekNumber==row.weekNumber && it.dayOfWeek==row.dayOfWeek }.sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
            val index=peers.indexOfFirst { it.localId==row.localId }
            val other=peers.getOrNull(index+offset) ?: continue
            changes[row.localId]=other.orderIndex; changes[other.localId]=row.orderIndex
        }
        return plan.copy(items=plan.items.map { row -> changes[row.localId]?.let { row.copy(orderIndex=it) } ?: row })
    }
    fun days(plan: GeneratedProgramSkeleton,week: Int,days: Set<Int>,scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(days.all { it in 1..7 })
        if(scope==ProgramEditScope.ALL_WEEKS) require(supportsAll(plan))
        val weeks=if(scope==ProgramEditScope.ALL_WEEKS) (1..plan.request.durationWeeks).toSet() else setOf(week)
        return plan.copy(items=plan.items.filterNot { it.weekNumber in weeks && it.dayOfWeek !in days },
            weekDaySchedule=plan.weekDaySchedule.mapValues { (key,old) -> if(key in weeks) days else old })
    }
    fun add(plan: GeneratedProgramSkeleton,item: ProgramSkeletonItem,scope: ProgramEditScope): GeneratedProgramSkeleton {
        require(plan.items.none { it.localId==item.localId })
        if(scope==ProgramEditScope.ALL_WEEKS) require(supportsAll(plan))
        val weeks=if(scope==ProgramEditScope.ALL_WEEKS) (1..plan.request.durationWeeks).toList() else listOf(item.weekNumber)
        val added=weeks.map { week -> item.copy(localId=if(week==item.weekNumber) item.localId else UUID.randomUUID().toString(),
            weekNumber=week,orderIndex=(plan.items.filter { it.weekNumber==week && it.dayOfWeek==item.dayOfWeek }.maxOfOrNull { it.orderIndex } ?: 0)+1,
            progressionBinding=null) }
        return plan.copy(items=plan.items+added,weekDaySchedule=plan.weekDaySchedule.mapValues { (week,days) -> if(week in weeks) days+item.dayOfWeek else days })
    }
}
