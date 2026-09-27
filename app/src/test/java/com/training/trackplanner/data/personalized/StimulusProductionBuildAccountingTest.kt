package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Test

class StimulusProductionBuildAccountingTest {
    @Test
    fun typedInvocationBoundaryCountsAThirdBuildWithoutCategorizingIt() {
        val counts = MutableStimulusProductionBuildCounts()
        counts.recordProgramBuildInvocation(StimulusProductionBuildKind.CONTROL)
        counts.recordProgramBuildInvocation(StimulusProductionBuildKind.EXPERIMENTAL)
        counts.recordProgramBuildInvocation(StimulusProductionBuildKind.OTHER)

        val snapshot = counts.snapshot()
        assertEquals(3, snapshot.totalBuildInvocations)
        assertEquals(1, snapshot.controlBuilds)
        assertEquals(1, snapshot.experimentalBuilds)
        assertEquals(1, snapshot.thirdBuilds)
        assertEquals(1, snapshot.otherBuilds)
    }

    @Test
    fun normalTwoBuildAccountingHasNoUnclassifiedInvocation() {
        val counts = MutableStimulusProductionBuildCounts()
        counts.recordProgramBuildInvocation(StimulusProductionBuildKind.CONTROL)
        counts.recordProgramBuildInvocation(StimulusProductionBuildKind.EXPERIMENTAL)

        val snapshot = counts.snapshot()
        assertEquals(2, snapshot.totalBuildInvocations)
        assertEquals(1, snapshot.controlBuilds)
        assertEquals(1, snapshot.experimentalBuilds)
        assertEquals(0, snapshot.thirdBuilds)
    }
}
