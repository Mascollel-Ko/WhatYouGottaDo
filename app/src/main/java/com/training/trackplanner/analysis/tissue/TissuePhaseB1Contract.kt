package com.training.trackplanner.analysis.tissue

object TissuePhaseB1Contract {
    const val reviewBatchId = "TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE"
    const val preservedPreflightSourceId = "PREFLIGHT_32658037"

    val targetStableKeys = setOf(
        "barbell_back_squat",
        "ex_cb3c4dc2",
        "ex_c5043892",
        "ex_b78a8f95",
        "ex_d60745b4",
        "ex_64644b5e",
        "ex_bb728af2",
        "ex_bd072cd",
        "ex_5c8751d2",
        "ex_5ca7133f",
        "ex_e465d1e9",
        "ex_a3ddd8ac",
        "ex_314df428",
        "ex_d6726746",
        "ex_377448a9"
    )

    val targetTissueDimensions = setOf(
        target("ACHILLES_TENDON", TissueLoadDimension.PEAK_TENSILE_LOAD),
        target("ACHILLES_TENDON", TissueLoadDimension.CYCLIC_TENSILE_LOAD),
        target("ACHILLES_TENDON", TissueLoadDimension.ENERGY_STORAGE_RELEASE),
        target("ACHILLES_TENDON", TissueLoadDimension.LOADING_RATE),
        target("PATELLAR_TENDON", TissueLoadDimension.PEAK_TENSILE_LOAD),
        target("PATELLAR_TENDON", TissueLoadDimension.CYCLIC_TENSILE_LOAD),
        target("PATELLAR_TENDON", TissueLoadDimension.ECCENTRIC_LOAD),
        target("PATELLAR_TENDON", TissueLoadDimension.ENERGY_STORAGE_RELEASE),
        target("PATELLAR_TENDON", TissueLoadDimension.LOADING_RATE),
        target("KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION),
        target("KNEE_TIBIOFEMORAL", TissueLoadDimension.COMPRESSION),
        target("KNEE_TIBIOFEMORAL", TissueLoadDimension.ANTERIOR_POSTERIOR_SHEAR),
        target("KNEE_ACL", TissueLoadDimension.ANTERIOR_TRANSLATION),
        target("KNEE_PCL", TissueLoadDimension.POSTERIOR_TRANSLATION),
        target("QUADRICEPS_TENDON", TissueLoadDimension.PEAK_TENSILE_LOAD),
        target("QUADRICEPS_TENDON", TissueLoadDimension.CYCLIC_TENSILE_LOAD),
        target("QUADRICEPS_TENDON", TissueLoadDimension.ECCENTRIC_LOAD),
        target("ANKLE_TALOCRURAL", TissueLoadDimension.COMPRESSION),
        target("ANKLE_TALOCRURAL", TissueLoadDimension.IMPACT_IMPULSE),
        target("ANKLE_TALOCRURAL", TissueLoadDimension.END_RANGE_STRESS),
        target("ANKLE_SUBTALAR", TissueLoadDimension.ROTATIONAL_SHEAR),
        target("ANKLE_SUBTALAR", TissueLoadDimension.STABILITY_DEMAND),
        target("ANKLE_SUBTALAR", TissueLoadDimension.IMPACT_IMPULSE),
        target("KNEE_ACL", TissueLoadDimension.VALGUS),
        target("KNEE_ACL", TissueLoadDimension.INTERNAL_ROTATION),
        target("KNEE_ACL", TissueLoadDimension.DECELERATION_STABILIZATION),
        target("KNEE_ACL", TissueLoadDimension.IMPACT_STABILIZATION),
        target("KNEE_MCL", TissueLoadDimension.VALGUS),
        target("KNEE_MCL", TissueLoadDimension.DECELERATION_STABILIZATION),
        target("ANKLE_LATERAL_LIGAMENT_COMPLEX", TissueLoadDimension.INVERSION),
        target("ANKLE_LATERAL_LIGAMENT_COMPLEX", TissueLoadDimension.IMPACT_STABILIZATION)
    )

    private fun target(tissueId: String, dimension: TissueLoadDimension) =
        TissueDimensionReference(tissueId, dimension)
}
