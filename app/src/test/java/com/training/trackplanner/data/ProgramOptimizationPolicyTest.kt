package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramOptimizationPolicyTest {
    @Test
    fun optimizationImprovesHighLowerClusterWithinIterationLimit() {
        val skeleton = skeleton(highCluster = true)
        val before = ProgramEvaluationPolicy().evaluate(skeleton)

        val optimized = ProgramOptimizationPolicy().optimize(skeleton)
        val after = optimized.evaluation ?: error("missing optimized evaluation")

        assertTrue("optimization should improve score", after.overallScore > before.overallScore)
        assertTrue("optimization must stay bounded", optimized.optimizationTrace.size in 1..3)
        assertTrue("repair should be accepted", optimized.optimizationTrace.any(ProgramOptimizationTrace::accepted))
        assertFalse("cluster should be resolved",
            after.issues.any { it.type == ProgramEvaluationIssueType.HIGH_LOWER_BODY_FATIGUE_CLUSTER })
        assertTrue("summary should mention fatigue redistribution",
            optimized.optimizationSummary.messages.any { it.contains("하체 피로") })
        assertEquals("repair must not add exercises", skeleton.items.map(ProgramSkeletonItem::exerciseId).toSet(),
            optimized.items.map(ProgramSkeletonItem::exerciseId).toSet())
    }

    @Test
    fun optimizationKeepsSkeletonWhenNoRepairImprovesIt() {
        val skeleton = skeleton(highCluster = false)

        val optimized = ProgramOptimizationPolicy().optimize(skeleton)

        assertTrue("healthy skeleton should not need repair", optimized.optimizationTrace.isEmpty())
        assertEquals(skeleton.items, optimized.items)
    }

    private fun skeleton(highCluster: Boolean): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "optimization fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = setOf("바벨", "덤벨", "머신", "케이블", "맨몸"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4
        )
        val weekPlans = (1..4).map { week ->
            ProgramWeekPlan(
                weekIndex = week,
                weekType = ProgramWeekType.BUILD.name,
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false
            )
        }
        val days = listOf(1, 2, 4, 6, 7)
        val items = weekPlans.flatMap { week ->
            days.flatMap { day ->
                val clustered = highCluster && week.weekIndex == 1 && day in setOf(1, 2)
                listOf(
                    item(week.weekIndex, day, 1, clustered),
                    item(week.weekIndex, day, 2, highLower = false, slot = ProgramSlotId.UPPER_PULL_ANCHOR),
                    item(week.weekIndex, day, 3, highLower = false, role = ProgramExerciseRole.TRANSFER,
                        slot = ProgramSlotId.BADMINTON_FOOTWORK_REACTION, transfer = "SUPPORTIVE"),
                    item(week.weekIndex, day, 4, highLower = false, role = ProgramExerciseRole.CORE,
                        slot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY)
                )
            }
        }
        return GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = request.durationWeeks * 7,
            request = request,
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            weekPlans = weekPlans,
            items = items
        )
    }

    private fun item(
        week: Int,
        day: Int,
        order: Int,
        highLower: Boolean,
        role: ProgramExerciseRole = ProgramExerciseRole.ANCHOR,
        slot: ProgramSlotId = ProgramSlotId.LOWER_SQUAT_PATTERN,
        transfer: String = "GENERAL"
    ): ProgramSkeletonItem = ProgramSkeletonItem(
        localId = "$week-$day-$order",
        weekNumber = week,
        dayOfWeek = day,
        orderIndex = order,
        exerciseId = (week * 100 + day * 10 + order).toLong(),
        exerciseName = if (highLower) "바벨 스쿼트" else "덤벨 보조 운동 $order",
        category = "근력",
        restSeconds = 60,
        prescription = "3x10",
        setCount = 3,
        reps = 10,
        weightKg = 0.0,
        seconds = 0,
        selectionReason = "",
        weightSource = "",
        trainingSlot = if (highLower) ProgramTrainingSlot.LOWER_STRENGTH_HEAVY.name else ProgramTrainingSlot.UPPER_STRENGTH.name,
        dayIntensity = if (highLower) ProgramDayIntensity.HARD.name else ProgramDayIntensity.MODERATE.name,
        stableKey = "fixture_${week}_${day}_$order",
        selectionRole = role.name,
        movementFamily = slot.name,
        redundancyGroup = slot.name,
        stressMagnitudeHint = if (highLower) "VERY_HIGH" else "LOW",
        neuromuscularStressLevel = if (highLower) "VERY_HIGH" else "LOW",
        jointTendonImpactStressLevel = if (highLower) "HIGH" else "LOW",
        badmintonTransferLevel = transfer,
        primarySlotCapabilities = listOf(slot.name),
        requestedTemplateSlot = slot.name
    )
}
