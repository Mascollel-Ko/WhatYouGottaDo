package com.training.trackplanner.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * P0 read-only, debug-only gateway. Heart-rate samples exist in memory only
 * during a foreground diagnostic query; never stored in Room or sent to backup.
 */
internal class WatchHealthConnectGateway(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val EXERCISE_READ: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val HEART_READ: String = HealthPermission.getReadPermission(HeartRateRecord::class)
        private const val PAGE_SIZE = 250
        private const val MAX_PAGES = 60
    }

    data class SessionRow(
        val start: Instant,
        val end: Instant,
        val type: Int,
        val origin: String,
        val validDurationSeconds: Long?
    )

    data class Snapshot(
        val localDate: LocalDate,
        val zone: ZoneId,
        val sessions: List<SessionRow>,
        val exerciseStatus: String,
        val heartSummary: WatchDiagnosticMath.HeartSummary?,
        val heartStatus: String,
        val queryTime: Instant
    )

    fun availability(): Int = HealthConnectClient.getSdkStatus(appContext)

    suspend fun grantedPermissions(): Set<String> {
        check(availability() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect unavailable" }
        return HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
    }

    suspend fun read(date: LocalDate, zone: ZoneId, granted: Set<String>): Snapshot {
        check(availability() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect unavailable" }
        val client = HealthConnectClient.getOrCreate(appContext)
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        // The prior day also covers sessions beginning before local midnight.
        val sessionStart = date.minusDays(1).atStartOfDay(zone).toInstant()
        var exerciseStatus = "읽기 권한 없음"
        var sessions = emptyList<SessionRow>()
        if (EXERCISE_READ in granted) {
            try {
                val (records, capped) = pages<ExerciseSessionRecord>(client, sessionStart, end)
                sessions = records.asSequence()
                    .filter { WatchDiagnosticMath.intersects(it.startTime, it.endTime, start, end) }
                    .map {
                        SessionRow(
                            start = it.startTime,
                            end = it.endTime,
                            type = it.exerciseType,
                            origin = it.metadata.dataOrigin.packageName,
                            validDurationSeconds = WatchDiagnosticMath.elapsedSeconds(it.startTime, it.endTime)
                        )
                    }
                    .sortedWith(compareBy<SessionRow> { it.start }.thenBy { it.origin })
                    .toList()
                exerciseStatus = if (capped) "일부만 조회됨(페이지 제한)" else if (sessions.isEmpty()) "기록 없음" else "조회 완료"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exerciseStatus = "조회 오류: " + e.javaClass.simpleName
            }
        }

        var heartStatus = "읽기 권한 없음"
        var summary: WatchDiagnosticMath.HeartSummary? = null
        if (HEART_READ in granted) {
            try {
                val (records, capped) = pages<HeartRateRecord>(client, start, end)
                // A single daily read can have many samples. Discard the samples
                // immediately after computing aggregate diagnostics.
                val samples = records.flatMap { record ->
                    val source = record.metadata.dataOrigin.packageName
                    record.samples.map { sample ->
                        WatchDiagnosticMath.HeartSample(sample.time, sample.beatsPerMinute, source)
                    }
                }
                summary = WatchDiagnosticMath.summarizeHearts(samples, start, end)
                heartStatus = if (capped) "일부만 조회됨(페이지 제한)" else if (summary.count == 0) "기록 없음" else "조회 완료"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                heartStatus = "조회 오류: " + e.javaClass.simpleName
            }
        }
        return Snapshot(date, zone, sessions, exerciseStatus, summary, heartStatus, Instant.now())
    }

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> pages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): Pair<List<T>, Boolean> {
        val result = mutableListOf<T>()
        var token: String? = null
        var pageCount = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = token
                )
            )
            result.addAll(response.records)
            token = response.pageToken
            pageCount++
            if (pageCount >= MAX_PAGES) return result to (token != null)
        } while (token != null)
        return result to false
    }
}
