package com.training.trackplanner.analysis.tissue

const val TISSUE_COD_CONTEXT_POLICY_VERSION = "RCV-COD-CONTEXT-1.0"
const val TISSUE_RCV_CALCULATION_VERSION = "RCV-EXPOSURE-1.1"

data class TissueCodContextExerciseTier(
    val exerciseStableKey: String,
    val displayNameKo: String,
    val factor: Double,
    val policyVersion: String,
    val rationale: String,
    val evidenceStatus: String,
    val reviewStatus: String
)

data class TissueCodContextLoadUnitEligibility(
    val loadUnitStableKey: String,
    val jointComplexStableKey: String,
    val displayNameKo: String,
    val bodyRegion: String,
    val eligible: Boolean,
    val eligibilityReason: String,
    val exclusionReason: String,
    val policyVersion: String,
    val reviewStatus: String
)

data class TissueCodContextModifierRule(
    val modifierRuleId: String,
    val exerciseStableKey: String,
    val loadUnitStableKey: String,
    val contextType: String,
    val factor: Double,
    val policyVersion: String,
    val rationale: String,
    val evidenceStatus: String,
    val reviewStatus: String
)

enum class TissueContextModifierStatus {
    APPLIED_APPROVED_COD_CONTEXT,
    DEFAULT_EXERCISE_NOT_WHITELISTED,
    DEFAULT_LOAD_UNIT_NOT_ELIGIBLE,
    INVALID_DUPLICATE_RULE,
    INVALID_UNKNOWN_KEY
}

data class TissueContextModifierResolution(
    val factor: Double,
    val status: TissueContextModifierStatus,
    val modifierRuleId: String?,
    val exerciseStableKey: String,
    val loadUnitStableKey: String,
    val policyVersion: String,
    val diagnosticReason: String
)

class TissueContextModifierResolver(catalog: TissueRcvCatalog) {
    private val exerciseStableKeys = catalog.exerciseStableKeys
    private val loadUnitStableKeys = catalog.loadUnits.keys
    private val tiers = catalog.codContextExerciseTiers.associateBy(
        TissueCodContextExerciseTier::exerciseStableKey
    )
    private val eligibility = catalog.codContextLoadUnitEligibility.associateBy(
        TissueCodContextLoadUnitEligibility::loadUnitStableKey
    )
    private val rules = catalog.codContextModifierRules.groupBy {
        it.exerciseStableKey to it.loadUnitStableKey
    }

    fun resolve(
        exerciseStableKey: String,
        loadUnitStableKey: String
    ): TissueContextModifierResolution {
        if (exerciseStableKey !in exerciseStableKeys || loadUnitStableKey !in loadUnitStableKeys) {
            return resolution(
                factor = 1.0,
                status = TissueContextModifierStatus.INVALID_UNKNOWN_KEY,
                exerciseStableKey = exerciseStableKey,
                loadUnitStableKey = loadUnitStableKey,
                reason = "Unknown exercise or load-unit stable key."
            )
        }
        val tier = tiers[exerciseStableKey] ?: return resolution(
            factor = 1.0,
            status = TissueContextModifierStatus.DEFAULT_EXERCISE_NOT_WHITELISTED,
            exerciseStableKey = exerciseStableKey,
            loadUnitStableKey = loadUnitStableKey,
            reason = "Exercise is not on the approved COD context whitelist."
        )
        if (eligibility.getValue(loadUnitStableKey).eligible.not()) {
            return resolution(
                factor = 1.0,
                status = TissueContextModifierStatus.DEFAULT_LOAD_UNIT_NOT_ELIGIBLE,
                exerciseStableKey = exerciseStableKey,
                loadUnitStableKey = loadUnitStableKey,
                reason = eligibility.getValue(loadUnitStableKey).exclusionReason
            )
        }
        val matching = rules[exerciseStableKey to loadUnitStableKey].orEmpty()
        if (matching.size != 1) {
            return resolution(
                factor = 1.0,
                status = TissueContextModifierStatus.INVALID_DUPLICATE_RULE,
                exerciseStableKey = exerciseStableKey,
                loadUnitStableKey = loadUnitStableKey,
                reason = "Expected one approved COD context rule; found ${matching.size}."
            )
        }
        val rule = matching.single()
        return TissueContextModifierResolution(
            factor = rule.factor,
            status = TissueContextModifierStatus.APPLIED_APPROVED_COD_CONTEXT,
            modifierRuleId = rule.modifierRuleId,
            exerciseStableKey = exerciseStableKey,
            loadUnitStableKey = loadUnitStableKey,
            policyVersion = rule.policyVersion,
            diagnosticReason = rule.rationale
        )
    }

    private fun resolution(
        factor: Double,
        status: TissueContextModifierStatus,
        exerciseStableKey: String,
        loadUnitStableKey: String,
        reason: String
    ) = TissueContextModifierResolution(
        factor = factor,
        status = status,
        modifierRuleId = null,
        exerciseStableKey = exerciseStableKey,
        loadUnitStableKey = loadUnitStableKey,
        policyVersion = TISSUE_COD_CONTEXT_POLICY_VERSION,
        diagnosticReason = reason
    )
}

object TissueCodContextValidator {
    private val approvedFactors = setOf(1.04, 1.06, 1.09)
    private const val CONTEXT_TYPE = "CHANGE_OF_DIRECTION_DECELERATION"
    private const val EVIDENCE_STATUS = "USER_APPROVED_BOUNDED_PRODUCT_POLICY"

    fun requireValid(catalog: TissueRcvCatalog) {
        val tiers = catalog.codContextExerciseTiers
        require(tiers.size == 22) { "Expected exactly 22 COD context exercise tiers." }
        require(tiers.map { it.exerciseStableKey }.distinct().size == tiers.size) {
            "COD context exercise tiers contain duplicate stable keys."
        }
        require(tiers.count { it.factor == 1.09 } == 9)
        require(tiers.count { it.factor == 1.06 } == 6)
        require(tiers.count { it.factor == 1.04 } == 7)
        tiers.forEach { tier ->
            require(tier.exerciseStableKey in catalog.exerciseStableKeys)
            require(catalog.exerciseNamesByStableKey[tier.exerciseStableKey] == tier.displayNameKo) {
                "${tier.exerciseStableKey}: approved display name does not match the exercise index."
            }
            require(tier.factor.isFinite() && tier.factor in 1.0..1.10)
            require(tier.factor in approvedFactors && tier.factor != 1.08 && tier.factor != 1.10)
            require(tier.policyVersion == TISSUE_COD_CONTEXT_POLICY_VERSION)
            require(tier.rationale.isNotBlank() && tier.reviewStatus.isNotBlank())
            require(tier.evidenceStatus == EVIDENCE_STATUS)
        }

        val eligibility = catalog.codContextLoadUnitEligibility
        require(eligibility.size == 77)
        require(eligibility.map { it.loadUnitStableKey }.toSet() == catalog.loadUnits.keys) {
            "COD context eligibility must cover every load unit exactly once."
        }
        eligibility.forEach { row ->
            val loadUnit = catalog.loadUnits.getValue(row.loadUnitStableKey)
            val joint = catalog.jointComplexes.getValue(loadUnit.jointComplexStableKey)
            require(row.jointComplexStableKey == loadUnit.jointComplexStableKey)
            require(row.displayNameKo == loadUnit.nameKo)
            require(row.bodyRegion == joint.bodyRegion)
            require(row.policyVersion == TISSUE_COD_CONTEXT_POLICY_VERSION)
            require(row.reviewStatus.isNotBlank())
            require(row.loadUnitStableKey.contains("LEFT", ignoreCase = true).not())
            require(row.loadUnitStableKey.contains("RIGHT", ignoreCase = true).not())
            if (row.eligible) {
                require(row.bodyRegion in setOf("HIP", "KNEE", "ANKLE", "FOOT"))
                require(row.eligibilityReason.isNotBlank() && row.exclusionReason.isBlank())
            } else {
                require(row.exclusionReason.isNotBlank() && row.eligibilityReason.isBlank())
            }
        }

        val rules = catalog.codContextModifierRules
        require(rules.map { it.modifierRuleId }.distinct().size == rules.size) {
            "COD context modifier rule IDs must be unique."
        }
        require(rules.map { it.exerciseStableKey to it.loadUnitStableKey }.distinct().size == rules.size) {
            "COD context modifier rules contain a duplicate exercise/load-unit pair."
        }
        val eligibleKeys = eligibility.filter(TissueCodContextLoadUnitEligibility::eligible)
            .mapTo(linkedSetOf(), TissueCodContextLoadUnitEligibility::loadUnitStableKey)
        val expectedPairs = tiers.flatMap { tier ->
            eligibleKeys.map { loadUnitStableKey -> tier.exerciseStableKey to loadUnitStableKey }
        }.toSet()
        require(rules.map { it.exerciseStableKey to it.loadUnitStableKey }.toSet() == expectedPairs) {
            "COD context modifier rules must be the exact approved exercise/load-unit cross product."
        }
        val tierByKey = tiers.associateBy(TissueCodContextExerciseTier::exerciseStableKey)
        rules.forEach { rule ->
            require(rule.exerciseStableKey in catalog.exerciseStableKeys)
            require(rule.loadUnitStableKey in catalog.loadUnits)
            require(rule.contextType == CONTEXT_TYPE)
            require(rule.factor.isFinite() && rule.factor in approvedFactors)
            require(rule.factor == tierByKey.getValue(rule.exerciseStableKey).factor)
            require(rule.policyVersion == TISSUE_COD_CONTEXT_POLICY_VERSION)
            require(rule.rationale.isNotBlank() && rule.reviewStatus.isNotBlank())
            require(rule.evidenceStatus == EVIDENCE_STATUS)
        }
    }
}
