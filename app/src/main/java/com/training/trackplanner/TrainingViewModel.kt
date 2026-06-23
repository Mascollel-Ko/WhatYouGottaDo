package com.training.trackplanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.coach.BadmintonTransferCoverageSummary
import com.training.trackplanner.analysis.coach.CoachAnalysisInsightBuilder
import com.training.trackplanner.analysis.coach.CoachAnalysisInsightSummary
import com.training.trackplanner.analysis.coach.CoachCheckInInterpreter
import com.training.trackplanner.analysis.coach.CoachFatigueCauseAnalyzer
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisMapper
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.data.AnalysisStats
import com.training.trackplanner.data.CalendarConflictMode
import com.training.trackplanner.data.CalendarConflictSummary
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyRecordSummary
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRuntimeMetadataEditorData
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.ProgramApplyConflictSummary
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingRepository
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrainingRepository(TrainingDatabase.get(application), application)
    private val currentDate = LocalDate.now()

    val exercises: StateFlow<List<Exercise>> = repository.exercises.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val programs: StateFlow<List<TrainingProgram>> = repository.programs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val analysisStats: StateFlow<AnalysisStats> = repository.analysisStats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AnalysisStats()
    )

    val initialUserProfile: StateFlow<InitialUserProfile?> = repository.initialUserProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val todayCheckIn: StateFlow<DailyCheckIn?> = repository.observeCheckInForDate(currentDate.toString()).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val recentCheckIns: StateFlow<List<DailyCheckIn>> = repository.observeRecentCheckIns(
        currentDate.minusDays(13).toString(),
        currentDate.toString()
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _todayReadinessSummary = MutableStateFlow<TodayReadinessSummary?>(null)
    val todayReadinessSummary: StateFlow<TodayReadinessSummary?> =
        _todayReadinessSummary.asStateFlow()

    private val _phaseAwareTodayStatus = MutableStateFlow<PhaseAwareTodayStatus?>(null)
    val phaseAwareTodayStatus: StateFlow<PhaseAwareTodayStatus?> =
        _phaseAwareTodayStatus.asStateFlow()

    private val _homeTodaySummary = MutableStateFlow(HomeTodaySummaryState.empty())
    val homeTodaySummary: StateFlow<HomeTodaySummaryState> = _homeTodaySummary.asStateFlow()

    private val _performanceTrendSummary = MutableStateFlow<PerformanceTrendSummary?>(null)
    val performanceTrendSummary: StateFlow<PerformanceTrendSummary?> =
        _performanceTrendSummary.asStateFlow()

    private val _badmintonTransferSummary = MutableStateFlow<BadmintonTransferSummary?>(null)
    val badmintonTransferSummary: StateFlow<BadmintonTransferSummary?> =
        _badmintonTransferSummary.asStateFlow()

    private var coachFatigueCauses = CoachFatigueCauseSummary.insufficient()
    private var coachTransferCoverage = BadmintonTransferCoverageSummary.insufficient()
    private var coachCheckInGuidance: List<String> = emptyList()
    private val _coachAnalysisInsight = MutableStateFlow(CoachAnalysisInsightSummary.empty())
    val coachAnalysisInsight: StateFlow<CoachAnalysisInsightSummary> =
        _coachAnalysisInsight.asStateFlow()
    private val _coachingSignalsSummary = MutableStateFlow(CoachingSignalsSummary.empty())
    val coachingSignalsSummary: StateFlow<CoachingSignalsSummary> =
        _coachingSignalsSummary.asStateFlow()

    private var fatigueAnalysisHistory: List<DailyFatigueResult> = emptyList()
    private var contributionSourcesUserSelected = false
    private val _fatigueAnalysisState = MutableStateFlow(FatigueAnalysisUiState())
    val fatigueAnalysisState: StateFlow<FatigueAnalysisUiState> = _fatigueAnalysisState.asStateFlow()

    private val _recordTransferMessage = MutableStateFlow<String?>(null)
    val recordTransferMessage: StateFlow<String?> =
        _recordTransferMessage.asStateFlow()

    private val _exerciseRuntimeMetadata = MutableStateFlow<Map<Long, RuntimeExerciseMetadata>>(emptyMap())
    val exerciseRuntimeMetadata: StateFlow<Map<Long, RuntimeExerciseMetadata>> =
        _exerciseRuntimeMetadata.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfNeeded()
            refreshExerciseRuntimeMetadataInternal()
            refreshAnalysisSummaries()
        }
    }

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        repository.entriesForDate(date)

    fun entryCount(date: String): Flow<Int> =
        repository.entryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        repository.plannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        repository.confirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        repository.dailySummaries(startDate, endDate)

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        repository.programItems(programId)

    fun metricForDate(date: String): Flow<DailyMetric?> =
        repository.metricForDate(date)

    fun saveDailyCheckIn(checkIn: DailyCheckIn) {
        viewModelScope.launch {
            repository.upsertDailyCheckIn(checkIn)
            refreshAnalysisSummaries()
        }
    }

    fun addWorkout(date: String, exerciseId: Long, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val entryId = repository.addWorkoutEntry(date, exerciseId)
            refreshAnalysisSummaries()
            if (entryId > 0) onAdded(entryId)
        }
    }

    fun updateWorkoutEntry(entry: WorkoutEntry) {
        viewModelScope.launch {
            repository.updateWorkoutEntry(entry)
            refreshAnalysisSummaries()
        }
    }

    fun deleteWorkoutEntry(entry: WorkoutEntry) {
        viewModelScope.launch {
            repository.deleteWorkoutEntry(entry)
            refreshAnalysisSummaries()
        }
    }

    fun addSet(entry: WorkoutEntry) {
        viewModelScope.launch {
            repository.addSet(entry)
            refreshAnalysisSummaries()
        }
    }

    fun updateSet(set: WorkoutSet) {
        viewModelScope.launch {
            repository.updateSet(set)
            refreshAnalysisSummaries()
        }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch {
            repository.deleteSet(set)
            refreshAnalysisSummaries()
        }
    }

    fun createProgram() {
        viewModelScope.launch {
            repository.createProgram()
        }
    }

    fun generateProgramSkeleton(
        request: ProgramSkeletonRequest,
        onResult: (GeneratedProgramSkeleton) -> Unit
    ) {
        viewModelScope.launch {
            onResult(repository.generateProgramSkeleton(request))
        }
    }

    fun saveGeneratedProgram(
        existingProgramId: Long?,
        skeleton: GeneratedProgramSkeleton,
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            onSaved(repository.saveGeneratedProgram(existingProgramId, skeleton))
        }
    }

    fun deleteProgram(programId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProgram(programId)
            onDeleted()
        }
    }

    fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseId: Long
    ) {
        viewModelScope.launch {
            repository.addExerciseToProgram(programId, weekNumber, dayOfWeek, exerciseId)
        }
    }

    fun updateProgramItem(item: TrainingProgramItem) {
        viewModelScope.launch {
            repository.updateProgramItem(item)
        }
    }

    fun deleteProgramItem(item: TrainingProgramItem) {
        viewModelScope.launch {
            repository.deleteProgramItem(item)
        }
    }

    fun applyProgram(
        programId: Long,
        startDate: String,
        mode: ProgramApplyMode
    ) {
        viewModelScope.launch {
            repository.applyProgramToDates(programId, startDate, mode)
            refreshAnalysisSummaries()
        }
    }

    fun checkProgramDateConflicts(
        programId: Long,
        startDate: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            onResult(repository.programHasDateConflicts(programId, startDate))
        }
    }

    fun loadProgramApplyConflictSummary(
        programId: Long,
        startDate: String,
        onResult: (ProgramApplyConflictSummary) -> Unit
    ) {
        viewModelScope.launch {
            onResult(repository.programApplyConflictSummary(programId, startDate))
        }
    }

    fun saveDailyMetric(date: String, sleepHours: Double?, bodyWeightKg: Double?) {
        viewModelScope.launch {
            repository.saveDailyMetric(date, sleepHours, bodyWeightKg)
            refreshAnalysisSummaries()
        }
    }

    fun saveInitialUserProfile(profile: InitialUserProfile) {
        viewModelScope.launch {
            repository.saveInitialUserProfile(profile)
            refreshAnalysisSummaries()
        }
    }

    fun loadCalendarConflictSummary(
        dates: List<String>,
        onResult: (CalendarConflictSummary) -> Unit
    ) {
        viewModelScope.launch {
            onResult(repository.calendarConflictSummary(dates))
        }
    }

    fun copyDate(
        sourceDate: String,
        targetDate: String,
        keepConfirmed: Boolean,
        conflictMode: CalendarConflictMode
    ) {
        viewModelScope.launch {
            repository.copyDate(sourceDate, targetDate, keepConfirmed, conflictMode)
            refreshAnalysisSummaries()
        }
    }

    fun moveDate(
        sourceDate: String,
        targetDate: String,
        conflictMode: CalendarConflictMode
    ) {
        viewModelScope.launch {
            repository.moveDate(sourceDate, targetDate, conflictMode)
            refreshAnalysisSummaries()
        }
    }

    fun deleteDate(date: String) {
        viewModelScope.launch {
            repository.deleteDate(date)
            refreshAnalysisSummaries()
        }
    }

    fun deleteDateRange(startDate: String, endDate: String, includeConfirmed: Boolean) {
        viewModelScope.launch {
            repository.deleteDateRange(startDate, endDate, includeConfirmed)
            refreshAnalysisSummaries()
        }
    }

    fun setExerciseActive(exerciseId: Long, active: Boolean) {
        viewModelScope.launch {
            repository.setExerciseActive(exerciseId, active)
            refreshAnalysisSummaries()
        }
    }

    fun loadExerciseEditor(
        exerciseId: Long?,
        onLoaded: (ExerciseRuntimeMetadataEditorData) -> Unit
    ) {
        viewModelScope.launch {
            onLoaded(repository.exerciseEditorData(exerciseId))
        }
    }

    fun refreshExerciseRuntimeMetadata() {
        viewModelScope.launch { refreshExerciseRuntimeMetadataInternal() }
    }

    fun saveExerciseEditor(
        data: ExerciseRuntimeMetadataEditorData,
        onResult: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching { repository.saveExerciseEditor(data) }
            if (result.isSuccess) {
                refreshExerciseRuntimeMetadataInternal()
                refreshAnalysisSummaries()
            }
            onResult(result)
        }
    }

    private suspend fun refreshExerciseRuntimeMetadataInternal() {
        _exerciseRuntimeMetadata.value = repository.resolvedRuntimeMetadataByExerciseId()
    }

    fun deleteExerciseIfUnused(exerciseId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteExerciseIfUnused(exerciseId)
            onResult(result.deleted)
            refreshAnalysisSummaries()
        }
    }

    fun copyDateRangeAsPlan(
        sourceStart: String,
        sourceEnd: String,
        targetStart: String,
        conflictMode: CalendarConflictMode
    ) {
        viewModelScope.launch {
            repository.copyDateRangeAsPlan(sourceStart, sourceEnd, targetStart, conflictMode)
            refreshAnalysisSummaries()
        }
    }

    fun refreshTodayReadiness() {
        viewModelScope.launch {
            refreshAnalysisSummaries()
        }
    }

    fun refreshPerformanceTrend() {
        viewModelScope.launch {
            refreshAnalysisSummaries()
        }
    }

    fun refreshBadmintonTransfer() {
        viewModelScope.launch {
            refreshAnalysisSummaries()
        }
    }

    fun selectFatigueAnalysisPeriod(period: FatigueAnalysisPeriod) {
        rebuildFatigueAnalysis(period = period)
    }

    fun toggleFatigueTrendTarget(target: FatigueTarget) {
        val selected = _fatigueAnalysisState.value.detail.selectedFatigueTargets.toMutableSet()
        if (target in selected && selected.size > 1) selected.remove(target) else selected.add(target)
        rebuildFatigueAnalysis(selectedTargets = selected)
    }

    fun selectFatigueContributionTarget(target: FatigueTarget) {
        contributionSourcesUserSelected = false
        rebuildFatigueAnalysis(contributionTarget = target, selectedSourceKeys = emptySet())
    }

    fun selectFatigueContributionGrouping(grouping: ContributionGrouping) {
        contributionSourcesUserSelected = false
        rebuildFatigueAnalysis(grouping = grouping, selectedSourceKeys = emptySet())
    }

    fun selectFatigueContributionSources(sourceKeys: Set<String>) {
        contributionSourcesUserSelected = true
        rebuildFatigueAnalysis(
            selectedSourceKeys = sourceKeys,
            defaultSourcesWhenEmpty = false
        )
    }

    fun backupRecords(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportRecordsBackup(uri)
            }.onSuccess { result ->
                _recordTransferMessage.value = result.summaryText("기록 백업")
            }.onFailure { error ->
                _recordTransferMessage.value = "기록 백업 실패: ${error.message ?: "알 수 없는 오류"}"
            }
        }
    }

    fun restoreRecords(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.importRecordsBackup(uri)
            }.onSuccess { result ->
                _recordTransferMessage.value = result.summaryText("기록 복원")
                refreshAnalysisSummaries()
            }.onFailure { error ->
                _recordTransferMessage.value = "기록 복원 실패: ${error.message ?: "알 수 없는 오류"}"
            }
        }
    }

    private suspend fun refreshAnalysisSummaries() {
        runCatching {
            repository.fatigueAnalysisHistory()
        }.onSuccess { history ->
            fatigueAnalysisHistory = history
            rebuildFatigueAnalysis()
            val today = history.lastOrNull()?.state?.date ?: currentDate
            val checkIns = repository.recentCheckIns(
                today.minusDays(13).toString(),
                today.toString()
            )
            coachFatigueCauses = CoachFatigueCauseAnalyzer().analyze(
                today = today,
                history = history,
                checkIns = checkIns
            )
            coachCheckInGuidance = CoachCheckInInterpreter().guidance(
                checkIn = checkIns.lastOrNull { it.date == today.toString() },
                objectiveFatigue = history.lastOrNull()?.state
            )
            _coachingSignalsSummary.value = repository.coachingSignalsSummary(history)
            rebuildCoachAnalysisInsight()
        }.onFailure {
            _fatigueAnalysisState.value = _fatigueAnalysisState.value.copy(
                isLoading = false,
                errorMessage = "피로도 기록을 불러오지 못했습니다."
            )
        }
        val phaseStatus = runCatching {
            repository.phaseAwareTodayStatus()
        }.onSuccess { status ->
            _phaseAwareTodayStatus.value = status
            _todayReadinessSummary.value = status.current
        }.getOrNull()
        runCatching {
            repository.homeTodaySummary(phaseStatus)
        }.onSuccess { summary ->
            _homeTodaySummary.value = summary
        }
        val readinessSummary = phaseStatus?.current ?: runCatching {
            repository.todayReadinessSummary()
        }.onSuccess { summary ->
            _todayReadinessSummary.value = summary
        }.getOrNull()
        runCatching {
            repository.badmintonTransferSummary(readinessSummary)
        }.onSuccess { summary ->
            _badmintonTransferSummary.value = summary
        }
        runCatching {
            repository.badmintonTransferCoverageSummary(fatigueAnalysisHistory.lastOrNull()?.state)
        }.onSuccess { summary ->
            coachTransferCoverage = summary
            rebuildCoachAnalysisInsight()
        }
        runCatching {
            repository.performanceTrendSummary()
        }.onSuccess { summary ->
            _performanceTrendSummary.value = summary
        }
    }

    private fun rebuildFatigueAnalysis(
        period: FatigueAnalysisPeriod = _fatigueAnalysisState.value.detail.selectedPeriod,
        selectedTargets: Set<FatigueTarget> = _fatigueAnalysisState.value.detail.selectedFatigueTargets,
        contributionTarget: FatigueTarget = _fatigueAnalysisState.value.detail.contributionTarget,
        grouping: ContributionGrouping = _fatigueAnalysisState.value.detail.contributionGrouping,
        selectedSourceKeys: Set<String> = _fatigueAnalysisState.value.detail.selectedContributionSourceKeys,
        defaultSourcesWhenEmpty: Boolean = !contributionSourcesUserSelected
    ) {
        _fatigueAnalysisState.value = FatigueAnalysisMapper.map(
            history = fatigueAnalysisHistory,
            period = period,
            selectedTargets = selectedTargets,
            contributionTarget = contributionTarget,
            grouping = grouping,
            selectedSourceKeys = selectedSourceKeys,
            defaultSourcesWhenEmpty = defaultSourcesWhenEmpty
        )
    }

    private fun rebuildCoachAnalysisInsight() {
        _coachAnalysisInsight.value = CoachAnalysisInsightBuilder.combine(
            fatigue = coachFatigueCauses,
            transfer = coachTransferCoverage,
            checkInGuidance = coachCheckInGuidance
        )
    }
}
