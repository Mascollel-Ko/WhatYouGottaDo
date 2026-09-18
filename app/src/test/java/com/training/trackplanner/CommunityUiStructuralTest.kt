package com.training.trackplanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommunityUiStructuralTest {
    @Test fun homePlacesCommunityBetweenSummaryAndCondition() {
        val source = source("src/main/java/com/training/trackplanner/HomeScreen.kt")
        val summary = source.indexOf("TodaySummaryCard(summary)")
        val community = source.indexOf("CommunityEntryCard(onClick = onOpenCommunity)")
        val condition = source.indexOf("HomeDailyCheckInCard(")
        assertTrue(summary >= 0 && community > summary && condition > community)
    }

    @Test fun communityUsesASeparateRouteAndGoogleLoginState() {
        val main = source("src/main/java/com/training/trackplanner/MainActivity.kt")
        val screen = source("src/main/java/com/training/trackplanner/CommunityScreen.kt")
        assertTrue(main.contains("communityRoute"))
        assertTrue(main.contains("CommunityScreen("))
        assertTrue(screen.contains("community_auth_required"))
        assertTrue(screen.contains("viewModel::signIn"))
    }

    @Test fun communityStringsHaveKoreanAndEnglishResources() {
        val ko = source("src/main/res/values/strings.xml")
        val en = source("src/main/res/values-en/strings.xml")
        listOf("community_entry", "community_nickname_change", "community_friend_code", "community_import", "community_publish_weekly")
            .forEach { key ->
                assertTrue(ko.contains("name=\"$key\""))
                assertTrue(en.contains("name=\"$key\""))
        }
    }

    @Test fun communityLabelSelectorsUseReusableMultiSelectDropdowns() {
        val screen = source("src/main/java/com/training/trackplanner/CommunityScreen.kt")
        assertTrue(screen.contains("CommunityMultiSelectDropdown"))
        assertTrue(screen.contains("community_filter_functional_goals"))
        assertTrue(screen.contains("community_filter_badminton_goals"))
        assertTrue(!screen.contains("FilterChip"))
        assertTrue(!screen.contains("ChoiceRow"))
    }

    @Test fun communityProgramsStartWithFeedAndCollapsedSearch() {
        val screen = source("src/main/java/com/training/trackplanner/CommunityScreen.kt")
        assertTrue(screen.contains("var searchPanelExpanded by rememberSaveable { mutableStateOf(false) }"))
        assertTrue(screen.contains("if (searchPanelExpanded) item"))
        assertTrue(screen.indexOf("if (programs.isEmpty())") < screen.indexOf("if (localPrograms.isNotEmpty())"))
        assertTrue(screen.contains("community_search_filters"))
        assertTrue(screen.contains("searchPanelExpanded = false"))
    }

    @Test fun communityDropdownEmptySummariesAreContextual() {
        val screen = source("src/main/java/com/training/trackplanner/CommunityScreen.kt")
        assertTrue(screen.contains("community_filter_select"))
        assertTrue(screen.contains("community_filter_none"))
        assertTrue(screen.contains("community_filter_all"))
    }

    private fun source(relativePath: String): String = sequenceOf(
        File(relativePath), File("app", relativePath), File("..", relativePath)
    ).first(File::isFile).readText()
}
