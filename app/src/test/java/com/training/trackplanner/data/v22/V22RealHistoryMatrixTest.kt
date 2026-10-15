package com.training.trackplanner.data.v22

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.zip.GZIPInputStream

class V22RealHistoryMatrixTest {
    @Test
    fun `all 528 real history cases match Python v1 semantics`() {
        val expected = gzipJson("python_expected_528.json.gz")
        val cases = expected.getJSONArray("cases")
        assertEquals(528, cases.length())
        val inferenceByTimeline = mutableMapOf<String, V22BaselineGeneration>()
        var successCount = 0

        repeat(cases.length()) { index ->
            val case = cases.getJSONObject(index)
            val timeline = case.getString("timeline")
            val asOf = LocalDate.parse(case.getString("asOf"))
            val baseline = inferenceByTimeline.getOrPut(timeline) {
                V22PythonParityEngine.generateBaseline(
                    rows = rows,
                    asOf = asOf,
                    sourceName = FIXTURE_NAME,
                    trainingDays = null
                )
            }
            assertEquals("$timeline inferred days", case.getInt("inferredDays"), baseline.trainingDays)
            assertSimilar(
                "$timeline training-day inference",
                case.getJSONObject("trainingDayInference"),
                baseline.trainingDayInference.json()
            )

            val request = V22GenerationRequest(
                name = case.getString("caseId"),
                weeks = case.getInt("weeks"),
                days = case.getInt("days"),
                mode = V22Mode.valueOf(case.getString("mode")),
                asOf = asOf,
                focusStableKey = case.optString("focusStableKey").takeIf { it.isNotBlank() },
                backupName = FIXTURE_NAME
            )
            val result = runCatching { V22PythonParityEngine.build(rows, request) }
            when (case.getString("status")) {
                "SUCCESS" -> {
                    val plan = result.getOrThrow()
                    assertSimilar(case.getString("caseId"), case.getJSONObject("semantic"), plan.compactJson())
                    plan.blocks.forEach { block ->
                        val representative = plan.weeks[block.startWeek - 1]
                        representative.sessions.flatMap(V22Session::items).flatMap(V22ExercisePlan::sets).forEach { set ->
                            assertTrue("${case.getString("caseId")} leaked future history", LocalDate.parse(set.sourceDate) <= asOf)
                        }
                    }
                    successCount++
                }
                "GENERATION_FAILED" -> assertEquals(case.getString("error"), result.exceptionOrNull()?.message)
                "INPUT_LIMITATION" -> assertTrue(request.mode == V22Mode.C && request.focusStableKey == null)
                else -> error("Unknown expected status: ${case.getString("status")}")
            }
        }
        assertEquals(528, successCount)
    }

    @Test
    fun `fixture preserves source range and cutoff rows without copying unrelated backup tables`() {
        assertEquals(940, rows.size)
        assertEquals(LocalDate.of(2026, 6, 1), rows.minOf(V22HistoryRow::date))
        assertEquals(LocalDate.of(2026, 10, 11), rows.maxOf(V22HistoryRow::date))
        assertTrue(rows.all { it.category == "근력운동" || it.category == "기능성운동" })
        assertTrue(rows.all { it.stableKey.isNotBlank() && it.exerciseName.isNotBlank() })
    }

    private fun assertSimilar(label: String, expected: JSONObject, actual: JSONObject) {
        assertTrue("$label first difference: ${firstDifference(expected, actual)}", expected.similar(actual))
    }

    private fun firstDifference(expected: Any?, actual: Any?, path: String = "$"): String? {
        if (expected is JSONObject && actual is JSONObject) {
            if (expected.keySet() != actual.keySet()) return "$path keys expected=${expected.keySet()} actual=${actual.keySet()}"
            expected.keySet().sorted().forEach { key ->
                firstDifference(expected.get(key), actual.get(key), "$path.$key")?.let { return it }
            }
            return null
        }
        if (expected is JSONArray && actual is JSONArray) {
            if (expected.length() != actual.length()) return "$path length expected=${expected.length()} actual=${actual.length()}"
            repeat(expected.length()) { index ->
                firstDifference(expected.get(index), actual.get(index), "$path[$index]")?.let { return it }
            }
            return null
        }
        if (expected is Number && actual is Number && expected.toDouble() == actual.toDouble()) return null
        return if (expected == actual) null else "$path expected=$expected actual=$actual"
    }

    private fun gzipJson(name: String): JSONObject = gzipResource(name).use { input ->
        JSONObject(InputStreamReader(input, Charsets.UTF_8).readText())
    }

    private fun gzipResource(name: String): GZIPInputStream = GZIPInputStream(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("v22-real-history/$name"))
    )

    private companion object {
        const val FIXTURE_NAME = "whatyougottado_backup_2026-10-12-v22.csv"
        val rows: List<V22HistoryRow> by lazy {
            GZIPInputStream(checkNotNull(javaClass.classLoader?.getResourceAsStream(
                "v22-real-history/$FIXTURE_NAME.gz"
            ))).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }
        }
    }
}

private fun V22TrainingDayInference.json(): JSONObject = JSONObject()
    .put("window", window)
    .put("categories", JSONArray(categories))
    .put("weeks", JSONArray(weeks.map { week ->
        JSONObject()
            .put("start", week.start)
            .put("end", week.end)
            .put("trainingDays", week.trainingDays)
            .put("excludedBottom30Percent", week.excludedBottom30Percent)
    }))
    .put("excludedWeekCount", excludedWeekCount)
    .put("rounding", rounding)
    .put("retainedMean", retainedMean)

private fun V22Plan.compactJson(): JSONObject = JSONObject()
    .put("historyEvidence", JSONObject().also { output ->
        historyEvidence.forEach { (role, evidence) ->
            output.put(role, JSONObject()
                .put("historyWeeklyNominal", evidence.historyWeeklyNominal)
                .put("baselinePlanned", evidence.baselinePlanned)
                .put("gap", evidence.gap))
        }
    })
    .put("baselineWeeklyNominalSets", baselineWeeklyNominalSets)
    .put("authority", JSONObject()
        .put("stimulus", authority.stimulus)
        .put("prescription", authority.prescription)
        .put("executionMissCarryover", authority.executionMissCarryover)
        .put("structuralPlanningOnly", authority.structuralPlanningOnly))
    .put("limitations", JSONArray(limitations))
    .put("blocks", JSONArray(blocks.map { block ->
        val representative = weeks[block.startWeek - 1]
        JSONObject()
            .put("block", block.block)
            .put("startWeek", block.startWeek)
            .put("endWeek", block.endWeek)
            .put("phase", block.phase)
            .put("plannedReallocations", JSONArray(block.plannedReallocations.map { move ->
                JSONObject()
                    .put("from", move.from)
                    .put("to", move.to)
                    .put("donorRole", move.donorRole)
                    .put("receiverRole", move.receiverRole)
                    .put("amountNominalSets", move.amountNominalSets)
                    .put("reason", move.reason)
            }))
            .put("plannedRoleSets", JSONObject(block.plannedRoleSets))
            .put("sessions", JSONArray(representative.sessions.map(V22Session::compactJson)))
    }))

private fun V22Session.compactJson(): JSONObject = JSONObject()
    .put("session", session)
    .put("nominalSets", nominalSets)
    .put("items", JSONArray(items.map { item ->
        JSONObject()
            .put("exercise", item.exercise)
            .put("stableKey", item.stableKey)
            .put("category", item.category)
            .put("role", item.role)
            .put("setCount", item.setCount)
            .put("sets", JSONArray(item.sets.map { set ->
                JSONObject()
                    .put("weightKg", set.weightKg ?: JSONObject.NULL)
                    .put("reps", set.reps ?: JSONObject.NULL)
                    .put("seconds", set.seconds ?: JSONObject.NULL)
                    .put("observedRpe", set.observedRpe ?: JSONObject.NULL)
                    .put("sourceDate", set.sourceDate)
                    .put("loadStatus", set.loadStatus)
            }))
            .put("historicalSets28d", item.historicalSets28d)
            .put("historicalSets56d", item.historicalSets56d)
    }))
