package com.training.trackplanner.data

/** Typed six-role user muscle relations, stored in existing versioned override rows. */
object UserMuscleRoleCodec {
    const val FIELD_KEY = "relation.muscleRoles"
    val MUSCLE_CODES = listOf(
        "ADDUCTOR_ABDUCTOR", "ANTERIOR_CORE", "BACK_LATS", "BICEPS",
        "CALVES", "CHEST", "FOREARM_GRIP", "GLUTES", "GLUTE_MEDIUS",
        "HAMSTRINGS", "HIP_ADDUCTOR", "LATERAL_CORE",
        "POSTERIOR_CHAIN_ERECTORS", "QUADS", "SHOULDERS", "TRICEPS"
    )
    private val allowed = MUSCLE_CODES.toSet()

    fun coefficient(role: CanonicalMuscleRole): Double? = when (role) {
        CanonicalMuscleRole.PRIMARY -> 1.0
        CanonicalMuscleRole.SECONDARY -> 0.5
        else -> null
    }

    fun entry(key: String, code: String, role: CanonicalMuscleRole): CanonicalMuscleRoleEntry {
        require(code in allowed) { "Unknown muscle code: " + code }
        return CanonicalMuscleRoleEntry(
            relationKey = "USER_ROLE_" + key + "_" + code,
            muscleCode = code,
            role = role,
            coefficient = coefficient(role),
            status = "USER_EDIT",
            sourceStableKey = key,
            provenance = "USER_OVERRIDE"
        )
    }

    fun encode(roles: List<CanonicalMuscleRoleEntry>): String {
        require(roles.map { it.muscleCode }.distinct().size == roles.size) { "Duplicate muscle code" }
        return roles.map { row ->
            require(row.muscleCode in allowed) { "Unknown muscle code" }
            require(row.coefficient == null ||
                (row.coefficient.isFinite() && row.coefficient >= 0.0))
            row.muscleCode + "~" + row.role.name + "~" + (row.coefficient?.toString() ?: "-")
        }.sorted().joinToString("|")
    }

    fun decode(raw: String, stableKey: String): List<CanonicalMuscleRoleEntry> {
        if (raw.isEmpty()) return emptyList()
        val rows = raw.split('|').map { token ->
            val parts = token.split('~')
            require(parts.size == 3 && parts[0] in allowed) { "Invalid typed muscle role" }
            val role = CanonicalMuscleRole.valueOf(parts[1])
            val coefficient = if (parts[2] == "-") null else
                requireNotNull(parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 })
            entry(stableKey, parts[0], role).copy(coefficient = coefficient)
        }
        require(rows.map { it.muscleCode }.distinct().size == rows.size)
        require(encode(rows) == raw) { "Noncanonical typed muscle roles" }
        return rows
    }

    /** No fabricated roles: unmappable old custom tokens are kept in the legacy record. */
    fun legacyDraft(exercise: Exercise): List<CanonicalMuscleRoleEntry> {
        val mapping = mapOf(
            "QUADRICEPS" to "QUADS", "RECTUS_FEMORIS" to "QUADS",
            "HAMSTRING" to "HAMSTRINGS", "GLUTE" to "GLUTES",
            "CALF" to "CALVES", "TIBIALIS" to "CALVES",
            "ERECTOR_SPINAE" to "POSTERIOR_CHAIN_ERECTORS",
            "BACK" to "BACK_LATS", "LAT" to "BACK_LATS",
            "RHOMBOID" to "BACK_LATS", "TRAPEZIUS" to "BACK_LATS",
            "FOREARM" to "FOREARM_GRIP", "GRIP" to "FOREARM_GRIP",
            "CORE" to "ANTERIOR_CORE", "OBLIQUE" to "LATERAL_CORE",
            "SHOULDER" to "SHOULDERS", "GLUTE_MEDIUS" to "GLUTE_MEDIUS",
            "HIP_ADDUCTOR" to "HIP_ADDUCTOR"
        )
        fun mapped(raw: String): List<String>? {
            val values = raw.split(',', '|', '/', ';').map(String::trim)
                .filter(String::isNotBlank)
                .map { it.uppercase(java.util.Locale.ROOT) }
                .map { mapping[it] ?: it }
            return values.takeIf { it.all(allowed::contains) }
        }
        val primary = mapped(exercise.primaryMuscles) ?: return emptyList()
        val secondary = mapped(exercise.secondaryMuscles) ?: return emptyList()
        return (primary.map { it to CanonicalMuscleRole.PRIMARY } +
            secondary.filterNot(primary::contains).map { it to CanonicalMuscleRole.SECONDARY })
            .distinctBy { it.first }
            .map { (code, role) -> entry(exercise.stableKey, code, role) }
    }
    /**
     * Consumers that take relation CSV rows (weekly anatomy/Program B) must see
     * precisely the same effective muscle roles as the exercise editor and graphs.
     */
    fun effectiveRelations(
        baseline: List<CanonicalMetadataRelation>,
        userRows: List<ExerciseMetadataUserOverrideEntity>,
        exercises: List<Exercise> = emptyList()
    ): List<CanonicalMetadataRelation> {
        val byKey = userRows
            .filter { it.fieldKey == FIELD_KEY }
            .associate { it.stableKey to it.validated().value }
        val generated = mutableListOf<CanonicalMetadataRelation>()
        // An old custom exercise must be reviewed, not silently converted
        // into active analysis relations. Its raw legacy fields remain intact.
        byKey.forEach { (key, value) ->
            decode(value, key).forEach { row ->
                generated += CanonicalMetadataRelation(
                    domain = CanonicalRelationDomain.MUSCLE,
                    relationKey = row.relationKey,
                    exerciseStableKey = key,
                    relationType = row.role.name,
                    relationValue = row.muscleCode,
                    coefficient = row.coefficient,
                    sourceStableKey = key,
                    status = row.status,
                    provenance = row.provenance
                )
            }
        }
        return baseline.filterNot { it.exerciseStableKey in byKey } + generated
    }

}
