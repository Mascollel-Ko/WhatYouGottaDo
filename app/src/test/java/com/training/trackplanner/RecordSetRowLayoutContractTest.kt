package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSetRowLayoutContractTest {
    @Test
    fun `confirmed weighted row keeps reps before kg before rpe`() {
        val source = source("RecordSetRow.kt")
        val compactBranch = source.substringAfter("if (set.confirmed && !isExpanded)").substringBefore("return")
        val reps = compactBranch.indexOf("\"\${set.reps}회\"")
        val weight = compactBranch.indexOf("\"\${formatWeight(set.weightKg)}kg\"")
        val rpe = compactBranch.indexOf("\"RPE\${formatRpe(it)}\"")

        assertTrue("reps chip must exist in confirmed layout", reps >= 0)
        assertTrue("weight chip must follow reps", weight > reps)
        assertTrue("RPE chip must follow weight", rpe > weight)
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}
