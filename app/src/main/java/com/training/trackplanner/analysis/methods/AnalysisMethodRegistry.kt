package com.training.trackplanner.analysis.methods

import com.training.trackplanner.analysis.core.AnalysisInputSnapshot

data class AnalysisMethodDescriptor(
    val methodId: String,
    val plannedVersion: String,
    val enabled: Boolean = false
)

class AnalysisMethodRegistry(
    private val methods: List<AnalysisMethod<AnalysisInputSnapshot, AnalysisMethodResult>> = emptyList(),
    val descriptors: List<AnalysisMethodDescriptor> = plannedDescriptors
) {
    fun enabledMethods(): List<AnalysisMethod<AnalysisInputSnapshot, AnalysisMethodResult>> =
        methods.filter { method -> method.enabled }

    companion object {
        val plannedDescriptors = listOf(
            AnalysisMethodDescriptor(
                methodId = "workload_recovery_v1",
                plannedVersion = "3.1.0"
            ),
            AnalysisMethodDescriptor(
                methodId = "strength_load_v1",
                plannedVersion = "3.2.0"
            ),
            AnalysisMethodDescriptor(
                methodId = "badminton_transfer_v1",
                plannedVersion = "3.3.0"
            ),
            AnalysisMethodDescriptor(
                methodId = "balance_safety_v1",
                plannedVersion = "3.4.0"
            ),
            AnalysisMethodDescriptor(
                methodId = "plan_adherence_v1",
                plannedVersion = "3.5.0"
            )
        )

        fun disabledFor300(): AnalysisMethodRegistry = AnalysisMethodRegistry()
    }
}
