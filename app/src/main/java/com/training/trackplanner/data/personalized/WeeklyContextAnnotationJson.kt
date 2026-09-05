package com.training.trackplanner.data.personalized

import org.json.JSONObject
import java.time.LocalDate

/** Separate portable app_meta payload. Never expands a legacy global cause into weekly answers. */
internal object WeeklyContextAnnotationJson {
    const val KEY = "personalized_planning_week_context_v1"
    fun read(value: String?): Map<LocalDate,WeeklyContextAnnotation> {
        val json=value?.let { runCatching { JSONObject(it) }.getOrNull() }?:return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            runCatching {
                val row=json.getJSONObject(key); val start=LocalDate.parse(key)
                start to WeeklyContextAnnotation(start,WeeklyContextCause.valueOf(row.getString("cause")),
                    WeeklyContextSource.valueOf(row.getString("source")),
                    row.optLong("answeredAtEpochMillis",Long.MIN_VALUE).takeUnless { it==Long.MIN_VALUE })
            }.getOrNull()
        }.toMap()
    }
    fun write(values: Map<LocalDate,WeeklyContextAnnotation>): String = JSONObject().apply {
        values.toSortedMap().forEach { (start,value) ->
            require(start==value.weekStart)
            put(start.toString(),JSONObject().put("cause",value.cause.name).put("source",value.source.name)
                .put("answeredAtEpochMillis",value.answeredAtEpochMillis?:JSONObject.NULL))
        }
    }.toString()
}
