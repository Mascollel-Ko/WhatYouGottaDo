package com.training.trackplanner.data

import org.json.JSONObject

/** Explicit typed wire records. JSON is CSV serialization only, never app_meta storage. */
internal object ProgramProgressionWireCodec {
    fun encode(value: ProgressionRule): JSONObject = JSONObject()
        .put("version", value.version)
        .put("requireCompletion", value.requireCompletion)
        .put("rpePolicy", value.rpePolicy.name)
        .put("rpeThreshold", value.rpeThreshold)
        .put("successesRequired", value.successesRequired)
        .put("incrementKg", value.incrementKg ?: JSONObject.NULL)
        .put("firstFailure", value.firstFailure.name)
        .put("failuresBeforeDecrease", value.failuresBeforeDecrease)
        .put("decreasePercent", value.decreasePercent)
        .put("missingRpe", value.missingRpe.name)

    fun progressionRule(json: JSONObject): ProgressionRule = ProgressionRule(
        version = json.getInt("version"),
        requireCompletion = json.getBoolean("requireCompletion"),
        rpePolicy = ProgressionRpePolicy.valueOf(json.getString("rpePolicy")),
        rpeThreshold = json.getDouble("rpeThreshold"),
        successesRequired = json.getInt("successesRequired"),
        incrementKg = if (json.isNull("incrementKg")) null else json.getDouble("incrementKg"),
        firstFailure = FirstProgressionFailure.valueOf(json.getString("firstFailure")),
        failuresBeforeDecrease = json.getInt("failuresBeforeDecrease"),
        decreasePercent = json.getDouble("decreasePercent"),
        missingRpe = MissingProgressionRpe.valueOf(json.getString("missingRpe"))
    )

    fun encode(value: ProgressionSignature): JSONObject = JSONObject()
        .put("plannerRole", value.plannerRole.name)
        .put("exerciseStableKey", value.exerciseStableKey)
        .put("setCount", value.setCount)
        .put("repsPattern", value.repsPattern)
        .put("baseKg", value.baseKg ?: JSONObject.NULL)
        .put("oneRmSnapshotKg", value.oneRmSnapshotKg ?: JSONObject.NULL)
        .put("relativeIntensity", value.relativeIntensity ?: JSONObject.NULL)
        .put("style", value.style)
        .put("variant", value.variant)
        .put("trainingSlot", value.trainingSlot)
        .put("dayIntensity", value.dayIntensity)
        .put("basePolicy", value.basePolicy.name)
        .put("anchorSetIndex", value.anchorSetIndex ?: JSONObject.NULL)

    fun progressionSignature(json: JSONObject): ProgressionSignature = ProgressionSignature(
        plannerRole = ProgressionRole.valueOf(json.getString("plannerRole")),
        exerciseStableKey = json.getString("exerciseStableKey"),
        setCount = json.getInt("setCount"),
        repsPattern = json.getString("repsPattern"),
        baseKg = if (json.isNull("baseKg")) null else json.getDouble("baseKg"),
        oneRmSnapshotKg = if (json.isNull("oneRmSnapshotKg")) null else json.getDouble("oneRmSnapshotKg"),
        relativeIntensity = if (json.isNull("relativeIntensity")) null else json.getDouble("relativeIntensity"),
        style = json.getString("style"),
        variant = json.getString("variant"),
        trainingSlot = json.getString("trainingSlot"),
        dayIntensity = json.getString("dayIntensity"),
        basePolicy = ProgressionBase.valueOf(json.getString("basePolicy")),
        anchorSetIndex = if (json.isNull("anchorSetIndex")) null else json.getInt("anchorSetIndex")
    )

    fun encode(value: ProgramProgressionTrack): JSONObject = JSONObject()
        .put("id", value.id)
        .put("programStableKey", value.programStableKey)
        .put("exerciseStableKey", value.exerciseStableKey)
        .put("label", value.label)
        .put("role", value.role.name)
        .put("roleOverride", value.roleOverride.name)
        .put("mode", value.mode.name)
        .put("basePolicy", value.basePolicy.name)
        .put("anchorSetIndex", value.anchorSetIndex ?: JSONObject.NULL)
        .put("needsReview", value.needsReview)
        .put("rule", encode(value.rule))

    fun programProgressionTrack(json: JSONObject): ProgramProgressionTrack = ProgramProgressionTrack(
        id = json.getString("id"),
        programStableKey = json.getString("programStableKey"),
        exerciseStableKey = json.getString("exerciseStableKey"),
        label = json.getString("label"),
        role = ProgressionRole.valueOf(json.getString("role")),
        roleOverride = ProgressionRole.valueOf(json.getString("roleOverride")),
        mode = ProgressionMode.valueOf(json.getString("mode")),
        basePolicy = ProgressionBase.valueOf(json.getString("basePolicy")),
        anchorSetIndex = if (json.isNull("anchorSetIndex")) null else json.getInt("anchorSetIndex"),
        needsReview = json.getBoolean("needsReview"),
        rule = progressionRule(json.getJSONObject("rule"))
    )

    fun encode(value: ProgramProgressionItem): JSONObject = JSONObject()
        .put("programItemId", value.programItemId)
        .put("logicalItemId", value.logicalItemId)
        .put("trackId", value.trackId)
        .put("linkMode", value.linkMode.name)
        .put("signature", encode(value.signature))

    fun programProgressionItem(json: JSONObject): ProgramProgressionItem = ProgramProgressionItem(
        programItemId = json.getLong("programItemId"),
        logicalItemId = json.getString("logicalItemId"),
        trackId = json.getString("trackId"),
        linkMode = ProgressionLinkMode.valueOf(json.getString("linkMode")),
        signature = progressionSignature(json.getJSONObject("signature"))
    )

    fun encode(value: ProgramApplication): JSONObject = JSONObject()
        .put("id", value.id)
        .put("programStableKey", value.programStableKey)
        .put("programName", value.programName)
        .put("startDate", value.startDate)
        .put("appliedAt", value.appliedAt)

    fun programApplication(json: JSONObject): ProgramApplication = ProgramApplication(
        id = json.getString("id"),
        programStableKey = json.getString("programStableKey"),
        programName = json.getString("programName"),
        startDate = json.getString("startDate"),
        appliedAt = json.getLong("appliedAt")
    )

    fun encode(value: ProgramWorkoutLink): JSONObject = JSONObject()
        .put("entryId", value.entryId)
        .put("applicationId", value.applicationId)
        .put("sourceProgramStableKey", value.sourceProgramStableKey)
        .put("sourceItemId", value.sourceItemId)
        .put("programName", value.programName)
        .put("weekNumber", value.weekNumber)
        .put("dayOfWeek", value.dayOfWeek)
        .put("trackId", value.trackId)
        .put("trackLabel", value.trackLabel)
        .put("sequence", value.sequence)
        .put("role", value.role.name)
        .put("mode", value.mode.name)
        .put("basePolicy", value.basePolicy.name)
        .put("anchorSetIndex", value.anchorSetIndex ?: JSONObject.NULL)
        .put("needsReview", value.needsReview)
        .put("rule", encode(value.rule))

    fun programWorkoutLink(json: JSONObject): ProgramWorkoutLink = ProgramWorkoutLink(
        entryId = json.getLong("entryId"),
        applicationId = json.getString("applicationId"),
        sourceProgramStableKey = json.getString("sourceProgramStableKey"),
        sourceItemId = json.getString("sourceItemId"),
        programName = json.getString("programName"),
        weekNumber = json.getInt("weekNumber"),
        dayOfWeek = json.getInt("dayOfWeek"),
        trackId = json.getString("trackId"),
        trackLabel = json.getString("trackLabel"),
        sequence = json.getInt("sequence"),
        role = ProgressionRole.valueOf(json.getString("role")),
        mode = ProgressionMode.valueOf(json.getString("mode")),
        basePolicy = ProgressionBase.valueOf(json.getString("basePolicy")),
        anchorSetIndex = if (json.isNull("anchorSetIndex")) null else json.getInt("anchorSetIndex"),
        needsReview = json.getBoolean("needsReview"),
        rule = progressionRule(json.getJSONObject("rule"))
    )

    fun encode(value: ProgramPrescriptionSet): JSONObject = JSONObject()
        .put("plannedSetIndex", value.plannedSetIndex ?: JSONObject.NULL)
        .put("originalExists", value.originalExists)
        .put("entryId", value.entryId)
        .put("setIndex", value.setIndex)
        .put("originalReps", value.originalReps)
        .put("originalKg", value.originalKg)
        .put("originalSeconds", value.originalSeconds)
        .put("plannedReps", value.plannedReps)
        .put("plannedKg", value.plannedKg)
        .put("plannedSeconds", value.plannedSeconds)

    fun programPrescriptionSet(json: JSONObject): ProgramPrescriptionSet = ProgramPrescriptionSet(
        plannedSetIndex = if (json.isNull("plannedSetIndex")) null else json.getInt("plannedSetIndex"),
        originalExists = json.getBoolean("originalExists"),
        entryId = json.getLong("entryId"),
        setIndex = json.getInt("setIndex"),
        originalReps = json.getInt("originalReps"),
        originalKg = json.getDouble("originalKg"),
        originalSeconds = json.getInt("originalSeconds"),
        plannedReps = json.getInt("plannedReps"),
        plannedKg = json.getDouble("plannedKg"),
        plannedSeconds = json.getInt("plannedSeconds")
    )

    fun encode(value: ProgressionSuggestion): JSONObject = JSONObject()
        .put("id", value.id)
        .put("applicationId", value.applicationId)
        .put("trackId", value.trackId)
        .put("sourceEntryId", value.sourceEntryId)
        .put("targetEntryId", value.targetEntryId)
        .put("previousActualKg", value.previousActualKg ?: JSONObject.NULL)
        .put("currentPlanKg", value.currentPlanKg ?: JSONObject.NULL)
        .put("suggestedKg", value.suggestedKg ?: JSONObject.NULL)
        .put("judgmentRpe", value.judgmentRpe ?: JSONObject.NULL)
        .put("direction", value.direction.name)
        .put("reasons", value.reasons)
        .put("evidenceHash", value.evidenceHash)
        .put("resolution", value.resolution.name)
        .put("createdAt", value.createdAt)
        .put("resolvedAt", value.resolvedAt ?: JSONObject.NULL)
        .put("resolvedKg", value.resolvedKg ?: JSONObject.NULL)
        .put("rule", encode(value.rule))

    fun progressionSuggestion(json: JSONObject): ProgressionSuggestion = ProgressionSuggestion(
        id = json.getString("id"),
        applicationId = json.getString("applicationId"),
        trackId = json.getString("trackId"),
        sourceEntryId = json.getLong("sourceEntryId"),
        targetEntryId = json.getLong("targetEntryId"),
        previousActualKg = if (json.isNull("previousActualKg")) null else json.getDouble("previousActualKg"),
        currentPlanKg = if (json.isNull("currentPlanKg")) null else json.getDouble("currentPlanKg"),
        suggestedKg = if (json.isNull("suggestedKg")) null else json.getDouble("suggestedKg"),
        judgmentRpe = if (json.isNull("judgmentRpe")) null else json.getDouble("judgmentRpe"),
        direction = ProgressionDirection.valueOf(json.getString("direction")),
        reasons = json.getString("reasons"),
        evidenceHash = json.getString("evidenceHash"),
        resolution = ProgressionResolution.valueOf(json.getString("resolution")),
        createdAt = json.getLong("createdAt"),
        resolvedAt = if (json.isNull("resolvedAt")) null else json.getLong("resolvedAt"),
        resolvedKg = if (json.isNull("resolvedKg")) null else json.getDouble("resolvedKg"),
        rule = progressionRule(json.getJSONObject("rule"))
    )

}
