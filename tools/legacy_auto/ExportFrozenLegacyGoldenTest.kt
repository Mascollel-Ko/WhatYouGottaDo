package com.training.trackplanner.data

import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import org.junit.Test

/** Run only inside the untouched f5cc0ac worktree; never used by parity tests to update goldens. */
class ExportFrozenLegacyGoldenTest {
    @Test fun exportHistorical360() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
            .first { File(it, "settings.gradle.kts").isFile }
        val lines = File(root, "app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv").readLines()
        val header = SeedData.parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        val exercises = lines.drop(1).filter(String::isNotBlank).map(SeedData::parseCsvLine).map { values ->
            header.mapIndexed { i, key -> key to values.getOrElse(i) { "" } }.toMap()
        }.filter { it["isActive"] == "YES" && it["planningEligibility"] != "HISTORY_ONLY" }.map {
            Exercise(stableKey = it.getValue("stableKey"), name = it.getValue("name"),
                category = it.getValue("category"), defaultRestSeconds = it.getValue("defaultRestSeconds").toInt())
        }
        val output = File(root, "build/frozen-legacy-golden").apply { mkdirs() }
        File(output, "exercises.tsv").writeText(exercises.joinToString("\n") {
            listOf(it.stableKey, it.name, it.category, it.defaultRestSeconds).joinToString("\t")
        } + "\n")
        val csv = buildString {
            appendLine("durationWeeks,weeklyDays,sessionMinutes,badmintonRatio,outputSha256")
            for (weeks in 3..8) for (days in 3..7) for (minutes in listOf(30, 45, 60))
                for (ratio in listOf(0.0, 0.30, 0.50, 0.70)) {
                    val request = ProgramSkeletonRequest("parity", ProgramGoal.BADMINTON_SUPPORT, days, minutes,
                        emptySet(), "", ratio, "AUTO", ProgramPeriodizationType.AUTO, weeks)
                    val result = ProgramAutoBuilder().build(request, exercises)
                    result.weekDaySchedule.forEach { (week, active) ->
                        check(active == result.items.filter { it.weekNumber == week }.map { it.dayOfWeek }.toSet())
                    }
                    appendLine("$weeks,$days,$minutes,$ratio,${frozenFingerprint(result)}")
                }
        }
        File(output, "legacy_f5cc0ac_360.csv").writeText(csv)
    }
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
