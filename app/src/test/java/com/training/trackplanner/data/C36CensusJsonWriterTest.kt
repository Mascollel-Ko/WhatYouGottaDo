package com.training.trackplanner.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class C36CensusJsonWriterTest {
    @Test
    fun compactWriterPreservesJsonValuesAndEscapesStrings() {
        val source = JSONObject()
            .put("quote", "a \"quoted\" line\nsecond line\u0001")
            .put("unicode", "근력 🏋")
            .put("values", JSONArray().put(1).put(1.25).put(true).put(JSONObject.NULL))
        val output = StringWriter()

        C36ExecutionProvenanceCensus.writeCompactJson(source, output)

        val decoded = JSONObject(output.toString())
        assertEquals(source.getString("quote"), decoded.getString("quote"))
        assertEquals(source.getString("unicode"), decoded.getString("unicode"))
        assertEquals(1, decoded.getJSONArray("values").getInt(0))
        assertEquals(1.25, decoded.getJSONArray("values").getDouble(1), 0.0)
        assertTrue(decoded.getJSONArray("values").getBoolean(2))
        assertTrue(decoded.getJSONArray("values").isNull(3))
    }
}
