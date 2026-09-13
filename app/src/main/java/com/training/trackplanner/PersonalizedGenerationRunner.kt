package com.training.trackplanner

import com.training.trackplanner.data.ProgramBuildProgressState
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgressReporter
import com.training.trackplanner.data.personalized.PersonalizedPlannerStage
import com.training.trackplanner.data.personalized.PersonalizedPlannerProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** ViewModel-owned single flight. No wall-clock timeout; cancellation belongs to its coroutine scope. */
internal class PersonalizedGenerationRunner(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ProgramBuildProgressState>
) {
    fun <T> launch(work: suspend (PersonalizedPlannerProgressReporter) -> T,
        onSuccess: (T) -> Unit): Boolean {
        if (state.value is ProgramBuildProgressState.Running) return false
        state.value = ProgramBuildProgressState.Running(5, PersonalizedPlannerStage.INPUT.message)
        scope.launch {
            val context = currentCoroutineContext()
            val reporter = object : PersonalizedPlannerProgressReporter {
                override fun report(stage: PersonalizedPlannerStage) = report(PersonalizedPlannerProgress(stage, stage.percent, stage.message))
                override fun report(update: PersonalizedPlannerProgress) {
                    context.ensureActive()
                    val current = state.value as? ProgramBuildProgressState.Running
                    if (current != null && update.percent >= current.progressPercent)
                        state.value = ProgramBuildProgressState.Running(update.percent, update.message)
                }
            }
            try {
                val result = work(reporter)
                context.ensureActive()
                onSuccess(result)
            } catch (cancelled: CancellationException) {
                state.value = ProgramBuildProgressState.Idle
                throw cancelled
            } catch (error: Exception) {
                state.value = ProgramBuildProgressState.Failed(error.message ?: "기록 기반 계획 생성에 실패했습니다.")
            }
        }
        return true
    }
}
