package com.training.trackplanner.data

import android.content.Context
import java.util.Locale

enum class TrainingRole {
    STRENGTH,
    HYPERTROPHY,
    POWER,
    PLYOMETRIC,
    STABILITY,
    MOBILITY,
    PREHAB,
    SKILL_DRILL,
    CONDITIONING,
    TEST,
    RECOVERY
}

enum class ProgramSlotCapability {
    MAIN_STRENGTH_SLOT,
    SECONDARY_STRENGTH_SLOT,
    ACCESSORY_SLOT,
    POWER_SLOT,
    PLYOMETRIC_SLOT,
    SPEED_REACTIVE_SLOT,
    STABILITY_SLOT
}

internal fun ProgramSlotCapability.legacyCompatibilityToken(): String = when (this) {
    ProgramSlotCapability.MAIN_STRENGTH_SLOT -> "MAIN_STRENGTH"
    ProgramSlotCapability.SECONDARY_STRENGTH_SLOT -> "SECONDARY_STRENGTH"
    ProgramSlotCapability.ACCESSORY_SLOT -> "ACCESSORY"
    ProgramSlotCapability.POWER_SLOT -> "POWER"
    ProgramSlotCapability.PLYOMETRIC_SLOT -> "PLYOMETRIC"
    ProgramSlotCapability.SPEED_REACTIVE_SLOT -> "SPEED_REACTIVE"
    ProgramSlotCapability.STABILITY_SLOT -> "STABILITY"
}

data class ExerciseRoleImportResolution(
    val trainingRoles: Set<TrainingRole>,
    val programSlotCapabilities: Set<ProgramSlotCapability>
)

object LegacyTrainingRoleImportMapper {
    fun resolve(raw: String): ExerciseRoleImportResolution = when (raw.trim().uppercase(Locale.ROOT)) {
        "MAIN_STRENGTH" -> ExerciseRoleImportResolution(
            setOf(TrainingRole.STRENGTH),
            setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT)
        )
        "SECONDARY_STRENGTH" -> ExerciseRoleImportResolution(
            setOf(TrainingRole.STRENGTH),
            setOf(ProgramSlotCapability.SECONDARY_STRENGTH_SLOT)
        )
        "ACCESSORY" -> ExerciseRoleImportResolution(
            emptySet(),
            setOf(ProgramSlotCapability.ACCESSORY_SLOT)
        )
        "POWER" -> ExerciseRoleImportResolution(
            setOf(TrainingRole.POWER),
            setOf(ProgramSlotCapability.POWER_SLOT)
        )
        "PLYOMETRIC" -> ExerciseRoleImportResolution(
            setOf(TrainingRole.PLYOMETRIC),
            setOf(ProgramSlotCapability.PLYOMETRIC_SLOT)
        )
        "SPEED_REACTIVE" -> ExerciseRoleImportResolution(
            emptySet(),
            setOf(ProgramSlotCapability.SPEED_REACTIVE_SLOT)
        )
        "STABILITY" -> ExerciseRoleImportResolution(
            setOf(TrainingRole.STABILITY),
            setOf(ProgramSlotCapability.STABILITY_SLOT)
        )
        "MOBILITY" -> ExerciseRoleImportResolution(setOf(TrainingRole.MOBILITY), emptySet())
        "PREHAB" -> ExerciseRoleImportResolution(setOf(TrainingRole.PREHAB), emptySet())
        "SKILL", "SKILL_DRILL" -> ExerciseRoleImportResolution(setOf(TrainingRole.SKILL_DRILL), emptySet())
        "CONDITIONING" -> ExerciseRoleImportResolution(setOf(TrainingRole.CONDITIONING), emptySet())
        "TEST" -> ExerciseRoleImportResolution(setOf(TrainingRole.TEST), emptySet())
        "RECOVERY" -> ExerciseRoleImportResolution(setOf(TrainingRole.RECOVERY), emptySet())
        "" -> ExerciseRoleImportResolution(emptySet(), emptySet())
        else -> throw IllegalArgumentException("Unsupported legacy training role: $raw")
    }
}

class ExerciseRoleRelationCatalog private constructor(
    private val trainingByStableKey: Map<String, Set<TrainingRole>>,
    private val capabilityByStableKey: Map<String, Set<ProgramSlotCapability>>
) {
    fun trainingRoles(exerciseStableKey: String): Set<TrainingRole> =
        trainingByStableKey[exerciseStableKey].orEmpty()

    fun programSlotCapabilities(exerciseStableKey: String): Set<ProgramSlotCapability> =
        capabilityByStableKey[exerciseStableKey].orEmpty()

    companion object {
        val EMPTY = ExerciseRoleRelationCatalog(emptyMap(), emptyMap())

        fun of(
            trainingRelations: Collection<ExerciseTrainingRoleRelation>,
            capabilityRelations: Collection<ExerciseProgramSlotCapabilityRelation>
        ): ExerciseRoleRelationCatalog {
            require(trainingRelations.distinctBy { it.exerciseStableKey to it.trainingRoleCode }.size == trainingRelations.size) {
                "Duplicate exercise training-role relation."
            }
            require(capabilityRelations.distinctBy { it.exerciseStableKey to it.capabilityCode }.size == capabilityRelations.size) {
                "Duplicate exercise program-slot capability relation."
            }
            return ExerciseRoleRelationCatalog(
                trainingByStableKey = trainingRelations.groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
                    .mapValues { (_, rows) -> rows.mapTo(linkedSetOf()) { TrainingRole.valueOf(it.trainingRoleCode) } },
                capabilityByStableKey = capabilityRelations.groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
                    .mapValues { (_, rows) -> rows.mapTo(linkedSetOf()) { ProgramSlotCapability.valueOf(it.capabilityCode) } }
            )
        }
    }
}

class ExerciseRoleRelationAssetLoader(private val context: Context) {
    fun load(validExerciseStableKeys: Set<String>): ExerciseRoleRelationCatalog {
        val training = parse(TRAINING_ROLE_ASSET).map { fields ->
            ExerciseTrainingRoleRelation(
                exerciseStableKey = fields.getValue("exerciseStableKey"),
                trainingRoleCode = TrainingRole.valueOf(fields.getValue("trainingRoleCode")).name,
                provenance = fields.getValue("provenance"),
                reviewStatus = fields.getValue("reviewStatus"),
                notes = fields.getValue("notes")
            )
        }
        val capabilities = parse(PROGRAM_SLOT_ASSET).map { fields ->
            ExerciseProgramSlotCapabilityRelation(
                exerciseStableKey = fields.getValue("exerciseStableKey"),
                capabilityCode = ProgramSlotCapability.valueOf(fields.getValue("capabilityCode")).name,
                provenance = fields.getValue("provenance"),
                reviewStatus = fields.getValue("reviewStatus"),
                notes = fields.getValue("notes")
            )
        }
        val orphans = (training.map { it.exerciseStableKey } + capabilities.map { it.exerciseStableKey })
            .filterNot(validExerciseStableKeys::contains)
            .distinct()
        require(orphans.isEmpty()) { "Orphan exercise role relations: ${orphans.joinToString()}" }
        return ExerciseRoleRelationCatalog.of(training, capabilities)
    }

    fun trainingRelations(): List<ExerciseTrainingRoleRelation> = parse(TRAINING_ROLE_ASSET).map { fields ->
        ExerciseTrainingRoleRelation(
            exerciseStableKey = fields.getValue("exerciseStableKey"),
            trainingRoleCode = TrainingRole.valueOf(fields.getValue("trainingRoleCode")).name,
            provenance = fields.getValue("provenance"),
            reviewStatus = fields.getValue("reviewStatus"),
            notes = fields.getValue("notes")
        )
    }

    fun programSlotCapabilityRelations(): List<ExerciseProgramSlotCapabilityRelation> =
        parse(PROGRAM_SLOT_ASSET).map { fields ->
            ExerciseProgramSlotCapabilityRelation(
                exerciseStableKey = fields.getValue("exerciseStableKey"),
                capabilityCode = ProgramSlotCapability.valueOf(fields.getValue("capabilityCode")).name,
                provenance = fields.getValue("provenance"),
                reviewStatus = fields.getValue("reviewStatus"),
                notes = fields.getValue("notes")
            )
        }

    private fun parse(asset: String): List<Map<String, String>> {
        val rows = context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().filter(String::isNotBlank).map(SeedData::parseCsvLine).toList()
        }
        require(rows.isNotEmpty()) { "Empty relation asset: $asset" }
        val header = rows.first().map { it.trim().trimStart('\uFEFF') }
        return rows.drop(1).map { values ->
            header.mapIndexed { index, name -> name to values.getOrElse(index) { "" }.trim() }.toMap()
        }
    }

    private companion object {
        const val TRAINING_ROLE_ASSET = "metadata/relations/exercise_training_role_relations_v1.csv"
        const val PROGRAM_SLOT_ASSET = "metadata/relations/exercise_program_slot_capability_relations_v1.csv"
    }
}
