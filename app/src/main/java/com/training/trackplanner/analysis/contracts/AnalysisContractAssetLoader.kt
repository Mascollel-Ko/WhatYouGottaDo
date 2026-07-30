package com.training.trackplanner.analysis.contracts

import android.content.Context
import java.util.Locale

class AnalysisContractRepository internal constructor(
    relations: Collection<ExerciseAnalysisRelations>
) {
    private val byStableKey = relations.associateBy { it.exerciseStableKey.lookupKey() }

    val size: Int
        get() = byStableKey.size

    fun all(): List<ExerciseAnalysisRelations> = byStableKey.values.toList()

    fun find(exerciseStableKey: String): ExerciseAnalysisRelations? =
        byStableKey[exerciseStableKey.lookupKey()]

    private fun String.lookupKey(): String = trim().lowercase(Locale.ROOT)
}

class AnalysisContractAssetLoader(
    private val context: Context
) {
    fun load(): AnalysisContractRepository =
        parse(context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() })

    companion object {
        const val ASSET_PATH = "metadata/analysis_contract_baseline_v1.csv"
        const val CONTRACT_VERSION = "ANALYSIS_CONTRACT_BASELINE_V1"

        internal fun parse(csv: String): AnalysisContractRepository {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.isNotEmpty()) { "Analysis contract asset is empty." }
            val headers = parseCsvLine(lines.first())
            val rows = lines.drop(1).mapIndexed { index, line ->
                val values = parseCsvLine(line)
                require(values.size == headers.size) {
                    "Analysis contract row ${index + 2} has ${values.size} values; expected ${headers.size}."
                }
                ContractRow(headers.zip(values).toMap())
            }
            require(rows.none { it.stableKey.isBlank() }) { "Analysis contract contains a blank stableKey." }
            val duplicateIdentities = rows.groupingBy(ContractRow::identity).eachCount().filterValues { it > 1 }
            require(duplicateIdentities.isEmpty()) {
                "Analysis contract contains duplicate relation identities: ${duplicateIdentities.keys.take(3)}"
            }
            return AnalysisContractRepository(rows.groupBy(ContractRow::stableKey).map { (stableKey, grouped) ->
                grouped.toRelations(stableKey)
            })
        }

        private fun parseCsvLine(line: String): List<String> {
            val values = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var index = 0
            while (index < line.length) {
                val char = line[index]
                when {
                    char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                        current.append('"')
                        index += 1
                    }
                    char == '"' -> quoted = !quoted
                    char == ',' && !quoted -> {
                        values += current.toString()
                        current.clear()
                    }
                    else -> current.append(char)
                }
                index += 1
            }
            require(!quoted) { "Unclosed quote in analysis contract CSV." }
            values += current.toString()
            return values
        }

        private fun List<ContractRow>.toRelations(stableKey: String): ExerciseAnalysisRelations {
            val summary = singleOrNull(RelationType.OFI_SNAPSHOT)
            val scores = rows(RelationType.OFI_SCORE).associate { row ->
                enumValueOf<OfiAxisId>(row.relationId) to row.coefficient.toInt()
            }
            return ExerciseAnalysisRelations(
                exerciseStableKey = stableKey,
                capabilities = rows(RelationType.CAPABILITY).map { row ->
                    ExerciseAnalysisCapability(
                        exerciseStableKey = stableKey,
                        analysisTypeId = enumValueOf(row.relationId),
                        status = enumValueOf(row.status),
                        confidence = row.confidence,
                        sourceStatus = row.sourceStatus,
                        version = row.version
                    )
                },
                ofiDoseProfile = singleOrNull(RelationType.OFI_DOSE)?.let { row ->
                    ExerciseOfiDoseProfile(stableKey, row.relationId, row.qualifier)
                },
                ofiAxisContributions = rows(RelationType.OFI_AXIS).map { row ->
                    ExerciseOfiAxisContribution(
                        stableKey,
                        enumValueOf(row.relationId),
                        row.coefficient,
                        row.qualifier,
                        row.confidence,
                        row.sourceStatus,
                        row.version
                    )
                },
                ofiComparisonGroups = rows(RelationType.OFI_GROUP).map { row ->
                    ExerciseOfiComparisonGroup(stableKey, enumValueOf(row.qualifier), row.relationId)
                },
                ofiGoldenSnapshot = summary?.let { row ->
                    ExerciseOfiGoldenSnapshot(
                        stableKey,
                        scores,
                        row.coefficient.toInt(),
                        row.role,
                        row.qualifier,
                        rows(RelationType.OFI_CAUTION).map(ContractRow::relationId)
                    )
                },
                programSlotCapabilities = rows(RelationType.PROGRAM_SLOT).map { row ->
                    ExerciseProgramSlotCapability(
                        stableKey,
                        row.relationId,
                        enumValueOf(row.role),
                        row.coefficient,
                        row.confidence,
                        row.sourceStatus
                    )
                },
                programRoleEligibility = rows(RelationType.PROGRAM_ROLE).map { row ->
                    ExerciseProgramRoleEligibility(stableKey, row.relationId, enumValueOf(row.qualifier))
                },
                variantGroups = rows(RelationType.VARIANT_GROUP).map { row ->
                    ExerciseVariantGroup(stableKey, row.relationId)
                },
                progressionGroups = rows(RelationType.PROGRESSION_GROUP).map { row ->
                    ExerciseProgressionGroup(stableKey, row.relationId)
                },
                muscleContributions = rows(RelationType.MUSCLE).map { row ->
                    ExerciseMuscleContribution(
                        stableKey,
                        row.relationId,
                        enumValueOf(row.role),
                        row.coefficient,
                        row.confidence,
                        row.sourceStatus
                    )
                },
                badmintonTransferPoints = rows(RelationType.BADMINTON_TRANSFER).map { row ->
                    ExerciseBadmintonTransferPoint(
                        stableKey,
                        row.relationId,
                        enumValueOf(row.qualifier),
                        row.coefficient,
                        row.confidence,
                        row.sourceStatus
                    )
                },
                badmintonFatigueCost = singleOrNull(RelationType.BADMINTON_FATIGUE_COST)
                    ?.relationId,
                physicalQualityPoints = rows(RelationType.PHYSICAL_QUALITY).map { row ->
                    ExercisePhysicalQualityPoint(
                        stableKey,
                        row.relationId,
                        row.coefficient,
                        row.confidence,
                        row.sourceStatus
                    )
                },
                movementPatterns = rows(RelationType.MOVEMENT_PATTERN).map { row ->
                    ExerciseMovementPatternMembership(stableKey, row.relationId)
                },
                jointActions = rows(RelationType.JOINT_ACTION).map { row ->
                    ExerciseJointAction(stableKey, row.relationId)
                },
                bodyRegions = rows(RelationType.BODY_REGION).map { row ->
                    ExerciseBodyRegionMembership(stableKey, row.relationId)
                },
                modalities = rows(RelationType.MODALITY).map { row ->
                    ExerciseModality(stableKey, row.relationId)
                },
                trainingGoals = rows(RelationType.TRAINING_GOAL).map { row ->
                    TrainingGoalMembership(stableKey, row.relationId)
                }
            )
        }

        private fun List<ContractRow>.rows(type: RelationType): List<ContractRow> =
            filter { it.relationType == type }

        private fun List<ContractRow>.singleOrNull(type: RelationType): ContractRow? =
            rows(type).let { matches ->
                require(matches.size <= 1) {
                    "Analysis contract contains multiple $type rows for ${first().stableKey}."
                }
                matches.singleOrNull()
            }
    }
}

private enum class RelationType {
    CAPABILITY,
    OFI_DOSE,
    OFI_AXIS,
    OFI_GROUP,
    OFI_SCORE,
    OFI_SNAPSHOT,
    OFI_CAUTION,
    PROGRAM_SLOT,
    PROGRAM_ROLE,
    VARIANT_GROUP,
    PROGRESSION_GROUP,
    MUSCLE,
    BADMINTON_TRANSFER,
    BADMINTON_FATIGUE_COST,
    PHYSICAL_QUALITY,
    MOVEMENT_PATTERN,
    JOINT_ACTION,
    BODY_REGION,
    MODALITY,
    TRAINING_GOAL
}

private data class ContractRow(
    val stableKey: String,
    val relationType: RelationType,
    val relationId: String,
    val role: String,
    val qualifier: String,
    val coefficient: Double,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus,
    val version: String,
    val status: String
) {
    constructor(values: Map<String, String>) : this(
        stableKey = values.getValue("exerciseStableKey"),
        relationType = enumValueOf(values.getValue("relationType")),
        relationId = values.getValue("relationId"),
        role = values.getValue("role"),
        qualifier = values.getValue("qualifier"),
        coefficient = values.getValue("coefficient").toDouble(),
        confidence = values.getValue("confidence").toDouble(),
        sourceStatus = enumValueOf(values.getValue("sourceStatus")),
        version = values.getValue("version"),
        status = values.getValue("status")
    )

    fun identity(): String = listOf(stableKey, relationType, relationId, role, qualifier).joinToString("\u001F")
}
