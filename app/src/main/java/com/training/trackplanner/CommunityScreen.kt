package com.training.trackplanner

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.training.trackplanner.data.CommunityProgramLabels
import com.training.trackplanner.data.CommunityProgramLabelCatalog
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
    val duplicateImport by viewModel.duplicateImport.collectAsState()
    val pendingPublication by viewModel.pendingPublication.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
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
    var regionFilter by remember { mutableStateOf(emptyList<String>()) }
    var goalFilter by remember { mutableStateOf(emptyList<String>()) }
    var functionalGoalFilter by remember { mutableStateOf(emptyList<String>()) }
    var badmintonGoalFilter by remember { mutableStateOf(emptyList<String>()) }
    var lookupPreview by remember { mutableStateOf<CommunityFriendPreview?>(null) }
    var publicationProgram by remember { mutableStateOf<TrainingProgram?>(null) }
    var publicationExisting by remember { mutableStateOf<CommunityProgram?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(session) { if (session != null) viewModel.load() }
    LaunchedEffect(session, pendingPublication, programs, loaded) {
        if (session != null && loaded) pendingPublication?.let { program ->
            publicationProgram = program
            publicationExisting = programs.firstOrNull { it.isMine && it.sourceProgramStableKey == program.stableKey }
            viewModel.consumePublicationRequest()
        }
    }

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
    duplicateImport?.let { program ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateImport,
            title = { Text(stringResource(R.string.community_import_duplicate_title)) },
            text = { Text(stringResource(R.string.community_import_duplicate_message)) },
            confirmButton = {
                Button(onClick = { viewModel.importProgram(program, allowDuplicate = true) }) {
                    Text(stringResource(R.string.community_import))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDuplicateImport) { Text(stringResource(R.string.close)) } }
        )
    }
    publicationProgram?.let { program ->
        CommunityPublicationDialog(
            existing = publicationExisting,
            onDismiss = { publicationProgram = null; publicationExisting = null },
            onPublish = { labels, comment, caution ->
                viewModel.publishProgram(program, labels, comment, caution)
                publicationProgram = null
                publicationExisting = null
            }
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
                functionalGoalFilter = functionalGoalFilter,
                badmintonGoalFilter = badmintonGoalFilter,
                onRegionFilter = { regionFilter = it },
                onGoalFilter = { goalFilter = it },
                onFunctionalGoalFilter = { functionalGoalFilter = it },
                onBadmintonGoalFilter = { badmintonGoalFilter = it },
                onResetFilters = { regionFilter = emptyList(); goalFilter = emptyList(); functionalGoalFilter = emptyList(); badmintonGoalFilter = emptyList() },
                onSearch = { viewModel.search(search, sort, regionFilter, goalFilter, functionalGoalFilter, badmintonGoalFilter) },
                onSort = { sort = it; viewModel.search(search, it, regionFilter, goalFilter, functionalGoalFilter, badmintonGoalFilter) },
                onSelect = { program -> viewModel.openProgram(program) { detail -> selectedProgram = detail } },
                onLike = viewModel::like,
                onPublish = { program, existing -> publicationProgram = program; publicationExisting = existing },
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

private fun LazyListScope.ProgramsSection(
    programs: List<CommunityProgram>,
    localPrograms: List<TrainingProgram>,
    search: String,
    sort: String,
    onSearchChange: (String) -> Unit,
    regionFilter: List<String>,
    goalFilter: List<String>,
    functionalGoalFilter: List<String>,
    badmintonGoalFilter: List<String>,
    onRegionFilter: (List<String>) -> Unit,
    onGoalFilter: (List<String>) -> Unit,
    onFunctionalGoalFilter: (List<String>) -> Unit,
    onBadmintonGoalFilter: (List<String>) -> Unit,
    onResetFilters: () -> Unit,
    onSearch: () -> Unit,
    onSort: (String) -> Unit,
    onSelect: (CommunityProgram) -> Unit,
    onLike: (CommunityProgram) -> Unit,
    onPublish: (TrainingProgram, CommunityProgram?) -> Unit,
    onUnpublish: (CommunityProgram) -> Unit
) {
    item {
        OutlinedTextField(value = search, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.community_search)) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.community_search)) }
            CommunitySortDropdown(sort, onSort, Modifier.weight(1f))
        }
        CommunityMultiSelectDropdown(stringResource(R.string.community_strength_region), listOf("UPPER_BODY" to R.string.community_filter_upper, "LOWER_BODY" to R.string.community_filter_lower, "ALL_LIMBS" to R.string.community_filter_all_limbs), regionFilter, onRegionFilter)
        CommunityMultiSelectDropdown(stringResource(R.string.community_strength_goal), listOf("HYPERTROPHY" to R.string.community_filter_hypertrophy, "STRENGTH" to R.string.community_filter_strength), goalFilter, onGoalFilter)
        CommunityMultiSelectDropdown(stringResource(R.string.community_filter_functional_goals), listOf("EXPLOSIVE_ACCELERATION" to R.string.community_goal_explosive, "ELASTIC_GROUND_REACTION" to R.string.community_goal_elastic, "BODY_COORDINATION" to R.string.community_goal_coordination, "NOT_INCLUDED" to R.string.community_filter_functional_not_included), functionalGoalFilter, onFunctionalGoalFilter)
        CommunityMultiSelectDropdown(stringResource(R.string.community_filter_badminton_goals), listOf("SWING_POWER" to R.string.community_goal_swing, "LANDING_DECELERATION_STABILITY" to R.string.community_goal_landing, "FOOTWORK" to R.string.community_goal_footwork, "NOT_INCLUDED" to R.string.community_filter_badminton_not_included), badmintonGoalFilter, onBadmintonGoalFilter)
        TextButton(onClick = onResetFilters) { Text(stringResource(R.string.community_filter_reset)) }
    }
    if (localPrograms.isNotEmpty()) {
        item { Text(stringResource(R.string.community_publish), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(localPrograms.take(6), key = { "local-${it.id}" }) { program ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(program.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val existing = programs.firstOrNull { it.isMine && it.sourceProgramStableKey == program.stableKey }
                OutlinedButton(onClick = { onPublish(program, existing) }) { Text(stringResource(if (existing == null) R.string.community_publish else R.string.community_update)) }
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
            CommunityLabelText(program.labels, Modifier.fillMaxWidth(), maxLines = 2)
            Text(program.representativeExercises.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLike(program) }) { Text("${if (program.likedByMe) "♥" else "♡"} ${program.likeCount}") }
                if (program.isMine) TextButton(onClick = { onUnpublish(program) }) { Text(stringResource(R.string.community_unpublish)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityMultiSelectDropdown(
    label: String,
    options: List<Pair<String, Int>>,
    selectedValues: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pending by remember(expanded, selectedValues) { mutableStateOf(selectedValues) }
    val summaryParts = mutableListOf<String>()
    if (pending.isNotEmpty()) for (option in options) if (option.first in pending) summaryParts += stringResource(option.second)
    val summary = if (summaryParts.isEmpty()) stringResource(R.string.community_filter_all) else summaryParts.joinToString(" · ")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, textId) ->
                DropdownMenuItem(
                    text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(checked = value in pending, onCheckedChange = null); Text(stringResource(textId)) } },
                    onClick = { pending = if (value in pending) pending - value else pending + value }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.community_filter_apply), fontWeight = FontWeight.Bold) },
                onClick = { onSelectionChange(pending); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.close)) },
                onClick = { expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunitySortDropdown(sort: String, onSort: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(value = if (sort == "POPULAR") stringResource(R.string.community_popular) else stringResource(R.string.community_latest), onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.menuAnchor().fillMaxWidth(), label = { Text(stringResource(R.string.community_sort)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.community_latest)) }, onClick = { onSort("LATEST"); expanded = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.community_popular)) }, onClick = { onSort("POPULAR"); expanded = false })
        }
    }
}

@Composable
private fun CommunityLabelText(labels: CommunityProgramLabels, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val parts = mutableListOf<String>()
    labels.strengthRegions.forEach { value -> parts += stringResource(when (value) {
        "UPPER_BODY" -> R.string.community_filter_upper
        "LOWER_BODY" -> R.string.community_filter_lower
        else -> R.string.community_filter_all_limbs
    }) }
    labels.strengthGoals.forEach { value -> parts += stringResource(if (value == "HYPERTROPHY") R.string.community_filter_hypertrophy else R.string.community_filter_strength) }
    labels.functionalGoals.forEach { value -> parts += stringResource(when (value) {
        "EXPLOSIVE_ACCELERATION" -> R.string.community_goal_explosive
        "ELASTIC_GROUND_REACTION" -> R.string.community_goal_elastic
        else -> R.string.community_goal_coordination
    }) }
    labels.badmintonGoals.forEach { value -> parts += stringResource(when (value) {
        "SWING_POWER" -> R.string.community_goal_swing
        "LANDING_DECELERATION_STABILITY" -> R.string.community_goal_landing
        else -> R.string.community_goal_footwork
    }) }
    Text(parts.joinToString(" · "), modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
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
            CommunityLabelText(program.labels, maxLines = 2)
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

@Composable
private fun CommunityPublicationDialog(
    existing: CommunityProgram?,
    onDismiss: () -> Unit,
    onPublish: (CommunityProgramLabels, String, String) -> Unit
) {
    var region by remember(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.labels?.strengthRegions ?: emptyList()) }
    var goal by remember(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.labels?.strengthGoals ?: emptyList()) }
    var functionalGoal by remember(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.labels?.functionalGoals ?: emptyList()) }
    var badmintonGoal by remember(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.labels?.badmintonGoals ?: emptyList()) }
    var comment by rememberSaveable(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.authorComment.orEmpty()) }
    var caution by rememberSaveable(existing?.publicProgramId ?: "new") { mutableStateOf(existing?.cautionText.orEmpty()) }
    var error by rememberSaveable(existing?.publicProgramId ?: "new") { mutableStateOf<String?>(null) }
    val labelsRequired = stringResource(R.string.community_publish_labels_required)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.community_publish_form_title else R.string.community_update_form_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CommunityMultiSelectDropdown(stringResource(R.string.community_strength_region), listOf("UPPER_BODY" to R.string.community_filter_upper, "LOWER_BODY" to R.string.community_filter_lower, "ALL_LIMBS" to R.string.community_filter_all_limbs), region) { region = it } }
                item { CommunityMultiSelectDropdown(stringResource(R.string.community_strength_goal), listOf("HYPERTROPHY" to R.string.community_filter_hypertrophy, "STRENGTH" to R.string.community_filter_strength), goal) { goal = it } }
                item { CommunityMultiSelectDropdown(stringResource(R.string.community_functional_goals), listOf("EXPLOSIVE_ACCELERATION" to R.string.community_goal_explosive, "ELASTIC_GROUND_REACTION" to R.string.community_goal_elastic, "BODY_COORDINATION" to R.string.community_goal_coordination), functionalGoal) { functionalGoal = it } }
                item { CommunityMultiSelectDropdown(stringResource(R.string.community_badminton_goals), listOf("SWING_POWER" to R.string.community_goal_swing, "LANDING_DECELERATION_STABILITY" to R.string.community_goal_landing, "FOOTWORK" to R.string.community_goal_footwork), badmintonGoal) { badmintonGoal = it } }
                item { OutlinedTextField(comment, { comment = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.community_author_comment)) }, supportingText = { Text("${comment.length}/100") }) }
                item { OutlinedTextField(caution, { caution = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.community_caution)) }, supportingText = { Text("${caution.length}/200") }) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when {
                    region.isEmpty() || goal.isEmpty() -> labelsRequired
                    else -> null
                }
                if (error == null) onPublish(CommunityProgramLabelCatalog.normalize(region, goal, functionalGoal, badmintonGoal), comment, caution)
            }) { Text(stringResource(R.string.community_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
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

@Composable private fun CommunityMessage(message: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
