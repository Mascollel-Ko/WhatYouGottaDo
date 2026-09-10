package com.training.trackplanner

import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreflight

/** Transient editor-attempt ownership; never persisted. Failure deliberately retains the confirmed payload. */
internal class PersonalizedPreparedRetryState {
    data class Payload(val preflight: PersonalizedPlanningPreflight, val answers: PersonalizedPlanningAnswers)
    var payload: Payload? = null
        private set

    fun confirm(preflight: PersonalizedPlanningPreflight, answers: Map<String, String>): Payload =
        Payload(preflight, PersonalizedPlanningAnswers(answers.toMap())).also { payload = it }

    fun clear() { payload = null }

    fun retry(prepare: () -> Unit, generate: (Payload) -> Unit) {
        val confirmed = payload
        if (confirmed == null) prepare() else generate(confirmed)
    }
}
