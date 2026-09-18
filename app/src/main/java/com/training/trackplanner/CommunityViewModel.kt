package com.training.trackplanner

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.training.trackplanner.data.CloudAuthRepository
import com.training.trackplanner.data.CloudAuthResult
import com.training.trackplanner.data.CloudAuthSession
import com.training.trackplanner.data.CommunityClient
import com.training.trackplanner.data.CommunityException
import com.training.trackplanner.data.CommunityFriendPreview
import com.training.trackplanner.data.CommunityFriendsState
import com.training.trackplanner.data.CommunityProgram
import com.training.trackplanner.data.CommunityProgramLabels
import com.training.trackplanner.data.CommunityProgramSnapshotCodec
import com.training.trackplanner.data.CommunityWeeklySummary
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CommunityWeeklySummaryCalculator
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

internal class CommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = CloudAuthRepository(application)
    private val db = TrainingDatabase.get(application)
    private val client = CommunityClient()
    private val canonicalMetadata = CanonicalExerciseMetadataRepository(application)
    /** Community observes the same application-scoped session as Home and Cloud Backup. */
    val session: StateFlow<CloudAuthSession?> = auth.session
    private val _profile = MutableStateFlow<com.training.trackplanner.data.CommunityProfile?>(null)
    val profile: StateFlow<com.training.trackplanner.data.CommunityProfile?> = _profile.asStateFlow()
    private val _programs = MutableStateFlow<List<CommunityProgram>>(emptyList())
    val programs: StateFlow<List<CommunityProgram>> = _programs.asStateFlow()
    private val _weekly = MutableStateFlow<List<CommunityWeeklySummary>>(emptyList())
    val weekly: StateFlow<List<CommunityWeeklySummary>> = _weekly.asStateFlow()
    private val _friends = MutableStateFlow(CommunityFriendsState())
    val friends: StateFlow<CommunityFriendsState> = _friends.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _duplicateImport = MutableStateFlow<CommunityProgram?>(null)
    val duplicateImport: StateFlow<CommunityProgram?> = _duplicateImport.asStateFlow()
    private val _pendingPublication = MutableStateFlow<TrainingProgram?>(null)
    val pendingPublication: StateFlow<TrainingProgram?> = _pendingPublication.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun load() {
        viewModelScope.launch {
            // Reuse a valid access token immediately, refreshing silently when it is near
            // expiry. A transient refresh failure returns the persisted session so Community
            // remains signed in and can surface a retryable request error instead of showing
            // a second Google sign-in flow.
            val current = auth.refreshIfNeeded()
            if (current == null) { _loaded.value = false; return@launch }
            _loading.value = true
            _loaded.value = false
            runCatching {
                val results = listOf(
                    async { client.profile(current) },
                    async { client.feed(current) },
                    async { client.weeklyFeed(current) },
                    async { client.friends(current) }
                ).awaitAll()
                @Suppress("UNCHECKED_CAST")
                _profile.value = results[0] as com.training.trackplanner.data.CommunityProfile
                @Suppress("UNCHECKED_CAST")
                _programs.value = results[1] as List<CommunityProgram>
                @Suppress("UNCHECKED_CAST")
                _weekly.value = results[2] as List<CommunityWeeklySummary>
                @Suppress("UNCHECKED_CAST")
                _friends.value = results[3] as CommunityFriendsState
                _message.value = null
                _loaded.value = true
            }.onFailure { error -> _message.value = error.communityMessage() }
            _loading.value = false
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = auth.signInWithGoogle(activity)) {
                is CloudAuthResult.Success -> {
                    load()
                }
                is CloudAuthResult.Failure -> _message.value = result.code
            }
            _loading.value = false
        }
    }

    fun setNickname(value: String) = launchRequest { session ->
        _profile.value = client.setNickname(session, value)
    }

    fun setPrivacy(lastWorkout: Boolean, currentTraining: Boolean, currentExercise: Boolean) = launchRequest { session ->
        _profile.value = client.setPrivacy(session, lastWorkout, currentTraining, currentExercise)
        _friends.value = client.friends(session)
    }

    fun search(query: String, sort: String, strengthRegions: List<String>, strengthGoals: List<String>, functionalGoals: List<String>, badmintonGoals: List<String>) = launchRequest { session ->
        _programs.value = client.feed(session, query, sort, strengthRegions, strengthGoals, functionalGoals, badmintonGoals)
    }

    fun like(program: CommunityProgram) = launchRequest { session ->
        val result = client.like(session, program.publicProgramId, !program.likedByMe)
        _programs.value = _programs.value.map {
            if (it.publicProgramId == program.publicProgramId) it.copy(likedByMe = result.first, likeCount = result.second) else it
        }
    }

    fun openProgram(program: CommunityProgram, onLoaded: (CommunityProgram) -> Unit) = launchRequest { session ->
        onLoaded(client.detail(session, program.publicProgramId))
    }

    fun importProgram(program: CommunityProgram, allowDuplicate: Boolean = false, onImported: (Long) -> Unit = {}) {
        val snapshot = program.snapshot ?: run { _message.value = "PROGRAM_DETAILS_REQUIRED"; return }
        viewModelScope.launch {
            val existing = db.communityProgramImportDao().findBySourcePublicProgramId(program.publicProgramId)
            val existingProgram = existing?.let { db.programDao().findProgramByStableKey(it.localProgramStableKey) }
            if (existingProgram != null && !allowDuplicate) {
                _duplicateImport.value = program
                return@launch
            }
            _duplicateImport.value = null
            runCatching { CommunityProgramSnapshotCodec.import(db, program.publicProgramId, snapshot) }
                .onSuccess(onImported)
                .onFailure { _message.value = it.communityMessage() }
        }
    }

    fun dismissDuplicateImport() { _duplicateImport.value = null }

    fun requestPublication(program: TrainingProgram) { _pendingPublication.value = program }
    fun consumePublicationRequest() { _pendingPublication.value = null }

    fun publishProgram(program: TrainingProgram, labels: CommunityProgramLabels, authorComment: String, cautionText: String) {
        launchRequest { session ->
            val snapshot = CommunityProgramSnapshotCodec.export(db, program.id)
            val published = client.publish(session, program.stableKey, program.updatedAt, snapshot, program.name, labels, authorComment, cautionText)
            _programs.value = listOf(published) + _programs.value.filterNot { it.publicProgramId == published.publicProgramId }
        }
    }

    fun unpublish(program: CommunityProgram) = launchRequest { session ->
        client.unpublish(session, program.publicProgramId)
        _programs.value = _programs.value.filterNot { it.publicProgramId == program.publicProgramId }
    }

    fun publishCurrentWeek() = launchRequest { session ->
        val today = LocalDate.now()
        val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val payload = withContext(Dispatchers.IO) {
            CommunityWeeklySummaryCalculator.payload(
                entries = db.workoutDao().allEntriesWithSets(),
                exercises = db.exerciseDao().allExercises(),
                start = start,
                end = today,
                runtimeMetadata = canonicalMetadata.runtimeMetadataCatalog()
            )
        }
        client.publishWeekly(session, start.toString(), payload)
        _weekly.value = client.weeklyFeed(session)
    }

    fun unpublishCurrentWeek() = launchRequest { session ->
        val today = LocalDate.now()
        val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString()
        client.unpublishWeekly(session, start)
        _weekly.value = client.weeklyFeed(session)
    }

    fun lookupFriend(code: String, onResult: (CommunityFriendPreview) -> Unit = {}) = launchRequest { session ->
        onResult(client.friendLookup(session, code))
    }

    fun sendFriendRequest(code: String) = launchRequest { session ->
        client.sendFriendRequest(session, code)
        _friends.value = client.friends(session)
    }

    fun respondFriendRequest(requestId: String, accept: Boolean) = launchRequest { session ->
        client.respondFriendRequest(session, requestId, accept)
        _friends.value = client.friends(session)
    }

    fun removeFriend(friendshipId: String) = launchRequest { session ->
        client.removeFriend(session, friendshipId)
        _friends.value = client.friends(session)
    }

    fun blockFriend(code: String) = launchRequest { session ->
        client.blockFriend(session, code)
        _friends.value = client.friends(session)
    }

    fun publishPerformedActivity(exerciseStableKey: String?, exerciseName: String?) {
        val current = auth.currentSession() ?: return
        viewModelScope.launch { runCatching { client.updateActivity(current, exerciseStableKey, exerciseName, System.currentTimeMillis()) } }
    }

    private fun launchRequest(block: suspend (CloudAuthSession) -> Unit) {
        val current = auth.currentSession()
        if (current == null) { _message.value = "LOGIN_REQUIRED"; return }
        viewModelScope.launch {
            _loading.value = true
            runCatching { block(current) }.onFailure { _message.value = it.communityMessage() }
            _loading.value = false
        }
    }

    private fun Throwable.communityMessage(): String = when (this) {
        is CommunityException -> code
        else -> message ?: "COMMUNITY_REQUEST_FAILED"
    }

}
