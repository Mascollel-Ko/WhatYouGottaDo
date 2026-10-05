package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import org.json.JSONObject

/** Within-session task dose only. This type is not B6 authority and does not authorize materialization. */
enum class TaskPrescriptionMode {
    REPETITIONS,
    REPETITIONS_PER_SIDE,
    DURATION_SECONDS,
    DURATION_RANGE_SECONDS,
    REPETITION_RANGE
}

enum class TaskLateralitySemantics { NOT_APPLICABLE, PER_SIDE }

/** Null means the source does not establish a load mode; it must not be treated as no load. */
enum class TaskPrescriptionLoadMode { BODYWEIGHT, NO_EXTERNAL_LOAD, EXTERNAL_LOAD, NOT_APPLICABLE }

/** Lossless typed representation for reviewed task guides and future task contracts. */
data class TaskPrescriptionShape(
    val mode: TaskPrescriptionMode,
    val setCount: Int,
    val reps: Int? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val seconds: Int? = null,
    val minSeconds: Int? = null,
    val maxSeconds: Int? = null,
    val laterality: TaskLateralitySemantics = TaskLateralitySemantics.NOT_APPLICABLE,
    val restSeconds: Int,
    val loadMode: TaskPrescriptionLoadMode? = null,
    val activityKind: PlannedActivityKind? = null,
    val targetRpe: Double? = null
) {
    init {
        require(setCount > 0) { "Task prescription requires a positive set/round count." }
        require(restSeconds >= 0) { "Task prescription rest cannot be negative." }
        require(targetRpe == null || targetRpe.isFinite() && targetRpe in 1.0..10.0)
        when (mode) {
            TaskPrescriptionMode.REPETITIONS -> {
                require(reps != null && reps > 0)
                require(minReps == null && maxReps == null && seconds == null && minSeconds == null && maxSeconds == null)
                require(laterality == TaskLateralitySemantics.NOT_APPLICABLE)
            }
            TaskPrescriptionMode.REPETITIONS_PER_SIDE -> {
                require(reps != null && reps > 0)
                require(minReps == null && maxReps == null && seconds == null && minSeconds == null && maxSeconds == null)
                require(laterality == TaskLateralitySemantics.PER_SIDE)
            }
            TaskPrescriptionMode.DURATION_SECONDS -> {
                require(seconds != null && seconds > 0)
                require(reps == null && minReps == null && maxReps == null && minSeconds == null && maxSeconds == null)
                require(laterality == TaskLateralitySemantics.NOT_APPLICABLE)
            }
            TaskPrescriptionMode.DURATION_RANGE_SECONDS -> {
                require(minSeconds != null && maxSeconds != null && minSeconds > 0 && minSeconds <= maxSeconds)
                require(reps == null && minReps == null && maxReps == null && seconds == null)
                require(laterality == TaskLateralitySemantics.NOT_APPLICABLE)
            }
            TaskPrescriptionMode.REPETITION_RANGE -> {
                require(minReps != null && maxReps != null && minReps > 0 && minReps <= maxReps)
                require(reps == null && seconds == null && minSeconds == null && maxSeconds == null)
                require(laterality == TaskLateralitySemantics.NOT_APPLICABLE)
            }
        }
    }

    /** A shape is execution-complete only when its exercise context specifies activity and load semantics. */
    val executionContextComplete: Boolean get() = activityKind != null && loadMode != null

    fun format(): String {
        val dose = when (mode) {
            TaskPrescriptionMode.REPETITIONS -> "${reps}회"
            TaskPrescriptionMode.REPETITIONS_PER_SIDE -> "${reps}회/side"
            TaskPrescriptionMode.DURATION_SECONDS -> "${seconds}초"
            TaskPrescriptionMode.DURATION_RANGE_SECONDS -> "${minSeconds}–${maxSeconds}초"
            TaskPrescriptionMode.REPETITION_RANGE -> "${minReps}–${maxReps}회"
        }
        val unit = if (mode in setOf(TaskPrescriptionMode.DURATION_SECONDS, TaskPrescriptionMode.DURATION_RANGE_SECONDS)) "라운드" else "세트"
        return "$setCount $unit × $dose · 휴식 ${restSeconds}초"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode.name)
        .put("setCount", setCount)
        .put("reps", reps ?: JSONObject.NULL)
        .put("minReps", minReps ?: JSONObject.NULL)
        .put("maxReps", maxReps ?: JSONObject.NULL)
        .put("seconds", seconds ?: JSONObject.NULL)
        .put("minSeconds", minSeconds ?: JSONObject.NULL)
        .put("maxSeconds", maxSeconds ?: JSONObject.NULL)
        .put("laterality", laterality.name)
        .put("restSeconds", restSeconds)
        .put("loadMode", loadMode?.name ?: JSONObject.NULL)
        .put("activityKind", activityKind?.name ?: JSONObject.NULL)
        .put("targetRpe", targetRpe ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): TaskPrescriptionShape = TaskPrescriptionShape(
            mode = TaskPrescriptionMode.valueOf(json.getString("mode")),
            setCount = json.getInt("setCount"),
            reps = json.intOrNull("reps"),
            minReps = json.intOrNull("minReps"),
            maxReps = json.intOrNull("maxReps"),
            seconds = json.intOrNull("seconds"),
            minSeconds = json.intOrNull("minSeconds"),
            maxSeconds = json.intOrNull("maxSeconds"),
            laterality = TaskLateralitySemantics.valueOf(json.getString("laterality")),
            restSeconds = json.getInt("restSeconds"),
            loadMode = json.stringOrNull("loadMode")?.let(TaskPrescriptionLoadMode::valueOf),
            activityKind = json.stringOrNull("activityKind")?.let(PlannedActivityKind::valueOf),
            targetRpe = json.doubleOrNull("targetRpe")
        )

        private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
        private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
        private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else getDouble(name)
    }
}

/** The six target families in the C23 task execution contract. */
enum class CanonicalTaskTarget {
    ACCELERATION, DECELERATION, FOOTWORK, JUMP_LANDING, LUNGE_REACH, REACTION;

    fun objective(): BadmintonObjective = BadmintonObjective.valueOf(name)
}
