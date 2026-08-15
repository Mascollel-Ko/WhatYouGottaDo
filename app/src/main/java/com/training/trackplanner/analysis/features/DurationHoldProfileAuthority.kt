package com.training.trackplanner.analysis.features

object DurationHoldProfileAuthority {
    private val policies = mapOf(
        "ex_a44ae2ca" to DurationHoldPolicy.PLANK,
        "ex_a8385c4a" to DurationHoldPolicy.PLANK,
        "ex_f6d43398" to DurationHoldPolicy.SIDE_PLANK
    )

    fun resolve(exerciseStableKey: String): DurationHoldPolicy? = policies[exerciseStableKey]

    fun supportedStableKeys(): Set<String> = policies.keys
}
