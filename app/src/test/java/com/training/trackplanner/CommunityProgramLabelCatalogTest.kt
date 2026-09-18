package com.training.trackplanner

import com.training.trackplanner.data.CommunityProgramLabelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommunityProgramLabelCatalogTest {
    @Test fun publicationLabelsAreDeduplicatedAndStable() {
        val labels = CommunityProgramLabelCatalog.normalize(
            listOf("lower_body", "UPPER_BODY", "LOWER_BODY"),
            listOf("STRENGTH", "HYPERTROPHY"),
            listOf("BODY_COORDINATION", "EXPLOSIVE_ACCELERATION"),
            emptyList()
        )
        assertEquals(listOf("UPPER_BODY", "LOWER_BODY"), labels.strengthRegions)
        assertEquals(listOf("HYPERTROPHY", "STRENGTH"), labels.strengthGoals)
        assertEquals(listOf("EXPLOSIVE_ACCELERATION", "BODY_COORDINATION"), labels.functionalGoals)
        assertEquals(emptyList<String>(), labels.badmintonGoals)
    }

    @Test fun strengthDimensionsAreRequiredAndOptionalDimensionsMayBeEmpty() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityProgramLabelCatalog.normalize(emptyList(), listOf("STRENGTH"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommunityProgramLabelCatalog.normalize(listOf("UPPER_BODY"), emptyList())
        }
        val labels = CommunityProgramLabelCatalog.normalize(listOf("UPPER_BODY"), listOf("STRENGTH"))
        assertEquals(emptyList<String>(), labels.functionalGoals)
        assertEquals(emptyList<String>(), labels.badmintonGoals)
    }

    @Test fun unsupportedAndNotIncludedPublicationValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityProgramLabelCatalog.normalize(listOf("NOT_INCLUDED"), listOf("STRENGTH"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommunityProgramLabelCatalog.normalize(listOf("UPPER_BODY"), listOf("UNKNOWN"))
        }
    }
}
