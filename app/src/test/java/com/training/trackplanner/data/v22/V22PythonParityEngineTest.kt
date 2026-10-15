package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramLoadState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.zip.GZIPInputStream

class V22PythonParityEngineTest {
    @Test
    fun `A B and C match the complete Python v1 json semantics`() {
        V22Mode.entries.forEach { mode ->
            val request = request(
                mode = mode,
                focus = "barbell_back_squat".takeIf { mode == V22Mode.C }
            )
            val actual = V22PythonParityEngine.build(rows, request).json()
            val expected = resourceJson("v2_2_v10_${mode.name}.json.gz")
            assertTrue("First difference for $mode: ${firstDifference(expected, actual)}", expected.similar(actual))
        }
    }

    @Test
    fun `block split and generation support short and longer durations`() {
        assertEquals(listOf(1), V22PythonParityEngine.splitBlocks(1))
        assertEquals(listOf(4), V22PythonParityEngine.splitBlocks(4))
        assertEquals(listOf(3, 2), V22PythonParityEngine.splitBlocks(5))
        assertEquals(listOf(3, 3, 2), V22PythonParityEngine.splitBlocks(8))
        assertEquals(listOf(3, 3, 3, 3), V22PythonParityEngine.splitBlocks(12))

        listOf(1 to 2, 4 to 3, 5 to 4, 12 to 7).forEach { (weeks, days) ->
            val plan = V22PythonParityEngine.build(rows, request(V22Mode.A, weeks = weeks, days = days))
            assertEquals(weeks, plan.weeks.size)
            assertTrue(plan.weeks.all { it.sessions.size == days })
        }
        assertEquals(
            "baseline placement failed",
            assertThrows(IllegalArgumentException::class.java) {
                V22PythonParityEngine.build(rows, request(V22Mode.A, weeks = 1, days = 1))
            }.message
        )
    }

    @Test
    fun `C rejects a focus that is absent from the baseline roster`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            V22PythonParityEngine.build(rows, request(V22Mode.C, focus = "not_in_baseline"))
        }
        assertEquals("C requires an existing explicit focus stableKey", error.message)
    }

    @Test
    fun `missing historical load remains unprescribed through the app adapter`() {
        val date = LocalDate.of(2026, 10, 10)
        val input = listOf(1, 2).map { set ->
            V22HistoryRow(date, "테스트 컬", "test_curl", "근력운동", set, 10.0, null, null, 8.0)
        }
        val request = V22GenerationRequest("Missing load", 1, 1, V22Mode.A, LocalDate.of(2026, 10, 11))
        val plan = V22PythonParityEngine.build(input, request)
        val item = plan.weeks.single().sessions.single().items.single()
        assertTrue(item.sets.all { it.weightKg == null && it.loadStatus == "NO_LOAD_OR_CALIBRATION_REQUIRED" })

        val skeleton = plan.toSkeleton(request, mapOf("test_curl" to Exercise("test_curl", "테스트 컬", "근력운동")))
        assertTrue(skeleton.items.single().setPrescriptions.all {
            it.weightKg == 0.0 && it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && it.targetRpeMin == null
        })
    }

    @Test
    fun `repeated generation is deterministic`() {
        val request = request(V22Mode.B)
        assertEquals(
            V22PythonParityEngine.build(rows, request),
            V22PythonParityEngine.build(rows, request)
        )
    }

    private fun request(
        mode: V22Mode,
        focus: String? = null,
        weeks: Int = 8,
        days: Int = 4
    ) = V22GenerationRequest(
        name = "V2-2 $mode",
        weeks = weeks,
        days = days,
        mode = mode,
        asOf = LocalDate.of(2026, 10, 11),
        focusStableKey = focus,
        backupName = "whatyougottado_backup_2026-10-11.csv"
    )

    private fun resourceJson(name: String): JSONObject = gzipResource(name).use { input ->
        JSONObject(InputStreamReader(input, Charsets.UTF_8).readText())
    }

    private fun gzipResource(name: String): GZIPInputStream = GZIPInputStream(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("v22-python-v1/$name"))
    )

    private fun firstDifference(expected: Any?, actual: Any?, path: String = "$"): String? {
        if (expected is JSONObject && actual is JSONObject) {
            val expectedKeys = expected.keySet()
            val actualKeys = actual.keySet()
            if (expectedKeys != actualKeys) return "$path keys expected=$expectedKeys actual=$actualKeys"
            expectedKeys.sorted().forEach { key ->
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

    private companion object {
        val rows: List<V22HistoryRow> by lazy {
            GZIPInputStream(checkNotNull(javaClass.classLoader?.getResourceAsStream(
                "v22-python-v1/whatyougottado_backup_2026-10-11.csv.gz"
            ))).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }
        }
    }
}

private fun V22Plan.json(): JSONObject = JSONObject()
    .put("schema", schema)
    .put("input", JSONObject()
        .put("backup", input.backup)
        .put("asOf", input.asOf)
        .put("weeks", input.weeks)
        .put("days", input.days)
        .put("mode", input.mode)
        .put("focusStableKey", input.focusStableKey ?: JSONObject.NULL)
        .put("sessionMinutes", input.sessionMinutes ?: JSONObject.NULL))
    .put("historyEvidence", JSONObject().also { out -> historyEvidence.forEach { (role, evidence) ->
        out.put(role, JSONObject()
            .put("historyWeeklyNominal", evidence.historyWeeklyNominal)
            .put("baselinePlanned", evidence.baselinePlanned)
            .put("gap", evidence.gap))
    } })
    .put("blocks", JSONArray(blocks.map(V22Block::json)))
    .put("weeks", JSONArray(weeks.map(V22Week::json)))
    .put("baselineWeeklyNominalSets", baselineWeeklyNominalSets)
    .put("authority", JSONObject()
        .put("stimulus", authority.stimulus)
        .put("prescription", authority.prescription)
        .put("executionMissCarryover", authority.executionMissCarryover)
        .put("structuralPlanningOnly", authority.structuralPlanningOnly))
    .put("limitations", JSONArray(limitations))

private fun V22Block.json(): JSONObject = JSONObject()
    .put("block", block)
    .put("startWeek", startWeek)
    .put("endWeek", endWeek)
    .put("phase", phase)
    .put("plannedReallocations", JSONArray(plannedReallocations.map(V22Reallocation::json)))
    .put("strengthPolicy", strengthPolicy)
    .put("hypertrophyPolicy", hypertrophyPolicy)
    .put("recoveryPolicy", recoveryPolicy)
    .put("plannedRoleSets", JSONObject(plannedRoleSets))

private fun V22Reallocation.json(): JSONObject = JSONObject()
    .put("from", from)
    .put("to", to)
    .put("donorRole", donorRole)
    .put("receiverRole", receiverRole)
    .put("amountNominalSets", amountNominalSets)
    .put("reason", reason)

private fun V22Week.json(): JSONObject = JSONObject()
    .put("week", week)
    .put("block", block)
    .put("sessions", JSONArray(sessions.map(V22Session::json)))
    .put("weeklyNominalSets", weeklyNominalSets)

private fun V22Session.json(): JSONObject = JSONObject()
    .put("session", session)
    .put("items", JSONArray(items.map(V22ExercisePlan::json)))
    .put("nominalSets", nominalSets)

private fun V22ExercisePlan.json(): JSONObject = JSONObject()
    .put("exercise", exercise)
    .put("stableKey", stableKey)
    .put("category", category)
    .put("role", role)
    .put("setCount", setCount)
    .put("sets", JSONArray(sets.map(V22ObservedSet::json)))
    .put("historicalSets28d", historicalSets28d)
    .put("historicalSets56d", historicalSets56d)
    .put("source", source)
    .put("prescriptionAuthority", prescriptionAuthority ?: JSONObject.NULL)
    .put("plannedProgression", plannedProgression ?: JSONObject.NULL)
    .put("block", block ?: JSONObject.NULL)

private fun V22ObservedSet.json(): JSONObject = JSONObject()
    .put("weightKg", weightKg ?: JSONObject.NULL)
    .put("reps", reps ?: JSONObject.NULL)
    .put("seconds", seconds ?: JSONObject.NULL)
    .put("observedRpe", observedRpe ?: JSONObject.NULL)
    .put("sourceDate", sourceDate)
    .put("loadStatus", loadStatus)
