package com.training.trackplanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.EffectiveMuscleMetadataResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Audit fixture for the shipped screen and picker predicates against all bundled exercises. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachCoreSearchAudit20261125 {
    @Test fun countSearchMatchesAgainstCoreAuthority() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val canonical = CanonicalExerciseMetadataRepository(context)
        val exercises = canonical.exercises()
        val runtime = canonical.runtimeMetadataCatalog()
        val resolver = EffectiveMuscleMetadataResolver(canonical.muscleRoleResolver())
        val muscles = exercises.associate { it.stableKey to resolver.resolve(it, emptyList()) }
        val translator = MetadataTranslator.from(context)
        val presenter = CanonicalMusclePresentation.from(context)
        val catalogue = MetadataDisplayCatalogue.from(context)
        val core = canonical.coreCatalog()
        val byKey = exercises.associateBy { it.stableKey }

        fun pickerMatches(key: String, query: String) = byKey.getValue(key).matchesMetadataQuery(
            query, translator, muscles[key], presenter, catalogue, runtime.resolveByStableKey(key)
        )
        fun screenMatches(key: String, query: String) =
            ExerciseSubcategoryMapper.matchesSearch(byKey.getValue(key), query) || pickerMatches(key, query)
        fun coreClass(key: String) = core.resolve(key)?.coreClass ?: CoreClass.NONE

        assertEquals(243, exercises.size)
        val curls = listOf("ex_281347da", "ex_2892da5a", "ex_3d5719de")
        for (key in curls) {
            assertEquals(CoreClass.HIDDEN_LOW, coreClass(key))
            assertTrue(muscles.getValue(key).canonical.forRole(CanonicalMuscleRole.STABILIZER)
                .any { it.muscleCode == "ANTERIOR_CORE" })
            assertTrue(pickerMatches(key, "코어"))
            assertTrue(screenMatches(key, "코어"))
        }
        assertFalse(ExerciseSubcategoryMapper.matchesSearch(byKey.getValue("ex_281347da"), "코어"))
        assertTrue(ExerciseSubcategoryMapper.matchesSearch(byKey.getValue("ex_3d5719de"), "코어"))
        assertEquals(CoreClass.DIRECT, coreClass("ex_d5bdffe1"))
        val queries = listOf("코어", "core", "복근", "복직근", "ANTERIOR_CORE", "LATERAL_CORE")
        for (query in queries) {
            val screen = byKey.keys.filter { screenMatches(it, query) }
            val picker = byKey.keys.filter { pickerMatches(it, query) }
            fun counts(keys: List<String>) = keys.groupingBy(::coreClass).eachCount().toSortedMap()
            println("query=$query screen=${counts(screen)} picker=${counts(picker)}")
            if (query == "코어") {
                val curlsShown = screen.filter { byKey.getValue(it).name.contains("컬") }
                println("curlMatches=" + curlsShown.joinToString(", ") { byKey.getValue(it).name })
                val missingDirect = byKey.keys.filter { coreClass(it) == CoreClass.DIRECT && it !in screen }
                println("directMissing=" + missingDirect.joinToString(", ") { byKey.getValue(it).name })
            }
        }
        val coreCategory = exercises.filter { exercise ->
            val key = exercise.stableKey
            val groups = ExerciseSubcategoryMapper.categoriesFor(
                exercise, runtime.resolveByStableKey(key), muscles[key]
            )
            ExerciseSubcategory.STRENGTH_CORE in groups || ExerciseSubcategory.FUNCTIONAL_CORE_CONTROL in groups
        }
        println("coreSubcategory=" + coreCategory.groupingBy { coreClass(it.stableKey) }.eachCount().toSortedMap())
        println("curlSubcategoryFlags=" + curls.associateWith { key -> coreCategory.any { it.stableKey == key } })

        System.getProperty("audit.coreSearchCsv")?.let { path ->
            val coreCategoryKeys = coreCategory.mapTo(mutableSetOf()) { it.stableKey }
            fun quoted(value: Any) = "\"" + value.toString().replace("\"", "\"\"") + "\""
            File(path).writeText(buildString {
                append('\uFEFF')
                appendLine("stableKey,exerciseName,coreClass,exerciseScreen,picker,coreSubcategory,detailTextHit,coreMuscleRoles")
                for (key in byKey.keys.sorted()) {
                    val exercise = byKey.getValue(key)
                    val coreMuscleRoles = muscles.getValue(key).canonical.relations
                        .filter { it.muscleCode in setOf("ANTERIOR_CORE", "LATERAL_CORE") }
                        .joinToString(" | ") { "${it.muscleCode}:${it.role.name}" }
                    appendLine(listOf(key, exercise.name, coreClass(key), screenMatches(key, "코어"),
                        pickerMatches(key, "코어"), key in coreCategoryKeys,
                        ExerciseSubcategoryMapper.matchesSearch(exercise, "코어"), coreMuscleRoles)
                        .joinToString(",", transform = ::quoted))
                }
            })
        }
    }
}
