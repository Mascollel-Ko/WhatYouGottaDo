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
    private val _session = MutableStateFlow(auth.currentSession())
    val session: StateFlow<CloudAuthSession?> = _session.asStateFlow()
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

    fun load() {
        val current = auth.currentSession()
        _session.value = current
        if (current == null) return
        viewModelScope.launch {
            _loading.value = true
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
            }.onFailure { error -> _message.value = error.communityMessage() }
            _loading.value = false
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = auth.signInWithGoogle(activity)) {
                is CloudAuthResult.Success -> {
                    _session.value = result.session
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

    fun search(query: String, sort: String, region: String?, goal: String?, functional: Boolean?, badminton: Boolean?) = launchRequest { session ->
        _programs.value = client.feed(session, query, sort, region, goal, functional, badminton)
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

    fun importProgram(program: CommunityProgram, onImported: (Long) -> Unit = {}) {
        val snapshot = program.snapshot ?: run { _message.value = "PROGRAM_DETAILS_REQUIRED"; return }
        viewModelScope.launch {
            runCatching { CommunityProgramSnapshotCodec.import(db, snapshot) }
                .onSuccess(onImported)
                .onFailure { _message.value = it.communityMessage() }
        }
    }

    fun publishProgram(program: TrainingProgram, labels: CommunityProgramLabels = CommunityProgramLabels("ALL_LIMBS", "STRENGTH", false, null, false, null)) {
        launchRequest { session ->
            val snapshot = CommunityProgramSnapshotCodec.export(db, program.id)
            val published = client.publish(session, program.stableKey, program.updatedAt, snapshot, program.name, labels, "", "")
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
        val records = withContext(Dispatchers.IO) { db.workoutDao().allEntriesWithSets().filter { it.entry.date in start.toString()..today.toString() } }
        val payload = JSONObject()
            .put("weekStart", start.toString())
            .put("weekEnd", today.toString())
            .put("trainingDays", records.map { it.entry.date }.distinct().size)
            .put("strengthSessionCount", records.count { it.entry.category != "배드민턴" && it.entry.category != "BADMINTON" })
            .put("confirmedStrengthSetCount", records.filter { it.entry.category != "배드민턴" && it.entry.category != "BADMINTON" }.sumOf { row -> row.sets.count { it.confirmed } })
            .put("badmintonSessionCount", records.count { it.entry.category == "배드민턴" || it.entry.category == "BADMINTON" })
            .put("badmintonMinutes", records.filter { it.entry.category == "배드민턴" || it.entry.category == "BADMINTON" }.sumOf { row -> row.sets.sumOf { it.seconds } } / 60)
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
        val current = _session.value ?: return
        viewModelScope.launch { runCatching { client.updateActivity(current, exerciseStableKey, exerciseName, System.currentTimeMillis()) } }
    }

    private fun launchRequest(block: suspend (CloudAuthSession) -> Unit) {
        val current = _session.value ?: auth.currentSession()
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
