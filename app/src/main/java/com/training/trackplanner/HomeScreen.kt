package com.training.trackplanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.fatigue.MiniTrendPoint
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.CloudAuthUiState
import com.training.trackplanner.data.DataTransferReport
import com.training.trackplanner.data.DataTransferStatus
import com.training.trackplanner.data.ExerciseListRestoreMode
import com.training.trackplanner.data.WorkoutRestoreMode
import com.training.trackplanner.localization.AppLanguage
import com.training.trackplanner.localization.AppLanguageRegistry
import com.training.trackplanner.localization.LocalizedPresentation
import com.training.trackplanner.localization.localizedUiText
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HomeScreen(
    viewModel: TrainingViewModel,
    onNavigate: (AppTab) -> Unit,
    onOpenAppExplanation: () -> Unit,
    onOpenCommunity: () -> Unit = {},
    onProgramTargetPositioned: (Rect) -> Unit = {}
) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val summary by viewModel.homeTodaySummary.collectAsState()
    val transferMessage by viewModel.recordTransferMessage.collectAsState()
    val transferReport by viewModel.dataTransferReport.collectAsState()
    val restoreUiState by viewModel.backupRestoreUiState.collectAsState()
    val initialProfile by viewModel.initialUserProfile.collectAsState()
    val todayCheckIn by viewModel.todayCheckIn.collectAsState()
    val cloudAuth by viewModel.cloudAuthState.collectAsState()
    val cloudBackup by viewModel.cloudBackupState.collectAsState()
    val cloudConflictCurrent by viewModel.cloudConflictCurrent.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    var showInitialProfile by rememberSaveable { mutableStateOf(false) }
    var showTransferReport by rememberSaveable { mutableStateOf(false) }
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var deleteInProgress by rememberSaveable { mutableStateOf(false) }
    var deleteError by rememberSaveable { mutableStateOf<String?>(null) }
    // In-progress is modal: no touch-through or premature success notification.
    var manualCloudUploadStatus by remember { mutableStateOf("idle") }
    var manualCloudError by remember { mutableStateOf<String?>(null) }
    var showCloudRestoreSafety by rememberSaveable { mutableStateOf(false) }
    var showGuestWarning by rememberSaveable { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(viewModel::backupRecords)
    }
    val protectLocalBeforeCloudReplaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) {
            manualCloudUploadStatus = "conflict"
        } else {
            manualCloudUploadStatus = "savingLocal"
            viewModel.saveLocalArchiveBeforeCloudReplace(uri) { saved, code ->
                if (!saved) {
                    manualCloudError = code
                    manualCloudUploadStatus = "conflictFailed"
                } else {
                    manualCloudUploadStatus = "restoringCloud"
                    viewModel.keepCloudAndReplaceLocal { success, restoreCode ->
                        manualCloudError = restoreCode
                        manualCloudUploadStatus = if (success) "conflictSuccess" else "conflictFailed"
                    }
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::restoreRecords)
    }
    val reportSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let(viewModel::saveLatestDataTransferReport)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val accountDescription = stringResource(R.string.cloud_account_control_description)
                OutlinedButton(
                    onClick = { showAccountDialog = true },
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .semantics { contentDescription = accountDescription },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                    Text(
                        text = cloudAuth.session?.displayEmail ?: stringResource(R.string.cloud_account_login),
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                AppLanguageSelector()
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            CompactHomeActionGroup(
                onRecord = { onNavigate(AppTab.Record) },
                onProgram = { onNavigate(AppTab.Plan) },
                onOpenAppExplanation = onOpenAppExplanation,
                onProgramTargetPositioned = onProgramTargetPositioned
            )
        }
        item {
            TodaySummaryCard(summary)
        }
        item {
            CommunityEntryCard(onClick = onOpenCommunity)
        }
        item {
            HomeDailyCheckInCard(
                checkIn = todayCheckIn,
                onSave = viewModel::saveDailyCheckIn
            )
        }
        item {
            RecordManagementCard(
                message = transferMessage,
                report = transferReport,
                onBackup = {
                    backupLauncher.launch("whatyougottado_backup_$today.csv")
                },
                onRestore = {
                    restoreLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.ms-excel",
                            "text/*"
                        )
                    )
                },
                onShowReport = { showTransferReport = true }
            )
        }
        item {
            InitialProfileCard(
                profile = initialProfile,
                onOpen = { showInitialProfile = true }
            )
        }
        item(key = "installed-build-provenance") {
            HomeBuildProvenanceFooter()
        }
    }

    if (showInitialProfile) {
        InitialProfileDialog(
            profile = initialProfile,
            onDismiss = { showInitialProfile = false },
            onSave = { profile ->
                viewModel.saveInitialUserProfile(profile)
                showInitialProfile = false
            }
        )
    }
    if (showTransferReport && transferReport != null) {
        DataTransferReportDialog(
            report = transferReport!!,
            onDismiss = { showTransferReport = false },
            onSave = {
                reportSaveLauncher.launch("whatyougottado_diagnostic_${transferReport!!.operationId}.txt")
            }
        )
    }
    if (showAccountDialog) {
        CloudAccountDialog(
            auth = cloudAuth,
            backup = cloudBackup,
            onDismiss = { showAccountDialog = false },
            onLogin = {
                if (activity != null) viewModel.signInWithGoogle(activity)
            },
            onUpload = {
                showAccountDialog = false
                manualCloudUploadStatus = "uploading"
                manualCloudError = null
                viewModel.uploadCloudBackup { acknowledged, code ->
                    manualCloudError = code
                    manualCloudUploadStatus = when {
                        acknowledged -> "success"
                        code == "LINEAGE_CONFLICT" -> "conflict"
                        else -> "failed"
                    }
                }
            },
            onRestore = { showAccountDialog = false; viewModel.restoreCloudBackup() },
            onLogout = { viewModel.logoutCloud() },
            onDelete = {
                deleteError = null
                showAccountDialog = false
                showDeleteConfirmation = true
            }
        )
    }
    if (manualCloudUploadStatus == "conflict") {
        val cloudTime = cloudConflictCurrent?.createdAt?.let { timestamp ->
            runCatching {
                OffsetDateTime.parse(timestamp)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }.getOrNull()
        } ?: stringResource(R.string.cloud_conflict_unknown_time)
        AlertDialog(
            onDismissRequest = { manualCloudUploadStatus = "idle" },
            title = { Text(stringResource(R.string.cloud_conflict_title)) },
            text = { Text(stringResource(R.string.cloud_conflict_description, cloudTime)) },
            confirmButton = {
                Column {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cloudConflictCurrent != null,
                        onClick = {
                            manualCloudUploadStatus = "uploading"
                            manualCloudError = null
                            viewModel.keepLocalAndReplaceCloud { success, code ->
                                manualCloudError = code
                                manualCloudUploadStatus = if (success) "conflictSuccess" else "conflictFailed"
                            }
                        }
                    ) { Text(stringResource(R.string.cloud_conflict_local_wins)) }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cloudConflictCurrent != null,
                        onClick = {
                            manualCloudUploadStatus = "idle"
                            showCloudRestoreSafety = true
                        }
                    ) { Text(stringResource(R.string.cloud_conflict_cloud_wins)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { manualCloudUploadStatus = "idle" }) {
                    Text(stringResource(R.string.restore_cancel))
                }
            }
        )
    }
    if (showCloudRestoreSafety) {
        AlertDialog(
            onDismissRequest = {
                showCloudRestoreSafety = false
                manualCloudUploadStatus = "conflict"
            },
            title = { Text(stringResource(R.string.cloud_conflict_safety_title)) },
            text = { Text(stringResource(R.string.cloud_conflict_safety_description)) },
            confirmButton = {
                Button(onClick = {
                    showCloudRestoreSafety = false
                    protectLocalBeforeCloudReplaceLauncher.launch("whatyougottado_before_cloud_restore_$today.csv")
                }) { Text(stringResource(R.string.cloud_conflict_archive_first)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCloudRestoreSafety = false
                    manualCloudUploadStatus = "conflict"
                }) { Text(stringResource(R.string.restore_cancel)) }
            }
        )
    }
    if (manualCloudUploadStatus != "idle" && manualCloudUploadStatus != "conflict") {
        val loading = manualCloudUploadStatus in setOf("uploading", "savingLocal", "restoringCloud")
        AlertDialog(
            onDismissRequest = { if (!loading) manualCloudUploadStatus = "idle" },
            title = {
                Text(
                    when (manualCloudUploadStatus) {
                        "success" -> stringResource(R.string.cloud_upload_success)
                        "conflictSuccess" -> stringResource(R.string.cloud_conflict_success)
                        "failed" -> stringResource(R.string.cloud_upload_failed)
                        "conflictFailed" -> stringResource(R.string.cloud_conflict_failed)
                        "savingLocal" -> stringResource(R.string.cloud_conflict_saving_local)
                        "restoringCloud" -> stringResource(R.string.cloud_conflict_restoring)
                        else -> stringResource(R.string.cloud_backup_in_progress)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loading) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.cloud_conflict_progress))
                    } else if (manualCloudUploadStatus == "success") {
                        Text(stringResource(R.string.cloud_upload_completed))
                    } else if (manualCloudUploadStatus == "conflictSuccess") {
                        Text(stringResource(R.string.cloud_conflict_completed))
                    } else if (manualCloudUploadStatus == "failed") {
                        Text(stringResource(R.string.cloud_upload_error, manualCloudError ?: "-"))
                    } else {
                        Text(stringResource(R.string.cloud_conflict_error, manualCloudError ?: "-"))
                    }
                }
            },
            confirmButton = {
                if (!loading) {
                    TextButton(onClick = { manualCloudUploadStatus = "idle" }) {
                        Text(stringResource(R.string.cloud_continue))
                    }
                }
            }
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!deleteInProgress) showDeleteConfirmation = false },
            title = { Text("계정 영구 삭제") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("이 계정에 연결된 서버의 운동 기록, 개인 정보, 클라우드 백업 및 이 기기의 개인 기록이 삭제됩니다. 삭제된 자료는 복구할 수 없습니다.")
                    Text("직접 공개 공유한 프로그램은 '탈퇴한 사용자'의 게시물로 계속 공개됩니다. Google 계정 자체는 삭제되지 않습니다.")
                    Text("휴대폰 외부에 직접 저장한 CSV 백업 파일은 자동으로 삭제되지 않습니다.")
                    Text("계속하려면 Google 계정을 다시 선택해 본인임을 확인해야 합니다.")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (deleteInProgress) {
                        Text("계정 삭제 요청을 처리 중입니다. 앱을 종료하지 마세요.")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleteInProgress && activity != null,
                    onClick = {
                        val currentActivity = activity ?: return@Button
                        deleteInProgress = true
                        deleteError = null
                        viewModel.deleteCloudAccount(currentActivity) { message, backupCleanupPending ->
                            deleteInProgress = false
                            if (message == null) {
                                showDeleteConfirmation = false
                                android.widget.Toast.makeText(
                                    currentActivity,
                                    if (backupCleanupPending)
                                        "계정은 삭제됐습니다. 클라우드 파일 정리는 서버에서 계속 진행됩니다."
                                    else "계정과 개인 기록을 삭제했습니다.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                deleteError = message
                            }
                        }
                    }
                ) { Text("Google로 재확인하고 계정 삭제") }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleteInProgress,
                    onClick = { showDeleteConfirmation = false }
                ) { Text("취소") }
            }
        )
    }
    if (cloudAuth.firstLaunchChoiceRequired) {
        FirstLaunchAuthDialog(
            message = cloudAuth.message,
            onLogin = {
                if (activity != null) viewModel.signInWithGoogle(activity)
            },
            onGuest = { showGuestWarning = true }
        )
    }
    if (showGuestWarning) {
        GuestWarningDialog(
            onContinue = {
                showGuestWarning = false
                viewModel.chooseGuest()
            },
            onLogin = {
                showGuestWarning = false
                if (activity != null) viewModel.signInWithGoogle(activity)
            }
        )
    }
    BackupRestoreDialogHost(
        state = restoreUiState,
        onWorkoutMode = viewModel::chooseWorkoutRestoreMode,
        onExerciseMode = viewModel::chooseExerciseRestoreMode,
        onConfirm = viewModel::confirmPreparedRecordsRestore,
        onBackToWorkout = viewModel::backToWorkoutRestoreMode,
        onBackToExercise = viewModel::backToExerciseRestoreMode,
        onCancel = viewModel::cancelPreparedRecordsRestore
    )
}

/**
 * This footer identifies the installed APK. It never claims that the newest
 * GitHub revision is installed or that an update has been downloaded.
 */
@Composable
internal fun HomeBuildProvenanceFooter() {
    val timestamp = remember(BuildConfig.BUILD_TIMESTAMP_UTC) {
        formatBuildTimestampKst(BuildConfig.BUILD_TIMESTAMP_UTC)
    }
    val revision = remember(BuildConfig.GIT_COMMIT_SHA) {
        BuildConfig.GIT_COMMIT_SHA.takeIf { sha ->
            sha.length >= 8 && sha.take(8).all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' }
        }?.take(8) ?: "—"
    }
    val isCi = BuildConfig.BUILD_TIMESTAMP_SOURCE == "CI_START"
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = timestamp?.let {
                stringResource(
                    if (isCi) R.string.home_build_ci_timestamp else R.string.home_build_commit_timestamp,
                    it
                )
            } ?: stringResource(R.string.home_build_timestamp_unknown),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.home_build_version_revision, BuildConfig.VERSION_NAME, revision),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** ISO-8601 UTC or offset commit time -> fixed KST, independent of device timezone. */
internal fun formatBuildTimestampKst(value: String): String? = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA))
}.getOrNull()

@Composable
internal fun AppLanguageSelector(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val effectiveLanguage = AppLanguageRegistry.effectiveLanguage(configuration)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
    ) {
        AppLanguage.entries.forEach { language ->
            val selected = language == effectiveLanguage
            val label = stringResource(
                if (language == AppLanguage.KOREAN) R.string.language_korean
                else R.string.language_english
            )
            val description = stringResource(
                if (language == AppLanguage.KOREAN) R.string.language_korean_description
                else R.string.language_english_description
            )
            if (selected) {
                Button(
                    onClick = { AppLanguageRegistry.select(language) },
                    modifier = Modifier
                        .height(36.dp)
                        .semantics { contentDescription = description },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = { AppLanguageRegistry.select(language) },
                    modifier = Modifier
                        .height(36.dp)
                        .semantics { contentDescription = description },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun FirstLaunchAuthDialog(message: String?, onLogin: () -> Unit, onGuest: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.cloud_first_launch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cloud_first_launch_body))
                if (message != null && message != "AUTHENTICATING") {
                    Text(
                        stringResource(
                            if (message == "CLOUD_NOT_CONFIGURED") {
                                R.string.cloud_not_configured
                            } else if (message == "CLOUD_CURRENT_DISCOVERY_FAILED") {
                                R.string.cloud_current_discovery_failed
                            } else if (message == "GOOGLE_PROVIDER_NOT_CONFIGURED" ||
                                message == "PROVIDER_NOT_CONFIGURED") {
                                R.string.cloud_provider_not_configured
                            } else {
                                R.string.cloud_login_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Column {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cloud_login_google))
                }
                OutlinedButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cloud_continue_guest))
                }
            }
        }
    )
}

@Composable
private fun GuestWarningDialog(onContinue: () -> Unit, onLogin: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.cloud_guest_warning_title)) },
        text = { Text(stringResource(R.string.cloud_guest_warning_body)) },
        confirmButton = {
            Column {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cloud_guest_continue))
                }
                OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cloud_login_google))
                }
            }
        }
    )
}

@Composable
private fun CloudAccountDialog(
    auth: CloudAuthUiState,
    backup: com.training.trackplanner.data.CloudBackupState?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit
) {
    val message = auth.message?.let { code ->
        when (code) {
            "AUTHENTICATING" -> stringResource(R.string.cloud_authenticating)
            "CLOUD_NOT_CONFIGURED" -> stringResource(R.string.cloud_not_configured)
            "CLOUD_CURRENT_DISCOVERY_FAILED" -> stringResource(R.string.cloud_current_discovery_failed)
            "GOOGLE_PROVIDER_NOT_CONFIGURED" -> stringResource(R.string.cloud_provider_not_configured)
            "PROVIDER_NOT_CONFIGURED" -> stringResource(R.string.cloud_provider_not_configured)
            "NO_GOOGLE_CREDENTIAL" -> stringResource(R.string.cloud_google_unavailable)
            "AUTHENTICATION_FAILED" -> stringResource(R.string.cloud_login_failed)
            "AUTH_NETWORK_UNAVAILABLE",
            "AUTH_UNAVAILABLE" -> stringResource(R.string.cloud_auth_unavailable)
            "SESSION_EXPIRED" -> stringResource(R.string.cloud_session_expired)
            "LOGIN_REQUIRED" -> stringResource(R.string.cloud_login_required)
            "BACKUP_CURRENT" -> stringResource(R.string.cloud_backup_current)
            "BACKUP_UPLOADED_PENDING" -> stringResource(R.string.cloud_backup_uploaded_pending)
            "NO_CLOUD_BACKUP" -> stringResource(R.string.cloud_no_backup)
            "CLOUD_BACKUP_READY" -> stringResource(R.string.cloud_backup_ready)
            "CLOUD_RESTORE_FAILED" -> stringResource(R.string.cloud_restore_failed)
            "GUEST_CLOUD_COMPARISON_REQUIRED" -> stringResource(R.string.cloud_guest_comparison_required)
            "ACCOUNT_ARCHIVE_REQUIRED" -> stringResource(R.string.cloud_account_archive_required)
            else -> stringResource(R.string.cloud_operation_failed)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_account_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (auth.loggedIn) {
                    Text(auth.session?.displayEmail ?: stringResource(R.string.cloud_account_signed_in))
                    Text(
                        text = if (backup?.lastSuccessfulBackupAt != null) {
                            stringResource(R.string.cloud_last_backup_available)
                        } else {
                            stringResource(R.string.cloud_last_backup_none)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_upload_now))
                    }
                    OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_restore_now))
                    }
                    TextButton(onClick = { onLogout(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_logout))
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("계정 영구 삭제")
                    }
                } else {
                    Text(stringResource(R.string.cloud_account_signed_out))
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_login_google))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_close)) }
        }
    )
}

@Composable
internal fun BackupRestoreDialogHost(
    state: BackupRestoreUiState,
    onWorkoutMode: (WorkoutRestoreMode) -> Unit,
    onExerciseMode: (ExerciseListRestoreMode) -> Unit,
    onConfirm: () -> Unit,
    onBackToWorkout: () -> Unit,
    onBackToExercise: () -> Unit,
    onCancel: () -> Unit
) {
    when (state) {
        BackupRestoreUiState.Idle -> Unit
        BackupRestoreUiState.Preparing,
        BackupRestoreUiState.Restoring -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_progress_title)) },
            text = { Text(stringResource(R.string.restore_progress_body)) },
            confirmButton = {}
        )
        is BackupRestoreUiState.ChooseWorkoutMode -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.restore_workout_overlap_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_workout_overlap_impact,
                        state.impact.overlappingWorkoutDateCount,
                        state.impact.currentEntriesOnOverlappingDates,
                        state.impact.backupEntriesOnOverlappingDates
                    )
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { onWorkoutMode(WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES) }) {
                        Text(stringResource(R.string.restore_workout_replace))
                    }
                    TextButton(onClick = { onWorkoutMode(WorkoutRestoreMode.APPEND_TO_CURRENT) }) {
                        Text(stringResource(R.string.restore_workout_append))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_cancel)) }
            }
        )
        is BackupRestoreUiState.ChooseExerciseMode -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.restore_exercise_list_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.restore_exercise_list_impact,
                            state.impact.representedExerciseCount,
                            state.impact.backupOverrideFieldsThatWouldReplaceCurrentCount
                        )
                    )
                    if (state.impact.sameStableKeyCustomDefinitionsThatWouldBeReplacedCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_custom_definition_warning,
                                state.impact.sameStableKeyCustomDefinitionsThatWouldBeReplacedCount
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Column {
                    TextButton(
                        onClick = {
                            onExerciseMode(ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES)
                        }
                    ) { Text(stringResource(R.string.restore_exercise_preserve)) }
                    TextButton(
                        onClick = {
                            onExerciseMode(ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST)
                        }
                    ) { Text(stringResource(R.string.restore_exercise_apply)) }
                }
            },
            dismissButton = {
                Row {
                    if (state.impact.overlappingWorkoutDateCount > 0) {
                        TextButton(onClick = onBackToWorkout) {
                            Text(stringResource(R.string.restore_back))
                        }
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_cancel)) }
                }
            }
        )
        is BackupRestoreUiState.Confirm -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.restore_final_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (state.workoutMode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES) {
                                R.string.restore_workout_replace
                            } else {
                                R.string.restore_workout_append
                            }
                        )
                    )
                    Text(
                        stringResource(
                            if (state.exerciseMode == ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES) {
                                R.string.restore_exercise_preserve
                            } else {
                                R.string.restore_exercise_apply
                            }
                        )
                    )
                    if (state.impact.overlappingWorkoutDateCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_final_workout_overlap,
                                state.impact.overlappingWorkoutDateCount
                            )
                        )
                    }
                    if (state.impact.activeExercisesThatWouldDisappearCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_final_exercises_deactivated,
                                state.impact.activeExercisesThatWouldDisappearCount
                            )
                        )
                    }
                    if (state.impact.referencedExercisesRequiringInternalRetentionCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_final_references_retained,
                                state.impact.referencedExercisesRequiringInternalRetentionCount
                            )
                        )
                    }
                    if (state.impact.currentMetadataOverrideFieldsThatWouldBeRemovedCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_metadata_reset_warning,
                                state.impact.currentMetadataOverrideFieldsThatWouldBeRemovedCount
                            )
                        )
                    }
                    if (state.impact.sameStableKeyCustomDefinitionsThatWouldBeReplacedCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_custom_definition_warning,
                                state.impact.sameStableKeyCustomDefinitionsThatWouldBeReplacedCount
                            )
                        )
                    }
                    if (state.impact.outOfScopeSameSourceIdentityDivergenceCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_final_out_of_scope_source_preserved,
                                state.impact.outOfScopeSameSourceIdentityDivergenceCount
                            )
                        )
                    }
                    if (state.impact.sameSourceIdentityDifferentContentCount > 0) {
                        Text(
                            stringResource(
                                R.string.restore_same_source_divergence_note,
                                state.impact.sameSourceIdentityDifferentContentCount
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBackToExercise) {
                        Text(stringResource(R.string.restore_back))
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_cancel)) }
                }
            }
        )
        is BackupRestoreUiState.Failed -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.restore_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            BackupRestoreFailureReason.MALFORMED_BACKUP -> R.string.restore_malformed_backup
                            BackupRestoreFailureReason.STALE_PREFLIGHT -> R.string.restore_stale_preflight
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_close)) }
            }
        )
    }
}

@Composable
internal fun CompactHomeActionGroup(
    onRecord: () -> Unit,
    onProgram: () -> Unit,
    onOpenAppExplanation: () -> Unit,
    onProgramTargetPositioned: (Rect) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactHomeActionButton(
            title = "오늘 운동 기록하기",
            body = "오늘 계획과 수행 기록을 바로 확인합니다.",
            icon = Icons.Outlined.EditNote,
            emphasized = true,
            onClick = onRecord
        )
        CompactHomeActionButton(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onProgramTargetPositioned(coordinates.boundsInRoot())
            },
            title = "프로그램으로 시작하기",
            body = "프로그램을 선택해 날짜별 계획을 만듭니다.",
            icon = Icons.AutoMirrored.Outlined.EventNote,
            onClick = onProgram
        )
        CompactInfoNavigationRow(
            label = stringResource(R.string.home_app_explanation_label),
            description = stringResource(R.string.home_app_explanation_description),
            onClick = onOpenAppExplanation,
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun CompactHomeActionButton(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    icon: ImageVector,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
internal fun TodaySummaryCard(summary: HomeTodaySummaryState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "오늘 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val currentStatus = localizedUiText(
                "현재 상태: ${summary.fatigueCard.primary.score} · ${summary.fatigueCard.primary.label}"
            )
            val statusLines = listOfNotNull(
                localizedUiText("현재 상태"),
                summary.fatigueCard.phaseLabel?.let { localizedUiText(it) },
                currentStatus
            )
            Text(
                text = statusLines.joinToString("\n"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            val planText = summary.fatigueCard.projection?.let { projection ->
                "${summary.fatigueCard.projectionPrefix}: ${projection.score} · ${projection.label}"
            } ?: summary.fatigueCard.statusText.orEmpty()
            if (planText.isNotBlank()) {
                Text(
                    text = planText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.axisMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.levelCountMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.headline?.let { headline ->
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            summary.fatigueCard.actionLabel?.let { action ->
                Text(
                    text = "판단: $action",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            summary.fatigueCard.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "오늘 계획",
                    value = summary.plannedExerciseCount.toString()
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "완료 세트",
                    value = summary.confirmedSetCount.toString()
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "미확인 세트",
                    value = summary.unconfirmedSetCount.toString()
                )
            }
            MiniTrendChart(
                title = "최근 훈련량",
                currentPoints = summary.recentTrainingLoadSeries,
                projectedPoints = summary.projectedTrainingLoadSeries
            )
            MiniTrendChart(
                title = "피로도 변화",
                currentPoints = summary.recentFatigueSeries,
                projectedPoints = summary.projectedFatigueSeries
            )
        }
    }
}

@Composable
private fun MiniTrendChart(
    title: String,
    currentPoints: List<MiniTrendPoint>,
    projectedPoints: List<MiniTrendPoint>? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val projectedColor = MaterialTheme.colorScheme.tertiary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (projectedPoints != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("현재", style = MaterialTheme.typography.labelSmall, color = lineColor)
                    Text("끝나면 예상", style = MaterialTheme.typography.labelSmall, color = projectedColor)
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            drawLine(
                color = guideColor,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1f
            )
            if (currentPoints.isEmpty()) return@Canvas
            val allPoints = currentPoints + projectedPoints.orEmpty()
            val min = allPoints.minOf { it.value }
            val max = allPoints.maxOf { it.value }
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            fun pathFor(points: List<MiniTrendPoint>): Path {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = if (points.size == 1) size.width / 2f else {
                        size.width * index / (points.size - 1).toFloat()
                    }
                    val normalized = ((point.value - min) / range).toFloat()
                    val y = size.height - 4.dp.toPx() - normalized * (size.height - 8.dp.toPx())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }
            drawPath(
                path = pathFor(currentPoints),
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            projectedPoints?.let { points ->
                drawPath(
                    path = pathFor(points),
                    color = projectedColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun RecordManagementCard(
    message: String?,
    report: DataTransferReport?,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onShowReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "기록 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onBackup
                ) {
                    Text("기록 백업")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestore
                ) {
                    Text("기록 복원")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = report != null,
                onClick = onShowReport
            ) {
                Text("최근 작업 상세 보기")
            }
            if (report?.status == DataTransferStatus.RUNNING) {
                Text(
                    text = report.currentStage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun DataTransferReportDialog(
    report: DataTransferReport,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("최근 작업 상세") },
        text = {
            Text(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                text = LocalizedPresentation.dataTransferReportText(LocalContext.current, report),
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(report.detailText())) }) {
                Text("상세 내용 복사")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSave) {
                    Text("진단 보고서 파일로 저장")
                }
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        }
    )
}

@Composable
private fun InitialProfileCard(
    profile: InitialUserProfile?,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "초기 프로필",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (profile == null) {
                    "기록이 적은 기간의 피로 기준을 설정합니다. 건너뛰어도 됩니다."
                } else {
                    "초기 프로필이 저장되어 있습니다. 언제든 수정할 수 있습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen
            ) {
                Text(if (profile == null) "입력하기" else "수정하기")
            }
        }
    }
}
