package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.personalized.ApprovedBadmintonTaskProtocols
import com.training.trackplanner.data.personalized.PlannedActivityKind
import com.training.trackplanner.data.personalized.TaskProtocolB6Authorization
import com.training.trackplanner.data.personalized.TaskProtocolB6Status
import com.training.trackplanner.data.personalized.TaskProtocolExposureMetadata

internal object C24TaskProtocolTestFixture {
    fun sixCornerSemanticsJson(): String {
        val definition = ApprovedBadmintonTaskProtocols.definitions.single { it.stableKey == "ex_33841b88" }
        val tasks = definition.authorizedTasks
        return TaskProtocolExposureMetadata(
            TaskProtocolB6Authorization(
                status = TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL,
                definition = definition,
                materializationActivityKind = PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
                attributedTasks = tasks,
                transferEvidence = tasks.associateWith { BadmintonObjectiveTransferLevel.DIRECT }
            ),
            exposureIndex = 1
        ).toJsonString()
    }
}
