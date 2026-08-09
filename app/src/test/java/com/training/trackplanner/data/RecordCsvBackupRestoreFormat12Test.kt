package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCsvBackupRestoreFormat12Test {
    @Test
    fun explicitOverrideCapabilityRoundTripsAndRepresentedZeroRemainsExplicit() {
        val exercise = exercise("format12_exercise")
        val override = overrideRow(exercise.stableKey, "exercise.category", "User category")
        val withOverride = format12(
            body = RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = emptyList(),
                metrics = emptyList(),
                exercises = listOf(exercise),
                metadataUserOverrides = listOf(override)
            ),
            represented = setOf(exercise.stableKey)
        )
        val parsedWithOverride = RecordCsvBackupRestore.parse(withOverride) as RecordCsvImportData.Restore

        assertEquals(
            setOf(RecordCsvBackupRestore.EXPLICIT_METADATA_USER_OVERRIDES_CAPABILITY),
            parsedWithOverride.manifest!!.capabilities
        )
        assertEquals(listOf(override), parsedWithOverride.metadataUserOverrideRows)
        assertEquals(1, parsedWithOverride.manifest!!.representedExerciseCount)

        val representedZero = format12(
            body = RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = emptyList(),
                metrics = emptyList(),
                exercises = listOf(exercise)
            ),
            represented = setOf(exercise.stableKey)
        )
        val parsedZero = RecordCsvBackupRestore.parse(representedZero) as RecordCsvImportData.Restore

        assertTrue(parsedZero.metadataUserOverrideRows.isEmpty())
        assertEquals(listOf(exercise.stableKey), parsedZero.exerciseRows.map { it.stableKey })
    }

    @Test
    fun format12RejectsMissingCapabilityOrRequiredColumns() {
        val exercise = exercise("format12_malformed")
        val body = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise)
        )
        val missingCapability = RecordCsvBackupRestore.wrapWithManifest(
            body = body,
            appVersion = "test",
            exportedAt = 1L,
            entityCounts = emptyMap(),
            capabilities = emptySet(),
            representedExerciseStableKeys = setOf(exercise.stableKey),
            semanticCanonicalRevision = "semantic-test",
            sourceDatabaseLineageId = "source-test"
        )
        val missingColumn = format12(
            body = body.replaceFirst("metadata_override_source", "removed_override_source"),
            represented = setOf(exercise.stableKey)
        )

        assertTrue(runCatching { RecordCsvBackupRestore.parse(missingCapability) }.isFailure)
        assertTrue(runCatching { RecordCsvBackupRestore.parse(missingColumn) }.isFailure)
    }

    @Test
    fun format12RejectsRepresentedExerciseWithoutExplicitUserState() {
        val exercise = exercise("format12_missing_state")
        val body = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise)
        )
        val malformedBody = mutateSimpleRow(body, "exercise", "is_active", "")

        assertTrue(
            runCatching {
                RecordCsvBackupRestore.parse(format12(malformedBody, setOf(exercise.stableKey)))
            }.isFailure
        )
    }

    private fun format12(body: String, represented: Set<String>): String =
        RecordCsvBackupRestore.wrapWithManifest(
            body = body,
            appVersion = "test",
            exportedAt = 1L,
            entityCounts = emptyMap(),
            representedExerciseStableKeys = represented,
            semanticCanonicalRevision = "semantic-test",
            sourceDatabaseLineageId = "source-test"
        )

    private fun exercise(stableKey: String): Exercise = Exercise(
        stableKey = stableKey,
        name = "Format 12 exercise",
        category = "Strength",
        equipment = "BARBELL",
        isCustom = true
    )

    private fun overrideRow(
        stableKey: String,
        fieldKey: String,
        value: String
    ): ExerciseMetadataUserOverrideEntity {
        val definition = requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))
        return ExerciseMetadataUserOverrideEntity(
            stableKey = stableKey,
            fieldScope = definition.fieldScope.name,
            fieldKey = fieldKey,
            valueEncoding = definition.valueEncoding.name,
            value = value,
            isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "semantic-test",
            updatedAt = 1L
        )
    }

    private fun mutateSimpleRow(body: String, rowType: String, column: String, value: String): String {
        val lines = body.lines().toMutableList()
        val header = lines.first().split(',')
        val columnIndex = header.indexOf(column)
        val rowTypeIndex = header.indexOf("row_type")
        val lineIndex = lines.indexOfFirst { line -> line.split(',').getOrNull(rowTypeIndex) == rowType }
        val cells = lines[lineIndex].split(',').toMutableList()
        cells[columnIndex] = value
        lines[lineIndex] = cells.joinToString(",")
        return lines.joinToString("\n")
    }
}
