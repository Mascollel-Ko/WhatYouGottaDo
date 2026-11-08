package com.training.trackplanner.health

import android.content.Context
import android.os.SystemClock
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Read-only Health Connect adapter. No raw health data is persisted or uploaded. */
internal class WatchHealthData(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val EXERCISE_PERMISSION: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val SLEEP_PERMISSION: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    }

    fun available(): Boolean = HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    suspend fun permissions(): Set<String> = withContext(Dispatchers.IO) {
        if (!available()) emptySet() else HealthConnectClient.getOrCreate(appContext)
            .permissionController.getGrantedPermissions()
    }

    suspend fun exercises(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<WatchSession> =
        withContext(Dispatchers.IO) {
            require(EXERCISE_PERMISSION in permissions()) { "EXERCISE_PERMISSION_REQUIRED" }
            val from = date.minusDays(1).atStartOfDay(zone).toInstant()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            readPages<ExerciseSessionRecord>(from, end)
                .filter { it.endTime > start && it.startTime < end && it.endTime > it.startTime }
                .map {
                    WatchSession(
                        sourceId = it.metadata.dataOrigin.packageName + ":" + it.metadata.id,
                        sourceName = it.metadata.dataOrigin.packageName,
                        start = it.startTime,
                        end = it.endTime,
                        type = it.exerciseType
                    )
                }
                .distinctBy { it.sourceId }
                .sortedBy { it.start }
        }

    /**
     * A short-lived process-only cache avoids repeating the same Health Connect read when
     * the daily condition card leaves and re-enters LazyColumn composition during scrolling.
     * Permission is checked on every call; explicit refresh always bypasses the cache.
     */
    suspend fun sleep(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        forceRefresh: Boolean = false
    ): List<WatchSleep> = withContext(Dispatchers.IO) {
        require(SLEEP_PERMISSION in permissions()) { "SLEEP_PERMISSION_REQUIRED" }
        val key = date.toString() + "|" + zone.id
        WatchSleepReadCache.mutex.lock()
        try {
            val now = SystemClock.elapsedRealtime()
            val cached = WatchSleepReadCache.items[key]
            val maxAge = if (cached?.sessions?.isEmpty() == true) 60_000L else 180_000L
            if (!forceRefresh && cached != null &&
                now >= cached.readAtMillis && now - cached.readAtMillis < maxAge) {
                cached.sessions
            } else {
                val from = date.minusDays(1).atTime(21, 0).atZone(zone).toInstant()
                val to = date.atTime(12, 0).atZone(zone).toInstant()
                val fresh = readPages<SleepSessionRecord>(from, to)
                    .filter { it.endTime > from && it.startTime < to && it.endTime > it.startTime }
                    .map { record ->
                        val clippedStart = maxOf(record.startTime, from)
                        val clippedEnd = minOf(record.endTime, to)
                        val phases = record.stages.mapNotNull {
                            val stageStart = maxOf(it.startTime, clippedStart)
                            val stageEnd = minOf(it.endTime, clippedEnd)
                            if (stageEnd > stageStart) WatchSleepMath.Stage(stageStart, stageEnd, it.stage) else null
                        }
                        val evaluation = WatchSleepMath.evaluate(clippedStart, clippedEnd, phases)
                        WatchSleep(
                            sourceId = record.metadata.dataOrigin.packageName + ":" + record.metadata.id,
                            sourceName = record.metadata.dataOrigin.packageName,
                            start = clippedStart,
                            end = clippedEnd,
                            sleepMinutes = evaluation.sleepMinutes,
                            elapsedMinutes = Duration.between(clippedStart, clippedEnd).toMinutes().toInt(),
                            reliable = evaluation.reliable
                        )
                    }
                    .distinctBy { it.sourceId }
                    .sortedByDescending { it.end }
                if (WatchSleepReadCache.items.size >= 16) {
                    WatchSleepReadCache.items.remove(WatchSleepReadCache.items.keys.first())
                }
                WatchSleepReadCache.items[key] = CachedWatchSleep(now, fresh)
                fresh
            }
        } finally {
            WatchSleepReadCache.mutex.unlock()
        }
    }

    private suspend inline fun <reified T : Record> readPages(from: Instant, to: Instant): List<T> {
        val client = HealthConnectClient.getOrCreate(appContext)
        val result = mutableListOf<T>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                pageSize = 250,
                pageToken = pageToken
            ))
            result.addAll(response.records)
            pageToken = response.pageToken
            pages++
            // Incomplete results are NOT safe for automatic import.
            check(pages < 40 || pageToken == null) { "HEALTH_CONNECT_RESULTS_TRUNCATED" }
        } while (pageToken != null)
        return result
    }
}

private data class CachedWatchSleep(
    val readAtMillis: Long,
    val sessions: List<WatchSleep>
)

/** No raw sleep timeline is persisted; entries expire quickly and are bounded in memory. */
private object WatchSleepReadCache {
    val mutex = Mutex()
    val items = LinkedHashMap<String, CachedWatchSleep>()
}

internal data class WatchSession(
    val sourceId: String,
    val sourceName: String,
    val start: Instant,
    val end: Instant,
    val type: Int
) {
    val minutes: Int get() = ((Duration.between(start, end).seconds + 30) / 60)
        .coerceIn(0L, 24L * 60L).toInt()
    val badminton: Boolean get() = type == ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON
    val walking: Boolean get() = type == ExerciseSessionRecord.EXERCISE_TYPE_WALKING
}

internal data class WatchSleep(
    val sourceId: String,
    val sourceName: String,
    val start: Instant,
    val end: Instant,
    val sleepMinutes: Int?,
    val elapsedMinutes: Int,
    val reliable: Boolean
)

internal object WatchSleepMath {
    data class Stage(val start: Instant, val end: Instant, val kind: Int)
    data class Evaluation(val sleepMinutes: Int?, val reliable: Boolean)

    private val asleep = setOf(
        SleepSessionRecord.STAGE_TYPE_SLEEPING,
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_DEEP,
        SleepSessionRecord.STAGE_TYPE_REM
    )

    fun evaluate(start: Instant, end: Instant, stages: List<Stage>): Evaluation {
        val total = Duration.between(start, end).seconds
        if (total <= 0 || total > 24 * 3600 || stages.isEmpty()) return Evaluation(null, false)
        val sorted = stages.sortedBy { it.start }
        var prior = start
        var covered = 0L
        var slept = 0L
        for (stage in sorted) {
            if (stage.start < prior || stage.end <= stage.start || stage.end > end) {
                return Evaluation(null, false) // Overlap or invalid bounds.
            }
            val duration = Duration.between(stage.start, stage.end).seconds
            covered += duration
            if (stage.kind in asleep) slept += duration
            prior = stage.end
        }
        if (covered.toDouble() / total < 0.8 || slept <= 0) return Evaluation(null, false)
        val mins = ((slept + 30) / 60).toInt()
        return Evaluation(mins, mins > 0 && mins <= 1440)
    }
}

/** Local-only duplicate protection; source IDs and health timelines never go to cloud backup. */
internal class WatchImportPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("watch_health_p1", Context.MODE_PRIVATE)
    var autoSleep: Boolean
        get() = store.getBoolean("auto_sleep_enabled", false)
        set(value) { store.edit().putBoolean("auto_sleep_enabled", value).apply() }

    fun hasHandledSleep(date: String): Boolean = store.getBoolean("sleep_handled_$date", false)
    fun markSleepHandled(date: String) { store.edit().putBoolean("sleep_handled_$date", true).apply() }

    fun linkedTarget(sourceId: String): String? = store.getString("session_used_$sourceId", null)
    fun markSessionsUsed(ids: Collection<String>, target: String) {
        store.edit().apply {
            ids.forEach { putString("session_used_$it", target) }
        }.apply()
    }
}

/** Aggregates only non-overlapping, stage-verified sessions within the daily sleep window. */
internal object WatchSleepAggregate {
    fun combine(sessions: List<WatchSleep>): WatchSleep? {
        if (sessions.isEmpty()) return null
        val sorted = sessions.sortedWith(compareBy<WatchSleep> { it.start }.thenBy { it.end })
        if (sorted.any { !it.reliable || it.sleepMinutes == null || it.sleepMinutes <= 0 }) return null
        if (sorted.zipWithNext().any { (a, b) -> b.start < a.end }) return null
        val minutes = sorted.sumOf { it.sleepMinutes ?: 0 }
        if (minutes !in 1..900) return null
        return WatchSleep(
            sourceId = "aggregate",
            sourceName = sorted.map { it.sourceName }.distinct().joinToString(", "),
            start = sorted.first().start,
            end = sorted.last().end,
            sleepMinutes = minutes,
            elapsedMinutes = sorted.sumOf { it.elapsedMinutes },
            reliable = true
        )
    }
}
