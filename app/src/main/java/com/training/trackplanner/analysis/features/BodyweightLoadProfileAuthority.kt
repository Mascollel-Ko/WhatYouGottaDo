package com.training.trackplanner.analysis.features

enum class BodyweightLoadPolicy {
    BODYWEIGHT_PLUS_ADDED,
    BODYWEIGHT_MINUS_ASSISTANCE,
    BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR
}

data class BodyweightLoadProfile(
    val policy: BodyweightLoadPolicy,
    val bodyweightFactor: Double = 1.0,
    val addedWeightFactor: Double = 1.0
)

object BodyweightLoadProfileAuthority {
    private val bodyweightPlusAdded = BodyweightLoadProfile(BodyweightLoadPolicy.BODYWEIGHT_PLUS_ADDED)
    private val invertedRow = BodyweightLoadProfile(
        policy = BodyweightLoadPolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR,
        bodyweightFactor = 0.60
    )
    private val defaultPushUp = BodyweightLoadProfile(
        policy = BodyweightLoadPolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR,
        bodyweightFactor = 0.65,
        addedWeightFactor = 0.70
    )
    private val pikePushUp = defaultPushUp.copy(bodyweightFactor = 0.70)
    private val declinePushUp = defaultPushUp.copy(bodyweightFactor = 0.80)

    private val profiles = mapOf(
        "pull_up" to bodyweightPlusAdded,
        "ex_6466fe77" to bodyweightPlusAdded,
        "ex_6463edad" to bodyweightPlusAdded,
        "ex_deca2b61" to bodyweightPlusAdded,
        "ex_e1894690" to bodyweightPlusAdded,
        "ex_e41e8dcf" to bodyweightPlusAdded,
        "ex_e41f4c2b" to bodyweightPlusAdded,
        "ex_e4f911bb" to bodyweightPlusAdded,
        "ex_d9084b5e" to invertedRow,
        "ex_e159d15a" to invertedRow,
        "gymnastic_ring_inverted_row" to invertedRow,
        "suspension_trainer_inverted_row" to invertedRow,
        "one_arm_gymnastic_ring_row" to invertedRow,
        "one_arm_suspension_trainer_row" to invertedRow,
        "ex_28902b13" to defaultPushUp,
        "ex_73b0b63f" to defaultPushUp,
        "ex_c4535de3" to defaultPushUp,
        "ex_debf6a8b" to defaultPushUp,
        "ex_fa2e73b3" to defaultPushUp,
        "ex_3caa236b" to pikePushUp,
        "ex_fb67af37" to declinePushUp
    )

    fun resolve(exerciseStableKey: String): BodyweightLoadProfile? = profiles[exerciseStableKey]

    fun supportedStableKeys(): Set<String> = profiles.keys
}
