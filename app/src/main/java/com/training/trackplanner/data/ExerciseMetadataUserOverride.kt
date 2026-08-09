package com.training.trackplanner.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

enum class ExerciseMetadataOverrideSource {
    USER_EDIT,
    RESTORED_USER_OVERRIDE
}

@Entity(
    tableName = "exercise_metadata_user_overrides",
    primaryKeys = ["stableKey", "fieldScope", "fieldKey"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["stableKey"],
            childColumns = ["stableKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["stableKey"])]
)
data class ExerciseMetadataUserOverrideEntity(
    val stableKey: String,
    val fieldScope: String,
    val fieldKey: String,
    val valueEncoding: String,
    val value: String,
    val isExplicitEmpty: Boolean,
    val source: String,
    val semanticCanonicalRevisionAtEdit: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validated(): ExerciseMetadataUserOverrideEntity {
        require(stableKey.isNotBlank()) { "Metadata override stableKey must be nonblank." }
        val scope = enumValueOf<ExerciseMetadataFieldScope>(fieldScope)
        val encoding = enumValueOf<ExerciseMetadataValueEncoding>(valueEncoding)
        enumValueOf<ExerciseMetadataOverrideSource>(source)
        require(semanticCanonicalRevisionAtEdit.isNotBlank()) {
            "Metadata override semantic revision must be nonblank."
        }
        require(updatedAt > 0L) { "Metadata override updatedAt must be positive." }
        val definition = requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey)) {
            "Unknown exercise metadata override field: $fieldKey"
        }
        require(definition.policy == ExerciseMetadataFieldPolicy.USER_OVERRIDE_ELIGIBLE) {
            "Field is not user-override eligible: $fieldKey"
        }
        require(definition.fieldScope == scope) { "Metadata override field scope mismatch: $fieldKey" }
        require(definition.valueEncoding == encoding) { "Metadata override encoding mismatch: $fieldKey" }
        if (isExplicitEmpty) {
            require(definition.supportsExplicitEmpty) { "Field does not support explicit empty: $fieldKey" }
            require(value.isEmpty()) { "Explicit-empty override must use the canonical empty value: $fieldKey" }
        } else {
            require(value.isNotEmpty()) { "Empty metadata override must be explicitly marked: $fieldKey" }
        }
        require(value == ExerciseMetadataFieldPolicyRegistry.canonicalize(value, encoding)) {
            "Metadata override value is not canonically encoded: $fieldKey"
        }
        return this
    }

    fun toSnapshotRow(): ExerciseMetadataSnapshotRow = ExerciseMetadataSnapshotRow(
        stableKey = stableKey,
        fieldKey = fieldKey,
        fieldScope = enumValueOf(fieldScope),
        valueEncoding = enumValueOf(valueEncoding),
        value = value,
        isExplicitEmpty = isExplicitEmpty
    )
}

@Dao
interface ExerciseMetadataUserOverrideDao {
    @Query(
        "SELECT * FROM exercise_metadata_user_overrides " +
            "WHERE stableKey = :stableKey ORDER BY fieldScope, fieldKey"
    )
    suspend fun findByStableKey(stableKey: String): List<ExerciseMetadataUserOverrideEntity>

    @Query(
        "SELECT * FROM exercise_metadata_user_overrides " +
            "WHERE stableKey = :stableKey AND fieldScope = :fieldScope AND fieldKey = :fieldKey LIMIT 1"
    )
    suspend fun findField(
        stableKey: String,
        fieldScope: String,
        fieldKey: String
    ): ExerciseMetadataUserOverrideEntity?

    @Query("SELECT * FROM exercise_metadata_user_overrides ORDER BY stableKey, fieldScope, fieldKey")
    suspend fun all(): List<ExerciseMetadataUserOverrideEntity>

    @Query("SELECT COUNT(*) FROM exercise_metadata_user_overrides WHERE stableKey = :stableKey")
    suspend fun countForStableKey(stableKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnchecked(row: ExerciseMetadataUserOverrideEntity)

    suspend fun upsert(row: ExerciseMetadataUserOverrideEntity) {
        upsertUnchecked(row.validated())
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllUnchecked(rows: List<ExerciseMetadataUserOverrideEntity>)

    @Transaction
    suspend fun upsertAll(rows: List<ExerciseMetadataUserOverrideEntity>) {
        validateOverrideBatch(rows)
        upsertAllUnchecked(rows)
    }

    @Query(
        "DELETE FROM exercise_metadata_user_overrides " +
            "WHERE stableKey = :stableKey AND fieldScope = :fieldScope AND fieldKey = :fieldKey"
    )
    suspend fun deleteField(stableKey: String, fieldScope: String, fieldKey: String)

    @Query("DELETE FROM exercise_metadata_user_overrides WHERE stableKey = :stableKey")
    suspend fun deleteForStableKey(stableKey: String)

    @Transaction
    suspend fun replaceForStableKey(stableKey: String, rows: List<ExerciseMetadataUserOverrideEntity>) {
        require(rows.all { it.stableKey == stableKey }) { "Replacement override stableKey mismatch." }
        validateOverrideBatch(rows)
        deleteForStableKey(stableKey)
        if (rows.isNotEmpty()) upsertAllUnchecked(rows)
    }

    @Query("DELETE FROM exercise_metadata_user_overrides")
    suspend fun deleteAll()
}

private fun validateOverrideBatch(rows: List<ExerciseMetadataUserOverrideEntity>) {
    rows.forEach(ExerciseMetadataUserOverrideEntity::validated)
    require(rows.distinctBy { Triple(it.stableKey, it.fieldScope, it.fieldKey) }.size == rows.size) {
        "Duplicate metadata override field in one mutation."
    }
}
