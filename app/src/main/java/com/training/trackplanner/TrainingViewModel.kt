package com.training.trackplanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.data.AnalysisStats
import com.training.trackplanner.data.CalendarConflictMode
import com.training.trackplanner.data.CalendarConflictSummary
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.DailyRecordSummary
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramApplyConflictSummary
import com.training.trackplanner.data.ProgramApplyMode
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

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrainingRepository(TrainingDatabase.get(application), application)

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

    private val _todayReadinessSummary = MutableStateFlow<TodayReadinessSummary?>(null)
    val todayReadinessSummary: StateFlow<TodayReadinessSummary?> =
        _todayReadinessSummary.asStateFlow()

    private val _performanceTrendSummary = MutableStateFlow<PerformanceTrendSummary?>(null)
    val performanceTrendSummary: StateFlow<PerformanceTrendSummary?> =
        _performanceTrendSummary.asStateFlow()

    private val _badmintonTransferSummary = MutableStateFlow<BadmintonTransferSummary?>(null)
    val badmintonTransferSummary: StateFlow<BadmintonTransferSummary?> =
        _badmintonTransferSummary.asStateFlow()

    private val _recordTransferMessage = MutableStateFlow<String?>(null)
    val recordTransferMessage: StateFlow<String?> =
        _recordTransferMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfNeeded()
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

    fun addWorkout(date: String, exerciseId: Long) {
        viewModelScope.launch {
            repository.addWorkoutEntry(date, exerciseId)
            refreshAnalysisSummaries()
        }
    }

    fun updateWorkoutEntry(entry: WorkoutEntry) {
        viewModelScope.launch {
            repository.updateWorkoutEntry(entry)
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
        val readinessSummary = runCatching {
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
            repository.performanceTrendSummary()
        }.onSuccess { summary ->
            _performanceTrendSummary.value = summary
        }
    }
}
