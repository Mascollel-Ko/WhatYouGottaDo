package com.training.trackplanner

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.CommunityFriendActivity
import com.training.trackplanner.data.CommunityFriendPreview
import com.training.trackplanner.data.CommunityFriendRequest
import com.training.trackplanner.data.CommunityProgram
import com.training.trackplanner.data.TrainingProgram

@Composable
internal fun CommunityEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(com.training.trackplanner.R.string.community_entry), fontWeight = FontWeight.Bold)
            Text(stringResource(com.training.trackplanner.R.string.community_title), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun CommunityScreen(
    viewModel: CommunityViewModel,
    trainingViewModel: TrainingViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val programs by viewModel.programs.collectAsState()
    val weekly by viewModel.weekly.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val localPrograms by trainingViewModel.programs.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var segment by rememberSaveable { mutableStateOf(1) }
    var nicknameDialog by rememberSaveable { mutableStateOf(false) }
    var privacyDialog by rememberSaveable { mutableStateOf(false) }
    var selectedProgram by remember { mutableStateOf<CommunityProgram?>(null) }
    var friendCode by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("LATEST") }
    var regionFilter by rememberSaveable { mutableStateOf("") }
    var goalFilter by rememberSaveable { mutableStateOf("") }
    var functionalFilter by rememberSaveable { mutableStateOf("ANY") }
    var badmintonFilter by rememberSaveable { mutableStateOf("ANY") }
    var lookupPreview by remember { mutableStateOf<CommunityFriendPreview?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(session) { if (session != null) viewModel.load() }

    if (session == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ScreenHeader(stringResource(R.string.community_title), stringResource(R.string.community_auth_required)) }
            item {
                Button(onClick = { (context as? Activity)?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.community_login))
                }
            }
            item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.navigation_back)) } }
        }
        return
    }

    if (nicknameDialog) {
        NicknameDialog(
            initial = profile?.nickname.orEmpty(),
            onDismiss = { nicknameDialog = false },
            onSave = { value -> viewModel.setNickname(value); nicknameDialog = false }
        )
    }
    if (privacyDialog) {
        PrivacyDialog(
            profile = profile,
            onDismiss = { privacyDialog = false },
            onSave = { last, status, exercise -> viewModel.setPrivacy(last, status, exercise); privacyDialog = false }
        )
    }
    lookupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { lookupPreview = null },
            title = { Text(preview.nickname) },
            text = { Text("♥ ${preview.receivedLikeCount} · ${preview.friendCode}") },
            confirmButton = {
                Button(onClick = { viewModel.sendFriendRequest(preview.friendCode); lookupPreview = null }) { Text(stringResource(R.string.community_add)) }
            },
            dismissButton = { TextButton(onClick = { lookupPreview = null }) { Text(stringResource(R.string.close)) } }
        )
    }
    selectedProgram?.let { program ->
        ProgramDetailDialog(
            program = program,
            onDismiss = { selectedProgram = null },
            onLike = { viewModel.like(program) },
            onImport = { viewModel.importProgram(program); selectedProgram = null }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.navigation_back)) }
                OutlinedButton(onClick = { nicknameDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.community_nickname_change), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        item { CommunityProfileHeader(profile = profile, onCopy = { profile?.friendCode?.let { clipboard.setText(AnnotatedString(it)) } }, onPrivacy = { privacyDialog = true }) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.community_friends, R.string.community_programs, R.string.community_weekly).forEachIndexed { index, label ->
                    if (segment == index) Button(onClick = { segment = index }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(label), maxLines = 1) }
                    else OutlinedButton(onClick = { segment = index }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(label), maxLines = 1) }
                }
            }
        }
        message?.let { error -> item { CommunityMessage(error) } }
        when (segment) {
            0 -> FriendsSection(
                friendCode = friendCode,
                onFriendCodeChange = { friendCode = it },
                onLookup = { viewModel.lookupFriend(friendCode) { lookupPreview = it } },
                state = friends,
                onRespond = viewModel::respondFriendRequest,
                onRemove = viewModel::removeFriend
            )
            1 -> ProgramsSection(
                programs = programs,
                localPrograms = localPrograms,
                search = search,
                sort = sort,
                onSearchChange = { search = it },
                regionFilter = regionFilter,
                goalFilter = goalFilter,
                functionalFilter = functionalFilter,
                badmintonFilter = badmintonFilter,
                onRegionFilter = { regionFilter = if (regionFilter == it) "" else it },
                onGoalFilter = { goalFilter = if (goalFilter == it) "" else it },
                onFunctionalFilter = { functionalFilter = if (functionalFilter == it) "ANY" else it },
                onBadmintonFilter = { badmintonFilter = if (badmintonFilter == it) "ANY" else it },
                onResetFilters = { regionFilter = ""; goalFilter = ""; functionalFilter = "ANY"; badmintonFilter = "ANY" },
                onSearch = { viewModel.search(search, sort, regionFilter.ifBlank { null }, goalFilter.ifBlank { null }, filterBoolean(functionalFilter), filterBoolean(badmintonFilter)) },
                onSort = { sort = it; viewModel.search(search, it, regionFilter.ifBlank { null }, goalFilter.ifBlank { null }, filterBoolean(functionalFilter), filterBoolean(badmintonFilter)) },
                onSelect = { program -> viewModel.openProgram(program) { detail -> selectedProgram = detail } },
                onLike = viewModel::like,
                onPublish = viewModel::publishProgram,
                onUnpublish = viewModel::unpublish
            )
            else -> WeeklySection(weekly, onPublish = viewModel::publishCurrentWeek, onUnpublish = viewModel::unpublishCurrentWeek)
        }
    }
}

@Composable
private fun CommunityProfileHeader(profile: com.training.trackplanner.data.CommunityProfile?, onCopy: () -> Unit, onPrivacy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile?.nickname ?: stringResource(R.string.community_nickname), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("♥ ${profile?.receivedLikeCount ?: 0}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.community_friend_code)} ${profile?.friendCode.orEmpty()}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onCopy) { Text(stringResource(R.string.community_copy)) }
            }
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_privacy_title)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.ProgramsSection(
    programs: List<CommunityProgram>,
    localPrograms: List<TrainingProgram>,
    search: String,
    sort: String,
    onSearchChange: (String) -> Unit,
    regionFilter: String,
    goalFilter: String,
    functionalFilter: String,
    badmintonFilter: String,
    onRegionFilter: (String) -> Unit,
    onGoalFilter: (String) -> Unit,
    onFunctionalFilter: (String) -> Unit,
    onBadmintonFilter: (String) -> Unit,
    onResetFilters: () -> Unit,
    onSearch: () -> Unit,
    onSort: (String) -> Unit,
    onSelect: (CommunityProgram) -> Unit,
    onLike: (CommunityProgram) -> Unit,
    onPublish: (TrainingProgram) -> Unit,
    onUnpublish: (CommunityProgram) -> Unit
) {
    item {
        OutlinedTextField(value = search, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.community_search)) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.community_search)) }
            FilterChip(selected = sort == "LATEST", onClick = { onSort("LATEST") }, label = { Text(stringResource(R.string.community_latest)) })
            FilterChip(selected = sort == "POPULAR", onClick = { onSort("POPULAR") }, label = { Text(stringResource(R.string.community_popular)) })
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = regionFilter == "UPPER_BODY", onClick = { onRegionFilter("UPPER_BODY") }, label = { Text(stringResource(R.string.community_filter_upper)) })
            FilterChip(selected = regionFilter == "LOWER_BODY", onClick = { onRegionFilter("LOWER_BODY") }, label = { Text(stringResource(R.string.community_filter_lower)) })
            FilterChip(selected = regionFilter == "ALL_LIMBS", onClick = { onRegionFilter("ALL_LIMBS") }, label = { Text(stringResource(R.string.community_filter_all_limbs)) })
            FilterChip(selected = goalFilter == "HYPERTROPHY", onClick = { onGoalFilter("HYPERTROPHY") }, label = { Text(stringResource(R.string.community_filter_hypertrophy)) })
            FilterChip(selected = goalFilter == "STRENGTH", onClick = { onGoalFilter("STRENGTH") }, label = { Text(stringResource(R.string.community_filter_strength)) })
            FilterChip(selected = functionalFilter == "true", onClick = { onFunctionalFilter("true") }, label = { Text(stringResource(R.string.community_filter_functional)) })
            FilterChip(selected = badmintonFilter == "true", onClick = { onBadmintonFilter("true") }, label = { Text(stringResource(R.string.community_filter_badminton)) })
            TextButton(onClick = onResetFilters) { Text(stringResource(R.string.community_filter_reset)) }
        }
    }
    if (localPrograms.isNotEmpty()) {
        item { Text(stringResource(R.string.community_publish), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(localPrograms.take(6), key = { "local-${it.id}" }) { program ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(program.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = { onPublish(program) }) { Text(stringResource(R.string.community_publish)) }
            }
        }
    }
    item { Text(stringResource(R.string.community_programs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    if (programs.isEmpty()) item { Text(stringResource(R.string.community_no_programs)) }
    else items(programs, key = { it.publicProgramId }) { program ->
        ProgramCard(program, onSelect, onLike, onUnpublish)
    }
}

@Composable
private fun ProgramCard(program: CommunityProgram, onSelect: (CommunityProgram) -> Unit, onLike: (CommunityProgram) -> Unit, onUnpublish: (CommunityProgram) -> Unit) {
    Card(onClick = { onSelect(program) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${program.nickname}  ♥ ${program.authorReceivedLikeCount}", style = MaterialTheme.typography.labelMedium)
            Text(program.programName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(program.representativeExercises.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLike(program) }) { Text("${if (program.likedByMe) "♥" else "♡"} ${program.likeCount}") }
                if (program.isMine) TextButton(onClick = { onUnpublish(program) }) { Text(stringResource(R.string.community_unpublish)) }
            }
        }
    }
}

private fun LazyListScope.FriendsSection(friendCode: String, onFriendCodeChange: (String) -> Unit, onLookup: () -> Unit, state: com.training.trackplanner.data.CommunityFriendsState, onRespond: (String, Boolean) -> Unit, onRemove: (String) -> Unit) {
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = friendCode, onValueChange = onFriendCodeChange, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.community_friend_code_input)) }, singleLine = true)
            Button(onClick = onLookup, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.community_add)) }
        }
    }
    item { Text(stringResource(R.string.community_incoming), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    if (state.incoming.isEmpty()) item { Text(stringResource(R.string.community_no_friends)) }
    else items(state.incoming, key = { it.requestId }) { request ->
        RequestCard(request, incoming = true, onRespond = onRespond)
    }
    item { Text(stringResource(R.string.community_outgoing), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    items(state.outgoing, key = { "out-${it.requestId}" }) { request -> RequestCard(request, incoming = false, onRespond = onRespond) }
    item { Text(stringResource(R.string.community_friends), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    items(state.friends, key = { "friend-${it.friendshipId}" }) { friend ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${friend.nickname}  ♥ ${friend.receivedLikeCount}")
            friend.friendshipId?.let { id -> TextButton(onClick = { onRemove(id) }) { Text(stringResource(R.string.community_remove_friend)) } }
        } }
    }
    items(state.activity, key = { "activity-${it.nickname}" }) { activity -> ActivityCard(activity) }
}

@Composable private fun RequestCard(request: CommunityFriendRequest, incoming: Boolean, onRespond: (String, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${request.nickname}  ♥ ${request.receivedLikeCount}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (incoming) { TextButton(onClick = { onRespond(request.requestId, true) }) { Text(stringResource(R.string.community_accept)) }; TextButton(onClick = { onRespond(request.requestId, false) }) { Text(stringResource(R.string.community_decline)) } }
    } }
}

@Composable private fun ActivityCard(activity: CommunityFriendActivity) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text("${activity.nickname}  ♥ ${activity.receivedLikeCount}")
        if (activity.isTraining == true) Text("${stringResource(R.string.community_current_training)} · ${activity.currentExerciseName.orEmpty()}")
        activity.lastWorkoutAt?.let { Text("${stringResource(R.string.community_last_workout)} $it", style = MaterialTheme.typography.bodySmall) }
    } }
}

private fun LazyListScope.WeeklySection(weekly: List<com.training.trackplanner.data.CommunityWeeklySummary>, onPublish: () -> Unit, onUnpublish: () -> Unit) {
    item { Button(onClick = onPublish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_publish_weekly)) } }
    if (weekly.isEmpty()) item { Text(stringResource(R.string.community_no_weekly)) }
    else items(weekly, key = { it.summaryId }) { summary ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${summary.nickname}  ♥ ${summary.authorReceivedLikeCount}")
            Text(summary.weekStart)
            Text(summary.payload.toString(), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onUnpublish) { Text(stringResource(R.string.community_unpublish)) }
        } }
    }
}

@Composable private fun ProgramDetailDialog(program: CommunityProgram, onDismiss: () -> Unit, onLike: () -> Unit, onImport: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(program.programName) },
        text = { Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${program.nickname}  ♥ ${program.authorReceivedLikeCount}")
            Text(program.representativeExercises.joinToString(" · "))
            if (program.authorComment.isNotBlank()) Text(program.authorComment)
            if (program.cautionText.isNotBlank()) Text(program.cautionText, color = MaterialTheme.colorScheme.error)
            Text("${stringResource(R.string.community_like)} ${program.likeCount}")
        } },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_import)) }
                OutlinedButton(onClick = onLike, modifier = Modifier.fillMaxWidth()) { Text("♥ ${program.likeCount}") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

@Composable private fun NicknameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.community_nickname_change)) }, text = { OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.community_nickname)) }, singleLine = true) }, confirmButton = { Button(onClick = { onSave(value) }) { Text(stringResource(R.string.community_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun PrivacyDialog(profile: com.training.trackplanner.data.CommunityProfile?, onDismiss: () -> Unit, onSave: (Boolean, Boolean, Boolean) -> Unit) {
    var last by rememberSaveable(profile?.friendCode) { mutableStateOf(profile?.shareLastWorkoutTime == true) }
    var status by rememberSaveable(profile?.friendCode) { mutableStateOf(profile?.shareCurrentTrainingStatus == true) }
    var exercise by rememberSaveable(profile?.friendCode) { mutableStateOf(profile?.shareCurrentExerciseName == true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.community_privacy_title)) }, text = { Column {
        PrivacyRow(stringResource(R.string.community_privacy_last_workout), last) { last = it }
        PrivacyRow(stringResource(R.string.community_privacy_training), status) { status = it }
        PrivacyRow(stringResource(R.string.community_privacy_exercise), exercise) { exercise = it }
    } }, confirmButton = { Button(onClick = { onSave(last, status, exercise) }) { Text(stringResource(R.string.community_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun PrivacyRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(checked, onChecked); Text(label, Modifier.padding(top = 12.dp)) } }

private fun filterBoolean(value: String): Boolean? = when (value) {
    "true" -> true
    "false" -> false
    else -> null
}

@Composable private fun CommunityMessage(message: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
