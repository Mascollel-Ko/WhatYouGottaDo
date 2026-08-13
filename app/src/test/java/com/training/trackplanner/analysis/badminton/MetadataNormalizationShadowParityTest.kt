package com.training.trackplanner.analysis.badminton

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodLabels
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MetadataNormalizationShadowParityTest {
    @Test
    fun canonical241IdentityShadowParityMatchesReviewedArtifact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = CanonicalExerciseMetadataRepository(context)
        val runtimeCatalog = repository.runtimeMetadataCatalog()
        val ofiByKey = repository.ofiRelations().groupBy { it.exerciseStableKey }
        val rolesByKey = repository.trainingRoleRelations().groupBy { it.exerciseStableKey }
        val slotsByKey = repository.programSlotCapabilityRelations().groupBy { it.exerciseStableKey }
        val progressionByKey = repository.progressionRelations().groupBy { it.exerciseStableKey }
        val strengthByKey = repository.strengthProxyRelations().groupBy { it.exerciseStableKey }

        val rows = repository.exercises().map { exercise ->
            val features = ExerciseAnalysisMapper.fromExercise(
                exercise,
                runtimeCatalog.resolve(exercise),
                canonicalBadmintonAuthority = true
            )
            val currentAxes = BadmintonTransferMetadataMapper.legacyTransferAxesForAudit(features).names()
            val normalizedAxes = BadmintonTransferMetadataMapper.transferAxes(features).names()
            val currentObjectives = legacyObjectiveKeys(exercise, features).sorted().joinToString("|")
            val normalizedObjectives = BadmintonTransferMetadataMapper.objectiveKeys(features)
                .map(::v050032ObjectiveToken)
                .sorted()
                .joinToString("|")
            val analysisEligibility = features.analysisEligibility.sorted().joinToString("|")
            val transferType = BadmintonTransferMetadataMapper.transferType(features).name
            val fatigueCost = BadmintonTransferMetadataMapper.fatigueCost(features).name
            val ofiSignals = ofiByKey[exercise.stableKey].orEmpty().sortedWith(compareBy({ it.relationType }, { it.relationValue }))
                .joinToString("|") { "${it.relationType}:${it.relationValue}:${it.coefficient.orEmpty()}" }
            val programClassification = (
                rolesByKey[exercise.stableKey].orEmpty().map { "ROLE:${it.trainingRoleCode}" } +
                    slotsByKey[exercise.stableKey].orEmpty().map { "SLOT:${it.capabilityCode}" }
                ).sorted().joinToString("|")
            val strengthClassification = (
                progressionByKey[exercise.stableKey].orEmpty().map { "PROGRESSION:${it.relationValue}" } +
                    strengthByKey[exercise.stableKey].orEmpty().map { "PROXY:${it.targetKey}:${it.relationRole}" }
                ).sorted().joinToString("|")
            val normalizedSource = buildList {
                add("level=${features.canonicalBadmintonTransferLevel}")
                add("types=${features.canonicalBadmintonTransferTypes.sorted().joinToString("|")}")
                add("skills=${features.canonicalBadmintonSkillTargets.sorted().joinToString("|")}")
                add("qualities=${features.badmintonPhysicalQualities.sorted().joinToString("|")}")
            }.joinToString(";")
            val currentSource = buildList {
                add("forceType=${features.forceType}")
                add("plane=${features.plane}")
                add("laterality=${features.laterality}")
                add("fatigue=${features.fatigueCategories.sorted().joinToString("|")}")
                add("muscles=${(features.primaryMuscles + features.secondaryMuscles).sorted().joinToString("|")}")
                add("roles=${features.badmintonTransferRoles.sorted().joinToString("|")}")
                add("skills=${features.badmintonSkillTargets.sorted().joinToString("|")}")
                add("qualities=${features.courtMovementTypes.sorted().joinToString("|")}")
            }.joinToString(";")
            val removedAxes = currentAxes.tokens() - normalizedAxes.tokens()
            val addedAxes = normalizedAxes.tokens() - currentAxes.tokens()
            val removedObjectives = currentObjectives.tokens() - normalizedObjectives.tokens()
            val addedObjectives = normalizedObjectives.tokens() - currentObjectives.tokens()
            val invariantMatch = listOf(
                analysisEligibility == analysisEligibility,
                transferType == transferType,
                fatigueCost == fatigueCost,
                ofiSignals == ofiSignals,
                programClassification == programClassification,
                strengthClassification == strengthClassification
            ).all { it }
            val decision = when {
                addedAxes.isNotEmpty() || addedObjectives.isNotEmpty() -> "CANONICAL_GAP"
                !invariantMatch -> "INFORMATION_LOSS"
                removedAxes.isEmpty() && removedObjectives.isEmpty() -> "PARITY_EXACT"
                else -> "PARITY_INTENTIONAL_CORRECTION"
            }
            listOf(
                exercise.stableKey,
                analysisEligibility,
                transferType,
                currentSource,
                currentAxes,
                currentObjectives,
                fatigueCost,
                ofiSignals,
                programClassification,
                strengthClassification,
                analysisEligibility,
                transferType,
                normalizedSource,
                normalizedAxes,
                normalizedObjectives,
                fatigueCost,
                ofiSignals,
                programClassification,
                strengthClassification,
                "removedAxes=${removedAxes.sorted().joinToString("|")};removedObjectives=${removedObjectives.sorted().joinToString("|")}",
                "addedAxes=${addedAxes.sorted().joinToString("|")};addedObjectives=${addedObjectives.sorted().joinToString("|")}",
                decision
            )
        }

        assertEquals(241, rows.size)
        val rendered = render(rows)
        val generated = repoFile("app/build/generated/metadata-normalization/metadata_normalization_shadow_parity_241.csv")
        generated.parentFile?.mkdirs()
        generated.writeText(rendered, Charsets.UTF_8)
        val blocking = rows.filter { it.last() in setOf("CANONICAL_GAP", "INFORMATION_LOSS", "AMBIGUOUS") }
        assertFalse(
            "Blocking parity rows:\n${blocking.joinToString("\n") { "${it.first()}: ${it[it.lastIndex - 1]} (${it.last()})" }}",
            blocking.isNotEmpty()
        )
        val reviewed = repoFile("docs/audits/metadata_normalization_shadow_parity_241.csv")
        check(reviewed.isFile) { "Reviewed parity artifact is missing. Copy ${generated.absolutePath} to ${reviewed.absolutePath}." }
        assertEquals(reviewed.readText(Charsets.UTF_8).normalizeLines(), rendered.normalizeLines())
    }

    private fun legacyObjectiveKeys(
        exercise: Exercise,
        features: com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
    ): Set<String> = BadmintonTrainingMethodLabels.keysFrom(
        courtMovementTypes = features.courtMovementTypes,
        transferRoles = features.badmintonTransferRoles,
        skillTargets = features.badmintonSkillTargets + features.canonicalBadmintonSkillTargets,
        includeAntiRotation = listOf(exercise.name, exercise.stableKey).joinToString(" ").uppercase().let { text ->
            listOf("PALLOF", "SUITCASE", "LANDMINE_ANTI_ROTATION", "ANTI_ROTATION_PRESS", "ANTI_ROTATION_HOLD", "항회전", "회전저항", "팔로프", "수트케이스")
                .any { it in text }
        }
    ).map(::v050032ObjectiveToken).toSet()

    // This frozen audit compares the v0.5.0.32 normalization artifact, whose
    // reviewed rotation token predates the v0.5.0.33 canonical UI rename.
    private fun v050032ObjectiveToken(value: String): String =
        if (value == "ROTATION_GENERATION") "ROTATION_POWER" else value

    private fun Set<BadmintonTransferAxis>.names(): String = map { it.name }.sorted().joinToString("|")

    private fun Double?.orEmpty(): String = this?.toString().orEmpty()

    private fun String.tokens(): Set<String> = split('|').filter(String::isNotBlank).toSet()

    private fun render(rows: List<List<String>>): String {
        val header = listOf(
            "stableKey",
            "currentAnalysisEligibility", "currentBadmintonTransferType", "currentBadmintonSourceSemantics",
            "currentDerivedBadmintonAxes", "currentBadmintonObjectiveKeys", "currentFatigueCost",
            "currentRelevantOfiSignals", "currentRelevantProgramClassification", "currentStrengthClassification",
            "normalizedAnalysisEligibility", "normalizedBadmintonTransferType", "normalizedBadmintonSourceSemantics",
            "normalizedDerivedBadmintonAxes", "normalizedBadmintonObjectiveKeys", "normalizedFatigueCost",
            "normalizedRelevantOfiSignals", "normalizedRelevantProgramClassification", "normalizedStrengthClassification",
            "semanticDelta", "outputDelta", "decision"
        )
        return (listOf(header) + rows).joinToString("\n", postfix = "\n") { row ->
            row.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
        }
    }

    private fun repoRoot(): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(current) { it.parentFile?.takeUnless { parent -> parent == it } }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private fun repoFile(path: String): File = File(repoRoot(), path)

    private fun String.normalizeLines(): String = replace("\r\n", "\n")
}
