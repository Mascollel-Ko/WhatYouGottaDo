package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.*
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LegacyAutoFrozenParityTest {
    @Test fun all360CasesMatchHistoricalOutputAndMaterializeEveryActiveDay() {
        val expected = frozenFixture("legacy_f5cc0ac_360.csv").readLines().drop(1)
        assertEquals(360, expected.size)
        val exercises = frozenLegacyExercises()
        expected.forEach { line ->
            val row = line.split(",")
            val result = LegacyAutoProgramBuilder().build(frozenLegacyRequest(row[0].toInt(), row[1].toInt(),
                row[2].toInt(), row[3].toDouble()), exercises)
            assertEquals(line, row[4], frozenFingerprint(result))
            result.weekDaySchedule.forEach { (week, active) ->
                assertEquals(line + " week " + week, active,
                    result.items.filter { it.weekNumber == week }.map { it.dayOfWeek }.toSet())
            }
        }
    }

    @Test fun manuallyAddedEmptyDayIsNotRepaired() {
        val original = LegacyAutoProgramBuilder().build(frozenLegacyRequest(3, 3), frozenLegacyExercises())
        val edited = original.withWeekDays(1, original.weekDaySchedule.getValue(1) + 2)
        assertTrue(2 in edited.resolvedWeekDaySchedule().getValue(1))
        assertTrue(edited.items.none { it.weekNumber == 1 && it.dayOfWeek == 2 })
    }
}

internal fun frozenLegacyRequest(weeks: Int = 3, days: Int = 3, minutes: Int = 30, ratio: Double = 0.0) =
    LegacyAutoRequest("parity", LegacyAutoGoal.BADMINTON_SUPPORT, days, minutes, emptySet(), "", ratio,
        "AUTO", LegacyAutoPeriodizationType.AUTO, weeks)

internal fun frozenFixture(name: String): File =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
        .first { File(it, "settings.gradle.kts").isFile }
        .resolve("app/src/test/resources/program-authority/legacy-frozen/$name")

internal fun frozenLegacyExercises(): List<Exercise> = frozenFixture("exercises.tsv").readLines().map {
    val row = it.split("\t")
    Exercise(stableKey = row[0], name = row[1], category = row[2], defaultRestSeconds = row[3].toInt())
}

/** Field-name based complete tree, independent of mechanically renamed type names. */
internal fun frozenCanonical(value: Any?): String = when (value) {
    null -> "null"
    is String -> "${value.length}:$value"
    is Number, is Boolean -> value.toString()
    is Enum<*> -> value.name
    is Map<*, *> -> value.entries.sortedBy { frozenCanonical(it.key) }
        .joinToString(prefix = "{", postfix = "}") { frozenCanonical(it.key) + "=" + frozenCanonical(it.value) }
    is Set<*> -> value.map(::frozenCanonical).sorted().joinToString(prefix = "<", postfix = ">")
    is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { frozenCanonical(it) }
    else -> value.javaClass.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
        .sortedBy { it.name }.joinToString(prefix = "(", postfix = ")") {
            it.isAccessible = true
            it.name + "=" + frozenCanonical(it.get(value))
        }
}
internal fun frozenFingerprint(value: Any?): String = MessageDigest.getInstance("SHA-256")
    .digest(frozenCanonical(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
