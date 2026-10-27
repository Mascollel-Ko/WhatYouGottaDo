package com.training.trackplanner.health

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exists under src/debug only. Separate DEBUG launcher entry avoids changing
 * the normal Record, Plan, and release navigation surfaces.
 */
class WatchDiagnosticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rationaleOnly = intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        setContent {
            TrainingTrackPlannerTheme {
                if (rationaleOnly) WatchP0Rationale() else WatchP0Diagnostics()
            }
        }
    }
}

@Composable
private fun WatchP0Rationale() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Health Connect 진단 데이터 사용 안내", style = MaterialTheme.typography.titleLarge)
        Text("이 화면은 개발 중 디버그 버전에서만 제공됩니다.")
        Text("운동 기록 읽기 권한은 운동 종류, 시작 및 종료 시각과 데이터 제공 앱을 확인하는 데 사용합니다.")
        Text("심박수 읽기 권한은 사용자가 선택한 날짜의 측정 개수, 평균/최솟값/최댓값, 수집 공백을 확인하는 데 사용합니다.")
        Text("권한은 화면에서 직접 요청할 때만 요청합니다. 앱의 운동일지, 운동 완료 여부, RPE 및 분석 계산은 변경하지 않습니다.")
        Text("심박수 원본 샘플은 조회 중 메모리에서만 처리하고 DB, 백업, 서버, 로그에 저장하지 않습니다.")
        Text("Health Connect에서 언제든 권한을 철회할 수 있습니다. 이 진단 기능은 정식 배포판에 포함되지 않습니다.")
    }
}

@Composable
private fun WatchP0Diagnostics() {
    val context = LocalContext.current
    val gateway = remember(context) { WatchHealthConnectGateway(context) }
    val scope = rememberCoroutineScope()
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var availability by remember { mutableIntStateOf(gateway.availability()) }
    var granted by remember { mutableStateOf(emptySet<String>()) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<WatchHealthConnectGateway.Snapshot?>(null) }
    var queryError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var queryJob by remember { mutableStateOf<Job?>(null) }

    fun invalidateQuery() {
        generation++
        queryJob?.cancel()
        queryJob = null
        busy = false
        report = null
        queryError = null
    }

    fun checkPermissions() {
        scope.launch {
            availability = gateway.availability()
            if (availability != HealthConnectClient.SDK_AVAILABLE) {
                granted = emptySet()
                return@launch
            }
            try {
                granted = gateway.grantedPermissions()
                permissionError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                granted = emptySet()
                permissionError = e.javaClass.simpleName
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        invalidateQuery()
        checkPermissions()
    }

    LaunchedEffect(Unit) { checkPermissions() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("워치 데이터 진단 · DEBUG", style = MaterialTheme.typography.titleLarge)
        Text(
            "삼성 헬스에서 Health Connect로 실제 공유된 기록을 읽습니다. " +
                "운동시간은 기존 운동일지에 적용하지 않습니다.",
            style = MaterialTheme.typography.bodyMedium
        )
        val status = when (availability) {
            HealthConnectClient.SDK_AVAILABLE -> "사용 가능"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "제공 앱 설치/업데이트 필요"
            else -> "지원되지 않음 (Android 9 이상 및 서비스 필요)"
        }
        Text("Health Connect: " + status)
        if (permissionError != null) Text("권한 확인 오류: " + permissionError)
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            Text("운동 세션: " + if (WatchHealthConnectGateway.EXERCISE_READ in granted) "허용됨" else "미허용")
            Text("심박수: " + if (WatchHealthConnectGateway.HEART_READ in granted) "허용됨" else "미허용")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (WatchHealthConnectGateway.EXERCISE_READ !in granted) {
                    Button(onClick = { launcher.launch(setOf(WatchHealthConnectGateway.EXERCISE_READ)) }) {
                        Text("운동 권한 요청")
                    }
                }
                if (WatchHealthConnectGateway.HEART_READ !in granted) {
                    OutlinedButton(onClick = { launcher.launch(setOf(WatchHealthConnectGateway.HEART_READ)) }) {
                        Text("심박수 권한")
                    }
                }
            }
            OutlinedButton(onClick = { checkPermissions() }) { Text("권한 상태 다시 확인") }
        }
        HorizontalDivider()
        Text("조회할 날짜 (현지 날짜)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                invalidateQuery()
                dayText = LocalDate.parse(dayText).minusDays(1).toString()
            }) { Text("전날") }
            Text(dayText, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = {
                invalidateQuery()
                dayText = LocalDate.parse(dayText).plusDays(1).toString()
            }) { Text("다음날") }
        }
        if (availability == HealthConnectClient.SDK_AVAILABLE &&
            (WatchHealthConnectGateway.EXERCISE_READ in granted ||
                WatchHealthConnectGateway.HEART_READ in granted)
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    invalidateQuery()
                    val key = generation
                    val date = LocalDate.parse(dayText)
                    val zone = ZoneId.systemDefault()
                    busy = true
                    queryJob = scope.launch {
                        try {
                            val actualGrants = gateway.grantedPermissions()
                            granted = actualGrants
                            if (actualGrants.isEmpty()) {
                                queryError = "권한이 철회되었습니다. 권한을 다시 확인하세요."
                            } else {
                                val result = gateway.read(date, zone, actualGrants)
                                if (generation == key && dayText == date.toString()) report = result
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (generation == key) queryError = "조회 실패: " + e.javaClass.simpleName
                        } finally {
                            if (generation == key) busy = false
                        }
                    }
                }
            ) { Text(if (busy) "조회 중…" else "선택 날짜의 기록 읽기") }
        }
        queryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        report?.let { snapshot -> WatchP0Report(snapshot) }
        Text(
            "참고: 워치→삼성 헬스→Health Connect 동기화에 시간이 걸릴 수 있습니다. " +
                "기록이 없다는 결과는 운동하지 않았다는 뜻이 아닙니다. " +
                "이 화면은 심박수로 배드민턴을 자동 판정하지 않습니다.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WatchP0Report(snapshot: WatchHealthConnectGateway.Snapshot) {
    val zone = snapshot.zone
    fun label(at: Instant): String = at.atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text("조회 기준: " + snapshot.localDate + " / " + zone.id)
        Text("조회 시각: " + label(snapshot.queryTime))
        Text("운동 기록: " + snapshot.exerciseStatus + " / " + snapshot.sessions.size + "개")
        snapshot.sessions.forEachIndexed { index, session ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val displayType = if (session.type == ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON) {
                        "배드민턴"
                    } else {
                        "운동 종류 미확인"
                    }
                    Text("세션 " + (index + 1) + " · " + displayType + " (code=" + session.type + ")")
                    Text(label(session.start) + " ~ " + label(session.end))
                    Text("경과 시간: " + (session.validDurationSeconds?.let { it.toString() + "초" } ?: "유효하지 않음"))
                    Text("출처 앱: " + session.origin)
                }
            }
        }
        Text("심박수: " + snapshot.heartStatus)
        snapshot.heartSummary?.let { heart ->
            Text("샘플 수: " + heart.count)
            Text("첫/마지막: " + (heart.first?.let(::label) ?: "-") + " / " + (heart.last?.let(::label) ?: "-"))
            Text(
                "최소/최대/산술평균: " + (heart.minimum?.toString() ?: "-") +
                    " / " + (heart.maximum?.toString() ?: "-") +
                    " / " + (heart.mean?.let { String.format(Locale.US, "%.1f", it) } ?: "-") + " bpm"
            )
            Text("중복 측정 시각: " + heart.duplicateTimestampCount)
            Text("10분 이상 무관측 간격: " + heart.largeGaps.size + "개 (운동 여부 추론 금지)")
            heart.largeGaps.take(5).forEach { gap ->
                Text("자료 공백: " + label(gap.first) + " ~ " + label(gap.second))
            }
            heart.sourceCounts.toSortedMap().forEach { (origin, count) ->
                Text("출처: " + origin + " / 샘플 " + count + "개")
            }
            if (heart.count == 0) Text("측정 자료가 없습니다. 0 bpm으로 보정하지 않습니다.")
        }
    }
}
