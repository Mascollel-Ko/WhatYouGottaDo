package com.training.trackplanner.analysis.tissue

class TissueLoadProfileRepository(
    profiles: Collection<TissueLoadProfile>,
    rubrics: Collection<TissueLoadRubric> = emptyList()
) {
    private val profilesByStableKey = profiles.groupBy(TissueLoadProfile::stableKey)
    private val rubricsById = rubrics.associateBy(TissueLoadRubric::rubricId)

    init {
        require(profiles.map(TissueLoadProfile::identity).distinct().size == profiles.size) {
            "Duplicate tissue profile identity."
        }
        require(rubricsById.size == rubrics.size) { "Duplicate tissue rubricId." }
    }

    fun profilesForStableKey(stableKey: String): List<TissueLoadProfile> =
        profilesByStableKey[stableKey].orEmpty()

    fun rubric(rubricId: String): TissueLoadRubric? = rubricsById[rubricId]
}
